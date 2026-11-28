package com.fluly.launcher.ui.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.graphics.drawable.toBitmap
import com.fluly.launcher.ABOUT_VERSION
import com.fluly.launcher.AUTHOR_BILIBILI_URL
import com.fluly.launcher.AUTHOR_QQ_NUMBER
import com.fluly.launcher.COMMUNITY_GROUP_2_NUMBER
import com.fluly.launcher.COMMUNITY_GROUP_NUMBER
import com.fluly.launcher.OPEN_SOURCE_PROJECTS
import com.fluly.launcher.R
import com.fluly.launcher.diagnostics.DiagnosticsInformationSection
import com.fluly.launcher.diagnostics.DiagnosticsSettingsSection
import com.fluly.launcher.layout.FLULY_LAYOUT_PROFILE_NAME_MAX_LENGTH
import com.fluly.launcher.ui.common.MAX_OVERLAY_MASK_CORNER_RADIUS_DP
import com.fluly.launcher.ui.common.isFlulyScreenRound
import com.fluly.launcher.ui.common.normalizeOverlayMaskCornerRadiusDp
import com.fluly.launcher.ui.style.FlulyAlertDialog
import com.fluly.launcher.ui.style.FlulyDialogButtons
import com.fluly.launcher.ui.style.FlulyFullScreenOverlay
import com.fluly.launcher.ui.style.FlulySlider
import com.fluly.launcher.ui.style.flulySettingsGroup
import com.fluly.launcher.ui.theme.LauncherTheme
import com.fluly.launcher.ui.theme.UiStyle
import com.fluly.launcher.ui.theme.roundAwareCardShape
import com.fluly.launcher.viewmodel.LauncherViewModel
import com.fluly.launcher.watchface.standard.WatchWeatherRepository
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/**
 * 关于、调试、捐赠三屏的自定义内容。
 *
 * 这三屏的条目不是「一个值 + 一个回调」，而是版本号、开源项目列表、
 * 捐赠二维码这类静态排版，因此保留手写，只把渲染部分搬到这里。
 */

@Composable
internal fun AboutCard(
    scale: Float,
    onDonateClick: () -> Unit,
    onVersionClick: () -> Unit
) {
    val launcherStyle = LauncherTheme.style
    val context = LocalContext.current
    // 两个交流群的选择弹窗。做成弹窗而不是并排两张卡：关于页在 230dp 圆屏上已经很挤，
    // 多一张卡就要多滚一屏，而「进哪个群」是个二选一的一次性决定，弹窗更合适。
    var groupPickerVisible by remember { mutableStateOf(false) }
    // QQ 的邀请链接带一次性 authKey，发到群里或用久了就会失效，所以只留群号：
    // 点一下复制，用户自己在 QQ 里搜索。和作者 QQ 用的是同一套交互。
    fun copyGroupNumber(number: String) {
        groupPickerVisible = false
        val clipboard = context.getSystemService(ClipboardManager::class.java)
        clipboard?.setPrimaryClip(ClipData.newPlainText("QQ", number))
        Toast.makeText(
            context,
            context.getString(R.string.about_group_copy_fmt, number),
            Toast.LENGTH_SHORT,
        ).show()
    }
    if (groupPickerVisible) {
        FlulyAlertDialog(
            onDismissRequest = { groupPickerVisible = false },
            title = { Text(stringResource(R.string.about_pick_group_title)) },
            confirmButton = {
                TextButton(onClick = { copyGroupNumber(COMMUNITY_GROUP_NUMBER) }) {
                    Text(stringResource(R.string.about_pick_group_1_fmt, COMMUNITY_GROUP_NUMBER))
                }
            },
            dismissButton = {
                TextButton(onClick = { copyGroupNumber(COMMUNITY_GROUP_2_NUMBER) }) {
                    Text(stringResource(R.string.about_pick_group_2_fmt, COMMUNITY_GROUP_2_NUMBER))
                }
            },
        )
    }
    fun openAuthorProfile() {
        context.startActivity(
            Intent(
                Intent.ACTION_VIEW,
                android.net.Uri.parse(AUTHOR_BILIBILI_URL)
            )
        )
    }
    fun copyAuthorQq() {
        val clipboard = context.getSystemService(ClipboardManager::class.java)
        clipboard?.setPrimaryClip(ClipData.newPlainText("QQ", AUTHOR_QQ_NUMBER))
        Toast.makeText(context, context.getString(R.string.about_qq_copy_fmt, AUTHOR_QQ_NUMBER), Toast.LENGTH_SHORT).show()
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                alpha = scale.coerceIn(0.55f, 1f)
            }
            // 关于页这张卡是整块竖内容（图标 + 版本 + 三个操作），宽高比远小于
            // RowCapsuleShape 的阈值，圆屏下会自动退回 cardShape 的大圆角。挂着是为了
            // 「圆屏形状取值统一走这个入口」，不是指望它变胶囊。
            .clip(launcherStyle.roundAwareCardShape())
            .background(launcherStyle.cardColor)
            .then(
                if (launcherStyle.uiStyle == UiStyle.MATERIAL_3) Modifier
                else Modifier.border(1.dp, launcherStyle.outlineColor, launcherStyle.roundAwareCardShape()),
            )
            .padding(horizontal = 20.dp, vertical = 24.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // painterResource 不支持 AdaptiveIconDrawable：启动图标是自适应
            // 图标时（mipmap-anydpi-v26），进入关于页会直接抛
            // IllegalArgumentException 闪退。走 PackageManager 拿 Drawable
            // 再栅格化，任何图标类型都安全。
            val appIconBitmap = remember {
                runCatching {
                    context.packageManager.getApplicationIcon(context.packageName)
                        .toBitmap(width = 192, height = 192)
                        .asImageBitmap()
                }.getOrNull()
            }
            Box(
                modifier = Modifier
                    .size(98.dp)
                    .clip(launcherStyle.cardShape)
                    .background(launcherStyle.titleColor.copy(alpha = 0.06f)),
                contentAlignment = Alignment.Center
            ) {
                if (appIconBitmap != null) {
                    Image(
                        bitmap = appIconBitmap,
                        contentDescription = null,
                        modifier = Modifier
                            .size(76.dp),
                        // PackageManager 已按当前系统遮罩渲染 AdaptiveIconDrawable，
                        // 这里不再二次裁圆角，避免前景被重复缩小或截边。
                        contentScale = ContentScale.Fit
                    )
                } else {
                    Text(stringResource(R.string.app_name), color = launcherStyle.titleColor, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(modifier = Modifier.height(14.dp))
            Text(stringResource(R.string.app_name), color = launcherStyle.titleColor, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                ABOUT_VERSION,
                color = launcherStyle.secondaryTextColor,
                fontSize = 12.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onVersionClick
                    )
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            )
            Spacer(modifier = Modifier.height(22.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painter = painterResource(id = R.drawable.author_avatar),
                    contentDescription = null,
                    modifier = Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .clickable(onClick = ::openAuthorProfile)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        stringResource(R.string.about_author_name),
                        color = launcherStyle.titleColor,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(onClick = ::openAuthorProfile)
                            .padding(horizontal = 2.dp, vertical = 1.dp)
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        "QQ：$AUTHOR_QQ_NUMBER",
                        color = launcherStyle.secondaryTextColor,
                        fontSize = 11.sp,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(onClick = ::copyAuthorQq)
                            .padding(horizontal = 2.dp, vertical = 1.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(18.dp))
            ActionCard(
                title = stringResource(R.string.about_join_group),
                subtitle = stringResource(
                    R.string.about_community_groups_fmt,
                    COMMUNITY_GROUP_NUMBER,
                    COMMUNITY_GROUP_2_NUMBER,
                ),
                // 不再直接跳 1 群：两个群都要能进，点击先问一句（见 groupPickerVisible）。
                onClick = { groupPickerVisible = true },
                scale = 1f
            )
            Spacer(modifier = Modifier.height(10.dp))
            ActionCard(
                title = stringResource(R.string.about_donate),
                subtitle = stringResource(R.string.about_donate_methods),
                onClick = onDonateClick,
                scale = 1f
            )
            Spacer(modifier = Modifier.height(18.dp))
            Text(
                text = stringResource(R.string.about_open_source),
                color = launcherStyle.titleColor,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(8.dp))
            OPEN_SOURCE_PROJECTS.forEachIndexed { index, project ->
                ActionCard(
                    title = project.name,
                    subtitle = project.url,
                    onClick = {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, android.net.Uri.parse(project.url))
                        )
                    },
                    scale = 1f,
                )
                if (index != OPEN_SOURCE_PROJECTS.lastIndex) {
                    Spacer(modifier = Modifier.height(10.dp))
                }
            }
        }
    }
}

@Composable
internal fun DebugPage(
    vm: LauncherViewModel,
    weatherRepository: WatchWeatherRepository,
    onBack: () -> Unit,
    onCrash: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var cacheStatus by remember { mutableStateOf(context.getString(R.string.diagnostics_cache_idle)) }
    SettingsPageScaffold(
        title = stringResource(R.string.diagnostics_title),
        onBack = onBack,
        headerTime = "",
        initialFirstVisibleItemIndex = 0,
        initialFirstVisibleItemScrollOffset = 0,
        onScrollChanged = { _, _ -> },
    ) { listState, screenCenterY, screenHeightPx, _ ->
        flulySettingsGroup {
            row("debug_crash") { position ->
                ActionCard(
                    title = stringResource(R.string.diagnostics_crash),
                    subtitle = stringResource(R.string.diagnostics_crash_description),
                    onClick = onCrash,
                    scale = itemFisheye(listState, "debug_crash", screenCenterY, screenHeightPx),
                    groupPosition = position,
                )
            }
            row("debug_missing_watchface") { position ->
                ActionCard(
                    title = stringResource(R.string.diagnostics_missing_face),
                    subtitle = stringResource(R.string.diagnostics_missing_face_description),
                    onClick = vm::selectMissingWatchFaceForDebug,
                    scale = itemFisheye(listState, "debug_missing_watchface", screenCenterY, screenHeightPx),
                    groupPosition = position,
                )
            }
            row("debug_restore_watchface") { position ->
                ActionCard(
                    title = stringResource(R.string.diagnostics_restore_face),
                    subtitle = stringResource(R.string.diagnostics_restore_face_description),
                    onClick = { vm.fallbackToBuiltIn("debug restore") },
                    scale = itemFisheye(listState, "debug_restore_watchface", screenCenterY, screenHeightPx),
                    groupPosition = position,
                )
            }
            row("debug_refresh_cache") { position ->
                ActionCard(
                    title = stringResource(R.string.diagnostics_refresh_cache),
                    subtitle = cacheStatus,
                    onClick = {
                        scope.launch {
                            cacheStatus = runCatching {
                                vm.refreshWatchFaces(force = true)
                                weatherRepository.requestRefresh()
                                context.getString(R.string.diagnostics_cache_refreshed, 0)
                            }.getOrElse { context.getString(R.string.diagnostics_cache_failed) }
                        }
                    },
                    scale = itemFisheye(listState, "debug_refresh_cache", screenCenterY, screenHeightPx),
                    groupPosition = position,
                )
            }
        }
        item("debug_performance") { DiagnosticsSettingsSection(vm) }
        item("debug_information") { DiagnosticsInformationSection(vm) }
    }
}

@Composable
internal fun DonateMethodCard(
    title: String,
    subtitle: String,
    resId: Int,
    scale: Float,
    onPreviewClick: () -> Unit
) {
    val pressedState = rememberPressedState()
    val pressed by pressedState
    val launcherStyle = LauncherTheme.style
    if (launcherStyle.uiStyle == UiStyle.MATERIAL_3) {
        androidx.compose.material3.Surface(
            onClick = onPreviewClick,
            modifier = Modifier.fillMaxWidth().chordScaled(scale),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(16.dp))
                Image(
                    painter = painterResource(id = resId),
                    contentDescription = title,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium),
                )
            }
        }
        return
    }
    val pressedScale = animateFloatAsState(
        if (pressed) 0.962f else 1f,
        animationSpec = spring(stiffness = 820f, dampingRatio = 0.74f),
        label = "donate_card_scale"
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale * pressedScale.value
                scaleY = scale * pressedScale.value
                alpha = scale.coerceIn(0.55f, 1f)
            }
            .clip(launcherStyle.cardShape)
            .background(launcherStyle.cardColor)
            .border(1.dp, launcherStyle.outlineColor, launcherStyle.cardShape)
            .instantPressGesture(pressedState, onClick = onPreviewClick)
            .padding(horizontal = 18.dp, vertical = 18.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, color = launcherStyle.titleColor, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(4.dp))
            Text(subtitle, color = launcherStyle.secondaryTextColor, fontSize = 12.sp)
            Spacer(modifier = Modifier.height(16.dp))
            Image(
                painter = painterResource(id = resId),
                contentDescription = title,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxWidth()
                    // 卡内嵌套的表面比卡本身小一档，用 compactShape 而不是又编一个数字。
                    .clip(launcherStyle.compactShape)
                    .background(launcherStyle.pressedCardColor)
            )
        }
    }
}

/**
 * 档案重命名弹窗（布局档案与动效档案共用）。
 *
 * 后端早就有 `renameActiveLayoutProfile` / `renameActiveMotionProfile`，缺的只是入口 ——
 * 所以这里只做一件事：收一个名字交给它们。空名不提交（改成空白等于把档案变成无名）。
 */
@Composable
internal fun ProfileRenameDialog(
    currentName: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    val style = LauncherTheme.style
    var text by remember(currentName) { mutableStateOf(currentName) }
    val trimmed = text.trim()
    FlulyAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_profile_rename), color = style.titleColor) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.take(FLULY_LAYOUT_PROFILE_NAME_MAX_LENGTH) },
                singleLine = true,
                shape = style.rowShape,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { if (trimmed.isNotEmpty()) onConfirm(trimmed) },
                enabled = trimmed.isNotEmpty(),
            ) {
                Text(stringResource(android.R.string.ok), color = style.accentColor)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel), color = style.secondaryTextColor)
            }
        },
    )
}


@Composable
internal fun OverlayMaskCornerRadiusDialog(
    currentRadiusDp: Int,
    onDismiss: () -> Unit,
    onSave: (Int) -> Unit,
) {
    val launcherStyle = LauncherTheme.style
    val dialogSurface = MaterialTheme.colorScheme.surface
    val dialogOnSurface = MaterialTheme.colorScheme.onSurface
    val dialogOnSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    var localRadiusDp by remember(currentRadiusDp) {
        mutableFloatStateOf(currentRadiusDp.toFloat())
    }
    val previewRadiusDp = localRadiusDp
        .coerceIn(0f, MAX_OVERLAY_MASK_CORNER_RADIUS_DP.toFloat())
        .dp
    val interactionSource = remember { MutableInteractionSource() }

    // Non-scrolling sheet: scrollableContent = false ensures the horizontal slider works.
    FlulyFullScreenOverlay(
        onDismissRequest = onDismiss,
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            // 实时展示调整的圆角大小：全屏幕边缘的蓝色高亮边框指示线
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .border(
                        width = 3.dp,
                        color = Color(0xFF2196F3),
                        shape = RoundedCornerShape(previewRadiusDp),
                    ),
            )

            // 中央圆角设置调整卡片
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .clip(launcherStyle.cardShape)
                    .background(dialogSurface)
                    .padding(horizontal = 16.dp, vertical = 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = stringResource(R.string.dialog_overlay_mask_corner_title),
                    color = dialogOnSurface,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.dialog_overlay_mask_corner_subtitle),
                    color = dialogOnSurfaceVariant,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(16.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(launcherStyle.compactShape)
                        .background(dialogOnSurface.copy(alpha = 0.055f))
                        .padding(horizontal = 8.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .clickable(
                                interactionSource = interactionSource,
                                indication = null,
                            ) {
                                localRadiusDp = (localRadiusDp - 1f).coerceAtLeast(0f)
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("−", color = dialogOnSurfaceVariant, fontSize = 30.sp)
                    }
                    FlulySlider(
                        value = localRadiusDp,
                        onValueChange = {
                            localRadiusDp = it.coerceIn(
                                0f,
                                MAX_OVERLAY_MASK_CORNER_RADIUS_DP.toFloat(),
                            )
                        },
                        valueRange = 0f..MAX_OVERLAY_MASK_CORNER_RADIUS_DP.toFloat(),
                        modifier = Modifier.weight(1f),
                    )
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .clickable(
                                interactionSource = interactionSource,
                                indication = null,
                            ) {
                                localRadiusDp = (localRadiusDp + 1f)
                                    .coerceAtMost(MAX_OVERLAY_MASK_CORNER_RADIUS_DP.toFloat())
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("+", color = dialogOnSurfaceVariant, fontSize = 30.sp)
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(
                        R.string.dialog_overlay_mask_corner_value_fmt,
                        localRadiusDp.roundToInt(),
                    ),
                    color = dialogOnSurface,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.dialog_overlay_mask_corner_hint),
                    color = dialogOnSurfaceVariant,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(16.dp))
                FlulyDialogButtons(
                    confirmText = stringResource(R.string.action_done),
                    onConfirm = {
                        onSave(normalizeOverlayMaskCornerRadiusDp(localRadiusDp.roundToInt()))
                    },
                    dismissText = stringResource(R.string.action_cancel),
                    onDismiss = onDismiss,
                )
            }
        }
    }
}

@Composable
internal fun DonatePreviewDialog(resId: Int, onDismiss: () -> Unit) {
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val transformState = rememberTransformableState { _, zoomChange, panChange, _ ->
        val newScale = (scale * zoomChange).coerceIn(1f, 4f)
        scale = newScale
        offset = if (newScale <= 1.02f) {
            Offset.Zero
        } else {
            offset + panChange
        }
    }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false,
            // 全屏看图要真的顶到屏幕边，否则顶部一条不被 98% 黑覆盖。
            decorFitsSystemWindows = false,
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.98f)),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(id = resId),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = offset.x
                        translationY = offset.y
                    }
                    .transformable(transformState)
                    .pointerInput(scale) {
                        detectTapGestures(
                            onDoubleTap = {
                                if (scale > 1.05f) {
                                    scale = 1f
                                    offset = Offset.Zero
                                } else {
                                    scale = 2.2f
                                    offset = Offset.Zero
                                }
                            }
                        )
                    }
                    .pointerInput(scale) {
                        detectDragGesturesAfterLongPress(
                            onDrag = { change, dragAmount ->
                                if (scale > 1.02f) {
                                    change.consume()
                                    offset += Offset(dragAmount.x, dragAmount.y)
                                }
                            }
                        )
                    }
            )
        }
    }
}
