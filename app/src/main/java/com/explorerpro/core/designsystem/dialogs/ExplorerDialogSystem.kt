package com.explorerpro.core.designsystem.dialogs

import android.os.SystemClock
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.explorerpro.core.designsystem.progress.ExplorerProgressIndicator
import com.explorerpro.core.designsystem.progress.ProgressLabelPosition
import com.explorerpro.core.filesystem.FormatDetectorAndMetadataEngine
import com.explorerpro.domain.model.DialogVisualStyle
import com.explorerpro.domain.model.ExplorerInputValidator
import com.explorerpro.domain.model.FileOperationTask
import com.explorerpro.domain.model.InputValidationResult
import com.explorerpro.domain.model.OperationStatus
import com.explorerpro.ui.theme.FluentAmber400
import com.explorerpro.ui.theme.FluentEmerald400
import com.explorerpro.ui.theme.FluentRose400

/**
 * Prevents rapid repeated submissions on dialog action buttons.
 */
@Composable
fun rememberDialogSubmitGuard(debounceMs: Long = 450L): (() -> Unit) -> Unit {
    var lastInvokeMs by remember { mutableLongStateOf(0L) }
    return remember(debounceMs) {
        { block: () -> Unit ->
            val now = SystemClock.elapsedRealtime()
            if (now - lastInvokeMs >= debounceMs) {
                lastInvokeMs = now
                block()
            }
        }
    }
}

/**
 * 1. EXPLORER ALERT DIALOG
 * Used for critical warnings, permission errors, unsupported operations, storage access failures,
 * operation errors, and security warnings.
 */
@Composable
fun ExplorerAlertDialog(
    title: String?,
    supportingText: String?,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = Icons.Default.Warning,
    iconTint: Color = MaterialTheme.colorScheme.primary,
    confirmLabel: String = "OK",
    dismissLabel: String? = "Cancel",
    neutralLabel: String? = null,
    onNeutralClick: (() -> Unit)? = null,
    isDestructive: Boolean = false,
    visualStyle: DialogVisualStyle = DialogVisualStyle.STANDARD,
    dismissOnBackPress: Boolean = true,
    dismissOnClickOutside: Boolean = true,
    customContent: (@Composable () -> Unit)? = null
) {
    val guard = rememberDialogSubmitGuard()
    val cornerRadius: Dp = when (visualStyle) {
        DialogVisualStyle.COMPACT -> 18.dp
        DialogVisualStyle.CENTERED -> 26.dp
        else -> 24.dp
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = dismissOnBackPress,
            dismissOnClickOutside = dismissOnClickOutside
        ),
        shape = RoundedCornerShape(cornerRadius),
        icon = if (icon != null) {
            {
                Surface(
                    color = (if (isDestructive) MaterialTheme.colorScheme.error else iconTint).copy(alpha = 0.14f),
                    shape = CircleShape,
                    modifier = Modifier.size(if (visualStyle == DialogVisualStyle.COMPACT) 40.dp else 48.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = icon,
                            contentDescription = title ?: "Alert icon",
                            tint = if (isDestructive) MaterialTheme.colorScheme.error else iconTint,
                            modifier = Modifier.size(if (visualStyle == DialogVisualStyle.COMPACT) 20.dp else 24.dp)
                        )
                    }
                }
            }
        } else null,
        title = if (!title.isNullOrBlank()) {
            {
                Text(
                    text = title,
                    style = if (visualStyle == DialogVisualStyle.COMPACT) {
                        MaterialTheme.typography.titleMedium
                    } else {
                        MaterialTheme.typography.titleLarge
                    },
                    fontWeight = FontWeight.SemiBold,
                    textAlign = if (visualStyle == DialogVisualStyle.CENTERED || icon != null) {
                        TextAlign.Center
                    } else {
                        TextAlign.Start
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { heading() }
                )
            }
        } else null,
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 380.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (!supportingText.isNullOrBlank()) {
                    Text(
                        text = supportingText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = if (visualStyle == DialogVisualStyle.CENTERED) TextAlign.Center else TextAlign.Start,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                customContent?.invoke()
            }
        },
        confirmButton = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (neutralLabel != null && onNeutralClick != null) {
                    TextButton(
                        onClick = { guard { onNeutralClick() } },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("dialog_neutral_button")
                    ) {
                        Text(neutralLabel)
                    }
                }
                if (dismissLabel != null) {
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("dialog_dismiss_button")
                    ) {
                        Text(dismissLabel)
                    }
                }
                Button(
                    onClick = { guard { onConfirm() } },
                    colors = if (isDestructive) {
                        ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError
                        )
                    } else {
                        ButtonDefaults.buttonColors()
                    },
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("dialog_confirm_button")
                ) {
                    Text(confirmLabel)
                }
            }
        },
        modifier = modifier.testTag("explorer_alert_dialog")
    )
}

/**
 * 2. EXPLORER CONFIRMATION DIALOG
 * Used for Delete, Permanent Delete, Overwrite, Replace, Move, Batch Rename, Archive Creation,
 * Archive Extraction, Empty Recycle Bin, Clear Operation History, and Permission-related actions.
 * Never executes destructive actions when dismissed.
 */
@Composable
fun ExplorerConfirmationDialog(
    title: String,
    consequenceMessage: String,
    confirmLabel: String,
    onConfirm: (rememberChoice: Boolean, secondaryOptionChecked: Boolean) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    dismissLabel: String = "Cancel",
    icon: ImageVector = Icons.Default.Warning,
    isDestructive: Boolean = false,
    secondaryOptionLabel: String? = null,
    initialSecondaryOptionChecked: Boolean = false,
    rememberChoiceLabel: String? = null,
    requireRiskyConfirmationCheckbox: Boolean = false,
    riskyConfirmationLabel: String = "I understand this action cannot be undone",
    summaryBadgeText: String? = null
) {
    var rememberChoice by rememberSaveable { mutableStateOf(false) }
    var secondaryOptionChecked by rememberSaveable { mutableStateOf(initialSecondaryOptionChecked) }
    var riskyConfirmed by rememberSaveable { mutableStateOf(!requireRiskyConfirmationCheckbox) }
    val guard = rememberDialogSubmitGuard()

    val effectiveDestructive = isDestructive || secondaryOptionChecked

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(24.dp),
        icon = {
            Surface(
                color = (if (effectiveDestructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                    .copy(alpha = 0.15f),
                shape = CircleShape,
                modifier = Modifier.size(48.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = title,
                        tint = if (effectiveDestructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        },
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { heading() }
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (!summaryBadgeText.isNullOrBlank()) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = summaryBadgeText,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                        )
                    }
                }

                Text(
                    text = consequenceMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (secondaryOptionLabel != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { secondaryOptionChecked = !secondaryOptionChecked }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = secondaryOptionChecked,
                            onCheckedChange = { secondaryOptionChecked = it },
                            modifier = Modifier.testTag("confirmation_secondary_checkbox")
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = secondaryOptionLabel,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = if (secondaryOptionChecked) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            }
                        )
                    }
                }

                if (requireRiskyConfirmationCheckbox || secondaryOptionChecked) {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.45f),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { riskyConfirmed = !riskyConfirmed }
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = riskyConfirmed,
                                onCheckedChange = { riskyConfirmed = it },
                                modifier = Modifier.testTag("confirmation_risky_checkbox")
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = riskyConfirmationLabel,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }

                if (rememberChoiceLabel != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { rememberChoice = !rememberChoice }
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = rememberChoice,
                            onCheckedChange = { rememberChoice = it },
                            modifier = Modifier.testTag("confirmation_remember_checkbox")
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = rememberChoiceLabel,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        },
        confirmButton = {
            val isConfirmAllowed = if (requireRiskyConfirmationCheckbox || secondaryOptionChecked) {
                riskyConfirmed
            } else {
                true
            }
            Button(
                onClick = { guard { onConfirm(rememberChoice, secondaryOptionChecked) } },
                enabled = isConfirmAllowed,
                colors = if (effectiveDestructive) {
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                } else {
                    ButtonDefaults.buttonColors()
                },
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("confirmation_confirm_button")
            ) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("confirmation_cancel_button")
            ) {
                Text(dismissLabel)
            }
        },
        modifier = modifier.testTag("explorer_confirmation_dialog")
    )
}

/**
 * 3. EXPLORER SIMPLE DIALOG
 * Clean list of action rows with icon, label, and optional supporting description.
 */
data class SimpleDialogItem(
    val id: String,
    val title: String,
    val description: String? = null,
    val icon: ImageVector? = null,
    val badgeText: String? = null,
    val isDestructive: Boolean = false
)

@Composable
fun ExplorerSimpleDialog(
    title: String,
    items: List<SimpleDialogItem>,
    onItemSelected: (SimpleDialogItem) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    supportingText: String? = null
) {
    val guard = rememberDialogSubmitGuard()
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(24.dp),
        title = {
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.semantics { heading() }
                )
                if (!supportingText.isNullOrBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = supportingText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        text = {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(items, key = { it.id }) { item ->
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                guard {
                                    onItemSelected(item)
                                }
                            }
                            .testTag("simple_dialog_item_${item.id}")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (item.icon != null) {
                                Icon(
                                    imageVector = item.icon,
                                    contentDescription = null,
                                    tint = if (item.isDestructive) {
                                        MaterialTheme.colorScheme.error
                                    } else {
                                        MaterialTheme.colorScheme.primary
                                    },
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(Modifier.width(12.dp))
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.title,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium,
                                    color = if (item.isDestructive) {
                                        MaterialTheme.colorScheme.error
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    }
                                )
                                if (!item.description.isNullOrBlank()) {
                                    Text(
                                        text = item.description,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            if (!item.badgeText.isNullOrBlank()) {
                                Surface(
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        text = item.badgeText,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.minimumInteractiveComponentSize()
            ) {
                Text("Close")
            }
        },
        modifier = modifier.testTag("explorer_simple_dialog")
    )
}

/**
 * 4. EXPLORER CHOICE DIALOG (Single Selection)
 * Supports radio buttons, icons, supporting descriptions, live search for large lists,
 * current selection indication, and Apply / Cancel actions.
 */
data class DialogChoiceOption<T>(
    val value: T,
    val label: String,
    val description: String? = null,
    val icon: ImageVector? = null,
    val badge: String? = null
)

@Composable
fun <T> ExplorerChoiceDialog(
    title: String,
    options: List<DialogChoiceOption<T>>,
    selectedValue: T,
    onApplySelection: (T) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
    searchable: Boolean = options.size > 6,
    instantApplyOnTap: Boolean = false,
    applyLabel: String = "Apply"
) {
    var pendingSelection by remember(selectedValue) { mutableStateOf(selectedValue) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    val guard = rememberDialogSubmitGuard()

    val filteredOptions = remember(options, searchQuery) {
        if (searchQuery.isBlank()) {
            options
        } else {
            val q = searchQuery.trim().lowercase()
            options.filter {
                it.label.lowercase().contains(q) ||
                    (it.description?.lowercase()?.contains(q) == true)
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(24.dp),
        title = {
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.semantics { heading() }
                )
                if (!supportingText.isNullOrBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = supportingText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (searchable) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search options…") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        trailingIcon = if (searchQuery.isNotEmpty()) {
                            {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Default.Close, contentDescription = "Clear search")
                                }
                            }
                        } else null,
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("choice_dialog_search_input")
                    )
                }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 320.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(filteredOptions) { option ->
                        val isSelected = option.value == pendingSelection
                        Surface(
                            color = if (isSelected) {
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                            } else {
                                Color.Transparent
                            },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    pendingSelection = option.value
                                    if (instantApplyOnTap) {
                                        guard { onApplySelection(option.value) }
                                    }
                                }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = isSelected,
                                    onClick = {
                                        pendingSelection = option.value
                                        if (instantApplyOnTap) {
                                            guard { onApplySelection(option.value) }
                                        }
                                    }
                                )
                                if (option.icon != null) {
                                    Spacer(Modifier.width(4.dp))
                                    Icon(
                                        imageVector = option.icon,
                                        contentDescription = null,
                                        tint = if (isSelected) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Spacer(Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = option.label,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    if (!option.description.isNullOrBlank()) {
                                        Text(
                                            text = option.description,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                if (!option.badge.isNullOrBlank()) {
                                    Surface(
                                        color = MaterialTheme.colorScheme.secondaryContainer,
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(
                                            text = option.badge,
                                            style = MaterialTheme.typography.labelSmall,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (!instantApplyOnTap) {
                Button(
                    onClick = { guard { onApplySelection(pendingSelection) } },
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("choice_dialog_apply_button")
                ) {
                    Text(applyLabel)
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("choice_dialog_cancel_button")
            ) {
                Text("Cancel")
            }
        },
        modifier = modifier.testTag("explorer_choice_dialog")
    )
}

/**
 * 5. EXPLORER MULTI-CHOICE DIALOG (Multiple Selection with Checkboxes)
 */
@Composable
fun <T> ExplorerMultiChoiceDialog(
    title: String,
    options: List<DialogChoiceOption<T>>,
    initialSelectedValues: Set<T>,
    onApplySelections: (Set<T>) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
    searchable: Boolean = options.size > 6,
    applyLabel: String = "Apply"
) {
    var selectedSet by remember(initialSelectedValues) { mutableStateOf(initialSelectedValues) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    val guard = rememberDialogSubmitGuard()

    val filteredOptions = remember(options, searchQuery) {
        if (searchQuery.isBlank()) {
            options
        } else {
            val q = searchQuery.trim().lowercase()
            options.filter {
                it.label.lowercase().contains(q) ||
                    (it.description?.lowercase()?.contains(q) == true)
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(24.dp),
        title = {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .weight(1f)
                            .semantics { heading() }
                    )
                    TextButton(
                        onClick = {
                            selectedSet = if (selectedSet.size == options.size) {
                                emptySet()
                            } else {
                                options.map { it.value }.toSet()
                            }
                        }
                    ) {
                        Text(if (selectedSet.size == options.size) "Clear All" else "Select All")
                    }
                }
                if (!supportingText.isNullOrBlank()) {
                    Text(
                        text = supportingText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (searchable) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Filter choices…") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 320.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(filteredOptions) { option ->
                        val isChecked = selectedSet.contains(option.value)
                        Surface(
                            color = if (isChecked) {
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                            } else {
                                Color.Transparent
                            },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    selectedSet = if (isChecked) {
                                        selectedSet - option.value
                                    } else {
                                        selectedSet + option.value
                                    }
                                }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = isChecked,
                                    onCheckedChange = { checked ->
                                        selectedSet = if (checked) {
                                            selectedSet + option.value
                                        } else {
                                            selectedSet - option.value
                                        }
                                    }
                                )
                                if (option.icon != null) {
                                    Spacer(Modifier.width(4.dp))
                                    Icon(
                                        imageVector = option.icon,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Spacer(Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = option.label,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = if (isChecked) FontWeight.SemiBold else FontWeight.Normal
                                    )
                                    if (!option.description.isNullOrBlank()) {
                                        Text(
                                            text = option.description,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { guard { onApplySelections(selectedSet) } },
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("multi_choice_apply_button")
            ) {
                Text("$applyLabel (${selectedSet.size})")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.minimumInteractiveComponentSize()
            ) {
                Text("Cancel")
            }
        },
        modifier = modifier.testTag("explorer_multi_choice_dialog")
    )
}

/**
 * 6. EXPLORER INPUT DIALOG
 * Reusable single-line or multi-line input dialog with live validation, character limits,
 * helper text, error messages, keyboard configuration, auto-focus, and Save/Cancel actions.
 * Strictly blocks unsafe or invalid file/folder paths.
 */
@Composable
fun ExplorerInputDialog(
    title: String,
    label: String,
    onConfirm: (primaryValue: String, secondaryValue: String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    initialValue: String = "",
    secondaryLabel: String? = null,
    initialSecondaryValue: String = "",
    helperText: String? = null,
    confirmLabel: String = "Save",
    dismissLabel: String = "Cancel",
    singleLine: Boolean = true,
    maxLength: Int = 255,
    existingSiblingNames: Set<String> = emptySet(),
    originalName: String? = null,
    validateAsFileName: Boolean = true,
    customValidator: ((String) -> InputValidationResult)? = null,
    autoFocus: Boolean = true,
    keyboardType: KeyboardType = KeyboardType.Text
) {
    var primaryText by rememberSaveable(initialValue) { mutableStateOf(initialValue) }
    var secondaryText by rememberSaveable(initialSecondaryValue) { mutableStateOf(initialSecondaryValue) }
    var hasUserInteracted by rememberSaveable { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val guard = rememberDialogSubmitGuard()

    val validation = remember(primaryText, existingSiblingNames, originalName, maxLength, validateAsFileName) {
        when {
            customValidator != null -> customValidator(primaryText)
            validateAsFileName -> ExplorerInputValidator.validateFileOrFolderName(
                rawInput = primaryText,
                maxLength = maxLength,
                existingSiblingNames = existingSiblingNames,
                originalName = originalName
            )
            else -> {
                val trimmed = primaryText.trim()
                InputValidationResult(
                    isValid = trimmed.isNotEmpty() && trimmed.length <= maxLength,
                    sanitizedValue = trimmed,
                    errorMessage = if (trimmed.isEmpty()) "Value cannot be empty." else null
                )
            }
        }
    }

    LaunchedEffect(autoFocus) {
        if (autoFocus) {
            runCatching { focusRequester.requestFocus() }
        }
    }

    val showError = hasUserInteracted && !validation.isValid && validation.errorMessage != null

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(24.dp),
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.semantics { heading() }
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = primaryText,
                    onValueChange = { updated ->
                        hasUserInteracted = true
                        if (updated.length <= maxLength + 16) {
                            primaryText = updated
                        }
                    },
                    label = { Text(label) },
                    singleLine = singleLine,
                    isError = showError,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = keyboardType,
                        imeAction = if (secondaryLabel != null) ImeAction.Next else ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            if (validation.isValid) {
                                guard { onConfirm(validation.sanitizedValue, secondaryText) }
                            } else {
                                hasUserInteracted = true
                            }
                        }
                    ),
                    supportingText = {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = when {
                                    showError -> validation.errorMessage.orEmpty()
                                    !helperText.isNullOrBlank() -> helperText
                                    else -> "Valid POSIX / SAF name"
                                },
                                color = if (showError) {
                                    MaterialTheme.colorScheme.error
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .semantics {
                                        if (showError) liveRegion = LiveRegionMode.Polite
                                    }
                            )
                            Text(
                                text = "${primaryText.trim().length}/$maxLength",
                                color = if (primaryText.trim().length > maxLength) {
                                    MaterialTheme.colorScheme.error
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                        .testTag("input_dialog_primary_field")
                )

                if (secondaryLabel != null) {
                    OutlinedTextField(
                        value = secondaryText,
                        onValueChange = { secondaryText = it },
                        label = { Text(secondaryLabel) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(130.dp)
                            .testTag("input_dialog_secondary_field")
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    hasUserInteracted = true
                    if (validation.isValid) {
                        guard { onConfirm(validation.sanitizedValue, secondaryText) }
                    }
                },
                enabled = validation.isValid,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("input_dialog_confirm_button")
            ) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("input_dialog_cancel_button")
            ) {
                Text(dismissLabel)
            }
        },
        modifier = modifier.testTag("explorer_input_dialog")
    )
}

/**
 * 7. EXPLORER PROGRESS DIALOG
 * Integrates the Universal Progress & Operation Management Add-on (`ExplorerProgressIndicator`).
 * Dismissing the dialog NEVER cancels the running operation; it continues in the background.
 */
@Composable
fun ExplorerProgressDialog(
    task: FileOperationTask,
    onPause: (String) -> Unit,
    onResume: (String) -> Unit,
    onCancel: (String) -> Unit,
    onRetry: (String) -> Unit,
    onOpenOperationCenter: () -> Unit,
    onDismissToBackground: () -> Unit,
    modifier: Modifier = Modifier
) {
    var detailsExpanded by rememberSaveable { mutableStateOf(true) }
    var showCancelConfirm by rememberSaveable { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismissToBackground,
        shape = RoundedCornerShape(24.dp),
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = task.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${task.type.label} • ${task.status.label}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                IconButton(onClick = { detailsExpanded = !detailsExpanded }) {
                    Icon(
                        imageVector = if (detailsExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (detailsExpanded) "Collapse details" else "Expand details"
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ExplorerProgressIndicator(
                    min = task.minValue,
                    max = task.maxValue,
                    value = task.progressValue,
                    labelPosition = ProgressLabelPosition.TOP_FLOATING,
                    showPercentage = !task.indeterminate,
                    showStatus = true,
                    animated = true,
                    trackHeight = 10.dp,
                    indeterminate = task.indeterminate,
                    completed = task.status == OperationStatus.COMPLETED,
                    status = task.status,
                    operationLabel = task.type.label,
                    statusDescription = task.currentItemName.ifEmpty { task.status.label }
                )

                AnimatedVisibility(visible = detailsExpanded) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            if (task.currentItemName.isNotEmpty()) {
                                ProgressMetricRow("Current Item", task.currentItemName)
                            }
                            ProgressMetricRow(
                                "Items Processed",
                                "${task.processedItems} / ${task.totalItems}"
                            )
                            if (task.totalBytes > 0L) {
                                ProgressMetricRow(
                                    "Transferred Size",
                                    "${FormatDetectorAndMetadataEngine.formatBytes(task.transferredBytes)} / ${FormatDetectorAndMetadataEngine.formatBytes(task.totalBytes)}"
                                )
                            }
                            if (task.speedBytesPerSec > 0L && task.status == OperationStatus.RUNNING) {
                                ProgressMetricRow(
                                    "Transfer Speed",
                                    "${FormatDetectorAndMetadataEngine.formatBytes(task.speedBytesPerSec)}/s"
                                )
                            }
                            if (task.estimatedRemainingSeconds != null && task.status == OperationStatus.RUNNING) {
                                ProgressMetricRow(
                                    "Estimated Remaining",
                                    formatEtaSeconds(task.estimatedRemainingSeconds)
                                )
                            }
                            if (task.destinationPath.isNotEmpty()) {
                                ProgressMetricRow("Destination", task.destinationPath)
                            }
                            if (!task.errorDetail.isNullOrBlank()) {
                                Text(
                                    text = "Error: ${task.errorDetail}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }

                // Operational Controls Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (task.canPause && task.status == OperationStatus.RUNNING) {
                        OutlinedButton(
                            onClick = { onPause(task.id) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Pause, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Pause")
                        }
                    } else if (task.status == OperationStatus.PAUSED) {
                        FilledTonalButton(
                            onClick = { onResume(task.id) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Resume")
                        }
                    }

                    if (task.canCancel && task.isActiveOrPaused) {
                        OutlinedButton(
                            onClick = { showCancelConfirm = true },
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            ),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Cancel")
                        }
                    }

                    if (task.canRetry && (task.status == OperationStatus.FAILED || task.status == OperationStatus.CANCELLED)) {
                        Button(
                            onClick = { onRetry(task.id) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Retry")
                        }
                    }
                }

                if (showCancelConfirm && task.isActiveOrPaused) {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Abort this operation?",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = { showCancelConfirm = false }) {
                                Text("Keep Running")
                            }
                            Button(
                                onClick = {
                                    showCancelConfirm = false
                                    onCancel(task.id)
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.error,
                                    contentColor = MaterialTheme.colorScheme.onError
                                )
                            ) {
                                Text("Stop Now")
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismissToBackground,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("progress_dialog_background_button")
            ) {
                Text(if (task.isActiveOrPaused) "Continue in Background" else "Done")
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    onDismissToBackground()
                    onOpenOperationCenter()
                },
                modifier = Modifier.minimumInteractiveComponentSize()
            ) {
                Text("Operation Center")
            }
        },
        modifier = modifier.testTag("explorer_progress_dialog")
    )
}

@Composable
private fun ProgressMetricRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private fun formatEtaSeconds(seconds: Long): String {
    if (seconds < 60L) return "${seconds}s"
    val mins = seconds / 60L
    val remSecs = seconds % 60L
    return if (mins < 60L) "${mins}m ${remSecs}s" else "${mins / 60L}h ${mins % 60L}m"
}

/**
 * 8. EXPLORER ERROR DIALOG
 * Displays structured operation, permission, or storage errors with technical diagnostics and retry support.
 */
@Composable
fun ExplorerErrorDialog(
    title: String,
    message: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    technicalDetails: String? = null,
    onCopyDetails: ((String) -> Unit)? = null,
    onRetry: (() -> Unit)? = null
) {
    var showTechnical by rememberSaveable { mutableStateOf(false) }
    val guard = rememberDialogSubmitGuard()

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(24.dp),
        icon = {
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                shape = CircleShape,
                modifier = Modifier.size(48.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.ErrorOutline,
                        contentDescription = "Error",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }
        },
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { heading() }
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (!technicalDetails.isNullOrBlank()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = { showTechnical = !showTechnical }) {
                            Text(if (showTechnical) "Hide Diagnostics" else "Show Diagnostics")
                        }
                        if (onCopyDetails != null) {
                            IconButton(onClick = { onCopyDetails("$title\n$message\n$technicalDetails") }) {
                                Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = "Copy error diagnostics",
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    AnimatedVisibility(visible = showTechnical) {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = technicalDetails,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (onRetry != null) {
                Button(
                    onClick = {
                        guard {
                            onDismiss()
                            onRetry()
                        }
                    },
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("error_dialog_retry_button")
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Retry")
                }
            } else {
                Button(
                    onClick = onDismiss,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("error_dialog_ok_button")
                ) {
                    Text("Dismiss")
                }
            }
        },
        dismissButton = if (onRetry != null) {
            {
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Text("Close")
                }
            }
        } else null,
        modifier = modifier.testTag("explorer_error_dialog")
    )
}

/**
 * 9. EXPLORER SUCCESS DIALOG
 * Displays completion summary for multi-step or archive/verification workflows.
 */
@Composable
fun ExplorerSuccessDialog(
    title: String,
    message: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    destinationPath: String? = null,
    onOpenDestination: ((String) -> Unit)? = null
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(24.dp),
        icon = {
            Surface(
                color = FluentEmerald400.copy(alpha = 0.16f),
                shape = CircleShape,
                modifier = Modifier.size(48.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Success",
                        tint = FluentEmerald400,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }
        },
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { heading() }
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                if (!destinationPath.isNullOrBlank()) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = destinationPath,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!destinationPath.isNullOrBlank() && onOpenDestination != null) {
                    FilledTonalButton(
                        onClick = {
                            onDismiss()
                            onOpenDestination(destinationPath)
                        },
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Open Folder")
                    }
                }
                Button(
                    onClick = onDismiss,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("success_dialog_done_button")
                ) {
                    Text("Done")
                }
            }
        },
        modifier = modifier.testTag("explorer_success_dialog")
    )
}

/**
 * 12. EXPLORER FULL-SCREEN DIALOG
 * Edge-to-edge full-screen workflow container for complex multi-step or preview-heavy operations
 * (Batch Rename, Create Archive, Archive Inspector, Folder Comparison, Advanced Search).
 */
@Composable
fun ExplorerFullScreenDialog(
    title: String,
    subtitle: String? = null,
    onDismiss: () -> Unit,
    primaryActionLabel: String? = null,
    onPrimaryAction: (() -> Unit)? = null,
    primaryActionEnabled: Boolean = true,
    secondaryActionLabel: String? = null,
    onSecondaryAction: (() -> Unit)? = null,
    currentStep: Int? = null,
    totalSteps: Int? = null,
    stepLabels: List<String> = emptyList(),
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val guard = rememberDialogSubmitGuard()

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true
        )
    ) {
        Surface(
            color = MaterialTheme.colorScheme.background,
            modifier = modifier
                .fillMaxSize()
                .testTag("explorer_full_screen_dialog")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .systemBarsPadding()
            ) {
                // Native Top App Bar
                Surface(
                    tonalElevation = 3.dp,
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = onDismiss,
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("full_screen_dialog_close_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Close $title"
                                )
                            }
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 6.dp)
                            ) {
                                Text(
                                    text = title,
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.semantics { heading() }
                                )
                                if (!subtitle.isNullOrBlank()) {
                                    Text(
                                        text = subtitle,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }

                        // Optional Multi-Step Indicator
                        if (currentStep != null && totalSteps != null && totalSteps > 1) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                for (stepIdx in 1..totalSteps) {
                                    val isDoneOrCurrent = stepIdx <= currentStep
                                    val stepName = stepLabels.getOrNull(stepIdx - 1) ?: "Step $stepIdx"
                                    Row(
                                        modifier = Modifier.weight(1f),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Surface(
                                            color = if (isDoneOrCurrent) {
                                                MaterialTheme.colorScheme.primary
                                            } else {
                                                MaterialTheme.colorScheme.surfaceVariant
                                            },
                                            shape = CircleShape,
                                            modifier = Modifier.size(22.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Text(
                                                    text = stepIdx.toString(),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = if (isDoneOrCurrent) {
                                                        MaterialTheme.colorScheme.onPrimary
                                                    } else {
                                                        MaterialTheme.colorScheme.onSurfaceVariant
                                                    }
                                                )
                                            }
                                        }
                                        Text(
                                            text = stepName,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = if (stepIdx == currentStep) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isDoneOrCurrent) {
                                                MaterialTheme.colorScheme.primary
                                            } else {
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                            },
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    }
                }

                // Main Body Content
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    contentAlignment = Alignment.TopCenter
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .widthIn(max = 760.dp)
                    ) {
                        content()
                    }
                }

                // Sticky Bottom Action Bar
                if (primaryActionLabel != null || secondaryActionLabel != null) {
                    Surface(
                        tonalElevation = 4.dp,
                        color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TextButton(
                                    onClick = onDismiss,
                                    modifier = Modifier.minimumInteractiveComponentSize()
                                ) {
                                    Text("Cancel")
                                }
                                if (secondaryActionLabel != null && onSecondaryAction != null) {
                                    OutlinedButton(
                                        onClick = { guard { onSecondaryAction() } },
                                        modifier = Modifier
                                            .minimumInteractiveComponentSize()
                                            .testTag("full_screen_dialog_secondary_button")
                                    ) {
                                        Text(secondaryActionLabel)
                                    }
                                }
                                if (primaryActionLabel != null && onPrimaryAction != null) {
                                    Button(
                                        onClick = { guard { onPrimaryAction() } },
                                        enabled = primaryActionEnabled,
                                        modifier = Modifier
                                            .minimumInteractiveComponentSize()
                                            .testTag("full_screen_dialog_primary_button")
                                    ) {
                                        Text(primaryActionLabel)
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

/**
 * 13. EXPLORER MODAL BOTTOM SHEET
 * Contextual bottom sheet with drag handle, header metadata summary, scrollable content,
 * navigation-bar insets, and accessible action rows.
 */
data class BottomSheetActionItem(
    val id: String,
    val title: String,
    val subtitle: String? = null,
    val icon: ImageVector,
    val badgeText: String? = null,
    val isDestructive: Boolean = false,
    val onClick: () -> Unit
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExplorerModalBottomSheet(
    title: String,
    subtitle: String? = null,
    headerIcon: ImageVector? = null,
    metadataBadges: List<String> = emptyList(),
    actions: List<BottomSheetActionItem> = emptyList(),
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    customContent: (@Composable () -> Unit)? = null
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    val guard = rememberDialogSubmitGuard()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        modifier = modifier.testTag("explorer_modal_bottom_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Sheet Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (headerIcon != null) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.size(44.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = headerIcon,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.semantics { heading() }
                    )
                    if (!subtitle.isNullOrBlank()) {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close bottom sheet")
                }
            }

            if (metadataBadges.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    metadataBadges.take(4).forEach { chip ->
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = chip,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            if (customContent != null) {
                customContent()
            }

            if (actions.isNotEmpty()) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 380.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(actions, key = { it.id }) { action ->
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    guard {
                                        onDismiss()
                                        action.onClick()
                                    }
                                }
                                .testTag("bottom_sheet_action_${action.id}")
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = action.icon,
                                    contentDescription = null,
                                    tint = if (action.isDestructive) {
                                        MaterialTheme.colorScheme.error
                                    } else {
                                        MaterialTheme.colorScheme.primary
                                    },
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = action.title,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium,
                                        color = if (action.isDestructive) {
                                            MaterialTheme.colorScheme.error
                                        } else {
                                            MaterialTheme.colorScheme.onSurface
                                        }
                                    )
                                    if (!action.subtitle.isNullOrBlank()) {
                                        Text(
                                            text = action.subtitle,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                if (!action.badgeText.isNullOrBlank()) {
                                    Surface(
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(
                                            text = action.badgeText,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
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
