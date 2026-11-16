package com.explorerpro.presentation.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FitScreen
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import com.explorerpro.core.filesystem.TermuxStorageBridge
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.explorerpro.core.designsystem.dialogs.ExplorerConfirmationDialog
import com.explorerpro.core.designsystem.dialogs.ExplorerFullScreenDialog
import com.explorerpro.core.designsystem.dialogs.ExplorerInputDialog
import com.explorerpro.core.designsystem.dialogs.ExplorerPropertiesDialog
import com.explorerpro.core.designsystem.progress.ExplorerProgressIndicator
import com.explorerpro.core.designsystem.progress.ProgressLabelPosition
import com.explorerpro.core.filesystem.FormatDetectorAndMetadataEngine
import com.explorerpro.data.local.OperationLogEntity
import com.explorerpro.domain.model.ArchiveCompressionLevel
import com.explorerpro.domain.model.ArchiveCreateFormat
import com.explorerpro.domain.model.ArchiveCreationConfig
import com.explorerpro.domain.model.ArchiveEncryptionMode
import com.explorerpro.domain.model.ArchiveEntryItem
import com.explorerpro.domain.model.BatchRenameConfig
import com.explorerpro.domain.model.BatchRenamePreviewItem
import com.explorerpro.domain.model.CaseTransform
import com.explorerpro.domain.model.ExplorerInputValidator
import com.explorerpro.domain.model.FileEntry
import com.explorerpro.domain.model.FileOperationTask
import com.explorerpro.domain.model.FolderComparisonResult
import com.explorerpro.domain.model.OperationStatus
import com.explorerpro.domain.model.StorageAnalysisReport
import com.explorerpro.ui.theme.FluentAmber400
import com.explorerpro.ui.theme.FluentEmerald400
import com.explorerpro.ui.theme.FluentRose400
import kotlinx.coroutines.delay
import java.io.File

@Composable
fun FullScreenImageViewerDialog(
    currentImage: FileEntry,
    galleryList: List<FileEntry>,
    onSelectImage: (FileEntry) -> Unit,
    onShare: (FileEntry) -> Unit,
    onToggleFavorite: (FileEntry) -> Unit,
    onDelete: (FileEntry) -> Unit,
    onDismiss: () -> Unit
) {
    var scale by remember(currentImage.path) { mutableFloatStateOf(1f) }
    var offset by remember(currentImage.path) { mutableStateOf(Offset.Zero) }
    var rotationDegrees by remember(currentImage.path) { mutableFloatStateOf(0f) }
    var slideshowPlaying by remember { mutableStateOf(false) }
    var showMetadataOverlay by remember { mutableStateOf(true) }

    val currentIndex = galleryList.indexOfFirst { it.path == currentImage.path }.coerceAtLeast(0)

    LaunchedEffect(slideshowPlaying, currentIndex) {
        if (slideshowPlaying && galleryList.size > 1) {
            delay(2500L)
            val nextIdx = (currentIndex + 1) % galleryList.size
            onSelectImage(galleryList[nextIdx])
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            color = Color(0xFF060B13),
            modifier = Modifier.fillMaxSize()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .systemBarsPadding()
            ) {
                // Top Viewer Control Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xCC0D1B2A))
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close viewer", tint = Color.White)
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = currentImage.name,
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${currentIndex + 1} of ${galleryList.size} • ${currentImage.dimensionsText ?: "Image"} • ${FormatDetectorAndMetadataEngine.formatBytes(currentImage.sizeBytes)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF94A3B8)
                        )
                    }
                    IconButton(onClick = { rotationDegrees = (rotationDegrees + 90f) % 360f }) {
                        Icon(Icons.Default.RotateRight, contentDescription = "Rotate 90°", tint = Color.White)
                    }
                    IconButton(onClick = {
                        scale = if (scale > 1.05f) 1f else 2f
                        offset = Offset.Zero
                    }) {
                        Icon(Icons.Default.FitScreen, contentDescription = "Reset / Toggle Zoom", tint = Color.White)
                    }
                    IconButton(onClick = { slideshowPlaying = !slideshowPlaying }) {
                        Icon(
                            imageVector = if (slideshowPlaying) Icons.Default.Stop else Icons.Default.PlayArrow,
                            contentDescription = "Slideshow",
                            tint = if (slideshowPlaying) FluentAmber400 else Color.White
                        )
                    }
                    IconButton(onClick = { onToggleFavorite(currentImage) }) {
                        Icon(Icons.Default.Star, contentDescription = "Favorite", tint = FluentAmber400)
                    }
                    IconButton(onClick = { onShare(currentImage) }) {
                        Icon(Icons.Default.Share, contentDescription = "Share", tint = Color.White)
                    }
                    IconButton(onClick = {
                        onDelete(currentImage)
                        onDismiss()
                    }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = FluentRose400)
                    }
                }

                // Interactive Pinch-to-Zoom & Double-Tap Canvas
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .pointerInput(currentImage.path) {
                            detectTapGestures(
                                onDoubleTap = {
                                    if (scale > 1.1f) {
                                        scale = 1f
                                        offset = Offset.Zero
                                    } else {
                                        scale = 2.5f
                                    }
                                },
                                onTap = { showMetadataOverlay = !showMetadataOverlay }
                            )
                        }
                        .pointerInput(currentImage.path) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                val newScale = (scale * zoom).coerceIn(0.75f, 5f)
                                scale = newScale
                                offset = if (newScale > 1f) offset + pan else Offset.Zero
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    AsyncImage(
                        model = if (currentImage.isSafUri) android.net.Uri.parse(currentImage.path) else File(currentImage.path),
                        contentDescription = currentImage.name,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer(
                                scaleX = scale,
                                scaleY = scale,
                                translationX = offset.x,
                                translationY = offset.y,
                                rotationZ = rotationDegrees
                            )
                    )

                    // Prev / Next Overlay Buttons
                    if (galleryList.size > 1) {
                        IconButton(
                            onClick = {
                                val prev = if (currentIndex > 0) currentIndex - 1 else galleryList.lastIndex
                                onSelectImage(galleryList[prev])
                            },
                            modifier = Modifier
                                .align(Alignment.CenterStart)
                                .padding(8.dp)
                                .background(Color(0x88000000), RoundedCornerShape(50))
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Previous image", tint = Color.White)
                        }

                        IconButton(
                            onClick = {
                                val next = (currentIndex + 1) % galleryList.size
                                onSelectImage(galleryList[next])
                            },
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .padding(8.dp)
                                .background(Color(0x88000000), RoundedCornerShape(50))
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Next image", tint = Color.White)
                        }
                    }

                    if (showMetadataOverlay) {
                        Surface(
                            color = Color(0xCC0D1B2A),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(12.dp)
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(
                                    text = "Format: ${currentImage.detectedFormat.displayName} (${currentImage.mimeType})",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Color.White
                                )
                                Text(
                                    text = "Dimensions: ${currentImage.dimensionsText ?: "Unavailable"} • Zoom: ${(scale * 100).toInt()}% • Rotation: ${rotationDegrees.toInt()}°",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFFBAE6FD)
                                )
                                Text(
                                    text = "Magic Header: ${currentImage.detectedFormat.magicSignatureHex}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFF94A3B8)
                                )
                            }
                        }
                    }
                }

                // Bottom Filmstrip Gallery
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xEE0D1B2A))
                        .padding(vertical = 8.dp, horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(galleryList, key = { it.path }) { item ->
                        val isCurrent = item.path == currentImage.path
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .border(
                                    width = if (isCurrent) 2.dp else 0.dp,
                                    color = if (isCurrent) MaterialTheme.colorScheme.primary else Color.Transparent,
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .clickable { onSelectImage(item) }
                        ) {
                            AsyncImage(
                                model = if (item.isSafUri) android.net.Uri.parse(item.path) else File(item.path),
                                contentDescription = item.name,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ArchiveInspectorDialog(
    archiveFile: FileEntry,
    entries: List<ArchiveEntryItem>,
    verificationStatus: String?,
    onVerifyIntegrity: (String?) -> Unit,
    onExtractHere: (String?) -> Unit,
    onExtractAll: (String?) -> Unit,
    onExtractSelected: (Set<String>, String?) -> Unit,
    onConvertArchive: (() -> Unit)? = null,
    onDismiss: () -> Unit
) {
    var selectedPaths by remember { mutableStateOf<Set<String>>(emptySet()) }
    var password by remember { mutableStateOf("") }
    var showPasswordField by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(
                    text = "Archive Engine — ${archiveFile.name}",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "${entries.size} entries • ${archiveFile.detectedFormat.displayName} • Path Traversal Guard Active",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(onClick = { onVerifyIntegrity(password.takeIf { it.isNotBlank() }) }) {
                        Icon(Icons.Default.VerifiedUser, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Verify Integrity")
                    }
                    if (onConvertArchive != null) {
                        OutlinedButton(
                            onClick = onConvertArchive,
                            modifier = Modifier.testTag("inspector_convert_archive_button")
                        ) {
                            Icon(Icons.Default.SwapHoriz, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Convert Format…")
                        }
                    }
                    FilterChip(
                        selected = showPasswordField || password.isNotEmpty(),
                        onClick = { showPasswordField = !showPasswordField },
                        label = { Text(if (password.isNotEmpty()) "Password Set" else "Password (Optional)") }
                    )
                    if (selectedPaths.isNotEmpty()) {
                        TextButton(onClick = {
                            onExtractSelected(selectedPaths, password.takeIf { it.isNotBlank() })
                        }) {
                            Text("Extract Selected (${selectedPaths.size})")
                        }
                    }
                }

                if (showPasswordField) {
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Archive Password (for encrypted 7Z / ZIP)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                if (verificationStatus != null) {
                    Spacer(Modifier.height(6.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = verificationStatus,
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 300.dp)
                ) {
                    items(entries, key = { it.entryPath }) { item ->
                        val isSelected = selectedPaths.contains(item.entryPath)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedPaths = if (isSelected) selectedPaths - item.entryPath
                                    else selectedPaths + item.entryPath
                                }
                                .padding(vertical = 6.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = isSelected,
                                onCheckedChange = {
                                    selectedPaths = if (it) selectedPaths + item.entryPath
                                    else selectedPaths - item.entryPath
                                }
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.entryPath,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = if (item.isDirectory) "Directory" else {
                                        "${FormatDetectorAndMetadataEngine.formatBytes(item.uncompressedSize)} (Saved ${item.compressionRatioPercent}%) • CRC: ${item.crc32Hex}"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Icon(
                                imageVector = if (item.isPathSafe) Icons.Default.Security else Icons.Default.Warning,
                                contentDescription = if (item.isPathSafe) "Safe path" else "Blocked path",
                                tint = if (item.isPathSafe) FluentEmerald400 else FluentRose400,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
                    }
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { onExtractHere(password.takeIf { it.isNotBlank() }) }
                ) {
                    Text("Extract Here")
                }
                Button(
                    onClick = { onExtractAll(password.takeIf { it.isNotBlank() }) }
                ) {
                    Icon(Icons.Default.Unarchive, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Extract to Folder")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateArchiveDialog(
    selectedCount: Int,
    defaultBaseName: String = "Archive_Bundle",
    sourceArchiveToConvert: FileEntry? = null,
    initialFormat: ArchiveCreateFormat = ArchiveCreateFormat.ZIP,
    onConfirmCreateArchive: (ArchiveCreationConfig, String?) -> Unit,
    onDismiss: () -> Unit
) {
    val isConvertMode = sourceArchiveToConvert != null
    var selectedFormat by remember(initialFormat) { mutableStateOf(initialFormat) }
    var compressionLevel by remember { mutableStateOf(ArchiveCompressionLevel.NORMAL) }
    var encryptionMode by remember { mutableStateOf(ArchiveEncryptionMode.NONE) }
    var baseName by remember(defaultBaseName) { mutableStateOf(defaultBaseName) }
    var outputPassword by remember { mutableStateOf("") }
    var outputPasswordVisible by remember { mutableStateOf(false) }
    var hasSourcePassword by remember { mutableStateOf(false) }
    var sourcePassword by remember { mutableStateOf("") }
    var sourcePasswordVisible by remember { mutableStateOf(false) }
    var deleteSourceAfterCompression by remember { mutableStateOf(false) }
    var createSeparateArchives by remember { mutableStateOf(false) }

    var formatMenuExpanded by remember { mutableStateOf(false) }
    var levelMenuExpanded by remember { mutableStateOf(false) }
    var encryptionMenuExpanded by remember { mutableStateOf(false) }

    val nameValidation = remember(baseName) {
        ExplorerInputValidator.validateFileOrFolderName(baseName)
    }

    val effectiveEncryption = if (selectedFormat.supportsEncryption) {
        encryptionMode
    } else {
        ArchiveEncryptionMode.NONE
    }

    val previewFileName = remember(nameValidation.sanitizedValue, selectedFormat, createSeparateArchives, isConvertMode) {
        if (!isConvertMode && createSeparateArchives && selectedCount > 1) {
            "<each_item>.${selectedFormat.extension} ($selectedCount archives)"
        } else if (nameValidation.sanitizedValue.lowercase().endsWith(".${selectedFormat.extension}")) {
            nameValidation.sanitizedValue
        } else {
            "${nameValidation.sanitizedValue}.${selectedFormat.extension}"
        }
    }

    val passwordMissingForEncryption =
        selectedFormat.supportsEncryption &&
            effectiveEncryption != ArchiveEncryptionMode.NONE &&
            outputPassword.isEmpty()

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(24.dp),
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = if (isConvertMode) "Convert Archive Format" else "Create Archive ($selectedCount selected)",
                    style = MaterialTheme.typography.titleLarge
                )
                if (sourceArchiveToConvert != null) {
                    Text(
                        text = "Source: ${sourceArchiveToConvert.name} (${sourceArchiveToConvert.detectedFormat.displayName})",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 480.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = baseName,
                    onValueChange = { baseName = it },
                    label = { Text("Archive") },
                    suffix = { Text(".${selectedFormat.extension}") },
                    singleLine = true,
                    enabled = isConvertMode || !createSeparateArchives || selectedCount <= 1,
                    isError = !nameValidation.isValid && (isConvertMode || !createSeparateArchives || selectedCount <= 1),
                    supportingText = {
                        Text(
                            text = if (!isConvertMode && createSeparateArchives && selectedCount > 1) {
                                "Each selected item will be packed using its own filename."
                            } else {
                                nameValidation.errorMessage ?: "Output: $previewFileName"
                            },
                            color = if (!nameValidation.isValid && (isConvertMode || !createSeparateArchives || selectedCount <= 1)) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("archive_name_input")
                )

                // Archive Format Dropdown (all 16 formats: 7z, zip, tar, tar.bz2, tar.gz, tar.xz, tar.lz4, tar.zstd, tar.lzma, jar, cpio, gz, bz2, xz, lz4, zstd)
                ExposedDropdownMenuBox(
                    expanded = formatMenuExpanded,
                    onExpandedChange = { formatMenuExpanded = it },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = selectedFormat.label,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Archive format (${ArchiveCreateFormat.entries.size} formats)") },
                        supportingText = { Text(selectedFormat.description, maxLines = 2) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = formatMenuExpanded) },
                        modifier = Modifier
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                            .fillMaxWidth()
                            .testTag("archive_format_dropdown")
                    )
                    ExposedDropdownMenu(
                        expanded = formatMenuExpanded,
                        onDismissRequest = { formatMenuExpanded = false }
                    ) {
                        ArchiveCreateFormat.entries.forEach { format ->
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(
                                            text = format.label,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = if (selectedFormat == format) FontWeight.Bold else FontWeight.Normal,
                                            color = if (selectedFormat == format) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = format.description,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                },
                                onClick = {
                                    selectedFormat = format
                                    if (!format.supportsEncryption) {
                                        encryptionMode = ArchiveEncryptionMode.NONE
                                    }
                                    formatMenuExpanded = false
                                },
                                modifier = Modifier.testTag("archive_format_option_${format.extension}")
                            )
                        }
                    }
                }

                // Compression Level Dropdown
                ExposedDropdownMenuBox(
                    expanded = levelMenuExpanded,
                    onExpandedChange = {
                        if (selectedFormat.supportsCompressionLevel) {
                            levelMenuExpanded = it
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = if (selectedFormat.supportsCompressionLevel) compressionLevel.label else "Standard (Fixed)",
                        onValueChange = {},
                        readOnly = true,
                        enabled = selectedFormat.supportsCompressionLevel,
                        label = { Text("Compression level") },
                        trailingIcon = {
                            if (selectedFormat.supportsCompressionLevel) {
                                ExposedDropdownMenuDefaults.TrailingIcon(expanded = levelMenuExpanded)
                            }
                        },
                        modifier = Modifier
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                            .fillMaxWidth()
                            .testTag("compression_level_dropdown")
                    )
                    ExposedDropdownMenu(
                        expanded = levelMenuExpanded,
                        onDismissRequest = { levelMenuExpanded = false }
                    ) {
                        ArchiveCompressionLevel.entries.forEach { level ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = level.label,
                                        fontWeight = if (compressionLevel == level) FontWeight.Bold else FontWeight.Normal,
                                        color = if (compressionLevel == level) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                    )
                                },
                                onClick = {
                                    compressionLevel = level
                                    levelMenuExpanded = false
                                },
                                modifier = Modifier.testTag("compression_level_option_${level.name}")
                            )
                        }
                    }
                }

                // Encryption Dropdown + Output Password
                ExposedDropdownMenuBox(
                    expanded = encryptionMenuExpanded,
                    onExpandedChange = {
                        if (selectedFormat.supportsEncryption) {
                            encryptionMenuExpanded = it
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = if (selectedFormat.supportsEncryption) encryptionMode.label else "None (ZIP supports AES-256)",
                        onValueChange = {},
                        readOnly = true,
                        enabled = selectedFormat.supportsEncryption,
                        label = { Text("Encryption") },
                        trailingIcon = {
                            if (selectedFormat.supportsEncryption) {
                                ExposedDropdownMenuDefaults.TrailingIcon(expanded = encryptionMenuExpanded)
                            }
                        },
                        modifier = Modifier
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                            .fillMaxWidth()
                            .testTag("archive_encryption_dropdown")
                    )
                    ExposedDropdownMenu(
                        expanded = encryptionMenuExpanded,
                        onDismissRequest = { encryptionMenuExpanded = false }
                    ) {
                        ArchiveEncryptionMode.entries.forEach { mode ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = mode.label,
                                        fontWeight = if (encryptionMode == mode) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                onClick = {
                                    encryptionMode = mode
                                    encryptionMenuExpanded = false
                                }
                            )
                        }
                    }
                }

                if (selectedFormat.supportsEncryption) {
                    OutlinedTextField(
                        value = outputPassword,
                        onValueChange = {
                            outputPassword = it
                            if (it.isNotEmpty() && encryptionMode == ArchiveEncryptionMode.NONE) {
                                encryptionMode = ArchiveEncryptionMode.AES_256
                            }
                        },
                        label = { Text("Enter password (optional)") },
                        singleLine = true,
                        isError = passwordMissingForEncryption,
                        supportingText = if (passwordMissingForEncryption) {
                            { Text("Enter a password for ${encryptionMode.label} encryption.", color = MaterialTheme.colorScheme.error) }
                        } else null,
                        visualTransformation = if (outputPasswordVisible) {
                            VisualTransformation.None
                        } else {
                            PasswordVisualTransformation()
                        },
                        trailingIcon = {
                            IconButton(
                                onClick = { outputPasswordVisible = !outputPasswordVisible },
                                modifier = Modifier.minimumInteractiveComponentSize()
                            ) {
                                Icon(
                                    imageVector = if (outputPasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = if (outputPasswordVisible) "Hide password" else "Show password"
                                )
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("archive_password_input")
                    )
                }

                if (isConvertMode) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .minimumInteractiveComponentSize()
                            .clickable { hasSourcePassword = !hasSourcePassword },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = hasSourcePassword,
                            onCheckedChange = { hasSourcePassword = it }
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = "Source archive is password-protected",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    if (hasSourcePassword) {
                        OutlinedTextField(
                            value = sourcePassword,
                            onValueChange = { sourcePassword = it },
                            label = { Text("Source archive password") },
                            singleLine = true,
                            visualTransformation = if (sourcePasswordVisible) {
                                VisualTransformation.None
                            } else {
                                PasswordVisualTransformation()
                            },
                            trailingIcon = {
                                IconButton(
                                    onClick = { sourcePasswordVisible = !sourcePasswordVisible },
                                    modifier = Modifier.minimumInteractiveComponentSize()
                                ) {
                                    Icon(
                                        imageVector = if (sourcePasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = if (sourcePasswordVisible) "Hide source password" else "Show source password"
                                    )
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("source_archive_password_input")
                        )
                    }
                }

                // Checkboxes (matching ZArchiver options)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .minimumInteractiveComponentSize()
                        .clickable { deleteSourceAfterCompression = !deleteSourceAfterCompression },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = deleteSourceAfterCompression,
                        onCheckedChange = { deleteSourceAfterCompression = it },
                        modifier = Modifier.testTag("delete_source_checkbox")
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = if (isConvertMode) {
                            "Delete original archive after conversion"
                        } else {
                            "Delete source files after compression"
                        },
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                if (!isConvertMode) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .minimumInteractiveComponentSize()
                            .clickable(enabled = selectedCount > 1) {
                                createSeparateArchives = !createSeparateArchives
                            },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = createSeparateArchives && selectedCount > 1,
                            enabled = selectedCount > 1,
                            onCheckedChange = { createSeparateArchives = it },
                            modifier = Modifier.testTag("create_separate_archives_checkbox")
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = if (selectedCount > 1) {
                                "Create separate archive ($selectedCount items)"
                            } else {
                                "Create separate archive (select 2+ items)"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (selectedCount > 1) {
                                MaterialTheme.colorScheme.onSurface
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val sanitizedName = if (!isConvertMode && createSeparateArchives && selectedCount > 1) {
                        "Separate_Archives"
                    } else {
                        nameValidation.sanitizedValue
                    }
                    onConfirmCreateArchive(
                        ArchiveCreationConfig(
                            archiveName = sanitizedName,
                            format = selectedFormat,
                            compressionLevel = compressionLevel,
                            encryptionMode = effectiveEncryption,
                            password = outputPassword,
                            deleteSourceAfterCompression = deleteSourceAfterCompression,
                            createSeparateArchives = !isConvertMode && createSeparateArchives && selectedCount > 1
                        ),
                        sourcePassword.takeIf { isConvertMode && hasSourcePassword && it.isNotEmpty() }
                    )
                },
                enabled = (!isConvertMode && createSeparateArchives && selectedCount > 1 || nameValidation.isValid) &&
                    !passwordMissingForEncryption,
                modifier = Modifier.testTag("confirm_archive_action_button")
            ) {
                Text(
                    if (isConvertMode) {
                        "Convert to ${selectedFormat.shortName.uppercase()}"
                    } else {
                        "Create ${selectedFormat.shortName.uppercase()}"
                    }
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun BatchRenameDialog(
    selectedFiles: List<FileEntry>,
    onGeneratePreview: (List<FileEntry>, BatchRenameConfig) -> List<BatchRenamePreviewItem>,
    onConfirmBatchRename: (BatchRenameConfig) -> Unit,
    onDismiss: () -> Unit
) {
    var prefix by remember { mutableStateOf("") }
    var suffix by remember { mutableStateOf("") }
    var findText by remember { mutableStateOf("") }
    var replaceText by remember { mutableStateOf("") }
    var useRegex by remember { mutableStateOf(false) }
    var removeChars by remember { mutableStateOf("") }
    var caseTransform by remember { mutableStateOf(CaseTransform.NONE) }
    var addSequence by remember { mutableStateOf(true) }
    var appendDate by remember { mutableStateOf(false) }
    var overrideExt by remember { mutableStateOf("") }
    var showConfirmExecution by remember { mutableStateOf(false) }

    val config = BatchRenameConfig(
        prefix = prefix,
        suffix = suffix,
        findText = findText,
        replaceText = replaceText,
        useRegex = useRegex,
        removeChars = removeChars,
        caseTransform = caseTransform,
        addSequentialNumber = addSequence,
        appendDateStamp = appendDate,
        overrideExtension = overrideExt
    )

    val previews = remember(selectedFiles, config) {
        onGeneratePreview(selectedFiles, config)
    }
    val hasConflicts = previews.any { it.hasConflict }
    val modifiedCount = previews.count { it.originalFile.name != it.proposedName }

    ExplorerFullScreenDialog(
        title = "Batch Rename (${selectedFiles.size} items)",
        subtitle = if (hasConflicts) {
            "⚠ Resolve naming conflicts before applying"
        } else {
            "$modifiedCount of ${previews.size} item(s) will be renamed"
        },
        onDismiss = onDismiss,
        primaryActionLabel = "Apply Batch Rename ($modifiedCount)",
        onPrimaryAction = { showConfirmExecution = true },
        primaryActionEnabled = !hasConflicts && previews.isNotEmpty() && modifiedCount > 0,
        currentStep = if (hasConflicts) 1 else 2,
        totalSteps = 2,
        stepLabels = listOf("1. Pattern Rules", "2. Live Preview & Verify")
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = prefix,
                    onValueChange = { prefix = it },
                    label = { Text("Prefix") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = suffix,
                    onValueChange = { suffix = it },
                    label = { Text("Suffix") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = findText,
                    onValueChange = { findText = it },
                    label = { Text(if (useRegex) "Regex Pattern" else "Find Text") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = replaceText,
                    onValueChange = { replaceText = it },
                    label = { Text("Replace With") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = removeChars,
                    onValueChange = { removeChars = it },
                    label = { Text("Strip Chars") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = overrideExt,
                    onValueChange = { overrideExt = it },
                    label = { Text("New Ext (opt)") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }

            // Toggles
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                FilterChip(
                    selected = addSequence,
                    onClick = { addSequence = !addSequence },
                    label = { Text("Numbering (_01)") }
                )
                FilterChip(
                    selected = appendDate,
                    onClick = { appendDate = !appendDate },
                    label = { Text("Date Stamp") }
                )
                FilterChip(
                    selected = useRegex,
                    onClick = { useRegex = !useRegex },
                    label = { Text("Regex") }
                )
            }

            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                CaseTransform.entries.forEach { mode ->
                    FilterChip(
                        selected = caseTransform == mode,
                        onClick = { caseTransform = mode },
                        label = { Text(mode.label) }
                    )
                }
            }

            HorizontalDivider()
            Text(
                text = "Live Preview (${previews.size} items):",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )

            previews.forEach { item ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .padding(8.dp)
                ) {
                    Text(
                        text = "${item.originalFile.name}  →  ${item.proposedName}",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (item.hasConflict) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                    )
                    if (item.conflictReason != null) {
                        Text(
                            text = "⚠ ${item.conflictReason}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }

    if (showConfirmExecution) {
        ExplorerConfirmationDialog(
            title = "Confirm Batch Rename",
            consequenceMessage = "Rename $modifiedCount item(s) using the configured pattern rules?",
            summaryBadgeText = "$modifiedCount of ${previews.size} items will be renamed on disk",
            confirmLabel = "Rename $modifiedCount Items",
            onConfirm = { _, _ ->
                showConfirmExecution = false
                onConfirmBatchRename(config)
            },
            onDismiss = { showConfirmExecution = false }
        )
    }
}

@Composable
fun FilePropertiesDialog(
    entry: FileEntry,
    sha256Checksum: String?,
    onCopyText: (String, String) -> Unit = { _, _ -> },
    onShare: ((FileEntry) -> Unit)? = null,
    onToggleFavorite: ((FileEntry) -> Unit)? = null,
    onDismiss: () -> Unit
) {
    ExplorerPropertiesDialog(
        entry = entry,
        sha256Checksum = sha256Checksum,
        onCopyText = onCopyText,
        onShare = onShare,
        onToggleFavorite = onToggleFavorite,
        onDismiss = onDismiss
    )
}

@Composable
private fun PropertyRow(
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

@Composable
fun StorageAnalyzerDialog(
    report: StorageAnalysisReport?,
    isAnalyzing: Boolean,
    activeScanTask: FileOperationTask? = null,
    onDeleteDuplicateFile: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Storage Intelligence & SHA-256 Duplicates") },
        text = {
            if (isAnalyzing || report == null) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ExplorerProgressIndicator(
                        min = activeScanTask?.minValue ?: 0f,
                        max = activeScanTask?.maxValue ?: 100f,
                        value = activeScanTask?.progressValue ?: 0f,
                        labelPosition = ProgressLabelPosition.TOP_FLOATING,
                        operationLabel = activeScanTask?.title ?: "Storage & Duplicate Analysis",
                        statusDescription = activeScanTask?.currentItemName?.ifEmpty {
                            "Scanning storage categories and SHA-256 digests…"
                        } ?: "Scanning storage categories and SHA-256 digests…",
                        indeterminate = activeScanTask?.indeterminate ?: true,
                        status = activeScanTask?.status ?: OperationStatus.RUNNING,
                        trackHeight = 10.dp
                    )
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Workspace Used: ${FormatDetectorAndMetadataEngine.formatBytes(report.totalWorkspaceBytes)}",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary
                    )

                    report.categoryBreakdown.entries.sortedByDescending { it.value }.forEach { (cat, bytes) ->
                        val ratioPercent = if (report.totalWorkspaceBytes > 0) {
                            (bytes.toFloat() / report.totalWorkspaceBytes.toFloat() * 100f).coerceIn(0f, 100f)
                        } else 0f
                        ExplorerProgressIndicator(
                            min = 0f,
                            max = 100f,
                            value = ratioPercent,
                            labelPosition = ProgressLabelPosition.INLINE,
                            operationLabel = "${cat.label} (${FormatDetectorAndMetadataEngine.formatBytes(bytes)})",
                            showStatus = false,
                            compact = true,
                            trackHeight = 6.dp
                        )
                    }

                    HorizontalDivider()
                    Text(
                        text = "Exact SHA-256 Duplicate Groups (${report.duplicateGroups.size}):",
                        style = MaterialTheme.typography.titleSmall,
                        color = FluentAmber400
                    )
                    if (report.duplicateGroups.isEmpty()) {
                        Text(
                            text = "No duplicate files found across workspace.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    } else {
                        report.duplicateGroups.forEach { group ->
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(8.dp)) {
                                    Text(
                                        text = "SHA-256: ${group.sha256.take(18)}… (${FormatDetectorAndMetadataEngine.formatBytes(group.sizeBytes)} each)",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    group.files.forEachIndexed { idx, dupFile ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = "${if (idx == 0) "[Original]" else "[Duplicate]"} ${dupFile.name}",
                                                style = MaterialTheme.typography.bodySmall,
                                                modifier = Modifier.weight(1f),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            if (idx > 0) {
                                                IconButton(
                                                    onClick = { onDeleteDuplicateFile(dupFile.path) },
                                                    modifier = Modifier.size(26.dp)
                                                ) {
                                                    Icon(
                                                        Icons.Default.Delete,
                                                        contentDescription = "Delete duplicate ${dupFile.name}",
                                                        tint = FluentRose400,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Done") }
        }
    )
}

@Composable
fun FolderComparisonDialog(
    comparison: FolderComparisonResult,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Dual-Pane Folder Comparison") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Left: ${File(comparison.leftPath).name}  ↔  Right: ${File(comparison.rightPath).name}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                HorizontalDivider()
                Text(
                    text = "Only in Left Pane (${comparison.onlyInLeft.size}):",
                    style = MaterialTheme.typography.titleSmall
                )
                comparison.onlyInLeft.forEach {
                    Text("• ${it.name} (${FormatDetectorAndMetadataEngine.formatBytes(it.sizeBytes)})", style = MaterialTheme.typography.bodySmall)
                }

                Text(
                    text = "Only in Right Pane (${comparison.onlyInRight.size}):",
                    style = MaterialTheme.typography.titleSmall
                )
                comparison.onlyInRight.forEach {
                    Text("• ${it.name} (${FormatDetectorAndMetadataEngine.formatBytes(it.sizeBytes)})", style = MaterialTheme.typography.bodySmall)
                }

                Text(
                    text = "Different Size / Type (${comparison.modifiedDifferences.size}):",
                    style = MaterialTheme.typography.titleSmall,
                    color = FluentAmber400
                )
                comparison.modifiedDifferences.forEach { (l, r) ->
                    Text(
                        "• ${l.name}: Left ${FormatDetectorAndMetadataEngine.formatBytes(l.sizeBytes)} vs Right ${FormatDetectorAndMetadataEngine.formatBytes(r.sizeBytes)}",
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                Text(
                    text = "Identical Name & Size (${comparison.identicalFiles.size}):",
                    style = MaterialTheme.typography.titleSmall,
                    color = FluentEmerald400
                )
                comparison.identicalFiles.forEach {
                    Text("✓ ${it.name}", style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

@Composable
fun TextFileEditorDialog(
    entry: FileEntry,
    initialContent: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var content by remember(entry.path) { mutableStateOf(initialContent) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(entry.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    "${entry.detectedFormat.displayName} • ${FormatDetectorAndMetadataEngine.formatBytes(entry.sizeBytes)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        },
        text = {
            OutlinedTextField(
                value = content,
                onValueChange = { content = it },
                textStyle = MaterialTheme.typography.labelMedium,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp)
            )
        },
        confirmButton = {
            Button(onClick = { onSave(content) }) {
                Text("Save File")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun SingleInputPromptDialog(
    title: String,
    label: String,
    initialValue: String = "",
    secondaryLabel: String? = null,
    existingSiblingNames: Set<String> = emptySet(),
    originalName: String? = null,
    helperText: String? = null,
    onConfirm: (String, String) -> Unit,
    onDismiss: () -> Unit
) {
    ExplorerInputDialog(
        title = title,
        label = label,
        initialValue = initialValue,
        secondaryLabel = secondaryLabel,
        helperText = helperText,
        existingSiblingNames = existingSiblingNames,
        originalName = originalName,
        validateAsFileName = true,
        onConfirm = onConfirm,
        onDismiss = onDismiss
    )
}
