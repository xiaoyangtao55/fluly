package com.flue.launcher.lockscreen

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.app.KeyguardManager
import android.os.Bundle
import android.os.PowerManager
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.flue.launcher.i18n.FlueLocaleManager
import com.flue.launcher.ui.home.WatchFaceLayer
import com.flue.launcher.ui.theme.ProvideGlobalUiScale
import com.flue.launcher.ui.theme.WatchLauncherTheme
import kotlinx.coroutines.launch

private const val UNLOCK_SELF_CHECK_DELAY_MS = 2_000L

/**
 * 系统锁屏之上的一层表盘。
 *
 * 与已删除的 AOD 的边界，只有一条，但必须守住：
 * **这里不调用 `setTurnScreenOn`，也不持有 `FLAG_KEEP_SCREEN_ON`。**
 * 屏幕该灭就灭、系统该锁屏就锁屏，本 Activity 只是「锁屏时最上面那张脸」。
 * 一旦加上 `setTurnScreenOn`，熄屏瞬间屏幕会被重新点亮，就退化成 AOD 了。
 *
 * 退出路径有三条，任意一条命中就自行 `finish()` 把桌面让出来：
 * 1. 解锁完成（`ACTION_USER_PRESENT`）——手势上滑或 PIN 认证之后
 * 2. 返回键——三键导航设备上用户想退回 Keyguard 看通知
 * 3. 解锁后系统回收 Activity
 */
class LockScreenWatchFaceActivity : ComponentActivity() {

    private var config by mutableStateOf<LockScreenFaceConfig?>(null)

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
        if (power?.isInteractive == true && keyguard?.isKeyguardLocked == false && !isFinishing) {
            finish()
        }
    }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(FlueLocaleManager.applyLocale(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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
        onBackPressedDispatcher.addCallback(this) { finish() }

        lifecycleScope.launch {
            config = readLockScreenFaceConfig()
        }

        setContent {
            val snapshot = config
            if (snapshot != null) {
                WatchLauncherTheme(themeMode = snapshot.themeMode, uiStyle = snapshot.uiStyle) {
                    ProvideGlobalUiScale(snapshot.globalUiScalePercent) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black),
                        ) {
                            WatchFaceLayer(
                                watchFaceId = snapshot.watchFaceId,
                                photoPath = snapshot.photoPath,
                                videoPath = snapshot.videoPath,
                                isFaceVisible = true,
                                uiStyle = snapshot.uiStyle,
                                photoOptions = snapshot.photoOptions,
                                videoOptions = snapshot.videoOptions,
                                // 锁屏在认证之前：通知角标、状态指示都不该出现。
                                showStatusIndicators = false,
                                showBottomFade = false,
                                hasNotifications = false,
                                // 锁屏上不接长按/双击：那两个动作属于桌面。
                                onLongPress = null,
                                onDoubleTap = null,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        window.decorView.postDelayed(unlockSelfCheck, UNLOCK_SELF_CHECK_DELAY_MS)
    }

    override fun onPause() {
        window.decorView.removeCallbacks(unlockSelfCheck)
        super.onPause()
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(userPresentReceiver) }
        super.onDestroy()
    }
}
