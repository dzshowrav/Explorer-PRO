package com.explorerpro.data.local

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(
    tableName = "favorites",
    indices = [Index(value = ["path"], unique = true)]
)
data class FavoriteEntity(
    @PrimaryKey val path: String,
    val name: String,
    val isDirectory: Boolean,
    val category: String,
    val addedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "recent_files",
    indices = [Index(value = ["accessedAt"])]
)
data class RecentFileEntity(
    @PrimaryKey val path: String,
    val name: String,
    val extension: String,
    val formatName: String,
    val sizeBytes: Long,
    val accessedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "search_history",
    indices = [Index(value = ["timestamp"])]
)
data class SearchHistoryEntity(
    @PrimaryKey val query: String,
    val scopePath: String,
    val resultCount: Int,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "tab_sessions",
    indices = [Index(value = ["paneId", "position"])]
)
data class TabSessionEntity(
    @PrimaryKey val tabId: String,
    val paneId: String,
    val title: String,
    val currentPath: String,
    val isActive: Boolean,
    val position: Int,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "operation_logs",
    indices = [Index(value = ["timestamp"])]
)
data class OperationLogEntity(
    @PrimaryKey val id: String,
    val operationType: String,
    val title: String,
    val sourceSummary: String,
    val destinationPath: String,
    val status: String,
    val processedItems: Int,
    val totalBytes: Long,
    val details: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "folder_stats_cache",
    indices = [Index(value = ["lastModifiedSnapshot"])]
)
data class FolderStatsCacheEntity(
    @PrimaryKey val folderPath: String,
    val directFiles: Int,
    val directFolders: Int,
    val recursiveFiles: Int,
    val recursiveFolders: Int,
    val recursiveSizeBytes: Long,
    val lastModifiedSnapshot: Long,
    val calculatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "archive_history",
    indices = [Index(value = ["timestamp"])]
)
data class ArchiveHistoryEntity(
    @PrimaryKey val archivePath: String,
    val archiveFormat: String,
    val entryCount: Int,
    val lastAction: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Dao
interface ExplorerDao {
    @Query("SELECT * FROM favorites ORDER BY addedAt DESC")
    fun observeFavorites(): Flow<List<FavoriteEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertFavorite(entity: FavoriteEntity)

    @Query("DELETE FROM favorites WHERE path = :path")
    suspend fun removeFavorite(path: String)

    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE path = :path)")
    suspend fun isFavorite(path: String): Boolean

    @Query("SELECT * FROM recent_files ORDER BY accessedAt DESC LIMIT 50")
    fun observeRecentFiles(): Flow<List<RecentFileEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRecentFile(entity: RecentFileEntity)

    @Query("DELETE FROM recent_files")
    suspend fun clearRecentFiles()

    @Query("SELECT * FROM search_history ORDER BY timestamp DESC LIMIT 25")
    fun observeSearchHistory(): Flow<List<SearchHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSearchQuery(entity: SearchHistoryEntity)

    @Query("DELETE FROM search_history")
    suspend fun clearSearchHistory()

    @Query("SELECT * FROM tab_sessions WHERE paneId = :paneId ORDER BY position ASC")
    suspend fun getTabsForPane(paneId: String): List<TabSessionEntity>

    @Query("DELETE FROM tab_sessions WHERE paneId = :paneId")
    suspend fun clearTabsForPane(paneId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTabSessions(tabs: List<TabSessionEntity>)

    @Query("SELECT * FROM operation_logs ORDER BY timestamp DESC LIMIT 100")
    fun observeOperationLogs(): Flow<List<OperationLogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertOperationLog(entity: OperationLogEntity)

    @Query("DELETE FROM operation_logs")
    suspend fun clearOperationLogs()

    @Query("SELECT * FROM folder_stats_cache WHERE folderPath = :path LIMIT 1")
    suspend fun getCachedFolderStats(path: String): FolderStatsCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertFolderStats(entity: FolderStatsCacheEntity)

    @Query("SELECT * FROM archive_history ORDER BY timestamp DESC LIMIT 30")
    fun observeArchiveHistory(): Flow<List<ArchiveHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertArchiveHistory(entity: ArchiveHistoryEntity)
}

@Database(
    entities = [
        FavoriteEntity::class,
        RecentFileEntity::class,
        SearchHistoryEntity::class,
        TabSessionEntity::class,
        OperationLogEntity::class,
        FolderStatsCacheEntity::class,
        ArchiveHistoryEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class ExplorerDatabase : RoomDatabase() {
    abstract fun explorerDao(): ExplorerDao

    companion object {
        @Volatile
        private var INSTANCE: ExplorerDatabase? = null

        fun getInstance(context: Context): ExplorerDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    ExplorerDatabase::class.java,
                    "explorer_pro.db"
                ).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }
    }
}
