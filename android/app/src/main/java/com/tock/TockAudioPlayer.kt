package com.tock

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Handler
import android.os.Looper
import android.util.Log

object TockAudioPlayer {

    private const val TAG = "TockAudioPlayer"

    private var soundPool: SoundPool? = null
    private var tickSoundId = 0
    private var hourSoundId = 0
    private var isLoaded = false
    private val handler = Handler(Looper.getMainLooper())

    fun init(context: Context) {
        if (soundPool != null) return
        try {
            val appContext = context.applicationContext
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA) // Ensures playback through Media stream (not muted by DND or touch sound settings)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()

            soundPool = SoundPool.Builder()
                .setMaxStreams(4)
                .setAudioAttributes(audioAttributes)
                .build().apply {
                    setOnLoadCompleteListener { _, sampleId, status ->
                        if (status == 0) {
                            isLoaded = true
                            Log.d(TAG, "Sound loaded successfully: $sampleId")
                        }
                    }
                    tickSoundId = load(appContext, R.raw.tick_minute, 1)
                    hourSoundId = load(appContext, R.raw.chime_hour, 1)
                }
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing SoundPool", e)
        }
    }

    fun playMinuteTick(context: Context) {
        if (!TockPreferences.isSoundEnabled(context)) return
        init(context)
        val pool = soundPool ?: return

        if (tickSoundId != 0) {
            val streamId = pool.play(tickSoundId, 1.0f, 1.0f, 1, 0, 1.0f)
            if (streamId == 0) {
                // Sound may still be loading, retry in 150ms
                handler.postDelayed({
                    try {
                        soundPool?.play(tickSoundId, 1.0f, 1.0f, 1, 0, 1.0f)
                    } catch (_: Exception) {}
                }, 150)
            }
        }
    }

    fun playHourChime(context: Context) {
        if (!TockPreferences.isSoundEnabled(context)) return
        init(context)
        val pool = soundPool ?: return

        if (hourSoundId != 0) {
            val streamId = pool.play(hourSoundId, 1.0f, 1.0f, 1, 0, 1.0f)
            if (streamId == 0) {
                handler.postDelayed({
                    try {
                        soundPool?.play(hourSoundId, 1.0f, 1.0f, 1, 0, 1.0f)
                    } catch (_: Exception) {}
                }, 150)
            }
        }
    }

    fun release() {
        try {
            soundPool?.release()
            soundPool = null
            isLoaded = false
            tickSoundId = 0
            hourSoundId = 0
        } catch (_: Exception) {}
    }
}
