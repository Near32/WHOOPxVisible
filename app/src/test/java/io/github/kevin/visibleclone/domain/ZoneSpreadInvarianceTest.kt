package io.github.kevin.visibleclone.domain

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Zone-duration scoring is a sum of rate(zone) x minutes(zone). Redistributing that time across the
 * activity window permutes the same multiset of (rate, minutes) pairs, so the day total cannot
 * change - only when the points appear in the pacing-day curve.
 *
 * This test exists so "spread zone time uniformly" is not proposed again as a scoring refinement.
 */
class ZoneSpreadInvarianceTest {
    private val cfg = PacingConfig()
    private val rhr = 47
    private val maxHr = 194
    private val minute = 60_000L

    private fun zoneMinutes(vararg pairs: Pair<Int, Long>) =
        List(6) { z -> pairs.firstOrNull { it.first == z }?.second?.times(minute) ?: 0L }

    /** Points contributed by each zone in isolation. */
    private fun perZone(zone: Int, minutes: Long): Double =
        PacePoints.fromZoneDurations(zoneMinutes(zone to minutes), rhr, maxHr, cfg).points

    @Test fun `total is the sum of per-zone contributions`() {
        val combined = PacePoints.fromZoneDurations(
            zoneMinutes(0 to 120L, 1 to 30L), rhr, maxHr, cfg,
        ).points
        val parts = perZone(0, 120L) + perZone(1, 30L)
        assertEquals(parts, combined, 1e-9)
    }

    @Test fun `reordering the same zone minutes gives the identical total`() {
        val a = PacePoints.fromZoneDurations(zoneMinutes(0 to 60L, 3 to 10L), rhr, maxHr, cfg).points
        val b = PacePoints.fromZoneDurations(zoneMinutes(3 to 10L, 0 to 60L), rhr, maxHr, cfg).points
        assertEquals(a, b, 1e-9)
    }

    @Test fun `splitting a zone into pieces does not change the total`() {
        val whole = PacePoints.fromZoneDurations(zoneMinutes(2 to 20L), rhr, maxHr, cfg).points
        val halves = perZone(2, 10L) + perZone(2, 10L)
        assertEquals(whole, halves, 1e-9)
    }
}
