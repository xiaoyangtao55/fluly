package com.fluly.launcher.ui.settings

import android.content.Context
import android.provider.Settings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
    fun `default detection resolves the app's own home activity on the fallback path`() {
        val context = RuntimeEnvironment.getApplication() as Context

        // 27/28 退化到解析 CATEGORY_HOME 的归属。Robolectric 按真实 manifest 注册
        // 组件：本项目自己就带 CATEGORY_HOME 的 LauncherActivity，测试进程里它是
        // 解析到的唯一桌面 —— 此时理应判定「已是默认」。
        assertTrue(isDefaultHomeApp(context))
    }
}
