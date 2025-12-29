package info.anodsplace.headunit.aap

import android.app.UiModeManager
import android.content.Context
import android.content.Context.UI_MODE_SERVICE
import android.media.AudioManager
import android.os.*
import android.util.SparseIntArray
import android.view.KeyEvent
import info.anodsplace.headunit.App
import info.anodsplace.headunit.aap.protocol.Channel
import info.anodsplace.headunit.aap.protocol.messages.*
import info.anodsplace.headunit.aap.protocol.proto.Input
import info.anodsplace.headunit.aap.protocol.proto.Sensors
import info.anodsplace.headunit.connection.AccessoryConnection
import info.anodsplace.headunit.connection.AccessoryConnection.Companion.CONNECT_TIMEOUT
import info.anodsplace.headunit.connection.NativeUsbAccessoryConnection
import info.anodsplace.headunit.contract.DisconnectIntent
import info.anodsplace.headunit.contract.ProjectionActivityRequest
import info.anodsplace.headunit.decoder.AudioDecoder
import info.anodsplace.headunit.decoder.MicRecorder
import info.anodsplace.headunit.decoder.VideoFrameQueue
import info.anodsplace.headunit.utils.*
import java.util.*

class AapTransport(
        audioDecoder: AudioDecoder,
        frameQueueProvider: () -> VideoFrameQueue?,
        audioManager: AudioManager,
        private val settings: Settings,
        private val context: Context)
    : Handler.Callback, MicRecorder.Listener {

    private val aapAudio: AapAudio
    private val aapVideo: AapVideo
    private val pollThread: HandlerThread = HandlerThread("AapTransport:Handler", Process.THREAD_PRIORITY_AUDIO)
    private val micRecorder: MicRecorder = MicRecorder(settings.micSampleRate, context)
    private val sessionIds = SparseIntArray(4)
    private val startedSensors = HashSet<Int>(4)
    private val ssl: AapSsl = AapSsl.INSTANCE
    private val keyCodes = settings.keyCodes.entries.associateTo(mutableMapOf()) {
        it.value to it.key
    }
    private val modeManager: UiModeManager =  context.getSystemService(UI_MODE_SERVICE) as UiModeManager
    private var connection: AccessoryConnection? = null
    private var nativeConnection: NativeUsbAccessoryConnection? = null
    private var aapRead: AapRead? = null
    private var messageHandler: AapMessageHandler? = null
    private var handler: Handler? = null
    private val pendingMessages = mutableListOf<AapMessage>()
    private var useNativeUsb = false

    val isAlive: Boolean
        get() = pollThread.isAlive

    init {
        micRecorder.listener = this
        aapAudio = AapAudio(audioDecoder, audioManager)
        aapVideo = AapVideo(frameQueueProvider)
    }

    internal fun startSensor(type: Int) {
        startedSensors.add(type)
        if (type == Sensors.SensorType.NIGHT_VALUE) {
            send(NightModeEvent(false))
        }
    }

    private var pollCount = 0

    override fun handleMessage(msg: Message): Boolean {
        return when (msg.what) {
            MSG_SEND -> {
                val size = msg.arg2
                this.sendEncryptedMessage(msg.obj as ByteArray, size)
                true
            }
            MSG_POLL -> {
                pollCount++
                if (pollCount <= 10) {
                    AppLog.e { "Poll #$pollCount starting, connection=${connection?.isConnected}" }
                }
                val ret = aapRead?.read() ?: -1
                if (pollCount <= 10) {
                    AppLog.e { "Poll #$pollCount returned: $ret" }
                }
                if (handler == null) {
                    return false
                }
                handler?.let {
                    if (!it.hasMessages(MSG_POLL)) {
                        it.sendEmptyMessage(MSG_POLL)
                    }
                }

                if (ret < 0) {
                    if (pollCount <= 5) {
                        AppLog.e { "Early poll failure (poll #$pollCount) - USB may need more time to stabilize" }
                    }
                    AppLog.e { "Poll returned error after $pollCount polls, quitting transport" }
                    this.quit()
                }
                true
            }
            else -> true
        }
    }

    private fun sendEncryptedMessage(data: ByteArray, length: Int) {
        // Encrypt from data[4] onwards
        val encryptedData = ssl.encrypt(AapMessage.HEADER_SIZE, length - AapMessage.HEADER_SIZE, data)

        // Copy data[0->4] into buffer. 3, 4 are length.
        encryptedData[0] = data[0]
        encryptedData[1] = data[1]
        Utils.intToBytes(encryptedData.size - AapMessage.HEADER_SIZE, 2, encryptedData)

        // Write 4 bytes of header and the encrypted data
        val size = connection!!.write(encryptedData)
        AppLog.d { "Sent size: $size" }
    }

    internal fun quit() {
        AppLog.i { "Transport quit - cleaning up" }
        micRecorder.listener = null
        pollThread.quit()
        aapRead = null
        handler = null
        aapVideo.reset()
        synchronized(pendingMessages) {
            pendingMessages.clear()
        }
        
        // Clean up native USB if used
        if (useNativeUsb) {
            nativeConnection?.let { conn ->
                conn.onAudioMessage = null
                conn.onVideoMessage = null
                conn.onControlMessage = null
                conn.onDisconnect = null
                conn.stopReading()
            }
            nativeConnection = null
            messageHandler = null
            useNativeUsb = false
        }
        
        // Notify that we're disconnecting - use LocalBroadcastManager for reliability
        App.provide(context).localBroadcastManager.sendBroadcast(DisconnectIntent())
        // Stop decoders
        App.provide(context).audioDecoder.stop()
        App.provide(context).videoDecoderController.stop("AapTransport::quit")
    }

    internal fun start(connection: AccessoryConnection): Boolean {
        AppLog.i { "Start Aap transport for $connection" }
        
        // Reset video state for new connection
        aapVideo.reset()

        if (!handshake(connection)) {
            AppLog.e { "Handshake failed" }
            return false
        }

        // USB connection needs time to stabilize after handshake
        // Without this delay, early poll reads may fail and critical setup messages can be lost
        // Older Android versions (especially 4.3) need longer stabilization time
        val stabilizationDelay = when {
            android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.KITKAT -> {
                AppLog.i { "Android 4.3: waiting 1000ms for USB to stabilize..." }
                1000L
            }
            android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.LOLLIPOP -> {
                AppLog.i { "Pre-Lollipop device: waiting 500ms for USB to stabilize..." }
                500L
            }
            else -> {
                AppLog.i { "Modern device: waiting 200ms for USB to stabilize..." }
                200L
            }
        }
        Thread.sleep(stabilizationDelay)

        this.connection = connection
        pollCount = 0

        // Check if this is a native USB connection
        if (connection is NativeUsbAccessoryConnection) {
            return startNativeUsb(connection)
        }
        
        // Legacy poll-based approach for non-native connections
        return startLegacyPoll(connection)
    }
    
    private fun startNativeUsb(connection: NativeUsbAccessoryConnection): Boolean {
        AppLog.i { "Starting native USB transport" }
        useNativeUsb = true
        nativeConnection = connection
        
        // Create message handler
        messageHandler = AapMessageHandlerImpl(this, micRecorder, aapAudio, aapVideo, settings, context)
        
        // Set SSL for decryption
        connection.ssl = ssl
        
        // Set up message callbacks - all callbacks route to the same handler
        // Native layer already prioritizes audio over video over control
        connection.onAudioMessage = { message ->
            try {
                messageHandler?.handle(message)
            } catch (e: Exception) {
                AppLog.e(e) { "Error handling audio message" }
            }
        }
        
        connection.onVideoMessage = { message ->
            try {
                messageHandler?.handle(message)
            } catch (e: Exception) {
                AppLog.e(e) { "Error handling video message" }
            }
        }
        
        connection.onControlMessage = { message ->
            try {
                messageHandler?.handle(message)
            } catch (e: Exception) {
                AppLog.e(e) { "Error handling control message" }
            }
        }
        
        connection.onDisconnect = {
            AppLog.i { "Native USB disconnected" }
            quit()
        }
        
        // Start the poll thread for sending messages (still needed for outbound)
        pollThread.start()
        handler = Handler(pollThread.looper, this)
        flushPendingMessages()
        
        // Start native async reading
        AppLog.i { "Calling startReading() on native connection..." }
        connection.startReading()
        AppLog.i { "startReading() returned, native USB should be reading now" }

        // Restart video decoder if surface is still available from previous connection
        App.provide(context).videoDecoderController.restartIfSurfaceAvailable()

        AppLog.i { "Native USB transport started successfully" }
        return true
    }
    
    private fun startLegacyPoll(connection: AccessoryConnection): Boolean {
        AppLog.i { "Starting legacy poll-based transport" }
        useNativeUsb = false
        
        aapRead = AapRead.Factory.create(connection, this, micRecorder, aapAudio, aapVideo, settings, context)

        pollThread.start()
        handler = Handler(pollThread.looper, this)
        AppLog.e { "Starting poll thread, sending first MSG_POLL" }
        flushPendingMessages()
        handler!!.sendEmptyMessage(MSG_POLL)
        
        // Restart video decoder if surface is still available from previous connection
        // This handles the case where activity stayed open during reconnection
        App.provide(context).videoDecoderController.restartIfSurfaceAvailable()
        
        // Create and start Transport Thread
        return true
    }

    private fun handshake(connection: AccessoryConnection): Boolean {
        val buffer = ByteArray(Messages.DEF_BUFFER_LENGTH)

        // Give the phone time to initialize Android Auto after accessory mode switch
        AppLog.i { "Waiting for phone to initialize..." }
        Thread.sleep(500)

        // Version request with retry
        AppLog.i { "Sending version request..." }
        val versionRequest = Messages.createRawMessage(0, 3, 1, Messages.VERSION_REQUEST) // Version Request

        var ret = -1
        for (attempt in 1..3) {
            ret = connection.write(versionRequest, 0, versionRequest.size, CONNECT_TIMEOUT)
            if (ret >= 0) {
                AppLog.i { "Version request sent successfully on attempt $attempt" }
                break
            }
            AppLog.e { "Version request attempt $attempt failed: ret=$ret" }
            Thread.sleep(500) // Wait before retry
        }

        if (ret < 0) {
            AppLog.e { "Version request sendEncrypted ret: $ret (all attempts failed)" }
            return false
        }

        ret = connection.read(buffer, 0, buffer.size, CONNECT_TIMEOUT)
        if (ret <= 0) {
            AppLog.e { "Version request read ret: $ret" }
            return false
        }
        // Parse and store the negotiated version
        if (!Messages.parseVersionResponse(buffer, ret)) {
            AppLog.e { "Failed to parse version response" }
            return false
        }
        AppLog.i { "Negotiated AA protocol version: ${Messages.getVersionString()}" }

        ssl.prepare()
        var handshakeCounter = 0
        val maxHandshakeRounds = 10 // Increased for compatibility with newer Android versions
        while (handshakeCounter++ < maxHandshakeRounds) {
            val sentHandshakeData = ssl.handshakeRead()

            // Check if handshake is complete (no more data to send)
            if (sentHandshakeData.isEmpty()) {
                AppLog.e { "SSL handshake complete after $handshakeCounter rounds" }
                break
            }

            val bio = Messages.createRawMessage(Channel.ID_CTR, 3, 3, sentHandshakeData)
            connection.write(bio, 0, bio.size)
            AppLog.e { "SSL handshake round $handshakeCounter - TxData size: ${sentHandshakeData.size}"}

            val size = connection.read(buffer, 0, buffer.size)
            if (size <= 0) {
                AppLog.e { "SSL handshake round $handshakeCounter - receive error: $size" }
                return false
            }

            // Log raw received data for debugging
            val rawHex = buffer.take(minOf(size, 20)).joinToString(" ") { String.format("%02X", it) }
            AppLog.e { "SSL handshake round $handshakeCounter - Raw data ($size bytes): $rawHex" }

            // Check if this looks like a TLS record (should start with 0x16 for handshake or 0x14 for ChangeCipherSpec)
            if (size > 6 && buffer[6] != 0x16.toByte() && buffer[6] != 0x14.toByte() && buffer[6] != 0x17.toByte()) {
                AppLog.e { "WARNING: Received non-TLS data! First byte after header: ${String.format("%02X", buffer[6])}" }
                // This might be an AAP error message, not TLS data
            }

            val receivedHandshakeData = ByteArray(size - 6)
            System.arraycopy(buffer, 6, receivedHandshakeData, 0, size - 6)
            AppLog.e { "SSL handshake round $handshakeCounter - RxData size: ${receivedHandshakeData.size}" }
            ssl.handshakeWrite(receivedHandshakeData)
        }

        // Check if we exited the loop without completing handshake
        if (handshakeCounter >= maxHandshakeRounds) {
            AppLog.e { "SSL handshake did NOT complete - exceeded max rounds" }
            return false
        }

        // Verify handshake actually completed
        if (ssl is AapSslImpl && !ssl.isHandshakeComplete()) {
            AppLog.e { "SSL handshake did NOT complete properly - engine still in handshake state" }
            return false
        }

        // Status = OK
        // byte ac_buf [] = {0, 3, 0, 4, 0, 4, 8, 0};
        val status = Messages.createRawMessage(0, 3, 4, byteArrayOf(8, 0))
        ret = connection.write(status, 0, status.size)
        if (ret < 0) {
            AppLog.e { "Status request sendEncrypted ret: $ret" }
            return false
        }

        AppLog.i { "Status OK sent: $ret" }
        AppLog.e { "=== HANDSHAKE COMPLETE - Transport starting ===" }

        return true
    }

    fun send(keyCode: Int, isPress: Boolean) {
        val mapped = keyCodes[keyCode] ?: keyCode
        val aapKeyCode = KeyCode.convert(mapped)

        if (mapped == KeyEvent.KEYCODE_GUIDE) { // TODO what...?
            // Hack for navigation button to simulate touch
            val action = if (isPress) Input.TouchEvent.PointerAction.TOUCH_ACTION_DOWN else Input.TouchEvent.PointerAction.TOUCH_ACTION_UP
            this.send(TouchEvent(SystemClock.elapsedRealtime(), action, 0, listOf(Triple(0, 99, 444))))
            return
        }

        if (mapped == KeyEvent.KEYCODE_N) {
            val enabled = modeManager.nightMode != UiModeManager.MODE_NIGHT_YES
            send(NightModeEvent(enabled))
            modeManager.nightMode = if (enabled) UiModeManager.MODE_NIGHT_YES else UiModeManager.MODE_NIGHT_NO
            return
        }

        if (aapKeyCode == KeyEvent.KEYCODE_UNKNOWN) {
            AppLog.i { "Unknown: $keyCode" }
        }

        val ts = SystemClock.elapsedRealtime()
        if (aapKeyCode == KeyEvent.KEYCODE_SOFT_LEFT|| aapKeyCode == KeyEvent.KEYCODE_SOFT_RIGHT) {
            if (isPress) {
                val delta = if (aapKeyCode == KeyEvent.KEYCODE_SOFT_LEFT) -1 else 1
                send(ScrollWheelEvent(ts, delta))
            }
            return
        }

        send(KeyCodeEvent(ts, aapKeyCode, isPress))
    }

    fun send(sensor: SensorEvent): Boolean {
        return if (startedSensors.contains(sensor.sensorType)) {
            send(sensor as AapMessage)
            true
        } else {
            AppLog.e { "Sensor " + sensor.sensorType + " is not started yet" }
            false
        }
    }

    fun send(message: AapMessage) {
        val h = handler
        if (h == null) {
            // Queue message to be sent once handler is ready
            synchronized(pendingMessages) {
                pendingMessages.add(message)
                if (pendingMessages.size == 1) {
                    AppLog.d { "Queuing message until handler is ready" }
                }
            }
        } else {
            AppLog.d { message.toString() }
            val msg = h.obtainMessage(MSG_SEND, 0, message.size, message.data)
            h.sendMessage(msg)
        }
    }

    private fun flushPendingMessages() {
        val h = handler ?: return
        synchronized(pendingMessages) {
            if (pendingMessages.isNotEmpty()) {
                AppLog.i { "Flushing ${pendingMessages.size} pending messages" }
                for (message in pendingMessages) {
                    val msg = h.obtainMessage(MSG_SEND, 0, message.size, message.data)
                    h.sendMessage(msg)
                }
                pendingMessages.clear()
            }
        }
    }

    internal fun gainVideoFocus() {
        context.sendBroadcast(ProjectionActivityRequest())
    }

    internal fun sendMediaAck(channel: Int) {
        send(MediaAck(channel, sessionIds.get(channel)))
    }

    internal fun setSessionId(channel: Int, sessionId: Int) {
        sessionIds.put(channel, sessionId)
    }

    override fun onMicDataAvailable(mic_buf: ByteArray, mic_audio_len: Int) {
        if (mic_audio_len > 64) { // If we read at least 64 bytes of audio data
            val length = mic_audio_len + 10
            val data = ByteArray(length)
            data[0] = Channel.ID_MIC.toByte()
            data[1] = 0x0b
            Utils.putTime(2, data, SystemClock.elapsedRealtime())
            System.arraycopy(mic_buf, 0, data, 10, mic_audio_len)
            send(AapMessage(Channel.ID_MIC, 0x0b.toByte(), -1, 2, length, data))
        }
    }

    companion object {
        private const val MSG_POLL = 1
        private const val MSG_SEND = 2
    }
}

