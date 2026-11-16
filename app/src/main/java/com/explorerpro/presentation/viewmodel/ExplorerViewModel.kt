package com.explorerpro.presentation.viewmodel

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.explorerpro.core.domain.operations.OperationController
import com.explorerpro.core.filesystem.FileSystemRepository
import com.explorerpro.core.filesystem.FormatDetectorAndMetadataEngine
import com.explorerpro.core.filesystem.SortingAndGroupingEngine
import com.explorerpro.core.filesystem.TermuxStorageBridge
import com.explorerpro.data.local.ArchiveHistoryEntity
import com.explorerpro.data.local.ExplorerDatabase
import com.explorerpro.data.local.FavoriteEntity
import com.explorerpro.data.local.OperationLogEntity
import com.explorerpro.data.local.PreferencesManager
import com.explorerpro.data.local.RecentFileEntity
import com.explorerpro.data.local.SearchHistoryEntity
import com.explorerpro.data.local.TabSessionEntity
import com.explorerpro.data.local.UserPreferencesState
import com.explorerpro.domain.model.ArchiveCompressionLevel
import com.explorerpro.domain.model.ArchiveCreateFormat
import com.explorerpro.domain.model.ArchiveCreationConfig
import com.explorerpro.domain.model.ArchiveEncryptionMode
import com.explorerpro.domain.model.ArchiveEntryItem
import com.explorerpro.domain.model.BatchRenameConfig
import com.explorerpro.domain.model.BatchRenamePreviewItem
import com.explorerpro.domain.model.DeleteConfirmationRequest
import com.explorerpro.domain.model.ExplorerNoticeDialogModel
import com.explorerpro.domain.model.ExplorerNoticeSeverity
import com.explorerpro.domain.model.ExplorerTab
import com.explorerpro.domain.model.FileCategory
import com.explorerpro.domain.model.FileConflictDecision
import com.explorerpro.domain.model.FileEntry
import com.explorerpro.domain.model.FileOperationTask
import com.explorerpro.domain.model.FolderComparisonResult
import com.explorerpro.domain.model.FolderPlacement
import com.explorerpro.domain.model.GroupBy
import com.explorerpro.domain.model.OperationStatus
import com.explorerpro.domain.model.OperationType
import com.explorerpro.domain.model.PaneId
import com.explorerpro.domain.model.PaneState
import com.explorerpro.domain.model.PendingTransferConflictState
import com.explorerpro.domain.model.SortConfig
import com.explorerpro.domain.model.SortField
import com.explorerpro.domain.model.SortOrder
import com.explorerpro.domain.model.StorageAnalysisReport
import com.explorerpro.domain.model.StorageVolumeSummary
import com.explorerpro.domain.model.TermuxIntegrationStatus
import com.explorerpro.domain.model.ThemePreference
import com.explorerpro.domain.model.ViewMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class ExplorerViewModel(application: Application) : AndroidViewModel(application) {

    private val database = ExplorerDatabase.getInstance(application)
    private val dao = database.explorerDao()
    val preferencesManager = PreferencesManager(application)
    val fileSystemRepository = FileSystemRepository(application)
    val operationController = OperationController(application, viewModelScope, dao)


    val userPreferences: StateFlow<UserPreferencesState> = preferencesManager.preferencesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UserPreferencesState())

    val favorites: StateFlow<List<FavoriteEntity>> = dao.observeFavorites()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recentFiles: StateFlow<List<RecentFileEntity>> = dao.observeRecentFiles()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val searchHistory: StateFlow<List<SearchHistoryEntity>> = dao.observeSearchHistory()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val operationLogs: StateFlow<List<OperationLogEntity>> = dao.observeOperationLogs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val archiveHistory: StateFlow<List<ArchiveHistoryEntity>> = dao.observeArchiveHistory()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _leftPane = MutableStateFlow(PaneState(paneId = PaneId.LEFT))
    val leftPane: StateFlow<PaneState> = _leftPane.asStateFlow()

    private val _rightPane = MutableStateFlow(PaneState(paneId = PaneId.RIGHT))
    val rightPane: StateFlow<PaneState> = _rightPane.asStateFlow()

    private val _activePaneId = MutableStateFlow(PaneId.LEFT)
    val activePaneId: StateFlow<PaneId> = _activePaneId.asStateFlow()

    private val _storageVolumes = MutableStateFlow<List<StorageVolumeSummary>>(emptyList())
    val storageVolumes: StateFlow<List<StorageVolumeSummary>> = _storageVolumes.asStateFlow()

    private val _termuxStatus = MutableStateFlow(TermuxIntegrationStatus())
    val termuxStatus: StateFlow<TermuxIntegrationStatus> = _termuxStatus.asStateFlow()

    private val _autoLaunchTermuxSafEvent = MutableStateFlow(false)
    val autoLaunchTermuxSafEvent: StateFlow<Boolean> = _autoLaunchTermuxSafEvent.asStateFlow()

    private var hasAutoPromptedTermuxSafThisSession = false

    private val _hasAllFilesAccess = MutableStateFlow(fileSystemRepository.hasAllFilesAccess())
    val hasAllFilesAccess: StateFlow<Boolean> = _hasAllFilesAccess.asStateFlow()

    private val _permissionStatus = MutableStateFlow(fileSystemRepository.queryAppPermissionStatus())
    val permissionStatus: StateFlow<com.explorerpro.domain.model.AppPermissionStatus> = _permissionStatus.asStateFlow()

    private val _clipboardEntries = MutableStateFlow<List<FileEntry>>(emptyList())
    val clipboardEntries: StateFlow<List<FileEntry>> = _clipboardEntries.asStateFlow()

    private val _isClipboardCut = MutableStateFlow(false)
    val isClipboardCut: StateFlow<Boolean> = _isClipboardCut.asStateFlow()

    val activeTasks: StateFlow<List<FileOperationTask>> = operationController.tasks

    private val _inspectedArchive = MutableStateFlow<FileEntry?>(null)
    val inspectedArchive: StateFlow<FileEntry?> = _inspectedArchive.asStateFlow()

    private val _archiveEntries = MutableStateFlow<List<ArchiveEntryItem>>(emptyList())
    val archiveEntries: StateFlow<List<ArchiveEntryItem>> = _archiveEntries.asStateFlow()

    private val _archiveVerificationStatus = MutableStateFlow<String?>(null)
    val archiveVerificationStatus: StateFlow<String?> = _archiveVerificationStatus.asStateFlow()

    private val _activeImageEntry = MutableStateFlow<FileEntry?>(null)
    val activeImageEntry: StateFlow<FileEntry?> = _activeImageEntry.asStateFlow()

    private val _galleryImages = MutableStateFlow<List<FileEntry>>(emptyList())
    val galleryImages: StateFlow<List<FileEntry>> = _galleryImages.asStateFlow()

    private val _propertiesEntry = MutableStateFlow<FileEntry?>(null)
    val propertiesEntry: StateFlow<FileEntry?> = _propertiesEntry.asStateFlow()

    private val _propertiesSha256 = MutableStateFlow<String?>(null)
    val propertiesSha256: StateFlow<String?> = _propertiesSha256.asStateFlow()

    private val _textViewerEntry = MutableStateFlow<Pair<FileEntry, String>?>(null)
    val textViewerEntry: StateFlow<Pair<FileEntry, String>?> = _textViewerEntry.asStateFlow()

    private val _folderComparison = MutableStateFlow<FolderComparisonResult?>(null)
    val folderComparison: StateFlow<FolderComparisonResult?> = _folderComparison.asStateFlow()

    private val _storageReport = MutableStateFlow<StorageAnalysisReport?>(null)
    val storageReport: StateFlow<StorageAnalysisReport?> = _storageReport.asStateFlow()

    private val _isAnalyzingStorage = MutableStateFlow(false)
    val isAnalyzingStorage: StateFlow<Boolean> = _isAnalyzingStorage.asStateFlow()

    private val _snackbarMessage = MutableStateFlow<String?>(null)
    val snackbarMessage: StateFlow<String?> = _snackbarMessage.asStateFlow()

    // --- Universal Dialog & Interaction System StateFlows ---

    private val _pendingTransferConflict = MutableStateFlow<PendingTransferConflictState?>(null)
    val pendingTransferConflict: StateFlow<PendingTransferConflictState?> = _pendingTransferConflict.asStateFlow()

    private val _deleteConfirmationRequest = MutableStateFlow<DeleteConfirmationRequest?>(null)
    val deleteConfirmationRequest: StateFlow<DeleteConfirmationRequest?> = _deleteConfirmationRequest.asStateFlow()

    private val _activeProgressDialogTaskId = MutableStateFlow<String?>(null)
    val activeProgressDialogTaskId: StateFlow<String?> = _activeProgressDialogTaskId.asStateFlow()

    private val _noticeDialog = MutableStateFlow<ExplorerNoticeDialogModel?>(null)
    val noticeDialog: StateFlow<ExplorerNoticeDialogModel?> = _noticeDialog.asStateFlow()

    private val _actionSheetEntry = MutableStateFlow<FileEntry?>(null)
    val actionSheetEntry: StateFlow<FileEntry?> = _actionSheetEntry.asStateFlow()

    private var skipDeleteConfirmationThisSession = false

    private var leftSearchJob: Job? = null
    private var rightSearchJob: Job? = null
    private var leftFolderStatsJob: Job? = null
    private var rightFolderStatsJob: Job? = null

    init {
        viewModelScope.launch {
            fileSystemRepository.initializeRealWorkspaceDirectories()
            refreshStorageVolumes()

            val rootPath = fileSystemRepository.primarySharedRoot.absolutePath
            val downloadsDir = File(fileSystemRepository.primarySharedRoot, "Download")
            val downloadsPath = if (downloadsDir.exists()) downloadsDir.absolutePath else rootPath
            val systemPath = fileSystemRepository.systemRoot.absolutePath
            val termuxState = _termuxStatus.value
            val secondaryInitialPath = when {
                termuxState.grantedTermuxTreeUris.isNotEmpty() ->
                    fileSystemRepository.normalizeSafTreeToDocumentUri(termuxState.grantedTermuxTreeUris.first())
                termuxState.isDirectHomeAccessible ->
                    TermuxStorageBridge.TERMUX_HOME_PATH
                else -> downloadsPath
            }

            // Restore and revalidate saved tabs (migrating any old ExplorerProWorkspace tabs to real storage)
            val savedLeftTabs = dao.getTabsForPane(PaneId.LEFT.name)
                .filterNot { it.currentPath.contains("ExplorerProWorkspace") }
            val initialLeftTabs = if (savedLeftTabs.isNotEmpty()) {
                savedLeftTabs.map {
                    val validPath = revalidateRestoredPathOrUri(it.currentPath, rootPath)
                    ExplorerTab(
                        id = it.tabId,
                        title = resolveTabTitle(validPath),
                        currentPath = validPath,
                        isSafTab = validPath.startsWith("content://"),
                        safUriString = validPath.takeIf { p -> p.startsWith("content://") }
                    )
                }
            } else {
                listOf(
                    ExplorerTab(title = resolveTabTitle(rootPath), currentPath = rootPath),
                    ExplorerTab(title = resolveTabTitle(secondaryInitialPath), currentPath = secondaryInitialPath)
                )
            }

            val savedRightTabs = dao.getTabsForPane(PaneId.RIGHT.name)
                .filterNot { it.currentPath.contains("ExplorerProWorkspace") }
            val initialRightTabs = if (savedRightTabs.isNotEmpty()) {
                savedRightTabs.map {
                    val validPath = revalidateRestoredPathOrUri(it.currentPath, secondaryInitialPath)
                    ExplorerTab(
                        id = it.tabId,
                        title = resolveTabTitle(validPath),
                        currentPath = validPath,
                        isSafTab = validPath.startsWith("content://"),
                        safUriString = validPath.takeIf { p -> p.startsWith("content://") }
                    )
                }
            } else {
                listOf(
                    ExplorerTab(title = resolveTabTitle(secondaryInitialPath), currentPath = secondaryInitialPath),
                    ExplorerTab(title = resolveTabTitle(systemPath), currentPath = systemPath)
                )
            }

            _leftPane.update {
                it.copy(
                    tabs = initialLeftTabs,
                    activeTabId = initialLeftTabs.first().id,
                    currentPath = initialLeftTabs.first().currentPath
                )
            }
            _rightPane.update {
                it.copy(
                    tabs = initialRightTabs,
                    activeTabId = initialRightTabs.first().id,
                    currentPath = initialRightTabs.first().currentPath,
                    viewMode = ViewMode.SIMPLE_LIST
                )
            }

            loadPaneDirectory(PaneId.LEFT, _leftPane.value.currentPath, recordHistory = false)
            loadPaneDirectory(PaneId.RIGHT, _rightPane.value.currentPath, recordHistory = false)

            // If Termux is already mounted/accessible at startup, ensure a Termux tab is attached
            if (_termuxStatus.value.isMountedOrAccessible) {
                ensureTermuxTabAutoMounted(switchToTab = false)
            } else if ((_termuxStatus.value.isTermuxInstalled || _termuxStatus.value.isDocumentsProviderAvailable) &&
                _permissionStatus.value.isStorageAccessGranted
            ) {
                if (!hasAutoPromptedTermuxSafThisSession) {
                    hasAutoPromptedTermuxSafThisSession = true
                    _autoLaunchTermuxSafEvent.value = true
                }
            }

            startTermuxAutoMountMonitor()

            userPreferences.collect { prefs ->
                _leftPane.update { pane ->
                    val updatedSort = pane.sortConfig.copy(field = prefs.leftSortField, order = prefs.leftSortOrder)
                    val sorted = SortingAndGroupingEngine.sortEntries(pane.entries, updatedSort)
                    pane.copy(
                        viewMode = prefs.leftViewMode,
                        gridColumns = prefs.leftGridColumns,
                        sortConfig = updatedSort,
                        groupBy = prefs.leftGroupBy,
                        entries = sorted,
                        groupedEntries = SortingAndGroupingEngine.groupEntries(sorted, prefs.leftGroupBy)
                    )
                }
                _rightPane.update { pane ->
                    val updatedSort = pane.sortConfig.copy(field = prefs.rightSortField, order = prefs.rightSortOrder)
                    val sorted = SortingAndGroupingEngine.sortEntries(pane.entries, updatedSort)
                    pane.copy(
                        viewMode = prefs.rightViewMode,
                        gridColumns = prefs.rightGridColumns,
                        sortConfig = updatedSort,
                        groupBy = prefs.rightGroupBy,
                        entries = sorted,
                        groupedEntries = SortingAndGroupingEngine.groupEntries(sorted, prefs.rightGroupBy)
                    )
                }
                refreshStorageVolumes(prefs.persistedSafUris)
            }
        }
    }

    private fun revalidateRestoredPathOrUri(savedPath: String, fallbackPath: String): String {
        if (savedPath.contains("ExplorerProWorkspace")) return fallbackPath
        if (savedPath.startsWith("content://")) {
            val uri = runCatching { Uri.parse(savedPath) }.getOrNull() ?: return fallbackPath
            return if (TermuxStorageBridge.hasUriReadPermission(getApplication(), uri)) {
                savedPath
            } else {
                fallbackPath
            }
        }
        val file = File(savedPath)
        return if (file.exists()) savedPath else fallbackPath
    }

    private fun resolveTabTitle(pathOrUri: String): String {
        if (pathOrUri.startsWith("content://")) {
            val crumbs = TermuxStorageBridge.parseSafBreadcrumbs(pathOrUri)
            return crumbs.lastOrNull()?.first ?: "SAF Folder"
        }
        if (pathOrUri == TermuxStorageBridge.TERMUX_HOME_PATH) return "Termux (\$HOME)"
        if (pathOrUri == fileSystemRepository.primarySharedRoot.absolutePath) return "Internal Storage"
        if (pathOrUri == fileSystemRepository.systemRoot.absolutePath) return "System (/system)"
        if (pathOrUri == fileSystemRepository.appSandboxRoot.absolutePath) return "App Sandbox"
        if (pathOrUri == fileSystemRepository.recycleBinDir.absolutePath) return "Recycle Bin"
        return File(pathOrUri).name.ifEmpty { "Root" }
    }

    fun setActivePane(paneId: PaneId) {
        _activePaneId.value = paneId
    }

    fun currentPaneState(paneId: PaneId = _activePaneId.value): PaneState {
        return if (paneId == PaneId.LEFT) _leftPane.value else _rightPane.value
    }

    private fun updatePane(paneId: PaneId, transform: (PaneState) -> PaneState) {
        if (paneId == PaneId.LEFT) {
            _leftPane.update(transform)
        } else {
            _rightPane.update(transform)
        }
    }

    fun refreshStorageVolumes(safUris: Set<String> = userPreferences.value.persistedSafUris) {
        val wasMounted = _termuxStatus.value.isMountedOrAccessible
        _hasAllFilesAccess.value = fileSystemRepository.hasAllFilesAccess()
        val updatedTermux = fileSystemRepository.queryTermuxStatus(safUris)
        _termuxStatus.value = updatedTermux
        _permissionStatus.value = fileSystemRepository.queryAppPermissionStatus(safUris)
        _storageVolumes.value = fileSystemRepository.getAvailableStorageVolumes(safUris)

        if (!wasMounted && updatedTermux.isMountedOrAccessible) {
            ensureTermuxTabAutoMounted(switchToTab = true)
            postMessage("Auto-mounted Termux (\$HOME) environment.")
        }
    }

    fun refreshPermissionStatus() {
        val safUris = userPreferences.value.persistedSafUris
        val prevStorageGranted = _permissionStatus.value.isStorageAccessGranted
        val updated = fileSystemRepository.queryAppPermissionStatus(safUris)
        _permissionStatus.value = updated
        _hasAllFilesAccess.value = updated.isAllFilesAccessGranted
        _storageVolumes.value = fileSystemRepository.getAvailableStorageVolumes(safUris)

        if (!prevStorageGranted && updated.isStorageAccessGranted) {
            refreshBothPanes()
            postMessage("Storage permissions granted — real device folders unlocked.")
        }
        if (updated.areAllCorePermissionsGranted && !userPreferences.value.firstLaunchPermissionsCompleted) {
            viewModelScope.launch {
                preferencesManager.setFirstLaunchPermissionsCompleted(true)
            }
        }
    }

    fun completeFirstLaunchPermissionsSetup() {
        viewModelScope.launch {
            preferencesManager.setFirstLaunchPermissionsCompleted(true)
        }
        refreshPermissionStatus()
        refreshBothPanes()
    }

    /**
     * Real-time background/foreground watcher that detects when Termux is opened, installed,
     * or exposes its `com.termux.documents` provider and auto-mounts it immediately.
     */
    private fun startTermuxAutoMountMonitor() {
        viewModelScope.launch(Dispatchers.IO) {
            while (isActive) {
                delay(3500L)
                val safUris = userPreferences.value.persistedSafUris
                val prev = _termuxStatus.value
                val current = fileSystemRepository.queryTermuxStatus(safUris)

                if (current != prev) {
                    withContext(Dispatchers.Main) {
                        _termuxStatus.value = current
                        _permissionStatus.value = fileSystemRepository.queryAppPermissionStatus(safUris)
                        _storageVolumes.value = fileSystemRepository.getAvailableStorageVolumes(safUris)

                        if (!prev.isMountedOrAccessible && current.isMountedOrAccessible) {
                            ensureTermuxTabAutoMounted(switchToTab = true)
                            postMessage("Auto-mounted Termux (\$HOME) live environment.")
                        } else if (!current.isMountedOrAccessible &&
                            (current.isTermuxInstalled || current.isDocumentsProviderAvailable) &&
                            _permissionStatus.value.isStorageAccessGranted &&
                            !hasAutoPromptedTermuxSafThisSession
                        ) {
                            hasAutoPromptedTermuxSafThisSession = true
                            _autoLaunchTermuxSafEvent.value = true
                        }
                    }
                }
            }
        }
    }

    private fun ensureTermuxTabAutoMounted(switchToTab: Boolean) {
        val rawTarget = _termuxStatus.value.activeMountTarget ?: return
        val normalizedTarget = if (rawTarget.startsWith("content://")) {
            fileSystemRepository.normalizeSafTreeToDocumentUri(rawTarget)
        } else rawTarget

        val currentTabs = _leftPane.value.tabs
        val existingTermuxTab = currentTabs.firstOrNull {
            TermuxStorageBridge.isTermuxUri(it.currentPath) || it.currentPath == normalizedTarget
        }
        if (existingTermuxTab == null) {
            val newTab = ExplorerTab(
                title = "Termux (\$HOME)",
                currentPath = normalizedTarget,
                isSafTab = normalizedTarget.startsWith("content://"),
                safUriString = normalizedTarget.takeIf { it.startsWith("content://") }
            )
            _leftPane.update { state ->
                state.copy(
                    tabs = state.tabs + newTab,
                    activeTabId = if (switchToTab) newTab.id else state.activeTabId,
                    currentPath = if (switchToTab) normalizedTarget else state.currentPath
                )
            }
            if (switchToTab) {
                loadPaneDirectory(PaneId.LEFT, normalizedTarget, recordHistory = false)
            }
        } else if (switchToTab) {
            switchTab(PaneId.LEFT, existingTermuxTab.id)
        }
    }

    fun consumeAutoLaunchTermuxSafEvent() {
        _autoLaunchTermuxSafEvent.value = false
    }

    fun onAppResumed() {
        val hadStorageAccess = _permissionStatus.value.isStorageAccessGranted
        refreshStorageVolumes()
        val nowHasStorageAccess = _permissionStatus.value.isStorageAccessGranted
        if (!hadStorageAccess && nowHasStorageAccess) {
            refreshBothPanes()
            postMessage("All Files Access enabled — full native storage unlocked.")
        }
        if (_permissionStatus.value.areAllCorePermissionsGranted &&
            !userPreferences.value.firstLaunchPermissionsCompleted
        ) {
            viewModelScope.launch {
                preferencesManager.setFirstLaunchPermissionsCompleted(true)
            }
        }
        val status = _termuxStatus.value
        if (!status.isMountedOrAccessible &&
            (status.isTermuxInstalled || status.isDocumentsProviderAvailable) &&
            nowHasStorageAccess &&
            !hasAutoPromptedTermuxSafThisSession
        ) {
            hasAutoPromptedTermuxSafThisSession = true
            _autoLaunchTermuxSafEvent.value = true
        }
    }

    fun launchTermuxApp() {
        val app = getApplication<Application>()
        val launchIntent = TermuxStorageBridge.createLaunchTermuxAppIntent(app)
        if (launchIntent != null) {
            runCatching { app.startActivity(launchIntent) }
                .onFailure { postMessage("Unable to launch Termux: ${it.message}") }
        } else {
            postMessage("Termux (com.termux) is not installed on this device.")
        }
    }

    /**
     * Opens Termux ($HOME) immediately if already granted/accessible, or launches the system
     * SAF DocumentTree picker pre-targeted at `com.termux.documents` (`/data/data/com.termux/files/home`).
     */
    fun openOrRequestTermuxStorage(
        paneId: PaneId = _activePaneId.value,
        onLaunchSafIntent: (Intent) -> Unit
    ) {
        val status = fileSystemRepository.queryTermuxStatus(userPreferences.value.persistedSafUris)
        _termuxStatus.value = status
        when {
            status.grantedTermuxTreeUris.isNotEmpty() -> {
                val docUri = fileSystemRepository.normalizeSafTreeToDocumentUri(status.grantedTermuxTreeUris.first())
                loadPaneDirectory(paneId, docUri, recordHistory = true)
                postMessage("Opened auto-mounted Termux (\$HOME) via SAF provider.")
            }
            status.isDirectHomeAccessible -> {
                loadPaneDirectory(paneId, TermuxStorageBridge.TERMUX_HOME_PATH, recordHistory = true)
                postMessage("Opened auto-mounted Termux (\$HOME) directly.")
            }
            status.isExternalTermuxDirAccessible && status.externalTermuxDirPath != null -> {
                loadPaneDirectory(paneId, status.externalTermuxDirPath, recordHistory = true)
                postMessage("Opened Termux shared directory.")
            }
            else -> {
                onLaunchSafIntent(TermuxStorageBridge.createTermuxSafTreeIntent(getApplication()))
            }
        }
    }

    fun loadPaneDirectory(
        paneId: PaneId = _activePaneId.value,
        path: String,
        recordHistory: Boolean = true
    ) {
        _activePaneId.value = paneId
        val normalizedPath = if (path.startsWith("content://")) {
            fileSystemRepository.normalizeSafTreeToDocumentUri(path)
        } else path

        updatePane(paneId) { it.copy(isLoading = true, errorMessage = null, selectedPaths = emptySet()) }

        viewModelScope.launch {
            val showHidden = userPreferences.value.showHiddenFiles
            val result = fileSystemRepository.listDirectory(normalizedPath, showHidden)
            result.fold(
                onSuccess = { rawList ->
                    val pane = currentPaneState(paneId)
                    val sorted = SortingAndGroupingEngine.sortEntries(rawList, pane.sortConfig)
                    val grouped = SortingAndGroupingEngine.groupEntries(sorted, pane.groupBy)
                    val folderTitle = resolveTabTitle(normalizedPath)

                    updatePane(paneId) { current ->
                        val updatedTabs = current.tabs.map { tab ->
                            if (tab.id == current.activeTabId) {
                                val newBack = if (recordHistory && tab.currentPath != normalizedPath && tab.currentPath.isNotEmpty()) {
                                    (tab.backStack + tab.currentPath).takeLast(30)
                                } else tab.backStack
                                tab.copy(
                                    title = folderTitle,
                                    currentPath = normalizedPath,
                                    backStack = newBack,
                                    forwardStack = if (recordHistory) emptyList() else tab.forwardStack,
                                    isSafTab = normalizedPath.startsWith("content://"),
                                    safUriString = normalizedPath.takeIf { it.startsWith("content://") }
                                )
                            } else tab
                        }
                        current.copy(
                            tabs = updatedTabs,
                            currentPath = normalizedPath,
                            entries = sorted,
                            groupedEntries = grouped,
                            isLoading = false,
                            errorMessage = null,
                            searchQuery = "",
                            isSearching = false
                        )
                    }
                    persistTabsForPane(paneId)
                    triggerFolderIntelligence(paneId, normalizedPath, recursive = false)
                },
                onFailure = { err ->
                    updatePane(paneId) {
                        it.copy(
                            currentPath = normalizedPath,
                            entries = emptyList(),
                            groupedEntries = emptyMap(),
                            isLoading = false,
                            errorMessage = err.message ?: "Unable to open folder"
                        )
                    }
                }
            )
        }
    }

    fun triggerFolderIntelligence(paneId: PaneId, path: String, recursive: Boolean) {
        val prevJob = if (paneId == PaneId.LEFT) leftFolderStatsJob else rightFolderStatsJob
        prevJob?.cancel()
        if (!recursive) {
            val job = viewModelScope.launch {
                val stats = fileSystemRepository.calculateFolderIntelligence(path, includeRecursive = false)
                updatePane(paneId) { it.copy(folderIntelligence = stats) }
            }
            if (paneId == PaneId.LEFT) leftFolderStatsJob = job else rightFolderStatsJob = job
            return
        }

        updatePane(paneId) {
            it.copy(
                folderIntelligence = (it.folderIntelligence ?: com.explorerpro.domain.model.FolderIntelligenceResult(path))
                    .copy(isCalculatingRecursive = true)
            )
        }

        val folderName = resolveTabTitle(path)
        operationController.launchOperation(
            type = OperationType.FOLDER_INTELLIGENCE,
            title = "Folder Intelligence: $folderName",
            sourcePaths = listOf(path),
            destinationPath = path,
            indeterminate = true,
            canPause = true,
            canCancel = true,
            canRetry = true,
            onRetryRequest = { triggerFolderIntelligence(paneId, path, recursive = true) }
        ) { scope ->
            val stats = fileSystemRepository.calculateFolderIntelligence(
                folderPathOrUri = path,
                includeRecursive = true,
                awaitIfPaused = { scope.checkPausedAndActive() }
            ) { recFiles, recFolders, recBytes, currentName ->
                scope.reportProgress(
                    fraction = 0f,
                    processedItems = recFiles + recFolders,
                    totalItems = (recFiles + recFolders).coerceAtLeast(1),
                    transferredBytes = recBytes,
                    totalBytes = recBytes,
                    currentItemName = currentName,
                    indeterminate = true
                )
            }
            withContext(Dispatchers.Main) {
                updatePane(paneId) { it.copy(folderIntelligence = stats) }
            }
            Result.success(stats.recursiveFileCount + stats.recursiveFolderCount)
        }
    }

    // --- Tab Management ---

    fun createNewTab(paneId: PaneId = _activePaneId.value, targetPath: String? = null) {
        val rawPath = targetPath ?: fileSystemRepository.workspaceRoot.absolutePath
        val path = if (rawPath.startsWith("content://")) {
            fileSystemRepository.normalizeSafTreeToDocumentUri(rawPath)
        } else rawPath
        val newTab = ExplorerTab(
            title = resolveTabTitle(path),
            currentPath = path,
            isSafTab = path.startsWith("content://"),
            safUriString = path.takeIf { it.startsWith("content://") }
        )
        updatePane(paneId) { state ->
            state.copy(
                tabs = state.tabs + newTab,
                activeTabId = newTab.id,
                currentPath = newTab.currentPath
            )
        }
        loadPaneDirectory(paneId, path, recordHistory = false)
    }

    fun duplicateActiveTab(paneId: PaneId = _activePaneId.value) {
        val state = currentPaneState(paneId)
        val activeTab = state.tabs.find { it.id == state.activeTabId } ?: return
        val copyTab = activeTab.copy(
            id = java.util.UUID.randomUUID().toString(),
            title = "${activeTab.title} (Copy)"
        )
        updatePane(paneId) {
            it.copy(tabs = it.tabs + copyTab, activeTabId = copyTab.id)
        }
        loadPaneDirectory(paneId, copyTab.currentPath, recordHistory = false)
    }

    fun switchTab(paneId: PaneId, tabId: String) {
        val state = currentPaneState(paneId)
        val targetTab = state.tabs.find { it.id == tabId } ?: return
        updatePane(paneId) { it.copy(activeTabId = tabId, currentPath = targetTab.currentPath) }
        loadPaneDirectory(paneId, targetTab.currentPath, recordHistory = false)
    }

    fun closeTab(paneId: PaneId, tabId: String) {
        val state = currentPaneState(paneId)
        if (state.tabs.size <= 1) {
            postMessage("At least one tab must remain open in each pane.")
            return
        }
        val closingTab = state.tabs.find { it.id == tabId } ?: return
        val remaining = state.tabs.filterNot { it.id == tabId }
        val nextActive = if (state.activeTabId == tabId) remaining.last() else remaining.find { it.id == state.activeTabId } ?: remaining.first()

        updatePane(paneId) {
            it.copy(
                tabs = remaining,
                activeTabId = nextActive.id,
                closedTabs = (it.closedTabs + closingTab).takeLast(10),
                currentPath = nextActive.currentPath
            )
        }
        loadPaneDirectory(paneId, nextActive.currentPath, recordHistory = false)
    }

    fun reopenLastClosedTab(paneId: PaneId = _activePaneId.value) {
        val state = currentPaneState(paneId)
        val lastClosed = state.closedTabs.lastOrNull()
        if (lastClosed == null) {
            postMessage("No recently closed tabs to reopen.")
            return
        }
        updatePane(paneId) {
            it.copy(
                tabs = it.tabs + lastClosed,
                activeTabId = lastClosed.id,
                closedTabs = it.closedTabs.dropLast(1),
                currentPath = lastClosed.currentPath
            )
        }
        loadPaneDirectory(paneId, lastClosed.currentPath, recordHistory = false)
    }

    fun navigateBackInTab(paneId: PaneId = _activePaneId.value): Boolean {
        val state = currentPaneState(paneId)
        val activeTab = state.tabs.find { it.id == state.activeTabId } ?: return false
        val previousPath = activeTab.backStack.lastOrNull()
        if (previousPath != null) {
            updatePane(paneId) { current ->
                current.copy(
                    tabs = current.tabs.map { tab ->
                        if (tab.id == activeTab.id) {
                            tab.copy(
                                currentPath = previousPath,
                                backStack = tab.backStack.dropLast(1),
                                forwardStack = tab.forwardStack + state.currentPath
                            )
                        } else tab
                    }
                )
            }
            loadPaneDirectory(paneId, previousPath, recordHistory = false)
            return true
        }
        // Fallback: navigate to parent directory inside SAF/Termux tree or POSIX directory
        if (state.currentPath.startsWith("content://")) {
            val parentSafUri = TermuxStorageBridge.resolveParentSafUri(state.currentPath)
            if (parentSafUri != null) {
                loadPaneDirectory(paneId, parentSafUri, recordHistory = true)
                return true
            }
            return false
        }
        val currentFile = File(state.currentPath)
        val parent = currentFile.parentFile
        if (parent != null && parent.canRead() &&
            state.currentPath != fileSystemRepository.workspaceRoot.absolutePath
        ) {
            loadPaneDirectory(paneId, parent.absolutePath, recordHistory = true)
            return true
        }
        return false
    }

    fun navigateForwardInTab(paneId: PaneId = _activePaneId.value) {
        val state = currentPaneState(paneId)
        val activeTab = state.tabs.find { it.id == state.activeTabId } ?: return
        val nextPath = activeTab.forwardStack.lastOrNull() ?: return
        updatePane(paneId) { current ->
            current.copy(
                tabs = current.tabs.map { tab ->
                    if (tab.id == activeTab.id) {
                        tab.copy(
                            currentPath = nextPath,
                            backStack = tab.backStack + state.currentPath,
                            forwardStack = tab.forwardStack.dropLast(1)
                        )
                    } else tab
                }
            )
        }
        loadPaneDirectory(paneId, nextPath, recordHistory = false)
    }

    private suspend fun persistTabsForPane(paneId: PaneId) {
        val state = currentPaneState(paneId)
        dao.clearTabsForPane(paneId.name)
        dao.insertTabSessions(
            state.tabs.mapIndexed { idx, tab ->
                TabSessionEntity(
                    tabId = tab.id,
                    paneId = paneId.name,
                    title = tab.title,
                    currentPath = tab.currentPath,
                    isActive = tab.id == state.activeTabId,
                    position = idx
                )
            }
        )
    }

    // --- Selection & File Activation ---

    fun onEntryClicked(paneId: PaneId, entry: FileEntry) {
        _activePaneId.value = paneId
        val state = currentPaneState(paneId)
        if (state.selectedPaths.isNotEmpty()) {
            toggleSelection(paneId, entry.path)
            return
        }

        if (entry.isDirectory) {
            loadPaneDirectory(paneId, entry.path, recordHistory = true)
        } else {
            recordRecentFileAccess(entry)
            when {
                entry.detectedFormat.isImage -> {
                    val allImages = state.entries.filter { it.detectedFormat.isImage }
                    _galleryImages.value = allImages.ifEmpty { listOf(entry) }
                    _activeImageEntry.value = entry
                }
                entry.detectedFormat.isArchive -> {
                    inspectArchiveFile(entry)
                }
                FormatDetectorAndMetadataEngine.isEditableTextFormat(entry.detectedFormat) -> {
                    openTextViewer(entry)
                }
                else -> {
                    openPropertiesDialog(entry)
                }
            }
        }
    }

    fun toggleSelection(paneId: PaneId, path: String) {
        _activePaneId.value = paneId
        updatePane(paneId) { state ->
            val updated = if (state.selectedPaths.contains(path)) {
                state.selectedPaths - path
            } else {
                state.selectedPaths + path
            }
            state.copy(selectedPaths = updated)
        }
    }

    fun selectAll(paneId: PaneId = _activePaneId.value) {
        updatePane(paneId) { state ->
            state.copy(selectedPaths = state.entries.map { it.path }.toSet())
        }
    }

    fun clearSelection(paneId: PaneId = _activePaneId.value) {
        updatePane(paneId) { it.copy(selectedPaths = emptySet()) }
    }

    private fun recordRecentFileAccess(entry: FileEntry) {
        viewModelScope.launch {
            dao.upsertRecentFile(
                RecentFileEntity(
                    path = entry.path,
                    name = entry.name,
                    extension = entry.extension,
                    formatName = entry.detectedFormat.displayName,
                    sizeBytes = entry.sizeBytes
                )
            )
        }
    }

    // --- Dual-Pane Special Operations ---

    fun swapPanes() {
        val leftPath = _leftPane.value.currentPath
        val rightPath = _rightPane.value.currentPath
        loadPaneDirectory(PaneId.LEFT, rightPath, recordHistory = true)
        loadPaneDirectory(PaneId.RIGHT, leftPath, recordHistory = true)
        postMessage("Swapped Left and Right pane directories.")
    }

    fun transferSelectedToOtherPane(sourcePaneId: PaneId, isMove: Boolean) {
        val sourceState = currentPaneState(sourcePaneId)
        val targetPaneId = if (sourcePaneId == PaneId.LEFT) PaneId.RIGHT else PaneId.LEFT
        val targetState = currentPaneState(targetPaneId)
        val selectedEntries = sourceState.entries.filter { sourceState.selectedPaths.contains(it.path) }
        if (selectedEntries.isEmpty()) {
            postMessage("Select one or more items first to transfer to the other pane.")
            return
        }
        initiateTransferWithConflictCheck(
            sourceEntries = selectedEntries,
            destinationPath = targetState.currentPath,
            isMove = isMove
        )
        clearSelection(sourcePaneId)
    }

    fun compareDualPanes() {
        viewModelScope.launch {
            val leftPath = _leftPane.value.currentPath
            val rightPath = _rightPane.value.currentPath
            val comparison = fileSystemRepository.compareDirectories(leftPath, rightPath)
            _folderComparison.value = comparison
        }
    }

    fun dismissFolderComparison() {
        _folderComparison.value = null
    }

    // --- File Operations & Operation Center ---

    fun copyToClipboard(paneId: PaneId = _activePaneId.value, isCut: Boolean) {
        val state = currentPaneState(paneId)
        val selected = state.entries.filter { state.selectedPaths.contains(it.path) }
        if (selected.isEmpty()) {
            postMessage("Select items first.")
            return
        }
        _clipboardEntries.value = selected
        _isClipboardCut.value = isCut
        postMessage("${selected.size} item(s) ${if (isCut) "cut" else "copied"} to clipboard.")
    }

    fun pasteClipboard(targetPaneId: PaneId = _activePaneId.value) {
        val sources = _clipboardEntries.value
        if (sources.isEmpty()) {
            postMessage("Clipboard is empty.")
            return
        }
        val destPath = currentPaneState(targetPaneId).currentPath
        val isMove = _isClipboardCut.value
        initiateTransferWithConflictCheck(
            sourceEntries = sources,
            destinationPath = destPath,
            isMove = isMove
        )
        if (isMove) {
            _clipboardEntries.value = emptyList()
            _isClipboardCut.value = false
        }
    }

    private fun initiateTransferWithConflictCheck(
        sourceEntries: List<FileEntry>,
        destinationPath: String,
        isMove: Boolean
    ) {
        viewModelScope.launch {
            val conflicts = fileSystemRepository.findTransferConflicts(sourceEntries, destinationPath)
            if (conflicts.isNotEmpty()) {
                _pendingTransferConflict.value = PendingTransferConflictState(
                    allSourceEntries = sourceEntries,
                    destinationDirPath = destinationPath,
                    isMove = isMove,
                    conflicts = conflicts,
                    currentConflictIndex = 0,
                    resolvedPerSourcePath = emptyMap()
                )
            } else {
                executeTransferTask(
                    sourceEntries = sourceEntries,
                    destinationPath = destinationPath,
                    isMove = isMove,
                    perSourceResolution = emptyMap()
                )
            }
        }
    }

    fun resolveCurrentTransferConflict(
        decision: FileConflictDecision,
        customNewName: String? = null,
        applyToAll: Boolean = false
    ) {
        val state = _pendingTransferConflict.value ?: return
        val activeConflict = state.activeConflict ?: return
        if (decision == FileConflictDecision.CANCEL) {
            _pendingTransferConflict.value = null
            postMessage("Transfer cancelled.")
            return
        }

        val updatedMap = state.resolvedPerSourcePath.toMutableMap()
        updatedMap[activeConflict.sourceEntry.path] = decision to customNewName

        if (applyToAll) {
            for (idx in (state.currentConflictIndex + 1) until state.conflicts.size) {
                val pair = state.conflicts[idx]
                updatedMap[pair.sourceEntry.path] = decision to null
            }
            _pendingTransferConflict.value = null
            executeTransferTask(
                sourceEntries = state.allSourceEntries,
                destinationPath = state.destinationDirPath,
                isMove = state.isMove,
                perSourceResolution = updatedMap
            )
        } else if (state.currentConflictIndex + 1 < state.conflicts.size) {
            _pendingTransferConflict.value = state.copy(
                currentConflictIndex = state.currentConflictIndex + 1,
                resolvedPerSourcePath = updatedMap
            )
        } else {
            _pendingTransferConflict.value = null
            executeTransferTask(
                sourceEntries = state.allSourceEntries,
                destinationPath = state.destinationDirPath,
                isMove = state.isMove,
                perSourceResolution = updatedMap
            )
        }
    }

    fun cancelPendingTransferConflict() {
        _pendingTransferConflict.value = null
        postMessage("Transfer cancelled.")
    }

    private fun executeTransferTask(
        sourceEntries: List<FileEntry>,
        destinationPath: String,
        isMove: Boolean,
        perSourceResolution: Map<String, Pair<FileConflictDecision, String?>> = emptyMap()
    ) {
        val totalBytes = sourceEntries.sumOf { it.sizeBytes.coerceAtLeast(1L) }.coerceAtLeast(1L)
        val opType = if (isMove) OperationType.MOVE else OperationType.COPY
        val opTitle = "${if (isMove) "Moving" else "Copying"} ${sourceEntries.size} item(s)"

        operationController.launchOperation(
            type = opType,
            title = opTitle,
            sourcePaths = sourceEntries.map { it.path },
            destinationPath = destinationPath,
            totalItems = sourceEntries.size,
            totalBytes = totalBytes,
            indeterminate = false,
            canPause = true,
            canCancel = true,
            canRetry = true,
            onRetryRequest = {
                executeTransferTask(sourceEntries, destinationPath, isMove, perSourceResolution)
            }
        ) { execScope ->
            val policy = userPreferences.value.defaultConflictResolution
            val result = fileSystemRepository.transferFiles(
                sourceEntries = sourceEntries,
                destinationDirPath = destinationPath,
                isMove = isMove,
                conflictPolicy = policy,
                perSourceResolution = perSourceResolution,
                awaitIfPaused = { execScope.checkPausedAndActive() }
            ) { fraction, doneItems, totalItems, transferredBytes, maxBytes, currentFile ->
                execScope.reportProgress(
                    fraction = fraction,
                    processedItems = doneItems,
                    totalItems = totalItems,
                    transferredBytes = transferredBytes,
                    totalBytes = maxBytes,
                    currentItemName = currentFile
                )
            }
            result.onSuccess { count ->
                withContext(Dispatchers.Main) {
                    refreshBothPanes()
                    postMessage("${opType.label} completed ($count item(s)).")
                }
            }.onFailure { err ->
                withContext(Dispatchers.Main) {
                    postErrorDialog(
                        title = "${opType.label} Failed",
                        message = err.message ?: "Unable to complete file transfer.",
                        technicalDetails = "Destination: $destinationPath\nItems: ${sourceEntries.size}",
                        onRetry = {
                            executeTransferTask(sourceEntries, destinationPath, isMove, perSourceResolution)
                        }
                    )
                }
            }
            result
        }
    }

    fun pauseOperation(taskId: String) = operationController.pauseOperation(taskId)
    fun resumeOperation(taskId: String) = operationController.resumeOperation(taskId)
    fun cancelOperation(taskId: String) = operationController.cancelOperation(taskId)
    fun retryOperation(taskId: String) = operationController.retryOperation(taskId)
    fun dismissOperation(taskId: String) = operationController.dismissOperation(taskId)
    fun clearCompletedOperations() = operationController.clearCompletedOperations()

    fun createNewFolder(paneId: PaneId = _activePaneId.value, folderName: String) {
        val parentPath = currentPaneState(paneId).currentPath
        viewModelScope.launch {
            fileSystemRepository.createDirectory(parentPath, folderName).fold(
                onSuccess = {
                    refreshBothPanes()
                    postMessage("Created folder '$folderName'.")
                },
                onFailure = { postMessage(it.message ?: "Failed to create folder.") }
            )
        }
    }

    fun createNewNoteFile(paneId: PaneId = _activePaneId.value, fileName: String, content: String) {
        val parentPath = currentPaneState(paneId).currentPath
        viewModelScope.launch {
            fileSystemRepository.createTextFile(parentPath, fileName, content).fold(
                onSuccess = {
                    refreshBothPanes()
                    postMessage("Created file '$fileName'.")
                },
                onFailure = { postMessage(it.message ?: "Failed to create file.") }
            )
        }
    }

    fun renameFileEntry(path: String, newName: String) {
        viewModelScope.launch {
            fileSystemRepository.renameSingleEntry(path, newName).fold(
                onSuccess = {
                    refreshBothPanes()
                    postMessage("Renamed to '$newName'.")
                },
                onFailure = { postMessage(it.message ?: "Rename failed.") }
            )
        }
    }

    fun buildBatchRenamePreview(
        files: List<FileEntry>,
        config: BatchRenameConfig
    ): List<BatchRenamePreviewItem> {
        if (files.isEmpty()) return emptyList()
        val siblings = currentPaneState().entries.map { it.name }.toSet()
        return SortingAndGroupingEngine.generateBatchRenamePreview(files, config, siblings)
    }

    fun applyBatchRename(files: List<FileEntry>, config: BatchRenameConfig) {
        val siblings = currentPaneState().entries.map { it.name }.toSet()
        val destDir = currentPaneState().currentPath
        operationController.launchOperation(
            type = OperationType.BATCH_RENAME,
            title = "Batch Rename (${files.size} items)",
            sourcePaths = files.map { it.path },
            destinationPath = destDir,
            totalItems = files.size,
            indeterminate = false,
            canPause = true,
            canCancel = true,
            canRetry = true,
            onRetryRequest = { applyBatchRename(files, config) }
        ) { execScope ->
            val result = fileSystemRepository.executeBatchRename(
                files = files,
                config = config,
                existingSiblingNames = siblings,
                awaitIfPaused = { execScope.checkPausedAndActive() }
            ) { done, total, skipped, errors, currentName ->
                execScope.reportProgress(
                    fraction = done.toFloat() / total.toFloat(),
                    processedItems = done,
                    totalItems = total,
                    currentItemName = currentName,
                    skippedCount = skipped,
                    errorCount = errors
                )
            }
            result.onSuccess { count ->
                withContext(Dispatchers.Main) {
                    clearSelection()
                    refreshBothPanes()
                    postMessage("Batch renamed $count item(s).")
                }
            }.onFailure {
                withContext(Dispatchers.Main) {
                    postMessage(it.message ?: "Batch rename failed.")
                }
            }
            result
        }
    }

    fun requestDeleteSelectedEntries(paneId: PaneId = _activePaneId.value) {
        val state = currentPaneState(paneId)
        val selectedEntries = state.entries.filter { state.selectedPaths.contains(it.path) }
        if (selectedEntries.isEmpty()) return
        if (skipDeleteConfirmationThisSession && userPreferences.value.recycleBinEnabled) {
            deleteSelectedEntries(paneId, permanent = false)
            return
        }
        val insideRecycleBin = state.currentPath.startsWith(fileSystemRepository.recycleBinDir.absolutePath)
        _deleteConfirmationRequest.value = DeleteConfirmationRequest(
            entries = selectedEntries,
            rawPaths = selectedEntries.map { it.path },
            defaultUseRecycleBin = userPreferences.value.recycleBinEnabled && !insideRecycleBin,
            isRecycleBinClearRequest = insideRecycleBin
        )
    }

    fun requestDeleteSingleEntry(entry: FileEntry) {
        if (skipDeleteConfirmationThisSession && userPreferences.value.recycleBinEnabled) {
            deletePaths(listOf(entry.path), permanent = false)
            return
        }
        val insideRecycleBin = entry.path.startsWith(fileSystemRepository.recycleBinDir.absolutePath)
        _deleteConfirmationRequest.value = DeleteConfirmationRequest(
            entries = listOf(entry),
            rawPaths = listOf(entry.path),
            defaultUseRecycleBin = userPreferences.value.recycleBinEnabled && !insideRecycleBin,
            isRecycleBinClearRequest = insideRecycleBin
        )
    }

    fun confirmPendingDelete(permanentDelete: Boolean, rememberChoiceForSession: Boolean) {
        val req = _deleteConfirmationRequest.value ?: return
        _deleteConfirmationRequest.value = null
        if (rememberChoiceForSession && !permanentDelete) {
            skipDeleteConfirmationThisSession = true
        }
        val executePermanent = permanentDelete || req.isRecycleBinClearRequest || !req.defaultUseRecycleBin
        deletePaths(req.rawPaths, permanent = executePermanent)
        clearSelection()
    }

    fun dismissPendingDelete() {
        _deleteConfirmationRequest.value = null
    }

    fun openProgressDialog(taskId: String) {
        _activeProgressDialogTaskId.value = taskId
    }

    fun dismissProgressDialogToBackground() {
        _activeProgressDialogTaskId.value = null
    }

    fun openFileActionBottomSheet(entry: FileEntry) {
        _actionSheetEntry.value = entry
    }

    fun closeFileActionBottomSheet() {
        _actionSheetEntry.value = null
    }

    fun postErrorDialog(
        title: String,
        message: String,
        technicalDetails: String? = null,
        onRetry: (() -> Unit)? = null
    ) {
        _noticeDialog.value = ExplorerNoticeDialogModel(
            severity = ExplorerNoticeSeverity.ERROR,
            title = title,
            message = message,
            technicalDetails = technicalDetails,
            onRetryAction = onRetry
        )
    }

    fun postSuccessDialog(
        title: String,
        message: String,
        destinationPath: String? = null
    ) {
        _noticeDialog.value = ExplorerNoticeDialogModel(
            severity = ExplorerNoticeSeverity.SUCCESS,
            title = title,
            message = message,
            destinationPath = destinationPath
        )
    }

    fun dismissNoticeDialog() {
        _noticeDialog.value = null
    }

    fun deleteSelectedEntries(paneId: PaneId = _activePaneId.value, permanent: Boolean = false) {
        val selected = currentPaneState(paneId).selectedPaths.toList()
        if (selected.isEmpty()) return
        deletePaths(selected, permanent)
        clearSelection(paneId)
    }

    fun deletePaths(paths: List<String>, permanent: Boolean = false) {
        val useRecycle = !permanent && userPreferences.value.recycleBinEnabled
        val allSaf = paths.all { it.startsWith("content://") }
        val opType = if (useRecycle && !allSaf) OperationType.RECYCLE else OperationType.DELETE
        val title = "${if (useRecycle && !allSaf) "Recycling" else "Deleting"} ${paths.size} item(s)"

        operationController.launchOperation(
            type = opType,
            title = title,
            sourcePaths = paths,
            destinationPath = if (useRecycle && !allSaf) fileSystemRepository.recycleBinDir.absolutePath else "",
            totalItems = paths.size,
            indeterminate = false,
            canPause = true,
            canCancel = true,
            canRetry = false
        ) { execScope ->
            val result = fileSystemRepository.deleteOrRecycleFiles(
                pathsOrUris = paths,
                useRecycleBin = useRecycle,
                awaitIfPaused = { execScope.checkPausedAndActive() }
            ) { done, total, currentName ->
                execScope.reportProgress(
                    fraction = done.toFloat() / total.toFloat(),
                    processedItems = done,
                    totalItems = total,
                    currentItemName = currentName
                )
            }
            result.onSuccess { count ->
                withContext(Dispatchers.Main) {
                    refreshBothPanes()
                    postMessage(
                        if (useRecycle && !allSaf) "Moved $count item(s) to Recycle Bin."
                        else "Deleted $count item(s)."
                    )
                }
            }.onFailure {
                withContext(Dispatchers.Main) {
                    postMessage(it.message ?: "Delete failed.")
                }
            }
            result
        }
    }

    fun openRecycleBin() {
        loadPaneDirectory(_activePaneId.value, fileSystemRepository.recycleBinDir.absolutePath, recordHistory = true)
    }

    // --- Multi-Format Archive Engine Integration (ZIP, TAR, GZ, BZ2, XZ, 7Z, RAR, ISO, APK) ---

    private fun stripArchiveExtension(fileName: String): String {
        val lower = fileName.lowercase()
        val compoundSuffixes = listOf(
            ".tar.gz", ".tar.bz2", ".tar.xz", ".tar.lz4", ".tar.zstd", ".tar.zst", ".tar.lzma",
            ".tgz", ".tbz2", ".txz", ".tlz4", ".tzst", ".tlz"
        )
        for (suffix in compoundSuffixes) {
            if (lower.endsWith(suffix) && fileName.length > suffix.length) {
                return fileName.dropLast(suffix.length)
            }
        }
        return fileName.substringBeforeLast('.').ifBlank { "${fileName}_Extracted" }
    }

    private fun resolveExtractionParentDir(archive: FileEntry): File {
        if (!archive.isSafUri) {
            val parent = File(archive.path).parentFile
            if (parent != null && parent.canWrite()) return parent
        }
        val activePath = currentPaneState().currentPath
        if (!activePath.startsWith("content://")) {
            val activeDir = File(activePath)
            if (activeDir.exists() && activeDir.canWrite()) return activeDir
        }
        return File(fileSystemRepository.primarySharedRoot, "Download").takeIf { it.exists() && it.canWrite() }
            ?: fileSystemRepository.appSandboxRoot
    }

    /**
     * Directly extracts any supported archive format (ZIP, TAR, GZIP, BZIP2, XZ, 7Z, RAR, ISO, APK, JAR)
     * from the File Explorer context menu or top overflow menu without requiring modal navigation.
     *
     * @param intoSubfolder When true ("Extract to <Folder>/"), creates a dedicated subfolder named after
     * the archive. When false ("Extract Here"), extracts directly into the current folder.
     */
    fun extractArchiveDirectly(
        entry: FileEntry,
        intoSubfolder: Boolean = true,
        password: String? = null
    ) {
        val parentDir = resolveExtractionParentDir(entry)
        val destDir = if (intoSubfolder) {
            val baseName = stripArchiveExtension(entry.name)
            SortingAndGroupingEngine.resolveNonConflictingFile(parentDir, baseName)
        } else {
            parentDir
        }

        operationController.launchOperation(
            type = OperationType.ARCHIVE_EXTRACT,
            title = "Extracting ${entry.name}",
            sourcePaths = listOf(entry.path),
            destinationPath = destDir.absolutePath,
            totalItems = 1,
            totalBytes = entry.sizeBytes.coerceAtLeast(1L),
            indeterminate = false,
            canPause = true,
            canCancel = true,
            canRetry = true,
            onRetryRequest = { extractArchiveDirectly(entry, intoSubfolder, password) }
        ) { execScope ->
            val result = fileSystemRepository.archiveEngine.extractArchiveFromPathOrUri(
                context = getApplication(),
                pathOrUri = entry.path,
                displayName = entry.name,
                destinationDir = destDir,
                selectedEntryPaths = null,
                password = password,
                awaitIfPaused = { execScope.checkPausedAndActive() }
            ) { fraction, doneItems, totalItems, doneBytes, totalBytes, currentEntryName ->
                execScope.reportProgress(
                    fraction = fraction,
                    processedItems = doneItems,
                    totalItems = totalItems,
                    transferredBytes = doneBytes,
                    totalBytes = totalBytes,
                    currentItemName = currentEntryName
                )
            }

            result.onSuccess { count ->
                dao.upsertArchiveHistory(
                    ArchiveHistoryEntity(
                        archivePath = entry.path,
                        archiveFormat = entry.detectedFormat.displayName,
                        entryCount = count,
                        lastAction = "Extracted ($count items)"
                    )
                )
                withContext(Dispatchers.Main) {
                    refreshBothPanes()
                    postMessage("Extracted $count item(s) from '${entry.name}' to '${destDir.name}'.")
                }
            }.onFailure { err ->
                withContext(Dispatchers.Main) {
                    postMessage("Extraction failed: ${err.message}")
                }
            }
            result
        }
    }

    fun inspectArchiveFile(entry: FileEntry, password: String? = null) {
        _inspectedArchive.value = entry
        _archiveVerificationStatus.value = "Reading archive structure…"
        viewModelScope.launch {
            fileSystemRepository.archiveEngine.listArchiveEntriesFromPathOrUri(
                context = getApplication(),
                pathOrUri = entry.path,
                displayName = entry.name,
                password = password
            ).fold(
                onSuccess = { items ->
                    _archiveEntries.value = items
                    _archiveVerificationStatus.value = null
                    dao.upsertArchiveHistory(
                        ArchiveHistoryEntity(
                            archivePath = entry.path,
                            archiveFormat = entry.detectedFormat.displayName,
                            entryCount = items.size,
                            lastAction = "Browsed"
                        )
                    )
                },
                onFailure = { err ->
                    _archiveEntries.value = emptyList()
                    _archiveVerificationStatus.value = "Unable to list entries: ${err.message}"
                    postMessage("Archive error: ${err.message}")
                }
            )
        }
    }

    fun verifyActiveArchiveIntegrity(password: String? = null) {
        val archive = _inspectedArchive.value ?: return
        _archiveVerificationStatus.value = "Verifying archive integrity…"

        operationController.launchOperation(
            type = OperationType.ARCHIVE_VERIFY,
            title = "Verifying ${archive.name}",
            sourcePaths = listOf(archive.path),
            destinationPath = archive.path,
            totalItems = _archiveEntries.value.size.coerceAtLeast(1),
            totalBytes = archive.sizeBytes,
            indeterminate = false,
            canPause = false,
            canCancel = true,
            canRetry = true,
            onRetryRequest = { verifyActiveArchiveIntegrity(password) }
        ) { execScope ->
            val result = if (archive.isSafUri) {
                fileSystemRepository.archiveEngine.verifyArchiveIntegrityFromUri(
                    context = getApplication(),
                    uriString = archive.path,
                    displayName = archive.name,
                    password = password
                ) { frac, done, total, bytes, maxBytes, currentName ->
                    execScope.reportProgress(frac, done, total, bytes, maxBytes, currentName)
                }
            } else {
                fileSystemRepository.archiveEngine.verifyArchiveIntegrity(
                    archiveFile = File(archive.path),
                    password = password
                ) { frac, done, total, bytes, maxBytes, currentName ->
                    execScope.reportProgress(frac, done, total, bytes, maxBytes, currentName)
                }
            }
            result.fold(
                onSuccess = { msg ->
                    withContext(Dispatchers.Main) { _archiveVerificationStatus.value = msg }
                    Result.success(_archiveEntries.value.size.coerceAtLeast(1))
                },
                onFailure = { err ->
                    withContext(Dispatchers.Main) {
                        _archiveVerificationStatus.value = "Integrity failure: ${err.message}"
                    }
                    Result.failure(err)
                }
            )
        }
    }

    fun extractActiveArchive(
        selectedEntries: Set<String>? = null,
        intoSubfolder: Boolean = true,
        password: String? = null
    ) {
        val archive = _inspectedArchive.value ?: return
        val parentDir = resolveExtractionParentDir(archive)
        val destDir = if (intoSubfolder) {
            val baseName = stripArchiveExtension(archive.name)
            SortingAndGroupingEngine.resolveNonConflictingFile(parentDir, "${baseName}_Extracted")
        } else {
            parentDir
        }

        _inspectedArchive.value = null

        operationController.launchOperation(
            type = OperationType.ARCHIVE_EXTRACT,
            title = "Extracting ${archive.name}",
            sourcePaths = listOf(archive.path),
            destinationPath = destDir.absolutePath,
            totalItems = selectedEntries?.size ?: _archiveEntries.value.size.coerceAtLeast(1),
            totalBytes = archive.sizeBytes.coerceAtLeast(1L),
            indeterminate = false,
            canPause = true,
            canCancel = true,
            canRetry = true,
            onRetryRequest = { extractArchiveDirectly(archive, intoSubfolder, password) }
        ) { execScope ->
            val result = fileSystemRepository.archiveEngine.extractArchiveFromPathOrUri(
                context = getApplication(),
                pathOrUri = archive.path,
                displayName = archive.name,
                destinationDir = destDir,
                selectedEntryPaths = selectedEntries,
                password = password,
                awaitIfPaused = { execScope.checkPausedAndActive() }
            ) { fraction, doneItems, totalItems, doneBytes, totalBytes, currentEntryName ->
                execScope.reportProgress(
                    fraction = fraction,
                    processedItems = doneItems,
                    totalItems = totalItems,
                    transferredBytes = doneBytes,
                    totalBytes = totalBytes,
                    currentItemName = currentEntryName
                )
            }

            result.onSuccess { count ->
                dao.upsertArchiveHistory(
                    ArchiveHistoryEntity(
                        archivePath = archive.path,
                        archiveFormat = archive.detectedFormat.displayName,
                        entryCount = count,
                        lastAction = "Extracted ($count items)"
                    )
                )
                withContext(Dispatchers.Main) {
                    refreshBothPanes()
                    postMessage("Extracted $count file(s) to '${destDir.name}'.")
                }
            }.onFailure { err ->
                withContext(Dispatchers.Main) {
                    postMessage("Extraction blocked/failed: ${err.message}")
                }
            }
            result
        }
    }

    fun compressSelectionToZip(
        paneId: PaneId = _activePaneId.value,
        archiveName: String,
        format: ArchiveCreateFormat = ArchiveCreateFormat.ZIP
    ) {
        createArchiveWithConfig(
            paneId = paneId,
            config = ArchiveCreationConfig(
                archiveName = archiveName,
                format = format
            )
        )
    }

    fun createArchiveWithConfig(
        paneId: PaneId = _activePaneId.value,
        config: ArchiveCreationConfig
    ) {
        val state = currentPaneState(paneId)
        val selectedLocalFiles = state.selectedPaths
            .filterNot { it.startsWith("content://") }
            .map { File(it) }
            .filter { it.exists() }
        if (selectedLocalFiles.isEmpty()) {
            postMessage("Select local storage files/folders to compress.")
            return
        }
        val outDir = if (state.currentPath.startsWith("content://")) {
            File(fileSystemRepository.primarySharedRoot, "Download").takeIf { it.exists() && it.canWrite() }
                ?: fileSystemRepository.appSandboxRoot
        } else {
            val candidate = File(state.currentPath)
            if (candidate.canWrite()) candidate else fileSystemRepository.appSandboxRoot
        }

        val effectiveEncryption = if (config.format.supportsEncryption && config.password.isNotEmpty()) {
            config.encryptionMode
        } else {
            ArchiveEncryptionMode.NONE
        }

        if (config.createSeparateArchives && selectedLocalFiles.size > 1) {
            val totalBytes = selectedLocalFiles.sumOf { it.length().coerceAtLeast(1L) }
            operationController.launchOperation(
                type = OperationType.ARCHIVE_CREATE,
                title = "Creating ${selectedLocalFiles.size} separate ${config.format.shortName.uppercase()} archives",
                sourcePaths = selectedLocalFiles.map { it.absolutePath },
                destinationPath = outDir.absolutePath,
                totalItems = selectedLocalFiles.size,
                totalBytes = totalBytes,
                indeterminate = false,
                canPause = true,
                canCancel = true,
                canRetry = true,
                onRetryRequest = { createArchiveWithConfig(paneId, config) }
            ) { execScope ->
                var createdCount = 0
                val succeededSources = mutableListOf<String>()
                var lastError: Throwable? = null
                selectedLocalFiles.forEachIndexed { idx, srcFile ->
                    execScope.checkPausedAndActive()
                    val cleanBase = stripArchiveExtension(srcFile.name)
                    val outArchive = SortingAndGroupingEngine.resolveNonConflictingFile(
                        outDir,
                        "$cleanBase.${config.format.extension}"
                    )
                    val res = fileSystemRepository.archiveEngine.createArchive(
                        sourceFiles = listOf(srcFile),
                        outputArchiveFile = outArchive,
                        format = config.format,
                        compressionLevel = config.compressionLevel,
                        encryptionMode = effectiveEncryption,
                        password = config.password,
                        awaitIfPaused = { execScope.checkPausedAndActive() }
                    ) { itemFraction, _, _, _, _, currentName ->
                        val overall = ((idx.toFloat() + itemFraction) / selectedLocalFiles.size.toFloat()).coerceIn(0f, 0.99f)
                        execScope.reportProgress(
                            fraction = overall,
                            processedItems = idx,
                            totalItems = selectedLocalFiles.size,
                            transferredBytes = 0L,
                            totalBytes = totalBytes,
                            currentItemName = "${outArchive.name}: $currentName"
                        )
                    }
                    res.onSuccess {
                        createdCount++
                        succeededSources.add(srcFile.absolutePath)
                    }.onFailure { err ->
                        lastError = err
                    }
                }
                if (config.deleteSourceAfterCompression && succeededSources.isNotEmpty()) {
                    fileSystemRepository.deleteOrRecycleFiles(
                        pathsOrUris = succeededSources,
                        useRecycleBin = userPreferences.value.recycleBinEnabled,
                        awaitIfPaused = { execScope.checkPausedAndActive() },
                        onProgress = { _, _, _ -> }
                    )
                }
                if (createdCount == 0 && lastError != null) {
                    withContext(Dispatchers.Main) {
                        postMessage("Compression failed: ${lastError?.message}")
                    }
                    Result.failure(lastError!!)
                } else {
                    withContext(Dispatchers.Main) {
                        clearSelection(paneId)
                        refreshBothPanes()
                        postMessage("Created $createdCount ${config.format.shortName.uppercase()} archive(s).")
                    }
                    Result.success(createdCount)
                }
            }
            return
        }

        val baseWithoutExt = stripArchiveExtension(config.archiveName.trim().ifBlank { "Archive_Bundle" })
        val cleanName = "$baseWithoutExt.${config.format.extension}"
        val outArchive = SortingAndGroupingEngine.resolveNonConflictingFile(outDir, cleanName)
        val totalBytes = selectedLocalFiles.sumOf { it.length().coerceAtLeast(1L) }

        operationController.launchOperation(
            type = OperationType.ARCHIVE_CREATE,
            title = "Creating ${outArchive.name}",
            sourcePaths = selectedLocalFiles.map { it.absolutePath },
            destinationPath = outArchive.absolutePath,
            totalItems = selectedLocalFiles.size,
            totalBytes = totalBytes,
            indeterminate = false,
            canPause = true,
            canCancel = true,
            canRetry = true,
            onRetryRequest = { createArchiveWithConfig(paneId, config) }
        ) { execScope ->
            val res = fileSystemRepository.archiveEngine.createArchive(
                sourceFiles = selectedLocalFiles,
                outputArchiveFile = outArchive,
                format = config.format,
                compressionLevel = config.compressionLevel,
                encryptionMode = effectiveEncryption,
                password = config.password,
                awaitIfPaused = { execScope.checkPausedAndActive() }
            ) { fraction, doneItems, totalItems, doneBytes, maxBytes, currentName ->
                execScope.reportProgress(
                    fraction = fraction,
                    processedItems = doneItems,
                    totalItems = totalItems,
                    transferredBytes = doneBytes,
                    totalBytes = maxBytes,
                    currentItemName = currentName
                )
            }
            res.fold(
                onSuccess = { created ->
                    if (config.deleteSourceAfterCompression) {
                        fileSystemRepository.deleteOrRecycleFiles(
                            pathsOrUris = selectedLocalFiles.map { it.absolutePath },
                            useRecycleBin = userPreferences.value.recycleBinEnabled,
                            awaitIfPaused = { execScope.checkPausedAndActive() },
                            onProgress = { _, _, _ -> }
                        )
                    }
                    dao.upsertArchiveHistory(
                        ArchiveHistoryEntity(
                            archivePath = created.absolutePath,
                            archiveFormat = config.format.label,
                            entryCount = selectedLocalFiles.size,
                            lastAction = if (effectiveEncryption != ArchiveEncryptionMode.NONE) {
                                "Created (${effectiveEncryption.label})"
                            } else {
                                "Created"
                            }
                        )
                    )
                    withContext(Dispatchers.Main) {
                        clearSelection(paneId)
                        refreshBothPanes()
                        val deletedNote = if (config.deleteSourceAfterCompression) " Source files cleaned up." else ""
                        postMessage("Created archive '${created.name}'.${deletedNote}")
                    }
                    Result.success(selectedLocalFiles.size)
                },
                onFailure = { err ->
                    withContext(Dispatchers.Main) {
                        postMessage("Compression failed: ${err.message}")
                    }
                    Result.failure(err)
                }
            )
        }
    }

    /**
     * Converts any supported existing archive (ZIP, 7Z, RAR, ISO, TAR, TAR.GZ, TAR.BZ2, TAR.XZ,
     * TAR.LZ4, TAR.ZSTD, TAR.LZMA, JAR, CPIO, APK, etc.) into another archive format.
     */
    fun convertArchiveFormat(
        sourceArchive: FileEntry,
        config: ArchiveCreationConfig,
        sourcePassword: String? = null
    ) {
        val outDir = resolveExtractionParentDir(sourceArchive)
        val cleanBase = stripArchiveExtension(
            config.archiveName.trim().ifBlank { stripArchiveExtension(sourceArchive.name) }
        )
        val outArchive = SortingAndGroupingEngine.resolveNonConflictingFile(
            outDir,
            "$cleanBase.${config.format.extension}"
        )
        val effectiveEncryption = if (config.format.supportsEncryption && config.password.isNotEmpty()) {
            config.encryptionMode
        } else {
            ArchiveEncryptionMode.NONE
        }

        operationController.launchOperation(
            type = OperationType.ARCHIVE_CREATE,
            title = "Converting ${sourceArchive.name} → ${config.format.shortName.uppercase()}",
            sourcePaths = listOf(sourceArchive.path),
            destinationPath = outArchive.absolutePath,
            totalItems = 1,
            totalBytes = sourceArchive.sizeBytes.coerceAtLeast(1L),
            indeterminate = false,
            canPause = true,
            canCancel = true,
            canRetry = true,
            onRetryRequest = { convertArchiveFormat(sourceArchive, config, sourcePassword) }
        ) { execScope ->
            val res = fileSystemRepository.archiveEngine.convertArchive(
                context = getApplication(),
                sourcePathOrUri = sourceArchive.path,
                sourceDisplayName = sourceArchive.name,
                outputArchiveFile = outArchive,
                targetFormat = config.format,
                compressionLevel = config.compressionLevel,
                encryptionMode = effectiveEncryption,
                password = config.password,
                sourcePassword = sourcePassword,
                awaitIfPaused = { execScope.checkPausedAndActive() }
            ) { fraction, doneItems, totalItems, doneBytes, maxBytes, currentName ->
                execScope.reportProgress(
                    fraction = fraction,
                    processedItems = doneItems,
                    totalItems = totalItems,
                    transferredBytes = doneBytes,
                    totalBytes = maxBytes,
                    currentItemName = currentName
                )
            }

            res.fold(
                onSuccess = { convertedFile ->
                    if (config.deleteSourceAfterCompression && !sourceArchive.isSafUri) {
                        fileSystemRepository.deleteOrRecycleFiles(
                            pathsOrUris = listOf(sourceArchive.path),
                            useRecycleBin = userPreferences.value.recycleBinEnabled,
                            awaitIfPaused = { execScope.checkPausedAndActive() },
                            onProgress = { _, _, _ -> }
                        )
                    }
                    dao.upsertArchiveHistory(
                        ArchiveHistoryEntity(
                            archivePath = convertedFile.absolutePath,
                            archiveFormat = config.format.label,
                            entryCount = 1,
                            lastAction = "Converted from ${sourceArchive.detectedFormat.displayName}"
                        )
                    )
                    withContext(Dispatchers.Main) {
                        clearSelection(_activePaneId.value)
                        refreshBothPanes()
                        postMessage("Converted '${sourceArchive.name}' → '${convertedFile.name}'.")
                    }
                    Result.success(1)
                },
                onFailure = { err ->
                    withContext(Dispatchers.Main) {
                        postMessage("Archive conversion failed: ${err.message}")
                    }
                    Result.failure(err)
                }
            )
        }
    }

    fun closeArchiveInspector() {
        _inspectedArchive.value = null
        _archiveEntries.value = emptyList()
        _archiveVerificationStatus.value = null
    }

    // --- Progressive Cancellable Search ---

    fun updateSearchQuery(
        paneId: PaneId = _activePaneId.value,
        query: String,
        categoryFilter: FileCategory? = null
    ) {
        val prevJob = if (paneId == PaneId.LEFT) leftSearchJob else rightSearchJob
        prevJob?.cancel()

        if (query.isBlank() && categoryFilter == null) {
            loadPaneDirectory(paneId, currentPaneState(paneId).currentPath, recordHistory = false)
            return
        }

        updatePane(paneId) {
            it.copy(searchQuery = query, searchCategoryFilter = categoryFilter, isSearching = true)
        }

        val state = currentPaneState(paneId)
        val job = viewModelScope.launch {
            fileSystemRepository.searchDirectoryProgressively(
                rootPathOrUri = state.currentPath,
                query = query,
                categoryFilter = categoryFilter,
                showHidden = userPreferences.value.showHiddenFiles
            ).collect { matches ->
                val sorted = SortingAndGroupingEngine.sortEntries(matches, currentPaneState(paneId).sortConfig)
                val grouped = SortingAndGroupingEngine.groupEntries(sorted, currentPaneState(paneId).groupBy)
                updatePane(paneId) {
                    it.copy(entries = sorted, groupedEntries = grouped, isSearching = false)
                }
            }
            if (query.length >= 2) {
                dao.insertSearchQuery(
                    SearchHistoryEntity(
                        query = query.trim(),
                        scopePath = state.currentPath,
                        resultCount = currentPaneState(paneId).entries.size
                    )
                )
            }
        }
        if (paneId == PaneId.LEFT) leftSearchJob = job else rightSearchJob = job
    }

    // --- Favorites, Properties, Image Viewer, Text Viewer, Share & SAF ---

    fun toggleFavorite(entry: FileEntry) {
        viewModelScope.launch {
            val exists = dao.isFavorite(entry.path)
            if (exists) {
                dao.removeFavorite(entry.path)
                postMessage("Removed '${entry.name}' from Favorites.")
            } else {
                dao.upsertFavorite(
                    FavoriteEntity(
                        path = entry.path,
                        name = entry.name,
                        isDirectory = entry.isDirectory,
                        category = entry.detectedFormat.category.label
                    )
                )
                postMessage("Added '${entry.name}' to Favorites.")
            }
        }
    }

    fun openPropertiesDialog(entry: FileEntry) {
        _propertiesEntry.value = entry
        _propertiesSha256.value = if (entry.isDirectory) "Directory (N/A)" else "Calculating SHA-256…"
        if (!entry.isDirectory) {
            // For large files (> 2 MB), register a visible Operation Center task as well
            if (entry.sizeBytes > 2 * 1024 * 1024) {
                operationController.launchOperation(
                    type = OperationType.CHECKSUM_VERIFY,
                    title = "SHA-256 Checksum: ${entry.name}",
                    sourcePaths = listOf(entry.path),
                    destinationPath = entry.path,
                    totalItems = 1,
                    totalBytes = entry.sizeBytes,
                    indeterminate = false,
                    canPause = false,
                    canCancel = true,
                    canRetry = false
                ) { execScope ->
                    val hashRes = FormatDetectorAndMetadataEngine.computeSha256ForPathOrUri(
                        context = getApplication(),
                        pathOrUri = entry.path,
                        totalBytesHint = entry.sizeBytes
                    ) { doneBytes, totalBytes ->
                        val frac = if (totalBytes > 0L) doneBytes.toFloat() / totalBytes.toFloat() else 0.5f
                        execScope.reportProgress(frac, 0, 1, doneBytes, totalBytes, entry.name)
                    }
                    val hash = hashRes.getOrDefault("Unavailable")
                    withContext(Dispatchers.Main) {
                        _propertiesSha256.value = hash
                    }
                    hashRes.map { 1 }
                }
            } else {
                viewModelScope.launch {
                    val hash = FormatDetectorAndMetadataEngine
                        .computeSha256ForPathOrUri(getApplication(), entry.path, entry.sizeBytes)
                        .getOrDefault("Unavailable")
                    _propertiesSha256.value = hash
                }
            }
        }
    }

    fun closePropertiesDialog() {
        _propertiesEntry.value = null
        _propertiesSha256.value = null
    }

    fun closeImageViewer() {
        _activeImageEntry.value = null
    }

    fun selectImageInViewer(entry: FileEntry) {
        _activeImageEntry.value = entry
    }

    private fun openTextViewer(entry: FileEntry) {
        viewModelScope.launch {
            fileSystemRepository.readTextFileContent(entry.path).fold(
                onSuccess = { content -> _textViewerEntry.value = entry to content },
                onFailure = { err -> postMessage("Cannot read text file: ${err.message}") }
            )
        }
    }

    fun saveTextViewerContent(entry: FileEntry, updatedContent: String) {
        viewModelScope.launch {
            fileSystemRepository.writeTextFileContent(entry.path, updatedContent).fold(
                onSuccess = {
                    _textViewerEntry.value = null
                    refreshBothPanes()
                    postMessage("Saved changes to '${entry.name}'.")
                },
                onFailure = { postMessage("Save failed: ${it.message}") }
            )
        }
    }

    fun closeTextViewer() {
        _textViewerEntry.value = null
    }

    fun runStorageAnalysis() {
        _isAnalyzingStorage.value = true
        val activeTarget = currentPaneState().currentPath.ifEmpty {
            fileSystemRepository.workspaceRoot.absolutePath
        }
        val targetLabel = resolveTabTitle(activeTarget)

        operationController.launchOperation(
            type = OperationType.STORAGE_ANALYSIS,
            title = "Storage & Duplicate Analysis ($targetLabel)",
            sourcePaths = listOf(activeTarget),
            destinationPath = activeTarget,
            indeterminate = true,
            canPause = true,
            canCancel = true,
            canRetry = true,
            onRetryRequest = { runStorageAnalysis() }
        ) { execScope ->
            try {
                val report = fileSystemRepository.analyzeStorageAndDuplicates(
                    rootPathOrUri = activeTarget,
                    awaitIfPaused = { execScope.checkPausedAndActive() }
                ) { isIndet, frac, doneItems, totalItems, doneBytes, totalBytes, currentItem ->
                    execScope.reportProgress(
                        fraction = frac,
                        processedItems = doneItems,
                        totalItems = totalItems,
                        transferredBytes = doneBytes,
                        totalBytes = totalBytes,
                        currentItemName = currentItem,
                        indeterminate = isIndet
                    )
                }
                withContext(Dispatchers.Main) {
                    _storageReport.value = report
                    _isAnalyzingStorage.value = false
                }
                Result.success(report.scannedFileCount)
            } catch (t: Throwable) {
                withContext(Dispatchers.Main) {
                    _isAnalyzingStorage.value = false
                }
                throw t
            }
        }
    }

    fun closeStorageAnalysis() {
        _storageReport.value = null
    }

    fun onSafTreeGranted(uri: Uri) {
        viewModelScope.launch {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            runCatching {
                getApplication<Application>().contentResolver.takePersistableUriPermission(uri, flags)
            }
            val docUriStr = fileSystemRepository.normalizeSafTreeToDocumentUri(uri.toString())
            preferencesManager.addPersistedSafUri(uri.toString())
            refreshStorageVolumes()
            createNewTab(_activePaneId.value, docUriStr)
            val isTermux = uri.authority == TermuxStorageBridge.TERMUX_DOCUMENTS_AUTHORITY
            postMessage(
                if (isTermux) "Mounted Termux (\$HOME) storage via SAF!"
                else "Mounted external SAF storage volume."
            )
        }
    }

    fun shareFileEntry(entry: FileEntry) {
        if (entry.isDirectory) {
            postMessage("Compress the folder to a ZIP archive before sharing.")
            return
        }
        runCatching {
            val app = getApplication<Application>()
            val shareUri = if (entry.isSafUri) {
                Uri.parse(entry.path)
            } else {
                FileProvider.getUriForFile(app, "${app.packageName}.fileprovider", File(entry.path))
            }
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = entry.mimeType
                putExtra(Intent.EXTRA_STREAM, shareUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = Intent.createChooser(shareIntent, "Share ${entry.name}").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            app.startActivity(chooser)
        }.onFailure {
            postMessage("Share unavailable: ${it.message}")
        }
    }

    // --- Per-Pane View Mode, Sorting & Grouping Updates ---

    fun setViewMode(paneId: PaneId, mode: ViewMode) {
        viewModelScope.launch { preferencesManager.setPaneViewMode(paneId, mode) }
    }

    fun setGridColumns(paneId: PaneId, columns: Int) {
        viewModelScope.launch { preferencesManager.setPaneGridColumns(paneId, columns) }
    }

    fun setSort(
        paneId: PaneId,
        field: SortField,
        order: SortOrder = currentPaneState(paneId).sortConfig.order,
        folderPlacement: FolderPlacement = currentPaneState(paneId).sortConfig.folderPlacement
    ) {
        updatePane(paneId) { state ->
            val newConfig = SortConfig(field = field, order = order, folderPlacement = folderPlacement)
            val sorted = SortingAndGroupingEngine.sortEntries(state.entries, newConfig)
            state.copy(
                sortConfig = newConfig,
                entries = sorted,
                groupedEntries = SortingAndGroupingEngine.groupEntries(sorted, state.groupBy)
            )
        }
        viewModelScope.launch { preferencesManager.setPaneSort(paneId, field, order) }
    }

    fun setGroupBy(paneId: PaneId, groupBy: GroupBy) {
        viewModelScope.launch { preferencesManager.setPaneGroupBy(paneId, groupBy) }
    }

    fun setThemePreference(theme: ThemePreference) {
        viewModelScope.launch { preferencesManager.setThemePreference(theme) }
    }

    fun setDynamicColor(enabled: Boolean) {
        viewModelScope.launch { preferencesManager.setDynamicColor(enabled) }
    }

    fun setShowHiddenFiles(show: Boolean) {
        viewModelScope.launch {
            preferencesManager.setShowHiddenFiles(show)
            refreshBothPanes()
        }
    }

    fun setDualPaneEnabled(enabled: Boolean) {
        viewModelScope.launch { preferencesManager.setDualPaneEnabled(enabled) }
    }

    fun setRecycleBinEnabled(enabled: Boolean) {
        viewModelScope.launch { preferencesManager.setRecycleBinEnabled(enabled) }
    }

    fun setConflictResolution(resolution: com.explorerpro.domain.model.ConflictResolution) {
        viewModelScope.launch { preferencesManager.setConflictResolution(resolution) }
    }

    fun refreshBothPanes() {
        loadPaneDirectory(PaneId.LEFT, _leftPane.value.currentPath, recordHistory = false)
        loadPaneDirectory(PaneId.RIGHT, _rightPane.value.currentPath, recordHistory = false)
        refreshStorageVolumes()
    }

    // --- System Clipboard Path Copy Features ---

    fun copyTextToSystemClipboard(
        text: String,
        clipLabel: String = "Explorer Pro Path",
        confirmationMessage: String? = null
    ) {
        if (text.isBlank()) return
        runCatching {
            val app = getApplication<Application>()
            val clipboard = app.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText(clipLabel, text))
            postMessage(confirmationMessage ?: "Copied to clipboard: $text")
        }.onFailure {
            postMessage("Failed to copy to clipboard: ${it.message}")
        }
    }

    fun copyPathOrUriString(pathOrUri: String, preferRawUri: Boolean = false) {
        val resolved = if (preferRawUri) {
            pathOrUri
        } else {
            TermuxStorageBridge.resolveRealPosixPathIfPossible(pathOrUri)
        }
        copyTextToSystemClipboard(
            text = resolved,
            clipLabel = "File Path",
            confirmationMessage = "Copied path: $resolved"
        )
    }

    fun copyEntryFullPath(entry: FileEntry, preferRawSafUri: Boolean = false) {
        val resolved = if (preferRawSafUri) {
            entry.path
        } else {
            TermuxStorageBridge.resolveRealPosixPathIfPossible(entry.path)
        }
        copyTextToSystemClipboard(
            text = resolved,
            clipLabel = if (preferRawSafUri) "SAF URI" else "File Path",
            confirmationMessage = if (preferRawSafUri) "Copied SAF URI: $resolved" else "Copied path: $resolved"
        )
    }

    fun copyEntryFileName(entry: FileEntry) {
        copyTextToSystemClipboard(
            text = entry.name,
            clipLabel = "File Name",
            confirmationMessage = "Copied name: ${entry.name}"
        )
    }

    fun copySelectedOrCurrentPath(paneId: PaneId = _activePaneId.value) {
        val state = currentPaneState(paneId)
        if (state.selectedPaths.isNotEmpty()) {
            val resolvedPaths = state.selectedPaths.map {
                TermuxStorageBridge.resolveRealPosixPathIfPossible(it)
            }
            val joined = resolvedPaths.joinToString("\n")
            val msg = if (resolvedPaths.size == 1) {
                "Copied path: ${resolvedPaths.first()}"
            } else {
                "Copied ${resolvedPaths.size} paths to clipboard"
            }
            copyTextToSystemClipboard(
                text = joined,
                clipLabel = "Selected File Paths",
                confirmationMessage = msg
            )
        } else {
            val currentDirResolved = TermuxStorageBridge.resolveRealPosixPathIfPossible(state.currentPath)
            copyTextToSystemClipboard(
                text = currentDirResolved,
                clipLabel = "Folder Path",
                confirmationMessage = "Copied folder path: $currentDirResolved"
            )
        }
    }

    // --- FAB & Quick Actions Add-On Operations ---

    fun pauseAllOperations() = operationController.pauseAllRunning()

    fun resumeAllOperations() = operationController.resumeAllPaused()

    fun retryAllFailedOperations() = operationController.retryAllFailed()

    fun syncOtherPaneToActivePath() {
        val activeId = _activePaneId.value
        val otherId = if (activeId == PaneId.LEFT) PaneId.RIGHT else PaneId.LEFT
        val sourcePath = currentPaneState(activeId).currentPath
        if (sourcePath.isNotBlank()) {
            loadPaneDirectory(otherId, sourcePath, recordHistory = true)
            postMessage("Synchronized ${otherId.name.lowercase().replaceFirstChar { it.uppercase() }} pane to ${resolveTabTitle(sourcePath)}.")
        }
    }

    fun pinCurrentDirectoryToFavorites(paneId: PaneId = _activePaneId.value) {
        val currentPath = currentPaneState(paneId).currentPath
        if (currentPath.isBlank()) return
        val name = resolveTabTitle(currentPath)
        viewModelScope.launch {
            val exists = dao.isFavorite(currentPath)
            if (exists) {
                dao.removeFavorite(currentPath)
                postMessage("Removed '$name' from Favorites.")
            } else {
                dao.upsertFavorite(
                    FavoriteEntity(
                        path = currentPath,
                        name = name,
                        isDirectory = true,
                        category = FileCategory.FOLDER.label
                    )
                )
                postMessage("Pinned '$name' to Quick Access Favorites.")
            }
        }
    }

    fun importExternalUris(paneId: PaneId = _activePaneId.value, uris: List<Uri>) {
        if (uris.isEmpty()) return
        val destinationPath = currentPaneState(paneId).currentPath
        val opTitle = "Importing ${uris.size} file(s)"
        operationController.launchOperation(
            type = OperationType.COPY,
            title = opTitle,
            sourcePaths = uris.map { it.toString() },
            destinationPath = destinationPath,
            totalItems = uris.size,
            indeterminate = false,
            canPause = true,
            canCancel = true,
            canRetry = true,
            onRetryRequest = { importExternalUris(paneId, uris) }
        ) { execScope ->
            val policy = userPreferences.value.defaultConflictResolution
            val result = fileSystemRepository.importExternalUrisToDirectory(
                uris = uris,
                destinationDirPath = destinationPath,
                conflictPolicy = policy,
                awaitIfPaused = { execScope.checkPausedAndActive() }
            ) { fraction, doneItems, totalItems, transferredBytes, maxBytes, currentFile ->
                execScope.reportProgress(
                    fraction = fraction,
                    processedItems = doneItems,
                    totalItems = totalItems,
                    transferredBytes = transferredBytes,
                    totalBytes = maxBytes,
                    currentItemName = currentFile
                )
            }
            result.onSuccess { count ->
                withContext(Dispatchers.Main) {
                    refreshBothPanes()
                    postMessage("Imported $count file(s) into '${resolveTabTitle(destinationPath)}'.")
                }
            }.onFailure { err ->
                withContext(Dispatchers.Main) {
                    postMessage("Import failed: ${err.message}")
                }
            }
            result
        }
    }

    fun calculateChecksumsForSelection(paneId: PaneId = _activePaneId.value) {
        val state = currentPaneState(paneId)
        val selectedFiles = state.entries.filter { state.selectedPaths.contains(it.path) && !it.isDirectory }
        if (selectedFiles.isEmpty()) {
            postMessage("Select one or more files to compute SHA-256 checksums.")
            return
        }
        if (selectedFiles.size == 1) {
            openPropertiesDialog(selectedFiles.first())
            return
        }

        val totalBytes = selectedFiles.sumOf { it.sizeBytes.coerceAtLeast(1L) }.coerceAtLeast(1L)
        operationController.launchOperation(
            type = OperationType.CHECKSUM_VERIFY,
            title = "SHA-256 Batch Verify (${selectedFiles.size} files)",
            sourcePaths = selectedFiles.map { it.path },
            destinationPath = state.currentPath,
            totalItems = selectedFiles.size,
            totalBytes = totalBytes,
            indeterminate = false,
            canPause = true,
            canCancel = true,
            canRetry = true,
            onRetryRequest = { calculateChecksumsForSelection(paneId) }
        ) { execScope ->
            val digestLines = mutableListOf<String>()
            var accumulatedBytes = 0L
            selectedFiles.forEachIndexed { index, fileEntry ->
                execScope.checkPausedAndActive()
                val baseBeforeFile = accumulatedBytes
                val hashRes = FormatDetectorAndMetadataEngine.computeSha256ForPathOrUri(
                    context = getApplication(),
                    pathOrUri = fileEntry.path,
                    totalBytesHint = fileEntry.sizeBytes
                ) { doneFileBytes, _ ->
                    val currentTotal = (baseBeforeFile + doneFileBytes).coerceAtMost(totalBytes)
                    execScope.reportProgress(
                        fraction = currentTotal.toFloat() / totalBytes.toFloat(),
                        processedItems = index,
                        totalItems = selectedFiles.size,
                        transferredBytes = currentTotal,
                        totalBytes = totalBytes,
                        currentItemName = fileEntry.name
                    )
                }
                val hash = hashRes.getOrDefault("ERROR")
                digestLines += "$hash  ${fileEntry.name}"
                accumulatedBytes += fileEntry.sizeBytes.coerceAtLeast(1L)
                execScope.reportProgress(
                    fraction = accumulatedBytes.toFloat() / totalBytes.toFloat(),
                    processedItems = index + 1,
                    totalItems = selectedFiles.size,
                    transferredBytes = accumulatedBytes,
                    totalBytes = totalBytes,
                    currentItemName = fileEntry.name
                )
            }
            withContext(Dispatchers.Main) {
                copyTextToSystemClipboard(
                    text = digestLines.joinToString("\n"),
                    clipLabel = "SHA256SUMS",
                    confirmationMessage = "Computed & copied SHA-256 digests for ${selectedFiles.size} files!"
                )
            }
            Result.success(selectedFiles.size)
        }
    }

    fun shareSelectedFiles(paneId: PaneId = _activePaneId.value) {
        val state = currentPaneState(paneId)
        val selectedFiles = state.entries.filter { state.selectedPaths.contains(it.path) && !it.isDirectory }
        if (selectedFiles.isEmpty()) {
            postMessage("Select one or more files to share.")
            return
        }
        if (selectedFiles.size == 1) {
            shareFileEntry(selectedFiles.first())
            return
        }
        runCatching {
            val app = getApplication<Application>()
            val uris = ArrayList<Uri>()
            selectedFiles.forEach { entry ->
                val u = if (entry.isSafUri) {
                    Uri.parse(entry.path)
                } else {
                    FileProvider.getUriForFile(app, "${app.packageName}.fileprovider", File(entry.path))
                }
                uris.add(u)
            }
            val shareIntent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = "*/*"
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = Intent.createChooser(shareIntent, "Share ${uris.size} files").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            app.startActivity(chooser)
        }.onFailure {
            postMessage("Multi-file share unavailable: ${it.message}")
        }
    }

    fun updateFabSettings(transform: (com.explorerpro.domain.model.FabCustomizationSettings) -> com.explorerpro.domain.model.FabCustomizationSettings) {
        val current = userPreferences.value.fabSettings
        val updated = transform(current)
        viewModelScope.launch {
            preferencesManager.updateFabSettings(updated)
        }
    }

    fun postMessage(msg: String) {
        _snackbarMessage.value = msg
    }

    fun clearSnackbarMessage() {
        _snackbarMessage.value = null
    }
}
