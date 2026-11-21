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
    runCatching { context.startActivity(intent) }
        .onFailure { Log.w(LOG_TAG, "无法拉起锁屏表盘", it) }
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
        val fromLauncher = foregroundGate.consumeScreenOff()
        if (fromLauncher && isEnabled()) startLockScreenWatchFace(context)
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
