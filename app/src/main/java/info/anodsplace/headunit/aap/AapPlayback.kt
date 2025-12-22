package info.anodsplace.headunit.aap

import android.content.Context
import android.content.Intent
import info.anodsplace.headunit.aap.protocol.proto.MediaPlayback
import info.anodsplace.headunit.utils.AppLog

/**
 * Handles MUSIC_PLAYBACK channel messages including album art and metadata.
 *
 * The phone sends media metadata (song, artist, album, album art) on this channel
 * using media frame flags (8, 9, 10) similar to video frames.
 */
internal class AapPlayback(private val context: Context) {

    companion object {
        const val ACTION_METADATA_UPDATE = "info.anodsplace.headunit.METADATA_UPDATE"
        const val EXTRA_SONG = "song"
        const val EXTRA_ARTIST = "artist"
        const val EXTRA_ALBUM = "album"
        const val EXTRA_ALBUM_ART = "albumart"
        const val EXTRA_DURATION = "duration"

        const val ACTION_PLAYBACK_STATUS = "info.anodsplace.headunit.PLAYBACK_STATUS"
        const val EXTRA_STATE = "state"
    }

    private var lastSong: String? = null
    private var lastArtist: String? = null

    fun process(message: AapMessage) {
        val msgType = message.type

        try {
            when (msgType) {
                MediaPlayback.PlaybackMsgType.MSG_PLAYBACK_METADATA_VALUE -> {
                    processMetadata(message)
                }
                MediaPlayback.PlaybackMsgType.MSG_PLAYBACK_STARTRESPONSE_VALUE -> {
                    AppLog.d { "Playback start response received" }
                }
                MediaPlayback.PlaybackMsgType.MSG_PLAYBACK_METADATASTART_VALUE -> {
                    AppLog.d { "Playback metadata start received" }
                }
                else -> {
                    // Unknown playback message type - this is normal for data frames
                    // that contain raw album art without protobuf wrapping
                    AppLog.d { "Playback data frame received, size=${message.size}" }
                }
            }
        } catch (e: Exception) {
            // Parsing failed - likely raw binary data (album art chunk)
            AppLog.d { "Playback binary data received, size=${message.size}" }
        }
    }

    private fun processMetadata(message: AapMessage) {
        try {
            val metadata = message.parse(MediaPlayback.MediaMetaData.newBuilder()).build()

            val song = if (metadata.hasSong()) metadata.song else null
            val artist = if (metadata.hasArtist()) metadata.artist else null
            val album = if (metadata.hasAlbum()) metadata.album else null
            val duration = if (metadata.hasDuration()) metadata.duration else 0
            val hasAlbumArt = metadata.hasAlbumart() && metadata.albumart.size() > 0

            // Only log if something changed
            if (song != lastSong || artist != lastArtist) {
                AppLog.i { "Now playing: $song by $artist from $album (${duration}s)" }
                if (hasAlbumArt) {
                    AppLog.d { "Album art received: ${metadata.albumart.size()} bytes" }
                }
                lastSong = song
                lastArtist = artist
            }

            // Broadcast metadata update for any UI that wants to display it
            val intent = Intent(ACTION_METADATA_UPDATE).apply {
                song?.let { putExtra(EXTRA_SONG, it) }
                artist?.let { putExtra(EXTRA_ARTIST, it) }
                album?.let { putExtra(EXTRA_ALBUM, it) }
                putExtra(EXTRA_DURATION, duration)
                if (hasAlbumArt) {
                    putExtra(EXTRA_ALBUM_ART, metadata.albumart.toByteArray())
                }
            }
            context.sendBroadcast(intent)

        } catch (e: Exception) {
            AppLog.e { "Failed to parse metadata: ${e.message}" }
        }
    }

    fun processStatus(message: AapMessage) {
        try {
            val status = message.parse(MediaPlayback.MediaPlaybackStatus.newBuilder()).build()
            val state = if (status.hasState()) status.state.name else "UNKNOWN"
            AppLog.i { "Playback status: $state" }

            val intent = Intent(ACTION_PLAYBACK_STATUS).apply {
                putExtra(EXTRA_STATE, state)
            }
            context.sendBroadcast(intent)
        } catch (e: Exception) {
            AppLog.e { "Failed to parse playback status: ${e.message}" }
        }
    }
}
