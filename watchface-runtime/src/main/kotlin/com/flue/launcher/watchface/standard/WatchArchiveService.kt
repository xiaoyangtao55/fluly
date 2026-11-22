package com.flue.launcher.watchface.standard

import android.content.Context
import com.flue.launcher.watchface.WatchFaceDescriptor
import org.w3c.dom.Document
import org.w3c.dom.Element
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.Locale
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult

data class WatchArchiveExportResult(val file: File, val bytesWritten: Long, val entryCount: Int)

/** Installed-package operations, with no editor session, draft, history or UI dependency. */
object WatchArchiveService {
    fun exportWatch(
        context: Context,
        descriptor: WatchFaceDescriptor,
        outputFile: File,
        rootAttributeOverrides: Map<String, String?> = emptyMap(),
        protectedExport: Boolean? = null,
    ): WatchArchiveExportResult = exportInstalled(
        installedDirectory(context, descriptor), outputFile, rootAttributeOverrides, protectedExport,
    )

    /**
     * Overrides and protection conversions affect the archive only, never the installed files.
     * A null [protectedExport] preserves the installed protection flag; explicit true/false has
     * the same transport semantics as the editor's exportWatch argument.
     * Source must be a prepared installed package (as returned by WatchArchiveInstaller).
     */
    fun exportInstalled(
        sourceDirectory: File,
        outputFile: File,
        rootAttributeOverrides: Map<String, String?> = emptyMap(),
        protectedExport: Boolean? = null,
    ): WatchArchiveExportResult {
        val source = sourceDirectory.canonicalFile
        val output = outputFile.canonicalFile
        require(!isWithin(output, source)) { "导出文件不能位于表盘安装目录内" }
        return WatchPackageTransactionMutex.withTarget(source) {
            val files = packageFiles(source)
            val watchXml = findWatchXml(files)
            val document = SecureWatchXmlParser.parse(watchXml)
            val protectedExport = protectedExport ?: document.documentElement.attributes.let { attributes ->
                (0 until attributes.length).any { index ->
                    val attribute = attributes.item(index)
                    attribute.nodeName.equals("protection", true) &&
                        attribute.nodeValue.trim().lowercase(Locale.ROOT) in setOf("y", "yes", "true", "1")
                }
            }
            require(document.documentElement.tagName.equals("Watch", true)) { "watch.xml root must be <Watch>" }
            val relativeXml = relative(source, watchXml)
            val parent = relativeXml.substringBeforeLast('/', "")
            val pxml = if (parent.isEmpty()) "watch.pxml" else "$parent/watch.pxml"
            val script = File(source, "scripts/script.txt").takeIf(File::isFile)?.let {
                require(it.length() <= SecureWatchXmlParser.DEFAULT_MAX_BYTES) { "脚本过大" }
                it.readText(Charsets.UTF_8)
            }
            fun exportDocument(): Document = document.implementation.createDocument(null, null, null).also { copy ->
                copy.appendChild(copy.importNode(document.documentElement, true))
                rootAttributeOverrides.forEach { (name, value) ->
                    require(name.isNotBlank() && !name.contains(Regex("\\s"))) { "XML 属性名非法" }
                    if (value == null) copy.documentElement.removeAttribute(name)
                    else copy.documentElement.setAttribute(name, value)
                }
                setProtection(copy.documentElement, protectedExport)
            }
            val full = exportDocument()
            val root = full.documentElement
            if (!rootAttributeOverrides.containsKey("features")) {
                val scanned = serialize(full).toString(Charsets.UTF_8) + "\n" + script.orEmpty()
                val features = ArchiveFeatures.detect(scanned, root.getAttribute("build_source").equals("studio", true))
                if (features.isEmpty()) root.removeAttribute("features") else root.setAttribute("features", features)
            }
            if (root.getAttribute("ucolor_default").isBlank()) root.setAttribute("ucolor_default", "ffffff")
            val fullBytes = normalizePaths(serialize(full), protectedExport)
            val shellBytes = if (protectedExport) {
                val shell = exportDocument()
                while (shell.documentElement.firstChild != null) {
                    shell.documentElement.removeChild(shell.documentElement.firstChild)
                }
                serialize(shell)
            } else fullBytes
            val entries = linkedMapOf<String, ArchiveEntry>()
            entries[relativeXml] = ArchiveEntry(bytes = shellBytes)
            if (protectedExport) entries[pxml] = ArchiveEntry(bytes = WatchmakerProtection.encodeXml(fullBytes))
            val haystack = (fullBytes.toString(Charsets.UTF_8) + "\n" + script.orEmpty()).lowercase(Locale.ROOT)
            files.filter { it != watchXml && !it.name.equals("watch.pxml", true) }
                .sortedBy { if (it.extension.lowercase(Locale.ROOT) in PROTECTED_EXTENSIONS) 1 else 0 }
                .forEach { file ->
                    val path = relative(source, file)
                    if (isPrunable(path) && !haystack.contains(file.nameWithoutExtension.lowercase(Locale.ROOT))) {
                        return@forEach
                    }
                    val image = isImage(path)
                    val target = if (image) imagePath(path, protectedExport) else path
                    if (target != relativeXml && target != pxml) {
                        entries.putIfAbsent(target, ArchiveEntry(file = file, image = image))
                    }
                }
            ensurePreview(entries, document)
            output.parentFile?.let { require(it.isDirectory || it.mkdirs()) { "无法创建导出目录" } }
            val temporary = File(output.parentFile, ".${output.name}.${UUID.randomUUID()}.tmp")
            try {
                ZipOutputStream(BufferedOutputStream(FileOutputStream(temporary))).use { zip ->
                    entries.forEach { (path, entry) ->
                        zip.putNextEntry(ZipEntry(path))
                        if (entry.bytes != null) zip.write(entry.bytes)
                        else {
                            val file = requireNotNull(entry.file)
                            if (entry.image) {
                                val bytes = file.readBytes()
                                zip.write(if (protectedExport) {
                                    WatchmakerProtection.prepareProtectedAssetBytes(bytes, file.extension)
                                } else WatchmakerProtection.preparePlainAssetBytes(bytes, file.extension))
                            } else file.inputStream().use { it.copyTo(zip) }
                        }
                        zip.closeEntry()
                    }
                }
                WatchArchiveValidator().inspect(temporary)
                move(temporary, output)
                WatchArchiveExportResult(output, output.length(), entries.size)
            } finally {
                temporary.delete()
            }
        }
    }

    fun setInstalledShape(context: Context, descriptor: WatchFaceDescriptor, roundShape: Boolean) =
        setInstalledShape(installedDirectory(context, descriptor), roundShape)

    /** Updates the prepared installed XML only; stale transport pxml is deliberately not authoritative. */
    fun setInstalledShape(sourceDirectory: File, roundShape: Boolean) {
        val source = sourceDirectory.canonicalFile
        WatchPackageTransactionMutex.withTarget(source) {
            // Check links before the installer's legacy repair walk can follow any of them.
            packageFiles(source)
            val watchXml = WatchArchiveInstaller().repairInstalledPackage(source)
            val document = SecureWatchXmlParser.parse(watchXml)
            document.documentElement.setAttribute("shape", if (roundShape) "R" else "S")
            val temporary = File(watchXml.parentFile, ".${watchXml.name}.${UUID.randomUUID()}.tmp")
            try {
                FileOutputStream(temporary).use { output ->
                    output.write(serialize(document))
                    output.fd.sync()
                }
                move(temporary, watchXml)
            } finally {
                temporary.delete()
            }
        }
    }

    private fun installedDirectory(context: Context, descriptor: WatchFaceDescriptor): File {
        require(descriptor.isWatchmaker) { "只有标准 .watch 表盘支持归档操作" }
        val source = requireNotNull(descriptor.sourceDirPath) { "表盘没有源目录" }.let(::File).canonicalFile
        require(source.parentFile == WatchmakerWatchFaceStorage.rootDirectory(context).canonicalFile) {
            "表盘源目录不是有效的 Flue 标准表盘安装目录"
        }
        return source
    }

    private data class ArchiveEntry(val file: File? = null, val bytes: ByteArray? = null, val image: Boolean = false)
    private val PROTECTED_EXTENSIONS = setOf("ppng", "pjpg", "pjpeg")

    private fun packageFiles(source: File): List<File> {
        require(source.isDirectory) { "表盘源目录不存在" }
        val files = mutableListOf<File>()
        var total = 0L
        fun collect(directory: File, depth: Int) {
            require(depth <= 128) { "表盘目录嵌套过深" }
            directory.listFiles().orEmpty().forEach { child ->
                require(!Files.isSymbolicLink(child.toPath())) { "表盘包含不允许的符号链接" }
                val path = relative(source, child)
                if (WatchInstallMetadata.isReservedArchivePath(path)) return@forEach
                require(path.isNotBlank() && !path.contains('\\') && !path.contains('\u0000')) { "非法资源路径" }
                require(!path.startsWith('/') && !path.matches(Regex("^[A-Za-z]:.*"))) { "非法资源路径" }
                if (child.isDirectory) collect(child, depth + 1)
                else if (child.isFile) {
                    require(child.length() <= 450L * 1024 * 1024) { "表盘文件过大" }
                    total += child.length()
                    require(total <= 500L * 1024 * 1024) { "表盘资源总大小超限" }
                    files += child
                    require(files.size <= 20_000) { "表盘文件过多" }
                }
            }
        }
        collect(source, 0)
        return files
    }

    private fun findWatchXml(files: List<File>): File = files.filter { it.name.equals("watch.xml", true) }
        .also { require(it.size == 1) { "表盘必须包含唯一的 watch.xml" } }.single()

    private fun relative(source: File, file: File): String {
        require(isWithin(file.canonicalFile, source)) { "资源路径越界" }
        return file.canonicalFile.relativeTo(source).invariantSeparatorsPath
    }

    private fun isWithin(child: File, root: File): Boolean =
        child.path == root.path || child.path.startsWith(root.path + File.separator)

    private fun isPrunable(path: String): Boolean =
        path.startsWith("images/", true) || path.startsWith("videos/", true)

    private fun isImage(path: String): Boolean = path.startsWith("images/", true) &&
        path.substringAfterLast('.', "").lowercase(Locale.ROOT) in setOf("png", "jpg", "jpeg", "ppng", "pjpg", "pjpeg")

    private fun imagePath(path: String, protected: Boolean): String {
        val extension = path.substringAfterLast('.').lowercase(Locale.ROOT)
        val target = if (protected) when (extension) {
            "jpeg", "jpg" -> "pjpg"
            "png" -> "ppng"
            else -> extension
        } else when (extension) {
            "pjpeg" -> "jpeg"
            "pjpg" -> "jpg"
            "ppng" -> "png"
            else -> extension
        }
        return if (extension == target) path else path.substringBeforeLast('.') + "." + target
    }

    private fun normalizePaths(bytes: ByteArray, protected: Boolean): ByteArray {
        val xml = bytes.toString(Charsets.UTF_8)
        return (if (protected) xml.replace(".jpeg", ".pjpg", true).replace(".png", ".ppng", true)
            .replace(".jpg", ".pjpg", true)
        else xml.replace(".ppng", ".png", true).replace(".pjpeg", ".jpeg", true)
            .replace(".pjpg", ".jpg", true)).toByteArray(Charsets.UTF_8)
    }

    private fun setProtection(root: Element, enabled: Boolean) {
        (0 until root.attributes.length).map { root.attributes.item(it).nodeName }
            .filter { it.equals("protection", true) }.forEach(root::removeAttribute)
        if (enabled) root.setAttribute("protection", "y")
    }

    private fun ensurePreview(entries: MutableMap<String, ArchiveEntry>, document: Document) {
        if (entries.keys.any { it.equals("preview.jpg", true) }) return
        entries.entries.firstOrNull { it.key.equals("preview_dim.jpg", true) }?.let {
            entries["preview.jpg"] = it.value
            return
        }
        val bytes = runCatching {
            val background = normalizeWatchColorLiteral(document.documentElement.getAttribute("bg_color"))
                ?.let { android.graphics.Color.parseColor(it) } ?: android.graphics.Color.BLACK
            val bitmap = android.graphics.Bitmap.createBitmap(512, 512, android.graphics.Bitmap.Config.ARGB_8888)
            try {
                bitmap.eraseColor(background)
                ByteArrayOutputStream().use { out ->
                    bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, out)
                    out.toByteArray()
                }
            } finally { bitmap.recycle() }
        }.getOrNull()?.takeIf { it.isNotEmpty() }
        if (bytes != null) entries["preview.jpg"] = ArchiveEntry(bytes = bytes)
    }

    private fun serialize(document: Document): ByteArray {
        val factory = TransformerFactory.newInstance()
        runCatching { factory.setAttribute("http://javax.xml.XMLConstants/property/accessExternalDTD", "") }
        runCatching { factory.setAttribute("http://javax.xml.XMLConstants/property/accessExternalStylesheet", "") }
        val transformer = factory.newTransformer().apply {
            setOutputProperty(OutputKeys.ENCODING, "UTF-8")
            setOutputProperty(OutputKeys.INDENT, "no")
            setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no")
        }
        return ByteArrayOutputStream().use {
            transformer.transform(DOMSource(document), StreamResult(it))
            it.toByteArray()
        }
    }

    private fun move(source: File, target: File) {
        try {
            Files.move(source.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }
}

/** Same export-time rules as editor WatchFeatureDetector; kept private to avoid a runtime -> editor edge. */
private object ArchiveFeatures {
    private val labels = mapOf(
        "dt" to "Date", "12" to "12h Time", "24" to "24h Time", "u" to "Themeable",
        "an" to "Analog", "bt" to "Battery", "pbt" to "Phone Battery", "w" to "Weather",
        "mn" to "Moon Phase", "st" to "Steps", "hr" to "Heart Rate", "sw" to "Stopwatch",
        "zne" to "Time Zone", "gyr" to "Gyroscope", "cps" to "Compass", "cpn" to "Complications",
        "sfx" to "Sound", "gif" to "GIF", "ani" to "Animation", "tap" to "Tap",
        "cd" to "Countdown", "cal" to "Calendar",
    )
    private val tags = mapOf(
        "dt" to "dd ddz ddy ddiy ddim ddw ddw0 ddw1 ddw2 ddww dn dnn dnnn dnnnn dy dyy dw dwm",
        "12" to "dh dhz dhtt dhto dh11 dh11z dh11tt dh11to dhutc12 dhutc12z da",
        "24" to "dh23 dh23z dh23i dh23tt dh23to dh24 dh24z dh24tt dh24to dhutc24 dhutc24z d24h",
        "an" to "drh drh0 drh24 drm drms drs drss",
        "u" to "ucolor ucolor2 ucolor3 ucolor_b",
        "pbt" to "pbc pbl pblp pbr pbtc pbtcd pbtf pbtfd",
        "bt" to "bc bl blp br btc btcd btf btfd bv bi",
        "hr" to "shr shr_1 shrp sthr",
        "st" to "ssc sscp stsc sdst sdstp sdstu stdst scal scalp stcal hfc ham htam hae htae has htas",
        "cps" to "scb scbb scr sct sctd sctdb sctdbb",
        "gyr" to "sax say saz sgx sgy sgz sprs",
        "mn" to "wmp",
        "zne" to "aalt alat alatd alatdd alon alond alondd aw3w aw3w1 aw3w2 aw3w3",
    ).flatMap { (feature, names) -> names.split(' ').map { it to feature } }.toMap()

    fun detect(xml: String, studio: Boolean): String {
        val found = linkedSetOf<String>()
        Regex("""\{([^\}]*)\}""").findAll(xml).forEach { brace ->
            Regex("[A-Za-z_][A-Za-z0-9_]*").findAll(brace.groupValues[1]).forEach { identifier ->
                val tag = identifier.value.lowercase(Locale.ROOT)
                val feature = tags[tag] ?: when {
                    Regex("""^ddw(w|[12])?_\d$""").matches(tag) -> "dt"
                    Regex("^w[a-z0-9_]*$").matches(tag) -> "w"
                    Regex("^t?sw[a-z0-9_]*$").matches(tag) -> "sw"
                    Regex("^tz\\d[a-z0-9_]*$").matches(tag) -> "zne"
                    Regex("^cd_[a-z0-9_]*$").matches(tag) -> "cd"
                    Regex("^c_[a-z0-9_]*$").matches(tag) -> "ani"
                    tag == "cex" || Regex("^c\\d+[a-z]*$").matches(tag) -> "cal"
                    else -> null
                }
                if (feature != null) found += feature
            }
        }
        val lower = xml.lowercase(Locale.ROOT)
        if ("tweens." in lower || "type=\"video\"" in lower) found += "ani"
        if ("image_gif" in lower || "type=\"gif\"" in lower) found += "gif"
        if ("tap_action=" in lower) found += "tap"
        if ("<sound" in lower) found += "sfx"
        if ("type=\"complication\"" in lower || "linked_component_id=" in lower) found += "cpn"
        if (Regex("""gyro="(?!0*"|0*\.0*")[^"]+""").containsMatchIn(lower)) found += "gyr"
        if ("24" in found) found.remove("12")
        return found.map { if (studio) it else labels.getValue(it) }.sorted().joinToString(",")
    }
}
