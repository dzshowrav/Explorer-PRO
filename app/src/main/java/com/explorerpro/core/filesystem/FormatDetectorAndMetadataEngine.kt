package com.explorerpro.core.filesystem

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.webkit.MimeTypeMap
import com.explorerpro.domain.model.DetectedFileFormat
import com.explorerpro.domain.model.FileEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.io.RandomAccessFile
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipFile
import kotlin.coroutines.coroutineContext

/**
 * Inspects real file magic-byte content signatures (never trusting extensions alone)
 * across both standard POSIX files (`java.io.File`) and SAF / Termux content URIs (`content://`),
 * and extracts real dimensions, PDF page count, APK metadata, and SHA-256 digests.
 */
object FormatDetectorAndMetadataEngine {

    fun detectFormatFromHeaderBytes(
        header: ByteArray,
        bytesRead: Int,
        extension: String,
        zipSubtypeInspector: (() -> DetectedFileFormat)? = null
    ): DetectedFileFormat {
        if (bytesRead <= 0) return classifyByExtensionFallback(extension)

        // 1. PNG: 89 50 4E 47 0D 0A 1A 0A
        if (bytesRead >= 8 &&
            header[0] == 0x89.toByte() &&
            header[1] == 0x50.toByte() &&
            header[2] == 0x4E.toByte() &&
            header[3] == 0x47.toByte()
        ) {
            return DetectedFileFormat.PNG
        }

        // 2. JPEG: FF D8 FF
        if (bytesRead >= 3 &&
            header[0] == 0xFF.toByte() &&
            header[1] == 0xD8.toByte() &&
            header[2] == 0xFF.toByte()
        ) {
            return DetectedFileFormat.JPEG
        }

        // 3. GIF: "GIF87a" or "GIF89a"
        if (bytesRead >= 6 &&
            header[0] == 'G'.code.toByte() &&
            header[1] == 'I'.code.toByte() &&
            header[2] == 'F'.code.toByte() &&
            header[3] == '8'.code.toByte()
        ) {
            return DetectedFileFormat.GIF
        }

        // 4. WEBP / WAV: "RIFF" .... "WEBP" or "WAVE"
        if (bytesRead >= 12 &&
            header[0] == 'R'.code.toByte() &&
            header[1] == 'I'.code.toByte() &&
            header[2] == 'F'.code.toByte() &&
            header[3] == 'F'.code.toByte()
        ) {
            val subType = String(header, 8, 4, Charsets.US_ASCII)
            if (subType == "WEBP") return DetectedFileFormat.WEBP
            if (subType == "WAVE") return DetectedFileFormat.WAV
        }

        // 5. BMP: "BM"
        if (bytesRead >= 2 &&
            header[0] == 'B'.code.toByte() &&
            header[1] == 'M'.code.toByte() &&
            extension in setOf("bmp", "dib")
        ) {
            return DetectedFileFormat.BMP
        }

        // 6. PDF: "%PDF-"
        if (bytesRead >= 5 &&
            header[0] == '%'.code.toByte() &&
            header[1] == 'P'.code.toByte() &&
            header[2] == 'D'.code.toByte() &&
            header[3] == 'F'.code.toByte()
        ) {
            return DetectedFileFormat.PDF
        }

        // 7. ELF Binary (Linux / Android / Termux native executables & .so libraries): 7F 45 4C 46
        if (bytesRead >= 4 &&
            header[0] == 0x7F.toByte() &&
            header[1] == 'E'.code.toByte() &&
            header[2] == 'L'.code.toByte() &&
            header[3] == 'F'.code.toByte()
        ) {
            return DetectedFileFormat.ELF_BINARY
        }

        // 8. ZIP container family (ZIP, APK, DOCX, XLSX, PPTX): 50 4B 03 04 or empty ZIP 50 4B 05 06
        if (bytesRead >= 4 &&
            header[0] == 0x50.toByte() &&
            header[1] == 0x4B.toByte() &&
            ((header[2] == 0x03.toByte() && header[3] == 0x04.toByte()) ||
                (header[2] == 0x05.toByte() && header[3] == 0x06.toByte()))
        ) {
            return zipSubtypeInspector?.invoke() ?: when (extension) {
                "apk" -> DetectedFileFormat.APK
                "docx" -> DetectedFileFormat.DOCX
                "xlsx" -> DetectedFileFormat.XLSX
                "pptx" -> DetectedFileFormat.PPTX
                else -> DetectedFileFormat.ZIP
            }
        }

        // 9. GZIP: 1F 8B
        if (bytesRead >= 2 &&
            header[0] == 0x1F.toByte() &&
            header[1] == 0x8B.toByte()
        ) {
            return DetectedFileFormat.GZIP
        }

        // 10. BZIP2: "BZh" (42 5A 68)
        if (bytesRead >= 3 &&
            header[0] == 0x42.toByte() &&
            header[1] == 0x5A.toByte() &&
            header[2] == 0x68.toByte()
        ) {
            return DetectedFileFormat.BZIP2
        }

        // 11. XZ: FD 37 7A 58 5A 00
        if (bytesRead >= 6 &&
            header[0] == 0xFD.toByte() &&
            header[1] == 0x37.toByte() &&
            header[2] == 0x7A.toByte() &&
            header[3] == 0x58.toByte() &&
            header[4] == 0x5A.toByte() &&
            header[5] == 0x00.toByte()
        ) {
            return DetectedFileFormat.XZ
        }

        // 12. 7Z: 37 7A BC AF 27 1C
        if (bytesRead >= 6 &&
            header[0] == 0x37.toByte() &&
            header[1] == 0x7A.toByte() &&
            header[2] == 0xBC.toByte() &&
            header[3] == 0xAF.toByte()
        ) {
            return DetectedFileFormat.SEVEN_Z
        }

        // 13. RAR v4/v5: 52 61 72 21 1A 07
        if (bytesRead >= 6 &&
            header[0] == 0x52.toByte() &&
            header[1] == 0x61.toByte() &&
            header[2] == 0x72.toByte() &&
            header[3] == 0x21.toByte()
        ) {
            return DetectedFileFormat.RAR
        }

        // 13b. LZ4 Frame: 04 22 4D 18
        if (bytesRead >= 4 &&
            header[0] == 0x04.toByte() &&
            header[1] == 0x22.toByte() &&
            header[2] == 0x4D.toByte() &&
            header[3] == 0x18.toByte()
        ) {
            return DetectedFileFormat.LZ4
        }

        // 13c. Zstandard Frame: 28 B5 2F FD
        if (bytesRead >= 4 &&
            header[0] == 0x28.toByte() &&
            header[1] == 0xB5.toByte() &&
            header[2] == 0x2F.toByte() &&
            header[3] == 0xFD.toByte()
        ) {
            return DetectedFileFormat.ZSTD
        }

        // 13d. POSIX CPIO ASCII: "070701", "070702", "070707"
        if (bytesRead >= 6) {
            val cpioHeader = String(header, 0, 6, Charsets.US_ASCII)
            if (cpioHeader == "070701" || cpioHeader == "070702" || cpioHeader == "070707") {
                return DetectedFileFormat.CPIO
            }
        }

        // 14. POSIX TAR: "ustar" at offset 257
        if (bytesRead >= 262 &&
            header[257] == 'u'.code.toByte() &&
            header[258] == 's'.code.toByte() &&
            header[259] == 't'.code.toByte() &&
            header[260] == 'a'.code.toByte() &&
            header[261] == 'r'.code.toByte()
        ) {
            return DetectedFileFormat.TAR
        }

        // 15. SQLite 3: "SQLite format 3"
        if (bytesRead >= 15 && String(header, 0, 15, Charsets.US_ASCII) == "SQLite format 3") {
            return DetectedFileFormat.SQLITE
        }

        // 16. MP3: "ID3" or FF FB
        if (bytesRead >= 3 &&
            header[0] == 'I'.code.toByte() &&
            header[1] == 'D'.code.toByte() &&
            header[2] == '3'.code.toByte()
        ) {
            return DetectedFileFormat.MP3
        }

        // 17. MP4: "ftyp" at offset 4
        if (bytesRead >= 8 && String(header, 4, 4, Charsets.US_ASCII) == "ftyp") {
            return DetectedFileFormat.MP4
        }

        // 18. Shebang (#!) script (Termux bash/sh/python/node scripts)
        if (bytesRead >= 2 &&
            header[0] == '#'.code.toByte() &&
            header[1] == '!'.code.toByte()
        ) {
            return DetectedFileFormat.SHELL_SCRIPT
        }

        // 19. Check if printable UTF-8 text and refine subtype (sample first 64 bytes)
        val sampleLen = bytesRead.coerceAtMost(64)
        val sample = header.copyOfRange(0, sampleLen)
        val isLikelyText = sample.all { b ->
            val v = b.toInt() and 0xFF
            v == 0x09 || v == 0x0A || v == 0x0D || v in 0x20..0x7E || v >= 0x80
        }
        if (isLikelyText && extension !in setOf("tar", "iso", "bin", "dat")) {
            val textPrefix = String(sample, Charsets.UTF_8).trimStart()
            return when {
                textPrefix.startsWith("#!") || extension in setOf("sh", "bash", "zsh", "py") ->
                    DetectedFileFormat.SHELL_SCRIPT
                textPrefix.startsWith("<svg", ignoreCase = true) || extension == "svg" ->
                    DetectedFileFormat.SVG
                textPrefix.startsWith("<?xml", ignoreCase = true) || extension == "xml" ->
                    DetectedFileFormat.XML
                (textPrefix.startsWith("{") || textPrefix.startsWith("[")) && extension == "json" ->
                    DetectedFileFormat.JSON
                extension in setOf("kt", "kts", "java", "c", "cpp", "h", "rs", "go", "js", "ts") ->
                    DetectedFileFormat.KOTLIN_SOURCE
                extension in setOf("md", "markdown") ->
                    DetectedFileFormat.MARKDOWN
                else -> DetectedFileFormat.UTF8_TEXT
            }
        }

        return classifyByExtensionFallback(extension)
    }

    fun detectFormat(file: File): DetectedFileFormat {
        if (file.isDirectory) return DetectedFileFormat.DIRECTORY
        val ext = file.extension.lowercase()
        if (!file.exists() || !file.canRead() || file.length() == 0L) {
            return classifyByExtensionFallback(ext)
        }

        // Check ISO 9660 signature "CD001" at offset 0x8001 (32769) if file size >= 32774 bytes
        if (file.length() >= 0x8006L && (ext == "iso" || ext == "img")) {
            val isIso = runCatching {
                RandomAccessFile(file, "r").use { raf ->
                    raf.seek(0x8001L)
                    val sig = ByteArray(5)
                    raf.readFully(sig)
                    String(sig, Charsets.US_ASCII) == "CD001"
                }
            }.getOrDefault(false)
            if (isIso) return DetectedFileFormat.ISO
        }

        val header = ByteArray(264)
        val bytesRead = runCatching {
            FileInputStream(file).use { it.read(header) }
        }.getOrDefault(-1)

        return detectFormatFromHeaderBytes(
            header = header,
            bytesRead = bytesRead,
            extension = ext,
            zipSubtypeInspector = { inspectZipContainerSubtype(file) }
        )
    }

    fun detectSafUriFormat(context: Context, uri: Uri, displayName: String, isDir: Boolean): DetectedFileFormat {
        if (isDir) return DetectedFileFormat.DIRECTORY
        val ext = displayName.substringAfterLast('.', "").lowercase()
        val header = ByteArray(264)
        val bytesRead = runCatching {
            context.contentResolver.openInputStream(uri)?.use { it.read(header) } ?: -1
        }.getOrDefault(-1)

        return detectFormatFromHeaderBytes(
            header = header,
            bytesRead = bytesRead,
            extension = ext
        )
    }

    private fun inspectZipContainerSubtype(file: File): DetectedFileFormat {
        return runCatching {
            ZipFile(file).use { zip ->
                when {
                    zip.getEntry("AndroidManifest.xml") != null && zip.getEntry("classes.dex") != null ->
                        DetectedFileFormat.APK
                    zip.getEntry("word/document.xml") != null ->
                        DetectedFileFormat.DOCX
                    zip.getEntry("xl/workbook.xml") != null ->
                        DetectedFileFormat.XLSX
                    zip.getEntry("ppt/presentation.xml") != null ->
                        DetectedFileFormat.PPTX
                    file.extension.equals("jar", ignoreCase = true) ->
                        DetectedFileFormat.JAR
                    else -> DetectedFileFormat.ZIP
                }
            }
        }.getOrDefault(DetectedFileFormat.ZIP)
    }

    private fun classifyByExtensionFallback(ext: String): DetectedFileFormat = when (ext) {
        "sh", "bash", "zsh", "py", "pl", "rb" -> DetectedFileFormat.SHELL_SCRIPT
        "txt", "log", "csv", "ini", "conf", "bashrc", "zshrc", "profile", "gitconfig" -> DetectedFileFormat.UTF8_TEXT
        "md", "markdown" -> DetectedFileFormat.MARKDOWN
        "json" -> DetectedFileFormat.JSON
        "xml" -> DetectedFileFormat.XML
        "kt", "kts", "java", "c", "cpp", "h" -> DetectedFileFormat.KOTLIN_SOURCE
        "zip" -> DetectedFileFormat.ZIP
        "jar" -> DetectedFileFormat.JAR
        "tar" -> DetectedFileFormat.TAR
        "gz", "tgz" -> DetectedFileFormat.GZIP
        "bz2", "tbz2" -> DetectedFileFormat.BZIP2
        "xz", "txz" -> DetectedFileFormat.XZ
        "lz4", "tlz4" -> DetectedFileFormat.LZ4
        "zst", "zstd", "tzst" -> DetectedFileFormat.ZSTD
        "lzma", "tlz" -> DetectedFileFormat.LZMA
        "cpio" -> DetectedFileFormat.CPIO
        "7z" -> DetectedFileFormat.SEVEN_Z
        "rar" -> DetectedFileFormat.RAR
        "iso" -> DetectedFileFormat.ISO
        "png" -> DetectedFileFormat.PNG
        "jpg", "jpeg" -> DetectedFileFormat.JPEG
        "gif" -> DetectedFileFormat.GIF
        "webp" -> DetectedFileFormat.WEBP
        "bmp" -> DetectedFileFormat.BMP
        "pdf" -> DetectedFileFormat.PDF
        "apk" -> DetectedFileFormat.APK
        "mp3" -> DetectedFileFormat.MP3
        "wav" -> DetectedFileFormat.WAV
        "mp4", "mkv", "webm" -> DetectedFileFormat.MP4
        "db", "sqlite", "sqlite3" -> DetectedFileFormat.SQLITE
        "so", "elf" -> DetectedFileFormat.ELF_BINARY
        else -> DetectedFileFormat.UNKNOWN
    }

    fun isEditableTextFormat(format: DetectedFileFormat): Boolean {
        return format in setOf(
            DetectedFileFormat.UTF8_TEXT,
            DetectedFileFormat.MARKDOWN,
            DetectedFileFormat.JSON,
            DetectedFileFormat.XML,
            DetectedFileFormat.KOTLIN_SOURCE,
            DetectedFileFormat.SHELL_SCRIPT
        )
    }

    fun buildFileEntry(file: File, context: Context? = null): FileEntry {
        val isDir = file.isDirectory
        val ext = if (isDir) "" else file.extension.lowercase()
        val detectedFormat = detectFormat(file)
        val mime = resolveMimeType(isDir, ext, detectedFormat)

        var imgW: Int? = null
        var imgH: Int? = null
        if (!isDir && detectedFormat.isImage && detectedFormat != DetectedFileFormat.SVG) {
            runCatching {
                val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(file.absolutePath, opts)
                if (opts.outWidth > 0 && opts.outHeight > 0) {
                    imgW = opts.outWidth
                    imgH = opts.outHeight
                }
            }
        }

        var pdfPages: Int? = null
        if (!isDir && detectedFormat == DetectedFileFormat.PDF) {
            runCatching {
                ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)?.use { pfd ->
                    PdfRenderer(pfd).use { renderer ->
                        pdfPages = renderer.pageCount
                    }
                }
            }
        }

        var apkPkg: String? = null
        var apkVer: String? = null
        if (!isDir && detectedFormat == DetectedFileFormat.APK && context != null) {
            runCatching {
                val info = context.packageManager.getPackageArchiveInfo(file.absolutePath, 0)
                apkPkg = info?.packageName
                apkVer = info?.versionName
            }
        }

        val perms = buildString {
            append(if (isDir) "d" else "-")
            append(if (file.canRead()) "r" else "-")
            append(if (file.canWrite()) "w" else "-")
            append(if (file.canExecute()) "x" else "-")
        }

        val directChildren = if (isDir) file.list()?.size else null

        return FileEntry(
            path = file.absolutePath,
            name = file.name,
            extension = ext,
            mimeType = mime,
            detectedFormat = detectedFormat,
            sizeBytes = if (isDir) 0L else file.length(),
            lastModified = file.lastModified(),
            createdDate = file.lastModified().takeIf { it > 0L },
            isDirectory = isDir,
            isHidden = file.isHidden || file.name.startsWith("."),
            permissions = perms,
            directChildCount = directChildren,
            imageWidth = imgW,
            imageHeight = imgH,
            pageCount = pdfPages,
            exifSummary = if (imgW != null) "${imgW}×${imgH} px • ${detectedFormat.displayName}" else null,
            apkPackageName = apkPkg,
            apkVersionName = apkVer,
            isSafUri = false,
            safUriString = null
        )
    }

    fun resolveMimeType(isDir: Boolean, ext: String, detectedFormat: DetectedFileFormat): String {
        if (isDir) return "inode/directory"
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
            ?: when (detectedFormat) {
                DetectedFileFormat.PNG -> "image/png"
                DetectedFileFormat.JPEG -> "image/jpeg"
                DetectedFileFormat.GIF -> "image/gif"
                DetectedFileFormat.BMP -> "image/bmp"
                DetectedFileFormat.PDF -> "application/pdf"
                DetectedFileFormat.ZIP -> "application/zip"
                DetectedFileFormat.JAR -> "application/java-archive"
                DetectedFileFormat.TAR -> "application/x-tar"
                DetectedFileFormat.GZIP -> "application/gzip"
                DetectedFileFormat.BZIP2 -> "application/x-bzip2"
                DetectedFileFormat.XZ -> "application/x-xz"
                DetectedFileFormat.LZ4 -> "application/x-lz4"
                DetectedFileFormat.ZSTD -> "application/zstd"
                DetectedFileFormat.LZMA -> "application/x-lzma"
                DetectedFileFormat.CPIO -> "application/x-cpio"
                DetectedFileFormat.SEVEN_Z -> "application/x-7z-compressed"
                DetectedFileFormat.RAR -> "application/vnd.rar"
                DetectedFileFormat.ISO -> "application/x-iso9660-image"
                DetectedFileFormat.JSON -> "application/json"
                DetectedFileFormat.SHELL_SCRIPT -> "text/x-shellscript"
                DetectedFileFormat.MARKDOWN,
                DetectedFileFormat.UTF8_TEXT,
                DetectedFileFormat.KOTLIN_SOURCE -> "text/plain"
                else -> "application/octet-stream"
            }
    }

    suspend fun computeSha256(file: File): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            FileInputStream(file).use { computeSha256FromStream(it, file.length()) }
        }
    }

    suspend fun computeSha256ForPathOrUri(
        context: Context,
        pathOrUri: String,
        totalBytesHint: Long = 0L,
        onProgress: (suspend (Long, Long) -> Unit)? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            if (pathOrUri.startsWith("content://")) {
                val uri = Uri.parse(pathOrUri)
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    computeSha256FromStream(stream, totalBytesHint, onProgress)
                } ?: throw IllegalStateException("Cannot open input stream for URI")
            } else {
                val f = File(pathOrUri)
                FileInputStream(f).use { stream ->
                    computeSha256FromStream(stream, f.length().coerceAtLeast(totalBytesHint), onProgress)
                }
            }
        }
    }

    private suspend fun computeSha256FromStream(
        input: InputStream,
        totalBytes: Long = 0L,
        onProgress: (suspend (Long, Long) -> Unit)? = null
    ): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(64 * 1024)
        var processed = 0L
        var read: Int
        while (input.read(buffer).also { read = it } != -1) {
            coroutineContext.ensureActive()
            digest.update(buffer, 0, read)
            processed += read
            onProgress?.invoke(processed, totalBytes)
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    fun formatBytes(bytes: Long): String {
        if (bytes < 1024L) return "$bytes B"
        val kb = bytes / 1024.0
        if (kb < 1024.0) return String.format(Locale.US, "%.1f KB", kb)
        val mb = kb / 1024.0
        if (mb < 1024.0) return String.format(Locale.US, "%.2f MB", mb)
        val gb = mb / 1024.0
        return String.format(Locale.US, "%.2f GB", gb)
    }

    fun formatSpeed(bytesPerSec: Long): String {
        if (bytesPerSec <= 0L) return ""
        return "${formatBytes(bytesPerSec)}/s"
    }

    fun formatEtaSeconds(etaSeconds: Long?): String {
        val sec = etaSeconds ?: return ""
        if (sec < 0L) return ""
        if (sec < 60L) return "${sec}s left"
        val mins = sec / 60L
        val remSec = sec % 60L
        if (mins < 60L) return "${mins}m ${remSec}s left"
        val hrs = mins / 60L
        val remMins = mins % 60L
        return "${hrs}h ${remMins}m left"
    }

    fun formatTimestamp(epochMs: Long): String {
        if (epochMs <= 0L) return "Unavailable"
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
        return sdf.format(Date(epochMs))
    }
}
