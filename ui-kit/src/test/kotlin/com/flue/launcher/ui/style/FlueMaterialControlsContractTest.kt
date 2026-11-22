package com.flue.launcher.ui.style

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FlueMaterialControlsContractTest {
    private val root = generateSequence(File(".").canonicalFile) { it.parentFile }
        .first { File(it, "settings.gradle.kts").isFile }
    private fun source(path: String) = File(root, path).readText()
    private val controls get() = source("ui-kit/src/main/kotlin/com/flue/launcher/ui/style/FlueControls.kt")

    @Test
    fun switchesAndSlidersUseMaterialRegardlessOfPageStyle() {
        val source = controls.substringBefore("fun FlueTextField")
        assertTrue(source.contains("Md3Switch(checked, onCheckedChange, modifier, enabled)"))
        assertTrue(source.contains("Md3SwitchIndicator(checked, modifier, enabled)"))
        assertTrue(source.contains("Md3Slider("))
        listOf("AppleSwitch", "AppleSlider", "MiuixStyleSwitch", "MiuixStyleSlider", "when (LauncherTheme.style.uiStyle)").forEach {
            assertFalse("Old style dispatch must not return: $it", source.contains(it))
        }
    }

    @Test
    fun sliderForwardsAllInteractionParameters() {
        val slider = controls.substringAfter("fun FlueSlider").substringBefore("fun FlueTextField")
        listOf("value", "onValueChange", "modifier", "valueRange", "steps", "enabled", "onValueChangeFinished").forEach {
            assertTrue("Missing slider parameter $it", slider.contains("$it = $it"))
        }
    }

    @Test
    fun indicatorLeavesInputWithTheParentAndKeepsAccentColors() {
        val md3 = source("ui-kit/src/main/kotlin/com/flue/launcher/ui/style/md3/Md3Controls.kt")
        val indicator = md3.substringAfter("internal fun Md3SwitchIndicator").substringBefore("internal fun Md3Slider")
        assertTrue(indicator.contains("onCheckedChange = null"))
        assertTrue(indicator.contains("enabled = enabled"))
        assertTrue(indicator.contains("checkedTrackColor = style.accentColor"))
        assertTrue(md3.contains("activeTrackColor = style.accentColor"))
        val rows = source("app/src/main/kotlin/com/example/wlauncher/ui/settings/SettingsRowControls.kt")
        assertTrue(rows.contains("FlueSwitchIndicator("))
        assertFalse(rows.contains("androidx.compose.material3.Switch("))
        assertFalse(Regex("(?m)^\\s+Slider\\(").containsMatchIn(rows))
    }
}
