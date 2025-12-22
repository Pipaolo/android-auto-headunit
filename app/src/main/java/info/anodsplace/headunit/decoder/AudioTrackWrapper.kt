package info.anodsplace.headunit.decoder

import android.media.AudioFormat
import android.media.AudioTrack

import info.anodsplace.headunit.utils.AppLog

class AudioTrackWrapper(stream: Int, sampleRateInHz: Int, bitDepth: Int, channelCount: Int) {
    private val audioTrack: AudioTrack

    init {
        audioTrack = createAudioTrack(stream, sampleRateInHz, bitDepth, channelCount)
        audioTrack.play()
    }

    private fun createAudioTrack(stream: Int, sampleRateInHz: Int, bitDepth: Int, channelCount: Int): AudioTrack {
        val channelConfig = if (channelCount == 2) AudioFormat.CHANNEL_OUT_STEREO else AudioFormat.CHANNEL_OUT_MONO
        val dataFormat = if (bitDepth == 16) AudioFormat.ENCODING_PCM_16BIT else AudioFormat.ENCODING_PCM_8BIT
        val bufferSize = AudioBuffer.getSize(sampleRateInHz, channelConfig, dataFormat, 2 * channelCount)
        AppLog.i { "Audio stream: $stream buffer size: $bufferSize sampleRateInHz: $sampleRateInHz channelCount: $channelCount" }
        return AudioTrack(stream, sampleRateInHz, channelConfig, dataFormat, bufferSize, AudioTrack.MODE_STREAM)
    }

    fun write(buffer: ByteArray, offset: Int, size: Int): Int {
        return audioTrack.write(buffer, offset, size)
    }

    fun stop() {
        if (audioTrack.playState == AudioTrack.PLAYSTATE_PLAYING) {
            audioTrack.pause()
        }
        val toRelease = audioTrack
        // AudioTrack.release can take some time, so we call it on a background thread.
        Thread({
            try {
                toRelease.flush()
                toRelease.release()
            } catch (e: Exception) {
                // Ignore exceptions during cleanup
            }
        }, "AudioTrack-Release").start()
    }

    private object AudioBuffer {
        /**
         * A minimum length for the [android.media.AudioTrack] buffer, in microseconds.
         * 50ms provides low latency for real-time A/V sync.
         */
        private const val MIN_BUFFER_DURATION_US: Long = 50 * 1000

        /**
         * A multiplication factor to apply to the minimum buffer size requested by the underlying
         * [android.media.AudioTrack]. Lower value = lower latency.
         */
        private const val BUFFER_MULTIPLICATION_FACTOR = 2
        /**
         * A maximum length for the [android.media.AudioTrack] buffer, in microseconds.
         * 100ms caps latency while preventing underruns on slower devices.
         */
        private const val MAX_BUFFER_DURATION_US: Long = 100 * 1000

        /**
         * The number of microseconds in one second.
         */
        private const val MICROS_PER_SECOND: Long = 1000 * 1000

        internal fun getSize(sampleRate: Int, channelConfig: Int, audioFormat: Int, pcmFrameSize: Int): Int {
            val minBufferSize = AudioTrack.getMinBufferSize(sampleRate, channelConfig, audioFormat)
            val multipliedBufferSize = minBufferSize * BUFFER_MULTIPLICATION_FACTOR
            val minAppBufferSize = durationUsToFrames(MIN_BUFFER_DURATION_US, sampleRate) * pcmFrameSize
            val maxAppBufferSize = Math.max(minBufferSize, durationUsToFrames(MAX_BUFFER_DURATION_US, sampleRate) * pcmFrameSize)
            return when {
                multipliedBufferSize < minAppBufferSize -> minAppBufferSize
                multipliedBufferSize > maxAppBufferSize -> maxAppBufferSize
                else -> multipliedBufferSize
            }
        }

        private fun durationUsToFrames(durationUs: Long, sampleRate: Int): Int {
            return (durationUs * sampleRate / MICROS_PER_SECOND).toInt()
        }
    }
}
