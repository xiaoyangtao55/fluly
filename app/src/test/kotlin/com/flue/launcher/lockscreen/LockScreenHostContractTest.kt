package com.flue.launcher.lockscreen

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LockScreenHostContractTest {
    private val root = generateSequence(File(".").canonicalFile) { it.parentFile }
        .first { File(it, "settings.gradle.kts").isFile }

    @Test fun reusedOverlayReloadsAndCannotLaunchPermissionPrompts() {
        val activity = File(root, "app/src/main/kotlin/com/example/wlauncher/lockscreen/LockScreenWatchFaceActivity.kt").readText()
        val reuse = activity.substringAfter("override fun onNewIntent").substringBefore("private var touchDownX")
        assertTrue(reuse.contains("setIntent(intent)"))
        assertTrue(reuse.contains("reloadConfiguration()"))
        assertTrue(activity.contains("configurationJob?.cancel()"))
        // 渲染主体抽到了共享组合里，锁屏的权限请求守卫跟着搬过去（Activity 与悬浮窗共用）。
        val content = File(root, "app/src/main/kotlin/com/example/wlauncher/lockscreen/LockScreenFaceContent.kt").readText()
        assertTrue(content.contains("allowPermissionRequests = false"))
        assertTrue(activity.contains("LockScreenFaceContent("))
        val runtime = File(root, "watchface-runtime/src/main/kotlin/com/flue/launcher/watchface/standard/WatchmakerWatchFaceView.kt").readText()
        assertTrue(runtime.contains("view.setPermissionRequestsAllowed(allowPermissionRequests)"))
        assertTrue(runtime.contains("if (!permissionRequestsAllowed || prepared == null"))
    }

    @Test fun overlayWindowStaysDisplayOnlyAndDisposesItsComposition() {
        // 注释里写着 AOD 红线的原文，先剥离注释再断言「无实际使用」。
        val overlay = stripComments(
            File(root, "app/src/main/kotlin/com/example/wlauncher/lockscreen/LockScreenFaceOverlay.kt").readText(),
        )
        // 纯显示层：触摸穿透给 Keyguard，认证前这一层收不到任何输入。
        assertTrue(overlay.contains("FLAG_NOT_TOUCHABLE"))
        assertTrue(overlay.contains("TYPE_APPLICATION_OVERLAY"))
        assertTrue(overlay.contains("canDrawOverlays"))
        // AOD 红线：不得申请点亮屏幕或保持常亮。
        assertFalse(overlay.contains("FLAG_KEEP_SCREEN_ON"))
        assertFalse(overlay.contains("setTurnScreenOn"))
        // 移除前必须走完生命周期，Compose 的 Recomposer 才会随之销毁。
        assertTrue(overlay.contains("handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)"))
        assertTrue(overlay.contains("ACTION_USER_PRESENT"))
        // 持窗进程只有缓存优先级，LMK 会秒杀并带走窗口——保活 FGS 必须先于 addView。
        assertTrue(overlay.contains("LockScreenFaceOverlayService.acquire(appContext)"))
        assertTrue(overlay.contains("LockScreenFaceOverlayService.release(appContext)"))
        val manifest = File(root, "app/src/main/AndroidManifest.xml").readText()
        assertTrue(manifest.contains("SYSTEM_ALERT_WINDOW"))
        assertTrue(manifest.contains("FOREGROUND_SERVICE_SPECIAL_USE"))
        assertTrue(manifest.contains("PROPERTY_SPECIAL_USE_FGS_SUBTYPE"))
    }

    @Test fun lockScreenFaceUsesFullScreenIntentAsPrimaryPath() {
        // 安全锁屏期间第三方悬浮窗被系统强制隐藏，FSI 通知拉起 setShowWhenLocked
        // 的 Activity 是唯一能盖在 Keyguard 上的官方途径。
        val manifest = File(root, "app/src/main/AndroidManifest.xml").readText()
        assertTrue(manifest.contains("USE_FULL_SCREEN_INTENT"))
        val trigger = File(root, "app/src/main/kotlin/com/example/wlauncher/lockscreen/LockScreenTrigger.kt").readText()
        assertTrue(trigger.contains("setFullScreenIntent"))
        // FSI 只在 IMPORTANCE_HIGH 通道下立即弹出，但必须无声无振动。
        assertTrue(trigger.contains("IMPORTANCE_HIGH"))
        assertTrue(trigger.contains("setSound(null, null)"))
        assertTrue(trigger.contains("enableVibration(false)"))
        assertTrue(trigger.contains("NOTIFY_FULL_SCREEN"))
        val activity = File(root, "app/src/main/kotlin/com/example/wlauncher/lockscreen/LockScreenWatchFaceActivity.kt").readText()
        assertTrue(activity.contains("LOCK_SCREEN_FSI_NOTIFICATION_ID"))
    }

    /** 剥掉块注释与行注释（够用于源码契约检查，不追求词法级精确）。 */
    private fun stripComments(source: String): String = source
        .replace(Regex("/\\*[\\s\\S]*?\\*/"), "")
        .replace(Regex("//.*"), "")
}
