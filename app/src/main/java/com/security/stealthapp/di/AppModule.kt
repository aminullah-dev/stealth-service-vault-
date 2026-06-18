package com.security.stealthapp.di

import android.content.Context
import com.security.stealthapp.data.DatabaseSeeder
import com.security.stealthapp.data.db.AppDatabase
import com.security.stealthapp.data.db.dao.AppointmentDao
import com.security.stealthapp.data.db.dao.BookingDao
import com.security.stealthapp.data.db.dao.MessageDao
import com.security.stealthapp.data.db.dao.SalonDao
import com.security.stealthapp.data.db.dao.SecureLogDao
import com.security.stealthapp.data.db.dao.UserDao
import com.security.stealthapp.data.repository.AppointmentRepository
import com.security.stealthapp.data.repository.SalonRepository
import com.security.stealthapp.data.repository.VaultRepository
import com.security.stealthapp.security.DatabaseKeyManager
import com.security.stealthapp.security.PinHasher
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides @Singleton
    fun provideDatabaseKeyManager(@ApplicationContext ctx: Context): DatabaseKeyManager =
        DatabaseKeyManager(ctx)

    @Provides @Singleton
    fun providePinHasher(): PinHasher = PinHasher()

    @Provides @Singleton
    fun provideAppDatabase(
        @ApplicationContext ctx: Context,
        keyManager: DatabaseKeyManager
    ): AppDatabase = AppDatabase.create(ctx, keyManager)

    // ── DAO bindings ──────────────────────────────────────────────────────────

    @Provides @Singleton
    fun provideSecureLogDao(db: AppDatabase): SecureLogDao = db.secureLogDao()

    @Provides @Singleton
    fun provideMessageDao(db: AppDatabase): MessageDao = db.messageDao()

    @Provides @Singleton
    fun provideBookingDao(db: AppDatabase): BookingDao = db.bookingDao()

    @Provides @Singleton
    fun provideUserDao(db: AppDatabase): UserDao = db.userDao()

    @Provides @Singleton
    fun provideSalonDao(db: AppDatabase): SalonDao = db.salonDao()

    @Provides @Singleton
    fun provideAppointmentDao(db: AppDatabase): AppointmentDao = db.appointmentDao()

    // ── Repository bindings ───────────────────────────────────────────────────

    @Provides @Singleton
    fun provideVaultRepository(
        logDao: SecureLogDao,
        msgDao: MessageDao,
        bookDao: BookingDao
    ): VaultRepository = VaultRepository(logDao, msgDao, bookDao)

    @Provides @Singleton
    fun provideSalonRepository(dao: SalonDao): SalonRepository = SalonRepository(dao)

    @Provides @Singleton
    fun provideAppointmentRepository(
        dao: AppointmentDao,
        userDao: UserDao
    ): AppointmentRepository = AppointmentRepository(dao, userDao)

    // ── Seeder ────────────────────────────────────────────────────────────────

    @Provides @Singleton
    fun provideDatabaseSeeder(
        userDao: UserDao,
        salonDao: SalonDao,
        pinHasher: PinHasher
    ): DatabaseSeeder = DatabaseSeeder(userDao, salonDao, pinHasher)
}
