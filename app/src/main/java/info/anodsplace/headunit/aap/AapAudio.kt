package info.anodsplace.headunit.aap

import android.media.AudioManager

import info.anodsplace.headunit.aap.protocol.AudioConfigs
import info.anodsplace.headunit.aap.protocol.Channel
import info.anodsplace.headunit.aap.protocol.proto.Control
import info.anodsplace.headunit.decoder.AudioDecoder
import info.anodsplace.headunit.utils.AppLog

/**
 * @author algavris
 * *
 * @date 01/10/2016.
 * *
 * *
 * @link https://github.com/google/ExoPlayer/blob/release-v2/library/src/main/java/com/google/android/exoplayer2/audio/AudioTrack.java
 */
internal class AapAudio(
        private val audioDecoder: AudioDecoder,
        private val audioManager: AudioManager) {

    fun requestFocusChange(stream: Int, focusRequest: Int, callback: AudioManager.OnAudioFocusChangeListener) {
        when (focusRequest) {
            Control.AudioFocusRequestNotification.AudioFocusRequestType.RELEASE_VALUE -> {
                audioManager.abandonAudioFocus(callback)
            }
            Control.AudioFocusRequestNotification.AudioFocusRequestType.GAIN_VALUE -> {
                // Full GAIN request - let Android handle it normally
                audioManager.requestAudioFocus(callback, stream, AudioManager.AUDIOFOCUS_GAIN)
            }
            Control.AudioFocusRequestNotification.AudioFocusRequestType.GAIN_TRANSIENT_VALUE,
            Control.AudioFocusRequestNotification.AudioFocusRequestType.GAIN_TRANSIENT_MAY_DUCK_VALUE -> {
                // TRANSIENT requests cause ducking - bypass AudioManager entirely
                // Just immediately grant focus to the phone
                val focusChange = if (focusRequest == Control.AudioFocusRequestNotification.AudioFocusRequestType.GAIN_TRANSIENT_VALUE)
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT
                else
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
                callback.onAudioFocusChange(focusChange)
            }
        }
    }

    fun process(message: AapMessage): Int {
        if (message.size >= 10) {
            decode(message.channel, 10, message.data, message.size - 10)
        }

        return 0
    }

    private fun decode(channel: Int, start: Int, buf: ByteArray, len: Int) {
        var length = len
        if (length > AUDIO_BUFS_SIZE) {
            AppLog.e { "Error audio len: $length aud_buf_BUFS_SIZE: $AUDIO_BUFS_SIZE" }
            length = AUDIO_BUFS_SIZE
        }

        if (audioDecoder.getTrack(channel) == null) {
            val config = AudioConfigs[channel] ?: error("Audio channel $channel does not have a configuration registered.")
            val stream = AudioManager.STREAM_MUSIC
            audioDecoder.start(channel, stream, config.sampleRate, config.numberOfBits, config.numberOfChannels)
        }

        audioDecoder.decode(channel, buf, start, length)
    }

    fun stopAudio(channel: Int) {
        AppLog.i { "Audio Stop: " + Channel.name(channel) }
        audioDecoder.stop(channel)
    }

    companion object {
        private const val AUDIO_BUFS_SIZE = 65536 * 4 // Up to 256 Kbytes
    }
}

