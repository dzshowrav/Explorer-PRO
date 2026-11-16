package com.explorerpro.presentation.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CompareArrows
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.NoteAdd
import androidx.compose.material.icons.filled.RestorePage
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.TableRows
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material.icons.filled.VerticalSplit
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import com.explorerpro.core.filesystem.TermuxStorageBridge
import com.explorerpro.domain.model.ExplorerTab
import com.explorerpro.domain.model.FileCategory
import com.explorerpro.domain.model.FileEntry
import com.explorerpro.domain.model.FolderPlacement
import com.explorerpro.domain.model.GroupBy
import com.explorerpro.domain.model.PaneId
import com.explorerpro.domain.model.PaneState
import com.explorerpro.domain.model.SortField
import com.explorerpro.domain.model.SortOrder
import com.explorerpro.domain.model.ViewMode
import com.explorerpro.ui.theme.FluentAmber400
import java.io.File

private enum class TopMenuPage {
    MAIN,
    VIEW_MODE,
    SORT,
    GROUP
}

@Composable
fun ExplorerTopBarAndRibbon(
    activePaneState: PaneState,
    dualPaneEnabled: Boolean,
    hasClipboard: Boolean,
    activeTaskCount: Int,
    isTermuxMounted: Boolean,
    onOpenDrawer: () -> Unit,
    onToggleDualPane: () -> Unit,
    onSwapPanes: () -> Unit,
    onComparePanes: () -> Unit,
    onNewFolderClick: () -> Unit,
    onNewNoteClick: () -> Unit,
    onCopyClick: () -> Unit,
    onCutClick: () -> Unit,
    onPasteClick: () -> Unit,
    onRenameClick: () -> Unit,
    onBatchRenameClick: () -> Unit,
    onCompressClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onSelectAllClick: () -> Unit,
    onClearSelectionClick: () -> Unit,
    onChangeViewMode: (ViewMode) -> Unit,
    onChangeGridColumns: (Int) -> Unit,
    onChangeSort: (SortField, SortOrder, FolderPlacement) -> Unit,
    onChangeGroupBy: (GroupBy) -> Unit,
    onOpenStorageAnalyzer: () -> Unit,
    onOpenOperationCenter: () -> Unit,
    onOpenSettingsClick: () -> Unit = {},
    onTermuxStorageClick: () -> Unit,
    onMountSafClick: () -> Unit,
    onCopyPathClick: () -> Unit = {},
    onExtractSelectedArchiveHere: (FileEntry) -> Unit = {},
    onExtractSelectedArchiveToFolder: (FileEntry) -> Unit = {},
    onConvertSelectedArchive: (FileEntry) -> Unit = {}
) {
    var showOverflowMenu by remember { mutableStateOf(false) }
    var menuPage by remember { mutableStateOf(TopMenuPage.MAIN) }
    val selectedCount = activePaneState.selectedPaths.size
    val singleSelectedArchive = remember(activePaneState.selectedPaths, activePaneState.entries) {
        if (activePaneState.selectedPaths.size == 1) {
            val path = activePaneState.selectedPaths.first()
            activePaneState.entries.find { it.path == path && it.isExtractableArchive }
        } else null
    }

    Surface(
        tonalElevation = 3.dp,
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
        ) {
            // Main Application Bar with Gear Settings Button & 3-Dot Managed Menu
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onOpenDrawer,
                    modifier = Modifier.testTag("nav_drawer_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Menu,
                        contentDescription = "Open navigation sidebar"
                    )
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 4.dp)
                ) {
                    Text(
                        text = "Explorer Pro",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = if (dualPaneEnabled) "DUAL (${activePaneState.paneId.name})" else "SINGLE",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                maxLines = 1,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                            )
                        }
                        Text(
                            text = if (selectedCount > 0) {
                                "$selectedCount selected in ${File(activePaneState.currentPath).name.ifEmpty { "root" }}"
                            } else {
                                "${activePaneState.entries.size} items • ${activePaneState.viewMode.label}"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Dual-Pane Toggle Button
                IconButton(
                    onClick = onToggleDualPane,
                    modifier = Modifier.testTag("toggle_dual_pane_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.VerticalSplit,
                        contentDescription = "Toggle Dual-Pane Explorer",
                        tint = if (dualPaneEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Storage Intelligence & Duplicate Finder
                IconButton(
                    onClick = onOpenStorageAnalyzer,
                    modifier = Modifier.testTag("storage_analyzer_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Analytics,
                        contentDescription = "Storage Analyzer and Duplicate Finder"
                    )
                }

                // Operation Center with active badge
                IconButton(
                    onClick = onOpenOperationCenter,
                    modifier = Modifier.testTag("operation_center_button")
                ) {
                    BadgedBox(
                        badge = {
                            if (activeTaskCount > 0) {
                                Badge { Text(activeTaskCount.toString()) }
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = "Operation Center"
                        )
                    }
                }

                // Gear Icon for Full-Page Settings
                IconButton(
                    onClick = onOpenSettingsClick,
                    modifier = Modifier.testTag("settings_gear_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Open Settings"
                    )
                }

                // 3-Dot Managed Menu containing all Explorer Actions, View Modes, Sort & Group
                Box {
                    IconButton(
                        onClick = {
                            menuPage = TopMenuPage.MAIN
                            showOverflowMenu = true
                        },
                        modifier = Modifier.testTag("top_overflow_menu_button")
                    ) {
                        BadgedBox(
                            badge = {
                                if (selectedCount > 0) {
                                    Badge { Text(selectedCount.toString()) }
                                } else if (hasClipboard) {
                                    Badge { Text("1") }
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "More Explorer Actions",
                                tint = if (selectedCount > 0 || hasClipboard) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                }
                            )
                        }
                    }

                    DropdownMenu(
                        expanded = showOverflowMenu,
                        onDismissRequest = {
                            showOverflowMenu = false
                            menuPage = TopMenuPage.MAIN
                        }
                    ) {
                        when (menuPage) {
                            TopMenuPage.MAIN -> {
                                // 1. Create & Mount Actions
                                DropdownMenuItem(
                                    text = { Text("New Folder") },
                                    leadingIcon = { Icon(Icons.Default.CreateNewFolder, contentDescription = null) },
                                    onClick = {
                                        showOverflowMenu = false
                                        onNewFolderClick()
                                    },
                                    modifier = Modifier.testTag("ribbon_new_folder")
                                )
                                DropdownMenuItem(
                                    text = { Text("New File") },
                                    leadingIcon = { Icon(Icons.Default.NoteAdd, contentDescription = null) },
                                    onClick = {
                                        showOverflowMenu = false
                                        onNewNoteClick()
                                    },
                                    modifier = Modifier.testTag("ribbon_new_file")
                                )
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = if (isTermuxMounted) "Termux (\$HOME)" else "Mount Termux",
                                            fontWeight = if (isTermuxMounted) FontWeight.SemiBold else FontWeight.Normal
                                        )
                                    },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Default.Terminal,
                                            contentDescription = null,
                                            tint = if (isTermuxMounted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    },
                                    onClick = {
                                        showOverflowMenu = false
                                        onTermuxStorageClick()
                                    },
                                    modifier = Modifier.testTag("ribbon_termux_storage")
                                )
                                DropdownMenuItem(
                                    text = { Text("Mount SAF") },
                                    leadingIcon = { Icon(Icons.Default.FolderOpen, contentDescription = null) },
                                    onClick = {
                                        showOverflowMenu = false
                                        onMountSafClick()
                                    },
                                    modifier = Modifier.testTag("ribbon_mount_saf")
                                )

                                HorizontalDivider()

                                // 2. Path, Clipboard & Selection Actions
                                DropdownMenuItem(
                                    text = {
                                        Text(if (selectedCount > 0) "Copy Path ($selectedCount)" else "Copy Path")
                                    },
                                    leadingIcon = { Icon(Icons.Default.Link, contentDescription = null) },
                                    onClick = {
                                        showOverflowMenu = false
                                        onCopyPathClick()
                                    },
                                    modifier = Modifier.testTag("ribbon_copy_path")
                                )

                                if (hasClipboard) {
                                    DropdownMenuItem(
                                        text = {
                                            Text("Paste Here", fontWeight = FontWeight.SemiBold)
                                        },
                                        leadingIcon = {
                                            Icon(
                                                Icons.Default.ContentPaste,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        },
                                        onClick = {
                                            showOverflowMenu = false
                                            onPasteClick()
                                        },
                                        modifier = Modifier.testTag("ribbon_paste")
                                    )
                                }

                                if (singleSelectedArchive != null) {
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                "Extract Here (${singleSelectedArchive.detectedFormat.displayName})",
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
                                            showOverflowMenu = false
                                            onExtractSelectedArchiveHere(singleSelectedArchive)
                                        },
                                        modifier = Modifier.testTag("ribbon_extract_here")
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Extract to Subfolder") },
                                        leadingIcon = { Icon(Icons.Default.CreateNewFolder, contentDescription = null) },
                                        onClick = {
                                            showOverflowMenu = false
                                            onExtractSelectedArchiveToFolder(singleSelectedArchive)
                                        },
                                        modifier = Modifier.testTag("ribbon_extract_subfolder")
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Convert Format… (${singleSelectedArchive.detectedFormat.displayName})") },
                                        leadingIcon = { Icon(Icons.Default.SwapHoriz, contentDescription = null) },
                                        onClick = {
                                            showOverflowMenu = false
                                            onConvertSelectedArchive(singleSelectedArchive)
                                        },
                                        modifier = Modifier.testTag("ribbon_convert_archive")
                                    )
                                }

                                if (selectedCount > 0) {
                                    DropdownMenuItem(
                                        text = { Text("Copy ($selectedCount)") },
                                        leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null) },
                                        onClick = {
                                            showOverflowMenu = false
                                            onCopyClick()
                                        },
                                        modifier = Modifier.testTag("ribbon_copy")
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Move ($selectedCount)") },
                                        leadingIcon = { Icon(Icons.Default.ContentCut, contentDescription = null) },
                                        onClick = {
                                            showOverflowMenu = false
                                            onCutClick()
                                        },
                                        modifier = Modifier.testTag("ribbon_cut")
                                    )
                                    if (selectedCount == 1) {
                                        DropdownMenuItem(
                                            text = { Text("Rename") },
                                            leadingIcon = { Icon(Icons.Default.DriveFileRenameOutline, contentDescription = null) },
                                            onClick = {
                                                showOverflowMenu = false
                                                onRenameClick()
                                            },
                                            modifier = Modifier.testTag("ribbon_rename")
                                        )
                                    }
                                    DropdownMenuItem(
                                        text = { Text("Batch Rename ($selectedCount)") },
                                        leadingIcon = { Icon(Icons.Default.DriveFileRenameOutline, contentDescription = null) },
                                        onClick = {
                                            showOverflowMenu = false
                                            onBatchRenameClick()
                                        },
                                        modifier = Modifier.testTag("ribbon_batch_rename")
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Create Archive (7Z / ZIP / TAR / ZSTD / LZ4)") },
                                        leadingIcon = { Icon(Icons.Default.Archive, contentDescription = null) },
                                        onClick = {
                                            showOverflowMenu = false
                                            onCompressClick()
                                        },
                                        modifier = Modifier.testTag("ribbon_compress_zip")
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Delete ($selectedCount)", color = MaterialTheme.colorScheme.error) },
                                        leadingIcon = {
                                            Icon(
                                                Icons.Default.DeleteOutline,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.error
                                            )
                                        },
                                        onClick = {
                                            showOverflowMenu = false
                                            onDeleteClick()
                                        },
                                        modifier = Modifier.testTag("ribbon_delete")
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Clear Selection") },
                                        leadingIcon = { Icon(Icons.Default.Close, contentDescription = null) },
                                        onClick = {
                                            showOverflowMenu = false
                                            onClearSelectionClick()
                                        },
                                        modifier = Modifier.testTag("ribbon_clear_selection")
                                    )
                                }

                                DropdownMenuItem(
                                    text = { Text("Select All") },
                                    leadingIcon = { Icon(Icons.Default.SelectAll, contentDescription = null) },
                                    onClick = {
                                        showOverflowMenu = false
                                        onSelectAllClick()
                                    },
                                    modifier = Modifier.testTag("ribbon_select_all")
                                )

                                if (dualPaneEnabled) {
                                    HorizontalDivider()
                                    DropdownMenuItem(
                                        text = { Text("Swap Panes") },
                                        leadingIcon = { Icon(Icons.Default.SwapHoriz, contentDescription = null) },
                                        onClick = {
                                            showOverflowMenu = false
                                            onSwapPanes()
                                        },
                                        modifier = Modifier.testTag("ribbon_swap_panes")
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Compare Panes") },
                                        leadingIcon = { Icon(Icons.Default.CompareArrows, contentDescription = null) },
                                        onClick = {
                                            showOverflowMenu = false
                                            onComparePanes()
                                        },
                                        modifier = Modifier.testTag("ribbon_compare_panes")
                                    )
                                }

                                HorizontalDivider()

                                // 3. View Mode, Sort & Group Submenus
                                DropdownMenuItem(
                                    text = {
                                        val colSuffix = if (activePaneState.viewMode == ViewMode.GRID) {
                                            " (${activePaneState.gridColumns}×)"
                                        } else ""
                                        Text("View Mode: ${activePaneState.viewMode.label}$colSuffix")
                                    },
                                    leadingIcon = {
                                        val modeIcon = when (activePaneState.viewMode) {
                                            ViewMode.GRID -> Icons.Default.GridView
                                            ViewMode.SIMPLE_LIST -> Icons.AutoMirrored.Filled.ViewList
                                            ViewMode.DETAILED_LIST -> Icons.Default.TableRows
                                        }
                                        Icon(modeIcon, contentDescription = null)
                                    },
                                    trailingIcon = { Icon(Icons.Default.ChevronRight, contentDescription = null) },
                                    onClick = { menuPage = TopMenuPage.VIEW_MODE },
                                    modifier = Modifier.testTag("ribbon_view_mode_button")
                                )

                                DropdownMenuItem(
                                    text = {
                                        Text("Sort: ${activePaneState.sortConfig.field.label}")
                                    },
                                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = null) },
                                    trailingIcon = { Icon(Icons.Default.ChevronRight, contentDescription = null) },
                                    onClick = { menuPage = TopMenuPage.SORT },
                                    modifier = Modifier.testTag("ribbon_sort_button")
                                )

                                DropdownMenuItem(
                                    text = {
                                        Text("Group: ${activePaneState.groupBy.label}")
                                    },
                                    leadingIcon = { Icon(Icons.Default.FilterList, contentDescription = null) },
                                    trailingIcon = { Icon(Icons.Default.ChevronRight, contentDescription = null) },
                                    onClick = { menuPage = TopMenuPage.GROUP },
                                    modifier = Modifier.testTag("ribbon_group_button")
                                )
                            }

                            TopMenuPage.VIEW_MODE -> {
                                DropdownMenuItem(
                                    text = { Text("Back to Actions", fontWeight = FontWeight.SemiBold) },
                                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null) },
                                    onClick = { menuPage = TopMenuPage.MAIN }
                                )
                                HorizontalDivider()
                                val modes = listOf(
                                    Triple(ViewMode.GRID, Icons.Default.GridView, "view_mode_grid"),
                                    Triple(ViewMode.SIMPLE_LIST, Icons.AutoMirrored.Filled.ViewList, "view_mode_simple_list"),
                                    Triple(ViewMode.DETAILED_LIST, Icons.Default.TableRows, "view_mode_detailed_list")
                                )
                                modes.forEach { (mode, icon, tag) ->
                                    val isSelected = activePaneState.viewMode == mode
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text = if (isSelected) "✓ ${mode.label}" else mode.label,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                            )
                                        },
                                        leadingIcon = { Icon(icon, contentDescription = null) },
                                        onClick = {
                                            onChangeViewMode(mode)
                                            showOverflowMenu = false
                                            menuPage = TopMenuPage.MAIN
                                        },
                                        modifier = Modifier.testTag(tag)
                                    )
                                }
                                if (activePaneState.viewMode == ViewMode.GRID) {
                                    HorizontalDivider()
                                    DropdownMenuItem(
                                        text = {
                                            Text("Grid Columns: ${activePaneState.gridColumns}× (Tap to change)")
                                        },
                                        leadingIcon = { Icon(Icons.Default.GridView, contentDescription = null) },
                                        onClick = {
                                            val next = if (activePaneState.gridColumns >= 5) 2 else activePaneState.gridColumns + 1
                                            onChangeGridColumns(next)
                                        }
                                    )
                                }
                            }

                            TopMenuPage.SORT -> {
                                DropdownMenuItem(
                                    text = { Text("Back to Actions", fontWeight = FontWeight.SemiBold) },
                                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null) },
                                    onClick = { menuPage = TopMenuPage.MAIN }
                                )
                                HorizontalDivider()
                                SortField.entries.forEach { field ->
                                    val isCurrent = activePaneState.sortConfig.field == field
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text = if (isCurrent) {
                                                    "✓ ${field.label} (${activePaneState.sortConfig.order.label})"
                                                } else field.label,
                                                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal
                                            )
                                        },
                                        onClick = {
                                            val nextOrder = if (isCurrent && activePaneState.sortConfig.order == SortOrder.ASCENDING) {
                                                SortOrder.DESCENDING
                                            } else {
                                                SortOrder.ASCENDING
                                            }
                                            onChangeSort(field, nextOrder, activePaneState.sortConfig.folderPlacement)
                                            showOverflowMenu = false
                                            menuPage = TopMenuPage.MAIN
                                        }
                                    )
                                }
                                HorizontalDivider()
                                FolderPlacement.entries.forEach { placement ->
                                    val isSelected = activePaneState.sortConfig.folderPlacement == placement
                                    DropdownMenuItem(
                                        text = {
                                            Text(if (isSelected) "● ${placement.label}" else placement.label)
                                        },
                                        onClick = {
                                            onChangeSort(
                                                activePaneState.sortConfig.field,
                                                activePaneState.sortConfig.order,
                                                placement
                                            )
                                            showOverflowMenu = false
                                            menuPage = TopMenuPage.MAIN
                                        }
                                    )
                                }
                            }

                            TopMenuPage.GROUP -> {
                                DropdownMenuItem(
                                    text = { Text("Back to Actions", fontWeight = FontWeight.SemiBold) },
                                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null) },
                                    onClick = { menuPage = TopMenuPage.MAIN }
                                )
                                HorizontalDivider()
                                GroupBy.entries.forEach { group ->
                                    val isSelected = activePaneState.groupBy == group
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text = if (isSelected) "✓ ${group.label}" else group.label,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                            )
                                        },
                                        onClick = {
                                            onChangeGroupBy(group)
                                            showOverflowMenu = false
                                            menuPage = TopMenuPage.MAIN
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
        }
    }
}

@Composable
fun PaneTabsAndAddressBar(
    paneState: PaneState,
    isActivePane: Boolean,
    workspaceRootPath: String,
    onSelectTab: (String) -> Unit,
    onCloseTab: (String) -> Unit,
    onNewTab: () -> Unit,
    onDuplicateTab: () -> Unit,
    onReopenClosedTab: () -> Unit,
    onNavigateBack: () -> Unit,
    onNavigateForward: () -> Unit,
    onNavigateToPath: (String) -> Unit,
    onCopyPath: (String) -> Unit,
    onSearchQueryChange: (String, FileCategory?) -> Unit
) {
    var showSearchField by remember { mutableStateOf(false) }
    var isEditingPath by remember { mutableStateOf(false) }
    val resolvedDisplayPath = remember(paneState.currentPath) {
        TermuxStorageBridge.resolveRealPosixPathIfPossible(paneState.currentPath)
    }
    var rawPathInput by remember(paneState.currentPath, isEditingPath) {
        mutableStateOf(resolvedDisplayPath)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (isActivePane) MaterialTheme.colorScheme.surface
                else MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
            )
    ) {
        // 1. Tab Manager Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            paneState.tabs.forEach { tab ->
                val isSelected = tab.id == paneState.activeTabId
                TabPillItem(
                    tab = tab,
                    isSelected = isSelected,
                    canClose = paneState.tabs.size > 1,
                    onSelect = { onSelectTab(tab.id) },
                    onClose = { onCloseTab(tab.id) }
                )
            }

            IconButton(
                onClick = onNewTab,
                modifier = Modifier
                    .size(32.dp)
                    .testTag("new_tab_button_${paneState.paneId.name}")
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "New Tab",
                    modifier = Modifier.size(18.dp)
                )
            }

            IconButton(
                onClick = onDuplicateTab,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.ContentCopy,
                    contentDescription = "Duplicate Tab",
                    modifier = Modifier.size(15.dp)
                )
            }

            if (paneState.closedTabs.isNotEmpty()) {
                IconButton(
                    onClick = onReopenClosedTab,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.RestorePage,
                        contentDescription = "Reopen Closed Tab",
                        modifier = Modifier.size(17.dp),
                        tint = FluentAmber400
                    )
                }
            }
        }

        // 2. Navigation History + Interactive Breadcrumb Address Bar + Copy Path + Search Toggle
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val activeTab = paneState.tabs.find { it.id == paneState.activeTabId }
            IconButton(
                onClick = onNavigateBack,
                modifier = Modifier.size(34.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    modifier = Modifier.size(18.dp)
                )
            }

            IconButton(
                onClick = onNavigateForward,
                enabled = activeTab?.forwardStack?.isNotEmpty() == true,
                modifier = Modifier.size(34.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = "Forward",
                    modifier = Modifier.size(18.dp)
                )
            }

            // Interactive Breadcrumb Bar (tap segment to navigate, long-press segment to copy path)
            BreadcrumbBar(
                currentPath = paneState.currentPath,
                workspaceRootPath = workspaceRootPath,
                onSegmentClick = onNavigateToPath,
                onSegmentLongClick = onCopyPath,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 4.dp)
            )

            // 1-Tap Copy Current Directory Path to System Clipboard
            IconButton(
                onClick = { onCopyPath(paneState.currentPath) },
                modifier = Modifier
                    .size(34.dp)
                    .testTag("copy_current_path_${paneState.paneId.name}")
            ) {
                Icon(
                    imageVector = Icons.Default.ContentCopy,
                    contentDescription = "Copy Current Path",
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            // Toggle Raw Path Editor / Go-To Path Bar
            IconButton(
                onClick = { isEditingPath = !isEditingPath },
                modifier = Modifier
                    .size(34.dp)
                    .testTag("edit_path_toggle_${paneState.paneId.name}")
            ) {
                Icon(
                    imageVector = if (isEditingPath) Icons.Default.Close else Icons.Default.Edit,
                    contentDescription = "Edit or Go to Path",
                    modifier = Modifier.size(16.dp),
                    tint = if (isEditingPath) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            IconButton(
                onClick = {
                    showSearchField = !showSearchField
                    if (!showSearchField && paneState.searchQuery.isNotEmpty()) {
                        onSearchQueryChange("", null)
                    }
                },
                modifier = Modifier
                    .size(34.dp)
                    .testTag("search_toggle_${paneState.paneId.name}")
            ) {
                Icon(
                    imageVector = if (showSearchField) Icons.Default.Close else Icons.Default.Search,
                    contentDescription = "Toggle Search",
                    modifier = Modifier.size(18.dp),
                    tint = if (showSearchField || paneState.searchQuery.isNotEmpty()) {
                        MaterialTheme.colorScheme.primary
                    } else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // 2b. Raw Path Editor & Copy/Go Bar
        if (isEditingPath) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                OutlinedTextField(
                    value = rawPathInput,
                    onValueChange = { rawPathInput = it },
                    label = { Text("Full Path (Copy or Go To)", style = MaterialTheme.typography.labelSmall) },
                    singleLine = true,
                    trailingIcon = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = { onCopyPath(rawPathInput.trim()) },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = "Copy Path Text",
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                            IconButton(
                                onClick = {
                                    val target = rawPathInput.trim()
                                    if (target.isNotEmpty()) {
                                        onNavigateToPath(target)
                                        isEditingPath = false
                                    }
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Go to Path",
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("raw_path_input_${paneState.paneId.name}")
                )
            }
        }

        // 3. Progressive Search & Category Filter Bar
        if (showSearchField || paneState.searchQuery.isNotEmpty() || paneState.searchCategoryFilter != null) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                OutlinedTextField(
                    value = paneState.searchQuery,
                    onValueChange = { onSearchQueryChange(it, paneState.searchCategoryFilter) },
                    placeholder = {
                        Text(
                            text = "Search in ${File(paneState.currentPath).name.ifEmpty { "folder" }}…",
                            style = MaterialTheme.typography.bodySmall
                        )
                    },
                    singleLine = true,
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    trailingIcon = {
                        if (paneState.searchQuery.isNotEmpty()) {
                            IconButton(onClick = { onSearchQueryChange("", paneState.searchCategoryFilter) }) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Clear search",
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("search_input_${paneState.paneId.name}")
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val categories = listOf(
                        null to "All Types",
                        FileCategory.DOCUMENT to "Documents",
                        FileCategory.IMAGE to "Images",
                        FileCategory.ARCHIVE to "Archives",
                        FileCategory.CODE to "Code",
                        FileCategory.FOLDER to "Folders"
                    )
                    categories.forEach { (cat, label) ->
                        val selected = paneState.searchCategoryFilter == cat
                        FilterChip(
                            selected = selected,
                            onClick = {
                                val nextCat = if (selected) null else cat
                                onSearchQueryChange(paneState.searchQuery, nextCat)
                            },
                            label = { Text(label, style = MaterialTheme.typography.bodySmall) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TabPillItem(
    tab: ExplorerTab,
    isSelected: Boolean,
    canClose: Boolean,
    onSelect: () -> Unit,
    onClose: () -> Unit
) {
    val bgColor = if (isSelected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
    }
    val borderColor = if (isSelected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
    }

    Row(
        modifier = Modifier
            .height(32.dp)
            .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 4.dp, bottomEnd = 4.dp))
            .background(bgColor)
            .border(
                width = 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 4.dp, bottomEnd = 4.dp)
            )
            .clickable(onClick = onSelect)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(
            imageVector = Icons.Default.FolderOpen,
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = tab.title,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (canClose) {
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onClose),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close tab ${tab.title}",
                    modifier = Modifier.size(12.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BreadcrumbBar(
    currentPath: String,
    workspaceRootPath: String,
    onSegmentClick: (String) -> Unit,
    onSegmentLongClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val segments = remember(currentPath, workspaceRootPath) {
        if (currentPath.startsWith("content://")) {
            TermuxStorageBridge.parseSafBreadcrumbs(currentPath)
        } else if (currentPath.startsWith(TermuxStorageBridge.TERMUX_HOME_PATH)) {
            val rel = currentPath.removePrefix(TermuxStorageBridge.TERMUX_HOME_PATH).trim('/')
            val list = mutableListOf("Termux (\$HOME)" to TermuxStorageBridge.TERMUX_HOME_PATH)
            if (rel.isNotEmpty()) {
                var accum = TermuxStorageBridge.TERMUX_HOME_PATH
                rel.split('/').forEach { part ->
                    accum = "$accum/$part"
                    list.add(part to accum)
                }
            }
            list
        } else if (currentPath.startsWith(workspaceRootPath)) {
            val rel = currentPath.removePrefix(workspaceRootPath).trim('/')
            val rootLabel = if (workspaceRootPath.startsWith("/storage/")) "Internal Storage" else "Storage"
            val list = mutableListOf(rootLabel to workspaceRootPath)
            if (rel.isNotEmpty()) {
                var accum = workspaceRootPath
                rel.split('/').forEach { part ->
                    accum = "$accum/$part"
                    list.add(part to accum)
                }
            }
            list
        } else if (currentPath == "/system" || currentPath.startsWith("/system/")) {
            val rel = currentPath.removePrefix("/system").trim('/')
            val list = mutableListOf("System (/system)" to "/system")
            if (rel.isNotEmpty()) {
                var accum = "/system"
                rel.split('/').forEach { part ->
                    accum = "$accum/$part"
                    list.add(part to accum)
                }
            }
            list
        } else {
            val parts = currentPath.trim('/').split('/').filter { it.isNotEmpty() }
            var accum = ""
            parts.map { part ->
                accum = "$accum/$part"
                part to accum
            }.ifEmpty { listOf("Root" to "/") }
        }
    }

    Row(
        modifier = modifier
            .height(32.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        segments.forEachIndexed { index, (label, fullPath) ->
            val isLast = index == segments.lastIndex
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (isLast) FontWeight.Bold else FontWeight.Normal,
                color = if (isLast) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .combinedClickable(
                        onClick = { onSegmentClick(fullPath) },
                        onLongClick = { onSegmentLongClick(fullPath) }
                    )
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            )
            if (!isLast) {
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }
        }
    }
}
