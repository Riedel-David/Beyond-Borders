package com.riedel.beyondborders.ui

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The strings the advisory feature owns must exist in every locale with the same placeholders. */
class AdvisoryResourcesTest {
    private val res = File("src/main/res")
    private val placeholder = Regex("%(\\d+\\$)?[sd]")

    private fun strings(file: File, keep: (String) -> Boolean): Map<String, String> {
        if (!file.isFile) return emptyMap()
        val nodes = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file).getElementsByTagName("string")
        return (0 until nodes.length).map { nodes.item(it) }
            .associate { it.attributes.getNamedItem("name").nodeValue to it.textContent }
            .filterKeys(keep)
    }

    private fun locales() = res.listFiles { f -> f.isDirectory && f.name.startsWith("values-") }!!.sortedBy { it.name }

    private fun check(fileName: String, keep: (String) -> Boolean) {
        val base = strings(File(res, "values/$fileName"), keep)
        assertTrue("no base keys in $fileName", base.isNotEmpty())
        for (dir in locales()) {
            val other = strings(File(dir, fileName), keep)
            if (other.isEmpty() && !File(dir, fileName).isFile) continue
            assertEquals("${dir.name}/$fileName key set", base.keys, other.keys)
            for ((k, v) in base) {
                assertEquals("${dir.name}/$fileName $k placeholders",
                    placeholder.findAll(v).map { it.value }.sorted().toList(),
                    placeholder.findAll(other.getValue(k)).map { it.value }.sorted().toList())
            }
        }
    }

    @Test
    fun `every locale has exactly the advisory strings of the default with the same placeholders`() {
        check("strings_advisory.xml") { true }
    }

    @Test
    fun `every locale has the advisory settings strings of the default with the same placeholders`() {
        check("strings_settings.xml") { it.contains("advisory") }
    }

    @Test
    fun `all eight shipped locales carry advisory strings`() {
        assertEquals(setOf("values-de", "values-es", "values-fr", "values-pt", "values-pt-rPT", "values-ru", "values-uk"),
            locales().filter { File(it, "strings_advisory.xml").isFile }.map { it.name }.toSet())
    }
}
