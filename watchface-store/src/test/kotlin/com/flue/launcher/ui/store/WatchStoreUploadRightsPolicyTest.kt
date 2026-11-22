package com.flue.launcher.ui.store

import com.flue.launcher.watchface.store.catalog.WatchFaceInstallSource
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WatchStoreUploadRightsPolicyTest {
    @Test fun marketAndUnknownInstallsAreNotOfferedForPublication() {
        assertFalse(installedSourceMayBePublished(WatchFaceInstallSource.Market("store", "id")))
        assertFalse(installedSourceMayBePublished(WatchFaceInstallSource.Legacy))
        assertFalse(installedSourceMayBePublished(null))
        assertFalse(installedSourceMayBePublished(WatchFaceInstallSource.LocalImport("market:old")))
        assertFalse(installedSourceMayBePublished(WatchFaceInstallSource.LocalImport(" MARKET:old")))
        assertTrue(installedSourceMayBePublished(WatchFaceInstallSource.LocalImport("file")))
    }

    @Test fun possessionDoesNotGrantPublishingRights() {
        assertFalse(maySubmitWatchStoreArchive(hasArchive = true, rightsConfirmed = false))
        assertTrue(maySubmitWatchStoreArchive(hasArchive = true, rightsConfirmed = true))
        assertTrue(maySubmitWatchStoreArchive(hasArchive = false, rightsConfirmed = false))
    }
}
