package com.explorerpro

import android.Manifest
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FolderSpecial
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.SdStorage
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material3.Button
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.explorerpro.core.designsystem.dialogs.ExplorerAlertDialog
import com.explorerpro.core.designsystem.dialogs.ExplorerConfirmationDialog
import com.explorerpro.core.designsystem.dialogs.ExplorerConflictDialog
import com.explorerpro.core.designsystem.dialogs.ExplorerErrorDialog
import com.explorerpro.core.designsystem.dialogs.ExplorerFileActionBottomSheet
import com.explorerpro.core.designsystem.dialogs.ExplorerFirstLaunchPermissionDialog
import com.explorerpro.core.designsystem.dialogs.ExplorerProgressDialog
import com.explorerpro.core.designsystem.dialogs.ExplorerSuccessDialog
import com.explorerpro.core.designsystem.fab.ContextAwareExplorerFabHost
import com.explorerpro.core.domain.actions.ExplorerScreenDestination
import com.explorerpro.core.domain.actions.FabResolutionContext
import com.explorerpro.core.domain.actions.FabVisibilityPolicy
import com.explorerpro.core.domain.actions.QuickAction
import com.explorerpro.core.domain.actions.QuickActionId
import com.explorerpro.core.domain.actions.QuickActionResolver
import com.explorerpro.core.filesystem.FormatDetectorAndMetadataEngine
import com.explorerpro.domain.model.ClipboardAction
import com.explorerpro.domain.model.ClipboardState
import com.explorerpro.domain.model.ExplorerNoticeSeverity
import com.explorerpro.domain.model.FabPlacementMode
import com.explorerpro.domain.model.FileEntry
import com.explorerpro.domain.model.OperationStatus
import com.explorerpro.domain.model.OperationType
import com.explorerpro.domain.model.PaneId
import com.explorerpro.domain.model.SortOrder
import com.explorerpro.domain.model.TermuxIntegrationStatus
import com.explorerpro.domain.model.ThemePreference
import com.explorerpro.feature.operations.FloatingOperationIndicator
import com.explorerpro.feature.operations.OperationCenterModal
import com.explorerpro.presentation.ui.ArchiveInspectorDialog
import com.explorerpro.presentation.ui.BatchRenameDialog
import com.explorerpro.presentation.ui.CreateArchiveDialog
import com.explorerpro.presentation.ui.DeviconImageLoader
import com.explorerpro.presentation.ui.DeviconPickerDialog
import com.explorerpro.presentation.ui.ExplorerPaneContainer
import com.explorerpro.presentation.ui.ExplorerSettingsScreen
import com.explorerpro.presentation.ui.ExplorerTopBarAndRibbon
import com.explorerpro.presentation.ui.FilePropertiesDialog
import com.explorerpro.presentation.ui.FolderComparisonDialog
import com.explorerpro.presentation.ui.FullScreenImageViewerDialog
import com.explorerpro.presentation.ui.SingleInputPromptDialog
import com.explorerpro.presentation.ui.StorageAnalyzerDialog
import com.explorerpro.presentation.ui.TextFileEditorDialog
import com.explorerpro.presentation.viewmodel.ExplorerViewModel
import com.explorerpro.ui.theme.FluentAmber400
import com.explorerpro.ui.theme.FluentEmerald400
import com.explorerpro.ui.theme.MyApplicationTheme
import kotlinx.coroutines.launch
import java.io.File

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        coil.Coil.setImageLoader { DeviconImageLoader.get(applicationContext) }
        enableEdgeToEdge()
        setContent {
            val explorerViewModel: ExplorerViewModel = viewModel()
            val prefs by explorerViewModel.userPreferences.collectAsStateWithLifecycle()

            val useDarkTheme = when (prefs.themePreference) {
                ThemePreference.SYSTEM -> isSystemInDarkTheme()
                ThemePreference.DARK -> true
                ThemePreference.LIGHT -> false
            }

            SideEffect {
                val controller = WindowCompat.getInsetsController(window, window.decorView)
                controller.isAppearanceLightStatusBars = !useDarkTheme
                controller.isAppearanceLightNavigationBars = !useDarkTheme
            }

            MyApplicationTheme(
                darkTheme = useDarkTheme,
                dynamicColor = prefs.dynamicColorEnabled
            ) {
                ExplorerProAppScreen(viewModel = explorerViewModel)
            }
        }
    }
}

@Composable
fun ExplorerProAppScreen(viewModel: ExplorerViewModel) {
    val prefs by viewModel.userPreferences.collectAsStateWithLifecycle()
    val leftPane by viewModel.leftPane.collectAsStateWithLifecycle()
    val rightPane by viewModel.rightPane.collectAsStateWithLifecycle()
    val activePaneId by viewModel.activePaneId.collectAsStateWithLifecycle()
    val activePaneState = if (activePaneId == PaneId.LEFT) leftPane else rightPane

    val volumes by viewModel.storageVolumes.collectAsStateWithLifecycle()
    val termuxStatus by viewModel.termuxStatus.collectAsStateWithLifecycle()
    val permissionStatus by viewModel.permissionStatus.collectAsStateWithLifecycle()
    val autoLaunchTermuxSaf by viewModel.autoLaunchTermuxSafEvent.collectAsStateWithLifecycle()
    val favorites by viewModel.favorites.collectAsStateWithLifecycle()
    val recentFiles by viewModel.recentFiles.collectAsStateWithLifecycle()
    val activeTasks by viewModel.activeTasks.collectAsStateWithLifecycle()
    val operationLogs by viewModel.operationLogs.collectAsStateWithLifecycle()
    val clipboardEntries by viewModel.clipboardEntries.collectAsStateWithLifecycle()
    val isClipboardCut by viewModel.isClipboardCut.collectAsStateWithLifecycle()

    val inspectedArchive by viewModel.inspectedArchive.collectAsStateWithLifecycle()
    val archiveEntries by viewModel.archiveEntries.collectAsStateWithLifecycle()
    val archiveVerifyMsg by viewModel.archiveVerificationStatus.collectAsStateWithLifecycle()

    val activeImageEntry by viewModel.activeImageEntry.collectAsStateWithLifecycle()
    val galleryImages by viewModel.galleryImages.collectAsStateWithLifecycle()

    val propertiesEntry by viewModel.propertiesEntry.collectAsStateWithLifecycle()
    val propertiesSha256 by viewModel.propertiesSha256.collectAsStateWithLifecycle()

    val textViewerEntry by viewModel.textViewerEntry.collectAsStateWithLifecycle()
    val folderComparison by viewModel.folderComparison.collectAsStateWithLifecycle()
    val storageReport by viewModel.storageReport.collectAsStateWithLifecycle()
    val isAnalyzingStorage by viewModel.isAnalyzingStorage.collectAsStateWithLifecycle()
    val snackbarMessage by viewModel.snackbarMessage.collectAsStateWithLifecycle()

    val pendingTransferConflict by viewModel.pendingTransferConflict.collectAsStateWithLifecycle()
    val deleteConfirmationRequest by viewModel.deleteConfirmationRequest.collectAsStateWithLifecycle()
    val activeProgressDialogTaskId by viewModel.activeProgressDialogTaskId.collectAsStateWithLifecycle()
    val noticeDialog by viewModel.noticeDialog.collectAsStateWithLifecycle()
    val actionSheetEntry by viewModel.actionSheetEntry.collectAsStateWithLifecycle()
    val customFolderIcons by viewModel.customFolderIcons.collectAsStateWithLifecycle()
    val showDeviconBrowserDialog by viewModel.showDeviconBrowserDialog.collectAsStateWithLifecycle()
    val iconPickerTargetEntry by viewModel.iconPickerTargetEntry.collectAsStateWithLifecycle()

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    // Dialog states
    var showNewFolderDialog by remember { mutableStateOf(false) }
    var showNewNoteDialog by remember { mutableStateOf(false) }
    var renameTargetEntry by remember { mutableStateOf<FileEntry?>(null) }
    var showBatchRenameDialog by remember { mutableStateOf(false) }
    var showCompressZipDialog by remember { mutableStateOf(false) }
    var convertTargetArchive by remember { mutableStateOf<FileEntry?>(null) }
    var showOperationCenterDialog by remember { mutableStateOf(false) }
    var showStorageAnalyzerDialog by remember { mutableStateOf(false) }
    var showPermissionSetupDialog by remember { mutableStateOf(false) }
    var showSettingsPage by remember { mutableStateOf(false) }
    var hasTriggeredFirstLaunchPermissionCheck by remember { mutableStateOf(false) }
    var autoChainAllFilesAccessAfterRuntime by remember { mutableStateOf(false) }
    var isActivePaneScrollingDown by remember(activePaneId, activePaneState.currentPath) {
        mutableStateOf(false)
    }

    val context = LocalContext.current
    val density = LocalDensity.current
    val isKeyboardOpen = WindowInsets.ime.getBottom(density) > 0

    // Standard SAF Folder Picker Launcher
    val safTreeLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            viewModel.onSafTreeGranted(uri)
        }
    }

    // Multi-Document Import Picker Launcher (for FAB "Import Files" quick action)
    val importFilesLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.importExternalUris(activePaneId, uris)
        }
    }

    // Dedicated Termux SAF Tree Launcher (with EXTRA_INITIAL_URI pointing to com.termux.documents $HOME)
    val termuxSafLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri = result.data?.data
            if (uri != null) {
                viewModel.onSafTreeGranted(uri)
            }
        }
        viewModel.refreshPermissionStatus()
    }

    // All Files Access (MANAGE_EXTERNAL_STORAGE) & System Settings Launcher
    val allFilesAccessLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        viewModel.onAppResumed()
        viewModel.refreshPermissionStatus()
    }

    val launchAllFilesAccessSettings: () -> Unit = {
        runCatching {
            allFilesAccessLauncher.launch(viewModel.fileSystemRepository.createAllFilesAccessIntent())
        }.onFailure {
            runCatching {
                allFilesAccessLauncher.launch(
                    Intent(android.provider.Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                )
            }.onFailure {
                runCatching {
                    allFilesAccessLauncher.launch(viewModel.fileSystemRepository.createAppDetailsSettingsIntent())
                }
            }
        }
    }

    // Single Notification Permission Launcher (with fallback to Notification Settings if blocked)
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        viewModel.refreshPermissionStatus()
        if (!granted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val act = context as? Activity
            val canShowRationale = act?.shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS) ?: true
            if (!canShowRationale) {
                runCatching {
                    allFilesAccessLauncher.launch(viewModel.fileSystemRepository.createNotificationSettingsIntent())
                }
            }
        }
    }

    // Multi-Permission Runtime Launcher for First-Launch & "Grant All Permissions" Chaining
    val multiplePermissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        viewModel.refreshPermissionStatus()
        val updatedStatus = viewModel.fileSystemRepository.queryAppPermissionStatus(prefs.persistedSafUris)
        if (autoChainAllFilesAccessAfterRuntime) {
            autoChainAllFilesAccessAfterRuntime = false
            if (updatedStatus.requiresManageExternalStorage && !updatedStatus.isAllFilesAccessGranted) {
                launchAllFilesAccessSettings()
            } else if (updatedStatus.areAllCorePermissionsGranted) {
                viewModel.completeFirstLaunchPermissionsSetup()
                showPermissionSetupDialog = false
            }
        } else if (updatedStatus.areAllCorePermissionsGranted) {
            viewModel.completeFirstLaunchPermissionsSetup()
            showPermissionSetupDialog = false
        }
    }

    val launchGrantAllRequiredPermissions: () -> Unit = {
        val latestStatus = viewModel.fileSystemRepository.queryAppPermissionStatus(prefs.persistedSafUris)
        val missingRuntime = latestStatus.missingRuntimePermissions()
        when {
            missingRuntime.isNotEmpty() -> {
                autoChainAllFilesAccessAfterRuntime = true
                runCatching {
                    multiplePermissionsLauncher.launch(missingRuntime.toTypedArray())
                }.onFailure {
                    autoChainAllFilesAccessAfterRuntime = false
                    if (latestStatus.requiresManageExternalStorage && !latestStatus.isAllFilesAccessGranted) {
                        launchAllFilesAccessSettings()
                    }
                }
            }
            latestStatus.requiresManageExternalStorage && !latestStatus.isAllFilesAccessGranted -> {
                launchAllFilesAccessSettings()
            }
            latestStatus.isTermuxDetected && !latestStatus.isTermuxAccessGranted -> {
                viewModel.openOrRequestTermuxStorage(activePaneId) { intent ->
                    runCatching { termuxSafLauncher.launch(intent) }
                        .onFailure { safTreeLauncher.launch(null) }
                }
            }
            else -> {
                viewModel.completeFirstLaunchPermissionsSetup()
                showPermissionSetupDialog = false
            }
        }
    }

    // Fresh Install / First-Launch Automatic Permission Request Effect
    LaunchedEffect(prefs.isLoadedFromDisk) {
        if (prefs.isLoadedFromDisk && !hasTriggeredFirstLaunchPermissionCheck) {
            hasTriggeredFirstLaunchPermissionCheck = true
            viewModel.refreshPermissionStatus()
            val currentStatus = viewModel.fileSystemRepository.queryAppPermissionStatus(prefs.persistedSafUris)
            if (!prefs.firstLaunchPermissionsCompleted) {
                if (currentStatus.areAllCorePermissionsGranted) {
                    viewModel.completeFirstLaunchPermissionsSetup()
                } else {
                    showPermissionSetupDialog = true
                    launchGrantAllRequiredPermissions()
                }
            }
        }
    }

    // Auto-close First-Launch Permission Dialog as soon as all core permissions are granted
    LaunchedEffect(permissionStatus.areAllCorePermissionsGranted) {
        if (showPermissionSetupDialog &&
            permissionStatus.areAllCorePermissionsGranted &&
            (!permissionStatus.isTermuxDetected || permissionStatus.isTermuxAccessGranted)
        ) {
            viewModel.completeFirstLaunchPermissionsSetup()
            showPermissionSetupDialog = false
        }
    }

    val runningOrPausedCount = remember(activeTasks) {
        activeTasks.count {
            it.status == OperationStatus.RUNNING ||
                it.status == OperationStatus.QUEUED ||
                it.status == OperationStatus.PAUSED
        }
    }

    LaunchedEffect(runningOrPausedCount) {
        if (runningOrPausedCount > 0 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                runCatching { notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }
            }
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.onAppResumed()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)

        // Real OS BroadcastReceiver for Termux package installation, restart, or state change
        val pkgReceiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                val pkg = intent?.data?.schemeSpecificPart
                if (pkg == null || pkg.contains("termux", ignoreCase = true)) {
                    viewModel.onAppResumed()
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addAction(Intent.ACTION_PACKAGE_RESTARTED)
            addDataScheme("package")
        }
        runCatching {
            ContextCompat.registerReceiver(
                context,
                pkgReceiver,
                filter,
                ContextCompat.RECEIVER_EXPORTED
            )
        }

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            runCatching { context.unregisterReceiver(pkgReceiver) }
        }
    }

    // Automatically trigger Termux SAF mount handshake when Termux is detected open/installed on device
    LaunchedEffect(autoLaunchTermuxSaf, showPermissionSetupDialog) {
        if (autoLaunchTermuxSaf && !showPermissionSetupDialog) {
            viewModel.consumeAutoLaunchTermuxSafEvent()
            val intent = com.explorerpro.core.filesystem.TermuxStorageBridge.createTermuxSafTreeIntent(context)
            runCatching { termuxSafLauncher.launch(intent) }
        }
    }

    LaunchedEffect(snackbarMessage) {
        snackbarMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearSnackbarMessage()
        }
    }

    // Predictive Back / Navigation BackHandler
    BackHandler(
        enabled = !showSettingsPage && (
            activePaneState.selectedPaths.isNotEmpty() ||
                activePaneState.currentPath != viewModel.fileSystemRepository.primarySharedRoot.absolutePath
            )
    ) {
        if (activePaneState.selectedPaths.isNotEmpty()) {
            viewModel.clearSelection(activePaneId)
        } else {
            viewModel.navigateBackInTab(activePaneId)
        }
    }

    val favoritePathSet = remember(favorites) { favorites.map { it.path }.toSet() }
    val workspaceRootPath = viewModel.fileSystemRepository.primarySharedRoot.absolutePath
    val systemRootPath = viewModel.fileSystemRepository.systemRoot.absolutePath
    val appSandboxPath = viewModel.fileSystemRepository.appSandboxRoot.absolutePath

    // Resolve Context-Aware FAB Model for Main / Dual-Pane / Search Explorer
    val selectedEntriesInActivePane = remember(activePaneState.entries, activePaneState.selectedPaths) {
        activePaneState.entries.filter { activePaneState.selectedPaths.contains(it.path) }
    }
    val isCurrentDirWritable = remember(activePaneState.currentPath) {
        FabVisibilityPolicy.isDirectoryWritable(activePaneState.currentPath)
    }
    val isAnyDialogBlocking = showNewFolderDialog ||
        showNewNoteDialog ||
        renameTargetEntry != null ||
        showBatchRenameDialog ||
        showCompressZipDialog ||
        convertTargetArchive != null ||
        showOperationCenterDialog ||
        showStorageAnalyzerDialog ||
        showPermissionSetupDialog ||
        showSettingsPage ||
        inspectedArchive != null ||
        activeImageEntry != null ||
        propertiesEntry != null ||
        textViewerEntry != null ||
        folderComparison != null ||
        pendingTransferConflict != null ||
        deleteConfirmationRequest != null ||
        activeProgressDialogTaskId != null ||
        noticeDialog != null ||
        actionSheetEntry != null ||
        showDeviconBrowserDialog

    val resolvedFabModel = remember(
        activePaneState.currentPath,
        activePaneState.errorMessage,
        activePaneState.searchQuery,
        activePaneState.searchCategoryFilter,
        selectedEntriesInActivePane,
        isCurrentDirWritable,
        clipboardEntries,
        isClipboardCut,
        prefs.dualPaneEnabled,
        activePaneId,
        isActivePaneScrollingDown,
        isKeyboardOpen,
        isAnyDialogBlocking,
        prefs.fabSettings
    ) {
        val destination = when {
            activePaneState.errorMessage != null -> ExplorerScreenDestination.EMPTY_OR_ERROR
            activePaneState.searchQuery.isNotBlank() || activePaneState.searchCategoryFilter != null ->
                ExplorerScreenDestination.SEARCH_MODE
            prefs.dualPaneEnabled -> ExplorerScreenDestination.DUAL_PANE_EXPLORER
            else -> ExplorerScreenDestination.MAIN_EXPLORER
        }
        val clipState = ClipboardState(
            sourceFiles = clipboardEntries,
            action = if (clipboardEntries.isEmpty()) {
                ClipboardAction.NONE
            } else if (isClipboardCut) {
                ClipboardAction.MOVE
            } else {
                ClipboardAction.COPY
            }
        )
        QuickActionResolver.resolve(
            FabResolutionContext(
                destination = destination,
                currentPath = activePaneState.currentPath,
                isDirectoryWritable = isCurrentDirWritable,
                selectedItems = selectedEntriesInActivePane,
                clipboard = clipState,
                dualPaneEnabled = prefs.dualPaneEnabled,
                activePaneId = activePaneId,
                isSearchActive = activePaneState.searchQuery.isNotBlank() || activePaneState.searchCategoryFilter != null,
                isRecursiveSearch = true,
                isScrollingDown = isActivePaneScrollingDown,
                isKeyboardOpen = isKeyboardOpen,
                isModalSheetBlocking = isAnyDialogBlocking,
                settings = prefs.fabSettings
            )
        )
    }

    val handleQuickAction: (QuickAction) -> Unit = { action ->
        when (action.id) {
            QuickActionId.NEW_FOLDER -> showNewFolderDialog = true
            QuickActionId.NEW_FILE -> showNewNoteDialog = true
            QuickActionId.IMPORT_FILES -> {
                runCatching { importFilesLauncher.launch(arrayOf("*/*")) }
            }
            QuickActionId.MOUNT_SAF_FOLDER -> safTreeLauncher.launch(null)
            QuickActionId.PASTE_CLIPBOARD -> viewModel.pasteClipboard(activePaneId)
            QuickActionId.PIN_CURRENT_FOLDER -> viewModel.pinCurrentDirectoryToFavorites(activePaneId)
            QuickActionId.OPEN_STORAGE_ANALYZER -> {
                showStorageAnalyzerDialog = true
                viewModel.runStorageAnalysis()
            }
            QuickActionId.OPEN_SELECTED -> {
                selectedEntriesInActivePane.firstOrNull()?.let { first ->
                    viewModel.clearSelection(activePaneId)
                    viewModel.onEntryClicked(activePaneId, first)
                }
            }
            QuickActionId.OPEN_IN_NEW_TAB -> {
                selectedEntriesInActivePane.firstOrNull()?.let { first ->
                    viewModel.clearSelection(activePaneId)
                    viewModel.createNewTab(activePaneId, first.path)
                }
            }
            QuickActionId.EXTRACT_ARCHIVE_HERE -> {
                selectedEntriesInActivePane.firstOrNull()?.let { archive ->
                    viewModel.clearSelection(activePaneId)
                    viewModel.extractArchiveDirectly(archive, intoSubfolder = false)
                }
            }
            QuickActionId.EXTRACT_ARCHIVE_TO_FOLDER -> {
                selectedEntriesInActivePane.firstOrNull()?.let { archive ->
                    viewModel.clearSelection(activePaneId)
                    viewModel.extractArchiveDirectly(archive, intoSubfolder = true)
                }
            }
            QuickActionId.INSPECT_ARCHIVE -> {
                selectedEntriesInActivePane.firstOrNull()?.let { archive ->
                    viewModel.inspectArchiveFile(archive)
                }
            }
            QuickActionId.CONVERT_ARCHIVE -> {
                selectedEntriesInActivePane.firstOrNull()?.let { archive ->
                    convertTargetArchive = archive
                }
            }
            QuickActionId.COMPRESS_SELECTION -> showCompressZipDialog = true
            QuickActionId.COPY_SELECTION -> viewModel.copyToClipboard(activePaneId, isCut = false)
            QuickActionId.MOVE_SELECTION -> viewModel.copyToClipboard(activePaneId, isCut = true)
            QuickActionId.RENAME_SELECTION -> {
                selectedEntriesInActivePane.firstOrNull()?.let { renameTargetEntry = it }
            }
            QuickActionId.BATCH_RENAME_SELECTION -> showBatchRenameDialog = true
            QuickActionId.SHARE_SELECTION -> viewModel.shareSelectedFiles(activePaneId)
            QuickActionId.CALCULATE_FOLDER_SIZE -> {
                val targetPath = selectedEntriesInActivePane.firstOrNull()?.path ?: activePaneState.currentPath
                viewModel.triggerFolderIntelligence(activePaneId, targetPath, recursive = true)
            }
            QuickActionId.CALCULATE_CHECKSUMS -> viewModel.calculateChecksumsForSelection(activePaneId)
            QuickActionId.SHOW_PROPERTIES -> {
                selectedEntriesInActivePane.firstOrNull()?.let { viewModel.openPropertiesDialog(it) }
            }
            QuickActionId.DELETE_SELECTION -> viewModel.requestDeleteSelectedEntries(activePaneId)
            QuickActionId.DUAL_COPY_TO_OTHER_PANE -> viewModel.transferSelectedToOtherPane(activePaneId, isMove = false)
            QuickActionId.DUAL_MOVE_TO_OTHER_PANE -> viewModel.transferSelectedToOtherPane(activePaneId, isMove = true)
            QuickActionId.DUAL_SWAP_PANES -> viewModel.swapPanes()
            QuickActionId.DUAL_COMPARE_FOLDERS -> viewModel.compareDualPanes()
            QuickActionId.DUAL_SYNC_OTHER_PANE -> viewModel.syncOtherPaneToActivePath()
            QuickActionId.SEARCH_TOGGLE_RECURSIVE -> {
                viewModel.updateSearchQuery(
                    paneId = activePaneId,
                    query = activePaneState.searchQuery,
                    categoryFilter = activePaneState.searchCategoryFilter
                )
            }
            QuickActionId.SEARCH_CLEAR_FILTER -> {
                viewModel.updateSearchQuery(paneId = activePaneId, query = "", categoryFilter = null)
            }
            QuickActionId.OP_RETRY_ALL_FAILED -> viewModel.retryAllFailedOperations()
            QuickActionId.OP_PAUSE_ALL_RUNNING -> viewModel.pauseAllOperations()
            QuickActionId.OP_RESUME_ALL_PAUSED -> viewModel.resumeAllOperations()
            QuickActionId.OP_CLEAR_COMPLETED -> viewModel.clearCompletedOperations()
            QuickActionId.ANALYZER_RESCAN,
            QuickActionId.ANALYZER_SCAN_ACTIVE_FOLDER -> viewModel.runStorageAnalysis()
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = !showSettingsPage,
        drawerContent = {
            ExplorerNavigationDrawerSheet(
                volumes = volumes,
                termuxStatus = termuxStatus,
                permissionStatus = permissionStatus,
                favorites = favorites,
                recentFiles = recentFiles,
                sharedRootPath = workspaceRootPath,
                systemRootPath = systemRootPath,
                appSandboxPath = appSandboxPath,
                onNavigateToPath = { targetPath ->
                    viewModel.loadPaneDirectory(activePaneId, targetPath, recordHistory = true)
                    coroutineScope.launch { drawerState.close() }
                },
                onCopyPath = { pathOrUri ->
                    viewModel.copyPathOrUriString(pathOrUri)
                },
                onRequestAllFilesAccess = {
                    coroutineScope.launch { drawerState.close() }
                    launchAllFilesAccessSettings()
                },
                onOpenPermissionsDialog = {
                    coroutineScope.launch { drawerState.close() }
                    viewModel.refreshPermissionStatus()
                    showPermissionSetupDialog = true
                },
                onOpenOrMountTermux = {
                    coroutineScope.launch { drawerState.close() }
                    viewModel.openOrRequestTermuxStorage(activePaneId) { intent ->
                        runCatching { termuxSafLauncher.launch(intent) }
                            .onFailure { safTreeLauncher.launch(null) }
                    }
                },
                onLaunchTermuxApp = {
                    coroutineScope.launch { drawerState.close() }
                    viewModel.launchTermuxApp()
                },
                onOpenRecycleBin = {
                    viewModel.openRecycleBin()
                    coroutineScope.launch { drawerState.close() }
                },
                onMountSaf = {
                    coroutineScope.launch { drawerState.close() }
                    safTreeLauncher.launch(null)
                },
                onOpenSettings = {
                    coroutineScope.launch { drawerState.close() }
                    showSettingsPage = true
                }
            )
        }
    ) {
        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            topBar = {
                ExplorerTopBarAndRibbon(
                    activePaneState = activePaneState,
                    dualPaneEnabled = prefs.dualPaneEnabled,
                    hasClipboard = clipboardEntries.isNotEmpty(),
                    activeTaskCount = runningOrPausedCount,
                    isTermuxMounted = termuxStatus.isMountedOrAccessible,
                    onOpenDrawer = { coroutineScope.launch { drawerState.open() } },
                    onToggleDualPane = { viewModel.setDualPaneEnabled(!prefs.dualPaneEnabled) },
                    onSwapPanes = { viewModel.swapPanes() },
                    onComparePanes = { viewModel.compareDualPanes() },
                    onNewFolderClick = { showNewFolderDialog = true },
                    onNewNoteClick = { showNewNoteDialog = true },
                    onCopyClick = { viewModel.copyToClipboard(activePaneId, isCut = false) },
                    onCutClick = { viewModel.copyToClipboard(activePaneId, isCut = true) },
                    onPasteClick = { viewModel.pasteClipboard(activePaneId) },
                    onRenameClick = {
                        val firstSelectedPath = activePaneState.selectedPaths.firstOrNull()
                        val entry = activePaneState.entries.find { it.path == firstSelectedPath }
                        if (entry != null) renameTargetEntry = entry
                    },
                    onBatchRenameClick = { showBatchRenameDialog = true },
                    onCompressClick = { showCompressZipDialog = true },
                    onDeleteClick = { viewModel.requestDeleteSelectedEntries(activePaneId) },
                    onSelectAllClick = { viewModel.selectAll(activePaneId) },
                    onClearSelectionClick = { viewModel.clearSelection(activePaneId) },
                    onChangeViewMode = { viewModel.setViewMode(activePaneId, it) },
                    onChangeGridColumns = { viewModel.setGridColumns(activePaneId, it) },
                    onChangeSort = { field, order, placement ->
                        viewModel.setSort(activePaneId, field, order, placement)
                    },
                    onChangeGroupBy = { viewModel.setGroupBy(activePaneId, it) },
                    onOpenStorageAnalyzer = {
                        showStorageAnalyzerDialog = true
                        viewModel.runStorageAnalysis()
                    },
                    onOpenOperationCenter = { showOperationCenterDialog = true },
                    onOpenSettingsClick = { showSettingsPage = true },
                    onTermuxStorageClick = {
                        viewModel.openOrRequestTermuxStorage(activePaneId) { intent ->
                            runCatching { termuxSafLauncher.launch(intent) }
                                .onFailure { safTreeLauncher.launch(null) }
                        }
                    },
                    onMountSafClick = { safTreeLauncher.launch(null) },
                    onCopyPathClick = { viewModel.copySelectedOrCurrentPath(activePaneId) },
                    onExtractSelectedArchiveHere = { entry ->
                        viewModel.extractArchiveDirectly(entry, intoSubfolder = false)
                    },
                    onExtractSelectedArchiveToFolder = { entry ->
                        viewModel.extractArchiveDirectly(entry, intoSubfolder = true)
                    },
                    onConvertSelectedArchive = { entry ->
                        convertTargetArchive = entry
                    }
                )
            }
        ) { innerPadding ->
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                val isWideScreen = maxWidth >= 640.dp
                val halfWidthDp = (maxWidth / 2).value.toInt()

                Box(modifier = Modifier.fillMaxSize()) {
                    if (!prefs.dualPaneEnabled) {
                        ExplorerPaneWrapper(
                            paneId = PaneId.LEFT,
                            paneState = leftPane,
                            isActivePane = true,
                            dualPaneEnabled = false,
                            workspaceRootPath = workspaceRootPath,
                            favoritePaths = favoritePathSet,
                            customFolderIcons = customFolderIcons,
                            enableDeviconIcons = prefs.enableDeviconIcons,
                            enableSmartFolderBadges = prefs.enableSmartFolderBadges,
                            viewModel = viewModel,
                            onRenameRequest = { renameTargetEntry = it },
                            onConvertArchiveRequest = { convertTargetArchive = it },
                            onScrollDirectionChanged = { isActivePaneScrollingDown = it },
                            modifier = Modifier.fillMaxSize()
                        )
                    } else if (isWideScreen) {
                        Row(modifier = Modifier.fillMaxSize()) {
                            ExplorerPaneWrapper(
                                paneId = PaneId.LEFT,
                                paneState = leftPane,
                                isActivePane = activePaneId == PaneId.LEFT,
                                dualPaneEnabled = true,
                                workspaceRootPath = workspaceRootPath,
                                favoritePaths = favoritePathSet,
                                customFolderIcons = customFolderIcons,
                                enableDeviconIcons = prefs.enableDeviconIcons,
                                enableSmartFolderBadges = prefs.enableSmartFolderBadges,
                                viewModel = viewModel,
                                onRenameRequest = { renameTargetEntry = it },
                                onConvertArchiveRequest = { convertTargetArchive = it },
                                onScrollDirectionChanged = {
                                    if (activePaneId == PaneId.LEFT) isActivePaneScrollingDown = it
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                            )
                            VerticalDivider(
                                thickness = 2.dp,
                                color = MaterialTheme.colorScheme.outlineVariant
                            )
                            ExplorerPaneWrapper(
                                paneId = PaneId.RIGHT,
                                paneState = rightPane,
                                isActivePane = activePaneId == PaneId.RIGHT,
                                dualPaneEnabled = true,
                                workspaceRootPath = workspaceRootPath,
                                favoritePaths = favoritePathSet,
                                customFolderIcons = customFolderIcons,
                                enableDeviconIcons = prefs.enableDeviconIcons,
                                enableSmartFolderBadges = prefs.enableSmartFolderBadges,
                                viewModel = viewModel,
                                onRenameRequest = { renameTargetEntry = it },
                                onConvertArchiveRequest = { convertTargetArchive = it },
                                onScrollDirectionChanged = {
                                    if (activePaneId == PaneId.RIGHT) isActivePaneScrollingDown = it
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                            )
                        }
                    } else {
                        Column(modifier = Modifier.fillMaxSize()) {
                            ExplorerPaneWrapper(
                                paneId = PaneId.LEFT,
                                paneState = leftPane,
                                isActivePane = activePaneId == PaneId.LEFT,
                                dualPaneEnabled = true,
                                workspaceRootPath = workspaceRootPath,
                                favoritePaths = favoritePathSet,
                                customFolderIcons = customFolderIcons,
                                enableDeviconIcons = prefs.enableDeviconIcons,
                                enableSmartFolderBadges = prefs.enableSmartFolderBadges,
                                viewModel = viewModel,
                                onRenameRequest = { renameTargetEntry = it },
                                onConvertArchiveRequest = { convertTargetArchive = it },
                                onScrollDirectionChanged = {
                                    if (activePaneId == PaneId.LEFT) isActivePaneScrollingDown = it
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                            )
                            HorizontalDivider(
                                thickness = 2.dp,
                                color = MaterialTheme.colorScheme.outlineVariant
                            )
                            ExplorerPaneWrapper(
                                paneId = PaneId.RIGHT,
                                paneState = rightPane,
                                isActivePane = activePaneId == PaneId.RIGHT,
                                dualPaneEnabled = true,
                                workspaceRootPath = workspaceRootPath,
                                favoritePaths = favoritePathSet,
                                customFolderIcons = customFolderIcons,
                                enableDeviconIcons = prefs.enableDeviconIcons,
                                enableSmartFolderBadges = prefs.enableSmartFolderBadges,
                                viewModel = viewModel,
                                onRenameRequest = { renameTargetEntry = it },
                                onConvertArchiveRequest = { convertTargetArchive = it },
                                onScrollDirectionChanged = {
                                    if (activePaneId == PaneId.RIGHT) isActivePaneScrollingDown = it
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                            )
                        }
                    }

                    // Compact Floating Operation Indicator when background tasks are active/paused
                    FloatingOperationIndicator(
                        tasks = activeTasks,
                        onOpenOperationCenter = { showOperationCenterDialog = true },
                        onPauseTask = { viewModel.pauseOperation(it) },
                        onResumeTask = { viewModel.resumeOperation(it) },
                        onCancelTask = { viewModel.cancelOperation(it) },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 26.dp)
                    )

                    // Universal Context-Aware Explorer FAB & Speed Dial System
                    val fabBottomOffset = if (runningOrPausedCount > 0) 108 else 32
                    val fabEndOffset = if (
                        prefs.dualPaneEnabled &&
                        isWideScreen &&
                        prefs.fabSettings.placementMode == FabPlacementMode.CONTENT_AWARE &&
                        activePaneId == PaneId.LEFT
                    ) {
                        halfWidthDp + 16
                    } else {
                        16
                    }

                    ContextAwareExplorerFabHost(
                        model = resolvedFabModel,
                        settings = prefs.fabSettings,
                        onActionSelected = handleQuickAction,
                        bottomOffsetPadding = fabBottomOffset,
                        endOffsetPadding = fabEndOffset
                    )
                }
            }
        }
    }

    // --- Modals, Viewers & Universal Dialog System ---

    val activeSiblingNames = remember(activePaneState.entries) {
        activePaneState.entries.map { it.name }.toSet()
    }

    if (showNewFolderDialog) {
        SingleInputPromptDialog(
            title = "Create New Folder",
            label = "Folder Name",
            existingSiblingNames = activeSiblingNames,
            helperText = "Enter a valid folder name (no slashes or reserved names)",
            onConfirm = { folderName, _ ->
                showNewFolderDialog = false
                viewModel.createNewFolder(activePaneId, folderName)
            },
            onDismiss = { showNewFolderDialog = false }
        )
    }

    if (showNewNoteDialog) {
        SingleInputPromptDialog(
            title = "Create New File",
            label = "File Name",
            existingSiblingNames = activeSiblingNames,
            helperText = "e.g., notes.txt, build.sh, config.json",
            onConfirm = { fileName, _ ->
                showNewNoteDialog = false
                viewModel.createNewNoteFile(activePaneId, fileName, "")
            },
            onDismiss = { showNewNoteDialog = false }
        )
    }

    renameTargetEntry?.let { target ->
        SingleInputPromptDialog(
            title = "Rename Item",
            label = "New Name",
            initialValue = target.name,
            existingSiblingNames = activeSiblingNames,
            originalName = target.name,
            helperText = "Rename '${target.name}' in place",
            onConfirm = { newName, _ ->
                renameTargetEntry = null
                viewModel.renameFileEntry(target.path, newName)
            },
            onDismiss = { renameTargetEntry = null }
        )
    }

    if (showBatchRenameDialog) {
        val selectedFiles = activePaneState.entries.filter {
            activePaneState.selectedPaths.contains(it.path)
        }
        BatchRenameDialog(
            selectedFiles = selectedFiles,
            onGeneratePreview = { files, cfg -> viewModel.buildBatchRenamePreview(files, cfg) },
            onConfirmBatchRename = { cfg ->
                showBatchRenameDialog = false
                viewModel.applyBatchRename(selectedFiles, cfg)
            },
            onDismiss = { showBatchRenameDialog = false }
        )
    }

    if (showCompressZipDialog) {
        val defaultName = if (selectedEntriesInActivePane.size == 1) {
            selectedEntriesInActivePane.first().name.substringBeforeLast('.').ifBlank {
                selectedEntriesInActivePane.first().name
            }
        } else {
            "Archive_Bundle"
        }
        CreateArchiveDialog(
            selectedCount = activePaneState.selectedPaths.size,
            defaultBaseName = defaultName,
            initialFormat = com.explorerpro.domain.model.ArchiveCreateFormat.ZIP,
            onConfirmCreateArchive = { config, _ ->
                showCompressZipDialog = false
                viewModel.createArchiveWithConfig(activePaneId, config)
            },
            onDismiss = { showCompressZipDialog = false }
        )
    }

    convertTargetArchive?.let { srcArchive ->
        val defaultConvertName = srcArchive.name.let { name ->
            val lower = name.lowercase()
            val multi = listOf(".tar.gz", ".tar.bz2", ".tar.xz", ".tar.lz4", ".tar.zstd", ".tar.zst", ".tar.lzma")
                .firstOrNull { lower.endsWith(it) }
            when {
                multi != null -> name.dropLast(multi.length)
                name.contains('.') -> name.substringBeforeLast('.')
                else -> name
            }.ifBlank { "Converted_Archive" }
        }
        val defaultTargetFormat = if (srcArchive.name.lowercase().endsWith(".zip")) {
            com.explorerpro.domain.model.ArchiveCreateFormat.SEVEN_Z
        } else {
            com.explorerpro.domain.model.ArchiveCreateFormat.ZIP
        }
        CreateArchiveDialog(
            selectedCount = 1,
            defaultBaseName = defaultConvertName,
            sourceArchiveToConvert = srcArchive,
            initialFormat = defaultTargetFormat,
            onConfirmCreateArchive = { config, srcPassword ->
                convertTargetArchive = null
                viewModel.convertArchiveFormat(srcArchive, config, srcPassword)
            },
            onDismiss = { convertTargetArchive = null }
        )
    }

    inspectedArchive?.let { archive ->
        ArchiveInspectorDialog(
            archiveFile = archive,
            entries = archiveEntries,
            verificationStatus = archiveVerifyMsg,
            onVerifyIntegrity = { password -> viewModel.verifyActiveArchiveIntegrity(password) },
            onExtractHere = { password ->
                viewModel.closeArchiveInspector()
                viewModel.extractArchiveDirectly(archive, intoSubfolder = false, password = password)
            },
            onExtractAll = { password ->
                viewModel.extractActiveArchive(null, password = password, intoSubfolder = true)
            },
            onExtractSelected = { selectedSet, password ->
                viewModel.extractActiveArchive(selectedSet, password = password, intoSubfolder = true)
            },
            onConvertArchive = {
                viewModel.closeArchiveInspector()
                convertTargetArchive = archive
            },
            onDismiss = { viewModel.closeArchiveInspector() }
        )
    }

    activeImageEntry?.let { img ->
        FullScreenImageViewerDialog(
            currentImage = img,
            galleryList = galleryImages,
            onSelectImage = { viewModel.selectImageInViewer(it) },
            onShare = { viewModel.shareFileEntry(it) },
            onToggleFavorite = { viewModel.toggleFavorite(it) },
            onDelete = { viewModel.requestDeleteSingleEntry(it) },
            onDismiss = { viewModel.closeImageViewer() }
        )
    }

    propertiesEntry?.let { propEntry ->
        FilePropertiesDialog(
            entry = propEntry,
            sha256Checksum = propertiesSha256,
            onCopyText = { text, label ->
                viewModel.copyTextToSystemClipboard(
                    text = text,
                    clipLabel = label,
                    confirmationMessage = "Copied $label: $text"
                )
            },
            onShare = { viewModel.shareFileEntry(it) },
            onToggleFavorite = { viewModel.toggleFavorite(it) },
            onDismiss = { viewModel.closePropertiesDialog() }
        )
    }

    textViewerEntry?.let { (fileEntry, content) ->
        TextFileEditorDialog(
            entry = fileEntry,
            initialContent = content,
            onSave = { updated -> viewModel.saveTextViewerContent(fileEntry, updated) },
            onDismiss = { viewModel.closeTextViewer() }
        )
    }

    folderComparison?.let { comparison ->
        FolderComparisonDialog(
            comparison = comparison,
            onDismiss = { viewModel.dismissFolderComparison() }
        )
    }

    if (showStorageAnalyzerDialog) {
        val activeScanTask = activeTasks.firstOrNull {
            it.type == OperationType.STORAGE_ANALYSIS &&
                (it.status == OperationStatus.RUNNING || it.status == OperationStatus.PAUSED)
        }
        StorageAnalyzerDialog(
            report = storageReport,
            isAnalyzing = isAnalyzingStorage,
            activeScanTask = activeScanTask,
            onDeleteDuplicateFile = { pathToDelete ->
                viewModel.deletePaths(listOf(pathToDelete), permanent = false)
                viewModel.runStorageAnalysis()
            },
            onDismiss = {
                showStorageAnalyzerDialog = false
                viewModel.closeStorageAnalysis()
            }
        )
    }

    if (showOperationCenterDialog) {
        OperationCenterModal(
            tasks = activeTasks,
            historyLogs = operationLogs,
            onPauseTask = { viewModel.pauseOperation(it) },
            onResumeTask = { viewModel.resumeOperation(it) },
            onCancelTask = { viewModel.cancelOperation(it) },
            onRetryTask = { viewModel.retryOperation(it) },
            onDismissTask = { viewModel.dismissOperation(it) },
            onClearCompleted = { viewModel.clearCompletedOperations() },
            onPauseAll = { viewModel.pauseAllOperations() },
            onResumeAll = { viewModel.resumeAllOperations() },
            onRetryAllFailed = { viewModel.retryAllFailedOperations() },
            fabSettings = prefs.fabSettings,
            onDismiss = { showOperationCenterDialog = false }
        )
    }

    // Specialized File Conflict Dialog (Replace, Skip, Keep Both, Custom Rename, Compare Files, Apply to All)
    pendingTransferConflict?.let { conflictState ->
        val destExistingNames = remember(
            conflictState.destinationDirPath,
            leftPane.entries,
            rightPane.entries
        ) {
            when (conflictState.destinationDirPath) {
                leftPane.currentPath -> leftPane.entries.map { it.name }.toSet()
                rightPane.currentPath -> rightPane.entries.map { it.name }.toSet()
                else -> conflictState.conflicts.map { it.destinationEntry.name }.toSet()
            }
        }
        ExplorerConflictDialog(
            conflictState = conflictState,
            existingDestinationNames = destExistingNames,
            onResolveConflict = { decision, customNewName, applyToAll ->
                viewModel.resolveCurrentTransferConflict(
                    decision = decision,
                    customNewName = customNewName,
                    applyToAll = applyToAll
                )
            },
            onInspectEntryProperties = { entry ->
                viewModel.openPropertiesDialog(entry)
            },
            onCancelTransfer = { viewModel.cancelPendingTransferConflict() }
        )
    }

    // Universal Delete / Recycle Bin Confirmation Dialog
    deleteConfirmationRequest?.let { req ->
        val itemCount = req.entries.size
        val firstItemName = req.entries.firstOrNull()?.name ?: "selected items"
        val isLargeOrMulti = itemCount > 5 || req.isRecycleBinClearRequest
        ExplorerConfirmationDialog(
            title = when {
                req.isRecycleBinClearRequest -> "Permanently Delete from Recycle Bin?"
                itemCount == 1 -> "Delete '$firstItemName'?"
                else -> "Delete $itemCount Selected Items?"
            },
            consequenceMessage = if (req.defaultUseRecycleBin) {
                "Items will be moved to the Explorer Pro Recycle Bin so you can restore them later, unless you select permanent deletion below."
            } else {
                "These items will be permanently removed from storage and cannot be recovered."
            },
            summaryBadgeText = "$itemCount item(s) • ${FormatDetectorAndMetadataEngine.formatBytes(req.totalBytes)}",
            confirmLabel = if (req.defaultUseRecycleBin) "Delete" else "Permanently Delete",
            isDestructive = true,
            secondaryOptionLabel = if (req.defaultUseRecycleBin) {
                "Permanently delete immediately (bypass Recycle Bin)"
            } else null,
            rememberChoiceLabel = if (req.defaultUseRecycleBin && !isLargeOrMulti) {
                "Don't ask again for Recycle Bin moves during this session"
            } else null,
            requireRiskyConfirmationCheckbox = !req.defaultUseRecycleBin || isLargeOrMulti,
            riskyConfirmationLabel = "I understand permanent deletion cannot be undone",
            onConfirm = { rememberSession, permanentChecked ->
                viewModel.confirmPendingDelete(
                    permanentDelete = permanentChecked || !req.defaultUseRecycleBin,
                    rememberChoiceForSession = rememberSession
                )
            },
            onDismiss = { viewModel.dismissPendingDelete() }
        )
    }

    // Focused Progress Dialog (Dismissing continues operation in background)
    activeProgressDialogTaskId?.let { taskId ->
        val task = activeTasks.find { it.id == taskId }
        if (task != null) {
            ExplorerProgressDialog(
                task = task,
                onPause = { viewModel.pauseOperation(it) },
                onResume = { viewModel.resumeOperation(it) },
                onCancel = { viewModel.cancelOperation(it) },
                onRetry = { viewModel.retryOperation(it) },
                onOpenOperationCenter = { showOperationCenterDialog = true },
                onDismissToBackground = { viewModel.dismissProgressDialogToBackground() }
            )
        } else {
            viewModel.dismissProgressDialogToBackground()
        }
    }

    // Centralized Notice / Error / Success / Alert Dialog
    noticeDialog?.let { notice ->
        when (notice.severity) {
            ExplorerNoticeSeverity.ERROR -> {
                ExplorerErrorDialog(
                    title = notice.title,
                    message = notice.message,
                    technicalDetails = notice.technicalDetails,
                    onCopyDetails = { text ->
                        viewModel.copyTextToSystemClipboard(
                            text = text,
                            clipLabel = "Error Diagnostics",
                            confirmationMessage = "Copied error diagnostics to clipboard"
                        )
                    },
                    onRetry = notice.onRetryAction,
                    onDismiss = { viewModel.dismissNoticeDialog() }
                )
            }
            ExplorerNoticeSeverity.SUCCESS -> {
                ExplorerSuccessDialog(
                    title = notice.title,
                    message = notice.message,
                    destinationPath = notice.destinationPath,
                    onOpenDestination = { dest ->
                        viewModel.loadPaneDirectory(activePaneId, dest, recordHistory = true)
                    },
                    onDismiss = { viewModel.dismissNoticeDialog() }
                )
            }
            else -> {
                ExplorerAlertDialog(
                    title = notice.title,
                    supportingText = notice.message,
                    confirmLabel = notice.primaryActionLabel,
                    dismissLabel = notice.secondaryActionLabel,
                    onConfirm = { viewModel.dismissNoticeDialog() },
                    onDismiss = { viewModel.dismissNoticeDialog() }
                )
            }
        }
    }

    // Contextual File / Folder Action Modal Bottom Sheet
    actionSheetEntry?.let { sheetEntry ->
        ExplorerFileActionBottomSheet(
            entry = sheetEntry,
            isFavorite = favoritePathSet.contains(sheetEntry.path),
            dualPaneEnabled = prefs.dualPaneEnabled,
            onOpen = { viewModel.onEntryClicked(activePaneId, sheetEntry) },
            onOpenInNewTab = { viewModel.createNewTab(activePaneId, sheetEntry.path) },
            onExtractHere = { viewModel.extractArchiveDirectly(sheetEntry, intoSubfolder = false) },
            onExtractToSubfolder = { viewModel.extractArchiveDirectly(sheetEntry, intoSubfolder = true) },
            onInspectArchive = { viewModel.inspectArchiveFile(sheetEntry) },
            onConvertArchive = { convertTargetArchive = sheetEntry },
            onCopy = {
                viewModel.toggleSelection(activePaneId, sheetEntry.path)
                viewModel.copyToClipboard(activePaneId, isCut = false)
            },
            onMove = {
                viewModel.toggleSelection(activePaneId, sheetEntry.path)
                viewModel.copyToClipboard(activePaneId, isCut = true)
            },
            onCopyToOtherPane = {
                viewModel.toggleSelection(activePaneId, sheetEntry.path)
                viewModel.transferSelectedToOtherPane(activePaneId, isMove = false)
            },
            onMoveToOtherPane = {
                viewModel.toggleSelection(activePaneId, sheetEntry.path)
                viewModel.transferSelectedToOtherPane(activePaneId, isMove = true)
            },
            onRename = { renameTargetEntry = sheetEntry },
            onCompressToArchive = {
                viewModel.toggleSelection(activePaneId, sheetEntry.path)
                showCompressZipDialog = true
            },
            onCopyFullPath = { viewModel.copyEntryFullPath(sheetEntry, preferRawSafUri = false) },
            onCopyFileName = { viewModel.copyEntryFileName(sheetEntry) },
            onToggleFavorite = { viewModel.toggleFavorite(sheetEntry) },
            onSetCustomIcon = { viewModel.openCustomIconPicker(sheetEntry) },
            onShare = { viewModel.shareFileEntry(sheetEntry) },
            onProperties = { viewModel.openPropertiesDialog(sheetEntry) },
            onDelete = { viewModel.requestDeleteSingleEntry(sheetEntry) },
            onDismiss = { viewModel.closeFileActionBottomSheet() }
        )
    }

    // Full-Page Gear-Icon Managed Settings Screen
    if (showSettingsPage) {
        ExplorerSettingsScreen(
            prefs = prefs,
            permissionStatus = permissionStatus,
            termuxStatus = termuxStatus,
            onNavigateBack = { showSettingsPage = false },
            onSetTheme = { viewModel.setThemePreference(it) },
            onToggleDynamicColor = { viewModel.setDynamicColor(it) },
            onToggleHiddenFiles = { viewModel.setShowHiddenFiles(it) },
            onToggleDualPane = { viewModel.setDualPaneEnabled(it) },
            onToggleRecycleBin = { viewModel.setRecycleBinEnabled(it) },
            onSetConflictResolution = { viewModel.setConflictResolution(it) },
            onUpdateFabSettings = { transform -> viewModel.updateFabSettings(transform) },
            onOpenPermissionsDialog = {
                viewModel.refreshPermissionStatus()
                showPermissionSetupDialog = true
            },
            onOpenOrMountTermux = {
                viewModel.openOrRequestTermuxStorage(activePaneId) { intent ->
                    runCatching { termuxSafLauncher.launch(intent) }
                        .onFailure { safTreeLauncher.launch(null) }
                }
            },
            onToggleDeviconIcons = { viewModel.setEnableDeviconIcons(it) },
            onToggleSmartFolderBadges = { viewModel.setEnableSmartFolderBadges(it) },
            onOpenDeviconCatalogBrowser = { viewModel.openDeviconCatalogBrowser() }
        )
    }

    // Devicon Picker / Full 657-Icon Catalog Dialog
    if (showDeviconBrowserDialog) {
        val targetFolder = iconPickerTargetEntry
        DeviconPickerDialog(
            targetFolderEntry = targetFolder,
            currentCustomIconId = targetFolder?.let { customFolderIcons[it.path] },
            onSelectIcon = { chosenId ->
                if (targetFolder != null) {
                    viewModel.setCustomFolderIcon(targetFolder.path, chosenId)
                }
            },
            onClearCustomIcon = {
                if (targetFolder != null) {
                    viewModel.clearCustomFolderIcon(targetFolder.path)
                }
            },
            onDismiss = { viewModel.closeDeviconPicker() }
        )
    }

    // First-Launch & Live Permission Setup Dialog
    if (showPermissionSetupDialog) {
        ExplorerFirstLaunchPermissionDialog(
            permissionStatus = permissionStatus,
            onGrantAllRequired = launchGrantAllRequiredPermissions,
            onRequestStorageAccess = {
                if (permissionStatus.requiresManageExternalStorage) {
                    launchAllFilesAccessSettings()
                } else {
                    val storagePerms = buildList {
                        add(Manifest.permission.READ_EXTERNAL_STORAGE)
                        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q) {
                            add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                        }
                    }
                    runCatching {
                        multiplePermissionsLauncher.launch(storagePerms.toTypedArray())
                    }.onFailure {
                        launchAllFilesAccessSettings()
                    }
                }
            },
            onRequestNotificationPermission = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    runCatching {
                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }.onFailure {
                        runCatching {
                            allFilesAccessLauncher.launch(
                                viewModel.fileSystemRepository.createNotificationSettingsIntent()
                            )
                        }
                    }
                }
            },
            onRequestTermuxMount = {
                viewModel.openOrRequestTermuxStorage(activePaneId) { intent ->
                    runCatching { termuxSafLauncher.launch(intent) }
                        .onFailure { safTreeLauncher.launch(null) }
                }
            },
            onOpenSystemAppSettings = {
                runCatching {
                    allFilesAccessLauncher.launch(
                        viewModel.fileSystemRepository.createAppDetailsSettingsIntent()
                    )
                }
            },
            onCompleteOrContinue = {
                viewModel.completeFirstLaunchPermissionsSetup()
                showPermissionSetupDialog = false
            }
        )
    }
}

@Composable
private fun ExplorerPaneWrapper(
    paneId: PaneId,
    paneState: com.explorerpro.domain.model.PaneState,
    isActivePane: Boolean,
    dualPaneEnabled: Boolean,
    workspaceRootPath: String,
    favoritePaths: Set<String>,
    customFolderIcons: Map<String, String> = emptyMap(),
    enableDeviconIcons: Boolean = true,
    enableSmartFolderBadges: Boolean = true,
    viewModel: ExplorerViewModel,
    onRenameRequest: (FileEntry) -> Unit,
    onConvertArchiveRequest: (FileEntry) -> Unit = {},
    onScrollDirectionChanged: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier
) {
    ExplorerPaneContainer(
        paneState = paneState,
        isActivePane = isActivePane,
        dualPaneEnabled = dualPaneEnabled,
        workspaceRootPath = workspaceRootPath,
        favoritePaths = favoritePaths,
        onActivatePane = { viewModel.setActivePane(paneId) },
        onSelectTab = { viewModel.switchTab(paneId, it) },
        onCloseTab = { viewModel.closeTab(paneId, it) },
        onNewTab = { viewModel.createNewTab(paneId) },
        onDuplicateTab = { viewModel.duplicateActiveTab(paneId) },
        onReopenClosedTab = { viewModel.reopenLastClosedTab(paneId) },
        onNavigateBack = { viewModel.navigateBackInTab(paneId) },
        onNavigateForward = { viewModel.navigateForwardInTab(paneId) },
        onNavigateToPath = { viewModel.loadPaneDirectory(paneId, it, recordHistory = true) },
        onCopyPath = { viewModel.copyPathOrUriString(it) },
        onSearchQueryChange = { q, cat -> viewModel.updateSearchQuery(paneId, q, cat) },
        onEntryClick = { viewModel.onEntryClicked(paneId, it) },
        onEntryLongClick = { viewModel.toggleSelection(paneId, it.path) },
        onOpenInNewTab = { viewModel.createNewTab(paneId, it.path) },
        onToggleFavorite = { viewModel.toggleFavorite(it) },
        onCopyEntryPath = { viewModel.copyEntryFullPath(it, preferRawSafUri = false) },
        onCopyEntryName = { viewModel.copyEntryFileName(it) },
        onCopyEntrySafUri = { viewModel.copyEntryFullPath(it, preferRawSafUri = true) },
        onRenameEntry = onRenameRequest,
        onInspectProperties = { viewModel.openPropertiesDialog(it) },
        onShareEntry = { viewModel.shareFileEntry(it) },
        onDeleteEntry = { viewModel.requestDeleteSingleEntry(it) },
        onExtractArchive = { viewModel.inspectArchiveFile(it) },
        onExtractArchiveHere = {
            viewModel.setActivePane(paneId)
            viewModel.extractArchiveDirectly(it, intoSubfolder = false)
        },
        onExtractArchiveToFolder = {
            viewModel.setActivePane(paneId)
            viewModel.extractArchiveDirectly(it, intoSubfolder = true)
        },
        onConvertArchive = {
            viewModel.setActivePane(paneId)
            onConvertArchiveRequest(it)
        },
        onCopyToOtherPane = { viewModel.transferSelectedToOtherPane(paneId, isMove = false) },
        onMoveToOtherPane = { viewModel.transferSelectedToOtherPane(paneId, isMove = true) },
        onHeaderSortClick = { clickedField ->
            val currentSort = paneState.sortConfig
            val nextOrder = if (currentSort.field == clickedField && currentSort.order == SortOrder.ASCENDING) {
                SortOrder.DESCENDING
            } else {
                SortOrder.ASCENDING
            }
            viewModel.setSort(paneId, clickedField, nextOrder, currentSort.folderPlacement)
        },
        onRequestDeepFolderStats = {
            viewModel.triggerFolderIntelligence(paneId, paneState.currentPath, recursive = true)
        },
        onScrollDirectionChanged = onScrollDirectionChanged,
        onOpenActionSheet = {
            viewModel.setActivePane(paneId)
            viewModel.openFileActionBottomSheet(it)
        },
        customFolderIcons = customFolderIcons,
        enableDeviconIcons = enableDeviconIcons,
        enableSmartFolderBadges = enableSmartFolderBadges,
        onSetCustomIcon = { viewModel.openCustomIconPicker(it) },
        modifier = modifier
    )
}

@Composable
private fun ExplorerNavigationDrawerSheet(
    volumes: List<com.explorerpro.domain.model.StorageVolumeSummary>,
    termuxStatus: TermuxIntegrationStatus,
    permissionStatus: com.explorerpro.domain.model.AppPermissionStatus,
    favorites: List<com.explorerpro.data.local.FavoriteEntity>,
    recentFiles: List<com.explorerpro.data.local.RecentFileEntity>,
    sharedRootPath: String,
    systemRootPath: String,
    appSandboxPath: String,
    onNavigateToPath: (String) -> Unit,
    onCopyPath: (String) -> Unit,
    onRequestAllFilesAccess: () -> Unit,
    onOpenPermissionsDialog: () -> Unit,
    onOpenOrMountTermux: () -> Unit,
    onLaunchTermuxApp: () -> Unit,
    onOpenRecycleBin: () -> Unit,
    onMountSaf: () -> Unit,
    onOpenSettings: () -> Unit
) {
    ModalDrawerSheet(modifier = Modifier.width(320.dp)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Explorer Pro",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Native File & Termux Storage Manager",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(
                    onClick = onOpenSettings,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("drawer_header_settings_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Open Settings",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Surface(
                color = if (permissionStatus.areAllCorePermissionsGranted) {
                    FluentEmerald400.copy(alpha = 0.14f)
                } else {
                    FluentAmber400.copy(alpha = 0.16f)
                },
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { onOpenPermissionsDialog() }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.VerifiedUser,
                            contentDescription = null,
                            tint = if (permissionStatus.areAllCorePermissionsGranted) {
                                FluentEmerald400
                            } else {
                                FluentAmber400
                            },
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(
                                text = if (permissionStatus.areAllCorePermissionsGranted) {
                                    "All Permissions Active"
                                } else {
                                    "Permissions Required • Tap to Grant"
                                },
                                style = MaterialTheme.typography.labelLarge,
                                color = if (permissionStatus.areAllCorePermissionsGranted) {
                                    FluentEmerald400
                                } else {
                                    FluentAmber400
                                }
                            )
                            Text(
                                text = "Storage: ${if (permissionStatus.isStorageAccessGranted) "Granted" else "Missing"} • Notifications: ${if (permissionStatus.isNotificationPermissionGranted) "Granted" else "Missing"}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            // 1. Dedicated Real Termux ($HOME) Auto-Mount Integration Panel
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Termux Environment",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                Surface(
                    color = when {
                        termuxStatus.isMountedOrAccessible -> FluentEmerald400.copy(alpha = 0.2f)
                        termuxStatus.isTermuxInstalled || termuxStatus.isDocumentsProviderAvailable -> FluentAmber400.copy(alpha = 0.2f)
                        else -> MaterialTheme.colorScheme.primaryContainer
                    },
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = when {
                            termuxStatus.isMountedOrAccessible -> "AUTO-MOUNTED • LIVE"
                            termuxStatus.isTermuxInstalled || termuxStatus.isDocumentsProviderAvailable -> "DETECTED • AUTO-MOUNT"
                            else -> "AUTO-WATCHING"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = when {
                            termuxStatus.isMountedOrAccessible -> FluentEmerald400
                            termuxStatus.isTermuxInstalled || termuxStatus.isDocumentsProviderAvailable -> FluentAmber400
                            else -> MaterialTheme.colorScheme.onPrimaryContainer
                        },
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Terminal,
                            contentDescription = null,
                            tint = if (termuxStatus.isMountedOrAccessible) FluentEmerald400 else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Termux (\$HOME)",
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                text = when {
                                    termuxStatus.grantedTermuxTreeUris.isNotEmpty() ->
                                        "Auto-Mounted SAF (${termuxStatus.termuxVersionName ?: "com.termux.documents"}) • /data/data/com.termux/files/home"
                                    termuxStatus.isDirectHomeAccessible ->
                                        "Auto-Mounted POSIX • /data/data/com.termux/files/home"
                                    termuxStatus.isExternalTermuxDirAccessible ->
                                        "Auto-Mounted Shared • ${termuxStatus.externalTermuxDirPath}"
                                    termuxStatus.isTermuxInstalled || termuxStatus.isDocumentsProviderAvailable ->
                                        "Active on Device (${termuxStatus.termuxVersionName ?: "com.termux"}) • Provider com.termux.documents Ready"
                                    else ->
                                        "Live Auto-Mount Watcher Active • Waiting for com.termux / com.termux.documents"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    if (termuxStatus.isMountedOrAccessible && termuxStatus.totalBytes > 0L) {
                        val usedBytes = (termuxStatus.totalBytes - termuxStatus.freeBytes).coerceAtLeast(0L)
                        val usedFrac = (usedBytes.toFloat() / termuxStatus.totalBytes.toFloat()).coerceIn(0f, 1f)
                        LinearProgressIndicator(
                            progress = { usedFrac },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(5.dp)
                                .clip(RoundedCornerShape(3.dp))
                        )
                        Text(
                            text = "${FormatDetectorAndMetadataEngine.formatBytes(usedBytes)} used • ${FormatDetectorAndMetadataEngine.formatBytes(termuxStatus.freeBytes)} free of ${FormatDetectorAndMetadataEngine.formatBytes(termuxStatus.totalBytes)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Button(
                        onClick = onOpenOrMountTermux,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Terminal, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            when {
                                termuxStatus.isMountedOrAccessible -> "Open Auto-Mounted Termux \$HOME"
                                termuxStatus.isTermuxInstalled || termuxStatus.isDocumentsProviderAvailable -> "Complete Termux Auto-Mount"
                                else -> "Connect Termux \$HOME (SAF)"
                            }
                        )
                    }

                    if (termuxStatus.isTermuxInstalled) {
                        OutlinedButton(
                            onClick = onLaunchTermuxApp,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Terminal, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Launch Termux App", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            // 2. Real OS Hardware & Partition Storage Volumes
            Text("Storage Volumes", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            volumes.forEach { vol ->
                val usedFraction = if (vol.totalBytes > 0) {
                    (vol.usedBytes.toFloat() / vol.totalBytes.toFloat()).coerceIn(0f, 1f)
                } else 0f

                val volIcon = when {
                    vol.id == "primary_shared" -> Icons.Default.PhoneAndroid
                    vol.id == "system_partition" -> Icons.Default.Memory
                    vol.id.startsWith("termux") -> Icons.Default.Terminal
                    else -> Icons.Default.SdStorage
                }

                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onNavigateToPath(vol.rootPath) }
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = volIcon,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(vol.title, style = MaterialTheme.typography.titleSmall)
                                if (vol.subtitle.isNotEmpty()) {
                                    Text(
                                        text = vol.subtitle,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            IconButton(
                                onClick = { onCopyPath(vol.rootPath) },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = "Copy volume path",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        LinearProgressIndicator(
                            progress = { usedFraction },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(5.dp)
                                .clip(RoundedCornerShape(3.dp))
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "${FormatDetectorAndMetadataEngine.formatBytes(vol.usedBytes)} used • ${FormatDetectorAndMetadataEngine.formatBytes(vol.freeBytes)} free of ${FormatDetectorAndMetadataEngine.formatBytes(vol.totalBytes)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (vol.requiresAllFilesAccess) {
                            Spacer(Modifier.height(6.dp))
                            OutlinedButton(
                                onClick = onRequestAllFilesAccess,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(
                                    imageVector = Icons.Default.LockOpen,
                                    contentDescription = null,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text("Grant All Files Access", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
            }

            // 3. Real Android Device Quick Access Folders
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            Text("Quick Access", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)

            val quickFolders = listOf(
                Triple("Internal Storage", Icons.Default.PhoneAndroid, sharedRootPath),
                Triple("Downloads", Icons.Default.Download, "$sharedRootPath/Download"),
                Triple("Documents", Icons.Default.Description, "$sharedRootPath/Documents"),
                Triple("Pictures", Icons.Default.Image, "$sharedRootPath/Pictures"),
                Triple("DCIM (Camera)", Icons.Default.CameraAlt, "$sharedRootPath/DCIM"),
                Triple("Music", Icons.Default.AudioFile, "$sharedRootPath/Music"),
                Triple("Movies", Icons.Default.VideoFile, "$sharedRootPath/Movies"),
                Triple("System (/system)", Icons.Default.Memory, systemRootPath),
                Triple("App Private Sandbox", Icons.Default.Code, appSandboxPath)
            )
            quickFolders.forEach { (label, icon, path) ->
                NavigationDrawerItem(
                    label = { Text(label, style = MaterialTheme.typography.bodyMedium) },
                    selected = false,
                    icon = { Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    onClick = { onNavigateToPath(path) },
                    modifier = Modifier.height(42.dp)
                )
            }

            NavigationDrawerItem(
                label = { Text("Recycle Bin", style = MaterialTheme.typography.bodyMedium) },
                selected = false,
                icon = { Icon(Icons.Default.DeleteSweep, contentDescription = null, modifier = Modifier.size(18.dp)) },
                onClick = onOpenRecycleBin,
                modifier = Modifier.height(42.dp)
            )

            NavigationDrawerItem(
                label = { Text("Mount External Volume (SAF)", style = MaterialTheme.typography.bodyMedium) },
                selected = false,
                icon = { Icon(Icons.Default.SdStorage, contentDescription = null, modifier = Modifier.size(18.dp)) },
                onClick = onMountSaf,
                modifier = Modifier.height(42.dp)
            )

            // 4. Pinned Favorites
            if (favorites.isNotEmpty()) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                Text("Pinned Favorites", style = MaterialTheme.typography.labelLarge, color = FluentAmber400)
                favorites.take(8).forEach { fav ->
                    NavigationDrawerItem(
                        label = { Text(fav.name, maxLines = 1) },
                        selected = false,
                        icon = { Icon(Icons.Default.Star, contentDescription = null, tint = FluentAmber400, modifier = Modifier.size(16.dp)) },
                        onClick = {
                            val target = if (fav.isDirectory) fav.path else File(fav.path).parent ?: sharedRootPath
                            onNavigateToPath(target)
                        },
                        modifier = Modifier.height(40.dp)
                    )
                }
            }

            // 5. Recent Files
            if (recentFiles.isNotEmpty()) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                Text("Recent Files", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                recentFiles.take(5).forEach { rec ->
                    NavigationDrawerItem(
                        label = { Text("${rec.name} (${rec.formatName})", style = MaterialTheme.typography.bodySmall, maxLines = 1) },
                        selected = false,
                        icon = { Icon(Icons.Default.History, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        onClick = {
                            val parent = if (rec.path.startsWith("content://")) {
                                com.explorerpro.core.filesystem.TermuxStorageBridge.resolveParentSafUri(rec.path) ?: rec.path
                            } else {
                                File(rec.path).parent ?: sharedRootPath
                            }
                            onNavigateToPath(parent)
                        },
                        modifier = Modifier.height(38.dp)
                    )
                }
            }

            // 6. Full-Page App Settings Entry
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            NavigationDrawerItem(
                label = {
                    Column {
                        Text(
                            text = "Settings",
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            text = "Theme, Dual-Pane, Recycle Bin & FAB",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                selected = false,
                icon = {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Settings",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                },
                onClick = onOpenSettings,
                modifier = Modifier
                    .height(54.dp)
                    .testTag("drawer_settings_button")
            )
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}
