package com.riedel.aeroglobe.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [PlaceStatusEntity::class],
    version = 1,
    // Schemas are committed under app/schemas so migrations can be tested against
    // the real previous version instead of a reconstruction of it.
    exportSchema = true,
)
abstract class AeroGlobeDatabase : RoomDatabase() {

    abstract fun placeStatusDao(): PlaceStatusDao

    companion object {
        const val NAME = "aeroglobe.db"

        fun open(context: Context): AeroGlobeDatabase =
            Room.databaseBuilder(context, AeroGlobeDatabase::class.java, NAME)
                // No fallbackToDestructiveMigration: this database holds the one
                // thing in the app that cannot be re-fetched from anywhere. A
                // missing migration must fail the build's tests, not quietly wipe
                // a decade of travel history on upgrade.
                .build()
    }
}
