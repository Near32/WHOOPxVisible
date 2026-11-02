package io.github.kevin.visibleclone.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ZoneDurationsTest {
    private val cfg = PacingConfig(zoneScale = 1.0)
    private val rhr = 60
    private val maxHr = 180 // activity 75, exertion 120

    @Test fun `empty zones earn nothing`() {
        val r = PacePoints.fromZoneDurations(List(6) { 0L }, rhr, maxHr, cfg)
        assertEquals(0.0, r.points, 1e-9)
    }

    @Test fun `null zones are ignored`() {
        val r = PacePoints.fromZoneDurations(List(6) { null }, rhr, maxHr, cfg)
        assertEquals(0.0, r.points, 1e-9)
    }

    @Test fun `zone midpoints follow heart-rate reserve, not max HR`() {
        // rhr 60, maxHr 180 -> reserve 120.
        // zone 0 = [0, 0.40) of reserve -> midpoint 0.20*120 + 60 = 84
        // zone 5 = [0.90, 1.0]      -> midpoint 0.95*120 + 60 = 174
        val low = PacePoints.fromZoneDurations(listOf(600_000L, 0, 0, 0, 0, 0), rhr, maxHr, cfg)
        assertEquals(84.0, low.peakBpm.toDouble(), 1.0)
        val high = PacePoints.fromZoneDurations(listOf(0, 0, 0, 0, 0, 600_000L), rhr, maxHr, cfg)
        assertEquals(174.0, high.peakBpm.toDouble(), 1.0)
    }

    @Test fun `zone 1 midpoint sits around half of reserve`() {
        // [0.40, 0.60) -> 0.50*120 + 60 = 120
        val z1 = PacePoints.fromZoneDurations(listOf(0, 600_000L, 0, 0, 0, 0), rhr, maxHr, cfg)
        assertEquals(120.0, z1.peakBpm.toDouble(), 1.0)
    }

    @Test fun `time in high zones costs more than the same time low`() {
        val low = PacePoints.fromZoneDurations(listOf(0, 0, 600_000L, 0, 0, 0), rhr, maxHr, cfg)
        val high = PacePoints.fromZoneDurations(listOf(0, 0, 0, 0, 600_000L, 0), rhr, maxHr, cfg)
        assertTrue(high.points > low.points)
        assertTrue(high.exertionMinutes >= 10)
    }

    @Test fun `equal time in a higher zone always costs more`() {
        val perZone = (0..5).map { z ->
            val ms = List(6) { if (it == z) 600_000L else 0L }
            PacePoints.fromZoneDurations(ms, rhr, maxHr, cfg).points
        }
        // Zones 0-1 are below 75 bpm? zone0=81 so all earn; check monotonic non-decreasing.
        for (i in 1..5) {
            assertTrue("zone $i (${perZone[i]}) should cost >= zone ${i - 1} (${perZone[i - 1]})",
                perZone[i] >= perZone[i - 1] - 1e-9)
        }
        assertTrue(perZone[5] > perZone[2])
    }
}
