// Engineered by uncoalesced
package com.uncoalesced.impart.presentation.intrusion

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log

// Plays the emergency tone on the alarm stream (the one stream Do Not Disturb never mutes)
// and vibrates until stopped. Every step is best-effort and logged: a failure to make noise
// must never prevent the red screen from rendering.
class IntrusionAlarm(context: Context) {
    private val appContext = context.applicationContext
    private val audioManager = appContext.getSystemService(AudioManager::class.java)

    private var player: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var previousAlarmVolume: Int? = null

    fun start() {
        if (player != null) return
        raiseAlarmVolume()
        startAudio()
        startVibration()
    }

    fun stop() {
        player?.let { current ->
            try {
                if (current.isPlaying) current.stop()
            } catch (e: IllegalStateException) {
                Log.w(TAG, "Alarm player was not in a stoppable state", e)
            }
            current.release()
        }
        player = null
        vibrator?.cancel()
        vibrator = null
        restoreAlarmVolume()
    }

    private fun startAudio() {
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        for (uri in candidateUris()) {
            val candidate = MediaPlayer()
            try {
                candidate.setAudioAttributes(attributes)
                candidate.setDataSource(appContext, uri)
                candidate.isLooping = true
                candidate.prepare()
                candidate.start()
                player = candidate
                return
            } catch (e: Exception) {
                Log.w(TAG, "Alarm source $uri unusable, trying the next one", e)
                candidate.release()
            }
        }
        Log.e(TAG, "No playable alarm sound on this device; intrusion screen is silent")
    }

    private fun candidateUris(): List<Uri> = listOf(
        RingtoneManager.TYPE_ALARM,
        RingtoneManager.TYPE_RINGTONE,
        RingtoneManager.TYPE_NOTIFICATION
    ).mapNotNull { RingtoneManager.getDefaultUri(it) }

    private fun startVibration() {
        try {
            val target = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                appContext.getSystemService(VibratorManager::class.java).defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                appContext.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }
            if (!target.hasVibrator()) return
            val effect = VibrationEffect.createWaveform(VIBRATION_PATTERN_MS, 0)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                target.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_ALARM))
            } else {
                @Suppress("DEPRECATION")
                target.vibrate(
                    effect,
                    AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build()
                )
            }
            vibrator = target
        } catch (e: Exception) {
            Log.w(TAG, "Vibration unavailable", e)
        }
    }

    // A panic at alarm volume zero is a panic nobody hears. Restored on stop().
    private fun raiseAlarmVolume() {
        try {
            val current = audioManager.getStreamVolume(AudioManager.STREAM_ALARM)
            val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
            if (current < max) {
                previousAlarmVolume = current
                audioManager.setStreamVolume(AudioManager.STREAM_ALARM, max, 0)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not raise alarm volume", e)
        }
    }

    private fun restoreAlarmVolume() {
        val previous = previousAlarmVolume ?: return
        previousAlarmVolume = null
        try {
            audioManager.setStreamVolume(AudioManager.STREAM_ALARM, previous, 0)
        } catch (e: Exception) {
            Log.w(TAG, "Could not restore alarm volume", e)
        }
    }

    private companion object {
        const val TAG = "IntrusionAlarm"
        val VIBRATION_PATTERN_MS = longArrayOf(0, 800, 400)
    }
}
