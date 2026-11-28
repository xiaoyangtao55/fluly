package com.flue.launcher.lockscreen

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.flue.launcher.R

/**
 * 锁屏表盘悬浮窗的保活前台服务。
 *
 * 持有悬浮窗窗口的进程只是「缓存」优先级，LMK 随时可以收割——收割时窗口被
 * 系统一并撤走，锁屏上表盘就消失了（装机实测：HOME 进程在熄屏后一秒内被杀，
 * pid 不断轮换）。FGS 把进程抬到前台服务优先级，锁屏期间不会被 LMK 动。
 *
 * - 启动：悬浮窗 addView 之前（[LockScreenFaceOverlay.show]），优先级先抬高再挂窗口；
 *   从后台 startForegroundService 依赖的正是已授予的 `SYSTEM_ALERT_WINDOW` 豁免。
 * - 停止：解锁移除悬浮窗时（[LockScreenFaceOverlay.hide]），不留常驻通知。
 * - 通知：`IMPORTANCE_MIN` 无声无横幅，`VISIBILITY_SECRET` 在锁屏上不可见——
 *   我们的层本来就是「锁屏上最上面那张脸」，不能让系统通知抢戏。
 * - 进程被杀后靠 `START_REDELIVER_INTENT` 拉起：熄屏状态下把悬浮窗补回来。
 */
class LockScreenFaceOverlayService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 两参 startForeground：API 34+ 用 manifest 声明的 specialUse 类型，
        // 更旧的版本无类型校验，不需要运行时传 type（见 manifest property）。
        startForeground(NOTIFICATION_ID, buildNotification())
        if (intent?.action == ACTION_ACQUIRE &&
            getSystemService(PowerManager::class.java)?.isInteractive == false
        ) {
            LockScreenFaceOverlay.show(this)
        }
        return START_REDELIVER_INTENT
    }

    private fun buildNotification(): Notification {
        getSystemService(NotificationManager::class.java)?.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.lock_screen_overlay_channel),
                NotificationManager.IMPORTANCE_MIN,
            ),
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(getString(R.string.app_name))
            .setVisibility(NotificationCompat.VISIBILITY_SECRET)
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "lock_screen_face_overlay"
        private const val NOTIFICATION_ID = 1
        private const val ACTION_ACQUIRE = "com.flue.launcher.lockscreen.OVERLAY_ACQUIRE"

        /** 悬浮窗 addView 之前调用：进程先抬到前台服务优先级。 */
        fun acquire(context: Context) {
            context.startForegroundService(
                Intent(context, LockScreenFaceOverlayService::class.java)
                    .setAction(ACTION_ACQUIRE),
            )
        }

        /** 悬浮窗移除后调用，不留常驻通知。 */
        fun release(context: Context) {
            context.stopService(Intent(context, LockScreenFaceOverlayService::class.java))
        }
    }
}
