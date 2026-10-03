package com.rootfix.app.service

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.rootfix.app.data.repository.PifRepository
import com.rootfix.app.data.repository.RootExecutor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

class PifSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    private val pifRepo = PifRepository()

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        Log.d(TAG, "Starting autonomous PIF background sync")

        if (!RootExecutor.isRootAvailable()) {
            Log.e(TAG, "Root permission not available during background sync")
            return@withContext Result.failure()
        }

        try {
            val currentProfile = pifRepo.getActiveProfile()
            val available = pifRepo.getAvailableProfiles(fetchRemote = true)

            if (available.isNotEmpty()) {
                val latest = available.first()
                if (currentProfile == null || currentProfile.fingerprint != latest.fingerprint) {
                    Log.i(TAG, "New fingerprint detected: ${latest.fingerprint}. Applying automatically...")
                    val success = pifRepo.applyProfile(
                        cacheDir = applicationContext.cacheDir,
                        profile = latest,
                        restartGmsNow = true
                    )
                    if (success) {
                        Log.i(TAG, "PIF updated and GMS restarted successfully")
                    }
                } else {
                    Log.d(TAG, "Current fingerprint is up to date: ${currentProfile.fingerprint}")
                }
            }
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Error during autonomous PIF sync", e)
            Result.retry()
        }
    }

    companion object {
        private const val TAG = "PifSyncWorker"
        private const val UNIQUE_WORK_NAME = "pif_autonomous_sync_worker"

        fun schedulePeriodicSync(context: Context, intervalHours: Long = 12) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(true)
                .build()

            val workRequest = PeriodicWorkRequestBuilder<PifSyncWorker>(
                intervalHours, TimeUnit.HOURS,
                15, TimeUnit.MINUTES // flex window
            )
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                workRequest
            )
            Log.i(TAG, "Periodic sync scheduled every $intervalHours hours")
        }

        fun cancelPeriodicSync(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_WORK_NAME)
            Log.i(TAG, "Periodic sync cancelled")
        }
    }
}
