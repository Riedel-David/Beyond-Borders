package com.riedel.beyondborders.data.visa.update

import org.junit.Assert.assertEquals
import org.junit.Test

class VisaCheckOutcomeTest {

    @Test
    fun `a seen manifest with nothing to change is up to date`() {
        assertEquals(VisaCheckOutcome.UpToDate, outcomeOf(UpdateResult(manifestSeen = true)))
    }

    @Test
    fun `applied files are reported with their count`() {
        val r = UpdateResult(applied = listOf("DE", "FR"), manifestSeen = true)
        assertEquals(VisaCheckOutcome.Updated(2), outcomeOf(r))
    }

    @Test
    fun `a network error before the manifest means offline`() {
        assertEquals(VisaCheckOutcome.Offline, outcomeOf(UpdateResult(ioFailure = true)))
    }

    @Test
    fun `a rejected manifest without a network error means unavailable`() {
        assertEquals(VisaCheckOutcome.Unavailable, outcomeOf(UpdateResult()))
    }

    @Test
    fun `a network error after the manifest with nothing applied means offline`() {
        assertEquals(VisaCheckOutcome.Offline, outcomeOf(UpdateResult(manifestSeen = true, ioFailure = true)))
    }

    @Test
    fun `a partly finished run still counts what was applied`() {
        val r = UpdateResult(applied = listOf("DE"), manifestSeen = true, ioFailure = true)
        assertEquals(VisaCheckOutcome.Updated(1), outcomeOf(r))
    }

    @Test
    fun `deleting a stale copy alone is not an update`() {
        assertEquals(VisaCheckOutcome.UpToDate, outcomeOf(UpdateResult(deleted = listOf("DE"), manifestSeen = true)))
    }
}
