package com.fluly.launcher.ui.settings

import android.os.SystemClock
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fluly.launcher.LocalSettingsLeftSafeInsetPercent
import com.fluly.launcher.LocalSettingsRotaryHapticsEnabled
import com.fluly.launcher.LocalSettingsSearchTarget
import com.fluly.launcher.R
import com.fluly.launcher.ui.common.isFlulyScreenRound
import com.fluly.launcher.ui.input.ROTARY_SCROLL_MULTIPLIER
import com.fluly.launcher.ui.input.RotaryHapticGate
import com.fluly.launcher.ui.input.flulyRotaryScrollable
import com.fluly.launcher.ui.input.requestFocusAfterFirstFrame
import com.fluly.launcher.ui.settings.miuix.MiuixIcons
import com.fluly.launcher.ui.settings.miuix.MiuixSettingsCard
import com.fluly.launcher.ui.settings.miuix.MiuixSettingsRow
import com.fluly.launcher.ui.style.FlulyGroupPosition
import com.fluly.launcher.ui.style.flulyOverscroll
import com.fluly.launcher.ui.style.flulyRowHorizontalPadding
import com.fluly.launcher.ui.style.flulyScrollEndHaptic
import com.fluly.launcher.ui.style.vibrateHaptic
import com.fluly.launcher.ui.theme.LauncherTheme
import com.fluly.launcher.ui.theme.ProvideGlobalUiScale
import com.fluly.launcher.ui.theme.RoundElementRole
import com.fluly.launcher.ui.theme.UiStyle
import com.fluly.launcher.ui.theme.roundAwareCardShape
import com.fluly.launcher.ui.theme.roundAwareCompactShape
import kotlin.math.abs
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import top.yukonga.miuix.kmp.icon.MiuixIcons as OfficialMiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back as OfficialBack

/**
 * 设置页的排版骨架。
 *
 * 所有屏共用同一个脚手架：顶栏（返回 + 大标题）、圆屏弦宽缩放通道、
 * 以及「key → index」影子登记器。它们必须和页面一起看待，
 * 因为滚动位置记忆与搜索跳转都依赖同一套 key 约定。
 */

/**
 * 脚手架顶栏（返回按钮 + 大标题）那一项的 key。
 *
 * 原先是匿名 `item {}`，圆屏下 `chordScaleForListItem` 靠
 * `visibleItemsInfo.firstOrNull { it.key == key }` 找行，没有 key 就永远找不到，
 * 顶栏因此不跟随缩放（老师报的「设置顶栏不会适配圆屏」）。
 *
 * [SettingsKeyIndexRecorder] 那边同步登记这个 key：它只关心「登记了几项」来对齐
 * index，用具名 key 替换原来的 null 不影响计数，还顺带让搜索跳转不会误命中。
 */
private const val SETTINGS_HEADER_ITEM_KEY = "settings_page_header"

/**
 * 顶栏项高度的一半（估值），圆屏首项居中要用。
 *
 * 顶栏 ≈ 42dp 的返回按钮 + 8dp 底距 ≈ 50dp（26sp 单行标题比按钮矮，撑不高这一项）。
 *
 * 这里刻意取**静态估值**而不是实测高度：把实测高度回头喂给 `contentPadding`
 * 就是「布局 → 内边距 → 重新布局」的反馈环，正是圆屏抽搐的根因。标题换成两行
 * 时首项会比正中略高一点点，可接受。
 */
private val SETTINGS_HEADER_HALF_HEIGHT = 25.dp

/**
 * 普通设置行高度的一半（估值），圆屏底部留白要用。
 *
 * 设置行的 `defaultMinSize(minHeight = 56.dp)` 是这套页面里最常见的行高，取半即
 * 28dp。底部留 `半屏 − 28dp` 之后，末项滑到底时中心大致落在屏幕正中。
 * 同样是静态估值，理由见 [SETTINGS_HEADER_HALF_HEIGHT]。
 */
private val SETTINGS_ROW_HALF_HEIGHT = 28.dp

/**
 * 只登记 key 顺序的影子 [LazyListScope]。
 *
 * 搜索结果要跳到具体某一行需要 key→index，但 LazyListState 只暴露「当前
 * 可见项」的 key，屏幕外的拿不到。把页面的 content 往这个影子 scope 里跑
 * 一遍就能拿到完整顺序——item 的 @Composable 内容一行都不会执行，只登记
 * key，所以没有额外开销也没有副作用（content 本身不是 @Composable）。
 */
private class SettingsKeyIndexRecorder : LazyListScope {
    private val keys = ArrayList<Any?>()

    fun indexOf(key: String): Int = keys.indexOfFirst { it == key }

    override fun item(
        key: Any?,
        contentType: Any?,
        content: @Composable LazyItemScope.() -> Unit,
    ) {
        keys += key
    }

    override fun items(
        count: Int,
        key: ((index: Int) -> Any)?,
        contentType: (index: Int) -> Any?,
        itemContent: @Composable LazyItemScope.(index: Int) -> Unit,
    ) {
        repeat(count) { index -> keys += key?.invoke(index) }
    }

    @ExperimentalFoundationApi
    override fun stickyHeader(
        key: Any?,
        contentType: Any?,
        content: @Composable LazyItemScope.(Int) -> Unit,
    ) {
        keys += key
    }
}

@Composable
internal fun SettingsPageScaffold(
    title: String,
    onBack: () -> Unit,
    headerTime: String,
    initialFirstVisibleItemIndex: Int = 0,
    initialFirstVisibleItemScrollOffset: Int = 0,
    roundFirstActionIndex: Int = 1,
    roundFirstActionHalfHeight: Dp = SETTINGS_ROW_HALF_HEIGHT,
    onScrollChanged: (Int, Int) -> Unit = { _, _ -> },
    content: LazyListScope.(LazyListState, Float, Float, Set<Any>) -> Unit
) {
    val launcherStyle = LauncherTheme.style
    val context = LocalContext.current
    val density = LocalDensity.current
    val settingsRoundScreen = com.fluly.launcher.ui.common.isFlulyScreenRound()
    val leftSafeInsetPercent = LocalSettingsLeftSafeInsetPercent.current
    val rotaryHapticsEnabled = LocalSettingsRotaryHapticsEnabled.current
    val initialPosition = remember(
        settingsRoundScreen,
        initialFirstVisibleItemIndex,
        initialFirstVisibleItemScrollOffset,
        roundFirstActionIndex,
    ) {
        settingsInitialPosition(
            isRound = settingsRoundScreen,
            savedIndex = initialFirstVisibleItemIndex,
            savedOffset = initialFirstVisibleItemScrollOffset,
            firstActionIndex = roundFirstActionIndex,
        )
    }
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = initialPosition.index,
        initialFirstVisibleItemScrollOffset = initialPosition.offset
    )
    val focusRequester = remember { FocusRequester() }
    val scope = rememberCoroutineScope()
    val rotaryHapticGate = remember { RotaryHapticGate() }
    val latestOnScrollChanged = rememberUpdatedState(onScrollChanged)

    LaunchedEffect(listState) {
        // Persist only when motion settles. Writing every pixel into caller state was the
        // dominant settings-scroll recomposition path, especially in the icon-heavy hidden
        // apps page. onDispose below covers navigation while a gesture is still settling.
        snapshotFlow { listState.isScrollInProgress }
            .distinctUntilChanged()
            .collect { scrolling ->
                if (!scrolling) {
                    latestOnScrollChanged.value(
                        listState.firstVisibleItemIndex,
                        listState.firstVisibleItemScrollOffset,
                    )
                }
            }
    }

    DisposableEffect(listState) {
        onDispose {
            latestOnScrollChanged.value(
                listState.firstVisibleItemIndex,
                listState.firstVisibleItemScrollOffset,
            )
        }
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocusAfterFirstFrame()
    }

    val rotaryScrollMultiplier = ROTARY_SCROLL_MULTIPLIER

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val screenHeightPx = constraints.maxHeight.toFloat()
        val screenCenterY = screenHeightPx / 2f
        // 圆屏弦宽适配要用视口全宽当直径。靠 CompositionLocal 下发而不是加
        // 参数：itemFisheye 有 200+ 调用点，加参数等于逐个改一遍。
        val screenWidthPx = constraints.maxWidth.toFloat()
        val leftSafeInset = maxWidth * (leftSafeInsetPercent.coerceIn(0, 50) / 100f)
        val safeDrawingPadding = WindowInsets.safeDrawing.asPaddingValues()
        var chordListTopInRoot by remember { mutableFloatStateOf(0f) }
        var chordScreenCenterY by remember { mutableFloatStateOf(screenCenterY) }
        val horizontalContentPadding = if (settingsRoundScreen) 26.dp else 16.dp
        val verticalContentPadding = if (settingsRoundScreen) 30.dp else 18.dp
        // 圆屏上下各留半屏（蜂窝列表观感）：首项在进页面时就落在屏幕正中，
        // 末项也能一直滑到正中。取 `maxHeight / 2 − 半个首项高度`。
        //
        // 这是**静态**值：只看屏幕尺寸，不随滚动变化，所以不违反「禁止动态
        // padding」那条铁律（禁的是「每帧按缩放结果改布局属性」的反馈环）。
        //
        // 另外靠 padding 让首项居中，而不是进页面再 scrollToItem 纠一次：
        // 偏移量为 0 时首项本来就在正中，既省掉一次首帧跳动，也不会跟
        // initialFirstVisibleItemScrollOffset 的滚动位置恢复打架。
        val halfScreenPadding = maxHeight / 2f
        val topContentPadding = if (settingsRoundScreen) {
            (halfScreenPadding - SETTINGS_HEADER_HALF_HEIGHT).coerceAtLeast(46.dp)
        } else {
            verticalContentPadding
        }
        val bottomContentPadding = if (settingsRoundScreen) {
            // 用**本页声明的**行半高，而不是写死的设置行常量。
            //
            // 「末项能滑到屏幕正中」要的是 `半屏 − 半个末项高度`。这一项一直用
            // SETTINGS_ROW_HALF_HEIGHT（28dp，普通设置行），可表盘选择页的条目是
            // 高得多的表盘卡，它自己已经通过 roundFirstActionHalfHeight 把真实半高
            // （40dp）报上来了 —— 只是以前只有居中那条 effect 用它，底部留白没用，
            // 于是这一页的末项落不到正中（老师报的「最后一项到屏幕中间」做不到）。
            //
            // 其余页面该参数取默认值，正是 SETTINGS_ROW_HALF_HEIGHT，行为不变。
            (halfScreenPadding - roundFirstActionHalfHeight).coerceAtLeast(30.dp)
        } else {
            verticalContentPadding
        }
        // 圆屏行距收窄：外圈的项缩到 0.62，槽位高度不变（graphicsLayer 的固有
        // 代价），10dp 槽位间距叠上收缩留白就是老师说的「间隔太大」。槽位间距
        // 压到 6dp 抵消一部分，居中那几行也还不至于挤在一起。
        val itemSpacing = if (settingsRoundScreen) 6.dp else 10.dp

        // 依赖里必须带上 density。
        //
        // 居中偏移是按**当前 density** 把 dp 折成 px 算的，而 density 由
        // `ProvideGlobalUiScale` 按设置里的全局缩放改写。缩放值来自 DataStore 的 Flow，
        // 首帧拿到的是默认 100%，真值随后才到 —— 那一刻 initialPosition 和
        // screenHeightPx 都没变，这个 effect 不会重跑，于是首项一直停在「按 100% 算
        // 出来的」那个偏移上：页面顶/底拉不到屏幕中间，进一次二级页再回来（scaffold
        // 重新组合、listState 重建）才正常，重进设置又坏（老师报的）。
        //
        // screenCenterY 是 px、与 density 无关，但一并列出来更能说明这条 effect
        // 依赖的是「屏幕几何 + 折算比例」这一整套。
        //
        // roundFirstActionHalfHeight 同理必须在列表里：它被读进 itemHalfHeight，
        // 各页取值不同（表盘选择页是 40dp，其余走默认）。漏掉它就是 R10 那个
        // 「读了却没进 key」的同一族错误，只是换了个量。
        LaunchedEffect(
            initialPosition,
            screenHeightPx,
            screenCenterY,
            density,
            roundFirstActionHalfHeight,
        ) {
            if (initialPosition.centerOnScreen) {
                val centerOffset = settingsCenteredItemScrollOffset(
                    screenCenterY = screenCenterY,
                    itemHalfHeight = with(density) { roundFirstActionHalfHeight.toPx() },
                )
                listState.scrollToItem(initialPosition.index, centerOffset)
            }
        }

        // 搜索结果跳转定位。目标 key 不属于本页时 indexOf 返回 -1，本页
        // 什么都不做（转场期间新旧两页同时在树上，靠这条区分）。
        val searchTarget = LocalSettingsSearchTarget.current
        LaunchedEffect(searchTarget?.token, searchTarget?.itemKey) {
            val target = searchTarget ?: return@LaunchedEffect
            val recorder = SettingsKeyIndexRecorder()
            // 标题项也要登记，否则算出来的 index 会整体偏移。
            recorder.item(SETTINGS_HEADER_ITEM_KEY, null) {}
            with(recorder) {
                content(listState, screenCenterY, screenHeightPx, emptySet())
            }
            val index = recorder.indexOf(target.itemKey)
            if (index < 0) return@LaunchedEffect
            // 目标行滚到靠上位置（留一点上文），而不是贴着顶边。
            // 圆屏改成滚到屏幕正中：那里弦最宽、缩放为 1，行本身也最清楚；
            // 靠上 18% 的位置在圆屏上恰好是被弧线切得最狠的一段。
            val jumpOffset = if (settingsRoundScreen) {
                -(screenCenterY - with(density) { SETTINGS_ROW_HALF_HEIGHT.toPx() })
            } else {
                -(screenHeightPx * 0.18f)
            }
            listState.animateScrollToItem(index, scrollOffset = jumpOffset.toInt())
            target.onConsumed()
        }

        CompositionLocalProvider(
            com.fluly.launcher.ui.common.LocalChordViewportWidth provides screenWidthPx,
            com.fluly.launcher.ui.common.LocalChordListTopInViewport provides chordListTopInRoot,
            com.fluly.launcher.ui.common.LocalChordScreenCenterY provides chordScreenCenterY,
            com.fluly.launcher.ui.style.LocalFlulySettingsRowSpacing provides itemSpacing,
        ) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .onGloballyPositioned { coordinates ->
                    chordListTopInRoot = coordinates.positionInRoot().y
                    chordScreenCenterY = coordinates.findRootCoordinates().size.height / 2f
                }
                .flulyRotaryScrollable(
                    focusRequester,
                    rotaryScrollMultiplier,
                    onRotaryDelta = { rotaryDelta ->
                        scope.launch {
                            val consumed = listState.scrollBy(-rotaryDelta)
                            if (
                                rotaryHapticsEnabled &&
                                abs(consumed) > 0.01f &&
                                rotaryHapticGate.tryAcquire(SystemClock.elapsedRealtime())
                            ) {
                                vibrateHaptic(context)
                            }
                        }
                    },
                ) { rotaryDelta ->
                    scope.launch {
                        listState.scrollBy(-rotaryDelta)
                    }
                }
                .flulyOverscroll(listState)
                // 触底反馈挂在回弹之后（更靠内层）才收得到边缘余量，见 flulyScrollEndHaptic。
                .flulyScrollEndHaptic(),
            // The list owns the whole edge-to-edge viewport. Insets only move its items, so
            // the static background still paints behind round corners and transparent bars.
            // Keep a single scrollable top/bottom inset; the old modifier padding + bottom
            // contentPadding + tail spacer stacked into permanent empty bands.
            contentPadding = PaddingValues(
                start = horizontalContentPadding + leftSafeInset,
                // 圆屏那条分支单独取值、不再叠 safeDrawing：半屏留白本来就远大于
                // 任何系统 inset，叠上去只会把首项顶得偏离屏幕中心。
                top = if (settingsRoundScreen) {
                    topContentPadding
                } else {
                    safeDrawingPadding.calculateTopPadding() + topContentPadding
                },
                end = horizontalContentPadding,
                // 行距改由每行自己出（见 FlulySettingsListScope）之后，末行也带着一份，
                // 所以这里扣掉同样的一份 —— 底部总留白与改动前一致。
                bottom = (
                    if (settingsRoundScreen) {
                        bottomContentPadding
                    } else {
                        safeDrawingPadding.calculateBottomPadding() + bottomContentPadding
                    } - itemSpacing
                    ).coerceAtLeast(0.dp),
            ),
            // 间距归零：交给 FlulySettingsListScope 逐行给。verticalArrangement 是整表统一的，
            // 给不出「这两行贴住成一张卡、那两行留 12dp」，而那正是 MIUIX 档要的观感。
            verticalArrangement = Arrangement.Top,
        ) {
            val spacedScope = com.fluly.launcher.ui.style.FlulySettingsListScope(this, itemSpacing)
            spacedScope.item(SETTINGS_HEADER_ITEM_KEY) {
                // 占位 Spacer：保证滚动测量与跳转索引一致，真实标题由顶部浮动层随滚动缩放移动
                Spacer(modifier = Modifier.fillMaxWidth().height(if (settingsRoundScreen) 56.dp else 48.dp))
            }
            with(spacedScope) {
                content(listState, screenCenterY, screenHeightPx, emptySet())
            }
        }
        }

        // 统一可折叠标题浮动层：同一组元素随滚动变换，紧凑态 18sp，返回键视觉 80%，点击区 48dp
        val headerPlaceholderHeight = if (settingsRoundScreen) 56.dp else 48.dp
        val collapseDistancePx = with(density) { headerPlaceholderHeight.toPx() }
        val collapseProgress by remember(listState) {
            derivedStateOf {
                if (listState.firstVisibleItemIndex > 0) 1f
                else (listState.firstVisibleItemScrollOffset / collapseDistancePx).coerceIn(0f, 1f)
            }
        }
        val topSafeInset = safeDrawingPadding.calculateTopPadding()
        val gradientHeight = topSafeInset + 64.dp
        val gradientAlpha = (0.75f + 0.25f * collapseProgress).coerceIn(0f, 1f)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(gradientHeight)
                .background(
                    androidx.compose.ui.graphics.Brush.verticalGradient(
                        colors = listOf(
                            launcherStyle.screenBackground.copy(alpha = gradientAlpha),
                            launcherStyle.screenBackground.copy(alpha = gradientAlpha * 0.9f),
                            launcherStyle.screenBackground.copy(alpha = gradientAlpha * 0.4f),
                            androidx.compose.ui.graphics.Color.Transparent,
                        ),
                    ),
                ),
        )

        val compactTop = if (settingsRoundScreen) 6.dp else topSafeInset + 4.dp
        val expandedTop = if (settingsRoundScreen) 22.dp else topSafeInset + 16.dp
        val currentTop = androidx.compose.ui.unit.lerp(expandedTop, compactTop, collapseProgress)
        val largeTitleSp = if (settingsRoundScreen) 24f else 26f
        val compactTitleSp = 18f
        val titleScale = 1f + (largeTitleSp / compactTitleSp - 1f) * (1f - collapseProgress)
        val backVisualScale = 1f - 0.20f * collapseProgress
        val horizontalSafeInset = if (settingsRoundScreen) 24.dp else 16.dp

        // 圆屏展开态标题必须回到屏幕中心（老师报的「标题跑到左边」）。这里从 Row
        // 换成 Box：返回键降级为左侧覆盖层，标题独立占满整行、两侧留同样宽度 ——
        // 只有标题的可用框关于屏幕中线**对称**，TextAlign.Center 才真的落在表盘中心。
        // 之前用 Row + weight(1f)，标题框被返回键挤到右边，Center 也会整体偏右。
        //
        // 收缩要「丝滑收进左上角」：TextAlign 是离散枚举，没法按进度插值，用阈值
        // 切换会在滚动中点跳一下。所以文字始终保持 Center，靠 translationX 把
        // 「居中位置 → 左对齐位置」这条位移按 collapseProgress 连续插值。
        // 位移量 = (可用宽度 - 文字宽度) / 2，两个宽都由 onSizeChanged 实测。
        var headerInnerWidthPx by remember { mutableFloatStateOf(0f) }
        var headerTitleWidthPx by remember { mutableFloatStateOf(0f) }
        val titleDriftPx = if (settingsRoundScreen) {
            ((headerInnerWidthPx - headerTitleWidthPx) / 2f).coerceAtLeast(0f)
        } else {
            0f
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .offset { androidx.compose.ui.unit.IntOffset(0, with(density) { currentTop.toPx() }.toInt()) }
                .padding(horizontal = horizontalSafeInset + leftSafeInset),
        ) {
            if (launcherStyle.uiStyle == UiStyle.MATERIAL_3) {
                androidx.compose.material3.IconButton(
                    onClick = onBack,
                    modifier = Modifier.align(Alignment.CenterStart).size(48.dp),
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.common_back),
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.graphicsLayer {
                            scaleX = backVisualScale
                            scaleY = backVisualScale
                        },
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .size(48.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onBack,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .graphicsLayer {
                                scaleX = backVisualScale
                                scaleY = backVisualScale
                            }
                            .clip(CircleShape)
                            .background(launcherStyle.topBarChipColor),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            OfficialMiuixIcons.OfficialBack,
                            contentDescription = stringResource(R.string.common_back),
                            tint = launcherStyle.topBarTextColor,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }

            }

            // 标题用「外层 Box 居中 + 内层 Text 自身宽度」两段测量：
            // headerInnerWidthPx 是去掉返回键占位后**可用的整行宽度**，
            // headerTitleWidthPx 是文字**实际渲染宽度**。两者之差的一半，正好是
            // 「居中位置」到「左对齐位置」的距离，按 collapseProgress 线性插值后
            // 写成 translationX，就是老师要的平滑收进左上角。
            Box(
                modifier = Modifier
                    // Box 不再提供 Row 的 CenterVertically，这里显式垂直居中，
                    // 否则标题会贴到 48dp 返回键的顶边。
                    .align(if (settingsRoundScreen) Alignment.Center else Alignment.CenterStart)
                    .fillMaxWidth()
                    // 左侧 56dp = 返回键 48dp + 原来的 8dp 间距，别让文字压到按钮上。
                    // 圆屏右侧再留同样宽度，标题框才关于屏幕中线对称。
                    .padding(
                        start = 56.dp,
                        end = if (settingsRoundScreen) 56.dp else 0.dp,
                    )
                    .onSizeChanged { headerInnerWidthPx = it.width.toFloat() },
                contentAlignment = if (settingsRoundScreen) Alignment.Center else Alignment.CenterStart,
            ) {
                Text(
                    text = title,
                    color = if (launcherStyle.uiStyle == UiStyle.MATERIAL_3) {
                        MaterialTheme.colorScheme.onSurface
                    } else launcherStyle.accentColor,
                    style = if (launcherStyle.uiStyle == UiStyle.MATERIAL_3) {
                        MaterialTheme.typography.titleLarge
                    } else MaterialTheme.typography.titleMedium,
                    fontSize = 18.sp,
                    fontWeight = if (launcherStyle.uiStyle == UiStyle.MATERIAL_3) FontWeight.Normal else FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .onSizeChanged { headerTitleWidthPx = it.width.toFloat() }
                        .graphicsLayer {
                            translationX = -titleDriftPx * collapseProgress
                            scaleX = titleScale
                            scaleY = titleScale
                            // 展开态从中心缩放（贴合居中观感），收缩态改为从左边缩放
                            // （贴合左上角紧凑观感）。同样按进度插值。
                            transformOrigin = androidx.compose.ui.graphics.TransformOrigin(
                                if (settingsRoundScreen) 0.5f - 0.5f * collapseProgress else 0f,
                                0.5f,
                            )
                        },
                )
            }
        }
    }
}

@Composable
private fun HeaderBackButton(onClick: () -> Unit) {
    val launcherStyle = LauncherTheme.style
    if (launcherStyle.uiStyle == UiStyle.MATERIAL_3) {
        androidx.compose.material3.IconButton(onClick = onClick) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.common_back),
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
        return
    }
    val pressedState = rememberPressedState()
    val pressed by pressedState
    val scale = animateFloatAsState(if (pressed) 0.94f else 1f, label = "header_back_scale")
    Box(
        modifier = Modifier
            .size(42.dp)
            .graphicsLayer { scaleX = scale.value; scaleY = scale.value }
            .clip(CircleShape)
            .background(launcherStyle.topBarChipColor)
            .instantPressGesture(pressedState, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        // 返回图标统一用 miuix 官方的 Back（细尖角箭头），与表盘市场 / 编辑器 /
        // 文件浏览器的顶栏一致。Material 那枚自动镜像返回箭头是带横杆的实心造型，
        // 混在 MIUIX 档里线宽和体量都对不上，所以设置页不再引用它。
        Icon(
            OfficialMiuixIcons.OfficialBack,
            contentDescription = stringResource(R.string.common_back),
            tint = launcherStyle.topBarTextColor,
        )
    }
}

// Material settings use official components and state layers. Chord scaling is geometry,
// not a press animation: keep it even though Material rows no longer shrink on touch.
@Composable
internal fun MaterialSettingsListItem(
    title: String,
    subtitle: String?,
    scale: Float,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    headlineColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    val horizontalPadding = flulyRowHorizontalPadding(LauncherTheme.style, rowHeight = 56.dp)
    androidx.compose.material3.Surface(
        modifier = Modifier.fillMaxWidth().chordScaled(scale, enabled),
        shape = LauncherTheme.style.roundAwareCompactShape(RoundElementRole.CAPSULE),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        androidx.compose.material3.ListItem(
            headlineContent = { Text(title, style = MaterialTheme.typography.bodyLarge) },
            supportingContent = subtitle?.takeIf { it.isNotBlank() }?.let { text ->
                { Text(text, style = MaterialTheme.typography.bodyMedium) }
            },
            leadingContent = leading,
            trailingContent = trailing,
            colors = androidx.compose.material3.ListItemDefaults.colors(
                containerColor = Color.Transparent,
                headlineColor = headlineColor,
                supportingColor = MaterialTheme.colorScheme.onSurfaceVariant,
                leadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                trailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
            modifier = modifier.padding(
                horizontal = (horizontalPadding - 16.dp).coerceAtLeast(0.dp),
            ),
        )
    }
}

@Composable
internal fun SettingsCategoryCard(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    scale: Float,
    groupPosition: FlulyGroupPosition = FlulyGroupPosition.Single,
) {
    val launcherStyle = LauncherTheme.style
    if (launcherStyle.uiStyle == UiStyle.MATERIAL_3) {
        MaterialSettingsListItem(
            title = title,
            subtitle = subtitle,
            scale = scale,
            modifier = Modifier.clickable(onClick = onClick),
            trailing = { Icon(Icons.Filled.ChevronRight, contentDescription = null) },
        )
        return
    }
    val horizontalPadding = if (com.fluly.launcher.ui.common.isFlulyScreenRound()) 24.dp else 18.dp
    if (launcherStyle.uiStyle == UiStyle.MIUIX) {
        // MIUIX：成组大圆角卡片包一行设置行 + Forward 箭头。
        MiuixSettingsCard(
            modifier = Modifier.chordScaled(scale),
            roundRole = RoundElementRole.CAPSULE,
            groupPosition = groupPosition,
        ) {
            MiuixSettingsRow(
                title = title,
                subtitle = subtitle.takeIf { it.isNotBlank() },
                onClick = onClick,
                trailing = {
                    Icon(
                        imageVector = MiuixIcons.Forward,
                        contentDescription = null,
                        tint = launcherStyle.secondaryTextColor,
                        modifier = Modifier.size(18.dp)
                    )
                }
            )
        }
        return
    }
    val pressedState = rememberPressedState()
    val pressed by pressedState
    val pressedScale = animateFloatAsState(
        if (pressed) 0.958f else 1f,
        animationSpec = launcherStyle.pressScaleSpec,
        label = "settings_category_scale"
    )
    val background = animateColorAsState(
        if (pressed) launcherStyle.pressedCardColor else launcherStyle.cardColor,
        label = "settings_category_bg"
    )
    // 另两档风格不做行合并（flulyGroupingEnabled 只对 MIUIX 为真），所以这里的
    // groupPosition 必定是 Single，形状与改动前逐位相同。
    val cardShape = launcherStyle.roundAwareCardShape(RoundElementRole.CAPSULE)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale * pressedScale.value
                scaleY = scale * pressedScale.value
                alpha = scale.coerceIn(0.55f, 1f)
            }
            .clip(cardShape)
            .drawBehind { drawRect(background.value) }
            .border(1.dp, launcherStyle.outlineColor, cardShape)
            .instantPressGesture(pressedState, onClick = onClick)
            .padding(horizontal = horizontalPadding, vertical = 18.dp)
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
                Text(title, color = launcherStyle.titleColor, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(4.dp))
                Text(subtitle, color = launcherStyle.secondaryTextColor, fontSize = 13.sp)
            }
            Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = launcherStyle.accentColor)
        }
    }
}

@Composable
internal fun SectionTitle(text: String, scale: Float) {
    val launcherStyle = LauncherTheme.style
    // 圆屏：组标题改居中并去掉左内边距。左对齐的标题贴着弧线，实测「系统设置」
    // 会被切成「⼄设置」。这是静态判断（只看屏幕形状，不随滚动变化），不属于
    // 「动态 padding」那条禁令。
    val roundScreen = com.fluly.launcher.ui.common.isFlulyScreenRound()
    // 标题与分组名同样吃弦宽缩放；alpha 跟着收一点，让贴近弧线的标题自然淡出。
    val scaleModifier = Modifier.graphicsLayer {
        scaleX = scale
        scaleY = scale
        alpha = scale.coerceIn(0.55f, 1f)
    }
    if (launcherStyle.uiStyle == UiStyle.MATERIAL_3) {
        Text(
            text = text,
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.titleSmall,
            textAlign = if (roundScreen) TextAlign.Center else null,
            modifier = Modifier
                .then(if (roundScreen) Modifier.fillMaxWidth() else Modifier)
                .padding(top = 8.dp, start = if (roundScreen) 0.dp else 16.dp, bottom = 4.dp)
                .then(scaleModifier),
        )
        return
    }
    if (launcherStyle.uiStyle == UiStyle.MIUIX) {
        // MIUIX：组标题小灰字，对齐 MiuixSettingsCard 的 title 风格。
        Text(
            text = text,
            color = launcherStyle.secondaryTextColor,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            textAlign = if (roundScreen) TextAlign.Center else null,
            modifier = Modifier
                .then(if (roundScreen) Modifier.fillMaxWidth() else Modifier)
                .padding(top = 8.dp, start = if (roundScreen) 0.dp else 14.dp)
                .then(scaleModifier)
        )
        return
    }
    Text(
        text = text,
        color = launcherStyle.secondaryTextColor,
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        textAlign = if (roundScreen) TextAlign.Center else null,
        modifier = Modifier
            .then(if (roundScreen) Modifier.fillMaxWidth() else Modifier)
            .padding(top = 4.dp, start = if (roundScreen) 0.dp else 4.dp, bottom = 2.dp)
            .then(scaleModifier)
    )
}

@Composable
internal fun rememberPressedState(): MutableState<Boolean> {
    val state = remember { mutableStateOf(false) }
    DisposableEffect(Unit) {
        onDispose { state.value = false }
    }
    return state
}

internal fun Modifier.instantPressGesture(
    pressedState: MutableState<Boolean>,
    enabled: Boolean = true,
    onLongPress: (() -> Unit)? = null,
    longPressDurationMs: Long? = null,
    onClick: () -> Unit
): Modifier {
    if (!enabled) return this
    if (onLongPress != null && longPressDurationMs != null) {
        return pointerInput(onClick, onLongPress, enabled, longPressDurationMs) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false)
                pressedState.value = true
                var completedBeforeTimeout = false
                val up = withTimeoutOrNull(longPressDurationMs) {
                    waitForUpOrCancellation().also { completedBeforeTimeout = true }
                }
                if (completedBeforeTimeout) {
                    pressedState.value = false
                    if (up != null) onClick()
                } else {
                    pressedState.value = false
                    onLongPress()
                    waitForUpOrCancellation()
                }
            }
        }
    }
    return pointerInput(onClick, onLongPress, enabled) {
        var longPressConsumed = false
        detectTapGestures(
            onLongPress = onLongPress?.let { handler ->
                {
                    longPressConsumed = true
                    pressedState.value = false
                    handler()
                }
            },
            onPress = {
                longPressConsumed = false
                pressedState.value = true
                val released = tryAwaitRelease()
                pressedState.value = false
                if (released && !longPressConsumed) onClick()
            }
        )
    }
}

@Composable
internal fun MessageCard(text: String, background: Color, onClick: () -> Unit) {
    val launcherStyle = LauncherTheme.style
    if (launcherStyle.uiStyle == UiStyle.MATERIAL_3) {
        androidx.compose.material3.Surface(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ) {
            Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(16.dp))
        }
        return
    }
    val pressedState = rememberPressedState()
    val pressed by pressedState
    val pressedScale = animateFloatAsState(
        if (pressed) 0.964f else 1f,
        animationSpec = launcherStyle.pressScaleSpec,
        label = "message_card_scale"
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = pressedScale.value
                scaleY = pressedScale.value
            }
            .clip(launcherStyle.roundAwareCompactShape())
            .background(background)
            .instantPressGesture(pressedState, onClick = onClick)
            .padding(14.dp)
    ) {
        Text(text = text, color = launcherStyle.bodyColor, fontSize = 12.sp)
    }
}

/**
 * 把弦宽缩放施加到某个组件上的统一入口。
 *
 * 几个设置行组件在 MIUIX 分支里提前 `return`，各自的 `scale` 参数一次都没用到，
 * 圆屏下那条分支就会整页平铺（老师报的「miuix 还是平铺」）。通用路径里那段
 * `graphicsLayer { scaleX/scaleY/alpha }` 抄五遍不现实，抽成这个 modifier。
 *
 * 只改合成属性，不碰任何布局属性 —— 弦宽适配的第三条铁律。
 */
internal fun Modifier.chordScaled(scale: Float, enabled: Boolean = true): Modifier =
    graphicsLayer {
        scaleX = scale
        scaleY = scale
        alpha = if (enabled) scale.coerceIn(0.55f, 1f) else 0.48f
    }

@Composable
internal fun itemFisheye(
    listState: LazyListState,
    key: String,
    screenCenterY: Float,
    screenHeight: Float
): Float {
    if (!com.fluly.launcher.ui.common.isFlulyScreenRound()) return 1f
    val rootCenterY = com.fluly.launcher.ui.common.LocalChordScreenCenterY.current
    return com.fluly.launcher.ui.common.chordScale(
        listState = listState,
        key = key,
        screenCenterY = rootCenterY.takeIf(Float::isFinite) ?: screenCenterY,
        viewportWidth = com.fluly.launcher.ui.common.LocalChordViewportWidth.current,
        listTopInViewport = com.fluly.launcher.ui.common.LocalChordListTopInViewport.current,
    )
}
