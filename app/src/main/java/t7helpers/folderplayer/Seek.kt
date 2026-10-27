package t7helpers.folderplayer

import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sign

/** Jumps and scrubbing within the current file. */
object Seek {
    /** The jump buttons, each backward and forward. */
    val JUMPS_MS = listOf(30_000L, 60_000L, 5 * 60_000L, 10 * 60_000L, 30 * 60_000L, 2 * 3_600_000L)

    /** "30s", "1m", "2h". */
    fun label(ms: Long): String = when {
        ms % 3_600_000L == 0L -> "${ms / 3_600_000L}h"
        ms % 60_000L == 0L -> "${ms / 60_000L}m"
        else -> "${ms / 1000}s"
    }

    /**
     * Where a jump of [deltaMs] from [positionMs] lands: never before the start; past the
     * end it lands on the end, so the player goes on to the next file. [durationMs] is
     * negative when not known yet.
     */
    fun target(positionMs: Long, deltaMs: Long, durationMs: Long): Long {
        val target = (positionMs + deltaMs).coerceAtLeast(0)
        return if (durationMs > 0) target.coerceAtMost(durationMs) else target
    }

    /**
     * How far a horizontal drag over [fraction] of the swipe area's width (negative to the
     * left) moves: small drags move a little, for fine positioning, and a drag across the
     * whole width moves twice the file's length, so any point can be reached from anywhere.
     */
    fun scrubDelta(fraction: Float, durationMs: Long): Long {
        val span = 2.0 * durationMs.coerceAtLeast(MIN_SCRUB_SPAN_MS)
        val f = fraction.toDouble().coerceIn(-1.0, 1.0)
        return (sign(f) * abs(f).pow(3) * span).toLong()
    }

    /** Files shorter than this scrub as if they were this long. */
    private const val MIN_SCRUB_SPAN_MS = 60_000L

    /** "4:05", "1:02:03". */
    fun format(ms: Long): String {
        val seconds = ms.coerceAtLeast(0) / 1000
        val h = seconds / 3600
        val m = seconds / 60 % 60
        val s = seconds % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
    }

    /** "+1:30", "−0:05". */
    fun formatDelta(ms: Long): String = (if (ms < 0) "−" else "+") + format(abs(ms))
}
