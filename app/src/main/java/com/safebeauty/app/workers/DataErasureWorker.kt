package com.safebeauty.app.workers

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.safebeauty.app.data.repository.VaultRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Runs every 24 hours (scheduled in [SafeBeautyApplication]).
 * Soft-deletes records older than 24 h, then hard-purges the soft-deleted rows
 * so no plain-text fragments linger on the SQLCipher pages.
 */
@HiltWorker
class DataErasureWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val repository: VaultRepository
) : CoroutineWorker(appContext, params) {

    companion object {
        const val WORK_NAME = "data_erasure_periodic"
        private const val WINDOW_MS = 24L * 60 * 60 * 1000 // 24 hours
    }

    override suspend fun doWork(): Result {
        return try {
            val logsSwept     = repository.sweepLogs(WINDOW_MS)
            val msgsSwept     = repository.sweepMessages(WINDOW_MS)
            val bookingsSwept = repository.sweepBookings(WINDOW_MS)

            repository.log(
                eventType = "AUTO_ERASURE",
                details   = "logs=$logsSwept msgs=$msgsSwept bookings=$bookingsSwept"
            )

            Result.success()
        } catch (e: Exception) {
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }
}
