package com.fluly.launcher.lockscreen

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.app.KeyguardManager
import android.os.Bundle
import android.os.PowerManager
import android.view.WindowManager
import android.view.MotionEvent
import com.fluly.launcher.watchface.WatchFaceDescriptor
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.fluly.launcher.i18n.FlulyLocaleManager
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import android.util.Log

private const val UNLOCK_SELF_CHECK_DELAY_MS = 2_000L

/**
 * 系统锁屏之上的一层表盘。
 *
 * 与已删除的 AOD 的边界，只有一条，但必须守住：
 * **这里不调用 `setTurnScreenOn`，也不持有 `FLAG_KEEP_SCREEN_ON`。**
 * 屏幕该灭就灭、系统该锁屏就锁屏，本 Activity 只是「锁屏时最上面那张脸」。
 * 一旦加上 `setTurnScreenOn`，熄屏瞬间屏幕会被重新点亮，就退化成 AOD 了。
 *
 * 上滑或返回只移除本覆盖层，露出 Keyguard，绝不绕过系统认证。
 * 解锁完成广播（`ACTION_USER_PRESENT`）及 resume 后的一次自查负责清理残留覆盖层。
 */
class LockScreenWatchFaceActivity : ComponentActivity() {

    companion object {
        /**
         * 表盘 Activity 是否存活。FSI 触发器用它判断「通知发射后系统有没有真把
         * Activity 拉起来」——部分 ROM（Phh-Treble GSI 实测）会把 full-screen
         * intent 降级成普通 heads-up 而不拉起，触发器据此重试。
         */
        @Volatile var isAlive: Boolean = false
    }

    private var config by mutableStateOf<LockScreenFaceConfig?>(null)
    private var descriptor by mutableStateOf<WatchFaceDescriptor?>(null)
    private var faceVisible by mutableStateOf(false)
    private var resumed = false
    private var configurationJob: Job? = null

    private fun reloadConfiguration() {
        configurationJob?.cancel()
        configurationJob = lifecycleScope.launch {
            val snapshot = readLockScreenFaceConfig()
            val resolved = resolveLockScreenFaceDescriptor(this@LockScreenWatchFaceActivity, snapshot)
            descriptor = resolved
            config = snapshot
            Log.i("LockScreenWatchFace", "configuration loaded type=${resolved?.type}")
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        Log.i("LockScreenWatchFace", "overlay reused; refreshing configuration")
        reloadConfiguration()
    }
    private var touchDownX = 0f
    private var touchDownY = 0f
    private var singlePointerGesture = false

    private fun updateFaceVisibility() {
        faceVisible = isLockScreenFaceVisible(
            resumed,
            getSystemService(PowerManager::class.java)?.isInteractive == true,
        )
    }

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_SCREEN_OFF) faceVisible = false
            else if (intent?.action == Intent.ACTION_SCREEN_ON) updateFaceVisibility()
        }
    }

    // Handle at the Activity boundary so native WatchMaker/JB views cannot swallow the exit
    // gesture. Other touches are also withheld: custom-face actions must not launch apps pre-auth.
    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                touchDownX = event.x
                touchDownY = event.y
                singlePointerGesture = true
            }
            MotionEvent.ACTION_POINTER_DOWN, MotionEvent.ACTION_CANCEL -> singlePointerGesture = false
            MotionEvent.ACTION_UP -> {
                if (singlePointerGesture && isLockScreenExitSwipe(
                        event.x - touchDownX,
                        event.y - touchDownY,
                        48f * resources.displayMetrics.density,
                    )) {
                    // 上滑 = 请求解锁：requestDismissKeyguard 让 SystemUI 的认证弹层
                    // （PIN 键盘）盖在本表盘之上——它是系统窗口，层级永远在应用之上；
                    // 表盘保留为背景，认证成功后 keyguard 消失，USER_PRESENT 收掉本层。
                    // 安全锁屏走弹层，非安全锁屏会立即解锁（同样由 USER_PRESENT 收尾），
                    // keyguard 已不在（解锁广播被裁剪）时直接撤掉本层兜底。
                    // 弹层取消认证也不影响：表盘还在，可以再次上滑。
                    val keyguard = getSystemService(KeyguardManager::class.java)
                    if (keyguard?.isKeyguardLocked == true) {
                        keyguard.requestDismissKeyguard(this, object : KeyguardManager.KeyguardDismissCallback() {
                            override fun onDismissError() {
                                Log.w("LockScreenWatchFace", "keyguard dismiss error")
                                finish()
                            }

                            override fun onDismissSucceeded() {
                                // 解锁成功：直接回桌面；USER_PRESENT 若也到达则幂等。
                                Log.i("LockScreenWatchFace", "keyguard dismissed")
                                finish()
                            }

                            override fun onDismissCancelled() {
                                // 用户取消认证：表盘保留为背景，可以再次上滑。
                                Log.i("LockScreenWatchFace", "keyguard dismiss cancelled")
                            }
                        })
                        // USER_PRESENT 在部分 ROM 上缺席（装机实测），自查兜底一次。
                        window.decorView.postDelayed(unlockSelfCheck, UNLOCK_SELF_CHECK_DELAY_MS)
                    } else {
                        finish()
                    }
                }
                singlePointerGesture = false
            }
        }
        return true
    }

    private val userPresentReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_USER_PRESENT && !isFinishing) {
                finish()
            }
        }
    }

    /**
     * 解锁广播的兜底。厂商裁过 SystemUI 的 ROM 上 `ACTION_USER_PRESENT` 有几率不来，
     * 那时表盘会一直盖在桌面上。这里在每次 resume 后自查一次：屏幕亮着、且已经
     * 不在锁屏状态，就主动退出。
     *
     * 只查一次而不是轮询：正常路径由广播负责，这条只是补漏。
     */
    private val unlockSelfCheck = Runnable {
        val keyguard = getSystemService(KeyguardManager::class.java)
        val power = getSystemService(PowerManager::class.java)
        Log.i("LockScreenWatchFace", "self-check interactive=${power?.isInteractive} keyguard=${keyguard?.isKeyguardLocked}")
        if (power?.isInteractive == true && keyguard?.isKeyguardLocked == false && !isFinishing) {
            Log.i("LockScreenWatchFace", "overlay finish: self-check unlocked")
            finish()
        }
    }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(FlulyLocaleManager.applyLocale(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        isAlive = true

        // FSI 通知已把本 Activity 拉起，全部（含重试的多条）随之取消，不在通知栏残留。
        for (id in LOCK_SCREEN_FSI_NOTIFICATION_ID until LOCK_SCREEN_FSI_NOTIFICATION_ID + MAX_FSI_ATTEMPTS) {
            NotificationManagerCompat.from(this).cancel(id)
        }

        // 盖在 Keyguard 之上。刻意不调 setTurnScreenOn —— 见类注释。
        setShowWhenLocked(true)
        window.setFlags(
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        )

        // 注册在 onCreate/onDestroy，而不是 onResume/onPause：
        // 认证界面弹出时本 Activity 会 onPause，若那时反注册就收不到解锁广播，
        // 解锁后表盘会一直盖在桌面上。
        ContextCompat.registerReceiver(
            this,
            userPresentReceiver,
            IntentFilter(Intent.ACTION_USER_PRESENT),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        ContextCompat.registerReceiver(
            this,
            screenReceiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_SCREEN_ON)
            },
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        onBackPressedDispatcher.addCallback(this) { finish() }

        Log.i("LockScreenWatchFace", "overlay created")
        reloadConfiguration()

        setContent {
            val currentSnapshot = config
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

    override fun onResume() {
        super.onResume()
        resumed = true
        updateFaceVisibility()
        window.decorView.postDelayed(unlockSelfCheck, UNLOCK_SELF_CHECK_DELAY_MS)
    }

    override fun onPause() {
        resumed = false
        faceVisible = false
        window.decorView.removeCallbacks(unlockSelfCheck)
        super.onPause()
    }

    override fun onDestroy() {
        isAlive = false
        window.decorView.removeCallbacks(unlockSelfCheck)
        runCatching { unregisterReceiver(userPresentReceiver) }
        runCatching { unregisterReceiver(screenReceiver) }
        super.onDestroy()
    }
}
