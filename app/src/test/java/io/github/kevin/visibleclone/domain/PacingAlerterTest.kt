package io.github.kevin.visibleclone.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PacingAlerterTest {
    private val cfg = PacingConfig(exertionAlertHoldSeconds = 30, exertionAlertCooldownMinutes = 5)
    private val rhr = 60
    private val maxHr = 180 // exertion threshold 120

    @Test fun `no alert below threshold`() {
        val a = PacingAlerter(cfg)
        assertFalse(a.onSample(100, rhr, maxHr, 0))
        assertFalse(a.onSample(119, rhr, maxHr, 60_000))
    }

    @Test fun `alert only after sustained elevation`() {
        val a = PacingAlerter(cfg)
        assertFalse(a.onSample(130, rhr, maxHr, 0))
        assertFalse(a.onSample(130, rhr, maxHr, 10_000))
        assertTrue(a.onSample(130, rhr, maxHr, 30_000))
    }

    @Test fun `no repeat inside cooldown`() {
        val a = PacingAlerter(cfg)
        assertFalse(a.onSample(130, rhr, maxHr, 0))
        assertTrue(a.onSample(130, rhr, maxHr, 30_000))
        assertFalse(a.onSample(130, rhr, maxHr, 60_000))
        assertFalse(a.onSample(130, rhr, maxHr, 200_000))
    }

    @Test fun `dropping below resets the hold timer`() {
        val a = PacingAlerter(cfg)
        a.onSample(130, rhr, maxHr, 0)
        a.onSample(100, rhr, maxHr, 20_000)
        assertFalse(a.onSample(130, rhr, maxHr, 30_000))
        assertTrue(a.onSample(130, rhr, maxHr, 60_000))
    }

    @Test fun `cooldown expires eventually`() {
        val a = PacingAlerter(cfg)
        a.onSample(130, rhr, maxHr, 0)
        assertTrue(a.onSample(130, rhr, maxHr, 30_000))
        assertTrue(a.onSample(130, rhr, maxHr, 30_000 + 5 * 60_000))
    }
}
