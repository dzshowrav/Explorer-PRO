package com.explorerpro.core.domain.actions

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material.icons.filled.Compare
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.FilterAltOff
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.FolderSpecial
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.ManageSearch
import androidx.compose.material.icons.filled.NoteAdd
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.SdStorage
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Tab
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.ui.graphics.vector.ImageVector
import com.explorerpro.domain.model.ClipboardAction
import com.explorerpro.domain.model.ClipboardState
import com.explorerpro.domain.model.FabCustomizationSettings
import com.explorerpro.domain.model.FileEntry
import com.explorerpro.domain.model.PaneId
import java.io.File

/**
 * Categories of quick actions supported across Explorer Pro modules.
 */
enum class QuickActionCategory {
    CREATE,
    IMPORT,
    CLIPBOARD,
    SELECTION,
    ARCHIVE,
    DUAL_PANE,
    SEARCH,
    OPERATION_CENTER,
    STORAGE_ANALYZER,
    NAVIGATION
}

/**
 * High-level destination context used by [FabVisibilityPolicy] and [QuickActionResolver].
 */
enum class ExplorerScreenDestination {
    MAIN_EXPLORER,
    ARCHIVE_INSPECTOR,
    IMAGE_GALLERY_VIEWER,
    SEARCH_MODE,
    DUAL_PANE_EXPLORER,
    BATCH_RENAME,
    STORAGE_ANALYZER,
    OPERATION_CENTER,
    FILE_PROPERTIES,
    PERMISSION_SETUP,
    EMPTY_OR_ERROR
}

/**
 * Strongly-typed identifier for every real action executable from the FAB & Quick Actions system.
 */
enum class QuickActionId {
    // Creation & Import
    NEW_FOLDER,
    NEW_FILE,
    IMPORT_FILES,
    MOUNT_SAF_FOLDER,
    PASTE_CLIPBOARD,
    PIN_CURRENT_FOLDER,
    OPEN_STORAGE_ANALYZER,

    // Single / Multi Selection
    OPEN_SELECTED,
    OPEN_IN_NEW_TAB,
    EXTRACT_ARCHIVE_HERE,
    EXTRACT_ARCHIVE_TO_FOLDER,
    INSPECT_ARCHIVE,
    CONVERT_ARCHIVE,
    COMPRESS_SELECTION,
    COPY_SELECTION,
    MOVE_SELECTION,
    RENAME_SELECTION,
    BATCH_RENAME_SELECTION,
    SHARE_SELECTION,
    CALCULATE_FOLDER_SIZE,
    CALCULATE_CHECKSUMS,
    SHOW_PROPERTIES,
    DELETE_SELECTION,

    // Dual Pane
    DUAL_COPY_TO_OTHER_PANE,
    DUAL_MOVE_TO_OTHER_PANE,
    DUAL_SWAP_PANES,
    DUAL_COMPARE_FOLDERS,
    DUAL_SYNC_OTHER_PANE,

    // Search
    SEARCH_TOGGLE_RECURSIVE,
    SEARCH_CLEAR_FILTER,

    // Operation Center
    OP_RETRY_ALL_FAILED,
    OP_PAUSE_ALL_RUNNING,
    OP_RESUME_ALL_PAUSED,
    OP_CLEAR_COMPLETED,

    // Storage Analyzer
    ANALYZER_RESCAN,
    ANALYZER_SCAN_ACTIVE_FOLDER
}

/**
 * Universal representation of a quick action item in the Explorer Pro FAB system.
 */
data class QuickAction(
    val id: QuickActionId,
    val title: String,
    val subtitle: String? = null,
    val icon: ImageVector,
    val category: QuickActionCategory,
    val enabled: Boolean = true,
    val visible: Boolean = true,
    val requiresSelection: Boolean = false,
    val requiresWriteAccess: Boolean = false,
    val isDestructive: Boolean = false,
    val requiresConfirmation: Boolean = false,
    val confirmationTitle: String? = null,
    val confirmationMessage: String? = null,
    val badgeText: String? = null
)

/**
 * Resolved FAB configuration for the current screen & state.
 */
data class ResolvedFabModel(
    val visible: Boolean,
    val primaryTitle: String,
    val primaryIcon: ImageVector,
    val primaryBadge: String? = null,
    val primaryDirectAction: QuickAction? = null,
    val speedDialActions: List<QuickAction> = emptyList(),
    val isSelectionMode: Boolean = false,
    val isReadOnlyContext: Boolean = false
)

/**
 * Complete snapshot of UI + domain state needed to resolve FAB visibility and actions.
 */
data class FabResolutionContext(
    val destination: ExplorerScreenDestination,
    val currentPath: String,
    val isDirectoryWritable: Boolean,
    val selectedItems: List<FileEntry>,
    val clipboard: ClipboardState,
    val dualPaneEnabled: Boolean,
    val activePaneId: PaneId,
    val isSearchActive: Boolean,
    val isRecursiveSearch: Boolean,
    val isScrollingDown: Boolean,
    val isKeyboardOpen: Boolean,
    val isModalSheetBlocking: Boolean = false,
    val hasRunningOperations: Boolean = false,
    val hasPausedOperations: Boolean = false,
    val hasFailedOperations: Boolean = false,
    val hasCompletedOperations: Boolean = false,
    val settings: FabCustomizationSettings = FabCustomizationSettings()
)

/**
 * Central policy deciding whether the FAB should be visible for the given [FabResolutionContext].
 */
object FabVisibilityPolicy {

    fun isDirectoryWritable(path: String): Boolean {
        if (path.isBlank()) return false
        if (path.startsWith("content://")) {
            return true
        }
        if (path == "/system" || path.startsWith("/system/") ||
            path == "/proc" || path.startsWith("/proc/") ||
            path == "/vendor" || path.startsWith("/vendor/")
        ) {
            return false
        }
        val file = File(path)
        return file.exists() && file.canWrite()
    }

    fun shouldShowFab(context: FabResolutionContext): Boolean {
        if (context.isModalSheetBlocking) return false
        if (context.settings.hideWhenKeyboardOpen && context.isKeyboardOpen) return false
        if (context.settings.hideWhileScrolling && context.isScrollingDown) return false

        return when (context.destination) {
            ExplorerScreenDestination.PERMISSION_SETUP,
            ExplorerScreenDestination.EMPTY_OR_ERROR -> false

            ExplorerScreenDestination.SEARCH_MODE -> {
                context.isSearchActive || context.selectedItems.isNotEmpty()
            }

            ExplorerScreenDestination.OPERATION_CENTER -> {
                context.hasRunningOperations ||
                    context.hasPausedOperations ||
                    context.hasFailedOperations ||
                    context.hasCompletedOperations
            }

            ExplorerScreenDestination.MAIN_EXPLORER,
            ExplorerScreenDestination.DUAL_PANE_EXPLORER,
            ExplorerScreenDestination.ARCHIVE_INSPECTOR,
            ExplorerScreenDestination.IMAGE_GALLERY_VIEWER,
            ExplorerScreenDestination.BATCH_RENAME,
            ExplorerScreenDestination.STORAGE_ANALYZER,
            ExplorerScreenDestination.FILE_PROPERTIES -> true
        }
    }
}

/**
 * Resolves selection-aware quick actions when one or more files/folders are selected.
 */
object SelectionActionResolver {

    fun resolveForSelection(
        selectedItems: List<FileEntry>,
        isDirectoryWritable: Boolean,
        dualPaneEnabled: Boolean,
        activePaneId: PaneId
    ): List<QuickAction> {
        if (selectedItems.isEmpty()) return emptyList()

        val actions = mutableListOf<QuickAction>()
        val count = selectedItems.size

        if (count == 1) {
            val item = selectedItems.first()
            val isArchive = item.isExtractableArchive

            if (item.isDirectory) {
                actions += QuickAction(
                    id = QuickActionId.OPEN_SELECTED,
                    title = "Open Folder",
                    subtitle = item.name,
                    icon = Icons.Default.FolderOpen,
                    category = QuickActionCategory.SELECTION,
                    requiresSelection = true
                )
                actions += QuickAction(
                    id = QuickActionId.OPEN_IN_NEW_TAB,
                    title = "Open in New Tab",
                    subtitle = "Open ${item.name} in tab",
                    icon = Icons.Default.Tab,
                    category = QuickActionCategory.NAVIGATION,
                    requiresSelection = true
                )
                actions += QuickAction(
                    id = QuickActionId.CALCULATE_FOLDER_SIZE,
                    title = "Calculate Folder Size",
                    subtitle = "Deep recursive size & item count",
                    icon = Icons.Default.Calculate,
                    category = QuickActionCategory.SELECTION,
                    requiresSelection = true
                )
            } else {
                actions += QuickAction(
                    id = QuickActionId.OPEN_SELECTED,
                    title = "Open File",
                    subtitle = item.name,
                    icon = Icons.Default.FileOpen,
                    category = QuickActionCategory.SELECTION,
                    requiresSelection = true
                )
                if (isArchive) {
                    if (isDirectoryWritable) {
                        actions += QuickAction(
                            id = QuickActionId.EXTRACT_ARCHIVE_HERE,
                            title = "Extract Here",
                            subtitle = "Unpack ${item.name} into current folder",
                            icon = Icons.Default.Unarchive,
                            category = QuickActionCategory.ARCHIVE,
                            requiresSelection = true,
                            requiresWriteAccess = true
                        )
                        actions += QuickAction(
                            id = QuickActionId.EXTRACT_ARCHIVE_TO_FOLDER,
                            title = "Extract to Folder…",
                            subtitle = "Unpack into subfolder",
                            icon = Icons.Default.FolderZip,
                            category = QuickActionCategory.ARCHIVE,
                            requiresSelection = true,
                            requiresWriteAccess = true
                        )
                    }
                    actions += QuickAction(
                        id = QuickActionId.INSPECT_ARCHIVE,
                        title = "Inspect Archive",
                        subtitle = "Browse entries & verify integrity",
                        icon = Icons.AutoMirrored.Filled.OpenInNew,
                        category = QuickActionCategory.ARCHIVE,
                        requiresSelection = true
                    )
                    if (isDirectoryWritable) {
                        actions += QuickAction(
                            id = QuickActionId.CONVERT_ARCHIVE,
                            title = "Convert Format…",
                            subtitle = "Convert to 7z, ZIP, TAR, ZSTD, LZ4, XZ…",
                            icon = Icons.Default.SwapHoriz,
                            category = QuickActionCategory.ARCHIVE,
                            requiresSelection = true,
                            requiresWriteAccess = true
                        )
                    }
                }
            }

            if (dualPaneEnabled) {
                val directionLabel = if (activePaneId == PaneId.LEFT) "Left → Right" else "Right → Left"
                actions += QuickAction(
                    id = QuickActionId.DUAL_COPY_TO_OTHER_PANE,
                    title = "Copy ($directionLabel)",
                    subtitle = "Transfer to opposite pane",
                    icon = Icons.AutoMirrored.Filled.CompareArrows,
                    category = QuickActionCategory.DUAL_PANE,
                    requiresSelection = true
                )
                if (isDirectoryWritable) {
                    actions += QuickAction(
                        id = QuickActionId.DUAL_MOVE_TO_OTHER_PANE,
                        title = "Move ($directionLabel)",
                        subtitle = "Move to opposite pane",
                        icon = Icons.AutoMirrored.Filled.DriveFileMove,
                        category = QuickActionCategory.DUAL_PANE,
                        requiresSelection = true,
                        requiresWriteAccess = true
                    )
                }
            }

            actions += QuickAction(
                id = QuickActionId.COPY_SELECTION,
                title = "Copy",
                subtitle = "Copy to Explorer clipboard",
                icon = Icons.Default.ContentCopy,
                category = QuickActionCategory.CLIPBOARD,
                requiresSelection = true
            )

            if (isDirectoryWritable) {
                actions += QuickAction(
                    id = QuickActionId.MOVE_SELECTION,
                    title = "Move (Cut)",
                    subtitle = "Cut to Explorer clipboard",
                    icon = Icons.AutoMirrored.Filled.DriveFileMove,
                    category = QuickActionCategory.CLIPBOARD,
                    requiresSelection = true,
                    requiresWriteAccess = true
                )
                actions += QuickAction(
                    id = QuickActionId.RENAME_SELECTION,
                    title = "Rename",
                    subtitle = "Rename ${item.name}",
                    icon = Icons.Default.DriveFileRenameOutline,
                    category = QuickActionCategory.SELECTION,
                    requiresSelection = true,
                    requiresWriteAccess = true
                )
                actions += QuickAction(
                    id = QuickActionId.COMPRESS_SELECTION,
                    title = "Compress to Archive",
                    subtitle = "7z, ZIP, TAR, ZSTD, LZ4, XZ",
                    icon = Icons.Default.FolderZip,
                    category = QuickActionCategory.ARCHIVE,
                    requiresSelection = true,
                    requiresWriteAccess = true
                )
            }

            if (!item.isDirectory) {
                actions += QuickAction(
                    id = QuickActionId.SHARE_SELECTION,
                    title = "Share",
                    subtitle = "Share ${item.name}",
                    icon = Icons.Default.Share,
                    category = QuickActionCategory.SELECTION,
                    requiresSelection = true
                )
            }

            actions += QuickAction(
                id = QuickActionId.SHOW_PROPERTIES,
                title = "Properties & Hashes",
                subtitle = "Inspect permissions, size & SHA-256",
                icon = Icons.Default.Info,
                category = QuickActionCategory.SELECTION,
                requiresSelection = true
            )

            if (isDirectoryWritable) {
                actions += QuickAction(
                    id = QuickActionId.DELETE_SELECTION,
                    title = "Delete",
                    subtitle = "Delete ${item.name}",
                    icon = Icons.Default.Delete,
                    category = QuickActionCategory.SELECTION,
                    requiresSelection = true,
                    requiresWriteAccess = true,
                    isDestructive = true,
                    requiresConfirmation = true,
                    confirmationTitle = "Delete ${item.name}?",
                    confirmationMessage = "Are you sure you want to delete \"${item.name}\"?"
                )
            }
        } else {
            // Multi-item selection
            if (dualPaneEnabled) {
                val directionLabel = if (activePaneId == PaneId.LEFT) "Left → Right" else "Right → Left"
                actions += QuickAction(
                    id = QuickActionId.DUAL_COPY_TO_OTHER_PANE,
                    title = "Copy $count ($directionLabel)",
                    subtitle = "Copy selected items to opposite pane",
                    icon = Icons.AutoMirrored.Filled.CompareArrows,
                    category = QuickActionCategory.DUAL_PANE,
                    requiresSelection = true,
                    badgeText = count.toString()
                )
                if (isDirectoryWritable) {
                    actions += QuickAction(
                        id = QuickActionId.DUAL_MOVE_TO_OTHER_PANE,
                        title = "Move $count ($directionLabel)",
                        subtitle = "Move selected items to opposite pane",
                        icon = Icons.AutoMirrored.Filled.DriveFileMove,
                        category = QuickActionCategory.DUAL_PANE,
                        requiresSelection = true,
                        requiresWriteAccess = true,
                        badgeText = count.toString()
                    )
                }
            }

            actions += QuickAction(
                id = QuickActionId.COPY_SELECTION,
                title = "Copy ($count)",
                subtitle = "Copy $count items to clipboard",
                icon = Icons.Default.ContentCopy,
                category = QuickActionCategory.CLIPBOARD,
                requiresSelection = true,
                badgeText = count.toString()
            )

            if (isDirectoryWritable) {
                actions += QuickAction(
                    id = QuickActionId.MOVE_SELECTION,
                    title = "Move / Cut ($count)",
                    subtitle = "Move $count items to clipboard",
                    icon = Icons.AutoMirrored.Filled.DriveFileMove,
                    category = QuickActionCategory.CLIPBOARD,
                    requiresSelection = true,
                    requiresWriteAccess = true
                )
                actions += QuickAction(
                    id = QuickActionId.COMPRESS_SELECTION,
                    title = "Compress ($count)",
                    subtitle = "Pack $count items into archive",
                    icon = Icons.Default.FolderZip,
                    category = QuickActionCategory.ARCHIVE,
                    requiresSelection = true,
                    requiresWriteAccess = true
                )
                actions += QuickAction(
                    id = QuickActionId.BATCH_RENAME_SELECTION,
                    title = "Batch Rename ($count)",
                    subtitle = "Pattern, prefix/suffix & numbering",
                    icon = Icons.Default.DriveFileRenameOutline,
                    category = QuickActionCategory.SELECTION,
                    requiresSelection = true,
                    requiresWriteAccess = true
                )
            }

            val hasFiles = selectedItems.any { !it.isDirectory }
            if (hasFiles) {
                actions += QuickAction(
                    id = QuickActionId.SHARE_SELECTION,
                    title = "Share Files",
                    subtitle = "Share selected files with other apps",
                    icon = Icons.Default.Share,
                    category = QuickActionCategory.SELECTION,
                    requiresSelection = true
                )
                actions += QuickAction(
                    id = QuickActionId.CALCULATE_CHECKSUMS,
                    title = "Calculate Checksums ($count)",
                    subtitle = "Verify SHA-256 digests for selected files",
                    icon = Icons.Default.Fingerprint,
                    category = QuickActionCategory.SELECTION,
                    requiresSelection = true
                )
            }

            if (isDirectoryWritable) {
                actions += QuickAction(
                    id = QuickActionId.DELETE_SELECTION,
                    title = "Delete ($count)",
                    subtitle = "Delete $count selected items",
                    icon = Icons.Default.Delete,
                    category = QuickActionCategory.SELECTION,
                    requiresSelection = true,
                    requiresWriteAccess = true,
                    isDestructive = true,
                    requiresConfirmation = true,
                    confirmationTitle = "Delete $count items?",
                    confirmationMessage = "Are you sure you want to delete the $count selected items?"
                )
            }
        }

        return actions
    }
}

/**
 * Resolves the complete [ResolvedFabModel] for any screen and context in Explorer Pro.
 */
object QuickActionResolver {

    fun resolve(context: FabResolutionContext): ResolvedFabModel {
        if (!FabVisibilityPolicy.shouldShowFab(context)) {
            return ResolvedFabModel(
                visible = false,
                primaryTitle = "",
                primaryIcon = Icons.Default.Add
            )
        }

        return when (context.destination) {
            ExplorerScreenDestination.OPERATION_CENTER -> resolveOperationCenterFab(context)
            ExplorerScreenDestination.STORAGE_ANALYZER -> resolveStorageAnalyzerFab(context)
            ExplorerScreenDestination.SEARCH_MODE -> resolveSearchFab(context)
            else -> resolveExplorerFab(context)
        }
    }

    private fun resolveExplorerFab(context: FabResolutionContext): ResolvedFabModel {
        val selected = context.selectedItems
        val writable = context.isDirectoryWritable

        // 1. Selection-Aware Mode (when adaptToSelection is enabled and items are selected)
        if (context.settings.adaptToSelection && selected.isNotEmpty()) {
            val selectionActions = SelectionActionResolver.resolveForSelection(
                selectedItems = selected,
                isDirectoryWritable = writable,
                dualPaneEnabled = context.dualPaneEnabled,
                activePaneId = context.activePaneId
            )
            if (selectionActions.isEmpty()) {
                return ResolvedFabModel(
                    visible = false,
                    primaryTitle = "",
                    primaryIcon = Icons.Default.Add
                )
            }
            val primaryAction = selectionActions.first()
            val title = if (selected.size == 1) {
                val item = selected.first()
                if (item.isExtractableArchive) {
                    "Archive Actions"
                } else {
                    "Item Actions"
                }
            } else {
                "${selected.size} Selected"
            }

            return ResolvedFabModel(
                visible = true,
                primaryTitle = title,
                primaryIcon = primaryAction.icon,
                primaryBadge = selected.size.toString(),
                primaryDirectAction = primaryAction,
                speedDialActions = selectionActions,
                isSelectionMode = true,
                isReadOnlyContext = !writable
            )
        }

        // 2. Default / Dual-Pane / Clipboard-Aware Explorer Mode
        val actions = mutableListOf<QuickAction>()

        // Prioritize Paste if clipboard has valid items and directory is writable
        val hasClipboard = !context.clipboard.isEmpty && context.clipboard.action != ClipboardAction.NONE
        if (hasClipboard && writable) {
            val modeVerb = if (context.clipboard.action == ClipboardAction.MOVE) "Move" else "Paste"
            val clipCount = context.clipboard.sourceFiles.size
            actions += QuickAction(
                id = QuickActionId.PASTE_CLIPBOARD,
                title = "$modeVerb Here ($clipCount)",
                subtitle = "Paste $clipCount item(s) from clipboard",
                icon = Icons.Default.ContentPaste,
                category = QuickActionCategory.CLIPBOARD,
                requiresWriteAccess = true,
                badgeText = clipCount.toString()
            )
        }

        // Creation and Import actions (only when directory is writable)
        if (writable) {
            actions += QuickAction(
                id = QuickActionId.NEW_FOLDER,
                title = "New Folder",
                subtitle = "Create directory in current path",
                icon = Icons.Default.CreateNewFolder,
                category = QuickActionCategory.CREATE,
                requiresWriteAccess = true
            )
            actions += QuickAction(
                id = QuickActionId.NEW_FILE,
                title = "New File",
                subtitle = "Create empty text, script or config file",
                icon = Icons.Default.NoteAdd,
                category = QuickActionCategory.CREATE,
                requiresWriteAccess = true
            )
            actions += QuickAction(
                id = QuickActionId.IMPORT_FILES,
                title = "Import Files",
                subtitle = "Copy external files into this folder",
                icon = Icons.Default.UploadFile,
                category = QuickActionCategory.IMPORT,
                requiresWriteAccess = true
            )
        }

        // Dual-pane specific quick actions when dual-pane mode is active
        if (context.dualPaneEnabled) {
            actions += QuickAction(
                id = QuickActionId.DUAL_SWAP_PANES,
                title = "Swap Panes",
                subtitle = "Exchange Left and Right pane paths",
                icon = Icons.Default.SwapHoriz,
                category = QuickActionCategory.DUAL_PANE
            )
            actions += QuickAction(
                id = QuickActionId.DUAL_COMPARE_FOLDERS,
                title = "Compare Folders",
                subtitle = "Highlight differences between Left & Right",
                icon = Icons.Default.Compare,
                category = QuickActionCategory.DUAL_PANE
            )
            actions += QuickAction(
                id = QuickActionId.DUAL_SYNC_OTHER_PANE,
                title = "Synchronize View",
                subtitle = "Sync opposite pane to active folder",
                icon = Icons.Default.Sync,
                category = QuickActionCategory.DUAL_PANE
            )
        }

        // Always-available non-destructive folder & storage actions
        actions += QuickAction(
            id = QuickActionId.MOUNT_SAF_FOLDER,
            title = "Mount Folder (SAF)",
            subtitle = "Grant access to external or Termux directory",
            icon = Icons.Default.SdStorage,
            category = QuickActionCategory.IMPORT
        )
        actions += QuickAction(
            id = QuickActionId.PIN_CURRENT_FOLDER,
            title = "Favorite Current Folder",
            subtitle = "Pin to Quick Access drawer",
            icon = Icons.Default.Star,
            category = QuickActionCategory.NAVIGATION
        )
        actions += QuickAction(
            id = QuickActionId.OPEN_STORAGE_ANALYZER,
            title = "Analyze Folder Space",
            subtitle = "Inspect largest files & duplicates",
            icon = Icons.Default.Analytics,
            category = QuickActionCategory.STORAGE_ANALYZER
        )

        if (actions.isEmpty()) {
            return ResolvedFabModel(
                visible = false,
                primaryTitle = "",
                primaryIcon = Icons.Default.Add
            )
        }

        val primaryTitle = when {
            hasClipboard && writable -> "Paste (${context.clipboard.sourceFiles.size})"
            !writable -> "Read-Only Actions"
            context.dualPaneEnabled -> "Dual-Pane & Create"
            else -> "Create & Import"
        }

        val primaryIcon = when {
            hasClipboard && writable -> Icons.Default.ContentPaste
            !writable -> Icons.Default.FolderSpecial
            else -> Icons.Default.Add
        }

        return ResolvedFabModel(
            visible = true,
            primaryTitle = primaryTitle,
            primaryIcon = primaryIcon,
            primaryBadge = if (hasClipboard && writable) context.clipboard.sourceFiles.size.toString() else null,
            primaryDirectAction = actions.first(),
            speedDialActions = actions,
            isSelectionMode = false,
            isReadOnlyContext = !writable
        )
    }

    private fun resolveSearchFab(context: FabResolutionContext): ResolvedFabModel {
        if (context.settings.adaptToSelection && context.selectedItems.isNotEmpty()) {
            return resolveExplorerFab(context)
        }
        val actions = listOf(
            QuickAction(
                id = QuickActionId.SEARCH_TOGGLE_RECURSIVE,
                title = if (context.isRecursiveSearch) "Switch to Current Folder Only" else "Search Subfolders Recursively",
                subtitle = if (context.isRecursiveSearch) "Currently searching deep subfolders" else "Currently searching immediate folder",
                icon = Icons.Default.ManageSearch,
                category = QuickActionCategory.SEARCH
            ),
            QuickAction(
                id = QuickActionId.SEARCH_CLEAR_FILTER,
                title = "Reset Search & Filters",
                subtitle = "Clear query and file type filters",
                icon = Icons.Default.FilterAltOff,
                category = QuickActionCategory.SEARCH
            )
        )
        return ResolvedFabModel(
            visible = true,
            primaryTitle = "Search Options",
            primaryIcon = Icons.Default.ManageSearch,
            primaryDirectAction = actions.first(),
            speedDialActions = actions
        )
    }

    private fun resolveOperationCenterFab(context: FabResolutionContext): ResolvedFabModel {
        val actions = mutableListOf<QuickAction>()
        if (context.hasFailedOperations) {
            actions += QuickAction(
                id = QuickActionId.OP_RETRY_ALL_FAILED,
                title = "Retry Failed Operations",
                subtitle = "Restart failed transfers or extractions",
                icon = Icons.Default.Replay,
                category = QuickActionCategory.OPERATION_CENTER
            )
        }
        if (context.hasRunningOperations) {
            actions += QuickAction(
                id = QuickActionId.OP_PAUSE_ALL_RUNNING,
                title = "Pause All Running",
                subtitle = "Temporarily pause active file tasks",
                icon = Icons.Default.Pause,
                category = QuickActionCategory.OPERATION_CENTER
            )
        }
        if (context.hasPausedOperations) {
            actions += QuickAction(
                id = QuickActionId.OP_RESUME_ALL_PAUSED,
                title = "Resume All Paused",
                subtitle = "Continue paused file operations",
                icon = Icons.Default.PlayArrow,
                category = QuickActionCategory.OPERATION_CENTER
            )
        }
        if (context.hasCompletedOperations || context.hasFailedOperations) {
            actions += QuickAction(
                id = QuickActionId.OP_CLEAR_COMPLETED,
                title = "Clear Completed History",
                subtitle = "Remove finished, cancelled & failed records",
                icon = Icons.Default.DeleteSweep,
                category = QuickActionCategory.OPERATION_CENTER,
                isDestructive = true,
                requiresConfirmation = true,
                confirmationTitle = "Clear Operation History?",
                confirmationMessage = "Remove all completed, cancelled, and failed tasks from the operation log?"
            )
        }

        if (actions.isEmpty()) {
            return ResolvedFabModel(
                visible = false,
                primaryTitle = "",
                primaryIcon = Icons.Default.ClearAll
            )
        }

        return ResolvedFabModel(
            visible = true,
            primaryTitle = actions.first().title,
            primaryIcon = actions.first().icon,
            primaryDirectAction = actions.first(),
            speedDialActions = actions
        )
    }

    private fun resolveStorageAnalyzerFab(context: FabResolutionContext): ResolvedFabModel {
        val actions = listOf(
            QuickAction(
                id = QuickActionId.ANALYZER_RESCAN,
                title = "Rescan Storage",
                subtitle = "Refresh category breakdown & duplicates",
                icon = Icons.Default.Refresh,
                category = QuickActionCategory.STORAGE_ANALYZER
            ),
            QuickAction(
                id = QuickActionId.ANALYZER_SCAN_ACTIVE_FOLDER,
                title = "Scan Active Folder",
                subtitle = "Analyze ${File(context.currentPath).name.ifEmpty { context.currentPath }}",
                icon = Icons.Default.Analytics,
                category = QuickActionCategory.STORAGE_ANALYZER
            )
        )
        return ResolvedFabModel(
            visible = true,
            primaryTitle = "Rescan Storage",
            primaryIcon = Icons.Default.Refresh,
            primaryDirectAction = actions.first(),
            speedDialActions = actions
        )
    }
}
