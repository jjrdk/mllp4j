package mllp4j

import ca.uhn.hl7v2.DefaultHapiContext
import ca.uhn.hl7v2.model.Message
import ca.uhn.hl7v2.util.Terser
import java.io.IOException
import java.net.Socket
import java.security.cert.X509Certificate
import java.util.concurrent.CompletableFuture
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket

class MllpClient(host: String?, port: Int, sslContext: SSLContext?, clientCert: X509Certificate?) : AutoCloseable {
    private var clientSocket: Socket
    private val parser = DefaultHapiContext().pipeParser
    private val listener: Thread
    private val awaitingResponse = HashMap<String, CompletableFuture<Hl7Message>>()

    // A constructor that takes the host name, port number, an optional ssl context, and an optional client certificate as parameters
    init {
        // If the ssl context is not null, create a secure socket with the given ssl context
        if (sslContext != null) {
            val socket =
                sslContext.socketFactory.createSocket(host, port) ?: throw IOException("Could not create socket")
            clientSocket = socket
            // If the client certificate is not null, set it as the socket's local certificate
            val sslSocket = (clientSocket as SSLSocket?)!!
            if (clientCert != null) {
                val p = sslSocket.sslParameters
                p.protocols = arrayOf("TLSv1.2", "TLSv1.3")
                p.needClientAuth = true
                p.cipherSuites = arrayOf("TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256", "TLS_AES_128_GCM_SHA256")
                sslSocket.useClientMode = true
                sslSocket.enableSessionCreation = true
                sslSocket.setSSLParameters(p)
            }
            sslSocket.startHandshake()
        } else {
            clientSocket = Socket(host, port)
        }

        listener = Thread {
            try {
                val reader = clientSocket.getInputStream().bufferedReader()

                while (true) {
                    val buffer = CharArray(256)
                    val read = reader.read(buffer)
                    if (read <= 0) {
                        Thread.sleep(1000)
                        continue
                    }
                    val startIndex = buffer.indexOf(Constants.startCharacter)
                    val endStartIndex = buffer.indexOf(Constants.firstEndChar)
                    if (buffer.size >= endStartIndex + 2 && buffer[endStartIndex + 1] == Constants.lastEndChar) {
                        val msgBuffer = buffer.sliceArray(IntRange(startIndex + 1, endStartIndex - 1))
                        val hl7 = String(msgBuffer)
                        val msg = parser.parse(hl7)
                        val terser = Terser(msg)
                        val msgControlId = terser.get("/.MSH-10-1")
                        val receivedMessage = Hl7Message(msg, clientSocket.remoteSocketAddress.toString())
                        val task = awaitingResponse[msgControlId]
                        task?.complete(receivedMessage)
                    }
                }
            } catch (e: IOException) {
                clientSocket.close()
                Thread.currentThread().join()
            }
        }

        // Start the background thread
        listener.start()
    }

    // A method to send data to the server and read the response asynchronously
    fun <TMessage : Message> send(message: TMessage): CompletableFuture<Hl7Message> {
        val writer = clientSocket.getOutputStream().writer()
        val terser = Terser(message)
        val msgControlId = terser.get("/.MSH-10-1")
        val future = CompletableFuture<Hl7Message>()
        awaitingResponse[msgControlId] = future
        val hl7 = parser.encode(message)
        val msg =
            charArrayOf(Constants.startCharacter, *hl7.toCharArray(), Constants.firstEndChar, Constants.lastEndChar)
        writer.write(msg)
        writer.flush()
        return future
    }

    /**
     * Closes the client socket
     *
     * {@inheritDoc}
     */
    override fun close() {
        clientSocket.close()
    }
}