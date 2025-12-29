package info.anodsplace.headunit.connection

import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbManager
import info.anodsplace.headunit.aap.AapMessageIncoming
import info.anodsplace.headunit.aap.AapSsl
import info.anodsplace.headunit.aap.AapMessage
import info.anodsplace.headunit.utils.AppLog

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
    private var nativeHandle: Long = 0

    // Message handler callbacks (set by AapTransport)
    var onAudioMessage: ((AapMessage) -> Unit)? = null
    var onVideoMessage: ((AapMessage) -> Unit)? = null
    var onControlMessage: ((AapMessage) -> Unit)? = null
    var onDisconnect: (() -> Unit)? = null

    // SSL for decryption
    internal var ssl: AapSsl? = null

    fun isDeviceRunning(device: UsbDevice): Boolean {
        synchronized(this) {
            if (nativeHandle == 0L) return false
            return this.device.uniqueName == device.uniqueName
        }
    }

    override val isSingleMessage: Boolean = false

    override val isConnected: Boolean
        get() = synchronized(this) {
            nativeHandle != 0L && NativeUsb.isOpen(nativeHandle)
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

            // Open USB device via Android API to get file descriptor
            val connection = usbMgr.openDevice(device)
                ?: throw UsbOpenException("openDevice: connection is null")

            // Get file descriptor for native layer
            val fd = connection.fileDescriptor
            if (fd < 0) {
                connection.close()
                throw UsbOpenException("Invalid file descriptor: $fd")
            }

            AppLog.i { "Opening native USB with fd=$fd" }

            // Open via native libusb
            val handle = NativeUsb.open(fd)
            if (handle == 0L) {
                connection.close()
                throw UsbOpenException("Native USB open failed")
            }

            usbDeviceConnection = connection
            nativeHandle = handle

            // Set up callbacks
            setupCallbacks()

            AppLog.i { "Native USB connected successfully" }
        }
    }

    private fun setupCallbacks() {
        NativeUsb.audioCallback = { channel, data, length ->
            handleMessage(channel, data, length, onAudioMessage)
        }

        NativeUsb.videoCallback = { channel, data, length ->
            handleMessage(channel, data, length, onVideoMessage)
        }

        NativeUsb.controlCallback = { channel, data, length ->
            handleMessage(channel, data, length, onControlMessage)
        }

        NativeUsb.errorCallback = { errorCode, message ->
            AppLog.e { "USB error $errorCode: $message" }
            if (errorCode == -4) { // LIBUSB_ERROR_NO_DEVICE
                onDisconnect?.invoke()
            }
        }
    }

    private fun handleMessage(
        channel: Int,
        data: ByteArray,
        length: Int,
        callback: ((AapMessage) -> Unit)?
    ) {
        val currentSsl = ssl
        if (currentSsl == null) {
            AppLog.e { "SSL not set, cannot decrypt message" }
            return
        }

        try {
            // Parse the header from the data
            // Data format: [channel(1), flags(1), encLen(2), ...encrypted payload...]
            if (length < 4) {
                AppLog.e { "Message too short: $length" }
                return
            }

            val header = AapMessageIncoming.EncryptedHeader()
            header.buf[0] = data[0]
            header.buf[1] = data[1]
            header.buf[2] = data[2]
            header.buf[3] = data[3]
            header.decode()

            // Decrypt the payload (skip 4-byte header)
            val payloadStart = 4
            val payloadLength = length - payloadStart

            if (payloadLength != header.enc_len) {
                AppLog.e { "Payload length mismatch: expected ${header.enc_len}, got $payloadLength" }
                return
            }

            val message = AapMessageIncoming.decrypt(header, payloadStart, data, currentSsl)
            if (message != null) {
                callback?.invoke(message)
            }
        } catch (e: Exception) {
            AppLog.e(e) { "Error processing message on channel $channel" }
        }
    }

    /**
     * Start reading from USB.
     * Call this after connection is established and SSL is set up.
     */
    fun startReading() {
        synchronized(this) {
            if (nativeHandle != 0L) {
                NativeUsb.startReading(nativeHandle)
            }
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
    }

    override fun disconnect() {
        synchronized(this) {
            if (nativeHandle != 0L) {
                AppLog.i { "Disconnecting native USB" }
                NativeUsb.stopReading(nativeHandle)
                NativeUsb.close(nativeHandle)
                nativeHandle = 0
            }

            // Clear callbacks
            NativeUsb.audioCallback = null
            NativeUsb.videoCallback = null
            NativeUsb.controlCallback = null
            NativeUsb.errorCallback = null

            usbDeviceConnection?.close()
            usbDeviceConnection = null
        }
    }

    /**
     * Write data to USB.
     * Note: In the native implementation, writes are handled directly,
     * not through the AccessoryConnection interface.
     */
    override fun write(buf: ByteArray, offset: Int, length: Int, timeout: Int): Int {
        synchronized(this) {
            if (nativeHandle == 0L) {
                AppLog.e { "Cannot write: not connected" }
                return -1
            }

            // If offset is 0, we can write directly
            return if (offset == 0) {
                NativeUsb.write(nativeHandle, buf, length)
            } else {
                // Need to copy to avoid offset
                val data = buf.copyOfRange(offset, offset + length)
                NativeUsb.write(nativeHandle, data, length)
            }
        }
    }

    /**
     * Read is not used with native USB - data comes via callbacks.
     * This method exists for interface compatibility.
     */
    override fun read(buf: ByteArray, offset: Int, length: Int, timeout: Int): Int {
        // Not used - native USB delivers data via callbacks
        AppLog.w { "read() called on NativeUsbAccessoryConnection - use callbacks instead" }
        return -1
    }

    private class UsbOpenException(message: String) : Exception(message)
}
