package mllp4j

import ca.uhn.hl7v2.DefaultHapiContext
import ca.uhn.hl7v2.parser.Parser
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
    private val logger: MessageLog
) :
    AutoCloseable {
    // A thread pool to handle multiple client connections
    private val executor: ExecutorService = Executors.newFixedThreadPool(10) { r ->
        val t = Thread(r)
        t.priority = Thread.MAX_PRIORITY
        t.start()
        t
    }

    // A flag to indicate whether the server is running or not
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
            if (trustManagerFactory.trustManagers.size > 0) {
                (serverSocket as SSLServerSocket?)!!.needClientAuth = true
            }
        } else {
            // Otherwise, create a plain server socket with the given port number
            serverSocket = ServerSocket(port)
        }
    }

    // A method to start the server and accept client connections
    fun listen() {
        logger.write("Server started on port ${serverSocket!!.getLocalPort()}")
        while (running) {
            try {
                // Accept a client connection and wrap it in a runnable task
                val clientSocket = serverSocket!!.accept()
                val task: Runnable = MllpHandler(clientSocket, middleware, logger)

                // Submit the task to the thread pool for execution
                executor.execute(task)
            } catch (e: IOException) {
                logger.write("Error accepting client connection: ${e.message}")
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
            println("Error closing server socket: " + e.message)
        }
        // Shutdown the thread pool and wait for all tasks to finish
        executor.shutdown()
        try {
            executor.awaitTermination(3, TimeUnit.SECONDS)
        } catch (e: InterruptedException) {
            println("Error waiting for thread pool termination: " + e.message)
        }
    }

    // A nested class that implements the runnable interface and handles the communication with a single client
    private class MllpHandler // A constructor that takes the client socket as a parameter
        (// A socket to communicate with the client
        private val clientSocket: Socket,
        private val middleware: Hl7MessageMiddleware,
        private val logger: MessageLog
    ) : Runnable {
        private val parser: Parser = DefaultHapiContext().pipeParser

        override fun run() {
            logger.write("Client connected from ${clientSocket.remoteSocketAddress}")
            try {
                // Create input and output streams to read and write data from and to the client socket
                val reader = clientSocket.getInputStream().bufferedReader()
                val writer = clientSocket.getOutputStream().bufferedWriter()

                // Read data from the input stream until the end of stream or an exception occurs
                val messageBuilder = ArrayList<Char>()
                val buffer = CharArray(256)
                while (reader.read(buffer) >= 0) {
                    logger.write("Received from client: ${buffer}")

                    // Process the data and generate a response
                    val response = process(buffer, messageBuilder)

                    // Write the response to the output stream
                    writer.write(response)
                    logger.write("Sent to client: $response")
                }
            } catch (e: IOException) {
                logger.write("Error communicating with client: ${e.message}")
            } finally {
                try {
                    // Close the client socket
                    clientSocket.close()
                } catch (e: IOException) {
                    logger.write("Error closing client socket: " + e.message)
                }
                logger.write("Client disconnected from ${clientSocket.remoteSocketAddress}")
            }
        }

        private fun process(buffer: CharArray, messageBuilder: ArrayList<Char>): Int {
            if (buffer.isEmpty()) {
                return -1
            }

            val isStart = buffer[0] == Constants.startBlock
            if (isStart && messageBuilder.size > 0) {
                throw Exception(
                    "Unexpected character: " + buffer[0].toString()
                )
            }

            val endBlockStart = buffer.indexOf(Constants.endBlock[0])
            val endBlockEnd = endBlockStart + 1
            var bytes = buffer.sliceArray(
                IntRange(
                    0,
                    if (endBlockStart > 1) {
                        endBlockStart - 1
                    } else {
                        buffer.size - 1
                    }
                )
            )
            if (isStart) {
                bytes = if (bytes.isEmpty()) {
                    bytes
                } else {
                    bytes.sliceArray(IntRange(1, bytes.size - 1))
                }
            }

            if (buffer[endBlockEnd] == Constants.endBlock[1]) {
                for (element in bytes) {
                    messageBuilder.add(element)
                }
                sendResponse(messageBuilder.toCharArray())
                messageBuilder.clear()
            } else {
                for (element in bytes) {
                    messageBuilder.add(element)
                }
            }

            if (endBlockStart == -1) {
                return -1
            }

            val newStartBlock = buffer.sliceArray(IntRange(endBlockEnd, buffer.size)).indexOf(Constants.startBlock)
            return if (newStartBlock == -1) {
                -1
            } else {
                (newStartBlock + endBlockEnd)
            }
        }

        private fun sendResponse(messageBuilder: CharArray) {
            val s = String(messageBuilder)
            val received = parser.parse(s)
            val message = Hl7Message(
                received,
                clientSocket.remoteSocketAddress.toString()
            )

            logger.write(s).get()
            val result = middleware.handle(message)

            val hl7 = result.join()
            val resultMsg = parser.encode(hl7)
            writeToStream(resultMsg)
            logger.write(resultMsg)
        }

        private fun writeToStream(response: String): Unit {
            logger.write(response)
            val count = response.length + 3
            val buffer = CharArray(count)
            buffer[0] = Constants.startBlock
            response.forEachIndexed { i, c -> buffer[i + 1] = c }
            Constants.endBlock.copyInto(buffer, response.length + 1)
            val bytes = buffer.map { c -> c.code.toByte() }.toByteArray()
            val outputStream = clientSocket.getOutputStream()
            outputStream.write(bytes)
            outputStream.flush()
        }

    }

    override fun close() {
        if (serverSocket?.isClosed != true) {
            serverSocket?.close()
        }
    }
}


