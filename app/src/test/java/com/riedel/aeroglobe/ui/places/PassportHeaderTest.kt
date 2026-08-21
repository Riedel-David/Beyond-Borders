package com.riedel.aeroglobe.ui.places

import com.riedel.aeroglobe.data.place.PlaceRegistry
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The passport list's order and section headers, over the *shipped* registry.
 *
 * Both properties are of places.json as much as of the code, which is why this test
 * parses the real asset rather than fabricating places like [PlacesLogicTest] does.
 * Fabricated places would have proved nothing about either bug: that Åland Islands
 * sorted after Zimbabwe, and that all seven uninhabited names start with a letter some
 * real country already uses.
 */
class PassportHeaderTest {

    private val places = PlaceRegistry
        .parse(File("src/main/assets/${PlaceRegistry.ASSET_NAME}").readText())
        .places

    /** Only header *changes* become list items, so a run of equal headers is one item. */
    private fun List<String>.collapseRuns(): List<String> =
        filterIndexed { index, value -> index == 0 || value != this[index - 1] }

    /**
     * A LazyColumn key has to be unique among the items composed at the same time, and
     * every item here is keyed on its header text. A repeat throws
     * `Key header-S was used multiple times`.
     *
     * Filters and queries are part of the case rather than decoration: unfiltered, the
     * two "S" runs are 200 rows apart and never composed together, so the crash needed
     * a list short enough to show both at once — searching "south" in A-Z order was
     * five rows and two S headers.
     */
    @Test
    fun `no section header is ever emitted twice in one list`() {
        val queries = listOf("", "south", "island", "u", "a", "new", "saint", "territor")
        Grouping.entries.forEach { grouping ->
            StatusFilter.entries.forEach { filter ->
                queries.forEach { query ->
                    val headers = buildRows(places, emptyMap(), query, filter, grouping)
                        .map { sectionHeader(it.place, grouping) }
                        .collapseRuns()
                    assertEquals(
                        "$grouping / $filter / \"$query\" repeats a header: $headers",
                        headers.distinct(),
                        headers,
                    )
                }
            }
        }
    }

    @Test
    fun `the A-Z list is ordered by the unaccented name`() {
        val rows = buildRows(places, emptyMap(), "", StatusFilter.ALL, Grouping.ALPHABETICAL)
        val keys = rows.filterNot { it.place.uninhabited }.map { it.place.sortKey }
        assertEquals(keys.sorted(), keys)
    }

    /**
     * The five that UTF-16 code-unit order put past Z, named individually: a sorted-ness
     * assertion alone would still pass if [com.riedel.aeroglobe.data.place.Place.sortKey]
     * stopped folding, since the raw names are self-consistently sorted too — just in
     * the wrong places.
     */
    @Test
    fun `an accented name sits between its real neighbours`() {
        val names = buildRows(places, emptyMap(), "", StatusFilter.ALL, Grouping.ALPHABETICAL)
            .map { it.place.name }
        listOf(
            Triple("Afghanistan", "Åland Islands", "Albania"),
            Triple("Costa Rica", "Côte d'Ivoire", "Croatia"),
            Triple("Republic of the Congo", "Réunion", "Romania"),
            Triple("San Marino", "São Tomé and Príncipe", "Saudi Arabia"),
            Triple("Tunisia", "Türkiye", "Turkmenistan"),
        ).forEach { (before, name, after) ->
            val index = names.indexOf(name)
            assertTrue("$name is not in the A-Z list", index > 0)
            assertEquals(
                "wrong neighbours for $name",
                listOf(before, name, after),
                names.subList(index - 1, index + 2),
            )
        }
    }

    /**
     * The uninhabited block is one section, not a second alphabet — and it is still
     * last, which is the point of it being a block at all.
     */
    @Test
    fun `uninhabited places share a single header at the end of the A-Z list`() {
        val rows = buildRows(places, emptyMap(), "", StatusFilter.ALL, Grouping.ALPHABETICAL)
        val headers = rows.map { sectionHeader(it.place, Grouping.ALPHABETICAL) }.collapseRuns()

        assertEquals(UNINHABITED_HEADER, headers.last())
        assertEquals(1, headers.count { it == UNINHABITED_HEADER })
        assertEquals(
            rows.count { it.place.uninhabited },
            rows.takeLastWhile { it.place.uninhabited }.size,
        )
    }

    /**
     * Continent grouping never needed the split: the uninhabited places sort last
     * inside their own continent, so each header is one contiguous run already.
     */
    @Test
    fun `continent grouping files every place under its continent`() {
        places.forEach { place ->
            assertEquals(place.continent, sectionHeader(place, Grouping.CONTINENT))
        }
    }
}
