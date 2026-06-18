package com.security.stealthapp.di

import android.content.Context
import com.security.stealthapp.data.db.AppDatabase
import com.security.stealthapp.data.db.dao.BookingDao
import com.security.stealthapp.data.db.dao.MessageDao
import com.security.stealthapp.data.db.dao.SecureLogDao
import com.security.stealthapp.data.repository.VaultRepository
import com.security.stealthapp.security.DatabaseKeyManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabaseKeyManager(
        @ApplicationContext context: Context
    ): DatabaseKeyManager = DatabaseKeyManager(context)

    @Provides
    @Singleton
    fun provideAppDatabase(
        @ApplicationContext context: Context,
        keyManager: DatabaseKeyManager
    ): AppDatabase = AppDatabase.create(context, keyManager)

    @Provides
    @Singleton
    fun provideSecureLogDao(db: AppDatabase): SecureLogDao = db.secureLogDao()

    @Provides
    @Singleton
    fun provideMessageDao(db: AppDatabase): MessageDao = db.messageDao()

    @Provides
    @Singleton
    fun provideBookingDao(db: AppDatabase): BookingDao = db.bookingDao()

    @Provides
    @Singleton
    fun provideVaultRepository(
        logDao: SecureLogDao,
        messageDao: MessageDao,
        bookingDao: BookingDao
    ): VaultRepository = VaultRepository(logDao, messageDao, bookingDao)
}
