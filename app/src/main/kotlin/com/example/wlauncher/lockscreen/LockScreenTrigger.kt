package com.flue.launcher.lockscreen

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.flue.launcher.R

private const val LOG_TAG = "LockScreenWatchFace"

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
 * Activity 在 onCreate 里把这条通知取消掉。
 */
internal fun notifyLockScreenFace(context: Context) {
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
    runCatching { NotificationManagerCompat.from(context).notify(LOCK_SCREEN_FSI_NOTIFICATION_ID, notification) }
        .onFailure { Log.w(LOG_TAG, "全屏意图通知发送失败", it) }
    Log.i(LOG_TAG, "full-screen intent notification posted")
}

/** FSI 路径是否可用：通知能发且全屏意图权限有效（Android 12 上安装即授）。 */
internal fun canUseLockScreenFullScreenIntent(context: Context): Boolean =
    NotificationManagerCompat.from(context).areNotificationsEnabled() &&
        NotificationManagerCompat.from(context).canUseFullScreenIntent()

/**
 * Keep registered until destroy: screen-off can pause/stop HOME before delivering the broadcast.
 * Both eligibility and the preference are evaluated per event; registration is not foreground proof.
 */
fun screenOffReceiverFor(
    context: Context,
    foregroundGate: LockScreenForegroundGate,
    isEnabled: () -> Boolean,
): BroadcastReceiver = object : BroadcastReceiver() {
    override fun onReceive(receiverContext: Context?, intent: Intent?) {
        if (intent?.action != Intent.ACTION_SCREEN_OFF) return
        val gateEligible = foregroundGate.consumeScreenOff()
        val enabled = isEnabled()
        val fsiUsable = canUseLockScreenFullScreenIntent(context)
        val overlayPermitted = LockScreenFaceOverlay.canShow(context)
        val action = lockScreenAction(fsiUsable, overlayPermitted, enabled, gateEligible)
        Log.i(
            LOG_TAG,
            "SCREEN_OFF gate=$gateEligible enabled=$enabled fsi=$fsiUsable " +
                "overlay=$overlayPermitted action=$action",
        )
        when (action) {
            LockScreenAction.NOTIFY_FULL_SCREEN -> notifyLockScreenFace(context)
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

fun registerScreenOffReceiver(context: Context, receiver: BroadcastReceiver) {
    ContextCompat.registerReceiver(
        context,
        receiver,
        IntentFilter(Intent.ACTION_SCREEN_OFF),
        ContextCompat.RECEIVER_NOT_EXPORTED,
    )
}
