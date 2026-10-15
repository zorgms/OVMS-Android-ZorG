package com.openvehicles.OVMS.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.openvehicles.OVMS.api.ApiService
import com.openvehicles.OVMS.utils.AppPrefs
import com.openvehicles.OVMS.work.WorkManagerScheduler

/**
 * Created by balzer on 04.12.16.
 */
class AutoStart : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val appPrefs = AppPrefs(context, "ovms")
        val serviceEnabled = appPrefs.getData("option_service_enabled", "0") == "1"
        if (serviceEnabled) {
            Log.i(TAG, "Starting ApiService")
            try {
                context.startService(Intent(context, ApiService::class.java))
            } catch (e: Exception) {
                Log.e(TAG, "Can't start ApiService: ERROR", e)
            }
        }

        // Ensure WorkManager periodic poll is configured if enabled
        val periodicPollEnabled = appPrefs.getData("option_periodic_poll_enabled", "0") == "1"
        if (periodicPollEnabled) {
            Log.i(TAG, "Setting up WorkManager periodic poll")
            WorkManagerScheduler.setupPeriodicPoll(context)
        }
    }

    companion object {

        private const val TAG = "AutoStart"
    }
}
