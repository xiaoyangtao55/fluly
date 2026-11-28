package com.fluly.launcher.watchface.standard

import android.graphics.Bitmap

/**
 * API 27-30 的「预烘焙模糊」：降采样 + 三轮可分离 box blur + 双线性放大。
 *
 * 运行时（陀螺仪背衬）和 HOME 的应用列表背景都要用，原来放在 app 的
 * `ui/anim/LegacyWatchFaceBackdrop.kt`，拆模块时随渲染需求一起挪过来。
 * 常量是实测配方，两边必须共用同一份，避免「桌面模糊」和「表盘模糊」强度不一致。
 */
object LegacyPrebuiltBlur {
    const val DOWNSAMPLE_DIVISOR = 12
    const val PASSES = 3
    const val RADIUS = 6

    /**
     * Reuses the API 27-30 prebuilt blur for other renderers that need a stable
     * backdrop (for example the opt-in gyro face). The returned bitmap is owned by
     * the caller; [source] remains untouched.
     *
     * @param radius 降采样图上的盒模糊半径。默认值是表盘背衬那套实测配方；应用列表背景要
     *   **可调强度**（老师定的「模糊程度也可以修改」），所以把它开成参数 —— 否则那条滑块在
     *   Android 12 以下只有开与关两档。
     */
    fun create(
        source: Bitmap,
        radius: Int = RADIUS,
    ): Bitmap = strongBlur(source, radius)

    private fun strongBlur(
        source: Bitmap,
        radius: Int = RADIUS,
    ): Bitmap {
        val effectiveRadius = radius.coerceIn(1, 32)
        val smallWidth = (source.width / DOWNSAMPLE_DIVISOR).coerceAtLeast(28)
        val smallHeight = (source.height / DOWNSAMPLE_DIVISOR).coerceAtLeast(28)
        val small = Bitmap.createScaledBitmap(source, smallWidth, smallHeight, true)
        val pixels = IntArray(smallWidth * smallHeight)
        val scratch = IntArray(pixels.size)
        small.getPixels(pixels, 0, smallWidth, 0, 0, smallWidth, smallHeight)
        // Previous policy used four radius-7 passes. Three radius-6 passes lower
        // the effective blur spread by roughly one fifth while retaining enough
        // diffusion to hide mirrored seams on API 27-30.
        repeat(PASSES) {
            boxBlurHorizontal(
                pixels,
                scratch,
                smallWidth,
                smallHeight,
                radius = effectiveRadius,
            )
            boxBlurVertical(
                scratch,
                pixels,
                smallWidth,
                smallHeight,
                radius = effectiveRadius,
            )
        }
        small.setPixels(pixels, 0, smallWidth, 0, 0, smallWidth, smallHeight)
        val enlarged = Bitmap.createScaledBitmap(small, source.width, source.height, true)
        if (small !== enlarged && !small.isRecycled) small.recycle()
        return enlarged
    }

    private fun boxBlurHorizontal(
        input: IntArray,
        output: IntArray,
        width: Int,
        height: Int,
        radius: Int,
    ) {
        val diameter = radius * 2 + 1
        for (y in 0 until height) {
            val row = y * width
            var red = 0
            var green = 0
            var blue = 0
            for (offset in -radius..radius) {
                val color = input[row + offset.coerceIn(0, width - 1)]
                red += color shr 16 and 0xFF
                green += color shr 8 and 0xFF
                blue += color and 0xFF
            }
            for (x in 0 until width) {
                output[row + x] = android.graphics.Color.rgb(red / diameter, green / diameter, blue / diameter)
                val outgoing = input[row + (x - radius).coerceIn(0, width - 1)]
                val incoming = input[row + (x + radius + 1).coerceIn(0, width - 1)]
                red += (incoming shr 16 and 0xFF) - (outgoing shr 16 and 0xFF)
                green += (incoming shr 8 and 0xFF) - (outgoing shr 8 and 0xFF)
                blue += (incoming and 0xFF) - (outgoing and 0xFF)
            }
        }
    }

    private fun boxBlurVertical(
        input: IntArray,
        output: IntArray,
        width: Int,
        height: Int,
        radius: Int,
    ) {
        val diameter = radius * 2 + 1
        for (x in 0 until width) {
            var red = 0
            var green = 0
            var blue = 0
            for (offset in -radius..radius) {
                val color = input[offset.coerceIn(0, height - 1) * width + x]
                red += color shr 16 and 0xFF
                green += color shr 8 and 0xFF
                blue += color and 0xFF
            }
            for (y in 0 until height) {
                output[y * width + x] = android.graphics.Color.rgb(red / diameter, green / diameter, blue / diameter)
                val outgoing = input[(y - radius).coerceIn(0, height - 1) * width + x]
                val incoming = input[(y + radius + 1).coerceIn(0, height - 1) * width + x]
                red += (incoming shr 16 and 0xFF) - (outgoing shr 16 and 0xFF)
                green += (incoming shr 8 and 0xFF) - (outgoing shr 8 and 0xFF)
                blue += (incoming and 0xFF) - (outgoing and 0xFF)
            }
        }
    }
}
