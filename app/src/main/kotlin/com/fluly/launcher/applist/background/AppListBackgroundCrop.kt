package com.fluly.launcher.applist.background

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Rect
import android.net.Uri
import com.fluly.launcher.watchface.core.WatchEditorSourceRect
import com.fluly.launcher.watchface.core.watchEditorCropSampleSize
import java.io.ByteArrayOutputStream
import java.io.InputStream

/**
 * 抽屉背景的「按区域取图」。
 *
 * 编辑器模块里已经有一份功能更全的 `WatchEditorImageCropper`（带 GIF / 格式嗅探），
 * 但那一个属于编辑器，HOME 不再依赖它 —— 这里只留抽屉换背景真正用到的那半条路径：
 * 优先区域解码、解不了就整图采样再裁，输出尺寸有上限，避免一张相机原图直接吃满内存。
 *
 * 采样上限的算法仍复用 `:watchface-core` 里的 [watchEditorCropSampleSize]，两边口径一致。
 */
internal object AppListBackgroundCrop {

    /** 落盘 PNG 的最长边。抽屉背景最终会被模糊 + 压暗，再大只是白耗内存。 */
    private const val OUTPUT_MAX_DIMENSION = 1024

    /** 裁出 [sourceRect]（原图坐标）并编码为 PNG 字节；失败返回 null。 */
    fun cropToPng(resolver: ContentResolver, uri: Uri, sourceRect: WatchEditorSourceRect): ByteArray? {
        if (sourceRect.width <= 0 || sourceRect.height <= 0) return null
        val bitmap = runCatching { cropRegion(resolver, uri, sourceRect) }.getOrNull() ?: return null
        return runCatching {
            ByteArrayOutputStream().use { output ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
                output.toByteArray()
            }
        }.getOrNull()
    }

    private fun cropRegion(
        resolver: ContentResolver,
        uri: Uri,
        sourceRect: WatchEditorSourceRect,
    ): Bitmap {
        val sample = watchEditorCropSampleSize(sourceRect.width, sourceRect.height, OUTPUT_MAX_DIMENSION)
        val region = Rect(sourceRect.left, sourceRect.top, sourceRect.right, sourceRect.bottom)
        val fromRegionDecoder = runCatching {
            openStream(resolver, uri).use { input ->
                val decoder = newRegionDecoder(input) ?: return@use null
                try {
                    decoder.decodeRegion(region, BitmapFactory.Options().apply { inSampleSize = sample })
                } finally {
                    decoder.recycle()
                }
            }
        }.getOrNull()
        if (fromRegionDecoder != null) return fromRegionDecoder

        val bounds = readBounds(resolver, uri)
        val fullSample = watchEditorCropSampleSize(
            bounds.outWidth,
            bounds.outHeight,
            OUTPUT_MAX_DIMENSION * maxOf(
                1,
                maxOf(bounds.outWidth, bounds.outHeight) /
                    maxOf(1, maxOf(sourceRect.width, sourceRect.height)),
            ),
        )
        val full = openStream(resolver, uri).use { input ->
            BitmapFactory.decodeStream(input, null, BitmapFactory.Options().apply { inSampleSize = fullSample })
        } ?: error("图片解码失败")
        val scaleX = full.width.toFloat() / bounds.outWidth
        val scaleY = full.height.toFloat() / bounds.outHeight
        val left = (sourceRect.left * scaleX).toInt().coerceIn(0, full.width - 1)
        val top = (sourceRect.top * scaleY).toInt().coerceIn(0, full.height - 1)
        val width = (sourceRect.width * scaleX).toInt().coerceIn(1, full.width - left)
        val height = (sourceRect.height * scaleY).toInt().coerceIn(1, full.height - top)
        val cropped = Bitmap.createBitmap(full, left, top, width, height)
        // createBitmap 可能直接返回源实例，只有真拷贝出来的才回收。
        if (cropped !== full) full.recycle()
        return cropped
    }

    private fun readBounds(resolver: ContentResolver, uri: Uri): BitmapFactory.Options {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        openStream(resolver, uri).use { input -> BitmapFactory.decodeStream(input, null, options) }
        check(options.outWidth > 0 && options.outHeight > 0) { "无法识别所选图片" }
        return options
    }

    private fun openStream(resolver: ContentResolver, uri: Uri): InputStream =
        resolver.openInputStream(uri) ?: error("无法读取所选图片")

    private fun newRegionDecoder(input: InputStream): BitmapRegionDecoder? =
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            BitmapRegionDecoder.newInstance(input)
        } else {
            @Suppress("DEPRECATION")
            BitmapRegionDecoder.newInstance(input, false)
        }
}
