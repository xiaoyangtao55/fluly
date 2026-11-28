package com.fluly.launcher.watchface

/**
 * 表盘标识与类型：HOME（运行时/选择器）与 Studio（编辑器）共用的最小模型。
 * 工厂 [WatchFaceDescriptors] 依赖 App 的字符串资源，留在 app 侧。
 */
const val BUILT_IN_WATCHFACE_ID = "builtin"
const val BUILT_IN_PHOTO_WATCHFACE_ID = "builtin_photo"
const val BUILT_IN_VIDEO_WATCHFACE_ID = "builtin_video"

enum class WatchFaceType {
    BUILTIN,
    JBWATCH,
    /** Native Watchmaker 1.0/Studio 2.0 archive. */
    WATCHMAKER,
}

/** Fluly-owned metadata for built-in, standard .watch, and JB watch faces. */
data class WatchFaceDescriptor(
    val id: String,
    val type: WatchFaceType,
    val displayName: String,
    val summary: String = "",
    val previewFilePath: String? = null,
    val versionCode: Long = 0,
    val author: String? = null,
    val sourceDirPath: String? = null,
    val supportsSettings: Boolean = false,
) {
    val isBuiltin: Boolean get() = type == WatchFaceType.BUILTIN
    val isJbWatch: Boolean get() = type == WatchFaceType.JBWATCH
    val isWatchmaker: Boolean get() = type == WatchFaceType.WATCHMAKER
    val stableKey: String get() = id
}
