package com.fluly.launcher.ui.style

import androidx.compose.runtime.Composable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import com.fluly.launcher.ui.style.md3.Md3Slider
import com.fluly.launcher.ui.style.md3.Md3Switch
import com.fluly.launcher.ui.style.md3.Md3SwitchIndicator
import com.fluly.launcher.ui.theme.LauncherTheme

/**
 * 业务侧统一控件入口。开关与滑块始终使用 Material 3 组件，
 * 颜色继续读取 LauncherTheme；不改变页面布局、卡片形状或主题选择。
 * 参数透传保证禁用态、滑块步进及拖动结束回调保持原有契约。
 */

/** 完整交互版开关：自带手势与触觉，用于绝大多数设置行。 */
@Composable
fun FlulySwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Md3Switch(checked, onCheckedChange, modifier, enabled)
}

/**
 * 只绘制、**不收手势**的开关。
 *
 * 交互由外层整行的 `clickable` 承担。存在的理由是一条已经修过的性能问题：隐藏应用页
 * 几乎每个 fling 帧都会新组合一行，所以那里刻意压成「一个 clickable 节点 + 一个小动画层」，
 * 而不是 `Card → Row → 各自可点的 Switch`。库的真 `Switch` 自带 interactionSource 与
 * 手势链，直接塞进去等于推翻那个修复，而且子节点先拿到指针事件会抢掉整行点击。
 *
 * 迁移时逐点判定归属：默认用 [FlulySwitch]，原本就是「整行可点 + 开关纯展示」的用这个。
 */
@Composable
fun FlulySwitchIndicator(
    checked: Boolean,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    @Suppress("UNUSED_PARAMETER") pressed: Boolean = false,
) {
    Md3SwitchIndicator(checked, modifier, enabled)
}

@Composable
fun FlulySlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    enabled: Boolean = true,
    onValueChangeFinished: (() -> Unit)? = null,
) {
    Md3Slider(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        valueRange = valueRange,
        steps = steps,
        enabled = enabled,
        onValueChangeFinished = onValueChangeFinished,
    )
}

/**
 * Store/editor forms use one input entry point so borders, cursor and text
 * tokens do not drift between login, profile and upload overlays.
 *
 * The field keeps Material's semantics and keyboard behavior; the surface
 * colors come from the active launcher style. This is intentionally a small
 * adapter, so pages can migrate incrementally without changing their state
 * or validation logic.
 */
@Composable
fun FlulyTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    enabled: Boolean = true,
    singleLine: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    visualTransformation: androidx.compose.ui.text.input.VisualTransformation =
        androidx.compose.ui.text.input.VisualTransformation.None,
    placeholder: String? = null,
    supportingText: String? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    minLines: Int = 1,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    isError: Boolean = false,
) {
    val style = LauncherTheme.style
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        enabled = enabled,
        singleLine = singleLine,
        minLines = minLines,
        maxLines = maxLines,
        isError = isError,
        keyboardOptions = keyboardOptions,
        visualTransformation = visualTransformation,
        label = label?.let { { Text(it) } },
        placeholder = placeholder?.let { { Text(it) } },
        supportingText = supportingText?.let { { Text(it) } },
        trailingIcon = trailingIcon,
        shape = style.rowShape,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = style.accentColor,
            unfocusedBorderColor = style.outlineColor,
            focusedContainerColor = style.screenBackground,
            unfocusedContainerColor = style.screenBackground,
            cursorColor = style.accentColor,
            focusedTextColor = style.bodyColor,
            unfocusedTextColor = style.bodyColor,
        ),
    )
}
