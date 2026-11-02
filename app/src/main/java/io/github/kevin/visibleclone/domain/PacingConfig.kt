package io.github.kevin.visibleclone.domain

import kotlinx.serialization.Serializable

/**
 * Every tunable number in the app. Visible does not publish its formulas, so these are our
 * starting values; the Settings screen edits them and they are persisted as JSON in DataStore.
 *
 * // ponytail: constants from the comparison doc; tune in Settings after a week of real data.
 */
@Serializable
data class PacingConfig(
    // ---- Stability Score ----
    val baselineDays: Int = 30,
    val minBaselineNights: Int = 7,
    val wHrv: Double = 1.0,
    val wRhr: Double = 1.0,
    val wSleep: Double = 0.5,
    val wSymptom: Double = 0.5,
    /** Dev at or below this scores 5, then 4, 3, 2, everything above scores 1. */
    val cut5: Double = -1.0,
    val cut4: Double = -0.25,
    val cut3: Double = 0.75,
    val cut2: Double = 1.75,

    // ---- PacePoints ----
    val activityOffset: Int = 15,
    val exertionFraction: Double = 0.5,
    val c1: Double = 1.0,
    val c2: Double = 3.0,
    val alpha: Double = 0.05,
    /**
     * Global scale applied to every per-minute rate.
     *
     * The comparison doc's c1 = 1 point/minute is illustrative and yields absurd totals against a
     * ~14-point budget (an hour of activity would cost 60 points). 0.05 makes an hour of activity
     * cost ~3 points, i.e. roughly a fifth of the budget — which matches how Visible prices
     * ordinary tasks. Tune it once you have a week of real days.
     */
    val zoneScale: Double = 0.05,
    val fallbackMaxHr: Int = 185,

    // ---- Budget ----
    val budgetAtStability1: Double = 4.0,
    val budgetAtStability5: Double = 14.0,

    // ---- Alerts ----
    val exertionAlertHoldSeconds: Int = 30,
    val exertionAlertCooldownMinutes: Int = 5,
) {
    fun activityThreshold(rhr: Int): Int = rhr + activityOffset

    fun exertionThreshold(rhr: Int, maxHr: Int): Int =
        (rhr + exertionFraction * (maxHr - rhr)).toInt()

    fun dailyBudget(stability: Int): Double {
        val clamped = stability.coerceIn(1, 5)
        return budgetAtStability1 + (clamped - 1) * (budgetAtStability5 - budgetAtStability1) / 4.0
    }
}
