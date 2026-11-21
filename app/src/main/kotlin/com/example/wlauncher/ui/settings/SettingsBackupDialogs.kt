package com.flue.launcher.ui.settings

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.flue.launcher.BlockingProgressState
import com.flue.launcher.R
import com.flue.launcher.backup.FlueBackupExportSummary
import com.flue.launcher.backup.FlueBackupOptions
import com.flue.launcher.ui.common.isFlueScreenRound
import com.flue.launcher.ui.style.FlueLoadingIndicator
import com.flue.launcher.ui.theme.LauncherTheme
import com.flue.launcher.ui.theme.ProvideDialogUiScale
import top.yukonga.miuix.kmp.icon.MiuixIcons as OfficialMiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back as OfficialBack
import top.yukonga.miuix.kmp.icon.extended.Ok as OfficialOk

/** 备份内容勾选项的七个行 key。导出/导入两屏共用同一批条目。 */
internal val BackupOptionRows: Set<String> = setOf(
    "backup_settings",
    "backup_app_order",
    "backup_widget_order",
    "backup_side_screen_order",
    "backup_photo_watchface",
    "backup_video_watchface",
    "backup_watch_files",
)

/**
 * 行 key → [FlueBackupOptions] 字段。
 *
 * 反过来（字段 → 行 key）由数据层负责：这类映射写两遍就会不同步，
 * 但方向相反，没法共用一份。这里只保留「从 UI 反查字段」这一个方向，
 * 因为勾选与可用性判断都发生在渲染层。
 */
internal fun FlueBackupOptions.valueOfRow(rowId: String): Boolean = when (rowId) {
    "backup_settings" -> settings
    "backup_app_order" -> appOrder
    "backup_widget_order" -> widgetOrder
    "backup_side_screen_order" -> sideScreenOrder
    "backup_photo_watchface" -> photoWatchFace
    "backup_video_watchface" -> videoWatchFace
    "backup_watch_files" -> watchFiles
    else -> false
}

/** 勾选一行，返回新的选项；未知 key 原样返回。 */
internal fun FlueBackupOptions.withRow(rowId: String, value: Boolean): FlueBackupOptions =
    when (rowId) {
        "backup_settings" -> copy(settings = value)
        "backup_app_order" -> copy(appOrder = value)
        "backup_widget_order" -> copy(widgetOrder = value)
        "backup_side_screen_order" -> copy(sideScreenOrder = value)
        "backup_photo_watchface" -> copy(photoWatchFace = value)
        "backup_video_watchface" -> copy(videoWatchFace = value)
        "backup_watch_files" -> copy(watchFiles = value)
        else -> this
    }

internal fun FlueBackupOptions.backupSummaryText(context: Context): String {
    val labels = selectedSectionKeys().map { key -> context.getString(backupSectionLabelRes(key)) }
    return labels.takeIf { it.isNotEmpty() }?.joinToString(" · ")
        ?: context.getString(R.string.backup_summary_none)
}

/**
 * 分区 key → 展示名。顺序由 [FlueBackupOptions.selectedSectionKeys] 定义，
 * 名称只在这里映射一次，导出摘要与勾选列表共用。
 */
@StringRes
internal fun backupSectionLabelRes(key: String): Int = when (key) {
    FlueBackupOptions.SECTION_SETTINGS -> R.string.backup_item_settings
    FlueBackupOptions.SECTION_APP_ORDER -> R.string.backup_item_app_order
    FlueBackupOptions.SECTION_WIDGET_ORDER -> R.string.backup_item_widget_order
    FlueBackupOptions.SECTION_SIDE_SCREEN_ORDER -> R.string.backup_item_side_screen_order
    FlueBackupOptions.SECTION_PHOTO_WATCH_FACE -> R.string.backup_item_photo_watchface
    FlueBackupOptions.SECTION_VIDEO_WATCH_FACE -> R.string.backup_item_video_watchface
    FlueBackupOptions.SECTION_WATCH_FILES -> R.string.backup_item_watch_files
    else -> R.string.backup_summary_none
}

/** 「导出完成」页 ✅ 下面那三行灰字：内容 / 大小 / 时间。 */
internal fun FlueBackupExportSummary.exportDoneDetailText(context: Context): String = listOf(
    context.getString(R.string.backup_export_detail_sections_fmt, sections.backupSummaryText(context)),
    context.getString(
        R.string.backup_export_detail_size_fmt,
        android.text.format.Formatter.formatShortFileSize(context, bytes),
    ),
    context.getString(R.string.backup_export_detail_time_fmt, createdAt),
).joinToString("\n")

@Composable
internal fun BlockingProgressDialog(
    state: BlockingProgressState,
    onDismiss: () -> Unit,
    onShare: () -> Unit
) {
    val launcherStyle = LauncherTheme.style
    val flueScreenRound = isFlueScreenRound()
    Dialog(
        onDismissRequest = { if (state.completed) onDismiss() },
        properties = DialogProperties(
            dismissOnBackPress = state.completed,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false,
            // 全屏 Dialog 必须自己接管 insets：默认 true 时 Dialog 的 Window 会去避让
            // 状态栏，窗口顶边被系统下移，让出的那一条露出 Dialog 默认遮罩（看起来像
            // 顶部空了一条半透明黑）。下面 Column 已经用 safeDrawing 自己让位了。
            decorFitsSystemWindows = false,
        )
    ) {
        BackHandler(enabled = state.completed) { onDismiss() }
        ProvideDialogUiScale {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(launcherStyle.screenBackground),
        ) {
            // 二级页面结构：顶部标题栏，内容居中，底部胶囊按钮。
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(WindowInsets.safeDrawing.asPaddingValues())
                    .padding(horizontal = if (flueScreenRound) 26.dp else 16.dp),
            ) {
                Spacer(modifier = Modifier.height(if (flueScreenRound) 30.dp else 18.dp))
                // 标题栏：完成态左对齐「返回」按钮，进行中无按钮。
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                ) {
                    if (state.completed) {
                        val pressedState = rememberPressedState()
                        val pressed by pressedState
                        val scale = animateFloatAsState(
                            if (pressed) 0.94f else 1f,
                            label = "progress_back_scale"
                        )
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .graphicsLayer { scaleX = scale.value; scaleY = scale.value }
                                .clip(CircleShape)
                                .background(launcherStyle.topBarChipColor)
                                .instantPressGesture(pressedState, onClick = onDismiss),
                            contentAlignment = Alignment.Center,
                        ) {
                            // 和 HeaderBackButton 用同一个 miuix 官方 Back：这颗胶囊
                            // 就是设置页顶栏返回键的复刻，两处必须是同一枚箭头。
                            Icon(
                                OfficialMiuixIcons.OfficialBack,
                                contentDescription = null,
                                tint = launcherStyle.topBarTextColor
                            )
                        }
                    }
                    Text(
                        text = state.title,
                        color = launcherStyle.accentColor,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = if (flueScreenRound) TextAlign.Center else TextAlign.Start,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = if (flueScreenRound) {
                            Modifier
                                .align(Alignment.Center)
                                .fillMaxWidth()
                                .padding(horizontal = 50.dp)
                        } else {
                            Modifier
                                .align(Alignment.CenterStart)
                                .fillMaxWidth()
                                .padding(start = 54.dp, end = 16.dp)
                        },
                    )
                }

                // 内容区：居中转圈 / 完成图标 + 副标题。
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        if (state.completed) {
                            Icon(
                                imageVector = OfficialMiuixIcons.OfficialOk,
                                contentDescription = null,
                                tint = launcherStyle.accentColor,
                                modifier = Modifier.size(64.dp)
                            )
                        } else {
                            // 与表盘加载页同一个抽象入口，三档各转各自的圈。
                            FlueLoadingIndicator(
                                size = 32.dp,
                            )
                        }
                        if (!state.detail.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = state.detail,
                                color = launcherStyle.secondaryTextColor,
                                fontSize = 14.sp,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }

                // 底部按钮区：完成态显示「分享」+「完成」胶囊按钮。
                if (state.completed) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        if (state.shareUri != null) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(54.dp)
                                    .clip(RoundedCornerShape(999.dp))
                                    .background(launcherStyle.cardColor)
                                    .clickable(onClick = onShare),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = stringResource(R.string.action_share),
                                    color = launcherStyle.accentColor,
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(54.dp)
                                .clip(RoundedCornerShape(999.dp))
                                .background(launcherStyle.accentColor)
                                .clickable(onClick = onDismiss),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = stringResource(R.string.action_done),
                                color = Color.White,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(if (flueScreenRound) 30.dp else 18.dp))
            }
        }
        } // ProvideDialogUiScale
    }
}
