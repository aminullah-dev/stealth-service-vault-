package com.security.stealthapp.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.security.stealthapp.data.db.converters.Converters
import com.security.stealthapp.data.db.dao.AppointmentDao
import com.security.stealthapp.data.db.dao.BookingDao
import com.security.stealthapp.data.db.dao.MessageDao
import com.security.stealthapp.data.db.dao.SalonDao
import com.security.stealthapp.data.db.dao.SecureLogDao
import com.security.stealthapp.data.db.dao.UserDao
import com.security.stealthapp.data.db.entities.Appointment
import com.security.stealthapp.data.db.entities.BookingEntry
import com.security.stealthapp.data.db.entities.Message
import com.security.stealthapp.data.db.entities.Salon
import com.security.stealthapp.data.db.entities.SecureLog
import com.security.stealthapp.data.db.entities.User
import com.security.stealthapp.security.DatabaseKeyManager
import net.sqlcipher.database.SupportFactory

@Database(
    entities = [
        // ── Existing ──────────────────
        SecureLog::class,
        Message::class,
        BookingEntry::class,
        // ── New live-architecture ──────
        User::class,
        Salon::class,
        Appointment::class
    ],
    version      = 2,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    // Existing DAOs
    abstract fun secureLogDao(): SecureLogDao
    abstract fun messageDao(): MessageDao
    abstract fun bookingDao(): BookingDao

    // New DAOs
    abstract fun userDao(): UserDao
    abstract fun salonDao(): SalonDao
    abstract fun appointmentDao(): AppointmentDao

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
                // Version bump from 1 → 2. Destructive migration is acceptable here
                // because the DB is encrypted and all data can be re-seeded.
                .fallbackToDestructiveMigration()
                .build()
        }
    }
}
