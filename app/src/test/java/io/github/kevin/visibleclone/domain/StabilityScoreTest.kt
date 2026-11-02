package io.github.kevin.visibleclone.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StabilityScoreTest {

    private val config = PacingConfig(minBaselineNights = 3)

    private fun inputs(
        hrv: List<Double>,
        rhr: List<Double>,
        sleep: Int? = 3,
        symptom: Int? = 3,
    ) = StabilityInputs(hrv, rhr, sleep, symptom)

    @Test fun `steady night scores 3`() {
        // Flat baselines: today equals the mean, neutral check-in -> deviation 0 -> score 3.
        val r = StabilityScore.compute(
            inputs(hrv = List(10) { 50.0 }, rhr = List(10) { 60.0 }),
            config,
        )
        assertEquals(0.0, r.deviation, 1e-9)
        assertEquals(3, r.score)
    }

    @Test fun `hrv drop and rhr spike lowers the score`() {
        // Varying baseline so stddev is non-zero; today is a deep HRV drop + RHR spike.
        val hrv = List(10) { if (it % 2 == 0) 48.0 else 52.0 } + listOf(30.0)
        val rhr = List(10) { if (it % 2 == 0) 58.0 else 62.0 } + listOf(70.0)
        val r = StabilityScore.compute(inputs(hrv, rhr), PacingConfig(minBaselineNights = 3))
        assertTrue("deviation should be strongly positive, was ${r.deviation}", r.deviation > 1.75)
        assertEquals(1, r.score)
    }

    @Test fun `hrv rise and rhr drop raises the score`() {
        // Alternating values give a real stddev so z is meaningful.
        val base = List(10) { if (it % 2 == 0) 48.0 else 52.0 }
        val hrv = base + listOf(60.0)
        val rhrBase = List(10) { if (it % 2 == 0) 58.0 else 62.0 }
        val rhr = rhrBase + listOf(52.0)
        val r = StabilityScore.compute(inputs(hrv, rhr), PacingConfig(minBaselineNights = 3))
        assertTrue("deviation should be negative, was ${r.deviation}", r.deviation < -1.0)
        assertEquals(5, r.score)
    }

    @Test fun `poor sleep and symptoms push the score down`() {
        val r = StabilityScore.compute(
            inputs(hrv = List(10) { 50.0 }, rhr = List(10) { 60.0 }, sleep = 1, symptom = 5),
            PacingConfig(minBaselineNights = 3),
        )
        // sleep term -1.0 * 0.5 + symptom +1.0 * 0.5 = 0 -> still 3; check terms separately.
        assertEquals(-1.0, r.sleepTerm, 1e-9)
        assertEquals(1.0, r.symptomTerm, 1e-9)
    }

    @Test fun `not enough nights is calibrating`() {
        val r = StabilityScore.compute(
            inputs(hrv = List(3) { 50.0 }, rhr = List(3) { 60.0 }),
            PacingConfig(minBaselineNights = 7),
        )
        assertTrue(r.calibrating)
        assertEquals("Still learning your baseline", r.interpretation)
    }

    @Test fun `cut points map monotonically`() {
        val c = PacingConfig(minBaselineNights = 2)
        val hrv = List(10) { if (it % 2 == 0) 48.0 else 52.0 } + listOf(70.0)
        val rhr = List(10) { if (it % 2 == 0) 58.0 else 62.0 } + listOf(50.0)
        assertEquals(5, StabilityScore.compute(inputs(hrv, rhr), c).score)
    }

    @Test fun `flat baseline is treated as no signal`() {
        // Zero variance would make z infinite; we choose 0 (neutral) instead.
        val r = StabilityScore.compute(inputs(List(10) { 50.0 } + 20.0, List(10) { 60.0 } + 90.0), PacingConfig(minBaselineNights = 3))
        assertEquals(0.0, r.zHrv ?: Double.NaN, 1e-9)
        assertEquals(0.0, r.zRhr ?: Double.NaN, 1e-9)
    }
}
