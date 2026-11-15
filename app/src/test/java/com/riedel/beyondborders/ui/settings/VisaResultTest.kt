package com.riedel.beyondborders.ui.settings

import com.riedel.beyondborders.data.visa.update.VisaCheckOutcome
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VisaResultTest {
    @Test
    fun `an up-to-date result clears itself`() {
        assertTrue(shouldAutoClearVisaResult(VisaCheckOutcome.UpToDate))
    }

    @Test
    fun `an update or a failure stays until the next check`() {
        assertFalse(shouldAutoClearVisaResult(VisaCheckOutcome.Updated(3)))
        assertFalse(shouldAutoClearVisaResult(VisaCheckOutcome.Offline))
        assertFalse(shouldAutoClearVisaResult(VisaCheckOutcome.Unavailable))
    }
}
