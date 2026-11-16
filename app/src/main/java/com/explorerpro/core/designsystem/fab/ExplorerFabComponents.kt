package com.explorerpro.core.designsystem.fab

import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.explorerpro.core.domain.actions.QuickAction
import com.explorerpro.core.domain.actions.ResolvedFabModel
import com.explorerpro.domain.model.FabCustomizationSettings
import com.explorerpro.domain.model.FabStyleMode
import com.explorerpro.domain.model.FabThemeMode
import com.explorerpro.domain.model.SpeedDialDirection

/**
 * Resolves container and content colors for the Explorer FAB system according to [FabThemeMode].
 */
@Composable
fun resolveFabColors(
    themeMode: FabThemeMode,
    isSelectionMode: Boolean = false,
    isDestructive: Boolean = false
): Pair<Color, Color> {
    if (isDestructive) {
        return MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
    }
    if (isSelectionMode) {
        return MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
    }
    return when (themeMode) {
        FabThemeMode.FOLLOW_SYSTEM -> {
            MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
        }
        FabThemeMode.DYNAMIC_MATERIAL_YOU -> {
            MaterialTheme.colorScheme.primary to MaterialTheme.colorScheme.onPrimary
        }
        FabThemeMode.CUSTOM_ACCENT -> {
            Color(0xFF00B4D8) to Color(0xFF041C24)
        }
    }
}

/**
 * A. Standard circular Floating Action Button inspired by
 * `com.google.android.material.floatingactionbutton.FloatingActionButton`.
 */
@Composable
fun ExplorerFab(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.primaryContainer,
    contentColor: Color = MaterialTheme.colorScheme.onPrimaryContainer,
    badgeText: String? = null,
    iconRotationDegrees: Float = 0f,
    animationsEnabled: Boolean = true,
    testTag: String = "explorer_standard_fab"
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed && animationsEnabled) 0.92f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "fab_press_scale"
    )
    var lastClickTimestamp by remember { mutableLongStateOf(0L) }
    val haptics = LocalHapticFeedback.current

    FloatingActionButton(
        onClick = {
            val now = SystemClock.elapsedRealtime()
            if (now - lastClickTimestamp >= 220L) {
                lastClickTimestamp = now
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            }
        },
        interactionSource = interactionSource,
        containerColor = containerColor,
        contentColor = contentColor,
        shape = CircleShape,
        elevation = FloatingActionButtonDefaults.elevation(
            defaultElevation = 6.dp,
            pressedElevation = 10.dp
        ),
        modifier = modifier
            .scale(scale)
            .sizeIn(minWidth = 56.dp, minHeight = 56.dp)
            .semantics { this.contentDescription = contentDescription }
            .testTag(testTag)
    ) {
        if (!badgeText.isNullOrBlank()) {
            BadgedBox(
                badge = {
                    Badge(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    ) {
                        Text(
                            text = badgeText,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = contentDescription,
                    modifier = Modifier
                        .size(24.dp)
                        .rotate(iconRotationDegrees)
                )
            }
        } else {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                modifier = Modifier
                    .size(24.dp)
                    .rotate(iconRotationDegrees)
            )
        }
    }
}

/**
 * B. Small contextual Floating Action Button with guaranteed 48dp minimum touch target.
 */
@Composable
fun ExplorerSmallFab(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.secondaryContainer,
    contentColor: Color = MaterialTheme.colorScheme.onSecondaryContainer,
    animationsEnabled: Boolean = true,
    testTag: String = "explorer_small_fab"
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed && animationsEnabled) 0.90f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "small_fab_press_scale"
    )
    var lastClickTimestamp by remember { mutableLongStateOf(0L) }
    val haptics = LocalHapticFeedback.current

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
            .semantics { this.contentDescription = contentDescription }
            .testTag(testTag)
    ) {
        SmallFloatingActionButton(
            onClick = {
                val now = SystemClock.elapsedRealtime()
                if (now - lastClickTimestamp >= 220L) {
                    lastClickTimestamp = now
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onClick()
                }
            },
            interactionSource = interactionSource,
            containerColor = containerColor,
            contentColor = contentColor,
            shape = CircleShape,
            elevation = FloatingActionButtonDefaults.elevation(
                defaultElevation = 4.dp,
                pressedElevation = 8.dp
            ),
            modifier = Modifier.scale(scale)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

/**
 * C. Extended Floating Action Button with dynamic expand/shrink behavior.
 */
@Composable
fun ExplorerExtendedFab(
    text: String,
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    expanded: Boolean = true,
    containerColor: Color = MaterialTheme.colorScheme.primaryContainer,
    contentColor: Color = MaterialTheme.colorScheme.onPrimaryContainer,
    badgeText: String? = null,
    iconRotationDegrees: Float = 0f,
    animationsEnabled: Boolean = true,
    testTag: String = "explorer_extended_fab"
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed && animationsEnabled) 0.94f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "extended_fab_press_scale"
    )
    var lastClickTimestamp by remember { mutableLongStateOf(0L) }
    val haptics = LocalHapticFeedback.current

    ExtendedFloatingActionButton(
        text = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = text,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!badgeText.isNullOrBlank()) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        shape = RoundedCornerShape(999.dp)
                    ) {
                        Text(
                            text = badgeText,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        },
        icon = {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                modifier = Modifier
                    .size(24.dp)
                    .rotate(iconRotationDegrees)
            )
        },
        onClick = {
            val now = SystemClock.elapsedRealtime()
            if (now - lastClickTimestamp >= 220L) {
                lastClickTimestamp = now
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            }
        },
        expanded = expanded,
        interactionSource = interactionSource,
        containerColor = containerColor,
        contentColor = contentColor,
        shape = RoundedCornerShape(18.dp),
        elevation = FloatingActionButtonDefaults.elevation(
            defaultElevation = 6.dp,
            pressedElevation = 10.dp
        ),
        modifier = modifier
            .scale(scale)
            .sizeIn(minWidth = 56.dp, minHeight = 56.dp)
            .semantics { this.contentDescription = contentDescription }
            .testTag(testTag)
    )
}

/**
 * Individual action row/chip inside [ExplorerSpeedDial].
 */
@Composable
fun ExplorerQuickActionItem(
    action: QuickAction,
    showLabel: Boolean,
    direction: SpeedDialDirection,
    themeMode: FabThemeMode,
    animationsEnabled: Boolean,
    onActionClick: (QuickAction) -> Unit,
    modifier: Modifier = Modifier
) {
    val (containerColor, contentColor) = resolveFabColors(
        themeMode = themeMode,
        isSelectionMode = action.requiresSelection,
        isDestructive = action.isDestructive
    )

    if (direction == SpeedDialDirection.UPWARD) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.End,
            modifier = modifier
                .padding(vertical = 4.dp)
                .testTag("speed_dial_item_${action.id.name.lowercase()}")
        ) {
            if (showLabel) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    tonalElevation = 4.dp,
                    shadowElevation = 4.dp,
                    modifier = Modifier
                        .widthIn(max = 230.dp)
                        .clickable(enabled = action.enabled) { onActionClick(action) }
                ) {
                    Column(
                        horizontalAlignment = Alignment.End,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = action.title,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = if (action.isDestructive) {
                                    MaterialTheme.colorScheme.error
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (!action.badgeText.isNullOrBlank()) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                    shape = RoundedCornerShape(999.dp)
                                ) {
                                    Text(
                                        text = action.badgeText,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                                    )
                                }
                            }
                        }
                        if (!action.subtitle.isNullOrBlank()) {
                            Text(
                                text = action.subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
            }

            ExplorerSmallFab(
                icon = action.icon,
                contentDescription = action.title,
                onClick = { onActionClick(action) },
                containerColor = containerColor,
                contentColor = contentColor,
                animationsEnabled = animationsEnabled,
                testTag = "speed_dial_fab_${action.id.name.lowercase()}"
            )
        }
    } else {
        // Horizontal expansion item
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = containerColor,
            contentColor = contentColor,
            tonalElevation = 6.dp,
            shadowElevation = 6.dp,
            modifier = modifier
                .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                .clickable(enabled = action.enabled) { onActionClick(action) }
                .testTag("speed_dial_item_${action.id.name.lowercase()}")
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Icon(
                    imageVector = action.icon,
                    contentDescription = action.title,
                    modifier = Modifier.size(20.dp)
                )
                if (showLabel) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = action.title,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

/**
 * D. Expandable Speed Dial FAB with dimmed scrim, tap-outside dismissal, BackHandler,
 * and destructive action confirmation dialog.
 */
@Composable
fun ExplorerSpeedDial(
    model: ResolvedFabModel,
    settings: FabCustomizationSettings,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onActionSelected: (QuickAction) -> Unit,
    modifier: Modifier = Modifier,
    bottomOffsetPadding: Int = 16,
    endOffsetPadding: Int = 16
) {
    var pendingConfirmationAction by remember { mutableStateOf<QuickAction?>(null) }

    if (expanded) {
        BackHandler {
            onExpandedChange(false)
        }
    }

    val rotationDegrees by animateFloatAsState(
        targetValue = if (expanded && settings.animationsEnabled) 135f else 0f,
        animationSpec = tween(durationMillis = if (settings.animationsEnabled) 220 else 0, easing = FastOutSlowInEasing),
        label = "speed_dial_main_icon_rotation"
    )

    val (mainContainerColor, mainContentColor) = resolveFabColors(
        themeMode = settings.themeMode,
        isSelectionMode = model.isSelectionMode
    )

    val handleActionSelection: (QuickAction) -> Unit = { action ->
        onExpandedChange(false)
        if (action.requiresConfirmation) {
            pendingConfirmationAction = action
        } else {
            onActionSelected(action)
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        // Dimmed background scrim when Speed Dial is expanded
        AnimatedVisibility(
            visible = expanded,
            enter = if (settings.animationsEnabled) fadeIn(tween(180)) else fadeIn(tween(0)),
            exit = if (settings.animationsEnabled) fadeOut(tween(150)) else fadeOut(tween(0))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.48f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        onExpandedChange(false)
                    }
                    .semantics { contentDescription = "Dismiss quick actions menu" }
                    .testTag("speed_dial_scrim")
            )
        }

        // FAB + Speed Dial Items aligned to BottomEnd
        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.Bottom,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(
                    end = endOffsetPadding.dp,
                    bottom = bottomOffsetPadding.dp,
                    start = 16.dp,
                    top = 24.dp
                )
        ) {
            // Upward Speed Dial Menu
            if (settings.speedDialDirection == SpeedDialDirection.UPWARD) {
                AnimatedVisibility(
                    visible = expanded,
                    enter = if (settings.animationsEnabled) {
                        fadeIn(tween(200)) + slideInVertically(tween(220)) { it / 3 } + scaleIn(tween(200), initialScale = 0.88f)
                    } else {
                        fadeIn(tween(0))
                    },
                    exit = if (settings.animationsEnabled) {
                        fadeOut(tween(150)) + slideOutVertically(tween(160)) { it / 3 } + scaleOut(tween(150), targetScale = 0.88f)
                    } else {
                        fadeOut(tween(0))
                    }
                ) {
                    Column(
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier
                            .heightIn(max = 420.dp)
                            .verticalScroll(rememberScrollState())
                            .padding(bottom = 12.dp)
                            .testTag("speed_dial_upward_menu")
                    ) {
                        model.speedDialActions.filter { it.visible && it.enabled }.forEach { action ->
                            ExplorerQuickActionItem(
                                action = action,
                                showLabel = settings.showSpeedDialLabels,
                                direction = SpeedDialDirection.UPWARD,
                                themeMode = settings.themeMode,
                                animationsEnabled = settings.animationsEnabled,
                                onActionClick = handleActionSelection
                            )
                        }
                    }
                }
            }

            // Bottom Row: Horizontal Speed Dial items (if HORIZONTAL) + Main Trigger FAB
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End
            ) {
                if (settings.speedDialDirection == SpeedDialDirection.HORIZONTAL) {
                    AnimatedVisibility(
                        visible = expanded,
                        enter = if (settings.animationsEnabled) {
                            fadeIn(tween(200)) + slideInHorizontally(tween(220)) { it / 2 } + expandHorizontally()
                        } else {
                            fadeIn(tween(0))
                        },
                        exit = if (settings.animationsEnabled) {
                            fadeOut(tween(150)) + slideOutHorizontally(tween(160)) { it / 2 } + shrinkHorizontally()
                        } else {
                            fadeOut(tween(0))
                        }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .weight(1f, fill = false)
                                .horizontalScroll(rememberScrollState())
                                .padding(end = 12.dp)
                                .testTag("speed_dial_horizontal_menu")
                        ) {
                            model.speedDialActions.filter { it.visible && it.enabled }.forEach { action ->
                                ExplorerQuickActionItem(
                                    action = action,
                                    showLabel = settings.showSpeedDialLabels,
                                    direction = SpeedDialDirection.HORIZONTAL,
                                    themeMode = settings.themeMode,
                                    animationsEnabled = settings.animationsEnabled,
                                    onActionClick = handleActionSelection
                                )
                            }
                        }
                    }
                }

                // Primary FAB trigger
                when (settings.fabStyleMode) {
                    FabStyleMode.SPEED_DIAL -> {
                        ExplorerExtendedFab(
                            text = if (expanded) "Close" else model.primaryTitle,
                            icon = if (expanded && !settings.animationsEnabled) Icons.Default.Close else model.primaryIcon,
                            contentDescription = if (expanded) "Close Quick Actions" else model.primaryTitle,
                            expanded = !expanded && model.primaryTitle.isNotBlank(),
                            onClick = {
                                if (model.speedDialActions.size <= 1 && model.primaryDirectAction != null) {
                                    handleActionSelection(model.primaryDirectAction)
                                } else {
                                    onExpandedChange(!expanded)
                                }
                            },
                            containerColor = mainContainerColor,
                            contentColor = mainContentColor,
                            badgeText = if (expanded) null else model.primaryBadge,
                            iconRotationDegrees = rotationDegrees,
                            animationsEnabled = settings.animationsEnabled,
                            testTag = "explorer_main_speed_dial_fab"
                        )
                    }

                    FabStyleMode.EXTENDED -> {
                        ExplorerExtendedFab(
                            text = model.primaryDirectAction?.title ?: model.primaryTitle,
                            icon = model.primaryIcon,
                            contentDescription = model.primaryTitle,
                            expanded = true,
                            onClick = {
                                if (model.speedDialActions.size > 1) {
                                    onExpandedChange(!expanded)
                                } else {
                                    model.primaryDirectAction?.let(handleActionSelection)
                                }
                            },
                            containerColor = mainContainerColor,
                            contentColor = mainContentColor,
                            badgeText = model.primaryBadge,
                            iconRotationDegrees = rotationDegrees,
                            animationsEnabled = settings.animationsEnabled,
                            testTag = "explorer_main_extended_fab"
                        )
                    }

                    FabStyleMode.STANDARD -> {
                        ExplorerFab(
                            icon = if (expanded && !settings.animationsEnabled) Icons.Default.Close else model.primaryIcon,
                            contentDescription = model.primaryTitle,
                            onClick = {
                                if (model.speedDialActions.size > 1) {
                                    onExpandedChange(!expanded)
                                } else {
                                    model.primaryDirectAction?.let(handleActionSelection)
                                }
                            },
                            containerColor = mainContainerColor,
                            contentColor = mainContentColor,
                            badgeText = model.primaryBadge,
                            iconRotationDegrees = rotationDegrees,
                            animationsEnabled = settings.animationsEnabled,
                            testTag = "explorer_main_standard_fab"
                        )
                    }

                    FabStyleMode.SMALL -> {
                        ExplorerSmallFab(
                            icon = if (expanded) Icons.Default.Close else model.primaryIcon,
                            contentDescription = model.primaryTitle,
                            onClick = {
                                if (model.speedDialActions.size > 1) {
                                    onExpandedChange(!expanded)
                                } else {
                                    model.primaryDirectAction?.let(handleActionSelection)
                                }
                            },
                            containerColor = mainContainerColor,
                            contentColor = mainContentColor,
                            animationsEnabled = settings.animationsEnabled,
                            testTag = "explorer_main_small_fab"
                        )
                    }
                }
            }
        }
    }

    // Destructive Action Confirmation Dialog
    pendingConfirmationAction?.let { actionToConfirm ->
        AlertDialog(
            onDismissRequest = { pendingConfirmationAction = null },
            icon = {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = "Confirm Action",
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = {
                Text(
                    text = actionToConfirm.confirmationTitle ?: "Confirm ${actionToConfirm.title}",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = actionToConfirm.confirmationMessage
                        ?: "Are you sure you want to execute \"${actionToConfirm.title}\"?"
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val confirmed = actionToConfirm
                        pendingConfirmationAction = null
                        onActionSelected(confirmed)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (actionToConfirm.isDestructive) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.primary
                        }
                    ),
                    modifier = Modifier.testTag("fab_confirm_action_button")
                ) {
                    Text(actionToConfirm.title)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { pendingConfirmationAction = null },
                    modifier = Modifier.testTag("fab_cancel_action_button")
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}

/**
 * E. Context-Aware Explorer FAB Host that animates transitions across screen states,
 * selection counts, scroll direction, and active pane placement.
 */
@Composable
fun ContextAwareExplorerFabHost(
    model: ResolvedFabModel,
    settings: FabCustomizationSettings,
    onActionSelected: (QuickAction) -> Unit,
    modifier: Modifier = Modifier,
    bottomOffsetPadding: Int = 16,
    endOffsetPadding: Int = 16
) {
    var speedDialExpanded by rememberSaveable(model.isSelectionMode, model.primaryTitle) {
        mutableStateOf(false)
    }

    AnimatedVisibility(
        visible = model.visible,
        enter = if (settings.animationsEnabled) {
            fadeIn(tween(200)) + scaleIn(tween(220, easing = FastOutSlowInEasing), initialScale = 0.82f)
        } else {
            fadeIn(tween(0))
        },
        exit = if (settings.animationsEnabled) {
            fadeOut(tween(160)) + scaleOut(tween(160), targetScale = 0.82f)
        } else {
            fadeOut(tween(0))
        },
        modifier = modifier
    ) {
        AnimatedContent(
            targetState = model.isSelectionMode to model.primaryTitle,
            transitionSpec = {
                fadeIn(tween(180)) togetherWith fadeOut(tween(140))
            },
            label = "context_aware_fab_transition"
        ) { _ ->
            ExplorerSpeedDial(
                model = model,
                settings = settings,
                expanded = speedDialExpanded,
                onExpandedChange = { speedDialExpanded = it },
                onActionSelected = onActionSelected,
                bottomOffsetPadding = bottomOffsetPadding,
                endOffsetPadding = endOffsetPadding
            )
        }
    }
}
