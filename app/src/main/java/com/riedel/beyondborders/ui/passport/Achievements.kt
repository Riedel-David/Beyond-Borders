package com.riedel.beyondborders.ui.passport

import com.riedel.beyondborders.data.place.INFORMAL_REGIONS
import com.riedel.beyondborders.data.place.Place
import com.riedel.beyondborders.data.place.PlaceStatus

enum class AchievementGroup(val label: String) {
    COUNTS("Places"),
    CONTINENTS("Continents"),
    REGIONS("Regions"),
    GROUPS("Named groupings"),
    DEPENDENCIES("Territories"),
    NON_UN("Beyond the UN"),
    TINY("Tiny places"),
    GIANTS("Giants"),
    SPECIAL("Special"),
    LIVED("Lived"),
    WISHLIST("Wishlist"),
}

/**
 * A badge, derived from the current statuses and never stored: unticking a place
 * can take one away again, which is the honest reading of "I have been there".
 * The English copy sits here so a later i18n step can move it out by [id].
 */
data class Achievement(
    val id: String,
    val title: String,
    val description: String,
    val progress: Int,
    val target: Int,
    val group: AchievementGroup,
) {
    val unlocked: Boolean get() = progress >= target
}

private val CountTiers = listOf(1, 10, 25, 50, 100, 150, 200)
private const val TinyAreaKm2 = 1000
private const val GiantCount = 10

/**
 * Every badge, with fixed rules that ignore the user's Denominator setting:
 * "been" is [PlaceStatus.isBeen] over all places, and "complete" means every
 * inhabited place in the set is been. Territories are their own places, so a been
 * Greenland counts nowhere towards Denmark.
 */
internal fun computeAchievements(
    places: List<Place>,
    statuses: Map<String, PlaceStatus>,
): List<Achievement> {
    fun isBeen(p: Place) = statuses[p.code]?.isBeen == true
    fun count(status: PlaceStatus) = places.count { statuses[it.code] == status }

    val been = places.filter(::isBeen)
    val out = ArrayList<Achievement>()

    for (n in CountTiers) {
        out += Achievement(
            "count_$n", if (n == 1) "First stamp" else "$n places",
            if (n == 1) "Mark your first place as been." else "Be in $n places.",
            been.size.coerceAtMost(n), n, AchievementGroup.COUNTS,
        )
    }

    // The sub-Antarctic islands are tagged Antarctica too; only the continent itself counts.
    val touched = been.filter { it.continent != "Antarctica" || it.code == "AQ" }
        .map { it.continent }.toSet().size
    for (n in listOf(3, 5, 7)) {
        out += Achievement(
            "continents_touch_$n", "$n continents", "Be somewhere on $n continents.",
            touched.coerceAtMost(n), n, AchievementGroup.CONTINENTS,
        )
    }
    // One named badge per continent, region and informal grouping. A set with nobody
    // living in it (Antarctica) has nothing to complete, so it gets no badge.
    fun named(id: String, title: String, description: String, set: Collection<Place>, group: AchievementGroup) {
        val inhabited = set.filter { !it.uninhabited }
        if (inhabited.isEmpty()) return
        out += Achievement(id, title, description, inhabited.count(::isBeen), inhabited.size, group)
    }
    for ((continent, members) in places.groupBy { it.continent }.toSortedMap()) {
        named(
            "continent_complete_" + slug(continent), "All of $continent",
            "Be in every inhabited place in $continent.", members, AchievementGroup.CONTINENTS,
        )
    }
    for ((region, members) in places.groupBy { it.region }.toSortedMap()) {
        named(
            "region_" + slug(region), region,
            "Be in every inhabited place in $region (${members.count { !it.uninhabited }} in all).",
            members, AchievementGroup.REGIONS,
        )
    }
    val byCode = places.associateBy { it.code }
    for (g in INFORMAL_REGIONS) {
        val members = g.codes.mapNotNull { byCode[it] }
        named(
            "group_" + slug(g.name), g.name,
            "Be in every inhabited place in ${g.name}: ${members.joinToString(", ") { it.name }}.",
            members, AchievementGroup.GROUPS,
        )
    }

    // Not `sovereign != name` alone: a dozen UN members are spelled differently in the two
    // fields (Türkiye / Turkey, Tanzania / United Republic of Tanzania, ...).
    val deps = been.count { !it.unMember && it.sovereign != it.name }
    for (n in listOf(1, 10)) {
        out += Achievement(
            "dependencies_$n", "$n ${if (n == 1) "territory" else "territories"}",
            "Be in $n place${if (n == 1) "" else "s"} that belong${if (n == 1) "s" else ""} to another country.",
            deps.coerceAtMost(n), n, AchievementGroup.DEPENDENCIES,
        )
    }

    val nonUn = been.count { !it.unMember }
    for (n in listOf(1, 5)) {
        out += Achievement(
            "non_un_$n", "$n outside the UN",
            "Be in $n place${if (n == 1) "" else "s"} that ${if (n == 1) "is" else "are"} not a UN member or observer.",
            nonUn.coerceAtMost(n), n, AchievementGroup.NON_UN,
        )
    }

    val tiny = been.count { it.areaKm2 < TinyAreaKm2 }
    for (n in listOf(1, 5)) {
        out += Achievement(
            "tiny_$n", "$n tiny ${if (n == 1) "place" else "places"}",
            "Be in $n place${if (n == 1) "" else "s"} under $TinyAreaKm2 km².",
            tiny.coerceAtMost(n), n, AchievementGroup.TINY,
        )
    }

    val giants = places.filter { !it.uninhabited }.sortedWith(compareByDescending<Place> { it.areaKm2 }.thenBy { it.code })
        .take(GiantCount).map { it.code }.toSet()
    out += Achievement(
        "giant_1", "A giant", "Be in one of the $GiantCount largest places by area.",
        been.count { it.code in giants }.coerceAtMost(1), 1, AchievementGroup.GIANTS,
    )

    out += Achievement(
        "antarctica", "The white continent", "Set foot in Antarctica.",
        if (statuses["AQ"]?.isBeen == true) 1 else 0, 1, AchievementGroup.SPECIAL,
    )

    // Centroid based: a place counts for the side its label anchor is on, so a
    // country straddling the equator is only in one hemisphere. Exactly 0 counts for neither.
    val sides = buildSet {
        for (p in been) {
            if (p.lat > 0) add("N") else if (p.lat < 0) add("S")
            if (p.lon > 0) add("E") else if (p.lon < 0) add("W")
        }
    }.size
    out += Achievement(
        "hemispheres", "Four hemispheres",
        "Be in the north, south, east and west (by each place's centre point).",
        sides, 4, AchievementGroup.SPECIAL,
    )

    val lived = count(PlaceStatus.LIVED)
    for (n in listOf(1, 3)) {
        out += Achievement(
            "lived_$n", if (n == 1) "Lived abroad" else "Lived in $n places",
            "Mark $n place${if (n == 1) "" else "s"} as lived.",
            lived.coerceAtMost(n), n, AchievementGroup.LIVED,
        )
    }

    out += Achievement(
        "wishlist_10", "Dreamer", "Put 10 places on your wishlist.",
        count(PlaceStatus.WISHLIST).coerceAtMost(10), 10, AchievementGroup.WISHLIST,
    )

    return out
}

private fun slug(s: String) = s.lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_')


/** A traveller rank. [next] and [remaining] are both null at the top rank. */
internal data class RankInfo(val title: String, val next: String?, val remaining: Int?) {
    /** The line under the title: how far the next rank is, or a closing word at the top. */
    val progressLine: String
        get() = if (next == null || remaining == null) "Top rank reached" else "$remaining more to reach $next"
}

/** Minimum places been for each rank, ascending. */
private val Ranks = listOf(
    0 to "Newcomer", 5 to "Wanderer", 15 to "Explorer", 30 to "Adventurer",
    60 to "Globetrotter", 100 to "World traveller", 150 to "Legend",
)

internal fun rankFor(placesVisited: Int): RankInfo {
    val index = Ranks.indexOfLast { placesVisited >= it.first }.coerceAtLeast(0)
    val next = Ranks.getOrNull(index + 1)
    return RankInfo(Ranks[index].second, next?.second, next?.let { it.first - placesVisited })
}

/** Unlocked and total badge count per group, for the section headers. */
internal fun groupProgress(achievements: List<Achievement>): Map<AchievementGroup, Pair<Int, Int>> =
    achievements.groupBy { it.group }.mapValues { (_, list) -> list.count { it.unlocked } to list.size }
