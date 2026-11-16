package com.explorerpro.core.security

import android.content.Context
import android.net.Uri
import com.explorerpro.domain.model.ArchiveCompressionLevel
import com.explorerpro.domain.model.ArchiveCreateFormat
import com.explorerpro.domain.model.ArchiveEncryptionMode
import com.explorerpro.domain.model.ArchiveEntryItem
import com.github.junrar.Archive
import com.github.junrar.rarfile.FileHeader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import net.lingala.zip4j.ZipFile as Zip4jFile
import net.lingala.zip4j.model.ZipParameters
import net.lingala.zip4j.model.enums.AesKeyStrength
import net.lingala.zip4j.model.enums.CompressionLevel
import net.lingala.zip4j.model.enums.CompressionMethod
import net.lingala.zip4j.model.enums.EncryptionMethod
import org.apache.commons.compress.archivers.ArchiveEntry
import org.apache.commons.compress.archivers.ArchiveInputStream
import org.apache.commons.compress.archivers.ArchiveOutputStream
import org.apache.commons.compress.archivers.cpio.CpioArchiveEntry
import org.apache.commons.compress.archivers.cpio.CpioArchiveInputStream
import org.apache.commons.compress.archivers.cpio.CpioArchiveOutputStream
import org.apache.commons.compress.archivers.jar.JarArchiveEntry
import org.apache.commons.compress.archivers.jar.JarArchiveOutputStream
import org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry
import org.apache.commons.compress.archivers.sevenz.SevenZFile
import org.apache.commons.compress.archivers.sevenz.SevenZMethod
import org.apache.commons.compress.archivers.sevenz.SevenZOutputFile
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorOutputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream
import org.apache.commons.compress.compressors.gzip.GzipParameters
import org.apache.commons.compress.compressors.lz4.FramedLZ4CompressorInputStream
import org.apache.commons.compress.compressors.lz4.FramedLZ4CompressorOutputStream
import org.apache.commons.compress.compressors.lzma.LZMACompressorInputStream
import org.apache.commons.compress.compressors.lzma.LZMACompressorOutputStream
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream
import org.apache.commons.compress.compressors.xz.XZCompressorOutputStream
import org.apache.commons.compress.compressors.zstandard.ZstdCompressorInputStream
import org.apache.commons.compress.compressors.zstandard.ZstdCompressorOutputStream
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlin.coroutines.coroutineContext

/**
 * Detailed progress callback for Archive Engine operations.
 */
fun interface ArchiveProgressListener {
    fun onProgress(
        fraction: Float,
        processedItems: Int,
        totalItems: Int,
        processedBytes: Long,
        totalBytes: Long,
        currentItemName: String
    )
}

/**
 * Multi-format native Kotlin/JVM Archive Engine with strict security hardening:
 * - Supports ZIP, TAR, GZIP (.gz / .tar.gz / .tgz), BZIP2 (.bz2 / .tar.bz2 / .tbz2),
 *   XZ (.xz / .tar.xz / .txz), 7Z (.7z with optional password), RAR (.rar), ISO 9660 (.iso), APK & JAR.
 * - Prevents ZIP Slip & Path Traversal attacks across all archive formats.
 * - Enforces configurable Decompression Bomb limits (max total bytes, max entries, max compression ratio).
 * - Detects duplicate archive entries.
 * - Supports both local File paths and SAF / Termux content:// URIs.
 */
class ArchiveEngine(
    private val maxTotalExtractedBytes: Long = 1024L * 1024L * 1024L, // 1 GB default limit
    private val maxEntryCount: Int = 50_000,
    private val maxCompressionRatio: Int = 150
) {

    /**
     * Validates that [entryName] resolves strictly inside [destinationDir] and does not contain
     * path traversal sequences, absolute path prefixes, null bytes, or drive letters.
     */
    fun validateSafeDestinationFile(destinationDir: File, entryName: String): File {
        if (entryName.isBlank()) {
            throw SecurityException("Archive entry path cannot be blank.")
        }
        if (entryName.contains('\u0000')) {
            throw SecurityException("Archive entry contains illegal null byte: $entryName")
        }
        val normalized = entryName.replace('\\', '/')
        if (normalized.startsWith("/") || normalized.matches(Regex("^[A-Za-z]:.*"))) {
            throw SecurityException("Absolute paths are rejected in archive entries: $entryName")
        }
        val segments = normalized.split('/')
        if (segments.any { it == ".." }) {
            throw SecurityException("ZIP Slip path traversal blocked for entry: $entryName")
        }

        val canonicalDestRoot = destinationDir.canonicalPath
        val targetFile = File(destinationDir, normalized)
        val canonicalTarget = targetFile.canonicalPath

        val expectedPrefix = if (canonicalDestRoot.endsWith(File.separator)) {
            canonicalDestRoot
        } else {
            canonicalDestRoot + File.separator
        }

        if (canonicalTarget != canonicalDestRoot && !canonicalTarget.startsWith(expectedPrefix)) {
            throw SecurityException(
                "ZIP Slip path traversal blocked: '$entryName' escapes '$canonicalDestRoot'"
            )
        }
        return targetFile
    }

    fun isEntryPathSafe(destinationDir: File, entryName: String): Boolean {
        return runCatching {
            validateSafeDestinationFile(destinationDir, entryName)
            true
        }.getOrDefault(false)
    }

    /**
     * Lists contents of any supported archive (ZIP, TAR, GZ/TGZ, BZ2/TBZ2, XZ/TXZ, 7Z, RAR, ISO, APK)
     * from either a local File or a SAF/Termux content:// URI.
     */
    suspend fun listArchiveEntriesFromPathOrUri(
        context: Context,
        pathOrUri: String,
        displayName: String,
        password: String? = null
    ): Result<List<ArchiveEntryItem>> = withContext(Dispatchers.IO) {
        if (!pathOrUri.startsWith("content://")) {
            return@withContext listArchiveEntries(File(pathOrUri), password)
        }
        runCatching {
            val uri = Uri.parse(pathOrUri)
            val lower = displayName.lowercase()
            // For random-access formats (7z, rar, iso), stage to a temporary cache file
            if (lower.endsWith(".7z") || lower.endsWith(".rar") || lower.endsWith(".iso")) {
                val tempFile = copySafUriToTempCacheFile(context, uri, displayName)
                try {
                    return@runCatching listArchiveEntries(tempFile, password).getOrThrow()
                } finally {
                    tempFile.delete()
                }
            }
            val dummyDest = context.cacheDir
            context.contentResolver.openInputStream(uri)?.use { rawStream ->
                listEntriesFromInputStreamByFormat(
                    rawStream = rawStream,
                    fileName = displayName,
                    dummyDest = dummyDest,
                    totalArchiveBytes = 0L
                )
            } ?: throw IOException("Unable to open archive stream: $displayName")
        }
    }

    suspend fun listArchiveEntries(
        archiveFile: File,
        password: String? = null
    ): Result<List<ArchiveEntryItem>> = withContext(Dispatchers.IO) {
        runCatching {
            if (!archiveFile.exists() || !archiveFile.canRead()) {
                throw IOException("Archive does not exist or is unreadable: ${archiveFile.name}")
            }
            val lowerName = archiveFile.name.lowercase()
            val dummyDest = archiveFile.parentFile ?: File("/tmp")

            when {
                lowerName.endsWith(".7z") -> listSevenZEntries(archiveFile, dummyDest, password)
                lowerName.endsWith(".rar") -> listRarEntries(archiveFile, dummyDest)
                lowerName.endsWith(".iso") -> listIso9660Entries(archiveFile, dummyDest)
                lowerName.endsWith(".zip") || lowerName.endsWith(".apk") || lowerName.endsWith(".jar") ||
                    lowerName.endsWith(".docx") || lowerName.endsWith(".xlsx") || lowerName.endsWith(".pptx") -> {
                    listZipEntries(archiveFile, dummyDest)
                }
                else -> {
                    // Check magic bytes for 7z / RAR / ISO / GZ / BZ2 / XZ / TAR / ZIP even if renamed
                    val header = ByteArray(16)
                    val read = FileInputStream(archiveFile).use { it.read(header) }
                    if (read >= 6 && header[0] == 0x37.toByte() && header[1] == 0x7A.toByte() &&
                        header[2] == 0xBC.toByte() && header[3] == 0xAF.toByte()
                    ) {
                        return@runCatching listSevenZEntries(archiveFile, dummyDest, password)
                    }
                    if (read >= 6 && header[0] == 0x52.toByte() && header[1] == 0x61.toByte() &&
                        header[2] == 0x72.toByte() && header[3] == 0x21.toByte()
                    ) {
                        return@runCatching listRarEntries(archiveFile, dummyDest)
                    }
                    FileInputStream(archiveFile).use { fis ->
                        listEntriesFromInputStreamByFormat(
                            rawStream = fis,
                            fileName = archiveFile.name,
                            dummyDest = dummyDest,
                            totalArchiveBytes = archiveFile.length()
                        )
                    }
                }
            }
        }
    }

    private fun listEntriesFromInputStreamByFormat(
        rawStream: InputStream,
        fileName: String,
        dummyDest: File,
        totalArchiveBytes: Long
    ): List<ArchiveEntryItem> {
        val lower = fileName.lowercase()
        val buffered = BufferedInputStream(rawStream, 64 * 1024)
        buffered.mark(32)
        val magic = ByteArray(16)
        val magicLen = buffered.read(magic).coerceAtLeast(0)
        buffered.reset()

        val isGzipMagic = magicLen >= 2 && magic[0] == 0x1F.toByte() && magic[1] == 0x8B.toByte()
        val isBzip2Magic = magicLen >= 3 && magic[0] == 0x42.toByte() && magic[1] == 0x5A.toByte() && magic[2] == 0x68.toByte()
        val isXzMagic = magicLen >= 6 && magic[0] == 0xFD.toByte() && magic[1] == 0x37.toByte() && magic[2] == 0x7A.toByte()
        val isLz4Magic = magicLen >= 4 && magic[0] == 0x04.toByte() && magic[1] == 0x22.toByte() && magic[2] == 0x4D.toByte() && magic[3] == 0x18.toByte()
        val isZstdMagic = magicLen >= 4 && magic[0] == 0x28.toByte() && magic[1] == 0xB5.toByte() && magic[2] == 0x2F.toByte() && magic[3] == 0xFD.toByte()
        val isZipMagic = magicLen >= 4 && magic[0] == 0x50.toByte() && magic[1] == 0x4B.toByte()
        val cpioMagic = if (magicLen >= 6) String(magic, 0, 6, Charsets.US_ASCII) else ""
        val isCpioMagic = cpioMagic == "070701" || cpioMagic == "070702" || cpioMagic == "070707"

        return when {
            lower.endsWith(".tar.gz") || lower.endsWith(".tgz") -> {
                GzipCompressorInputStream(buffered).use { gzis ->
                    listCommonsArchiveStream(TarArchiveInputStream(BufferedInputStream(gzis)), dummyDest, "TAR.GZ")
                }
            }
            lower.endsWith(".tar.bz2") || lower.endsWith(".tbz2") -> {
                BZip2CompressorInputStream(buffered).use { bzis ->
                    listCommonsArchiveStream(TarArchiveInputStream(BufferedInputStream(bzis)), dummyDest, "TAR.BZ2")
                }
            }
            lower.endsWith(".tar.xz") || lower.endsWith(".txz") -> {
                XZCompressorInputStream(buffered).use { xzis ->
                    listCommonsArchiveStream(TarArchiveInputStream(BufferedInputStream(xzis)), dummyDest, "TAR.XZ")
                }
            }
            lower.endsWith(".tar.lz4") || lower.endsWith(".tlz4") -> {
                FramedLZ4CompressorInputStream(buffered).use { lz4is ->
                    listCommonsArchiveStream(TarArchiveInputStream(BufferedInputStream(lz4is)), dummyDest, "TAR.LZ4")
                }
            }
            lower.endsWith(".tar.zst") || lower.endsWith(".tar.zstd") || lower.endsWith(".tzst") -> {
                ZstdCompressorInputStream(buffered).use { zstis ->
                    listCommonsArchiveStream(TarArchiveInputStream(BufferedInputStream(zstis)), dummyDest, "TAR.ZSTD")
                }
            }
            lower.endsWith(".tar.lzma") || lower.endsWith(".tlz") -> {
                LZMACompressorInputStream(buffered).use { lzmais ->
                    listCommonsArchiveStream(TarArchiveInputStream(BufferedInputStream(lzmais)), dummyDest, "TAR.LZMA")
                }
            }
            lower.endsWith(".cpio") || isCpioMagic -> {
                listCommonsArchiveStream(CpioArchiveInputStream(buffered), dummyDest, "CPIO")
            }
            lower.endsWith(".tar") -> {
                listCommonsArchiveStream(TarArchiveInputStream(buffered), dummyDest, "TAR")
            }
            lower.endsWith(".gz") || isGzipMagic -> {
                GzipCompressorInputStream(buffered).use { gzis ->
                    val innerBuffer = BufferedInputStream(gzis, 64 * 1024)
                    if (isStreamTarArchive(innerBuffer)) {
                        listCommonsArchiveStream(TarArchiveInputStream(innerBuffer), dummyDest, "TAR.GZ")
                    } else {
                        val singleName = fileName.removeSuffix(".gz").ifBlank { "${fileName}_decompressed" }
                        listOf(
                            ArchiveEntryItem(
                                entryPath = singleName,
                                name = singleName.substringAfterLast('/'),
                                uncompressedSize = totalArchiveBytes,
                                compressedSize = totalArchiveBytes,
                                isDirectory = false,
                                lastModified = System.currentTimeMillis(),
                                crc32Hex = "GZIP",
                                isPathSafe = isEntryPathSafe(dummyDest, singleName),
                                compressionRatioPercent = 0
                            )
                        )
                    }
                }
            }
            lower.endsWith(".bz2") || isBzip2Magic -> {
                BZip2CompressorInputStream(buffered).use { bzis ->
                    val innerBuffer = BufferedInputStream(bzis, 64 * 1024)
                    if (isStreamTarArchive(innerBuffer)) {
                        listCommonsArchiveStream(TarArchiveInputStream(innerBuffer), dummyDest, "TAR.BZ2")
                    } else {
                        val singleName = fileName.removeSuffix(".bz2").ifBlank { "${fileName}_decompressed" }
                        listOf(
                            ArchiveEntryItem(
                                entryPath = singleName,
                                name = singleName.substringAfterLast('/'),
                                uncompressedSize = totalArchiveBytes,
                                compressedSize = totalArchiveBytes,
                                isDirectory = false,
                                lastModified = System.currentTimeMillis(),
                                crc32Hex = "BZIP2",
                                isPathSafe = isEntryPathSafe(dummyDest, singleName),
                                compressionRatioPercent = 0
                            )
                        )
                    }
                }
            }
            lower.endsWith(".xz") || isXzMagic -> {
                XZCompressorInputStream(buffered).use { xzis ->
                    val innerBuffer = BufferedInputStream(xzis, 64 * 1024)
                    if (isStreamTarArchive(innerBuffer)) {
                        listCommonsArchiveStream(TarArchiveInputStream(innerBuffer), dummyDest, "TAR.XZ")
                    } else {
                        val singleName = fileName.removeSuffix(".xz").ifBlank { "${fileName}_decompressed" }
                        listOf(
                            ArchiveEntryItem(
                                entryPath = singleName,
                                name = singleName.substringAfterLast('/'),
                                uncompressedSize = totalArchiveBytes,
                                compressedSize = totalArchiveBytes,
                                isDirectory = false,
                                lastModified = System.currentTimeMillis(),
                                crc32Hex = "XZ",
                                isPathSafe = isEntryPathSafe(dummyDest, singleName),
                                compressionRatioPercent = 0
                            )
                        )
                    }
                }
            }
            lower.endsWith(".lz4") || isLz4Magic -> {
                FramedLZ4CompressorInputStream(buffered).use { lz4is ->
                    val innerBuffer = BufferedInputStream(lz4is, 64 * 1024)
                    if (isStreamTarArchive(innerBuffer)) {
                        listCommonsArchiveStream(TarArchiveInputStream(innerBuffer), dummyDest, "TAR.LZ4")
                    } else {
                        val singleName = fileName.removeSuffix(".lz4").ifBlank { "${fileName}_decompressed" }
                        listOf(
                            ArchiveEntryItem(
                                entryPath = singleName,
                                name = singleName.substringAfterLast('/'),
                                uncompressedSize = totalArchiveBytes,
                                compressedSize = totalArchiveBytes,
                                isDirectory = false,
                                lastModified = System.currentTimeMillis(),
                                crc32Hex = "LZ4",
                                isPathSafe = isEntryPathSafe(dummyDest, singleName),
                                compressionRatioPercent = 0
                            )
                        )
                    }
                }
            }
            lower.endsWith(".zst") || lower.endsWith(".zstd") || isZstdMagic -> {
                ZstdCompressorInputStream(buffered).use { zstis ->
                    val innerBuffer = BufferedInputStream(zstis, 64 * 1024)
                    if (isStreamTarArchive(innerBuffer)) {
                        listCommonsArchiveStream(TarArchiveInputStream(innerBuffer), dummyDest, "TAR.ZSTD")
                    } else {
                        val singleName = fileName.removeSuffix(".zst").removeSuffix(".zstd").ifBlank { "${fileName}_decompressed" }
                        listOf(
                            ArchiveEntryItem(
                                entryPath = singleName,
                                name = singleName.substringAfterLast('/'),
                                uncompressedSize = totalArchiveBytes,
                                compressedSize = totalArchiveBytes,
                                isDirectory = false,
                                lastModified = System.currentTimeMillis(),
                                crc32Hex = "ZSTD",
                                isPathSafe = isEntryPathSafe(dummyDest, singleName),
                                compressionRatioPercent = 0
                            )
                        )
                    }
                }
            }
            lower.endsWith(".lzma") -> {
                LZMACompressorInputStream(buffered).use { lzmais ->
                    val innerBuffer = BufferedInputStream(lzmais, 64 * 1024)
                    if (isStreamTarArchive(innerBuffer)) {
                        listCommonsArchiveStream(TarArchiveInputStream(innerBuffer), dummyDest, "TAR.LZMA")
                    } else {
                        val singleName = fileName.removeSuffix(".lzma").ifBlank { "${fileName}_decompressed" }
                        listOf(
                            ArchiveEntryItem(
                                entryPath = singleName,
                                name = singleName.substringAfterLast('/'),
                                uncompressedSize = totalArchiveBytes,
                                compressedSize = totalArchiveBytes,
                                isDirectory = false,
                                lastModified = System.currentTimeMillis(),
                                crc32Hex = "LZMA",
                                isPathSafe = isEntryPathSafe(dummyDest, singleName),
                                compressionRatioPercent = 0
                            )
                        )
                    }
                }
            }
            isZipMagic -> listZipStreamEntries(buffered, dummyDest)
            isStreamTarArchive(buffered) -> listCommonsArchiveStream(TarArchiveInputStream(buffered), dummyDest, "TAR")
            else -> listZipStreamEntries(buffered, dummyDest)
        }
    }

    private fun isStreamTarArchive(buffered: BufferedInputStream): Boolean {
        buffered.mark(520)
        val header = ByteArray(512)
        var offset = 0
        while (offset < 512) {
            val read = buffered.read(header, offset, 512 - offset)
            if (read <= 0) break
            offset += read
        }
        buffered.reset()
        if (offset < 263) return false
        val ustar = header[257] == 'u'.code.toByte() &&
            header[258] == 's'.code.toByte() &&
            header[259] == 't'.code.toByte() &&
            header[260] == 'a'.code.toByte() &&
            header[261] == 'r'.code.toByte()
        if (ustar) return true
        // Also check valid octal size field at 124..135 for classic V7 tar archives
        val nameFirstByte = header[0].toInt() and 0xFF
        val sizeStr = header.decodeToString(124, 136).trim('\u0000', ' ')
        return nameFirstByte in 0x20..0x7E && sizeStr.isNotEmpty() && sizeStr.all { it in '0'..'7' }
    }

    private fun listZipEntries(archiveFile: File, dummyDest: File): List<ArchiveEntryItem> {
        val items = mutableListOf<ArchiveEntryItem>()
        val seenPaths = mutableSetOf<String>()
        val zip4jResult = runCatching {
            val z4j = Zip4jFile(archiveFile)
            val headers = z4j.fileHeaders
            var count = 0
            for (header in headers) {
                count++
                if (count > maxEntryCount) {
                    throw SecurityException("Archive exceeds maximum entry limit ($maxEntryCount).")
                }
                val entryName = header.fileName
                val safe = isEntryPathSafe(dummyDest, entryName) && seenPaths.add(entryName)
                val uncompressed = header.uncompressedSize.coerceAtLeast(0L)
                val compressed = header.compressedSize.coerceAtLeast(0L)
                val ratio = if (uncompressed > 0 && compressed >= 0) {
                    (((uncompressed - compressed).toDouble() / uncompressed.toDouble()) * 100.0)
                        .toInt()
                        .coerceIn(0, 99)
                } else 0
                items.add(
                    ArchiveEntryItem(
                        entryPath = entryName,
                        name = entryName.trimEnd('/').substringAfterLast('/'),
                        uncompressedSize = uncompressed,
                        compressedSize = compressed,
                        isDirectory = header.isDirectory,
                        lastModified = if (header.lastModifiedTimeEpoch > 0) header.lastModifiedTimeEpoch else archiveFile.lastModified(),
                        crc32Hex = if (header.crc > 0L) header.crc.toString(16).uppercase().padStart(8, '0') else "ZIP",
                        isPathSafe = safe,
                        compressionRatioPercent = ratio,
                        isEncrypted = header.isEncrypted
                    )
                )
            }
        }
        if (zip4jResult.isSuccess && items.isNotEmpty()) {
            return items.sortedWith(compareByDescending<ArchiveEntryItem> { it.isDirectory }.thenBy { it.entryPath })
        }
        items.clear()
        seenPaths.clear()
        ZipFile(archiveFile).use { zip ->
            val entries = zip.entries()
            var count = 0
            while (entries.hasMoreElements()) {
                count++
                if (count > maxEntryCount) {
                    throw SecurityException("Archive exceeds maximum entry limit ($maxEntryCount).")
                }
                val entry = entries.nextElement()
                val safe = isEntryPathSafe(dummyDest, entry.name) && seenPaths.add(entry.name)
                val uncompressed = entry.size.coerceAtLeast(0L)
                val compressed = entry.compressedSize.coerceAtLeast(0L)
                val ratio = if (uncompressed > 0 && compressed >= 0) {
                    (((uncompressed - compressed).toDouble() / uncompressed.toDouble()) * 100.0)
                        .toInt()
                        .coerceIn(0, 99)
                } else 0

                items.add(
                    ArchiveEntryItem(
                        entryPath = entry.name,
                        name = entry.name.trimEnd('/').substringAfterLast('/'),
                        uncompressedSize = uncompressed,
                        compressedSize = compressed,
                        isDirectory = entry.isDirectory,
                        lastModified = if (entry.time > 0) entry.time else archiveFile.lastModified(),
                        crc32Hex = if (entry.crc >= 0) entry.crc.toString(16).uppercase().padStart(8, '0') else "N/A",
                        isPathSafe = safe,
                        compressionRatioPercent = ratio
                    )
                )
            }
        }
        return items.sortedWith(compareByDescending<ArchiveEntryItem> { it.isDirectory }.thenBy { it.entryPath })
    }

    private fun listZipStreamEntries(input: InputStream, dummyDest: File): List<ArchiveEntryItem> {
        val items = mutableListOf<ArchiveEntryItem>()
        val seenPaths = mutableSetOf<String>()
        ZipInputStream(input).use { zis ->
            var entry = zis.nextEntry
            var count = 0
            while (entry != null) {
                count++
                if (count > maxEntryCount) {
                    throw SecurityException("Archive exceeds maximum entry limit ($maxEntryCount).")
                }
                val safe = isEntryPathSafe(dummyDest, entry.name) && seenPaths.add(entry.name)
                val uncompressed = entry.size.coerceAtLeast(0L)
                val compressed = entry.compressedSize.coerceAtLeast(0L)
                items.add(
                    ArchiveEntryItem(
                        entryPath = entry.name,
                        name = entry.name.trimEnd('/').substringAfterLast('/'),
                        uncompressedSize = uncompressed,
                        compressedSize = compressed,
                        isDirectory = entry.isDirectory,
                        lastModified = if (entry.time > 0) entry.time else System.currentTimeMillis(),
                        crc32Hex = if (entry.crc >= 0) entry.crc.toString(16).uppercase().padStart(8, '0') else "STREAM",
                        isPathSafe = safe,
                        compressionRatioPercent = 0
                    )
                )
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
        return items.sortedWith(compareByDescending<ArchiveEntryItem> { it.isDirectory }.thenBy { it.entryPath })
    }

    private fun listCommonsArchiveStream(
        archiveStream: ArchiveInputStream<out ArchiveEntry>,
        dummyDest: File,
        formatTag: String
    ): List<ArchiveEntryItem> {
        val items = mutableListOf<ArchiveEntryItem>()
        val seenPaths = mutableSetOf<String>()
        archiveStream.use { ais ->
            var entry = ais.nextEntry
            var count = 0
            while (entry != null) {
                count++
                if (count > maxEntryCount) {
                    throw SecurityException("Archive exceeds maximum entry limit ($maxEntryCount).")
                }
                val rawName = entry.name
                val safe = isEntryPathSafe(dummyDest, rawName) && seenPaths.add(rawName)
                val size = entry.size.coerceAtLeast(0L)
                val modTime = runCatching { entry.lastModifiedDate?.time ?: System.currentTimeMillis() }
                    .getOrDefault(System.currentTimeMillis())

                items.add(
                    ArchiveEntryItem(
                        entryPath = rawName,
                        name = rawName.trimEnd('/').substringAfterLast('/'),
                        uncompressedSize = size,
                        compressedSize = size,
                        isDirectory = entry.isDirectory,
                        lastModified = modTime,
                        crc32Hex = formatTag,
                        isPathSafe = safe,
                        compressionRatioPercent = 0
                    )
                )
                entry = ais.nextEntry
            }
        }
        return items.sortedWith(compareByDescending<ArchiveEntryItem> { it.isDirectory }.thenBy { it.entryPath })
    }

    private fun listSevenZEntries(
        archiveFile: File,
        dummyDest: File,
        password: String?
    ): List<ArchiveEntryItem> {
        val items = mutableListOf<ArchiveEntryItem>()
        val seenPaths = mutableSetOf<String>()
        @Suppress("DEPRECATION")
        val sevenZInstance = if (!password.isNullOrEmpty()) {
            SevenZFile(archiveFile, password.toCharArray())
        } else {
            SevenZFile(archiveFile)
        }
        sevenZInstance.use { sevenZ ->
            var count = 0
            for (entry in sevenZ.entries) {
                val rawName = entry.name ?: continue
                count++
                if (count > maxEntryCount) {
                    throw SecurityException("7z archive exceeds maximum entry limit ($maxEntryCount).")
                }
                val safe = isEntryPathSafe(dummyDest, rawName) && seenPaths.add(rawName)
                val size = entry.size.coerceAtLeast(0L)
                val crcHex = if (entry.hasCrc) entry.crcValue.toString(16).uppercase().padStart(8, '0') else "7Z"
                val modTime = if (entry.hasLastModifiedDate) entry.lastModifiedDate.time else archiveFile.lastModified()

                items.add(
                    ArchiveEntryItem(
                        entryPath = rawName,
                        name = rawName.trimEnd('/').substringAfterLast('/'),
                        uncompressedSize = size,
                        compressedSize = size,
                        isDirectory = entry.isDirectory,
                        lastModified = modTime,
                        crc32Hex = crcHex,
                        isPathSafe = safe,
                        compressionRatioPercent = 0
                    )
                )
            }
        }
        return items.sortedWith(compareByDescending<ArchiveEntryItem> { it.isDirectory }.thenBy { it.entryPath })
    }

    private fun listRarEntries(archiveFile: File, dummyDest: File): List<ArchiveEntryItem> {
        val items = mutableListOf<ArchiveEntryItem>()
        val seenPaths = mutableSetOf<String>()
        Archive(archiveFile).use { rar ->
            if (rar.isEncrypted) {
                throw IOException("Encrypted RAR headers require unencrypted header access.")
            }
            var count = 0
            for (header in rar.fileHeaders) {
                val rawName = header.fileName.replace('\\', '/').trimStart('/')
                if (rawName.isBlank()) continue
                count++
                if (count > maxEntryCount) {
                    throw SecurityException("RAR archive exceeds maximum entry limit ($maxEntryCount).")
                }
                val safe = isEntryPathSafe(dummyDest, rawName) && seenPaths.add(rawName)
                val unpSize = header.fullUnpackSize.coerceAtLeast(0L)
                val packSize = header.fullPackSize.coerceAtLeast(0L)
                val ratio = if (unpSize > 0 && packSize >= 0) {
                    (((unpSize - packSize).toDouble() / unpSize.toDouble()) * 100.0).toInt().coerceIn(0, 99)
                } else 0
                val crcHex = header.fileCRC.toLong().and(0xFFFFFFFFL).toString(16).uppercase().padStart(8, '0')

                items.add(
                    ArchiveEntryItem(
                        entryPath = rawName,
                        name = rawName.trimEnd('/').substringAfterLast('/'),
                        uncompressedSize = unpSize,
                        compressedSize = packSize,
                        isDirectory = header.isDirectory,
                        lastModified = header.mTime?.time ?: archiveFile.lastModified(),
                        crc32Hex = crcHex,
                        isPathSafe = safe,
                        compressionRatioPercent = ratio,
                        isEncrypted = header.isEncrypted
                    )
                )
            }
        }
        return items.sortedWith(compareByDescending<ArchiveEntryItem> { it.isDirectory }.thenBy { it.entryPath })
    }

    /**
     * Native ISO 9660 Primary Volume Descriptor & directory record parser.
     */
    private data class IsoRecord(
        val entryPath: String,
        val name: String,
        val lba: Long,
        val dataLength: Long,
        val isDirectory: Boolean
    )

    private fun parseIso9660Records(isoFile: File): List<IsoRecord> {
        val records = mutableListOf<IsoRecord>()
        RandomAccessFile(isoFile, "r").use { raf ->
            val sectorSize = 2048L
            var pvdSector = -1L
            for (sector in 16..31) {
                val offset = sector * sectorSize
                if (offset + 2048L > raf.length()) break
                raf.seek(offset)
                val type = raf.readUnsignedByte()
                val idBytes = ByteArray(5)
                raf.readFully(idBytes)
                val id = String(idBytes, Charsets.US_ASCII)
                if (id == "CD001" && type == 1) {
                    pvdSector = sector.toLong()
                    break
                }
            }
            if (pvdSector < 0L) {
                throw IOException("Invalid ISO 9660 image: Primary Volume Descriptor (CD001) not found.")
            }

            // Root Directory Record is at offset 156 (34 bytes) of the Primary Volume Descriptor
            raf.seek(pvdSector * sectorSize + 156L)
            val rootRec = ByteArray(34)
            raf.readFully(rootRec)
            val rootLba = ByteBuffer.wrap(rootRec, 2, 4).order(ByteOrder.LITTLE_ENDIAN).int.toLong() and 0xFFFFFFFFL
            val rootLen = ByteBuffer.wrap(rootRec, 10, 4).order(ByteOrder.LITTLE_ENDIAN).int.toLong() and 0xFFFFFFFFL

            fun walkIsoDirectory(lba: Long, length: Long, parentPath: String, depth: Int) {
                if (depth > 16 || length <= 0L) return
                val dirOffset = lba * sectorSize
                if (dirOffset + length > raf.length()) return
                val dirBytes = ByteArray(length.coerceAtMost(4 * 1024 * 1024).toInt())
                raf.seek(dirOffset)
                raf.readFully(dirBytes)

                var pos = 0
                while (pos < dirBytes.size) {
                    val recLen = dirBytes[pos].toInt() and 0xFF
                    if (recLen == 0) {
                        // Advance to next 2048-byte sector boundary
                        val nextSectorPos = ((pos / 2048) + 1) * 2048
                        if (nextSectorPos <= pos) break
                        pos = nextSectorPos
                        continue
                    }
                    if (pos + recLen > dirBytes.size || recLen < 34) break

                    val extentLba = ByteBuffer.wrap(dirBytes, pos + 2, 4).order(ByteOrder.LITTLE_ENDIAN).int.toLong() and 0xFFFFFFFFL
                    val dataLen = ByteBuffer.wrap(dirBytes, pos + 10, 4).order(ByteOrder.LITTLE_ENDIAN).int.toLong() and 0xFFFFFFFFL
                    val flags = dirBytes[pos + 25].toInt() and 0xFF
                    val isDir = (flags and 0x02) != 0
                    val nameLen = dirBytes[pos + 32].toInt() and 0xFF

                    if (nameLen > 0 && pos + 33 + nameLen <= pos + recLen) {
                        val rawId = dirBytes.copyOfRange(pos + 33, pos + 33 + nameLen)
                        val isDot = nameLen == 1 && (rawId[0] == 0.toByte() || rawId[0] == 1.toByte())
                        if (!isDot) {
                            var cleanName = String(rawId, Charsets.US_ASCII)
                                .substringBefore(";1")
                                .trimEnd('.')
                            if (cleanName.isNotEmpty()) {
                                val fullPath = if (parentPath.isEmpty()) cleanName else "$parentPath/$cleanName"
                                val entryPath = if (isDir) "$fullPath/" else fullPath
                                records.add(
                                    IsoRecord(
                                        entryPath = entryPath,
                                        name = cleanName,
                                        lba = extentLba,
                                        dataLength = dataLen,
                                        isDirectory = isDir
                                    )
                                )
                                if (isDir && extentLba != lba) {
                                    walkIsoDirectory(extentLba, dataLen, fullPath, depth + 1)
                                }
                            }
                        }
                    }
                    pos += recLen
                }
            }

            walkIsoDirectory(rootLba, rootLen, "", 0)
        }
        return records
    }

    private fun listIso9660Entries(isoFile: File, dummyDest: File): List<ArchiveEntryItem> {
        val records = parseIso9660Records(isoFile)
        return records.map { rec ->
            ArchiveEntryItem(
                entryPath = rec.entryPath,
                name = rec.name,
                uncompressedSize = if (rec.isDirectory) 0L else rec.dataLength,
                compressedSize = if (rec.isDirectory) 0L else rec.dataLength,
                isDirectory = rec.isDirectory,
                lastModified = isoFile.lastModified(),
                crc32Hex = "ISO9660",
                isPathSafe = isEntryPathSafe(dummyDest, rec.entryPath),
                compressionRatioPercent = 0
            )
        }
    }

    /**
     * Extracts any supported archive (ZIP, TAR, GZIP, BZIP2, XZ, 7Z, RAR, ISO, APK, JAR)
     * from either a local File or a SAF/Termux content:// URI into [destinationDir].
     */
    suspend fun extractArchiveFromPathOrUri(
        context: Context,
        pathOrUri: String,
        displayName: String,
        destinationDir: File,
        selectedEntryPaths: Set<String>? = null,
        password: String? = null,
        awaitIfPaused: suspend () -> Unit = {},
        progressListener: ArchiveProgressListener? = null
    ): Result<Int> = withContext(Dispatchers.IO) {
        if (!pathOrUri.startsWith("content://")) {
            return@withContext extractArchiveDetailed(
                archiveFile = File(pathOrUri),
                destinationDir = destinationDir,
                selectedEntryPaths = selectedEntryPaths,
                password = password,
                awaitIfPaused = awaitIfPaused,
                progressListener = progressListener
            )
        }
        runCatching {
            val uri = Uri.parse(pathOrUri)
            val tempFile = copySafUriToTempCacheFile(context, uri, displayName)
            try {
                extractArchiveDetailed(
                    archiveFile = tempFile,
                    destinationDir = destinationDir,
                    selectedEntryPaths = selectedEntryPaths,
                    password = password,
                    awaitIfPaused = awaitIfPaused,
                    progressListener = progressListener
                ).getOrThrow()
            } finally {
                tempFile.delete()
            }
        }
    }

    suspend fun extractArchive(
        archiveFile: File,
        destinationDir: File,
        selectedEntryPaths: Set<String>? = null,
        onProgress: (Float, String) -> Unit = { _, _ -> }
    ): Result<Int> = extractArchiveDetailed(
        archiveFile = archiveFile,
        destinationDir = destinationDir,
        selectedEntryPaths = selectedEntryPaths,
        progressListener = { fraction, _, _, _, _, currentName ->
            onProgress(fraction, currentName)
        }
    )

    suspend fun extractArchiveFromStream(
        inputStream: InputStream,
        destinationDir: File,
        selectedEntryPaths: Set<String>? = null,
        onProgress: (Float, String) -> Unit = { _, _ -> }
    ): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            extractZipInputStream(
                inputStream = BufferedInputStream(inputStream),
                destinationDir = destinationDir,
                selectedEntryPaths = selectedEntryPaths,
                totalExpectedEntries = 1,
                totalExpectedBytes = 0L,
                awaitIfPaused = {},
                progressListener = { fraction, _, _, _, _, currentName ->
                    onProgress(fraction, currentName)
                }
            )
        }
    }

    suspend fun extractArchiveDetailed(
        archiveFile: File,
        destinationDir: File,
        selectedEntryPaths: Set<String>? = null,
        password: String? = null,
        awaitIfPaused: suspend () -> Unit = {},
        progressListener: ArchiveProgressListener? = null
    ): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            if (!archiveFile.exists() || !archiveFile.canRead()) {
                throw IOException("Archive does not exist or is unreadable: ${archiveFile.name}")
            }
            if (!destinationDir.exists() && !destinationDir.mkdirs()) {
                throw IOException("Cannot create extraction directory: ${destinationDir.absolutePath}")
            }

            // Pre-scan entry list when possible so progress percentage & total bytes are 100% accurate
            val preScanned = listArchiveEntries(archiveFile, password).getOrDefault(emptyList())
            val matchingPreScanned = if (selectedEntryPaths == null) {
                preScanned
            } else {
                preScanned.filter { selectedEntryPaths.contains(it.entryPath) }
            }
            val totalEntries = matchingPreScanned.count { !it.isDirectory }.coerceAtLeast(1)
            val totalExpectedBytes = matchingPreScanned.sumOf { it.uncompressedSize.coerceAtLeast(0L) }
                .takeIf { it > 0L } ?: archiveFile.length()

            val lowerName = archiveFile.name.lowercase()
            val header = ByteArray(16)
            val magicLen = FileInputStream(archiveFile).use { it.read(header) }.coerceAtLeast(0)

            val is7z = lowerName.endsWith(".7z") || (magicLen >= 6 &&
                header[0] == 0x37.toByte() && header[1] == 0x7A.toByte() &&
                header[2] == 0xBC.toByte() && header[3] == 0xAF.toByte())

            val isRar = lowerName.endsWith(".rar") || (magicLen >= 6 &&
                header[0] == 0x52.toByte() && header[1] == 0x61.toByte() &&
                header[2] == 0x72.toByte() && header[3] == 0x21.toByte())

            val isIso = lowerName.endsWith(".iso")

            val isGzip = lowerName.endsWith(".gz") || lowerName.endsWith(".tgz") ||
                (magicLen >= 2 && header[0] == 0x1F.toByte() && header[1] == 0x8B.toByte())

            val isBzip2 = lowerName.endsWith(".bz2") || lowerName.endsWith(".tbz2") ||
                (magicLen >= 3 && header[0] == 0x42.toByte() && header[1] == 0x5A.toByte() && header[2] == 0x68.toByte())

            val isXz = lowerName.endsWith(".xz") || lowerName.endsWith(".txz") ||
                (magicLen >= 6 && header[0] == 0xFD.toByte() && header[1] == 0x37.toByte() && header[2] == 0x7A.toByte())

            val isLz4 = lowerName.endsWith(".lz4") || lowerName.endsWith(".tlz4") ||
                (magicLen >= 4 && header[0] == 0x04.toByte() && header[1] == 0x22.toByte() &&
                    header[2] == 0x4D.toByte() && header[3] == 0x18.toByte())

            val isZstd = lowerName.endsWith(".zst") || lowerName.endsWith(".zstd") || lowerName.endsWith(".tzst") ||
                (magicLen >= 4 && header[0] == 0x28.toByte() && header[1] == 0xB5.toByte() &&
                    header[2] == 0x2F.toByte() && header[3] == 0xFD.toByte())

            val isLzma = lowerName.endsWith(".lzma") || lowerName.endsWith(".tlz")

            val cpioHeader = if (magicLen >= 6) String(header, 0, 6, Charsets.US_ASCII) else ""
            val isCpio = lowerName.endsWith(".cpio") ||
                cpioHeader == "070701" || cpioHeader == "070702" || cpioHeader == "070707"

            val isZip = lowerName.endsWith(".zip") || lowerName.endsWith(".jar") || lowerName.endsWith(".apk") ||
                (magicLen >= 4 && header[0] == 0x50.toByte() && header[1] == 0x4B.toByte())

            when {
                is7z -> extractSevenZArchive(
                    archiveFile = archiveFile,
                    destinationDir = destinationDir,
                    selectedEntryPaths = selectedEntryPaths,
                    password = password,
                    totalEntries = totalEntries,
                    totalExpectedBytes = totalExpectedBytes,
                    awaitIfPaused = awaitIfPaused,
                    progressListener = progressListener
                )
                isRar -> extractRarArchive(
                    archiveFile = archiveFile,
                    destinationDir = destinationDir,
                    selectedEntryPaths = selectedEntryPaths,
                    totalEntries = totalEntries,
                    totalExpectedBytes = totalExpectedBytes,
                    awaitIfPaused = awaitIfPaused,
                    progressListener = progressListener
                )
                isIso -> extractIso9660Archive(
                    isoFile = archiveFile,
                    destinationDir = destinationDir,
                    selectedEntryPaths = selectedEntryPaths,
                    totalEntries = totalEntries,
                    totalExpectedBytes = totalExpectedBytes,
                    awaitIfPaused = awaitIfPaused,
                    progressListener = progressListener
                )
                lowerName.endsWith(".tar.gz") || lowerName.endsWith(".tgz") -> {
                    FileInputStream(archiveFile).use { fis ->
                        GzipCompressorInputStream(BufferedInputStream(fis)).use { gzis ->
                            extractCommonsArchiveInputStream(
                                archiveInput = TarArchiveInputStream(BufferedInputStream(gzis)),
                                destinationDir = destinationDir,
                                selectedEntryPaths = selectedEntryPaths,
                                totalEntries = totalEntries,
                                totalExpectedBytes = totalExpectedBytes,
                                awaitIfPaused = awaitIfPaused,
                                progressListener = progressListener
                            )
                        }
                    }
                }
                lowerName.endsWith(".tar.bz2") || lowerName.endsWith(".tbz2") -> {
                    FileInputStream(archiveFile).use { fis ->
                        BZip2CompressorInputStream(BufferedInputStream(fis)).use { bzis ->
                            extractCommonsArchiveInputStream(
                                archiveInput = TarArchiveInputStream(BufferedInputStream(bzis)),
                                destinationDir = destinationDir,
                                selectedEntryPaths = selectedEntryPaths,
                                totalEntries = totalEntries,
                                totalExpectedBytes = totalExpectedBytes,
                                awaitIfPaused = awaitIfPaused,
                                progressListener = progressListener
                            )
                        }
                    }
                }
                lowerName.endsWith(".tar.xz") || lowerName.endsWith(".txz") -> {
                    FileInputStream(archiveFile).use { fis ->
                        XZCompressorInputStream(BufferedInputStream(fis)).use { xzis ->
                            extractCommonsArchiveInputStream(
                                archiveInput = TarArchiveInputStream(BufferedInputStream(xzis)),
                                destinationDir = destinationDir,
                                selectedEntryPaths = selectedEntryPaths,
                                totalEntries = totalEntries,
                                totalExpectedBytes = totalExpectedBytes,
                                awaitIfPaused = awaitIfPaused,
                                progressListener = progressListener
                            )
                        }
                    }
                }
                lowerName.endsWith(".tar.lz4") || lowerName.endsWith(".tlz4") -> {
                    FileInputStream(archiveFile).use { fis ->
                        FramedLZ4CompressorInputStream(BufferedInputStream(fis)).use { lz4is ->
                            extractCommonsArchiveInputStream(
                                archiveInput = TarArchiveInputStream(BufferedInputStream(lz4is)),
                                destinationDir = destinationDir,
                                selectedEntryPaths = selectedEntryPaths,
                                totalEntries = totalEntries,
                                totalExpectedBytes = totalExpectedBytes,
                                awaitIfPaused = awaitIfPaused,
                                progressListener = progressListener
                            )
                        }
                    }
                }
                lowerName.endsWith(".tar.zst") || lowerName.endsWith(".tar.zstd") || lowerName.endsWith(".tzst") -> {
                    FileInputStream(archiveFile).use { fis ->
                        ZstdCompressorInputStream(BufferedInputStream(fis)).use { zstis ->
                            extractCommonsArchiveInputStream(
                                archiveInput = TarArchiveInputStream(BufferedInputStream(zstis)),
                                destinationDir = destinationDir,
                                selectedEntryPaths = selectedEntryPaths,
                                totalEntries = totalEntries,
                                totalExpectedBytes = totalExpectedBytes,
                                awaitIfPaused = awaitIfPaused,
                                progressListener = progressListener
                            )
                        }
                    }
                }
                lowerName.endsWith(".tar.lzma") || lowerName.endsWith(".tlz") -> {
                    FileInputStream(archiveFile).use { fis ->
                        LZMACompressorInputStream(BufferedInputStream(fis)).use { lzmais ->
                            extractCommonsArchiveInputStream(
                                archiveInput = TarArchiveInputStream(BufferedInputStream(lzmais)),
                                destinationDir = destinationDir,
                                selectedEntryPaths = selectedEntryPaths,
                                totalEntries = totalEntries,
                                totalExpectedBytes = totalExpectedBytes,
                                awaitIfPaused = awaitIfPaused,
                                progressListener = progressListener
                            )
                        }
                    }
                }
                isCpio -> {
                    FileInputStream(archiveFile).use { fis ->
                        extractCommonsArchiveInputStream(
                            archiveInput = CpioArchiveInputStream(BufferedInputStream(fis)),
                            destinationDir = destinationDir,
                            selectedEntryPaths = selectedEntryPaths,
                            totalEntries = totalEntries,
                            totalExpectedBytes = totalExpectedBytes,
                            awaitIfPaused = awaitIfPaused,
                            progressListener = progressListener
                        )
                    }
                }
                lowerName.endsWith(".tar") -> {
                    FileInputStream(archiveFile).use { fis ->
                        extractCommonsArchiveInputStream(
                            archiveInput = TarArchiveInputStream(BufferedInputStream(fis)),
                            destinationDir = destinationDir,
                            selectedEntryPaths = selectedEntryPaths,
                            totalEntries = totalEntries,
                            totalExpectedBytes = totalExpectedBytes,
                            awaitIfPaused = awaitIfPaused,
                            progressListener = progressListener
                        )
                    }
                }
                isGzip -> {
                    FileInputStream(archiveFile).use { fis ->
                        GzipCompressorInputStream(BufferedInputStream(fis)).use { gzis ->
                            val inner = BufferedInputStream(gzis, 64 * 1024)
                            if (isStreamTarArchive(inner)) {
                                extractCommonsArchiveInputStream(
                                    archiveInput = TarArchiveInputStream(inner),
                                    destinationDir = destinationDir,
                                    selectedEntryPaths = selectedEntryPaths,
                                    totalEntries = totalEntries,
                                    totalExpectedBytes = totalExpectedBytes,
                                    awaitIfPaused = awaitIfPaused,
                                    progressListener = progressListener
                                )
                            } else {
                                val outName = archiveFile.name.removeSuffix(".gz").ifBlank { "${archiveFile.name}_extracted" }
                                extractSingleCompressedStream(
                                    decompressedStream = inner,
                                    destinationDir = destinationDir,
                                    outputFileName = outName,
                                    totalExpectedBytes = totalExpectedBytes,
                                    awaitIfPaused = awaitIfPaused,
                                    progressListener = progressListener
                                )
                            }
                        }
                    }
                }
                isBzip2 -> {
                    FileInputStream(archiveFile).use { fis ->
                        BZip2CompressorInputStream(BufferedInputStream(fis)).use { bzis ->
                            val inner = BufferedInputStream(bzis, 64 * 1024)
                            if (isStreamTarArchive(inner)) {
                                extractCommonsArchiveInputStream(
                                    archiveInput = TarArchiveInputStream(inner),
                                    destinationDir = destinationDir,
                                    selectedEntryPaths = selectedEntryPaths,
                                    totalEntries = totalEntries,
                                    totalExpectedBytes = totalExpectedBytes,
                                    awaitIfPaused = awaitIfPaused,
                                    progressListener = progressListener
                                )
                            } else {
                                val outName = archiveFile.name.removeSuffix(".bz2").ifBlank { "${archiveFile.name}_extracted" }
                                extractSingleCompressedStream(
                                    decompressedStream = inner,
                                    destinationDir = destinationDir,
                                    outputFileName = outName,
                                    totalExpectedBytes = totalExpectedBytes,
                                    awaitIfPaused = awaitIfPaused,
                                    progressListener = progressListener
                                )
                            }
                        }
                    }
                }
                isXz -> {
                    FileInputStream(archiveFile).use { fis ->
                        XZCompressorInputStream(BufferedInputStream(fis)).use { xzis ->
                            val inner = BufferedInputStream(xzis, 64 * 1024)
                            if (isStreamTarArchive(inner)) {
                                extractCommonsArchiveInputStream(
                                    archiveInput = TarArchiveInputStream(inner),
                                    destinationDir = destinationDir,
                                    selectedEntryPaths = selectedEntryPaths,
                                    totalEntries = totalEntries,
                                    totalExpectedBytes = totalExpectedBytes,
                                    awaitIfPaused = awaitIfPaused,
                                    progressListener = progressListener
                                )
                            } else {
                                val outName = archiveFile.name.removeSuffix(".xz").ifBlank { "${archiveFile.name}_extracted" }
                                extractSingleCompressedStream(
                                    decompressedStream = inner,
                                    destinationDir = destinationDir,
                                    outputFileName = outName,
                                    totalExpectedBytes = totalExpectedBytes,
                                    awaitIfPaused = awaitIfPaused,
                                    progressListener = progressListener
                                )
                            }
                        }
                    }
                }
                isLz4 -> {
                    FileInputStream(archiveFile).use { fis ->
                        FramedLZ4CompressorInputStream(BufferedInputStream(fis)).use { lz4is ->
                            val inner = BufferedInputStream(lz4is, 64 * 1024)
                            if (isStreamTarArchive(inner)) {
                                extractCommonsArchiveInputStream(
                                    archiveInput = TarArchiveInputStream(inner),
                                    destinationDir = destinationDir,
                                    selectedEntryPaths = selectedEntryPaths,
                                    totalEntries = totalEntries,
                                    totalExpectedBytes = totalExpectedBytes,
                                    awaitIfPaused = awaitIfPaused,
                                    progressListener = progressListener
                                )
                            } else {
                                val outName = archiveFile.name.removeSuffix(".lz4").ifBlank { "${archiveFile.name}_extracted" }
                                extractSingleCompressedStream(
                                    decompressedStream = inner,
                                    destinationDir = destinationDir,
                                    outputFileName = outName,
                                    totalExpectedBytes = totalExpectedBytes,
                                    awaitIfPaused = awaitIfPaused,
                                    progressListener = progressListener
                                )
                            }
                        }
                    }
                }
                isZstd -> {
                    FileInputStream(archiveFile).use { fis ->
                        ZstdCompressorInputStream(BufferedInputStream(fis)).use { zstis ->
                            val inner = BufferedInputStream(zstis, 64 * 1024)
                            if (isStreamTarArchive(inner)) {
                                extractCommonsArchiveInputStream(
                                    archiveInput = TarArchiveInputStream(inner),
                                    destinationDir = destinationDir,
                                    selectedEntryPaths = selectedEntryPaths,
                                    totalEntries = totalEntries,
                                    totalExpectedBytes = totalExpectedBytes,
                                    awaitIfPaused = awaitIfPaused,
                                    progressListener = progressListener
                                )
                            } else {
                                val outName = archiveFile.name.removeSuffix(".zst").removeSuffix(".zstd").ifBlank { "${archiveFile.name}_extracted" }
                                extractSingleCompressedStream(
                                    decompressedStream = inner,
                                    destinationDir = destinationDir,
                                    outputFileName = outName,
                                    totalExpectedBytes = totalExpectedBytes,
                                    awaitIfPaused = awaitIfPaused,
                                    progressListener = progressListener
                                )
                            }
                        }
                    }
                }
                isLzma -> {
                    FileInputStream(archiveFile).use { fis ->
                        LZMACompressorInputStream(BufferedInputStream(fis)).use { lzmais ->
                            val inner = BufferedInputStream(lzmais, 64 * 1024)
                            if (isStreamTarArchive(inner)) {
                                extractCommonsArchiveInputStream(
                                    archiveInput = TarArchiveInputStream(inner),
                                    destinationDir = destinationDir,
                                    selectedEntryPaths = selectedEntryPaths,
                                    totalEntries = totalEntries,
                                    totalExpectedBytes = totalExpectedBytes,
                                    awaitIfPaused = awaitIfPaused,
                                    progressListener = progressListener
                                )
                            } else {
                                val outName = archiveFile.name.removeSuffix(".lzma").ifBlank { "${archiveFile.name}_extracted" }
                                extractSingleCompressedStream(
                                    decompressedStream = inner,
                                    destinationDir = destinationDir,
                                    outputFileName = outName,
                                    totalExpectedBytes = totalExpectedBytes,
                                    awaitIfPaused = awaitIfPaused,
                                    progressListener = progressListener
                                )
                            }
                        }
                    }
                }
                isZip -> {
                    val zip4jAttempt = Zip4jFile(archiveFile)
                    val requiresZip4j = !password.isNullOrEmpty() || runCatching { zip4jAttempt.isEncrypted }.getOrDefault(false)
                    if (requiresZip4j) {
                        extractZip4jArchive(
                            archiveFile = archiveFile,
                            destinationDir = destinationDir,
                            selectedEntryPaths = selectedEntryPaths,
                            password = password,
                            totalEntries = totalEntries,
                            totalExpectedBytes = totalExpectedBytes,
                            awaitIfPaused = awaitIfPaused,
                            progressListener = progressListener
                        )
                    } else {
                        FileInputStream(archiveFile).use { fis ->
                            extractZipInputStream(
                                inputStream = BufferedInputStream(fis, 64 * 1024),
                                destinationDir = destinationDir,
                                selectedEntryPaths = selectedEntryPaths,
                                totalExpectedEntries = totalEntries,
                                totalExpectedBytes = totalExpectedBytes,
                                awaitIfPaused = awaitIfPaused,
                                progressListener = progressListener
                            )
                        }
                    }
                }
                else -> {
                    FileInputStream(archiveFile).use { fis ->
                        val buffered = BufferedInputStream(fis, 64 * 1024)
                        if (isStreamTarArchive(buffered)) {
                            extractCommonsArchiveInputStream(
                                archiveInput = TarArchiveInputStream(buffered),
                                destinationDir = destinationDir,
                                selectedEntryPaths = selectedEntryPaths,
                                totalEntries = totalEntries,
                                totalExpectedBytes = totalExpectedBytes,
                                awaitIfPaused = awaitIfPaused,
                                progressListener = progressListener
                            )
                        } else {
                            extractZipInputStream(
                                inputStream = buffered,
                                destinationDir = destinationDir,
                                selectedEntryPaths = selectedEntryPaths,
                                totalExpectedEntries = totalEntries,
                                totalExpectedBytes = totalExpectedBytes,
                                awaitIfPaused = awaitIfPaused,
                                progressListener = progressListener
                            )
                        }
                    }
                }
            }
        }
    }

    private suspend fun extractZipInputStream(
        inputStream: InputStream,
        destinationDir: File,
        selectedEntryPaths: Set<String>?,
        totalExpectedEntries: Int,
        totalExpectedBytes: Long,
        awaitIfPaused: suspend () -> Unit,
        progressListener: ArchiveProgressListener?
    ): Int {
        if (!destinationDir.exists() && !destinationDir.mkdirs()) {
            throw IOException("Cannot create extraction directory: ${destinationDir.absolutePath}")
        }

        var extractedCount = 0
        var totalBytesWritten = 0L
        val seenEntries = mutableSetOf<String>()
        val buffer = ByteArray(64 * 1024)

        ZipInputStream(inputStream).use { zis ->
            var entry: ZipEntry? = zis.nextEntry
            while (entry != null) {
                coroutineContext.ensureActive()
                awaitIfPaused()
                val currentEntry = entry
                val shouldExtract = selectedEntryPaths == null || selectedEntryPaths.contains(currentEntry.name)

                if (shouldExtract) {
                    if (!seenEntries.add(currentEntry.name)) {
                        throw SecurityException("Duplicate archive entry rejected: ${currentEntry.name}")
                    }
                    if (seenEntries.size > maxEntryCount) {
                        throw SecurityException("Archive exceeds max entry limit ($maxEntryCount)")
                    }

                    val targetFile = validateSafeDestinationFile(destinationDir, currentEntry.name)

                    if (currentEntry.isDirectory) {
                        targetFile.mkdirs()
                    } else {
                        targetFile.parentFile?.mkdirs()
                        var entryBytes = 0L
                        BufferedOutputStream(FileOutputStream(targetFile)).use { bos ->
                            var read: Int
                            while (zis.read(buffer).also { read = it } != -1) {
                                coroutineContext.ensureActive()
                                awaitIfPaused()
                                entryBytes += read
                                totalBytesWritten += read
                                if (totalBytesWritten > maxTotalExtractedBytes) {
                                    targetFile.delete()
                                    throw SecurityException(
                                        "Decompression bomb protection triggered: exceeded ${maxTotalExtractedBytes / (1024 * 1024)} MB"
                                    )
                                }
                                if (currentEntry.compressedSize > 1024 &&
                                    entryBytes / currentEntry.compressedSize > maxCompressionRatio
                                ) {
                                    targetFile.delete()
                                    throw SecurityException(
                                        "Suspicious compression ratio (> $maxCompressionRatio:1) in ${currentEntry.name}"
                                    )
                                }
                                bos.write(buffer, 0, read)
                                val frac = computeProgressFraction(
                                    totalBytesWritten,
                                    totalExpectedBytes,
                                    extractedCount,
                                    totalExpectedEntries
                                )
                                progressListener?.onProgress(
                                    frac,
                                    extractedCount,
                                    totalExpectedEntries,
                                    totalBytesWritten,
                                    totalExpectedBytes,
                                    currentEntry.name
                                )
                            }
                        }
                        if (currentEntry.time > 0) {
                            targetFile.setLastModified(currentEntry.time)
                        }
                        extractedCount++
                        val frac = computeProgressFraction(
                            totalBytesWritten,
                            totalExpectedBytes,
                            extractedCount,
                            totalExpectedEntries
                        )
                        progressListener?.onProgress(
                            frac,
                            extractedCount,
                            totalExpectedEntries,
                            totalBytesWritten,
                            totalExpectedBytes,
                            currentEntry.name
                        )
                    }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
        progressListener?.onProgress(
            1f,
            extractedCount,
            extractedCount.coerceAtLeast(1),
            totalBytesWritten,
            totalBytesWritten.coerceAtLeast(totalExpectedBytes),
            "Completed ($extractedCount files)"
        )
        return extractedCount
    }

    private suspend fun extractCommonsArchiveInputStream(
        archiveInput: ArchiveInputStream<out ArchiveEntry>,
        destinationDir: File,
        selectedEntryPaths: Set<String>?,
        totalEntries: Int,
        totalExpectedBytes: Long,
        awaitIfPaused: suspend () -> Unit,
        progressListener: ArchiveProgressListener?
    ): Int {
        var extractedCount = 0
        var totalBytesWritten = 0L
        val seenEntries = mutableSetOf<String>()
        val buffer = ByteArray(64 * 1024)

        archiveInput.use { ais ->
            var entry = ais.nextEntry
            while (entry != null) {
                coroutineContext.ensureActive()
                awaitIfPaused()
                val rawName = entry.name
                val shouldExtract = selectedEntryPaths == null || selectedEntryPaths.contains(rawName)
                if (shouldExtract) {
                    if (!seenEntries.add(rawName)) {
                        throw SecurityException("Duplicate archive entry rejected: $rawName")
                    }
                    if (seenEntries.size > maxEntryCount) {
                        throw SecurityException("Archive exceeds max entry limit ($maxEntryCount)")
                    }
                    val targetFile = validateSafeDestinationFile(destinationDir, rawName)
                    if (entry.isDirectory) {
                        targetFile.mkdirs()
                    } else {
                        targetFile.parentFile?.mkdirs()
                        BufferedOutputStream(FileOutputStream(targetFile)).use { bos ->
                            var read: Int
                            while (ais.read(buffer).also { read = it } != -1) {
                                coroutineContext.ensureActive()
                                awaitIfPaused()
                                totalBytesWritten += read
                                if (totalBytesWritten > maxTotalExtractedBytes) {
                                    targetFile.delete()
                                    throw SecurityException("Decompression bomb limit exceeded.")
                                }
                                bos.write(buffer, 0, read)
                                val frac = computeProgressFraction(
                                    totalBytesWritten,
                                    totalExpectedBytes,
                                    extractedCount,
                                    totalEntries
                                )
                                progressListener?.onProgress(
                                    frac,
                                    extractedCount,
                                    totalEntries,
                                    totalBytesWritten,
                                    totalExpectedBytes,
                                    rawName
                                )
                            }
                        }
                        runCatching {
                            entry.lastModifiedDate?.time?.takeIf { it > 0 }?.let { targetFile.setLastModified(it) }
                        }
                        extractedCount++
                    }
                }
                entry = ais.nextEntry
            }
        }
        progressListener?.onProgress(
            1f,
            extractedCount,
            extractedCount.coerceAtLeast(1),
            totalBytesWritten,
            totalBytesWritten.coerceAtLeast(totalExpectedBytes),
            "Completed ($extractedCount files)"
        )
        return extractedCount
    }

    private suspend fun extractSingleCompressedStream(
        decompressedStream: InputStream,
        destinationDir: File,
        outputFileName: String,
        totalExpectedBytes: Long,
        awaitIfPaused: suspend () -> Unit,
        progressListener: ArchiveProgressListener?
    ): Int {
        val targetFile = validateSafeDestinationFile(destinationDir, outputFileName)
        targetFile.parentFile?.mkdirs()
        var totalBytesWritten = 0L
        val buffer = ByteArray(64 * 1024)

        BufferedOutputStream(FileOutputStream(targetFile)).use { bos ->
            var read: Int
            while (decompressedStream.read(buffer).also { read = it } != -1) {
                coroutineContext.ensureActive()
                awaitIfPaused()
                totalBytesWritten += read
                if (totalBytesWritten > maxTotalExtractedBytes) {
                    targetFile.delete()
                    throw SecurityException("Decompression bomb limit exceeded.")
                }
                bos.write(buffer, 0, read)
                val frac = if (totalExpectedBytes > 0L) {
                    (totalBytesWritten.toFloat() / totalExpectedBytes.toFloat()).coerceIn(0f, 0.99f)
                } else 0.5f
                progressListener?.onProgress(frac, 0, 1, totalBytesWritten, totalExpectedBytes, outputFileName)
            }
        }
        progressListener?.onProgress(1f, 1, 1, totalBytesWritten, totalBytesWritten, outputFileName)
        return 1
    }

    private suspend fun extractSevenZArchive(
        archiveFile: File,
        destinationDir: File,
        selectedEntryPaths: Set<String>?,
        password: String?,
        totalEntries: Int,
        totalExpectedBytes: Long,
        awaitIfPaused: suspend () -> Unit,
        progressListener: ArchiveProgressListener?
    ): Int {
        var extractedCount = 0
        var totalBytesWritten = 0L
        val seenEntries = mutableSetOf<String>()
        val buffer = ByteArray(64 * 1024)

        @Suppress("DEPRECATION")
        val sevenZInstance = if (!password.isNullOrEmpty()) {
            SevenZFile(archiveFile, password.toCharArray())
        } else {
            SevenZFile(archiveFile)
        }

        sevenZInstance.use { sevenZ ->
            var entry: SevenZArchiveEntry? = sevenZ.nextEntry
            while (entry != null) {
                coroutineContext.ensureActive()
                awaitIfPaused()
                val current = entry
                val rawName = current.name ?: ""
                val shouldExtract = rawName.isNotEmpty() &&
                    (selectedEntryPaths == null || selectedEntryPaths.contains(rawName))

                if (shouldExtract) {
                    if (!seenEntries.add(rawName)) {
                        throw SecurityException("Duplicate 7z entry rejected: $rawName")
                    }
                    val targetFile = validateSafeDestinationFile(destinationDir, rawName)
                    if (current.isDirectory) {
                        targetFile.mkdirs()
                    } else {
                        targetFile.parentFile?.mkdirs()
                        BufferedOutputStream(FileOutputStream(targetFile)).use { bos ->
                            var read: Int
                            while (sevenZ.read(buffer).also { read = it } != -1) {
                                coroutineContext.ensureActive()
                                awaitIfPaused()
                                totalBytesWritten += read
                                if (totalBytesWritten > maxTotalExtractedBytes) {
                                    targetFile.delete()
                                    throw SecurityException("Decompression bomb limit exceeded.")
                                }
                                bos.write(buffer, 0, read)
                                val frac = computeProgressFraction(
                                    totalBytesWritten,
                                    totalExpectedBytes,
                                    extractedCount,
                                    totalEntries
                                )
                                progressListener?.onProgress(
                                    frac,
                                    extractedCount,
                                    totalEntries,
                                    totalBytesWritten,
                                    totalExpectedBytes,
                                    rawName
                                )
                            }
                        }
                        if (current.hasLastModifiedDate) {
                            runCatching { targetFile.setLastModified(current.lastModifiedDate.time) }
                        }
                        extractedCount++
                    }
                }
                entry = sevenZ.nextEntry
            }
        }
        progressListener?.onProgress(
            1f,
            extractedCount,
            extractedCount.coerceAtLeast(1),
            totalBytesWritten,
            totalBytesWritten.coerceAtLeast(totalExpectedBytes),
            "Completed ($extractedCount files)"
        )
        return extractedCount
    }

    private suspend fun extractRarArchive(
        archiveFile: File,
        destinationDir: File,
        selectedEntryPaths: Set<String>?,
        totalEntries: Int,
        totalExpectedBytes: Long,
        awaitIfPaused: suspend () -> Unit,
        progressListener: ArchiveProgressListener?
    ): Int {
        var extractedCount = 0
        var totalBytesWritten = 0L
        val seenEntries = mutableSetOf<String>()

        Archive(archiveFile).use { rar ->
            if (rar.isEncrypted) {
                throw IOException("Password-encrypted RAR headers cannot be extracted without decryption.")
            }
            var header: FileHeader? = rar.nextFileHeader()
            while (header != null) {
                coroutineContext.ensureActive()
                awaitIfPaused()
                val currentHeader = header
                val rawName = currentHeader.fileName.replace('\\', '/').trimStart('/')
                val shouldExtract = rawName.isNotEmpty() &&
                    (selectedEntryPaths == null || selectedEntryPaths.contains(rawName))

                if (shouldExtract) {
                    if (!seenEntries.add(rawName)) {
                        throw SecurityException("Duplicate RAR entry rejected: $rawName")
                    }
                    val targetFile = validateSafeDestinationFile(destinationDir, rawName)
                    if (currentHeader.isDirectory) {
                        targetFile.mkdirs()
                    } else {
                        if (currentHeader.isEncrypted) {
                            throw IOException("Entry '$rawName' is password-encrypted in RAR archive.")
                        }
                        targetFile.parentFile?.mkdirs()
                        BufferedOutputStream(FileOutputStream(targetFile)).use { bos ->
                            rar.extractFile(currentHeader, bos)
                        }
                        val written = targetFile.length()
                        totalBytesWritten += written
                        if (totalBytesWritten > maxTotalExtractedBytes) {
                            targetFile.delete()
                            throw SecurityException("Decompression bomb limit exceeded.")
                        }
                        currentHeader.mTime?.time?.let { targetFile.setLastModified(it) }
                        extractedCount++
                        val frac = computeProgressFraction(
                            totalBytesWritten,
                            totalExpectedBytes,
                            extractedCount,
                            totalEntries
                        )
                        progressListener?.onProgress(
                            frac,
                            extractedCount,
                            totalEntries,
                            totalBytesWritten,
                            totalExpectedBytes,
                            rawName
                        )
                    }
                }
                header = rar.nextFileHeader()
            }
        }
        progressListener?.onProgress(
            1f,
            extractedCount,
            extractedCount.coerceAtLeast(1),
            totalBytesWritten,
            totalBytesWritten.coerceAtLeast(totalExpectedBytes),
            "Completed ($extractedCount files)"
        )
        return extractedCount
    }

    private suspend fun extractIso9660Archive(
        isoFile: File,
        destinationDir: File,
        selectedEntryPaths: Set<String>?,
        totalEntries: Int,
        totalExpectedBytes: Long,
        awaitIfPaused: suspend () -> Unit,
        progressListener: ArchiveProgressListener?
    ): Int {
        val records = parseIso9660Records(isoFile)
        var extractedCount = 0
        var totalBytesWritten = 0L
        val buffer = ByteArray(64 * 1024)

        RandomAccessFile(isoFile, "r").use { raf ->
            for (rec in records) {
                coroutineContext.ensureActive()
                awaitIfPaused()
                val shouldExtract = selectedEntryPaths == null || selectedEntryPaths.contains(rec.entryPath)
                if (!shouldExtract) continue

                val targetFile = validateSafeDestinationFile(destinationDir, rec.entryPath)
                if (rec.isDirectory) {
                    targetFile.mkdirs()
                } else {
                    targetFile.parentFile?.mkdirs()
                    raf.seek(rec.lba * 2048L)
                    var remaining = rec.dataLength
                    BufferedOutputStream(FileOutputStream(targetFile)).use { bos ->
                        while (remaining > 0L) {
                            coroutineContext.ensureActive()
                            awaitIfPaused()
                            val toRead = remaining.coerceAtMost(buffer.size.toLong()).toInt()
                            val read = raf.read(buffer, 0, toRead)
                            if (read <= 0) break
                            bos.write(buffer, 0, read)
                            remaining -= read
                            totalBytesWritten += read
                            if (totalBytesWritten > maxTotalExtractedBytes) {
                                targetFile.delete()
                                throw SecurityException("Decompression bomb limit exceeded.")
                            }
                            val frac = computeProgressFraction(
                                totalBytesWritten,
                                totalExpectedBytes,
                                extractedCount,
                                totalEntries
                            )
                            progressListener?.onProgress(
                                frac,
                                extractedCount,
                                totalEntries,
                                totalBytesWritten,
                                totalExpectedBytes,
                                rec.entryPath
                            )
                        }
                    }
                    extractedCount++
                }
            }
        }
        progressListener?.onProgress(
            1f,
            extractedCount,
            extractedCount.coerceAtLeast(1),
            totalBytesWritten,
            totalBytesWritten.coerceAtLeast(totalExpectedBytes),
            "Completed ($extractedCount files)"
        )
        return extractedCount
    }

    private fun computeProgressFraction(
        processedBytes: Long,
        totalBytes: Long,
        processedItems: Int,
        totalItems: Int
    ): Float {
        if (totalBytes > 0L) {
            return (processedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 0.99f)
        }
        if (totalItems > 0) {
            return (processedItems.toFloat() / totalItems.toFloat()).coerceIn(0f, 0.99f)
        }
        return 0.5f
    }

    private suspend fun extractZip4jArchive(
        archiveFile: File,
        destinationDir: File,
        selectedEntryPaths: Set<String>?,
        password: String?,
        totalEntries: Int,
        totalExpectedBytes: Long,
        awaitIfPaused: suspend () -> Unit,
        progressListener: ArchiveProgressListener?
    ): Int {
        val zip4j = if (!password.isNullOrEmpty()) {
            Zip4jFile(archiveFile, password.toCharArray())
        } else {
            Zip4jFile(archiveFile)
        }
        val headers = zip4j.fileHeaders
        var extractedCount = 0
        var totalBytesWritten = 0L
        val seenEntries = mutableSetOf<String>()
        val buffer = ByteArray(64 * 1024)

        for (header in headers) {
            coroutineContext.ensureActive()
            awaitIfPaused()
            val rawName = header.fileName
            val shouldExtract = selectedEntryPaths == null || selectedEntryPaths.contains(rawName)
            if (!shouldExtract) continue

            if (!seenEntries.add(rawName)) {
                throw SecurityException("Duplicate ZIP entry rejected: $rawName")
            }
            if (seenEntries.size > maxEntryCount) {
                throw SecurityException("Archive exceeds max entry limit ($maxEntryCount)")
            }

            val targetFile = validateSafeDestinationFile(destinationDir, rawName)
            if (header.isDirectory) {
                targetFile.mkdirs()
            } else {
                if (header.isEncrypted && password.isNullOrEmpty()) {
                    throw IOException("Entry '$rawName' is password-encrypted. Please provide the archive password.")
                }
                targetFile.parentFile?.mkdirs()
                zip4j.getInputStream(header).use { zis ->
                    BufferedOutputStream(FileOutputStream(targetFile)).use { bos ->
                        var read: Int
                        while (zis.read(buffer).also { read = it } != -1) {
                            coroutineContext.ensureActive()
                            awaitIfPaused()
                            totalBytesWritten += read
                            if (totalBytesWritten > maxTotalExtractedBytes) {
                                targetFile.delete()
                                throw SecurityException("Decompression bomb limit exceeded.")
                            }
                            bos.write(buffer, 0, read)
                            val frac = computeProgressFraction(
                                totalBytesWritten,
                                totalExpectedBytes,
                                extractedCount,
                                totalEntries
                            )
                            progressListener?.onProgress(
                                frac,
                                extractedCount,
                                totalEntries,
                                totalBytesWritten,
                                totalExpectedBytes,
                                rawName
                            )
                        }
                    }
                }
                if (header.lastModifiedTimeEpoch > 0L) {
                    runCatching { targetFile.setLastModified(header.lastModifiedTimeEpoch) }
                }
                extractedCount++
            }
        }
        progressListener?.onProgress(
            1f,
            extractedCount,
            extractedCount.coerceAtLeast(1),
            totalBytesWritten,
            totalBytesWritten.coerceAtLeast(totalExpectedBytes),
            "Completed ($extractedCount files)"
        )
        return extractedCount
    }

    /**
     * Creates an archive in any supported [ArchiveCreateFormat] (7z, ZIP with optional AES-256 / ZipCrypto,
     * TAR, TAR.BZ2, TAR.GZ, TAR.XZ, TAR.LZ4, TAR.ZSTD, TAR.LZMA, JAR, CPIO, and single-stream formats).
     */
    suspend fun createArchive(
        sourceFiles: List<File>,
        outputArchiveFile: File,
        format: ArchiveCreateFormat = ArchiveCreateFormat.ZIP,
        compressionLevel: ArchiveCompressionLevel = ArchiveCompressionLevel.NORMAL,
        encryptionMode: ArchiveEncryptionMode = ArchiveEncryptionMode.NONE,
        password: String = "",
        awaitIfPaused: suspend () -> Unit = {},
        progressListener: ArchiveProgressListener? = null
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            outputArchiveFile.parentFile?.mkdirs()
            if (outputArchiveFile.exists()) {
                outputArchiveFile.delete()
            }
            val allFiles = mutableListOf<Pair<File, String>>()
            sourceFiles.forEach { src ->
                collectFilesForArchive(src, src.name, allFiles)
            }
            val totalCount = allFiles.size.coerceAtLeast(1)
            val totalBytes = allFiles.sumOf { (f, _) -> if (f.isDirectory) 0L else f.length() }.coerceAtLeast(1L)
            var processedBytes = 0L
            val buffer = ByteArray(64 * 1024)

            when (format) {
                ArchiveCreateFormat.ZIP -> {
                    val useZip4jEncryption = encryptionMode != ArchiveEncryptionMode.NONE && password.isNotEmpty()
                    if (useZip4jEncryption) {
                        val zip4jOut = Zip4jFile(outputArchiveFile, password.toCharArray())
                        val zip4jCompLevel = when (compressionLevel) {
                            ArchiveCompressionLevel.NO_COMPRESSION -> CompressionLevel.NO_COMPRESSION
                            ArchiveCompressionLevel.FASTEST -> CompressionLevel.FASTEST
                            ArchiveCompressionLevel.FAST -> CompressionLevel.FAST
                            ArchiveCompressionLevel.NORMAL -> CompressionLevel.NORMAL
                            ArchiveCompressionLevel.MAXIMUM -> CompressionLevel.MAXIMUM
                            ArchiveCompressionLevel.ULTRA -> CompressionLevel.ULTRA
                        }
                        allFiles.forEachIndexed { index, (file, relativePath) ->
                            coroutineContext.ensureActive()
                            awaitIfPaused()
                            val params = ZipParameters().apply {
                                isEncryptFiles = true
                                encryptionMethod = when (encryptionMode) {
                                    ArchiveEncryptionMode.AES_256 -> EncryptionMethod.AES
                                    ArchiveEncryptionMode.ZIP_CRYPTO -> EncryptionMethod.ZIP_STANDARD
                                    ArchiveEncryptionMode.NONE -> EncryptionMethod.NONE
                                }
                                if (encryptionMode == ArchiveEncryptionMode.AES_256) {
                                    aesKeyStrength = AesKeyStrength.KEY_STRENGTH_256
                                }
                                compressionMethod = if (compressionLevel == ArchiveCompressionLevel.NO_COMPRESSION) {
                                    CompressionMethod.STORE
                                } else {
                                    CompressionMethod.DEFLATE
                                }
                                this.compressionLevel = zip4jCompLevel
                                fileNameInZip = if (file.isDirectory && !relativePath.endsWith("/")) "$relativePath/" else relativePath
                                if (!file.isDirectory) {
                                    entrySize = file.length()
                                }
                            }
                            if (file.isDirectory) {
                                zip4jOut.addStream(ByteArrayInputStream(ByteArray(0)), params)
                            } else {
                                BufferedInputStream(FileInputStream(file)).use { bis ->
                                    zip4jOut.addStream(bis, params)
                                }
                                processedBytes += file.length()
                            }
                            val frac = (processedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 0.99f)
                            progressListener?.onProgress(frac, index + 1, totalCount, processedBytes, totalBytes, relativePath)
                        }
                    } else {
                        ZipOutputStream(BufferedOutputStream(FileOutputStream(outputArchiveFile))).use { zos ->
                            zos.setLevel(compressionLevel.zipDeflateLevel)
                            allFiles.forEachIndexed { index, (file, relativePath) ->
                                coroutineContext.ensureActive()
                                awaitIfPaused()
                                if (file.isDirectory) {
                                    val dirEntry = ZipEntry(if (relativePath.endsWith("/")) relativePath else "$relativePath/")
                                    dirEntry.time = file.lastModified()
                                    zos.putNextEntry(dirEntry)
                                    zos.closeEntry()
                                } else {
                                    val entry = ZipEntry(relativePath)
                                    entry.time = file.lastModified()
                                    zos.putNextEntry(entry)
                                    BufferedInputStream(FileInputStream(file)).use { bis ->
                                        var read: Int
                                        while (bis.read(buffer).also { read = it } != -1) {
                                            coroutineContext.ensureActive()
                                            awaitIfPaused()
                                            zos.write(buffer, 0, read)
                                            processedBytes += read
                                            val frac = (processedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 0.99f)
                                            progressListener?.onProgress(
                                                frac,
                                                index + 1,
                                                totalCount,
                                                processedBytes,
                                                totalBytes,
                                                relativePath
                                            )
                                        }
                                    }
                                    zos.closeEntry()
                                }
                            }
                        }
                    }
                }

                ArchiveCreateFormat.SEVEN_Z -> {
                    SevenZOutputFile(outputArchiveFile).use { sevenZ ->
                        sevenZ.setContentCompression(
                            if (compressionLevel == ArchiveCompressionLevel.NO_COMPRESSION) {
                                SevenZMethod.COPY
                            } else {
                                SevenZMethod.LZMA2
                            }
                        )
                        allFiles.forEachIndexed { index, (file, relativePath) ->
                            coroutineContext.ensureActive()
                            awaitIfPaused()
                            val entryName = if (file.isDirectory && !relativePath.endsWith("/")) "$relativePath/" else relativePath
                            val entry: ArchiveEntry = sevenZ.createArchiveEntry(file, entryName)
                            sevenZ.putArchiveEntry(entry)
                            if (!file.isDirectory) {
                                BufferedInputStream(FileInputStream(file)).use { bis ->
                                    var read: Int
                                    while (bis.read(buffer).also { read = it } != -1) {
                                        coroutineContext.ensureActive()
                                        awaitIfPaused()
                                        sevenZ.write(buffer, 0, read)
                                        processedBytes += read
                                        val frac = (processedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 0.99f)
                                        progressListener?.onProgress(
                                            frac,
                                            index + 1,
                                            totalCount,
                                            processedBytes,
                                            totalBytes,
                                            relativePath
                                        )
                                    }
                                }
                            }
                            sevenZ.closeArchiveEntry()
                        }
                        sevenZ.finish()
                    }
                }

                ArchiveCreateFormat.JAR -> {
                    JarArchiveOutputStream(BufferedOutputStream(FileOutputStream(outputArchiveFile))).use { jos ->
                        jos.setLevel(compressionLevel.zipDeflateLevel)
                        @Suppress("UNCHECKED_CAST")
                        val rawJarOut = jos as ArchiveOutputStream<ArchiveEntry>
                        allFiles.forEachIndexed { index, (file, relativePath) ->
                            coroutineContext.ensureActive()
                            awaitIfPaused()
                            val entryName = if (file.isDirectory && !relativePath.endsWith("/")) "$relativePath/" else relativePath
                            val jarEntry = JarArchiveEntry(entryName).apply {
                                time = file.lastModified()
                                if (!file.isDirectory) size = file.length()
                            }
                            rawJarOut.putArchiveEntry(jarEntry)
                            if (!file.isDirectory) {
                                BufferedInputStream(FileInputStream(file)).use { bis ->
                                    var read: Int
                                    while (bis.read(buffer).also { read = it } != -1) {
                                        coroutineContext.ensureActive()
                                        awaitIfPaused()
                                        rawJarOut.write(buffer, 0, read)
                                        processedBytes += read
                                        val frac = (processedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 0.99f)
                                        progressListener?.onProgress(frac, index + 1, totalCount, processedBytes, totalBytes, relativePath)
                                    }
                                }
                            }
                            rawJarOut.closeArchiveEntry()
                        }
                        rawJarOut.finish()
                    }
                }

                ArchiveCreateFormat.CPIO -> {
                    CpioArchiveOutputStream(BufferedOutputStream(FileOutputStream(outputArchiveFile))).use { cos ->
                        @Suppress("UNCHECKED_CAST")
                        val rawCpioOut = cos as ArchiveOutputStream<ArchiveEntry>
                        allFiles.forEachIndexed { index, (file, relativePath) ->
                            coroutineContext.ensureActive()
                            awaitIfPaused()
                            val entryName = if (file.isDirectory && !relativePath.endsWith("/")) "$relativePath/" else relativePath
                            val cpioEntry = CpioArchiveEntry(file, entryName)
                            rawCpioOut.putArchiveEntry(cpioEntry)
                            if (!file.isDirectory) {
                                BufferedInputStream(FileInputStream(file)).use { bis ->
                                    var read: Int
                                    while (bis.read(buffer).also { read = it } != -1) {
                                        coroutineContext.ensureActive()
                                        awaitIfPaused()
                                        rawCpioOut.write(buffer, 0, read)
                                        processedBytes += read
                                        val frac = (processedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 0.99f)
                                        progressListener?.onProgress(frac, index + 1, totalCount, processedBytes, totalBytes, relativePath)
                                    }
                                }
                            }
                            rawCpioOut.closeArchiveEntry()
                        }
                        rawCpioOut.finish()
                    }
                }

                ArchiveCreateFormat.GZ,
                ArchiveCreateFormat.BZ2,
                ArchiveCreateFormat.XZ,
                ArchiveCreateFormat.LZ4,
                ArchiveCreateFormat.ZSTD -> {
                    val rawFos = BufferedOutputStream(FileOutputStream(outputArchiveFile))
                    val compressorStream = createCompressorStream(format, rawFos, compressionLevel)
                    if (allFiles.size == 1 && !allFiles.first().first.isDirectory) {
                        val (singleFile, relativePath) = allFiles.first()
                        compressorStream.use { out ->
                            BufferedInputStream(FileInputStream(singleFile)).use { bis ->
                                var read: Int
                                while (bis.read(buffer).also { read = it } != -1) {
                                    coroutineContext.ensureActive()
                                    awaitIfPaused()
                                    out.write(buffer, 0, read)
                                    processedBytes += read
                                    val frac = (processedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 0.99f)
                                    progressListener?.onProgress(frac, 1, 1, processedBytes, totalBytes, relativePath)
                                }
                            }
                        }
                    } else {
                        // Multiple files or directory: automatically wrap in a TAR container inside the compressor stream
                        writeTarEntries(
                            compressorStream = compressorStream,
                            allFiles = allFiles,
                            totalCount = totalCount,
                            totalBytes = totalBytes,
                            buffer = buffer,
                            awaitIfPaused = awaitIfPaused
                        ) { frac, idx, count, doneBytes, maxBytes, rel ->
                            processedBytes = doneBytes
                            progressListener?.onProgress(frac, idx, count, doneBytes, maxBytes, rel)
                        }
                    }
                }

                ArchiveCreateFormat.TAR,
                ArchiveCreateFormat.TAR_BZ2,
                ArchiveCreateFormat.TAR_GZ,
                ArchiveCreateFormat.TAR_XZ,
                ArchiveCreateFormat.TAR_LZ4,
                ArchiveCreateFormat.TAR_ZSTD,
                ArchiveCreateFormat.TAR_LZMA -> {
                    val rawFos = BufferedOutputStream(FileOutputStream(outputArchiveFile))
                    val compressorStream = createCompressorStream(format, rawFos, compressionLevel)
                    writeTarEntries(
                        compressorStream = compressorStream,
                        allFiles = allFiles,
                        totalCount = totalCount,
                        totalBytes = totalBytes,
                        buffer = buffer,
                        awaitIfPaused = awaitIfPaused
                    ) { frac, idx, count, doneBytes, maxBytes, rel ->
                        processedBytes = doneBytes
                        progressListener?.onProgress(frac, idx, count, doneBytes, maxBytes, rel)
                    }
                }
            }

            progressListener?.onProgress(
                1f,
                totalCount,
                totalCount,
                totalBytes,
                totalBytes,
                outputArchiveFile.name
            )
            outputArchiveFile
        }.onFailure {
            if (outputArchiveFile.exists()) {
                runCatching { outputArchiveFile.delete() }
            }
        }
    }

    private fun createCompressorStream(
        format: ArchiveCreateFormat,
        rawOut: BufferedOutputStream,
        compressionLevel: ArchiveCompressionLevel
    ): java.io.OutputStream = when (format) {
        ArchiveCreateFormat.TAR_GZ,
        ArchiveCreateFormat.GZ -> {
            val params = GzipParameters().apply {
                this.compressionLevel = compressionLevel.gzipLevel
            }
            GzipCompressorOutputStream(rawOut, params)
        }
        ArchiveCreateFormat.TAR_BZ2,
        ArchiveCreateFormat.BZ2 -> BZip2CompressorOutputStream(rawOut, compressionLevel.bzip2BlockSize)
        ArchiveCreateFormat.TAR_XZ,
        ArchiveCreateFormat.XZ -> XZCompressorOutputStream(rawOut, compressionLevel.xzPreset)
        ArchiveCreateFormat.TAR_LZ4,
        ArchiveCreateFormat.LZ4 -> FramedLZ4CompressorOutputStream(rawOut)
        ArchiveCreateFormat.TAR_ZSTD,
        ArchiveCreateFormat.ZSTD -> ZstdCompressorOutputStream(rawOut, compressionLevel.zstdLevel)
        ArchiveCreateFormat.TAR_LZMA -> LZMACompressorOutputStream(rawOut)
        else -> rawOut
    }

    private suspend fun writeTarEntries(
        compressorStream: java.io.OutputStream,
        allFiles: List<Pair<File, String>>,
        totalCount: Int,
        totalBytes: Long,
        buffer: ByteArray,
        awaitIfPaused: suspend () -> Unit,
        progressListener: ArchiveProgressListener?
    ) {
        var processedBytes = 0L
        TarArchiveOutputStream(compressorStream).use { tos ->
            tos.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX)
            tos.setBigNumberMode(TarArchiveOutputStream.BIGNUMBER_POSIX)
            @Suppress("UNCHECKED_CAST")
            val rawArchiveOut = tos as ArchiveOutputStream<ArchiveEntry>
            allFiles.forEachIndexed { index, (file, relativePath) ->
                coroutineContext.ensureActive()
                awaitIfPaused()
                val entryName = if (file.isDirectory && !relativePath.endsWith("/")) "$relativePath/" else relativePath
                val tarEntry = TarArchiveEntry(entryName).apply {
                    size = if (file.isDirectory) 0L else file.length()
                    setModTime(file.lastModified())
                }
                rawArchiveOut.putArchiveEntry(tarEntry)
                if (!file.isDirectory) {
                    BufferedInputStream(FileInputStream(file)).use { bis ->
                        var read: Int
                        while (bis.read(buffer).also { read = it } != -1) {
                            coroutineContext.ensureActive()
                            awaitIfPaused()
                            rawArchiveOut.write(buffer, 0, read)
                            processedBytes += read
                            val frac = (processedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 0.99f)
                            progressListener?.onProgress(
                                frac,
                                index + 1,
                                totalCount,
                                processedBytes,
                                totalBytes,
                                relativePath
                            )
                        }
                    }
                }
                rawArchiveOut.closeArchiveEntry()
            }
            rawArchiveOut.finish()
        }
    }

    /**
     * Converts any supported archive (ZIP, 7Z, RAR, ISO, TAR, TAR.GZ, TAR.BZ2, TAR.XZ, TAR.LZ4,
     * TAR.ZSTD, TAR.LZMA, JAR, CPIO, APK, etc.) into another [targetFormat].
     */
    suspend fun convertArchive(
        context: Context,
        sourcePathOrUri: String,
        sourceDisplayName: String,
        outputArchiveFile: File,
        targetFormat: ArchiveCreateFormat,
        compressionLevel: ArchiveCompressionLevel = ArchiveCompressionLevel.NORMAL,
        encryptionMode: ArchiveEncryptionMode = ArchiveEncryptionMode.NONE,
        password: String = "",
        sourcePassword: String? = null,
        awaitIfPaused: suspend () -> Unit = {},
        progressListener: ArchiveProgressListener? = null
    ): Result<File> = withContext(Dispatchers.IO) {
        if (!sourcePathOrUri.startsWith("content://")) {
            return@withContext convertArchiveFile(
                sourceArchiveFile = File(sourcePathOrUri),
                workingTempRoot = context.cacheDir,
                outputArchiveFile = outputArchiveFile,
                targetFormat = targetFormat,
                compressionLevel = compressionLevel,
                encryptionMode = encryptionMode,
                password = password,
                sourcePassword = sourcePassword,
                awaitIfPaused = awaitIfPaused,
                progressListener = progressListener
            )
        }
        val stagedSource = runCatching {
            copySafUriToTempCacheFile(context, Uri.parse(sourcePathOrUri), sourceDisplayName)
        }.getOrElse { return@withContext Result.failure(it) }

        try {
            convertArchiveFile(
                sourceArchiveFile = stagedSource,
                workingTempRoot = context.cacheDir,
                outputArchiveFile = outputArchiveFile,
                targetFormat = targetFormat,
                compressionLevel = compressionLevel,
                encryptionMode = encryptionMode,
                password = password,
                sourcePassword = sourcePassword,
                awaitIfPaused = awaitIfPaused,
                progressListener = progressListener
            )
        } finally {
            stagedSource.delete()
        }
    }

    /**
     * Converts any local readable [sourceArchiveFile] into [outputArchiveFile] using [targetFormat].
     */
    suspend fun convertArchiveFile(
        sourceArchiveFile: File,
        workingTempRoot: File,
        outputArchiveFile: File,
        targetFormat: ArchiveCreateFormat,
        compressionLevel: ArchiveCompressionLevel = ArchiveCompressionLevel.NORMAL,
        encryptionMode: ArchiveEncryptionMode = ArchiveEncryptionMode.NONE,
        password: String = "",
        sourcePassword: String? = null,
        awaitIfPaused: suspend () -> Unit = {},
        progressListener: ArchiveProgressListener? = null
    ): Result<File> = withContext(Dispatchers.IO) {
        val tempWorkspace = File(
            workingTempRoot,
            "archive_convert_${System.currentTimeMillis()}_${java.util.UUID.randomUUID().toString().take(8)}"
        )
        runCatching {
            tempWorkspace.mkdirs()
            extractArchiveDetailed(
                archiveFile = sourceArchiveFile,
                destinationDir = tempWorkspace,
                selectedEntryPaths = null,
                password = sourcePassword,
                awaitIfPaused = awaitIfPaused
            ) { frac, done, total, doneBytes, maxBytes, itemName ->
                progressListener?.onProgress(
                    fraction = (frac * 0.5f).coerceIn(0f, 0.5f),
                    processedItems = done,
                    totalItems = total,
                    processedBytes = doneBytes,
                    totalBytes = maxBytes,
                    currentItemName = "Unpacking: $itemName"
                )
            }.getOrThrow()

            val topLevelEntries = tempWorkspace.listFiles()?.toList().orEmpty()
            if (topLevelEntries.isEmpty()) {
                throw IOException("Source archive '${sourceArchiveFile.name}' contains no entries to convert.")
            }

            createArchive(
                sourceFiles = topLevelEntries,
                outputArchiveFile = outputArchiveFile,
                format = targetFormat,
                compressionLevel = compressionLevel,
                encryptionMode = encryptionMode,
                password = password,
                awaitIfPaused = awaitIfPaused
            ) { frac, done, total, doneBytes, maxBytes, itemName ->
                progressListener?.onProgress(
                    fraction = (0.5f + frac * 0.5f).coerceIn(0.5f, 1f),
                    processedItems = done,
                    totalItems = total,
                    processedBytes = doneBytes,
                    totalBytes = maxBytes,
                    currentItemName = "Repacking: $itemName"
                )
            }.getOrThrow()
        }.also {
            tempWorkspace.deleteRecursively()
        }
    }

    suspend fun createZipArchive(
        sourceFiles: List<File>,
        outputZipFile: File,
        onProgress: (Float, String) -> Unit = { _, _ -> }
    ): Result<File> = createArchive(
        sourceFiles = sourceFiles,
        outputArchiveFile = outputZipFile,
        format = ArchiveCreateFormat.ZIP,
        progressListener = { fraction, _, _, _, _, currentName ->
            onProgress(fraction, currentName)
        }
    )

    private fun collectFilesForArchive(current: File, relativePath: String, out: MutableList<Pair<File, String>>) {
        if (current.isDirectory) {
            val children = current.listFiles().orEmpty()
            if (children.isEmpty()) {
                out.add(current to "$relativePath/")
            } else {
                children.forEach { child ->
                    collectFilesForArchive(child, "$relativePath/${child.name}", out)
                }
            }
        } else {
            out.add(current to relativePath)
        }
    }

    /**
     * Verifies archive integrity across all supported formats (ZIP, TAR, GZIP, BZIP2, XZ, LZ4, ZSTD, LZMA, CPIO, 7Z, RAR, ISO).
     */
    suspend fun verifyArchiveIntegrity(
        archiveFile: File,
        password: String? = null,
        progressListener: ArchiveProgressListener? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val lower = archiveFile.name.lowercase()
            when {
                lower.endsWith(".7z") -> {
                    @Suppress("DEPRECATION")
                    val sevenZInstance = if (!password.isNullOrEmpty()) {
                        SevenZFile(archiveFile, password.toCharArray())
                    } else {
                        SevenZFile(archiveFile)
                    }
                    var checked = 0
                    val buf = ByteArray(64 * 1024)
                    sevenZInstance.use { sz ->
                        var entry = sz.nextEntry
                        while (entry != null) {
                            coroutineContext.ensureActive()
                            if (!entry.isDirectory) {
                                while (sz.read(buf) != -1) {
                                    coroutineContext.ensureActive()
                                }
                            }
                            checked++
                            progressListener?.onProgress(0.5f, checked, checked, 0L, archiveFile.length(), entry.name ?: "")
                            entry = sz.nextEntry
                        }
                    }
                    "Verified $checked 7z entries — stream & CRC integrity OK"
                }
                lower.endsWith(".rar") -> {
                    val entries = listRarEntries(archiveFile, archiveFile.parentFile ?: File("/tmp"))
                    "Verified ${entries.size} RAR entries — header & structure integrity OK"
                }
                lower.endsWith(".iso") -> {
                    val records = parseIso9660Records(archiveFile)
                    "Verified ${records.size} ISO 9660 entries — Primary Volume Descriptor OK"
                }
                lower.endsWith(".zip") || lower.endsWith(".jar") || lower.endsWith(".apk") -> {
                    val zip4jAttempt = Zip4jFile(archiveFile)
                    val requiresZip4j = !password.isNullOrEmpty() || runCatching { zip4jAttempt.isEncrypted }.getOrDefault(false)
                    if (requiresZip4j) {
                        val zip4j = if (!password.isNullOrEmpty()) Zip4jFile(archiveFile, password.toCharArray()) else zip4jAttempt
                        var checked = 0
                        val buf = ByteArray(64 * 1024)
                        zip4j.fileHeaders.forEach { header ->
                            coroutineContext.ensureActive()
                            if (!header.isDirectory) {
                                zip4j.getInputStream(header).use { isStream ->
                                    while (isStream.read(buf) != -1) {
                                        coroutineContext.ensureActive()
                                    }
                                }
                            }
                            checked++
                        }
                        "Verified $checked ZIP entries — decryption & stream integrity OK"
                    } else {
                        FileInputStream(archiveFile).use {
                            verifyZipStreamIntegrity(BufferedInputStream(it), archiveFile.length(), progressListener)
                        }
                    }
                }
                lower.endsWith(".tar") || lower.endsWith(".tar.gz") || lower.endsWith(".tgz") ||
                    lower.endsWith(".tar.bz2") || lower.endsWith(".tbz2") ||
                    lower.endsWith(".tar.xz") || lower.endsWith(".txz") ||
                    lower.endsWith(".tar.lz4") || lower.endsWith(".tlz4") ||
                    lower.endsWith(".tar.zst") || lower.endsWith(".tar.zstd") || lower.endsWith(".tzst") ||
                    lower.endsWith(".tar.lzma") || lower.endsWith(".tlz") ||
                    lower.endsWith(".cpio") ||
                    lower.endsWith(".gz") || lower.endsWith(".bz2") || lower.endsWith(".xz") ||
                    lower.endsWith(".lz4") || lower.endsWith(".zst") || lower.endsWith(".zstd") || lower.endsWith(".lzma") -> {
                    val entries = listArchiveEntries(archiveFile, password).getOrThrow()
                    val unsafeCount = entries.count { !it.isPathSafe }
                    if (unsafeCount > 0) {
                        throw SecurityException("Found $unsafeCount unsafe path traversal entries in archive.")
                    }
                    "Verified ${entries.size} archive entries — stream & header integrity OK"
                }
                else -> {
                    FileInputStream(archiveFile).use {
                        verifyZipStreamIntegrity(BufferedInputStream(it), archiveFile.length(), progressListener)
                    }
                }
            }
        }
    }

    suspend fun verifyArchiveIntegrityFromUri(
        context: Context,
        uriString: String,
        displayName: String = "archive.zip",
        password: String? = null,
        progressListener: ArchiveProgressListener? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val uri = Uri.parse(uriString)
            val tempFile = copySafUriToTempCacheFile(context, uri, displayName)
            try {
                verifyArchiveIntegrity(tempFile, password, progressListener).getOrThrow()
            } finally {
                tempFile.delete()
            }
        }
    }

    private suspend fun verifyZipStreamIntegrity(
        input: InputStream,
        totalBytes: Long = 0L,
        progressListener: ArchiveProgressListener? = null
    ): String {
        var checkedEntries = 0
        var processedBytes = 0L
        val buffer = ByteArray(64 * 1024)
        ZipInputStream(input).use { zis ->
            var entry: ZipEntry? = zis.nextEntry
            while (entry != null) {
                coroutineContext.ensureActive()
                val crc = CRC32()
                var read: Int
                while (zis.read(buffer).also { read = it } != -1) {
                    coroutineContext.ensureActive()
                    crc.update(buffer, 0, read)
                    processedBytes += read
                }
                if (entry.crc != -1L && entry.crc != crc.value) {
                    throw IOException("CRC32 mismatch on entry ${entry.name}")
                }
                checkedEntries++
                val frac = if (totalBytes > 0L) (processedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 0.99f) else 0.5f
                progressListener?.onProgress(frac, checkedEntries, checkedEntries, processedBytes, totalBytes, entry.name)
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
        progressListener?.onProgress(1f, checkedEntries, checkedEntries, processedBytes, totalBytes, "Verified")
        return "Verified $checkedEntries entries — CRC32 integrity OK"
    }

    private fun copySafUriToTempCacheFile(context: Context, uri: Uri, displayName: String): File {
        val ext = displayName.substringAfterLast('.', "tmp")
        val tempFile = File.createTempFile("explorer_archive_", ".$ext", context.cacheDir)
        context.contentResolver.openInputStream(uri)?.use { input ->
            BufferedOutputStream(FileOutputStream(tempFile)).use { output ->
                input.copyTo(output, 64 * 1024)
            }
        } ?: throw IOException("Unable to open SAF archive stream: $displayName")
        return tempFile
    }
}
