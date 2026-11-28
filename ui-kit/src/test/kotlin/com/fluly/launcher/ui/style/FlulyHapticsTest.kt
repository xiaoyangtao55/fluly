package com.fluly.launcher.ui.style

import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import com.fluly.launcher.ui.theme.UiStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 三套风格的触觉映射。
 *
 * 老师报的问题之一是「开关一点反馈都没有」——`SettingsSwitchRow` 的 49 个调用点里
 * 一次 `performHapticFeedback` 都没有。这套测试保证：每套风格开关都有反馈、
 * 开与关能区分、连续滑块不会被当成逐格滑块去逐帧震动。
 */
class FlulyHapticsTest {
    private val styles = UiStyle.entries

    @Test
    fun `every style gives distinct feedback for on and off`() {
        styles.forEach { style ->
            assertNotEquals(
                "style $style must distinguish on from off",
                FlulyHaptics.toggleOn(style),
                FlulyHaptics.toggleOff(style),
            )
        }
    }

    @Test
    fun `toggle dispatches by checked state`() {
        styles.forEach { style ->
            assertEquals(FlulyHaptics.toggleOn(style), FlulyHaptics.toggle(style, checked = true))
            assertEquals(FlulyHaptics.toggleOff(style), FlulyHaptics.toggle(style, checked = false))
        }
    }

    @Test
    fun `hyperos uses the stronger confirm and reject pair`() {
        // HyperOS 的开关是一记确认感更强的短促反馈，与 watchOS 的「轻确认」刻意不同。
        assertEquals(HapticFeedbackType.Confirm, FlulyHaptics.toggleOn(UiStyle.MIUIX))
        assertEquals(HapticFeedbackType.Reject, FlulyHaptics.toggleOff(UiStyle.MIUIX))
        assertNotEquals(
            FlulyHaptics.toggleOn(UiStyle.MIUIX),
            FlulyHaptics.toggleOn(UiStyle.APPLE_WATCH),
        )
    }

    @Test
    fun `material and apple use the semantic toggle constants`() {
        listOf(UiStyle.MATERIAL_3, UiStyle.APPLE_WATCH).forEach { style ->
            assertEquals(HapticFeedbackType.ToggleOn, FlulyHaptics.toggleOn(style))
            assertEquals(HapticFeedbackType.ToggleOff, FlulyHaptics.toggleOff(style))
        }
    }

    @Test
    fun `continuous sliders never use a per-step tick`() {
        // 无刻度的滑块若逐格给反馈，连续拖动就变成一串持续震动。
        styles.forEach { style ->
            val continuous = FlulyHaptics.sliderTick(style, stepped = false)
            assertEquals(HapticFeedbackType.GestureThresholdActivate, continuous)
            assertNotEquals(HapticFeedbackType.SegmentTick, continuous)
            assertNotEquals(HapticFeedbackType.SegmentFrequentTick, continuous)
        }
    }

    @Test
    fun `stepped sliders tick and hyperos ticks more densely`() {
        assertEquals(
            HapticFeedbackType.SegmentFrequentTick,
            FlulyHaptics.sliderTick(UiStyle.MIUIX, stepped = true),
        )
        listOf(UiStyle.MATERIAL_3, UiStyle.APPLE_WATCH).forEach { style ->
            assertEquals(
                HapticFeedbackType.SegmentTick,
                FlulyHaptics.sliderTick(style, stepped = true),
            )
        }
    }

    @Test
    fun `slider release is a gesture end everywhere`() {
        styles.forEach { style ->
            assertEquals(HapticFeedbackType.GestureEnd, FlulyHaptics.sliderRelease(style))
        }
    }

    @Test
    fun `scroll end is a threshold hit on apple and a gesture end elsewhere`() {
        // watchOS 的触底读作「撞到了」，另两档是手势收尾。
        assertEquals(
            HapticFeedbackType.GestureThresholdActivate,
            FlulyHaptics.scrollEnd(UiStyle.APPLE_WATCH),
        )
        listOf(UiStyle.MATERIAL_3, UiStyle.MIUIX).forEach { style ->
            assertEquals(HapticFeedbackType.GestureEnd, FlulyHaptics.scrollEnd(style))
        }
    }

    @Test
    fun `no style is left without a mapping`() {
        // 新增风格档时这条会先红 —— 提醒把三处映射都补上，而不是静默落到某个默认值。
        assertEquals(3, styles.size)
        styles.forEach { style ->
            assertTrue(FlulyHaptics.toggleOn(style) != FlulyHaptics.toggleOff(style))
            FlulyHaptics.sliderTick(style, stepped = true)
            FlulyHaptics.sliderRelease(style)
            FlulyHaptics.scrollEnd(style)
        }
    }
}
