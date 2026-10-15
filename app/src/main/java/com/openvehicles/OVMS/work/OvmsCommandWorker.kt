package com.openvehicles.OVMS.work

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.openvehicles.OVMS.R
import com.openvehicles.OVMS.api.OvmsShortClient
import com.openvehicles.OVMS.appwidgets.ApiWidget
import com.openvehicles.OVMS.utils.AppPrefs
import com.openvehicles.OVMS.utils.CarsStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * WorkManager worker for executing on-demand OVMS commands (scheduled or triggered from widget).
 * If the persistent ApiService is currently online, delegates through it.
 * Otherwise, performs an on-demand short connect to the OVMS server, sends the command,
 * waits for the response, updates widget data, and disconnects.
 */
class OvmsCommandWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    companion object {
        private const val TAG = "OvmsCommandWorker"
        const val KEY_COMMAND = "key_command"
        const val KEY_TITLE = "key_title"
        const val KEY_WIDGET_ID = "key_widget_id"
        private const val NOTIFICATION_ID = 2001
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        val title = inputData.getString(KEY_TITLE) ?: "Command"
        val notification = NotificationCompat.Builder(applicationContext, "info")
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle(applicationContext.getString(R.string.app_name))
            .setContentText(applicationContext.getString(R.string.executing_command, title))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        return ForegroundInfo(NOTIFICATION_ID, notification)
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val command = inputData.getString(KEY_COMMAND)
        val title = inputData.getString(KEY_TITLE) ?: "Command"

        if (command.isNullOrEmpty()) {
            Log.w(TAG, "No command provided to OvmsCommandWorker")
            return@withContext Result.failure()
        }

        val appPrefs = AppPrefs(applicationContext, "ovms")
        val commandsEnabled = appPrefs.getData("option_commands_enabled", "0") == "1"
        if (!commandsEnabled) {
            Log.w(TAG, "Commands are disabled in app settings (option_commands_enabled=0)")
            return@withContext Result.failure()
        }

        val carData = CarsStorage.getSelectedCarData()
        if (carData == null) {
            Log.w(TAG, "No car selected for command execution")
            return@withContext Result.failure()
        }

        Log.i(TAG, "Executing command '$command' ($title) for vehicle ${carData.sel_vehicleid}")

        // If ApiService is currently running and online, delegate through it:
        val apikey = appPrefs.getData("APIKey")
        if (ApiWidget.isServiceOnline) {
            Log.i(TAG, "ApiService is online, routing command through active ApiService")
            val intent = Intent("com.openvehicles.OVMS.SendCommand").apply {
                setPackage(applicationContext.packageName)
                putExtra("command", command)
                putExtra("apikey", apikey)
            }
            applicationContext.sendBroadcast(intent)
            return@withContext Result.success()
        }

        // Otherwise execute directly via short client connection:
        val response = OvmsShortClient.executeCommand(applicationContext, carData, command)
        Log.i(TAG, "Command execution finished with response: $response")

        Handler(Looper.getMainLooper()).post {
            val message = if (response != null) {
                applicationContext.getString(R.string.command_executed_success, title)
            } else {
                applicationContext.getString(R.string.command_execution_failed, title)
            }
            Toast.makeText(applicationContext, message, Toast.LENGTH_SHORT).show()
        }

        if (response != null) {
            Result.success()
        } else {
            Result.retry()
        }
    }
}
