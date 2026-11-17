package com.boop

import android.app.KeyguardManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlin.math.abs

/**
 * Listens for ACTION_POWER_CONNECTED.
 * When charging starts, checks if:
 *   1. Phone is idle (screen off or on lock screen)
 *   2. Phone is in horizontal position (landscape dock or flat)
 * If both conditions are met, activates Boop automatically.
 */
class PowerConnectionReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BoopPowerReceiver"
        private const val CHANNEL_ID = "boop_standby_channel"
        private const val NOTIF_ID = 2001
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_POWER_CONNECTED) return
        if (!BoopPreferences.isAutoLaunch(context)) return

        Log.d(TAG, "Charger connected. Checking idle state and orientation...")

        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val km = context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager

        // Condition 1: Device is idle (screen is off or keyguard/lockscreen is locked)
        val isIdle = !pm.isInteractive || km.isKeyguardLocked
        if (!isIdle) {
            Log.d(TAG, "Device is in active foreground use (not idle). Skipping auto-launch.")
            return
        }

        // Condition 2: Check horizontal / landscape orientation via accelerometer
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val accel = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        if (accel == null) {
            Log.d(TAG, "No accelerometer available, launching clock directly.")
            launchClock(context)
            return
        }

        val handler = Handler(Looper.getMainLooper())
        var triggered = false

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                if (triggered) return
                triggered = true
                sm.unregisterListener(this)

                val x = event.values[0]
                val y = event.values[1]
                val z = event.values[2]

                // Landscape dock/kickstand: gravity along X-axis (|x| > 5.5 and |x| > |y|)
                val isLandscapeDock = abs(x) > 5.0f && abs(x) > abs(y)

                // Flat horizontal surface: gravity along Z-axis (|z| > 6.5 and |z| > |y|)
                val isFlatHorizontal = abs(z) > 6.5f && abs(z) > abs(y)

                if (isLandscapeDock || isFlatHorizontal) {
                    Log.d(TAG, "Device is horizontal (x=$x, y=$y, z=$z). Launching Boop!")
                    launchClock(context)
                } else {
                    Log.d(TAG, "Device is portrait/upright (x=$x, y=$y, z=$z). Skipping.")
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }

        sm.registerListener(listener, accel, SensorManager.SENSOR_DELAY_NORMAL, handler)

        // Safety timeout: unregister listener after 2 seconds if no sensor event
        handler.postDelayed({
            if (!triggered) {
                sm.unregisterListener(listener)
            }
        }, 2000)
    }

    private fun launchClock(context: Context) {
        val launchIntent = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
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

        // Create high priority notification channel for Android 8.0+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Boop StandBy Activation",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Wakes display into StandBy mode when charging horizontally"
                setShowBadge(false)
                enableVibration(false)
                setSound(null, null)
            }
            nm.createNotificationChannel(channel)
        }

        // Full-screen intent notification wakes the screen and brings activity forward on lockscreen
        val notif = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Boop StandBy Clock")
            .setContentText("Active while docked and charging")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
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
            Log.e(TAG, "Direct startActivity failed (using fullScreenIntent)", e)
        }
    }
}
