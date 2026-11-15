package com.riedel.beyondborders.data.visa.update

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Test

class NetworkUseTest {
    // INTERNET is granted to the whole app, so nothing but convention keeps MapLibre or
    // a future change from using it. This is that convention made loud: a URL literal in
    // a new file fails here, and whoever adds it has to say why and update the list.
    private val allowed = setOf(
        "data/visa/update/VisaUpdate.kt", // the updater's one host
        "ui/ReviewPrompt.kt", // opens the Play page in the Play app or a browser
        "ui/settings/LicencesDialog.kt", // opens the repository page in a browser
        "ui/places/TravelAdvice.kt", // opens the UK government advice page in a browser
    )

    @Test
    fun `only the known files contain a URL literal`() {
        val root = File("src/main/java/com/riedel/beyondborders")
        val hits = root.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .filter { Regex("https?://").containsMatchIn(it.readText()) }
            .map { it.relativeTo(root).invariantSeparatorsPath }
            .toSortedSet()
        assertEquals(allowed.toSortedSet(), hits)
    }

    @Test
    fun `no source file mentions cleartext http`() {
        val root = File("src/main/java/com/riedel/beyondborders")
        val hits = root.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .filter { it.readText().contains("http://") }
            .map { it.name }.toList()
        assertEquals(emptyList<String>(), hits)
    }
}
