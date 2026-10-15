package com.openvehicles.OVMS.work

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.openvehicles.OVMS.utils.AppPrefs
import java.util.concurrent.TimeUnit

/**
 * Helper to manage background work:
 * 1) Periodic short connect poll to OVMS server (alternative to persistent foreground connection).
 * 2) On-demand execution of scheduled or manual widget commands.
 */
object WorkManagerScheduler {

    private const val TAG = "WorkManagerScheduler"
    private const val UNIQUE_PERIODIC_POLL = "ovms_periodic_poll"

    /**
     * Configures the periodic poll job according to current user preferences.
     * If periodic poll is disabled, any active periodic job is cancelled.
     */
    fun setupPeriodicPoll(context: Context) {
        val appPrefs = AppPrefs(context, "ovms")
        val enabled = appPrefs.getData("option_periodic_poll_enabled", "0") == "1"
        val intervalMinutes = appPrefs.getData("option_periodic_poll_interval", "30")?.toLongOrNull() ?: 30L

        val workManager = WorkManager.getInstance(context)

        if (!enabled) {
            Log.i(TAG, "setupPeriodicPoll: Periodic poll disabled, cancelling work")
            workManager.cancelUniqueWork(UNIQUE_PERIODIC_POLL)
            return
        }

        val effectiveInterval = intervalMinutes.coerceAtLeast(15L) // WorkManager minimum is 15 minutes
        Log.i(TAG, "setupPeriodicPoll: Scheduling periodic poll every $effectiveInterval minutes")

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val periodicRequest = PeriodicWorkRequestBuilder<OvmsPeriodicPollWorker>(
            effectiveInterval,
            TimeUnit.MINUTES
        )
            .setConstraints(constraints)
            .build()

        workManager.enqueueUniquePeriodicWork(
            UNIQUE_PERIODIC_POLL,
            ExistingPeriodicWorkPolicy.UPDATE,
            periodicRequest
        )
    }

    /**
     * Cancels any active periodic poll job.
     */
    fun cancelPeriodicPoll(context: Context) {
        Log.i(TAG, "cancelPeriodicPoll: Cancelling periodic poll")
        WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_PERIODIC_POLL)
    }

    /**
     * Enqueues an on-demand command execution job.
     */
    fun enqueueCommand(
        context: Context,
        command: String,
        widgetId: Int = -1,
        title: String? = null
    ) {
        Log.i(TAG, "enqueueCommand: command=$command, widgetId=$widgetId, title=$title")
        val data = Data.Builder()
            .putString(OvmsCommandWorker.KEY_COMMAND, command)
            .putString(OvmsCommandWorker.KEY_TITLE, title ?: command)
            .putInt(OvmsCommandWorker.KEY_WIDGET_ID, widgetId)
            .build()

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val commandRequest = OneTimeWorkRequestBuilder<OvmsCommandWorker>()
            .setConstraints(constraints)
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .setInputData(data)
            .build()

        WorkManager.getInstance(context).enqueue(commandRequest)
    }
}
