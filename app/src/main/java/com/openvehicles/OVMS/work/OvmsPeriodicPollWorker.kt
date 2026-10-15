package com.openvehicles.OVMS.work

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.openvehicles.OVMS.api.OvmsShortClient
import com.openvehicles.OVMS.utils.CarsStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * WorkManager worker for performing periodic short server connects to OVMS.
 * Fetches latest status, environment, and location, updates widget data, and disconnects.
 * Does not keep a persistent foreground service alive.
 */
class OvmsPeriodicPollWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    companion object {
        private const val TAG = "OvmsPeriodicPollWorker"
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Starting periodic poll")
            val carData = CarsStorage.getSelectedCarData()
            if (carData == null) {
                Log.w(TAG, "No car selected for periodic poll")
                return@withContext Result.success()
            }

            val success = OvmsShortClient.fetchLatestData(applicationContext, carData)
            Log.d(TAG, "Periodic poll completed. Success: $success")
            if (success) {
                Result.success()
            } else {
                Result.retry()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in OvmsPeriodicPollWorker", e)
            Result.retry()
        }
    }
}
