package io.github.kevin.visibleclone.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PacePointsTest {

    private val config = PacingConfig(zoneScale = 1.0)
    // rhr 60 -> activity 75, exertion with maxHr 180 -> 60 + 0.5*120 = 120
    private val rhr = 60
    private val maxHr = 180

    private fun series(vararg bpm: Int, stepMs: Long = 60_000): List<HrSample> =
        bpm.mapIndexed { i, b -> HrSample(i * stepMs, b) }

    @Test fun `thresholds follow the config`() {
        assertEquals(75, config.activityThreshold(rhr))
        assertEquals(120, config.exertionThreshold(rhr, maxHr))
    }

    @Test fun `resting time earns nothing`() {
        val r = PacePoints.compute(series(60, 62, 70, 74), rhr, maxHr, config)
        assertEquals(0.0, r.points, 1e-9)
        assertEquals(3, r.restingMinutes) // last sample has no span
    }

    @Test fun `activity zone earns c1 per minute`() {
        val r = PacePoints.compute(series(90, 90, 90), rhr, maxHr, config)
        assertEquals(2.0, r.points, 1e-9) // 2 minute-spans
        assertEquals(2, r.activityMinutes)
    }

    @Test fun `exertion zone costs exponentially more`() {
        val atThreshold = PacePoints.compute(series(120), rhr, maxHr, config)
        assertEquals(0.0, atThreshold.points, 1e-9) // single sample, no span

        val r = PacePoints.compute(series(120, 120), rhr, maxHr, config)
        assertEquals(3.0, r.points, 1e-9) // c2 * exp(0) * 1 min

        val high = PacePoints.compute(series(140, 140), rhr, maxHr, config)
        assertEquals(3.0 * Math.exp(0.05 * 20), high.points, 1e-9)
        assertTrue(high.points > r.points * 2)
    }

    @Test fun `long gaps contribute no time`() {
        val samples = listOf(HrSample(0, 90), HrSample(10 * 60_000, 90))
        val r = PacePoints.compute(samples, rhr, maxHr, config, gapMs = 60_000)
        assertEquals(1.0, r.points, 1e-9) // capped at one minute
    }

    @Test fun `unsorted input is ordered`() {
        val samples = listOf(HrSample(60_000, 90), HrSample(0, 90), HrSample(30_000, 90))
        val r = PacePoints.compute(samples, rhr, maxHr, config)
        assertEquals(1.0, r.points, 1e-9)
    }

    @Test fun `zones classify correctly`() {
        assertEquals(PacePoints.Zone.RESTING, PacePoints.zoneAt(70, rhr, maxHr, config))
        assertEquals(PacePoints.Zone.ACTIVITY, PacePoints.zoneAt(100, rhr, maxHr, config))
        assertEquals(PacePoints.Zone.EXERTION, PacePoints.zoneAt(130, rhr, maxHr, config))
    }

    @Test fun `a typical tagged day lands inside the budget`() {
        // 2h pottering in zone 0 plus 30 min in zone 1 — an ordinary day for a pacing user.
        val day = PacePoints.fromZoneDurations(
            listOf(120 * 60_000L, 30 * 60_000L, 0, 0, 0, 0), rhr, maxHr, PacingConfig(),
        )
        assertTrue("expected within a 14pt budget, got ${day.points}", day.points in 1.0..14.0)
    }

    @Test fun `a hard exertional day exceeds the budget`() {
        // 3h of zone 2 is genuinely overexerting and should blow through the budget.
        val day = PacePoints.fromZoneDurations(
            listOf(0, 0, 180 * 60_000L, 0, 0, 0), rhr, maxHr, PacingConfig(),
        )
        assertTrue("expected over budget, got ${day.points}", day.points > 14.0)
    }

    @Test fun `budget interpolates between anchors`() {
        assertEquals(4.0, config.dailyBudget(1), 1e-9)
        assertEquals(14.0, config.dailyBudget(5), 1e-9)
        assertEquals(9.0, config.dailyBudget(3), 1e-9)
        assertEquals(4.0, config.dailyBudget(0), 1e-9)
        assertEquals(14.0, config.dailyBudget(9), 1e-9)
    }
}
