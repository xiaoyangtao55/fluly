package com.fluly.launcher

import android.app.Activity
import android.app.AlertDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.os.Parcelable
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.drawable.toBitmap

/**
 * 两条「添加到桌面」入口的落地端，装的是同一套处理逻辑：
 *
 * - [ShortcutReceiverActivity]：现代路径。应用调 `ShortcutManager.requestPinShortcut()`
 *   后，系统把 `CONFIRM_PIN_SHORTCUT` 发给**当前默认启动器**，用户确认后才 accept。
 * - [ShortcutInstallReceiver]：旧路径。应用直接发 `INSTALL_SHORTCUT` 广播，Android 8.0
 *   之前的通行做法，至今仍有大量应用（尤其是国内工具类）在用。
 *
 * 两条都需要「本应用是默认桌面」才算完整可用：PIN 请求只发给默认启动器；
 * 旧广播虽然不校验，但应用通常也会先检查 `resolveActivity(HOME)`。
 */
class ShortcutReceiverActivity : Activity() {
    private var confirmationDialog: AlertDialog? = null
    private var pendingConfirmation: ShortcutPinConfirmation? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showRequest(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        clearConfirmation()
        setIntent(intent)
        showRequest(intent)
    }

    private fun showRequest(sourceIntent: Intent?) {
        // Legacy activity callers retain the same policy as the legacy broadcast receiver.
        if (sourceIntent?.action != LauncherApps.ACTION_CONFIRM_PIN_SHORTCUT) {
            showResult(runCatching { ShortcutInstallHandler.handle(this, sourceIntent) }
                .getOrDefault(ShortcutInstallResult.FAILED))
            return
        }
        val request = runCatching { ShortcutInstallHandler.readPinRequest(sourceIntent) }.getOrNull()
        if (request == null) {
            showResult(ShortcutInstallResult.INVALID)
            return
        }
        val confirmation = ShortcutPinConfirmation {
            ShortcutInstallHandler.confirmPinnedShortcut(this, request)
        }
        pendingConfirmation = confirmation
        // Do not display or save external labels, intents, extras or request tokens.
        confirmationDialog = AlertDialog.Builder(this)
            .setTitle(R.string.shortcut_pin_confirmation_title)
            .setMessage(R.string.shortcut_pin_confirmation_message)
            .setPositiveButton(R.string.shortcut_pin_confirmation_add) { _, _ ->
                // Old dialog callbacks cannot approve a replacement onNewIntent request.
                if (pendingConfirmation === confirmation) {
                    confirmation.confirm()?.let(::showResult)
                }
            }
            .setNegativeButton(R.string.shortcut_pin_confirmation_cancel) { _, _ ->
                cancelConfirmation(confirmation)
            }
            .setOnCancelListener { cancelConfirmation(confirmation) }
            .show()
    }

    private fun cancelConfirmation(confirmation: ShortcutPinConfirmation) {
        if (pendingConfirmation !== confirmation) return
        clearConfirmation()
        finish()
    }

    private fun showResult(result: ShortcutInstallResult) {
        clearConfirmation()
        Toast.makeText(this, result.messageRes, Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun clearConfirmation() {
        pendingConfirmation?.cancel()
        pendingConfirmation = null
        confirmationDialog?.dismiss()
        confirmationDialog = null
    }

    override fun onDestroy() {
        clearConfirmation()
        super.onDestroy()
    }
}

/** Memory-only, one-shot consent. Cancellation/destruction never runs the install action. */
internal class ShortcutPinConfirmation(private var install: (() -> ShortcutInstallResult)?) {
    fun confirm(): ShortcutInstallResult? {
        val action = install ?: return null
        install = null
        return runCatching(action).getOrDefault(ShortcutInstallResult.FAILED)
    }

    fun cancel() {
        install = null
    }
}

class ShortcutInstallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val result = runCatching { ShortcutInstallHandler.handle(context, intent) }
            .getOrDefault(ShortcutInstallResult.FAILED)
        // 只在成功时提示。失败基本是发送方的问题（没有快捷方式、包名解析不出来），
        // 而从后台弹一条失败 Toast 对用户没有任何可操作性，只会像故障。
        if (result == ShortcutInstallResult.ADDED) {
            Toast.makeText(context, R.string.shortcut_added, Toast.LENGTH_SHORT).show()
        }
    }
}

internal enum class ShortcutInstallResult(@StringRes val messageRes: Int) {
    ADDED(R.string.shortcut_added),
    INVALID(R.string.shortcut_add_failed_invalid),
    FAILED(R.string.shortcut_add_failed),
}

private object ShortcutInstallHandler {
    fun handle(context: Context, intent: Intent?): ShortcutInstallResult {
        return when (intent?.action) {
            ACTION_INSTALL_SHORTCUT -> handleLegacyShortcut(context, intent)
            else -> ShortcutInstallResult.FAILED
        }
    }

    fun readPinRequest(sourceIntent: Intent): LauncherApps.PinItemRequest? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return null
        val request = sourceIntent.getParcelableCompat(
            LauncherApps.EXTRA_PIN_ITEM_REQUEST,
            LauncherApps.PinItemRequest::class.java
        ) ?: return null
        return request.takeIf { isValidShortcutRequest(it) }
    }

    private fun isValidShortcutRequest(request: LauncherApps.PinItemRequest): Boolean {
        return runCatching {
            request.isValid &&
                request.requestType == LauncherApps.PinItemRequest.REQUEST_TYPE_SHORTCUT &&
                request.shortcutInfo != null
        }.getOrDefault(false)
    }

    // Only called by the explicit positive-button consent action, never by handle/onCreate.
    fun confirmPinnedShortcut(context: Context, request: LauncherApps.PinItemRequest): ShortcutInstallResult {
        // The user may leave the dialog open until the request expires. Recheck at confirmation.
        if (!isValidShortcutRequest(request)) return ShortcutInstallResult.INVALID
        val shortcutInfo = runCatching { request.shortcutInfo }.getOrNull()
            ?: return ShortcutInstallResult.INVALID
        // 先 accept 再落库：accept 是让系统把快捷方式标记为 pinned 的那一步，
        // 它失败时落库只会留下一个点不开的条目。反过来（已 pinned 但没落库）
        // 下次用户再触发会被去重逻辑接住，代价小得多。
        if (!runCatching { request.accept() }.getOrDefault(false)) return ShortcutInstallResult.FAILED
        val added = FlulyApplication.repositories(context).appRepository.addPinnedShortcut(shortcutInfo)
        return if (added) ShortcutInstallResult.ADDED else ShortcutInstallResult.FAILED
    }

    private fun handleLegacyShortcut(context: Context, sourceIntent: Intent): ShortcutInstallResult {
        val shortcutIntent = sourceIntent.getParcelableCompat(
            Intent.EXTRA_SHORTCUT_INTENT,
            Intent::class.java
        ) ?: return ShortcutInstallResult.INVALID
        val label = sourceIntent.getStringExtra(Intent.EXTRA_SHORTCUT_NAME)
        val icon = sourceIntent.getParcelableCompat(
            Intent.EXTRA_SHORTCUT_ICON,
            Bitmap::class.java
        ) ?: loadLegacyShortcutIcon(context, sourceIntent)
        val added = FlulyApplication.repositories(context).appRepository.addLegacyShortcut(
            label = label,
            intent = shortcutIntent,
            icon = icon
        )
        return if (added) ShortcutInstallResult.ADDED else ShortcutInstallResult.INVALID
    }

    private fun loadLegacyShortcutIcon(context: Context, sourceIntent: Intent): Bitmap? {
        val iconResource = sourceIntent.getParcelableCompat(
            Intent.EXTRA_SHORTCUT_ICON_RESOURCE,
            Intent.ShortcutIconResource::class.java
        ) ?: return null
        return runCatching {
            val resources = context.packageManager.getResourcesForApplication(iconResource.packageName)
            val resourceName = iconResource.resourceName
            val id = if (':' in resourceName && '/' in resourceName) {
                val packageName = resourceName.substringBefore(':')
                val typeAndName = resourceName.substringAfter(':')
                val type = typeAndName.substringBefore('/')
                val entryName = typeAndName.substringAfter('/', typeAndName)
                resources.getIdentifier(entryName, type, packageName)
            } else {
                resources.getIdentifier(resourceName, null, iconResource.packageName)
            }
            if (id == 0) return@runCatching null
            ResourcesCompat.getDrawable(resources, id, null)
                ?.toBitmap(128, 128, Bitmap.Config.ARGB_8888)
        }.getOrNull()
    }

    private fun <T : Parcelable> Intent.getParcelableCompat(key: String, clazz: Class<T>): T? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getParcelableExtra(key, clazz)
        } else {
            @Suppress("DEPRECATION")
            getParcelableExtra(key) as? T
        }
    }

    private const val ACTION_INSTALL_SHORTCUT = "com.android.launcher.action.INSTALL_SHORTCUT"
}
