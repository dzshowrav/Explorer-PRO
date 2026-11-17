package com.explorerpro.presentation.ui

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.decode.SvgDecoder
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.explorerpro.core.icons.DeviconCatalogRepository
import com.explorerpro.core.icons.DeviconCategory
import com.explorerpro.core.icons.DeviconItem
import com.explorerpro.domain.model.FileEntry
import com.explorerpro.ui.theme.FluentFolderGold

object DeviconImageLoader {

    @Volatile
    private var instance: ImageLoader? = null

    fun get(context: Context): ImageLoader {
        instance?.let { return it }
        return synchronized(this) {
            instance?.let { return it }
            val created = ImageLoader.Builder(context.applicationContext)
                .components {
                    add(SvgDecoder.Factory())
                }
                .memoryCachePolicy(CachePolicy.ENABLED)
                .diskCachePolicy(CachePolicy.DISABLED)
                .crossfade(false)
                .build()
            instance = created
            created
        }
    }
}

@Composable
fun DeviconAssetIcon(
    item: DeviconItem,
    size: Dp,
    contentDescription: String? = item.displayName,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val imageLoader = remember(context) { DeviconImageLoader.get(context) }
    val isDarkSurface = MaterialTheme.colorScheme.surface.luminance() < 0.5f || isSystemInDarkTheme()
    val useLightPill = item.needsLightBacking && isDarkSurface

    val request = remember(item.assetUri, context) {
        ImageRequest.Builder(context)
            .data(item.assetUri)
            .build()
    }

    if (useLightPill) {
        Box(
            modifier = modifier
                .size(size)
                .clip(RoundedCornerShape(6.dp))
                .background(Color(0xFFF1F5F9).copy(alpha = 0.94f))
                .padding((size.value * 0.10f).dp.coerceAtLeast(2.dp)),
            contentAlignment = Alignment.Center
        ) {
            AsyncImage(
                model = request,
                imageLoader = imageLoader,
                contentDescription = contentDescription,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )
        }
    } else {
        AsyncImage(
            model = request,
            imageLoader = imageLoader,
            contentDescription = contentDescription,
            contentScale = ContentScale.Fit,
            modifier = modifier.size(size)
        )
    }
}

@Composable
fun FolderDeviconBadgeOverlay(
    badgeItem: DeviconItem,
    badgeSize: Dp,
    modifier: Modifier = Modifier
) {
    val isDarkSurface = MaterialTheme.colorScheme.surface.luminance() < 0.5f || isSystemInDarkTheme()
    val containerColor = if (badgeItem.needsLightBacking && isDarkSurface) {
        Color(0xFFF1F5F9)
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh
    }
    Surface(
        color = containerColor,
        shape = RoundedCornerShape((badgeSize.value * 0.30f).dp.coerceAtLeast(4.dp)),
        tonalElevation = 2.dp,
        shadowElevation = 2.dp,
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.surface
        ),
        modifier = modifier.size(badgeSize)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding((badgeSize.value * 0.14f).dp.coerceAtLeast(2.dp)),
            contentAlignment = Alignment.Center
        ) {
            val context = LocalContext.current
            val imageLoader = remember(context) { DeviconImageLoader.get(context) }
            val request = remember(badgeItem.assetUri, context) {
                ImageRequest.Builder(context)
                    .data(badgeItem.assetUri)
                    .build()
            }
            AsyncImage(
                model = request,
                imageLoader = imageLoader,
                contentDescription = "${badgeItem.displayName} folder badge",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
fun DeviconPickerDialog(
    targetFolderEntry: FileEntry?,
    currentCustomIconId: String?,
    onSelectIcon: (String) -> Unit,
    onClearCustomIcon: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val allIcons = remember(context) { DeviconCatalogRepository.getCatalog(context) }

    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf(DeviconCategory.ALL) }
    var chosenIconId by remember(currentCustomIconId, targetFolderEntry?.path) {
        mutableStateOf(currentCustomIconId ?: allIcons.firstOrNull()?.id)
    }

    val filteredIcons = remember(allIcons, searchQuery, selectedCategory) {
        DeviconCatalogRepository.search(allIcons, searchQuery, selectedCategory)
    }
    val chosenItem = remember(allIcons, chosenIconId) {
        allIcons.firstOrNull { it.id == chosenIconId }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(horizontal = 12.dp, vertical = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp,
                shadowElevation = 12.dp,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 760.dp)
                    .testTag("devicon_picker_dialog")
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Header
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 18.dp, end = 8.dp, top = 12.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Folder,
                                    contentDescription = null,
                                    tint = FluentFolderGold,
                                    modifier = Modifier.size(28.dp)
                                )
                                if (chosenItem != null) {
                                    FolderDeviconBadgeOverlay(
                                        badgeItem = chosenItem,
                                        badgeSize = 18.dp,
                                        modifier = Modifier
                                            .align(Alignment.BottomEnd)
                                            .padding(2.dp)
                                    )
                                }
                            }
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = if (targetFolderEntry != null) {
                                        "Choose Folder Icon"
                                    } else {
                                        "Devicon Library (${allIcons.size} Icons)"
                                    },
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = if (targetFolderEntry != null) {
                                        "Folder: ${targetFolderEntry.name}"
                                    } else {
                                        "All ${allIcons.size} bundled offline SVG icons"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("devicon_picker_close_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close Devicon Picker"
                            )
                        }
                    }

                    // Search Bar
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        singleLine = true,
                        leadingIcon = {
                            Icon(Icons.Default.Search, contentDescription = null)
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(
                                    onClick = { searchQuery = "" },
                                    modifier = Modifier.minimumInteractiveComponentSize()
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = "Clear search")
                                }
                            }
                        },
                        placeholder = {
                            Text("Search ${allIcons.size} icons (e.g. kotlin, docker, react, rust)")
                        },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .testTag("devicon_search_input")
                    )

                    Spacer(Modifier.height(10.dp))

                    // Category Filter Chips
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        DeviconCategory.entries.forEach { cat ->
                            val selected = selectedCategory == cat
                            FilterChip(
                                selected = selected,
                                onClick = { selectedCategory = cat },
                                leadingIcon = if (selected) {
                                    {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                } else null,
                                label = { Text(cat.label) },
                                modifier = Modifier.testTag("devicon_category_${cat.name.lowercase()}")
                            )
                        }
                    }

                    Spacer(Modifier.height(6.dp))

                    // Results Count
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 18.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Showing ${filteredIcons.size} of ${allIcons.size} icons",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (chosenItem != null) {
                            Text(
                                text = "${chosenItem.displayName} • ${chosenItem.variant}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                    // Adaptive Grid of all 657 Devicon SVGs
                    if (filteredIcons.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "No matching Devicon icons",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = "Try another keyword or clear category filters",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(minSize = 88.dp),
                            contentPadding = PaddingValues(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .testTag("devicon_picker_grid")
                        ) {
                            items(filteredIcons, key = { it.id }) { item ->
                                val isSelected = item.id == chosenIconId
                                Surface(
                                    color = if (isSelected) {
                                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f)
                                    } else {
                                        MaterialTheme.colorScheme.surfaceContainerLow
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(
                                        width = if (isSelected) 2.dp else 1.dp,
                                        color = if (isSelected) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                                        }
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable { chosenIconId = item.id }
                                        .testTag("devicon_grid_item_${item.id}")
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 6.dp, vertical = 10.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        DeviconAssetIcon(
                                            item = item,
                                            size = 38.dp,
                                            contentDescription = item.displayName
                                        )
                                        Spacer(Modifier.height(6.dp))
                                        Text(
                                            text = item.displayName,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            color = if (isSelected) {
                                                MaterialTheme.colorScheme.onPrimaryContainer
                                            } else {
                                                MaterialTheme.colorScheme.onSurface
                                            },
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                    // Bottom Metadata & Action Strip
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (chosenItem != null) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                DeviconAssetIcon(
                                    item = chosenItem,
                                    size = 30.dp,
                                    contentDescription = chosenItem.displayName
                                )
                                Spacer(Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "${chosenItem.displayName} (${chosenItem.id})",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    val tagSummary = chosenItem.tags.take(4).joinToString(" • ")
                                        .ifBlank { chosenItem.category.label }
                                    Text(
                                        text = "${chosenItem.category.label} • $tagSummary",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                val parsedColor = runCatching {
                                    Color(android.graphics.Color.parseColor(chosenItem.colorHex))
                                }.getOrDefault(MaterialTheme.colorScheme.primary)
                                Surface(
                                    color = parsedColor,
                                    shape = CircleShape,
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                                    modifier = Modifier.size(16.dp)
                                ) {}
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (targetFolderEntry != null && !currentCustomIconId.isNullOrBlank()) {
                                OutlinedButton(
                                    onClick = {
                                        onClearCustomIcon()
                                        onDismiss()
                                    },
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .testTag("devicon_reset_default_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.RestartAlt,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text("Reset Default")
                                }
                            }
                            if (targetFolderEntry != null) {
                                Button(
                                    onClick = {
                                        chosenItem?.let {
                                            onSelectIcon(it.id)
                                            onDismiss()
                                        }
                                    },
                                    enabled = chosenItem != null,
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .testTag("devicon_apply_icon_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text("Apply Icon")
                                }
                            } else {
                                Button(
                                    onClick = onDismiss,
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .testTag("devicon_done_browse_button")
                                ) {
                                    Text("Done")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
