package com.moez.QKSMS.common.util

import android.content.Context
import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/** A short notification sound independent of whether a visual notification is posted. */
@Singleton
open class SpamSoundPlayer @Inject constructor(private val context: Context) {
    private var current: Ringtone? = null
    private val handler = Handler(Looper.getMainLooper())

    @Synchronized
    open fun play(uri: Uri) {
        try {
            current?.stop()
            val sound = RingtoneManager.getRingtone(context, uri) ?: return
            sound.audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION_COMMUNICATION_INSTANT)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
            if (Build.VERSION.SDK_INT >= 28) sound.isLooping = false
            current = sound
            sound.play()
            handler.postDelayed({
                synchronized(this) {
                    if (current === sound) { sound.stop(); current = null }
                }
            }, 30_000L)
        } catch (error: Exception) { Timber.w(error, "Unable to play spam alert sound") }
    }
}
