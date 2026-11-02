package io.github.kevin.visibleclone.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BaselineTest {
    @Test fun `known mean and stddev`() {
        val b = Baseline.of(listOf(2.0, 4.0, 4.0, 4.0, 5.0, 5.0, 7.0, 9.0))!!
        assertEquals(5.0, b.mean, 1e-9)
        assertEquals(2.0, b.stdDev, 1e-9)
        assertEquals(1.5, b.z(8.0), 1e-9)
    }

    @Test fun `zero variance yields zero z rather than infinity`() {
        val b = Baseline.of(listOf(60.0, 60.0, 60.0))!!
        assertEquals(0.0, b.z(90.0), 1e-9)
    }

    @Test fun `preceding excludes today and respects window`() {
        val values = (1..10).map { it.toDouble() } // today = 10
        val b = Baseline.preceding(values, days = 3)!!
        assertEquals(3, b.n)
        assertEquals(8.0, b.mean, 1e-9) // 7, 8, 9
    }

    @Test fun `empty baseline is null`() {
        assertEquals(null, Baseline.of(emptyList()))
        assertEquals(null, Baseline.preceding(listOf(1.0), 30))
    }
}
