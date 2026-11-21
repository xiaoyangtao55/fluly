package com.flue.launcher.ui.style

import java.io.File

/** Source policies cover all seven modules, independent of Gradle's working directory. */
internal object RepositorySourceScan {
    private val modules = listOf(
        "app", "studio", "ui-kit", "watchface-editor", "agent", "watchface-core", "watchface-runtime",
    )

    private val repositoryRoot: File by lazy {
        generateSequence(File(".").canonicalFile) { it.parentFile }
            .firstOrNull { File(it, "settings.gradle.kts").isFile && File(it, "ui-kit").isDirectory }
            ?: error("找不到仓库根目录 —— 工作目录是 ${File(".").absolutePath}")
    }

    fun sourceRoots(): List<File> = modules.flatMap { module ->
        val main = File(repositoryRoot, "$module/src/main")
        check(main.isDirectory) { "找不到模块源码目录：$main" }
        listOf("kotlin", "java").map { File(main, it) }.filter(File::isDirectory)
    }

    fun kotlinSources(): Sequence<File> = sourceRoots().asSequence()
        .flatMap { root -> root.walkTopDown().onEnter { it.name != "build" } }
        .filter { it.isFile && it.extension == "kt" }

    fun relative(file: File): String = file.relativeTo(repositoryRoot).invariantSeparatorsPath

    fun sourceRelative(file: File): String = relative(file)
        .substringAfter("/src/main/").substringAfter('/')
}
