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
    // A socket to communicate with the server
    private var clientSocket: Socket? = null
    private val parser = DefaultHapiContext().pipeParser
    private val listener: Thread
    private val awaitingResponse = HashMap<String, CompletableFuture<Hl7Message>>()

    // A constructor that takes the host name, port number, an optional ssl context, and an optional client certificate as parameters
    init {
        // If the ssl context is not null, create a secure socket with the given ssl context
        if (sslContext != null) {
            clientSocket = sslContext.socketFactory.createSocket(host, port)
            // If the client certificate is not null, set it as the socket's local certificate
            val sslSocket = (clientSocket as SSLSocket?)!!
            if (clientCert != null) {
                sslSocket.useClientMode = true
                sslSocket.enabledProtocols = arrayOf("TLSv1.2")
                sslSocket.needClientAuth = true
                //sslSocket.setSSLParameters(SSLParameters(arrayOf(clientCert)))
            }
            // Start the SSL handshake and validate the server's certificate
            sslSocket.startHandshake()
        } else {
            // Otherwise, create a plain socket with the given host name and port number
            clientSocket = Socket(host, port)
        }

        // Create a background thread to listen for responses from the server
        listener = Thread {
            try {
                // Create an input stream to read data from the server socket
                val reader = clientSocket!!.getInputStream().bufferedReader()

                // Read data from the input stream until the end of stream or an exception occurs
                val buffer = CharArray(256)
                while (true) {
                    val read = reader.read(buffer)
                    if (read <= 0) {
                        Thread.sleep(1000)
                        continue
                    }
                    val startIndex = buffer.indexOf(Constants.startBlock)
                    val endStartIndex = buffer.indexOf(Constants.endBlock[0])
                    val msgBuffer = buffer.sliceArray(IntRange(startIndex + 1, endStartIndex - 1))
                    val hl7 = String(msgBuffer)
                    val msg = parser.parse(hl7)
                    val terser = Terser(msg)
                    val msgControlId = terser.get("/.MSH-10-1")
                    val receivedMessage = Hl7Message(msg, clientSocket!!.remoteSocketAddress.toString())
                    val task = awaitingResponse[msgControlId]
                    task?.complete(receivedMessage)
                }
            } catch (e: IOException) {
                clientSocket?.close()
                Thread.currentThread().join()
            }
        }

        // Start the background thread
        listener.start()
    }

    // A method to send data to the server and read the response asynchronously
    fun <TMessage : Message> send(message: TMessage): CompletableFuture<Hl7Message> {
        // Create an output stream to write data to the server socket
        val writer = clientSocket!!.getOutputStream().writer()
        val terser = Terser(message)
        val msgControlId = terser.get("/.MSH-10-1")
        val future = CompletableFuture<Hl7Message>()
        awaitingResponse[msgControlId] = future
        val hl7 = parser.encode(message)
        val msg = charArrayOf(Constants.startBlock, *hl7.toCharArray(), *Constants.endBlock)
        writer.write(msg)
        writer.flush()
        return future
    }

    override fun close() {
        // Close the client socket
        clientSocket!!.close()
    }
}