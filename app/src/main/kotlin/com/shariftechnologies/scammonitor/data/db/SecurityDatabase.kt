package com.shariftechnologies.scammonitor.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration

/**
 * Local SQLite store in app-private storage. It is not encrypted by this app; see docs/PRIVACY.md.
 *
 * Schema changes must bump [version], add a [Migration] to [MIGRATIONS] and a migration test.
 * Destructive migration is deliberately not enabled: users' history must not vanish on update.
 */
@Database(entities = [ScanEntity::class, FlaggedItemEntity::class], version = 1, exportSchema = true)
abstract class SecurityDatabase : RoomDatabase() {
    abstract fun securityLogDao(): SecurityLogDao
    abstract fun flaggedItemDao(): FlaggedItemDao

    companion object {
        const val NAME = "security.db"

        /** Empty for schema version 1. */
        val MIGRATIONS: Array<Migration> = emptyArray()

        fun create(context: Context): SecurityDatabase =
            Room.databaseBuilder(context.applicationContext, SecurityDatabase::class.java, NAME)
                .addMigrations(*MIGRATIONS)
                .build()
    }
}
