package com.fluly.launcher.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import com.fluly.launcher.ui.common.FlulyScreenShape
import com.fluly.launcher.ui.common.LocalFlulyScreenShapeOverride
import com.fluly.launcher.ui.style.FlulyOverlayHost

// Internal component render modes, not user-selectable launcher themes.
// MATERIAL_3 is still required by the independent settings screen; legacy enum
// names remain for shared component/source compatibility, never preference selection.
enum class UiStyle {
    APPLE_WATCH,
    MATERIAL_3,
    MIUIX
}

/** Launcher styling is fixed; legacy persisted values migrate to watchOS. */
val DEFAULT_UI_STYLE: UiStyle = UiStyle.APPLE_WATCH

@Suppress("UNUSED_PARAMETER")
fun uiStyleFromPreference(raw: String?): UiStyle = DEFAULT_UI_STYLE

// 深色 Material3 颜色方案
private val DarkColorScheme = darkColorScheme(
    primary = WatchColorsDark.ActiveCyan,
    secondary = WatchColorsDark.ActiveGreen,
    tertiary = WatchColorsDark.ActiveBlue,
    background = WatchColorsDark.Black,
    surface = WatchColorsDark.Black,
    onPrimary = WatchColorsDark.White,
    onSecondary = WatchColorsDark.White,
    onTertiary = WatchColorsDark.White,
    onBackground = WatchColorsDark.White,
    onSurface = WatchColorsDark.White,
)

// 浅色 Material3 颜色方案
private val LightColorScheme = lightColorScheme(
    primary = WatchColorsLight.ActiveCyan,
    secondary = WatchColorsLight.ActiveGreen,
    tertiary = WatchColorsLight.ActiveBlue,
    background = WatchColorsDark.White,
    surface = WatchColorsDark.White,
    onPrimary = WatchColorsDark.Black,
    onSecondary = WatchColorsDark.Black,
    onTertiary = WatchColorsDark.Black,
    onBackground = WatchColorsDark.Black,
    onSurface = WatchColorsDark.Black,
)

/** Keeps raw Material text inputs visually aligned with Fluly's larger row corners. */
private fun flulyMaterialShapes(style: LauncherStyle): Shapes {
    val rowShape = style.rowShape as CornerBasedShape
    val cardShape = style.cardShape as CornerBasedShape
    val panelShape = style.panelShape as CornerBasedShape
    return Shapes(
        extraSmall = rowShape,
        small = rowShape,
        medium = cardShape,
        large = panelShape,
        extraLarge = panelShape,
    )
}

// 主题模式
enum class ThemeMode {
    SYSTEM,
    DARK,
    LIGHT
}

val LocalGlobalUiScale = compositionLocalOf { 1f }

@Composable
fun WatchLauncherTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    @Suppress("UNUSED_PARAMETER") uiStyle: UiStyle = UiStyle.APPLE_WATCH,
    screenShape: FlulyScreenShape = FlulyScreenShape.AUTO,
    content: @Composable () -> Unit
) {
    val isDark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
    }

    WatchColors.setDarkMode(isDark)
    val shapes = remember(isDark) { flulyMaterialShapes(appleWatchLauncherStyle(isDark)) }
    MaterialTheme(
        colorScheme = if (isDark) DarkColorScheme else LightColorScheme,
        shapes = shapes,
    ) {
        CompositionLocalProvider(LocalFlulyScreenShapeOverride provides screenShape) {
            ProvideLauncherStyle(
                uiStyle = UiStyle.APPLE_WATCH,
                darkMode = isDark,
                content = { FlulyOverlayHost(content = content) },
            )
        }
    }
}

@Composable
fun ProvideGlobalUiScale(    scalePercent: Int,
    content: @Composable () -> Unit
) {
    val baseDensity = LocalDensity.current
    val scale = scalePercent.coerceIn(50, 150) / 100f
    val scaledDensity = remember(baseDensity, scale) {
        Density(
            density = baseDensity.density * scale,
            fontScale = baseDensity.fontScale
        )
    }
    CompositionLocalProvider(
        LocalDensity provides scaledDensity,
        LocalGlobalUiScale provides scale,
        content = content
    )
}

/**
 * 在 [androidx.compose.ui.window.Dialog] 内重新挂上全局 UI 缩放。
 *
 * Dialog 是**独立 Window**，Compose 在它的根节点按该 Window 自己的
 * DisplayMetrics 重新提供 [LocalDensity]，外层 [ProvideGlobalUiScale] 那份
 * 已缩放的 density 到不了弹窗里 —— 所以弹窗一直按系统原始 density 排版，
 * 设置里的「全局 UI 大小」对它毫无作用（老师实机反馈）。
 *
 * [LocalGlobalUiScale] 走的是普通 CompositionLocal，**能**穿过 Dialog 边界，
 * 所以这里直接读它拿到当前倍率，不需要调用方再传一次百分比。
 */
@Composable
fun ProvideDialogUiScale(
    scaleOverride: Float? = null,
    content: @Composable () -> Unit
) {
    val scale = scaleOverride ?: LocalGlobalUiScale.current
    val dialogDensity = LocalDensity.current
    val scaledDensity = remember(dialogDensity, scale) {
        Density(
            density = dialogDensity.density * scale,
            fontScale = dialogDensity.fontScale
        )
    }
    CompositionLocalProvider(
        LocalDensity provides scaledDensity,
        content = content
    )
}
