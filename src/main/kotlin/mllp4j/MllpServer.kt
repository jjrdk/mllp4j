package mllp4j

import ca.uhn.hl7v2.DefaultHapiContext
import ca.uhn.hl7v2.parser.Parser
import org.slf4j.LoggerFactory
import java.io.IOException
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLServerSocket
import javax.net.ssl.TrustManagerFactory


class MllpServer(
    port: Int,
    sslContext: SSLContext?,
    private val middleware: Hl7MessageMiddleware,
    private val messageLogger: MessageLog
) :
    AutoCloseable {
    private val logger = LoggerFactory.getLogger(MllpServer::class.java)
    private val executor: ExecutorService = Executors.newFixedThreadPool(10)
    private var running = true

    // A server socket to accept client connections
    private var serverSocket: ServerSocket? = null

    // A constructor that takes the port number and an optional ssl certificate as parameters
    init {
        // Initialize the thread pool with a fixed number of threads

        // Initialize the running flag to true

        // If the ssl context is not null, create a secure server socket with the given ssl context
        if (sslContext != null) {
            serverSocket = sslContext.serverSocketFactory.createServerSocket(port)
            // Enable client authentication if the ssl context has a trust manager
            val trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
            if (trustManagerFactory.trustManagers.isNotEmpty()) {
                (serverSocket as SSLServerSocket?)!!.needClientAuth = true
            }
        } else {
            // Otherwise, create a plain server socket with the given port number
            serverSocket = ServerSocket(port)
        }
    }

    // A method to start the server and accept client connections
    fun listen() {
        logger.debug("Server started on port {}", serverSocket!!.getLocalPort())
        while (running) {
            try {
                val clientSocket = serverSocket!!.accept()
                val task: Runnable = MllpHandler(clientSocket, middleware, messageLogger)

                executor.execute(task)
            } catch (e: IOException) {
                logger.error("Error accepting client connection: {}", e.message)
            }
        }
    }

    // A method to stop the server and close the resources
    fun stop() {
        println("Server stopped")
        running = false
        try {
            // Close the server socket
            serverSocket!!.close()
        } catch (e: IOException) {
            logger.error("Error closing server socket: ${e.message}")
        }
        // Shutdown the thread pool and wait for all tasks to finish
        executor.shutdown()
        try {
            executor.awaitTermination(3, TimeUnit.SECONDS)
        } catch (e: InterruptedException) {
            logger.error("Error waiting for thread pool termination: {}", e.message)
        }
    }

    // A nested class that implements the runnable interface and handles the communication with a single client
    private class MllpHandler // A constructor that takes the client socket as a parameter
        (// A socket to communicate with the client
        private val clientSocket: Socket,
        private val middleware: Hl7MessageMiddleware,
        private val messageLogger: MessageLog
    ) : Runnable {
        private val parser: Parser = DefaultHapiContext().pipeParser
        private val logger = LoggerFactory.getLogger(MllpHandler::class.java)
        override fun run() {
            logger.info("Client connected from {}", clientSocket.remoteSocketAddress)
            try {
                // Create input and output streams to read and write data from and to the client socket
                val reader = clientSocket.getInputStream().bufferedReader()
                val writer = clientSocket.getOutputStream().bufferedWriter()

                // Read data from the input stream until the end of stream or an exception occurs
                val messageBuilder = ArrayList<Char>()
                val buffer = CharArray(256)
                while (reader.read(buffer) >= 0) {
                    logger.info("Received from client: {}", buffer)

                    // Process the data and generate a response
                    val response = process(buffer, messageBuilder)

                    // Write the response to the output stream
                    writer.write(response)
                    logger.info("Sent to client: {}", response)
                }
            } catch (e: IOException) {
                logger.error("Error communicating with client: {}", e.message)
            } finally {
                try {
                    // Close the client socket
                    clientSocket.close()
                } catch (e: IOException) {
                    logger.error("Error closing client socket: {}", e.message)
                }
                logger.info("Client disconnected from {}", clientSocket.remoteSocketAddress)
            }
        }

        private fun process(buffer: CharArray, messageBuilder: ArrayList<Char>): Int {
            if (buffer.isEmpty()) {
                return -1
            }

            val isStart = buffer[0] == Constants.startCharacter
            if (isStart && messageBuilder.size > 0) {
                throw Exception("Unexpected character: ${buffer[0]}")
            }

            val endBlockStart = buffer.indexOf(Constants.firstEndChar)
            val endBlockEnd = endBlockStart + 1
            val bytes = buffer.sliceArray(
                IntRange(
                    if (isStart) 1 else 0,
                    if (endBlockStart > 1) endBlockStart - 1 else buffer.size - 1
                )
            )

            if (buffer[endBlockEnd] == Constants.lastEndChar) {
                sendResponse(charArrayOf(*messageBuilder.toCharArray(), *bytes))
                messageBuilder.clear()
            } else {
                for (element in bytes) {
                    messageBuilder.add(element)
                }
            }

            if (endBlockStart == -1) {
                return -1
            }

            val newStartBlock =
                buffer.sliceArray(IntRange(endBlockEnd, buffer.size - 1)).indexOf(Constants.startCharacter)
            return if (newStartBlock == -1) -1 else (newStartBlock + endBlockEnd)
        }

        private fun sendResponse(messageBuilder: CharArray) {
            val s = String(messageBuilder)
            val received = parser.parse(s)
            val message = Hl7Message(
                received,
                clientSocket.remoteSocketAddress.toString()
            )

            messageLogger.write(s).join()
            val result = middleware.handle(message)

            val hl7 = result.join()
            val resultMsg = parser.encode(hl7)
            writeToStream(resultMsg)
            messageLogger.write(resultMsg)
        }

        private fun writeToStream(response: String) {
            messageLogger.write(response)
            val buffer = ByteArray(response.length + 3)
            buffer[0] = Constants.startCharacter.code.toByte()
            response.forEachIndexed { i, c -> buffer[i + 1] = c.code.toByte() }
            Constants.endBlock.copyInto(buffer, response.length + 1)
            val outputStream = clientSocket.getOutputStream()
            outputStream.write(buffer)
            outputStream.flush()
        }

    }

    override fun close() {
        if (serverSocket?.isClosed != true) {
            serverSocket?.close()
        }
    }
}


