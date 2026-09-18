package com.riedel.beyondborders.data.backup

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The regression guard that matters most here: nothing about a future edit to
 * these two files should quietly put the travel history, the settings prefs or a
 * widget's config back into Auto Backup's scope.
 *
 * Read as plain text rather than parsed as XML — the module directory is the CWD
 * for host tests, the same way the asset tests read `src/main/assets/...`.
 */
class BackupRulesTest {

    private val backupRules = File("src/main/res/xml/backup_rules.xml").readText()
    private val dataExtractionRules = File("src/main/res/xml/data_extraction_rules.xml").readText()

    private val requiredExclusions = listOf(
        """<exclude domain="database" path="beyondborders.db"/>""",
        """<exclude domain="database" path="beyondborders.db-wal"/>""",
        """<exclude domain="database" path="beyondborders.db-shm"/>""",
        """<exclude domain="sharedpref" path="beyondborders.settings.xml"/>""",
        """<exclude domain="sharedpref" path="widget_prefs.xml"/>""",
    )

    @Test
    fun `backup_rules excludes the database and the settings and widget prefs`() {
        requiredExclusions.forEach { exclusion ->
            assertTrue("Missing in backup_rules.xml: $exclusion", backupRules.contains(exclusion))
        }
    }

    @Test
    fun `data_extraction_rules excludes the same paths under cloud-backup`() {
        val cloudBackup = dataExtractionRules.substringAfter("<cloud-backup>").substringBefore("</cloud-backup>")
        requiredExclusions.forEach { exclusion ->
            assertTrue("Missing under cloud-backup: $exclusion", cloudBackup.contains(exclusion))
        }
    }

    @Test
    fun `data_extraction_rules excludes the same paths under device-transfer`() {
        val deviceTransfer = dataExtractionRules.substringAfter("<device-transfer>").substringBefore("</device-transfer>")
        requiredExclusions.forEach { exclusion ->
            assertTrue("Missing under device-transfer: $exclusion", deviceTransfer.contains(exclusion))
        }
    }
}
