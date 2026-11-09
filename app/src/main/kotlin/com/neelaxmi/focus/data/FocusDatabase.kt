package com.neelaxmi.focus.data

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [SessionEntity::class, SessionLogEntity::class], version = 1, exportSchema = false)
abstract class FocusDatabase : RoomDatabase() {
    abstract fun sessions(): SessionDao
}
