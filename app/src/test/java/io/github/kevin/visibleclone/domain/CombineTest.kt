package io.github.kevin.visibleclone.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CombineTest {
    private val cfg = PacingConfig()
    private val rhr = 60
    private val maxHr = 180
    private val hour = 3_600_000L

    private fun samplesEveryMinute(from: Long, minutes: Int, bpm: Int) =
        (0 until minutes).map { HrSample(from + it * 60_000L, bpm) }

    @Test fun `activities alone are unaffected`() {
        val a = PacePoints.ZonedActivity(0, 2 * hour, listOf(2 * hour, 0, 0, 0, 0, 0))
        val r = PacePoints.combine(emptyList(), listOf(a), rhr, maxHr, cfg)
        val zoneOnly = PacePoints.fromZoneDurations(listOf(2 * hour, 0, 0, 0, 0, 0), rhr, maxHr, cfg)
        assertEquals(zoneOnly.points, r.points, 1e-6)
    }

    @Test fun `live HR in a different window adds to activity points`() {
        val a = PacePoints.ZonedActivity(0, 2 * hour, listOf(2 * hour, 0, 0, 0, 0, 0))
        // Live samples in the afternoon, nowhere near the morning activity.
        val s = samplesEveryMinute(6 * hour, 30, 100)
        val live = PacePoints.compute(s, rhr, maxHr, cfg)
        val r = PacePoints.combine(s, listOf(a), rhr, maxHr, cfg)

        assertTrue("combined should exceed live alone", r.points > live.points)
        val zoneOnly = PacePoints.fromZoneDurations(listOf(2 * hour, 0, 0, 0, 0, 0), rhr, maxHr, cfg)
        assertEquals(live.points + zoneOnly.points, r.points, 1e-6)
    }

    @Test fun `live HR covering the activity does not double count it`() {
        val a = PacePoints.ZonedActivity(0, 30 * 60_000L, listOf(30 * 60_000L, 0, 0, 0, 0, 0))
        // 31 samples from 0..30 min inclusive: coverage reaches the activity's end.
        val s = samplesEveryMinute(0, 31, 100)
        val live = PacePoints.compute(s, rhr, maxHr, cfg)
        val r = PacePoints.combine(s, listOf(a), rhr, maxHr, cfg)
        assertEquals(live.points, r.points, 1e-6)
    }

    @Test fun `partially covered activity keeps only its uncovered share`() {
        val a = PacePoints.ZonedActivity(0, 60 * 60_000L, listOf(60 * 60_000L, 0, 0, 0, 0, 0))
        // Samples 0..30 min -> 30 of 60 minutes covered, so half the zone points remain.
        val s = samplesEveryMinute(0, 31, 100)
        val live = PacePoints.compute(s, rhr, maxHr, cfg)
        val zoneOnly = PacePoints.fromZoneDurations(listOf(60 * 60_000L, 0, 0, 0, 0, 0), rhr, maxHr, cfg)
        val r = PacePoints.combine(s, listOf(a), rhr, maxHr, cfg)
        assertEquals(live.points + zoneOnly.points * 0.5, r.points, 1e-6)
    }

    @Test fun `gaps split coverage so distant samples do not mask an activity`() {
        val a = PacePoints.ZonedActivity(10 * hour, 10 * hour + 30 * 60_000L, listOf(30 * 60_000L, 0, 0, 0, 0, 0))
        val s = samplesEveryMinute(0, 5, 100) // far earlier, then a big gap
        val r = PacePoints.combine(s, listOf(a), rhr, maxHr, cfg)
        val zoneOnly = PacePoints.fromZoneDurations(listOf(30 * 60_000L, 0, 0, 0, 0, 0), rhr, maxHr, cfg)
        val live = PacePoints.compute(s, rhr, maxHr, cfg)
        assertEquals(live.points + zoneOnly.points, r.points, 1e-6)
    }
}
