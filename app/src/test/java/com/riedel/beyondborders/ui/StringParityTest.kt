package com.riedel.beyondborders.ui

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

/** Parses the real resource files (module dir is the CWD), so a missed or broken translation fails here rather than at runtime. */
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

    /**
     * Adding a language is one entry here (plus its [sameAsEnglish] set) and a values-xx
     * directory; every check below runs per tag.
     */
    private val translations: Map<String, Map<String, Entry>> = listOf("de", "es").associateWith { load("values-$it") }

    /** Quantities beyond one/other that a language's CLDR rules use; lint demands them only when the app needs them. */
    private val extraQuantities = mapOf("de" to emptySet<String>(), "es" to setOf("many"))

    private fun placeholders(s: String): List<String> =
        Regex("%(\\d+\\$)?[sd]").findAll(s).map { it.value }.sorted().toList()

    /** Keys that are legitimately the same as English in every language (brand names, symbols, pure format strings). */
    private val sameEverywhere = setOf(
        "app_name", "widget_wordmark", "welcome_title", "settings_licence_app", // brand
        "passport_group_alphabetical", // "A-Z"
        "settings_ok", "info_button_ok", // "OK"
        "stats_hero_percent", "badge_rank_line", "share_stamp", // pure format strings
        "visa_legend_item", "visa_dataset_credit", "visa_summary_with_days", "map_legend_count_labeled", "map_legend_chip",
        "map_join_comma", "backup_sentence_join", "settings_swatch_description", "settings_preview_caveat",
        "settings_about_version", // product name + version
    )

    /** Per-language additions: loanwords and cognates that happen to match English there. */
    private val sameInLanguage = mapOf(
        "de" to setOf(
            "settings_denominator_un_bare", "settings_denominator_un", // "UN" / "%1$d UN"
            "settings_theme_system", "settings_base_sand", "rank_globetrotter", "rank_wanderer",
            "widget_cfg_background_transparent",
            "geo_group_benelux", "geo_group_maghreb", "geo_group_indochina",
        ),
        "es" to setOf(
            "geo_group_benelux", "geo_group_indochina", // proper nouns
            "geo_continent_asia", "geo_region_melanesia", "geo_region_micronesia", // spelled the same in Spanish
            "visa_band_eta", "visa_category_e_visa", // "e-Ticket/ETA" and "eVisa" are the official labels
        ),
    )

    private fun forEachLanguage(check: (tag: String, tr: Map<String, Entry>) -> Unit) {
        for ((tag, tr) in translations) check(tag, tr)
    }

    @Test
    fun `every translatable key exists in every language`() = forEachLanguage { tag, tr ->
        val missing = en.keys - tr.keys
        val extra = tr.keys - en.keys
        assertTrue("[$tag] missing in values-$tag: $missing", missing.isEmpty())
        assertTrue("[$tag] only in values-$tag: $extra", extra.isEmpty())
    }

    @Test
    fun `placeholders match per key and per plural item`() = forEachLanguage { tag, tr ->
        val bad = mutableListOf<String>()
        for ((k, e) in en) {
            val g = tr[k] ?: continue
            if (e.plural != g.plural) { bad += "$k (kind differs)"; continue }
            val enAll = e.items.values.flatMap(::placeholders).toSet()
            for ((q, text) in g.items) {
                // A translated "one" is checked against English "one", everything else against "other".
                val src = e.items[q] ?: e.items["other"] ?: ""
                if (placeholders(text) != placeholders(src)) bad += "$k[$q]: ${placeholders(src)} vs ${placeholders(text)}"
            }
            if (enAll.isEmpty() && g.items.values.any { placeholders(it).isNotEmpty() }) bad += k
        }
        assertTrue("[$tag] placeholder mismatch: $bad", bad.isEmpty())
    }

    @Test
    fun `plurals carry one and other and only the extra quantities the language uses`() {
        for (e in en.values.filter { it.plural }) {
            assertTrue("en ${e.key} lacks one/other", e.items.keys.containsAll(listOf("one", "other")))
        }
        forEachLanguage { tag, tr ->
            val allowed = setOf("one", "other") + (extraQuantities[tag] ?: error("no extraQuantities entry for $tag"))
            for ((k, e) in tr.filter { it.value.plural }) {
                assertTrue("[$tag] $k lacks one/other", e.items.keys.containsAll(listOf("one", "other")))
                val extra = e.items.keys - allowed
                assertTrue("[$tag] $k has quantities the language does not use: $extra", extra.isEmpty())
            }
        }
    }

    @Test
    fun `no translated string is empty`() = forEachLanguage { tag, tr ->
        val empty = tr.values.flatMap { e -> e.items.filter { it.value.isBlank() }.map { e.key } }
        assertTrue("[$tag] empty strings: $empty", empty.isEmpty())
    }

    @Test
    fun `no translated string is identical to english outside the allow list`() = forEachLanguage { tag, tr ->
        val allowed = sameEverywhere + (sameInLanguage[tag] ?: error("no sameInLanguage entry for $tag"))
        val same = mutableListOf<String>()
        for ((k, g) in tr) {
            val e = en[k] ?: continue
            if (k in allowed) continue
            if (e.items == g.items) same += k
        }
        assertTrue("[$tag] untranslated (identical to English): $same", same.isEmpty())
    }

    @Test
    fun `no translated string holds an unescaped apostrophe`() = forEachLanguage { tag, _ ->
        // The DOM unescapes \', so read the raw text instead.
        val bad = mutableListOf<String>()
        File("src/main/res/values-$tag").listFiles()!!.filter { it.name.startsWith("strings") }.forEach { f ->
            f.readLines().forEachIndexed { i, line ->
                if (line.trimStart().startsWith("<!--")) return@forEachIndexed
                val body = line.substringAfter(">", "").substringBeforeLast("</")
                if (Regex("(?<!\\\\)'").containsMatchIn(body)) bad += "${f.name}:${i + 1}"
            }
        }
        assertTrue("[$tag] unescaped apostrophes: $bad", bad.isEmpty())
    }

    @Test
    fun `placeholder counting sees positional and plain forms`() {
        assertEquals(listOf("%1\$d", "%2\$s"), placeholders("%2\$s of %1\$d"))
    }
}
