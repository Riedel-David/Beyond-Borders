package com.riedel.beyondborders.ui

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

/** Parses the real resource files (module dir is the CWD), so a missed or broken German string fails here rather than at runtime. */
class StringParityTest {

    private class Entry(val key: String, val items: Map<String, String>, val plural: Boolean)

    private fun load(dir: String): Map<String, Entry> {
        val files = File("src/main/res/$dir").listFiles { f -> f.name.matches(Regex("strings.*\\.xml")) }
            ?: error("no $dir directory")
        val out = LinkedHashMap<String, Entry>()
        for (f in files.sortedBy { it.name }) {
            val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(f)
            val nodes = doc.documentElement.childNodes
            for (i in 0 until nodes.length) {
                val n = nodes.item(i) as? Element ?: continue
                if (n.getAttribute("translatable") == "false") continue
                val name = n.getAttribute("name")
                when (n.tagName) {
                    "string" -> out[name] = Entry(name, mapOf("" to n.textContent), false)
                    "plurals" -> {
                        val items = LinkedHashMap<String, String>()
                        val kids = n.getElementsByTagName("item")
                        for (j in 0 until kids.length) {
                            val it = kids.item(j) as Element
                            items[it.getAttribute("quantity")] = it.textContent
                        }
                        out[name] = Entry(name, items, true)
                    }
                }
            }
        }
        return out
    }

    private val en = load("values")
    private val de = load("values-de")

    private fun placeholders(s: String): List<String> =
        Regex("%(\\d+\\$)?[sd]").findAll(s).map { it.value }.sorted().toList()

    /** Words that are legitimately the same in both languages (brand names, symbols, loanwords). */
    private val sameInGerman = setOf(
        "app_name", "widget_wordmark", "welcome_title", "settings_licence_app", // brand
        "settings_denominator_un_bare", "settings_denominator_un", // "UN" / "%1$d UN"
        "passport_group_alphabetical", // "A-Z"
        "settings_theme_system", "settings_base_sand", "rank_globetrotter", "rank_wanderer",
        "widget_cfg_background_transparent", // same word in German
        "settings_ok", "info_button_ok", // "OK"
        "stats_hero_percent", "badge_rank_line", "share_stamp", // pure format strings
        "visa_legend_item", "visa_dataset_credit", "visa_summary_with_days", "map_legend_count_labeled", "map_legend_chip",
        "map_join_comma", "backup_sentence_join", "settings_swatch_description", "settings_preview_caveat",
        "settings_about_version", // product name + version
        "geo_group_benelux", "geo_group_maghreb", "geo_group_indochina", // loanwords, same in German
    )

    @Test
    fun `every translatable key exists in both languages`() {
        val missingDe = en.keys - de.keys
        val extraDe = de.keys - en.keys
        assertTrue("missing in values-de: $missingDe", missingDe.isEmpty())
        assertTrue("only in values-de: $extraDe", extraDe.isEmpty())
    }

    @Test
    fun `placeholders match per key and per plural item`() {
        val bad = mutableListOf<String>()
        for ((k, e) in en) {
            val g = de[k] ?: continue
            if (e.plural != g.plural) { bad += "$k (kind differs)"; continue }
            val enAll = e.items.values.flatMap(::placeholders).toSet()
            for ((q, text) in g.items) {
                // German "one" is checked against English "one", "other" against "other".
                val src = e.items[q] ?: e.items["other"] ?: ""
                if (placeholders(text) != placeholders(src)) bad += "$k[$q]: ${placeholders(src)} vs ${placeholders(text)}"
            }
            if (enAll.isEmpty() && g.items.values.any { placeholders(it).isNotEmpty() }) bad += k
        }
        assertTrue("placeholder mismatch: $bad", bad.isEmpty())
    }

    @Test
    fun `plurals carry the one and other quantities in both languages`() {
        for (map in listOf(en, de)) for (e in map.values.filter { it.plural }) {
            assertTrue("${e.key} lacks one/other", e.items.keys.containsAll(listOf("one", "other")))
        }
        for ((k, e) in de.filter { it.value.plural }) {
            val extra = e.items.keys - setOf("one", "other")
            assertTrue("$k has quantities German does not use: $extra", extra.isEmpty())
        }
    }

    @Test
    fun `no german string is empty`() {
        val empty = de.values.flatMap { e -> e.items.filter { it.value.isBlank() }.map { e.key } }
        assertTrue("empty German strings: $empty", empty.isEmpty())
    }

    @Test
    fun `no german string is identical to english outside the allow list`() {
        val same = mutableListOf<String>()
        for ((k, g) in de) {
            val e = en[k] ?: continue
            if (k in sameInGerman) continue
            if (e.items == g.items) same += k
        }
        assertTrue("untranslated (identical to English): $same", same.isEmpty())
    }

    @Test
    fun `no german string holds an unescaped apostrophe`() {
        // The DOM unescapes \', so read the raw text instead.
        val bad = mutableListOf<String>()
        File("src/main/res/values-de").listFiles()!!.filter { it.name.startsWith("strings") }.forEach { f ->
            f.readLines().forEachIndexed { i, line ->
                if (line.trimStart().startsWith("<!--")) return@forEachIndexed
                val body = line.substringAfter(">", "").substringBeforeLast("</")
                if (Regex("(?<!\\\\)'").containsMatchIn(body)) bad += "${f.name}:${i + 1}"
            }
        }
        assertTrue("unescaped apostrophes: $bad", bad.isEmpty())
    }

    @Test
    fun `placeholder counting sees positional and plain forms`() {
        assertEquals(listOf("%1\$d", "%2\$s"), placeholders("%2\$s of %1\$d"))
    }
}
