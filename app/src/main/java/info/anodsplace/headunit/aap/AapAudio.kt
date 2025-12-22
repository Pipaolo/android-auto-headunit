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
        private val audioDecoder: AudioDecoder) {

    fun requestFocusChange(stream: Int, focusRequest: Int, callback: AudioManager.OnAudioFocusChangeListener) {
        // Immediately grant focus without actually requesting from Android AudioManager.
        // This prevents Android from ducking/lowering other audio when the phone requests focus
        // (e.g., for touch feedback sounds or navigation prompts during screen interaction).
        when (focusRequest) {
            Control.AudioFocusRequestNotification.AudioFocusRequestType.RELEASE_VALUE -> {
                callback.onAudioFocusChange(AudioManager.AUDIOFOCUS_LOSS)
            }
            Control.AudioFocusRequestNotification.AudioFocusRequestType.GAIN_VALUE -> {
                callback.onAudioFocusChange(AudioManager.AUDIOFOCUS_GAIN)
            }
            Control.AudioFocusRequestNotification.AudioFocusRequestType.GAIN_TRANSIENT_VALUE -> {
                callback.onAudioFocusChange(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            }
            Control.AudioFocusRequestNotification.AudioFocusRequestType.GAIN_TRANSIENT_MAY_DUCK_VALUE -> {
                callback.onAudioFocusChange(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
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

