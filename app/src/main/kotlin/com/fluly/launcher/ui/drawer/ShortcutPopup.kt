package com.fluly.launcher.ui.drawer

import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.content.pm.ShortcutInfo
import android.net.Uri
import android.os.Build
import android.os.Process
import android.provider.Settings
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.res.stringResource
import com.fluly.launcher.R
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fluly.launcher.data.model.AppInfo
import com.fluly.launcher.ui.style.FlulyAlertDialog
import com.fluly.launcher.ui.style.flulyOverscroll
import com.fluly.launcher.ui.theme.LauncherTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun AppShortcutOverlay(
    app: AppInfo,
    blurEnabled: Boolean = true,
    onExcludeApp: (() -> Unit)? = null,
    onRemoveShortcut: (() -> Unit)? = null,
    onRenameFolder: ((String) -> Unit)? = null,
    onDissolveFolder: (() -> Unit)? = null,
    onTransitionProgress: (Float) -> Unit = {},
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val appDetailsPackage = if (app.isBuiltInSettingsEntry) context.packageName else app.packageName
    val appShortcuts = rememberAppShortcuts(app)

    var showing by remember { mutableStateOf(false) }
    var dismissing by remember { mutableStateOf(false) }
    var renameDialogVisible by remember { mutableStateOf(false) }
    var renameText by remember(app.componentKey) { mutableStateOf(app.label) }
    val dismissInteraction = remember { MutableInteractionSource() }
    val blockInteraction = remember { MutableInteractionSource() }
    val panelScrollState = rememberScrollState()

    fun animateDismiss() {
        dismissing = true
        showing = false
    }

    LaunchedEffect(Unit) { showing = true }

    BackHandler(enabled = showing && !dismissing) {
        animateDismiss()
    }

    // 关门契约与 AppFolderOverlay 完全对齐：卸载时机由 alpha 真正落到 0 决定，
    // 不再用 delay(250) 猜。旧写法的两条时间线是错开的——alpha 走 tween(200)
    // 淡完之后，还要空等 50ms 才 onDismiss()，而列表背景模糊是挂在
    // actionApp != null 上的另一条 tween(180)，只有 onDismiss() 之后才开始退。
    // 结果就是菜单已经看不见了，模糊还满着，然后才突兀地自己化开。
    // 现在两侧同用一条 alpha：菜单淡多少，模糊就退多少。
    val animAlpha by animateFloatAsState(
        targetValue = if (showing && !dismissing) 1f else 0f,
        animationSpec = tween(180),
        label = "overlay_alpha",
        finishedListener = { finalAlpha ->
            if (dismissing && finalAlpha <= 0.001f) {
                onDismiss()
            }
        },
    )
    val animScale by animateFloatAsState(
        targetValue = if (showing && !dismissing) 1f else 0.92f,
        animationSpec = spring(dampingRatio = 0.78f, stiffness = 520f),
        label = "overlay_scale"
    )
    // **进度要在能观察到动画的地方上报**。
    //
    // 原来写的是 `SideEffect { onTransitionProgress(animAlpha) }`。SideEffect 的 lambda 在组合
    // 应用之后才执行，里面的 State 读**不算进重组作用域** —— 动画每帧推进都不会让这个 composable
    // 重组，于是这一句只在 showing / dismissing 变化那两次组合里跑到：
    //
    // - 长按打开：那一次组合时 alpha 才刚起步（≈0），报出去的就是 0 —— 列表全程不模糊；
    // - 按返回 / 点空白：这一次组合读到的是早就跑满的 1，模糊**突然出现**；
    // - 紧接着 overlay 卸载，列表侧把进度清 0，模糊又**突然消失**。
    //
    // 正好是老师描述的现象。snapshotFlow 会真正订阅这个 State，每帧都 emit 一次。
    val reportProgress by rememberUpdatedState(onTransitionProgress)
    LaunchedEffect(Unit) {
        snapshotFlow { animAlpha }.collect { reportProgress(it) }
    }

    if (renameDialogVisible) {
        FlulyAlertDialog(
            onDismissRequest = { renameDialogVisible = false },
            title = { Text(stringResource(R.string.shortcut_menu_rename_folder_title)) },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onRenameFolder?.invoke(renameText)
                        renameDialogVisible = false
                        animateDismiss()
                    }
                ) {
                    Text(stringResource(R.string.shortcut_menu_rename_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { renameDialogVisible = false }) {
                    Text(stringResource(R.string.shortcut_menu_rename_cancel))
                }
            }
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = animAlpha }
            .background(Color.Black.copy(alpha = 0.72f))
            .clickable(indication = null, interactionSource = dismissInteraction) { animateDismiss() }
            .consumeOverlayPointers(),
        contentAlignment = Alignment.Center
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            // dp 本身就是密度无关单位——以前这里再乘一次 densityDpi/320 并
            // 用 180dp 下限兜底，高密度表上 0.76×maxWidth 低于 180dp 被强行
            // 拉宽到近乎满屏，菜单文字贴弦被切。整块只按屏宽比例取，绝不
            // 超过屏宽。
            val panelWidth = (maxWidth * 0.78f).coerceAtMost(312.dp)
            val panelMaxHeight = maxHeight * 0.86f

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .align(Alignment.Center)
                    .width(panelWidth)
                    .heightIn(max = panelMaxHeight)
                    .padding(horizontal = 8.dp, vertical = 12.dp)
                    .flulyOverscroll(panelScrollState)
                    .verticalScroll(panelScrollState)
                    .graphicsLayer {
                        scaleX = animScale
                        scaleY = animScale
                    }
                    .clickable(indication = null, interactionSource = blockInteraction) { }
                    .consumeOverlayPointers()
            ) {
                Spacer(modifier = Modifier.height(8.dp))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(LauncherTheme.style.cardShape)
                        .background(Color(0xFF2C2C2E))
                ) {
                    if (app.isFolder) {
                        ShortcutMenuItem(stringResource(R.string.shortcut_menu_rename)) {
                            renameText = app.label
                            renameDialogVisible = true
                        }
                        onDissolveFolder?.let { dissolve ->
                            Box(modifier = Modifier.fillMaxWidth().height(0.5.dp).background(Color(0xFF48484A)))
                            ShortcutMenuItem(
                                stringResource(R.string.shortcut_menu_dissolve_folder),
                                Color(0xFFFF9F0A),
                            ) {
                                dissolve()
                                onDismiss()
                            }
                        }
                    } else {
                        // 应用自带快捷方式（Android 7.1 的 Shortcuts），排在管理类动作之前，
                        // 与系统长按菜单的顺序一致：快捷动作优先。手表上多数 app 没提供，
                        // 查不到就整块不出现，不留空行也不留分隔线。
                        appShortcuts.forEach { shortcut ->
                            ShortcutMenuItem(
                                text = shortcut.shortLabel?.toString().orEmpty()
                                    .ifBlank { shortcut.id },
                            ) {
                                launchAppShortcut(context, app, shortcut)
                                onDismiss()
                            }
                            ShortcutDivider()
                        }
                        ShortcutMenuItem(stringResource(R.string.shortcut_menu_app_info)) {
                            context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = Uri.parse("package:$appDetailsPackage")
                            })
                            onDismiss()
                        }
                        // 从应用列表里隐藏这个应用，与设置→隐藏应用写的是同一份数据。
                        // 内置设置入口不可隐藏（隐藏了就没有入口再改回来），
                        // 快捷启动项本身由「移除」处理，这里都不显示。
                        if (!app.isBuiltInSettingsEntry && !app.isAppListShortcut) {
                            onExcludeApp?.let { excludeApp ->
                                Box(modifier = Modifier.fillMaxWidth().height(0.5.dp).background(Color(0xFF48484A)))
                                ShortcutMenuItem(
                                    stringResource(R.string.shortcut_menu_exclude_app),
                                    Color(0xFFFF9F0A),
                                ) {
                                    excludeApp()
                                    onDismiss()
                                }
                            }
                        }
                        if (app.isAppListShortcut) {
                            onRemoveShortcut?.let { removeShortcut ->
                                Box(modifier = Modifier.fillMaxWidth().height(0.5.dp).background(Color(0xFF48484A)))
                                ShortcutMenuItem(
                                    stringResource(R.string.shortcut_menu_remove),
                                    Color(0xFFFF9F0A),
                                ) {
                                    removeShortcut()
                                    onDismiss()
                                }
                            }
                        } else {
                            onRemoveShortcut?.let { removeShortcut ->
                                Box(modifier = Modifier.fillMaxWidth().height(0.5.dp).background(Color(0xFF48484A)))
                                ShortcutMenuItem(
                                    stringResource(R.string.shortcut_menu_remove_from_quick_launch),
                                    Color(0xFFFF9F0A),
                                ) {
                                    removeShortcut()
                                    onDismiss()
                                }
                            }
                            if (!app.isBuiltInSettingsEntry) {
                                Box(modifier = Modifier.fillMaxWidth().height(0.5.dp).background(Color(0xFF48484A)))
                                ShortcutMenuItem(
                                    stringResource(R.string.shortcut_menu_uninstall),
                                    Color(0xFFFF453A),
                                ) {
                                    val packageUri = Uri.fromParts("package", app.packageName, null)
                                    val intents = listOf(
                                        Intent(Intent.ACTION_DELETE, packageUri).apply {
                                            putExtra(Intent.EXTRA_RETURN_RESULT, false)
                                            putExtra(Intent.EXTRA_USER, Process.myUserHandle())
                                        }
                                    )
                                    val fallbackIntent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                        data = packageUri
                                    }
                                    val launched = intents.any { baseIntent ->
                                        val intent = baseIntent.apply {
                                            if (context !is android.app.Activity) {
                                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                            }
                                        }
                                        val resolved = intent.resolveActivity(context.packageManager) != null
                                        if (!resolved) return@any false
                                        runCatching {
                                            context.startActivity(intent)
                                        }.onFailure {
                                            Log.w("ShortcutPopup", "Failed to launch uninstall intent for ${app.packageName}", it)
                                        }.isSuccess
                                    }
                                    if (!launched) {
                                        runCatching {
                                            if (context !is android.app.Activity) {
                                                fallbackIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                            }
                                            context.startActivity(fallbackIntent)
                                        }.onFailure {
                                            Log.w("ShortcutPopup", "Failed to open app details for ${app.packageName}", it)
                                        }
                                    }
                                    onDismiss()
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Image(
                    bitmap = app.cachedIcon,
                    contentDescription = null,
                    modifier = Modifier
                        .size(88.dp)
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(app.label, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.W600)
                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }
}

private fun Modifier.consumeOverlayPointers(): Modifier = pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Final)
            event.changes.forEach { it.consume() }
        }
    }
}

/** 菜单里最多列几条应用快捷方式。再多就把整块挤成必须滚动的列表。 */
private const val MAX_APP_SHORTCUTS = 3

/**
 * 读取应用自带的快捷方式（Android 7.1 的 Shortcuts）。
 *
 * 只有**默认启动器**才有权限读（`LauncherApps.hasShortcutHostPermission()`）——
 * 本项目带 `CATEGORY_HOME`，正常情况下成立；用户临时切到别的启动器时返回 false，
 * 这里静默返回空列表，菜单少几行而已，不该报错也不该提示。
 *
 * 刻意在组件内部查而不是让宿主传进来：这个菜单有 6 个调用点（蜂窝 / 密网格 /
 * 列表 / 原生列表 / 文件夹 / 侧屏），为一条可选条目在每处穿一遍回调不划算。
 * 代价是打开菜单时多一次 binder 往返，本地调用，几毫秒。
 */
@Composable
private fun rememberAppShortcuts(app: AppInfo): List<ShortcutInfo> {
    val context = LocalContext.current
    // 文件夹、列表快捷方式、内置设置入口都不是真实可启动的应用，没有快捷方式可查。
    val eligible = !app.isFolder && !app.isAppListShortcut && !app.isBuiltInSettingsEntry
    var shortcuts by remember(app.componentKey) { mutableStateOf(emptyList<ShortcutInfo>()) }

    LaunchedEffect(app.componentKey, eligible) {
        if (!eligible) return@LaunchedEffect
        shortcuts = withContext(Dispatchers.IO) {
            runCatching {
                val launcherApps = context.getSystemService(LauncherApps::class.java)
                    ?: return@runCatching emptyList<ShortcutInfo>()
                if (!launcherApps.hasShortcutHostPermission()) {
                    return@runCatching emptyList<ShortcutInfo>()
                }
                val query = LauncherApps.ShortcutQuery()
                    .setPackage(app.packageName)
                    .setQueryFlags(
                        LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or
                            LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST or
                            LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED,
                    )
                launcherApps.getShortcuts(query, Process.myUserHandle())
                    .orEmpty()
                    .filter { it.isEnabled }
                    .take(MAX_APP_SHORTCUTS)
            }.getOrDefault(emptyList())
        }
    }
    return shortcuts
}

private fun launchAppShortcut(context: Context, app: AppInfo, shortcut: ShortcutInfo) {
    val launcherApps = context.getSystemService(LauncherApps::class.java) ?: return
    runCatching {
        launcherApps.startShortcut(
            app.packageName,
            shortcut.id,
            null,
            null,
            Process.myUserHandle(),
        )
    }.onFailure {
        Log.w("ShortcutPopup", "启动快捷方式失败: ${app.packageName}/${shortcut.id}", it)
    }
}

@Composable
private fun ShortcutDivider() {
    Box(modifier = Modifier.fillMaxWidth().height(0.5.dp).background(Color(0xFF48484A)))
}

@Composable
private fun ShortcutMenuItem(text: String, color: Color = Color.White, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Text(
            text,
            color = color,
            fontSize = 15.sp,
            fontWeight = FontWeight.W500,
            // 应用快捷方式的文案由各家 app 自定，可能很长。菜单条目本来就是单行，
            // 截断比换行撑高整块更好——高度一变，圆屏上的滚动位置也会跟着跳。
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

