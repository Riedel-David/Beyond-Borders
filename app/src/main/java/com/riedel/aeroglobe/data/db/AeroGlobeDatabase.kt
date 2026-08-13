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
    ],
    version = 2,
    // Schemas are committed under app/schemas so migrations can be tested against
    // the real previous version instead of a reconstruction of it.
    exportSchema = true,
)
abstract class AeroGlobeDatabase : RoomDatabase() {

    abstract fun placeStatusDao(): PlaceStatusDao
    abstract fun flightDao(): FlightDao

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

        fun open(context: Context): AeroGlobeDatabase =
            Room.databaseBuilder(context, AeroGlobeDatabase::class.java, NAME)
                // No fallbackToDestructiveMigration: this database holds the one
                // thing in the app that cannot be re-fetched from anywhere. A
                // missing migration must fail the build's tests, not quietly wipe
                // a decade of travel history on upgrade.
                .addMigrations(MIGRATION_1_2)
                .build()
    }
}
