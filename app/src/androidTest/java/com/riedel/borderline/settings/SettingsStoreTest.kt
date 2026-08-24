package com.riedel.borderline.settings

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Where [SettingsStore.deviceId] is stored, which is a data-integrity question rather
 * than a preferences one.
 *
 * The id has to be per install. Two installs sharing one would each read the other's
 * writes as an echo of its own and skip them, so a restore has to land on a new id
 * while the theme and the map colours carry over — and Auto Backup can only exclude
 * whole files, which is why there are two of them.
 *
 * The file names are written out here rather than taken from [SettingsStore]: they are
 * also written out in `backup_rules.xml` and `data_extraction_rules.xml`, which no test
 * can reach, so a rename that misses those is what this is watching for.
 */
@RunWith(AndroidJUnit4::class)
class SettingsStoreTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val settingsFile = context.getSharedPreferences("borderline.settings", Context.MODE_PRIVATE)
    private val deviceFile = context.getSharedPreferences("borderline.device", Context.MODE_PRIVATE)

    @Before
    @After
    fun clearBothFiles() {
        settingsFile.edit().clear().commit()
        deviceFile.edit().clear().commit()
    }

    @Test
    fun `a fresh install generates one id and keeps it`() {
        val first = SettingsStore(context).deviceId
        assertTrue("no id was generated", first.isNotEmpty())
        assertEquals("the id changed on the next launch", first, SettingsStore(context).deviceId)
        assertEquals(first, deviceFile.getString("device_id", null))
    }

    /**
     * The id must not sit in the file a backup carries. Excluding it by name only works
     * if nothing else puts it back, so this asserts the settings file stays clean rather
     * than just that the device file is right.
     */
    @Test
    fun `the id is not written to the file a backup restores`() {
        val id = SettingsStore(context).deviceId
        assertEquals(id, deviceFile.getString("device_id", null))
        assertFalse(
            "device_id is in the backed-up settings file",
            settingsFile.contains("device_id"),
        )
    }

    /**
     * The upgrade path, and the restored-backup path, which are the same two files in
     * the same two states: an old `device_id` in the settings file and nothing in the
     * device file. Adopting that value would keep an upgrade's id stable — and hand a
     * restored phone the id it must not have. It gets a new one instead, and the old
     * key is deleted so it stops riding along in future backups.
     */
    @Test
    fun `an id left in the settings file is discarded rather than adopted`() {
        settingsFile.edit().putString("device_id", "id-from-an-older-build").commit()

        val id = SettingsStore(context).deviceId

        assertNotEquals("the old id was adopted", "id-from-an-older-build", id)
        assertEquals(id, deviceFile.getString("device_id", null))
        assertFalse("the old key was left behind", settingsFile.contains("device_id"))
    }

    /** The other half of the split: what a restore is supposed to bring back. */
    @Test
    fun `the theme and the map colours stay in the backed-up file`() {
        val store = SettingsStore(context)
        store.setThemeMode(ThemeMode.DARK)
        store.setMapHue(MapSlot.VISITED, MapColors.DEFAULT.lived)

        assertTrue("theme is not in the backed-up file", settingsFile.contains("theme_mode"))
        assertTrue("map hues are not in the backed-up file", settingsFile.contains("map_hue_visited"))
        assertEquals(ThemeMode.DARK, SettingsStore(context).themeMode.value)
    }
}
