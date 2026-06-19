package com.security.stealthapp.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.security.stealthapp.data.db.dao.BookingDao
import com.security.stealthapp.data.db.dao.FavoriteSalonDao
import com.security.stealthapp.data.db.dao.MessageDao
import com.security.stealthapp.data.db.dao.SalonCacheDao
import com.security.stealthapp.data.db.dao.SecureLogDao
import com.security.stealthapp.data.db.entities.BookingEntry
import com.security.stealthapp.data.db.entities.FavoriteSalonEntity
import com.security.stealthapp.data.db.entities.Message
import com.security.stealthapp.data.db.entities.SalonCacheEntity
import com.security.stealthapp.data.db.entities.SecureLog
import com.security.stealthapp.security.DatabaseKeyManager
import net.sqlcipher.database.SupportFactory

// v4: Added SalonCacheEntity for offline browsing.
// v5: Added FavoriteSalonEntity for device-local saved salons.
@Database(
    entities  = [SecureLog::class, Message::class, BookingEntry::class, SalonCacheEntity::class, FavoriteSalonEntity::class],
    version   = 5,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun secureLogDao(): SecureLogDao
    abstract fun messageDao(): MessageDao
    abstract fun bookingDao(): BookingDao
    abstract fun salonCacheDao(): SalonCacheDao
    abstract fun favoriteSalonDao(): FavoriteSalonDao

    companion object {
        private const val DB_NAME = "vault_encrypted.db"

        fun create(context: Context, keyManager: DatabaseKeyManager): AppDatabase {
            val passphrase = keyManager.getOrCreatePassphrase()
            val factory    = SupportFactory(passphrase)

            return Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                DB_NAME
            )
                .openHelperFactory(factory)
                .fallbackToDestructiveMigration()
                .build()
        }
    }
}
