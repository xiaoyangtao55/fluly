package com.flue.launcher.watchface.store.ui

import android.app.Activity
import android.net.Uri
import com.flue.launcher.ui.common.FlueScreenShape
import com.flue.launcher.ui.theme.ThemeMode
import com.flue.launcher.ui.theme.UiStyle
import com.flue.launcher.watchface.WatchFaceDescriptor
import java.io.File
import kotlinx.coroutines.flow.StateFlow

/** Launcher-owned operations; the store never opens an editor or Agent. */
interface WatchStoreHost {
    val themeMode: StateFlow<ThemeMode>
    val uiStyle: StateFlow<UiStyle>
    val screenShapeOverride: StateFlow<FlueScreenShape>
    val globalUiScalePercent: StateFlow<Int>
    val rotaryHapticsEnabled: StateFlow<Boolean>
    val availableWatchFaces: StateFlow<List<WatchFaceDescriptor>>
    suspend fun importWatchFaceArchive(uri: Uri): Result<WatchFaceDescriptor>
    suspend fun installWatchStoreArchive(archive: File, installAsRound: Boolean): Result<WatchFaceDescriptor>
    fun selectWatchFace(launcherId: String)
    suspend fun deleteImportedWatchFaces(ids: Collection<String>): Result<Unit>
    fun readHideFromRecents(): Boolean
    fun applyHideFromRecents(activity: Activity, enabled: Boolean)
}

object WatchStoreHostProvider {
    @Volatile var host: WatchStoreHost? = null
    fun require(): WatchStoreHost = host ?: error("WatchStoreHost must be injected before opening the store")
}
