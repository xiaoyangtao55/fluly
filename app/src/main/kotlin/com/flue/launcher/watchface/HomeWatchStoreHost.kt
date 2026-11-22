package com.flue.launcher.watchface

import android.app.Activity
import android.app.Application
import android.net.Uri
import com.flue.launcher.util.RecentsVisibility
import com.flue.launcher.viewmodel.LauncherViewModel
import com.flue.launcher.watchface.standard.WatchArchiveService
import com.flue.launcher.watchface.standard.WatchmakerWatchFaceStorage
import com.flue.launcher.watchface.store.ui.WatchStoreHost
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** HOME owns store installations; Studio never receives a private installed-directory path. */
class HomeWatchStoreHost(private val application: Application) : WatchStoreHost {
    // Created only on first store use; this ViewModel observes HOME's same DataStore/storage.
    private val model by lazy { LauncherViewModel(application) }
    override val themeMode get() = model.themeMode
    override val uiStyle get() = model.uiStyle
    override val screenShapeOverride get() = model.screenShapeOverride
    override val globalUiScalePercent get() = model.globalUiScalePercent
    override val rotaryHapticsEnabled get() = model.rotaryHapticsEnabled
    override val availableWatchFaces get() = model.availableWatchFaces

    override suspend fun installWatchStoreArchive(archive: File, installAsRound: Boolean) = runCatching {
        val installed = withContext(Dispatchers.IO) {
            val descriptor = WatchmakerWatchFaceStorage.importArchiveFile(application, archive, forceNewInstall = true)
            WatchArchiveService.setInstalledShape(application, descriptor, installAsRound)
            WatchmakerWatchFaceStorage.scan(application).firstOrNull { it.id == descriptor.id } ?: descriptor
        }
        model.refreshWatchFaces(force = true)
        installed
    }

    override suspend fun importWatchFaceArchive(uri: Uri) = runCatching {
        val installed = withContext(Dispatchers.IO) {
            WatchmakerWatchFaceStorage.importArchive(application, uri)
        }
        model.refreshWatchFaces(force = true)
        installed
    }

    override fun selectWatchFace(launcherId: String) = model.selectWatchFace(launcherId)
    override suspend fun deleteImportedWatchFaces(ids: Collection<String>) = model.deleteImportedWatchFaces(ids)
    override fun readHideFromRecents() = RecentsVisibility.readPreference(application)
    override fun applyHideFromRecents(activity: Activity, enabled: Boolean) = RecentsVisibility.apply(activity, enabled)
}
