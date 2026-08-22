package com.safebeauty.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.safebeauty.app.data.db.dao.BookingDao
import com.safebeauty.app.data.db.dao.FavoriteSalonDao
import com.safebeauty.app.data.db.dao.MessageDao
import com.safebeauty.app.data.db.dao.SalonCacheDao
import com.safebeauty.app.data.db.dao.SecureLogDao
import com.safebeauty.app.data.db.entities.BookingEntry
import com.safebeauty.app.data.db.entities.FavoriteSalonEntity
import com.safebeauty.app.data.db.entities.Message
import com.safebeauty.app.data.db.entities.SalonCacheEntity
import com.safebeauty.app.data.db.entities.SecureLog
import com.safebeauty.app.security.DatabaseKeyManager
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

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
            // Unlike the old android-database-sqlcipher, the sqlcipher-android
            // artifact does not self-load its native library — do it explicitly
            // before opening the DB. loadLibrary is idempotent, so a repeat call
            // is a safe no-op.
            System.loadLibrary("sqlcipher")
            val passphrase = keyManager.getOrCreatePassphrase()
            val factory    = SupportOpenHelperFactory(passphrase)

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
