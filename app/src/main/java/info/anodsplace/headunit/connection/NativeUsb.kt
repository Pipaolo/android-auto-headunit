package info.anodsplace.headunit.connection

import info.anodsplace.headunit.utils.AppLog

/**
 * JNI bindings for native USB communication via libusb.
 *
 * Provides high-performance async USB I/O with priority-based
 * message dispatching for audio, video, and control channels.
 */
object NativeUsb {

    init {
        try {
            System.loadLibrary("headunit_usb")
            AppLog.i { "Native USB library loaded successfully" }
        } catch (e: UnsatisfiedLinkError) {
            AppLog.e(e) { "Failed to load native USB library" }
            throw e
        }
    }

    // Callbacks - set by NativeUsbAccessoryConnection

    /**
     * Raw data callback - receives raw USB bytes for parsing in Kotlin.
     * Messages are delivered from the USB event thread sequentially.
     */
    @Volatile
    var rawDataCallback: ((data: ByteArray, length: Int) -> Unit)? = null

    /**
     * Error callback - receives USB error notifications.
     */
    @Volatile
    var errorCallback: ((errorCode: Int, message: String) -> Unit)? = null

    // Native methods
    @JvmStatic
    private external fun nativeOpen(fileDescriptor: Int): Long

    @JvmStatic
    private external fun nativeClose(handle: Long)

    @JvmStatic
    private external fun nativeStartReading(handle: Long)

    @JvmStatic
    private external fun nativeStopReading(handle: Long)

    @JvmStatic
    private external fun nativeWrite(handle: Long, data: ByteArray, length: Int): Int

    @JvmStatic
    private external fun nativeRead(handle: Long, data: ByteArray, length: Int, timeoutMs: Int): Int

    @JvmStatic
    private external fun nativeIsOpen(handle: Long): Boolean

    /**
     * Open a USB device using the file descriptor from Android's UsbDeviceConnection.
     * @param fileDescriptor The file descriptor from UsbDeviceConnection.fileDescriptor
     * @return A handle to the native connection, or 0 on failure
     */
    fun open(fileDescriptor: Int): Long {
        return nativeOpen(fileDescriptor)
    }

    /**
     * Close the USB connection.
     * @param handle The handle returned from open()
     */
    fun close(handle: Long) {
        nativeClose(handle)
    }

    /**
     * Start reading from USB asynchronously.
     * Data will be delivered via the registered callbacks.
     * @param handle The handle returned from open()
     */
    fun startReading(handle: Long) {
        nativeStartReading(handle)
    }

    /**
     * Stop reading from USB.
     * @param handle The handle returned from open()
     */
    fun stopReading(handle: Long) {
        nativeStopReading(handle)
    }

    /**
     * Write data to USB.
     * @param handle The handle returned from open()
     * @param data The data to write
     * @param length The number of bytes to write
     * @return The number of bytes written, or negative on error
     */
    fun write(handle: Long, data: ByteArray, length: Int): Int {
        return nativeWrite(handle, data, length)
    }

    /**
     * Check if the connection is open.
     * @param handle The handle returned from open()
     * @return true if open, false otherwise
     */
    fun isOpen(handle: Long): Boolean {
        return nativeIsOpen(handle)
    }

    /**
     * Read data from USB synchronously (for handshake).
     * @param handle The handle returned from open()
     * @param data The buffer to read into
     * @param length The maximum number of bytes to read
     * @param timeoutMs The timeout in milliseconds
     * @return The number of bytes read, or negative on error
     */
    fun read(handle: Long, data: ByteArray, length: Int, timeoutMs: Int): Int {
        return nativeRead(handle, data, length, timeoutMs)
    }

    // Callbacks from native code (called on native threads)

    /**
     * Raw data callback - called with raw USB bytes.
     * Kotlin handles all message parsing and TLS decryption.
     */
    @JvmStatic
    fun onRawData(data: ByteArray, length: Int) {
        try {
            rawDataCallback?.invoke(data, length)
        } catch (e: Exception) {
            AppLog.e(e) { "Error in raw data callback" }
        }
    }

    @JvmStatic
    fun onError(errorCode: Int, message: String) {
        AppLog.e { "Native USB error $errorCode: $message" }
        try {
            errorCallback?.invoke(errorCode, message)
        } catch (e: Exception) {
            AppLog.e(e) { "Error in error callback" }
        }
    }
}
