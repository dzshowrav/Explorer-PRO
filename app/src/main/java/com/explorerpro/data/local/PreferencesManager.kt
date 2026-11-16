package com.explorerpro.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.explorerpro.domain.model.ConflictResolution
import com.explorerpro.domain.model.FabCustomizationSettings
import com.explorerpro.domain.model.FabPlacementMode
import com.explorerpro.domain.model.FabStyleMode
import com.explorerpro.domain.model.FabThemeMode
import com.explorerpro.domain.model.GroupBy
import com.explorerpro.domain.model.PaneId
import com.explorerpro.domain.model.SortField
import com.explorerpro.domain.model.SortOrder
import com.explorerpro.domain.model.SpeedDialDirection
import com.explorerpro.domain.model.ThemePreference
import com.explorerpro.domain.model.ViewMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "explorer_pro_preferences")

data class UserPreferencesState(
    val isLoadedFromDisk: Boolean = false,
    val firstLaunchPermissionsCompleted: Boolean = false,
    val themePreference: ThemePreference = ThemePreference.DARK,
    val dynamicColorEnabled: Boolean = false,
    val showHiddenFiles: Boolean = false,
    val dualPaneEnabled: Boolean = false,
    val recycleBinEnabled: Boolean = true,
    val defaultConflictResolution: ConflictResolution = ConflictResolution.RENAME,
    val leftViewMode: ViewMode = ViewMode.DETAILED_LIST,
    val rightViewMode: ViewMode = ViewMode.SIMPLE_LIST,
    val leftGridColumns: Int = 3,
    val rightGridColumns: Int = 3,
    val leftSortField: SortField = SortField.NAME_NATURAL,
    val leftSortOrder: SortOrder = SortOrder.ASCENDING,
    val rightSortField: SortField = SortField.NAME_NATURAL,
    val rightSortOrder: SortOrder = SortOrder.ASCENDING,
    val leftGroupBy: GroupBy = GroupBy.NONE,
    val rightGroupBy: GroupBy = GroupBy.NONE,
    val persistedSafUris: Set<String> = emptySet(),
    val fabSettings: FabCustomizationSettings = FabCustomizationSettings()
)

class PreferencesManager(private val context: Context) {

    private object Keys {
        val FIRST_LAUNCH_PERMISSIONS_COMPLETED = booleanPreferencesKey("first_launch_permissions_completed")
        val THEME = stringPreferencesKey("theme_preference")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val SHOW_HIDDEN = booleanPreferencesKey("show_hidden_files")
        val DUAL_PANE = booleanPreferencesKey("dual_pane_enabled")
        val RECYCLE_BIN = booleanPreferencesKey("recycle_bin_enabled")
        val CONFLICT_POLICY = stringPreferencesKey("conflict_resolution")
        val LEFT_VIEW_MODE = stringPreferencesKey("left_view_mode")
        val RIGHT_VIEW_MODE = stringPreferencesKey("right_view_mode")
        val LEFT_GRID_COLS = intPreferencesKey("left_grid_cols")
        val RIGHT_GRID_COLS = intPreferencesKey("right_grid_cols")
        val LEFT_SORT_FIELD = stringPreferencesKey("left_sort_field")
        val LEFT_SORT_ORDER = stringPreferencesKey("left_sort_order")
        val RIGHT_SORT_FIELD = stringPreferencesKey("right_sort_field")
        val RIGHT_SORT_ORDER = stringPreferencesKey("right_sort_order")
        val LEFT_GROUP_BY = stringPreferencesKey("left_group_by")
        val RIGHT_GROUP_BY = stringPreferencesKey("right_group_by")
        val SAF_URIS = stringSetPreferencesKey("persisted_saf_uris")
        val FAB_STYLE = stringPreferencesKey("fab_style_mode")
        val FAB_DIRECTION = stringPreferencesKey("fab_speed_dial_direction")
        val FAB_SHOW_LABELS = booleanPreferencesKey("fab_show_labels")
        val FAB_ANIMATIONS = booleanPreferencesKey("fab_animations_enabled")
        val FAB_HIDE_SCROLL = booleanPreferencesKey("fab_hide_while_scrolling")
        val FAB_ADAPT_SELECTION = booleanPreferencesKey("fab_adapt_to_selection")
        val FAB_HIDE_KEYBOARD = booleanPreferencesKey("fab_hide_when_keyboard_open")
        val FAB_PLACEMENT = stringPreferencesKey("fab_placement_mode")
        val FAB_THEME = stringPreferencesKey("fab_theme_mode")
    }

    val preferencesFlow: Flow<UserPreferencesState> = context.dataStore.data.map { prefs ->
        UserPreferencesState(
            isLoadedFromDisk = true,
            firstLaunchPermissionsCompleted = prefs[Keys.FIRST_LAUNCH_PERMISSIONS_COMPLETED] ?: false,
            themePreference = runCatching {
                ThemePreference.valueOf(prefs[Keys.THEME] ?: ThemePreference.DARK.name)
            }.getOrDefault(ThemePreference.DARK),
            dynamicColorEnabled = prefs[Keys.DYNAMIC_COLOR] ?: false,
            showHiddenFiles = prefs[Keys.SHOW_HIDDEN] ?: false,
            dualPaneEnabled = prefs[Keys.DUAL_PANE] ?: false,
            recycleBinEnabled = prefs[Keys.RECYCLE_BIN] ?: true,
            defaultConflictResolution = runCatching {
                ConflictResolution.valueOf(prefs[Keys.CONFLICT_POLICY] ?: ConflictResolution.RENAME.name)
            }.getOrDefault(ConflictResolution.RENAME),
            leftViewMode = runCatching {
                ViewMode.valueOf(prefs[Keys.LEFT_VIEW_MODE] ?: ViewMode.DETAILED_LIST.name)
            }.getOrDefault(ViewMode.DETAILED_LIST),
            rightViewMode = runCatching {
                ViewMode.valueOf(prefs[Keys.RIGHT_VIEW_MODE] ?: ViewMode.SIMPLE_LIST.name)
            }.getOrDefault(ViewMode.SIMPLE_LIST),
            leftGridColumns = (prefs[Keys.LEFT_GRID_COLS] ?: 3).coerceIn(2, 6),
            rightGridColumns = (prefs[Keys.RIGHT_GRID_COLS] ?: 3).coerceIn(2, 6),
            leftSortField = runCatching {
                SortField.valueOf(prefs[Keys.LEFT_SORT_FIELD] ?: SortField.NAME_NATURAL.name)
            }.getOrDefault(SortField.NAME_NATURAL),
            leftSortOrder = runCatching {
                SortOrder.valueOf(prefs[Keys.LEFT_SORT_ORDER] ?: SortOrder.ASCENDING.name)
            }.getOrDefault(SortOrder.ASCENDING),
            rightSortField = runCatching {
                SortField.valueOf(prefs[Keys.RIGHT_SORT_FIELD] ?: SortField.NAME_NATURAL.name)
            }.getOrDefault(SortField.NAME_NATURAL),
            rightSortOrder = runCatching {
                SortOrder.valueOf(prefs[Keys.RIGHT_SORT_ORDER] ?: SortOrder.ASCENDING.name)
            }.getOrDefault(SortOrder.ASCENDING),
            leftGroupBy = runCatching {
                GroupBy.valueOf(prefs[Keys.LEFT_GROUP_BY] ?: GroupBy.NONE.name)
            }.getOrDefault(GroupBy.NONE),
            rightGroupBy = runCatching {
                GroupBy.valueOf(prefs[Keys.RIGHT_GROUP_BY] ?: GroupBy.NONE.name)
            }.getOrDefault(GroupBy.NONE),
            persistedSafUris = prefs[Keys.SAF_URIS] ?: emptySet(),
            fabSettings = FabCustomizationSettings(
                fabStyleMode = runCatching {
                    FabStyleMode.valueOf(prefs[Keys.FAB_STYLE] ?: FabStyleMode.SPEED_DIAL.name)
                }.getOrDefault(FabStyleMode.SPEED_DIAL),
                speedDialDirection = runCatching {
                    SpeedDialDirection.valueOf(prefs[Keys.FAB_DIRECTION] ?: SpeedDialDirection.UPWARD.name)
                }.getOrDefault(SpeedDialDirection.UPWARD),
                showSpeedDialLabels = prefs[Keys.FAB_SHOW_LABELS] ?: true,
                animationsEnabled = prefs[Keys.FAB_ANIMATIONS] ?: true,
                hideWhileScrolling = prefs[Keys.FAB_HIDE_SCROLL] ?: true,
                adaptToSelection = prefs[Keys.FAB_ADAPT_SELECTION] ?: true,
                hideWhenKeyboardOpen = prefs[Keys.FAB_HIDE_KEYBOARD] ?: true,
                placementMode = runCatching {
                    FabPlacementMode.valueOf(prefs[Keys.FAB_PLACEMENT] ?: FabPlacementMode.CONTENT_AWARE.name)
                }.getOrDefault(FabPlacementMode.CONTENT_AWARE),
                themeMode = runCatching {
                    FabThemeMode.valueOf(prefs[Keys.FAB_THEME] ?: FabThemeMode.FOLLOW_SYSTEM.name)
                }.getOrDefault(FabThemeMode.FOLLOW_SYSTEM)
            )
        )
    }

    suspend fun setFirstLaunchPermissionsCompleted(completed: Boolean) {
        context.dataStore.edit { it[Keys.FIRST_LAUNCH_PERMISSIONS_COMPLETED] = completed }
    }

    suspend fun setThemePreference(theme: ThemePreference) {
        context.dataStore.edit { it[Keys.THEME] = theme.name }
    }

    suspend fun setDynamicColor(enabled: Boolean) {
        context.dataStore.edit { it[Keys.DYNAMIC_COLOR] = enabled }
    }

    suspend fun setShowHiddenFiles(show: Boolean) {
        context.dataStore.edit { it[Keys.SHOW_HIDDEN] = show }
    }

    suspend fun setDualPaneEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.DUAL_PANE] = enabled }
    }

    suspend fun setRecycleBinEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.RECYCLE_BIN] = enabled }
    }

    suspend fun setConflictResolution(resolution: ConflictResolution) {
        context.dataStore.edit { it[Keys.CONFLICT_POLICY] = resolution.name }
    }

    suspend fun setPaneViewMode(paneId: PaneId, mode: ViewMode) {
        context.dataStore.edit {
            if (paneId == PaneId.LEFT) it[Keys.LEFT_VIEW_MODE] = mode.name
            else it[Keys.RIGHT_VIEW_MODE] = mode.name
        }
    }

    suspend fun setPaneGridColumns(paneId: PaneId, columns: Int) {
        val safeCols = columns.coerceIn(2, 6)
        context.dataStore.edit {
            if (paneId == PaneId.LEFT) it[Keys.LEFT_GRID_COLS] = safeCols
            else it[Keys.RIGHT_GRID_COLS] = safeCols
        }
    }

    suspend fun setPaneSort(paneId: PaneId, field: SortField, order: SortOrder) {
        context.dataStore.edit {
            if (paneId == PaneId.LEFT) {
                it[Keys.LEFT_SORT_FIELD] = field.name
                it[Keys.LEFT_SORT_ORDER] = order.name
            } else {
                it[Keys.RIGHT_SORT_FIELD] = field.name
                it[Keys.RIGHT_SORT_ORDER] = order.name
            }
        }
    }

    suspend fun setPaneGroupBy(paneId: PaneId, groupBy: GroupBy) {
        context.dataStore.edit {
            if (paneId == PaneId.LEFT) it[Keys.LEFT_GROUP_BY] = groupBy.name
            else it[Keys.RIGHT_GROUP_BY] = groupBy.name
        }
    }

    suspend fun addPersistedSafUri(uriString: String) {
        context.dataStore.edit { prefs ->
            val current = prefs[Keys.SAF_URIS] ?: emptySet()
            prefs[Keys.SAF_URIS] = current + uriString
        }
    }

    suspend fun updateFabSettings(settings: FabCustomizationSettings) {
        context.dataStore.edit { prefs ->
            prefs[Keys.FAB_STYLE] = settings.fabStyleMode.name
            prefs[Keys.FAB_DIRECTION] = settings.speedDialDirection.name
            prefs[Keys.FAB_SHOW_LABELS] = settings.showSpeedDialLabels
            prefs[Keys.FAB_ANIMATIONS] = settings.animationsEnabled
            prefs[Keys.FAB_HIDE_SCROLL] = settings.hideWhileScrolling
            prefs[Keys.FAB_ADAPT_SELECTION] = settings.adaptToSelection
            prefs[Keys.FAB_HIDE_KEYBOARD] = settings.hideWhenKeyboardOpen
            prefs[Keys.FAB_PLACEMENT] = settings.placementMode.name
            prefs[Keys.FAB_THEME] = settings.themeMode.name
        }
    }
}
