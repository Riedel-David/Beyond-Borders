package com.riedel.beyondborders.ui.map

import com.riedel.beyondborders.R
import com.riedel.beyondborders.data.visa.VisaRegistry
import com.riedel.beyondborders.ui.UiText
import com.riedel.beyondborders.ui.places.TravelStats
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The collapsed travel and visa legends are colour dots plus bare numbers, which
 * TalkBack has nothing to say about on its own. These functions are the sentence
 * [Modifier.clearAndSetSemantics] hands it instead, so they are worth pinning down
 * on the host JVM rather than only in the composable.
 */
class MapLegendAccessibilityTest {

    private fun stats(visited: Int, lived: Int = 0, wishlist: Int = 0) = TravelStats(
        visited = visited,
        lived = lived,
        wishlist = wishlist,
        total = 252,
        continents = emptyList(),
    )

    @Test
    fun `travel legend names visited without mentioning bands that are not drawn`() {
        assertEquals(UiText.Res(R.string.map_legend_visited, 40), travelLegendDescription(stats(visited = 40)))
    }

    @Test
    fun `travel legend adds lived and wishlisted only once they are drawn`() {
        assertEquals(
            UiText.Res(
                R.string.map_join_comma,
                UiText.Res(
                    R.string.map_join_comma,
                    UiText.Res(R.string.map_legend_visited, 40),
                    UiText.Res(R.string.map_legend_lived, 3),
                ),
                UiText.Res(R.string.map_legend_wishlisted, 7),
            ),
            travelLegendDescription(stats(visited = 40, lived = 3, wishlist = 7)),
        )
    }

    private fun registryWith(vararg codeAndCategory: Pair<String, String>): VisaRegistry {
        val requirements = codeAndCategory.joinToString(",") { (code, category) ->
            """{"code": "$code", "category": "$category", "source": "dataset"}"""
        }
        return VisaRegistry.parse(
            """
            {
              "passport": "DE",
              "asOf": "2026-02-17",
              "dataset": {"name": "n", "url": "https://example.org", "licence": "MIT"},
              "iataUrl": "https://example.org/iata",
              "requirements": [$requirements]
            }
            """.trimIndent()
        )
    }

    @Test
    fun `visa legend without an asset says so rather than describing empty dots`() {
        assertEquals(UiText.Res(R.string.map_visa_summary_none_single), visaLegendSummary(null, noDataCount = 0))
    }

    @Test
    fun `visa legend counts the bands actually drawn plus the visa-free headline`() {
        val registry = registryWith("FR" to "VISA_FREE", "US" to "ETA", "CN" to "VISA_REQUIRED")
        assertEquals(
            UiText.Plural(R.plurals.map_visa_summary, 3, 3, 1),
            visaLegendSummary(registry, noDataCount = 0),
        )
    }

    @Test
    fun `visa legend adds the no-data band only when the map actually paints one`() {
        val registry = registryWith("FR" to "VISA_FREE")
        assertEquals(
            UiText.Plural(R.plurals.map_visa_summary, 2, 2, 1),
            visaLegendSummary(registry, noDataCount = 5),
        )
    }
}
