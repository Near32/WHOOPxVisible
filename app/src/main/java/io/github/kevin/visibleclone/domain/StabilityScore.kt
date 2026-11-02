package io.github.kevin.visibleclone.domain

/**
 * Visible's Stability Score, as far as its published description allows.
 *
 * Inputs are overnight HRV (rMSSD), overnight resting HR, a 1-5 sleep-quality rating and a 1-5
 * symptom-severity rating. Each biometric is z-scored against its own trailing baseline; the
 * check-in ratings are mapped to 0..1. Higher [deviation] means more autonomic stress.
 */
data class StabilityInputs(
    /** Oldest first; the last element is tonight. */
    val hrvSeries: List<Double>,
    val rhrSeries: List<Double>,
    val sleepQuality: Int?,      // 1..5
    val symptomSeverity: Int?,   // 1..5
)

data class StabilityResult(
    val score: Int,              // 1..5
    val deviation: Double,
    val zHrv: Double?,
    val zRhr: Double?,
    val sleepTerm: Double,
    val symptomTerm: Double,
    val baselineNights: Int,
    val calibrating: Boolean,
) {
    val interpretation: String
        get() = when {
            calibrating -> "Still learning your baseline"
            score >= 5 -> "Steady"
            score == 4 -> "Settled"
            score == 3 -> "A little strained"
            score == 2 -> "Stretched — pace carefully"
            else -> "Running low — rest today"
        }
}

object StabilityScore {

    fun compute(inputs: StabilityInputs, config: PacingConfig): StabilityResult {
        val hrvBaseline = Baseline.preceding(inputs.hrvSeries, config.baselineDays)
        val rhrBaseline = Baseline.preceding(inputs.rhrSeries, config.baselineDays)

        val tonightHrv = inputs.hrvSeries.lastOrNull()
        val tonightRhr = inputs.rhrSeries.lastOrNull()

        // z of 0 means "no signal"; a missing baseline collapses the term.
        val zHrv = if (hrvBaseline != null && tonightHrv != null) -hrvBaseline.z(tonightHrv) else 0.0
        val zRhr = if (rhrBaseline != null && tonightRhr != null) rhrBaseline.z(tonightRhr) else 0.0

        // 1 (best) .. 5 (worst) mapped to 0..1, centred at 0 so it matches the z-score scale.
        val sleepTerm = (inputs.sleepQuality ?: 3).let { (it - 3) / 2.0 }
        val symptomTerm = (inputs.symptomSeverity ?: 3).let { (it - 3) / 2.0 }

        val deviation =
            config.wHrv * zHrv +
            config.wRhr * zRhr +
            config.wSleep * sleepTerm +
            config.wSymptom * symptomTerm

        val nights = minOf(hrvBaseline?.n ?: 0, rhrBaseline?.n ?: 0)
        val calibrating = nights < config.minBaselineNights

        val score = when {
            calibrating -> 3
            deviation <= config.cut5 -> 5
            deviation <= config.cut4 -> 4
            deviation <= config.cut3 -> 3
            deviation <= config.cut2 -> 2
            else -> 1
        }

        return StabilityResult(
            score = score,
            deviation = deviation,
            zHrv = if (hrvBaseline != null && tonightHrv != null) zHrv else null,
            zRhr = if (rhrBaseline != null && tonightRhr != null) zRhr else null,
            sleepTerm = sleepTerm,
            symptomTerm = symptomTerm,
            baselineNights = nights,
            calibrating = calibrating,
        )
    }
}
