
import org.apache.commons.compress.archivers.ArchiveEntry
import org.apache.commons.compress.archivers.ArchiveInputStream
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Path
import java.util.zip.ZipInputStream
import kotlin.io.path.*

/**
 * Extracts a ZIP or TAR.GZ archive to the specified destination directory.
 *
 * @param archiveFilePath Path to the archive file (ZIP or TAR.GZ)
 * @param destDirectoryPath Destination directory where files will be extracted
 * @param useNativeZip Whether to use native Java ZIP handling (true) or Apache Commons Compress (false)
 * @return Number of files extracted
 */
fun extractArchive(archiveFilePath: Path, destDirectoryPath: Path, useNativeZip: Boolean = true): Int {
    if (archiveFilePath.notExists()) throw IllegalArgumentException("Archive file does not exist: $archiveFilePath")

    // Create destination directory if it doesn't exist
    if (destDirectoryPath.notExists()) destDirectoryPath.createDirectory()

    return when {
        archiveFilePath.extension.endsWith("zip", ignoreCase = true) -> {
            if (useNativeZip) {
                extractZipNative(archiveFilePath, destDirectoryPath)
            } else {
                extractWithCommonsCompress(archiveFilePath, destDirectoryPath, ArchiveType.ZIP)
            }
        }
        archiveFilePath.name.endsWith(".tar.gz", ignoreCase = true) ||
        archiveFilePath.name.endsWith(".tgz", ignoreCase = true) -> {
            extractWithCommonsCompress(archiveFilePath, destDirectoryPath, ArchiveType.TAR_GZ)
        }
        else -> throw IllegalArgumentException("Unsupported archive format. Supported formats: .zip, .tar.gz, .tgz")
    }
}

/**
 * Archive types supported by this extractor
 */
private enum class ArchiveType {
    ZIP, TAR_GZ
}

/**
 * Extracts an archive using Apache Commons Compress
 */
private fun extractWithCommonsCompress(
    archiveFilePath: Path,
    destDirectoryPath: Path,
    archiveType: ArchiveType
): Int {
    var extractedFileCount = 0

    archiveFilePath.inputStream().use { fileIs ->
        fileIs.buffered().use { bufferedIs ->
            // Create the appropriate archive input stream based on the archive type
            val archiveInputStream: ArchiveInputStream<*> = when (archiveType) {
                ArchiveType.ZIP -> ZipArchiveInputStream(bufferedIs)
                ArchiveType.TAR_GZ -> TarArchiveInputStream(GzipCompressorInputStream(bufferedIs))
            }

            archiveInputStream.use { archiveIs ->
                var entry: ArchiveEntry? = archiveIs.nextEntry

                while (entry != null) {
                    val outputFile = File(destDirectoryPath.toFile(), entry.name)

                    if (entry.isDirectory) {
                        outputFile.mkdirs()
                    } else {
                        // Create parent directories for files if they don't exist
                        outputFile.parentFile?.mkdirs()

                        // Extract file
                        FileOutputStream(outputFile).use { output ->
                            archiveIs.copyTo(output)
                            extractedFileCount++
                        }
                    }

                    entry = archiveIs.nextEntry
                }
            }
        }
    }

    return extractedFileCount
}

/**
 * Extracts a ZIP archive using Java's native ZIP implementation
 */
private fun extractZipNative(zipFilePath: Path, destDirectoryPath: Path): Int {
    var extractedFileCount = 0

    zipFilePath.inputStream().use { fileIs ->
        fileIs.buffered().use { bufferedIs ->
            ZipInputStream(bufferedIs).use { zipIs ->
                var entry = zipIs.nextEntry

                while (entry != null) {
                    val outputFile = File(destDirectoryPath.toFile(), entry.name)

                    if (entry.isDirectory) {
                        outputFile.mkdirs()
                    } else {
                        // Create parent directories for files if they don't exist
                        outputFile.parentFile?.mkdirs()

                        // Extract file
                        FileOutputStream(outputFile).use { output ->
                            zipIs.copyTo(output)
                            extractedFileCount++
                        }
                    }

                    zipIs.closeEntry()
                    entry = zipIs.nextEntry
                }
            }
        }
    }

    return extractedFileCount
}

/**
 * Example usage
 */
fun main() {
    val archiveFilePath = "/path/to/your/archive.tar.gz" // or .zip
    val destDirectoryPath = "/path/to/extract"

    try {
        val extractedFiles = extractArchive(Path.of(archiveFilePath), Path.of(destDirectoryPath))
        println("Extraction completed successfully. Extracted $extractedFiles files.")
    } catch (e: Exception) {
        println("Extraction failed: ${e.message}")
        e.printStackTrace()
    }
}