package com.security.stealthapp

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.security.stealthapp.data.firebase.FirestoreSeeder
import com.security.stealthapp.util.NotificationHelper
import com.security.stealthapp.workers.DataErasureWorker
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltAndroidApp
class StealthApplication : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var firestoreSeeder: FirestoreSeeder

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .setMinimumLoggingLevel(android.util.Log.ERROR)
            .build()

    override fun onCreate() {
        super.onCreate()

        NotificationHelper.createChannels(this)

        CoroutineScope(Dispatchers.IO).launch {
            // Defensive: the seeder reads Firestore before the user authenticates,
            // which the security rules reject. Never let that crash app launch.
            runCatching { firestoreSeeder.seedIfEmpty() }
        }

        schedulePeriodicErasure()
    }

    private fun schedulePeriodicErasure() {
        val request = PeriodicWorkRequestBuilder<DataErasureWorker>(24, TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(false).build())
            .setInitialDelay(1, TimeUnit.HOURS)
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            DataErasureWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }
}
