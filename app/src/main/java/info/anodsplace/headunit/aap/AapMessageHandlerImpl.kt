package info.anodsplace.headunit.aap

import android.content.Context
import info.anodsplace.headunit.aap.protocol.Channel
import info.anodsplace.headunit.aap.protocol.proto.MediaPlayback
import info.anodsplace.headunit.decoder.MicRecorder
import info.anodsplace.headunit.utils.AppLog
import info.anodsplace.headunit.utils.Settings
import info.anodsplace.headunit.utils.bytesToHex
import java.lang.Exception


internal class AapMessageHandlerImpl (
        private val transport: AapTransport,
        recorder: MicRecorder,
        private val aapAudio: AapAudio,
        private val aapVideo: AapVideo,
        settings: Settings,
        context: Context) : AapMessageHandler {

    private val aapControl: AapControl = AapControlGateway(transport, recorder, aapAudio, settings, context)
    private val aapPlayback: AapPlayback = AapPlayback(context)

    @Throws(AapMessageHandler.HandleException::class)
    override fun handle(message: AapMessage) {
        val msgType = message.type
        val flags = message.flags

        when {
            message.isAudio && (msgType == 0 || msgType == 1) -> {
                transport.sendMediaAck(message.channel)
                aapAudio.process(message)
                // 300 ms @ 48000/sec   samples = 14400     stereo 16 bit results in bytes = 57600
            }
            message.isVideo && (msgType == 0 || msgType == 1 || flags.toInt() == 8 || flags.toInt() == 9 || flags.toInt() == 10) -> {
                transport.sendMediaAck(message.channel)
                aapVideo.process(message)
            }
            // MUSIC_PLAYBACK channel receives album art and metadata with media frame flags
            message.channel == Channel.ID_MPB && (flags.toInt() == 8 || flags.toInt() == 9 || flags.toInt() == 10) -> {
                transport.sendMediaAck(message.channel)
                aapPlayback.process(message)
            }
            msgType in 0..31 || msgType in 32768..32799 || msgType in 65504..65535 -> try {
                aapControl.execute(message)
            } catch (e: Exception) {
                AppLog.e(e)
                throw AapMessageHandler.HandleException(e)
            }
            else -> AppLog.e {
                "Unknown msg_type $msgType on channel ${Channel.name(message.channel)}, flags=${flags.toInt()}, size=${message.size}"
            }
        }
    }
}
