package com.riedel.beyondborders.data.advisory

import com.riedel.beyondborders.data.visa.update.VisaFetchNotFound
import com.riedel.beyondborders.data.visa.update.VisaFetchRejected
import com.riedel.beyondborders.data.visa.update.failureForStatus
import com.riedel.beyondborders.data.visa.update.VisaFetcher
import java.io.File
import java.io.IOException
import java.time.Instant
import kotlinx.coroutines.runBlocking
import org.junit.Assume
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AdvisoryTest {

    private val now = Instant.parse("2026-11-25T00:00:00Z")
    private val fixture = File("src/test/resources/advisory_fixture.json").readText()
    private val fixtureBytes = fixture.toByteArray()
    private val data = parseAdvisory(fixture)!!

    private fun withGenerated(at: String, countries: String = """{"FR": {"US": {"stance": 1, "label": "L", "updated": "2026-01-01", "url": "https://travel.state.gov/"}}}"""): ByteArray =
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

    private fun deEntry(notes: String) = parseAdvisory(
        withGenerated(
            "2026-11-24T07:00:00Z",
            """{"GE": {"DE": {"stance": 2, "label": "L", "updated": "2026-01-01", "url": "https://travel.state.gov/"$notes}}}""",
        ).toString(Charsets.UTF_8),
    )!!.forPlace("GE").getValue("DE")

    @Test
    fun `notes are read verbatim and an entry without them has an empty list`() {
        assertEquals(listOf("Von Reisen nach X wird abgeraten."), deEntry(""", "notes": ["Von Reisen nach X wird abgeraten."]""").notes)
        assertEquals(emptyList<String>(), deEntry("").notes)
        assertEquals(emptyList<String>(), data.forPlace("fr").getValue("DE").notes)
    }

    @Test
    fun `a label key is read when it is a non-blank string and is otherwise null`() {
        assertEquals("de_partial", deEntry(""", "labelKey": "de_partial"""").labelKey)
        assertNull(deEntry("").labelKey)
        assertNull(deEntry(""", "labelKey": "  """").labelKey)
        assertNull(deEntry(""", "labelKey": 5""").labelKey)
        assertNull(deEntry(""", "labelKey": null""").labelKey)
        assertEquals("L", deEntry(""", "labelKey": 5""").label)
    }

    @Test
    fun `non-string or blank notes are dropped and a non-array notes field is ignored`() {
        assertEquals(listOf("a", "b"), deEntry(""", "notes": ["a", 5, null, "  ", {"x": 1}, "b"]""").notes)
        assertEquals(emptyList<String>(), deEntry(""", "notes": "Von Reisen wird abgeraten"""").notes)
        assertEquals(2, deEntry(""", "notes": null""").stance)
    }

    @Test
    fun `an entry whose stance is not a JSON integer is skipped`() {
        for (bad in listOf("\"3\"", "2.9", "null", "true")) {
            val d = parseAdvisory(
                withGenerated(
                    "2026-11-24T07:00:00Z",
                    """{"GE": {"DE": {"stance": $bad, "label": "L", "updated": "2026-01-01", "url": "https://travel.state.gov/"}}}""",
                ).toString(Charsets.UTF_8),
            )!!
            assertTrue(bad, d.forPlace("GE").isEmpty())
        }
    }

    @Test
    fun `a null source label is not read as the text null`() {
        val d = parseAdvisory(
            """{"dataFormat": 1, "generatedAt": "2026-11-24T07:00:00Z", "sources": {"US": {"label": null}, "UK": {"label": "UK FCDO"}}, "countries": {}}""",
        )!!
        assertNull(d.sourceLabels["US"])
        assertEquals("UK FCDO", d.sourceLabels["UK"])
    }

    @Test
    fun `notes are capped in count and a note over the length cap is dropped never cut`() {
        assertEquals(listOf("1", "2", "3"), deEntry(""", "notes": ["1", "2", "3", "4", "5"]""").notes)
        val atCap = "x".repeat(MAX_ADVISORY_NOTE_CHARS)
        assertEquals(listOf(atCap), deEntry(""", "notes": ["$atCap"]""").notes)
        val over = "x".repeat(MAX_ADVISORY_NOTE_CHARS + 1)
        assertEquals(listOf("kept"), deEntry(""", "notes": ["$over", "kept"]""").notes)
    }

    @Test
    fun `an entry whose url is on a host outside the allow-list is dropped`() {
        val entries = parseAdvisory(
            withGenerated(
                "2026-11-24T07:00:00Z",
                """{"FR": {"US": {"stance": 1, "label": "L", "updated": "2026-01-01", "url": "https://evil.example/x"},
                    "UK": {"stance": 1, "label": "L", "updated": "2026-01-01", "url": "https://www.gov.uk.evil.example/x"},
                    "DE": {"stance": 1, "label": "L", "updated": "2026-01-01", "url": "https://www.auswaertiges-amt.de/de/-/1"}}}""",
            ).toString(Charsets.UTF_8),
        )!!.forPlace("FR")
        assertEquals(setOf("DE"), entries.keys)
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
        assertNull(verifyAdvisory(fixtureBytes, Instant.parse("2026-11-01T00:00:00Z"), now, minCountries = 1))
        assertNull(verifyAdvisory(fixtureBytes, null, now, minCountries = 1))
    }

    @Test
    fun `a file that is not newer than the copy in use is not applied`() {
        assertEquals(AdvisoryRejection.NOT_NEWER, verifyAdvisory(fixtureBytes, data.generatedAt, now, minCountries = 1))
        assertEquals(AdvisoryRejection.NOT_NEWER, verifyAdvisory(fixtureBytes, Instant.parse("2026-12-01T00:00:00Z"), now, minCountries = 1))
    }

    @Test
    fun `a future-dated file is rejected but tomorrow is tolerated`() {
        assertEquals(AdvisoryRejection.FUTURE_DATED, verifyAdvisory(withGenerated("2026-12-30T00:00:00Z"), null, now, minCountries = 1))
        assertNull(verifyAdvisory(withGenerated("2026-11-25T12:00:00Z"), null, now, minCountries = 1))
    }

    @Test
    fun `a file with no usable country is rejected so it cannot blank the section`() {
        assertEquals(AdvisoryRejection.UNPARSEABLE, verifyAdvisory(withGenerated("2026-11-24T00:00:00Z", "{}"), null, now, minCountries = 1))
        assertEquals(AdvisoryRejection.UNPARSEABLE, verifyAdvisory("junk".toByteArray(), null, now, minCountries = 1))
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
        assertEquals(AdvisoryOutcome.UPDATED, runAdvisoryUpdate(fetcher, store, { null }, { now }, minCountries = 1))
        assertArrayEquals(fixtureBytes, store.bytes)
        assertEquals("advisory.json", fetcher.lastFile)
    }

    @Test
    fun `an offline run leaves the stored copy alone`() {
        val store = FakeStore(bytes = fixtureBytes)
        assertEquals(AdvisoryOutcome.OFFLINE, runAdvisoryUpdate(FakeFetcher(fail = IOException("down")), store, { null }, { now }, minCountries = 1))
        assertArrayEquals(fixtureBytes, store.bytes)
    }

    @Test
    fun `a rejected fetch or a garbage body changes nothing and does not throw`() {
        val store = FakeStore(bytes = fixtureBytes)
        assertEquals(AdvisoryOutcome.REJECTED, runAdvisoryUpdate(FakeFetcher(fail = VisaFetchRejected("x")), store, { null }, { now }, minCountries = 1))
        assertEquals(AdvisoryOutcome.REJECTED, runAdvisoryUpdate(FakeFetcher(body = "<html>".toByteArray()), store, { null }, { now }, minCountries = 1))
        assertArrayEquals(fixtureBytes, store.bytes)
    }

    @Test
    fun `an identical download is up to date and writes nothing`() {
        val store = FakeStore()
        assertEquals(AdvisoryOutcome.UP_TO_DATE, runAdvisoryUpdate(FakeFetcher(body = fixtureBytes), store, { data }, { now }, minCountries = 1))
        assertNull(store.bytes)
    }

    @Test
    fun `a disk failure is a rejection and not a throw`() {
        assertEquals(AdvisoryOutcome.REJECTED, runAdvisoryUpdate(FakeFetcher(body = fixtureBytes), FakeStore(failWrite = true), { null }, { now }, minCountries = 1))
    }

    @Test
    fun `an advisory run never touches a visa store because it has none`() {
        // Independence is structural: runAdvisoryUpdate takes only an AdvisoryStore and a fetcher,
        // so a visa failure cannot reach it and its failures cannot reach a visa run.
        val fetcher = FakeFetcher(fail = IOException("down"))
        runAdvisoryUpdate(fetcher, FakeStore(), { null }, { now }, minCountries = 1)
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

    // --- plausibility ------------------------------------------------------------------

    private fun manyCountries(n: Int): String =
        (0 until n).joinToString(",", "{", "}") {
            val code = "" + ('A' + it / 26) + ('A' + it % 26)
            """"$code": {"US": {"stance": 1, "label": "L", "updated": "2026-01-01", "url": "https://travel.state.gov/"}}"""
        }

    @Test
    fun `a valid file under the 150 country floor is rejected`() {
        assertEquals(AdvisoryRejection.IMPLAUSIBLE, verifyAdvisory(withGenerated("2026-11-24T00:00:00Z", manyCountries(149)), null, now))
        assertNull(verifyAdvisory(withGenerated("2026-11-24T00:00:00Z", manyCountries(150)), null, now))
    }

    @Test
    fun `a file below 80 percent of the copy in use is rejected`() {
        val b = withGenerated("2026-11-24T00:00:00Z", manyCountries(159))
        assertEquals(AdvisoryRejection.IMPLAUSIBLE, verifyAdvisory(b, null, now, inUseCountries = 200, minCountries = 1))
        assertNull(verifyAdvisory(withGenerated("2026-11-24T00:00:00Z", manyCountries(160)), null, now, inUseCountries = 200, minCountries = 1))
    }

    @Test
    fun `a truncated download does not replace a full stored copy`() {
        val full = withGenerated("2026-11-01T00:00:00Z", manyCountries(200))
        val store = FakeStore(bytes = full)
        val short = withGenerated("2026-11-24T00:00:00Z", manyCountries(160).let { manyCountries(100) })
        assertEquals(AdvisoryOutcome.REJECTED, runAdvisoryUpdate(FakeFetcher(body = short), store, { null }, { now }, minCountries = 1))
        assertArrayEquals(full, store.bytes)
    }

    @Test
    fun `a null label or url is rejected rather than shown as the word null`() {
        val j = """{"FR": {"US": {"stance": 1, "label": null, "updated": "2026-01-01", "url": "https://travel.state.gov/"},
                    "UK": {"stance": 1, "label": "L", "updated": "2026-01-01", "url": null},
                    "DE": {"stance": 1, "label": "L", "updated": null, "url": "https://travel.state.gov/"}}}"""
        assertTrue(parseAdvisory(String(withGenerated("2026-11-24T00:00:00Z", j)))!!.countries.isEmpty())
    }

    @Test
    fun `a load that races invalidate does not publish the stale result`() = runBlocking {
        val store = FakeStore()
        lateinit var repo: AdvisoryRepository
        var first = true
        repo = AdvisoryRepository({
            if (first) { first = false; store.bytes = withGenerated("2026-11-24T09:00:00Z"); repo.invalidate() }
            fixtureBytes
        }, store, { now })
        // The first pass reads under generation 0, invalidate() bumps it, so the pass is discarded and redone.
        assertEquals(Instant.parse("2026-11-24T09:00:00Z"), repo.current()!!.generatedAt)
    }

    // --- notes on every source, allow-list, stricter guards -----------------------------

    private fun entry(source: String, extra: String = "", url: String = "https://travel.state.gov/x") =
        """"$source": {"stance": 1, "label": "L", "updated": "2026-01-01", "url": "$url"$extra}"""

    @Test
    fun `notes are read for all three sources and an over-long one is dropped for each`() {
        val long = "x".repeat(MAX_ADVISORY_NOTE_CHARS + 1)
        val j = """{"FR": {${entry("US", ""","notes": ["US one", "US two"]""")},
            ${entry("UK", ""","notes": ["UK one"]""", "https://www.gov.uk/x")},
            ${entry("DE", ""","notes": ["a", "b", "c", "d", "$long"]""", "https://www.auswaertiges-amt.de/x")}}}"""
        val fr = parseAdvisory(String(withGenerated("2026-11-24T00:00:00Z", j)))!!.forPlace("FR")
        assertEquals(listOf("US one", "US two"), fr.getValue("US").notes)
        assertEquals(listOf("UK one"), fr.getValue("UK").notes)
        assertEquals(listOf("a", "b", "c"), fr.getValue("DE").notes)
        val capped = parseAdvisory(String(withGenerated("2026-11-24T00:00:00Z",
            """{"FR": {${entry("US", ""","notes": ["$long"]""")}}}""")))!!.forPlace("FR").getValue("US").notes
        assertTrue(capped.isEmpty())
    }

    @Test
    fun `a url with userinfo or a lookalike host is dropped and an upper-case allowed host is kept`() {
        fun parsed(url: String) = parseAdvisory(String(withGenerated("2026-11-24T00:00:00Z", """{"FR": {${entry("US", "", url)}}}""")))!!.forPlace("FR")
        assertTrue(parsed("https://travel.state.gov@evil.example/").isEmpty())
        assertTrue(parsed("https://travel.state.gov.evil.example/").isEmpty())
        assertTrue(parsed("http://travel.state.gov/").isEmpty())
        assertEquals(1, parsed("https://TRAVEL.STATE.GOV/x").size)
    }

    private fun sourced(count: Int, vararg sources: String): ByteArray {
        val urls = mapOf("US" to "https://travel.state.gov/x", "UK" to "https://www.gov.uk/x", "DE" to "https://www.auswaertiges-amt.de/x")
        val body = (1..count).joinToString(",") { n ->
            val code = "" + ('A' + n / 26) + ('A' + n % 26)
            """"$code": {${sources.joinToString(",") { entry(it, "", urls.getValue(it)) }}}"""
        }
        return withGenerated("2026-11-24T00:00:00Z", "{$body}")
    }

    private val threeSources = mapOf("US" to 200, "UK" to 200, "DE" to 200)

    @Test
    fun `a file in which one whole source has no entries is rejected`() {
        val noDe = sourced(200, "US", "UK")
        assertEquals(AdvisoryRejection.IMPLAUSIBLE, verifyAdvisory(noDe, null, now, 200, 1, threeSources))
        assertNull(verifyAdvisory(sourced(200, "US", "UK", "DE"), null, now, 200, 1, threeSources))
    }

    @Test
    fun `a source that keeps half of its entries passes and one below half is rejected`() {
        val half = withGenerated("2026-11-24T00:00:00Z", "{" + (1..200).joinToString(",") { n ->
            val code = "" + ('A' + n / 26) + ('A' + n % 26)
            """"$code": {${entry("US")}${if (n <= 100) "," + entry("UK", "", "https://www.gov.uk/x") else ""}}"""
        } + "}")
        val inUse = mapOf("US" to 200, "UK" to 200)
        assertNull(verifyAdvisory(half, null, now, 200, 1, inUse))
        assertEquals(AdvisoryRejection.IMPLAUSIBLE, verifyAdvisory(half, null, now, 200, 1, mapOf("US" to 200, "UK" to 201)))
    }

    @Test
    fun `a small source is not guarded so a short list can shrink`() {
        assertNull(verifyAdvisory(sourced(200, "US", "UK"), null, now, 200, 1, mapOf("US" to 200, "UK" to 200, "DE" to 99)))
    }

    @Test
    fun `a run rejects a source collapse against the stored copy and leaves it alone`() {
        val full = sourced(200, "US", "UK", "DE").toString(Charsets.UTF_8).replace("2026-11-24", "2026-11-01").toByteArray()
        val store = FakeStore(bytes = full)
        assertEquals(AdvisoryOutcome.REJECTED, runAdvisoryUpdate(FakeFetcher(body = sourced(200, "US", "UK")), store, { null }, { now }, minCountries = 1))
        assertArrayEquals(full, store.bytes)
    }

    // --- format bump and 404 --------------------------------------------------------------

    private fun format(n: Int) = """{"dataFormat": $n, "generatedAt": "2026-11-24T00:00:00Z", "sources": {}, "countries": {}}""".toByteArray()

    @Test
    fun `a download with a newer dataFormat is its own calm outcome and leaves the stored copy untouched`() {
        val store = FakeStore(bytes = fixtureBytes)
        assertEquals(AdvisoryRejection.NEWER_FORMAT, verifyAdvisory(format(2), null, now))
        assertEquals(AdvisoryOutcome.NEEDS_NEWER_APP, runAdvisoryUpdate(FakeFetcher(body = format(2)), store, { null }, { now }, minCountries = 1))
        assertArrayEquals(fixtureBytes, store.bytes)
    }

    @Test
    fun `an older or missing dataFormat and plain garbage stay a rejection`() {
        assertEquals(AdvisoryRejection.UNPARSEABLE, verifyAdvisory(format(0), null, now))
        assertEquals(AdvisoryRejection.UNPARSEABLE, verifyAdvisory("<html>".toByteArray(), null, now))
    }

    @Test
    fun `a stored copy in a newer dataFormat is ignored and the bundled asset is used`() = runBlocking {
        val repo = AdvisoryRepository({ fixtureBytes }, FakeStore(bytes = format(2)), { now })
        assertEquals(data, repo.current())
    }

    @Test
    fun `a 404 means nothing newer and is not a failure`() {
        val store = FakeStore(bytes = fixtureBytes)
        assertEquals(AdvisoryOutcome.UP_TO_DATE, runAdvisoryUpdate(FakeFetcher(fail = VisaFetchNotFound("HTTP 404")), store, { null }, { now }, minCountries = 1))
        assertArrayEquals(fixtureBytes, store.bytes)
    }

    @Test
    fun `http statuses map to not found, rejected or retryable`() {
        assertTrue(failureForStatus(404) is VisaFetchNotFound)
        assertTrue(failureForStatus(403) is VisaFetchRejected && failureForStatus(403) !is VisaFetchNotFound)
        for (retry in listOf(408, 429, 500, 503)) assertTrue("$retry", failureForStatus(retry) is IOException)
    }

    @Test
    fun `the worker retries only when offline`() {
        assertTrue(shouldRetryAdvisory(AdvisoryOutcome.OFFLINE))
        for (o in AdvisoryOutcome.values().filter { it != AdvisoryOutcome.OFFLINE }) assertFalse("$o", shouldRetryAdvisory(o))
    }

    @Test
    fun `a stored copy dated exactly like the bundled one loses to it`() {
        val same = parseAdvisory(fixture)!!.copy(countries = emptyMap())
        assertEquals(data, chooseAdvisory(data, same, now))
    }

    @Test
    fun `a garbage stored file is replaced by the next newer valid download`() {
        val store = FakeStore(bytes = "garbage".toByteArray())
        assertEquals(AdvisoryOutcome.UPDATED, runAdvisoryUpdate(FakeFetcher(body = fixtureBytes), store, { null }, { now }, minCountries = 1))
        assertArrayEquals(fixtureBytes, store.bytes)
    }

    // --- FileAdvisoryStore ---------------------------------------------------------------------

    private fun tempDir() = kotlin.io.path.createTempDirectory("advisory").toFile()

    @Test
    fun `the file store round-trips bytes and reads null before the first write`() {
        val dir = File(tempDir(), "nested")
        try {
            val s = FileAdvisoryStore(dir)
            assertNull(s.read())
            s.write(fixtureBytes)
            assertArrayEquals(fixtureBytes, FileAdvisoryStore(dir).read())
            s.write("two".toByteArray())
            assertArrayEquals("two".toByteArray(), s.read())
            assertEquals(listOf("advisory.json"), dir.list()!!.toList())
        } finally { dir.parentFile.deleteRecursively() }
    }

    @Test
    fun `a corrupt file in the store reads as bytes that parse to nothing so the bundled copy wins`() = runBlocking {
        val dir = tempDir()
        try {
            File(dir, ADVISORY_FILE).writeText("{not json")
            assertEquals(data, AdvisoryRepository({ fixtureBytes }, FileAdvisoryStore(dir), { now }).current())
        } finally { dir.deleteRecursively() }
    }

    @Test
    fun `a write clears temp files left by a process that died mid-write`() {
        val dir = tempDir()
        try {
            File(dir, "advisory-123.tmp").writeText("half")
            File(dir, "other.txt").writeText("keep")
            FileAdvisoryStore(dir).write(fixtureBytes)
            assertEquals(setOf("advisory.json", "other.txt"), dir.list()!!.toSet())
        } finally { dir.deleteRecursively() }
    }

    // --- bundled asset ---------------------------------------------------------------------

    @Test
    fun `the bundled advisory asset parses and keeps every country it lists`() {
        val f = File("src/main/assets/advisory.json")
        Assume.assumeTrue("asset not on this branch", f.isFile)
        val text = f.readText()
        val parsed = parseAdvisory(text)
        assertNotNull(parsed)
        assertEquals(org.json.JSONObject(text).getJSONObject("countries").length(), parsed!!.countries.size)
        assertTrue(parsed.countries.size >= MIN_ADVISORY_COUNTRIES)
        // No single entry lost either (a url on a host outside the allow-list would be dropped quietly).
        val raw = org.json.JSONObject(text).getJSONObject("countries")
        val rawEntries = raw.keys().asSequence().sumOf { raw.getJSONObject(it).length() }
        assertEquals(rawEntries, parsed.countries.values.sumOf { it.size })
    }
}
