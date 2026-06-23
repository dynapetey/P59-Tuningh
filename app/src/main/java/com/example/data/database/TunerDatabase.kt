package com.example.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.data.model.CalFile
import com.example.data.model.LogDataPoint
import com.example.data.model.LogSession

@Database(
    entities = [CalFile::class, LogSession::class, LogDataPoint::class],
    version = 1,
    exportSchema = false
)
abstract class TunerDatabase : RoomDatabase() {

    abstract fun tunerDao(): TunerDao

    companion object {
        @Volatile
        private var INSTANCE: TunerDatabase? = null

        fun getDatabase(context: Context): TunerDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    TunerDatabase::class.java,
                    "obdx_tuner_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
