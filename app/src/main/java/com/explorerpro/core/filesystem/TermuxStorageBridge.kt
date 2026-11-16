package com.explorerpro.core.filesystem

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Process
import android.os.StatFs
import android.provider.DocumentsContract
import com.explorerpro.domain.model.TermuxIntegrationStatus
import java.io.File

/**
 * Real Android integration and auto-mount bridge for Termux (`com.termux`) and its
 * `TermuxDocumentsProvider` (`com.termux.documents`).
 */
object TermuxStorageBridge {

    const val TERMUX_PACKAGE_NAME = "com.termux"
    const val TERMUX_DOCUMENTS_AUTHORITY = "com.termux.documents"
    const val TERMUX_HOME_PATH = "/data/data/com.termux/files/home"
    const val TERMUX_USER0_HOME_PATH = "/data/user/0/com.termux/files/home"
    const val TERMUX_USR_PATH = "/data/data/com.termux/files/usr"

    /**
     * Checks via [PackageManager] whether a given ContentProvider [authority] is installed and
     * registered on the device without invoking `ActivityThread.acquireProvider` (preventing
     * `E/ActivityThread: Failed to find provider info` logs).
     */
    fun isAuthorityAvailable(context: Context, authority: String?): Boolean {
        if (authority.isNullOrBlank()) return false
        val pm = context.packageManager

        val resolved = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.resolveContentProvider(authority, PackageManager.ComponentInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.resolveContentProvider(authority, 0)
            }
        }.getOrNull()
        if (resolved != null) return true

        return runCatching {
            val intent = Intent(DocumentsContract.PROVIDER_INTERFACE)
            val providers = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.queryIntentContentProviders(intent, PackageManager.ResolveInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.queryIntentContentProviders(intent, 0)
            }
            providers.any { it.providerInfo?.authority == authority }
        }.getOrDefault(false)
    }

    /**
     * Verifies both that the URI's authority exists in [PackageManager] and that our app holds a valid
     * OS read permission grant (`persistedUriPermissions` or runtime URI permission) before any
     * `ContentResolver` IPC call is made.
     */
    fun hasUriReadPermission(context: Context, uri: Uri): Boolean {
        val authority = uri.authority ?: return false
        if (!isAuthorityAvailable(context, authority)) return false

        val uriStr = uri.toString()
        val treePrefix = uriStr.substringBefore("/document/")
        val hasPersistedGrant = runCatching {
            context.contentResolver.persistedUriPermissions.any { perm ->
                perm.isReadPermission &&
                    perm.uri.authority == authority &&
                    (uriStr.startsWith(perm.uri.toString()) ||
                        perm.uri.toString().startsWith(treePrefix))
            }
        }.getOrDefault(false)

        if (hasPersistedGrant) return true

        val runtimeGrant = runCatching {
            context.checkUriPermission(
                uri,
                Process.myPid(),
                Process.myUid(),
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            ) == PackageManager.PERMISSION_GRANTED
        }.getOrDefault(false)

        return runtimeGrant
    }

    /**
     * Queries the device [PackageManager], SAF `persistedUriPermissions`, and POSIX filesystem to
     * determine real-time Termux status and auto-mountable endpoints without making ungranted
     * `ContentResolver.acquireProvider` calls.
     */
    fun queryTermuxStatus(
        context: Context,
        savedSafUris: Set<String> = emptySet()
    ): TermuxIntegrationStatus {
        val pm = context.packageManager
        val resolver = context.contentResolver

        val packageInfo = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageInfo(
                    TERMUX_PACKAGE_NAME,
                    PackageManager.PackageInfoFlags.of(PackageManager.GET_PROVIDERS.toLong())
                )
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(TERMUX_PACKAGE_NAME, PackageManager.GET_PROVIDERS)
            }
        }.getOrNull()

        val hasDocumentsProvider = isAuthorityAvailable(context, TERMUX_DOCUMENTS_AUTHORITY) ||
            packageInfo?.providers?.any { it.authority == TERMUX_DOCUMENTS_AUTHORITY } == true

        // Collect only OS-verified persisted Termux SAF URIs when the provider is registered
        val activeGrantedUris = if (hasDocumentsProvider) {
            runCatching {
                resolver.persistedUriPermissions
                    .filter { it.uri.authority == TERMUX_DOCUMENTS_AUTHORITY && it.isReadPermission }
                    .map { it.uri.toString() }
                    .distinct()
            }.getOrDefault(emptyList())
        } else {
            emptyList()
        }

        // Check direct POSIX access to /data/data/com.termux/files/home or /data/user/0/com.termux/files/home
        val directHomeDir = File(TERMUX_HOME_PATH)
        val user0HomeDir = File(TERMUX_USER0_HOME_PATH)
        val isDirectFilesystemAccessible =
            (directHomeDir.exists() && directHomeDir.canRead() && directHomeDir.listFiles() != null) ||
                (user0HomeDir.exists() && user0HomeDir.canRead() && user0HomeDir.listFiles() != null)

        // Check Termux external shared storage directory (/storage/emulated/0/Termux or Android/media/com.termux)
        val extRoot = runCatching { Environment.getExternalStorageDirectory() }.getOrNull()
        val candidates = if (extRoot != null) {
            listOf(
                File(extRoot, "Termux"),
                File(extRoot, "termux"),
                File(extRoot, "Android/media/com.termux"),
                File(extRoot, "Android/data/com.termux/files")
            )
        } else emptyList()
        val accessibleExternalTermuxDir = candidates.firstOrNull {
            it.exists() && it.isDirectory && it.canRead() && it.listFiles() != null
        }

        val isInstalled = packageInfo != null ||
            hasDocumentsProvider ||
            directHomeDir.exists() ||
            user0HomeDir.exists() ||
            accessibleExternalTermuxDir != null

        // Calculate real capacity metrics for the /data partition where Termux $HOME resides
        // using direct POSIX StatFs (zero ContentResolver IPC overhead).
        var totalBytes = 0L
        var freeBytes = 0L
        if (isDirectFilesystemAccessible) {
            val targetPath = if (directHomeDir.exists()) TERMUX_HOME_PATH else TERMUX_USER0_HOME_PATH
            runCatching {
                val stat = StatFs(targetPath)
                totalBytes = stat.totalBytes
                freeBytes = stat.availableBytes
            }
        }
        if (totalBytes <= 0L) {
            runCatching {
                val dataPartitionStat = StatFs(context.filesDir.absolutePath)
                totalBytes = dataPartitionStat.totalBytes
                freeBytes = dataPartitionStat.availableBytes
            }
        }

        return TermuxIntegrationStatus(
            isTermuxInstalled = isInstalled,
            termuxVersionName = packageInfo?.versionName,
            isDocumentsProviderAvailable = hasDocumentsProvider,
            isProviderClientAlive = hasDocumentsProvider && activeGrantedUris.isNotEmpty(),
            grantedTermuxTreeUris = activeGrantedUris,
            isDirectHomeAccessible = isDirectFilesystemAccessible,
            isExternalTermuxDirAccessible = accessibleExternalTermuxDir != null,
            externalTermuxDirPath = accessibleExternalTermuxDir?.absolutePath,
            defaultTermuxHomeUri = buildTermuxHomeTreeUri().toString(),
            totalBytes = totalBytes,
            freeBytes = freeBytes,
            autoMountEnabled = true
        )
    }

    /**
     * Builds the canonical SAF Tree URI for Termux $HOME (`content://com.termux.documents/tree/%2Fdata%2Fdata%2Fcom.termux%2Ffiles%2Fhome`).
     */
    fun buildTermuxHomeTreeUri(): Uri {
        return DocumentsContract.buildTreeDocumentUri(
            TERMUX_DOCUMENTS_AUTHORITY,
            TERMUX_HOME_PATH
        )
    }

    /**
     * Builds the canonical SAF Document URI for Termux $HOME within its tree.
     */
    fun buildTermuxHomeDocumentUri(): Uri {
        val treeUri = buildTermuxHomeTreeUri()
        return DocumentsContract.buildDocumentUriUsingTree(treeUri, TERMUX_HOME_PATH)
    }

    /**
     * Builds the plain SAF Root/Document URI that Android's `DocumentsUI` expects in
     * `DocumentsContract.EXTRA_INITIAL_URI` to open directly inside Termux ($HOME).
     */
    fun buildTermuxInitialPickerUri(): Uri {
        return DocumentsContract.buildDocumentUri(
            TERMUX_DOCUMENTS_AUTHORITY,
            TERMUX_HOME_PATH
        )
    }

    /**
     * Creates an `ACTION_OPEN_DOCUMENT_TREE` Intent pre-configured with `EXTRA_INITIAL_URI`
     * pointing directly to Termux's `com.termux.documents` `$HOME` root when `com.termux.documents`
     * is registered on the device.
     */
    fun createTermuxSafTreeIntent(context: Context? = null): Intent {
        return Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                    Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or
                    Intent.FLAG_GRANT_PREFIX_URI_PERMISSION
            )
            val providerPresent = context == null || isAuthorityAvailable(context, TERMUX_DOCUMENTS_AUTHORITY)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && providerPresent) {
                putExtra(DocumentsContract.EXTRA_INITIAL_URI, buildTermuxInitialPickerUri())
            }
        }
    }

    /**
     * Creates a launch Intent to open or switch to the Termux app (`com.termux`) if installed.
     */
    fun createLaunchTermuxAppIntent(context: Context): Intent? {
        return context.packageManager.getLaunchIntentForPackage(TERMUX_PACKAGE_NAME)?.apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    fun isTermuxUri(pathOrUri: String): Boolean {
        return pathOrUri.startsWith("content://$TERMUX_DOCUMENTS_AUTHORITY") ||
            pathOrUri.startsWith(TERMUX_HOME_PATH) ||
            pathOrUri.startsWith(TERMUX_USER0_HOME_PATH) ||
            pathOrUri.startsWith(TERMUX_USR_PATH)
    }

    /**
     * Extracts a human-readable display name and navigable breadcrumb segments from any SAF or Termux URI.
     */
    fun parseSafBreadcrumbs(uriString: String): List<Pair<String, String>> {
        val uri = runCatching { Uri.parse(uriString) }.getOrNull() ?: return listOf("SAF Root" to uriString)
        val authority = uri.authority ?: return listOf("SAF Root" to uriString)
        val treeDocId = runCatching { DocumentsContract.getTreeDocumentId(uri) }.getOrNull()
        val currentDocId = runCatching { DocumentsContract.getDocumentId(uri) }.getOrElse { treeDocId ?: "" }

        if (authority == TERMUX_DOCUMENTS_AUTHORITY) {
            val baseTreeUri = if (treeDocId != null) {
                DocumentsContract.buildTreeDocumentUri(authority, treeDocId)
            } else {
                buildTermuxHomeTreeUri()
            }
            val rootDocId = treeDocId ?: TERMUX_HOME_PATH
            val rootDocUri = DocumentsContract.buildDocumentUriUsingTree(baseTreeUri, rootDocId).toString()
            val crumbs = mutableListOf("Termux (\$HOME)" to rootDocUri)

            val relative = currentDocId.removePrefix(rootDocId).trim('/')
            if (relative.isNotEmpty()) {
                var accum = rootDocId.trimEnd('/')
                relative.split('/').forEach { segment ->
                    accum = "$accum/$segment"
                    val segUri = DocumentsContract.buildDocumentUriUsingTree(baseTreeUri, accum).toString()
                    crumbs.add(segment to segUri)
                }
            }
            return crumbs
        }

        // Generic SAF provider (SD card, USB OTG, Downloads, External Storage)
        val baseTree = if (treeDocId != null) {
            DocumentsContract.buildTreeDocumentUri(authority, treeDocId)
        } else uri
        val rootId = treeDocId ?: currentDocId
        val rootUriStr = runCatching {
            DocumentsContract.buildDocumentUriUsingTree(baseTree, rootId).toString()
        }.getOrDefault(uriString)

        val rootLabel = when {
            rootId.startsWith("primary:") -> "Internal Storage (${rootId.removePrefix("primary:").ifEmpty { "/" }})"
            rootId.contains(":") -> "Volume (${rootId})"
            else -> "SAF Volume"
        }
        val crumbs = mutableListOf(rootLabel to rootUriStr)
        if (currentDocId != rootId && currentDocId.startsWith(rootId)) {
            val rel = currentDocId.removePrefix(rootId).trim('/', ':')
            if (rel.isNotEmpty()) {
                var accum = rootId.trimEnd('/')
                rel.split('/').forEach { part ->
                    accum = if (accum.endsWith(":")) "$accum$part" else "$accum/$part"
                    val partUri = runCatching {
                        DocumentsContract.buildDocumentUriUsingTree(baseTree, accum).toString()
                    }.getOrDefault(uriString)
                    crumbs.add(part to partUri)
                }
            }
        }
        return crumbs
    }

    /**
     * Resolves the parent Document URI inside a SAF tree so Back navigation works seamlessly inside
     * Termux (`com.termux.documents`) and external SAF folders.
     */
    fun resolveParentSafUri(uriString: String): String? {
        val uri = runCatching { Uri.parse(uriString) }.getOrNull() ?: return null
        val treeDocId = runCatching { DocumentsContract.getTreeDocumentId(uri) }.getOrNull() ?: return null
        val currentDocId = runCatching { DocumentsContract.getDocumentId(uri) }.getOrNull() ?: return null

        if (currentDocId == treeDocId) return null // Already at the root of the granted tree
        val normalizedCurrent = currentDocId.trimEnd('/')
        val lastSlash = normalizedCurrent.lastIndexOf('/')
        val lastColon = normalizedCurrent.lastIndexOf(':')

        val parentDocId = when {
            lastSlash > treeDocId.length -> normalizedCurrent.substring(0, lastSlash)
            lastSlash == treeDocId.length || lastColon >= 0 -> treeDocId
            else -> treeDocId
        }
        val treeUri = DocumentsContract.buildTreeDocumentUri(uri.authority, treeDocId)
        return DocumentsContract.buildDocumentUriUsingTree(treeUri, parentDocId).toString()
    }

    /**
     * Resolves a POSIX path or SAF/Termux `content://` URI into its canonical filesystem path
     * (e.g. `/data/data/com.termux/files/home/...` or `/storage/emulated/0/...`) when possible,
     * or returns the original URI string if the SAF provider is non-POSIX.
     */
    fun resolveRealPosixPathIfPossible(pathOrUri: String): String {
        if (!pathOrUri.startsWith("content://")) return pathOrUri
        val uri = runCatching { Uri.parse(pathOrUri) }.getOrNull() ?: return pathOrUri
        val docId = runCatching { DocumentsContract.getDocumentId(uri) }
            .getOrElse { runCatching { DocumentsContract.getTreeDocumentId(uri) }.getOrNull() }
            ?: return pathOrUri

        return when {
            uri.authority == TERMUX_DOCUMENTS_AUTHORITY || docId.startsWith("/") -> docId
            uri.authority == "com.android.externalstorage.documents" && docId.startsWith("primary:") -> {
                val rel = docId.removePrefix("primary:").trimStart('/')
                val sharedRoot = runCatching { Environment.getExternalStorageDirectory().absolutePath }
                    .getOrDefault("/storage/emulated/0")
                if (rel.isEmpty()) sharedRoot else "$sharedRoot/$rel"
            }
            uri.authority == "com.android.externalstorage.documents" && docId.contains(":") -> {
                val volumeId = docId.substringBefore(':')
                val rel = docId.substringAfter(':').trimStart('/')
                if (rel.isEmpty()) "/storage/$volumeId" else "/storage/$volumeId/$rel"
            }
            else -> pathOrUri
        }
    }
}
