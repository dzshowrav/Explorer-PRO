package com.explorerpro.core.domain.operations

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.explorerpro.data.local.ExplorerDao
import com.explorerpro.data.local.OperationLogEntity
import com.explorerpro.domain.model.FileOperationTask
import com.explorerpro.domain.model.OperationStatus
import com.explorerpro.domain.model.OperationType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.coroutineContext
import kotlin.math.roundToInt

/**
 * Execution handle passed to any long-running Explorer Pro operation (File Copy/Move/Delete,
 * Archive Extract/Create/Verify, Batch Rename, Checksum, Search, Folder Intelligence, Storage Analyzer).
 */
class OperationExecutionScope(
    val taskId: String,
    private val pauseFlow: StateFlow<Boolean>,
    private val onProgressUpdate: (
        fraction: Float,
        processedItems: Int,
        totalItems: Int,
        transferredBytes: Long,
        totalBytes: Long,
        currentItemName: String,
        indeterminate: Boolean,
        skippedCount: Int,
        errorCount: Int
    ) -> Unit
) {
    /**
     * Suspends while the user has paused this operation and throws [CancellationException]
     * immediately if the user cancels the operation.
     */
    suspend fun checkPausedAndActive() {
        coroutineContext.ensureActive()
        if (pauseFlow.value) {
            // Wait until pauseFlow becomes false or the coroutine is cancelled
            pauseFlow.first { !it }
            coroutineContext.ensureActive()
        }
    }

    fun reportProgress(
        fraction: Float,
        processedItems: Int = 0,
        totalItems: Int = 1,
        transferredBytes: Long = 0L,
        totalBytes: Long = 0L,
        currentItemName: String = "",
        indeterminate: Boolean = false,
        skippedCount: Int = 0,
        errorCount: Int = 0
    ) {
        onProgressUpdate(
            fraction.coerceIn(0f, 1f),
            processedItems,
            totalItems,
            transferredBytes,
            totalBytes,
            currentItemName,
            indeterminate,
            skippedCount,
            errorCount
        )
    }
}

/**
 * Native Android Notification integration for Explorer Pro operations (API 26–36+).
 * Respects Android 13+ (`POST_NOTIFICATIONS`) runtime permission and throttles ongoing updates.
 */
class OperationNotificationManager(private val context: Context) {

    companion object {
        const val CHANNEL_ONGOING_ID = "explorer_pro_ongoing_ops"
        const val CHANNEL_COMPLETED_ID = "explorer_pro_completed_ops"
    }

    init {
        createChannelsIfNeeded()
    }

    private fun createChannelsIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            val ongoingChannel = NotificationChannel(
                CHANNEL_ONGOING_ID,
                "Active File & Archive Operations",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Real-time progress for file transfers, archive extraction, and storage scans"
                setShowBadge(false)
            }
            val completedChannel = NotificationChannel(
                CHANNEL_COMPLETED_ID,
                "Completed & Failed Operations",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Completion and error alerts for Explorer Pro operations"
            }
            nm.createNotificationChannel(ongoingChannel)
            nm.createNotificationChannel(completedChannel)
        }
    }

    fun canPostNotifications(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun notifyOperationState(task: FileOperationTask) {
        if (!canPostNotifications()) return
        runCatching {
            val notificationId = task.id.hashCode()
            val isOngoing = task.status == OperationStatus.RUNNING ||
                task.status == OperationStatus.PAUSED ||
                task.status == OperationStatus.QUEUED
            val channelId = if (isOngoing) CHANNEL_ONGOING_ID else CHANNEL_COMPLETED_ID

            val pct = (task.progressFraction * 100f).roundToInt().coerceIn(0, 100)
            val contentText = when (task.status) {
                OperationStatus.RUNNING -> {
                    val itemPart = task.currentItemName.ifEmpty { "${task.processedItems}/${task.totalItems} items" }
                    if (task.indeterminate) itemPart else "$pct% • $itemPart"
                }
                OperationStatus.PAUSED -> "Paused at $pct% • ${task.currentItemName}"
                OperationStatus.COMPLETED -> "Completed (${task.processedItems} item(s))"
                OperationStatus.FAILED -> "Failed: ${task.errorDetail ?: "Error"}"
                OperationStatus.CANCELLED -> "Cancelled by user"
                else -> task.status.label
            }

            val builder = NotificationCompat.Builder(context, channelId)
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentTitle(task.title)
                .setContentText(contentText)
                .setOnlyAlertOnce(true)
                .setOngoing(isOngoing)
                .setAutoCancel(!isOngoing)

            if (isOngoing) {
                builder.setSmallIcon(android.R.drawable.stat_sys_download)
                builder.setProgress(100, pct, task.indeterminate)
            } else {
                builder.setProgress(0, 0, false)
            }

            NotificationManagerCompat.from(context).notify(notificationId, builder.build())
        }
    }

    fun cancelNotification(taskId: String) {
        runCatching {
            NotificationManagerCompat.from(context).cancel(taskId.hashCode())
        }
    }
}

/**
 * Centralized Operation Controller & Engine for Explorer Pro.
 * Manages real-time StateFlow updates, Pause/Resume/Cancel/Retry lifecycles, transfer speed & ETA
 * measurement, Room history persistence, and Android notification updates.
 */
class OperationController(
    context: Context,
    private val scope: CoroutineScope,
    private val dao: ExplorerDao
) {
    private val notificationManager = OperationNotificationManager(context.applicationContext)

    private val _tasks = MutableStateFlow<List<FileOperationTask>>(emptyList())
    val tasks: StateFlow<List<FileOperationTask>> = _tasks.asStateFlow()

    private val activeJobs = ConcurrentHashMap<String, Job>()
    private val pauseFlows = ConcurrentHashMap<String, MutableStateFlow<Boolean>>()
    private val retryActions = ConcurrentHashMap<String, () -> Unit>()
    private val pauseAccumulatedMs = ConcurrentHashMap<String, Long>()
    private val pauseStartedAtMs = ConcurrentHashMap<String, Long>()

    fun launchOperation(
        type: OperationType,
        title: String,
        sourcePaths: List<String>,
        destinationPath: String,
        totalItems: Int = 1,
        totalBytes: Long = 0L,
        indeterminate: Boolean = false,
        canPause: Boolean = true,
        canCancel: Boolean = true,
        canRetry: Boolean = true,
        onRetryRequest: (() -> Unit)? = null,
        block: suspend (OperationExecutionScope) -> Result<Int>
    ): String {
        val initialTask = FileOperationTask(
            type = type,
            title = title,
            sourcePaths = sourcePaths,
            destinationPath = destinationPath,
            status = OperationStatus.RUNNING,
            minValue = 0f,
            maxValue = 100f,
            progressValue = 0f,
            progressFraction = 0f,
            indeterminate = indeterminate,
            processedItems = 0,
            totalItems = totalItems.coerceAtLeast(1),
            transferredBytes = 0L,
            totalBytes = totalBytes,
            canPause = canPause,
            canCancel = canCancel,
            canRetry = canRetry && onRetryRequest != null
        )

        val taskId = initialTask.id
        val pauseState = MutableStateFlow(false)
        pauseFlows[taskId] = pauseState
        pauseAccumulatedMs[taskId] = 0L
        if (onRetryRequest != null) {
            retryActions[taskId] = onRetryRequest
        }

        _tasks.update { listOf(initialTask) + it }
        notificationManager.notifyOperationState(initialTask)

        val startedRealtimeMs = SystemClock.elapsedRealtime()
        var lastUiEmitMs = 0L
        var lastProcessedItems = -1

        val execScope = OperationExecutionScope(
            taskId = taskId,
            pauseFlow = pauseState
        ) { fraction, doneItems, maxItems, doneBytes, maxBytes, currentItem, isIndeterminate, skipped, errors ->
            val nowMs = SystemClock.elapsedRealtime()
            val shouldEmit = fraction >= 0.999f ||
                doneItems != lastProcessedItems ||
                (nowMs - lastUiEmitMs) >= 45L

            if (shouldEmit) {
                lastUiEmitMs = nowMs
                lastProcessedItems = doneItems

                val pausedMs = pauseAccumulatedMs[taskId] ?: 0L
                val activeElapsedMs = (nowMs - startedRealtimeMs - pausedMs).coerceAtLeast(1L)
                val speedBps = if (doneBytes > 0L && activeElapsedMs >= 120L) {
                    (doneBytes * 1000L) / activeElapsedMs
                } else 0L

                val remainingBytes = (maxBytes - doneBytes).coerceAtLeast(0L)
                val etaSeconds = if (speedBps > 0L && maxBytes > doneBytes) {
                    remainingBytes / speedBps
                } else null

                val safeFraction = fraction.coerceIn(0f, 1f)
                var updatedTaskForNotif: FileOperationTask? = null

                _tasks.update { list ->
                    list.map { existing ->
                        if (existing.id == taskId) {
                            val updated = existing.copy(
                                progressFraction = safeFraction,
                                progressValue = safeFraction * 100f,
                                indeterminate = isIndeterminate,
                                processedItems = doneItems,
                                totalItems = maxItems.coerceAtLeast(1),
                                transferredBytes = doneBytes,
                                totalBytes = maxBytes,
                                speedBytesPerSec = speedBps,
                                estimatedRemainingSeconds = etaSeconds,
                                currentItemName = currentItem,
                                skippedCount = skipped,
                                errorCount = errors
                            )
                            updatedTaskForNotif = updated
                            updated
                        } else existing
                    }
                }
                updatedTaskForNotif?.let { notificationManager.notifyOperationState(it) }
            }
        }

        val job = scope.launch(Dispatchers.IO) {
            try {
                val result = block(execScope)
                result.fold(
                    onSuccess = { finalCount ->
                        val completedAt = System.currentTimeMillis()
                        var completedTask: FileOperationTask? = null
                        _tasks.update { list ->
                            list.map { existing ->
                                if (existing.id == taskId) {
                                    val done = existing.copy(
                                        status = OperationStatus.COMPLETED,
                                        progressFraction = 1f,
                                        progressValue = 100f,
                                        indeterminate = false,
                                        processedItems = finalCount.coerceAtLeast(existing.processedItems),
                                        completionTimeMs = completedAt,
                                        estimatedRemainingSeconds = 0L
                                    )
                                    completedTask = done
                                    done
                                } else existing
                            }
                        }
                        completedTask?.let {
                            notificationManager.notifyOperationState(it)
                            persistOperationLog(it, "Completed successfully ($finalCount item(s))")
                        }
                    },
                    onFailure = { err ->
                        if (err is CancellationException) throw err
                        markFailed(taskId, err.message ?: "Operation failed")
                    }
                )
            } catch (ce: CancellationException) {
                val cancelledAt = System.currentTimeMillis()
                var cancelledTask: FileOperationTask? = null
                _tasks.update { list ->
                    list.map { existing ->
                        if (existing.id == taskId) {
                            val c = existing.copy(
                                status = OperationStatus.CANCELLED,
                                indeterminate = false,
                                completionTimeMs = cancelledAt,
                                errorDetail = "Cancelled by user"
                            )
                            cancelledTask = c
                            c
                        } else existing
                    }
                }
                cancelledTask?.let {
                    notificationManager.notifyOperationState(it)
                    persistOperationLog(it, "Cancelled by user")
                }
            } catch (t: Throwable) {
                markFailed(taskId, t.message ?: "Unexpected error")
            } finally {
                activeJobs.remove(taskId)
                pauseFlows.remove(taskId)
                pauseAccumulatedMs.remove(taskId)
                pauseStartedAtMs.remove(taskId)
            }
        }

        activeJobs[taskId] = job
        return taskId
    }

    private suspend fun markFailed(taskId: String, message: String) {
        val failedAt = System.currentTimeMillis()
        var failedTask: FileOperationTask? = null
        _tasks.update { list ->
            list.map { existing ->
                if (existing.id == taskId) {
                    val f = existing.copy(
                        status = OperationStatus.FAILED,
                        indeterminate = false,
                        errorDetail = message,
                        completionTimeMs = failedAt
                    )
                    failedTask = f
                    f
                } else existing
            }
        }
        failedTask?.let {
            notificationManager.notifyOperationState(it)
            persistOperationLog(it, "Failed: $message")
        }
    }

    private suspend fun persistOperationLog(task: FileOperationTask, details: String) {
        runCatching {
            dao.upsertOperationLog(
                OperationLogEntity(
                    id = task.id,
                    operationType = task.type.label,
                    title = task.title,
                    sourceSummary = if (task.sourcePaths.size == 1) {
                        task.sourcePaths.first().substringAfterLast('/')
                    } else {
                        "${task.sourcePaths.size} items"
                    },
                    destinationPath = task.destinationPath,
                    status = task.status.label,
                    processedItems = task.processedItems,
                    totalBytes = task.totalBytes.coerceAtLeast(task.transferredBytes),
                    details = details,
                    timestamp = task.completionTimeMs ?: System.currentTimeMillis()
                )
            )
        }
    }

    fun pauseOperation(taskId: String) {
        val pauseFlow = pauseFlows[taskId] ?: return
        if (!pauseFlow.value) {
            pauseFlow.value = true
            pauseStartedAtMs[taskId] = SystemClock.elapsedRealtime()
            _tasks.update { list ->
                list.map {
                    if (it.id == taskId && it.status == OperationStatus.RUNNING) {
                        val paused = it.copy(status = OperationStatus.PAUSED, speedBytesPerSec = 0L)
                        notificationManager.notifyOperationState(paused)
                        paused
                    } else it
                }
            }
        }
    }

    fun resumeOperation(taskId: String) {
        val pauseFlow = pauseFlows[taskId] ?: return
        if (pauseFlow.value) {
            val startedPause = pauseStartedAtMs.remove(taskId)
            if (startedPause != null) {
                val delta = (SystemClock.elapsedRealtime() - startedPause).coerceAtLeast(0L)
                pauseAccumulatedMs[taskId] = (pauseAccumulatedMs[taskId] ?: 0L) + delta
            }
            pauseFlow.value = false
            _tasks.update { list ->
                list.map {
                    if (it.id == taskId && it.status == OperationStatus.PAUSED) {
                        val resumed = it.copy(status = OperationStatus.RUNNING)
                        notificationManager.notifyOperationState(resumed)
                        resumed
                    } else it
                }
            }
        }
    }

    fun cancelOperation(taskId: String) {
        // Unblock if paused first so cancellation propagates immediately
        pauseFlows[taskId]?.value = false
        val job = activeJobs[taskId]
        if (job != null && job.isActive) {
            _tasks.update { list ->
                list.map {
                    if (it.id == taskId) it.copy(status = OperationStatus.CANCELLING) else it
                }
            }
            job.cancel(CancellationException("Cancelled by user"))
        } else {
            _tasks.update { list ->
                list.map {
                    if (it.id == taskId && it.isActiveOrPaused) {
                        it.copy(
                            status = OperationStatus.CANCELLED,
                            indeterminate = false,
                            completionTimeMs = System.currentTimeMillis(),
                            errorDetail = "Cancelled by user"
                        )
                    } else it
                }
            }
            notificationManager.cancelNotification(taskId)
        }
    }

    fun retryOperation(taskId: String) {
        val retryAction = retryActions[taskId] ?: return
        dismissOperation(taskId)
        retryAction.invoke()
    }

    fun dismissOperation(taskId: String) {
        if (activeJobs.containsKey(taskId)) {
            cancelOperation(taskId)
        }
        _tasks.update { list -> list.filterNot { it.id == taskId } }
        retryActions.remove(taskId)
        notificationManager.cancelNotification(taskId)
    }

    fun clearCompletedOperations() {
        _tasks.update { list ->
            list.filter { it.isActiveOrPaused }
        }
        scope.launch(Dispatchers.IO) {
            runCatching { dao.clearOperationLogs() }
        }
    }

    fun pauseAllRunning() {
        val runningIds = _tasks.value.filter { it.status == OperationStatus.RUNNING && it.canPause }.map { it.id }
        runningIds.forEach { pauseOperation(it) }
    }

    fun resumeAllPaused() {
        val pausedIds = _tasks.value.filter { it.status == OperationStatus.PAUSED }.map { it.id }
        pausedIds.forEach { resumeOperation(it) }
    }

    fun retryAllFailed() {
        val failedIds = _tasks.value.filter { it.status == OperationStatus.FAILED && it.canRetry }.map { it.id }
        failedIds.forEach { retryOperation(it) }
    }
}
