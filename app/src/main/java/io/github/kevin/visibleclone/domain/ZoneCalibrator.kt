package io.github.kevin.visibleclone.domain

/**
 * Infers WHOOP's heart-rate zone boundaries from the user's own workout history.
 *
 * The API exposes per-workout time-in-zone but never the bpm edges. Those edges can still be
 * bracketed: if a workout whose peak HR was M spent time in zone z, the zone must start at or below
 * M. If a workout that reached zone z-1 but no higher peaked at M, zone z must start above M.
 * Intersecting those constraints across many workouts pins each boundary to a narrow interval.
 *
 * The result is expressed as fractions of heart-rate reserve, which is the model that fits the
 * observed data far better than fractions of maximum HR.
 */
object ZoneCalibrator {

    data class ZoneEstimate(
        val zone: Int,          // 1..5
        val minBpm: Int,        // smallest peak HR that reached this zone
        val maxBpm: Int?,       // largest peak HR that stopped short of it
        val fraction: Double,   // inferred boundary as a fraction of heart-rate reserve
        val samples: Int,
        val confident: Boolean,
    )

    data class Result(
        val estimates: List<ZoneEstimate>,
        val fractions: List<Double>,   // zone 1..5 boundaries, ready for PacingConfig
        val workoutsUsed: Int,
    ) {
        fun bpmBounds(restingHr: Int, maxHr: Int): List<Pair<Int, Int>> {
            val reserve = (maxHr - restingHr).coerceAtLeast(1)
            val edges = listOf(0.0) + fractions + listOf(1.0)
            return (0..5).map { i ->
                val lo = restingHr + (edges[i] * reserve).toInt()
                val hi = restingHr + (edges[i + 1] * reserve).toInt()
                lo to hi
            }
        }
    }

    /** [entries] are (peak HR, 6 booleans for zones 0..5 having any time). */
    fun calibrate(
        entries: List<Pair<Int, List<Boolean>>>,
        restingHr: Int,
        maxHr: Int,
        defaults: List<Double> = PacingConfig().whoopZoneHrr,
        minSamples: Int = 3,
    ): Result {
        val reserve = (maxHr - restingHr).coerceAtLeast(1)
        val usable = entries.filter { it.first > restingHr && it.second.size >= 6 }

        val estimates = (1..5).map { z ->
            val reached = usable.filter { it.second[z] }.map { it.first }
            val stopped = usable
                .filter { !it.second[z] && it.second[z - 1] }
                .map { it.first }

            val minBpm = reached.minOrNull()
            val maxBpm = stopped.maxOrNull()
            val confident = minBpm != null && reached.size >= minSamples

            // Boundary lies in (maxBpm, minBpm]; take the midpoint of that bracket.
            val boundary = when {
                minBpm != null && maxBpm != null -> (maxBpm + minBpm) / 2.0
                minBpm != null -> minBpm.toDouble()
                else -> restingHr + defaults[z - 1] * reserve
            }
            val fraction = ((boundary - restingHr) / reserve).coerceIn(0.05, 0.98)

            ZoneEstimate(
                zone = z,
                minBpm = minBpm ?: 0,
                maxBpm = maxBpm,
                fraction = if (confident) fraction else defaults[z - 1],
                samples = reached.size,
                confident = confident,
            )
        }

        // Boundaries must be strictly increasing; repair any crossings from noisy data.
        val repaired = estimates.map { it.fraction }.toMutableList()
        for (i in 1 until repaired.size) {
            if (repaired[i] <= repaired[i - 1]) repaired[i] = repaired[i - 1] + 0.02
        }

        return Result(
            estimates = estimates,
            fractions = repaired.map { (it * 100).toInt() / 100.0 },
            workoutsUsed = usable.size,
        )
    }
}
