package com.fluly.launcher.ui.settings

import com.fluly.launcher.ui.navigation.LayoutMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SettingsEnumOptionTest {
    @Test
    fun everyLayoutPickerIdResolvesToItsActualEnum() {
        LayoutMode.entries.forEach { mode ->
            assertEquals(mode, settingsEnumOption<LayoutMode>(mode.name.lowercase()))
            assertEquals(mode, settingsEnumOption<LayoutMode>(mode.name.uppercase()))
            assertEquals(mode, settingsEnumOption<LayoutMode>(mode.name))
        }
    }

    @Test
    fun ringUsesMixedCaseEnumInsteadOfValueOfUppercase() {
        assertEquals(LayoutMode.Ring, settingsEnumOption<LayoutMode>("ring"))
        assertEquals(LayoutMode.Ring, settingsEnumOption<LayoutMode>("RING"))
    }

    @Test
    fun unknownOrEmptyOptionDoesNotChangeSelectionOrCrash() {
        assertNull(settingsEnumOption<LayoutMode>("removed-layout"))
        assertNull(settingsEnumOption<LayoutMode>(""))
    }
}
