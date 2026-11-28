package com.fluly.launcher.lockscreen

import kotlin.math.abs

/** One-finger, predominantly upward drag; evaluated before children receive ACTION_UP. */
internal fun isLockScreenFaceVisible(resumed: Boolean, interactive: Boolean): Boolean =
    resumed && interactive

internal fun isLockScreenExitSwipe(dx: Float, dy: Float, thresholdPx: Float): Boolean =
    dy <= -thresholdPx && abs(dy) > abs(dx)
