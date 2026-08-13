package com.riedel.aeroglobe.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        PlaceStatusEntity::class,
        FlightEntity::class,
        FlightStatusEntity::class,
        FlightStatusChangeEntity::class,
    ],
    version = 5,
    // Schemas are committed under app/schemas so migrations can be tested against
    // the real previous version instead of a reconstruction of it.
    exportSchema = true,
)
abstract class AeroGlobeDatabase : RoomDatabase() {

    abstract fun placeStatusDao(): PlaceStatusDao
    abstract fun flightDao(): FlightDao
    abstract fun flightStatusDao(): FlightStatusDao
    abstract fun flightStatusChangeDao(): FlightStatusChangeDao

    companion object {
        const val NAME = "aeroglobe.db"

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `flight` (
                        `id` TEXT NOT NULL,
                        `flightNumber` TEXT NOT NULL,
                        `date` TEXT NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        `deletedAt` INTEGER,
                        `deviceId` TEXT NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_flight_updatedAt` ON `flight` (`updatedAt`)"
                )
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `flight_status` (
                        `flightId` TEXT NOT NULL,
                        `scheduledDepartureUtc` INTEGER NOT NULL,
                        `estimatedDepartureUtc` INTEGER,
                        `departureGate` TEXT,
                        `departureTerminal` TEXT,
                        `phase` TEXT NOT NULL,
                        `fetchedAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        `deletedAt` INTEGER,
                        `deviceId` TEXT NOT NULL,
                        PRIMARY KEY(`flightId`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `flight_status_change` (
                        `id` TEXT NOT NULL,
                        `flightId` TEXT NOT NULL,
                        `field` TEXT NOT NULL,
                        `oldValue` TEXT,
                        `newValue` TEXT,
                        `changedAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        `deletedAt` INTEGER,
                        `deviceId` TEXT NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_flight_status_change_flightId_changedAt` ON `flight_status_change` (`flightId`, `changedAt`)"
                )
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `flight` ADD COLUMN `seat` TEXT")
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `flight_status` ADD COLUMN `aircraftIcao24` TEXT")
            }
        }

        fun open(context: Context): AeroGlobeDatabase =
            Room.databaseBuilder(context, AeroGlobeDatabase::class.java, NAME)
                // No fallbackToDestructiveMigration: this database holds the one
                // thing in the app that cannot be re-fetched from anywhere. A
                // missing migration must fail the build's tests, not quietly wipe
                // a decade of travel history on upgrade.
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                .build()
    }
}
