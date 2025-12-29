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
    @Volatile
    var audioCallback: ((channel: Int, data: ByteArray, length: Int) -> Unit)? = null

    @Volatile
    var videoCallback: ((channel: Int, data: ByteArray, length: Int) -> Unit)? = null

    @Volatile
    var controlCallback: ((channel: Int, data: ByteArray, length: Int) -> Unit)? = null

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

    // Callbacks from native code (called on native threads)

    @JvmStatic
    fun onAudioData(channel: Int, data: ByteArray, length: Int) {
        try {
            audioCallback?.invoke(channel, data, length)
        } catch (e: Exception) {
            AppLog.e(e) { "Error in audio callback" }
        }
    }

    @JvmStatic
    fun onVideoData(channel: Int, data: ByteArray, length: Int) {
        try {
            videoCallback?.invoke(channel, data, length)
        } catch (e: Exception) {
            AppLog.e(e) { "Error in video callback" }
        }
    }

    @JvmStatic
    fun onControlData(channel: Int, data: ByteArray, length: Int) {
        try {
            controlCallback?.invoke(channel, data, length)
        } catch (e: Exception) {
            AppLog.e(e) { "Error in control callback" }
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
