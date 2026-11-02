package io.github.kevin.visibleclone.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Zone 0 spans rest up to 40% of reserve, so its midpoint overstates low-effort time. When WHOOP
 * gives the workout's average HR we infer zone 0's real intensity from it.
 */
class ZoneZeroAnchorTest {
    // Ramp off here: these tests assert crisp classification, not the soft boundary.
    private val cfg = PacingConfig(thresholdRampBpm = 0.0)
    private val rhr = 47
    private val maxHr = 194          // reserve 147; zone 0 = 47..106 bpm, midpoint 76
    private val oneHour = 3_600_000L

    @Test fun `restful session near RHR is not charged as activity`() {
        // Average 55 bpm is below the 62 bpm activity threshold.
        val r = PacePoints.fromZoneDurations(
            listOf(oneHour, 0, 0, 0, 0, 0), rhr, maxHr, cfg, avgHeartRate = 55,
        )
        assertEquals(0.0, r.points, 1e-9)
        assertEquals(60, r.restingMinutes)
    }

    @Test fun `anchoring decides whether a session is activity or rest`() {
        // The midpoint (76 bpm) is always above the 62 bpm threshold, so a restful session is
        // charged as activity. With the real average it correctly costs nothing.
        val restful = PacePoints.fromZoneDurations(
            listOf(2 * oneHour, 0, 0, 0, 0, 0), rhr, maxHr, cfg, avgHeartRate = 60,
        )
        val assumed = PacePoints.fromZoneDurations(
            listOf(2 * oneHour, 0, 0, 0, 0, 0), rhr, maxHr, cfg, avgHeartRate = null,
        )
        assertEquals(0.0, restful.points, 1e-9)
        assertTrue("midpoint assumption should charge, got ${assumed.points}", assumed.points > 0.0)
    }

    @Test fun `within the activity band the exact average does not change the rate`() {
        // 66 and 76 both lie between activity and exertion, so both cost c1 per minute.
        val a = PacePoints.fromZoneDurations(listOf(oneHour, 0, 0, 0, 0, 0), rhr, maxHr, cfg, 66)
        val b = PacePoints.fromZoneDurations(listOf(oneHour, 0, 0, 0, 0, 0), rhr, maxHr, cfg, null)
        assertEquals(b.points, a.points, 1e-9)
    }

    @Test fun `implausible averages fall back to the midpoint`() {
        // Average 190 bpm cannot arise from a workout that spent an hour in zone 0; ignore it.
        val weird = PacePoints.fromZoneDurations(
            listOf(oneHour, 0, 0, 0, 0, 0), rhr, maxHr, cfg, avgHeartRate = 190,
        )
        val plain = PacePoints.fromZoneDurations(
            listOf(oneHour, 0, 0, 0, 0, 0), rhr, maxHr, cfg, avgHeartRate = null,
        )
        assertEquals(plain.points, weird.points, 1e-9)
    }

    @Test fun `anchoring is skipped when zone 0 is a small share`() {
        // 1 min zone 0 alongside 30 min zone 2: dividing by 1 min is numerically unstable and the
        // measured data confirms nonsense results, so fall back to the midpoint.
        val zones = listOf(oneHour / 60, 0L, 30 * oneHour / 60, 0L, 0L, 0L)
        val anchored = PacePoints.fromZoneDurations(zones, rhr, maxHr, cfg, avgHeartRate = 150)
        val plain = PacePoints.fromZoneDurations(zones, rhr, maxHr, cfg, avgHeartRate = null)
        assertEquals(plain.points, anchored.points, 1e-9)
    }

    @Test fun `higher zones still use their midpoints`() {
        // With no zone-0 time there is nothing to anchor, so behaviour is unchanged.
        val r = PacePoints.fromZoneDurations(
            listOf(0, 0, oneHour, 0, 0, 0), rhr, maxHr, cfg, avgHeartRate = 140,
        )
        val plain = PacePoints.fromZoneDurations(
            listOf(0, 0, oneHour, 0, 0, 0), rhr, maxHr, cfg, avgHeartRate = null,
        )
        assertEquals(plain.points, r.points, 1e-9)
    }
}
