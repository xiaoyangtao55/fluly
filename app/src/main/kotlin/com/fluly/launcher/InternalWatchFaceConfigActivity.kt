package com.fluly.launcher

import com.fluly.launcher.i18n.FlulyComponentActivity

import android.app.Application
import android.app.Activity.RESULT_OK
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.activity.result.contract.ActivityResultContracts.OpenDocument
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.spring
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fluly.launcher.ui.home.BuiltInWatchFacePreview
import com.fluly.launcher.ui.home.FIXED_PREVIEW_CLOCK
import com.fluly.launcher.ui.style.flulyOverscroll
import com.fluly.launcher.ui.input.flulyRotaryScrollable
import com.fluly.launcher.ui.input.ROTARY_SCROLL_MULTIPLIER
import com.fluly.launcher.ui.input.requestFocusAfterFirstFrame
import com.fluly.launcher.ui.style.FlulySlider
import com.fluly.launcher.ui.style.FlulySwitch
import com.fluly.launcher.ui.theme.LauncherTheme
import com.fluly.launcher.ui.theme.ProvideGlobalUiScale
import com.fluly.launcher.ui.theme.WatchColors
import com.fluly.launcher.ui.theme.WatchLauncherTheme
import com.fluly.launcher.util.RecentsVisibility
import com.fluly.launcher.viewmodel.LauncherViewModel
import com.fluly.launcher.watchface.BUILT_IN_PHOTO_WATCHFACE_ID
import com.fluly.launcher.watchface.BuiltInWatchFaceOptions
import com.fluly.launcher.watchface.InternalWatchFaceStorage
import com.fluly.launcher.watchface.WatchClockColorMode
import com.fluly.launcher.watchface.WatchClockPosition
import kotlinx.coroutines.launch
import java.io.File

const val EXTRA_INTERNAL_WATCHFACE_ID = "internal_watchface_id"

class InternalWatchFaceConfigActivity : FlulyComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        RecentsVisibility.apply(this)
        val watchFaceId = intent.getStringExtra(EXTRA_INTERNAL_WATCHFACE_ID) ?: BUILT_IN_PHOTO_WATCHFACE_ID
        setContent {
            val viewModel: LauncherViewModel = viewModel()
            val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
            val uiStyle by viewModel.uiStyle.collectAsStateWithLifecycle()
            val globalUiScalePercent by viewModel.globalUiScalePercent.collectAsStateWithLifecycle()
            WatchLauncherTheme(themeMode = themeMode, uiStyle = uiStyle) {
                ProvideGlobalUiScale(globalUiScalePercent) {
                    InternalWatchFaceConfigScreen(
                        watchFaceId = watchFaceId,
                        onBack = { finish() }
                    )
                }
            }
        }
    }
}
@Composable
private fun InternalWatchFaceConfigScreen(
    watchFaceId: String,
    onBack: () -> Unit
) {
    val vm: LauncherViewModel = viewModel()
    val application = vm.getApplication<Application>()
    val launcherStyle = LauncherTheme.style
    val photoPath by vm.builtInPhotoPath.collectAsStateWithLifecycle()
    val videoPath by vm.builtInVideoPath.collectAsStateWithLifecycle()
    val photoClockPosition by vm.builtInPhotoClockPosition.collectAsStateWithLifecycle()
    val videoClockPosition by vm.builtInVideoClockPosition.collectAsStateWithLifecycle()
    val photoClockSize by vm.builtInPhotoClockSize.collectAsStateWithLifecycle()
    val videoClockSize by vm.builtInVideoClockSize.collectAsStateWithLifecycle()
    val photoClockBold by vm.builtInPhotoClockBold.collectAsStateWithLifecycle()
    val videoClockBold by vm.builtInVideoClockBold.collectAsStateWithLifecycle()
    val videoFillScreen by vm.builtInVideoFillScreen.collectAsStateWithLifecycle()
    val videoClockColorMode by vm.builtInVideoClockColorMode.collectAsStateWithLifecycle()
    val watchFaceFontPath by vm.builtInWatchFaceFontPath.collectAsStateWithLifecycle()
    val photoUseThemeTextColor by vm.builtInPhotoUseThemeTextColor.collectAsStateWithLifecycle()
    val videoUseThemeTextColor by vm.builtInVideoUseThemeTextColor.collectAsStateWithLifecycle()
    val photoTextColorArgb by vm.builtInPhotoTextColorArgb.collectAsStateWithLifecycle()
    val videoTextColorArgb by vm.builtInVideoTextColorArgb.collectAsStateWithLifecycle()
    val photoShowSeconds by vm.builtInPhotoShowSeconds.collectAsStateWithLifecycle()
    val videoShowSeconds by vm.builtInVideoShowSeconds.collectAsStateWithLifecycle()
    val photoCustomText by vm.builtInPhotoCustomText.collectAsStateWithLifecycle()
    val videoCustomText by vm.builtInVideoCustomText.collectAsStateWithLifecycle()
    val isPhoto = watchFaceId == BUILT_IN_PHOTO_WATCHFACE_ID
    val currentPath = if (isPhoto) photoPath else videoPath
    val activeClockPosition = if (isPhoto) photoClockPosition else videoClockPosition
    val activeClockSize = if (isPhoto) photoClockSize else videoClockSize
    val activeClockBold = if (isPhoto) photoClockBold else videoClockBold
    val activeUseThemeTextColor = if (isPhoto) photoUseThemeTextColor else videoUseThemeTextColor
    val activeTextColorArgb = if (isPhoto) photoTextColorArgb else videoTextColorArgb
    val activeShowSeconds = if (isPhoto) photoShowSeconds else videoShowSeconds
    val activeCustomText = if (isPhoto) photoCustomText else videoCustomText
    var localPath by remember(watchFaceId) { mutableStateOf(currentPath) }
    var localClockPosition by remember(watchFaceId) { mutableStateOf(activeClockPosition) }
    var localClockSize by remember(watchFaceId) { mutableFloatStateOf(activeClockSize.toFloat()) }
    var localClockBold by remember(watchFaceId) { mutableStateOf(activeClockBold) }
    var localUseThemeTextColor by remember(watchFaceId) { mutableStateOf(activeUseThemeTextColor) }
    var localTextColorArgb by remember(watchFaceId) { mutableStateOf(activeTextColorArgb) }
    var localShowSeconds by remember(watchFaceId) { mutableStateOf(activeShowSeconds) }
    var localCustomText by remember(watchFaceId) { mutableStateOf(activeCustomText) }
    var localVideoFillScreen by remember(watchFaceId) { mutableStateOf(videoFillScreen) }
    var localVideoClockColorMode by remember(watchFaceId) { mutableStateOf(videoClockColorMode) }

    androidx.compose.runtime.LaunchedEffect(
        watchFaceId,
        currentPath,
        activeClockPosition,
        activeClockSize,
        activeClockBold,
        activeUseThemeTextColor,
        activeTextColorArgb,
        activeShowSeconds,
        activeCustomText,
        videoFillScreen,
        videoClockColorMode
    ) {
        localPath = currentPath
        localClockPosition = activeClockPosition
        localClockSize = activeClockSize.toFloat()
        localClockBold = activeClockBold
        localUseThemeTextColor = activeUseThemeTextColor
        localTextColorArgb = activeTextColorArgb
        localShowSeconds = activeShowSeconds
        localCustomText = activeCustomText
        localVideoFillScreen = videoFillScreen
        localVideoClockColorMode = videoClockColorMode
    }

    fun persistPendingChanges() {
        if (isPhoto) {
            if (localPath != photoPath) vm.setBuiltInPhotoPath(localPath)
            if (localClockPosition != photoClockPosition) vm.setBuiltInPhotoClockPosition(localClockPosition)
            if (localClockSize.toInt() != photoClockSize) vm.setBuiltInPhotoClockSize(localClockSize.toInt())
            if (localClockBold != photoClockBold) vm.setBuiltInPhotoClockBold(localClockBold)
            if (localUseThemeTextColor != photoUseThemeTextColor) vm.setBuiltInPhotoUseThemeTextColor(localUseThemeTextColor)
            if (localTextColorArgb != photoTextColorArgb) vm.setBuiltInPhotoTextColorArgb(localTextColorArgb)
            if (localShowSeconds != photoShowSeconds) vm.setBuiltInPhotoShowSeconds(localShowSeconds)
            if (localCustomText != photoCustomText) vm.setBuiltInPhotoCustomText(localCustomText)
        } else {
            if (localPath != videoPath) vm.setBuiltInVideoPath(localPath)
            if (localClockPosition != videoClockPosition) vm.setBuiltInVideoClockPosition(localClockPosition)
            if (localClockSize.toInt() != videoClockSize) vm.setBuiltInVideoClockSize(localClockSize.toInt())
            if (localClockBold != videoClockBold) vm.setBuiltInVideoClockBold(localClockBold)
            if (localUseThemeTextColor != videoUseThemeTextColor) vm.setBuiltInVideoUseThemeTextColor(localUseThemeTextColor)
            if (localTextColorArgb != videoTextColorArgb) vm.setBuiltInVideoTextColorArgb(localTextColorArgb)
            if (localShowSeconds != videoShowSeconds) vm.setBuiltInVideoShowSeconds(localShowSeconds)
            if (localCustomText != videoCustomText) vm.setBuiltInVideoCustomText(localCustomText)
            if (localVideoFillScreen != videoFillScreen) vm.setBuiltInVideoFillScreen(localVideoFillScreen)
            if (localVideoClockColorMode != videoClockColorMode) vm.setBuiltInVideoClockColorMode(localVideoClockColorMode)
        }
    }

    val picker = rememberLauncherForActivityResult(OpenDocument()) { uri ->
        if (uri != null) {
            val savedPath = handlePickedMedia(
                application = application,
                watchFaceId = watchFaceId,
                sourceUri = uri,
                onMessage = { message ->
                    Toast.makeText(application, message, Toast.LENGTH_SHORT).show()
                }
            )
            if (!savedPath.isNullOrBlank()) {
                localPath = savedPath
                if (isPhoto) {
                    vm.setBuiltInPhotoPath(savedPath)
                } else {
                    vm.setBuiltInVideoPath(savedPath)
                }
            }
        }
    }
    val fontPicker = rememberLauncherForActivityResult(OpenDocument()) { uri ->
        if (uri != null) {
            val savedPath = InternalWatchFaceStorage.copyFont(application, uri)
            if (savedPath.isNullOrBlank()) {
                Toast.makeText(
                    application,
                    application.getString(R.string.internal_watchface_font_save_failed),
                    Toast.LENGTH_SHORT,
                ).show()
            } else {
                vm.setBuiltInWatchFaceFontPath(savedPath)
                Toast.makeText(
                    application,
                    application.getString(R.string.internal_watchface_font_updated),
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }
    }
    val fileManagerPicker = rememberLauncherForActivityResult(StartActivityForResult()) { result ->
        if (result.resultCode != RESULT_OK) return@rememberLauncherForActivityResult
        val uriText = result.data?.getStringExtra(EXTRA_FILE_MANAGER_RESULT_URI).orEmpty()
        val uri = uriText.takeIf { it.isNotBlank() }?.let(Uri::parse) ?: return@rememberLauncherForActivityResult
        val savedPath = handlePickedMedia(
            application = application,
            watchFaceId = watchFaceId,
            sourceUri = uri,
            onMessage = { message ->
                Toast.makeText(application, message, Toast.LENGTH_SHORT).show()
            }
        )
        if (!savedPath.isNullOrBlank()) {
            localPath = savedPath
            if (isPhoto) vm.setBuiltInPhotoPath(savedPath) else vm.setBuiltInVideoPath(savedPath)
        }
    }

    BackHandler {
        persistPendingChanges()
        onBack()
    }

    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocusAfterFirstFrame()
    }

    Column(
            modifier = Modifier
                .fillMaxSize()
                .background(launcherStyle.screenBackground)
                .flulyRotaryScrollable(focusRequester, ROTARY_SCROLL_MULTIPLIER) { rotaryDelta ->
                    scope.launch { scrollState.scrollBy(-rotaryDelta) }
                }
                .flulyOverscroll(scrollState)
                .verticalScroll(scrollState)
                .padding(horizontal = 18.dp, vertical = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
        Text(
            text = stringResource(
                if (isPhoto) R.string.internal_watchface_photo_title
                else R.string.internal_watchface_video_title,
            ),
            color = launcherStyle.titleColor,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = stringResource(
                if (isPhoto) R.string.internal_watchface_photo_subtitle
                else R.string.internal_watchface_video_subtitle,
            ),
            color = WatchColors.TextTertiary,
            fontSize = 13.sp
        )
        Spacer(modifier = Modifier.height(22.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(launcherStyle.panelShape)
                .background(Color(0xFF10141D)),
            contentAlignment = Alignment.Center
        ) {
            BuiltInWatchFacePreview(
                watchFaceId = watchFaceId,
                photoPath = if (isPhoto) localPath else photoPath,
                videoPath = if (isPhoto) videoPath else localPath,
                photoOptions = BuiltInWatchFaceOptions(
                    clockPosition = if (isPhoto) localClockPosition else photoClockPosition,
                    clockSizeSp = localClockSize.toInt(),
                    boldClock = if (isPhoto) localClockBold else photoClockBold,
                    showSeconds = if (isPhoto) localShowSeconds else photoShowSeconds,
                    customText = if (isPhoto) localCustomText else photoCustomText,
                    fontPath = watchFaceFontPath,
                    useThemeTextColor = if (isPhoto) localUseThemeTextColor else photoUseThemeTextColor,
                    textColorArgb = if (isPhoto) localTextColorArgb else photoTextColorArgb
                ),
                videoOptions = BuiltInWatchFaceOptions(
                    clockPosition = if (isPhoto) videoClockPosition else localClockPosition,
                    clockSizeSp = localClockSize.toInt(),
                    boldClock = if (isPhoto) videoClockBold else localClockBold,
                    cropToFill = if (isPhoto) videoFillScreen else localVideoFillScreen,
                    clockColorMode = if (isPhoto) videoClockColorMode else localVideoClockColorMode,
                    showSeconds = if (isPhoto) videoShowSeconds else localShowSeconds,
                    customText = if (isPhoto) videoCustomText else localCustomText,
                    fontPath = watchFaceFontPath,
                    useThemeTextColor = if (isPhoto) videoUseThemeTextColor else localUseThemeTextColor,
                    textColorArgb = if (isPhoto) videoTextColorArgb else localTextColorArgb
                ),
                clockOverride = FIXED_PREVIEW_CLOCK,
                showClock = true,
                playVideo = true,
                showBottomFade = false,
                modifier = Modifier.fillMaxSize()
            )
        }

        Spacer(modifier = Modifier.height(22.dp))

        ToggleRow(
            label = stringResource(R.string.internal_watchface_use_theme_color),
            enabled = localUseThemeTextColor,
            onToggle = {
                localUseThemeTextColor = !localUseThemeTextColor
                if (isPhoto) {
                    vm.setBuiltInPhotoUseThemeTextColor(localUseThemeTextColor)
                } else {
                    vm.setBuiltInVideoUseThemeTextColor(localUseThemeTextColor)
                }
            }
        )
        if (!localUseThemeTextColor) {
            Spacer(modifier = Modifier.height(10.dp))
            ColorRgbSliderGroup(
                title = stringResource(R.string.internal_watchface_text_color),
                argb = localTextColorArgb,
                onColorChange = {
                    localTextColorArgb = it
                    if (isPhoto) vm.setBuiltInPhotoTextColorArgb(it) else vm.setBuiltInVideoTextColorArgb(it)
                }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.internal_watchface_clock_position),
            color = launcherStyle.titleColor,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        PositionPickerRow(
            current = localClockPosition,
            onSelect = {
                localClockPosition = it
                if (isPhoto) {
                    vm.setBuiltInPhotoClockPosition(it)
                } else {
                    vm.setBuiltInVideoClockPosition(it)
                }
            }
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = stringResource(
                R.string.internal_watchface_clock_size_fmt,
                localClockSize.toInt(),
            ),
            color = launcherStyle.titleColor,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.fillMaxWidth()
        )
        FlulySlider(
            value = localClockSize,
            onValueChange = {
                localClockSize = it
            },
            onValueChangeFinished = {
                val value = localClockSize.toInt()
                if (isPhoto) vm.setBuiltInPhotoClockSize(value) else vm.setBuiltInVideoClockSize(value)
            },
            valueRange = 28f..92f,
            steps = 15,
        )

        Spacer(modifier = Modifier.height(8.dp))
        ToggleRow(
            label = stringResource(R.string.internal_watchface_bold_clock),
            enabled = localClockBold,
            onToggle = {
                localClockBold = !localClockBold
                if (isPhoto) {
                    vm.setBuiltInPhotoClockBold(localClockBold)
                } else {
                    vm.setBuiltInVideoClockBold(localClockBold)
                }
            }
        )

        Spacer(modifier = Modifier.height(8.dp))
        ToggleRow(
            label = stringResource(R.string.internal_watchface_show_seconds),
            enabled = localShowSeconds,
            onToggle = {
                localShowSeconds = !localShowSeconds
                if (isPhoto) {
                    vm.setBuiltInPhotoShowSeconds(localShowSeconds)
                } else {
                    vm.setBuiltInVideoShowSeconds(localShowSeconds)
                }
            }
        )

        Spacer(modifier = Modifier.height(12.dp))
        CustomTextField(
            value = localCustomText,
            onValueChange = { text ->
                localCustomText = text.take(32)
                if (isPhoto) {
                    vm.setBuiltInPhotoCustomText(localCustomText)
                } else {
                    vm.setBuiltInVideoCustomText(localCustomText)
                }
            }
        )

        Spacer(modifier = Modifier.height(8.dp))
        val fontActionText = watchFaceFontPath?.let { path ->
            stringResource(R.string.internal_watchface_change_font_fmt, File(path).name)
        } ?: stringResource(R.string.internal_watchface_select_font)
        ActionButton(text = fontActionText) {
            fontPicker.launch(
                arrayOf(
                    "font/*",
                    "application/x-font-ttf",
                    "application/x-font-otf",
                    "application/vnd.ms-opentype",
                    "application/octet-stream"
                )
            )
        }

        if (!watchFaceFontPath.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(8.dp))
            ActionButton(text = stringResource(R.string.internal_watchface_reset_font)) {
                vm.setBuiltInWatchFaceFontPath(null)
            }
        }

        if (!isPhoto) {
            Spacer(modifier = Modifier.height(8.dp))
            ToggleRow(
                label = stringResource(R.string.internal_watchface_video_fill_screen),
                enabled = localVideoFillScreen,
                onToggle = {
                    localVideoFillScreen = !localVideoFillScreen
                    vm.setBuiltInVideoFillScreen(localVideoFillScreen)
                }
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.internal_watchface_clock_color),
                color = launcherStyle.titleColor,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
            ClockColorPickerRow(
                current = localVideoClockColorMode,
                onSelect = {
                    localVideoClockColorMode = it
                    vm.setBuiltInVideoClockColorMode(it)
                }
            )
        }

        Spacer(modifier = Modifier.height(18.dp))

        ActionButton(
            text = if (localPath.isNullOrBlank()) {
                stringResource(
                    if (isPhoto) R.string.internal_watchface_select_image
                    else R.string.internal_watchface_select_video,
                )
            } else {
                stringResource(
                    if (isPhoto) R.string.internal_watchface_change_image
                    else R.string.internal_watchface_change_video,
                )
            },
            onLongClick = {
                val intent = Intent(vm.getApplication(), BuiltInFileManagerActivity::class.java).apply {
                    putExtra(
                        EXTRA_FILE_MANAGER_MODE,
                        if (isPhoto) FILE_MANAGER_MODE_IMAGE else FILE_MANAGER_MODE_VIDEO
                    )
                }
                fileManagerPicker.launch(intent)
            }
        ) {
            picker.launch(arrayOf(if (isPhoto) "image/*" else "video/*"))
        }

        Spacer(modifier = Modifier.height(10.dp))

        ActionButton(
            text = stringResource(
                if (isPhoto) R.string.internal_watchface_clear_image
                else R.string.internal_watchface_clear_video,
            ),
            enabled = !localPath.isNullOrBlank()
        ) {
            localPath = null
            if (isPhoto) {
                vm.setBuiltInPhotoPath(null)
            } else {
                vm.setBuiltInVideoPath(null)
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        ActionButton(text = stringResource(R.string.common_back)) {
            persistPendingChanges()
            onBack()
        }
    }
}
@Composable
private fun ActionButton(
    text: String,
    enabled: Boolean = true,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit
) {
    var pressed by remember { mutableStateOf(false) }
    val launcherStyle = LauncherTheme.style
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.958f else 1f,
        animationSpec = androidx.compose.animation.core.tween(durationMillis = 170),
        label = "internal_action_press_scale"
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            }
            .clip(launcherStyle.rowShape)
            .background(if (enabled) WatchColors.SurfaceGlass else WatchColors.InactiveGray)
            .pointerInput(enabled, onLongClick) {
                if (!enabled) return@pointerInput
                detectTapGestures(
                    onPress = {
                        pressed = true
                        tryAwaitRelease()
                        pressed = false
                    },
                    onLongPress = { onLongClick?.invoke() },
                    onTap = { onClick() }
                )
            }
            .padding(vertical = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = if (enabled) WatchColors.ActiveCyan else WatchColors.TextTertiary,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

private fun handlePickedMedia(
    application: Application,
    watchFaceId: String,
    sourceUri: Uri,
    onMessage: (String) -> Unit
): String? {
    val context = application
    runCatching {
        context.contentResolver.takePersistableUriPermission(
            sourceUri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION
        )
    }
    val savedPath = if (watchFaceId == BUILT_IN_PHOTO_WATCHFACE_ID) {
        InternalWatchFaceStorage.copyPhoto(context, sourceUri)
    } else {
        InternalWatchFaceStorage.copyVideo(context, sourceUri)
    }

    if (savedPath.isNullOrBlank()) {
        onMessage(context.getString(R.string.internal_watchface_media_save_failed))
        return null
    }
    onMessage(context.getString(R.string.internal_watchface_media_updated))
    return savedPath
}
@Composable
private fun CustomTextField(
    value: String,
    onValueChange: (String) -> Unit
) {
    val launcherStyle = LauncherTheme.style
    Text(
        text = stringResource(R.string.internal_watchface_custom_text),
        color = launcherStyle.titleColor,
        fontSize = 15.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(modifier = Modifier.height(8.dp))
    TextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        placeholder = {
            Text(
                stringResource(R.string.internal_watchface_custom_text_hint),
                color = WatchColors.TextTertiary,
            )
        },
        colors = TextFieldDefaults.colors(
            focusedContainerColor = WatchColors.SurfaceGlass,
            unfocusedContainerColor = WatchColors.SurfaceGlass,
            disabledContainerColor = WatchColors.SurfaceGlass,
            focusedTextColor = launcherStyle.bodyColor,
            unfocusedTextColor = launcherStyle.bodyColor,
            focusedIndicatorColor = WatchColors.ActiveCyan,
            unfocusedIndicatorColor = launcherStyle.outlineColor,
            cursorColor = WatchColors.ActiveCyan
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clip(launcherStyle.rowShape)
    )
}

@Composable
private fun ColorRgbSliderGroup(
    title: String,
    argb: Int,
    onColorChange: (Int) -> Unit
) {
    val launcherStyle = LauncherTheme.style
    val red = android.graphics.Color.red(argb)
    val green = android.graphics.Color.green(argb)
    val blue = android.graphics.Color.blue(argb)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(launcherStyle.rowShape)
            .background(WatchColors.SurfaceGlass)
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                color = launcherStyle.titleColor,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold
            )
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(Color(argb))
            )
        }
        ColorChannelSlider("\u0052", red) { onColorChange(rgbArgb(it, green, blue)) }
        ColorChannelSlider("\u0047", green) { onColorChange(rgbArgb(red, it, blue)) }
        ColorChannelSlider("\u0042", blue) { onColorChange(rgbArgb(red, green, it)) }
    }
}

@Composable
private fun ColorChannelSlider(
    label: String,
    value: Int,
    onValueChange: (Int) -> Unit
) {
    Text(
        text = "$label  $value",
        color = WatchColors.TextTertiary,
        fontSize = 12.sp,
        modifier = Modifier.padding(top = 8.dp)
    )
    FlulySlider(
        value = value.toFloat(),
        onValueChange = { onValueChange(it.toInt().coerceIn(0, 255)) },
        valueRange = 0f..255f,
        // 254 格的逐格触觉会变成持续震动，这里只要吸附不要反馈 —— 交给 FlulySlider 的
        // 无刻度分支（端点各给一次）。
        steps = 0,
    )
}

private fun rgbArgb(red: Int, green: Int, blue: Int): Int {
    val r = red.coerceIn(0, 255)
    val g = green.coerceIn(0, 255)
    val b = blue.coerceIn(0, 255)
    return android.graphics.Color.argb(255, r, g, b)
}

@Composable
private fun PositionPickerRow(
    current: WatchClockPosition,
    onSelect: (WatchClockPosition) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        listOf(
            listOf(
                WatchClockPosition.TOP_LEFT to stringResource(R.string.internal_watchface_position_top_left),
                WatchClockPosition.TOP_CENTER to stringResource(R.string.internal_watchface_position_top_center),
                WatchClockPosition.TOP_RIGHT to stringResource(R.string.internal_watchface_position_top_right),
            ),
            listOf(
                WatchClockPosition.LEFT_CENTER to stringResource(R.string.internal_watchface_position_center_left),
                WatchClockPosition.CENTER to stringResource(R.string.internal_watchface_position_center),
                WatchClockPosition.RIGHT_CENTER to stringResource(R.string.internal_watchface_position_center_right),
            ),
            listOf(
                WatchClockPosition.BOTTOM_LEFT to stringResource(R.string.internal_watchface_position_bottom_left),
                WatchClockPosition.BOTTOM_CENTER to stringResource(R.string.internal_watchface_position_bottom_center),
                WatchClockPosition.BOTTOM_RIGHT to stringResource(R.string.internal_watchface_position_bottom_right),
            ),
        ).forEach { rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                rowItems.forEach { item ->
                    val (position, label) = item
                    SmallChoiceChip(
                        label = label,
                        selected = position == current,
                        modifier = Modifier
                            .width(92.dp)
                            .padding(horizontal = 4.dp)
                    ) {
                        onSelect(position)
                    }
                }
            }
        }
    }
}

@Composable
private fun ClockColorPickerRow(
    current: WatchClockColorMode,
    onSelect: (WatchClockColorMode) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
    ) {
        SmallChoiceChip(
            label = stringResource(R.string.internal_watchface_color_white),
            selected = current == WatchClockColorMode.WHITE,
            modifier = Modifier.width(120.dp)
        ) { onSelect(WatchClockColorMode.WHITE) }
        SmallChoiceChip(
            label = stringResource(R.string.internal_watchface_color_black),
            selected = current == WatchClockColorMode.BLACK,
            modifier = Modifier.width(120.dp)
        ) { onSelect(WatchClockColorMode.BLACK) }
    }
}

@Composable
private fun SmallChoiceChip(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val launcherStyle = LauncherTheme.style
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.958f else 1f,
        animationSpec = androidx.compose.animation.core.tween(durationMillis = 170),
        label = "clock_position_chip_press_scale"
    )
    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            }
            .clip(launcherStyle.rowShape)
            .background(if (selected) WatchColors.ActiveCyan.copy(alpha = 0.22f) else WatchColors.SurfaceGlass)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (selected) WatchColors.ActiveCyan else launcherStyle.bodyColor,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun ToggleRow(
    label: String,
    enabled: Boolean,
    onToggle: () -> Unit
) {
    val launcherStyle = LauncherTheme.style
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.958f else 1f,
        animationSpec = androidx.compose.animation.core.tween(durationMillis = 170),
        label = "toggle_row_press_scale"
    )
    val knobOffset by animateDpAsState(
        targetValue = if (enabled) 23.dp else 3.dp,
        animationSpec = spring(stiffness = 760f, dampingRatio = 0.82f),
        label = "clock_toggle_knob_offset"
    )
    val trackColor by animateColorAsState(
        targetValue = if (enabled) WatchColors.ActiveGreen else WatchColors.InactiveGray,
        label = "clock_toggle_track_color"
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            }
            .clip(launcherStyle.rowShape)
            .background(WatchColors.SurfaceGlass)
            .clickable(interactionSource = interaction, indication = null, onClick = onToggle)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = launcherStyle.bodyColor,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            // weight：Row 里没权重的子节点按顺序拿全部可用宽度，长标签会把后面的开关
            // 挤到 0 宽（AW 档轨道有 64dp，被挤扁就是「开关右边显示不完全」）。
            modifier = Modifier.weight(1f, fill = false).padding(end = 10.dp),
        )
        // 开关走抽象入口：三档风格各自的外观 + 触觉都在那一层，这里不再手画。
        FlulySwitch(checked = enabled, onCheckedChange = { onToggle() })
    }
}
