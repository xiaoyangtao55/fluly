package com.fluly.launcher.ui.style

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import com.fluly.launcher.ui.theme.UiStyle

/**
 * 三套风格的触觉映射。
 *
 * **纯映射、不碰 Compose 运行时**，所以能在纯 JVM 单测里把「哪套风格给哪种反馈」钉住
 * （见 `FlulyHapticsTest`）。真正的 `performHapticFeedback` 由控件实现调用。
 *
 * 为什么要分档而不是统一给一种：三套风格的触觉语言本来就不同 ——
 * - HyperOS 的开关是一记短促的「咔」，滑块每过一格都有细微反馈；
 * - Material 3 Expressive 用 `ToggleOn/Off` 与 `SegmentTick` 这套语义常量，跟着系统走；
 * - watchOS 的开关偏「轻确认」，滑块只在跨过刻度时提示，不逐格震。
 *
 * 现状里 `SettingsSwitchRow`（49 个调用点）**一次震动都没有**，这是老师报的问题之一。
 */
object FlulyHaptics {
    /** 开关拨到「开」。 */
    fun toggleOn(style: UiStyle): HapticFeedbackType = when (style) {
        // HyperOS 的开关是一记确认感更强的短促反馈。
        UiStyle.MIUIX -> HapticFeedbackType.Confirm
        UiStyle.MATERIAL_3 -> HapticFeedbackType.ToggleOn
        // watchOS 偏「轻确认」，用 ToggleOn 而不是 Confirm，免得比 HyperOS 还重。
        UiStyle.APPLE_WATCH -> HapticFeedbackType.ToggleOn
    }

    /** 开关拨到「关」。 */
    fun toggleOff(style: UiStyle): HapticFeedbackType = when (style) {
        UiStyle.MIUIX -> HapticFeedbackType.Reject
        UiStyle.MATERIAL_3 -> HapticFeedbackType.ToggleOff
        UiStyle.APPLE_WATCH -> HapticFeedbackType.ToggleOff
    }

    fun toggle(style: UiStyle, checked: Boolean): HapticFeedbackType =
        if (checked) toggleOn(style) else toggleOff(style)

    /**
     * 滑块跨过一个刻度。
     *
     * 有刻度（`steps > 0`）时逐格给；无刻度的连续滑块只在拖到端点时给，
     * 否则连续拖动会变成一串持续震动。
     */
    fun sliderTick(style: UiStyle, stepped: Boolean): HapticFeedbackType = when {
        !stepped -> HapticFeedbackType.GestureThresholdActivate
        // HyperOS 逐格的反馈更细密。
        style == UiStyle.MIUIX -> HapticFeedbackType.SegmentFrequentTick
        else -> HapticFeedbackType.SegmentTick
    }

    /** 滑块松手。 */
    fun sliderRelease(style: UiStyle): HapticFeedbackType = HapticFeedbackType.GestureEnd

    /**
     * 滚动触底。
     *
     * MIUIX 档走 miuix 自己的 `scrollEndHaptic`（在 [FlulyScrollBehavior] 那侧接），
     * 这里给的是另两档自接时用的类型。
     */
    fun scrollEnd(style: UiStyle): HapticFeedbackType = when (style) {
        // watchOS 的触底是「撞到了」的一记，用手势阈值激活更贴近。
        UiStyle.APPLE_WATCH -> HapticFeedbackType.GestureThresholdActivate
        UiStyle.MATERIAL_3 -> HapticFeedbackType.GestureEnd
        UiStyle.MIUIX -> HapticFeedbackType.GestureEnd
    }
}

/**
 * 一次短促的「咔」。给那些不需要区分风格、只想确认「点到了」的场合。
 *
 * 原先住在 `ui/drawer/ShortcutPopup.kt`，被抽屉、智能堆叠、商店、编辑器到处 import；
 * 它跟风格映射无关，但同样是「触觉」这件事，一起收在这里。
 */
fun vibrateHaptic(context: Context) {
    runCatching {
        val effect = VibrationEffect.createOneShot(30, VibrationEffect.DEFAULT_AMPLITUDE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            manager.defaultVibrator.vibrate(effect)
        } else {
            @Suppress("DEPRECATION")
            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            vibrator.vibrate(effect)
        }
    }
}
