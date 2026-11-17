package com.boop

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat

/**
 * Lightweight background monitor running while device is connected to power.
 * When the mobile goes idle (screen turns off), it wakes the display and launches Boop.
 */
class BoopStandbyService : Service() {

    companion object {
        private const val TAG = "BoopStandbyService"
        private const val CHANNEL_ID = "boop_service_channel"
        private const val NOTIF_ID = 3001

        fun start(context: Context) {
            val intent = Intent(context, BoopStandbyService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                try {
                    context.startForegroundService(intent)
                } catch (e: Exception) {
                    Log.e(TAG, "startForegroundService failed, starting normal service", e)
                    context.startService(intent)
                }
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, BoopStandbyService::class.java))
        }
    }

    private val idleReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    // Mobile went idle. If still charging, activate Boop!
                    if (isDeviceCharging()) {
                        Log.d(TAG, "Screen turned off while charging. Waking device into Boop StandBy!")
                        wakeAndLaunch(context)
                    }
                }
                Intent.ACTION_POWER_DISCONNECTED -> {
                    Log.d(TAG, "Power disconnected. Stopping Standby service.")
                    stopSelf()
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        startAsForeground()

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
        }
        registerReceiver(idleReceiver, filter)
    }

    private fun startAsForeground() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Boop StandBy Background Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Monitors charger and idle state to activate StandBy clock"
                setShowBadge(false)
            }
            nm.createNotificationChannel(channel)
        }

        val launchIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        )

        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Boop StandBy")
            .setContentText("Ready to activate when docked or charging")
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        startForeground(NOTIF_ID, notification)
    }

    private fun isDeviceCharging(): Boolean {
        val batteryIntent = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return false
        val status = batteryIntent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val plugged = batteryIntent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)
        return status == BatteryManager.BATTERY_STATUS_CHARGING ||
               status == BatteryManager.BATTERY_STATUS_FULL ||
               plugged != 0
    }

    private fun wakeAndLaunch(context: Context) {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val wakeLock = pm.newWakeLock(
            PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP or PowerManager.ON_AFTER_RELEASE,
            "boop:idle_wake"
        )
        wakeLock.acquire(3000L)

        val launchIntent = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }

        try {
            context.startActivity(launchIntent)
        } catch (e: Exception) {
            Log.e(TAG, "startActivity failed from idle service", e)
        }
    }

    override fun onDestroy() {
        try {
            unregisterReceiver(idleReceiver)
        } catch (_: Exception) {}
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
