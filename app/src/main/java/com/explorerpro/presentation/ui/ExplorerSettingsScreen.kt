package com.explorerpro.presentation.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.explorerpro.data.local.UserPreferencesState
import com.explorerpro.domain.model.AppPermissionStatus
import com.explorerpro.domain.model.ConflictResolution
import com.explorerpro.domain.model.FabCustomizationSettings
import com.explorerpro.domain.model.FabPlacementMode
import com.explorerpro.domain.model.FabStyleMode
import com.explorerpro.domain.model.FabThemeMode
import com.explorerpro.domain.model.SpeedDialDirection
import com.explorerpro.domain.model.TermuxIntegrationStatus
import com.explorerpro.domain.model.ThemePreference
import com.explorerpro.ui.theme.FluentAmber400
import com.explorerpro.ui.theme.FluentEmerald400

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExplorerSettingsScreen(
    prefs: UserPreferencesState,
    permissionStatus: AppPermissionStatus,
    termuxStatus: TermuxIntegrationStatus,
    onNavigateBack: () -> Unit,
    onSetTheme: (ThemePreference) -> Unit,
    onToggleDynamicColor: (Boolean) -> Unit,
    onToggleHiddenFiles: (Boolean) -> Unit,
    onToggleDualPane: (Boolean) -> Unit,
    onToggleRecycleBin: (Boolean) -> Unit,
    onSetConflictResolution: (ConflictResolution) -> Unit,
    onUpdateFabSettings: ((FabCustomizationSettings) -> FabCustomizationSettings) -> Unit,
    onOpenPermissionsDialog: () -> Unit,
    onOpenOrMountTermux: () -> Unit,
    onToggleDeviconIcons: (Boolean) -> Unit = {},
    onToggleSmartFolderBadges: (Boolean) -> Unit = {},
    onOpenDeviconCatalogBrowser: () -> Unit = {},
    onResetFabDefaults: () -> Unit = { onUpdateFabSettings { FabCustomizationSettings() } },
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onNavigateBack)

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection)
            .testTag("explorer_settings_full_page"),
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            LargeTopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Settings",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Theme, Explorer Behavior, FAB & Permissions",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("settings_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back to Explorer"
                        )
                    }
                },
                scrollBehavior = scrollBehavior
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .navigationBarsPadding(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Appearance & Theme (Transferred from Navigation Drawer)
            item(key = "section_appearance") {
                SettingsSectionCard(
                    title = "Appearance & Theme",
                    subtitle = "Visual style and Material You palette",
                    icon = Icons.Default.Palette,
                    testTag = "settings_section_appearance"
                ) {
                    Text(
                        text = "Color Theme",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(8.dp))
                    SingleChoiceSegmentedButtonRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("settings_theme_selector")
                    ) {
                        ThemePreference.entries.forEachIndexed { index, mode ->
                            SegmentedButton(
                                selected = prefs.themePreference == mode,
                                onClick = { onSetTheme(mode) },
                                shape = SegmentedButtonDefaults.itemShape(
                                    index = index,
                                    count = ThemePreference.entries.size
                                ),
                                modifier = Modifier.testTag("settings_theme_${mode.name.lowercase()}")
                            ) {
                                Text(
                                    text = mode.label,
                                    style = MaterialTheme.typography.labelMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 12.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )

                    SettingsSwitchRow(
                        label = "Material You Dynamic Color",
                        subtitle = "Adapt surface accents to device wallpaper on Android 12+",
                        checked = prefs.dynamicColorEnabled,
                        onCheckedChange = onToggleDynamicColor,
                        testTag = "settings_switch_dynamic_color"
                    )
                }
            }

            // 2. Explorer & File Operations (Transferred from Navigation Drawer + File Preferences)
            item(key = "section_explorer") {
                SettingsSectionCard(
                    title = "Explorer & File Operations",
                    subtitle = "Pane layout, hidden files, deletion safety & conflicts",
                    icon = Icons.Default.FolderOpen,
                    testTag = "settings_section_explorer"
                ) {
                    SettingsSwitchRow(
                        label = "Show Hidden Files",
                        subtitle = "Display .dotfiles, .bashrc, .nomedia & system metadata",
                        checked = prefs.showHiddenFiles,
                        onCheckedChange = onToggleHiddenFiles,
                        icon = Icons.Default.Visibility,
                        testTag = "settings_switch_hidden_files"
                    )

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 8.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
                    )

                    SettingsSwitchRow(
                        label = "Dual-Pane Explorer Mode",
                        subtitle = "Show two side-by-side panes for rapid cross-folder transfers",
                        checked = prefs.dualPaneEnabled,
                        onCheckedChange = onToggleDualPane,
                        testTag = "settings_switch_dual_pane"
                    )

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 8.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
                    )

                    SettingsSwitchRow(
                        label = "Move Deleted Files to Recycle Bin",
                        subtitle = "Safely stage deleted items in .Trash before permanent removal",
                        checked = prefs.recycleBinEnabled,
                        onCheckedChange = onToggleRecycleBin,
                        icon = Icons.Default.DeleteSweep,
                        testTag = "settings_switch_recycle_bin"
                    )

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 10.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
                    )

                    Text(
                        text = "Default Conflict Resolution",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Action taken when pasting or moving files with matching names",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ConflictResolution.entries.forEach { policy ->
                            val selected = prefs.defaultConflictResolution == policy
                            FilterChip(
                                selected = selected,
                                onClick = { onSetConflictResolution(policy) },
                                leadingIcon = if (selected) {
                                    { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                } else null,
                                label = { Text(policy.label) },
                                modifier = Modifier.testTag("settings_conflict_${policy.name.lowercase()}")
                            )
                        }
                    }
                }
            }

            // 2B. Developer Icons (Devicon - 657 Icons Bundled Offline)
            item(key = "section_devicon") {
                SettingsSectionCard(
                    title = "Developer Icons (Devicon)",
                    subtitle = "657 technology icons for code files, folders & custom badges",
                    icon = Icons.Default.Code,
                    testTag = "settings_section_devicon"
                ) {
                    SettingsSwitchRow(
                        label = "Use Devicon File Icons",
                        subtitle = "Display original colored Devicon SVGs for programming, config & build files",
                        checked = prefs.enableDeviconIcons,
                        onCheckedChange = onToggleDeviconIcons,
                        testTag = "settings_switch_devicon_files"
                    )

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 8.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
                    )

                    SettingsSwitchRow(
                        label = "Smart Folder Technology Badges",
                        subtitle = "Auto-badge .git, node_modules, android, docker & framework directories",
                        checked = prefs.enableSmartFolderBadges,
                        onCheckedChange = onToggleSmartFolderBadges,
                        testTag = "settings_switch_devicon_folders"
                    )

                    Spacer(Modifier.height(10.dp))

                    OutlinedButton(
                        onClick = onOpenDeviconCatalogBrowser,
                        modifier = Modifier
                            .fillMaxWidth()
                            .minimumInteractiveComponentSize()
                            .testTag("settings_browse_devicon_catalog")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Code,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Browse All 657 Devicon Icons")
                    }
                }
            }

            // 3. FAB & Quick Actions (Transferred from Navigation Drawer)
            item(key = "section_fab") {
                SettingsSectionCard(
                    title = "FAB & Quick Actions",
                    subtitle = "Floating action button appearance, layout & smart behavior",
                    icon = Icons.Default.TouchApp,
                    headerTrailing = {
                        TextButton(
                            onClick = onResetFabDefaults,
                            modifier = Modifier.testTag("settings_reset_fab_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.RestartAlt,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text("Reset", style = MaterialTheme.typography.labelMedium)
                        }
                    },
                    testTag = "settings_section_fab"
                ) {
                    Text(
                        text = "FAB Style",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FabStyleMode.entries.forEach { styleMode ->
                            val selected = prefs.fabSettings.fabStyleMode == styleMode
                            FilterChip(
                                selected = selected,
                                onClick = {
                                    onUpdateFabSettings { it.copy(fabStyleMode = styleMode) }
                                },
                                leadingIcon = if (selected) {
                                    { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                } else null,
                                label = { Text(styleMode.label) },
                                modifier = Modifier.testTag("settings_fab_style_${styleMode.name.lowercase()}")
                            )
                        }
                    }

                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "Speed Dial Expansion Direction",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(6.dp))
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        SpeedDialDirection.entries.forEachIndexed { index, dir ->
                            SegmentedButton(
                                selected = prefs.fabSettings.speedDialDirection == dir,
                                onClick = {
                                    onUpdateFabSettings { it.copy(speedDialDirection = dir) }
                                },
                                shape = SegmentedButtonDefaults.itemShape(
                                    index = index,
                                    count = SpeedDialDirection.entries.size
                                )
                            ) {
                                Text(dir.label, maxLines = 1)
                            }
                        }
                    }

                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "FAB Theme Accent",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FabThemeMode.entries.forEach { themeMode ->
                            val selected = prefs.fabSettings.themeMode == themeMode
                            FilterChip(
                                selected = selected,
                                onClick = {
                                    onUpdateFabSettings { it.copy(themeMode = themeMode) }
                                },
                                leadingIcon = if (selected) {
                                    { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                } else null,
                                label = { Text(themeMode.label) }
                            )
                        }
                    }

                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "Dual-Pane FAB Placement",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(6.dp))
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        FabPlacementMode.entries.forEachIndexed { index, placement ->
                            SegmentedButton(
                                selected = prefs.fabSettings.placementMode == placement,
                                onClick = {
                                    onUpdateFabSettings { it.copy(placementMode = placement) }
                                },
                                shape = SegmentedButtonDefaults.itemShape(
                                    index = index,
                                    count = FabPlacementMode.entries.size
                                )
                            ) {
                                Text(
                                    text = placement.label,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 12.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )

                    SettingsSwitchRow(
                        label = "Show Speed Dial Labels",
                        subtitle = "Display descriptive action titles & subtitles beside buttons",
                        checked = prefs.fabSettings.showSpeedDialLabels,
                        onCheckedChange = { enabled ->
                            onUpdateFabSettings { it.copy(showSpeedDialLabels = enabled) }
                        },
                        testTag = "settings_switch_fab_labels"
                    )

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 8.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
                    )

                    SettingsSwitchRow(
                        label = "Adapt to Item Selection",
                        subtitle = "Switch FAB dynamically to Archive, Copy, Move & Rename actions",
                        checked = prefs.fabSettings.adaptToSelection,
                        onCheckedChange = { enabled ->
                            onUpdateFabSettings { it.copy(adaptToSelection = enabled) }
                        },
                        testTag = "settings_switch_fab_adapt"
                    )

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 8.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
                    )

                    SettingsSwitchRow(
                        label = "Hide FAB While Scrolling",
                        subtitle = "Auto-collapse button smoothly when scrolling down long file lists",
                        checked = prefs.fabSettings.hideWhileScrolling,
                        onCheckedChange = { enabled ->
                            onUpdateFabSettings { it.copy(hideWhileScrolling = enabled) }
                        },
                        testTag = "settings_switch_fab_scroll"
                    )

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 8.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
                    )

                    SettingsSwitchRow(
                        label = "Hide When Keyboard Opens",
                        subtitle = "Prevent IME overlap during search, rename & path editing",
                        checked = prefs.fabSettings.hideWhenKeyboardOpen,
                        onCheckedChange = { enabled ->
                            onUpdateFabSettings { it.copy(hideWhenKeyboardOpen = enabled) }
                        },
                        testTag = "settings_switch_fab_keyboard"
                    )

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 8.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
                    )

                    SettingsSwitchRow(
                        label = "Enable FAB Animations",
                        subtitle = "Spring press feedback and Speed Dial staggered transitions",
                        checked = prefs.fabSettings.animationsEnabled,
                        onCheckedChange = { enabled ->
                            onUpdateFabSettings { it.copy(animationsEnabled = enabled) }
                        },
                        testTag = "settings_switch_fab_animations"
                    )
                }
            }

            // 4. Permissions & Termux Integration Management
            item(key = "section_permissions_termux") {
                SettingsSectionCard(
                    title = "Permissions & Termux Bridge",
                    subtitle = "System access status and live Termux \$HOME mounting",
                    icon = Icons.Default.Tune,
                    testTag = "settings_section_permissions"
                ) {
                    Surface(
                        color = if (permissionStatus.areAllCorePermissionsGranted) {
                            FluentEmerald400.copy(alpha = 0.14f)
                        } else {
                            FluentAmber400.copy(alpha = 0.16f)
                        },
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(
                            1.dp,
                            if (permissionStatus.areAllCorePermissionsGranted) {
                                FluentEmerald400.copy(alpha = 0.35f)
                            } else {
                                FluentAmber400.copy(alpha = 0.45f)
                            }
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.VerifiedUser,
                                    contentDescription = null,
                                    tint = if (permissionStatus.areAllCorePermissionsGranted) {
                                        FluentEmerald400
                                    } else {
                                        FluentAmber400
                                    },
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = if (permissionStatus.areAllCorePermissionsGranted) {
                                            "All Core Permissions Granted"
                                        } else {
                                            "Action Needed: Missing Permissions"
                                        },
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = "Storage: ${if (permissionStatus.isStorageAccessGranted) "Granted" else "Required"} • Notifications: ${if (permissionStatus.isNotificationPermissionGranted) "Granted" else "Required"}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            OutlinedButton(
                                onClick = onOpenPermissionsDialog,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .minimumInteractiveComponentSize()
                                    .testTag("settings_manage_permissions_button")
                            ) {
                                Text("Manage & Grant Permissions")
                            }
                        }
                    }

                    Spacer(Modifier.height(10.dp))

                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Terminal,
                                    contentDescription = null,
                                    tint = if (termuxStatus.isMountedOrAccessible) {
                                        FluentEmerald400
                                    } else {
                                        MaterialTheme.colorScheme.primary
                                    },
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Termux (\$HOME) Environment",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = when {
                                            termuxStatus.isMountedOrAccessible -> "Auto-mounted and accessible via Explorer Pro"
                                            termuxStatus.isTermuxInstalled || termuxStatus.isDocumentsProviderAvailable ->
                                                "Termux detected on device (${termuxStatus.termuxVersionName ?: "com.termux.documents"})"
                                            else -> "Live watcher waiting for Termux / com.termux.documents"
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            OutlinedButton(
                                onClick = {
                                    onNavigateBack()
                                    onOpenOrMountTermux()
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .minimumInteractiveComponentSize()
                                    .testTag("settings_termux_mount_button")
                            ) {
                                Text(
                                    if (termuxStatus.isMountedOrAccessible) {
                                        "Open Auto-Mounted Termux \$HOME"
                                    } else {
                                        "Mount / Connect Termux \$HOME"
                                    }
                                )
                            }
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun SettingsSectionCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    testTag: String,
    headerTrailing: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 2.dp,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag(testTag)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.size(36.dp)
                    ) {
                        androidx.compose.foundation.layout.Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                if (headerTrailing != null) {
                    headerTrailing()
                }
            }

            Spacer(Modifier.height(14.dp))
            content()
        }
    }
}

@Composable
private fun SettingsSwitchRow(
    label: String,
    subtitle: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    icon: ImageVector? = null,
    testTag: String? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable { onCheckedChange(!checked) }
            .minimumInteractiveComponentSize()
            .padding(horizontal = 4.dp, vertical = 6.dp)
            .let { if (testTag != null) it.testTag(testTag) else it },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(12.dp))
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 12.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (!subtitle.isNullOrEmpty()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}
