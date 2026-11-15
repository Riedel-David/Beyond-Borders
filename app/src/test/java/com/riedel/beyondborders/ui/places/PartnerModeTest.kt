package com.riedel.beyondborders.ui.places

import com.riedel.beyondborders.data.place.Bounds
import com.riedel.beyondborders.data.place.Place
import com.riedel.beyondborders.data.visa.VisaBand
import com.riedel.beyondborders.data.visa.VisaCategory
import com.riedel.beyondborders.data.visa.VisaOrigin
import com.riedel.beyondborders.data.visa.VisaRegistry
import com.riedel.beyondborders.data.visa.VisaRequirement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class PartnerModeTest {
    private fun req(
        category: VisaCategory,
        days: Int? = null,
        code: String = "US",
        note: String? = null,
    ) = VisaRequirement(
        code = code, category = category, days = days, origin = VisaOrigin.DATASET, note = note,
        confidence = null, sourceUrl = null, datasetSaid = null, officialUrl = null, officialFor = null,
    )

    private fun reg(passport: String, asOf: String = "2026-01-01", vararg rows: Triple<String, String, Int?>) =
        VisaRegistry.parse(
            """{"passport":"$passport","asOf":"$asOf",
            "dataset":{"name":"n","url":"https://e.org","licence":"L"},"iataUrl":"https://e.org",
            "requirements":[${rows.joinToString(",") {
                """{"code":"${it.first}","category":"${it.second}","source":"dataset"${it.third?.let { d -> ""","days":$d""" } ?: ""}}"""
            }}]}"""
        )

    @Test
    fun `the harder of the two answers wins and names its owner as the blocker`() {
        val free = req(VisaCategory.VISA_FREE)
        val evisa = req(VisaCategory.E_VISA)
        val a = combineForPartners(free, evisa)!!
        assertSame(evisa, a.winner)
        assertEquals(PartnerBlocker.PARTNER, a.partner?.blocker)
        assertEquals(false, a.fromPrimary)
        val b = combineForPartners(evisa, free)!!
        assertSame(evisa, b.winner)
        assertEquals(PartnerBlocker.YOU, b.partner?.blocker)
        assertEquals(true, b.fromPrimary)
    }

    @Test
    fun `a definite refusal outranks unknown and unknown outranks every easier answer`() {
        val check = req(VisaCategory.CHECK)
        for (hard in listOf(VisaCategory.PERMIT_REQUIRED, VisaCategory.NO_ADMISSION)) {
            val h = req(hard)
            assertEquals(hard, combineForPartners(check, h)?.winner?.category)
            assertEquals(PartnerBlocker.PARTNER, combineForPartners(check, h)?.partner?.blocker)
            assertEquals(hard, combineForPartners(h, check)?.winner?.category)
            assertEquals(PartnerBlocker.YOU, combineForPartners(h, check)?.partner?.blocker)
        }
        for (easy in listOf(VisaCategory.VISA_FREE, VisaCategory.E_VISA, VisaCategory.VISA_REQUIRED)) {
            assertEquals(VisaCategory.CHECK, combineForPartners(check, req(easy))?.winner?.category)
            assertEquals(PartnerBlocker.YOU, combineForPartners(check, req(easy))?.partner?.blocker)
            assertEquals(PartnerBlocker.PARTNER, combineForPartners(req(easy), check)?.partner?.blocker)
        }
        assertEquals(VisaCategory.NO_ADMISSION, combineForPartners(req(VisaCategory.PERMIT_REQUIRED), req(VisaCategory.NO_ADMISSION))?.winner?.category)
        assertEquals(PartnerBlocker.NEITHER, combineForPartners(check, check)?.partner?.blocker)
    }

    @Test
    fun `low confidence on either side is carried onto the pair result`() {
        fun low(c: VisaCategory) = req(c).copy(confidence = com.riedel.beyondborders.data.visa.VisaConfidence.LOW)
        val solidHard = req(VisaCategory.E_VISA)
        val lowEasy = low(VisaCategory.VISA_FREE)
        assertEquals(com.riedel.beyondborders.data.visa.VisaConfidence.LOW, combineForPartners(lowEasy, solidHard)?.winner?.confidence)
        assertEquals(com.riedel.beyondborders.data.visa.VisaConfidence.LOW, combineForPartners(solidHard, lowEasy)?.winner?.confidence)
        assertEquals(null, combineForPartners(solidHard, req(VisaCategory.VISA_FREE))?.winner?.confidence)
    }

    @Test
    fun `the via passport rides along and a partner equal to your own passport is no partner`() {
        assertEquals("FR", combineForPartners(req(VisaCategory.ETA), req(VisaCategory.ETA), "FR")?.partner?.youVia)
        assertEquals(null, combineForPartners(req(VisaCategory.ETA), req(VisaCategory.ETA))?.partner?.youVia)
        assertEquals(null, effectivePartner("DE", "DE", null))
        assertEquals(null, effectivePartner("FR", "DE", "FR"))
        assertEquals("UA", effectivePartner("UA", "DE", "FR"))
        assertEquals(null, effectivePartner(null, "DE", null))
    }

    @Test
    fun `equal categories show the shorter stated stay`() {
        val long = req(VisaCategory.VISA_FREE, days = 90, note = "long")
        val short = req(VisaCategory.VISA_FREE, days = 30, note = "short")
        assertSame(short, combineForPartners(long, short)?.winner)
        assertEquals(PartnerBlocker.PARTNER, combineForPartners(long, short)?.partner?.blocker)
        assertSame(short, combineForPartners(short, long)?.winner)
        assertEquals(PartnerBlocker.YOU, combineForPartners(short, long)?.partner?.blocker)
    }

    @Test
    fun `a stated day limit is stricter than an unstated one either way round`() {
        val unstated = req(VisaCategory.VISA_FREE, days = null, note = "open")
        val stated = req(VisaCategory.VISA_FREE, days = 30, note = "limited")
        val youOpen = combineForPartners(unstated, stated)!!
        assertSame(stated, youOpen.winner)
        assertEquals(PartnerBlocker.PARTNER, youOpen.partner?.blocker)
        assertEquals(false, youOpen.fromPrimary)
        val youLimited = combineForPartners(stated, unstated)!!
        assertSame(stated, youLimited.winner)
        assertEquals(PartnerBlocker.YOU, youLimited.partner?.blocker)
        assertEquals(true, youLimited.fromPrimary)
    }

    @Test
    fun `two unstated stays and two equal stays are a tie that goes to you`() {
        val a = req(VisaCategory.VISA_FREE, days = null)
        val b = req(VisaCategory.VISA_FREE, days = null)
        assertSame(a, combineForPartners(a, b)?.winner)
        assertEquals(PartnerBlocker.NEITHER, combineForPartners(a, b)?.partner?.blocker)
        val c = req(VisaCategory.VISA_FREE, days = 30)
        val d = req(VisaCategory.VISA_FREE, days = 30)
        assertSame(c, combineForPartners(c, d)?.winner)
        assertEquals(PartnerBlocker.NEITHER, combineForPartners(c, d)?.partner?.blocker)
    }

    @Test
    fun `a full tie goes to you and keeps fromPrimary`() {
        val same = req(VisaCategory.ETA, days = 90)
        val other = req(VisaCategory.ETA, days = 90)
        val tie = combineForPartners(same, other)!!
        assertSame(same, tie.winner)
        assertEquals(true, tie.fromPrimary)
        assertEquals(PartnerBlocker.NEITHER, tie.partner?.blocker)
    }

    @Test
    fun `no answer on either side means no answer for the pair`() {
        assertNull(combineForPartners(null, req(VisaCategory.VISA_FREE)))
        assertNull(combineForPartners(req(VisaCategory.VISA_FREE), null))
        assertNull(combineForPartners(null, null))
    }

    @Test
    fun `the winning requirement is returned whole with its own note`() {
        val you = req(VisaCategory.VISA_FREE, note = "yours")
        val partner = req(VisaCategory.VISA_ON_ARRIVAL, note = "theirs")
        assertEquals("theirs", combineForPartners(you, partner)?.winner?.note)
    }

    @Test
    fun `a second nationality is resolved to its best before the worst of the pair`() {
        val primary = reg("DE", rows = arrayOf(Triple("TH", "E_VISA", null)))
        val second = reg("GB", rows = arrayOf(Triple("TH", "VISA_FREE", 30)))
        val partner = reg("UA", rows = arrayOf(Triple("TH", "VISA_ON_ARRIVAL", null)))
        val result = partnerRegistry(primary, second, partner)
        // The user's best is GB's visa-free; the partner's visa on arrival then limits the pair.
        assertEquals(VisaCategory.VISA_ON_ARRIVAL, result["TH"]?.category)
        val withoutPartnerSecond = partnerRegistry(primary, null, partner)
        assertEquals(VisaCategory.E_VISA, withoutPartnerSecond["TH"]?.category)
    }

    @Test
    fun `places either person has no answer for are absent and the date is the oldest`() {
        val primary = reg("DE", "2026-03-01", Triple("FR", "VISA_FREE", null), Triple("KP", "VISA_REQUIRED", null))
        val partner = reg("UA", "2026-02-01", Triple("FR", "VISA_FREE", null), Triple("CN", "E_VISA", null))
        val result = partnerRegistry(primary, null, partner)
        assertEquals(setOf("FR"), result.requirements.map { it.code }.toSet())
        assertEquals("2026-02-01", result.asOf)
    }

    @Test
    fun `partner mode off or without a partner leaves the registries untouched`() {
        val primary = reg("DE", rows = arrayOf(Triple("FR", "VISA_FREE", null)))
        val second = reg("GB", rows = arrayOf(Triple("FR", "VISA_FREE", null)))
        val partner = reg("UA", rows = arrayOf(Triple("FR", "E_VISA", null)))
        assertSame(primary, visaViews(primary, second, partner, partnerMode = false).first)
        assertSame(second, visaViews(primary, second, partner, partnerMode = false).second)
        assertSame(primary, visaViews(primary, second, null, partnerMode = true).first)
        val on = visaViews(primary, second, partner, partnerMode = true)
        assertNull(on.second)
        assertEquals(VisaCategory.E_VISA, on.first?.get("FR")?.category)
    }

    @Test
    fun `legend counts and map band codes agree in partner mode`() {
        val primary = reg(
            "DE",
            rows = arrayOf(
                Triple("FR", "VISA_FREE", 90), Triple("TH", "VISA_FREE", 60), Triple("RU", "NO_ADMISSION", null),
                Triple("CN", "E_VISA", null), Triple("KP", "VISA_REQUIRED", null), Triple("AU", "ETA", null),
            ),
        )
        val partner = reg(
            "UA",
            rows = arrayOf(
                Triple("FR", "VISA_FREE", 90), Triple("TH", "VISA_FREE", 30), Triple("RU", "CHECK", null),
                Triple("CN", "VISA_FREE", null), Triple("AU", "VISA_REQUIRED", null),
            ),
        )
        val (visa, second) = visaViews(primary, null, partner, partnerMode = true)
        val bands = bandCodes(visa, second, lens = true)
        val counts = combinedBandCounts(visa, second)
        assertEquals(bands.mapValues { it.value.size }, counts)
        assertEquals(setOf("FR", "TH"), bands[VisaBand.VISA_FREE])
        // RU: your definite refusal outranks their unknown, so it is CLOSED, not CHECK.
        assertEquals(setOf("RU"), bands[VisaBand.CLOSED])
        // CN: the partner is visa-free but you need an e-visa; AU: your ETA, their visa.
        assertEquals(setOf("CN", "AU"), bands[VisaBand.VISA_REQUIRED])
        // KP has no partner answer: grey, and never also painted.
        val places = listOf("FR", "TH", "RU", "CN", "KP", "AU").map { place(it) }
        val noData = noDataCodes(places, visa, second, lens = true)
        assertEquals(setOf("KP"), noData)
        assertTrue(bands.values.flatten().toSet().intersect(noData).isEmpty())
        // The list reads the same registry, so it agrees with the legend's total.
        val rows = buildVisaRows(places, visa, second, emptyMap(), "", VisaFilter.All)
        assertEquals(counts.values.sum(), rows.size)
        assertEquals(combinedTotals(visa, second).answered, rows.size)
    }

    @Test
    fun `a selected category chip the partner view does not offer falls back to All`() {
        val primary = reg("DE", rows = arrayOf(Triple("FR", "VISA_FREE", null), Triple("CN", "ETA", null)))
        val partner = reg("UA", rows = arrayOf(Triple("FR", "VISA_FREE", null), Triple("CN", "E_VISA", null)))
        val eta = VisaFilter.Category(VisaCategory.ETA)
        val own = visaFilters(primary, null)
        assertTrue(eta in own)
        assertEquals(eta, filterOrAll(eta, own))
        val (v, s) = visaViews(primary, null, partner, partnerMode = true)
        assertEquals(VisaFilter.All, filterOrAll(eta, visaFilters(v, s)))
        assertEquals(VisaFilter.Easy, filterOrAll(VisaFilter.Easy, visaFilters(v, s)))
    }

    @Test
    fun `revalidating keeps a chip the new registries offer and drops one they do not`() {
        val primary = reg("DE", rows = arrayOf(Triple("FR", "VISA_FREE", null), Triple("CN", "ETA", null)))
        val partner = reg("UA", rows = arrayOf(Triple("FR", "VISA_FREE", null), Triple("CN", "E_VISA", null)))
        val eta = VisaFilter.Category(VisaCategory.ETA)
        val own = VisaViews(primary, null, null)
        assertEquals(eta, revalidatedFilter(eta, own))
        val (v, s) = visaViews(primary, null, partner, partnerMode = true)
        assertEquals(VisaFilter.All, revalidatedFilter(eta, VisaViews(v, s, partner)))
        assertEquals(VisaFilter.Easy, revalidatedFilter(VisaFilter.Easy, VisaViews(v, s, partner)))
    }

    @Test
    fun `second won is false in partner mode and in a tie and true only for a second passport win`() {
        val free = req(VisaCategory.VISA_FREE)
        val eta = req(VisaCategory.ETA)
        assertEquals(true, combineRequirements(eta, free)?.secondWon)
        assertEquals(false, combineRequirements(free, eta)?.secondWon)
        assertEquals(false, combineForPartners(eta, free)?.secondWon)
        assertEquals(false, combineForPartners(free, eta)?.secondWon)
    }

    @Test
    fun `a row takes its links from the partner registry only where the partner limits the pair`() {
        val you = reg("DE", rows = arrayOf(Triple("FR", "VISA_FREE", null)))
        val partner = reg("UA", rows = arrayOf(Triple("FR", "VISA_FREE", null)))
        val sources = PartnerSources(you, null, partner)
        val limit = { b: PartnerBlocker -> PartnerComparison(req(VisaCategory.ETA), req(VisaCategory.ETA), b) }
        assertSame(partner, rowSource(you, sources, limit(PartnerBlocker.PARTNER)))
        assertSame(you, rowSource(you, sources, limit(PartnerBlocker.YOU)))
        assertSame(you, rowSource(you, sources, limit(PartnerBlocker.NEITHER)))
        assertSame(you, rowSource(you, null, null))
    }

    @Test
    fun `travel advice is hidden for the own passports only, not where an official link shows`() {
        assertEquals(false, showTravelAdvice("DE", listOf("DE")))
        assertEquals(false, showTravelAdvice("fr", listOf("DE", "FR")))
        assertEquals(true, showTravelAdvice("JP", listOf("DE")))
        assertEquals(true, showTravelAdvice("JP", listOf("DE", "FR")))
        // The partner's passport country is passed in alongside the user's in partner mode.
        assertEquals(false, showTravelAdvice("UA", listOf("DE", "FR", "UA")))
    }

    @Test
    fun `a partner who becomes one of your own passports is cleared and others are kept`() {
        assertEquals(null, com.riedel.beyondborders.settings.partnerAfterOwnChange("FR", "FR"))
        assertEquals("FR", com.riedel.beyondborders.settings.partnerAfterOwnChange("FR", "DE"))
        assertEquals("FR", com.riedel.beyondborders.settings.partnerAfterOwnChange("FR", null))
        assertEquals(null, com.riedel.beyondborders.settings.partnerAfterOwnChange(null, null))
    }

    private fun place(code: String) = Place(
        code = code, name = code, continent = "Europe", region = "Europe", sovereign = code,
        uninhabited = false, unMember = true, lat = 0.0, lon = 0.0,
        bounds = Bounds(-1.0, -1.0, 1.0, 1.0), areaKm2 = 0, popEst = 0,
    )
}
