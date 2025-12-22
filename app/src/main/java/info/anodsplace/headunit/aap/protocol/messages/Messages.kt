package info.anodsplace.headunit.aap.protocol.messages

import info.anodsplace.headunit.aap.Utils


/**
 * @author algavris
 * *
 * @date 08/06/2016.
 */

object Messages {
    const val DEF_BUFFER_LENGTH = 131080

    // Initial version request - we claim a high version, phone will respond with what it supports
    val VERSION_REQUEST = byteArrayOf(0, 1, 0, 7) // Request version 1.7

    // Negotiated version from phone's response
    var negotiatedMajorVersion: Int = 1
    var negotiatedMinorVersion: Int = 1

    fun parseVersionResponse(buffer: ByteArray, length: Int): Boolean {
        if (length < 10) return false
        // Format: chan(1) + flags(1) + len(2) + type(2) + major(2) + minor(2) + status(2)
        negotiatedMajorVersion = ((buffer[6].toInt() and 0xFF) shl 8) or (buffer[7].toInt() and 0xFF)
        negotiatedMinorVersion = ((buffer[8].toInt() and 0xFF) shl 8) or (buffer[9].toInt() and 0xFF)
        return true
    }

    fun getVersionString(): String = "$negotiatedMajorVersion.$negotiatedMinorVersion"

    fun createRawMessage(chan: Int, flags: Int, type: Int, data: ByteArray): ByteArray {
        val size = data.size
        val total = 6 + size
        val buffer = ByteArray(total)

        buffer[0] = chan.toByte()
        buffer[1] = flags.toByte()
        Utils.intToBytes(size + 2, 2, buffer)
        Utils.intToBytes(type, 4, buffer)

        System.arraycopy(data, 0, buffer, 6, size)
        return buffer
    }
}
