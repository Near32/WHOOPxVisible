package io.github.kevin.visibleclone.domain

/**
 * Decides when a pacing alert should fire. Kept separate from Android so it can be tested.
 *
 * Rules from the behaviour spec: alert once when the higher of the two thresholds has been
 * sustained for [PacingConfig.exertionAlertHoldSeconds], then quiet for
 * [PacingConfig.exertionAlertCooldownMinutes].
 */
class PacingAlerter(private val config: PacingConfig) {

    private var overSinceMs: Long? = null
    private var lastAlertMs = 0L
    private var lastDroppedBelowMs = 0L

    /** @return true when an alert should be raised now. */
    fun onSample(bpm: Int, restingHr: Int, maxHr: Int, nowMs: Long): Boolean {
        val exertion = config.exertionThreshold(restingHr, maxHr)
        if (bpm < exertion) {
            overSinceMs = null
            lastDroppedBelowMs = nowMs
            return false
        }
        val since = overSinceMs ?: nowMs.also { overSinceMs = it }
        val heldLongEnough = nowMs - since >= config.exertionAlertHoldSeconds * 1000L
        val cooledDown = nowMs - lastAlertMs >= config.exertionAlertCooldownMinutes * 60_000L ||
            lastAlertMs == 0L
        if (heldLongEnough && cooledDown) {
            lastAlertMs = nowMs
            return true
        }
        return false
    }

    fun reset() {
        overSinceMs = null
        lastAlertMs = 0L
    }
}
