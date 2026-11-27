package com.flue.launcher.ui.settings

import android.content.Context
import android.provider.Settings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * 「主屏幕应用」入口的回退路径：Android 10 以下（本项目 minSdk 27）没有
 * RoleManager.ROLE_HOME，必须落到系统「默认应用 → 主屏幕应用」那一页，
 * 不能让 27/28 的设备点了没反应。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class DefaultHomeAppTest {

    @Test
    fun `below Android 10 the request falls back to the system home settings page`() {
        val context = RuntimeEnvironment.getApplication() as Context

        val intent = defaultHomeAppRequestIntent(context)

        assertEquals(Settings.ACTION_HOME_SETTINGS, intent.action)
    }

    @Test
    fun `default detection is false when no home activity resolves`() {
        val context = RuntimeEnvironment.getApplication() as Context

        // Robolectric 环境没有注册 CATEGORY_HOME 的解析目标：拿不到默认归属时
        // 必须如实返回 false（宁可信「不是默认」，让用户多按一次确认）。
        assertFalse(isDefaultHomeApp(context))
    }
}
