package com.riedel.beyondborders.ui.settings

import com.riedel.beyondborders.data.advisory.AdvisoryOutcome
import com.riedel.beyondborders.data.visa.update.VisaCheckOutcome
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
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

class CheckNowTest {
    private val visaOk: suspend () -> VisaCheckOutcome = { VisaCheckOutcome.UpToDate }
    private val advisoryOk: suspend () -> AdvisoryOutcome = { AdvisoryOutcome.UP_TO_DATE }

    @Test
    fun `a throwing visa check does not stop the advisory check`() = runBlocking {
        var advisoryRan = false
        val done = checkBoth(
            visa = { error("boom") },
            advisory = { advisoryRan = true; AdvisoryOutcome.UPDATED },
        )
        assertTrue(advisoryRan)
        assertEquals(VisaCheckOutcome.Unavailable, done.outcome)
        assertEquals(AdvisoryOutcome.UPDATED, done.advisory)
    }

    @Test
    fun `a throwing advisory check leaves the visa result intact`() = runBlocking {
        val done = checkBoth(visa = { VisaCheckOutcome.Updated(2) }, advisory = { error("boom") })
        assertEquals(VisaCheckOutcome.Updated(2), done.outcome)
        assertEquals(AdvisoryOutcome.REJECTED, done.advisory)
    }

    @Test
    fun `cancellation is not swallowed by either side`() {
        val thrown = runCatching {
            runBlocking { checkBoth(visa = { throw CancellationException("stop") }, advisory = advisoryOk) }
        }.exceptionOrNull()
        assertTrue(thrown is CancellationException)
    }

    @Test
    fun `the result fades only when both datasets were already current`() {
        assertTrue(shouldAutoClearCheck(VisaCheckState.Done(VisaCheckOutcome.UpToDate, AdvisoryOutcome.UP_TO_DATE)))
        assertFalse(shouldAutoClearCheck(VisaCheckState.Done(VisaCheckOutcome.UpToDate, AdvisoryOutcome.UPDATED)))
        assertFalse(shouldAutoClearCheck(VisaCheckState.Done(VisaCheckOutcome.UpToDate, AdvisoryOutcome.OFFLINE)))
        assertFalse(shouldAutoClearCheck(VisaCheckState.Done(VisaCheckOutcome.Offline, AdvisoryOutcome.UP_TO_DATE)))
    }

    @Test
    fun `an advisory check that did not happen counts as a failure`() {
        assertTrue(advisoryCheckFailed(AdvisoryOutcome.OFFLINE))
        assertTrue(advisoryCheckFailed(AdvisoryOutcome.REJECTED))
        assertFalse(advisoryCheckFailed(AdvisoryOutcome.UPDATED))
        assertFalse(advisoryCheckFailed(AdvisoryOutcome.UP_TO_DATE))
    }
}
