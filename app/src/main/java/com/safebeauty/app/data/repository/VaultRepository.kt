package com.safebeauty.app.data.repository

import com.safebeauty.app.data.db.dao.BookingDao
import com.safebeauty.app.data.db.dao.MessageDao
import com.safebeauty.app.data.db.dao.SecureLogDao
import com.safebeauty.app.data.db.entities.BookingEntry
import com.safebeauty.app.data.db.entities.Message
import com.safebeauty.app.data.db.entities.SecureLog
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VaultRepository @Inject constructor(
    private val logDao: SecureLogDao,
    private val messageDao: MessageDao,
    private val bookingDao: BookingDao
) {

    // ── Secure Logs ───────────────────────────────────────────────────────────

    val activeLogs: Flow<List<SecureLog>> = logDao.observeActiveLogs()

    suspend fun log(eventType: String, details: String): Long =
        logDao.insert(SecureLog(eventType = eventType, details = details))

    /** Soft-delete logs older than [windowMs] milliseconds, then hard-purge them. */
    suspend fun sweepLogs(windowMs: Long): Int {
        val cutoff = System.currentTimeMillis() - windowMs
        val count = logDao.softDeleteOlderThan(cutoff)
        logDao.purgeErased()
        return count
    }

    // ── Messages ──────────────────────────────────────────────────────────────

    val activeMessages: Flow<List<Message>> = messageDao.observeActiveMessages()

    fun conversationWith(userId: String): Flow<List<Message>> =
        messageDao.observeConversation(userId)

    suspend fun sendMessage(message: Message): Long =
        messageDao.insert(message)

    suspend fun sweepMessages(windowMs: Long): Int {
        val cutoff = System.currentTimeMillis() - windowMs
        val count = messageDao.softDeleteOlderThan(cutoff)
        messageDao.purgeErased()
        return count
    }

    // ── Bookings ──────────────────────────────────────────────────────────────

    val activeBookings: Flow<List<BookingEntry>> = bookingDao.observeActiveBookings()

    fun bookingsByCategory(category: String): Flow<List<BookingEntry>> =
        bookingDao.observeByCategory(category)

    suspend fun createBooking(booking: BookingEntry): Long =
        bookingDao.insert(booking)

    suspend fun updateBooking(booking: BookingEntry): Int =
        bookingDao.update(booking)

    suspend fun sweepBookings(windowMs: Long): Int {
        val cutoff = System.currentTimeMillis() - windowMs
        val count = bookingDao.softDeleteOlderThan(cutoff)
        bookingDao.purgeErased()
        return count
    }
}
