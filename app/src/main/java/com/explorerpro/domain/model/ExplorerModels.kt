package com.explorerpro.domain.model

import java.util.UUID

/**
 * Strictly three primary file view modes as specified in Section 5.
 */
enum class ViewMode(val label: String) {
    GRID("Grid"),
    SIMPLE_LIST("Simple List"),
    DETAILED_LIST("Detailed List")
}

enum class ThemePreference(val label: String) {
    SYSTEM("System"),
    DARK("Dark"),
    LIGHT("Light")
}

enum class PaneId {
    LEFT,
    RIGHT
}

enum class DetectedFileFormat(
    val displayName: String,
    val category: FileCategory,
    val magicSignatureHex: String,
    val isArchive: Boolean = false,
    val isImage: Boolean = false
) {
    DIRECTORY("Folder", FileCategory.FOLDER, "DIR"),
    PNG("PNG Image", FileCategory.IMAGE, "89 50 4E 47 0D 0A 1A 0A", isImage = true),
    JPEG("JPEG Image", FileCategory.IMAGE, "FF D8 FF", isImage = true),
    GIF("GIF Animation", FileCategory.IMAGE, "47 49 46 38", isImage = true),
    WEBP("WebP Image", FileCategory.IMAGE, "52 49 46 46 .. 57 45 42 50", isImage = true),
    BMP("Bitmap Image", FileCategory.IMAGE, "42 4D", isImage = true),
    SVG("SVG Vector", FileCategory.IMAGE, "<svg", isImage = true),
    PDF("PDF Document", FileCategory.DOCUMENT, "25 50 44 46 2D"),
    DOCX("Word OpenXML Document", FileCategory.DOCUMENT, "50 4B 03 04 [word/]"),
    XLSX("Excel OpenXML Spreadsheet", FileCategory.DOCUMENT, "50 4B 03 04 [xl/]"),
    PPTX("PowerPoint Presentation", FileCategory.DOCUMENT, "50 4B 03 04 [ppt/]"),
    ZIP("ZIP Archive", FileCategory.ARCHIVE, "50 4B 03 04", isArchive = true),
    TAR("POSIX TAR Archive", FileCategory.ARCHIVE, "75 73 74 61 72 (ustar)", isArchive = true),
    GZIP("GZIP Compressed Archive", FileCategory.ARCHIVE, "1F 8B 08", isArchive = true),
    BZIP2("BZIP2 Compressed Archive", FileCategory.ARCHIVE, "42 5A 68 (BZh)", isArchive = true),
    XZ("XZ / LZMA2 Compressed Archive", FileCategory.ARCHIVE, "FD 37 7A 58 5A 00", isArchive = true),
    SEVEN_Z("7-Zip Archive", FileCategory.ARCHIVE, "37 7A BC AF 27 1C", isArchive = true),
    RAR("RAR Archive", FileCategory.ARCHIVE, "52 61 72 21 1A 07", isArchive = true),
    ISO("ISO 9660 Disc Image", FileCategory.ARCHIVE, "43 44 30 30 31 (CD001)", isArchive = true),
    LZ4("LZ4 Compressed Archive", FileCategory.ARCHIVE, "04 22 4D 18", isArchive = true),
    ZSTD("Zstandard Archive", FileCategory.ARCHIVE, "28 B5 2F FD", isArchive = true),
    LZMA("LZMA Compressed Archive", FileCategory.ARCHIVE, "5D 00 00", isArchive = true),
    CPIO("POSIX CPIO Archive", FileCategory.ARCHIVE, "30 37 30 37 30", isArchive = true),
    JAR("Java Archive (JAR)", FileCategory.ARCHIVE, "50 4B 03 04 [META-INF]", isArchive = true),
    APK("Android Package (APK)", FileCategory.APK, "50 4B 03 04 [AndroidManifest.xml]"),
    MP3("MP3 Audio", FileCategory.AUDIO, "49 44 33 (ID3) / FF FB"),
    WAV("WAV PCM Audio", FileCategory.AUDIO, "52 49 46 46 .. 57 41 56 45"),
    MP4("MP4 Multimedia Video", FileCategory.VIDEO, "00 00 00 .. 66 74 79 70"),
    SQLITE("SQLite 3 Database", FileCategory.DATA, "53 51 4C 69 74 65"),
    ELF_BINARY("ELF Native Binary (Linux/Android)", FileCategory.CODE, "7F 45 4C 46"),
    SHELL_SCRIPT("Shell / Executable Script", FileCategory.CODE, "23 21 (#! Shebang)"),
    JSON("JSON Document", FileCategory.CODE, "UTF-8 JSON"),
    XML("XML Document", FileCategory.CODE, "3C 3F 78 6D 6C"),
    KOTLIN_SOURCE("Kotlin Source File", FileCategory.CODE, "UTF-8 Kotlin"),
    MARKDOWN("Markdown Document", FileCategory.DOCUMENT, "UTF-8 Markdown"),
    UTF8_TEXT("Plain UTF-8 Text", FileCategory.DOCUMENT, "UTF-8 Text"),
    UNKNOWN("Unverified Binary / Format", FileCategory.OTHER, "UNVERIFIED")
}

enum class FileCategory(val label: String) {
    FOLDER("Folders"),
    IMAGE("Images"),
    VIDEO("Videos"),
    AUDIO("Audio"),
    DOCUMENT("Documents"),
    CODE("Code & Config"),
    ARCHIVE("Archives"),
    APK("Android Packages"),
    DATA("Databases"),
    OTHER("Other Files")
}

data class FileEntry(
    val path: String,
    val name: String,
    val extension: String,
    val mimeType: String,
    val detectedFormat: DetectedFileFormat,
    val sizeBytes: Long,
    val lastModified: Long,
    val createdDate: Long?,
    val isDirectory: Boolean,
    val isHidden: Boolean,
    val permissions: String,
    val directChildCount: Int? = null,
    val imageWidth: Int? = null,
    val imageHeight: Int? = null,
    val durationMs: Long? = null,
    val artist: String? = null,
    val album: String? = null,
    val bitrateKbps: Int? = null,
    val videoCodec: String? = null,
    val pageCount: Int? = null,
    val exifSummary: String? = null,
    val apkPackageName: String? = null,
    val apkVersionName: String? = null,
    val isSafUri: Boolean = false,
    val safUriString: String? = null
) {
    val dimensionsText: String?
        get() = if (imageWidth != null && imageHeight != null && imageWidth > 0 && imageHeight > 0) {
            "${imageWidth}×${imageHeight}"
        } else null

    /**
     * Returns true if this file can be extracted or inspected as an archive directly from the
     * file explorer context menu (ZIP, TAR, GZ/TGZ, BZ2/TBZ2, XZ/TXZ, 7Z, RAR, ISO, APK, JAR).
     */
    val isExtractableArchive: Boolean
        get() {
            if (isDirectory) return false
            if (detectedFormat.isArchive || detectedFormat == DetectedFileFormat.APK) return true
            val lower = name.lowercase()
            return lower.endsWith(".zip") ||
                lower.endsWith(".tar") ||
                lower.endsWith(".tar.gz") ||
                lower.endsWith(".tgz") ||
                lower.endsWith(".gz") ||
                lower.endsWith(".tar.bz2") ||
                lower.endsWith(".tbz2") ||
                lower.endsWith(".bz2") ||
                lower.endsWith(".tar.xz") ||
                lower.endsWith(".txz") ||
                lower.endsWith(".xz") ||
                lower.endsWith(".tar.lz4") ||
                lower.endsWith(".tlz4") ||
                lower.endsWith(".lz4") ||
                lower.endsWith(".tar.zst") ||
                lower.endsWith(".tar.zstd") ||
                lower.endsWith(".tzst") ||
                lower.endsWith(".zst") ||
                lower.endsWith(".zstd") ||
                lower.endsWith(".tar.lzma") ||
                lower.endsWith(".tlz") ||
                lower.endsWith(".lzma") ||
                lower.endsWith(".cpio") ||
                lower.endsWith(".7z") ||
                lower.endsWith(".rar") ||
                lower.endsWith(".iso") ||
                lower.endsWith(".jar") ||
                lower.endsWith(".apk")
        }
}

enum class SortField(val label: String) {
    NAME_NATURAL("Name (Natural)"),
    SIZE("Size"),
    MODIFIED_DATE("Modified Date"),
    CREATED_DATE("Created Date"),
    TYPE("Type / Category"),
    EXTENSION("Extension"),
    DETECTED_FORMAT("Detected Format"),
    DIMENSIONS("Image Dimensions"),
    DURATION("Media Duration"),
    FOLDER_ITEM_COUNT("Folder Item Count")
}

enum class SortOrder(val label: String) {
    ASCENDING("Ascending"),
    DESCENDING("Descending")
}

enum class FolderPlacement(val label: String) {
    FOLDERS_FIRST("Folders First"),
    FILES_FIRST("Files First"),
    FOLDERS_LAST("Folders Last")
}

data class SortConfig(
    val field: SortField = SortField.NAME_NATURAL,
    val order: SortOrder = SortOrder.ASCENDING,
    val folderPlacement: FolderPlacement = FolderPlacement.FOLDERS_FIRST,
    val caseSensitive: Boolean = false
)

enum class GroupBy(val label: String) {
    NONE("No Grouping"),
    FILES_VS_FOLDERS("Folders vs Files"),
    TYPE("Category / Type"),
    EXTENSION("Extension"),
    SIZE("Size Tier"),
    MODIFIED_DATE("Modified Date"),
    NAME("Alphabetical Initial")
}

data class ExplorerTab(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val currentPath: String,
    val backStack: List<String> = emptyList(),
    val forwardStack: List<String> = emptyList(),
    val isSafTab: Boolean = false,
    val safUriString: String? = null
)

data class FolderIntelligenceResult(
    val path: String,
    val directFileCount: Int = 0,
    val directFolderCount: Int = 0,
    val directTotalCount: Int = 0,
    val recursiveFileCount: Int = 0,
    val recursiveFolderCount: Int = 0,
    val recursiveTotalBytes: Long = 0L,
    val lastModifiedTimestamp: Long = 0L,
    val isCalculatingRecursive: Boolean = false,
    val isComplete: Boolean = false
)

data class PaneState(
    val paneId: PaneId,
    val tabs: List<ExplorerTab> = emptyList(),
    val activeTabId: String = "",
    val closedTabs: List<ExplorerTab> = emptyList(),
    val currentPath: String = "",
    val entries: List<FileEntry> = emptyList(),
    val groupedEntries: Map<String, List<FileEntry>> = emptyMap(),
    val selectedPaths: Set<String> = emptySet(),
    val viewMode: ViewMode = ViewMode.DETAILED_LIST,
    val gridColumns: Int = 3,
    val sortConfig: SortConfig = SortConfig(),
    val groupBy: GroupBy = GroupBy.NONE,
    val searchQuery: String = "",
    val searchRecursive: Boolean = false,
    val searchCategoryFilter: FileCategory? = null,
    val isSearching: Boolean = false,
    val folderIntelligence: FolderIntelligenceResult? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

enum class OperationType(val label: String) {
    COPY("Copy"),
    MOVE("Move"),
    DELETE("Delete"),
    RECYCLE("Move to Recycle Bin"),
    RESTORE("Restore from Recycle Bin"),
    BATCH_RENAME("Batch Rename"),
    CHECKSUM_VERIFY("Checksum Verification"),
    ARCHIVE_EXTRACT("Extract Archive"),
    ARCHIVE_CREATE("Create Archive"),
    ARCHIVE_VERIFY("Verify Archive Integrity"),
    THUMBNAIL_GENERATION("Thumbnail & Preview Generation"),
    SEARCH_INDEXING("Search & Indexing"),
    FOLDER_INTELLIGENCE("Folder Intelligence"),
    STORAGE_ANALYSIS("Storage & Duplicate Analysis")
}

enum class OperationStatus(val label: String) {
    QUEUED("Queued"),
    RUNNING("Running"),
    PAUSED("Paused"),
    CANCELLING("Cancelling"),
    CANCELLED("Cancelled"),
    CONFLICT_PENDING("Conflict Decision Needed"),
    COMPLETED("Completed"),
    FAILED("Failed"),
    INTERRUPTED("Interrupted")
}

enum class ConflictResolution(val label: String) {
    ASK("Ask on Conflict"),
    RENAME("Keep Both (Auto-Rename)"),
    REPLACE("Replace Existing"),
    SKIP("Skip Conflicting File")
}

data class FileOperationTask(
    val id: String = UUID.randomUUID().toString(),
    val type: OperationType,
    val title: String,
    val sourcePaths: List<String>,
    val destinationPath: String,
    val status: OperationStatus = OperationStatus.QUEUED,
    val minValue: Float = 0f,
    val maxValue: Float = 100f,
    val progressValue: Float = 0f,
    val progressFraction: Float = 0f,
    val indeterminate: Boolean = false,
    val processedItems: Int = 0,
    val totalItems: Int = 1,
    val transferredBytes: Long = 0L,
    val totalBytes: Long = 0L,
    val speedBytesPerSec: Long = 0L,
    val estimatedRemainingSeconds: Long? = null,
    val currentItemName: String = "",
    val conflictingFileName: String? = null,
    val errorDetail: String? = null,
    val skippedCount: Int = 0,
    val errorCount: Int = 0,
    val canPause: Boolean = false,
    val canCancel: Boolean = true,
    val canRetry: Boolean = false,
    val timestamp: Long = System.currentTimeMillis(),
    val completionTimeMs: Long? = null
) {
    val isActiveOrPaused: Boolean
        get() = status == OperationStatus.QUEUED ||
            status == OperationStatus.RUNNING ||
            status == OperationStatus.PAUSED ||
            status == OperationStatus.CANCELLING
}

data class ArchiveEntryItem(
    val entryPath: String,
    val name: String,
    val uncompressedSize: Long,
    val compressedSize: Long,
    val isDirectory: Boolean,
    val lastModified: Long,
    val crc32Hex: String,
    val isPathSafe: Boolean,
    val compressionRatioPercent: Int,
    val isEncrypted: Boolean = false
)

enum class ArchiveCreateFormat(
    val extension: String,
    val shortName: String,
    val label: String,
    val description: String,
    val supportsEncryption: Boolean = false,
    val supportsCompressionLevel: Boolean = true,
    val isSingleStreamFormat: Boolean = false
) {
    SEVEN_Z("7z", "7z", "7-Zip (.7z)", "High-ratio LZMA2 solid archive", supportsEncryption = false, supportsCompressionLevel = true),
    ZIP("zip", "zip", "ZIP (.zip)", "Universal ZIP with optional AES-256 encryption", supportsEncryption = true, supportsCompressionLevel = true),
    TAR("tar", "tar", "TAR (.tar)", "Uncompressed POSIX archive", supportsEncryption = false, supportsCompressionLevel = false),
    TAR_BZ2("tar.bz2", "tar.bz2", "TAR.BZ2 (.tar.bz2)", "BZIP2 compressed POSIX TAR", supportsEncryption = false, supportsCompressionLevel = true),
    TAR_GZ("tar.gz", "tar.gz", "TAR.GZ (.tar.gz)", "GZIP compressed POSIX TAR", supportsEncryption = false, supportsCompressionLevel = true),
    TAR_XZ("tar.xz", "tar.xz", "TAR.XZ (.tar.xz)", "XZ / LZMA2 compressed TAR", supportsEncryption = false, supportsCompressionLevel = true),
    TAR_LZ4("tar.lz4", "tar.lz4", "TAR.LZ4 (.tar.lz4)", "Ultra-fast LZ4 compressed TAR", supportsEncryption = false, supportsCompressionLevel = false),
    TAR_ZSTD("tar.zst", "tar.zstd", "TAR.ZSTD (.tar.zst)", "Zstandard high-speed TAR", supportsEncryption = false, supportsCompressionLevel = true),
    TAR_LZMA("tar.lzma", "tar.lzma", "TAR.LZMA (.tar.lzma)", "LZMA compressed TAR", supportsEncryption = false, supportsCompressionLevel = false),
    JAR("jar", "jar", "JAR (.jar)", "Java ZIP archive package", supportsEncryption = false, supportsCompressionLevel = true),
    CPIO("cpio", "cpio", "CPIO (.cpio)", "POSIX CPIO archive", supportsEncryption = false, supportsCompressionLevel = false),
    GZ("gz", "gz", "GZIP (.gz)", "GZIP single-stream (auto-TAR for folders)", supportsEncryption = false, supportsCompressionLevel = true, isSingleStreamFormat = true),
    BZ2("bz2", "bz2", "BZIP2 (.bz2)", "BZIP2 single-stream (auto-TAR for folders)", supportsEncryption = false, supportsCompressionLevel = true, isSingleStreamFormat = true),
    XZ("xz", "xz", "XZ (.xz)", "XZ single-stream (auto-TAR for folders)", supportsEncryption = false, supportsCompressionLevel = true, isSingleStreamFormat = true),
    LZ4("lz4", "lz4", "LZ4 (.lz4)", "LZ4 single-stream (auto-TAR for folders)", supportsEncryption = false, supportsCompressionLevel = false, isSingleStreamFormat = true),
    ZSTD("zst", "zstd", "ZSTD (.zst)", "Zstandard single-stream (auto-TAR for folders)", supportsEncryption = false, supportsCompressionLevel = true, isSingleStreamFormat = true)
}

enum class ArchiveCompressionLevel(
    val label: String,
    val zipDeflateLevel: Int,
    val gzipLevel: Int,
    val bzip2BlockSize: Int,
    val xzPreset: Int,
    val zstdLevel: Int
) {
    NO_COMPRESSION("No compression", 0, 0, 1, 0, 1),
    FASTEST("Fastest", 1, 1, 1, 1, 1),
    FAST("Fast", 3, 3, 3, 3, 3),
    NORMAL("Normal", 6, 6, 6, 6, 6),
    MAXIMUM("Maximum", 8, 8, 8, 7, 12),
    ULTRA("Ultra", 9, 9, 9, 9, 19)
}

enum class ArchiveEncryptionMode(val label: String) {
    NONE("None"),
    AES_256("Data (AES-256)"),
    ZIP_CRYPTO("ZipCrypto (Legacy)")
}

data class ArchiveCreationConfig(
    val archiveName: String,
    val format: ArchiveCreateFormat = ArchiveCreateFormat.ZIP,
    val compressionLevel: ArchiveCompressionLevel = ArchiveCompressionLevel.NORMAL,
    val encryptionMode: ArchiveEncryptionMode = ArchiveEncryptionMode.NONE,
    val password: String = "",
    val deleteSourceAfterCompression: Boolean = false,
    val createSeparateArchives: Boolean = false
)

enum class CaseTransform(val label: String) {
    NONE("Keep Original Case"),
    LOWERCASE("lowercase"),
    UPPERCASE("UPPERCASE"),
    TITLE_CASE("Title Case")
}

data class BatchRenameConfig(
    val prefix: String = "",
    val suffix: String = "",
    val findText: String = "",
    val replaceText: String = "",
    val useRegex: Boolean = false,
    val removeChars: String = "",
    val caseTransform: CaseTransform = CaseTransform.NONE,
    val addSequentialNumber: Boolean = false,
    val sequenceStart: Int = 1,
    val sequenceDigits: Int = 2,
    val appendDateStamp: Boolean = false,
    val overrideExtension: String = ""
)

data class BatchRenamePreviewItem(
    val originalFile: FileEntry,
    val proposedName: String,
    val hasConflict: Boolean,
    val conflictReason: String? = null
)

data class FolderComparisonResult(
    val leftPath: String,
    val rightPath: String,
    val onlyInLeft: List<FileEntry>,
    val onlyInRight: List<FileEntry>,
    val modifiedDifferences: List<Pair<FileEntry, FileEntry>>,
    val identicalFiles: List<FileEntry>
)

data class DuplicateFileGroup(
    val sha256: String,
    val sizeBytes: Long,
    val files: List<FileEntry>
) {
    val wastedBytes: Long
        get() = if (files.size > 1) sizeBytes * (files.size - 1) else 0L
}

data class StorageVolumeSummary(
    val id: String,
    val title: String,
    val subtitle: String = "",
    val rootPath: String,
    val totalBytes: Long,
    val freeBytes: Long,
    val usedBytes: Long,
    val isRemovable: Boolean,
    val isSafBacked: Boolean = false,
    val isReadOnly: Boolean = false,
    val requiresAllFilesAccess: Boolean = false
)

data class StorageAnalysisReport(
    val totalWorkspaceBytes: Long,
    val categoryBreakdown: Map<FileCategory, Long>,
    val largestFiles: List<FileEntry>,
    val duplicateGroups: List<DuplicateFileGroup>,
    val scannedFileCount: Int = 0,
    val scannedFolderCount: Int = 0,
    val restrictedFolderCount: Int = 0,
    val isComplete: Boolean = true
)

data class TermuxIntegrationStatus(
    val isTermuxInstalled: Boolean = false,
    val termuxVersionName: String? = null,
    val isDocumentsProviderAvailable: Boolean = false,
    val isProviderClientAlive: Boolean = false,
    val grantedTermuxTreeUris: List<String> = emptyList(),
    val isDirectHomeAccessible: Boolean = false,
    val isExternalTermuxDirAccessible: Boolean = false,
    val externalTermuxDirPath: String? = null,
    val defaultTermuxHomeUri: String = "",
    val totalBytes: Long = 0L,
    val freeBytes: Long = 0L,
    val autoMountEnabled: Boolean = true
) {
    val isMountedOrAccessible: Boolean
        get() = grantedTermuxTreeUris.isNotEmpty() || isDirectHomeAccessible || isExternalTermuxDirAccessible

    val activeMountTarget: String?
        get() = when {
            grantedTermuxTreeUris.isNotEmpty() -> grantedTermuxTreeUris.first()
            isDirectHomeAccessible -> "/data/data/com.termux/files/home"
            isExternalTermuxDirAccessible -> externalTermuxDirPath
            else -> null
        }
}

enum class ClipboardAction {
    NONE,
    COPY,
    MOVE
}

data class ClipboardState(
    val sourceFiles: List<FileEntry> = emptyList(),
    val action: ClipboardAction = ClipboardAction.NONE
) {
    val isEmpty: Boolean
        get() = sourceFiles.isEmpty() || action == ClipboardAction.NONE
}

enum class FabStyleMode(val label: String) {
    SPEED_DIAL("Speed Dial"),
    EXTENDED("Extended"),
    STANDARD("Standard"),
    SMALL("Small")
}

enum class SpeedDialDirection(val label: String) {
    UPWARD("Upward"),
    HORIZONTAL("Horizontal")
}

enum class FabPlacementMode(val label: String) {
    BOTTOM_END("Bottom-End"),
    CONTENT_AWARE("Active Pane Aware")
}

enum class FabThemeMode(val label: String) {
    FOLLOW_SYSTEM("Material 3 Default"),
    DYNAMIC_MATERIAL_YOU("Dynamic Material You"),
    CUSTOM_ACCENT("Fluent Cyan Accent")
}

data class FabCustomizationSettings(
    val fabStyleMode: FabStyleMode = FabStyleMode.SPEED_DIAL,
    val speedDialDirection: SpeedDialDirection = SpeedDialDirection.UPWARD,
    val showSpeedDialLabels: Boolean = true,
    val animationsEnabled: Boolean = true,
    val hideWhileScrolling: Boolean = true,
    val adaptToSelection: Boolean = true,
    val hideWhenKeyboardOpen: Boolean = true,
    val placementMode: FabPlacementMode = FabPlacementMode.CONTENT_AWARE,
    val themeMode: FabThemeMode = FabThemeMode.FOLLOW_SYSTEM
)

// --- Universal Dialog & Interaction System Models ---

enum class DialogVisualStyle(val label: String) {
    STANDARD("Standard M3"),
    COMPACT("Compact"),
    CENTERED("Centered"),
    FULL_SCREEN("Edge-to-Edge Full Screen"),
    BOTTOM_SHEET("Modal Bottom Sheet")
}

enum class FileConflictDecision(val label: String) {
    REPLACE("Replace Existing"),
    SKIP("Skip File"),
    KEEP_BOTH("Keep Both (Auto-Rename)"),
    CUSTOM_RENAME("Custom Rename"),
    CANCEL("Cancel Operation")
}

data class FileConflictPair(
    val sourceEntry: FileEntry,
    val destinationEntry: FileEntry
)

data class PendingTransferConflictState(
    val allSourceEntries: List<FileEntry>,
    val destinationDirPath: String,
    val isMove: Boolean,
    val conflicts: List<FileConflictPair>,
    val currentConflictIndex: Int = 0,
    val resolvedPerSourcePath: Map<String, Pair<FileConflictDecision, String?>> = emptyMap()
) {
    val activeConflict: FileConflictPair?
        get() = conflicts.getOrNull(currentConflictIndex)

    val remainingConflictsCount: Int
        get() = (conflicts.size - currentConflictIndex - 1).coerceAtLeast(0)
}

data class DeleteConfirmationRequest(
    val entries: List<FileEntry>,
    val rawPaths: List<String>,
    val defaultUseRecycleBin: Boolean,
    val isRecycleBinClearRequest: Boolean = false
) {
    val totalBytes: Long
        get() = entries.sumOf { it.sizeBytes.coerceAtLeast(0L) }
}

enum class ExplorerNoticeSeverity {
    WARNING,
    ERROR,
    SUCCESS,
    SECURITY,
    PERMISSION
}

data class ExplorerNoticeDialogModel(
    val id: String = UUID.randomUUID().toString(),
    val severity: ExplorerNoticeSeverity,
    val title: String,
    val message: String,
    val technicalDetails: String? = null,
    val destinationPath: String? = null,
    val primaryActionLabel: String = "OK",
    val secondaryActionLabel: String? = null,
    val onRetryAction: (() -> Unit)? = null
)

data class InputValidationResult(
    val isValid: Boolean,
    val sanitizedValue: String,
    val errorMessage: String? = null
)

object ExplorerInputValidator {
    private val ILLEGAL_CHARS = charArrayOf('/', '\\', '\u0000', ':', '*', '?', '"', '<', '>', '|')
    private val RESERVED_NAMES = setOf(".", "..", "CON", "PRN", "AUX", "NUL")
    private val RESERVED_DEVICE_BASES = setOf("CON", "PRN", "AUX", "NUL")

    fun validateFileOrFolderName(
        rawInput: String,
        maxLength: Int = 255,
        existingSiblingNames: Set<String> = emptySet(),
        originalName: String? = null,
        requireExtension: Boolean = false
    ): InputValidationResult {
        val trimmed = rawInput.trim()
        if (trimmed.isEmpty()) {
            return InputValidationResult(
                isValid = false,
                sanitizedValue = "",
                errorMessage = "Name cannot be empty."
            )
        }
        if (trimmed.length > maxLength) {
            return InputValidationResult(
                isValid = false,
                sanitizedValue = trimmed.take(maxLength),
                errorMessage = "Name exceeds maximum length ($maxLength characters)."
            )
        }
        val baseWithoutExt = trimmed.substringBefore('.')
        if (trimmed in RESERVED_NAMES ||
            trimmed.uppercase() in RESERVED_NAMES ||
            (baseWithoutExt.isNotEmpty() && baseWithoutExt.uppercase() in RESERVED_DEVICE_BASES)
        ) {
            return InputValidationResult(
                isValid = false,
                sanitizedValue = trimmed,
                errorMessage = "'$trimmed' is a reserved system path name."
            )
        }
        if (trimmed.contains("..")) {
            return InputValidationResult(
                isValid = false,
                sanitizedValue = trimmed.replace("..", ""),
                errorMessage = "Path traversal sequences ('..') are not allowed."
            )
        }
        val foundIllegal = ILLEGAL_CHARS.firstOrNull { trimmed.indexOf(it) >= 0 }
        if (foundIllegal != null) {
            val displayChar = if (foundIllegal == '\u0000') "NUL" else foundIllegal.toString()
            return InputValidationResult(
                isValid = false,
                sanitizedValue = trimmed.filterNot { it in ILLEGAL_CHARS },
                errorMessage = "Invalid character '$displayChar' in file or folder name."
            )
        }
        if (trimmed.any { it.code < 32 }) {
            return InputValidationResult(
                isValid = false,
                sanitizedValue = trimmed.filter { it.code >= 32 },
                errorMessage = "Control characters are not allowed in file names."
            )
        }
        if (requireExtension && (!trimmed.contains('.') || trimmed.endsWith('.'))) {
            return InputValidationResult(
                isValid = false,
                sanitizedValue = trimmed,
                errorMessage = "Please include a valid file extension (e.g., .txt, .md, .sh)."
            )
        }
        if (originalName == null || !trimmed.equals(originalName, ignoreCase = true)) {
            if (existingSiblingNames.any { it.equals(trimmed, ignoreCase = true) }) {
                return InputValidationResult(
                    isValid = false,
                    sanitizedValue = trimmed,
                    errorMessage = "An item named '$trimmed' already exists in this folder."
                )
            }
        }
        return InputValidationResult(
            isValid = true,
            sanitizedValue = trimmed,
            errorMessage = null
        )
    }
}

data class AppPermissionStatus(
    val sdkInt: Int = 34,
    val requiresManageExternalStorage: Boolean = true,
    val isAllFilesAccessGranted: Boolean = false,
    val requiresRuntimeStoragePermissions: Boolean = false,
    val isRuntimeReadStorageGranted: Boolean = true,
    val isRuntimeWriteStorageGranted: Boolean = true,
    val requiresNotificationPermission: Boolean = true,
    val isNotificationPermissionGranted: Boolean = false,
    val isTermuxDetected: Boolean = false,
    val isTermuxAccessGranted: Boolean = false
) {
    val isStorageAccessGranted: Boolean
        get() = if (requiresManageExternalStorage) {
            isAllFilesAccessGranted
        } else {
            isRuntimeReadStorageGranted && isRuntimeWriteStorageGranted
        }

    val areAllCorePermissionsGranted: Boolean
        get() = isStorageAccessGranted &&
            (!requiresNotificationPermission || isNotificationPermissionGranted)

    val areAllPermissionsIncludingTermuxGranted: Boolean
        get() = areAllCorePermissionsGranted && (!isTermuxDetected || isTermuxAccessGranted)

    fun missingRuntimePermissions(): List<String> {
        val list = mutableListOf<String>()
        if (requiresNotificationPermission && !isNotificationPermissionGranted) {
            list.add("android.permission.POST_NOTIFICATIONS")
        }
        if (requiresRuntimeStoragePermissions) {
            if (!isRuntimeReadStorageGranted) {
                list.add("android.permission.READ_EXTERNAL_STORAGE")
            }
            if (!isRuntimeWriteStorageGranted && sdkInt <= 29) {
                list.add("android.permission.WRITE_EXTERNAL_STORAGE")
            }
        }
        return list
    }
}


