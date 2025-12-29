package info.anodsplace.headunit.connection

import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import android.os.Process
import info.anodsplace.headunit.aap.AapMessageIncoming
import info.anodsplace.headunit.aap.AapSsl
import info.anodsplace.headunit.aap.AapMessage
import info.anodsplace.headunit.aap.Utils
import info.anodsplace.headunit.aap.protocol.Channel
import info.anodsplace.headunit.aap.protocol.messages.Messages
import info.anodsplace.headunit.utils.AppLog
import info.anodsplace.headunit.utils.MessageDispatcher
import java.nio.BufferUnderflowException
import java.nio.ByteBuffer

/**
 * USB connection using Android's Java USB API with polling.
 *
 * This implementation uses synchronous bulkTransfer for both read and write,
 * matching the proven approach used by HeadUnit Reloaded. A dedicated polling
 * thread reads USB data and dispatches messages via callbacks.
 *
 * Key design decisions:
 * - Uses Android's bulkTransfer API (not libusb) for maximum compatibility
 * - Simple polling thread for reading (no complex async transfers)
 * - Messages dispatched via callbacks for decoupled processing
 * - Audio/video bypass dispatcher for lowest latency
 * - Control messages go through dispatcher
 */
class UsbAccessoryConnection(
    private val usbMgr: UsbManager,
    private val device: UsbDevice
) : AccessoryConnection {

    private var usbDeviceConnection: UsbDeviceConnection? = null
    private var usbInterface: UsbInterface? = null
    private var inEndpoint: UsbEndpoint? = null
    private var outEndpoint: UsbEndpoint? = null

    // Polling thread for reading USB data
    @Volatile private var pollThread: Thread? = null
    @Volatile private var running = false

    // Message dispatcher for control messages (decoupled processing)
    private val dispatcher = MessageDispatcher()

    // Message handler callbacks (set by AapTransport)
    var onAudioMessage: ((AapMessage) -> Unit)? = null
    var onVideoMessage: ((AapMessage) -> Unit)? = null
    var onControlMessage: ((AapMessage) -> Unit)? = null
        set(value) {
            field = value
            dispatcher.setCallback(MessageDispatcher.Type.CONTROL, value)
        }
    var onDisconnect: (() -> Unit)? = null

    // SSL for decryption (set by AapTransport after handshake)
    internal var ssl: AapSsl? = null

    // Read buffer - 16KB matches USB bulk transfer size
    private val readBuffer = ByteArray(16384)

    // Message parsing buffers
    private val fifo = ByteBuffer.allocate(Messages.DEF_BUFFER_LENGTH * 2)
    private val recvHeader = AapMessageIncoming.EncryptedHeader()

    // Error tracking for resilience
    private var consecutiveErrors = 0
    private companion object {
        const val MAX_CONSECUTIVE_ERRORS = 20
        const val INITIAL_RETRY_DELAY_MS = 50L
        const val MAX_RETRY_DELAY_MS = 500L
        const val READ_TIMEOUT_MS = 1000
    }

    fun isDeviceRunning(device: UsbDevice): Boolean {
        synchronized(this) {
            if (usbDeviceConnection == null) return false
            return this.device.uniqueName == device.uniqueName
        }
    }

    override val isSingleMessage: Boolean = false

    override val isConnected: Boolean
        get() = synchronized(this) { usbDeviceConnection != null }

    override fun connect(listener: AccessoryConnection.Listener) {
        try {
            connectInternal()
            listener.onConnectionResult(true)
        } catch (e: Exception) {
            AppLog.e(e) { "Failed to connect via USB" }
            listener.onConnectionResult(false)
        }
    }

    private fun connectInternal() {
        synchronized(this) {
            disconnect()

            val connection = usbMgr.openDevice(device)
                ?: throw UsbOpenException("openDevice: connection is null")

            try {
                val interfaceCount = device.interfaceCount
                if (interfaceCount <= 0) {
                    throw UsbOpenException("No USB interfaces")
                }

                val iface = device.getInterface(0)
                if (!connection.claimInterface(iface, true)) {
                    throw UsbOpenException("Error claiming interface")
                }

                var foundIn: UsbEndpoint? = null
                var foundOut: UsbEndpoint? = null
                for (i in 0 until iface.endpointCount) {
                    val ep = iface.getEndpoint(i)
                    if (ep.direction == UsbConstants.USB_DIR_IN) {
                        if (foundIn == null) foundIn = ep
                    } else {
                        if (foundOut == null) foundOut = ep
                    }
                }

                if (foundIn == null) throw UsbOpenException("Could not find IN endpoint")
                if (foundOut == null) throw UsbOpenException("Could not find OUT endpoint")

                usbDeviceConnection = connection
                usbInterface = iface
                inEndpoint = foundIn
                outEndpoint = foundOut

                AppLog.i { "USB connected successfully" }
                AppLog.i { "  IN endpoint: address=${foundIn.address}, maxPacketSize=${foundIn.maxPacketSize}" }
                AppLog.i { "  OUT endpoint: address=${foundOut.address}, maxPacketSize=${foundOut.maxPacketSize}" }

            } catch (e: Exception) {
                connection.close()
                throw if (e is UsbOpenException) e else UsbOpenException(e.message ?: "Unknown error")
            }
        }
    }

    /**
     * Start polling USB for data.
     * Call this after handshake is complete and SSL is set up.
     */
    fun startReading() {
        synchronized(this) {
            if (running) return
            if (usbDeviceConnection == null) {
                AppLog.e { "Cannot start reading: not connected" }
                return
            }

            // Clear FIFO for clean start
            synchronized(fifo) {
                fifo.clear()
            }
            consecutiveErrors = 0

            running = true
            dispatcher.start()

            pollThread = Thread({
                Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
                AppLog.i { "USB poll thread started" }
                pollLoop()
                AppLog.i { "USB poll thread stopped" }
            }, "AAP-USB-Poll").also { it.start() }
        }
    }

    /**
     * Main polling loop - reads USB data and dispatches messages.
     */
    private fun pollLoop() {
        while (running) {
            try {
                val conn = usbDeviceConnection
                val endpoint = inEndpoint
                if (conn == null || endpoint == null) {
                    AppLog.e { "Connection lost in poll loop" }
                    break
                }

                // Synchronous USB read using Android's bulkTransfer
                val bytesRead = conn.bulkTransfer(endpoint, readBuffer, 0, readBuffer.size, READ_TIMEOUT_MS)

                when {
                    bytesRead > 0 -> {
                        // Data received - reset error counter and process
                        if (consecutiveErrors > 0) {
                            AppLog.i { "USB read recovered after $consecutiveErrors errors" }
                        }
                        consecutiveErrors = 0
                        handleRawData(readBuffer, bytesRead)
                    }
                    bytesRead == 0 -> {
                        // Timeout with no data - normal, continue polling
                    }
                    bytesRead < 0 -> {
                        // Error
                        consecutiveErrors++
                        AppLog.e { "USB read error: $bytesRead (consecutive: $consecutiveErrors/$MAX_CONSECUTIVE_ERRORS)" }

                        if (consecutiveErrors >= MAX_CONSECUTIVE_ERRORS) {
                            AppLog.e { "Max consecutive errors reached, disconnecting" }
                            running = false
                            onDisconnect?.invoke()
                            break
                        }

                        // Brief delay before retry with exponential backoff
                        val delay = minOf(
                            INITIAL_RETRY_DELAY_MS * (1 shl minOf(consecutiveErrors - 1, 4)),
                            MAX_RETRY_DELAY_MS
                        )
                        Thread.sleep(delay)
                    }
                }
            } catch (e: InterruptedException) {
                // Normal shutdown
                break
            } catch (e: Exception) {
                AppLog.e(e) { "Error in USB poll loop" }
                consecutiveErrors++
                if (consecutiveErrors >= MAX_CONSECUTIVE_ERRORS) {
                    running = false
                    onDisconnect?.invoke()
                    break
                }
            }
        }
    }

    /**
     * Handle raw USB data - parse AAP messages.
     */
    private fun handleRawData(data: ByteArray, length: Int) {
        val currentSsl = ssl
        if (currentSsl == null) {
            AppLog.e { "SSL not set, cannot process data" }
            return
        }

        // Collect messages to decrypt
        data class PendingMessage(
            val channel: Int,
            val flags: Int,
            val encryptedData: ByteArray,
            val encLen: Int
        )
        val pendingMessages = mutableListOf<PendingMessage>()

        synchronized(fifo) {
            if (fifo.remaining() < length) {
                AppLog.e { "FIFO buffer overflow, clearing buffer" }
                fifo.clear()
                return
            }

            fifo.put(data, 0, length)
            fifo.flip()

            while (fifo.hasRemaining()) {
                fifo.mark()

                if (fifo.remaining() < 4) {
                    fifo.reset()
                    break
                }

                try {
                    fifo.get(recvHeader.buf, 0, 4)
                } catch (e: BufferUnderflowException) {
                    fifo.reset()
                    break
                }

                recvHeader.decode()

                // Handle first fragment marker
                if (recvHeader.flags == 0x09) {
                    if (fifo.remaining() < 4) {
                        fifo.reset()
                        break
                    }
                    val sizeBuf = ByteArray(4)
                    fifo.get(sizeBuf, 0, 4)
                    val totalSize = Utils.bytesToInt(sizeBuf, 0, false)
                    AppLog.d { "First fragment total_size: $totalSize" }
                }

                if (fifo.remaining() < recvHeader.enc_len) {
                    fifo.reset()
                    break
                }

                val encryptedCopy = ByteArray(recvHeader.enc_len)
                try {
                    fifo.get(encryptedCopy, 0, recvHeader.enc_len)
                } catch (e: BufferUnderflowException) {
                    fifo.reset()
                    break
                }

                pendingMessages.add(PendingMessage(
                    channel = recvHeader.chan,
                    flags = recvHeader.flags,
                    encryptedData = encryptedCopy,
                    encLen = recvHeader.enc_len
                ))
            }

            fifo.compact()
        }

        // Decrypt and dispatch outside the lock
        val decryptHeader = AapMessageIncoming.EncryptedHeader()
        for (pending in pendingMessages) {
            try {
                decryptHeader.chan = pending.channel
                decryptHeader.flags = pending.flags
                decryptHeader.enc_len = pending.encLen

                val msg = AapMessageIncoming.decrypt(decryptHeader, 0, pending.encryptedData, currentSsl)
                if (msg != null) {
                    when {
                        isVideoChannel(pending.channel) -> onVideoMessage?.invoke(msg)
                        isAudioChannel(pending.channel) -> onAudioMessage?.invoke(msg)
                        else -> dispatcher.dispatch(MessageDispatcher.Type.CONTROL, pending.channel, msg)
                    }
                } else {
                    AppLog.e { "Decrypt failed: chan=${pending.channel} ${Channel.name(pending.channel)} flags=${pending.flags.toString(16)}" }
                }
            } catch (e: Exception) {
                AppLog.e(e) { "Error decrypting message on channel ${pending.channel}" }
            }
        }
    }

    private fun isAudioChannel(channel: Int) = channel in 4..6
    private fun isVideoChannel(channel: Int) = channel == 2

    fun stopReading() {
        running = false
        pollThread?.interrupt()
        try {
            pollThread?.join(1000)
        } catch (e: InterruptedException) {
            // Ignore
        }
        pollThread = null
        dispatcher.stop()
    }

    override fun disconnect() {
        synchronized(this) {
            stopReading()

            val conn = usbDeviceConnection
            val iface = usbInterface
            if (conn != null) {
                if (iface != null) {
                    conn.releaseInterface(iface)
                }
                conn.close()
            }
            usbDeviceConnection = null
            usbInterface = null
            inEndpoint = null
            outEndpoint = null
        }
    }

    override fun write(buf: ByteArray, offset: Int, length: Int, timeout: Int): Int {
        val conn = usbDeviceConnection
        val endpoint = outEndpoint
        if (conn == null || endpoint == null) {
            AppLog.e { "Cannot write: not connected" }
            return -1
        }

        return try {
            conn.bulkTransfer(endpoint, buf, offset, length, timeout)
        } catch (e: Exception) {
            AppLog.e(e) { "USB write exception" }
            -1
        }
    }

    override fun read(buf: ByteArray, offset: Int, length: Int, timeout: Int): Int {
        val conn = usbDeviceConnection
        val endpoint = inEndpoint
        if (conn == null || endpoint == null) {
            AppLog.e { "Cannot read: not connected" }
            return -1
        }

        return try {
            conn.bulkTransfer(endpoint, buf, offset, length, timeout)
        } catch (e: Exception) {
            AppLog.e(e) { "USB read exception" }
            -1
        }
    }

    private class UsbOpenException(message: String) : Exception(message)
}
