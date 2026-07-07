package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.data.model.BabyActivity

@Database(entities = [BabyActivity::class], version = 1, exportSchema = false)
abstract class BabyDatabase : RoomDatabase() {

    abstract fun babyActivityDao(): BabyActivityDao

    companion object {
        @Volatile
        private var INSTANCE: BabyDatabase? = null

        fun getDatabase(context: Context): BabyDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    BabyDatabase::class.java,
                    "baby_tracker_db"
                ).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }
    }
}
