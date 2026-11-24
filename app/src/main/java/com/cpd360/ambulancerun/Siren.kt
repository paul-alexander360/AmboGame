package com.cpd360.ambulancerun

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.math.sin

// A looping wail siren made from a generated tone, so no sound file is needed.
class Siren {
    private var track: AudioTrack? = null

    fun start() {
        if (track != null) return
        try {
            val rate = RATE
            val pcm = tone
            val n = pcm.size
            val t = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(rate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setTransferMode(AudioTrack.MODE_STATIC)
                .setBufferSizeInBytes(n * 2)
                .build()
            t.write(pcm, 0, n)
            t.setLoopPoints(0, n, -1)
            t.play()
            track = t
        } catch (e: Exception) {
            track = null
        }
    }

    companion object {
        private const val RATE = 22050

        // The wail is made once, the first time it's needed, and reused for every drive
        private val tone: ShortArray by lazy {
            val n = (RATE * 3.0).toInt()
            val pcm = ShortArray(n)
            var phase = 0.0
            for (i in 0 until n) {
                val u = i.toDouble() / n
                // rise then fall: 650 Hz up to 1350 Hz and back down
                val f = 650.0 + 700.0 * sin(PI * u)
                phase += 2.0 * PI * f / RATE
                if (phase > 2.0 * PI) phase -= 2.0 * PI
                pcm[i] = (sin(phase) * 0.28 * Short.MAX_VALUE).toInt().toShort()
            }
            pcm
        }
    }

    fun stop() {
        try {
            track?.stop()
            track?.release()
        } catch (e: Exception) {
        }
        track = null
    }
}
