package com.fluly.launcher.ui.store

import com.fluly.launcher.watchface.store.catalog.WatchFaceInstallSource

/** Ownership cannot be inferred for a market download, a legacy install, or an unknown origin. */
internal fun installedSourceMayBePublished(source: WatchFaceInstallSource?): Boolean =
    source is WatchFaceInstallSource.LocalImport &&
        source.sourceLabel?.trim()?.startsWith("market:", ignoreCase = true) != true

internal fun maySubmitWatchStoreArchive(hasArchive: Boolean, rightsConfirmed: Boolean): Boolean =
    !hasArchive || rightsConfirmed
