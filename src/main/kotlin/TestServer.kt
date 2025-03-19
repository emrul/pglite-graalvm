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

    private fun shutdown() {
        running = false
        client.close()
        println("${client.inetAddress.hostAddress} closed the connection")
    }

}