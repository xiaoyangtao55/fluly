package com.flue.launcher.lockscreen

import java.io.File
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
        assertTrue(activity.contains("allowPermissionRequests = false"))
        val runtime = File(root, "watchface-runtime/src/main/kotlin/com/flue/launcher/watchface/standard/WatchmakerWatchFaceView.kt").readText()
        assertTrue(runtime.contains("view.setPermissionRequestsAllowed(allowPermissionRequests)"))
        assertTrue(runtime.contains("if (!permissionRequestsAllowed || prepared == null"))
    }
}
