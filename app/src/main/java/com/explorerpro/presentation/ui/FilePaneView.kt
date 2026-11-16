package com.explorerpro.presentation.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.explorerpro.R
import com.explorerpro.core.designsystem.progress.MinimalProgressIndicator
import com.explorerpro.core.filesystem.FormatDetectorAndMetadataEngine
import com.explorerpro.domain.model.DetectedFileFormat
import com.explorerpro.domain.model.FileCategory
import com.explorerpro.domain.model.FileEntry
import com.explorerpro.domain.model.FolderIntelligenceResult
import com.explorerpro.domain.model.GroupBy
import com.explorerpro.domain.model.PaneState
import com.explorerpro.domain.model.SortField
import com.explorerpro.domain.model.SortOrder
import com.explorerpro.domain.model.ViewMode
import com.explorerpro.ui.theme.FluentAmber400
import com.explorerpro.ui.theme.FluentCyan400
import com.explorerpro.ui.theme.FluentEmerald400
import com.explorerpro.ui.theme.FluentFolderGold
import com.explorerpro.ui.theme.FluentRose400
import com.explorerpro.ui.theme.FluentTeal400
import java.io.File

@Composable
fun ExplorerPaneContainer(
    paneState: PaneState,
    isActivePane: Boolean,
    dualPaneEnabled: Boolean,
    workspaceRootPath: String,
    favoritePaths: Set<String>,
    onActivatePane: () -> Unit,
    onSelectTab: (String) -> Unit,
    onCloseTab: (String) -> Unit,
    onNewTab: () -> Unit,
    onDuplicateTab: () -> Unit,
    onReopenClosedTab: () -> Unit,
    onNavigateBack: () -> Unit,
    onNavigateForward: () -> Unit,
    onNavigateToPath: (String) -> Unit,
    onCopyPath: (String) -> Unit,
    onSearchQueryChange: (String, FileCategory?) -> Unit,
    onEntryClick: (FileEntry) -> Unit,
    onEntryLongClick: (FileEntry) -> Unit,
    onOpenInNewTab: (FileEntry) -> Unit,
    onToggleFavorite: (FileEntry) -> Unit,
    onCopyEntryPath: (FileEntry) -> Unit,
    onCopyEntryName: (FileEntry) -> Unit,
    onCopyEntrySafUri: (FileEntry) -> Unit,
    onRenameEntry: (FileEntry) -> Unit,
    onInspectProperties: (FileEntry) -> Unit,
    onShareEntry: (FileEntry) -> Unit,
    onDeleteEntry: (FileEntry) -> Unit,
    onExtractArchive: (FileEntry) -> Unit,
    onExtractArchiveHere: (FileEntry) -> Unit = {},
    onExtractArchiveToFolder: (FileEntry) -> Unit = {},
    onConvertArchive: (FileEntry) -> Unit = {},
    onCopyToOtherPane: () -> Unit,
    onMoveToOtherPane: () -> Unit,
    onHeaderSortClick: (SortField) -> Unit,
    onRequestDeepFolderStats: () -> Unit,
    onScrollDirectionChanged: (Boolean) -> Unit = {},
    onOpenActionSheet: (FileEntry) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val borderColor = if (isActivePane && dualPaneEnabled) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
    }

    Column(
        modifier = modifier
            .border(width = if (dualPaneEnabled) 1.5.dp else 0.dp, color = borderColor)
            .clickable(
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                indication = null,
                onClick = onActivatePane
            )
            .testTag("explorer_pane_${paneState.paneId.name}")
    ) {
        PaneTabsAndAddressBar(
            paneState = paneState,
            isActivePane = isActivePane,
            workspaceRootPath = workspaceRootPath,
            onSelectTab = onSelectTab,
            onCloseTab = onCloseTab,
            onNewTab = onNewTab,
            onDuplicateTab = onDuplicateTab,
            onReopenClosedTab = onReopenClosedTab,
            onNavigateBack = onNavigateBack,
            onNavigateForward = onNavigateForward,
            onNavigateToPath = onNavigateToPath,
            onCopyPath = onCopyPath,
            onSearchQueryChange = onSearchQueryChange
        )

        if (paneState.isSearching) {
            MinimalProgressIndicator(
                indeterminate = true,
                modifier = Modifier.fillMaxWidth()
            )
        }

        // Optional Dual-Pane Quick Transfer Bar when items are selected
        if (dualPaneEnabled && paneState.selectedPaths.isNotEmpty()) {
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "${paneState.selectedPaths.size} selected",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = onCopyToOtherPane) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Copy to Other Pane")
                        }
                        TextButton(onClick = onMoveToOtherPane) {
                            Icon(Icons.AutoMirrored.Filled.DriveFileMove, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Move to Other Pane")
                        }
                    }
                }
            }
        }

        // Main File Browser Area
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            when {
                paneState.isLoading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                paneState.errorMessage != null -> {
                    EmptyOrErrorDirectoryState(
                        message = paneState.errorMessage,
                        isError = true
                    )
                }
                paneState.entries.isEmpty() -> {
                    EmptyOrErrorDirectoryState(
                        message = "This folder is empty. Tap the + Floating Action Button or 3-dot menu (⋮) to create folders, files, or import content.",
                        isError = false
                    )
                }
                else -> {
                    when (paneState.viewMode) {
                        ViewMode.GRID -> GridFileBrowser(
                            paneState = paneState,
                            favoritePaths = favoritePaths,
                            onEntryClick = onEntryClick,
                            onEntryLongClick = onEntryLongClick,
                            onOpenInNewTab = onOpenInNewTab,
                            onToggleFavorite = onToggleFavorite,
                            onCopyEntryPath = onCopyEntryPath,
                            onCopyEntryName = onCopyEntryName,
                            onCopyEntrySafUri = onCopyEntrySafUri,
                            onRenameEntry = onRenameEntry,
                            onInspectProperties = onInspectProperties,
                            onShareEntry = onShareEntry,
                            onDeleteEntry = onDeleteEntry,
                            onExtractArchive = onExtractArchive,
                            onExtractArchiveHere = onExtractArchiveHere,
                            onExtractArchiveToFolder = onExtractArchiveToFolder,
                            onConvertArchive = onConvertArchive,
                            onOpenActionSheet = onOpenActionSheet,
                            onScrollDirectionChanged = onScrollDirectionChanged
                        )
                        ViewMode.SIMPLE_LIST -> SimpleListFileBrowser(
                            paneState = paneState,
                            favoritePaths = favoritePaths,
                            onEntryClick = onEntryClick,
                            onEntryLongClick = onEntryLongClick,
                            onOpenInNewTab = onOpenInNewTab,
                            onToggleFavorite = onToggleFavorite,
                            onCopyEntryPath = onCopyEntryPath,
                            onCopyEntryName = onCopyEntryName,
                            onCopyEntrySafUri = onCopyEntrySafUri,
                            onRenameEntry = onRenameEntry,
                            onInspectProperties = onInspectProperties,
                            onShareEntry = onShareEntry,
                            onDeleteEntry = onDeleteEntry,
                            onExtractArchive = onExtractArchive,
                            onExtractArchiveHere = onExtractArchiveHere,
                            onExtractArchiveToFolder = onExtractArchiveToFolder,
                            onConvertArchive = onConvertArchive,
                            onOpenActionSheet = onOpenActionSheet,
                            onScrollDirectionChanged = onScrollDirectionChanged
                        )
                        ViewMode.DETAILED_LIST -> DetailedListFileBrowser(
                            paneState = paneState,
                            favoritePaths = favoritePaths,
                            onHeaderSortClick = onHeaderSortClick,
                            onEntryClick = onEntryClick,
                            onEntryLongClick = onEntryLongClick,
                            onOpenInNewTab = onOpenInNewTab,
                            onToggleFavorite = onToggleFavorite,
                            onCopyEntryPath = onCopyEntryPath,
                            onCopyEntryName = onCopyEntryName,
                            onCopyEntrySafUri = onCopyEntrySafUri,
                            onRenameEntry = onRenameEntry,
                            onInspectProperties = onInspectProperties,
                            onShareEntry = onShareEntry,
                            onDeleteEntry = onDeleteEntry,
                            onExtractArchive = onExtractArchive,
                            onExtractArchiveHere = onExtractArchiveHere,
                            onExtractArchiveToFolder = onExtractArchiveToFolder,
                            onConvertArchive = onConvertArchive,
                            onOpenActionSheet = onOpenActionSheet,
                            onScrollDirectionChanged = onScrollDirectionChanged
                        )
                    }
                }
            }
        }

        // Bottom Status Bar & Folder Intelligence Summary
        PaneFolderIntelligenceStatusBar(
            stats = paneState.folderIntelligence,
            selectedCount = paneState.selectedPaths.size,
            onRequestDeepStats = onRequestDeepFolderStats
        )
    }
}

@Composable
private fun EmptyOrErrorDirectoryState(
    message: String,
    isError: Boolean
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(
            painter = painterResource(id = R.drawable.img_explorer_banner_1794685576752),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .width(220.dp)
                .height(120.dp)
                .clip(RoundedCornerShape(16.dp))
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = if (isError) "Storage Access Notice" else "Folder Empty",
            style = MaterialTheme.typography.titleMedium,
            color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GridFileBrowser(
    paneState: PaneState,
    favoritePaths: Set<String>,
    onEntryClick: (FileEntry) -> Unit,
    onEntryLongClick: (FileEntry) -> Unit,
    onOpenInNewTab: (FileEntry) -> Unit,
    onToggleFavorite: (FileEntry) -> Unit,
    onCopyEntryPath: (FileEntry) -> Unit,
    onCopyEntryName: (FileEntry) -> Unit,
    onCopyEntrySafUri: (FileEntry) -> Unit,
    onRenameEntry: (FileEntry) -> Unit,
    onInspectProperties: (FileEntry) -> Unit,
    onShareEntry: (FileEntry) -> Unit,
    onDeleteEntry: (FileEntry) -> Unit,
    onExtractArchive: (FileEntry) -> Unit,
    onExtractArchiveHere: (FileEntry) -> Unit,
    onExtractArchiveToFolder: (FileEntry) -> Unit,
    onConvertArchive: (FileEntry) -> Unit,
    onOpenActionSheet: (FileEntry) -> Unit,
    onScrollDirectionChanged: (Boolean) -> Unit
) {
    val gridState = rememberLazyGridState()
    var prevIndex by remember { mutableIntStateOf(0) }
    var prevOffset by remember { mutableIntStateOf(0) }

    LaunchedEffect(gridState) {
        snapshotFlow { gridState.firstVisibleItemIndex to gridState.firstVisibleItemScrollOffset }
            .collect { (index, offset) ->
                if (index == 0 && offset < 20) {
                    onScrollDirectionChanged(false)
                } else if (index > prevIndex || (index == prevIndex && offset > prevOffset + 24)) {
                    onScrollDirectionChanged(true)
                } else if (index < prevIndex || (index == prevIndex && offset < prevOffset - 24)) {
                    onScrollDirectionChanged(false)
                }
                prevIndex = index
                prevOffset = offset
            }
    }

    LazyVerticalGrid(
        state = gridState,
        columns = GridCells.Fixed(paneState.gridColumns.coerceIn(2, 6)),
        contentPadding = PaddingValues(start = 10.dp, end = 10.dp, top = 10.dp, bottom = 96.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(paneState.entries, key = { it.path }) { entry ->
            val isSelected = paneState.selectedPaths.contains(entry.path)
            val isFav = favoritePaths.contains(entry.path)
            var showContextMenu by remember { mutableStateOf(false) }

            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (isSelected) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    }
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .combinedClickable(
                        onClick = { onEntryClick(entry) },
                        onLongClick = { onEntryLongClick(entry) }
                    )
                    .testTag("file_grid_item_${entry.name}")
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1.25f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surface),
                        contentAlignment = Alignment.Center
                    ) {
                        UniversalFileThumbnail(
                            entry = entry,
                            iconSize = 42.dp,
                            fillBounds = true
                        )
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "Selected",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .padding(6.dp)
                                    .size(20.dp)
                            )
                        }
                        Box(modifier = Modifier.align(Alignment.TopEnd)) {
                            IconButton(
                                onClick = { showContextMenu = true },
                                modifier = Modifier
                                    .size(28.dp)
                                    .testTag("context_menu_button_${entry.name}")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.MoreVert,
                                    contentDescription = "Context menu for ${entry.name}",
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            FileEntryContextMenu(
                                expanded = showContextMenu,
                                entry = entry,
                                isFavorite = isFav,
                                onDismiss = { showContextMenu = false },
                                onOpen = { onEntryClick(entry) },
                                onOpenInNewTab = { onOpenInNewTab(entry) },
                                onToggleFavorite = { onToggleFavorite(entry) },
                                onCopyPath = { onCopyEntryPath(entry) },
                                onCopyName = { onCopyEntryName(entry) },
                                onCopySafUri = { onCopyEntrySafUri(entry) },
                                onRename = { onRenameEntry(entry) },
                                onExtractArchive = { onExtractArchive(entry) },
                                onExtractArchiveHere = { onExtractArchiveHere(entry) },
                                onExtractArchiveToFolder = { onExtractArchiveToFolder(entry) },
                                onConvertArchive = { onConvertArchive(entry) },
                                onOpenActionSheet = { onOpenActionSheet(entry) },
                                onShare = { onShareEntry(entry) },
                                onProperties = { onInspectProperties(entry) },
                                onDelete = { onDeleteEntry(entry) }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = entry.name,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = if (entry.isDirectory) {
                            "${entry.directChildCount ?: 0} items"
                        } else {
                            "${FormatDetectorAndMetadataEngine.formatBytes(entry.sizeBytes)} • ${entry.detectedFormat.displayName}"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SimpleListFileBrowser(
    paneState: PaneState,
    favoritePaths: Set<String>,
    onEntryClick: (FileEntry) -> Unit,
    onEntryLongClick: (FileEntry) -> Unit,
    onOpenInNewTab: (FileEntry) -> Unit,
    onToggleFavorite: (FileEntry) -> Unit,
    onCopyEntryPath: (FileEntry) -> Unit,
    onCopyEntryName: (FileEntry) -> Unit,
    onCopyEntrySafUri: (FileEntry) -> Unit,
    onRenameEntry: (FileEntry) -> Unit,
    onInspectProperties: (FileEntry) -> Unit,
    onShareEntry: (FileEntry) -> Unit,
    onDeleteEntry: (FileEntry) -> Unit,
    onExtractArchive: (FileEntry) -> Unit,
    onExtractArchiveHere: (FileEntry) -> Unit,
    onExtractArchiveToFolder: (FileEntry) -> Unit,
    onConvertArchive: (FileEntry) -> Unit,
    onOpenActionSheet: (FileEntry) -> Unit,
    onScrollDirectionChanged: (Boolean) -> Unit
) {
    val listState = rememberLazyListState()
    var prevIndex by remember { mutableIntStateOf(0) }
    var prevOffset by remember { mutableIntStateOf(0) }

    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
            .collect { (index, offset) ->
                if (index == 0 && offset < 20) {
                    onScrollDirectionChanged(false)
                } else if (index > prevIndex || (index == prevIndex && offset > prevOffset + 24)) {
                    onScrollDirectionChanged(true)
                } else if (index < prevIndex || (index == prevIndex && offset < prevOffset - 24)) {
                    onScrollDirectionChanged(false)
                }
                prevIndex = index
                prevOffset = offset
            }
    }

    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(top = 4.dp, bottom = 96.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        paneState.groupedEntries.forEach { (groupHeader, groupItems) ->
            if (paneState.groupBy != GroupBy.NONE) {
                item(key = "header_$groupHeader") {
                    GroupSectionHeader(title = groupHeader, count = groupItems.size)
                }
            }
            items(groupItems, key = { it.path }) { entry ->
                val isSelected = paneState.selectedPaths.contains(entry.path)
                val isFav = favoritePaths.contains(entry.path)
                var showMenu by remember { mutableStateOf(false) }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                            else Color.Transparent
                        )
                        .combinedClickable(
                            onClick = { onEntryClick(entry) },
                            onLongClick = { onEntryLongClick(entry) }
                        )
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                        .testTag("file_simple_row_${entry.name}"),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        UniversalFileThumbnail(entry = entry, iconSize = 24.dp, fillBounds = true)
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = entry.name,
                                style = MaterialTheme.typography.titleSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            if (isFav) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Icon(
                                    imageVector = Icons.Default.Star,
                                    contentDescription = "Favorited",
                                    tint = FluentAmber400,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                        Text(
                            text = if (entry.isDirectory) {
                                "Folder • ${entry.directChildCount ?: 0} items"
                            } else {
                                "${FormatDetectorAndMetadataEngine.formatBytes(entry.sizeBytes)} • ${entry.detectedFormat.displayName}"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Box {
                        IconButton(
                            onClick = { showMenu = true },
                            modifier = Modifier.testTag("context_menu_button_${entry.name}")
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "More options for ${entry.name}",
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        FileEntryContextMenu(
                            expanded = showMenu,
                            entry = entry,
                            isFavorite = isFav,
                            onDismiss = { showMenu = false },
                            onOpen = { onEntryClick(entry) },
                            onOpenInNewTab = { onOpenInNewTab(entry) },
                            onToggleFavorite = { onToggleFavorite(entry) },
                            onCopyPath = { onCopyEntryPath(entry) },
                            onCopyName = { onCopyEntryName(entry) },
                            onCopySafUri = { onCopyEntrySafUri(entry) },
                            onRename = { onRenameEntry(entry) },
                            onExtractArchive = { onExtractArchive(entry) },
                            onExtractArchiveHere = { onExtractArchiveHere(entry) },
                            onExtractArchiveToFolder = { onExtractArchiveToFolder(entry) },
                            onConvertArchive = { onConvertArchive(entry) },
                            onOpenActionSheet = { onOpenActionSheet(entry) },
                            onShare = { onShareEntry(entry) },
                            onProperties = { onInspectProperties(entry) },
                            onDelete = { onDeleteEntry(entry) }
                        )
                    }
                }
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f),
                    modifier = Modifier.padding(start = 64.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DetailedListFileBrowser(
    paneState: PaneState,
    favoritePaths: Set<String>,
    onHeaderSortClick: (SortField) -> Unit,
    onEntryClick: (FileEntry) -> Unit,
    onEntryLongClick: (FileEntry) -> Unit,
    onOpenInNewTab: (FileEntry) -> Unit,
    onToggleFavorite: (FileEntry) -> Unit,
    onCopyEntryPath: (FileEntry) -> Unit,
    onCopyEntryName: (FileEntry) -> Unit,
    onCopyEntrySafUri: (FileEntry) -> Unit,
    onRenameEntry: (FileEntry) -> Unit,
    onInspectProperties: (FileEntry) -> Unit,
    onShareEntry: (FileEntry) -> Unit,
    onDeleteEntry: (FileEntry) -> Unit,
    onExtractArchive: (FileEntry) -> Unit,
    onExtractArchiveHere: (FileEntry) -> Unit,
    onExtractArchiveToFolder: (FileEntry) -> Unit,
    onConvertArchive: (FileEntry) -> Unit,
    onOpenActionSheet: (FileEntry) -> Unit,
    onScrollDirectionChanged: (Boolean) -> Unit
) {
    val listState = rememberLazyListState()
    var prevIndex by remember { mutableIntStateOf(0) }
    var prevOffset by remember { mutableIntStateOf(0) }

    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
            .collect { (index, offset) ->
                if (index == 0 && offset < 20) {
                    onScrollDirectionChanged(false)
                } else if (index > prevIndex || (index == prevIndex && offset > prevOffset + 24)) {
                    onScrollDirectionChanged(true)
                } else if (index < prevIndex || (index == prevIndex && offset < prevOffset - 24)) {
                    onScrollDirectionChanged(false)
                }
                prevIndex = index
                prevOffset = offset
            }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Windows Explorer Sortable Column Headers
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SortableColumnHeader(
                    title = "Name",
                    field = SortField.NAME_NATURAL,
                    currentField = paneState.sortConfig.field,
                    currentOrder = paneState.sortConfig.order,
                    onClick = { onHeaderSortClick(SortField.NAME_NATURAL) },
                    modifier = Modifier.weight(2.2f)
                )
                SortableColumnHeader(
                    title = "Format",
                    field = SortField.DETECTED_FORMAT,
                    currentField = paneState.sortConfig.field,
                    currentOrder = paneState.sortConfig.order,
                    onClick = { onHeaderSortClick(SortField.DETECTED_FORMAT) },
                    modifier = Modifier.weight(1.3f)
                )
                SortableColumnHeader(
                    title = "Size",
                    field = SortField.SIZE,
                    currentField = paneState.sortConfig.field,
                    currentOrder = paneState.sortConfig.order,
                    onClick = { onHeaderSortClick(SortField.SIZE) },
                    modifier = Modifier.weight(1.0f)
                )
                SortableColumnHeader(
                    title = "Modified",
                    field = SortField.MODIFIED_DATE,
                    currentField = paneState.sortConfig.field,
                    currentOrder = paneState.sortConfig.order,
                    onClick = { onHeaderSortClick(SortField.MODIFIED_DATE) },
                    modifier = Modifier.weight(1.2f)
                )
                Spacer(modifier = Modifier.width(32.dp))
            }
        }

        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(bottom = 96.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            paneState.groupedEntries.forEach { (groupHeader, groupItems) ->
                if (paneState.groupBy != GroupBy.NONE) {
                    item(key = "detailed_group_$groupHeader") {
                        GroupSectionHeader(title = groupHeader, count = groupItems.size)
                    }
                }
                items(groupItems, key = { it.path }) { entry ->
                    val isSelected = paneState.selectedPaths.contains(entry.path)
                    val isFav = favoritePaths.contains(entry.path)
                    var showMenu by remember { mutableStateOf(false) }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.75f)
                                else Color.Transparent
                            )
                            .combinedClickable(
                                onClick = { onEntryClick(entry) },
                                onLongClick = { onEntryLongClick(entry) }
                            )
                            .padding(horizontal = 10.dp, vertical = 7.dp)
                            .testTag("file_detailed_row_${entry.name}"),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Column 1: Thumbnail + Name + Extension/Dimensions badge
                        Row(
                            modifier = Modifier.weight(2.2f),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                                contentAlignment = Alignment.Center
                            ) {
                                UniversalFileThumbnail(entry = entry, iconSize = 20.dp, fillBounds = true)
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = entry.name,
                                        style = MaterialTheme.typography.titleSmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (isFav) {
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Icon(
                                            imageVector = Icons.Default.Star,
                                            contentDescription = "Favorite",
                                            tint = FluentAmber400,
                                            modifier = Modifier.size(13.dp)
                                        )
                                    }
                                }
                                val subMeta = buildList {
                                    add(entry.permissions)
                                    entry.dimensionsText?.let { add(it) }
                                    entry.pageCount?.let { add("$it pages") }
                                }.joinToString(" • ")
                                Text(
                                    text = subMeta,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        // Column 2: Detected Format & Extension
                        Column(modifier = Modifier.weight(1.3f)) {
                            Text(
                                text = entry.detectedFormat.displayName,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = if (entry.isDirectory) "DIR" else entry.extension.uppercase().ifEmpty { "BIN" },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        // Column 3: Size or Direct Folder Item Count
                        Text(
                            text = if (entry.isDirectory) {
                                "${entry.directChildCount ?: 0} items"
                            } else {
                                FormatDetectorAndMetadataEngine.formatBytes(entry.sizeBytes)
                            },
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.weight(1.0f)
                        )

                        // Column 4: Modified Date
                        Text(
                            text = FormatDetectorAndMetadataEngine.formatTimestamp(entry.lastModified),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1.2f)
                        )

                        // Context Menu Button
                        Box(modifier = Modifier.width(32.dp), contentAlignment = Alignment.CenterEnd) {
                            IconButton(
                                onClick = { showMenu = true },
                                modifier = Modifier
                                    .size(28.dp)
                                    .testTag("context_menu_button_${entry.name}")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.MoreVert,
                                    contentDescription = "Context menu for ${entry.name}",
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            FileEntryContextMenu(
                                expanded = showMenu,
                                entry = entry,
                                isFavorite = isFav,
                                onDismiss = { showMenu = false },
                                onOpen = { onEntryClick(entry) },
                                onOpenInNewTab = { onOpenInNewTab(entry) },
                                onToggleFavorite = { onToggleFavorite(entry) },
                                onCopyPath = { onCopyEntryPath(entry) },
                                onCopyName = { onCopyEntryName(entry) },
                                onCopySafUri = { onCopyEntrySafUri(entry) },
                                onRename = { onRenameEntry(entry) },
                                onExtractArchive = { onExtractArchive(entry) },
                                onExtractArchiveHere = { onExtractArchiveHere(entry) },
                                onExtractArchiveToFolder = { onExtractArchiveToFolder(entry) },
                                onConvertArchive = { onConvertArchive(entry) },
                                onOpenActionSheet = { onOpenActionSheet(entry) },
                                onShare = { onShareEntry(entry) },
                                onProperties = { onInspectProperties(entry) },
                                onDelete = { onDeleteEntry(entry) }
                            )
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
                }
            }
        }
    }
}

@Composable
private fun SortableColumnHeader(
    title: String,
    field: SortField,
    currentField: SortField,
    currentOrder: SortOrder,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val active = field == currentField
    val arrow = if (!active) "" else if (currentOrder == SortOrder.ASCENDING) " ↑" else " ↓"
    Text(
        text = "$title$arrow",
        style = MaterialTheme.typography.labelMedium,
        fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
        color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 2.dp, horizontal = 2.dp)
    )
}

@Composable
private fun GroupSectionHeader(title: String, count: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
            .padding(horizontal = 12.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = "$count item(s)",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun UniversalFileThumbnail(
    entry: FileEntry,
    iconSize: androidx.compose.ui.unit.Dp,
    fillBounds: Boolean
) {
    if (!entry.isDirectory && entry.detectedFormat.isImage) {
        val model: Any = if (entry.isSafUri) android.net.Uri.parse(entry.path) else File(entry.path)
        AsyncImage(
            model = model,
            contentDescription = entry.name,
            contentScale = ContentScale.Crop,
            modifier = if (fillBounds) Modifier.fillMaxSize() else Modifier.size(iconSize)
        )
    } else {
        val (icon, tint) = resolveFileIconAndTint(entry)
        Icon(
            imageVector = icon,
            contentDescription = entry.detectedFormat.displayName,
            tint = tint,
            modifier = Modifier.size(iconSize)
        )
    }
}

fun resolveFileIconAndTint(entry: FileEntry): Pair<ImageVector, Color> {
    if (entry.isDirectory) return Icons.Default.Folder to FluentFolderGold
    return when (entry.detectedFormat) {
        DetectedFileFormat.PNG,
        DetectedFileFormat.JPEG,
        DetectedFileFormat.GIF,
        DetectedFileFormat.WEBP,
        DetectedFileFormat.BMP,
        DetectedFileFormat.SVG -> Icons.Default.Image to FluentTeal400
        DetectedFileFormat.PDF -> Icons.Default.PictureAsPdf to FluentRose400
        DetectedFileFormat.ZIP,
        DetectedFileFormat.TAR,
        DetectedFileFormat.GZIP,
        DetectedFileFormat.BZIP2,
        DetectedFileFormat.XZ,
        DetectedFileFormat.SEVEN_Z,
        DetectedFileFormat.RAR,
        DetectedFileFormat.ISO,
        DetectedFileFormat.LZ4,
        DetectedFileFormat.ZSTD,
        DetectedFileFormat.LZMA,
        DetectedFileFormat.CPIO,
        DetectedFileFormat.JAR -> Icons.Default.Archive to FluentAmber400
        DetectedFileFormat.APK -> Icons.Default.Android to FluentEmerald400
        DetectedFileFormat.MP3,
        DetectedFileFormat.WAV -> Icons.Default.AudioFile to FluentCyan400
        DetectedFileFormat.MP4 -> Icons.Default.VideoFile to FluentRose400
        DetectedFileFormat.ELF_BINARY,
        DetectedFileFormat.SHELL_SCRIPT,
        DetectedFileFormat.JSON,
        DetectedFileFormat.XML,
        DetectedFileFormat.KOTLIN_SOURCE -> Icons.Default.Code to FluentCyan400
        DetectedFileFormat.MARKDOWN,
        DetectedFileFormat.UTF8_TEXT,
        DetectedFileFormat.DOCX,
        DetectedFileFormat.XLSX,
        DetectedFileFormat.PPTX -> Icons.Default.Description to FluentCyan400
        DetectedFileFormat.SQLITE -> Icons.Default.Storage to FluentTeal400
        DetectedFileFormat.DIRECTORY -> Icons.Default.Folder to FluentFolderGold
        DetectedFileFormat.UNKNOWN -> Icons.AutoMirrored.Filled.InsertDriveFile to Color(0xFF94A3B8)
    }
}

private fun deriveArchiveFolderLabel(fileName: String): String {
    val lower = fileName.lowercase()
    return when {
        lower.endsWith(".tar.gz") -> fileName.dropLast(7)
        lower.endsWith(".tar.bz2") -> fileName.dropLast(8)
        lower.endsWith(".tar.xz") -> fileName.dropLast(7)
        lower.endsWith(".tar.lz4") -> fileName.dropLast(8)
        lower.endsWith(".tar.zstd") -> fileName.dropLast(9)
        lower.endsWith(".tar.zst") -> fileName.dropLast(8)
        lower.endsWith(".tar.lzma") -> fileName.dropLast(9)
        fileName.contains('.') -> fileName.substringBeforeLast('.')
        else -> "${fileName}_extracted"
    }.ifBlank { "Extracted" }
}

@Composable
private fun FileEntryContextMenu(
    expanded: Boolean,
    entry: FileEntry,
    isFavorite: Boolean,
    onDismiss: () -> Unit,
    onOpen: () -> Unit,
    onOpenInNewTab: () -> Unit,
    onToggleFavorite: () -> Unit,
    onCopyPath: () -> Unit,
    onCopyName: () -> Unit,
    onCopySafUri: () -> Unit,
    onRename: () -> Unit,
    onExtractArchive: () -> Unit,
    onExtractArchiveHere: () -> Unit,
    onExtractArchiveToFolder: () -> Unit,
    onConvertArchive: () -> Unit = {},
    onOpenActionSheet: () -> Unit = {},
    onShare: () -> Unit,
    onProperties: () -> Unit,
    onDelete: () -> Unit
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        DropdownMenuItem(
            text = { Text("Quick Action Sheet…", fontWeight = FontWeight.SemiBold) },
            leadingIcon = { Icon(Icons.Default.MoreVert, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            onClick = {
                onDismiss()
                onOpenActionSheet()
            },
            modifier = Modifier.testTag("context_action_sheet_${entry.name}")
        )
        HorizontalDivider()
        DropdownMenuItem(
            text = { Text(if (entry.isDirectory) "Open Folder" else "Open / Inspect") },
            leadingIcon = { Icon(Icons.Default.Folder, contentDescription = null) },
            onClick = {
                onDismiss()
                onOpen()
            }
        )
        if (entry.isDirectory) {
            DropdownMenuItem(
                text = { Text("Open in New Tab") },
                leadingIcon = { Icon(Icons.Default.Folder, contentDescription = null) },
                onClick = {
                    onDismiss()
                    onOpenInNewTab()
                }
            )
        }
        if (entry.isExtractableArchive) {
            HorizontalDivider()
            val folderLabel = deriveArchiveFolderLabel(entry.name)
            DropdownMenuItem(
                text = {
                    Text(
                        text = "Extract Here (${entry.detectedFormat.displayName})",
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                },
                leadingIcon = {
                    Icon(
                        Icons.Default.Unarchive,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                },
                onClick = {
                    onDismiss()
                    onExtractArchiveHere()
                },
                modifier = Modifier.testTag("context_extract_here_${entry.name}")
            )
            DropdownMenuItem(
                text = { Text("Extract to \"$folderLabel/\"") },
                leadingIcon = { Icon(Icons.Default.CreateNewFolder, contentDescription = null) },
                onClick = {
                    onDismiss()
                    onExtractArchiveToFolder()
                },
                modifier = Modifier.testTag("context_extract_subfolder_${entry.name}")
            )
            DropdownMenuItem(
                text = { Text("Browse & Extract Archive…") },
                leadingIcon = { Icon(Icons.Default.Archive, contentDescription = null) },
                onClick = {
                    onDismiss()
                    onExtractArchive()
                },
                modifier = Modifier.testTag("context_browse_archive_${entry.name}")
            )
            DropdownMenuItem(
                text = { Text("Convert Archive Format…") },
                leadingIcon = {
                    Icon(
                        Icons.Default.SwapHoriz,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary
                    )
                },
                onClick = {
                    onDismiss()
                    onConvertArchive()
                },
                modifier = Modifier.testTag("context_convert_archive_${entry.name}")
            )
            HorizontalDivider()
        }
        DropdownMenuItem(
            text = { Text("Copy Full Path") },
            leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null) },
            onClick = {
                onDismiss()
                onCopyPath()
            }
        )
        DropdownMenuItem(
            text = { Text("Copy File Name") },
            leadingIcon = { Icon(Icons.Default.Link, contentDescription = null) },
            onClick = {
                onDismiss()
                onCopyName()
            }
        )
        if (entry.isSafUri) {
            DropdownMenuItem(
                text = { Text("Copy SAF Content URI") },
                leadingIcon = { Icon(Icons.Default.Link, contentDescription = null) },
                onClick = {
                    onDismiss()
                    onCopySafUri()
                }
            )
        }
        DropdownMenuItem(
            text = { Text(if (isFavorite) "Remove from Favorites" else "Add to Favorites") },
            leadingIcon = {
                Icon(
                    if (isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                    contentDescription = null
                )
            },
            onClick = {
                onDismiss()
                onToggleFavorite()
            }
        )
        DropdownMenuItem(
            text = { Text("Rename") },
            leadingIcon = { Icon(Icons.Default.DriveFileRenameOutline, contentDescription = null) },
            onClick = {
                onDismiss()
                onRename()
            }
        )
        if (!entry.isDirectory) {
            DropdownMenuItem(
                text = { Text("Share via Android Sheet") },
                leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) },
                onClick = {
                    onDismiss()
                    onShare()
                }
            )
        }
        DropdownMenuItem(
            text = { Text("Properties & SHA-256") },
            leadingIcon = { Icon(Icons.Default.Info, contentDescription = null) },
            onClick = {
                onDismiss()
                onProperties()
            }
        )
        HorizontalDivider()
        DropdownMenuItem(
            text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
            leadingIcon = {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            onClick = {
                onDismiss()
                onDelete()
            }
        )
    }
}

@Composable
private fun PaneFolderIntelligenceStatusBar(
    stats: FolderIntelligenceResult?,
    selectedCount: Int,
    onRequestDeepStats: () -> Unit
) {
    Surface(
        tonalElevation = 2.dp,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            if (stats?.isCalculatingRecursive == true) {
                MinimalProgressIndicator(
                    indeterminate = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                if (stats != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "Direct: ${stats.directFolderCount} folders, ${stats.directFileCount} files (${stats.directTotalCount} total)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (stats.isComplete) {
                            Text(
                                text = "• Recursive: ${stats.recursiveFolderCount} folders, ${stats.recursiveFileCount} files (${FormatDetectorAndMetadataEngine.formatBytes(stats.recursiveTotalBytes)})",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        } else if (stats.isCalculatingRecursive) {
                            Text(
                                text = "• Scanning subtree… (${stats.recursiveFolderCount} folders, ${stats.recursiveFileCount} files, ${FormatDetectorAndMetadataEngine.formatBytes(stats.recursiveTotalBytes)})",
                                style = MaterialTheme.typography.labelSmall,
                                color = FluentAmber400
                            )
                        } else {
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .clickable(onClick = onRequestDeepStats)
                                    .padding(horizontal = 4.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Calculate,
                                    contentDescription = "Calculate Recursive Size",
                                    modifier = Modifier.size(13.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = "Calc Deep Size",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }

                if (selectedCount > 0) {
                    Text(
                        text = "$selectedCount selected",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}
