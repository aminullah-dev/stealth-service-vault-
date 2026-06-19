package com.security.stealthapp.di

import android.content.Context
import com.security.stealthapp.data.db.AppDatabase
import com.security.stealthapp.data.db.dao.BookingDao
import com.security.stealthapp.data.db.dao.MessageDao
import com.security.stealthapp.data.db.dao.SecureLogDao
import com.security.stealthapp.data.firebase.FirebaseAuthManager
import com.security.stealthapp.data.firebase.FirestoreRepository
import com.security.stealthapp.data.firebase.FirestoreSeeder
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

    @Provides @Singleton
    fun provideSecureLogDao(db: AppDatabase): SecureLogDao = db.secureLogDao()

    @Provides @Singleton
    fun provideMessageDao(db: AppDatabase): MessageDao = db.messageDao()

    @Provides @Singleton
    fun provideBookingDao(db: AppDatabase): BookingDao = db.bookingDao()

    @Provides @Singleton
    fun provideVaultRepository(
        logDao: SecureLogDao,
        msgDao: MessageDao,
        bookDao: BookingDao
    ): VaultRepository = VaultRepository(logDao, msgDao, bookDao)

    @Provides @Singleton
    fun provideFirebaseAuthManager(): FirebaseAuthManager = FirebaseAuthManager()

    @Provides @Singleton
    fun provideFirestoreRepository(): FirestoreRepository = FirestoreRepository()

    @Provides @Singleton
    fun provideFirestoreSeeder(
        repo: FirestoreRepository,
        auth: FirebaseAuthManager,
        pinHasher: PinHasher
    ): FirestoreSeeder = FirestoreSeeder(repo, auth, pinHasher)
}
