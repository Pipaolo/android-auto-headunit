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

    // Flag to track if we've acquired initial audio focus
    // We only request focus once to prevent audio ducking on subsequent requests
    private var hasAcquiredFocus = false

    fun requestFocusChange(stream: Int, focusRequest: Int, callback: AudioManager.OnAudioFocusChangeListener) {
        when (focusRequest) {
            Control.AudioFocusRequestNotification.AudioFocusRequestType.RELEASE_VALUE -> {
                // Always handle release
                audioManager.abandonAudioFocus(callback)
                callback.onAudioFocusChange(AudioManager.AUDIOFOCUS_LOSS)
            }
            Control.AudioFocusRequestNotification.AudioFocusRequestType.GAIN_VALUE,
            Control.AudioFocusRequestNotification.AudioFocusRequestType.GAIN_TRANSIENT_VALUE,
            Control.AudioFocusRequestNotification.AudioFocusRequestType.GAIN_TRANSIENT_MAY_DUCK_VALUE -> {
                if (!hasAcquiredFocus) {
                    // First focus request - actually request from Android
                    AppLog.i { "Requesting initial audio focus" }
                    audioManager.requestAudioFocus(callback, stream, AudioManager.AUDIOFOCUS_GAIN)
                    hasAcquiredFocus = true
                } else {
                    // Subsequent requests - just grant immediately without involving Android
                    // This prevents audio ducking during screen interactions
                    AppLog.d { "Bypassing audio focus request (already have focus)" }
                }
                // Always notify the callback that focus is granted
                val focusChange = when (focusRequest) {
                    Control.AudioFocusRequestNotification.AudioFocusRequestType.GAIN_VALUE -> 
                        AudioManager.AUDIOFOCUS_GAIN
                    Control.AudioFocusRequestNotification.AudioFocusRequestType.GAIN_TRANSIENT_VALUE -> 
                        AudioManager.AUDIOFOCUS_GAIN_TRANSIENT
                    else -> 
                        AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
                }
                callback.onAudioFocusChange(focusChange)
            }
        }
    }
    
    /**
     * Reset audio focus state. Call this when disconnecting.
     */
    fun reset() {
        hasAcquiredFocus = false
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

