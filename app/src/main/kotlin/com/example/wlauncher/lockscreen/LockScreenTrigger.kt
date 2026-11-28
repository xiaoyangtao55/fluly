package com.flue.launcher.lockscreen

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import androidx.core.content.ContextCompat

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
internal enum class LockScreenAction { SHOW_OVERLAY, START_ACTIVITY, NONE }

/**
 * 路径决策（纯函数，便于单测）。
 *
 * - 悬浮窗权限在手时一律走 overlay：它不经过 startActivity，不受后台启动
 *   限制约束，也就**不依赖门禁**——从其他 App 里熄屏同样生效。
 * - 没有权限时维持原状：门禁放行（HOME 是最后前台应用）才拉起锁屏 Activity。
 * - 门禁令牌只在未授权路径上有意义；悬浮窗路径不消费它，避免把
 *   「下次熄屏的资格」提前烧掉。
 */
internal fun lockScreenAction(
    overlayPermitted: Boolean,
    enabled: Boolean,
    gateEligible: Boolean,
): LockScreenAction = when {
    !enabled -> LockScreenAction.NONE
    overlayPermitted -> LockScreenAction.SHOW_OVERLAY
    gateEligible -> LockScreenAction.START_ACTIVITY
    else -> LockScreenAction.NONE
}

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
        val overlayPermitted = LockScreenFaceOverlay.canShow(context)
        val action = lockScreenAction(overlayPermitted, enabled, gateEligible)
        Log.i(
            LOG_TAG,
            "SCREEN_OFF gate=$gateEligible enabled=$enabled overlay=$overlayPermitted action=$action",
        )
        when (action) {
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
