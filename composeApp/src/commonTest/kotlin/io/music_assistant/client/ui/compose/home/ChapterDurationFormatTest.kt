package io.music_assistant.client.ui.compose.home

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * [formatChapterDuration] chooses the coarsest unit that still conveys the
 * length, and hides sub-second durations entirely.
 */
class ChapterDurationFormatTest {
    @Test
    fun belowOneSecondIsNull() {
        assertNull(formatChapterDuration(0.0))
        assertNull(formatChapterDuration(0.9))
    }

    @Test
    fun subMinuteShownInSeconds() {
        assertEquals("45s", formatChapterDuration(45.0))
    }

    @Test
    fun underAnHourShownInWholeMinutes() {
        assertEquals("5m", formatChapterDuration(5 * 60.0))
        // 59m59s is still under an hour, so it stays in minutes.
        assertEquals("59m", formatChapterDuration(59 * 60.0 + 59))
    }

    @Test
    fun overAnHourShowsHoursAndMinutes() {
        assertEquals("1h 15m", formatChapterDuration(75 * 60.0))
    }

    @Test
    fun wholeHoursOmitZeroMinutes() {
        assertEquals("2h", formatChapterDuration(2 * 3600.0))
    }
}
