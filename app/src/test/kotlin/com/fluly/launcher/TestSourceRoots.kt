package com.fluly.launcher

import java.io.File

/**
 * 契约测试要按路径读源码做断言，而源码在阶段二被拆到了多个模块。
 *
 * 这些测试按包内相对路径书写，这里在各模块源码根下查找；`ui/style`、
 * `ui/theme`、`motion` 位于 :ui-kit，其余分布在 :app 等模块。
 */
object TestSourceRoots {

    private val roots: List<File> = listOf(
        "src/main/kotlin",
        "app/src/main/kotlin",
        "../app/src/main/kotlin",
        "ui-kit/src/main/kotlin",
        "../ui-kit/src/main/kotlin",
        "watchface-core/src/main/kotlin",
        "../watchface-core/src/main/kotlin",
        "watchface-runtime/src/main/kotlin",
        "../watchface-runtime/src/main/kotlin",
        "watchface-editor/src/main/kotlin",
        "../watchface-editor/src/main/kotlin",
        "watchface-store-client/src/main/kotlin",
        "../watchface-store-client/src/main/kotlin",
        "watchface-store/src/main/kotlin",
        "../watchface-store/src/main/kotlin",
        "agent/src/main/kotlin",
        "../agent/src/main/kotlin",
        // Studio 是独立应用模块，源码在 src/main/java 而不是 kotlin。
        "studio/src/main/java",
        "../studio/src/main/java",
    ).map(::File).filter(File::isDirectory)

    /** 读源码并把行尾统一成 `\n`：Windows 的 `core.autocrlf` 检出会让结构断言假红。 */
    fun read(relative: String): String = readOrNull(relative)
        ?.replace("\r\n", "\n")
        ?: error("找不到源码 $relative（已查源码根：${roots.joinToString { it.path }}）")

    /** 找不到时返回 null：有些测试对「文件是否已删除」本身做断言。 */
    fun readOrNull(relative: String): String? {
        val candidates = buildList {
            add(relative)
            // Agent 设置三页随 :agent 一起搬进了 Studio，包名也从 launcher 换成 studio。
            if (relative.startsWith("com/fluly/launcher/ui/settings/agent/")) {
                add(
                    relative.replaceFirst(
                        "com/fluly/launcher/ui/settings/agent/",
                        "com/fluly/studio/ui/settings/agent/",
                    ),
                )
            }
            // 有的调用点只写包内相对路径（如 "ui/theme/Theme.kt"），补上包前缀。
            if (!relative.startsWith("com/")) {
                add("com/fluly/launcher/$relative")
            }
        }
        roots.forEach { root ->
            candidates.forEach { candidate ->
                val file = File(root, candidate)
                if (file.isFile) return file.readText()
            }
        }
        return null
    }
}
