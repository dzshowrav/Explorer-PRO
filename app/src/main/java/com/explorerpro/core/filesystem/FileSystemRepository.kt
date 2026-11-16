package com.explorerpro.core.filesystem

import android.app.usage.StorageStatsManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.os.storage.StorageManager
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.Settings
import android.system.Os
import com.explorerpro.core.security.ArchiveEngine
import com.explorerpro.domain.model.BatchRenameConfig
import com.explorerpro.domain.model.ConflictResolution
import com.explorerpro.domain.model.DetectedFileFormat
import com.explorerpro.domain.model.DuplicateFileGroup
import com.explorerpro.domain.model.FileCategory
import com.explorerpro.domain.model.FileConflictDecision
import com.explorerpro.domain.model.FileConflictPair
import com.explorerpro.domain.model.FileEntry
import com.explorerpro.domain.model.FolderComparisonResult
import com.explorerpro.domain.model.FolderIntelligenceResult
import com.explorerpro.domain.model.StorageAnalysisReport
import com.explorerpro.domain.model.StorageVolumeSummary
import com.explorerpro.domain.model.TermuxIntegrationStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import kotlin.coroutines.coroutineContext

class FileSystemRepository(
    private val context: Context,
    val archiveEngine: ArchiveEngine = ArchiveEngine()
) {
    /**
     * Real Primary Shared Storage root (`/storage/emulated/0`) when available on device,
     * falling back to `context.filesDir` only in headless JVM unit test environments.
     */
    val primarySharedRoot: File by lazy {
        val ext = runCatching { Environment.getExternalStorageDirectory() }.getOrNull()
        if (ext != null && ext.exists()) ext else context.filesDir
    }

    /**
     * Real App Private Sandbox directory (`/data/user/0/<pkg>/files`).
     */
    val appSandboxRoot: File by lazy {
        context.filesDir.apply { mkdirs() }
    }

    /**
     * Real Android OS System partition root (`/system`).
     */
    val systemRoot: File by lazy {
        runCatching { Environment.getRootDirectory() }.getOrNull() ?: File("/system")
    }

    /**
     * Default root used for initial navigation — points to real Internal Shared Storage (`/storage/emulated/0`).
     */
    val workspaceRoot: File
        get() = primarySharedRoot

    val recycleBinDir: File by lazy {
        File(context.filesDir, ".Trash").apply { mkdirs() }
    }

    /**
     * Checks whether the app currently holds full POSIX All Files Access (`MANAGE_EXTERNAL_STORAGE`)
     * on Android 11+ (API 30+), or standard storage access on older APIs.
     */
    fun hasAllFilesAccess(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            runCatching { Environment.isExternalStorageManager() }
                .getOrElse { primarySharedRoot.canRead() }
        } else {
            val readGranted = androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.READ_EXTERNAL_STORAGE
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            readGranted || primarySharedRoot.canRead()
        }
    }

    /**
     * Queries live OS runtime & special permissions required by Explorer Pro.
     */
    fun queryAppPermissionStatus(persistedSafUris: Set<String> = emptySet()): com.explorerpro.domain.model.AppPermissionStatus {
        val sdk = Build.VERSION.SDK_INT
        val requiresManageStorage = sdk >= Build.VERSION_CODES.R
        val allFilesGranted = hasAllFilesAccess()

        val requiresRuntimeStorage = sdk <= Build.VERSION_CODES.S_V2
        val readStorageGranted = if (requiresRuntimeStorage) {
            androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.READ_EXTERNAL_STORAGE
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
        val writeStorageGranted = if (sdk <= Build.VERSION_CODES.Q) {
            androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

        val requiresNotif = sdk >= Build.VERSION_CODES.TIRAMISU
        val notifGranted = if (requiresNotif) {
            androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

        val termux = queryTermuxStatus(persistedSafUris)

        return com.explorerpro.domain.model.AppPermissionStatus(
            sdkInt = sdk,
            requiresManageExternalStorage = requiresManageStorage,
            isAllFilesAccessGranted = allFilesGranted,
            requiresRuntimeStoragePermissions = requiresRuntimeStorage,
            isRuntimeReadStorageGranted = readStorageGranted,
            isRuntimeWriteStorageGranted = writeStorageGranted,
            requiresNotificationPermission = requiresNotif,
            isNotificationPermissionGranted = notifGranted,
            isTermuxDetected = termux.isTermuxInstalled || termux.isDocumentsProviderAvailable,
            isTermuxAccessGranted = termux.isMountedOrAccessible
        )
    }

    /**
     * Builds the system Settings Intent for granting `MANAGE_EXTERNAL_STORAGE` (All Files Access)
     * to this File Manager app on Android 11+.
     */
    fun createAllFilesAccessIntent(): Intent {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Intent(
                Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                Uri.parse("package:${context.packageName}")
            )
        } else {
            createAppDetailsSettingsIntent()
        }
    }

    /**
     * Builds the system App Notification Settings Intent for Android 8.0+.
     */
    fun createNotificationSettingsIntent(): Intent {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            }
        } else {
            createAppDetailsSettingsIntent()
        }
    }

    /**
     * Builds the fallback Application Details Settings Intent.
     */
    fun createAppDetailsSettingsIntent(): Intent {
        return Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.parse("package:${context.packageName}")
        )
    }

    /**
     * Cleans up any legacy `ExplorerProWorkspace` synthetic directory from earlier builds
     * so 100% of folders and files shown in the app come from the real Android filesystem.
     */
    suspend fun initializeRealWorkspaceDirectories() = withContext(Dispatchers.IO) {
        val legacyWorkspace = File(context.filesDir, "ExplorerProWorkspace")
        if (legacyWorkspace.exists()) {
            runCatching { legacyWorkspace.deleteRecursively() }
        }
        recycleBinDir.mkdirs()
    }

    fun queryTermuxStatus(persistedSafUris: Set<String> = emptySet()): TermuxIntegrationStatus {
        return TermuxStorageBridge.queryTermuxStatus(context, persistedSafUris)
    }

    /**
     * Queries real Android OS hardware/partition storage volumes using `StorageManager`,
     * `StorageStatsManager`, `StatFs`, and `Os.fstatvfs` — never duplicating fake volumes.
     */
    fun getAvailableStorageVolumes(persistedSafUris: Set<String> = emptySet()): List<StorageVolumeSummary> {
        val volumes = mutableListOf<StorageVolumeSummary>()
        val storageManager = runCatching {
            context.getSystemService(Context.STORAGE_SERVICE) as? StorageManager
        }.getOrNull()
        val storageStatsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            runCatching {
                context.getSystemService(Context.STORAGE_STATS_SERVICE) as? StorageStatsManager
            }.getOrNull()
        } else null

        val allFilesGranted = hasAllFilesAccess()
        val primarySafTree = findPersistedSafTreeForPrimaryRoot()

        // 1. Real Primary Shared Storage (/storage/emulated/0) via StorageManager & StorageStatsManager
        val sharedRoot = primarySharedRoot
        if (sharedRoot.exists()) {
            var totalBytes = 0L
            var freeBytes = 0L

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && storageStatsManager != null) {
                runCatching {
                    totalBytes = storageStatsManager.getTotalBytes(StorageManager.UUID_DEFAULT)
                    freeBytes = storageStatsManager.getFreeBytes(StorageManager.UUID_DEFAULT)
                }
            }
            if (totalBytes <= 0L) {
                val statShared = runCatching { StatFs(sharedRoot.absolutePath) }.getOrNull()
                totalBytes = statShared?.totalBytes ?: 0L
                freeBytes = statShared?.availableBytes ?: 0L
            }
            val usedBytes = (totalBytes - freeBytes).coerceAtLeast(0L)

            val primaryVol = runCatching { storageManager?.primaryStorageVolume }.getOrNull()
            val osTitle = runCatching { primaryVol?.getDescription(context) }.getOrNull()
                ?.takeIf { it.isNotBlank() }
                ?: "Internal Shared Storage"
            val accessLabel = when {
                allFilesGranted -> "Full Native Access"
                primarySafTree != null -> "SAF Mounted"
                else -> "Scoped • Tap to Grant Full Access"
            }

            volumes.add(
                StorageVolumeSummary(
                    id = "primary_shared",
                    title = osTitle,
                    subtitle = "${sharedRoot.absolutePath} • $accessLabel",
                    rootPath = primarySafTree ?: sharedRoot.absolutePath,
                    totalBytes = totalBytes,
                    freeBytes = freeBytes,
                    usedBytes = usedBytes,
                    isRemovable = false,
                    isSafBacked = primarySafTree != null && !allFilesGranted,
                    isReadOnly = false,
                    requiresAllFilesAccess = !allFilesGranted && primarySafTree == null
                )
            )
        }

        // 2. Real Removable SD Cards / USB OTG Drives from StorageManager.storageVolumes
        val osVolumes = runCatching { storageManager?.storageVolumes.orEmpty() }.getOrDefault(emptyList())
        osVolumes.filter { !it.isPrimary }.forEachIndexed { idx, vol ->
            val volDir = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                vol.directory
            } else null

            if (volDir != null && volDir.exists()) {
                val stat = runCatching { StatFs(volDir.absolutePath) }.getOrNull()
                val total = stat?.totalBytes ?: 0L
                val free = stat?.availableBytes ?: 0L
                val desc = vol.getDescription(context)?.takeIf { it.isNotBlank() }
                    ?: "External Drive #${idx + 1}"
                volumes.add(
                    StorageVolumeSummary(
                        id = "os_vol_${vol.uuid ?: idx}",
                        title = desc,
                        subtitle = "${volDir.absolutePath} • ${vol.state}",
                        rootPath = volDir.absolutePath,
                        totalBytes = total,
                        freeBytes = free,
                        usedBytes = (total - free).coerceAtLeast(0L),
                        isRemovable = vol.isRemovable,
                        isSafBacked = false
                    )
                )
            }
        }

        // 3. Real Android OS System Partition (/system) — Separate hardware partition with distinct StatFs
        val sysDir = systemRoot
        if (sysDir.exists() && sysDir.canRead()) {
            val sysStat = runCatching { StatFs(sysDir.absolutePath) }.getOrNull()
            val sysTotal = sysStat?.totalBytes ?: 0L
            val sysFree = sysStat?.availableBytes ?: 0L
            if (sysTotal > 0L) {
                volumes.add(
                    StorageVolumeSummary(
                        id = "system_partition",
                        title = "System Partition",
                        subtitle = "${sysDir.absolutePath} • Read-Only OS",
                        rootPath = sysDir.absolutePath,
                        totalBytes = sysTotal,
                        freeBytes = sysFree,
                        usedBytes = (sysTotal - sysFree).coerceAtLeast(0L),
                        isRemovable = false,
                        isSafBacked = false,
                        isReadOnly = true
                    )
                )
            }
        }

        // 4. Real Termux ($HOME) Volume if accessible directly, via SAF, or via Termux External Dir
        val termuxStatus = queryTermuxStatus(persistedSafUris)
        if (termuxStatus.isDirectHomeAccessible) {
            val termuxStat = runCatching { StatFs(TermuxStorageBridge.TERMUX_HOME_PATH) }.getOrNull()
            val tTotal = termuxStat?.totalBytes ?: termuxStatus.totalBytes
            val tFree = termuxStat?.availableBytes ?: termuxStatus.freeBytes
            volumes.add(
                StorageVolumeSummary(
                    id = "termux_direct_home",
                    title = "Termux (\$HOME)",
                    subtitle = "${TermuxStorageBridge.TERMUX_HOME_PATH} • Auto-Mounted POSIX",
                    rootPath = TermuxStorageBridge.TERMUX_HOME_PATH,
                    totalBytes = tTotal,
                    freeBytes = tFree,
                    usedBytes = (tTotal - tFree).coerceAtLeast(0L),
                    isRemovable = false,
                    isSafBacked = false
                )
            )
        }
        termuxStatus.grantedTermuxTreeUris.forEachIndexed { index, treeUriStr ->
            val docUriStr = normalizeSafTreeToDocumentUri(treeUriStr)
            val (tTotal, tFree) = querySafStatVfs(Uri.parse(docUriStr))
            volumes.add(
                StorageVolumeSummary(
                    id = "termux_saf_$index",
                    title = if (index == 0) "Termux (\$HOME)" else "Termux Volume #${index + 1}",
                    subtitle = "com.termux.documents • Auto-Mounted SAF",
                    rootPath = docUriStr,
                    totalBytes = tTotal,
                    freeBytes = tFree,
                    usedBytes = (tTotal - tFree).coerceAtLeast(0L),
                    isRemovable = false,
                    isSafBacked = true
                )
            )
        }
        if (termuxStatus.isExternalTermuxDirAccessible && termuxStatus.externalTermuxDirPath != null) {
            val extTermuxPath = termuxStatus.externalTermuxDirPath
            val stat = runCatching { StatFs(extTermuxPath) }.getOrNull()
            val tTotal = stat?.totalBytes ?: 0L
            val tFree = stat?.availableBytes ?: 0L
            volumes.add(
                StorageVolumeSummary(
                    id = "termux_external_dir",
                    title = "Termux Shared Files",
                    subtitle = "$extTermuxPath • Auto-Mounted",
                    rootPath = extTermuxPath,
                    totalBytes = tTotal,
                    freeBytes = tFree,
                    usedBytes = (tTotal - tFree).coerceAtLeast(0L),
                    isRemovable = false,
                    isSafBacked = false
                )
            )
        }

        // 5. All Active OS-Verified Persisted SAF Grants from ContentResolver
        val allSafTreeUris = runCatching {
            context.contentResolver.persistedUriPermissions
                .filter {
                    it.isReadPermission &&
                        it.uri.authority != TermuxStorageBridge.TERMUX_DOCUMENTS_AUTHORITY &&
                        TermuxStorageBridge.isAuthorityAvailable(context, it.uri.authority)
                }
                .map { it.uri.toString() }
                .distinct()
        }.getOrDefault(emptyList())

        allSafTreeUris.forEachIndexed { idx, uriStr ->
            val docUriStr = normalizeSafTreeToDocumentUri(uriStr)
            // Skip if already used as primarySafTree when All Files Access is not granted
            if (!allFilesGranted && docUriStr == primarySafTree) return@forEachIndexed

            val crumbs = TermuxStorageBridge.parseSafBreadcrumbs(docUriStr)
            val label = crumbs.lastOrNull()?.first ?: "Mounted SAF Volume #${idx + 1}"
            val (safTotal, safFree) = querySafStatVfs(Uri.parse(docUriStr))
            volumes.add(
                StorageVolumeSummary(
                    id = "saf_$idx",
                    title = label,
                    subtitle = "${Uri.parse(docUriStr).authority ?: "SAF"} • Mounted Tree",
                    rootPath = docUriStr,
                    totalBytes = safTotal,
                    freeBytes = safFree,
                    usedBytes = (safTotal - safFree).coerceAtLeast(0L),
                    isRemovable = true,
                    isSafBacked = true
                )
            )
        }

        return volumes
    }

    /**
     * Queries real Linux kernel `fstatvfs` metrics on a granted SAF URI descriptor, or `/data`
     * `StatFs` for `com.termux.documents`, falling back to primary shared storage `StatFs`.
     */
    private fun querySafStatVfs(uri: Uri): Pair<Long, Long> {
        if (uri.authority == TermuxStorageBridge.TERMUX_DOCUMENTS_AUTHORITY) {
            val dataStat = runCatching { StatFs(context.filesDir.absolutePath) }.getOrNull()
            if (dataStat != null && dataStat.totalBytes > 0L) {
                return dataStat.totalBytes to dataStat.availableBytes
            }
        } else if (TermuxStorageBridge.hasUriReadPermission(context, uri)) {
            runCatching {
                context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                    val statVfs = Os.fstatvfs(pfd.fileDescriptor)
                    val total = statVfs.f_blocks * statVfs.f_frsize
                    val free = statVfs.f_bavail * statVfs.f_frsize
                    if (total > 0L) return total to free
                }
            }
        }
        val fallback = runCatching { StatFs(primarySharedRoot.absolutePath) }.getOrNull()
        return (fallback?.totalBytes ?: 0L) to (fallback?.availableBytes ?: 0L)
    }

    private fun findPersistedSafTreeForPrimaryRoot(): String? {
        val perms = context.contentResolver.persistedUriPermissions.filter { it.isReadPermission }
        val match = perms.firstOrNull { perm ->
            val uri = perm.uri
            uri.authority == "com.android.externalstorage.documents" &&
                runCatching { DocumentsContract.getTreeDocumentId(uri) }.getOrNull() == "primary:"
        } ?: return null
        return normalizeSafTreeToDocumentUri(match.uri.toString())
    }

    /**
     * Maps a local `/storage/emulated/0/...` path to a granted SAF Document URI if the user
     * has mounted a covering SAF tree in `com.android.externalstorage.documents`.
     */
    private fun findMatchingSafUriForLocalPath(localFile: File): Uri? {
        val sharedPath = primarySharedRoot.absolutePath
        val targetPath = localFile.absolutePath
        if (!targetPath.startsWith(sharedPath)) return null

        val relativeToPrimary = targetPath.removePrefix(sharedPath).trim('/')
        val desiredDocId = if (relativeToPrimary.isEmpty()) "primary:" else "primary:$relativeToPrimary"

        val perms = context.contentResolver.persistedUriPermissions.filter {
            it.isReadPermission && it.uri.authority == "com.android.externalstorage.documents"
        }
        for (perm in perms) {
            val treeId = runCatching { DocumentsContract.getTreeDocumentId(perm.uri) }.getOrNull() ?: continue
            if (desiredDocId == treeId ||
                (treeId == "primary:" && desiredDocId.startsWith("primary:")) ||
                desiredDocId.startsWith("$treeId/")
            ) {
                return DocumentsContract.buildDocumentUriUsingTree(perm.uri, desiredDocId)
            }
        }
        return null
    }

    private fun isSafDocumentUriStructure(uri: Uri): Boolean {
        if (uri.scheme != "content") return false
        val paths = uri.pathSegments
        if (paths.size >= 2 && paths[0] == "document") return true
        if (paths.size >= 4 && paths[0] == "tree" && paths[2] == "document") return true
        return false
    }

    fun normalizeSafTreeToDocumentUri(uriString: String): String {
        val uri = runCatching { Uri.parse(uriString) }.getOrNull() ?: return uriString
        if (isSafDocumentUriStructure(uri)) return uriString
        return runCatching {
            val treeDocId = DocumentsContract.getTreeDocumentId(uri)
            DocumentsContract.buildDocumentUriUsingTree(uri, treeDocId).toString()
        }.getOrDefault(uriString)
    }

    /**
     * Lists real directory contents from POSIX (`File.listFiles()`), SAF (`DocumentsContract`),
     * or real OS `MediaStore` + public directory discovery on `/storage/emulated/0` when Scoped Storage
     * is active and `MANAGE_EXTERNAL_STORAGE` has not yet been toggled.
     */
    suspend fun listDirectory(
        pathOrUri: String,
        showHidden: Boolean
    ): Result<List<FileEntry>> = withContext(Dispatchers.IO) {
        runCatching {
            if (pathOrUri.startsWith("content://")) {
                listSafDirectory(Uri.parse(pathOrUri), showHidden)
            } else {
                val dir = File(pathOrUri)
                if (!dir.exists()) {
                    throw IOException("Directory does not exist: $pathOrUri")
                }

                // 1. Check if direct POSIX listFiles() works
                val posixChildren = runCatching { dir.listFiles() }.getOrNull()
                if (posixChildren != null && (hasAllFilesAccess() || posixChildren.isNotEmpty() || !isInsidePrimaryShared(dir))) {
                    return@runCatching posixChildren
                        .filter { showHidden || (!it.isHidden && !it.name.startsWith(".")) }
                        .map { FormatDetectorAndMetadataEngine.buildFileEntry(it, context) }
                }

                // 2. Check if a persisted SAF tree covers this path
                val matchingSafUri = findMatchingSafUriForLocalPath(dir)
                if (matchingSafUri != null) {
                    val safResult = runCatching { listSafDirectory(matchingSafUri, showHidden) }.getOrNull()
                    if (safResult != null) return@runCatching safResult
                }

                // 3. If inside /storage/emulated/0 and All Files Access isn't granted yet,
                // discover real directories & files via POSIX stat probes + Android MediaStore
                if (isInsidePrimaryShared(dir)) {
                    val discovered = discoverRealSharedStorageEntries(dir, posixChildren, showHidden)
                    return@runCatching discovered
                }

                // 4. Non-shared restricted directory (e.g., /data/data/com.termux without SAF)
                val hint = if (TermuxStorageBridge.isTermuxUri(pathOrUri)) {
                    "Android UID isolation prevents direct POSIX read of '$pathOrUri'. Tap 'Mount Termux (\$HOME)' to access Termux storage via its com.termux.documents SAF provider."
                } else {
                    "Access restricted by Android permissions for '${dir.AbsolutePathOrName()}'. Grant 'All Files Access' or mount via SAF."
                }
                throw IOException(hint)
            }
        }
    }

    private fun File.AbsolutePathOrName(): String = name.ifEmpty { absolutePath }

    private fun isInsidePrimaryShared(file: File): Boolean {
        val sharedRootPath = primarySharedRoot.absolutePath
        return file.absolutePath == sharedRootPath || file.absolutePath.startsWith("$sharedRootPath/")
    }

    /**
     * Discovers 100% real directories and files inside `/storage/emulated/0` (or its subdirectories)
     * using real OS `Environment` directory probes + `MediaStore.Files` queries when Scoped Storage
     * restricts raw `File.listFiles()` before `MANAGE_EXTERNAL_STORAGE` is granted.
     */
    private fun discoverRealSharedStorageEntries(
        dir: File,
        partialPosix: Array<File>?,
        showHidden: Boolean
    ): List<FileEntry> {
        val byPath = linkedMapOf<String, FileEntry>()

        // Include any entries already visible via FUSE
        partialPosix?.forEach { f ->
            if (showHidden || (!f.isHidden && !f.name.startsWith("."))) {
                byPath[f.absolutePath] = FormatDetectorAndMetadataEngine.buildFileEntry(f, context)
            }
        }

        // If listing /storage/emulated/0 root, probe real standard Android public directories on disk
        if (dir.absolutePath == primarySharedRoot.absolutePath) {
            val standardNames = listOf(
                Environment.DIRECTORY_DOWNLOADS,
                Environment.DIRECTORY_DOCUMENTS,
                Environment.DIRECTORY_PICTURES,
                Environment.DIRECTORY_DCIM,
                Environment.DIRECTORY_MUSIC,
                Environment.DIRECTORY_MOVIES,
                Environment.DIRECTORY_ALARMS,
                Environment.DIRECTORY_NOTIFICATIONS,
                Environment.DIRECTORY_PODCASTS,
                Environment.DIRECTORY_RINGTONES,
                "Android",
                "Audiobooks",
                "Recordings"
            ).distinct()

            for (folderName in standardNames) {
                val candidate = File(dir, folderName)
                if (candidate.exists() && !byPath.containsKey(candidate.absolutePath)) {
                    if (showHidden || !candidate.name.startsWith(".")) {
                        byPath[candidate.absolutePath] = FormatDetectorAndMetadataEngine.buildFileEntry(candidate, context)
                    }
                }
            }
        }

        // Query real OS MediaStore.Files for entries directly inside `dir`
        runCatching {
            val volumeUri = MediaStore.Files.getContentUri("external")
            val projection = arrayOf(
                MediaStore.Files.FileColumns.DATA,
                MediaStore.Files.FileColumns.DISPLAY_NAME,
                MediaStore.Files.FileColumns.SIZE,
                MediaStore.Files.FileColumns.DATE_MODIFIED,
                MediaStore.Files.FileColumns.MIME_TYPE
            )
            val parentPrefix = dir.absolutePath.trimEnd('/') + "/"
            val selection = "${MediaStore.Files.FileColumns.DATA} LIKE ?"
            val selectionArgs = arrayOf("$parentPrefix%")

            context.contentResolver.query(volumeUri, projection, selection, selectionArgs, null)?.use { cursor ->
                val dataIdx = cursor.getColumnIndex(MediaStore.Files.FileColumns.DATA)
                while (cursor.moveToNext()) {
                    if (dataIdx < 0) continue
                    val fullPath = cursor.getString(dataIdx) ?: continue
                    if (!fullPath.startsWith(parentPrefix)) continue
                    val remainder = fullPath.removePrefix(parentPrefix)
                    if (remainder.isEmpty()) continue

                    // Direct child (either a file in `dir` or an immediate subfolder of `dir`)
                    val immediateChildName = remainder.substringBefore('/')
                    if (!showHidden && immediateChildName.startsWith(".")) continue
                    val childFile = File(dir, immediateChildName)
                    if (childFile.exists() && !byPath.containsKey(childFile.absolutePath)) {
                        byPath[childFile.absolutePath] = FormatDetectorAndMetadataEngine.buildFileEntry(childFile, context)
                    }
                }
            }
        }

        return byPath.values.toList()
    }

    private fun listSafDirectory(treeOrDocUri: Uri, showHidden: Boolean): List<FileEntry> {
        val resolver = context.contentResolver
        val treeDocId = runCatching { DocumentsContract.getTreeDocumentId(treeOrDocUri) }.getOrNull()
        val parentDocId = runCatching {
            if (isSafDocumentUriStructure(treeOrDocUri)) {
                DocumentsContract.getDocumentId(treeOrDocUri)
            } else {
                DocumentsContract.getTreeDocumentId(treeOrDocUri)
            }
        }.getOrElse { treeDocId ?: throw IOException("Invalid SAF Document URI: $treeOrDocUri") }

        val baseTreeUri = if (treeDocId != null) {
            DocumentsContract.buildTreeDocumentUri(treeOrDocUri.authority, treeDocId)
        } else {
            treeOrDocUri
        }

        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(baseTreeUri, parentDocId)
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
            DocumentsContract.Document.COLUMN_FLAGS
        )
        val results = mutableListOf<FileEntry>()
        val isTermux = treeOrDocUri.authority == TermuxStorageBridge.TERMUX_DOCUMENTS_AUTHORITY

        if (!TermuxStorageBridge.isAuthorityAvailable(context, treeOrDocUri.authority)) {
            throw IOException(
                if (isTermux) {
                    "Termux (com.termux) is not installed on this device. Install Termux to mount /data/data/com.termux/files/home."
                } else {
                    "Storage provider '${treeOrDocUri.authority}' is not available on this device."
                }
            )
        }

        if (!TermuxStorageBridge.hasUriReadPermission(context, baseTreeUri) &&
            !TermuxStorageBridge.hasUriReadPermission(context, treeOrDocUri)
        ) {
            throw IOException(
                if (isTermux) {
                    "Termux SAF permission is not yet granted. Tap 'Mount Termux (\$HOME)' to grant read/write access."
                } else {
                    "SAF permission not granted for $treeOrDocUri."
                }
            )
        }

        val cursor = resolver.query(childrenUri, projection, null, null, null)
            ?: throw IOException(
                if (isTermux) {
                    "Termux DocumentsProvider (com.termux.documents) did not respond or permission is not yet granted. Tap 'Mount Termux (\$HOME)' to grant SAF access."
                } else {
                    "SAF permission revoked or provider unavailable for $treeOrDocUri"
                }
            )

        cursor.use { c ->
            val idIdx = c.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val nameIdx = c.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            val mimeIdx = c.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
            val sizeIdx = c.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_SIZE)
            val modIdx = c.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
            val flagsIdx = c.getColumnIndex(DocumentsContract.Document.COLUMN_FLAGS)

            while (c.moveToNext()) {
                val childDocId = c.getString(idIdx) ?: continue
                val name = c.getString(nameIdx) ?: childDocId.substringAfterLast('/')
                if (!showHidden && name.startsWith(".")) continue
                val mime = c.getString(mimeIdx) ?: "application/octet-stream"
                val isDir = mime == DocumentsContract.Document.MIME_TYPE_DIR
                val size = if (isDir || c.isNull(sizeIdx)) 0L else c.getLong(sizeIdx)
                val lastMod = if (c.isNull(modIdx)) 0L else c.getLong(modIdx)
                val flags = if (flagsIdx >= 0 && !c.isNull(flagsIdx)) c.getInt(flagsIdx) else 0
                val childUri = DocumentsContract.buildDocumentUriUsingTree(baseTreeUri, childDocId)
                val ext = if (isDir) "" else name.substringAfterLast('.', "").lowercase()

                val detectedFormat = FormatDetectorAndMetadataEngine.detectSafUriFormat(
                    context = context,
                    uri = childUri,
                    displayName = name,
                    isDir = isDir
                )

                val writable = (flags and DocumentsContract.Document.FLAG_SUPPORTS_WRITE) != 0 ||
                    (flags and DocumentsContract.Document.FLAG_DIR_SUPPORTS_CREATE) != 0
                val deletable = (flags and DocumentsContract.Document.FLAG_SUPPORTS_DELETE) != 0
                val permPrefix = if (isTermux) "termux:" else "saf:"
                val perms = buildString {
                    append(permPrefix)
                    append(if (isDir) "d" else "-")
                    append("r")
                    append(if (writable) "w" else "-")
                    append(if (deletable) "d" else "-")
                }

                results.add(
                    FileEntry(
                        path = childUri.toString(),
                        name = name,
                        extension = ext,
                        mimeType = mime,
                        detectedFormat = detectedFormat,
                        sizeBytes = size,
                        lastModified = lastMod,
                        createdDate = lastMod.takeIf { it > 0L },
                        isDirectory = isDir,
                        isHidden = name.startsWith("."),
                        permissions = perms,
                        isSafUri = true,
                        safUriString = childUri.toString()
                    )
                )
            }
        }
        return results
    }

    suspend fun createDirectory(parentPathOrUri: String, folderName: String): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                val sanitized = folderName.trim().replace("/", "").replace("\\", "")
                require(sanitized.isNotEmpty()) { "Folder name cannot be blank." }

                if (parentPathOrUri.startsWith("content://")) {
                    val parentUri = resolveSafDocumentUri(Uri.parse(parentPathOrUri))
                    val createdUri = DocumentsContract.createDocument(
                        context.contentResolver,
                        parentUri,
                        DocumentsContract.Document.MIME_TYPE_DIR,
                        sanitized
                    ) ?: throw IOException("SAF provider rejected directory creation for '$sanitized'.")
                    createdUri.toString()
                } else {
                    val parentDir = File(parentPathOrUri)
                    val target = File(parentDir, sanitized)
                    if (target.exists()) throw IOException("'$sanitized' already exists.")
                    if (target.mkdirs()) {
                        return@runCatching target.absolutePath
                    }
                    // Fallback to persisted SAF tree if available
                    val safParent = findMatchingSafUriForLocalPath(parentDir)
                    if (safParent != null) {
                        val createdUri = DocumentsContract.createDocument(
                            context.contentResolver,
                            safParent,
                            DocumentsContract.Document.MIME_TYPE_DIR,
                            sanitized
                        ) ?: throw IOException("Failed to create folder '$sanitized' via SAF.")
                        return@runCatching createdUri.toString()
                    }
                    throw IOException(
                        "Failed to create folder '$sanitized' in '${parentDir.name}'. Grant 'All Files Access' for full write access on Internal Shared Storage."
                    )
                }
            }
        }

    suspend fun createTextFile(parentPathOrUri: String, fileName: String, content: String): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                val sanitized = fileName.trim().replace("/", "").replace("\\", "")
                require(sanitized.isNotEmpty()) { "Filename cannot be blank." }
                val finalName = if (sanitized.contains('.') || sanitized.startsWith(".")) sanitized else "$sanitized.txt"
                val ext = finalName.substringAfterLast('.', "").lowercase()
                val mime = FormatDetectorAndMetadataEngine.resolveMimeType(
                    isDir = false,
                    ext = ext,
                    detectedFormat = DetectedFileFormat.UTF8_TEXT
                )

                if (parentPathOrUri.startsWith("content://")) {
                    val parentUri = resolveSafDocumentUri(Uri.parse(parentPathOrUri))
                    val newDocUri = DocumentsContract.createDocument(
                        context.contentResolver,
                        parentUri,
                        mime,
                        finalName
                    ) ?: throw IOException("Failed to create '$finalName' via SAF provider.")
                    context.contentResolver.openOutputStream(newDocUri, "wt")?.use { out ->
                        out.write(content.toByteArray(Charsets.UTF_8))
                    } ?: throw IOException("Failed to write content to '$finalName'.")
                    newDocUri.toString()
                } else {
                    val parentDir = File(parentPathOrUri)
                    val target = SortingAndGroupingEngine.resolveNonConflictingFile(parentDir, finalName)
                    val directSuccess = runCatching {
                        target.writeText(content)
                        true
                    }.getOrDefault(false)

                    if (directSuccess) {
                        return@runCatching target.absolutePath
                    }

                    // Try SAF tree if mounted
                    val safParent = findMatchingSafUriForLocalPath(parentDir)
                    if (safParent != null) {
                        val newDocUri = DocumentsContract.createDocument(
                            context.contentResolver,
                            safParent,
                            mime,
                            finalName
                        ) ?: throw IOException("Failed to create '$finalName' via SAF.")
                        context.contentResolver.openOutputStream(newDocUri, "wt")?.use { out ->
                            out.write(content.toByteArray(Charsets.UTF_8))
                        }
                        return@runCatching newDocUri.toString()
                    }

                    // Try MediaStore if inside /storage/emulated/0 public directories on Android 10+
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && isInsidePrimaryShared(parentDir)) {
                        val relPath = parentDir.absolutePath
                            .removePrefix(primarySharedRoot.absolutePath)
                            .trim('/')
                        if (relPath.isNotEmpty()) {
                            val values = ContentValues().apply {
                                put(MediaStore.MediaColumns.DISPLAY_NAME, finalName)
                                put(MediaStore.MediaColumns.MIME_TYPE, mime)
                                put(MediaStore.MediaColumns.RELATIVE_PATH, "$relPath/")
                            }
                            val insertedUri = context.contentResolver.insert(
                                MediaStore.Files.getContentUri("external"),
                                values
                            )
                            if (insertedUri != null) {
                                context.contentResolver.openOutputStream(insertedUri, "wt")?.use { out ->
                                    out.write(content.toByteArray(Charsets.UTF_8))
                                }
                                return@runCatching target.absolutePath
                            }
                        }
                    }

                    throw IOException(
                        "Cannot write '$finalName' to '${parentDir.name}'. Grant 'All Files Access' to write directly to Internal Shared Storage root."
                    )
                }
            }
        }

    suspend fun readTextFileContent(pathOrUri: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            if (pathOrUri.startsWith("content://")) {
                val uri = Uri.parse(pathOrUri)
                context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { reader ->
                    val buf = CharArray(64 * 1024)
                    val read = reader.read(buf)
                    if (read > 0) String(buf, 0, read) else ""
                } ?: throw IOException("Unable to open stream for reading.")
            } else {
                File(pathOrUri).bufferedReader(Charsets.UTF_8).use { reader ->
                    val buf = CharArray(64 * 1024)
                    val read = reader.read(buf)
                    if (read > 0) String(buf, 0, read) else ""
                }
            }
        }
    }

    suspend fun writeTextFileContent(pathOrUri: String, updatedContent: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                if (pathOrUri.startsWith("content://")) {
                    val uri = Uri.parse(pathOrUri)
                    context.contentResolver.openOutputStream(uri, "wt")?.use { out ->
                        out.write(updatedContent.toByteArray(Charsets.UTF_8))
                    } ?: throw IOException("Unable to open SAF/Termux stream for writing.")
                } else {
                    File(pathOrUri).writeText(updatedContent, Charsets.UTF_8)
                }
            }
        }

    suspend fun renameSingleEntry(sourcePathOrUri: String, newName: String): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                val sanitized = newName.trim().replace("/", "").replace("\\", "")
                require(sanitized.isNotEmpty()) { "New name cannot be empty." }

                if (sourcePathOrUri.startsWith("content://")) {
                    val docUri = resolveSafDocumentUri(Uri.parse(sourcePathOrUri))
                    val renamedUri = DocumentsContract.renameDocument(
                        context.contentResolver,
                        docUri,
                        sanitized
                    ) ?: throw IOException("Provider does not support rename for this item.")
                    renamedUri.toString()
                } else {
                    val src = File(sourcePathOrUri)
                    if (!src.exists()) throw IOException("Source item no longer exists.")
                    val dest = File(src.parentFile, sanitized)
                    if (dest.exists() && dest.absolutePath != src.absolutePath) {
                        throw IOException("A file named '$sanitized' already exists.")
                    }
                    if (!src.renameTo(dest)) {
                        throw IOException("Rename failed for '${src.name}'.")
                    }
                    dest.absolutePath
                }
            }
        }

    suspend fun executeBatchRename(
        files: List<FileEntry>,
        config: BatchRenameConfig,
        existingSiblingNames: Set<String>,
        awaitIfPaused: suspend () -> Unit = {},
        onProgress: (Int, Int, Int, Int, String) -> Unit = { _, _, _, _, _ -> }
    ): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            if (files.isEmpty()) return@runCatching 0
            val previews = SortingAndGroupingEngine.generateBatchRenamePreview(
                files = files,
                config = config,
                existingSiblingNames = existingSiblingNames
            )
            val conflicting = previews.firstOrNull { it.hasConflict }
            if (conflicting != null) {
                throw IOException("Batch rename aborted due to conflict: ${conflicting.conflictReason}")
            }

            var renamedCount = 0
            var skippedCount = 0
            var errorCount = 0
            val total = previews.size.coerceAtLeast(1)

            previews.forEachIndexed { index, item ->
                coroutineContext.ensureActive()
                awaitIfPaused()
                if (item.originalFile.name != item.proposedName) {
                    val res = renameSingleEntry(item.originalFile.path, item.proposedName)
                    if (res.isSuccess) {
                        renamedCount++
                    } else {
                        errorCount++
                    }
                } else {
                    skippedCount++
                }
                onProgress(index + 1, total, skippedCount, errorCount, item.proposedName)
            }
            renamedCount
        }
    }

    /**
     * Inspects [destinationDirPath] for any existing entries with the same name as [sourceEntries]
     * (excluding self-transfers where source path equals destination path).
     */
    suspend fun findTransferConflicts(
        sourceEntries: List<FileEntry>,
        destinationDirPath: String
    ): List<FileConflictPair> = withContext(Dispatchers.IO) {
        val destItems = listDirectory(destinationDirPath, showHidden = true)
            .getOrDefault(emptyList())
            .associateBy { it.name }

        sourceEntries.mapNotNull { src ->
            val existing = destItems[src.name]
            if (existing != null && existing.path != src.path) {
                FileConflictPair(sourceEntry = src, destinationEntry = existing)
            } else {
                null
            }
        }
    }

    /**
     * Copies or Moves [sourceEntries] into [destinationDirPath] across any combination of
     * POSIX files (`file://`) and SAF / Termux URIs (`content://`).
     * Enforces destination verification before deleting source files during move operations.
     */
    suspend fun transferFiles(
        sourceEntries: List<FileEntry>,
        destinationDirPath: String,
        isMove: Boolean,
        conflictPolicy: ConflictResolution,
        perSourceResolution: Map<String, Pair<FileConflictDecision, String?>> = emptyMap(),
        awaitIfPaused: suspend () -> Unit = {},
        onProgress: (Float, Int, Int, Long, Long, String) -> Unit
    ): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val totalBytes = sourceEntries.sumOf { it.sizeBytes.coerceAtLeast(1L) }.coerceAtLeast(1L)
            var transferredBytes = 0L
            var completedItems = 0

            sourceEntries.forEachIndexed { index, entry ->
                coroutineContext.ensureActive()
                awaitIfPaused()
                val explicitDecision = perSourceResolution[entry.path]
                if (explicitDecision?.first == FileConflictDecision.SKIP ||
                    explicitDecision?.first == FileConflictDecision.CANCEL
                ) {
                    onProgress(
                        (transferredBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f),
                        index + 1,
                        sourceEntries.size,
                        transferredBytes,
                        totalBytes,
                        entry.name
                    )
                    return@forEachIndexed
                }

                transferredBytes = transferSingleEntryCrossProvider(
                    entry = entry,
                    destinationDirPath = destinationDirPath,
                    isMove = isMove,
                    conflictPolicy = conflictPolicy,
                    explicitDecision = explicitDecision,
                    currentTransferred = transferredBytes,
                    totalBytes = totalBytes,
                    itemIndex = index + 1,
                    totalItems = sourceEntries.size,
                    awaitIfPaused = awaitIfPaused,
                    onProgress = onProgress
                )
                completedItems++
            }
            completedItems
        }
    }

    private suspend fun transferSingleEntryCrossProvider(
        entry: FileEntry,
        destinationDirPath: String,
        isMove: Boolean,
        conflictPolicy: ConflictResolution,
        explicitDecision: Pair<FileConflictDecision, String?>? = null,
        currentTransferred: Long,
        totalBytes: Long,
        itemIndex: Int,
        totalItems: Int,
        awaitIfPaused: suspend () -> Unit,
        onProgress: (Float, Int, Int, Long, Long, String) -> Unit
    ): Long {
        coroutineContext.ensureActive()
        awaitIfPaused()
        val destIsSaf = destinationDirPath.startsWith("content://")
        val targetEntryName = if (explicitDecision?.first == FileConflictDecision.CUSTOM_RENAME &&
            !explicitDecision.second.isNullOrBlank()
        ) {
            explicitDecision.second!!.trim()
        } else {
            entry.name
        }

        if (entry.isDirectory) {
            val createdSubDirPath = createDirectory(destinationDirPath, targetEntryName)
                .getOrElse {
                    if (destIsSaf) throw it
                    File(destinationDirPath, targetEntryName).apply { mkdirs() }.absolutePath
                }
            val children = listDirectory(entry.path, showHidden = true).getOrDefault(emptyList())
            var accum = currentTransferred
            children.forEach { child ->
                accum = transferSingleEntryCrossProvider(
                    entry = child,
                    destinationDirPath = createdSubDirPath,
                    isMove = isMove,
                    conflictPolicy = conflictPolicy,
                    explicitDecision = null,
                    currentTransferred = accum,
                    totalBytes = totalBytes,
                    itemIndex = itemIndex,
                    totalItems = totalItems,
                    awaitIfPaused = awaitIfPaused,
                    onProgress = onProgress
                )
            }
            if (isMove) {
                deleteOrRecycleFiles(listOf(entry.path), useRecycleBin = false)
            }
            return accum
        }

        val buffer = ByteArray(64 * 1024)
        var transferred = currentTransferred
        var bytesWrittenForThisFile = 0L

        val inputStream = if (entry.path.startsWith("content://")) {
            context.contentResolver.openInputStream(Uri.parse(entry.path))
                ?: throw IOException("Cannot read SAF source: ${entry.name}")
        } else {
            FileInputStream(File(entry.path))
        }

        var destinationVerified = false

        if (destIsSaf) {
            val destParentUri = resolveSafDocumentUri(Uri.parse(destinationDirPath))
            val newDocUri = DocumentsContract.createDocument(
                context.contentResolver,
                destParentUri,
                entry.mimeType,
                targetEntryName
            ) ?: throw IOException("Cannot create '$targetEntryName' in SAF destination.")

            BufferedInputStream(inputStream).use { bis ->
                context.contentResolver.openOutputStream(newDocUri, "w")?.use { rawOut ->
                    BufferedOutputStream(rawOut).use { bos ->
                        var read: Int
                        while (bis.read(buffer).also { read = it } != -1) {
                            coroutineContext.ensureActive()
                            awaitIfPaused()
                            bos.write(buffer, 0, read)
                            transferred += read
                            bytesWrittenForThisFile += read
                            onProgress(
                                (transferred.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f),
                                itemIndex,
                                totalItems,
                                transferred,
                                totalBytes,
                                targetEntryName
                            )
                        }
                        bos.flush()
                    }
                } ?: throw IOException("Cannot open output stream for '$targetEntryName'.")
            }
            destinationVerified = bytesWrittenForThisFile >= entry.sizeBytes.coerceAtLeast(0L)
        } else {
            val destRoot = File(destinationDirPath).apply { mkdirs() }
            var targetFile = File(destRoot, targetEntryName)
            if (targetFile.exists()) {
                when (explicitDecision?.first) {
                    FileConflictDecision.SKIP, FileConflictDecision.CANCEL -> {
                        runCatching { inputStream.close() }
                        return transferred
                    }
                    FileConflictDecision.KEEP_BOTH -> {
                        targetFile = SortingAndGroupingEngine.resolveNonConflictingFile(destRoot, targetEntryName)
                    }
                    FileConflictDecision.REPLACE -> {
                        if (targetFile.absolutePath == entry.path) {
                            runCatching { inputStream.close() }
                            return transferred
                        }
                        targetFile.delete()
                    }
                    FileConflictDecision.CUSTOM_RENAME -> {
                        if (targetFile.exists()) {
                            targetFile = SortingAndGroupingEngine.resolveNonConflictingFile(destRoot, targetEntryName)
                        }
                    }
                    null -> {
                        when (conflictPolicy) {
                            ConflictResolution.SKIP -> {
                                runCatching { inputStream.close() }
                                return transferred
                            }
                            ConflictResolution.RENAME, ConflictResolution.ASK -> {
                                targetFile = SortingAndGroupingEngine.resolveNonConflictingFile(destRoot, targetEntryName)
                            }
                            ConflictResolution.REPLACE -> {
                                if (targetFile.absolutePath == entry.path) {
                                    runCatching { inputStream.close() }
                                    return transferred
                                }
                                targetFile.delete()
                            }
                        }
                    }
                }
            }
            BufferedInputStream(inputStream).use { bis ->
                BufferedOutputStream(FileOutputStream(targetFile)).use { bos ->
                    var read: Int
                    while (bis.read(buffer).also { read = it } != -1) {
                        coroutineContext.ensureActive()
                        awaitIfPaused()
                        bos.write(buffer, 0, read)
                        transferred += read
                        bytesWrittenForThisFile += read
                        onProgress(
                            (transferred.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f),
                            itemIndex,
                            totalItems,
                            transferred,
                            totalBytes,
                            targetFile.name
                        )
                    }
                    bos.flush()
                }
            }
            destinationVerified = targetFile.exists() && targetFile.length() == bytesWrittenForThisFile
        }

        if (isMove) {
            if (!destinationVerified) {
                throw IOException("Move safety check failed: destination verification did not match source '${entry.name}'. Source preserved.")
            }
            deleteOrRecycleFiles(listOf(entry.path), useRecycleBin = false)
        }
        return transferred
    }

    private fun resolveSafDocumentUri(uri: Uri): Uri {
        return if (isSafDocumentUriStructure(uri)) {
            uri
        } else {
            val treeDocId = DocumentsContract.getTreeDocumentId(uri)
            DocumentsContract.buildDocumentUriUsingTree(uri, treeDocId)
        }
    }

    /**
     * Imports external content URIs (selected via Android Photo/Document Picker) into [destinationDirPath]
     * with real byte-stream progress reporting and conflict resolution.
     */
    suspend fun importExternalUrisToDirectory(
        uris: List<Uri>,
        destinationDirPath: String,
        conflictPolicy: ConflictResolution,
        awaitIfPaused: suspend () -> Unit = {},
        onProgress: (Float, Int, Int, Long, Long, String) -> Unit
    ): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            if (uris.isEmpty()) return@runCatching 0
            val resolver = context.contentResolver

            data class ImportMeta(val uri: Uri, val displayName: String, val mimeType: String, val sizeBytes: Long)

            val metas = uris.mapIndexed { idx, uri ->
                var name = "imported_file_${idx + 1}"
                var size = 0L
                runCatching {
                    resolver.query(
                        uri,
                        arrayOf(
                            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                            DocumentsContract.Document.COLUMN_SIZE
                        ),
                        null,
                        null,
                        null
                    )?.use { c ->
                        if (c.moveToFirst()) {
                            val nameIdx = c.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                            val sizeIdx = c.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE)
                            if (nameIdx >= 0 && !c.isNull(nameIdx)) {
                                name = c.getString(nameIdx).ifBlank { name }
                            }
                            if (sizeIdx >= 0 && !c.isNull(sizeIdx)) {
                                size = c.getLong(sizeIdx).coerceAtLeast(0L)
                            }
                        }
                    }
                }
                val mime = resolver.getType(uri) ?: "application/octet-stream"
                ImportMeta(uri, name, mime, size)
            }

            val totalBytes = metas.sumOf { it.sizeBytes.coerceAtLeast(1L) }.coerceAtLeast(1L)
            var transferredBytes = 0L
            var importedCount = 0
            val destIsSaf = destinationDirPath.startsWith("content://")
            val buffer = ByteArray(64 * 1024)

            metas.forEachIndexed { index, meta ->
                coroutineContext.ensureActive()
                awaitIfPaused()

                val input = resolver.openInputStream(meta.uri)
                    ?: throw IOException("Cannot open imported URI for '${meta.displayName}'")

                if (destIsSaf) {
                    val destParentUri = resolveSafDocumentUri(Uri.parse(destinationDirPath))
                    val newDocUri = DocumentsContract.createDocument(
                        resolver,
                        destParentUri,
                        meta.mimeType,
                        meta.displayName
                    ) ?: throw IOException("Cannot create '${meta.displayName}' in SAF destination.")

                    BufferedInputStream(input).use { bis ->
                        resolver.openOutputStream(newDocUri, "w")?.use { rawOut ->
                            BufferedOutputStream(rawOut).use { bos ->
                                var read: Int
                                while (bis.read(buffer).also { read = it } != -1) {
                                    coroutineContext.ensureActive()
                                    awaitIfPaused()
                                    bos.write(buffer, 0, read)
                                    transferredBytes += read
                                    onProgress(
                                        (transferredBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f),
                                        index + 1,
                                        metas.size,
                                        transferredBytes,
                                        totalBytes,
                                        meta.displayName
                                    )
                                }
                                bos.flush()
                            }
                        } ?: throw IOException("Cannot open output stream for '${meta.displayName}'.")
                    }
                } else {
                    val destRoot = File(destinationDirPath).apply { mkdirs() }
                    var targetFile = File(destRoot, meta.displayName)
                    if (targetFile.exists()) {
                        when (conflictPolicy) {
                            ConflictResolution.SKIP -> return@forEachIndexed
                            ConflictResolution.RENAME, ConflictResolution.ASK -> {
                                targetFile = SortingAndGroupingEngine.resolveNonConflictingFile(destRoot, meta.displayName)
                            }
                            ConflictResolution.REPLACE -> {
                                targetFile.delete()
                            }
                        }
                    }
                    BufferedInputStream(input).use { bis ->
                        BufferedOutputStream(FileOutputStream(targetFile)).use { bos ->
                            var read: Int
                            while (bis.read(buffer).also { read = it } != -1) {
                                coroutineContext.ensureActive()
                                awaitIfPaused()
                                bos.write(buffer, 0, read)
                                transferredBytes += read
                                onProgress(
                                    (transferredBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f),
                                    index + 1,
                                    metas.size,
                                    transferredBytes,
                                    totalBytes,
                                    meta.displayName
                                )
                            }
                            bos.flush()
                        }
                    }
                }
                importedCount++
            }
            importedCount
        }
    }

    suspend fun deleteOrRecycleFiles(
        pathsOrUris: List<String>,
        useRecycleBin: Boolean,
        awaitIfPaused: suspend () -> Unit = {},
        onProgress: (Int, Int, String) -> Unit = { _, _, _ -> }
    ): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            var count = 0
            val total = pathsOrUris.size.coerceAtLeast(1)
            pathsOrUris.forEachIndexed { index, path ->
                coroutineContext.ensureActive()
                awaitIfPaused()
                val displayName = path.substringAfterLast('/')
                if (path.startsWith("content://")) {
                    val docUri = resolveSafDocumentUri(Uri.parse(path))
                    if (DocumentsContract.deleteDocument(context.contentResolver, docUri)) {
                        count++
                    }
                } else {
                    val file = File(path)
                    if (file.exists()) {
                        val isAlreadyInRecycleBin = file.canonicalPath.startsWith(recycleBinDir.canonicalPath)
                        if (useRecycleBin && !isAlreadyInRecycleBin) {
                            val dest = SortingAndGroupingEngine.resolveNonConflictingFile(recycleBinDir, file.name)
                            if (file.renameTo(dest)) {
                                count++
                            } else {
                                file.copyRecursively(dest, overwrite = true)
                                if (dest.exists() && file.length() == dest.length() && file.deleteRecursively()) count++
                            }
                        } else {
                            if (file.deleteRecursively()) count++
                        }
                    }
                }
                onProgress(index + 1, total, displayName)
            }
            count
        }
    }

    suspend fun calculateFolderIntelligence(
        folderPathOrUri: String,
        includeRecursive: Boolean,
        awaitIfPaused: suspend () -> Unit = {},
        onProgress: (Int, Int, Long, String) -> Unit = { _, _, _, _ -> }
    ): FolderIntelligenceResult = withContext(Dispatchers.IO) {
        val directList = listDirectory(folderPathOrUri, showHidden = true).getOrNull()
            ?: return@withContext FolderIntelligenceResult(path = folderPathOrUri, isComplete = true)

        val directFolders = directList.count { it.isDirectory }
        val directFiles = directList.size - directFolders
        val maxDirectMod = directList.maxOfOrNull { it.lastModified } ?: 0L

        if (!includeRecursive) {
            return@withContext FolderIntelligenceResult(
                path = folderPathOrUri,
                directFileCount = directFiles,
                directFolderCount = directFolders,
                directTotalCount = directList.size,
                lastModifiedTimestamp = maxDirectMod,
                isCalculatingRecursive = false,
                isComplete = false
            )
        }

        var recFiles = 0
        var recFolders = 0
        var recBytes = 0L
        var maxMod = maxDirectMod

        suspend fun walkRecursive(currentPath: String, depth: Int) {
            if (depth > 20) return
            coroutineContext.ensureActive()
            awaitIfPaused()
            val children = listDirectory(currentPath, showHidden = true).getOrDefault(emptyList())
            for (child in children) {
                coroutineContext.ensureActive()
                awaitIfPaused()
                if (child.lastModified > maxMod) maxMod = child.lastModified
                if (child.isDirectory) {
                    recFolders++
                    onProgress(recFiles, recFolders, recBytes, child.name)
                    walkRecursive(child.path, depth + 1)
                } else {
                    recFiles++
                    recBytes += child.sizeBytes
                    if (recFiles % 12 == 0) {
                        onProgress(recFiles, recFolders, recBytes, child.name)
                    }
                }
            }
        }

        walkRecursive(folderPathOrUri, 0)
        onProgress(recFiles, recFolders, recBytes, "Completed")

        FolderIntelligenceResult(
            path = folderPathOrUri,
            directFileCount = directFiles,
            directFolderCount = directFolders,
            directTotalCount = directList.size,
            recursiveFileCount = recFiles,
            recursiveFolderCount = recFolders,
            recursiveTotalBytes = recBytes,
            lastModifiedTimestamp = maxMod,
            isCalculatingRecursive = false,
            isComplete = true
        )
    }

    /**
     * Progressive, cancellable search across both POSIX and SAF / Termux directory trees.
     */
    fun searchDirectoryProgressively(
        rootPathOrUri: String,
        query: String,
        categoryFilter: FileCategory?,
        showHidden: Boolean
    ): Flow<List<FileEntry>> = flow {
        val cleanQuery = query.trim().lowercase()
        val accumulated = mutableListOf<FileEntry>()

        suspend fun searchRecursive(currentPath: String, depth: Int) {
            if (depth > 16) return
            coroutineContext.ensureActive()
            val items = listDirectory(currentPath, showHidden).getOrDefault(emptyList())
            for (entry in items) {
                coroutineContext.ensureActive()
                val nameMatches = cleanQuery.isEmpty() ||
                    entry.name.lowercase().contains(cleanQuery) ||
                    entry.extension.lowercase() == cleanQuery

                val categoryMatches = categoryFilter == null || entry.detectedFormat.category == categoryFilter
                if (nameMatches && categoryMatches) {
                    accumulated.add(entry)
                    if (accumulated.size % 8 == 0) {
                        emit(accumulated.toList())
                    }
                }
                if (entry.isDirectory) {
                    searchRecursive(entry.path, depth + 1)
                }
            }
        }

        searchRecursive(rootPathOrUri, 0)
        emit(accumulated.toList())
    }.flowOn(Dispatchers.IO)

    suspend fun compareDirectories(leftPath: String, rightPath: String): FolderComparisonResult =
        withContext(Dispatchers.IO) {
            val leftFiles = listDirectory(leftPath, showHidden = false).getOrDefault(emptyList())
                .associateBy { it.name }
            val rightFiles = listDirectory(rightPath, showHidden = false).getOrDefault(emptyList())
                .associateBy { it.name }

            val onlyLeft = mutableListOf<FileEntry>()
            val onlyRight = mutableListOf<FileEntry>()
            val modifiedDiffs = mutableListOf<Pair<FileEntry, FileEntry>>()
            val identical = mutableListOf<FileEntry>()

            leftFiles.forEach { (name, lEntry) ->
                val rEntry = rightFiles[name]
                if (rEntry == null) {
                    onlyLeft.add(lEntry)
                } else if (lEntry.isDirectory != rEntry.isDirectory || lEntry.sizeBytes != rEntry.sizeBytes) {
                    modifiedDiffs.add(lEntry to rEntry)
                } else {
                    identical.add(lEntry)
                }
            }
            rightFiles.forEach { (name, rEntry) ->
                if (!leftFiles.containsKey(name)) {
                    onlyRight.add(rEntry)
                }
            }

            FolderComparisonResult(
                leftPath = leftPath,
                rightPath = rightPath,
                onlyInLeft = onlyLeft,
                onlyInRight = onlyRight,
                modifiedDifferences = modifiedDiffs,
                identicalFiles = identical
            )
        }

    suspend fun analyzeStorageAndDuplicates(
        rootPathOrUri: String,
        awaitIfPaused: suspend () -> Unit = {},
        onScanProgress: (
            indeterminate: Boolean,
            fraction: Float,
            processedItems: Int,
            totalItems: Int,
            processedBytes: Long,
            totalBytes: Long,
            currentItem: String
        ) -> Unit = { _, _, _, _, _, _, _ -> }
    ): StorageAnalysisReport = withContext(Dispatchers.IO) {
        val categoryMap = mutableMapOf<FileCategory, Long>()
        val allFiles = mutableListOf<FileEntry>()
        var scannedFolders = 0
        var restrictedFolders = 0
        var accumulatedBytes = 0L

        suspend fun collectRecursive(currentPath: String, depth: Int) {
            if (depth > 16) return
            coroutineContext.ensureActive()
            awaitIfPaused()
            val dirResult = listDirectory(currentPath, showHidden = false)
            if (dirResult.isFailure) {
                restrictedFolders++
                return
            }
            val items = dirResult.getOrDefault(emptyList())
            for (entry in items) {
                coroutineContext.ensureActive()
                awaitIfPaused()
                if (entry.name == ".Trash" || entry.name == ".RecycleBin") continue
                if (entry.isDirectory) {
                    scannedFolders++
                    onScanProgress(
                        true,
                        0f,
                        allFiles.size,
                        allFiles.size + 1,
                        accumulatedBytes,
                        accumulatedBytes,
                        "Scanning folder: ${entry.name}"
                    )
                    collectRecursive(entry.path, depth + 1)
                } else {
                    allFiles.add(entry)
                    accumulatedBytes += entry.sizeBytes
                    val cat = entry.detectedFormat.category
                    categoryMap[cat] = (categoryMap[cat] ?: 0L) + entry.sizeBytes
                    if (allFiles.size % 15 == 0) {
                        onScanProgress(
                            true,
                            0f,
                            allFiles.size,
                            allFiles.size + 1,
                            accumulatedBytes,
                            accumulatedBytes,
                            entry.name
                        )
                    }
                }
            }
        }

        collectRecursive(rootPathOrUri, 0)

        val totalBytes = allFiles.sumOf { it.sizeBytes }
        val largestFiles = allFiles.sortedByDescending { it.sizeBytes }.take(15)

        val candidatesBySize = allFiles.filter { it.sizeBytes > 0L }.groupBy { it.sizeBytes }
            .filterValues { it.size > 1 }

        val candidateFiles = candidatesBySize.values.flatten()
        val totalCandidateCount = candidateFiles.size.coerceAtLeast(1)
        val totalCandidateBytes = candidateFiles.sumOf { it.sizeBytes }.coerceAtLeast(1L)
        var hashedCandidateCount = 0
        var hashedCandidateBytes = 0L

        val duplicateGroups = mutableListOf<DuplicateFileGroup>()
        candidatesBySize.forEach { (size, sameSizeEntries) ->
            coroutineContext.ensureActive()
            awaitIfPaused()
            val byHash = mutableMapOf<String, MutableList<FileEntry>>()
            sameSizeEntries.forEach { entry ->
                coroutineContext.ensureActive()
                awaitIfPaused()
                val frac = (hashedCandidateBytes.toFloat() / totalCandidateBytes.toFloat()).coerceIn(0f, 0.99f)
                onScanProgress(
                    false,
                    frac,
                    hashedCandidateCount,
                    totalCandidateCount,
                    hashedCandidateBytes,
                    totalCandidateBytes,
                    "SHA-256: ${entry.name}"
                )
                val hash = FormatDetectorAndMetadataEngine
                    .computeSha256ForPathOrUri(context, entry.path)
                    .getOrNull()
                if (hash != null) {
                    byHash.getOrPut(hash) { mutableListOf() }.add(entry)
                }
                hashedCandidateCount++
                hashedCandidateBytes += entry.sizeBytes
            }
            byHash.filterValues { it.size > 1 }.forEach { (sha256, dupList) ->
                duplicateGroups.add(
                    DuplicateFileGroup(
                        sha256 = sha256,
                        sizeBytes = size,
                        files = dupList
                    )
                )
            }
        }

        onScanProgress(
            false,
            1f,
            allFiles.size,
            allFiles.size.coerceAtLeast(1),
            totalBytes,
            totalBytes,
            "Scan complete"
        )

        StorageAnalysisReport(
            totalWorkspaceBytes = totalBytes,
            categoryBreakdown = categoryMap,
            largestFiles = largestFiles,
            duplicateGroups = duplicateGroups.sortedByDescending { it.wastedBytes },
            scannedFileCount = allFiles.size,
            scannedFolderCount = scannedFolders,
            restrictedFolderCount = restrictedFolders,
            isComplete = true
        )
    }
}

