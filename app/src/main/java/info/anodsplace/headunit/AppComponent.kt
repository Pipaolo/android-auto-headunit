package info.anodsplace.headunit

import android.app.Application
import android.media.AudioManager
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import info.anodsplace.headunit.aap.AapTransport
import info.anodsplace.headunit.decoder.AudioDecoder
import info.anodsplace.headunit.decoder.VideoDecoderController
import info.anodsplace.headunit.utils.Settings

class AppComponent(private val app: App) {

    private var _transport: AapTransport? = null
    val transport: AapTransport
        get() {
            if (_transport == null) {
               _transport = AapTransport(
                   audioDecoder,
                   { videoDecoderController.getFrameQueue() },
                   audioManager,
                   settings,
                   app
               )
            }
            return _transport!!
        }

    val settings = Settings(app)
    val videoDecoderController = VideoDecoderController()
    val audioDecoder = AudioDecoder()

    fun resetTransport() {
        _transport?.quit()
        _transport = null
    }

    private val audioManager: AudioManager
        get() = app.getSystemService(Application.AUDIO_SERVICE) as AudioManager

    val localBroadcastManager = LocalBroadcastManager.getInstance(app)
}
