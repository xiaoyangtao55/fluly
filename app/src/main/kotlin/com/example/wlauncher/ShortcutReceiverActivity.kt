package com.flue.launcher

import android.app.Activity
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
 *   后，系统把 `CONFIRM_PIN_SHORTCUT` 发给**当前默认启动器**，这里接住并 accept。
 * - [ShortcutInstallReceiver]：旧路径。应用直接发 `INSTALL_SHORTCUT` 广播，Android 8.0
 *   之前的通行做法，至今仍有大量应用（尤其是国内工具类）在用。
 *
 * 两条都需要「本应用是默认桌面」才算完整可用：PIN 请求只发给默认启动器；
 * 旧广播虽然不校验，但应用通常也会先检查 `resolveActivity(HOME)`。
 */
class ShortcutReceiverActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val result = runCatching { ShortcutInstallHandler.handle(this, intent) }
            .getOrDefault(ShortcutInstallResult.FAILED)
        Toast.makeText(this, result.messageRes, Toast.LENGTH_SHORT).show()
        finish()
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
            LauncherApps.ACTION_CONFIRM_PIN_SHORTCUT -> handlePinnedShortcut(context, intent)
            else -> ShortcutInstallResult.FAILED
        }
    }

    private fun handlePinnedShortcut(context: Context, sourceIntent: Intent): ShortcutInstallResult {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return ShortcutInstallResult.FAILED
        val request = sourceIntent.getParcelableCompat(
            LauncherApps.EXTRA_PIN_ITEM_REQUEST,
            LauncherApps.PinItemRequest::class.java
        ) ?: return ShortcutInstallResult.INVALID
        // 请求有寿命：系统给启动器的时间窗过了、或同一请求被处理过，再 accept 只会抛异常。
        // 提前用 isValid 挡掉，避免把「请求过期」误报成「添加失败」。
        if (!runCatching { request.isValid }.getOrDefault(false)) return ShortcutInstallResult.INVALID
        val requestType = runCatching { request.requestType }.getOrNull()
            ?: return ShortcutInstallResult.INVALID
        // 同一个通道也送小组件（widget）请求，本启动器没有宿主，只能按无效处理。
        if (requestType != LauncherApps.PinItemRequest.REQUEST_TYPE_SHORTCUT) {
            return ShortcutInstallResult.INVALID
        }
        val shortcutInfo = runCatching { request.shortcutInfo }.getOrNull()
            ?: return ShortcutInstallResult.INVALID
        // 先 accept 再落库：accept 是让系统把快捷方式标记为 pinned 的那一步，
        // 它失败时落库只会留下一个点不开的条目。反过来（已 pinned 但没落库）
        // 下次用户再触发会被去重逻辑接住，代价小得多。
        if (!runCatching { request.accept() }.getOrDefault(false)) return ShortcutInstallResult.FAILED
        val added = FlueApplication.repositories(context).appRepository.addPinnedShortcut(shortcutInfo)
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
        val added = FlueApplication.repositories(context).appRepository.addLegacyShortcut(
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
