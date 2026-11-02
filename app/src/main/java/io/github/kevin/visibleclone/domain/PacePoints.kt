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

    /** Rate in points/minute for a single reading — used by the live screen. */
    fun rateAt(bpm: Int, restingHr: Int, maxHr: Int, config: PacingConfig): Double {
        val activity = config.activityThreshold(restingHr)
        val exertion = config.exertionThreshold(restingHr, maxHr)
        return when {
            bpm < activity -> 0.0
            bpm < exertion -> config.c1 * config.zoneScale
            else -> config.c2 * config.zoneScale * kotlin.math.exp(config.alpha * (bpm - exertion))
        }
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
    ): PaceBreakdown {
        val reserve = (maxHr - restingHr).coerceAtLeast(1)
        // Edges: zone 0 starts at reserve, zone 5 ends at max HR.
        val edges = listOf(0.0) + config.whoopZoneHrr + listOf(1.0)
        val midpoints = (0..5).map { i ->
            restingHr + ((edges[i] + edges[i + 1]) / 2.0) * reserve
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
            val bpm = midpoints[i].toInt()
            peak = maxOf(peak, bpm)
            val minutes = ms / 60_000.0
            when {
                bpm < activity -> restMin += (ms / 60_000L).toInt()
                bpm < exertion -> {
                    actMin += (ms / 60_000L).toInt()
                    points += config.c1 * config.zoneScale * minutes
                }
                else -> {
                    excMin += (ms / 60_000L).toInt()
                    points += config.c2 * config.zoneScale *
                        kotlin.math.exp(config.alpha * (bpm - exertion)) * minutes
                }
            }
        }
        return PaceBreakdown(points, restMin, actMin, excMin, peak)
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
