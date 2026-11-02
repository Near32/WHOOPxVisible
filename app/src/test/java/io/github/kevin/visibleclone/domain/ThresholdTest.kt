package io.github.kevin.visibleclone.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ThresholdTest {
    private val rhr = 47
    private val maxHr = 194

    @Test fun `thresholds derive from resting and max HR by default`() {
        val c = PacingConfig()
        assertEquals(62, c.activityThreshold(rhr))
        assertEquals(120, c.exertionThreshold(rhr, maxHr))
    }

    @Test fun `explicit bpm overrides derivation`() {
        val c = PacingConfig(activityBpmOverride = 70, exertionBpmOverride = 130)
        assertEquals(70, c.activityThreshold(rhr))
        assertEquals(130, c.exertionThreshold(rhr, maxHr))
    }

    @Test fun `thresholds track resting HR across days`() {
        val c = PacingConfig()
        // RHR rises 10 bpm -> both derived thresholds rise 10 bpm (exertion by half of that).
        assertEquals(72, c.activityThreshold(57))
        assertEquals(125, c.exertionThreshold(57, maxHr))
    }

    @Test fun `ramp makes the boundary gradual rather than a cliff`() {
        val hard = PacingConfig(thresholdRampBpm = 0.0)
        val soft = PacingConfig(thresholdRampBpm = 5.0)
        val below = 58   // just under the 62 bpm activity threshold
        val at = 62
        assertEquals(0.0, PacePoints.rateAt(below, rhr, maxHr, hard), 1e-9)
        assertTrue(PacePoints.rateAt(below, rhr, maxHr, soft) > 0.0)
        assertTrue(PacePoints.rateAt(at, rhr, maxHr, soft) < PacePoints.rateAt(70, rhr, maxHr, soft))
    }

    @Test fun `ramp is monotonic around the activity threshold`() {
        val c = PacingConfig(thresholdRampBpm = 5.0)
        var prev = -1.0
        (50..80).forEach { bpm ->
            val r = PacePoints.rateAt(bpm, rhr, maxHr, c)
            assertTrue("rate should not decrease at $bpm", r >= prev - 1e-9)
            prev = r
        }
    }
}
