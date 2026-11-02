package io.github.kevin.visibleclone.domain

/** One heart-rate reading; [timestampMs] is epoch millis, [bpm] the measured rate. */
data class HrSample(val timestampMs: Long, val bpm: Int)

data class PaceBreakdown(
    val points: Double,
    val restingMinutes: Int,
    val activityMinutes: Int,
    val exertionMinutes: Int,
    val peakBpm: Int,
) {
    fun budgetFraction(budget: Double): Double =
        if (budget <= 0.0) 0.0 else (points / budget).coerceIn(0.0, 1.0)
}

object PacePoints {

    /**
     * Converts a heart-rate series into points.
     *
     * Samples are treated as instantaneous readings spaced at most [gapMs] apart; a gap larger than
     * that contributes no time (the phone was away from the strap). Each interval is charged at the
     * rate determined by its sample, which is equivalent to minute-level bucketing for the alert
     * thresholds we care about.
     */
    fun compute(
        samples: List<HrSample>,
        restingHr: Int,
        maxHr: Int,
        config: PacingConfig,
        gapMs: Long = 60_000,
    ): PaceBreakdown {
        if (samples.isEmpty()) return PaceBreakdown(0.0, 0, 0, 0, 0)

        val activity = config.activityThreshold(restingHr)
        val exertion = config.exertionThreshold(restingHr, maxHr)

        var points = 0.0
        var restMin = 0
        var actMin = 0
        var excMin = 0
        var peak = 0

        val ordered = samples.sortedBy { it.timestampMs }
        for (i in ordered.indices) {
            val s = ordered[i]
            peak = maxOf(peak, s.bpm)
            val spanMs = if (i + 1 < ordered.size) {
                (ordered[i + 1].timestampMs - s.timestampMs).coerceIn(0L, gapMs)
            } else 0L
            val minutes = spanMs / 60_000.0

            when {
                s.bpm < activity -> restMin += (spanMs / 60_000L).toInt()
                s.bpm < exertion -> {
                    actMin += (spanMs / 60_000L).toInt()
                    points += config.c1 * config.zoneScale * minutes
                }
                else -> {
                    excMin += (spanMs / 60_000L).toInt()
                    points += config.c2 * config.zoneScale *
                        kotlin.math.exp(config.alpha * (s.bpm - exertion)) * minutes
                }
            }
        }

        return PaceBreakdown(
            points = points,
            restingMinutes = restMin,
            activityMinutes = actMin,
            exertionMinutes = excMin,
            peakBpm = peak,
        )
    }

    /**
     * Rate in points/minute for a single reading, with smooth transitions at both thresholds.
     *
     * Below the activity threshold the rate eases in from zero rather than switching on; above the
     * exertion threshold it eases into the exponential. This keeps a session straddling a boundary
     * from being priced all-or-nothing. `zoneAt` still reports the crisp zone for display.
     */
    fun rateAt(bpm: Int, restingHr: Int, maxHr: Int, config: PacingConfig): Double {
        val activity = config.activityThreshold(restingHr)
        val exertion = config.exertionThreshold(restingHr, maxHr)
        val ramp = config.thresholdRampBpm

        val a = smoothstep((bpm - (activity - ramp)) / (2 * ramp))
        if (a <= 0.0) return 0.0
        val e = smoothstep((bpm - (exertion - ramp)) / (2 * ramp))
        val excess = config.c2 * kotlin.math.exp(config.alpha * (bpm - exertion).coerceAtLeast(0)) - config.c1
        return config.zoneScale * (config.c1 * a + excess * e)
    }

    /** Hermite smoothstep; linear when ramp is zero. */
    private fun smoothstep(t: Double): Double {
        val x = t.coerceIn(0.0, 1.0)
        return x * x * (3 - 2 * x)
    }

    /**
     * Points from WHOOP's own per-workout zone durations (milliseconds in zones 0-5).
     *
     * WHOOP's zone edges are fractions of heart-rate reserve (measured, see [PacingConfig.whoopZoneHrr]).
     * We take each zone's midpoint in bpm and charge it at our pacing rate, so a logged activity
     * contributes its real measured time-in-zone instead of an estimate from a daily average.
     */
    fun fromZoneDurations(
        zoneMillis: List<Long?>,
        restingHr: Int,
        maxHr: Int,
        config: PacingConfig,
        avgHeartRate: Int? = null,
    ): PaceBreakdown {
        val reserve = (maxHr - restingHr).coerceAtLeast(1)
        // Edges: zone 0 starts at reserve, zone 5 ends at max HR.
        val edges = listOf(0.0) + config.whoopZoneHrr + listOf(1.0)
        val midpoints = (0..5).map { i ->
            restingHr + ((edges[i] + edges[i + 1]) / 2.0) * reserve
        }
        // Anchor zone 0 to the workout's own average HR.
        //
        // Zone 0 is wide (rest up to 40% of reserve) and most low-effort time sits near the bottom
        // of it, so its midpoint badly overstates the real intensity: a 3h chess session with an
        // average of 66 bpm was being charged as if it were 76 bpm. Invert the weighted mean
        // instead — everything outside zone 0 uses its midpoint, and zone 0 absorbs the residual.
        val effective = midpoints.toMutableList()
        if (avgHeartRate != null && zoneMillis.size >= 6) {
            val minutes = zoneMillis.map { (it ?: 0L) / 60_000.0 }
            val total = minutes.sum()
            // Anchoring divides by zone-0 time, so it is only stable when zone 0 is a meaningful
            // share of the activity. Measured across 312 workouts: >=90% zone-0 sessions imply a
            // coherent 64.8 bpm mean, while <20% zone-0 sessions produce nonsense (-776 bpm mean).
            if (total > 0 && minutes[0] >= total * 0.2) {
                val nonZeroContribution = (1..5).sumOf { midpoints[it] * minutes[it] }
                val implied = (avgHeartRate * total - nonZeroContribution) / minutes[0]
                val lower = restingHr.toDouble()
                val upper = restingHr + config.whoopZoneHrr.first() * reserve
                if (implied in lower..upper) effective[0] = implied
            }
        }

        val activity = config.activityThreshold(restingHr)
        val exertion = config.exertionThreshold(restingHr, maxHr)

        var points = 0.0
        var restMin = 0
        var actMin = 0
        var excMin = 0
        var peak = 0

        zoneMillis.forEachIndexed { i, msLong ->
            val ms = msLong ?: 0L
            if (ms <= 0L) return@forEachIndexed
            val bpm = effective[i].toInt()
            peak = maxOf(peak, bpm)
            val minutes = ms / 60_000.0
            // One rate function for live samples and zone durations alike.
            points += rateAt(bpm, restingHr, maxHr, config) * minutes
            // Minutes are bucketed crisply for display.
            when {
                bpm < activity -> restMin += (ms / 60_000L).toInt()
                bpm < exertion -> actMin += (ms / 60_000L).toInt()
                else -> excMin += (ms / 60_000L).toInt()
            }
        }
        return PaceBreakdown(points, restMin, actMin, excMin, peak)
    }

    /** A logged activity and its measured time-in-zone, as stored from WHOOP. */
    data class ZonedActivity(
        val startMs: Long,
        val endMs: Long,
        val zoneMillis: List<Long?>,
        val avgHeartRate: Int? = null,
    )

    /**
     * Combines live HR samples with logged activities.
     *
     * These cover different time windows and must not be treated as alternatives: a day can have
     * three hours of tagged activity in the morning and live HR in the afternoon. Where live samples
     * cover an activity's window we trust the samples (finer resolution) and drop that share of the
     * activity's zone points; uncovered portions keep their measured zone time.
     */
    fun combine(
        samples: List<HrSample>,
        activities: List<ZonedActivity>,
        restingHr: Int,
        maxHr: Int,
        config: PacingConfig,
        gapMs: Long = 120_000,
    ): PaceBreakdown {
        val live = compute(samples, restingHr, maxHr, config)
        if (activities.isEmpty()) return live

        val intervals = coverageIntervals(samples, gapMs)

        var points = live.points
        var rest = live.restingMinutes
        var act = live.activityMinutes
        var exc = live.exertionMinutes
        var peak = live.peakBpm

        activities.forEach { a ->
            val duration = (a.endMs - a.startMs).coerceAtLeast(1L)
            val covered = intervals.sumOf { (s, e) ->
                (minOf(e, a.endMs) - maxOf(s, a.startMs)).coerceAtLeast(0L)
            }.coerceAtMost(duration)
            val uncoveredFraction = 1.0 - covered.toDouble() / duration
            if (uncoveredFraction <= 0.0) return@forEach

            val z = fromZoneDurations(a.zoneMillis, restingHr, maxHr, config, a.avgHeartRate)
            points += z.points * uncoveredFraction
            rest += (z.restingMinutes * uncoveredFraction).toInt()
            act += (z.activityMinutes * uncoveredFraction).toInt()
            exc += (z.exertionMinutes * uncoveredFraction).toInt()
            peak = maxOf(peak, z.peakBpm)
        }

        return PaceBreakdown(points, rest, act, exc, peak)
    }

    /** Merged [start, end] spans where samples were arriving without a gap longer than [gapMs]. */
    fun coverageIntervals(samples: List<HrSample>, gapMs: Long): List<Pair<Long, Long>> {
        if (samples.isEmpty()) return emptyList()
        val sorted = samples.sortedBy { it.timestampMs }
        val out = mutableListOf<Pair<Long, Long>>()
        var start = sorted[0].timestampMs
        var prev = start
        for (i in 1 until sorted.size) {
            val ts = sorted[i].timestampMs
            if (ts - prev > gapMs) {
                out += start to prev
                start = ts
            }
            prev = ts
        }
        out += start to prev
        return out
    }

    enum class Zone { RESTING, ACTIVITY, EXERTION }

    fun zoneAt(bpm: Int, restingHr: Int, maxHr: Int, config: PacingConfig): Zone {
        val activity = config.activityThreshold(restingHr)
        val exertion = config.exertionThreshold(restingHr, maxHr)
        return when {
            bpm < activity -> Zone.RESTING
            bpm < exertion -> Zone.ACTIVITY
            else -> Zone.EXERTION
        }
    }
}
