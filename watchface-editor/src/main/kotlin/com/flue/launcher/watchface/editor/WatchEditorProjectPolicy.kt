package com.flue.launcher.watchface.editor

import com.flue.launcher.watchface.store.catalog.FileWatchFaceCatalog
import com.flue.launcher.watchface.store.catalog.WatchFaceInstallSource
import java.io.File

/** Local workflow provenance, not a copyright certificate or a claim of authorship. */
data class WatchEditorProjectRights(
    val origin: Origin,
    val editRightsDeclared: Boolean,
) {
    enum class Origin { CREATED, EXTERNAL }
}

class WatchEditorRightsRequiredException : IllegalArgumentException("Editing rights declaration required")
class WatchEditorMarketSourceException : IllegalArgumentException("Market downloads cannot be edited or published")

object WatchEditorProjectPolicy {
    private const val MARKER = ".flue/editor-project-rights"

    fun isMarketSource(source: WatchFaceInstallSource): Boolean =
        source is WatchFaceInstallSource.Market ||
            (source is WatchFaceInstallSource.LocalImport &&
                source.sourceLabel?.trim()?.startsWith("market:", ignoreCase = true) == true)

    /** Check the old Studio catalog even when opening directly by directory. Never detach it. */
    fun requireNonMarket(directory: File, launcherId: String? = null) {
        if (launcherId?.startsWith("market:", ignoreCase = true) == true) throw WatchEditorMarketSourceException()
        val source = directory.canonicalFile
        val filesRoot = source.parentFile?.takeIf { it.name == "watchmaker_faces" }?.parentFile ?: return
        val catalogFile = File(filesRoot, "watchface/watch-face-catalog.json")
        if (!catalogFile.exists()) return
        val snapshot = FileWatchFaceCatalog(catalogFile).read()
        check(snapshot.diagnostic == null) { "Cannot verify historical project provenance" }
        val aliases = setOfNotNull(launcherId, "watchmaker:${source.name}")
        if (snapshot.records.any { record ->
                isMarketSource(record.source) && aliases.any {
                    record.logicalId == it || record.activeLauncherId == it || it in record.legacyIds
                }
            }) throw WatchEditorMarketSourceException()
    }

    fun declareExternalImport(directory: File, launcherId: String? = null) {
        requireNonMarket(directory, launcherId)
        write(directory, WatchEditorProjectRights(WatchEditorProjectRights.Origin.EXTERNAL, true))
    }

    fun open(directory: File, rightsDeclared: Boolean, launcherId: String? = null): WatchEditorProjectRights {
        requireNonMarket(directory, launcherId)
        val stored = read(directory)
        if (stored != null && (stored.origin == WatchEditorProjectRights.Origin.CREATED || stored.editRightsDeclared)) {
            return stored
        }
        if (!rightsDeclared) throw WatchEditorRightsRequiredException()
        return WatchEditorProjectRights(WatchEditorProjectRights.Origin.EXTERNAL, true).also { write(directory, it) }
    }

    fun read(directory: File): WatchEditorProjectRights? {
        val marker = File(directory, MARKER)
        if (!marker.isFile || marker.length() !in 1L..128L) return null
        return when (marker.readText()) {
            "v1\ncreated\n" -> WatchEditorProjectRights(WatchEditorProjectRights.Origin.CREATED, false)
            "v1\nexternal\nedit-rights-declared\n" -> WatchEditorProjectRights(WatchEditorProjectRights.Origin.EXTERNAL, true)
            else -> null
        }
    }

    internal fun write(directory: File, rights: WatchEditorProjectRights) {
        requireNonMarket(directory)
        val marker = File(directory, MARKER)
        check(marker.parentFile!!.isDirectory || marker.parentFile!!.mkdirs())
        // Saving a new project over a content-identical external install must not relabel it.
        val effective = read(directory)?.takeIf { it.origin == WatchEditorProjectRights.Origin.EXTERNAL } ?: rights
        marker.writeText(when (effective.origin) {
            WatchEditorProjectRights.Origin.CREATED -> "v1\ncreated\n"
            WatchEditorProjectRights.Origin.EXTERNAL -> {
                require(effective.editRightsDeclared)
                "v1\nexternal\nedit-rights-declared\n"
            }
        })
    }

    fun requirePublishable(rights: WatchEditorProjectRights, publicationRightsDeclared: Boolean) {
        require(rights.origin == WatchEditorProjectRights.Origin.CREATED || rights.editRightsDeclared)
        require(publicationRightsDeclared) { "Publication rights declaration required" }
    }
}
