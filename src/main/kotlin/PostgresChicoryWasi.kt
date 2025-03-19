import at.released.weh.bindings.chicory.wasip1.ChicoryWasiPreview1Builder
import at.released.weh.host.EmbedderHost
import com.dylibso.chicory.runtime.ExportFunction
import com.dylibso.chicory.runtime.HostFunction
import com.dylibso.chicory.runtime.ImportValues
import com.dylibso.chicory.runtime.Instance
import com.dylibso.chicory.wasm.Parser
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.createDirectory
import kotlin.io.path.notExists


class PostgresChicoryWasi(val wasiVmDir: String = WASI_VM_DIR) : AutoCloseable {
    val embedderHost : EmbedderHost
    val pgModule : Instance
    private val TMP_DIR = "${WASI_VM_DIR}/tmp"
    private val DEV_DIR = "${WASI_VM_DIR}/dev"
    private val POSTGRES_WASI_FILE : String

    private val interactive_write : ExportFunction
    public val interactive_one : ExportFunction
    private val interactive_read : ExportFunction
    public val use_wire : ExportFunction
    private var bytesWritten = 0

    init {
        val tmpDirPath = Path.of(TMP_DIR)
        if (tmpDirPath.notExists()) {
            tmpDirPath.createDirectories()
            val archive = PostgresWasiTest::class.java.classLoader.getResource(PGLITE_EMBED_ARCHIVE)
                ?: throw Exception("Could not load resource '${PGLITE_EMBED_ARCHIVE}'")
            extractArchive(Path.of(archive.toURI()), Path.of(wasiVmDir))
        }

        // Create directory for /dev
        val devDirPath = Path.of(DEV_DIR)
        if (devDirPath.notExists()) devDirPath.createDirectory()


        POSTGRES_WASI_FILE = "$TMP_DIR/pglite/bin/postgres.wasi"

        val wasmModule = Parser.parse(Path.of(POSTGRES_WASI_FILE))

        embedderHost = EmbedderHost {
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
        }


        // Prepare WASI host imports
        val wasiImports: List<HostFunction> = ChicoryWasiPreview1Builder {
            host = embedderHost
        }.build()

        val hostImports = ImportValues.builder().withFunctions(wasiImports).build()

        // Instantiate the WebAssembly module
        pgModule = Instance
            .builder(wasmModule)
            .withImportValues(hostImports)
            .withInitialize(true)
            .withStart(false)
            .build()


        interactive_write = pgModule.export("interactive_write")
        interactive_one = pgModule.export("interactive_one")
        interactive_read = pgModule.export("interactive_read")
        use_wire = pgModule.export("use_wire")

    }
    fun start() {
        //println("- *** Executing '_start' *** -")
        pgModule.export("_start").apply()

        //println("- *** Executing 'pg_initdb' *** -")
        pgModule.export("pg_initdb").apply()

        use_wire.apply(1)
    }

    fun submitPgWireMessage(queryBytes: ByteArray) {
        interactive_write.apply(queryBytes.size.toLong())
        pgModule.memory().write(PGLITE_QUERY_CHANNEL, queryBytes)
        bytesWritten = queryBytes.size
        println("interactive_write $bytesWritten")
        //interactive_one.apply()
        //println("interactive_one")
    }

    fun readWireBuffer() : ByteArray?{
        val readRes = interactive_read.apply()[0].toInt()
        if (readRes>0) {
            val res = pgModule.memory().readBytes(PGLITE_RESULT_CHANNEL, readRes + 2 + bytesWritten)
            val rtn = res.copyOfRange(bytesWritten+2, res.size)
            println("CMA reply length $readRes at ${bytesWritten+2}:")
            interactive_write.apply(0)
            bytesWritten = 0
            return rtn
        }
        return null
    }

    override fun close() {
        embedderHost.close()
    }

    companion object {
        private const val WASI_VM_DIR = "./PgWasi"
        private const val PGLITE_EMBED_ARCHIVE = "pglite-wasi.tar.gz"
        private const val PGLITE_RESULT_CHANNEL = 0
        private const val PGLITE_QUERY_CHANNEL = 1
    }
}