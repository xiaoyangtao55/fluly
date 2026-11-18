package com.flue.launcher.ui.smartstack

import org.junit.Assert.assertEquals
import org.junit.Test

class MediaCardLayoutTest {
    @Test
    fun `marquee next copy enters before the previous copy fully leaves`() {
        val spec = mediaMarqueeSpec(
            textWidthPx = 320,
            viewportWidthPx = 120,
            gapPx = 8,
        )

        assertEquals(328, spec.cycleDistancePx)
        assertEquals(208, spec.nextCopyEntryOffsetPx)
        assertEquals(112, spec.previousCopyVisiblePxAtEntry)
    }

    @Test
    fun `marquee duration grows with measured text distance`() {
        val shortDuration = mediaMarqueeDurationMs(distancePx = 120, density = 2f, minimumMs = 0)
        val longDuration = mediaMarqueeDurationMs(distancePx = 360, density = 2f, minimumMs = 0)

        assertEquals(true, longDuration > shortDuration)
    }

    @Test
    fun `side-screen music card matches the full media card layout`() {
        val sideScreen = sideScreenMediaCardLayoutSpec()
        val full = mediaCardLayoutSpec(compact = false)

        assertEquals(full, sideScreen)
        assertEquals(156, sideScreen.cardHeightDp)
        assertEquals(132, sideScreen.artworkSizeDp)
    }

    @Test
    fun `side screen allows vertical scrolling for cards or the expanded shortcut grid`() {
        assertEquals(true, sideScreenContentAllowsVerticalScroll(showMusicCard = true, showNotificationPreview = false, expandedShortcutGrid = false))
        assertEquals(true, sideScreenContentAllowsVerticalScroll(showMusicCard = false, showNotificationPreview = true, expandedShortcutGrid = false))
        assertEquals(true, sideScreenContentAllowsVerticalScroll(showMusicCard = false, showNotificationPreview = false, expandedShortcutGrid = true))
        assertEquals(false, sideScreenContentAllowsVerticalScroll(showMusicCard = false, showNotificationPreview = false, expandedShortcutGrid = false))
    }

    @Test
    fun `scrollable side-screen cards use the shared elastic edge`() {
        assertEquals(
            true,
            sideScreenContentUsesElasticOverscroll(showMusicCard = true, showNotificationPreview = false, expandedShortcutGrid = false),
        )
        assertEquals(
            true,
            sideScreenContentUsesElasticOverscroll(showMusicCard = false, showNotificationPreview = true, expandedShortcutGrid = false),
        )
        assertEquals(
            true,
            sideScreenContentUsesElasticOverscroll(showMusicCard = true, showNotificationPreview = true, expandedShortcutGrid = false),
        )
        assertEquals(
            true,
            sideScreenContentUsesElasticOverscroll(showMusicCard = false, showNotificationPreview = false, expandedShortcutGrid = true),
        )
    }

    @Test
    fun `elastic overscroll predicate never diverges from the scrollable predicate`() {
        for (music in listOf(false, true)) {
            for (preview in listOf(false, true)) {
                assertEquals(
                    sideScreenContentAllowsVerticalScroll(
                        showMusicCard = music,
                        showNotificationPreview = preview,
                        expandedShortcutGrid = true,
                    ),
                    sideScreenContentUsesElasticOverscroll(
                        showMusicCard = music,
                        showNotificationPreview = preview,
                        expandedShortcutGrid = true,
                    ),
                )
            }
        }
    }
}
