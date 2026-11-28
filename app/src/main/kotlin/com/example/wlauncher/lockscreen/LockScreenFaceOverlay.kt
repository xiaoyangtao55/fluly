package com.flue.launcher.lockscreen

import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import android.view.WindowManager
import androidx.core.content.ContextCompat
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.OnBackPressedDispatcherOwner
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.ui.platform.AbstractComposeView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.navigationevent.NavigationEventDispatcher
import androidx.navigationevent.NavigationEventDispatcherOwner
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.flue.launcher.watchface.WatchFaceDescriptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

private const val TAG = "LockScreenFaceOverlay"
private const val UNLOCK_POLL_INTERVAL_MS = 1_000L

/**
 * 锁屏表盘的悬浮窗路径：拥有 `SYSTEM_ALERT_WINDOW` 权限时，用
 * `TYPE_APPLICATION_OVERLAY` 把表盘盖在 Keyguard 之上。
 *
 * 为什么存在：锁屏 Activity 的启动要经过 `LockScreenForegroundGate`——
 * 其他 App 前台时熄屏，startActivity 会撞后台启动限制（Android 14 起
 * SAW 也不再豁免），从别的应用锁屏就看不到表盘。悬浮窗不经过 startActivity，
 * `WindowManager.addView` 只需要 SAW 权限，任意前台状态下都能生效。
 *
 * 与锁屏 Activity 共用的红线（docs/design/lock-screen-watch-face.md）：
 * **不调 `setTurnScreenOn`，不持 `FLAG_KEEP_SCREEN_ON`** —— 窗口只在
 * 「屏幕亮且 Keyguard 锁定」期间可见，熄屏期间 `faceVisible=false` 暂停渲染；
 * 解锁完成（USER_PRESENT）立即移除。触摸全部穿透（FLAG_NOT_TOUCHABLE），
 * 上滑解锁手势交给 Keyguard，认证之前这张层收不到任何输入、也不发任何动作。
 *
 * 全部方法要求主线程调用（receiver 与 addView/removeView 的约束一致）。
 * 进程存活是前提：与锁屏 Activity 相同的 best effort——HOME 进程被杀则
 * 触发链路静默失效，不额外保活。
 */
object LockScreenFaceOverlay {

    private var shown: OverlayHost? = null
    private var loadJob: Job? = null
    private var unlockPolling: Runnable? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /** 是否具备悬浮窗路径的资格（权限授予与否由用户在系统设置里控制）。 */
    fun canShow(context: Context): Boolean = Settings.canDrawOverlays(context)

    /**
     * 添加悬浮窗并加载表盘配置。已在显示、权限缺失或 addView 抛异常时返回 false
     * （调用方据此回退锁屏 Activity 路径）。
     */
    fun show(context: Context): Boolean {
        if (shown != null) return true
        val appContext = context.applicationContext
        if (!canShow(appContext)) return false
        val windowManager = appContext.getSystemService(WindowManager::class.java)
            ?: return false

        val view = LockScreenFaceOverlayView(appContext)
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            // NOT_TOUCHABLE：纯显示层，触摸穿透给 Keyguard；认证前这一层收不到输入。
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.OPAQUE,
        )
        return try {
            // 先把进程抬到前台服务优先级再挂窗口：持窗的缓存进程会被 LMK 秒杀
            // （装机实测 pid 一秒一换），窗口跟着陪葬。startForegroundService
            // 从后台启动依赖的正是已授予的 SYSTEM_ALERT_WINDOW 豁免。
            LockScreenFaceOverlayService.acquire(appContext)
            // Compose 在窗口 attach 时会用 SavedStateRegistry 恢复 Recomposer，
            // 组件必须先到 CREATED（performRestore + ON_CREATE），否则
            // consumeRestoredStateForKey 会抛 IllegalStateException（与 Activity
            // 的 onCreate 先于窗口 attach 同构）。
            view.moveToCreated()
            windowManager.addView(view, params)
            view.startLifecycle()
            val host = OverlayHost(appContext, view, windowManager)
            shown = host
            Log.i(TAG, "overlay added")
            loadConfiguration()
            registerReceivers(appContext)
            startUnlockPolling(host)
            true
        } catch (t: Throwable) {
            Log.w(TAG, "无法添加锁屏表盘悬浮窗", t)
            // 别留孤儿前台服务：窗口没挂成，通知也不该常驻。
            LockScreenFaceOverlayService.release(appContext)
            runCatching { windowManager.removeView(view) }
            view.disposeLifecycle()
            false
        }
    }

    /** 移除悬浮窗并清理。未显示时是空操作。 */
    fun hide() {
        val host = shown ?: return
        shown = null
        loadJob?.cancel()
        loadJob = null
        cancelUnlockPolling()
        runCatching { host.context.unregisterReceiver(receiver) }
        try {
            host.windowManager.removeView(host.view)
        } catch (t: Throwable) {
            Log.w(TAG, "移除悬浮窗失败", t)
        } finally {
            host.view.disposeLifecycle()
        }
        // 解锁了，保活服务和通知一并撤走。
        LockScreenFaceOverlayService.release(host.context)
        Log.i(TAG, "overlay removed")
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val host = shown ?: return
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> host.view.faceVisible = false
                Intent.ACTION_SCREEN_ON -> {
                    // 可见性只看 interactive（与 Activity 的 isLockScreenFaceVisible 同
                    // 语义），**不**用 isKeyguardLocked 决定收不收——部分 ROM 在锁屏
                    // 期间把它误报成 false，会让表盘在亮屏瞬间被自己收掉。
                    if (host.context.getSystemService(PowerManager::class.java)?.isInteractive == true) {
                        host.view.faceVisible = true
                    }
                }
                Intent.ACTION_USER_PRESENT -> hide()
            }
        }
    }

    /**
     * 解锁兜底：正常路径靠 USER_PRESENT，但厂商裁过 SystemUI 的 ROM 上它有几率
     * 不来（装机实测），那时悬浮窗会一直盖在解锁后的应用上。所以显示期间每秒
     * 查一次「亮屏且已不在锁屏」——是就收摊。1Hz 的 KeyguardManager binder 调用
     * 只在悬浮窗显示期间存在，熄屏时窗口本来就不可见，代价可忽略。
     */
    private fun startUnlockPolling(host: OverlayHost) {
        cancelUnlockPolling()
        val poll = object : Runnable {
            override fun run() {
                if (shown?.view !== host.view) return
                val power = host.context.getSystemService(PowerManager::class.java)
                val keyguard = host.context.getSystemService(KeyguardManager::class.java)
                if (power?.isInteractive == true && keyguard?.isKeyguardLocked == false) {
                    Log.i(TAG, "poll: unlocked, removing overlay")
                    hide()
                    return
                }
                mainHandler.postDelayed(this, UNLOCK_POLL_INTERVAL_MS)
            }
        }
        unlockPolling = poll
        mainHandler.postDelayed(poll, UNLOCK_POLL_INTERVAL_MS)
    }

    private fun cancelUnlockPolling() {
        unlockPolling?.let(mainHandler::removeCallbacks)
        unlockPolling = null
    }

    private fun registerReceivers(context: Context) {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        ContextCompat.registerReceiver(
            context,
            receiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }

    private fun loadConfiguration() {
        val host = shown ?: return
        loadJob?.cancel()
        loadJob = scope.launch {
            val snapshot = host.context.readLockScreenFaceConfig()
            val resolved = resolveLockScreenFaceDescriptor(host.context, snapshot)
            // 配置加载期间窗口可能已被移除（解锁广播比磁盘更快时）。
            if (shown?.view === host.view) {
                host.view.snapshot = snapshot
                host.view.descriptor = resolved
                Log.i(TAG, "configuration loaded type=${resolved?.type}")
            }
        }
    }

    private class OverlayHost(
        val context: Context,
        val view: LockScreenFaceOverlayView,
        val windowManager: WindowManager,
    )
}

/**
 * 悬浮窗里的 Compose 宿主。窗口内没有 Activity 的生命周期，这里自建一对
 * ViewTree owner 挂给 Compose（官方模式：LifecycleRegistry +
 * SavedStateRegistryController），attach 后推 ON_CREATE/ON_START，
 * 移除前推 ON_DESTROY，Compose 的 Recomposer 随之创建与销毁。
 *
 * 另外要自己当 `OnBackPressedDispatcherOwner` / `NavigationEventDispatcherOwner`
 * 并用 CompositionLocal 显式提供：宿主渲染的表盘组合树里有 `BackHandler`
 * （WatchMaker/JB 宿主、WatchFaceLayer 都带），没有 Owner 会在组合时直接
 * IllegalStateException 崩掉进程、窗口陪葬。悬浮窗不消费返回键——
 * dispatcher 只是占位满足组合树，事件层由 FLAG_NOT_TOUCHABLE 保证收不到。
 */
private class LockScreenFaceOverlayView(context: Context) : AbstractComposeView(context),
    LifecycleOwner,
    SavedStateRegistryOwner,
    OnBackPressedDispatcherOwner,
    NavigationEventDispatcherOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateRegistryController.savedStateRegistry

    private val backDispatcher = OnBackPressedDispatcher(Runnable {})
    override val onBackPressedDispatcher: OnBackPressedDispatcher get() = backDispatcher
    override val navigationEventDispatcher: NavigationEventDispatcher = NavigationEventDispatcher()

    var snapshot by mutableStateOf<LockScreenFaceConfig?>(null)
    var descriptor by mutableStateOf<WatchFaceDescriptor?>(null)
    var faceVisible by mutableStateOf(false)

    init {
        this.setViewTreeLifecycleOwner(this)
        this.setViewTreeSavedStateRegistryOwner(this)
        savedStateRegistryController.performAttach()
    }

    /** 窗口 attach **之前**调用：进 CREATED 态，SavedStateRegistry 才可用。 */
    fun moveToCreated() {
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
    }

    /** 窗口 attach 之后调用，对应 Activity 的 onStart。 */
    fun startLifecycle() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
    }

    fun disposeLifecycle() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
    }

    @Composable
    override fun Content() {
        CompositionLocalProvider(
            LocalOnBackPressedDispatcherOwner provides this,
            LocalNavigationEventDispatcherOwner provides this,
        ) {
            val currentSnapshot = snapshot
            if (currentSnapshot != null) {
                LockScreenFaceContent(
                    snapshot = currentSnapshot,
                    descriptor = descriptor,
                    faceVisible = faceVisible,
                    onFaceLoadFailure = { descriptor = null },
                )
            }
        }
    }
}
