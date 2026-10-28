package com.riedel.beyondborders.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory

@Database(
    entities = [
        PlaceStatusEntity::class,
    ],
    version = 10,
    // Schemas are committed under app/schemas so migrations can be tested against
    // the real previous version instead of a reconstruction of it.
    exportSchema = true,
)
abstract class BeyondBordersDatabase : RoomDatabase() {

    abstract fun placeStatusDao(): PlaceStatusDao

    companion object {
        const val NAME = "beyondborders.db"

        /**
         * Flight tracking was removed. Drops the three
         * tables `MIGRATION_1_2`..`MIGRATION_7_8` built up rather than leaving
         * them behind for Room's schema validator to complain about.
         */
        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP TABLE IF EXISTS `flight`")
                db.execSQL("DROP TABLE IF EXISTS `flight_status`")
                db.execSQL("DROP TABLE IF EXISTS `flight_status_change`")
            }
        }

        /**
         * Drops `firstVisitedOn` and `note`: two columns no screen ever wrote to
         *. SQLite's ALTER TABLE cannot drop a column on
         * the SQLite version Room targets here, so this rebuilds the table as
         * MIGRATION_6_7 did for the same reason.
         */
        private val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE `place_status_new` (
                        `id` TEXT NOT NULL,
                        `code` TEXT NOT NULL,
                        `status` TEXT NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        `deletedAt` INTEGER,
                        `deviceId` TEXT NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO `place_status_new` (
                        `id`, `code`, `status`, `updatedAt`, `deletedAt`, `deviceId`
                    )
                    SELECT `id`, `code`, `status`, `updatedAt`, `deletedAt`, `deviceId`
                    FROM `place_status`
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE `place_status`")
                db.execSQL("ALTER TABLE `place_status_new` RENAME TO `place_status`")
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_place_status_code` ON `place_status` (`code`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_place_status_updatedAt` ON `place_status` (`updatedAt`)"
                )
            }
        }

        /** [name] is only ever overridden by tests, which must not open the real file. */
        fun open(context: Context, name: String = NAME): BeyondBordersDatabase =
            Room.databaseBuilder(context, BeyondBordersDatabase::class.java, name)
                // No fallbackToDestructiveMigration: this database holds the one
                // thing in the app that cannot be re-fetched from anywhere. A
                // missing migration must fail the build's tests, not quietly wipe
                // a decade of travel history on upgrade.
                .addMigrations(MIGRATION_8_9, MIGRATION_9_10)
                .openHelperFactory(KeepOnCorruptionFactory(FrameworkSQLiteOpenHelperFactory()))
                .build()
    }

    /**
     * Same reasoning as the missing destructive-migration fallback: the stock
     * [SupportSQLiteOpenHelper.Callback.onCorruption] deletes the database file, so a
     * single failed integrity check would silently erase the history before the
     * recovery screen could offer a copy. This hands every call through unchanged
     * except that one, which does nothing; the open then throws and
     * [DatabaseRecovery] reports it.
     */
    private class KeepOnCorruptionFactory(
        private val delegate: SupportSQLiteOpenHelper.Factory,
    ) : SupportSQLiteOpenHelper.Factory {
        override fun create(configuration: SupportSQLiteOpenHelper.Configuration): SupportSQLiteOpenHelper {
            val inner = configuration.callback
            val wrapped = object : SupportSQLiteOpenHelper.Callback(inner.version) {
                override fun onConfigure(db: SupportSQLiteDatabase) = inner.onConfigure(db)
                override fun onCreate(db: SupportSQLiteDatabase) = inner.onCreate(db)
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) =
                    inner.onUpgrade(db, oldVersion, newVersion)
                override fun onDowngrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) =
                    inner.onDowngrade(db, oldVersion, newVersion)
                override fun onOpen(db: SupportSQLiteDatabase) = inner.onOpen(db)
                override fun onCorruption(db: SupportSQLiteDatabase) = Unit
            }
            return delegate.create(
                SupportSQLiteOpenHelper.Configuration.builder(configuration.context)
                    .name(configuration.name)
                    .callback(wrapped)
                    .noBackupDirectory(configuration.useNoBackupDirectory)
                    .allowDataLossOnRecovery(configuration.allowDataLossOnRecovery)
                    .build()
            )
        }
    }
}
