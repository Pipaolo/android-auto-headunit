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

    override fun handshakeRead(): ByteArray {
        require(sslEngine != null) { "SSL Engine not initialized - prepare() was not called" }
        txBuffer!!.clear()
        val result = sslEngine!!.wrap(emptyArray(), txBuffer)
        runDelegatedTasks(result, sslEngine!!)
        val resultBuffer = ByteArray(result.bytesProduced())
        txBuffer!!.flip()
        txBuffer!!.get(resultBuffer)
        return resultBuffer
    }

    override fun handshakeWrite(handshakeData: ByteArray) {
        require(sslEngine != null) { "SSL Engine not initialized - prepare() was not called" }
        rxBuffer!!.clear()
        val data = ByteBuffer.wrap(handshakeData)
        val result = sslEngine!!.unwrap(data, rxBuffer)
        runDelegatedTasks(result, sslEngine!!)
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
