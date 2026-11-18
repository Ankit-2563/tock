package com.tock

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.SoundPool
import android.util.Log

object TockAudioPlayer {

    private const val TAG = "TockAudioPlayer"

    private var soundPool: SoundPool? = null
    private var tickSoundId = 0
    private var hourSoundId = 0
    @Volatile private var isLoaded = false

    fun init(context: Context) {
        if (soundPool != null) return
        try {
            val appContext = context.applicationContext
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()

            soundPool = SoundPool.Builder()
                .setMaxStreams(4)
                .setAudioAttributes(audioAttributes)
                .build().apply {
                    setOnLoadCompleteListener { _, sampleId, status ->
                        if (status == 0) {
                            isLoaded = true
                            Log.d(TAG, "SoundPool sample loaded: $sampleId")
                        }
                    }
                    tickSoundId = load(appContext, R.raw.tick_minute, 1)
                    hourSoundId = load(appContext, R.raw.chime_hour, 1)
                }
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing SoundPool", e)
        }
    }

    /** Plays gentle, acoustic ambient clock tick when a minute passes. */
    fun playMinuteTick(context: Context) {
        if (!TockPreferences.isSoundEnabled(context)) return
        val appContext = context.applicationContext
        init(appContext)

        var played = false
        val pool = soundPool
        if (pool != null && tickSoundId != 0 && isLoaded) {
            try {
                // Soft, ambient surround sound level (not loud or distracting)
                val streamId = pool.play(tickSoundId, 0.48f, 0.48f, 1, 0, 1.0f)
                if (streamId != 0) played = true
            } catch (_: Exception) {}
        }

        // Guaranteed fallback via MediaPlayer if SoundPool has not finished loading or fails
        if (!played) {
            try {
                MediaPlayer.create(appContext, R.raw.tick_minute)?.apply {
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build()
                    )
                    setVolume(0.48f, 0.48f)
                    setOnCompletionListener { mp -> mp.release() }
                    start()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error playing minute tick via MediaPlayer", e)
            }
        }
    }

    /** Plays warm harmonic bell chime when an hour completes. */
    fun playHourChime(context: Context) {
        if (!TockPreferences.isSoundEnabled(context)) return
        val appContext = context.applicationContext
        init(appContext)

        var played = false
        val pool = soundPool
        if (pool != null && hourSoundId != 0 && isLoaded) {
            try {
                val streamId = pool.play(hourSoundId, 0.52f, 0.52f, 1, 0, 1.0f)
                if (streamId != 0) played = true
            } catch (_: Exception) {}
        }

        // Guaranteed fallback via MediaPlayer if SoundPool has not finished loading or fails
        if (!played) {
            try {
                MediaPlayer.create(appContext, R.raw.chime_hour)?.apply {
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build()
                    )
                    setVolume(0.52f, 0.52f)
                    setOnCompletionListener { mp -> mp.release() }
                    start()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error playing hour chime via MediaPlayer", e)
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
