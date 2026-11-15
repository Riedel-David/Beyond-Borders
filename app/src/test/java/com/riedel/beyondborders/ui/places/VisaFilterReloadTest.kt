package com.riedel.beyondborders.ui.places

import com.riedel.beyondborders.data.visa.VisaCategory
import org.junit.Assert.assertEquals
import org.junit.Test

class VisaFilterReloadTest {

    @Test
    fun `a category chip that new data no longer offers falls back to all`() {
        val offered = listOf(VisaFilter.All, VisaFilter.Easy)
        assertEquals(VisaFilter.All, visaFilterStillOffered(VisaFilter.Category(VisaCategory.ETA), offered))
    }

    @Test
    fun `a chip that is still offered is kept`() {
        val chip = VisaFilter.Category(VisaCategory.ETA)
        assertEquals(chip, visaFilterStillOffered(chip, listOf(VisaFilter.All, chip)))
    }
}
