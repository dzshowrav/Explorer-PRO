package com.explorerpro.core.designsystem.dialogs

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.TableRows
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.explorerpro.core.filesystem.FormatDetectorAndMetadataEngine
import com.explorerpro.core.filesystem.TermuxStorageBridge
import com.explorerpro.domain.model.ExplorerInputValidator
import com.explorerpro.domain.model.FileConflictDecision
import com.explorerpro.domain.model.FileEntry
import com.explorerpro.domain.model.FolderPlacement
import com.explorerpro.domain.model.GroupBy
import com.explorerpro.domain.model.PendingTransferConflictState
import com.explorerpro.domain.model.SortField
import com.explorerpro.domain.model.SortOrder
import com.explorerpro.domain.model.ViewMode
import com.explorerpro.ui.theme.FluentAmber400
import com.explorerpro.ui.theme.FluentEmerald400
import com.explorerpro.ui.theme.FluentRose400

/**
 * 10. EXPLORER CONFLICT DIALOG
 * Specialized File & Folder Conflict Resolution Dialog when copying or moving into a destination
 * containing items with identical names.
 *
 * Supports:
 * - Replace
 * - Skip
 * - Keep Both (Auto-Rename)
 * - Custom Rename (with live validation)
 * - Compare Files (expandable side-by-side comparison of size, modified time, format, permissions)
 * - Inspect Properties of Source or Destination
 * - Apply to All remaining conflicts in the batch
 */
@Composable
fun ExplorerConflictDialog(
    conflictState: PendingTransferConflictState,
    existingDestinationNames: Set<String>,
    onResolveConflict: (
        decision: FileConflictDecision,
        customNewName: String?,
        applyToAll: Boolean
    ) -> Unit,
    onInspectEntryProperties: (FileEntry) -> Unit,
    onCancelTransfer: () -> Unit,
    modifier: Modifier = Modifier
) {
    val activePair = conflictState.activeConflict ?: return
    val source = activePair.sourceEntry
    val dest = activePair.destinationEntry
    val remainingCount = conflictState.remainingConflictsCount

    var selectedDecision by rememberSaveable(source.path) {
        mutableStateOf(FileConflictDecision.KEEP_BOTH)
    }
    var applyToAll by rememberSaveable(source.path) { mutableStateOf(false) }
    var showComparisonTable by rememberSaveable(source.path) { mutableStateOf(false) }

    val suggestedRename = remember(source.name, existingDestinationNames) {
        val dotIdx = source.name.lastIndexOf('.')
        val base = if (!source.isDirectory && dotIdx > 0) source.name.substring(0, dotIdx) else source.name
        val ext = if (!source.isDirectory && dotIdx > 0) source.name.substring(dotIdx) else ""
        var counter = 1
        var candidate = "$base ($counter)$ext"
        while (existingDestinationNames.contains(candidate)) {
            counter++
            candidate = "$base ($counter)$ext"
        }
        candidate
    }

    var customName by rememberSaveable(source.path) { mutableStateOf(suggestedRename) }
    val customValidation = remember(customName, existingDestinationNames) {
        ExplorerInputValidator.validateFileOrFolderName(
            rawInput = customName,
            existingSiblingNames = existingDestinationNames
        )
    }

    val guard = rememberDialogSubmitGuard()

    val sizeDeltaSummary = remember(source.sizeBytes, dest.sizeBytes, source.lastModified, dest.lastModified) {
        val sizeComparison = when {
            source.sizeBytes > dest.sizeBytes -> "Source is larger (+${FormatDetectorAndMetadataEngine.formatBytes(source.sizeBytes - dest.sizeBytes)})"
            source.sizeBytes < dest.sizeBytes -> "Destination is larger (+${FormatDetectorAndMetadataEngine.formatBytes(dest.sizeBytes - source.sizeBytes)})"
            else -> "Identical file size (${FormatDetectorAndMetadataEngine.formatBytes(source.sizeBytes)})"
        }
        val timeComparison = when {
            source.lastModified > dest.lastModified -> "Source is newer"
            source.lastModified < dest.lastModified -> "Destination is newer"
            else -> "Same timestamp"
        }
        "$timeComparison • $sizeComparison"
    }

    AlertDialog(
        onDismissRequest = onCancelTransfer,
        shape = RoundedCornerShape(24.dp),
        icon = {
            Surface(
                color = FluentAmber400.copy(alpha = 0.16f),
                shape = CircleShape,
                modifier = Modifier.size(48.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "File conflict",
                        tint = FluentAmber400,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }
        },
        title = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = if (source.isDirectory) "Folder Name Conflict" else "File Name Conflict",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.semantics { heading() }
                )
                Text(
                    text = "Conflict ${conflictState.currentConflictIndex + 1} of ${conflictState.conflicts.size} • ${if (conflictState.isMove) "Move" else "Copy"} Operation",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 430.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "An item named '${source.name}' already exists in '${conflictState.destinationDirPath.substringAfterLast('/').ifEmpty { conflictState.destinationDirPath }}'.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Source vs Destination Summary Cards
                ConflictEndpointCard(
                    badgeLabel = "SOURCE (${if (conflictState.isMove) "MOVING" else "COPYING"})",
                    entry = source,
                    isNewer = source.lastModified > dest.lastModified,
                    onInspectProperties = { onInspectEntryProperties(source) }
                )

                ConflictEndpointCard(
                    badgeLabel = "EXISTING IN DESTINATION",
                    entry = dest,
                    isNewer = dest.lastModified > source.lastModified,
                    onInspectProperties = { onInspectEntryProperties(dest) }
                )

                // Quick Comparison Banner + Expandable Compare Files View
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = sizeDeltaSummary,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(
                                onClick = { showComparisonTable = !showComparisonTable },
                                modifier = Modifier.testTag("conflict_compare_files_button")
                            ) {
                                Icon(Icons.AutoMirrored.Filled.CompareArrows, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(if (showComparisonTable) "Hide Compare" else "Compare Files")
                            }
                        }

                        AnimatedVisibility(visible = showComparisonTable) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 6.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                HorizontalDivider()
                                CompareAttributeRow(
                                    label = "Size",
                                    sourceVal = FormatDetectorAndMetadataEngine.formatBytes(source.sizeBytes),
                                    destVal = FormatDetectorAndMetadataEngine.formatBytes(dest.sizeBytes)
                                )
                                CompareAttributeRow(
                                    label = "Modified",
                                    sourceVal = FormatDetectorAndMetadataEngine.formatTimestamp(source.lastModified),
                                    destVal = FormatDetectorAndMetadataEngine.formatTimestamp(dest.lastModified)
                                )
                                CompareAttributeRow(
                                    label = "Format",
                                    sourceVal = source.detectedFormat.displayName,
                                    destVal = dest.detectedFormat.displayName
                                )
                                CompareAttributeRow(
                                    label = "Permissions",
                                    sourceVal = source.permissions,
                                    destVal = dest.permissions
                                )
                            }
                        }
                    }
                }

                HorizontalDivider()

                Text(
                    text = "Choose Resolution Action:",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )

                val decisions = listOf(
                    Triple(
                        FileConflictDecision.KEEP_BOTH,
                        "Keep Both (Auto-Rename)",
                        "Save incoming item as '$suggestedRename' without overwriting"
                    ),
                    Triple(
                        FileConflictDecision.REPLACE,
                        "Replace Existing File",
                        "Overwrite destination item with source item"
                    ),
                    Triple(
                        FileConflictDecision.SKIP,
                        "Skip This File",
                        "Leave existing destination item untouched"
                    ),
                    Triple(
                        FileConflictDecision.CUSTOM_RENAME,
                        "Rename Incoming Item",
                        "Specify a custom target name in destination"
                    )
                )

                decisions.forEach { (decision, title, subtitle) ->
                    val isSelected = selectedDecision == decision
                    val isReplace = decision == FileConflictDecision.REPLACE
                    Surface(
                        color = when {
                            isSelected && isReplace -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.55f)
                            isSelected -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                            else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { selectedDecision = decision }
                            .testTag("conflict_option_${decision.name}")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = { selectedDecision = decision }
                            )
                            Spacer(Modifier.width(6.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                    color = if (isReplace && isSelected) {
                                        MaterialTheme.colorScheme.error
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    }
                                )
                                Text(
                                    text = subtitle,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                // Custom Rename Input Field when CUSTOM_RENAME is selected
                AnimatedVisibility(visible = selectedDecision == FileConflictDecision.CUSTOM_RENAME) {
                    OutlinedTextField(
                        value = customName,
                        onValueChange = { customName = it },
                        label = { Text("Custom Destination Name") },
                        singleLine = true,
                        isError = !customValidation.isValid,
                        supportingText = {
                            Text(
                                text = customValidation.errorMessage
                                    ?: "Valid non-conflicting destination name",
                                color = if (!customValidation.isValid) {
                                    MaterialTheme.colorScheme.error
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("conflict_custom_rename_field")
                    )
                }

                // Apply to All Remaining Conflicts Checkbox
                if (remainingCount > 0 && selectedDecision != FileConflictDecision.CUSTOM_RENAME) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { applyToAll = !applyToAll }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = applyToAll,
                            onCheckedChange = { applyToAll = it },
                            modifier = Modifier.testTag("conflict_apply_to_all_checkbox")
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "Apply '${selectedDecision.label}' to all $remainingCount remaining conflict(s)",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        },
        confirmButton = {
            val isConfirmEnabled = selectedDecision != FileConflictDecision.CUSTOM_RENAME || customValidation.isValid
            Button(
                onClick = {
                    guard {
                        onResolveConflict(
                            selectedDecision,
                            if (selectedDecision == FileConflictDecision.CUSTOM_RENAME) {
                                customValidation.sanitizedValue
                            } else null,
                            applyToAll && selectedDecision != FileConflictDecision.CUSTOM_RENAME
                        )
                    }
                },
                enabled = isConfirmEnabled,
                colors = if (selectedDecision == FileConflictDecision.REPLACE) {
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                } else {
                    ButtonDefaults.buttonColors()
                },
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("conflict_confirm_button")
            ) {
                Text(
                    when (selectedDecision) {
                        FileConflictDecision.REPLACE -> "Replace"
                        FileConflictDecision.SKIP -> "Skip"
                        FileConflictDecision.KEEP_BOTH -> "Keep Both"
                        FileConflictDecision.CUSTOM_RENAME -> "Save as Renamed"
                        FileConflictDecision.CANCEL -> "Cancel"
                    }
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = onCancelTransfer,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("conflict_cancel_button")
            ) {
                Text("Cancel Transfer")
            }
        },
        modifier = modifier.testTag("explorer_conflict_dialog")
    )
}

@Composable
private fun ConflictEndpointCard(
    badgeLabel: String,
    entry: FileEntry,
    isNewer: Boolean,
    onInspectProperties: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = badgeLabel,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    if (isNewer) {
                        Surface(
                            color = FluentEmerald400.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = "NEWER",
                                style = MaterialTheme.typography.labelSmall,
                                color = FluentEmerald400,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                            )
                        }
                    }
                }
                IconButton(
                    onClick = onInspectProperties,
                    modifier = Modifier.size(26.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "Inspect $badgeLabel properties",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Text(
                text = entry.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${FormatDetectorAndMetadataEngine.formatBytes(entry.sizeBytes)} • Modified ${FormatDetectorAndMetadataEngine.formatTimestamp(entry.lastModified)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = entry.path,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun CompareAttributeRow(
    label: String,
    sourceVal: String,
    destVal: String
) {
    val differs = sourceVal != destVal
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(72.dp)
        )
        Text(
            text = sourceVal,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (differs) FontWeight.SemiBold else FontWeight.Normal,
            color = if (differs) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = "vs",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
        Text(
            text = destVal,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}

/**
 * 11. EXPLORER PROPERTIES DIALOG
 * Reusable File & Folder Properties Inspector with Magic-Byte Format Signature, POSIX & SAF URIs,
 * 1-tap Clipboard Copy, and Live SHA-256 Digest Verifier.
 */
@Composable
fun ExplorerPropertiesDialog(
    entry: FileEntry,
    sha256Checksum: String?,
    onCopyText: (String, String) -> Unit = { _, _ -> },
    onShare: ((FileEntry) -> Unit)? = null,
    onToggleFavorite: ((FileEntry) -> Unit)? = null,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val resolvedPosixPath = remember(entry.path) {
        TermuxStorageBridge.resolveRealPosixPathIfPossible(entry.path)
    }
    var expectedHashInput by rememberSaveable(entry.path) { mutableStateOf("") }
    var showHashVerifier by rememberSaveable(entry.path) { mutableStateOf(false) }

    val hashMatches = remember(sha256Checksum, expectedHashInput) {
        val cleanExpected = expectedHashInput.trim().lowercase()
        val cleanActual = sha256Checksum?.trim()?.lowercase().orEmpty()
        if (cleanExpected.isEmpty() || cleanActual.length != 64) null
        else cleanExpected == cleanActual
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(24.dp),
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = if (entry.isDirectory) Icons.Default.Folder else Icons.AutoMirrored.Filled.InsertDriveFile,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = entry.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.semantics { heading() }
                    )
                }
                IconButton(
                    onClick = { onCopyText(entry.name, "File Name") },
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "Copy file name",
                        modifier = Modifier.size(17.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                InspectorPropertyRow("Detected Format", entry.detectedFormat.displayName)
                InspectorPropertyRow("Magic Signature", entry.detectedFormat.magicSignatureHex)
                InspectorPropertyRow("MIME Type", entry.mimeType)
                InspectorPropertyRow("Extension", entry.extension.ifEmpty { "None" })
                InspectorPropertyRow(
                    "Size",
                    if (entry.isDirectory) "${entry.directChildCount ?: 0} direct items"
                    else "${FormatDetectorAndMetadataEngine.formatBytes(entry.sizeBytes)} (${entry.sizeBytes} bytes)"
                )
                InspectorPropertyRow("Modified Date", FormatDetectorAndMetadataEngine.formatTimestamp(entry.lastModified))
                InspectorPropertyRow(
                    "Created Date",
                    entry.createdDate?.let { FormatDetectorAndMetadataEngine.formatTimestamp(it) } ?: "Unavailable"
                )
                InspectorPropertyRow("Permissions", entry.permissions)
                InspectorPropertyRow(
                    label = "Full Path",
                    value = resolvedPosixPath,
                    onCopy = { onCopyText(resolvedPosixPath, "Full Path") }
                )
                if (entry.isSafUri && entry.path != resolvedPosixPath) {
                    InspectorPropertyRow(
                        label = "SAF Content URI",
                        value = entry.path,
                        onCopy = { onCopyText(entry.path, "SAF URI") }
                    )
                }
                entry.dimensionsText?.let { InspectorPropertyRow("Image Dimensions", "$it px") }
                entry.pageCount?.let { InspectorPropertyRow("PDF Page Count", "$it pages") }
                entry.apkPackageName?.let { InspectorPropertyRow("APK Package", "$it (${entry.apkVersionName ?: "N/A"})") }

                if (!entry.isDirectory) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    InspectorPropertyRow(
                        label = "SHA-256 Digest",
                        value = sha256Checksum ?: "Calculating real SHA-256 digest…",
                        onCopy = sha256Checksum?.takeIf { it.length == 64 }?.let { hash ->
                            { onCopyText(hash, "SHA-256 Checksum") }
                        }
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = { showHashVerifier = !showHashVerifier }) {
                            Icon(Icons.Default.VerifiedUser, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(if (showHashVerifier) "Hide Checksum Verifier" else "Verify Expected SHA-256")
                        }
                    }

                    AnimatedVisibility(visible = showHashVerifier) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            OutlinedTextField(
                                value = expectedHashInput,
                                onValueChange = { expectedHashInput = it },
                                label = { Text("Paste expected SHA-256 hash") },
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("properties_hash_verifier_input")
                            )
                            if (hashMatches != null) {
                                Surface(
                                    color = if (hashMatches) {
                                        FluentEmerald400.copy(alpha = 0.18f)
                                    } else {
                                        FluentRose400.copy(alpha = 0.18f)
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = if (hashMatches) {
                                            "✓ SHA-256 Checksum Matches Exactly"
                                        } else {
                                            "✗ Checksum Mismatch — File differs from expected digest"
                                        },
                                        style = MaterialTheme.typography.labelMedium,
                                        color = if (hashMatches) FluentEmerald400 else FluentRose400,
                                        modifier = Modifier.padding(8.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (!entry.isDirectory && onShare != null) {
                    OutlinedButton(
                        onClick = {
                            onDismiss()
                            onShare(entry)
                        },
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Share")
                    }
                }
                Button(
                    onClick = { onCopyText(resolvedPosixPath, "Full Path") },
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("properties_copy_path_button")
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Copy Path")
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.minimumInteractiveComponentSize()
            ) {
                Text("Close")
            }
        },
        modifier = modifier.testTag("explorer_properties_dialog")
    )
}

@Composable
private fun InspectorPropertyRow(
    label: String,
    value: String,
    onCopy: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
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
        if (onCopy != null) {
            IconButton(
                onClick = onCopy,
                modifier = Modifier.size(30.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.ContentCopy,
                    contentDescription = "Copy $label",
                    modifier = Modifier.size(15.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

/**
 * CONTEXTUAL FILE / FOLDER ACTION BOTTOM SHEET
 * Provides a rich Material 3 ModalBottomSheet with file metadata summary chips and categorized actions.
 */
@Composable
fun ExplorerFileActionBottomSheet(
    entry: FileEntry,
    isFavorite: Boolean,
    dualPaneEnabled: Boolean,
    onOpen: () -> Unit,
    onOpenInNewTab: () -> Unit,
    onExtractHere: () -> Unit,
    onExtractToSubfolder: () -> Unit,
    onInspectArchive: () -> Unit,
    onConvertArchive: () -> Unit = {},
    onCopy: () -> Unit,
    onMove: () -> Unit,
    onCopyToOtherPane: () -> Unit,
    onMoveToOtherPane: () -> Unit,
    onRename: () -> Unit,
    onCompressToArchive: () -> Unit,
    onCopyFullPath: () -> Unit,
    onCopyFileName: () -> Unit,
    onToggleFavorite: () -> Unit,
    onSetCustomIcon: () -> Unit = {},
    onShare: () -> Unit,
    onProperties: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    val badges = buildList {
        add(entry.detectedFormat.displayName)
        if (!entry.isDirectory) {
            add(FormatDetectorAndMetadataEngine.formatBytes(entry.sizeBytes))
        } else {
            add("${entry.directChildCount ?: 0} items")
        }
        add(entry.permissions)
    }

    val actions = buildList {
        add(
            BottomSheetActionItem(
                id = "open",
                title = if (entry.isDirectory) "Open Folder" else "Open / Preview",
                subtitle = entry.path,
                icon = if (entry.isDirectory) Icons.Default.FolderOpen else Icons.AutoMirrored.Filled.InsertDriveFile,
                onClick = onOpen
            )
        )
        if (entry.isDirectory) {
            add(
                BottomSheetActionItem(
                    id = "open_new_tab",
                    title = "Open in New Tab",
                    subtitle = "Create a dedicated tab for '${entry.name}'",
                    icon = Icons.Default.CreateNewFolder,
                    onClick = onOpenInNewTab
                )
            )
        }
        if (entry.isExtractableArchive) {
            add(
                BottomSheetActionItem(
                    id = "extract_here",
                    title = "Extract Here",
                    subtitle = "Unpack ${entry.detectedFormat.displayName} into current folder",
                    icon = Icons.Default.Unarchive,
                    badgeText = entry.extension.uppercase(),
                    onClick = onExtractHere
                )
            )
            add(
                BottomSheetActionItem(
                    id = "extract_subfolder",
                    title = "Extract to Subfolder",
                    subtitle = "Unpack into '${entry.name.substringBeforeLast('.')}/'",
                    icon = Icons.Default.CreateNewFolder,
                    onClick = onExtractToSubfolder
                )
            )
            add(
                BottomSheetActionItem(
                    id = "inspect_archive",
                    title = "Browse & Verify Archive",
                    subtitle = "Inspect entries, verify CRC/SHA integrity, or enter password",
                    icon = Icons.Default.Archive,
                    onClick = onInspectArchive
                )
            )
            add(
                BottomSheetActionItem(
                    id = "convert_archive",
                    title = "Convert Archive Format",
                    subtitle = "Repack into 7z, ZIP, TAR, LZ4, ZSTD, GZ, XZ, JAR, CPIO",
                    icon = Icons.Default.SwapHoriz,
                    onClick = onConvertArchive
                )
            )
        }
        add(
            BottomSheetActionItem(
                id = "copy",
                title = "Copy to Clipboard",
                subtitle = "Stage '${entry.name}' for pasting",
                icon = Icons.Default.ContentCopy,
                onClick = onCopy
            )
        )
        add(
            BottomSheetActionItem(
                id = "move",
                title = "Cut / Move",
                subtitle = "Move '${entry.name}' to another folder",
                icon = Icons.Default.ContentCut,
                onClick = onMove
            )
        )
        if (dualPaneEnabled) {
            add(
                BottomSheetActionItem(
                    id = "copy_other_pane",
                    title = "Copy to Other Pane",
                    subtitle = "Transfer directly to opposite pane",
                    icon = Icons.Default.SwapHoriz,
                    onClick = onCopyToOtherPane
                )
            )
            add(
                BottomSheetActionItem(
                    id = "move_other_pane",
                    title = "Move to Other Pane",
                    subtitle = "Move directly to opposite pane",
                    icon = Icons.AutoMirrored.Filled.DriveFileMove,
                    onClick = onMoveToOtherPane
                )
            )
        }
        add(
            BottomSheetActionItem(
                id = "rename",
                title = "Rename",
                subtitle = "Change file or folder name with validation",
                icon = Icons.Default.DriveFileRenameOutline,
                onClick = onRename
            )
        )
        add(
            BottomSheetActionItem(
                id = "compress",
                title = "Compress to Archive",
                subtitle = "Pack into 7z, ZIP, TAR, LZ4, ZSTD, GZ, XZ & more",
                icon = Icons.Default.Archive,
                onClick = onCompressToArchive
            )
        )
        add(
            BottomSheetActionItem(
                id = "copy_path",
                title = "Copy Full Path",
                subtitle = TermuxStorageBridge.resolveRealPosixPathIfPossible(entry.path),
                icon = Icons.Default.Link,
                onClick = onCopyFullPath
            )
        )
        add(
            BottomSheetActionItem(
                id = "copy_name",
                title = "Copy File Name",
                subtitle = entry.name,
                icon = Icons.Default.ContentCopy,
                onClick = onCopyFileName
            )
        )
        add(
            BottomSheetActionItem(
                id = "favorite",
                title = if (isFavorite) "Remove from Favorites" else "Pin to Quick Access Favorites",
                icon = if (isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                onClick = onToggleFavorite
            )
        )
        if (entry.isDirectory) {
            add(
                BottomSheetActionItem(
                    id = "set_custom_icon",
                    title = "Set Custom Folder Icon",
                    subtitle = "Choose from 657 bundled Devicon technology icons",
                    icon = Icons.Default.Palette,
                    onClick = onSetCustomIcon
                )
            )
        }
        if (!entry.isDirectory) {
            add(
                BottomSheetActionItem(
                    id = "share",
                    title = "Share File",
                    subtitle = "Send via Android share sheet",
                    icon = Icons.Default.Share,
                    onClick = onShare
                )
            )
        }
        add(
            BottomSheetActionItem(
                id = "properties",
                title = "Properties & SHA-256 Checksum",
                subtitle = "Inspect format header, permissions & verify hash",
                icon = Icons.Default.Info,
                onClick = onProperties
            )
        )
        add(
            BottomSheetActionItem(
                id = "delete",
                title = "Delete",
                subtitle = "Move to Recycle Bin or permanently delete",
                icon = Icons.Default.Delete,
                isDestructive = true,
                onClick = onDelete
            )
        )
    }

    ExplorerModalBottomSheet(
        title = entry.name,
        subtitle = FormatDetectorAndMetadataEngine.formatTimestamp(entry.lastModified),
        headerIcon = if (entry.isDirectory) Icons.Default.Folder else Icons.AutoMirrored.Filled.InsertDriveFile,
        metadataBadges = badges,
        actions = actions,
        onDismiss = onDismiss
    )
}

/**
 * CONTEXTUAL SORT, GROUPING & VIEW MODE MODAL BOTTOM SHEET
 */
@Composable
fun ExplorerSortAndViewBottomSheet(
    currentViewMode: ViewMode,
    currentGridColumns: Int,
    currentSortField: SortField,
    currentSortOrder: SortOrder,
    currentFolderPlacement: FolderPlacement,
    currentGroupBy: GroupBy,
    onChangeViewMode: (ViewMode) -> Unit,
    onChangeGridColumns: (Int) -> Unit,
    onChangeSort: (SortField, SortOrder, FolderPlacement) -> Unit,
    onChangeGroupBy: (GroupBy) -> Unit,
    onDismiss: () -> Unit
) {
    ExplorerModalBottomSheet(
        title = "View, Sort & Grouping",
        subtitle = "Customize active pane layout, sorting & grouping",
        headerIcon = Icons.AutoMirrored.Filled.Sort,
        metadataBadges = listOf(
            currentViewMode.label,
            "${currentSortField.label} (${currentSortOrder.label})",
            "Group: ${currentGroupBy.label}"
        ),
        onDismiss = onDismiss,
        customContent = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Display Layout Mode",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ViewMode.entries.forEach { mode ->
                        FilterChip(
                            selected = currentViewMode == mode,
                            onClick = { onChangeViewMode(mode) },
                            label = { Text(mode.label) },
                            leadingIcon = {
                                val icon = when (mode) {
                                    ViewMode.GRID -> Icons.Default.GridView
                                    ViewMode.SIMPLE_LIST -> Icons.AutoMirrored.Filled.ViewList
                                    ViewMode.DETAILED_LIST -> Icons.Default.TableRows
                                }
                                Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                if (currentViewMode == ViewMode.GRID) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Grid Columns:",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        listOf(2, 3, 4, 5).forEach { cols ->
                            FilterChip(
                                selected = currentGridColumns == cols,
                                onClick = { onChangeGridColumns(cols) },
                                label = { Text("${cols}×") }
                            )
                        }
                    }
                }

                HorizontalDivider()

                Text(
                    text = "Sort By Field & Order",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    SortField.entries.forEach { field ->
                        FilterChip(
                            selected = currentSortField == field,
                            onClick = {
                                onChangeSort(field, currentSortOrder, currentFolderPlacement)
                            },
                            label = { Text(field.label) }
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SortOrder.entries.forEach { order ->
                        FilterChip(
                            selected = currentSortOrder == order,
                            onClick = {
                                onChangeSort(currentSortField, order, currentFolderPlacement)
                            },
                            label = { Text(order.label) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Text(
                    text = "Folder Placement",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FolderPlacement.entries.forEach { placement ->
                        FilterChip(
                            selected = currentFolderPlacement == placement,
                            onClick = {
                                onChangeSort(currentSortField, currentSortOrder, placement)
                            },
                            label = { Text(placement.label) }
                        )
                    }
                }

                HorizontalDivider()

                Text(
                    text = "Group Items By",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    GroupBy.entries.forEach { group ->
                        FilterChip(
                            selected = currentGroupBy == group,
                            onClick = { onChangeGroupBy(group) },
                            label = { Text(group.label) }
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))
            }
        }
    )
}

/**
 * First-Launch & Live Permission Setup Dialog for Explorer Pro.
 * Displays live OS permission status for All Files Access (MANAGE_EXTERNAL_STORAGE / Runtime Storage),
 * Operation Notifications (POST_NOTIFICATIONS), and Termux $HOME SAF Bridge, with 1-tap "Grant All"
 * chaining and per-permission grant triggers.
 */
@Composable
fun ExplorerFirstLaunchPermissionDialog(
    permissionStatus: com.explorerpro.domain.model.AppPermissionStatus,
    onGrantAllRequired: () -> Unit,
    onRequestStorageAccess: () -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onRequestTermuxMount: () -> Unit,
    onOpenSystemAppSettings: () -> Unit,
    onCompleteOrContinue: () -> Unit,
    modifier: Modifier = Modifier
) {
    val allCoreGranted = permissionStatus.areAllCorePermissionsGranted

    AlertDialog(
        onDismissRequest = {
            if (allCoreGranted) {
                onCompleteOrContinue()
            }
        },
        icon = {
            Surface(
                color = if (allCoreGranted) {
                    FluentEmerald400.copy(alpha = 0.16f)
                } else {
                    MaterialTheme.colorScheme.primaryContainer
                },
                shape = CircleShape,
                modifier = Modifier.size(48.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.VerifiedUser,
                        contentDescription = null,
                        tint = if (allCoreGranted) FluentEmerald400 else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }
        },
        title = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = if (allCoreGranted) {
                        "All Required Permissions Granted"
                    } else {
                        "Permissions Required for Explorer Pro"
                    },
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.semantics { heading() }
                )
                Text(
                    text = if (allCoreGranted) {
                        "Full native filesystem and background operation access is active."
                    } else {
                        "Grant the permissions below so Explorer Pro can manage device storage, extract archives, and report background progress."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // 1. Storage Permission Row (MANAGE_EXTERNAL_STORAGE on API 30+, Runtime Read/Write on API <= 29)
                PermissionItemCard(
                    title = if (permissionStatus.requiresManageExternalStorage) {
                        "All Files Access (Manage Storage)"
                    } else {
                        "Device Storage Read & Write"
                    },
                    description = "Required to list, create, rename, copy, move, delete, and extract archives across Internal Shared Storage (/storage/emulated/0) and SD cards.",
                    isGranted = permissionStatus.isStorageAccessGranted,
                    badgeLabel = if (permissionStatus.isStorageAccessGranted) "GRANTED" else "REQUIRED",
                    actionLabel = if (permissionStatus.requiresManageExternalStorage) "Enable in Settings" else "Grant Access",
                    onActionClick = onRequestStorageAccess,
                    testTag = "permission_row_storage"
                )

                // 2. Notification Permission Row (POST_NOTIFICATIONS on Android 13+)
                PermissionItemCard(
                    title = "Background Operation Notifications",
                    description = if (permissionStatus.requiresNotificationPermission) {
                        "Required on Android 13+ to display real-time progress bars, speed, ETA, and completion alerts for file transfers and archive extraction."
                    } else {
                        "Enabled automatically by Android OS for background file and archive operations."
                    },
                    isGranted = permissionStatus.isNotificationPermissionGranted,
                    badgeLabel = if (permissionStatus.isNotificationPermissionGranted) "GRANTED" else "REQUIRED",
                    actionLabel = "Allow Notifications",
                    onActionClick = onRequestNotificationPermission,
                    testTag = "permission_row_notifications"
                )

                // 3. Termux $HOME SAF Bridge (when Termux is installed or available)
                PermissionItemCard(
                    title = "Termux (\$HOME) SAF Bridge",
                    description = when {
                        permissionStatus.isTermuxAccessGranted ->
                            "Termux \$HOME (/data/data/com.termux/files/home) is mounted and ready for direct file operations."
                        permissionStatus.isTermuxDetected ->
                            "Termux was detected on this device. Grant SAF folder access to auto-mount /data/data/com.termux/files/home."
                        else ->
                            "Auto-watcher active. When Termux is installed or opened on this device, Explorer Pro will automatically offer to mount \$HOME."
                    },
                    isGranted = permissionStatus.isTermuxAccessGranted || !permissionStatus.isTermuxDetected,
                    badgeLabel = when {
                        permissionStatus.isTermuxAccessGranted -> "MOUNTED"
                        permissionStatus.isTermuxDetected -> "DETECTED"
                        else -> "AUTO-WATCH"
                    },
                    actionLabel = if (permissionStatus.isTermuxDetected && !permissionStatus.isTermuxAccessGranted) {
                        "Mount Termux"
                    } else null,
                    onActionClick = onRequestTermuxMount,
                    testTag = "permission_row_termux"
                )

                if (!allCoreGranted) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = onOpenSystemAppSettings) {
                            Text(
                                text = "Open App Info Settings",
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (!allCoreGranted) {
                Button(
                    onClick = onGrantAllRequired,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("permission_grant_all_button")
                ) {
                    Icon(Icons.Default.VerifiedUser, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Grant All Permissions")
                }
            } else {
                Button(
                    onClick = onCompleteOrContinue,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("permission_start_exploring_button")
                ) {
                    Text("Start Exploring")
                }
            }
        },
        dismissButton = {
            if (!allCoreGranted) {
                TextButton(
                    onClick = onCompleteOrContinue,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("permission_continue_scoped_button")
                ) {
                    Text("Continue Limited")
                }
            }
        },
        modifier = modifier.testTag("explorer_first_launch_permission_dialog")
    )
}

@Composable
private fun PermissionItemCard(
    title: String,
    description: String,
    isGranted: Boolean,
    badgeLabel: String,
    actionLabel: String?,
    onActionClick: () -> Unit,
    testTag: String
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(
            width = 1.dp,
            color = if (isGranted) {
                FluentEmerald400.copy(alpha = 0.4f)
            } else {
                FluentAmber400.copy(alpha = 0.5f)
            }
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag(testTag)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                Surface(
                    color = if (isGranted) {
                        FluentEmerald400.copy(alpha = 0.18f)
                    } else {
                        FluentAmber400.copy(alpha = 0.2f)
                    },
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = badgeLabel,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (isGranted) FluentEmerald400 else FluentAmber400,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                    )
                }
            }

            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (!isGranted && actionLabel != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    OutlinedButton(
                        onClick = onActionClick,
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Text(actionLabel, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}

