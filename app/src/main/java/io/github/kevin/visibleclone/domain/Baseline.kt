package io.github.kevin.visibleclone.domain

/** Rolling mean / population standard deviation over a trailing window. */
data class Baseline(
    val mean: Double,
    val stdDev: Double,
    val n: Int,
) {
    fun z(value: Double): Double = if (stdDev < 1e-9) 0.0 else (value - mean) / stdDev

    companion object {
        fun of(values: List<Double>): Baseline? {
            if (values.isEmpty()) return null
            val mean = values.average()
            val variance = values.sumOf { (it - mean) * (it - mean) } / values.size
            return Baseline(mean, kotlin.math.sqrt(variance), values.size)
        }

        /** Values from the `days` entries before the last one (the last entry is "today"). */
        fun preceding(values: List<Double>, days: Int): Baseline? {
            if (values.size < 2) return null
            val prior = values.dropLast(1).takeLast(days)
            return of(prior)
        }
    }
}
