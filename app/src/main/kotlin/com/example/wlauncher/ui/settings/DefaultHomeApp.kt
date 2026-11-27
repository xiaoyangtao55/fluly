package com.flue.launcher.ui.settings

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings

/**
 * 设置 → 工具 → 主屏幕应用：设为系统默认启动器的两条路径。
 *
 * - Android 10+ 走 [RoleManager.ROLE_HOME] 的系统确认框（[defaultHomeAppRequestIntent]
 *   返回 role intent，应用内直接弹框，不用跳走）；
 * - 更旧的版本（或个别 ROM 没注册 ROLE_HOME）返回 `ACTION_HOME_SETTINGS`，
 *   落到系统「默认应用 → 主屏幕应用」那一页。
 *
 * 调用方对返回的 Intent 仍要 runCatching 再 startActivity：个别精简 ROM 连
 * 「默认应用」设置页都没有，ActivityNotFoundException 不能让它崩到设置页。
 */
internal fun defaultHomeAppRequestIntent(context: Context): Intent {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val roleManager = context.getSystemService(RoleManager::class.java)
        if (roleManager?.isRoleAvailable(RoleManager.ROLE_HOME) == true) {
            return roleManager.createRequestRoleIntent(RoleManager.ROLE_HOME)
        }
    }
    return Intent(Settings.ACTION_HOME_SETTINGS)
}

/**
 * 当前应用是否已是系统默认主屏幕应用，用于设置行的状态回显。
 *
 * Android 10+ 读 [RoleManager.ROLE_HOME] 的持有人；27/28 没有 RoleManager，
 * 退化为看 `CATEGORY_HOME` 的默认解析落在谁身上 —— 多个启动器并存时解析可能
 * 落到系统的 Resolver，那时如实显示「不是默认」，宁可多让用户点一次确认。
 */
internal fun isDefaultHomeApp(context: Context): Boolean {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val roleManager = context.getSystemService(RoleManager::class.java) ?: return false
        return roleManager.isRoleHeld(RoleManager.ROLE_HOME)
    }
    val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
    val resolved = runCatching {
        context.packageManager.resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY)
    }.getOrNull() ?: return false
    val resolvedPackage = resolved.activityInfo?.packageName ?: return false
    return resolvedPackage == context.packageName
}
