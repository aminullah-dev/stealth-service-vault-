package com.security.stealthapp.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.security.stealthapp.data.db.dao.BookingDao
import com.security.stealthapp.data.db.dao.MessageDao
import com.security.stealthapp.data.db.dao.SecureLogDao
import com.security.stealthapp.data.db.entities.BookingEntry
import com.security.stealthapp.data.db.entities.Message
import com.security.stealthapp.data.db.entities.SecureLog
import com.security.stealthapp.security.DatabaseKeyManager
import net.sqlcipher.database.SupportFactory

@Database(
    entities = [SecureLog::class, Message::class, BookingEntry::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun secureLogDao(): SecureLogDao
    abstract fun messageDao(): MessageDao
    abstract fun bookingDao(): BookingDao

    companion object {
        private const val DB_NAME = "vault_encrypted.db"

        fun create(context: Context, keyManager: DatabaseKeyManager): AppDatabase {
            // SupportFactory hands the passphrase to SQLCipher's JNI layer;
            // it is zeroed from the JVM heap as soon as the database is opened.
            val passphrase = keyManager.getOrCreatePassphrase()
            val factory = SupportFactory(passphrase)

            return Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                DB_NAME
            )
                .openHelperFactory(factory)
                // Accept destructive recreation on schema version mismatch during
                // development; replace with proper Migration objects before release.
                .fallbackToDestructiveMigration()
                .build()
        }
    }
}
