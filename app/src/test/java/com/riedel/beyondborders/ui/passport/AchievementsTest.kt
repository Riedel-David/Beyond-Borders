package com.riedel.beyondborders.ui.passport

import com.riedel.beyondborders.data.place.Bounds
import com.riedel.beyondborders.data.place.INFORMAL_REGIONS
import com.riedel.beyondborders.data.place.PlaceRegistry
import com.riedel.beyondborders.data.place.Place
import com.riedel.beyondborders.data.place.PlaceStatus
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AchievementsTest {

    private fun place(
        code: String,
        continent: String = "Europe",
        region: String = "Western Europe",
        sovereign: String = code,
        name: String = code,
        uninhabited: Boolean = false,
        unMember: Boolean = true,
        lat: Double = 10.0,
        lon: Double = 10.0,
        area: Long = 50_000,
    ) = Place(
        code, name, continent, region, sovereign, uninhabited, unMember, lat, lon,
        Bounds(0.0, 0.0, 1.0, 1.0), area,
    )

    private fun been(vararg codes: String) = codes.associateWith { PlaceStatus.VISITED }

    private fun List<Achievement>.byId(id: String) = single { it.id == id }

    private fun numbered(n: Int, build: (Int) -> Place = { place("P$it") }) = (1..n).map(build)

    @Test
    fun `empty state unlocks nothing`() {
        val all = computeAchievements(numbered(20), emptyMap())
        assertTrue(all.none { it.unlocked })
    }

    @Test
    fun `count badges unlock exactly at their target and not one before`() {
        val places = numbered(210)
        for (n in listOf(1, 10, 25, 50, 100, 150, 200)) {
            val before = computeAchievements(places, been(*places.take(n - 1).map { it.code }.toTypedArray()))
            val at = computeAchievements(places, been(*places.take(n).map { it.code }.toTypedArray()))
            assertFalse("count_$n at ${n - 1}", before.byId("count_$n").unlocked)
            assertTrue("count_$n at $n", at.byId("count_$n").unlocked)
        }
    }

    @Test
    fun `lived tiers count only LIVED and a visited place does not help`() {
        val places = numbered(5)
        val st = mapOf("P1" to PlaceStatus.LIVED, "P2" to PlaceStatus.VISITED, "P3" to PlaceStatus.LIVED)
        var a = computeAchievements(places, st)
        assertTrue(a.byId("lived_1").unlocked)
        assertFalse(a.byId("lived_3").unlocked)
        assertEquals(2, a.byId("lived_3").progress)
        a = computeAchievements(places, st + ("P4" to PlaceStatus.LIVED))
        assertTrue(a.byId("lived_3").unlocked)
    }

    @Test
    fun `a lived place also counts as been`() {
        val a = computeAchievements(numbered(2), mapOf("P1" to PlaceStatus.LIVED))
        assertTrue(a.byId("count_1").unlocked)
    }

    @Test
    fun `wishlist counts only WISHLIST and unlocks exactly at ten`() {
        val places = numbered(12)
        val nine = places.take(9).associate { it.code to PlaceStatus.WISHLIST }
        assertFalse(computeAchievements(places, nine).byId("wishlist_10").unlocked)
        val ten = nine + ("P10" to PlaceStatus.WISHLIST)
        assertTrue(computeAchievements(places, ten).byId("wishlist_10").unlocked)
        val mixed = nine + ("P10" to PlaceStatus.VISITED)
        assertFalse(computeAchievements(places, mixed).byId("wishlist_10").unlocked)
    }

    @Test
    fun `Greenland is not Denmark so a been territory does not complete its sovereign's groups`() {
        val places = listOf(
            place("DK", name = "Denmark", sovereign = "Denmark", continent = "Europe", region = "Northern Europe"),
            place("GL", name = "Greenland", sovereign = "Denmark", continent = "North America", region = "Northern America", unMember = false),
        )
        val a = computeAchievements(places, been("GL"))
        assertFalse(a.byId("continent_complete_europe").unlocked)
        assertTrue(a.byId("continent_complete_north_america").unlocked)
        assertEquals(1, a.byId("dependencies_1").progress)
        assertEquals(0, computeAchievements(places, been("DK")).byId("dependencies_1").progress)
    }

    @Test
    fun `dependency badges unlock at one and ten`() {
        val places = numbered(12) { place("P$it", sovereign = "Elsewhere", unMember = false) }
        val nine = computeAchievements(places, been(*places.take(9).map { it.code }.toTypedArray()))
        val ten = computeAchievements(places, been(*places.take(10).map { it.code }.toTypedArray()))
        assertTrue(nine.byId("dependencies_1").unlocked)
        assertFalse(nine.byId("dependencies_10").unlocked)
        assertTrue(ten.byId("dependencies_10").unlocked)
    }

    @Test
    fun `non-UN and tiny badges unlock at one and five`() {
        val places = numbered(6) { place("P$it", unMember = false, area = 10) }
        val four = computeAchievements(places, been("P1", "P2", "P3", "P4"))
        val five = computeAchievements(places, been("P1", "P2", "P3", "P4", "P5"))
        for (g in listOf("non_un", "tiny")) {
            assertTrue(four.byId("${g}_1").unlocked)
            assertFalse(four.byId("${g}_5").unlocked)
            assertTrue(five.byId("${g}_5").unlocked)
        }
    }

    @Test
    fun `tiny means under 1000 square km, exactly 1000 is not tiny`() {
        val places = listOf(place("A", area = 999), place("B", area = 1000))
        assertTrue(computeAchievements(places, been("A")).byId("tiny_1").unlocked)
        assertFalse(computeAchievements(places, been("B")).byId("tiny_1").unlocked)
    }

    @Test
    fun `uninhabited places do not block completing a continent, region or group`() {
        val places = listOf(
            place("NO", continent = "Europe", region = "Northern Europe"),
            place("SE", continent = "Europe", region = "Northern Europe"),
            place("DK", continent = "Europe", region = "Northern Europe"),
            place("BV", continent = "Europe", region = "Northern Europe", uninhabited = true),
        )
        val a = computeAchievements(places, been("NO", "SE", "DK"))
        assertTrue(a.byId("continent_complete_europe").unlocked)
        assertTrue(a.byId("region_northern_europe").unlocked)
        assertTrue(a.byId("group_scandinavia").unlocked)
        assertFalse(computeAchievements(places, been("NO", "SE")).byId("region_northern_europe").unlocked)
    }

    @Test
    fun `a continent with no inhabited place gets no complete badge`() {
        val places = listOf(place("AQ", continent = "Antarctica", uninhabited = true), place("FR"))
        val ids = computeAchievements(places, emptyMap()).map { it.id }
        assertFalse("continent_complete_antarctica" in ids)
        assertTrue("continent_complete_europe" in ids)
    }

    @Test
    fun `continent touch tiers unlock at three, five and seven`() {
        val conts = listOf("A", "B", "C", "D", "E", "F", "G")
        val places = conts.map { place("P$it", continent = it) }
        for (n in listOf(3, 5, 7)) {
            val before = computeAchievements(places, been(*places.take(n - 1).map { it.code }.toTypedArray()))
            val at = computeAchievements(places, been(*places.take(n).map { it.code }.toTypedArray()))
            assertFalse(before.byId("continents_touch_$n").unlocked)
            assertTrue(at.byId("continents_touch_$n").unlocked)
        }
    }

    @Test
    fun `each region has its own badge that unlocks only when all its inhabited places are been`() {
        val places = listOf(place("A", region = "Alpha"), place("B", region = "Alpha"), place("C", region = "Beta"))
        val a = computeAchievements(places, been("A", "C"))
        assertFalse(a.byId("region_alpha").unlocked)
        assertEquals(1, a.byId("region_alpha").progress)
        assertEquals(2, a.byId("region_alpha").target)
        assertTrue(a.byId("region_beta").unlocked)
        assertEquals("Alpha", a.byId("region_alpha").title)
    }

    @Test
    fun `a hand-built group badge unlocks when its places are been and not before`() {
        val places = listOf(place("BE"), place("NL"), place("LU"))
        assertFalse(computeAchievements(places, been("BE", "NL")).byId("group_benelux").unlocked)
        assertTrue(computeAchievements(places, been("BE", "NL", "LU")).byId("group_benelux").unlocked)
    }

    @Test
    fun `every informal grouping and every region in the real asset produces a badge`() {
        val places = PlaceRegistry.parse(File("src/main/assets/places.json").readText()).places
        val ids = computeAchievements(places, emptyMap()).map { it.id }.toSet()
        INFORMAL_REGIONS.forEach { assertTrue(it.name, "group_" + slug(it.name) in ids) }
        places.filter { !it.uninhabited }.map { it.region }.toSet()
            .forEach { assertTrue(it, "region_" + slug(it) in ids) }
    }

    @Test
    fun `hemispheres need all four sides by centroid`() {
        val places = listOf(
            place("NE", lat = 10.0, lon = 10.0), place("SW", lat = -10.0, lon = -10.0),
            place("SE", lat = -10.0, lon = 10.0), place("NW", lat = 10.0, lon = -10.0),
        )
        val two = computeAchievements(places, been("NE", "SW")).byId("hemispheres")
        assertEquals(4, two.target)
        assertEquals(4, two.progress)
        val one = computeAchievements(places, been("NE")).byId("hemispheres")
        assertEquals(2, one.progress)
        assertFalse(one.unlocked)
        val three = computeAchievements(places, been("NE", "SE")).byId("hemispheres")
        assertEquals(3, three.progress)
    }

    @Test
    fun `giants are the ten largest places derived from the data`() {
        val places = numbered(12) { place("P$it", area = it * 1000L) }
        // P3..P12 are the ten largest; P2 is eleventh.
        assertFalse(computeAchievements(places, been("P2", "P1")).byId("giant_1").unlocked)
        assertTrue(computeAchievements(places, been("P3")).byId("giant_1").unlocked)
    }

    @Test
    fun `a UN member spelled differently from its sovereign is not a territory`() {
        val places = listOf(place("TR", name = "Türkiye", sovereign = "Turkey"))
        assertFalse(computeAchievements(places, been("TR")).byId("dependencies_1").unlocked)
    }

    @Test
    fun `a sub-Antarctic island does not count as touching Antarctica`() {
        val places = listOf(
            place("GS", continent = "Antarctica", uninhabited = true),
            place("AQ", continent = "Antarctica", uninhabited = true),
            place("FR"),
        )
        fun touched(vararg codes: String) =
            computeAchievements(places, been(*codes)).byId("continents_touch_3").progress
        assertEquals(0, touched("GS"))
        assertEquals(1, touched("GS", "FR"))
        assertEquals(2, touched("AQ", "FR"))
    }

    @Test
    fun `uninhabited places are never giants`() {
        val places = numbered(11) { place("P$it", area = it * 1000L) } +
            place("BIG", uninhabited = true, area = 99_000_000)
        assertFalse(computeAchievements(places, been("BIG")).byId("giant_1").unlocked)
    }

    @Test
    fun `Antarctica badge needs the code AQ`() {
        val places = listOf(place("AQ", continent = "Antarctica", uninhabited = true), place("FR"))
        assertFalse(computeAchievements(places, been("FR")).byId("antarctica").unlocked)
        assertTrue(computeAchievements(places, been("AQ")).byId("antarctica").unlocked)
    }

    @Test
    fun `every achievement id is unique`() {
        val places = listOf(place("A", continent = "X"), place("B", continent = "Y"))
        val ids = computeAchievements(places, emptyMap()).map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun `real asset has Antarctica and at least one tiny, giant, non-UN and dependency place`() {
        val places = PlaceRegistry.parse(File("src/main/assets/places.json").readText()).places
        assertTrue(places.any { it.code == "AQ" })
        val all = computeAchievements(places, places.associate { it.code to PlaceStatus.VISITED })
        for (id in listOf("antarctica", "tiny_5", "giant_1", "non_un_5", "dependencies_10", "hemispheres", "count_200")) {
            assertTrue("$id should unlock when everything is been", all.byId(id).unlocked)
        }
        assertTrue(all.filter { it.id.startsWith("continent_complete_") }.all { it.unlocked })
        assertTrue(all.all { it.unlocked || it.group in setOf(AchievementGroup.LIVED, AchievementGroup.WISHLIST) })
    }

    private fun slug(s: String) = s.lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_')
}
