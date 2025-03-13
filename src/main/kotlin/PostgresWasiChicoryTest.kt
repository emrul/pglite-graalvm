
import at.released.weh.bindings.chicory.wasip1.ChicoryWasiPreview1Builder
import at.released.weh.host.EmbedderHost
import com.dylibso.chicory.runtime.ExportFunction
import com.dylibso.chicory.runtime.HostFunction
import com.dylibso.chicory.runtime.ImportValues
import com.dylibso.chicory.runtime.Instance
import com.dylibso.chicory.wasm.Parser
import com.dylibso.chicory.wasm.WasmModule
import jdk.internal.joptsimple.internal.Messages.message
import java.nio.file.Path
import java.security.SecureRandom
import kotlin.io.path.createDirectories
import kotlin.io.path.createDirectory
import kotlin.io.path.notExists
import kotlin.io.path.writeBytes


/*
    Refs:
        1) https://github.com/electric-sql/pglite/issues/89
        2) https://github.com/sgosiaco/pglite-go/

 */
object PostgresWasiChicoryTest {
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
            val archive = PostgresWasiChicoryTest::class.java.classLoader.getResource(PGLITE_EMBED_ARCHIVE)
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


        // Prepare Source
        val wasmModule = Parser.parse(Path.of(POSTGRES_WASI_FILE))
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
        }.use {
            bootAndTestPostgres(it, wasmModule)
        }
    }

    private fun bootAndTestPostgres(embedderHost: EmbedderHost, wasmModule: WasmModule) {


        // Prepare WASI host imports
        val wasiImports: List<HostFunction> = ChicoryWasiPreview1Builder {
            host = embedderHost
        }.build()

        val hostImports = ImportValues.builder().withFunctions(wasiImports).build()

        // Instantiate the WebAssembly module
        val pgModule = Instance
            .builder(wasmModule)
            .withImportValues(hostImports)
            .withInitialize(true)
            .withStart(false)
            .build()

        println("- *** Executing '_start' *** -")
        pgModule.export("_start").apply()

        println("- *** Executing 'pg_initdb' *** -")
        pgModule.export("pg_initdb").apply()

        println("- *** Executing 'use_socketfile' *** -")
        val useSocketFileFn = pgModule.export("use_socketfile").apply()

        try {
            println("- *** Executing 'interactive_one' *** -")
            val query = "SELECT now();"
            val len = query.toByteArray().size;
            val alloc = pgModule.export("alloc")
            val dealloc = pgModule.export("dealloc")
            val pg_interactiveFn = pgModule.export("interactive_one")

            // We can now write the message to the module's memory:
            val memory = pgModule.memory();
            memory.writeString(alloc.apply(len).get(0).toInt(), query)

            val pg_interactiveRes = pg_interactiveFn.apply()
            val res = readStringFromMemory(pgMemory, 0, pgMemory.bufferSize.toInt())
            println("- Function returned: $pg_interactiveRes")
            println("- Memory contains: $res")
        }
        catch (e: Exception) {
            println("Exception from 'interactive_one': $e")
            throw e
        }
    }

}