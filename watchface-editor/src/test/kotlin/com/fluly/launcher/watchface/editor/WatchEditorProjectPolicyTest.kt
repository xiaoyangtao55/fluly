package com.fluly.launcher.watchface.editor

import com.fluly.launcher.watchface.store.catalog.FileWatchFaceCatalog
import com.fluly.launcher.watchface.store.catalog.WatchFaceInstallSource
import com.fluly.launcher.watchface.store.catalog.WatchFaceRecord
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipFile

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [27], manifest = Config.NONE)
class WatchEditorProjectPolicyTest {
    @Test fun unknownAndAuthorOwnAreNotOriginalOrAutomaticallyEditable() = withRoot { root ->
        val source = source(root)
        File(source, "watch.xml").writeText("<Watch author=\"own\" build_source=\"studio\"/>")
        assertThrows(WatchEditorRightsRequiredException::class.java) {
            WatchEditorSession.openInstalled(source, File(root, "cache"))
        }
        WatchEditorSession.openInstalled(source, File(root, "cache"), editRightsDeclared = true).use {
            assertEquals(WatchEditorProjectRights.Origin.EXTERNAL, it.projectRights.origin)
            assertTrue(it.projectRights.editRightsDeclared)
            assertThrows(IllegalArgumentException::class.java) { it.requirePublicationRights(false) }
            it.requirePublicationRights(true)
        }
    }

    @Test fun historicalMarketAndDetachedMarketLabelAreBlockedEvenWithDeclaration() = withRoot { root ->
        val installed = File(root, "watchmaker_faces/watch_example").apply { mkdirs() }
        File(installed, "watch.xml").writeText("<Watch/>")
        val catalog = FileWatchFaceCatalog(File(root, "watchface/watch-face-catalog.json"))
        listOf(
            WatchFaceInstallSource.Market("store", "remote"),
            WatchFaceInstallSource.LocalImport("market:historical"),
        ).forEach { origin ->
            catalog.upsert(WatchFaceRecord(
                logicalId = "old-project", activeRevisionSha256 = "a".repeat(64),
                installedAtEpochMs = 1, displayNameOverride = null, source = origin,
                legacyIds = setOf("watchmaker:watch_example"),
                activeLauncherId = "watchmaker:watch_example",
            ))
            assertThrows(WatchEditorMarketSourceException::class.java) {
                WatchEditorSession.openInstalled(installed, File(root, "cache"), editRightsDeclared = true)
            }
            assertThrows(WatchEditorMarketSourceException::class.java) {
                WatchEditorProjectPolicy.write(installed,
                    WatchEditorProjectRights(WatchEditorProjectRights.Origin.CREATED, false))
            }
            assertEquals(origin, catalog.read().records.single().source)
        }
    }

    @Test fun newProjectsWorkButPublicationStillRequiresSeparateDeclaration() = withRoot { root ->
        WatchEditorSession.createBlank(File(root, "cache")).use { session ->
            assertEquals(WatchEditorProjectRights.Origin.CREATED, session.projectRights.origin)
            session.addLayer("text")
            assertThrows(IllegalArgumentException::class.java) { session.requirePublicationRights(false) }
            session.requirePublicationRights(true)
        }
    }

    @Test fun savedRevisionsAndCopiedRightsKeepExternalOriginAndArchiveDoesNotCarryClaim() = withRoot { root ->
        val installed = source(root)
        WatchEditorSession.openInstalled(installed, File(root, "cache"),
            logicalId = "watchmaker:source", installedDirectory = installed, editRightsDeclared = true).use { session ->
            session.updateRootAttribute("author", "own")
            session.saveCurrentRevision()
            assertEquals(WatchEditorProjectRights.Origin.EXTERNAL, WatchEditorProjectPolicy.read(installed)?.origin)
            val copy = File(root, "copy").apply { mkdirs() }
            WatchEditorProjectPolicy.write(copy, session.projectRights)
            assertEquals(session.projectRights, WatchEditorProjectPolicy.read(copy))
            val archive = File(root, "export.watch")
            session.exportWatch(archive)
            ZipFile(archive).use { zip -> assertFalse(zip.entries().asSequence().any { it.name.startsWith(".fluly/") }) }
            val imported = File(root, "reimport").apply { mkdirs() }
            File(imported, "watch.xml").writeText("<Watch author=\"own\"/>")
            assertThrows(WatchEditorRightsRequiredException::class.java) {
                WatchEditorSession.openInstalled(imported, File(root, "cache2"))
            }
        }
    }

    private fun source(root: File) = File(root, "source").apply {
        mkdirs()
        File(this, "watch.xml").writeText("<Watch/>")
    }
    private fun withRoot(block: (File) -> Unit) {
        val root = Files.createTempDirectory("editor-rights").toFile()
        try { block(root) } finally { root.deleteRecursively() }
    }
}
