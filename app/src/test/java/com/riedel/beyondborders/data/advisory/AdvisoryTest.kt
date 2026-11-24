package com.riedel.beyondborders.data.advisory

import com.riedel.beyondborders.data.visa.update.VisaFetchRejected
import com.riedel.beyondborders.data.visa.update.VisaFetcher
import java.io.File
import java.io.IOException
import java.time.Instant
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AdvisoryTest {

    private val now = Instant.parse("2026-11-25T00:00:00Z")
    private val fixture = File("src/test/resources/advisory_fixture.json").readText()
    private val fixtureBytes = fixture.toByteArray()
    private val data = parseAdvisory(fixture)!!

    private fun withGenerated(at: String, countries: String = """{"FR": {"US": {"stance": 1, "label": "L", "updated": "2026-01-01", "url": "https://a.example/"}}}"""): ByteArray =
        """{"dataFormat": 1, "generatedAt": "$at", "sources": {}, "countries": $countries}""".toByteArray()

    private class FakeFetcher(var body: ByteArray? = null, var fail: Exception? = null) : VisaFetcher {
        var calls = 0
        var lastFile: String? = null
        override fun fetch(file: String, maxBytes: Int): ByteArray {
            calls++
            lastFile = file
            fail?.let { throw it }
            return body!!
        }
    }

    private class FakeStore(var bytes: ByteArray? = null, var failWrite: Boolean = false) : AdvisoryStore {
        override fun read() = bytes
        override fun write(bytes: ByteArray) {
            if (failWrite) throw IOException("disk")
            this.bytes = bytes
        }
    }

    // --- parseAdvisory ---------------------------------------------------------

    @Test
    fun `a well-formed file parses with the source's own wording`() {
        assertEquals(Instant.parse("2026-11-24T07:00:00Z"), data.generatedAt)
        assertEquals("Auswärtiges Amt", data.sourceLabels["DE"])
        val fr = data.forPlace("fr")
        assertEquals("Level 2: Exercise increased caution", fr.getValue("US").label)
        assertEquals(listOf("terrorism", "civil-unrest"), fr.getValue("US").tags)
        assertEquals(emptyList<String>(), fr.getValue("UK").tags)
    }

    @Test
    fun `an unknown dataFormat is ignored entirely`() {
        assertNull(parseAdvisory(fixture.replace("\"dataFormat\": 1", "\"dataFormat\": 2")))
    }

    @Test
    fun `malformed json or a missing generatedAt gives null rather than a throw`() {
        assertNull(parseAdvisory("not json"))
        assertNull(parseAdvisory("""{"dataFormat": 1, "countries": {}}"""))
        assertNull(parseAdvisory(""))
    }

    @Test
    fun `a bad entry is skipped on its own and the rest of the country survives`() {
        // XX has a stance of 7, an http link and a date that is not a date: nothing usable, so no country at all.
        assertTrue("XX" !in data.countries)
        assertTrue("france" !in data.countries)
        assertEquals(setOf("FR", "SY", "GL"), data.countries.keys)
    }

    @Test
    fun `a source absent for a country stays absent and is not read as a warning level`() {
        val sy = data.forPlace("SY")
        assertEquals(setOf("US", "UK"), sy.keys)
        assertNull(sy["DE"])
    }

    @Test
    fun `a territory gets only what the data holds for its own code`() {
        // Denmark has no entry in the fixture, Greenland has one; neither borrows from the other.
        assertTrue(data.forPlace("DK").isEmpty())
        assertEquals(setOf("DE"), data.forPlace("GL").keys)
        assertTrue(data.forPlace("XA").isEmpty())
    }

    // --- agreement ---------------------------------------------------------------

    @Test
    fun `agreement counts stance 3 out of the sources that have data`() {
        assertEquals(Agreement(2, 2), agreementOf(data.forPlace("SY")))
        assertEquals(Agreement(0, 3), agreementOf(data.forPlace("FR")))
        assertEquals(Agreement(0, 1), agreementOf(data.forPlace("GL")))
    }

    @Test
    fun `agreement with no data at all is zero of zero`() {
        assertEquals(Agreement(0, 0), agreementOf(emptyMap()))
    }

    @Test
    fun `stance 2 does not count as advising against travel`() {
        val e = data.forPlace("FR").getValue("US").copy(stance = 2)
        assertEquals(Agreement(0, 1), agreementOf(mapOf("US" to e)))
    }

    // --- verify and choose ---------------------------------------------------------

    @Test
    fun `a newer valid file is accepted`() {
        assertNull(verifyAdvisory(fixtureBytes, Instant.parse("2026-11-01T00:00:00Z"), now))
        assertNull(verifyAdvisory(fixtureBytes, null, now))
    }

    @Test
    fun `a file that is not newer than the copy in use is not applied`() {
        assertEquals(AdvisoryRejection.NOT_NEWER, verifyAdvisory(fixtureBytes, data.generatedAt, now))
        assertEquals(AdvisoryRejection.NOT_NEWER, verifyAdvisory(fixtureBytes, Instant.parse("2026-12-01T00:00:00Z"), now))
    }

    @Test
    fun `a future-dated file is rejected but tomorrow is tolerated`() {
        assertEquals(AdvisoryRejection.FUTURE_DATED, verifyAdvisory(withGenerated("2026-12-30T00:00:00Z"), null, now))
        assertNull(verifyAdvisory(withGenerated("2026-11-25T12:00:00Z"), null, now))
    }

    @Test
    fun `a file with no usable country is rejected so it cannot blank the section`() {
        assertEquals(AdvisoryRejection.UNPARSEABLE, verifyAdvisory(withGenerated("2026-11-24T00:00:00Z", "{}"), null, now))
        assertEquals(AdvisoryRejection.UNPARSEABLE, verifyAdvisory("junk".toByteArray(), null, now))
    }

    @Test
    fun `the stored copy wins only when strictly newer than the bundled one`() {
        val older = parseAdvisory(String(withGenerated("2026-11-01T00:00:00Z")))!!
        val newer = parseAdvisory(String(withGenerated("2026-11-24T08:00:00Z")))!!
        assertEquals(newer, chooseAdvisory(older, newer, now))
        assertEquals(data, chooseAdvisory(data, older, now))
        assertEquals(data, chooseAdvisory(data, data.copy(), now))
    }

    @Test
    fun `chooseAdvisory falls back to whichever exists and to null for neither`() {
        assertEquals(data, chooseAdvisory(null, data, now))
        assertEquals(data, chooseAdvisory(data, null, now))
        assertNull(chooseAdvisory(null, null, now))
    }

    @Test
    fun `a future-dated stored copy loses to the bundled one`() {
        val future = parseAdvisory(String(withGenerated("2027-06-01T00:00:00Z")))!!
        assertEquals(data, chooseAdvisory(data, future, now))
    }

    // --- runAdvisoryUpdate -----------------------------------------------------------

    @Test
    fun `a newer download is written to the store`() {
        val store = FakeStore()
        val fetcher = FakeFetcher(body = fixtureBytes)
        assertEquals(AdvisoryOutcome.UPDATED, runAdvisoryUpdate(fetcher, store, { null }, { now }))
        assertArrayEquals(fixtureBytes, store.bytes)
        assertEquals("advisory.json", fetcher.lastFile)
    }

    @Test
    fun `an offline run leaves the stored copy alone`() {
        val store = FakeStore(bytes = fixtureBytes)
        assertEquals(AdvisoryOutcome.OFFLINE, runAdvisoryUpdate(FakeFetcher(fail = IOException("down")), store, { null }, { now }))
        assertArrayEquals(fixtureBytes, store.bytes)
    }

    @Test
    fun `a rejected fetch or a garbage body changes nothing and does not throw`() {
        val store = FakeStore(bytes = fixtureBytes)
        assertEquals(AdvisoryOutcome.REJECTED, runAdvisoryUpdate(FakeFetcher(fail = VisaFetchRejected("x")), store, { null }, { now }))
        assertEquals(AdvisoryOutcome.REJECTED, runAdvisoryUpdate(FakeFetcher(body = "<html>".toByteArray()), store, { null }, { now }))
        assertArrayEquals(fixtureBytes, store.bytes)
    }

    @Test
    fun `an identical download is up to date and writes nothing`() {
        val store = FakeStore()
        assertEquals(AdvisoryOutcome.UP_TO_DATE, runAdvisoryUpdate(FakeFetcher(body = fixtureBytes), store, { data }, { now }))
        assertNull(store.bytes)
    }

    @Test
    fun `a disk failure is a rejection and not a throw`() {
        assertEquals(AdvisoryOutcome.REJECTED, runAdvisoryUpdate(FakeFetcher(body = fixtureBytes), FakeStore(failWrite = true), { null }, { now }))
    }

    @Test
    fun `an advisory run never touches a visa store because it has none`() {
        // Independence is structural: runAdvisoryUpdate takes only an AdvisoryStore and a fetcher,
        // so a visa failure cannot reach it and its failures cannot reach a visa run.
        val fetcher = FakeFetcher(fail = IOException("down"))
        runAdvisoryUpdate(fetcher, FakeStore(), { null }, { now })
        assertEquals(1, fetcher.calls)
    }

    // --- repository fallback ---------------------------------------------------------------

    @Test
    fun `a missing bundled asset means no advisory data and no crash`() = runBlocking {
        val repo = AdvisoryRepository({ null }, FakeStore(), { now })
        assertNull(repo.current())
    }

    @Test
    fun `an unparsable bundled asset means no advisory data and a throwing loader too`() = runBlocking {
        assertNull(AdvisoryRepository({ "garbage".toByteArray() }, FakeStore(), { now }).current())
        assertNull(AdvisoryRepository({ error("boom") }, FakeStore(), { now }).current())
    }

    @Test
    fun `an unparsable stored copy falls back to the bundled asset`() = runBlocking {
        val repo = AdvisoryRepository({ fixtureBytes }, FakeStore(bytes = "garbage".toByteArray()), { now })
        assertNotNull(repo.current())
        assertEquals(data, repo.current())
    }

    @Test
    fun `invalidate makes the next load see a newly stored copy`() = runBlocking {
        val store = FakeStore()
        val repo = AdvisoryRepository({ fixtureBytes }, store, { now })
        assertEquals(data.generatedAt, repo.current()!!.generatedAt)
        val before = repo.dataVersion.value
        store.bytes = withGenerated("2026-11-24T09:00:00Z")
        repo.invalidate()
        assertEquals(Instant.parse("2026-11-24T09:00:00Z"), repo.current()!!.generatedAt)
        assertTrue(repo.dataVersion.value > before)
    }
}
