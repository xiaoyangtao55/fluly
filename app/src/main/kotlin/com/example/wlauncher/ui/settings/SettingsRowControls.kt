package com.flue.launcher.ui.settings

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flue.launcher.R
import com.flue.launcher.ui.settings.miuix.MiuixIcons
import com.flue.launcher.ui.settings.miuix.MiuixSettingsCard
import com.flue.launcher.ui.settings.miuix.MiuixSettingsRow
import com.flue.launcher.ui.style.FlueGroupDivider
import com.flue.launcher.ui.style.FlueGroupPosition
import com.flue.launcher.ui.style.FlueSlider
import com.flue.launcher.ui.style.FlueSwitch
import com.flue.launcher.ui.style.FlueSwitchIndicator
import com.flue.launcher.ui.style.flueGroupShape
import com.flue.launcher.ui.style.flueRowHorizontalPadding
import com.flue.launcher.ui.theme.LauncherTheme
import com.flue.launcher.ui.theme.RoundElementRole
import com.flue.launcher.ui.theme.UiStyle
import com.flue.launcher.ui.theme.roundAwareCompactShape
import top.yukonga.miuix.kmp.icon.MiuixIcons as OfficialMiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Ok as OfficialOk

/**
 * 设置页的行控件：单选、开关、滑块、动作卡。
 *
 * 这些组件只负责「长什么样、点击反馈」，值从哪来、点了做什么
 * 一律由调用方传入 —— 数据驱动渲染器与手写页共用同一批控件。
 */

@Composable
internal fun SettingsChoiceRow(
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit,
    scale: Float,
    enabled: Boolean = true,
    /**
     * 在成组卡里的位置。
     *
     * 单选组（界面风格 / 界面动画 / 屏幕形状）本来就是「一个类型的几个选项」，MIUIX 档
     * 要并成一张卡；以前这里收不到位置，于是三行各自一张独立胶囊卡，和旁边已经并好的
     * 分组摆在一起就很割裂（老师报的「一个类型的应该合并起来」）。
     */
    groupPosition: FlueGroupPosition = FlueGroupPosition.Single,
) {
    val launcherStyle = LauncherTheme.style
    if (launcherStyle.uiStyle == UiStyle.MATERIAL_3) {
        MaterialSettingsListItem(
            title = title,
            subtitle = subtitle,
            scale = scale,
            enabled = enabled,
            modifier = Modifier.selectable(
                selected = selected,
                enabled = enabled,
                role = androidx.compose.ui.semantics.Role.RadioButton,
                onClick = onClick,
            ),
            trailing = {
                androidx.compose.material3.RadioButton(
                    selected = selected,
                    onClick = null,
                    enabled = enabled,
                )
            },
        )
        return
    }
    val horizontalPadding = flueRowHorizontalPadding(launcherStyle, rowHeight = 56.dp)
    if (launcherStyle.uiStyle == UiStyle.MIUIX) {
        // MIUIX：56dp 设置行，选中态在右侧显示 Check 图标。
        MiuixSettingsCard(
            modifier = Modifier.chordScaled(scale, enabled),
            roundRole = RoundElementRole.CAPSULE,
            groupPosition = groupPosition,
        ) {
            MiuixSettingsRow(
                title = title,
                subtitle = subtitle.takeIf { it.isNotBlank() },
                enabled = enabled,
                onClick = onClick,
                trailing = {
                    if (selected) {
                        Icon(
                            imageVector = MiuixIcons.Check,
                            contentDescription = null,
                            tint = launcherStyle.accentColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            )
        }
        return
    }
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val pressedScale = animateFloatAsState(
        if (pressed) 0.964f else 1f,
        animationSpec = launcherStyle.pressScaleSpec,
        label = "choice_row_scale"
    )
    val rowShape = launcherStyle.roundAwareCompactShape(RoundElementRole.CAPSULE)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale * pressedScale.value
                scaleY = scale * pressedScale.value
                alpha = if (enabled) scale.coerceIn(0.55f, 1f) else 0.48f
            }
            .clip(rowShape)
            .background(
                when {
                    pressed && selected -> launcherStyle.selectedCardColor.copy(alpha = 0.80f)
                    pressed -> launcherStyle.pressedCardColor
                    selected -> launcherStyle.selectedCardColor
                    else -> launcherStyle.cardColor
                }
            )
            .border(1.dp, launcherStyle.outlineColor, rowShape)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClick = onClick
            )
            .padding(horizontal = horizontalPadding, vertical = 14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth(0.84f)
                    .padding(end = 12.dp)
            ) {
                Text(title, color = launcherStyle.titleColor, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(3.dp))
                Text(subtitle, color = launcherStyle.secondaryTextColor, fontSize = 12.sp)
            }
            if (selected) {
                Icon(OfficialMiuixIcons.OfficialOk, contentDescription = null, tint = launcherStyle.accentColor)
            }
        }
    }
}

@Composable
internal fun SettingsSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean = true,
    onToggle: (Boolean) -> Unit,
    scale: Float,
    leadingIcon: ImageBitmap? = null,
    reserveLeadingIconSpace: Boolean = false,
    groupPosition: FlueGroupPosition = FlueGroupPosition.Single,
    indicatorOnly: Boolean = false,
) {
    val launcherStyle = LauncherTheme.style
    if (launcherStyle.uiStyle == UiStyle.MATERIAL_3) {
        val leading: (@Composable () -> Unit)? = if (leadingIcon != null || reserveLeadingIconSpace) {
            {
                Box(
                    modifier = Modifier.size(34.dp).clip(CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    if (leadingIcon != null) {
                        Image(leadingIcon, contentDescription = null, modifier = Modifier.fillMaxSize())
                    }
                }
            }
        } else null
        MaterialSettingsListItem(
            title = title,
            subtitle = subtitle,
            scale = scale,
            enabled = enabled,
            modifier = Modifier.toggleable(
                value = checked,
                enabled = enabled,
                role = androidx.compose.ui.semantics.Role.Switch,
                onValueChange = onToggle,
            ),
            leading = leading,
            trailing = {
                // A single toggleable parent owns input and accessibility, including
                // indicator-only rows; the visual switch must not toggle twice.
                FlueSwitchIndicator(
                    checked = checked,
                    enabled = enabled,
                )
            },
        )
        return
    }
    val horizontalPadding = flueRowHorizontalPadding(launcherStyle, rowHeight = 56.dp)
    if (launcherStyle.uiStyle == UiStyle.MIUIX) {
        // The hidden-app page can compose a new row on nearly every fling frame.
        // Keep this common MIUIX switch row to one clickable node and one small
        // animated layer instead of nesting Card -> Row -> independently clickable
        // Switch (four animation states and two pointer-input chains per app).
        val interactionSource = remember { MutableInteractionSource() }
        val pressed by interactionSource.collectIsPressedAsState()
        val rowShape = launcherStyle.flueGroupShape(groupPosition, RoundElementRole.CAPSULE)
        val rowBackground = if (pressed) launcherStyle.pressedCardColor else launcherStyle.cardColor
        if (groupPosition.needsDivider) {
            // 组内非末行：分隔线要画在**卡内**，所以背景上移到外层 Column，行本身只留
            // 点击与内边距。多这一层只发生在成组的设置页上；上面那条 fling 路径
            // （隐藏应用页，永远是 Single）走的还是原来的单节点结构。
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .chordScaled(scale, enabled)
                    .clip(rowShape)
                    .background(rowBackground),
            ) {
                MiuixSwitchRowBody(
                    modifier = Modifier,
                    title = title,
                    subtitle = subtitle,
                    enabled = enabled,
                    checked = checked,
                    onToggle = onToggle,
                    leadingIcon = leadingIcon,
                    reserveLeadingIconSpace = reserveLeadingIconSpace,
                    horizontalPadding = horizontalPadding,
                    interactionSource = interactionSource,
                    pressed = pressed,
                    indicatorOnly = indicatorOnly,
                )
                FlueGroupDivider(groupPosition)
            }
        } else {
            MiuixSwitchRowBody(
                modifier = Modifier
                    .chordScaled(scale, enabled)
                    .background(color = rowBackground, shape = rowShape),
                title = title,
                subtitle = subtitle,
                enabled = enabled,
                checked = checked,
                onToggle = onToggle,
                leadingIcon = leadingIcon,
                reserveLeadingIconSpace = reserveLeadingIconSpace,
                horizontalPadding = horizontalPadding,
                interactionSource = interactionSource,
                pressed = pressed,
                indicatorOnly = indicatorOnly,
            )
        }
        return
    }
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val pressedScale = animateFloatAsState(
        if (pressed) 0.958f else 1f,
        animationSpec = launcherStyle.pressScaleSpec,
        label = "switch_row_scale"
    )
    val switchRowShape = launcherStyle.roundAwareCompactShape(RoundElementRole.CAPSULE)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale * pressedScale.value
                scaleY = scale * pressedScale.value
                alpha = if (enabled) scale.coerceIn(0.55f, 1f) else 0.5f
            }
            .clip(switchRowShape)
            .background(if (pressed) launcherStyle.pressedCardColor else launcherStyle.cardColor)
            .border(1.dp, launcherStyle.outlineColor, switchRowShape)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClick = { onToggle(!checked) }
            )
            .padding(horizontal = horizontalPadding, vertical = 14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                // **weight 而不是 fillMaxWidth(0.82f)**：定分数把开关挤到只剩 18% 行宽，
                // 而 AW 档的轨道是 64dp —— 窄屏上 18% 装不下，`Modifier.width(64.dp)`
                // 被上游约束夹小，轨道画短了、滑块反而溢出来。那就是老师看到的
                // 「开关右边显示不完全」。weight 让文字吃掉**剩下**的空间，
                // 尾部控件永远拿自己的自然宽度。
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (leadingIcon != null || reserveLeadingIconSpace) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(
                                launcherStyle.titleColor.copy(
                                    alpha = if (leadingIcon != null) 0f else 0.05f,
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (leadingIcon != null) {
                            Image(
                                bitmap = leadingIcon,
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                }
                Column {
                    Text(title, color = launcherStyle.titleColor, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(subtitle, color = launcherStyle.secondaryTextColor, fontSize = 12.sp)
                }
            }
            if (indicatorOnly) {
                FlueSwitchIndicator(checked = checked, enabled = enabled, pressed = pressed)
            } else {
                FlueSwitch(checked = checked, onCheckedChange = onToggle, enabled = enabled)
            }
        }
    }
}

/**
 * MIUIX 开关行的行体。
 *
 * 抽出来是为了让「成组（外层 Column 出背景 + 卡内分隔线）」与「独立卡（行自己出背景）」
 * 复用同一份内容，而不是把 60 行复制两遍 —— 抽的是 composable 函数，不引入布局节点，
 * 所以隐藏应用页那条 fling 路径的节点数与改动前一致。
 */
@Composable
private fun MiuixSwitchRowBody(
    modifier: Modifier,
    title: String,
    subtitle: String,
    enabled: Boolean,
    checked: Boolean,
    onToggle: (Boolean) -> Unit,
    leadingIcon: ImageBitmap?,
    reserveLeadingIconSpace: Boolean,
    horizontalPadding: Dp,
    interactionSource: MutableInteractionSource,
    pressed: Boolean,
    indicatorOnly: Boolean,
) {
    val launcherStyle = LauncherTheme.style
    Row(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 56.dp)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClick = { onToggle(!checked) },
            )
            .padding(horizontal = horizontalPadding, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leadingIcon != null || reserveLeadingIconSpace) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(
                        if (leadingIcon != null) Color.Transparent
                        else launcherStyle.pressedCardColor.copy(alpha = 0.6f),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (leadingIcon != null) {
                    Image(
                        bitmap = leadingIcon,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
        }
        Column(
            modifier = Modifier.weight(1f).padding(end = 12.dp),
        ) {
            Text(
                title,
                color = if (enabled) launcherStyle.titleColor else launcherStyle.secondaryTextColor,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
            )
            if (subtitle.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(subtitle, color = launcherStyle.secondaryTextColor, fontSize = 12.sp)
            }
        }
        if (indicatorOnly) {
            FlueSwitchIndicator(checked = checked, enabled = enabled, pressed = pressed)
        } else {
            FlueSwitch(checked = checked, onCheckedChange = onToggle, enabled = enabled)
        }
    }
}

/**
 * 滑块行的**名义纵向几何**。内边距在组合期就要交给 `Modifier.padding`，那时行还没测量，
 * 所以带区只能按声明值推 —— 而三档风格的 `capsuleMaxRadius` 都有上限（APPLE 32dp /
 * MD3 28dp / MIUIX 22dp），弧深不会随行高继续增长，名义值足够准。
 *
 * MIUIX 档：`MiuixSettingsRow`（最小 56dp）+ 24dp 轨道 + 14dp 底部留白。
 */
private val MIUIX_SLIDER_ROW_HEIGHT = 94.dp
private val MIUIX_SLIDER_TEXT_BAND_BOTTOM = 56.dp
private val MIUIX_SLIDER_TRACK_BOTTOM_GAP = 14.dp

/**
 * 通用档（APPLE / MD3）：14dp 上下留白 + 标题 18dp + 4dp + 数值 15dp + 8dp + M3 滑块 48dp。
 */
private val GENERIC_SLIDER_ROW_HEIGHT = 121.dp
private val GENERIC_SLIDER_VERTICAL_PADDING = 14.dp
private val GENERIC_SLIDER_TEXT_BAND_BOTTOM = 51.dp
private val GENERIC_SLIDER_TRACK_BAND_TOP = 59.dp

@Composable
internal fun SettingsSliderRow(
    title: String,
    value: Float,
    valueText: String,
    localValueText: ((Float) -> String)? = null,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: ((Float) -> Unit)? = null,
    scale: Float,
    enabled: Boolean = true,
    supportingText: String? = null,
    groupPosition: FlueGroupPosition = FlueGroupPosition.Single,
    /**
     * 滑杆下方、**同一张卡内**的附加内容（按钮等）。
     *
     * 存在的理由：像「整体缩放」这种改完要重启才生效的项，动作必须挨着那根滑杆 ——
     * 单独摆一行会读成「与上面无关的功能」。所以它属于这一行，不是下一行。
     */
    footer: (@Composable () -> Unit)? = null,
) {
    val launcherStyle = LauncherTheme.style
    // 滑块行有**两块内容，纵向位置完全不同**，所以要算两个内边距：
    //
    // - 「标题 + 数值」那块文字在行的上部；
    // - 轨道在行的**下部**（MIUIX 档 24dp 高、底沿距行底 14dp）。
    //
    // 以前两块共用一个按「44dp 内容居中于 100dp 行」算出来的值 —— 居中假设对轨道不成立，
    // 算出 0 弧深，而轨道真实位置正好压在下圆角的弧上。这就是老师报的「滑轨顶到胶囊边缘」。
    // 现在轨道走带区式弧深（见 FlueCapsuleGeometry.contentArcInsetForBandPx）。
    //
    // MIUIX 档的上部文字不用在这里算：`MiuixSettingsRow` 自己按 56dp 行查表。
    val trackPadding = flueRowHorizontalPadding(
        launcherStyle,
        rowHeight = MIUIX_SLIDER_ROW_HEIGHT,
        contentBand = MIUIX_SLIDER_TEXT_BAND_BOTTOM to
            (MIUIX_SLIDER_ROW_HEIGHT - MIUIX_SLIDER_TRACK_BOTTOM_GAP),
    )
    var localValue by remember(title) { mutableFloatStateOf(value) }
    LaunchedEffect(value) { localValue = value }
    if (launcherStyle.uiStyle == UiStyle.MIUIX) {
        // MIUIX：设置行显示标题与当前值，下面接胶囊滑块（FlueSlider 分发到 miuix 真组件）。
        MiuixSettingsCard(
            modifier = Modifier.chordScaled(scale, enabled),
            roundRole = RoundElementRole.CAPSULE,
            groupPosition = groupPosition,
        ) {
            MiuixSettingsRow(
                title = title,
                subtitle = localValueText?.invoke(localValue) ?: valueText,
                enabled = enabled
            )
            Box(
                modifier = Modifier
                    .padding(horizontal = trackPadding)
                    .padding(bottom = MIUIX_SLIDER_TRACK_BOTTOM_GAP)
            ) {
                FlueSlider(
                    value = localValue,
                    onValueChange = {
                        localValue = it
                        onValueChange(it)
                    },
                    valueRange = range,
                    steps = steps,
                    enabled = enabled,
                    onValueChangeFinished = {
                        onValueChangeFinished?.invoke(localValue)
                    }
                )
            }
            if (!supportingText.isNullOrBlank()) {
                Text(
                    text = supportingText,
                    color = launcherStyle.secondaryTextColor,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(
                        start = trackPadding,
                        end = trackPadding,
                        bottom = 12.dp,
                    ),
                )
            }
            footer?.let { content ->
                Box(
                    modifier = Modifier.padding(
                        start = trackPadding,
                        end = trackPadding,
                        bottom = 12.dp,
                    ),
                ) {
                    content()
                }
            }
        }
        return
    }
    val sliderShape = launcherStyle.roundAwareCompactShape(RoundElementRole.CAPSULE)
    // 通用档（APPLE / MD3）：整块自己画卡片，所以外层内边距按上部文字带给，
    // 轨道再单独补上它那条带比文字带多吃的那一截。
    val genericTextPadding = flueRowHorizontalPadding(
        launcherStyle,
        rowHeight = GENERIC_SLIDER_ROW_HEIGHT,
        contentBand = GENERIC_SLIDER_VERTICAL_PADDING to GENERIC_SLIDER_TEXT_BAND_BOTTOM,
    )
    val genericTrackPadding = flueRowHorizontalPadding(
        launcherStyle,
        rowHeight = GENERIC_SLIDER_ROW_HEIGHT,
        contentBand = GENERIC_SLIDER_TRACK_BAND_TOP to
            (GENERIC_SLIDER_ROW_HEIGHT - GENERIC_SLIDER_VERTICAL_PADDING),
    )
    val genericTrackExtraPadding = (genericTrackPadding - genericTextPadding)
        .coerceAtLeast(0.dp)
    if (launcherStyle.uiStyle == UiStyle.MATERIAL_3) {
        androidx.compose.material3.Surface(
            modifier = Modifier.fillMaxWidth().chordScaled(scale, enabled),
            shape = sliderShape,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ) {
            Column(
                modifier = Modifier.padding(
                    horizontal = genericTextPadding,
                    vertical = GENERIC_SLIDER_VERTICAL_PADDING,
                ),
            ) {
                Text(title, style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(4.dp))
                Text(
                    localValueText?.invoke(localValue) ?: valueText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                FlueSlider(
                    value = localValue,
                    onValueChange = {
                        localValue = it
                        onValueChange(it)
                    },
                    onValueChangeFinished = { onValueChangeFinished?.invoke(localValue) },
                    valueRange = range,
                    steps = steps,
                    enabled = enabled,
                    modifier = Modifier.padding(horizontal = genericTrackExtraPadding),
                )
                if (!supportingText.isNullOrBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = supportingText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = genericTrackExtraPadding),
                    )
                }
                footer?.let { content ->
                    Spacer(Modifier.height(8.dp))
                    Box(Modifier.padding(horizontal = genericTrackExtraPadding)) { content() }
                }
            }
        }
        return
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                alpha = if (enabled) scale.coerceIn(0.55f, 1f) else 0.5f
            }
            .clip(sliderShape)
            .background(launcherStyle.cardColor)
            .border(1.dp, launcherStyle.outlineColor, sliderShape)
            .padding(
                horizontal = genericTextPadding,
                vertical = GENERIC_SLIDER_VERTICAL_PADDING,
            )
    ) {
        Column {
            Text(title, color = launcherStyle.titleColor, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(4.dp))
            Text(localValueText?.invoke(localValue) ?: valueText, color = launcherStyle.secondaryTextColor, fontSize = 12.sp)
            Spacer(modifier = Modifier.height(8.dp))
            FlueSlider(
                value = localValue,
                onValueChange = {
                    localValue = it
                    onValueChange(it)
                },
                onValueChangeFinished = {
                    onValueChangeFinished?.invoke(localValue)
                },
                valueRange = range,
                steps = steps,
                enabled = enabled,
                modifier = Modifier.padding(horizontal = genericTrackExtraPadding),
            )
            if (!supportingText.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = supportingText,
                    color = launcherStyle.secondaryTextColor,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(horizontal = genericTrackExtraPadding),
                )
            }
            footer?.let { content ->
                Spacer(modifier = Modifier.height(8.dp))
                Box(modifier = Modifier.padding(horizontal = genericTrackExtraPadding)) {
                    content()
                }
            }
        }
    }
}

@Composable
internal fun ActionCard(
    title: String,
    subtitle: String? = null,
    icon: (@Composable () -> Unit)? = null,
    onClick: () -> Unit,
    onLongPress: (() -> Unit)? = null,
    longPressDurationMs: Long? = null,
    scale: Float,
    groupPosition: FlueGroupPosition = FlueGroupPosition.Single,
) {
    val launcherStyle = LauncherTheme.style
    if (launcherStyle.uiStyle == UiStyle.MATERIAL_3) {
        val materialPressedState = rememberPressedState()
        val materialPressed by materialPressedState
        // Keep the custom long-press timeout (including the import easter egg).
        // Standard clicks use Material ripple/semantics; timed long presses retain
        // their original gesture arbitration and use a tonal state layer instead.
        val actionModifier = if (onLongPress == null) {
            Modifier.clickable(onClick = onClick)
        } else {
            Modifier
                .background(
                    if (materialPressed) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                    else Color.Transparent,
                )
                .semantics {
                    onClick { onClick(); true }
                    onLongClick { onLongPress(); true }
                }
                .instantPressGesture(
                    materialPressedState,
                    onLongPress = onLongPress,
                    longPressDurationMs = longPressDurationMs,
                    onClick = onClick,
                )
        }
        MaterialSettingsListItem(
            title = title,
            subtitle = subtitle,
            scale = scale,
            headlineColor = MaterialTheme.colorScheme.primary,
            modifier = actionModifier,
            trailing = icon,
        )
        return
    }
    val horizontalPadding = flueRowHorizontalPadding(launcherStyle, rowHeight = 56.dp)
    val pressedState = rememberPressedState()
    val pressed by pressedState
    if (launcherStyle.uiStyle == UiStyle.MIUIX) {
        // MIUIX：动作行走强调色标题；保留长按支持（导入表盘彩蛋）。
        MiuixSettingsCard(
            modifier = Modifier.chordScaled(scale),
            roundRole = RoundElementRole.CAPSULE,
            groupPosition = groupPosition,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 56.dp)
                    .background(if (pressed) launcherStyle.pressedCardColor else Color.Transparent)
                    .instantPressGesture(
                        pressedState,
                        onLongPress = onLongPress,
                        longPressDurationMs = longPressDurationMs,
                        onClick = onClick
                    )
                    .padding(horizontal = horizontalPadding, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 12.dp)
                ) {
                    Text(title, color = launcherStyle.accentColor, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    if (!subtitle.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(subtitle, color = launcherStyle.secondaryTextColor, fontSize = 12.sp)
                    }
                }
                icon?.invoke()
            }
        }
        return
    }
    val pressedScale = animateFloatAsState(
        if (pressed) 0.96f else 1f,
        animationSpec = launcherStyle.pressScaleSpec,
        label = "action_card_scale"
    )
    val actionShape = launcherStyle.roundAwareCompactShape(RoundElementRole.CAPSULE)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale * pressedScale.value
                scaleY = scale * pressedScale.value
                alpha = scale.coerceIn(0.55f, 1f)
            }
            .clip(actionShape)
            .background(if (pressed) launcherStyle.pressedCardColor else launcherStyle.cardColor)
            .border(1.dp, launcherStyle.outlineColor, actionShape)
            .instantPressGesture(
                pressedState,
                onLongPress = onLongPress,
                longPressDurationMs = longPressDurationMs,
                onClick = onClick
            )
            .padding(horizontal = horizontalPadding, vertical = 14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth(0.82f)
                    .padding(end = 12.dp)
            ) {
                Text(title, color = launcherStyle.accentColor, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                if (!subtitle.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(subtitle, color = launcherStyle.secondaryTextColor, fontSize = 12.sp)
                }
            }
            icon?.invoke()
        }
    }
}
