package com.fluly.launcher

import android.os.Looper
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.lifecycle.ViewModelStore
import com.fluly.launcher.viewmodel.LauncherViewModel
import com.fluly.launcher.viewmodel.dataStore
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

/**
 * 内置表盘只保留数字样式后，被移除的三种样式（AW / MD3 指针 / MD3E 图形）的
 * 偏好键不再读写。加载偏好时要顺手把它们清掉：老版本升级或还原旧备份后
 * 不能残留死键（死键还会跟着设置导出一直传下去）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
@LooperMode(LooperMode.Mode.PAUSED)
class LegacyWatchFaceStylePreferenceTest {

    private suspend fun awaitLegacyKeysRemoved() {
        val app = RuntimeEnvironment.getApplication()
        val deadline = System.currentTimeMillis() + 10_000
        while (System.currentTimeMillis() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            val prefs = app.dataStore.data.first()
            val remaining = LauncherViewModel.LEGACY_WATCHFACE_STYLE_PREFERENCE_NAMES
                .any { name -> prefs.asMap().keys.any { it.name == name } }
            if (!remaining) return
            delay(10)
        }
        error("Timed out waiting for legacy style keys to be removed")
    }

    @Test
    fun `stale style preferences are removed once the view model loads preferences`() = runBlocking {
        val app = RuntimeEnvironment.getApplication()
        // 老版本遗留的死键三种类型都有：样式键是 string，配色键是 int，自动取色是 boolean。
        // 再混入一个正常键：清理只许删死键，不能碰别的。
        app.dataStore.edit {
            it[stringPreferencesKey("photo_clock_style")] = "MD3E_CLOCK"
            it[stringPreferencesKey("video_clock_style")] = "APPLE_WATCH"
            it[intPreferencesKey("photo_md3e_second_color")] = 0xFF806EA4.toInt()
            it[booleanPreferencesKey("video_md3e_auto_colors")] = false
            it[LauncherViewModel.KEY_PHOTO_USE_THEME_TEXT_COLOR] = false
        }

        val store = ViewModelStore()
        try {
            LauncherViewModel(app).also { store.put("launcher", it) }
            shadowOf(Looper.getMainLooper()).idle()
            awaitLegacyKeysRemoved()

            val prefs = app.dataStore.data.first()
            LauncherViewModel.LEGACY_WATCHFACE_STYLE_PREFERENCE_NAMES.forEach { name ->
                val key = prefs.asMap().keys.firstOrNull { it.name == name }
                assertNull(
                    "legacy key should be removed: $name (value=${key?.let { prefs[it] }}, " +
                        "allKeys=${prefs.asMap().keys.joinToString { k -> k.name }})",
                    key,
                )
            }
            // 清理只动了旧样式键：种子里的正常键原样保留（默认值不落盘、收集器只读，
            // 所以这里比的是种进去的值，不是 StateFlow）。
            assertTrue(prefs[LauncherViewModel.KEY_PHOTO_USE_THEME_TEXT_COLOR] == false)
        } finally {
            store.clear()
            shadowOf(Looper.getMainLooper()).idle()
        }
        Unit
    }
}
