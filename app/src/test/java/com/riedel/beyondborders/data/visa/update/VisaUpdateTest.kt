package com.riedel.beyondborders.data.visa.update

import com.riedel.beyondborders.data.visa.VisaRegistry
import java.io.File
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VisaUpdateTest {

    private val TODAY = "2026-11-15"
    private val noBundled: (String) -> String? = { null }
    private val oldBundle: (String) -> String? = { "2026-01-01" }

    private fun probe(): String = File("src/main/assets/visa.json").readText()

    private fun json(passport: String, asOf: String): ByteArray {
        val root = org.json.JSONObject(probe())
        root.put("passport", passport)
        root.put("asOf", asOf)
        return root.toString().toByteArray()
    }

    private fun entryFor(bytes: ByteArray, code: String, asOf: String) =
        ManifestEntry("visa_${code.lowercase()}.json", sha256Hex(bytes), bytes.size, asOf)

    private fun manifestJson(
        entries: Map<String, ManifestEntry>,
        manifestVersion: Int = 1,
        dataFormat: Int = 1,
    ): String {
        val p = org.json.JSONObject()
        entries.forEach { (c, e) ->
            p.put(c, org.json.JSONObject().put("file", e.file).put("sha256", e.sha256).put("bytes", e.bytes).put("asOf", e.asOf))
        }
        return org.json.JSONObject().put("manifestVersion", manifestVersion).put("dataFormat", dataFormat)
            .put("generatedAt", "x").put("passports", p).toString()
    }

    // --- parseManifest ---------------------------------------------------------

    @Test
    fun `a well-formed manifest parses into upper-case codes`() {
        val b = json("FR", "2026-11-11")
        val m = parseManifest(manifestJson(mapOf("FR" to entryFor(b, "FR", "2026-11-11"))), TODAY)!!
        assertEquals(setOf("FR"), m.passports.keys)
        assertEquals(b.size, m.passports.getValue("FR").bytes)
    }

    @Test
    fun `an unknown manifestVersion is ignored whole`() {
        assertNull(parseManifest(manifestJson(emptyMap(), manifestVersion = 2), TODAY))
    }

    @Test
    fun `an unknown dataFormat is ignored whole`() {
        assertNull(parseManifest(manifestJson(emptyMap(), dataFormat = 2), TODAY))
    }

    @Test
    fun `garbage is not a manifest`() {
        assertNull(parseManifest("<html>", TODAY))
        assertNull(parseManifest("""{"manifestVersion":1,"dataFormat":1}""", TODAY))
    }

    @Test
    fun `an entry whose file name is not visa_code_json is skipped`() {
        val b = json("FR", "2026-11-11")
        val bad = entryFor(b, "FR", "2026-11-11").copy(file = "../x.json")
        val m = parseManifest(manifestJson(mapOf("FR" to bad)), TODAY)!!
        assertTrue(m.passports.isEmpty())
    }

    // --- planFetches -----------------------------------------------------------

    private val manifest = Manifest(
        mapOf(
            "DE" to ManifestEntry("visa_de.json", "a", 1, "2026-11-11"),
            "FR" to ManifestEntry("visa_fr.json", "b", 1, "2026-11-11"),
        ),
    )

    @Test
    fun `wanted passports in the manifest are fetched and others skipped`() {
        val plan = planFetches(manifest, setOf("DE", "ZZ"), emptyMap(), oldBundle, TODAY)
        assertEquals(listOf("DE"), plan.fetch)
        assertTrue(plan.delete.isEmpty())
    }

    @Test
    fun `a stored passport missing from the manifest is deleted`() {
        val plan = planFetches(manifest, setOf("DE"), mapOf("US" to "2026-01-01"), oldBundle, TODAY)
        assertEquals(listOf("US"), plan.delete)
        assertFalse("US" in plan.fetch)
    }

    @Test
    fun `a stored passport is refreshed even when no longer wanted`() {
        val plan = planFetches(manifest, emptySet(), mapOf("FR" to "2026-01-01"), oldBundle, TODAY)
        assertEquals(listOf("FR"), plan.fetch)
    }

    @Test
    fun `a stored copy that is already current is not fetched again`() {
        val plan = planFetches(manifest, setOf("FR"), mapOf("FR" to "2026-11-11"), oldBundle, TODAY)
        assertTrue(plan.fetch.isEmpty())
    }

    @Test
    fun `an unparseable stored copy is fetched again`() {
        val plan = planFetches(manifest, emptySet(), mapOf("FR" to null), oldBundle, TODAY)
        assertEquals(listOf("FR"), plan.fetch)
    }

    // --- verifyDownload --------------------------------------------------------

    @Test
    fun `a good download is accepted`() {
        val b = json("FR", "2026-11-11")
        assertNull(verifyDownload(b, entryFor(b, "FR", "2026-11-11"), "FR", "2026-01-01", TODAY))
    }

    @Test
    fun `a download over the size limit is rejected`() {
        val b = ByteArray(MAX_FILE_BYTES + 1) { ' '.code.toByte() }
        assertEquals(Rejection.TOO_LARGE, verifyDownload(b, entryFor(b, "FR", "x"), "FR", null, TODAY))
    }

    @Test
    fun `a download whose length differs from the manifest is rejected`() {
        val b = json("FR", "2026-11-11")
        assertEquals(Rejection.WRONG_LENGTH, verifyDownload(b, entryFor(b, "FR", "2026-11-11").copy(bytes = b.size + 1), "FR", null, TODAY))
    }

    @Test
    fun `a sha256 mismatch is rejected`() {
        val b = json("FR", "2026-11-11")
        assertEquals(Rejection.WRONG_HASH, verifyDownload(b, entryFor(b, "FR", "2026-11-11").copy(sha256 = "0".repeat(64)), "FR", null, TODAY))
    }

    @Test
    fun `a file that does not parse is rejected`() {
        val b = "not json".toByteArray()
        assertEquals(Rejection.UNPARSEABLE, verifyDownload(b, entryFor(b, "FR", "2026-11-11"), "FR", null, TODAY))
    }

    @Test
    fun `a file for a different passport is rejected`() {
        val b = json("US", "2026-11-11")
        assertEquals(Rejection.WRONG_PASSPORT, verifyDownload(b, entryFor(b, "FR", "2026-11-11"), "FR", null, TODAY))
    }

    @Test
    fun `a file older than the copy in use is rejected but an equal one is not`() {
        val b = json("FR", "2026-05-01")
        assertEquals(Rejection.OLDER_THAN_IN_USE, verifyDownload(b, entryFor(b, "FR", "2026-05-01"), "FR", "2026-06-01", TODAY))
        assertNull(verifyDownload(b, entryFor(b, "FR", "2026-05-01"), "FR", "2026-05-01", TODAY))
    }

    // --- shouldUse / preferStored ---------------------------------------------

    @Test
    fun `a stored copy older or equal to the bundled one is not used`() {
        assertFalse(shouldUse("2026-01-01", "2026-06-01"))
        assertFalse(shouldUse("2026-06-01", "2026-06-01"))
        assertTrue(shouldUse("2026-06-02", "2026-06-01"))
    }

    private fun reg(code: String, asOf: String) = VisaRegistry.parse(String(json(code, asOf)))

    @Test
    fun `preferStored picks a newer stored copy and otherwise the bundled one`() {
        val bundled = reg("FR", "2026-01-01")
        assertEquals("2026-06-01", preferStored(bundled, { TODAY }) { reg("FR", "2026-06-01") }.asOf)
        assertEquals("2026-01-01", preferStored(bundled, { TODAY }) { reg("FR", "2026-01-01") }.asOf)
        assertEquals("2026-01-01", preferStored(bundled, { TODAY }) { null }.asOf)
        assertEquals("2026-01-01", preferStored(bundled, { TODAY }) { reg("US", "2027-01-01") }.asOf)
    }

    @Test
    fun `preferStored falls back to bundled when reading the stored copy throws`() {
        val bundled = reg("FR", "2026-01-01")
        assertEquals("2026-01-01", preferStored(bundled, { TODAY }) { throw IOException("boom") }.asOf)
    }

    @Test
    fun `only https urls are accepted`() {
        assertTrue(isHttps(UPDATE_BASE_URL))
        assertFalse(isHttps("http://riedel-david.github.io/x"))
    }

    // --- runUpdate end to end with fakes --------------------------------------

    private class FakeStore(initial: Map<String, ByteArray> = emptyMap()) : VisaStore {
        val files = initial.toMutableMap()
        override fun codes() = files.keys.toSet()
        override fun read(code: String) = files[code]
        override fun write(code: String, bytes: ByteArray) { files[code] = bytes }
        override fun delete(code: String) { files.remove(code) }
    }

    private class FakeFetcher(val files: Map<String, ByteArray>, val failing: Set<String> = emptySet()) : VisaFetcher {
        override fun fetch(file: String, maxBytes: Int): ByteArray {
            if (file in failing) throw IOException("offline")
            return files[file] ?: throw IOException("404")
        }
    }

    private fun site(vararg passports: Pair<String, String>): Map<String, ByteArray> {
        val files = HashMap<String, ByteArray>()
        val entries = HashMap<String, ManifestEntry>()
        for ((code, asOf) in passports) {
            val b = json(code, asOf)
            files["visa_${code.lowercase()}.json"] = b
            entries[code] = entryFor(b, code, asOf)
        }
        files[MANIFEST_FILE] = manifestJson(entries).toByteArray()
        return files
    }

    @Test
    fun `an update stores a newer wanted passport and reports it`() {
        val store = FakeStore()
        val r = runUpdate(FakeFetcher(site("FR" to "2026-11-11")), store, setOf("FR"), { "2026-01-01" }, { TODAY })
        assertEquals(listOf("FR"), r.applied)
        assertEquals("2026-11-11", r.appliedAsOf)
        assertTrue(r.manifestSeen)
        assertEquals(setOf("FR"), store.codes())
    }

    @Test
    fun `an update older than the bundled asset is not stored`() {
        val store = FakeStore()
        val r = runUpdate(FakeFetcher(site("FR" to "2026-01-01")), store, setOf("FR"), { "2026-06-01" }, { TODAY })
        assertTrue(r.applied.isEmpty())
        assertTrue(store.codes().isEmpty())
    }

    @Test
    fun `a dropped passport is deleted only when the manifest was understood`() {
        val store = FakeStore(mapOf("US" to json("US", "2026-01-01")))
        val r = runUpdate(FakeFetcher(site("FR" to "2026-11-11")), store, emptySet(), { null }, { TODAY })
        assertEquals(listOf("US"), r.deleted)
        assertFalse("US" in store.codes())
    }

    @Test
    fun `a failed manifest fetch deletes nothing and asks for a retry`() {
        val store = FakeStore(mapOf("US" to json("US", "2026-01-01")))
        val r = runUpdate(FakeFetcher(emptyMap()), store, setOf("US"), { null }, { TODAY })
        assertTrue(r.ioFailure)
        assertFalse(r.manifestSeen)
        assertEquals(setOf("US"), store.codes())
    }

    @Test
    fun `an unrecognised manifest version deletes nothing and does not retry`() {
        val store = FakeStore(mapOf("US" to json("US", "2026-01-01")))
        val files = mapOf(MANIFEST_FILE to manifestJson(emptyMap(), manifestVersion = 9).toByteArray())
        val r = runUpdate(FakeFetcher(files), store, setOf("US"), { null }, { TODAY })
        assertFalse(r.ioFailure)
        assertFalse(r.manifestSeen)
        assertEquals(setOf("US"), store.codes())
    }

    @Test
    fun `a corrupted download leaves the stored copy untouched`() {
        val files = site("FR" to "2026-11-11").toMutableMap()
        files["visa_fr.json"] = json("FR", "2026-11-11").also { it[0] = ' '.code.toByte() }
        val old = json("FR", "2026-01-01")
        val store = FakeStore(mapOf("FR" to old))
        val r = runUpdate(FakeFetcher(files), store, setOf("FR"), { "2025-01-01" }, { TODAY })
        assertTrue(r.applied.isEmpty())
        assertTrue(store.read("FR")!!.contentEquals(old))
    }

    @Test
    fun `a failed file fetch is reported as a retry but other passports still apply`() {
        val store = FakeStore()
        val r = runUpdate(FakeFetcher(site("FR" to "2026-11-11", "US" to "2026-11-11"), failing = setOf("visa_fr.json")), store, setOf("FR", "US"), oldBundle, { TODAY })
        assertTrue(r.ioFailure)
        assertEquals(listOf("US"), r.applied)
    }

    // --- review fixes ------------------------------------------------------------

    @Test
    fun `a future-dated manifest entry is skipped but its stored copy is kept`() {
        val b = json("FR", "2030-01-01")
        val m = parseManifest(manifestJson(mapOf("FR" to entryFor(b, "FR", "2030-01-01"))), TODAY)!!
        assertTrue(m.passports.isEmpty())
        assertEquals(setOf("FR"), m.listed)
        val plan = planFetches(m, setOf("FR"), mapOf("FR" to "2026-11-01"), oldBundle, TODAY)
        assertTrue(plan.fetch.isEmpty())
        assertTrue(plan.delete.isEmpty())
    }

    @Test
    fun `a future-dated file is rejected`() {
        val b = json("FR", "2030-01-01")
        assertEquals(Rejection.FUTURE_DATED, verifyDownload(b, entryFor(b, "FR", "2030-01-01"), "FR", null, TODAY))
        val tomorrow = json("FR", "2026-11-16")
        assertNull(verifyDownload(tomorrow, entryFor(tomorrow, "FR", "2026-11-16"), "FR", null, TODAY))
    }

    @Test
    fun `a future-dated stored copy is not used`() {
        val bundled = reg("FR", "2026-01-01")
        assertEquals("2026-01-01", preferStored(bundled, { TODAY }) { reg("FR", "2030-01-01") }.asOf)
    }

    @Test
    fun `a file whose own asOf differs from the manifest is rejected`() {
        val b = json("FR", "2026-11-11")
        assertEquals(Rejection.ASOF_MISMATCH, verifyDownload(b, entryFor(b, "FR", "2026-11-12"), "FR", null, TODAY))
    }

    @Test
    fun `a bundle newer than the manifest triggers no download`() {
        val plan = planFetches(manifest, setOf("FR"), emptyMap(), { "2026-11-12" }, TODAY)
        assertTrue(plan.fetch.isEmpty())
    }

    @Test
    fun `a stored copy not newer than the bundled one is deleted`() {
        val plan = planFetches(manifest, emptySet(), mapOf("FR" to "2026-01-01"), { "2026-02-01" }, TODAY)
        assertEquals(listOf("FR"), plan.delete)
    }

    @Test
    fun `a malformed manifest entry keeps the stored copy`() {
        val raw = """{"manifestVersion":1,"dataFormat":1,"passports":{"FR":{"file":"nope"}}}"""
        val m = parseManifest(raw, TODAY)!!
        val plan = planFetches(m, setOf("FR"), mapOf("FR" to "2026-11-01"), oldBundle, TODAY)
        assertTrue(plan.delete.isEmpty())
        assertTrue(plan.fetch.isEmpty())
    }

    @Test
    fun `a 404 on the manifest is not a retry and deletes nothing`() {
        val store = FakeStore(mapOf("US" to json("US", "2026-01-01")))
        val rejecting = object : VisaFetcher {
            override fun fetch(file: String, maxBytes: Int): ByteArray = throw VisaFetchRejected("HTTP 404")
        }
        val r = runUpdate(rejecting, store, setOf("US"), { null }, { TODAY })
        assertFalse(r.ioFailure)
        assertFalse(r.manifestSeen)
        assertEquals(setOf("US"), store.codes())
    }

    @Test
    fun `concurrent writes through the file store leave a whole file and no temp files`() {
        val dir = kotlin.io.path.createTempDirectory("visa").toFile()
        try {
            val s = FileVisaStore(dir)
            val threads = (1..8).map { n -> Thread { repeat(20) { s.write("FR", ByteArray(1000) { n.toByte() }) } } }
            threads.forEach { it.start() }; threads.forEach { it.join() }
            val bytes = s.read("FR")!!
            assertEquals(1000, bytes.size)
            assertTrue(bytes.all { it == bytes[0] })
            assertEquals(listOf("FR.json"), dir.list()!!.toList())
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `408 429 and 5xx are retryable while other 4xx are final`() {
        listOf(408, 429, 500, 503).forEach { assertTrue(it.toString(), isRetryableStatus(it)) }
        listOf(400, 403, 404, 410).forEach { assertFalse(it.toString(), isRetryableStatus(it)) }
    }

    @Test
    fun `a passport with no bundled asset is neither fetched nor kept`() {
        val plan = planFetches(manifest, setOf("FR"), mapOf("DE" to "2026-11-01"), { if (it == "DE") "2026-01-01" else null }, TODAY)
        assertFalse("FR" in plan.fetch)
        val orphan = planFetches(manifest, emptySet(), mapOf("FR" to "2026-11-01"), noBundled, TODAY)
        assertEquals(listOf("FR"), orphan.delete)
    }

    @Test
    fun `a failing disk write is skipped and does not ask for a retry`() {
        val failing = object : VisaStore {
            override fun codes() = emptySet<String>()
            override fun read(code: String): ByteArray? = null
            override fun write(code: String, bytes: ByteArray) = throw IOException("disk full")
            override fun delete(code: String) = Unit
        }
        val r = runUpdate(FakeFetcher(site("FR" to "2026-11-11", "US" to "2026-11-11")), failing, setOf("FR", "US"), oldBundle, { TODAY })
        assertFalse(r.ioFailure)
        assertTrue(r.manifestSeen)
        assertTrue(r.applied.isEmpty())
    }

    // --- FileVisaStore ---------------------------------------------------------

    @Test
    fun `the file store round-trips and deletes and ignores stray files`() {
        val dir = kotlin.io.path.createTempDirectory("visa").toFile()
        try {
            val s = FileVisaStore(File(dir, "visa"))
            assertTrue(s.codes().isEmpty())
            s.write("FR", "a".toByteArray())
            s.write("FR", "b".toByteArray())
            File(dir, "visa/junk.txt").writeText("x")
            assertEquals(setOf("FR"), s.codes())
            assertEquals("b", String(s.read("FR")!!))
            s.delete("FR")
            assertNull(s.read("FR"))
        } finally {
            dir.deleteRecursively()
        }
    }
}
