package com.fluly.launcher.ui.common

import com.fluly.launcher.R
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Keeps the rounded fallback icon set explicit. These IDs are referenced by
 * Compose painters in the store, control center and notification surfaces;
 * a missing resource should fail the unit suite before a release build.
 */
class RoundedIconResourceTest {
    @Test
    fun roundedFallbackIconsAreGenerated() {
        val ids = listOf(
            R.drawable.ic_fluly_check_round,
            R.drawable.ic_fluly_download_round,
            R.drawable.ic_fluly_media_next_round,
            R.drawable.ic_fluly_media_pause_round,
            R.drawable.ic_fluly_media_play_round,
            R.drawable.ic_fluly_media_previous_round,
            R.drawable.ic_fluly_music_round,
            R.drawable.ic_fluly_notification_round,
            R.drawable.ic_fluly_refresh_round,
        )
        ids.forEach { id -> assertTrue("drawable resource ID must be non-zero", id != 0) }
    }
}
