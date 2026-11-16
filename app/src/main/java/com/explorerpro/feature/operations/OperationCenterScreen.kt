package com.explorerpro.feature.operations

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FolderSpecial
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.explorerpro.core.designsystem.progress.ExplorerCircularProgressIndicator
import com.explorerpro.core.designsystem.progress.ExplorerProgressIndicator
import com.explorerpro.core.designsystem.progress.InlineProgressIndicator
import com.explorerpro.core.designsystem.progress.ProgressLabelPosition
import com.explorerpro.core.filesystem.FormatDetectorAndMetadataEngine
import com.explorerpro.data.local.OperationLogEntity
import com.explorerpro.domain.model.FileOperationTask
import com.explorerpro.domain.model.OperationStatus
import com.explorerpro.domain.model.OperationType
import com.explorerpro.ui.theme.FluentAmber400
import com.explorerpro.ui.theme.FluentEmerald400
import com.explorerpro.ui.theme.FluentRose400

enum class OperationStatusFilter(val label: String) {
    ALL("All"),
    ACTIVE("Active"),
    PAUSED("Paused"),
    COMPLETED("Completed"),
    FAILED("Failed"),
    CANCELLED("Cancelled")
}

enum class OperationCategoryFilter(val label: String) {
    ALL("All Types"),
    TRANSFER("Copy & Move"),
    ARCHIVE("Archive"),
    DELETE("Delete"),
    RENAME("Rename"),
    ANALYSIS("Scan & Verify")
}

enum class OperationSortOrder(val label: String) {
    NEWEST_FIRST("Newest First"),
    BY_STATUS("By Status")
}

/**
 * Compact in-app Floating Operation Indicator (Section 6).
 * Appears automatically when background operations are active or paused, displays aggregate
 * progress and task count, and opens the Operation Center when tapped.
 */
@Composable
fun FloatingOperationIndicator(
    tasks: List<FileOperationTask>,
    onOpenOperationCenter: () -> Unit,
    onPauseTask: (String) -> Unit,
    onResumeTask: (String) -> Unit,
    onCancelTask: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val activeOrPaused = remember(tasks) {
        tasks.filter { it.isActiveOrPaused }
    }
    val primaryTask = activeOrPaused.firstOrNull()

    AnimatedVisibility(
        visible = primaryTask != null,
        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
        modifier = modifier
    ) {
        if (primaryTask != null) {
            val isIndeterminate = activeOrPaused.all { it.indeterminate }
            val aggregatePercent = remember(activeOrPaused) {
                val determinates = activeOrPaused.filter { !it.indeterminate }
                if (determinates.isEmpty()) 0f
                else (determinates.map { it.progressFraction }.average().toFloat() * 100f).coerceIn(0f, 100f)
            }

            Surface(
                tonalElevation = 8.dp,
                shadowElevation = 10.dp,
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .widthIn(max = 520.dp)
                    .fillMaxWidth()
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                        shape = RoundedCornerShape(16.dp)
                    )
                    .clip(RoundedCornerShape(16.dp))
                    .clickable(onClick = onOpenOperationCenter)
                    .testTag("floating_operation_indicator")
            ) {
                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        ExplorerCircularProgressIndicator(
                            value = aggregatePercent,
                            min = 0f,
                            max = 100f,
                            indeterminate = isIndeterminate,
                            showCenterPercentage = !isIndeterminate,
                            size = 38.dp,
                            strokeWidth = 3.5.dp,
                            status = primaryTask.status
                        )

                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = if (activeOrPaused.size == 1) {
                                        primaryTask.title
                                    } else {
                                        "${activeOrPaused.size} Active Operations • ${primaryTask.title}"
                                    },
                                    style = MaterialTheme.typography.titleSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                            }

                            val speedStr = FormatDetectorAndMetadataEngine.formatSpeed(primaryTask.speedBytesPerSec)
                            val etaStr = FormatDetectorAndMetadataEngine.formatEtaSeconds(primaryTask.estimatedRemainingSeconds)
                            val subtitle = buildString {
                                if (primaryTask.currentItemName.isNotEmpty()) {
                                    append(primaryTask.currentItemName)
                                } else {
                                    append("${primaryTask.processedItems}/${primaryTask.totalItems} items")
                                }
                                if (speedStr.isNotEmpty()) append(" • $speedStr")
                                if (etaStr.isNotEmpty()) append(" • $etaStr")
                            }
                            Text(
                                text = subtitle,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        if (primaryTask.canPause) {
                            IconButton(
                                onClick = {
                                    if (primaryTask.status == OperationStatus.PAUSED) {
                                        onResumeTask(primaryTask.id)
                                    } else {
                                        onPauseTask(primaryTask.id)
                                    }
                                },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = if (primaryTask.status == OperationStatus.PAUSED) {
                                        Icons.Default.PlayArrow
                                    } else {
                                        Icons.Default.Pause
                                    },
                                    contentDescription = if (primaryTask.status == OperationStatus.PAUSED) "Resume" else "Pause",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        if (primaryTask.canCancel) {
                            IconButton(
                                onClick = { onCancelTask(primaryTask.id) },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Cancel operation",
                                    tint = FluentRose400
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    ExplorerProgressIndicator(
                        value = aggregatePercent,
                        min = 0f,
                        max = 100f,
                        labelPosition = ProgressLabelPosition.TOP_FLOATING,
                        showPercentage = !isIndeterminate,
                        showStatus = false,
                        indeterminate = isIndeterminate,
                        status = primaryTask.status,
                        compact = true,
                        trackHeight = 5.dp
                    )
                }
            }
        }
    }
}

/**
 * Full-Featured Operation Center Screen & Modal (Section 5).
 */
@Composable
fun OperationCenterModal(
    tasks: List<FileOperationTask>,
    historyLogs: List<OperationLogEntity>,
    onPauseTask: (String) -> Unit,
    onResumeTask: (String) -> Unit,
    onCancelTask: (String) -> Unit,
    onRetryTask: (String) -> Unit,
    onDismissTask: (String) -> Unit,
    onClearCompleted: () -> Unit,
    onPauseAll: () -> Unit = {},
    onResumeAll: () -> Unit = {},
    onRetryAllFailed: () -> Unit = {},
    fabSettings: com.explorerpro.domain.model.FabCustomizationSettings = com.explorerpro.domain.model.FabCustomizationSettings(),
    onDismiss: () -> Unit
) {
    var statusFilter by remember { mutableStateOf(OperationStatusFilter.ALL) }
    var categoryFilter by remember { mutableStateOf(OperationCategoryFilter.ALL) }
    var sortOrder by remember { mutableStateOf(OperationSortOrder.NEWEST_FIRST) }
    var inspectedTask by remember { mutableStateOf<FileOperationTask?>(null) }

    val activeTasks = remember(tasks) { tasks.filter { it.isActiveOrPaused } }
    val aggregatePercent = remember(activeTasks) {
        val determinates = activeTasks.filter { !it.indeterminate }
        if (determinates.isEmpty()) 0f
        else (determinates.map { it.progressFraction }.average().toFloat() * 100f).coerceIn(0f, 100f)
    }
    val allIndeterminate = activeTasks.isNotEmpty() && activeTasks.all { it.indeterminate }

    val filteredTasks = remember(tasks, statusFilter, categoryFilter, sortOrder) {
        tasks.filter { task ->
            val matchesStatus = when (statusFilter) {
                OperationStatusFilter.ALL -> true
                OperationStatusFilter.ACTIVE -> task.status == OperationStatus.RUNNING || task.status == OperationStatus.QUEUED
                OperationStatusFilter.PAUSED -> task.status == OperationStatus.PAUSED
                OperationStatusFilter.COMPLETED -> task.status == OperationStatus.COMPLETED
                OperationStatusFilter.FAILED -> task.status == OperationStatus.FAILED || task.status == OperationStatus.INTERRUPTED
                OperationStatusFilter.CANCELLED -> task.status == OperationStatus.CANCELLED || task.status == OperationStatus.CANCELLING
            }
            val matchesCategory = when (categoryFilter) {
                OperationCategoryFilter.ALL -> true
                OperationCategoryFilter.TRANSFER -> task.type in setOf(OperationType.COPY, OperationType.MOVE, OperationType.RESTORE)
                OperationCategoryFilter.ARCHIVE -> task.type in setOf(OperationType.ARCHIVE_EXTRACT, OperationType.ARCHIVE_CREATE, OperationType.ARCHIVE_VERIFY)
                OperationCategoryFilter.DELETE -> task.type in setOf(OperationType.DELETE, OperationType.RECYCLE)
                OperationCategoryFilter.RENAME -> task.type == OperationType.BATCH_RENAME
                OperationCategoryFilter.ANALYSIS -> task.type in setOf(
                    OperationType.CHECKSUM_VERIFY,
                    OperationType.THUMBNAIL_GENERATION,
                    OperationType.SEARCH_INDEXING,
                    OperationType.FOLDER_INTELLIGENCE,
                    OperationType.STORAGE_ANALYSIS
                )
            }
            matchesStatus && matchesCategory
        }.let { list ->
            when (sortOrder) {
                OperationSortOrder.NEWEST_FIRST -> list.sortedByDescending { it.timestamp }
                OperationSortOrder.BY_STATUS -> list.sortedWith(
                    compareBy<FileOperationTask> { it.status.ordinal }.thenByDescending { it.timestamp }
                )
            }
        }
    }

    val opFabModel = remember(tasks, historyLogs, fabSettings) {
        val hasRunning = tasks.any { it.status == OperationStatus.RUNNING && it.canPause }
        val hasPaused = tasks.any { it.status == OperationStatus.PAUSED }
        val hasFailed = tasks.any { it.status == OperationStatus.FAILED && it.canRetry }
        val hasCompleted = tasks.any { !it.isActiveOrPaused } || historyLogs.isNotEmpty()
        com.explorerpro.core.domain.actions.QuickActionResolver.resolve(
            com.explorerpro.core.domain.actions.FabResolutionContext(
                destination = com.explorerpro.core.domain.actions.ExplorerScreenDestination.OPERATION_CENTER,
                currentPath = "",
                isDirectoryWritable = true,
                selectedItems = emptyList(),
                clipboard = com.explorerpro.domain.model.ClipboardState(),
                dualPaneEnabled = false,
                activePaneId = com.explorerpro.domain.model.PaneId.LEFT,
                isSearchActive = false,
                isRecursiveSearch = false,
                isScrollingDown = false,
                isKeyboardOpen = false,
                hasRunningOperations = hasRunning,
                hasPausedOperations = hasPaused,
                hasFailedOperations = hasFailed,
                hasCompletedOperations = hasCompleted,
                settings = fabSettings
            )
        )
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            color = MaterialTheme.colorScheme.background,
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .testTag("operation_center_screen")
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Header Bar
                    Surface(
                        tonalElevation = 3.dp,
                        color = MaterialTheme.colorScheme.surface
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(onClick = onDismiss) {
                                    Icon(Icons.Default.Close, contentDescription = "Close Operation Center")
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Operation Center",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${activeTasks.size} active • ${tasks.count { it.status == OperationStatus.COMPLETED }} completed • ${historyLogs.size} logs",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        sortOrder = if (sortOrder == OperationSortOrder.NEWEST_FIRST) {
                                            OperationSortOrder.BY_STATUS
                                        } else {
                                            OperationSortOrder.NEWEST_FIRST
                                        }
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                    modifier = Modifier.defaultMinSize(minWidth = 1.dp, minHeight = 36.dp)
                                ) {
                                    Icon(Icons.Default.SwapVert, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        text = sortOrder.label,
                                        style = MaterialTheme.typography.labelMedium,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }

                                IconButton(
                                    onClick = onClearCompleted,
                                    modifier = Modifier.testTag("clear_completed_operations_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ClearAll,
                                        contentDescription = "Clear Completed History",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }

                    // Overall Aggregate Progress Card when tasks are running or paused
                    if (activeTasks.isNotEmpty()) {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                val totalBytes = activeTasks.sumOf { it.totalBytes }
                                val doneBytes = activeTasks.sumOf { it.transferredBytes }
                                val totalSpeed = activeTasks.sumOf { it.speedBytesPerSec }
                                val summaryDesc = buildString {
                                    append("${activeTasks.size} concurrent task(s)")
                                    if (totalBytes > 0L) {
                                        append(" • ${FormatDetectorAndMetadataEngine.formatBytes(doneBytes)} / ${FormatDetectorAndMetadataEngine.formatBytes(totalBytes)}")
                                    }
                                    if (totalSpeed > 0L) {
                                        append(" • ${FormatDetectorAndMetadataEngine.formatSpeed(totalSpeed)}")
                                    }
                                }
                                ExplorerProgressIndicator(
                                    value = aggregatePercent,
                                    min = 0f,
                                    max = 100f,
                                    labelPosition = ProgressLabelPosition.TOP_FLOATING,
                                    showPercentage = !allIndeterminate,
                                    showStatus = true,
                                    indeterminate = allIndeterminate,
                                    status = if (activeTasks.any { it.status == OperationStatus.RUNNING }) {
                                        OperationStatus.RUNNING
                                    } else {
                                        OperationStatus.PAUSED
                                    },
                                    operationLabel = "Overall Queue Progress",
                                    statusDescription = summaryDesc
                                )
                            }
                        }
                    }

                    // Filter Chips: Status & Category
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            OperationStatusFilter.entries.forEach { filter ->
                                FilterChip(
                                    selected = statusFilter == filter,
                                    onClick = { statusFilter = filter },
                                    label = { Text(filter.label) }
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            OperationCategoryFilter.entries.forEach { cat ->
                                FilterChip(
                                    selected = categoryFilter == cat,
                                    onClick = { categoryFilter = cat },
                                    label = { Text(cat.label) }
                                )
                            }
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                    // Main Scrollable Operations & History List
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (filteredTasks.isEmpty() && historyLogs.isEmpty()) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 48.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Icon(
                                            imageVector = Icons.Default.CheckCircle,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                                            modifier = Modifier.size(48.dp)
                                        )
                                        Spacer(Modifier.height(10.dp))
                                        Text(
                                            text = "No matching operations in queue",
                                            style = MaterialTheme.typography.titleMedium
                                        )
                                        Text(
                                            text = "File transfers, multi-format archive extractions, checksums, and storage scans appear here in real time.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }

                        items(filteredTasks, key = { it.id }) { task ->
                            OperationTaskCard(
                                task = task,
                                onPause = { onPauseTask(task.id) },
                                onResume = { onResumeTask(task.id) },
                                onCancel = { onCancelTask(task.id) },
                                onRetry = { onRetryTask(task.id) },
                                onViewDetails = { inspectedTask = task },
                                onDismiss = { onDismissTask(task.id) }
                            )
                        }

                        if (statusFilter == OperationStatusFilter.ALL || statusFilter == OperationStatusFilter.COMPLETED) {
                            val activeIds = tasks.map { it.id }.toSet()
                            val archivedLogs = historyLogs.filterNot { activeIds.contains(it.id) }
                            if (archivedLogs.isNotEmpty()) {
                                item {
                                    Spacer(Modifier.height(8.dp))
                                    Text(
                                        text = "Persisted Operation History (${archivedLogs.size})",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                items(archivedLogs, key = { "log_${it.id}" }) { log ->
                                    PersistedOperationLogCard(log = log)
                                }
                            }
                        }

                        item { Spacer(Modifier.height(88.dp)) }
                    }
                }

                // Operation Center Contextual Speed Dial FAB
                com.explorerpro.core.designsystem.fab.ContextAwareExplorerFabHost(
                    model = opFabModel,
                    settings = fabSettings,
                    onActionSelected = { action ->
                        when (action.id) {
                            com.explorerpro.core.domain.actions.QuickActionId.OP_RETRY_ALL_FAILED -> onRetryAllFailed()
                            com.explorerpro.core.domain.actions.QuickActionId.OP_PAUSE_ALL_RUNNING -> onPauseAll()
                            com.explorerpro.core.domain.actions.QuickActionId.OP_RESUME_ALL_PAUSED -> onResumeAll()
                            com.explorerpro.core.domain.actions.QuickActionId.OP_CLEAR_COMPLETED -> onClearCompleted()
                            else -> Unit
                        }
                    }
                )
            }
        }
    }

    inspectedTask?.let { detailTask ->
        OperationDetailsDialog(
            task = detailTask,
            onDismiss = { inspectedTask = null }
        )
    }
}

@Composable
fun OperationTaskCard(
    task: FileOperationTask,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    onViewDetails: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val opIcon = remember(task.type) { iconForOperationType(task.type) }

    val detailsLine = remember(
        task.processedItems,
        task.totalItems,
        task.transferredBytes,
        task.totalBytes,
        task.speedBytesPerSec,
        task.estimatedRemainingSeconds
    ) {
        buildString {
            append("${task.processedItems}/${task.totalItems} items")
            if (task.totalBytes > 0L || task.transferredBytes > 0L) {
                append(" • ${FormatDetectorAndMetadataEngine.formatBytes(task.transferredBytes)}")
                if (task.totalBytes > 0L) {
                    append(" / ${FormatDetectorAndMetadataEngine.formatBytes(task.totalBytes)}")
                }
            }
            val speedStr = FormatDetectorAndMetadataEngine.formatSpeed(task.speedBytesPerSec)
            if (speedStr.isNotEmpty() && task.status == OperationStatus.RUNNING) {
                append(" • $speedStr")
            }
            val etaStr = FormatDetectorAndMetadataEngine.formatEtaSeconds(task.estimatedRemainingSeconds)
            if (etaStr.isNotEmpty() && task.status == OperationStatus.RUNNING) {
                append(" • $etaStr")
            }
        }
    }

    Surface(
        tonalElevation = 2.dp,
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
        modifier = modifier
            .fillMaxWidth()
            .testTag("operation_card_${task.id}")
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.size(38.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = opIcon,
                            contentDescription = task.type.label,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = task.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (task.destinationPath.isNotBlank()) {
                        Text(
                            text = "To: ${task.destinationPath}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            if (task.currentItemName.isNotBlank()) {
                Text(
                    text = "Current: ${task.currentItemName}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Universal Progress Component with Top-Floating Percentage Label
            ExplorerProgressIndicator(
                value = task.progressValue,
                min = task.minValue,
                max = task.maxValue,
                labelPosition = ProgressLabelPosition.TOP_FLOATING,
                showPercentage = !task.indeterminate,
                showStatus = true,
                indeterminate = task.indeterminate,
                completed = task.status == OperationStatus.COMPLETED,
                status = task.status,
                operationLabel = task.type.label,
                statusDescription = detailsLine
            )

            if (!task.errorDetail.isNullOrBlank()) {
                Surface(
                    color = FluentRose400.copy(alpha = 0.14f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ErrorOutline,
                            contentDescription = null,
                            tint = FluentRose400,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = task.errorDetail,
                            style = MaterialTheme.typography.labelSmall,
                            color = FluentRose400
                        )
                    }
                }
            }

            // Action Controls — only exposing actions supported by the underlying operation
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onViewDetails) {
                    Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Details")
                }

                if (task.status == OperationStatus.RUNNING && task.canPause) {
                    TextButton(onClick = onPause) {
                        Icon(Icons.Default.Pause, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Pause")
                    }
                }

                if (task.status == OperationStatus.PAUSED && task.canPause) {
                    TextButton(onClick = onResume) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Resume")
                    }
                }

                if (task.isActiveOrPaused && task.canCancel) {
                    TextButton(onClick = onCancel) {
                        Icon(
                            Icons.Default.Cancel,
                            contentDescription = null,
                            tint = FluentRose400,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("Cancel", color = FluentRose400)
                    }
                }

                if ((task.status == OperationStatus.FAILED || task.status == OperationStatus.CANCELLED) && task.canRetry) {
                    TextButton(onClick = onRetry) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Retry")
                    }
                }

                if (!task.isActiveOrPaused) {
                    TextButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Dismiss")
                    }
                }
            }
        }
    }
}

@Composable
private fun PersistedOperationLogCard(log: OperationLogEntity) {
    val isFailed = log.status.contains("Fail", ignoreCase = true)
    val isCancelled = log.status.contains("Cancel", ignoreCase = true)
    val statusEnum = when {
        isFailed -> OperationStatus.FAILED
        isCancelled -> OperationStatus.CANCELLED
        else -> OperationStatus.COMPLETED
    }

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${log.operationType}: ${log.title}",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "${log.details} • ${FormatDetectorAndMetadataEngine.formatBytes(log.totalBytes)} • ${FormatDetectorAndMetadataEngine.formatTimestamp(log.timestamp)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            InlineProgressIndicator(
                value = if (statusEnum == OperationStatus.COMPLETED) 100f else 0f,
                status = statusEnum
            )
        }
    }
}

@Composable
fun OperationDetailsDialog(
    task: FileOperationTask,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Operation Details — ${task.title}") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                DetailRow("Operation ID", task.id)
                DetailRow("Operation Type", task.type.label)
                DetailRow("Status", task.status.label)
                DetailRow("Progress", "${task.progressValue.toInt()}% (${task.processedItems} / ${task.totalItems} items)")
                DetailRow(
                    "Transferred Size",
                    "${FormatDetectorAndMetadataEngine.formatBytes(task.transferredBytes)} / ${FormatDetectorAndMetadataEngine.formatBytes(task.totalBytes)}"
                )
                if (task.speedBytesPerSec > 0L) {
                    DetailRow("Transfer Speed", FormatDetectorAndMetadataEngine.formatSpeed(task.speedBytesPerSec))
                }
                task.estimatedRemainingSeconds?.let {
                    DetailRow("Estimated Time Remaining", FormatDetectorAndMetadataEngine.formatEtaSeconds(it))
                }
                if (task.currentItemName.isNotBlank()) {
                    DetailRow("Current Item", task.currentItemName)
                }
                if (task.destinationPath.isNotBlank()) {
                    DetailRow("Destination", task.destinationPath)
                }
                DetailRow("Started At", FormatDetectorAndMetadataEngine.formatTimestamp(task.timestamp))
                task.completionTimeMs?.let {
                    DetailRow("Completed At", FormatDetectorAndMetadataEngine.formatTimestamp(it))
                }
                if (task.skippedCount > 0 || task.errorCount > 0) {
                    DetailRow("Skipped / Errors", "${task.skippedCount} skipped, ${task.errorCount} errors")
                }
                if (!task.errorDetail.isNullOrBlank()) {
                    DetailRow("Error Details", task.errorDetail)
                }
                if (task.sourcePaths.isNotEmpty()) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    Text(
                        text = "Source Items (${task.sourcePaths.size}):",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    task.sourcePaths.take(20).forEach { src ->
                        Text(
                            text = "• $src",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

@Composable
private fun DetailRow(label: String, value: String) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

fun iconForOperationType(type: OperationType): ImageVector = when (type) {
    OperationType.COPY -> Icons.Default.ContentCopy
    OperationType.MOVE, OperationType.RESTORE -> Icons.AutoMirrored.Filled.DriveFileMove
    OperationType.DELETE, OperationType.RECYCLE -> Icons.Default.Delete
    OperationType.BATCH_RENAME -> Icons.Default.DriveFileRenameOutline
    OperationType.CHECKSUM_VERIFY, OperationType.ARCHIVE_VERIFY -> Icons.Default.Security
    OperationType.ARCHIVE_EXTRACT -> Icons.Default.Unarchive
    OperationType.ARCHIVE_CREATE -> Icons.Default.Archive
    OperationType.THUMBNAIL_GENERATION -> Icons.Default.Image
    OperationType.SEARCH_INDEXING -> Icons.Default.Search
    OperationType.FOLDER_INTELLIGENCE -> Icons.Default.FolderSpecial
    OperationType.STORAGE_ANALYSIS -> Icons.Default.Analytics
}
