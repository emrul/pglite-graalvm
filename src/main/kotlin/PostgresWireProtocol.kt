
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets

object PostgresWireProtocol {

    enum class MessageType(val code: Byte) {
        QUERY('Q'.code.toByte()),
        STARTUP(0x00), // Special case for startup message
        TERMINATE('X'.code.toByte());
    }

    fun createQueryMessage(query: String): ByteArray {
        // 1. Message Type (1 byte): 'Q' for Query
        val messageType = MessageType.QUERY.code

        // 2. Message Body:
        //    - Query String (null-terminated string)
        val queryStringBytes = query.toByteArray(StandardCharsets.UTF_8)
        val nullTerminatedQuery = queryStringBytes + 0.toByte()

        // 3. Message Length (4 bytes, Int32):
        //    - Length of the message body + 4 (for the length itself)
        val messageLength = 4 + nullTerminatedQuery.size

        // Create a ByteBuffer to hold the entire message
        val buffer = ByteBuffer.allocate(1 + messageLength).order(ByteOrder.BIG_ENDIAN)

        // Write the message type
        buffer.put(messageType)

        // Write the message length
        buffer.putInt(messageLength)

        // Write the null-terminated query string
        buffer.put(nullTerminatedQuery)

        // Get the byte array from the buffer
        return buffer.array()
    }

    fun createStartupMessage(user: String, database: String): ByteArray {
        // Format of a Startup Message:
        //   - Protocol Version (Int32): 3.0 (0x00030000)
        //   - Parameter Name (null-terminated string): "user"
        //   - Parameter Value (null-terminated string): <user>
        //   - Parameter Name (null-terminated string): "database"
        //   - Parameter Value (null-terminated string): <database>
        //   - End of Parameters (null byte)

        // Protocol Version
        val protocolVersion = 0x00030000
        val versionBytes = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(protocolVersion).array()

        // Parameters
        val userBytes = "user".toByteArray(StandardCharsets.UTF_8)
        val nullTerminatedUser = userBytes + 0.toByte()
        val usernameBytes = user.toByteArray(StandardCharsets.UTF_8)
        val nullTerminatedUsername = usernameBytes + 0.toByte()

        val databaseBytes = "database".toByteArray(StandardCharsets.UTF_8)
        val nullTerminatedDatabase = databaseBytes + 0.toByte()
        val dbnameBytes = database.toByteArray(StandardCharsets.UTF_8)
        val nullTerminatedDbname = dbnameBytes + 0.toByte()
        val endParameter = 0.toByte()

        //message length
        val messageLength = (versionBytes.size +
                nullTerminatedUser.size +
                nullTerminatedUsername.size +
                nullTerminatedDatabase.size +
                nullTerminatedDbname.size +
                1)

        // Create a ByteBuffer to hold the entire message
        val buffer = ByteBuffer.allocate(messageLength + 4).order(ByteOrder.BIG_ENDIAN)

        //write the length
        buffer.putInt(messageLength)

        buffer.put(versionBytes)

        buffer.put(nullTerminatedUser)
        buffer.put(nullTerminatedUsername)

        buffer.put(nullTerminatedDatabase)
        buffer.put(nullTerminatedDbname)

        buffer.put(endParameter)

        return buffer.array()
    }

    fun createTerminateMessage(): ByteArray {
        val messageType = MessageType.TERMINATE.code

        // Length of the message body + 4 (for the length itself)
        val messageLength = 4

        val buffer = ByteBuffer.allocate(1 + messageLength).order(ByteOrder.BIG_ENDIAN)

        buffer.put(messageType)

        // Message Length (4 bytes, Int32)
        buffer.putInt(messageLength)

        return buffer.array()
    }
}

fun main() {
    val query = "SELECT now();"
    val queryMessage = PostgresWireProtocol.createQueryMessage(query)
    println("Query Message (hex): " + queryMessage.joinToString("") { "%02x".format(it) })
    println("Query Message (length): ${queryMessage.size}")

    val startupMessage = PostgresWireProtocol.createStartupMessage("postgres", "template1")
    println("Startup Message (hex): " + startupMessage.joinToString("") { "%02x".format(it) })
    println("Startup Message (length): ${startupMessage.size}")

    val terminateMessage = PostgresWireProtocol.createTerminateMessage()
    println("Terminate Message (hex): " + terminateMessage.joinToString("") { "%02x".format(it) })
    println("Terminate Message (length): ${terminateMessage.size}")
}