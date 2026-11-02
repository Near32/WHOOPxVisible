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
                    points += config.c1 * minutes
                }
                else -> {
                    excMin += (spanMs / 60_000L).toInt()
                    points += config.c2 * kotlin.math.exp(config.alpha * (s.bpm - exertion)) * minutes
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
            bpm < exertion -> config.c1
            else -> config.c2 * kotlin.math.exp(config.alpha * (bpm - exertion))
        }
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
