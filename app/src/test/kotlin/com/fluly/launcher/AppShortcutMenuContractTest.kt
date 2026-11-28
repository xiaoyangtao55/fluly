package com.fluly.launcher

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 长按菜单里的「应用快捷方式」条目。
 *
 * 这段逻辑没有编译期契约能兜住，只能靠代码形态来钉：
 *
 * - 少判一次 `hasShortcutHostPermission()`，用户在非默认启动器状态下长按会直接抛
 *   `SecurityException`（`getShortcuts` 的文档写明了要 hasShortcutHostPermission）；
 * - 少一次 `take()`，菜单会被应用自带的十几条 shortcut 撑成滚动列表；
 * - 漏掉 `isFolder` 之类的排除，文件夹长按会去查一个不存在的包。
 */
class AppShortcutMenuContractTest {
    private val root = sequenceOf(
        File("src/main/kotlin"),
        File("app/src/main/kotlin"),
        File("../app/src/main/kotlin"),
    ).first(File::isDirectory)

    private val popup by lazy {
        File(root, "com/fluly/launcher/ui/drawer/ShortcutPopup.kt").readText()
    }

    @Test
    fun shortcutQueryChecksHostPermissionFirst() {
        assertTrue(popup.contains("hasShortcutHostPermission()"))
        assertTrue(popup.contains("FLAG_MATCH_DYNAMIC"))
        assertTrue(popup.contains("FLAG_MATCH_MANIFEST"))
        assertTrue(popup.contains("FLAG_MATCH_PINNED"))
    }

    @Test
    fun foldersAndBuiltInEntriesAreExcluded() {
        assertTrue(popup.contains("!app.isFolder"))
        assertTrue(popup.contains("!app.isAppListShortcut"))
        assertTrue(popup.contains("!app.isBuiltInSettingsEntry"))
    }

    @Test
    fun shortcutCountIsCapped() {
        assertTrue(popup.contains(".take(MAX_APP_SHORTCUTS)"))
        assertTrue(popup.contains("private const val MAX_APP_SHORTCUTS"))
    }
}
