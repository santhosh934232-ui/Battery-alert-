package com.batteryalert.app

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Handler
import android.os.Looper

/** Plays a sound on the alarm stream (looping) and stops it automatically after a duration. */
class AlertPlayer(context: Context) {

    private val appContext = context.applicationContext
    private val handler = Handler(Looper.getMainLooper())
    private val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val stopRunnable = Runnable { stop() }
    private val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ALARM)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

    private var player: MediaPlayer? = null
    private var focusRequest: AudioFocusRequest? = null

    val isPlaying: Boolean get() = player != null

    fun play(soundUri: String?, seconds: Int) {
        stop()

        val candidates = listOfNotNull(
            soundUri?.let { Uri.parse(it) },
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
        )
        var prepared: MediaPlayer? = null
        for (uri in candidates) {
            prepared = prepare(uri)
            if (prepared != null) break
        }
        val mediaPlayer = prepared ?: return

        player = mediaPlayer
        requestFocus()
        try {
            mediaPlayer.start()
        } catch (e: IllegalStateException) {
            stop()
            return
        }
        handler.postDelayed(stopRunnable, seconds * 1000L)
    }

    fun stop() {
        handler.removeCallbacks(stopRunnable)
        player?.let {
            try {
                if (it.isPlaying) it.stop()
            } catch (e: IllegalStateException) {
                // Already stopped.
            }
            it.release()
        }
        player = null
        abandonFocus()
    }

    private fun prepare(uri: Uri): MediaPlayer? {
        val mediaPlayer = MediaPlayer()
        return try {
            mediaPlayer.setAudioAttributes(attributes)
            mediaPlayer.setDataSource(appContext, uri)
            mediaPlayer.isLooping = true
            mediaPlayer.prepare()
            mediaPlayer
        } catch (e: Exception) {
            mediaPlayer.release()
            null
        }
    }

    private fun requestFocus() {
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(attributes)
            .build()
        focusRequest = request
        audioManager.requestAudioFocus(request)
    }

    private fun abandonFocus() {
        focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        focusRequest = null
    }
}
