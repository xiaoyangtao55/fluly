package com.flue.launcher.ui.smartstack

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.provider.Settings
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.mediarouter.app.SystemOutputSwitcherDialogController
import com.flue.launcher.FlueApplication
import com.flue.launcher.R
import com.flue.launcher.service.WLauncherNotificationListener
import com.flue.launcher.ui.common.glassSurface
import com.flue.launcher.ui.common.instantPressGesture
import com.flue.launcher.ui.common.rememberPressedState
import com.flue.launcher.ui.settings.miuix.MiuixIcons as FlueMiuixIcons
import com.flue.launcher.ui.theme.LauncherTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Music
import top.yukonga.miuix.kmp.icon.extended.Pause
import top.yukonga.miuix.kmp.icon.extended.Play
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

private const val MEDIA_BUTTON_MIN_PRESS_MS = 140L
private const val MEDIA_PROGRESS_COLLAPSED_HEIGHT_DP = 4f
private const val MEDIA_PROGRESS_EXPANDED_HEIGHT_DP = 8f
private const val MEDIA_PROGRESS_HIT_HEIGHT_DP = 24f
private const val MEDIA_PROGRESS_SEEK_SYNC_HOLD_MS = 1200L
private const val MUSIC_TEXT_MARQUEE_INITIAL_DELAY_MS = 650L
private const val MUSIC_TEXT_MARQUEE_END_HOLD_MS = 3_000L
private const val MUSIC_TEXT_MARQUEE_SPEED_DP_PER_SECOND = 34f
private const val MUSIC_TEXT_FADE_EDGE_DP = 18f
private const val MUSIC_TEXT_FADE_TRANSITION_MS = 80
// Keep a readable gap between the tail of one copy and the head of the next.
private const val MUSIC_TEXT_MARQUEE_SIDE_PADDING_DP = 20f

private data class SideScreenMediaState(
    val controller: MediaController? = null,
    val title: String = "",
    val artist: String = "",
    val artwork: ImageBitmap? = null,
    /** Stable metadata identity; prevents a new ImageBitmap wrapper every poll from restarting Crossfade. */
    val artworkIdentity: String? = null,
    val appIcon: ImageBitmap? = null,
    val customActions: List<SideScreenMediaAction> = emptyList(),
    val artworkColor: Color = Color(0xFF2F3B45),
    val playing: Boolean = false,
    val progress: Float = 0f,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val appPackage: String? = null,
)

private data class SideScreenMediaAction(
    val action: String,
    val title: String,
    val extras: Bundle? = null,
    val icon: ImageBitmap? = null,
)

internal data class MediaCardLayoutSpec(
    val cardHeightDp: Int,
    val paddingDp: Int,
    val artworkSizeDp: Int,
    val artworkGapDp: Int,
    val titleFontSizeSp: Int,
    val artistFontSizeSp: Int,
    val controlButtonDp: Int,
)

internal fun mediaCardLayoutSpec(compact: Boolean): MediaCardLayoutSpec = if (compact) {
    MediaCardLayoutSpec(
        cardHeightDp = 116,
        paddingDp = 10,
        artworkSizeDp = 92,
        artworkGapDp = 8,
        titleFontSizeSp = 13,
        artistFontSizeSp = 10,
        controlButtonDp = 28,
    )
} else {
    MediaCardLayoutSpec(
        cardHeightDp = 156,
        paddingDp = 12,
        artworkSizeDp = 132,
        artworkGapDp = 12,
        titleFontSizeSp = 15,
        artistFontSizeSp = 11,
        controlButtonDp = 36,
    )
}

internal fun sideScreenMediaCardLayoutSpec(): MediaCardLayoutSpec =
    mediaCardLayoutSpec(compact = false)

internal fun sideScreenContentAllowsVerticalScroll(
    showMusicCard: Boolean,
    showNotificationPreview: Boolean,
    expandedShortcutGrid: Boolean = false,
): Boolean = showMusicCard || showNotificationPreview || expandedShortcutGrid

/**
 * 负一屏挂不挂弹性回弹。
 *
 * **必须与 [sideScreenContentAllowsVerticalScroll] 同一个判据。**
 */
internal fun sideScreenContentUsesElasticOverscroll(
    showMusicCard: Boolean,
    showNotificationPreview: Boolean,
    expandedShortcutGrid: Boolean = false,
): Boolean = sideScreenContentAllowsVerticalScroll(
    showMusicCard = showMusicCard,
    showNotificationPreview = showNotificationPreview,
    expandedShortcutGrid = expandedShortcutGrid,
)

internal data class MediaMarqueeSpec(
    val cycleDistancePx: Int,
    val nextCopyEntryOffsetPx: Int,
    val previousCopyVisiblePxAtEntry: Int,
    val contentWidthPx: Int,
)

internal fun mediaMarqueeSpec(
    textWidthPx: Int,
    viewportWidthPx: Int,
    gapPx: Int,
): MediaMarqueeSpec {
    val textWidth = textWidthPx.coerceAtLeast(0)
    val viewportWidth = viewportWidthPx.coerceAtLeast(0)
    val gap = gapPx.coerceAtLeast(0)
    val cycleDistance = textWidth + gap
    val nextCopyEntryOffset = (cycleDistance - viewportWidth).coerceAtLeast(0)
    return MediaMarqueeSpec(
        cycleDistancePx = cycleDistance,
        nextCopyEntryOffsetPx = nextCopyEntryOffset,
        previousCopyVisiblePxAtEntry = (textWidth - nextCopyEntryOffset).coerceAtLeast(0),
        contentWidthPx = cycleDistance * 2 + viewportWidth,
    )
}

@Composable
fun SideScreenMusicCard(
    notificationAccessGranted: Boolean,
    musicTextSwitchAnimation: String = MusicTextSwitchAnimations.DEFAULT_ID,
    showMediaCustomActions: Boolean = true,
    glassHighlightEnabled: Boolean = true,
    // 负一屏常驻组合；只在它可见时才轮询媒体状态。
    active: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val mediaState = rememberMediaState(context, active = active)
    MediaControlCard(
        mediaState = mediaState,
        notificationAccessGranted = notificationAccessGranted,
        glassHighlightEnabled = glassHighlightEnabled,
        musicTextSwitchAnimation = musicTextSwitchAnimation,
        showCustomActions = showMediaCustomActions,
        onOpenApp = { openMusicApp(context, mediaState.appPackage) },
        onOpenNotificationAccess = { openNotificationAccessSettings(context) },
        onOpenOutputSwitcherOrApp = {
            if (!openMediaOutputSwitcher(context)) {
                openMusicApp(context, mediaState.appPackage)
            }
        },
        compact = false,
        modifier = modifier,
    )
}

@Composable
private fun MediaControlCard(
    mediaState: SideScreenMediaState,
    notificationAccessGranted: Boolean,
    glassHighlightEnabled: Boolean,
    musicTextSwitchAnimation: String,
    showCustomActions: Boolean,
    onOpenApp: () -> Unit,
    onOpenNotificationAccess: () -> Unit,
    onOpenOutputSwitcherOrApp: () -> Unit,
    compact: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val layout = mediaCardLayoutSpec(compact)
    val launcherStyle = LauncherTheme.style
    val resolvedMediaTitle = mediaState.title.ifBlank {
        stringResource(R.string.cc_media_not_playing)
    }
    val resolvedMediaArtist = mediaState.artist.ifBlank {
        stringResource(R.string.cc_media_open_app_hint)
    }
    val cardShape = launcherStyle.panelShape
    val artworkSurfaceActive = notificationAccessGranted &&
        mediaState.playing &&
        mediaState.artwork != null
    val primaryTarget = if (artworkSurfaceActive) Color.White else launcherStyle.titleColor
    val secondaryTarget = if (artworkSurfaceActive) {
        Color.White.copy(alpha = 0.72f)
    } else {
        launcherStyle.secondaryTextColor
    }
    val controlTarget = if (artworkSurfaceActive) Color.White else launcherStyle.bodyColor
    val primaryContentColor by animateColorAsState(
        targetValue = primaryTarget,
        animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing),
        label = "media_primary_content",
    )
    val secondaryContentColor by animateColorAsState(
        targetValue = secondaryTarget,
        animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing),
        label = "media_secondary_content",
    )
    val controlContentColor by animateColorAsState(
        targetValue = controlTarget,
        animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing),
        label = "media_control_content",
    )
    val blurredArtwork = rememberBlurredMediaArtwork(
        mediaState.artwork.takeIf { notificationAccessGranted },
    )
    val artworkSurfaceAlpha by animateFloatAsState(
        targetValue = if (artworkSurfaceActive && blurredArtwork != null) 1f else 0f,
        animationSpec = tween(durationMillis = 360, easing = FastOutSlowInEasing),
        label = "media_artwork_surface",
    )
    val bgTarget = if (notificationAccessGranted) mediaState.artworkColor else Color(0xFF2F3B45)
    val bg by animateColorAsState(
        targetValue = bgTarget,
        animationSpec = tween(durationMillis = 420, easing = FastOutSlowInEasing),
        label = "media_card_bg",
    )
    val playingEdgeAlpha by animateFloatAsState(
        targetValue = if (artworkSurfaceActive && glassHighlightEnabled) 1f else 0f,
        animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
        label = "media_card_playing_edge",
    )
    val pressedState = rememberPressedState()
    val pressed by pressedState
    val cardScale by animateFloatAsState(
        targetValue = if (pressed) 0.968f else 1f,
        animationSpec = spring(stiffness = 860f, dampingRatio = 0.72f),
        label = "media_card_press",
    )
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(layout.cardHeightDp.dp)
            .graphicsLayer {
                scaleX = cardScale
                scaleY = cardScale
            }
            .glassSurface(
                cardShape,
                launcherStyle.glass,
                highlightEnabled = glassHighlightEnabled,
            )
            .instantPressGesture(
                pressedState,
                onClick = if (notificationAccessGranted) onOpenApp else onOpenNotificationAccess,
            )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = artworkSurfaceAlpha }
                .background(
                    Brush.linearGradient(
                        listOf(
                            bg.copy(alpha = 0.92f),
                            Color(bg.red * 0.86f, bg.green * 0.86f, bg.blue * 0.86f, 0.94f),
                        )
                    )
                ),
        )
        Crossfade(
            targetState = blurredArtwork,
            animationSpec = tween(durationMillis = 420, easing = FastOutSlowInEasing),
            label = "media_blurred_artwork_crossfade",
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = 1.08f
                    scaleY = 1.08f
                    alpha = artworkSurfaceAlpha * 0.68f
                },
        ) { artwork ->
            if (artwork != null) {
                Image(
                    bitmap = artwork,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = artworkSurfaceAlpha }
                .background(Color.Black.copy(alpha = 0.10f)),
        )
        if (playingEdgeAlpha > 0.001f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = playingEdgeAlpha }
                    .border(
                        width = 1.25.dp,
                        brush = Brush.verticalGradient(
                            listOf(
                                Color.White.copy(alpha = 0.58f),
                                Color.White.copy(alpha = 0.10f),
                            )
                        ),
                        shape = cardShape,
                    )
            )
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(layout.paddingDp.dp)
        ) {
            if (!notificationAccessGranted) {
                MediaNotificationAccessPrompt(
                    primaryColor = primaryContentColor,
                    secondaryColor = secondaryContentColor,
                    controlColor = controlContentColor,
                    compact = compact,
                )
            } else {
                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    val artworkGap = layout.artworkGapDp.dp
                    val preferredArtworkSize = layout.artworkSizeDp.dp
                    val minArtworkSize = if (compact) 68.dp else 96.dp
                    val preferredControlsWidth = layout.controlButtonDp.dp * 3f +
                        (if (compact) 5.dp else 8.dp) * 2f
                    val artworkSize = if (maxWidth >= preferredArtworkSize + artworkGap + preferredControlsWidth) {
                        preferredArtworkSize
                    } else {
                        (maxWidth - artworkGap - preferredControlsWidth).coerceIn(minArtworkSize, preferredArtworkSize)
                    }

                    Row(
                        modifier = Modifier.fillMaxSize(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 18dp = panel 30dp - padding 12dp（同心圆角）。
                        val artworkShape = RoundedCornerShape(18.dp)
                        Box(
                            modifier = Modifier
                                .size(artworkSize)
                                .clip(artworkShape)
                                .background(controlContentColor.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Crossfade(
                                targetState = mediaState.artwork,
                                animationSpec = tween(durationMillis = 360, easing = FastOutSlowInEasing),
                                label = "media_cover_crossfade",
                                modifier = Modifier.fillMaxSize(),
                            ) { artwork ->
                                if (artwork != null) {
                                    Image(
                                        bitmap = artwork,
                                        contentDescription = null,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .clip(artworkShape),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = MiuixIcons.Music,
                                            contentDescription = null,
                                            tint = controlContentColor,
                                            modifier = Modifier
                                                .size(if (compact) 26.dp else 34.dp)
                                                .offset(x = (-2).dp),
                                        )
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.width(artworkGap))
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .padding(vertical = 4.dp),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.Top
                            ) {
                                Box(modifier = Modifier.fillMaxWidth()) {
                                    val titleEndPadding = if (mediaState.appIcon != null) 34.dp else 0.dp
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(end = titleEndPadding)
                                    ) {
                                        AnimatedMusicText(
                                            text = resolvedMediaTitle,
                                            color = primaryContentColor,
                                            fontSize = layout.titleFontSizeSp.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            animationStyleId = musicTextSwitchAnimation
                                        )
                                        AnimatedMusicText(
                                            text = resolvedMediaArtist,
                                            color = secondaryContentColor,
                                            fontSize = layout.artistFontSizeSp.sp,
                                            fontWeight = FontWeight.Normal,
                                            animationStyleId = musicTextSwitchAnimation
                                        )
                                    }
                                    if (mediaState.appIcon != null) {
                                        MediaAppIconButton(
                                            icon = mediaState.appIcon,
                                            modifier = Modifier
                                                .align(Alignment.TopEnd)
                                                .size(30.dp),
                                            onClick = onOpenOutputSwitcherOrApp
                                        )
                                    }
                                }
                            }
                            MediaProgressSection(
                                mediaState = mediaState,
                                foregroundColor = controlContentColor,
                                secondaryTextColor = secondaryContentColor,
                                compact = compact,
                                modifier = Modifier.fillMaxWidth()
                            )
                            MediaControlsRow(
                                mediaState = mediaState,
                                showCustomActions = showCustomActions,
                                foregroundColor = controlContentColor,
                                compact = compact,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun rememberBlurredMediaArtwork(artwork: ImageBitmap?): ImageBitmap? {
    var blurred by remember { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(artwork) {
        if (artwork == null) {
            blurred = null
        } else {
            blurred = withContext(Dispatchers.Default) {
                createMediumBlurredMediaArtwork(artwork)
            }
        }
    }
    return blurred
}

private fun createMediumBlurredMediaArtwork(sourceImage: ImageBitmap): ImageBitmap {
    val source = sourceImage.asAndroidBitmap()
    val sourceLongEdge = maxOf(source.width, source.height).coerceAtLeast(1)
    val targetLongEdge = sourceLongEdge.coerceIn(64, 160)
    val scale = targetLongEdge.toFloat() / sourceLongEdge
    val width = (source.width * scale).roundToInt().coerceAtLeast(1)
    val height = (source.height * scale).roundToInt().coerceAtLeast(1)
    val working = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    android.graphics.Canvas(working).drawBitmap(
        source,
        null,
        android.graphics.Rect(0, 0, width, height),
        android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG or android.graphics.Paint.FILTER_BITMAP_FLAG),
    )
    val pixels = IntArray(width * height)
    val scratch = IntArray(pixels.size)
    working.getPixels(pixels, 0, width, 0, 0, width, height)
    repeat(3) {
        mediaBoxBlurHorizontal(pixels, scratch, width, height, radius = 5)
        mediaBoxBlurVertical(scratch, pixels, width, height, radius = 5)
    }
    working.setPixels(pixels, 0, width, 0, 0, width, height)
    return working.asImageBitmap()
}

private fun mediaBoxBlurHorizontal(
    input: IntArray,
    output: IntArray,
    width: Int,
    height: Int,
    radius: Int,
) {
    val diameter = radius * 2 + 1
    for (y in 0 until height) {
        val row = y * width
        var red = 0
        var green = 0
        var blue = 0
        for (offset in -radius..radius) {
            val color = input[row + offset.coerceIn(0, width - 1)]
            red += color shr 16 and 0xFF
            green += color shr 8 and 0xFF
            blue += color and 0xFF
        }
        for (x in 0 until width) {
            output[row + x] = android.graphics.Color.rgb(red / diameter, green / diameter, blue / diameter)
            val outgoing = input[row + (x - radius).coerceIn(0, width - 1)]
            val incoming = input[row + (x + radius + 1).coerceIn(0, width - 1)]
            red += (incoming shr 16 and 0xFF) - (outgoing shr 16 and 0xFF)
            green += (incoming shr 8 and 0xFF) - (outgoing shr 8 and 0xFF)
            blue += (incoming and 0xFF) - (outgoing and 0xFF)
        }
    }
}

private fun mediaBoxBlurVertical(
    input: IntArray,
    output: IntArray,
    width: Int,
    height: Int,
    radius: Int,
) {
    val diameter = radius * 2 + 1
    for (x in 0 until width) {
        var red = 0
        var green = 0
        var blue = 0
        for (offset in -radius..radius) {
            val color = input[offset.coerceIn(0, height - 1) * width + x]
            red += color shr 16 and 0xFF
            green += color shr 8 and 0xFF
            blue += color and 0xFF
        }
        for (y in 0 until height) {
            output[y * width + x] = android.graphics.Color.rgb(red / diameter, green / diameter, blue / diameter)
            val outgoing = input[(y - radius).coerceIn(0, height - 1) * width + x]
            val incoming = input[(y + radius + 1).coerceIn(0, height - 1) * width + x]
            red += (incoming shr 16 and 0xFF) - (outgoing shr 16 and 0xFF)
            green += (incoming shr 8 and 0xFF) - (outgoing shr 8 and 0xFF)
            blue += (incoming and 0xFF) - (outgoing and 0xFF)
        }
    }
}

@Composable
private fun MediaNotificationAccessPrompt(
    primaryColor: Color,
    secondaryColor: Color,
    controlColor: Color,
    compact: Boolean,
) {
    Row(
        modifier = Modifier.fillMaxSize(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(if (compact) 92.dp else 132.dp)
                .clip(RoundedCornerShape(if (compact) 18.dp else 24.dp))
                .background(controlColor.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = MiuixIcons.Music,
                contentDescription = null,
                tint = controlColor,
                modifier = Modifier.size(if (compact) 26.dp else 34.dp),
            )
        }
        Spacer(Modifier.width(if (compact) 8.dp else 12.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = stringResource(R.string.cc_notification_permission_title),
                color = primaryColor,
                fontSize = if (compact) 13.sp else 15.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(if (compact) 2.dp else 5.dp))
            Text(
                text = stringResource(R.string.cc_media_permission_subtitle),
                color = secondaryColor,
                fontSize = if (compact) 9.sp else 11.sp,
                lineHeight = if (compact) 12.sp else 15.sp,
                maxLines = if (compact) 1 else Int.MAX_VALUE,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(if (compact) 5.dp else 12.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(controlColor.copy(alpha = 0.14f))
                    .padding(
                        horizontal = if (compact) 8.dp else 12.dp,
                        vertical = if (compact) 4.dp else 6.dp,
                    )
            ) {
                Text(
                    text = stringResource(R.string.cc_media_permission_action),
                    color = controlColor,
                    fontSize = if (compact) 9.sp else 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun MediaAppIconButton(
    icon: ImageBitmap,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val pressedState = rememberPressedState()
    val pressed by pressedState
    val scale by animateFloatAsState(if (pressed) 0.88f else 1f, label = "media_app_icon_scale")
    val iconAlpha by animateFloatAsState(if (pressed) 0.72f else 1f, label = "media_app_icon_alpha")
    val iconShape = RoundedCornerShape(9.dp)
    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(iconShape)
            .mediaPressGesture(
                pressedState = pressedState,
                onClick = onClick
            )
            .padding(1.dp),
        contentAlignment = Alignment.Center
    ) {
        Image(
            bitmap = icon,
            contentDescription = null,
            modifier = Modifier
                .fillMaxSize()
                .clip(iconShape)
                .graphicsLayer { alpha = iconAlpha },
            contentScale = ContentScale.Fit
        )
    }
}

@Composable
private fun MediaControlsRow(
    mediaState: SideScreenMediaState,
    showCustomActions: Boolean,
    foregroundColor: Color,
    compact: Boolean,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier, contentAlignment = Alignment.Center) {
        val customActions = if (showCustomActions) mediaState.customActions.take(2) else emptyList()
        val preferredButtonSize = if (compact) 28.dp else 36.dp
        val minButtonSize = if (compact) 22.dp else 24.dp
        val customButtonScale = if (compact) 1.05f else 1.12f
        val preferredSpacing = if (compact) 5.dp else 8.dp
        val minSpacing = if (compact) 3.dp else 4.dp
        val customCountFloat = customActions.size.toFloat()
        val buttonCount = 3 + customActions.size
        val gapCountFloat = (buttonCount - 1).coerceAtLeast(1).toFloat()
        val buttonWidthUnits = 3f + customCountFloat * customButtonScale
        val preferredWidth = preferredButtonSize * buttonWidthUnits + preferredSpacing * gapCountFloat
        val minSpacingWidth = minSpacing * gapCountFloat
        val buttonSize = if (maxWidth >= preferredWidth) {
            preferredButtonSize
        } else {
            ((maxWidth - minSpacingWidth) / buttonWidthUnits).coerceIn(minButtonSize, preferredButtonSize)
        }
        val customButtonSize = (buttonSize * customButtonScale).coerceAtLeast(buttonSize)
        val occupiedButtonWidth = buttonSize * 3f + customButtonSize * customCountFloat
        val buttonSpacing = ((maxWidth - occupiedButtonWidth) / gapCountFloat)
            .coerceIn(minSpacing, preferredSpacing)
        val iconSize = (buttonSize * 0.56f).coerceIn(16.dp, 20.dp)
        val customIconSize = (customButtonSize * 0.60f).coerceIn(iconSize, 23.dp)

        Row(
            horizontalArrangement = Arrangement.spacedBy(buttonSpacing),
            verticalAlignment = Alignment.CenterVertically
        ) {
            customActions.getOrNull(0)?.let { customAction ->
                MediaCustomActionButton(
                    action = customAction,
                    size = customButtonSize,
                    iconSize = customIconSize,
                    foregroundColor = foregroundColor,
                ) {
                    mediaState.controller?.transportControls?.sendCustomAction(
                        customAction.action,
                        customAction.extras
                    )
                }
            }
            MediaButton(
                icon = FlueMiuixIcons.SkipPrevious,
                size = buttonSize,
                iconSize = iconSize,
                foregroundColor = foregroundColor,
            ) {
                mediaState.controller?.transportControls?.skipToPrevious()
            }
            MediaButton(
                icon = if (mediaState.playing) MiuixIcons.Pause else MiuixIcons.Play,
                size = buttonSize,
                iconSize = iconSize,
                iconOffsetX = if (mediaState.playing) 0.dp else 0.8.dp,
                foregroundColor = foregroundColor,
            ) {
                val controls = mediaState.controller?.transportControls
                if (mediaState.playing) controls?.pause() else controls?.play()
            }
            MediaButton(
                icon = FlueMiuixIcons.SkipNext,
                size = buttonSize,
                iconSize = iconSize,
                foregroundColor = foregroundColor,
            ) {
                mediaState.controller?.transportControls?.skipToNext()
            }
            customActions.getOrNull(1)?.let { customAction ->
                MediaCustomActionButton(
                    action = customAction,
                    size = customButtonSize,
                    iconSize = customIconSize,
                    foregroundColor = foregroundColor,
                ) {
                    mediaState.controller?.transportControls?.sendCustomAction(
                        customAction.action,
                        customAction.extras
                    )
                }
            }
        }
    }
}

@Composable
private fun MediaCustomActionButton(
    action: SideScreenMediaAction,
    size: Dp,
    iconSize: Dp,
    foregroundColor: Color,
    onClick: () -> Unit,
) {
    val pressedState = rememberPressedState()
    val pressed by pressedState
    val scale by animateFloatAsState(if (pressed) 0.88f else 1f, label = "media_custom_button_scale")
    val contentAlpha by animateFloatAsState(if (pressed) 0.64f else 1f, label = "media_custom_button_alpha")
    Box(
        modifier = Modifier
            .requiredSize(size)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .mediaPressGesture(
                pressedState = pressedState,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        if (action.icon != null) {
            Image(
                bitmap = action.icon,
                contentDescription = action.title,
                modifier = Modifier
                    .size(iconSize)
                    .graphicsLayer { alpha = contentAlpha },
                contentScale = ContentScale.Fit
            )
        } else {
            Text(
                text = action.title.take(2).ifBlank { "..." },
                color = foregroundColor.copy(alpha = contentAlpha),
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun AnimatedMusicText(
    text: String,
    color: Color,
    fontSize: TextUnit,
    fontWeight: FontWeight,
    animationStyleId: String,
) {
    val density = LocalDensity.current
    val preset = remember(animationStyleId) { MusicTextSwitchAnimations.byId(animationStyleId) }
    var displayedText by remember { mutableStateOf(text) }
    var phase by remember { mutableStateOf(MusicTextSwitchPhase.Idle) }
    val progress = remember { Animatable(1f) }
    val distancePx = with(density) { 18.dp.toPx() }

    LaunchedEffect(text, animationStyleId) {
        val currentPreset = MusicTextSwitchAnimations.byId(animationStyleId)
        if (displayedText == text) {
            phase = MusicTextSwitchPhase.Idle
            progress.snapTo(1f)
            return@LaunchedEffect
        }
        phase = MusicTextSwitchPhase.Out
        progress.snapTo(0f)
        progress.animateTo(
            1f,
            tween(
                durationMillis = currentPreset.outMotion.durationMs,
                easing = currentPreset.outMotion.easing
            )
        )
        displayedText = text
        phase = MusicTextSwitchPhase.In
        progress.snapTo(0f)
        progress.animateTo(
            1f,
            tween(
                durationMillis = currentPreset.inMotion.durationMs,
                easing = currentPreset.inMotion.easing
            )
        )
        phase = MusicTextSwitchPhase.Idle
        progress.snapTo(1f)
    }

    Box(
        modifier = Modifier
            .clipToBounds()
            .graphicsLayer {
                val transform = preset.transform(phase, progress.value, distancePx)
                alpha = transform.alpha
                translationX = transform.translationX
                translationY = transform.translationY
                scaleX = transform.scale
                scaleY = transform.scale
                rotationX = transform.rotationX
                rotationY = transform.rotationY
                rotationZ = transform.rotationZ
                cameraDistance = 12f * density.density
            }
    ) {
        OneShotMarqueeText(
            text = displayedText,
            color = color,
            fontSize = fontSize,
            fontWeight = fontWeight
        )
    }
}

@Composable
private fun OneShotMarqueeText(
    text: String,
    color: Color,
    fontSize: TextUnit,
    fontWeight: FontWeight,
) {
    val density = LocalDensity.current
    val sidePadding = MUSIC_TEXT_MARQUEE_SIDE_PADDING_DP.dp
    val sidePaddingPx = with(density) { sidePadding.toPx() }
    val fadeEdgePx = with(density) { MUSIC_TEXT_FADE_EDGE_DP.dp.toPx() }
    var containerWidthPx by remember { mutableIntStateOf(0) }
    var rawTextWidthPx by remember(text) { mutableIntStateOf(0) }
    var contentWidthPx by remember { mutableIntStateOf(0) }
    val hasOverflow = containerWidthPx > 0 &&
        rawTextWidthPx > 0 &&
        rawTextWidthPx + sidePaddingPx > containerWidthPx.toFloat()
    val marqueeSpec = mediaMarqueeSpec(
        textWidthPx = rawTextWidthPx,
        viewportWidthPx = containerWidthPx,
        gapPx = sidePaddingPx.roundToInt(),
    )
    val contentLayoutReady = !hasOverflow || contentWidthPx >= marqueeSpec.contentWidthPx - 1
    val marqueeScrollState = rememberScrollState()
    val restingTargetPx = if (hasOverflow && contentLayoutReady) {
        marqueeSpec.nextCopyEntryOffsetPx
    } else {
        0
    }
    val exitTargetPx = if (hasOverflow && contentLayoutReady) {
        marqueeSpec.cycleDistancePx
    } else {
        0
    }

    LaunchedEffect(
        text,
        hasOverflow,
        restingTargetPx,
        exitTargetPx,
        density.density,
    ) {
        marqueeScrollState.scrollTo(0)
        if (
            hasOverflow &&
            restingTargetPx > 0 &&
            exitTargetPx > restingTargetPx
        ) {
            delay(MUSIC_TEXT_MARQUEE_INITIAL_DELAY_MS)
            val firstDurationMs = mediaMarqueeDurationMs(restingTargetPx, density.density, minimumMs = 900)
            marqueeScrollState.animateScrollTo(
                value = restingTargetPx,
                animationSpec = tween(durationMillis = firstDurationMs, easing = LinearEasing),
            )
            delay(MUSIC_TEXT_MARQUEE_END_HOLD_MS)
            val exitDurationMs = mediaMarqueeDurationMs(
                exitTargetPx - restingTargetPx,
                density.density,
                minimumMs = 420,
            )
            marqueeScrollState.animateScrollTo(
                value = exitTargetPx,
                animationSpec = tween(durationMillis = exitDurationMs, easing = LinearEasing),
            )

            while (true) {
                marqueeScrollState.scrollTo(0)
                val repeatScrollDurationMs = mediaMarqueeDurationMs(restingTargetPx, density.density, minimumMs = 900)
                marqueeScrollState.animateScrollTo(
                    value = restingTargetPx,
                    animationSpec = tween(durationMillis = repeatScrollDurationMs, easing = LinearEasing),
                )
                delay(MUSIC_TEXT_MARQUEE_END_HOLD_MS)
                val repeatExitDurationMs = mediaMarqueeDurationMs(
                    exitTargetPx - restingTargetPx,
                    density.density,
                    minimumMs = 420,
                )
                marqueeScrollState.animateScrollTo(
                    value = exitTargetPx,
                    animationSpec = tween(durationMillis = repeatExitDurationMs, easing = LinearEasing),
                )
            }
        }
    }

    val scrollOffsetPx = marqueeScrollState.value.toFloat()
    val remainingScrollPx = (restingTargetPx - marqueeScrollState.value)
        .coerceAtLeast(0)
        .toFloat()
    val atCycleStart = scrollOffsetPx <= 0.5f
    val leftFadeTarget = if (hasOverflow && fadeEdgePx > 0f) {
        if (!atCycleStart) 1f else 0f
    } else {
        0f
    }
    val rightFadeTarget = if (hasOverflow && fadeEdgePx > 0f) {
        (remainingScrollPx / fadeEdgePx).coerceIn(0f, 1f)
    } else {
        0f
    }
    val leftFadeAlpha by animateFloatAsState(
        targetValue = leftFadeTarget,
        animationSpec = tween(durationMillis = MUSIC_TEXT_FADE_TRANSITION_MS, easing = FastOutSlowInEasing),
        label = "music_text_left_edge_fade"
    )
    val rightFadeAlpha by animateFloatAsState(
        targetValue = rightFadeTarget,
        animationSpec = tween(durationMillis = MUSIC_TEXT_FADE_TRANSITION_MS, easing = FastOutSlowInEasing),
        label = "music_text_right_edge_fade"
    )
    val fadeModifier = if (hasOverflow || leftFadeAlpha > 0.001f || rightFadeAlpha > 0.001f) {
        Modifier
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithCache {
                val fadeWidthPx = MUSIC_TEXT_FADE_EDGE_DP.dp.toPx().coerceAtMost(size.width / 2f)
                val leftStop = if (size.width == 0f) 0f else (fadeWidthPx / size.width).coerceIn(0f, 1f)
                val rightStart = if (size.width == 0f) 1f else ((size.width - fadeWidthPx) / size.width).coerceIn(0f, 1f)
                val fadeBrush = Brush.horizontalGradient(
                    colorStops = arrayOf(
                        0f to Color.Black.copy(alpha = 1f - leftFadeAlpha.coerceIn(0f, 1f)),
                        leftStop to Color.Black,
                        rightStart to Color.Black,
                        1f to Color.Black.copy(alpha = 1f - rightFadeAlpha.coerceIn(0f, 1f))
                    )
                )
                onDrawWithContent {
                    drawContent()
                    drawRect(brush = fadeBrush, blendMode = BlendMode.DstIn)
                }
            }
    } else {
        Modifier
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clipToBounds()
            .onSizeChanged { containerWidthPx = it.width.coerceAtLeast(0) }
            .then(fadeModifier)
    ) {
        Row(
            modifier = Modifier
                .horizontalScroll(marqueeScrollState, enabled = false)
                .onSizeChanged { contentWidthPx = it.width.coerceAtLeast(0) },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = text,
                color = color,
                fontSize = fontSize,
                fontWeight = fontWeight,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Clip,
                onTextLayout = { rawTextWidthPx = it.size.width.coerceAtLeast(0) }
            )
            if (hasOverflow) {
                Spacer(Modifier.width(sidePadding))
                Text(
                    text = text,
                    color = color,
                    fontSize = fontSize,
                    fontWeight = fontWeight,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Clip,
                )
                Spacer(Modifier.width(sidePadding))
                // Keep a trailing viewport so the scroll range can finish one full copy cycle.
                Spacer(Modifier.width((containerWidthPx / density.density).dp))
            }
        }
    }
}

internal fun mediaMarqueeDurationMs(distancePx: Int, density: Float, minimumMs: Int): Int =
    ((distancePx.coerceAtLeast(1) / density.coerceAtLeast(0.1f)) /
        MUSIC_TEXT_MARQUEE_SPEED_DP_PER_SECOND * 1_000f)
        .roundToInt()
        .coerceAtLeast(minimumMs)

@Composable
private fun MediaProgressSection(
    mediaState: SideScreenMediaState,
    foregroundColor: Color,
    secondaryTextColor: Color,
    compact: Boolean,
    modifier: Modifier = Modifier,
) {
    val currentMediaState by rememberUpdatedState(mediaState)
    var widthPx by remember { mutableIntStateOf(1) }
    var dragging by remember { mutableStateOf(false) }
    var skipNextProgressSync by remember { mutableStateOf(false) }
    var seekSyncHoldUntilMs by remember { mutableStateOf(0L) }
    var activeMediaIdentity by remember {
        mutableStateOf(mediaState.appPackage to mediaState.title)
    }
    var localProgress by remember {
        mutableFloatStateOf(mediaState.progress.coerceIn(0f, 1f))
    }
    LaunchedEffect(mediaState.appPackage, mediaState.title, dragging) {
        val mediaIdentity = mediaState.appPackage to mediaState.title
        if (!dragging && activeMediaIdentity != mediaIdentity) {
            activeMediaIdentity = mediaIdentity
            skipNextProgressSync = false
            seekSyncHoldUntilMs = 0L
            localProgress = mediaState.progress.coerceIn(0f, 1f)
        }
    }
    LaunchedEffect(mediaState.progress, dragging) {
        if (!dragging) {
            val now = SystemClock.uptimeMillis()
            if (now < seekSyncHoldUntilMs) {
                return@LaunchedEffect
            }
            if (skipNextProgressSync) {
                skipNextProgressSync = false
                seekSyncHoldUntilMs = now + MEDIA_PROGRESS_SEEK_SYNC_HOLD_MS
                return@LaunchedEffect
            }
            localProgress = mediaState.progress.coerceIn(0f, 1f)
        }
    }
    val hitHeightDp = if (compact) 16f else MEDIA_PROGRESS_HIT_HEIGHT_DP
    val timeFontSize = if (compact) 8.sp else 10.sp
    val trackHeight by animateFloatAsState(
        if (dragging) MEDIA_PROGRESS_EXPANDED_HEIGHT_DP else MEDIA_PROGRESS_COLLAPSED_HEIGHT_DP,
        label = "media_progress_height"
    )
    fun seekToProgress(progress: Float) {
        val duration = currentMediaState.durationMs
        if (duration <= 0L) return
        currentMediaState.controller?.transportControls?.seekTo((progress.coerceIn(0f, 1f) * duration).toLong())
    }
    Column(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(hitHeightDp.dp)
                .onSizeChanged { widthPx = it.width.coerceAtLeast(1) }
                .pointerInput(Unit) {
                    val dragHitHeightPx = hitHeightDp.dp.toPx()
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val centerY = size.height / 2f
                        val withinTrack = abs(down.position.y - centerY) <= dragHitHeightPx / 2f
                        if (!withinTrack || currentMediaState.durationMs <= 0L) {
                            return@awaitEachGesture
                        }
                        down.consume()
                        val startX = down.position.x
                        val startProgress = localProgress.coerceIn(0f, 1f)
                        fun updateProgressFromDelta(deltaX: Float) {
                            localProgress = (startProgress + deltaX / widthPx).coerceIn(0f, 1f)
                        }
                        try {
                            dragging = true
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id }
                                    ?: event.changes.firstOrNull()
                                    ?: break
                                if (!change.pressed || change.changedToUpIgnoreConsumed()) {
                                    break
                                }
                                updateProgressFromDelta(change.position.x - startX)
                                change.consume()
                            }
                            skipNextProgressSync = true
                            seekToProgress(localProgress)
                        } finally {
                            dragging = false
                        }
                    }
                },
            contentAlignment = Alignment.CenterStart
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(trackHeight.dp)
            ) {
                val radius = size.height / 2f
                val corners = CornerRadius(radius, radius)
                drawRoundRect(
                    color = foregroundColor.copy(alpha = 0.22f),
                    size = size,
                    cornerRadius = corners,
                )
                val progress = localProgress.coerceIn(0f, 1f)
                if (progress > 0f) {
                    val activeWidth = (size.width * progress)
                        .coerceAtLeast(size.height)
                        .coerceAtMost(size.width)
                    drawRoundRect(
                        color = foregroundColor,
                        size = Size(activeWidth, size.height),
                        cornerRadius = corners,
                    )
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = formatMediaTime((localProgress.coerceIn(0f, 1f) * mediaState.durationMs).toLong()),
                color = secondaryTextColor,
                fontSize = timeFontSize
            )
            Text(
                text = formatMediaTime(mediaState.durationMs),
                color = secondaryTextColor,
                fontSize = timeFontSize
            )
        }
    }
}

@Composable
private fun MediaButton(
    icon: ImageVector,
    size: Dp = 36.dp,
    iconSize: Dp = 20.dp,
    iconOffsetX: Dp = 0.dp,
    foregroundColor: Color,
    onClick: () -> Unit,
) {
    val pressedState = rememberPressedState()
    val pressed by pressedState
    val scale by animateFloatAsState(if (pressed) 0.88f else 1f, label = "media_button_scale")
    val backgroundAlpha by animateFloatAsState(if (pressed) 0.28f else 0.18f, label = "media_button_alpha")
    Box(
        modifier = Modifier
            .requiredSize(size)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(CircleShape)
            .background(foregroundColor.copy(alpha = backgroundAlpha))
            .border(
                width = 0.8.dp,
                brush = Brush.verticalGradient(
                    listOf(
                        Color.White.copy(alpha = if (pressed) 0.52f else 0.38f),
                        Color.White.copy(alpha = 0.08f),
                    )
                ),
                shape = CircleShape,
            )
            .mediaPressGesture(
                pressedState = pressedState,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = foregroundColor,
            modifier = Modifier
                .size(iconSize)
                .offset(x = iconOffsetX),
        )
    }
}

private fun Modifier.mediaPressGesture(
    pressedState: MutableState<Boolean>,
    enabled: Boolean = true,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
): Modifier {
    if (!enabled) return this
    return pointerInput(onClick, onLongClick, enabled) {
        detectTapGestures(
            onPress = {
                val startTime = SystemClock.elapsedRealtime()
                pressedState.value = true
                try {
                    tryAwaitRelease()
                    val remaining = MEDIA_BUTTON_MIN_PRESS_MS - (SystemClock.elapsedRealtime() - startTime)
                    if (remaining > 0L) delay(remaining)
                } finally {
                    pressedState.value = false
                }
            },
            onTap = {
                onClick()
            },
            onLongPress = {
                onLongClick?.invoke()
            }
        )
    }
}

@Composable
private fun rememberMediaState(context: Context, active: Boolean = true): SideScreenMediaState {
    var state by remember(context) { mutableStateOf(SideScreenMediaState()) }
    var retainedArtwork by remember(context) { mutableStateOf<ImageBitmap?>(null) }
    var retainedArtworkColor by remember(context) { mutableStateOf(Color(0xFF2F3B45)) }
    var retainedArtworkPackage by remember(context) { mutableStateOf<String?>(null) }
    var retainedArtworkIdentity by remember(context) { mutableStateOf<String?>(null) }
    var retainedAppIcon by remember(context) { mutableStateOf<ImageBitmap?>(null) }
    var retainedAppIconPackage by remember(context) { mutableStateOf<String?>(null) }
    LaunchedEffect(context, active) {
        if (!active) return@LaunchedEffect
        while (true) {
            val next = withContext(Dispatchers.Default) { readMediaState(context) }
            state = when {
                next.controller == null -> {
                    retainedArtwork = null
                    retainedArtworkPackage = null
                    retainedArtworkIdentity = null
                    retainedAppIcon = null
                    retainedAppIconPackage = null
                    next
                }
                next.artwork != null &&
                    next.appPackage == retainedArtworkPackage &&
                    next.artworkIdentity == retainedArtworkIdentity &&
                    retainedArtwork != null -> {
                    next.copy(
                        artwork = retainedArtwork,
                        artworkColor = retainedArtworkColor,
                        appIcon = stableMediaAppIcon(
                            next = next,
                            retained = retainedAppIcon,
                            retainedPackage = retainedAppIconPackage,
                        ),
                    )
                }
                next.artwork != null -> {
                    retainedArtwork = next.artwork
                    retainedArtworkColor = next.artworkColor
                    retainedArtworkPackage = next.appPackage
                    retainedArtworkIdentity = next.artworkIdentity
                    val stableIcon = stableMediaAppIcon(
                        next = next,
                        retained = retainedAppIcon,
                        retainedPackage = retainedAppIconPackage,
                    )
                    if (next.appPackage != retainedAppIconPackage || retainedAppIcon == null) {
                        retainedAppIcon = next.appIcon
                        retainedAppIconPackage = next.appPackage
                    }
                    next.copy(appIcon = stableIcon)
                }
                retainedArtwork != null && next.appPackage == retainedArtworkPackage -> {
                    next.copy(
                        artwork = retainedArtwork,
                        artworkIdentity = retainedArtworkIdentity,
                        artworkColor = retainedArtworkColor,
                        appIcon = stableMediaAppIcon(
                            next = next,
                            retained = retainedAppIcon,
                            retainedPackage = retainedAppIconPackage,
                        ),
                    )
                }
                else -> {
                    retainedAppIcon = next.appIcon
                    retainedAppIconPackage = next.appPackage
                    next
                }
            }
            if (next.appPackage != retainedAppIconPackage || retainedAppIcon == null) {
                retainedAppIcon = next.appIcon
                retainedAppIconPackage = next.appPackage
            }
            delay(500)
        }
    }
    return state
}

private fun stableMediaAppIcon(
    next: SideScreenMediaState,
    retained: ImageBitmap?,
    retainedPackage: String?,
): ImageBitmap? = if (next.appPackage == retainedPackage && retained != null) retained else next.appIcon

private fun readMediaState(context: Context): SideScreenMediaState {
    val manager = context.getSystemService(MediaSessionManager::class.java) ?: return SideScreenMediaState()
    val component = ComponentName(context, WLauncherNotificationListener::class.java)
    val controller = runCatching {
        manager.getActiveSessions(component).firstOrNull()
    }.getOrNull() ?: return SideScreenMediaState()
    val metadata = controller.metadata
    val playbackState = controller.playbackState
    val appIconBitmap = loadMediaAppIconBitmap(context, controller.packageName)
    val artworkBitmap = metadata?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
        ?: metadata?.getBitmap(MediaMetadata.METADATA_KEY_ART)
        ?: metadata?.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
    val artworkIdentity = artworkBitmap?.let { bitmap ->
        listOf(
            controller.packageName,
            mediaArtworkContentFingerprint(bitmap),
            metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM_ART_URI),
            metadata?.getString(MediaMetadata.METADATA_KEY_ART_URI),
            metadata?.getString(MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI),
        ).joinToString("\u0000")
    }
    val customActions = playbackState?.customActions.orEmpty().mapNotNull { action ->
        val title = action.name?.toString().orEmpty()
        val actionIcon = loadCustomActionIconBitmap(context, controller.packageName, action.icon)
        if (action.action.isBlank() && title.isBlank()) return@mapNotNull null
        SideScreenMediaAction(
            action = action.action,
            title = title.ifBlank { action.action },
            extras = action.extras,
            icon = actionIcon?.asImageBitmap()
        )
    }.take(2)
    val duration = metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION)?.takeIf { it > 0L } ?: 0L
    val rawPosition = playbackState?.position?.coerceAtLeast(0L) ?: 0L
    val position = if (
        playbackState?.state == PlaybackState.STATE_PLAYING &&
        duration > 0L &&
        playbackState.lastPositionUpdateTime > 0L
    ) {
        val elapsed = SystemClock.elapsedRealtime() - playbackState.lastPositionUpdateTime
        (rawPosition + elapsed).coerceIn(0L, duration)
    } else {
        rawPosition.coerceAtMost(duration.takeIf { it > 0L } ?: rawPosition)
    }
    val progress = if (duration > 0L) position.toFloat() / duration else 0f
    return SideScreenMediaState(
        controller = controller,
        title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)
            ?: metadata?.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)
            ?: context.getString(R.string.cc_media_not_playing),
        artist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?: metadata?.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE)
            ?: controller.packageName.substringAfterLast('.'),
        artwork = artworkBitmap?.asImageBitmap(),
        artworkIdentity = artworkIdentity,
        appIcon = appIconBitmap?.asImageBitmap(),
        customActions = customActions,
        artworkColor = artworkBitmap?.let(::averageBitmapColor) ?: Color(0xFF2F3B45),
        playing = playbackState?.state == PlaybackState.STATE_PLAYING,
        progress = progress,
        positionMs = position,
        durationMs = duration,
        appPackage = controller.packageName
    )
}

private fun loadMediaAppIconBitmap(context: Context, packageName: String): Bitmap? {
    val repositoryIcon = runCatching {
        FlueApplication.repositories(context).appRepository.loadDisplayIconForPackage(
            packageName = packageName,
            iconSize = 128,
            preferPlainIcon = true
        )
    }.getOrNull()?.asAndroidBitmap()
    if (repositoryIcon != null) return repositoryIcon
    return runCatching {
        context.packageManager.getApplicationIcon(packageName).toBitmap(width = 128, height = 128)
    }.getOrNull()
}

private fun loadCustomActionIconBitmap(
    context: Context,
    packageName: String,
    iconResId: Int,
): Bitmap? {
    if (iconResId == 0) return null
    return runCatching {
        val packContext = context.createPackageContext(
            packageName,
            Context.CONTEXT_IGNORE_SECURITY or Context.CONTEXT_INCLUDE_CODE
        )
        packContext.resources.getDrawable(iconResId, packContext.theme)
            .toBitmap(width = 96, height = 96)
    }.getOrNull()
}

private fun openMusicApp(context: Context, packageName: String?) {
    if (packageName.isNullOrBlank()) return
    runCatching {
        val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName) ?: return
        context.startActivity(launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

private fun openMediaOutputSwitcher(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return false
    return runCatching {
        SystemOutputSwitcherDialogController.showDialog(context)
    }.getOrDefault(false)
}

private fun openNotificationAccessSettings(context: Context) {
    runCatching {
        context.startActivity(
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        )
    }
}

private fun formatMediaTime(timeMs: Long): String {
    val totalSeconds = (timeMs / 1000L).coerceAtLeast(0L)
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return "%d:%02d".format(Locale.US, minutes, seconds)
}

private fun averageBitmapColor(bitmap: Bitmap): Color {
    val sampleWidth = 18
    val sampleHeight = 18
    val scaled = Bitmap.createScaledBitmap(bitmap, sampleWidth, sampleHeight, true)
    var red = 0L
    var green = 0L
    var blue = 0L
    var count = 0L
    for (x in 0 until sampleWidth) {
        for (y in 0 until sampleHeight) {
            val pixel = scaled.getPixel(x, y)
            red += android.graphics.Color.red(pixel)
            green += android.graphics.Color.green(pixel)
            blue += android.graphics.Color.blue(pixel)
            count++
        }
    }
    if (scaled != bitmap) scaled.recycle()
    val r = ((red / count) * 0.72f).roundToInt().coerceIn(0, 255)
    val g = ((green / count) * 0.72f).roundToInt().coerceIn(0, 255)
    val b = ((blue / count) * 0.72f).roundToInt().coerceIn(0, 255)
    return Color(android.graphics.Color.rgb(r, g, b))
}

private fun mediaArtworkContentFingerprint(bitmap: Bitmap): String {
    val width = bitmap.width.coerceAtLeast(1)
    val height = bitmap.height.coerceAtLeast(1)
    return runCatching {
        var hash = 0xcbf29ce484222325UL
        repeat(7) { row ->
            repeat(7) { column ->
                val x = (column * (width - 1) / 6).coerceIn(0, width - 1)
                val y = (row * (height - 1) / 6).coerceIn(0, height - 1)
                hash = (hash xor bitmap.getPixel(x, y).toUInt().toULong()) * 0x100000001b3UL
            }
        }
        "${width}x$height:${hash.toString(16)}"
    }.getOrElse {
        "${width}x$height"
    }
}
