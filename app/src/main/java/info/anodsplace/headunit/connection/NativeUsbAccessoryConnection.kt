package info.anodsplace.headunit.connection

import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbManager
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
 * USB connection using native libusb for high-performance async I/O.
 *
 * Unlike the legacy UsbAccessoryConnection, this class:
 * - Uses async USB transfers (no blocking)
 * - Dispatches messages by priority (audio > video > control)
 * - Delivers messages via callbacks instead of polling
 *
 * The native layer parses message headers and routes by channel type.
 * Kotlin handles TLS decryption and message processing.
 */
class NativeUsbAccessoryConnection(
    private val usbMgr: UsbManager,
    private val device: UsbDevice
) : AccessoryConnection {

    private var usbDeviceConnection: UsbDeviceConnection? = null
    private var usbInterface: android.hardware.usb.UsbInterface? = null
    private var inEndpoint: android.hardware.usb.UsbEndpoint? = null
    private var outEndpoint: android.hardware.usb.UsbEndpoint? = null
    private var nativeHandle: Long = 0
    private var useNativeForIO = false  // Start with Android API for handshake

    // Message dispatcher for decoupled processing
    private val dispatcher = MessageDispatcher()

    // Message handler callbacks (set by AapTransport)
    // Audio and Control go through dispatcher for decoupled processing
    // Video bypasses dispatcher - it has its own optimized VideoFrameQueue
    var onAudioMessage: ((AapMessage) -> Unit)? = null
        set(value) {
            field = value
            dispatcher.setCallback(MessageDispatcher.Type.AUDIO, value)
        }
    var onVideoMessage: ((AapMessage) -> Unit)? = null  // Called directly, not through dispatcher
    var onControlMessage: ((AapMessage) -> Unit)? = null
        set(value) {
            field = value
            dispatcher.setCallback(MessageDispatcher.Type.CONTROL, value)
        }
    var onDisconnect: (() -> Unit)? = null

    // SSL for decryption
    internal var ssl: AapSsl? = null

    // Message parsing buffers (similar to AapReadMultipleMessages)
    private val fifo = ByteBuffer.allocate(Messages.DEF_BUFFER_LENGTH * 2)
    private val recvHeader = AapMessageIncoming.EncryptedHeader()
    private val msgBuffer = ByteArray(65535)

    fun isDeviceRunning(device: UsbDevice): Boolean {
        synchronized(this) {
            if (nativeHandle == 0L) return false
            return this.device.uniqueName == device.uniqueName
        }
    }

    override val isSingleMessage: Boolean = false

    override val isConnected: Boolean
        get() = synchronized(this) {
            usbDeviceConnection != null
        }

    override fun connect(listener: AccessoryConnection.Listener) {
        try {
            connectInternal()
            listener.onConnectionResult(true)
        } catch (e: Exception) {
            AppLog.e(e) { "Failed to connect via native USB" }
            listener.onConnectionResult(false)
        }
    }

    private fun connectInternal() {
        synchronized(this) {
            disconnect()

            // Open USB device via Android API
            val connection = usbMgr.openDevice(device)
                ?: throw UsbOpenException("openDevice: connection is null")

            try {
                // Get interface and claim it
                val interfaceCount = device.interfaceCount
                if (interfaceCount <= 0) {
                    throw UsbOpenException("No USB interfaces")
                }
                val iface = device.getInterface(0)
                if (!connection.claimInterface(iface, true)) {
                    throw UsbOpenException("Error claiming interface")
                }

                // Find endpoints
                var foundIn: android.hardware.usb.UsbEndpoint? = null
                var foundOut: android.hardware.usb.UsbEndpoint? = null
                for (i in 0 until iface.endpointCount) {
                    val ep = iface.getEndpoint(i)
                    if (ep.direction == android.hardware.usb.UsbConstants.USB_DIR_IN) {
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
                useNativeForIO = false  // Use Android API for handshake

                AppLog.i { "USB connected successfully via Android API" }
                AppLog.i { "  IN endpoint: address=${foundIn.address}, maxPacketSize=${foundIn.maxPacketSize}" }
                AppLog.i { "  OUT endpoint: address=${foundOut.address}, maxPacketSize=${foundOut.maxPacketSize}" }

                // Set up callbacks for later native use
                setupCallbacks()

            } catch (e: Exception) {
                connection.close()
                throw if (e is UsbOpenException) e else UsbOpenException(e.message ?: "Unknown error")
            }
        }
    }

    private fun setupCallbacks() {
        // Native layer sends raw USB data, we parse it here
        NativeUsb.rawDataCallback = { data, length ->
            handleRawData(data, length)
        }

        NativeUsb.errorCallback = { errorCode, message ->
            AppLog.e { "USB error $errorCode: $message" }
            if (errorCode == -4) { // LIBUSB_ERROR_NO_DEVICE
                onDisconnect?.invoke()
            }
        }
    }

    /**
     * Pending encrypted message data for decryption outside the FIFO lock.
     */
    private data class PendingMessage(
        val channel: Int,
        val flags: Int,
        val encryptedData: ByteArray,
        val encLen: Int
    )

    /**
     * Handle raw USB data - parse AAP messages from it.
     * Uses the same proven logic as AapReadMultipleMessages.
     * 
     * CRITICAL: Decryption and dispatch happen OUTSIDE the synchronized block
     * to prevent blocking USB data delivery. Only FIFO buffer operations are locked.
     */
    private fun handleRawData(data: ByteArray, length: Int) {
        val currentSsl = ssl
        if (currentSsl == null) {
            AppLog.e { "SSL not set, cannot process data" }
            return
        }

        // Collect encrypted payloads inside the lock, decrypt outside
        val pendingMessages = mutableListOf<PendingMessage>()

        synchronized(fifo) {
            // Add new data to FIFO buffer
            if (fifo.remaining() < length) {
                AppLog.e { "FIFO buffer overflow, dropping $length bytes" }
                return
            }
            fifo.put(data, 0, length)
            fifo.flip()

            // Parse headers and collect encrypted payloads (NO decryption here)
            while (fifo.hasRemaining()) {
                fifo.mark()

                // Try to read 4-byte header
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

                // Handle first fragment marker (flags 0x09)
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

                // Read encrypted payload
                if (fifo.remaining() < recvHeader.enc_len) {
                    fifo.reset()
                    break
                }

                // Copy encrypted data for decryption outside the lock
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

            // Compact buffer (move unprocessed data to beginning)
            fifo.compact()
        }

        // OUTSIDE THE LOCK: Decrypt and dispatch all messages
        // TLS decryption must still be sequential, but we're not blocking FIFO access
        for (pending in pendingMessages) {
            try {
                // Reconstruct header for decrypt
                recvHeader.chan = pending.channel
                recvHeader.flags = pending.flags
                recvHeader.enc_len = pending.encLen

                val msg = AapMessageIncoming.decrypt(recvHeader, 0, pending.encryptedData, currentSsl)
                if (msg != null) {
                    // Dispatch based on channel type
                    when {
                        isVideoChannel(pending.channel) -> onVideoMessage?.invoke(msg)
                        isAudioChannel(pending.channel) -> dispatcher.dispatch(MessageDispatcher.Type.AUDIO, pending.channel, msg)
                        else -> dispatcher.dispatch(MessageDispatcher.Type.CONTROL, pending.channel, msg)
                    }
                } else {
                    AppLog.e { "Decrypt failed: chan=${pending.channel} ${Channel.name(pending.channel)} flags=${pending.flags.toString(16)} enc_len=${pending.encLen}" }
                }
            } catch (e: Exception) {
                AppLog.e(e) { "Error decrypting message on channel ${pending.channel}" }
            }
        }
    }

    private fun isAudioChannel(channel: Int): Boolean {
        // Audio channels: 4 (media audio output), 5 (speech audio), 6 (system audio)
        return channel in 4..6
    }

    private fun isVideoChannel(channel: Int): Boolean {
        // Video channel: 2
        return channel == 2
    }

    /**
     * Start async reading from USB via native layer.
     * Call this after connection is established and SSL is set up.
     * This switches from Android API to native libusb for I/O.
     */
    fun startReading() {
        synchronized(this) {
            val conn = usbDeviceConnection
            if (conn == null) {
                AppLog.e { "Cannot start reading: not connected" }
                return
            }

            // Clear the FIFO buffer for a clean start
            synchronized(fifo) {
                fifo.clear()
            }

            // Initialize native USB if not already done
            if (nativeHandle == 0L) {
                val fd = conn.fileDescriptor
                if (fd < 0) {
                    AppLog.e { "Invalid file descriptor: $fd" }
                    return
                }

                AppLog.i { "Initializing native USB with fd=$fd" }
                val handle = NativeUsb.open(fd)
                if (handle == 0L) {
                    AppLog.e { "Failed to open native USB" }
                    return
                }
                nativeHandle = handle
                useNativeForIO = true
                AppLog.i { "Native USB initialized, handle=$handle" }
            }

            // Start message dispatcher threads
            dispatcher.start()

            NativeUsb.startReading(nativeHandle)
            AppLog.i { "Native USB reading started" }
        }
    }

    /**
     * Stop reading from USB.
     */
    fun stopReading() {
        synchronized(this) {
            if (nativeHandle != 0L) {
                NativeUsb.stopReading(nativeHandle)
            }
        }
        // Stop dispatcher threads (outside synchronized to avoid deadlock)
        dispatcher.stop()
    }

    override fun disconnect() {
        synchronized(this) {
            // Stop native USB if active
            if (nativeHandle != 0L) {
                AppLog.i { "Disconnecting native USB" }
                NativeUsb.stopReading(nativeHandle)
                NativeUsb.close(nativeHandle)
                nativeHandle = 0
            }

            // Clear callbacks
            NativeUsb.rawDataCallback = null
            NativeUsb.errorCallback = null

            // Release Android USB resources
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
            useNativeForIO = false
        }
    }

    /**
     * Write data to USB.
     * Uses Android API during handshake, native USB after startReading().
     */
    override fun write(buf: ByteArray, offset: Int, length: Int, timeout: Int): Int {
        synchronized(this) {
            val conn = usbDeviceConnection
            if (conn == null) {
                AppLog.e { "Cannot write: not connected" }
                return -1
            }

            return if (useNativeForIO && nativeHandle != 0L) {
                // Use native USB for streaming
                if (offset == 0) {
                    NativeUsb.write(nativeHandle, buf, length)
                } else {
                    val data = buf.copyOfRange(offset, offset + length)
                    NativeUsb.write(nativeHandle, data, length)
                }
            } else {
                // Use Android API for handshake
                try {
                    conn.bulkTransfer(outEndpoint, buf, offset, length, timeout)
                } catch (e: Exception) {
                    AppLog.e(e) { "USB write exception" }
                    -1
                }
            }
        }
    }

    /**
     * Synchronous read for handshake phase.
     * After handshake, data comes via async callbacks instead.
     */
    override fun read(buf: ByteArray, offset: Int, length: Int, timeout: Int): Int {
        synchronized(this) {
            val conn = usbDeviceConnection
            if (conn == null) {
                AppLog.e { "Cannot read: not connected" }
                return -1
            }

            return if (useNativeForIO && nativeHandle != 0L) {
                // Use native USB
                if (offset == 0) {
                    NativeUsb.read(nativeHandle, buf, length, timeout)
                } else {
                    val temp = ByteArray(length)
                    val result = NativeUsb.read(nativeHandle, temp, length, timeout)
                    if (result > 0) {
                        System.arraycopy(temp, 0, buf, offset, result)
                    }
                    result
                }
            } else {
                // Use Android API for handshake
                try {
                    conn.bulkTransfer(inEndpoint, buf, offset, length, timeout)
                } catch (e: Exception) {
                    AppLog.e(e) { "USB read exception" }
                    -1
                }
            }
        }
    }

    private class UsbOpenException(message: String) : Exception(message)
}
