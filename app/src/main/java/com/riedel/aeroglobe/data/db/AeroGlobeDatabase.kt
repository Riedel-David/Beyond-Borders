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
    ],
    version = 9,
    // Schemas are committed under app/schemas so migrations can be tested against
    // the real previous version instead of a reconstruction of it.
    exportSchema = true,
)
abstract class AeroGlobeDatabase : RoomDatabase() {

    abstract fun placeStatusDao(): PlaceStatusDao

    companion object {
        const val NAME = "aeroglobe.db"

        /**
         * Flight tracking was removed (see PLAN.md revision 7). Drops the three
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

        fun open(context: Context): AeroGlobeDatabase =
            Room.databaseBuilder(context, AeroGlobeDatabase::class.java, NAME)
                // No fallbackToDestructiveMigration: this database holds the one
                // thing in the app that cannot be re-fetched from anywhere. A
                // missing migration must fail the build's tests, not quietly wipe
                // a decade of travel history on upgrade.
                .addMigrations(MIGRATION_8_9)
                .build()
    }
}
