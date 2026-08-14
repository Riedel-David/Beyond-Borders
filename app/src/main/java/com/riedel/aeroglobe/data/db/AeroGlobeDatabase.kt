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
    version = 7,
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

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `flight_status` ADD COLUMN `departureAirportIata` TEXT")
                db.execSQL("ALTER TABLE `flight_status` ADD COLUMN `inboundOriginIata` TEXT")
                db.execSQL("ALTER TABLE `flight_status` ADD COLUMN `inboundScheduledArrivalUtc` INTEGER")
                db.execSQL("ALTER TABLE `flight_status` ADD COLUMN `inboundEstimatedArrivalUtc` INTEGER")
                db.execSQL("ALTER TABLE `flight_status` ADD COLUMN `inboundPhase` TEXT")
                db.execSQL("ALTER TABLE `flight_status` ADD COLUMN `inboundLegCheckedAt` INTEGER")
            }
        }

        /**
         * The only migration so far that drops columns (the 5 `inbound*` fields), which
         * SQLite's `ALTER TABLE` cannot do — so this rebuilds the table instead of altering
         * it: a new table under a temp name with the target shape, a copy of every row that
         * survives (inbound data has no replacement and is simply dropped), then the old
         * table is dropped and the new one renamed into its place.
         */
        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE `flight_status_new` (
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
                        `aircraftIcao24` TEXT,
                        `departureAirportIata` TEXT,
                        `actualDepartureUtc` INTEGER,
                        `arrivalAirportIata` TEXT,
                        `scheduledArrivalUtc` INTEGER,
                        `estimatedArrivalUtc` INTEGER,
                        `actualArrivalUtc` INTEGER,
                        `airlineName` TEXT,
                        `airlineIata` TEXT,
                        `airlineIcao` TEXT,
                        `aircraftModel` TEXT,
                        PRIMARY KEY(`flightId`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO `flight_status_new` (
                        `flightId`, `scheduledDepartureUtc`, `estimatedDepartureUtc`,
                        `departureGate`, `departureTerminal`, `phase`, `fetchedAt`,
                        `updatedAt`, `deletedAt`, `deviceId`, `aircraftIcao24`, `departureAirportIata`
                    )
                    SELECT
                        `flightId`, `scheduledDepartureUtc`, `estimatedDepartureUtc`,
                        `departureGate`, `departureTerminal`, `phase`, `fetchedAt`,
                        `updatedAt`, `deletedAt`, `deviceId`, `aircraftIcao24`, `departureAirportIata`
                    FROM `flight_status`
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE `flight_status`")
                db.execSQL("ALTER TABLE `flight_status_new` RENAME TO `flight_status`")
            }
        }

        fun open(context: Context): AeroGlobeDatabase =
            Room.databaseBuilder(context, AeroGlobeDatabase::class.java, NAME)
                // No fallbackToDestructiveMigration: this database holds the one
                // thing in the app that cannot be re-fetched from anywhere. A
                // missing migration must fail the build's tests, not quietly wipe
                // a decade of travel history on upgrade.
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)
                .build()
    }
}
