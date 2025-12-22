package info.anodsplace.headunit.aap

import info.anodsplace.headunit.aap.protocol.messages.Messages.DEF_BUFFER_LENGTH
import java.nio.ByteBuffer
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLEngine
import javax.net.ssl.SSLEngineResult

object AapSslImpl: AapSsl {
    private val sslContext: SSLContext by lazy { createSslContext() }
    private var sslEngine: SSLEngine? = null
    private var txBuffer: ByteBuffer? = null
    private var rxBuffer: ByteBuffer? = null

    private fun createSslContext(): SSLContext {
        // With Conscrypt installed, TLSv1.2 should be available on all Android versions
        val context = SSLContext.getInstance("TLSv1.2")
        context.init(arrayOf(SingleKeyKeyManager), arrayOf(NoCheckTrustManager), null)
        return context
    }

    override fun prepare() {
        val newSslEngine = sslContext.createSSLEngine()
        newSslEngine.useClientMode = true

        // Enable TLSv1.2 (required by modern Android Auto)
        newSslEngine.enabledProtocols = arrayOf("TLSv1.2")

        // Enable cipher suites compatible with Android Auto
        val supportedCiphers = newSslEngine.supportedCipherSuites.toSet()
        val preferredCiphers = arrayOf(
            // GCM ciphers (required by modern Android)
            "TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256",
            "TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384",
            "TLS_RSA_WITH_AES_128_GCM_SHA256",
            "TLS_RSA_WITH_AES_256_GCM_SHA384",
            // CBC fallbacks
            "TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA256",
            "TLS_ECDHE_RSA_WITH_AES_256_CBC_SHA384",
            "TLS_RSA_WITH_AES_128_CBC_SHA256",
            "TLS_RSA_WITH_AES_256_CBC_SHA256",
            "TLS_RSA_WITH_AES_128_CBC_SHA",
            "TLS_RSA_WITH_AES_256_CBC_SHA"
        )
        val enabledCiphers = preferredCiphers.filter { it in supportedCiphers }.toTypedArray()
        if (enabledCiphers.isNotEmpty()) {
            newSslEngine.enabledCipherSuites = enabledCiphers
        }

        val session = newSslEngine.session
        val appBufferMax = session.applicationBufferSize
        val netBufferMax = session.packetBufferSize

        sslEngine = newSslEngine
        txBuffer = ByteBuffer.allocateDirect(netBufferMax)
        rxBuffer = ByteBuffer.allocateDirect(DEF_BUFFER_LENGTH.coerceAtLeast(appBufferMax + 50))
    }

    private fun runDelegatedTasks(result: SSLEngineResult, engine: SSLEngine) {
        if (result.handshakeStatus === SSLEngineResult.HandshakeStatus.NEED_TASK) {
            var runnable: Runnable? = engine.delegatedTask
            while (runnable != null) {
                runnable.run()
                runnable = engine.delegatedTask
            }
            val hsStatus = engine.handshakeStatus
            if (hsStatus === SSLEngineResult.HandshakeStatus.NEED_TASK) {
                throw Exception("handshake shouldn't need additional tasks")
            }
        }
    }

    fun isHandshakeComplete(): Boolean {
        val status = sslEngine?.handshakeStatus
        return status == javax.net.ssl.SSLEngineResult.HandshakeStatus.FINISHED ||
               status == javax.net.ssl.SSLEngineResult.HandshakeStatus.NOT_HANDSHAKING
    }

    override fun handshakeRead(): ByteArray {
        require(sslEngine != null) { "SSL Engine not initialized - prepare() was not called" }
        val engine = sslEngine!!

        info.anodsplace.headunit.utils.AppLog.e { "handshakeRead: status=${engine.handshakeStatus}" }

        // Keep wrapping until we have data to send or handshake is complete
        txBuffer!!.clear()
        val result = engine.wrap(ByteBuffer.allocate(0), txBuffer)
        runDelegatedTasks(result, engine)

        info.anodsplace.headunit.utils.AppLog.e { "handshakeRead: wrap result=${result.status}, produced=${result.bytesProduced()}, hsStatus=${result.handshakeStatus}" }

        val resultBuffer = ByteArray(result.bytesProduced())
        txBuffer!!.flip()
        txBuffer!!.get(resultBuffer)
        return resultBuffer
    }

    override fun handshakeWrite(handshakeData: ByteArray) {
        require(sslEngine != null) { "SSL Engine not initialized - prepare() was not called" }
        val engine = sslEngine!!

        info.anodsplace.headunit.utils.AppLog.e { "handshakeWrite: status=${engine.handshakeStatus}, dataSize=${handshakeData.size}" }

        val data = ByteBuffer.wrap(handshakeData)
        var totalConsumed = 0

        // Unwrap ALL the data - phone may send multiple TLS records bundled together
        while (data.hasRemaining()) {
            rxBuffer!!.clear()

            val result = try {
                engine.unwrap(data, rxBuffer)
            } catch (e: javax.net.ssl.SSLException) {
                info.anodsplace.headunit.utils.AppLog.e { "handshakeWrite: SSLException at position ${data.position()}, remaining ${data.remaining()}: ${e.message}" }
                // Stop processing on SSL error - remaining bytes might not be TLS data
                break
            }

            runDelegatedTasks(result, engine)
            totalConsumed += result.bytesConsumed()

            info.anodsplace.headunit.utils.AppLog.e { "handshakeWrite: unwrap result=${result.status}, consumed=${result.bytesConsumed()}, hsStatus=${result.handshakeStatus}" }

            // If we need to wrap (send data) or handshake is finished, stop unwrapping
            if (result.handshakeStatus == javax.net.ssl.SSLEngineResult.HandshakeStatus.NEED_WRAP ||
                result.handshakeStatus == javax.net.ssl.SSLEngineResult.HandshakeStatus.FINISHED ||
                result.handshakeStatus == javax.net.ssl.SSLEngineResult.HandshakeStatus.NOT_HANDSHAKING) {
                break
            }

            // If unwrap didn't consume anything, avoid infinite loop
            if (result.bytesConsumed() == 0) {
                info.anodsplace.headunit.utils.AppLog.e { "handshakeWrite: no bytes consumed, breaking" }
                break
            }
        }

        info.anodsplace.headunit.utils.AppLog.e { "handshakeWrite: total consumed=$totalConsumed, remaining=${data.remaining()}, finalStatus=${engine.handshakeStatus}" }
    }

    override fun decrypt(start: Int, length: Int, buffer: ByteArray): ByteArray {
        require(sslEngine != null) { "SSL Engine not initialized - prepare() was not called" }
        rxBuffer!!.clear()
        val encrypted = ByteBuffer.wrap(buffer, start, length)
        val result = sslEngine!!.unwrap(encrypted, rxBuffer)
        runDelegatedTasks(result, sslEngine!!)
        val resultBuffer = ByteArray(result.bytesProduced())
        rxBuffer!!.flip()
        rxBuffer!!.get(resultBuffer)
        return resultBuffer
    }

    override fun encrypt(offset: Int, length: Int, buffer: ByteArray): ByteArray {
        require(sslEngine != null) { "SSL Engine not initialized - prepare() was not called" }
        txBuffer!!.clear()
        val byteBuffer = ByteBuffer.wrap(buffer, offset, length)
        val result = sslEngine!!.wrap(byteBuffer, txBuffer)
        runDelegatedTasks(result, sslEngine!!)
        val resultBuffer = ByteArray(result.bytesProduced() + offset)
        txBuffer!!.flip()
        txBuffer!!.get(resultBuffer, offset, result.bytesProduced())
        return resultBuffer
    }
}
