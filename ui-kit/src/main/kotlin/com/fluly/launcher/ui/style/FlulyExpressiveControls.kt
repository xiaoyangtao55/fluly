package com.fluly.launcher.ui.style

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SplitButtonDefaults
import androidx.compose.material3.SplitButtonLayout
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.fluly.launcher.ui.theme.LauncherTheme
import com.fluly.launcher.ui.theme.UiStyle

internal enum class FlulyExpressiveActionKind {
    LOGIN_ACTIONS,
    WEB_ACTIONS,
    EDITOR_TOOLBAR,
}

internal enum class FlulyExpressiveActionContainer {
    LEGACY,
    BUTTON_GROUP,
    SPLIT_BUTTON,
    FLOATING_TOOLBAR,
}

fun useMaterial3ExpressiveComponents(uiStyle: UiStyle): Boolean =
    uiStyle == UiStyle.MATERIAL_3

internal fun flulyExpressiveActionContainer(
    uiStyle: UiStyle,
    kind: FlulyExpressiveActionKind,
): FlulyExpressiveActionContainer = if (!useMaterial3ExpressiveComponents(uiStyle)) {
    FlulyExpressiveActionContainer.LEGACY
} else {
    when (kind) {
        FlulyExpressiveActionKind.LOGIN_ACTIONS -> FlulyExpressiveActionContainer.BUTTON_GROUP
        FlulyExpressiveActionKind.WEB_ACTIONS -> FlulyExpressiveActionContainer.SPLIT_BUTTON
        FlulyExpressiveActionKind.EDITOR_TOOLBAR -> FlulyExpressiveActionContainer.FLOATING_TOOLBAR
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun FlulyExpressiveButtonGroup(
    modifier: Modifier = Modifier,
    slot: @Composable () -> Unit,
) {
    if (useMaterial3ExpressiveComponents(LauncherTheme.style.uiStyle)) {
        ButtonGroup(modifier = modifier) {
            customItem(
                buttonGroupContent = slot,
                menuContent = {},
            )
        }
    } else {
        Row(
            modifier = modifier,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            content = { slot() },
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun FlulyExpressiveSplitButton(
    primaryOnClick: () -> Unit,
    secondaryOnClick: () -> Unit,
    modifier: Modifier = Modifier,
    primaryContent: @Composable () -> Unit,
    secondaryContent: @Composable () -> Unit,
) {
    if (useMaterial3ExpressiveComponents(LauncherTheme.style.uiStyle)) {
        SplitButtonLayout(
            leadingButton = {
                SplitButtonDefaults.TonalLeadingButton(onClick = primaryOnClick) {
                    primaryContent()
                }
            },
            trailingButton = {
                SplitButtonDefaults.TonalTrailingButton(
                    checked = false,
                    onCheckedChange = { secondaryOnClick() },
                ) {
                    secondaryContent()
                }
            },
            modifier = modifier,
        )
    } else {
        Row(
            modifier = modifier,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Button(onClick = primaryOnClick, modifier = Modifier.weight(1f)) { primaryContent() }
            OutlinedButton(
                onClick = secondaryOnClick,
                modifier = Modifier.weight(1f),
                content = { secondaryContent() },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun FlulyExpressiveFloatingToolbar(
    modifier: Modifier = Modifier,
    expanded: Boolean = true,
    content: @Composable () -> Unit,
) {
    if (useMaterial3ExpressiveComponents(LauncherTheme.style.uiStyle)) {
        HorizontalFloatingToolbar(
            expanded = expanded,
            modifier = modifier,
            colors = FloatingToolbarDefaults.standardFloatingToolbarColors(),
            content = { content() },
        )
    } else {
        Row(
            modifier = modifier,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            content = { content() },
        )
    }
}
