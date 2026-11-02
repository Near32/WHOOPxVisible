package io.github.kevin.visibleclone.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ZoneCalibratorTest {

    /** Convenience: a workout peaking at [peak] that reached zones 0..[top]. */
    private fun workout(peak: Int, top: Int): Pair<Int, List<Boolean>> =
        peak to List(6) { it <= top }

    @Test fun `recovers a known synthetic boundary`() {
        // Construct data where zone 2 always appears at 140+ and never below.
        val entries = (60..200 step 5).map { peak ->
            val top = when {
                peak >= 185 -> 5
                peak >= 168 -> 4
                peak >= 150 -> 3
                peak >= 140 -> 2
                peak >= 110 -> 1
                else -> 0
            }
            workout(peak, top)
        }
        val r = ZoneCalibrator.calibrate(entries, restingHr = 60, maxHr = 200)

        assertEquals(5, r.fractions.size)
        // zone 1 boundary between 105 and 110, zone 2 between 135 and 140, etc.
        val bounds = r.bpmBounds(60, 200)
        assertTrue("zone2 lower ${bounds[2].first}", bounds[2].first in 136..141)
        assertTrue("zone5 lower ${bounds[5].first}", bounds[5].first in 181..187)
    }

    @Test fun `fractions are strictly increasing`() {
        val entries = (60..200 step 3).map { peak ->
            workout(peak, when {
                peak >= 185 -> 5
                peak >= 168 -> 4
                peak >= 150 -> 3
                peak >= 140 -> 2
                peak >= 110 -> 1
                else -> 0
            })
        }
        val f = ZoneCalibrator.calibrate(entries, 60, 200).fractions
        for (i in 1 until f.size) assertTrue("$f", f[i] > f[i - 1])
    }

    @Test fun `insufficient data falls back to defaults`() {
        val r = ZoneCalibrator.calibrate(listOf(workout(150, 3)), restingHr = 60, maxHr = 200)
        assertEquals(PacingConfig().whoopZoneHrr, r.fractions)
    }

    @Test fun `empty history falls back to defaults`() {
        val r = ZoneCalibrator.calibrate(emptyList(), 60, 200)
        assertEquals(PacingConfig().whoopZoneHrr, r.fractions)
        assertEquals(0, r.workoutsUsed)
    }

    @Test fun `resting HR at or above max HR does not crash`() {
        val r = ZoneCalibrator.calibrate(listOf(workout(150, 3)), restingHr = 200, maxHr = 200)
        assertEquals(5, r.fractions.size)
    }
}
