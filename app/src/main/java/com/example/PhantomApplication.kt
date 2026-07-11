package com.example

import android.app.Application
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.room.migration.Migration
import com.example.db.PhantomDatabase
import com.example.db.PhantomRepository

class PhantomApplication : Application() {
    lateinit var database: PhantomDatabase
    lateinit var repository: PhantomRepository

    override fun onCreate() {
        super.onCreate()
        instance = this

        // Load SQLCipher native libraries
        try {
            System.loadLibrary("sqlcipher")
        } catch (e: UnsatisfiedLinkError) {
            android.util.Log.w("PHANTOM_INIT", "Failed to load sqlcipher library: " + e.message)
        }

        // Initialize local secure Room database encrypted with SQLCipher
        val dbPassphrase = CryptoUtils.getDatabasePassphrase(this)
        val supportFactory = net.zetetic.database.sqlcipher.SupportOpenHelperFactory(dbPassphrase)

        fun buildDatabase(): PhantomDatabase {
            return Room.databaseBuilder(
                this,
                PhantomDatabase::class.java,
                "phantom_secure.db"
            )
                .openHelperFactory(supportFactory)
                .addMigrations(MIGRATION_5_6)
                .fallbackToDestructiveMigration()
                .build()
        }

        try {
            database = buildDatabase()
            // Force open the database to trigger key validation
            database.openHelper.writableDatabase
        } catch (e: Exception) {
            android.util.Log.w("PHANTOM_INIT", "Database encryption key mismatch or format error, recreating...", e)
            try {
                database.close()
            } catch (ignored: Exception) {}
            deleteDatabase("phantom_secure.db")
            getDatabasePath("phantom_secure.db-wal").delete()
            getDatabasePath("phantom_secure.db-shm").delete()
            database = buildDatabase()
            try {
                database.openHelper.writableDatabase
            } catch (ignored: Exception) {}
        }

        repository = PhantomRepository(database.phantomDao(), database)
    }

    companion object {
        lateinit var instance: PhantomApplication
            private set

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Schema has not changed
            }
        }
    }
}
