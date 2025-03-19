import java.io.DataInputStream
import java.net.ServerSocket
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets
import java.util.function.Predicate
import kotlin.concurrent.thread


fun main(args: Array<String>) {
    val pgInstance = PostgresChicoryWasi()
    pgInstance.start()
    pgInstance.interactive_one.apply()
    val server = ServerSocket(5432)
    println("Server is running on port ${server.localPort}")

    while (true) {
        val client = server.accept()
        println("Client connected: ${client.inetAddress.hostAddress}")

        thread { ClientHandler(client, pgInstance).run() }
    }

}

class ClientHandler(client: Socket, pgInstance: PostgresChicoryWasi) {
    private val client: Socket = client
    private val inputStream = DataInputStream(client.getInputStream())
    private val writer = client.getOutputStream()
    private val pgInstance = pgInstance
    private var running: Boolean = false


    fun run() {
        running = true
        val messages = mutableListOf<ByteArray>()
        while (running) {
            try {
                var pgDataSentToClient = false
                var clientDataSentToPg = false

                val dataFromPg = pgInstance.readWireBuffer()
                if ( dataFromPg != null ) {
                    writer.write(dataFromPg)
                    writer.flush()
                    pgDataSentToClient = true
                }

                while (inputStream.available() > 0) {
                    val availableBytes = inputStream.available()
                    messages.add(inputStream.readNBytes(availableBytes))
                }

                if (messages.isNotEmpty()) {
                    pgInstance.submitPgWireMessage(messages.flatMap { it.toList() }.toByteArray())
                    messages.clear()
                    clientDataSentToPg = true
                }

                if ( clientDataSentToPg || pgDataSentToClient ) {
                    println("interactive_one")
                    pgInstance.use_wire.apply(1)
                    pgInstance.interactive_one.apply()
                }

            } catch (ex: Exception) {
                // TODO: Implement exception handling
                println("Exception: $ex")
                shutdown()
            } finally {

            }

        }
    }

    private fun createParameterStatus(name: String, value: String): ByteArray {
        // Message type: S
        // Message length: int32 (length of name, value and message type + 4 length itself)
        // name: null terminated
        // value: null terminated

        val nameBytes = name.toByteArray(StandardCharsets.UTF_8)
        val valueBytes = value.toByteArray(StandardCharsets.UTF_8)
        val buffer = ByteBuffer.allocate(1 + 4 + nameBytes.size + 1 + valueBytes.size + 1).order(ByteOrder.BIG_ENDIAN)
        buffer.put('S'.code.toByte())
        buffer.putInt(4 + nameBytes.size + 1 + valueBytes.size + 1)
        buffer.put(nameBytes)
        buffer.put(0.toByte())
        buffer.put(valueBytes)
        buffer.put(0.toByte())
        buffer.flip()
        return buffer.array()
    }

    private fun readMessageFromClient(): ByteArray {
        // Try to read the first byte
        val firstByte = inputStream.read()
        if (firstByte == -1) {
            return byteArrayOf() // Connection closed
        }

        // Check if first byte might be a message type
        val possibleMessageTypes = listOf(
            'Q'.code, 'P'.code, 'B'.code, 'F'.code, 'X'.code, 'C'.code, 'D'.code, 'H'.code,
            'S'.code, 'E'.code, 'd'.code, 'c'.code
        )

        if (firstByte in possibleMessageTypes) {
            // This is likely a regular message with a type byte
            val lengthBytes = ByteArray(4)
            val bytesRead = inputStream.readNBytes(lengthBytes, 0, 4)
            if (bytesRead != 4) {
                throw Exception("Could not read message length bytes")
            }

            val length = ByteBuffer.wrap(lengthBytes).order(ByteOrder.BIG_ENDIAN).int
            println("[SERVER] Regular message of type '${firstByte.toChar()}' with length $length")

            // Safety check for length
            if (length < 4 || length > 1_000_000) {
                throw Exception("Invalid message length: $length")
            }

            // Read the rest of the message (length includes the length field but not the type byte)
            val messageBytes = ByteArray(length - 4)
            val messageRead = inputStream.readNBytes(messageBytes, 0, length - 4)
            if (messageRead != length - 4) {
                throw Exception("Could not read complete message body, expected ${length - 4}, got $messageRead")
            }

            // Combine everything
            val fullMessage = ByteArray(1 + 4 + messageBytes.size)
            fullMessage[0] = firstByte.toByte()
            System.arraycopy(lengthBytes, 0, fullMessage, 1, 4)
            System.arraycopy(messageBytes, 0, fullMessage, 5, messageBytes.size)

            return fullMessage
        } else {
            // This could be a startup message, SSL request, or cancel request
            val nextThreeBytes = ByteArray(3)
            inputStream.readNBytes(nextThreeBytes, 0, 3)

            val headerBytes = ByteArray(4)
            headerBytes[0] = firstByte.toByte()
            System.arraycopy(nextThreeBytes, 0, headerBytes, 1, 3)

            val length = ByteBuffer.wrap(headerBytes).order(ByteOrder.BIG_ENDIAN).int
            println("[SERVER] Special message with length field $length")

            // Special case for SSL request
            if (length == 80877103 || length == 80877102) {
                println("[SERVER] Detected SSL/Cancel request")
                return headerBytes
            }

            // Must be a startup message
            // For startup, length includes the length field itself
            if (length < 8 || length > 10000) {
                throw Exception("Invalid startup message length: $length")
            }

            val remainingBytes = ByteArray(length - 4)
            val remainingRead = inputStream.readNBytes(remainingBytes, 0, length - 4)
            if (remainingRead != length - 4) {
                throw Exception("Could not read complete startup message, expected ${length - 4}, got $remainingRead")
            }

            val fullMessage = ByteArray(length)
            System.arraycopy(headerBytes, 0, fullMessage, 0, 4)
            System.arraycopy(remainingBytes, 0, fullMessage, 4, remainingBytes.size)

            return fullMessage
        }
    }

    private fun shutdown() {
        running = false
        client.close()
        println("${client.inetAddress.hostAddress} closed the connection")
    }

}