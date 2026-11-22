package com.flue.launcher.watchface.standard

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipFile

class WatchArchiveServiceTest {
    @get:Rule val temporary = TemporaryFolder()
    private val png = byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a, 1, 2, 3)

    @Test
    fun plainExportOverridesOnlyArchiveAndIncludesScriptsFontsAndPreviewFallback() {
        val source = temporary.newFolder("face")
        val original = """<Watch name="Original" author="Old" features="stale"><Layer type="image" path="images/face.png"/></Watch>"""
        put(source, "watch.xml", original.toByteArray())
        put(source, "images/face.png", png)
        put(source, "images/unused.png", png)
        put(source, "fonts_bm/font.png", png)
        put(source, "scripts/script.txt", "return '{shr}'".toByteArray())
        put(source, "preview_dim.jpg", byteArrayOf(1, 2))
        put(source, ".flue/installed-at", "123".toByteArray())
        put(source, "watch.pxml", "stale".toByteArray())
        val output = File(temporary.root, "plain.watch")
        val result = WatchArchiveService.exportInstalled(source, output, mapOf("author" to "Upload", "name" to null), false)
        assertEquals(output.canonicalFile, result.file)
        assertEquals(output.length(), result.bytesWritten)
        ZipFile(output).use { zip ->
            val document = parse(zip, "watch.xml")
            assertEquals("Upload", document.documentElement.getAttribute("author"))
            assertFalse(document.documentElement.hasAttribute("name"))
            assertEquals("Heart Rate", document.documentElement.getAttribute("features"))
            assertEquals("ffffff", document.documentElement.getAttribute("ucolor_default"))
            assertFalse(document.documentElement.hasAttribute("protection"))
            assertTrue(zip.getEntry("fonts_bm/font.png") != null)
            assertArrayEquals(byteArrayOf(1, 2), bytes(zip, "preview.jpg"))
            assertTrue(zip.getEntry("images/unused.png") == null)
            assertTrue(zip.getEntry(".flue/installed-at") == null)
            assertTrue(zip.getEntry("watch.pxml") == null)
            assertEquals(zip.size(), result.entryCount)
        }
        assertEquals(original, File(source, "watch.xml").readText())
        assertArrayEquals(png, File(source, "images/face.png").readBytes())
    }

    @Test
    fun protectedExportUsesEncryptedDefinitionAssetsAndPrefersWorkingCopyTwin() {
        val source = temporary.newFolder("protect")
        put(source, "watch.xml", """<Watch build_source="studio" author="Old"><Layer type="image" path="images/face.png" text="{dh23}"/></Watch>""".toByteArray())
        put(source, "images/face.png", png)
        put(source, "images/face.ppng", png + byteArrayOf(99))
        put(source, "preview.jpg", byteArrayOf(1))
        val output = File(temporary.root, "protected.watch")
        WatchArchiveService.exportInstalled(source, output, mapOf("author" to "Upload"), true)
        ZipFile(output).use { zip ->
            val shell = parse(zip, "watch.xml").documentElement
            assertEquals("y", shell.getAttribute("protection"))
            assertFalse(shell.hasChildNodes())
            val full = WatchmakerProtection.openDecodedXml(ByteArrayInputStream(bytes(zip, "watch.pxml"))).use {
                SecureWatchXmlParser.parse(it, SecureWatchXmlParser.DEFAULT_MAX_BYTES)
            }
            assertEquals("Upload", full.documentElement.getAttribute("author"))
            assertEquals("24", full.documentElement.getAttribute("features"))
            assertEquals("images/face.ppng", (full.getElementsByTagName("Layer").item(0) as org.w3c.dom.Element).getAttribute("path"))
            assertArrayEquals(WatchmakerProtection.protectAssetBytes(png), bytes(zip, "images/face.ppng"))
            assertTrue(zip.getEntry("images/face.png") == null)
        }
    }

    @Test
    fun defaultRetainsProtectionAndExplicitPlainExportDecodesAssets() {
        val source = temporary.newFolder("installed-protected")
        put(source, "watch.xml", """<Watch Protection="YES"><Layer type="image" path="images/face.ppng"/></Watch>""".toByteArray())
        put(source, "images/face.ppng", WatchmakerProtection.protectAssetBytes(png))
        put(source, "preview.jpg", byteArrayOf(1))
        val protected = File(temporary.root, "retained.watch")
        WatchArchiveService.exportInstalled(source, protected)
        ZipFile(protected).use { zip -> assertTrue(zip.getEntry("watch.pxml") != null) }
        val plain = File(temporary.root, "decoded.watch")
        WatchArchiveService.exportInstalled(source, plain, mapOf("features" to "Custom"), false)
        ZipFile(plain).use { zip ->
            assertArrayEquals(png, bytes(zip, "images/face.png"))
            assertTrue(zip.getEntry("watch.pxml") == null)
            val root = parse(zip, "watch.xml").documentElement
            assertFalse(root.hasAttribute("Protection"))
            assertEquals("Custom", root.getAttribute("features"))
        }
    }

    @Test
    fun shapeUpdatesPreparedXmlWithoutChangingProtectionOrInstallMetadata() {
        val source = temporary.newFolder("shape")
        val xml = put(source, "nested/watch.xml", """<Watch protection="y" shape="S"><Layer type="text" text="Hi"/></Watch>""".toByteArray())
        put(source, "nested/watch.pxml", WatchmakerProtection.encodeXml("<Watch shape=\"S\"/>".toByteArray()))
        WatchPreparedMarker.write(source, xml)
        WatchInstallMetadata.readOrCreate(source, 123L)
        WatchArchiveService.setInstalledShape(source, true)
        assertEquals("R", SecureWatchXmlParser.parse(xml).documentElement.getAttribute("shape"))
        assertEquals("y", SecureWatchXmlParser.parse(xml).documentElement.getAttribute("protection"))
        assertEquals(1, SecureWatchXmlParser.parse(xml).getElementsByTagName("Layer").length)
        WatchArchiveService.setInstalledShape(source, false)
        assertEquals("S", SecureWatchXmlParser.parse(xml).documentElement.getAttribute("shape"))
        assertEquals(123L, WatchInstallMetadata.read(source))
    }

    @Test
    fun rejectsNestedOutputAndSymlinksWithoutPublishing() {
        val source = temporary.newFolder("unsafe")
        put(source, "watch.xml", "<Watch/>".toByteArray())
        expectFailure { WatchArchiveService.exportInstalled(source, File(source, "output.watch")) }
        val outside = temporary.newFile("outside")
        Files.createSymbolicLink(File(source, "linked").toPath(), outside.toPath())
        val output = File(temporary.root, "not-published.watch")
        expectFailure { WatchArchiveService.exportInstalled(source, output) }
        assertFalse(output.exists())
    }

    @Test
    fun invalidOverrideDoesNotReplaceExistingArchive() {
        val source = temporary.newFolder("failure")
        put(source, "watch.xml", "<Watch/>".toByteArray())
        val output = temporary.newFile("existing.watch").apply { writeText("unchanged") }
        expectFailure { WatchArchiveService.exportInstalled(source, output, mapOf("invalid attribute" to "bad")) }
        assertEquals("unchanged", output.readText())
    }

    private fun put(root: File, path: String, bytes: ByteArray): File = File(root, path).apply {
        parentFile?.mkdirs()
        writeBytes(bytes)
    }
    private fun bytes(zip: ZipFile, path: String): ByteArray = zip.getInputStream(zip.getEntry(path)).use { it.readBytes() }
    private fun parse(zip: ZipFile, path: String) = zip.getInputStream(zip.getEntry(path)).use {
        SecureWatchXmlParser.parse(it, SecureWatchXmlParser.DEFAULT_MAX_BYTES)
    }
    private fun expectFailure(action: () -> Unit) {
        try {
            action()
            throw AssertionError("Expected failure")
        } catch (_: IllegalArgumentException) {
            // Expected trust-boundary failure.
        }
    }
}
