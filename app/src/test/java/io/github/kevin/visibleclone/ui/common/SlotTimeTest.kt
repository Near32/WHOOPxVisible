package io.github.kevin.visibleclone.ui.common

import org.junit.Assert.assertEquals
import org.junit.Test

class SlotTimeTest {
    @Test fun `midnight and noon use 12`() {
        assertEquals("12:00am", formatSlotTime(0))
        assertEquals("12:00pm", formatSlotTime(12 * 60))
    }

    @Test fun `morning and afternoon`() {
        assertEquals("6:00am", formatSlotTime(6 * 60))
        assertEquals("11:45am", formatSlotTime(11 * 60 + 45))
        assertEquals("1:30pm", formatSlotTime(13 * 60 + 30))
        assertEquals("11:45pm", formatSlotTime(23 * 60 + 45))
    }

    @Test fun `quarter hour slots round trip`() {
        assertEquals("12:15am", formatSlotTime(1 * 15))
        assertEquals("11:45pm", formatSlotTime(95 * 15))
    }
}
