package com.rootfix.app.service

import android.content.Context
import android.util.Log
import androidx.work.*
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
            // Autonomous Canary fetch via autopif engine
            Log.i(TAG, "Executing autonomous AutoPIF canary sync...")
            val (success, profile) = pifRepo.runAutoPif(
                cacheDir = applicationContext.cacheDir,
                device = null, // auto-selects latest verified Canary device
                restartGmsNow = true
            )

            if (success && profile != null) {
                Log.i(TAG, "Autonomous sync successful: ${profile.fingerprint} with all 7 spoofs active")
                Result.success()
            } else {
                // Fallback to presets if autopif script failed (e.g. offline)
                val available = pifRepo.getAvailableProfiles(fetchRemote = true)
                if (available.isNotEmpty()) {
                    pifRepo.applyProfile(
                        cacheDir = applicationContext.cacheDir,
                        profile = available.first().withAllSpoofsEnabled(),
                        restartGmsNow = true
                    )
                    Result.success()
                } else {
                    Result.retry()
                }
            }
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
                15, TimeUnit.MINUTES
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
