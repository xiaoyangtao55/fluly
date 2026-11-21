package com.flue.launcher.lockscreen

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import androidx.core.content.ContextCompat

private const val LOG_TAG = "LockScreenWatchFace"

/**
 * 熄屏瞬间把 [LockScreenWatchFaceActivity] 放到任务栈顶。
 *
 * 时机很关键：`ACTION_SCREEN_OFF` 派发时本进程仍持有可见的前台 Activity，
 * 属于「前台启动」，不触发 Android 10+ 的后台启动拦截，因此不需要悬浮窗权限。
 * 一旦改成在亮屏后才拉起，就从「前台启动」变成「后台启动」，那条路要多一套
 * `SYSTEM_ALERT_WINDOW` 引导 —— 这里先不走。
 *
 * 已知边界：只在**从启动器熄屏**时生效。用户在其他 App 里熄屏时本进程收不到广播，
 * 那种场景要等后台启动权限落地。
 */
fun startLockScreenWatchFace(context: Context) {
    val intent = Intent(context, LockScreenWatchFaceActivity::class.java)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }
        .onFailure { Log.w(LOG_TAG, "无法拉起锁屏表盘", it) }
}

/**
 * 返回该 receiver，调用方负责在销毁时反注册。
 *
 * 注册点必须长于 `onPause`：熄屏时 Activity 先 pause 再派发 `ACTION_SCREEN_OFF`，
 * 在 `onPause` 里反注册就永远收不到这条广播。
 *
 * [isEnabled] 每次熄屏时求值，而不是注册时求值 —— 用户在设置里关掉开关后
 * 不需要重启 Activity 就能生效。
 */
fun screenOffReceiverFor(context: Context, isEnabled: () -> Boolean): BroadcastReceiver =
    object : BroadcastReceiver() {
        override fun onReceive(receiverContext: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_SCREEN_OFF && isEnabled()) {
                startLockScreenWatchFace(context)
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
