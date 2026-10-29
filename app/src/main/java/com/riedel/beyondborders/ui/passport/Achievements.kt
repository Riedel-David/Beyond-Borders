package com.riedel.beyondborders.ui.passport

import androidx.annotation.StringRes
import com.riedel.beyondborders.R
import com.riedel.beyondborders.data.place.INFORMAL_REGIONS
import com.riedel.beyondborders.data.place.Place
import com.riedel.beyondborders.data.place.PlaceStatus
import com.riedel.beyondborders.ui.UiText

enum class AchievementGroup(@StringRes val label: Int) {
    COUNTS(R.string.badge_group_counts),
    CONTINENTS(R.string.badge_group_continents),
    REGIONS(R.string.badge_group_regions),
    GROUPS(R.string.badge_group_groups),
    DEPENDENCIES(R.string.badge_group_dependencies),
    NON_UN(R.string.badge_group_non_un),
    TINY(R.string.badge_group_tiny),
    GIANTS(R.string.badge_group_giants),
    SPECIAL(R.string.badge_group_special),
    LIVED(R.string.badge_group_lived),
    WISHLIST(R.string.badge_group_wishlist),
}

/**
 * A badge, derived from the current statuses and never stored: unticking a place
 * can take one away again, which is the honest reading of "I have been there".
 * The copy is a [UiText], resolved where it is drawn.
 */
data class Achievement(
    val id: String,
    val title: UiText,
    val description: UiText,
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
            "count_$n",
            if (n == 1) UiText.Res(R.string.badge_count_1_title) else UiText.Plural(R.plurals.badge_count_n_title, n, n),
            if (n == 1) UiText.Res(R.string.badge_count_1_desc) else UiText.Plural(R.plurals.badge_count_n_desc, n, n),
            been.size.coerceAtMost(n), n, AchievementGroup.COUNTS,
        )
    }

    // The sub-Antarctic islands are tagged Antarctica too; only the continent itself counts.
    val touched = been.filter { it.continent != "Antarctica" || it.code == "AQ" }
        .map { it.continent }.toSet().size
    for (n in listOf(3, 5, 7)) {
        out += Achievement(
            "continents_touch_$n",
            UiText.Plural(R.plurals.badge_continents_touch_title, n, n),
            UiText.Plural(R.plurals.badge_continents_touch_desc, n, n),
            touched.coerceAtMost(n), n, AchievementGroup.CONTINENTS,
        )
    }
    // One named badge per continent, region and informal grouping. A set with nobody
    // living in it (Antarctica) has nothing to complete, so it gets no badge.
    fun named(id: String, title: UiText, description: UiText, set: Collection<Place>, group: AchievementGroup) {
        val inhabited = set.filter { !it.uninhabited }
        if (inhabited.isEmpty()) return
        out += Achievement(id, title, description, inhabited.count(::isBeen), inhabited.size, group)
    }
    for ((continent, members) in places.groupBy { it.continent }.toSortedMap()) {
        named(
            "continent_complete_" + slug(continent),
            UiText.Res(R.string.badge_continent_complete_title, continent),
            UiText.Res(R.string.badge_continent_complete_desc, continent), members, AchievementGroup.CONTINENTS,
        )
    }
    for ((region, members) in places.groupBy { it.region }.toSortedMap()) {
        named(
            "region_" + slug(region), UiText.Plain(region),
            UiText.Res(R.string.badge_region_desc, region, members.count { !it.uninhabited }),
            members, AchievementGroup.REGIONS,
        )
    }
    val byCode = places.associateBy { it.code }
    for (g in INFORMAL_REGIONS) {
        val members = g.codes.mapNotNull { byCode[it] }
        named(
            "group_" + slug(g.name), UiText.Plain(g.name),
            UiText.Res(R.string.badge_group_desc, g.name, members.joinToString(", ") { it.name }),
            members, AchievementGroup.GROUPS,
        )
    }

    // Not `sovereign != name` alone: a dozen UN members are spelled differently in the two
    // fields (Türkiye / Turkey, Tanzania / United Republic of Tanzania, ...).
    val deps = been.count { !it.unMember && it.sovereign != it.name }
    for (n in listOf(1, 10)) {
        out += Achievement(
            "dependencies_$n",
            UiText.Plural(R.plurals.badge_dependencies_title, n, n),
            UiText.Plural(R.plurals.badge_dependencies_desc, n, n),
            deps.coerceAtMost(n), n, AchievementGroup.DEPENDENCIES,
        )
    }

    val nonUn = been.count { !it.unMember }
    for (n in listOf(1, 5)) {
        out += Achievement(
            "non_un_$n",
            UiText.Plural(R.plurals.badge_non_un_title, n, n),
            UiText.Plural(R.plurals.badge_non_un_desc, n, n),
            nonUn.coerceAtMost(n), n, AchievementGroup.NON_UN,
        )
    }

    val tiny = been.count { it.areaKm2 < TinyAreaKm2 }
    for (n in listOf(1, 5)) {
        out += Achievement(
            "tiny_$n",
            UiText.Plural(R.plurals.badge_tiny_title, n, n),
            UiText.Plural(R.plurals.badge_tiny_desc, n, n, TinyAreaKm2),
            tiny.coerceAtMost(n), n, AchievementGroup.TINY,
        )
    }

    val giants = places.filter { !it.uninhabited }.sortedWith(compareByDescending<Place> { it.areaKm2 }.thenBy { it.code })
        .take(GiantCount).map { it.code }.toSet()
    out += Achievement(
        "giant_1", UiText.Res(R.string.badge_giant_1_title), UiText.Res(R.string.badge_giant_1_desc, GiantCount),
        been.count { it.code in giants }.coerceAtMost(1), 1, AchievementGroup.GIANTS,
    )

    out += Achievement(
        "antarctica", UiText.Res(R.string.badge_antarctica_title), UiText.Res(R.string.badge_antarctica_desc),
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
        "hemispheres", UiText.Res(R.string.badge_hemispheres_title), UiText.Res(R.string.badge_hemispheres_desc),
        sides, 4, AchievementGroup.SPECIAL,
    )

    val lived = count(PlaceStatus.LIVED)
    for (n in listOf(1, 3)) {
        out += Achievement(
            "lived_$n",
            if (n == 1) UiText.Res(R.string.badge_lived_1_title) else UiText.Res(R.string.badge_lived_3_title, n),
            UiText.Plural(R.plurals.badge_lived_desc, n, n),
            lived.coerceAtMost(n), n, AchievementGroup.LIVED,
        )
    }

    out += Achievement(
        "wishlist_10", UiText.Res(R.string.badge_wishlist_10_title), UiText.Res(R.string.badge_wishlist_10_desc, 10),
        count(PlaceStatus.WISHLIST).coerceAtMost(10), 10, AchievementGroup.WISHLIST,
    )

    return out
}

private fun slug(s: String) = s.lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_')


/** A traveller rank. [next] and [remaining] are both null at the top rank. */
internal data class RankInfo(val title: UiText, val next: UiText?, val remaining: Int?) {
    /** The line under the title: how far the next rank is, or a closing word at the top. */
    val progressLine: UiText
        get() = if (next == null || remaining == null) {
            UiText.Res(R.string.rank_top_reached)
        } else {
            UiText.Res(R.string.rank_progress, remaining, next)
        }
}

/** Minimum places been for each rank, ascending. */
private val Ranks = listOf(
    0 to R.string.rank_newcomer, 5 to R.string.rank_wanderer, 15 to R.string.rank_explorer,
    30 to R.string.rank_adventurer, 60 to R.string.rank_globetrotter,
    100 to R.string.rank_world_traveller, 150 to R.string.rank_legend,
)

internal fun rankFor(placesVisited: Int): RankInfo {
    val index = Ranks.indexOfLast { placesVisited >= it.first }.coerceAtLeast(0)
    val next = Ranks.getOrNull(index + 1)
    return RankInfo(
        UiText.Res(Ranks[index].second), next?.let { UiText.Res(it.second) }, next?.let { it.first - placesVisited },
    )
}

/** Unlocked and total badge count per group, for the section headers. */
internal fun groupProgress(achievements: List<Achievement>): Map<AchievementGroup, Pair<Int, Int>> =
    achievements.groupBy { it.group }.mapValues { (_, list) -> list.count { it.unlocked } to list.size }

/** The locked badges closest to done (some progress, highest share first, ties by id). */
internal fun nextUp(achievements: List<Achievement>, limit: Int = 3): List<Achievement> =
    achievements.filter { !it.unlocked && it.progress > 0 }
        .sortedWith(
            compareByDescending<Achievement> { it.progress.toDouble() / it.target.coerceAtLeast(1) }
                .thenBy { it.id }
        )
        .take(limit)

/** [fresh] badges to show as new, and the [seen] set to persist afterwards. */
internal data class SeenUpdate(val fresh: List<Achievement>, val seen: Set<String>)

/**
 * Unlocked badges not yet in [seen]. A null [seen] means the set was never seeded (first
 * run, or a restore, since settings are not backed up): everything unlocked right now is
 * recorded as seen and nothing is new, rather than reading "empty" as "all new".
 * Ids are only ever added, so a badge that goes away and returns is not new again.
 */
internal fun newlyUnlocked(seen: Set<String>?, achievements: List<Achievement>): SeenUpdate {
    val unlocked = achievements.filter { it.unlocked }
    val ids = unlocked.map { it.id }
    if (seen == null) return SeenUpdate(emptyList(), ids.toSet())
    return SeenUpdate(unlocked.filter { it.id !in seen }, seen + ids)
}

internal fun celebrationMessage(fresh: List<Achievement>): UiText? = when (fresh.size) {
    0 -> null
    1 -> UiText.Res(R.string.badge_celebrate_one, fresh[0].title)
    else -> UiText.Res(R.string.badge_celebrate_more, fresh[0].title, fresh.size - 1)
}
