package t7helpers.folderplayer

import org.junit.Assert.assertEquals
import org.junit.Test

class SeekTest {
    private val hour = 3_600_000L

    @Test
    fun labels() {
        assertEquals(listOf("30s", "1m", "5m", "10m", "30m", "2h"), Seek.JUMPS_MS.map(Seek::label))
    }

    @Test
    fun jumpsStayWithinTheFile() {
        assertEquals(90_000L, Seek.target(60_000, 30_000, hour))
        assertEquals(0L, Seek.target(20_000, -30_000, hour))
        assertEquals(hour, Seek.target(50 * 60_000L, 2 * hour, hour))
        // Duration not known yet: only the start limits.
        assertEquals(2 * hour + 1000, Seek.target(1000, 2 * hour, -1))
    }

    @Test
    fun scrubMovesTwoMinutesPerWidth() {
        assertEquals(0L, Seek.scrubDelta(0f))
        assertEquals(120_000L, Seek.scrubDelta(1f))
        assertEquals(-120_000L, Seek.scrubDelta(-1f))
        assertEquals(-120_000L, Seek.scrubDelta(-3f))
        assertEquals(12_000L, Seek.scrubDelta(0.1f))
        assertEquals(-60_000L, Seek.scrubDelta(-0.5f))
        // From near the end of a file, a drag stops at its end.
        assertEquals(hour, Seek.target(hour - 30_000, Seek.scrubDelta(1f), hour))
    }

    @Test
    fun formatting() {
        assertEquals("0:00", Seek.format(0))
        assertEquals("0:00", Seek.format(-5))
        assertEquals("4:05", Seek.format(245_999))
        assertEquals("1:02:03", Seek.format(3_723_000))
        assertEquals("+1:30", Seek.formatDelta(90_000))
        assertEquals("−0:05", Seek.formatDelta(-5_000))
    }
}
