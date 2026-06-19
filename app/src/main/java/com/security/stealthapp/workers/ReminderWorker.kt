package com.security.stealthapp.workers

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.security.stealthapp.util.NotificationHelper
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class ReminderWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    companion object {
        const val KEY_TITLE = "title"
        const val KEY_BODY  = "body"
    }

    override suspend fun doWork(): Result {
        val title = inputData.getString(KEY_TITLE) ?: return Result.success()
        val body  = inputData.getString(KEY_BODY)  ?: return Result.success()
        NotificationHelper.showBookingUpdate(applicationContext, title, body)
        return Result.success()
    }
}
