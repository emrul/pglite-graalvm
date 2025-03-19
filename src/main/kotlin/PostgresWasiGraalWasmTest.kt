
import at.released.weh.bindings.graalvm241.wasip1.GraalvmWasiPreview1Builder
import at.released.weh.host.EmbedderHost
import org.graalvm.polyglot.Context
import org.graalvm.polyglot.Source
import org.graalvm.polyglot.Value
import java.security.SecureRandom
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.createDirectory
import kotlin.io.path.notExists
import kotlin.io.path.writeBytes

/*
    Refs:
        1) https://github.com/electric-sql/pglite/issues/89
        2) https://github.com/sgosiaco/pglite-go/

 */
object PostgresWasiTest {
    const val POSTGRES_MODULE_NAME = "postgres"
    const val PGLITE_EMBED_ARCHIVE = "pglite-wasi.tar.gz"

    const val WASI_VM_DIR = "./WasiVmDir"
    const val TMP_DIR = "${WASI_VM_DIR}/tmp"
    const val DEV_DIR = "${WASI_VM_DIR}/dev"
    const val POSTGRES_WASI_FILE = "$TMP_DIR/pglite/bin/postgres.wasi"

    @JvmStatic
    fun main(args: Array<String>) {
        // Make sure tmp location on host is populated
        val wasiVmDir = Path.of(WASI_VM_DIR)
        if (wasiVmDir.notExists()) wasiVmDir.createDirectories()
        val tmpDirPath = Path.of(TMP_DIR)
        if (tmpDirPath.notExists()) {
            tmpDirPath.createDirectories()
            val archive = PostgresWasiTest::class.java.classLoader.getResource(PGLITE_EMBED_ARCHIVE)
                ?: throw Exception("Could not load resource '${PGLITE_EMBED_ARCHIVE}'")
            extractArchive(Path.of(archive.toURI()), wasiVmDir)
        }

        // Create directory for /dev
        val devDirPath = Path.of(DEV_DIR)
        if (devDirPath.notExists()) devDirPath.createDirectory()

        // Write some bytes to /dev/urandom
        val randomBytes = ByteArray(128)
        SecureRandom().nextBytes(randomBytes)
        devDirPath.resolve("urandom").writeBytes(randomBytes)

        // Create Host and run code
        EmbedderHost {
            fileSystem {
                addPreopenedDirectory(TMP_DIR, "/tmp")
                addPreopenedDirectory(DEV_DIR, "/dev")
            }
            setCommandArgs { listOf("--single", "postgres") }
            setSystemEnv {
                mapOf(
                    "ENVIRONMENT" to "wasi-embed",
                    "REPL" to "Y",
                    "PGUSER" to "postgres",
                    "PGDATABASE" to "template1",
                    "PGDATA" to "/tmp/pglite/base"
                )
            }
        }.use(::bootAndTestPostgres)
    }

    private fun bootAndTestPostgres(embedderHost: EmbedderHost) {

        // Prepare Source
        val source = Source.newBuilder("wasm", Path.of(POSTGRES_WASI_FILE).toUri().toURL())
            .name(POSTGRES_MODULE_NAME)
            .build()

        // Setup Polyglot Context
        val context: Context = Context
            .newBuilder()
            .build()

        context.use {
            // Context must be initialized before installing modules
            context.initialize("wasm")

            // Setup WASI Preview 1 module
            GraalvmWasiPreview1Builder {
                host = embedderHost
            }.build(context)
            // Evaluate the WebAssembly module
            context.eval(source)

            // Run code
            val pgModule = context.getBindings("wasm").getMember(POSTGRES_MODULE_NAME)
            val pgMemory = pgModule.getMember("memory")

            println("- *** Executing '_start' *** -")
            pgModule.getMember("_start").execute()

            println("- *** Executing 'pg_initdb' *** -")
            pgModule.getMember("pg_initdb").execute()

            //println("- *** Executing 'use_socketfile' *** -")
            //val useSocketFileFn = pgModule.getMember("use_socketfile")
            //val useSocketFileRes = useSocketFileFn.execute()

            try {

                println("- *** Executing 'use_wire' *** -")
                pgModule.getMember("use_wire").execute(1)


                println("- *** Writing query to memory *** -")
                val query = "SELECT now();"
                //val bytesWritten = writeQueryToMemory(query, pgModule)
                val queryBytes = PostgresWireProtocol.createQueryMessage(query)


                println("- *** Executing 'interactive_one' *** -")
                val pg_interactiveFn = pgModule.getMember("interactive_one")
                writeStringToMemory(pgMemory, "SELECT now();", 0)
                val pg_interactiveRes = pg_interactiveFn.execute()
                val res = readStringFromMemory(pgMemory, 0, pgMemory.bufferSize.toInt())
                println("- Function returned: $pg_interactiveRes")
                println("- Memory contains: $res")
            }
            catch (e: Exception) {
                println("Exception from 'interactive_one': $e")
                throw e
            }
            return
        }
    }


    private fun writeStringToMemory(memory: Value, s: String, location: Int): Int {
        val stringBytes = s.toByteArray()
        for (i in stringBytes.indices) {
            memory.writeBufferByte((location + i).toLong(), stringBytes[i])
        }

        // Add NUL terminator at the end
        memory.writeBufferByte((location + stringBytes.size).toLong(), 0)

        return stringBytes.size
    }

    private fun readStringFromMemory(memory: Value, location: Int, size: Int): String {
        val stringBytes = ByteArray(size)
        for (i in stringBytes.indices) {
            stringBytes[i] = memory.readBufferByte((location + i).toLong())
        }
        return String(stringBytes)
    }
}