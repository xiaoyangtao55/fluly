package com.flue.launcher.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import com.flue.launcher.ui.common.FlueScreenShape
import com.flue.launcher.ui.common.LocalFlueScreenShapeOverride
import com.flue.launcher.ui.style.FlueOverlayHost

/** Settings has one visual language, independent of the saved launcher style.
 * Android 12+ uses wallpaper-derived Material You colors; older ROMs use the
 * complete Material baseline palette rather than partially overriding its roles.
 */
@Composable
fun SettingsMaterialTheme(
    themeMode: ThemeMode,
    screenShape: FlueScreenShape,
    content: @Composable () -> Unit,
) {
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
    }
    val context = LocalContext.current
    val colors = remember(context, dark) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        } else {
            if (dark) darkColorScheme() else lightColorScheme()
        }
    }
    MaterialTheme(colorScheme = colors, typography = Typography(), shapes = Shapes()) {
        val shapes = MaterialTheme.shapes
        val style = remember(colors, shapes, dark) {
            materialLauncherStyle(dark).copy(
                screenBackground = colors.surface,
                screenScrim = Brush.verticalGradient(listOf(colors.surface, colors.surface)),
                cardColor = colors.surfaceContainerLow,
                pressedCardColor = colors.surfaceContainerHigh,
                selectedCardColor = colors.secondaryContainer,
                outlineColor = colors.outlineVariant,
                titleColor = colors.onSurface,
                bodyColor = colors.onSurface,
                secondaryTextColor = colors.onSurfaceVariant,
                accentColor = colors.primary,
                topBarChipColor = colors.surfaceContainerHigh,
                topBarTextColor = colors.onSurface,
                cardShape = shapes.large,
                compactShape = shapes.medium,
                rowShape = shapes.medium,
                panelShape = shapes.extraLarge,
                bubbleShape = shapes.large,
            )
        }
        CompositionLocalProvider(LocalFlueScreenShapeOverride provides screenShape) {
            ProvideLauncherStyle(style) {
                FlueOverlayHost(content = content)
            }
        }
    }
}
