package com.fluly.launcher.lockscreen

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.fluly.launcher.R

private const val LOG_TAG = "LockScreenWatchFace"

/** 熄屏后延迟贴第一条 FSI 通知：避开熄屏过渡态，别和系统的 interactive 翻转抢跑。 */
private const val FSI_POST_DELAY_MS = 400L

/** 表盘 Activity 没被拉起时的重试间隔。 */
private const val FSI_RETRY_DELAY_MS = 2_000L

/** FSI 最多贴几次（3 次共约 6s，覆盖解锁前的观察窗口）。 */
internal const val MAX_FSI_ATTEMPTS = 3

/**
 * Tracks the launcher, not the process: a registered receiver also hears broadcasts in other apps.
 * A screen-off pause must retain the resume token until SCREEN_OFF consumes it. A normal,
 * interactive pause (opening another app, recents, or settings) immediately revokes that token.
 */
class LockScreenForegroundGate {
    private var eligible = false

    fun onResume() { eligible = true }

    fun onPause(isInteractive: Boolean) {
        if (isInteractive) eligible = false
    }

    fun onStop(isInteractive: Boolean) {
        if (isInteractive) eligible = false
    }

    fun consumeScreenOff(): Boolean = eligible.also { eligible = false }
}

/** Best effort only: OEM background-activity restrictions can still reject this launch. */
fun startLockScreenWatchFace(context: Context) {
    val intent = Intent(context, LockScreenWatchFaceActivity::class.java)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    Log.i(LOG_TAG, "request overlay launch")
    runCatching { context.startActivity(intent) }
        .onFailure { Log.w(LOG_TAG, "无法拉起锁屏表盘", it) }
}

/** SCREEN_OFF 时锁屏表盘走哪条路径。 */
internal enum class LockScreenAction { NOTIFY_FULL_SCREEN, SHOW_OVERLAY, START_ACTIVITY, NONE }

/**
 * 路径决策（纯函数，便于单测）。
 *
 * - **全屏意图通知优先**：安全锁屏期间系统会强制隐藏第三方悬浮窗
 *   （`mForceHideNonSystemOverlayWindow`，装机实测），悬浮窗压不到 Keyguard 上。
 *   full-screen intent 是官方许可的后台拉起机制（来电全屏同款），锁屏 Activity
 *   自带 `setShowWhenLocked`，任何前台状态下都能生效，也不需要门禁。
 * - FSI 不可用（通知权限关闭等）而悬浮窗权限在手时走 overlay：不经过
 *   startActivity，不受后台启动限制约束，同样不依赖门禁——但在安全锁屏的设备上
 *   实际会被系统压住，这是降级路径。
 * - 两者都不可用时维持原状：门禁放行（HOME 是最后前台应用）才拉起锁屏 Activity，
 *   零权限基线行为不变。
 */
internal fun lockScreenAction(
    fullScreenIntentUsable: Boolean,
    overlayPermitted: Boolean,
    enabled: Boolean,
    gateEligible: Boolean,
): LockScreenAction = when {
    !enabled -> LockScreenAction.NONE
    fullScreenIntentUsable -> LockScreenAction.NOTIFY_FULL_SCREEN
    overlayPermitted -> LockScreenAction.SHOW_OVERLAY
    gateEligible -> LockScreenAction.START_ACTIVITY
    else -> LockScreenAction.NONE
}

private const val FSI_CHANNEL_ID = "lock_screen_face_fsi"
internal const val LOCK_SCREEN_FSI_NOTIFICATION_ID = 2

/**
 * 用全屏意图通知从后台拉起锁屏表盘 Activity。
 *
 * 通知通道必须 IMPORTANCE_HIGH（低于它 full-screen intent 不会立即弹出），
 * 但要无声无振动无灯光——屏幕正在熄灭，表盘只该静静等在 Keyguard 后面。
 * Activity 在 onCreate 里把发出的通知（含重试的多条）全部取消掉。
 *
 * [notificationId] 每次重试都要换新 ID：同 ID 的 notify() 是「更新已有通知」，
 * 系统不会重新做「要不要立即拉起 FSI」的判定——重试就白贴了（装机实测）。
 */
internal fun notifyLockScreenFace(context: Context, notificationId: Int) {
    val manager = context.getSystemService(NotificationManager::class.java)
    manager?.createNotificationChannel(
        NotificationChannel(
            FSI_CHANNEL_ID,
            context.getString(R.string.lock_screen_overlay_channel),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            setSound(null, null)
            enableVibration(false)
            enableLights(false)
        },
    )
    val fullScreenIntent = Intent(context, LockScreenWatchFaceActivity::class.java)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    val notification = NotificationCompat.Builder(context, FSI_CHANNEL_ID)
        .setSmallIcon(R.mipmap.ic_launcher)
        .setContentTitle(context.getString(R.string.app_name))
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setCategory(NotificationCompat.CATEGORY_CALL)
        .setVisibility(NotificationCompat.VISIBILITY_SECRET)
        .setFullScreenIntent(
            android.app.PendingIntent.getActivity(
                context,
                0,
                fullScreenIntent,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE,
            ),
            true,
        )
        .build()
    runCatching { NotificationManagerCompat.from(context).notify(notificationId, notification) }
        .onFailure { Log.w(LOG_TAG, "全屏意图通知发送失败", it) }
    Log.i(LOG_TAG, "full-screen intent notification posted (id=$notificationId)")
}

/** FSI 路径是否可用：通知能发且全屏意图权限有效（Android 12 上安装即授）。 */
internal fun canUseLockScreenFullScreenIntent(context: Context): Boolean =
    NotificationManagerCompat.from(context).areNotificationsEnabled() &&
        NotificationManagerCompat.from(context).canUseFullScreenIntent()

/**
 * 进程级锁屏表盘触发器：SCREEN_OFF 广播注册在 applicationContext 上，随进程存活。
 *
 * 为什么不挂在 LauncherActivity 上（装机实测）：FSI 把锁屏表盘 Activity 从后台
 * 拉起后，后台的 HOME Activity 会被系统随手销毁——它一销毁，挂在它身上的
 * receiver 就在 onDestroy 里被反注册，之后的熄屏再也无人响应，表盘不再出现
 * （侧滑返回 → 解锁 → 再锁屏的复现路径）。注册挪到 application context 后，
 * receiver 随进程存活，Activity 的生死不再影响触发。
 *
 * 开关状态（[featureEnabled]）由 LauncherViewModel 加载/设置时同步进来；
 * 门禁（[onLauncherResumed] 等）由 LauncherActivity 生命周期同步，只服务
 * FSI/overlay 都不可用的降级路径。
 */
object LockScreenFaceTrigger {
    @Volatile var featureEnabled: Boolean = true

    private val gate = LockScreenForegroundGate()
    private var registered = false
    private var appContext: Context? = null

    /**
     * 在 LauncherActivity.onCreate 里调用一次。注册永不反注册（随进程存活）；
     * 进程被 LMK 杀掉后的重启由下一次 LauncherActivity.onCreate 重新武装。
     */
    fun ensureRegistered(context: Context) {
        if (registered) return
        val app = context.applicationContext
        appContext = app
        registered = true
        ContextCompat.registerReceiver(
            app,
            receiver,
            IntentFilter(Intent.ACTION_SCREEN_OFF),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        Log.i(LOG_TAG, "screen-off trigger registered")
    }

    fun onLauncherResumed() { gate.onResume() }
    fun onLauncherPaused(isInteractive: Boolean) { gate.onPause(isInteractive) }
    fun onLauncherStopped(isInteractive: Boolean) { gate.onStop(isInteractive) }

    private val handler = Handler(Looper.getMainLooper())
    private var pendingFullScreenIntent: Runnable? = null

    /**
     * FSI 发射带重试。部分 ROM（Phh-Treble GSI 实测）会把 full-screen intent
     * 降级成普通 heads-up 而不拉起 Activity——跟熄屏过渡期的系统状态竞态有关，
     * 同样的通知有的周期发射、有的周期不发射。所以不赌那一次：延迟 400ms 贴
     * 第一条（避开熄屏过渡态），之后每 2s 检查一次表盘 Activity 是否被拉起，
     * 没起来就再贴，最多 3 次。Activity 起来了（isAlive）、屏幕亮了或开关关了
     * 都会停止重试。
     */
    private fun scheduleFullScreenIntent(context: Context) {
        cancelFullScreenIntentRetry()
        var attempt = 0
        val retry = object : Runnable {
            override fun run() {
                if (!featureEnabled) return
                if (LockScreenWatchFaceActivity.isAlive) return
                val power = context.getSystemService(PowerManager::class.java)
                if (power?.isInteractive == true) return
                attempt++
                val notificationId = LOCK_SCREEN_FSI_NOTIFICATION_ID + attempt - 1
                Log.i(LOG_TAG, "posting full-screen intent (attempt $attempt/$MAX_FSI_ATTEMPTS id=$notificationId)")
                if (attempt > 1) {
                    // 撤旧条再插新条：新 ID 的插入才会让系统重新判定 FSI。
                    NotificationManagerCompat.from(context).cancel(notificationId - 1)
                }
                notifyLockScreenFace(context, notificationId)
                if (attempt < MAX_FSI_ATTEMPTS) handler.postDelayed(this, FSI_RETRY_DELAY_MS)
            }
        }
        pendingFullScreenIntent = retry
        handler.postDelayed(retry, FSI_POST_DELAY_MS)
    }

    private fun cancelFullScreenIntentRetry() {
        pendingFullScreenIntent?.let(handler::removeCallbacks)
        pendingFullScreenIntent = null
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(receiverContext: Context?, intent: Intent?) {
            if (intent?.action != Intent.ACTION_SCREEN_OFF) return
            val context = appContext ?: return
            val gateEligible = gate.consumeScreenOff()
            if (!featureEnabled) return
            val fsiUsable = canUseLockScreenFullScreenIntent(context)
            val overlayPermitted = LockScreenFaceOverlay.canShow(context)
            val action = lockScreenAction(fsiUsable, overlayPermitted, true, gateEligible)
            Log.i(
                LOG_TAG,
                "SCREEN_OFF gate=$gateEligible fsi=$fsiUsable " +
                    "overlay=$overlayPermitted action=$action",
            )
            when (action) {
                LockScreenAction.NOTIFY_FULL_SCREEN -> scheduleFullScreenIntent(context)
                LockScreenAction.SHOW_OVERLAY ->
                    // 添加失败（OEM 拦截 addView 等）回退 Activity 路径；回退只在令牌
                    // 原本合格时有效——没有权限的用户行为与历史版本完全一致。
                    if (!LockScreenFaceOverlay.show(context) && gateEligible) {
                        startLockScreenWatchFace(context)
                    }
                LockScreenAction.START_ACTIVITY -> startLockScreenWatchFace(context)
                LockScreenAction.NONE -> Unit
            }
        }
    }
}
