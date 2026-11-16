package com.explorerpro.core.filesystem

import com.explorerpro.domain.model.BatchRenameConfig
import com.explorerpro.domain.model.BatchRenamePreviewItem
import com.explorerpro.domain.model.CaseTransform
import com.explorerpro.domain.model.FileEntry
import com.explorerpro.domain.model.FolderPlacement
import com.explorerpro.domain.model.GroupBy
import com.explorerpro.domain.model.SortConfig
import com.explorerpro.domain.model.SortField
import com.explorerpro.domain.model.SortOrder
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Provides natural numeric-aware sorting, multi-attribute comparators, grouping,
 * and batch rename preview/collision verification.
 */
object SortingAndGroupingEngine {

    /**
     * Natural numeric-aware comparator so "Report_2.pdf" sorts before "Report_10.pdf".
     */
    fun compareNaturalStrings(a: String, b: String, caseSensitive: Boolean): Int {
        val s1 = if (caseSensitive) a else a.lowercase(Locale.ROOT)
        val s2 = if (caseSensitive) b else b.lowercase(Locale.ROOT)
        var i1 = 0
        var i2 = 0
        val n1 = s1.length
        val n2 = s2.length

        while (i1 < n1 && i2 < n2) {
            val c1 = s1[i1]
            val c2 = s2[i2]
            if (c1.isDigit() && c2.isDigit()) {
                var numStart1 = i1
                while (i1 < n1 && s1[i1].isDigit()) i1++
                var numStart2 = i2
                while (i2 < n2 && s2[i2].isDigit()) i2++

                val numStr1 = s1.substring(numStart1, i1).trimStart('0').ifEmpty { "0" }
                val numStr2 = s2.substring(numStart2, i2).trimStart('0').ifEmpty { "0" }
                if (numStr1.length != numStr2.length) {
                    return numStr1.length.compareTo(numStr2.length)
                }
                val cmp = numStr1.compareTo(numStr2)
                if (cmp != 0) return cmp
            } else {
                if (c1 != c2) return c1.compareTo(c2)
                i1++
                i2++
            }
        }
        return (n1 - i1).compareTo(n2 - i2)
    }

    fun sortEntries(entries: List<FileEntry>, config: SortConfig): List<FileEntry> {
        val fieldComparator = Comparator<FileEntry> { a, b ->
            // 1. Apply folder placement rule first
            if (a.isDirectory != b.isDirectory) {
                return@Comparator when (config.folderPlacement) {
                    FolderPlacement.FOLDERS_FIRST -> if (a.isDirectory) -1 else 1
                    FolderPlacement.FILES_FIRST -> if (a.isDirectory) 1 else -1
                    FolderPlacement.FOLDERS_LAST -> if (a.isDirectory) 1 else -1
                }
            }

            // 2. Compare by chosen field (never fabricating missing values; nulls sort last)
            val rawComparison = when (config.field) {
                SortField.NAME_NATURAL ->
                    compareNaturalStrings(a.name, b.name, config.caseSensitive)
                SortField.SIZE ->
                    a.sizeBytes.compareTo(b.sizeBytes)
                SortField.MODIFIED_DATE ->
                    a.lastModified.compareTo(b.lastModified)
                SortField.CREATED_DATE ->
                    compareNullableLong(a.createdDate, b.createdDate)
                SortField.TYPE ->
                    compareNaturalStrings(
                        a.detectedFormat.category.label,
                        b.detectedFormat.category.label,
                        config.caseSensitive
                    )
                SortField.EXTENSION ->
                    compareNaturalStrings(a.extension, b.extension, config.caseSensitive)
                SortField.DETECTED_FORMAT ->
                    compareNaturalStrings(
                        a.detectedFormat.displayName,
                        b.detectedFormat.displayName,
                        config.caseSensitive
                    )
                SortField.DIMENSIONS -> {
                    val pixelsA = if (a.imageWidth != null && a.imageHeight != null) a.imageWidth.toLong() * a.imageHeight.toLong() else null
                    val pixelsB = if (b.imageWidth != null && b.imageHeight != null) b.imageWidth.toLong() * b.imageHeight.toLong() else null
                    compareNullableLong(pixelsA, pixelsB)
                }
                SortField.DURATION ->
                    compareNullableLong(a.durationMs, b.durationMs)
                SortField.FOLDER_ITEM_COUNT ->
                    compareNullableLong(a.directChildCount?.toLong(), b.directChildCount?.toLong())
            }

            val finalCmp = if (rawComparison != 0) {
                rawComparison
            } else {
                compareNaturalStrings(a.name, b.name, config.caseSensitive)
            }

            if (config.order == SortOrder.ASCENDING) finalCmp else -finalCmp
        }

        return entries.sortedWith(fieldComparator)
    }

    private fun compareNullableLong(a: Long?, b: Long?): Int {
        if (a == null && b == null) return 0
        if (a == null) return 1
        if (b == null) return -1
        return a.compareTo(b)
    }

    fun groupEntries(entries: List<FileEntry>, groupBy: GroupBy): Map<String, List<FileEntry>> {
        if (groupBy == GroupBy.NONE) {
            return mapOf("All Items (${entries.size})" to entries)
        }
        val dateFormatter = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        return entries.groupBy { entry ->
            when (groupBy) {
                GroupBy.NONE -> "All Items"
                GroupBy.FILES_VS_FOLDERS -> if (entry.isDirectory) "Folders" else "Files"
                GroupBy.TYPE -> entry.detectedFormat.category.label
                GroupBy.EXTENSION -> if (entry.isDirectory) "Folder" else entry.extension.uppercase().ifEmpty { "No Extension" }
                GroupBy.SIZE -> when {
                    entry.isDirectory -> "Folders"
                    entry.sizeBytes == 0L -> "Empty (0 B)"
                    entry.sizeBytes < 100 * 1024L -> "Tiny (< 100 KB)"
                    entry.sizeBytes < 10 * 1024 * 1024L -> "Medium (100 KB – 10 MB)"
                    else -> "Large (> 10 MB)"
                }
                GroupBy.MODIFIED_DATE -> if (entry.lastModified > 0) dateFormatter.format(Date(entry.lastModified)) else "Unknown Date"
                GroupBy.NAME -> entry.name.firstOrNull()?.uppercaseChar()?.toString() ?: "#"
            }
        }
    }

    /**
     * Generates a complete batch rename preview with duplicate and filesystem conflict detection.
     */
    fun generateBatchRenamePreview(
        files: List<FileEntry>,
        config: BatchRenameConfig,
        existingSiblingNames: Set<String> = emptySet()
    ): List<BatchRenamePreviewItem> {
        val dateStamp = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
        val proposedNamesInBatch = mutableMapOf<String, Int>()
        val selectedNamesSet = files.map { it.name }.toSet()
        val unselectedExistingSiblings = existingSiblingNames - selectedNamesSet

        val rawProposed = files.mapIndexed { index, entry ->
            val dotIndex = entry.name.lastIndexOf('.')
            val hasExt = !entry.isDirectory && dotIndex > 0
            var baseName = if (hasExt) entry.name.substring(0, dotIndex) else entry.name
            val originalExt = if (hasExt) entry.name.substring(dotIndex + 1) else ""

            // 1. Find & replace (standard or regex)
            if (config.findText.isNotEmpty()) {
                baseName = if (config.useRegex) {
                    runCatching {
                        Regex(config.findText).replace(baseName, config.replaceText)
                    }.getOrDefault(baseName)
                } else {
                    baseName.replace(config.findText, config.replaceText)
                }
            }

            // 2. Remove specified characters
            if (config.removeChars.isNotEmpty()) {
                val charsToRemove = config.removeChars.toSet()
                baseName = baseName.filterNot { it in charsToRemove }
            }

            // 3. Case conversion
            baseName = when (config.caseTransform) {
                CaseTransform.NONE -> baseName
                CaseTransform.LOWERCASE -> baseName.lowercase(Locale.ROOT)
                CaseTransform.UPPERCASE -> baseName.uppercase(Locale.ROOT)
                CaseTransform.TITLE_CASE -> baseName.split(' ', '_', '-').joinToString("_") { word ->
                    word.lowercase(Locale.ROOT).replaceFirstChar { it.titlecase(Locale.ROOT) }
                }
            }

            // 4. Prefix & Suffix
            baseName = "${config.prefix}$baseName${config.suffix}"

            // 5. Optional Date Stamp
            if (config.appendDateStamp) {
                baseName = "${baseName}_$dateStamp"
            }

            // 6. Optional Sequential Numbering
            if (config.addSequentialNumber) {
                val seqNum = (config.sequenceStart + index).toString().padStart(config.sequenceDigits.coerceIn(1, 6), '0')
                baseName = "${baseName}_$seqNum"
            }

            // 7. Extension handling
            val finalExt = config.overrideExtension.trim().trimStart('.').ifEmpty { originalExt }
            val fullName = if (!entry.isDirectory && finalExt.isNotEmpty()) {
                "$baseName.$finalExt"
            } else {
                baseName
            }

            proposedNamesInBatch[fullName] = (proposedNamesInBatch[fullName] ?: 0) + 1
            entry to fullName
        }

        return rawProposed.map { (entry, proposed) ->
            val conflictReason = when {
                proposed.isBlank() -> "Filename cannot be empty"
                proposed.contains('/') || proposed.contains('\\') -> "Invalid path separator in filename"
                (proposedNamesInBatch[proposed] ?: 0) > 1 -> "Duplicate target name within batch"
                unselectedExistingSiblings.contains(proposed) -> "File already exists in directory"
                else -> null
            }
            BatchRenamePreviewItem(
                originalFile = entry,
                proposedName = proposed,
                hasConflict = conflictReason != null,
                conflictReason = conflictReason
            )
        }
    }

    fun resolveNonConflictingFile(parentDir: File, desiredName: String): File {
        var candidate = File(parentDir, desiredName)
        if (!candidate.exists()) return candidate
        val dotIndex = desiredName.lastIndexOf('.')
        val base = if (dotIndex > 0) desiredName.substring(0, dotIndex) else desiredName
        val ext = if (dotIndex > 0) desiredName.substring(dotIndex) else ""
        var counter = 1
        while (candidate.exists()) {
            candidate = File(parentDir, "${base}_($counter)$ext")
            counter++
        }
        return candidate
    }
}
