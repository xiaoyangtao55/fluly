package com.fluly.launcher

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 「应用请求把快捷方式加到桌面」的两条入口。
 *
 * 这里钉住的每一条都属于「删掉不报错、功能静默失效」，编译器和类型系统都兜不住：
 *
 * - manifest 少一行 `INSTALL_SHORTCUT` 权限，旧路径的广播会被系统在投递阶段跳过 ——
 *   receiver 写着、`exported` 也开着，就是收不到，而且没有任何日志；
 * - 不检查 `PinItemRequest.isValid`，过期请求会被当成「添加失败」报给用户；
 * - 先落库再 accept，accept 失败时就留下一个点不开的条目；
 * - legacy 路径用 `orEmpty()` 兜空包名，会落一个没图标、没归属的空条目。
 */
class ShortcutInstallContractTest {
    private val root = sequenceOf(
        File("src/main/kotlin"),
        File("app/src/main/kotlin"),
        File("../app/src/main/kotlin"),
    ).first(File::isDirectory)

    private val manifest by lazy {
        sequenceOf(
            File("src/main/AndroidManifest.xml"),
            File("app/src/main/AndroidManifest.xml"),
            File("../app/src/main/AndroidManifest.xml"),
        ).firstOrNull(File::isFile)?.readText() ?: error("找不到 AndroidManifest.xml")
    }

    private val handler by lazy {
        File(root, "com/example/wlauncher/ShortcutReceiverActivity.kt").readText()
    }

    private val repository by lazy {
        File(root, "com/example/wlauncher/data/repository/AppRepository.kt").readText()
    }

    @Test
    fun manifestDeclaresInstallShortcutPermission() {
        assertTrue(
            "缺少 com.android.launcher.permission.INSTALL_SHORTCUT：" +
                "带权限投递的旧式广播会被系统直接跳过，receiver 永远收不到",
            manifest.contains(
                "<uses-permission android:name=\"com.android.launcher.permission.INSTALL_SHORTCUT\" />"
            ),
        )
    }

    @Test
    fun legacyReceiverStaysExported() {
        val index = manifest.indexOf("ShortcutInstallReceiver")
        assertTrue("manifest 没有声明 ShortcutInstallReceiver", index >= 0)
        val block = manifest.substring(maxOf(0, index - 300), minOf(manifest.length, index + 300))
        assertTrue(
            "ShortcutInstallReceiver 不 exported 就收不到任何应用发来的 INSTALL_SHORTCUT",
            block.contains("android:exported=\"true\""),
        )
    }

    @Test
    fun expiredPinRequestIsRejectedBeforeAccepting() {
        assertTrue("缺少 request.isValid 检查", handler.contains("request.isValid"))
        val validIndex = handler.indexOf("request.isValid")
        val acceptIndex = handler.indexOf("request.accept()")
        assertTrue("isValid 必须在使用请求之前", validIndex in 0 until acceptIndex)
    }

    @Test
    fun pinRequestIsAcceptedBeforePersisting() {
        val acceptIndex = handler.indexOf("request.accept()")
        val persistIndex = handler.indexOf("addPinnedShortcut(shortcutInfo)")
        assertTrue("accept 必须先于落库，否则 accept 失败会留下点不开的条目", acceptIndex in 0 until persistIndex)
    }

    @Test
    fun modernPinCannotUseAutomaticLegacyDispatcher() {
        val dispatch = handler.substringAfter("fun handle(context:").substringBefore("fun readPinRequest")
        assertTrue("自动 dispatcher 必须保留旧广播", dispatch.contains("ACTION_INSTALL_SHORTCUT ->"))
        assertTrue("现代 pin 不能自动 accept", !dispatch.contains("ACTION_CONFIRM_PIN_SHORTCUT ->"))
    }

    @Test
    fun confirmationAndCancellationAreWiredToDialog() {
        val activity = handler.substringBefore("internal class ShortcutPinConfirmation")
        val positive = activity.substringAfter(".setPositiveButton(").substringBefore(".setNegativeButton(")
        assertTrue("仅明确添加按钮才确认", positive.contains("confirmation.confirm()"))
        assertTrue("旧回调不能确认新请求", positive.contains("pendingConfirmation === confirmation"))
        val negative = activity.substringAfter(".setNegativeButton(").substringBefore(".show()")
        assertTrue("取消按钮必须退出且不安装", negative.contains("cancelConfirmation(confirmation)"))
        assertTrue("返回键/点外部取消也必须退出", negative.contains(".setOnCancelListener"))
        assertTrue("取消处理不能触发确认", !negative.contains(".confirm()"))
    }

    @Test
    fun pinIsRevalidatedWhenUserConfirms() {
        val confirm = handler.substringAfter("fun confirmPinnedShortcut(").substringBefore("private fun handleLegacyShortcut")
        assertTrue(
            "用户等待期间请求可能过期，确认时必须重新验证",
            confirm.indexOf("isValidShortcutRequest(request)") in 0 until confirm.indexOf("request.accept()"),
        )
    }

    @Test
    fun activityReplacesPendingRequestAndReleasesItOnDestroy() {
        val newIntent = handler.substringAfter("override fun onNewIntent(").substringBefore("private fun showRequest")
        assertTrue(newIntent.contains("clearConfirmation()"))
        assertTrue(newIntent.indexOf("clearConfirmation()") < newIntent.indexOf("setIntent(intent)"))
        assertTrue(newIntent.contains("showRequest(intent)"))
        val destroy = handler.substringAfter("override fun onDestroy()").substringBefore("/** Memory-only")
        assertTrue(destroy.contains("clearConfirmation()"))
        val clear = handler.substringAfter("private fun clearConfirmation()").substringBefore("override fun onDestroy()")
        assertTrue(clear.contains("pendingConfirmation?.cancel()"))
        assertTrue(clear.contains("confirmationDialog?.dismiss()"))
        assertTrue("不把外部请求保存进 instance state", !handler.contains("onSaveInstanceState"))
    }

    @Test
    fun confirmationActivityHasDisplayableTheme() {
        val activity = manifest.substringAfter("android:name=\".ShortcutReceiverActivity\"")
            .substringBefore("</activity>")
        assertTrue(activity.contains("android:theme=\"@style/Theme.WatchLauncher\""))
        val themes = File(root.parentFile, "res/values/themes.xml").readText()
        assertTrue("确认页不能使用 NoDisplay 主题", !themes.contains("NoDisplay"))
        assertTrue(themes.contains("android:Theme.Material.NoActionBar"))
    }

    @Test
    fun legacyShortcutRejectsUnresolvablePackage() {
        val body = repository.substringAfter("fun addLegacyShortcut")
            .substringBefore("fun ", missingDelimiterValue = "")
        assertTrue("addLegacyShortcut 找不到", body.isNotBlank())
        assertTrue(
            "包名解析不出来时必须拒绝，不能用 orEmpty() 放行空包名",
            body.contains("?: return false") && !body.contains("orEmpty()"),
        )
    }

    @Test
    fun externalShortcutIntentStripsGrantFlags() {
        val body = repository.substringAfter("if (appInfo.isAppListShortcut)")
            .substringBefore("if (appInfo.isBuiltInSettingsEntry)")
        assertTrue(
            "外部应用给的快捷方式 URI 会带着 FLAG_GRANT_*，启动前必须清掉",
            body.contains("FLAG_GRANT_READ_URI_PERMISSION") &&
                body.contains("FLAG_GRANT_WRITE_URI_PERMISSION") &&
                body.contains("removeFlags"),
        )
    }
}
