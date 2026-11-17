package com.boop

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat

/**
 * Listens for ACTION_POWER_CONNECTED and ACTION_POWER_DISCONNECTED.
 * When charging starts:
 *   1. Physically wakes the device screen.
 *   2. Launches Boop StandBy clock immediately.
 *   3. Starts BoopStandbyService so Boop also activates whenever the mobile goes idle while charging.
 */
class PowerConnectionReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BoopPowerReceiver"
        private const val CHANNEL_ID = "boop_activation_channel"
        private const val NOTIF_ID = 2002
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        Log.d(TAG, "Received broadcast action: $action")

        if (!BoopPreferences.isAutoLaunch(context)) {
            Log.d(TAG, "Auto-launch is disabled in preferences. Skipping.")
            return
        }

        when (action) {
            Intent.ACTION_POWER_CONNECTED -> {
                Log.d(TAG, "Charger plugged in. Waking device and activating Boop!")

                // 1. Physically turn screen ON with wake lock
                val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
                val wakeLock = pm.newWakeLock(
                    PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP or PowerManager.ON_AFTER_RELEASE,
                    "boop:power_connected_wake"
                )
                wakeLock.acquire(4000L)

                // 2. Launch MainActivity
                launchClock(context)

                // 3. Start standby service to keep watching for idle screen-off events
                try {
                    BoopStandbyService.start(context)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to start BoopStandbyService", e)
                }
            }

            Intent.ACTION_POWER_DISCONNECTED -> {
                Log.d(TAG, "Charger unplugged. Stopping background monitoring.")
                try {
                    BoopStandbyService.stop(context)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to stop BoopStandbyService", e)
                }
            }
        }
    }

    private fun launchClock(context: Context) {
        val launchIntent = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Create high priority notification channel
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Boop StandBy Activation",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Wakes display into StandBy mode when connected to power"
                setShowBadge(false)
                enableVibration(false)
                setSound(null, null)
            }
            nm.createNotificationChannel(channel)
        }

        // Full-screen intent notification to bypass lockscreen restrictions
        val notif = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Boop StandBy Clock")
            .setContentText("Activating StandBy clock")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setFullScreenIntent(pendingIntent, true)
            .setAutoCancel(true)
            .build()

        try {
            nm.notify(NOTIF_ID, notif)
        } catch (e: Exception) {
            Log.e(TAG, "Notification notify failed", e)
        }

        try {
            context.startActivity(launchIntent)
        } catch (e: Exception) {
            Log.e(TAG, "startActivity failed directly", e)
        }
    }
}
