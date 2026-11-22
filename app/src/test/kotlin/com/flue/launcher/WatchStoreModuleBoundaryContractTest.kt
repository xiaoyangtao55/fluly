package com.flue.launcher

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Source-only regression guard: HOME owns store consumption; Studio owns creation. */
class WatchStoreModuleBoundaryContractTest {
    private val root = generateSequence(File(".").canonicalFile) { it.parentFile }
        .first { File(it, "settings.gradle.kts").isFile && File(it, "app/src/main").isDirectory }

    @Test
    fun homeDependsOnStoreWithoutPullingEditorAgentOrFfmpeg() {
        assertTrue(projectDependencies("app").contains("watchface-store"))
        val closure = dependencyClosure("app")
        assertTrue("HOME must not pull creation modules: $closure", closure.intersect(setOf("watchface-editor", "agent")).isEmpty())
        closure.forEach { module ->
            assertFalse("HOME dependency $module pulls FFmpeg", buildSource(module).contains("ffmpeg", ignoreCase = true))
        }
        assertNoCreationImports("app")
    }

    @Test
    fun editorPublishesThroughClientWithoutDependingOnStoreUi() {
        assertTrue(projectDependencies("watchface-editor").contains("watchface-store-client"))
        assertFalse(dependencyClosure("watchface-editor").contains("watchface-store"))
        val violations = sources("watchface-editor").filter {
            Regex("""(?m)^\s*import\s+com\.flue\.launcher\.(?:ui\.store\.|store\.|WatchStoreActivity\b)""")
                .containsMatchIn(it.readText())
        }.map { it.relativeTo(root).path }.toList()
        assertEquals("Editor must not import store UI", emptyList<String>(), violations)
    }

    @Test
    fun studioManifestAndDependencyGraphDoNotHostStore() {
        assertFalse(dependencyClosure("studio").contains("watchface-store"))
        val manifest = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
            .newDocumentBuilder().parse(File(root, "studio/src/main/AndroidManifest.xml"))
        val activities = manifest.getElementsByTagName("activity")
        for (index in 0 until activities.length) {
            val name = activities.item(index).attributes
                .getNamedItemNS("http://schemas.android.com/apk/res/android", "name").nodeValue
            assertFalse("Studio declares store activity $name", name.contains("WatchStore"))
        }
    }

    @Test
    fun storeFileProviderDoesNotMergeIntoHomeProvider() {
        val android = "http://schemas.android.com/apk/res/android"
        fun providerAttributes(module: String): List<Triple<String, String, String>> {
            val document = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
                .newDocumentBuilder().parse(File(root, "$module/src/main/AndroidManifest.xml"))
            val providers = document.getElementsByTagName("provider")
            return (0 until providers.length).map { index ->
                val node = providers.item(index)
                Triple(
                    node.attributes.getNamedItemNS(android, "name").nodeValue,
                    node.attributes.getNamedItemNS(android, "authorities").nodeValue,
                    node.attributes.getNamedItemNS(android, "exported").nodeValue,
                )
            }
        }
        val home = providerAttributes("app")
        val store = providerAttributes("watchface-store").single()
        assertEquals("com.flue.launcher.watchface.store.ui.WatchStoreFileProvider", store.first)
        assertEquals("\${applicationId}.watchstore.files", store.second)
        assertEquals("false", store.third)
        assertFalse("Manifest merger keys providers by class, not authority", home.any { it.first == store.first })
        assertFalse("Store grants must keep a distinct authority", home.any { it.second == store.second })
    }

    @Test
    fun storeAndClientCannotImportSessionOrExposeWatchEditorEntry() {
        listOf("watchface-store", "watchface-store-client").forEach { module ->
            val closure = dependencyClosure(module)
            assertTrue("Store must not pull creation modules: $closure", closure.intersect(setOf("watchface-editor", "agent")).isEmpty())
            assertNoCreationImports(module)
            val violations = sources(module).filter {
                Regex("""\b(?:WatchEditorSession|WatchEditorActivity|openWatchEditor|launchWatchEditor|onEditWatchFace|watch_store_edit_in_editor)\b""")
                    .containsMatchIn(it.readText())
            }.map { it.relativeTo(root).path }.toList()
            assertEquals("Store must not expose a watch-face editing entry", emptyList<String>(), violations)
        }
    }

    @Test
    fun homeOnlyEditsBuiltinConfigurationAndOpensStoreDirectly() {
        val settings = TestSourceRoots.read("com/example/wlauncher/SettingsActivity.kt")
        val selector = TestSourceRoots.read("com/example/wlauncher/ui/home/WatchFaceCardSelector.kt")
        assertFalse("HOME must not offer editing an installed archive", settings.contains("watchface_editor_current"))
        assertTrue(selector.contains("if (focused?.isBuiltin == true && capabilities?.canEdit == true)"))
        assertTrue(selector.contains("InternalWatchFaceConfigActivity::class.java"))
        listOf(settings, selector).forEach { source ->
            assertTrue(source.contains("com.flue.launcher.WatchStoreActivity::class.java"))
            assertFalse(source.contains("WatchEditorActivity::class.java"))
            assertFalse(source.contains("FlueStudioLauncher.openStore"))
        }
    }

    @Test
    fun dependencyParserIncludesApiAndImplementationProjectEdges() {
        assertEquals(
            setOf("watchface-core", "ui-kit"),
            parseProjectDependencies("""api(project(":watchface-core"))
                implementation(project(":ui-kit"))"""),
        )
    }

    @Test
    fun ciTestsEveryModuleButOnlyBuildsApksOnManualDispatch() {
        val ci = File(root, ".github/workflows/ci.yml").readText().replace("\r\n", "\n")
        val testJob = ci.substringAfter("  test:\n").substringBefore("\n  build:\n")
        listOf(
            "app", "studio", "agent", "ui-kit", "watchface-core", "watchface-runtime",
            "watchface-editor", "watchface-store-client", "watchface-store",
        ).forEach { module -> assertTrue("CI omits $module tests", testJob.contains(":$module:testDebugUnitTest")) }
        assertFalse("Push/PR test job must not assemble APKs", testJob.contains(":assemble"))
        val buildJob = ci.substringAfter("  build:\n").substringBefore("\n  release:\n")
        assertTrue(buildJob.contains("if: github.event_name == 'workflow_dispatch'"))
        val releaseJob = ci.substringAfter("  release:\n")
        assertTrue(releaseJob.contains("if: github.event_name == 'workflow_dispatch' && startsWith(github.ref, 'refs/tags/v')"))
    }

    private fun assertNoCreationImports(module: String) {
        val forbidden = Regex("""(?m)^\s*import\s+(?:com\.flue\.launcher\.(?:watchface\.(?:editor|agent)\.|ui\.editor\.|WatchEditorActivity\b)|com\.arthenica\.ffmpegkit\.)""")
        val violations = sources(module).filter { forbidden.containsMatchIn(it.readText()) }
            .map { it.relativeTo(root).path }.toList()
        assertEquals("$module must not import editor, Agent, or FFmpeg", emptyList<String>(), violations)
    }

    private fun sources(module: String): Sequence<File> {
        val main = File(root, "$module/src/main")
        check(main.isDirectory) { "Missing module sources: $main" }
        return main.walkTopDown().onEnter { it.name != "build" }
            .filter { it.isFile && it.extension in setOf("kt", "java") }
    }

    private fun buildSource(module: String): String = File(root, "$module/build.gradle.kts")
        .readLines().joinToString("\n") { it.substringBefore("//") }

    private fun projectDependencies(module: String): Set<String> =
        parseProjectDependencies(buildSource(module))

    private fun parseProjectDependencies(source: String): Set<String> =
        Regex("""\bproject\(\s*":([\w-]+)"\s*\)""").findAll(source)
            .map { it.groupValues[1] }.toSet()

    private fun dependencyClosure(module: String): Set<String> {
        val visited = linkedSetOf<String>()
        fun visit(name: String) {
            if (visited.add(name)) projectDependencies(name).forEach(::visit)
        }
        visit(module)
        return visited
    }
}
