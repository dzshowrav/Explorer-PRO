package com.explorerpro

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.explorerpro.core.domain.operations.OperationController
import com.explorerpro.core.filesystem.FileSystemRepository
import com.explorerpro.core.filesystem.TermuxStorageBridge
import com.explorerpro.core.security.ArchiveEngine
import com.explorerpro.domain.model.ArchiveCreateFormat
import com.explorerpro.domain.model.OperationStatus
import com.explorerpro.domain.model.OperationType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExplorerProRobolectricTest {

    @Test
    fun `verify real storage volumes, real file operations, Termux SAF bridge, and duplicate analysis`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Explorer Pro", appName)

        val repo = FileSystemRepository(context)
        repo.initializeRealWorkspaceDirectories()

        // Verify storage volumes do not include fake duplicate App Native / App External cards
        val volumes = repo.getAvailableStorageVolumes()
        assertTrue(volumes.any { it.id == "primary_shared" })
        assertFalse(volumes.any { it.title == "App Native Storage" })
        assertFalse(volumes.any { it.title == "App External Storage" })

        val testRoot = repo.appSandboxRoot.absolutePath
        repo.createDirectory(testRoot, "Scripts").getOrThrow()
        repo.createDirectory(testRoot, "Backups").getOrThrow()

        val scriptsPath = "$testRoot/Scripts"
        val backupsPath = "$testRoot/Backups"
        repo.createTextFile(scriptsPath, "termux_sync.sh", "#!/data/data/com.termux/files/usr/bin/bash\ngit status\n").getOrThrow()
        repo.createTextFile(backupsPath, "termux_sync_backup.sh", "#!/data/data/com.termux/files/usr/bin/bash\ngit status\n").getOrThrow()

        val rootEntries = repo.listDirectory(testRoot, showHidden = false).getOrThrow()
        assertTrue(rootEntries.any { it.name == "Scripts" && it.isDirectory })
        assertTrue(rootEntries.any { it.name == "Backups" && it.isDirectory })

        val stats = repo.calculateFolderIntelligence(testRoot, includeRecursive = true)
        assertTrue(stats.isComplete)
        assertTrue(stats.recursiveFileCount >= 2)

        val storageReport = repo.analyzeStorageAndDuplicates(testRoot)
        assertTrue(storageReport.duplicateGroups.isNotEmpty())

        // Verify Termux SAF URI construction, breadcrumb parsing, and parent resolution
        val termuxStatus = repo.queryTermuxStatus()
        assertNotNull(termuxStatus.defaultTermuxHomeUri)
        assertTrue(termuxStatus.defaultTermuxHomeUri.contains("com.termux.documents"))

        val termuxSubDocUri = android.provider.DocumentsContract.buildDocumentUriUsingTree(
            TermuxStorageBridge.buildTermuxHomeTreeUri(),
            "/data/data/com.termux/files/home/projects/explorer"
        ).toString()

        val crumbs = TermuxStorageBridge.parseSafBreadcrumbs(termuxSubDocUri)
        assertEquals(3, crumbs.size)
        assertEquals("Termux (\$HOME)", crumbs[0].first)
        assertEquals("projects", crumbs[1].first)
        assertEquals("explorer", crumbs[2].first)

        val parentUri = TermuxStorageBridge.resolveParentSafUri(termuxSubDocUri)
        assertNotNull(parentUri)
        assertTrue(parentUri!!.contains("projects"))
    }

    @Test
    fun `verify multi-format archive creation, inspection, verification, and extraction for ZIP, TAR, TAR_GZ, TAR_BZ2, TAR_XZ`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = FileSystemRepository(context)
        val engine = ArchiveEngine()
        val archiveRoot = File(repo.appSandboxRoot, "ArchiveTestSuite").apply {
            deleteRecursively()
            mkdirs()
        }

        val sourceDir = File(archiveRoot, "SourcePayload").apply { mkdirs() }
        val fileA = File(sourceDir, "readme.md").apply { writeText("# Explorer Pro Universal Archive Test\nLine 2\n") }
        val subDir = File(sourceDir, "nested").apply { mkdirs() }
        val fileB = File(subDir, "config.json").apply { writeText("""{"engine":"native","progress":100}""") }

        val formatsToTest = listOf(
            ArchiveCreateFormat.ZIP,
            ArchiveCreateFormat.TAR,
            ArchiveCreateFormat.TAR_GZ,
            ArchiveCreateFormat.TAR_BZ2,
            ArchiveCreateFormat.TAR_XZ
        )

        for (format in formatsToTest) {
            val archiveFile = File(archiveRoot, "bundle.${format.extension}")
            var lastProgressCount = 0
            val created = engine.createArchive(
                sourceFiles = listOf(sourceDir),
                outputArchiveFile = archiveFile,
                format = format,
                progressListener = { _, processed, _, _, _, _ -> lastProgressCount = processed }
            ).getOrThrow()

            assertTrue("Created archive must exist for ${format.name}", created.exists() && created.length() > 0L)
            assertTrue("Progress callback must have reported entries for ${format.name}", lastProgressCount >= 2)

            // Verify magic-byte & extension detection marks it as extractable archive
            val listedInDir = repo.listDirectory(archiveRoot.absolutePath, showHidden = false).getOrThrow()
            val archiveEntry = listedInDir.first { it.name == archiveFile.name }
            assertTrue("Entry ${archiveEntry.name} must be marked isExtractableArchive", archiveEntry.isExtractableArchive)

            // List archive entries
            val entries = engine.listArchiveEntries(archiveFile).getOrThrow()
            assertTrue("Listed entries should contain readme.md for ${format.name}", entries.any { it.entryPath.endsWith("readme.md") })
            assertTrue("Listed entries should contain nested/config.json for ${format.name}", entries.any { it.entryPath.endsWith("config.json") })

            // Verify integrity
            val integrityMsg = engine.verifyArchiveIntegrity(archiveFile).getOrThrow()
            assertTrue("Archive integrity check must pass for ${format.name}", integrityMsg.contains("OK"))

            // Extract archive directly
            val extractDest = File(archiveRoot, "Extracted_${format.name}")
            val extractedCount = engine.extractArchiveDetailed(
                archiveFile = archiveFile,
                destinationDir = extractDest
            ).getOrThrow()
            assertTrue("Extracted count must be >= 2 for ${format.name}", extractedCount >= 2)

            val extractedReadme = extractDest.walkTopDown().firstOrNull { it.name == "readme.md" }
            val extractedConfig = extractDest.walkTopDown().firstOrNull { it.name == "config.json" }
            assertNotNull("readme.md must exist after extracting ${format.name}", extractedReadme)
            assertNotNull("config.json must exist after extracting ${format.name}", extractedConfig)
            assertEquals(fileA.readText(), extractedReadme!!.readText())
            assertEquals(fileB.readText(), extractedConfig!!.readText())
        }
    }

    @Test
    fun `verify OperationController progress clamping, speed calculation, and completion lifecycle`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val dao = com.explorerpro.data.local.ExplorerDatabase.getInstance(context).explorerDao()
        val controller = OperationController(
            context = context,
            scope = scope,
            dao = dao
        )

        val taskId = controller.launchOperation(
            type = OperationType.ARCHIVE_EXTRACT,
            title = "Extracting bundle.tar.xz",
            sourcePaths = listOf("/sdcard/Download/bundle.tar.xz"),
            destinationPath = "/sdcard/Download/bundle",
            totalItems = 4,
            totalBytes = 4000L,
            indeterminate = false
        ) { execScope ->
            execScope.reportProgress(
                fraction = 0.4f,
                processedItems = 2,
                totalItems = 4,
                transferredBytes = 1600L,
                totalBytes = 4000L,
                currentItemName = "entry1.bin"
            )
            execScope.reportProgress(
                fraction = 1.0f,
                processedItems = 4,
                totalItems = 4,
                transferredBytes = 4000L,
                totalBytes = 4000L,
                currentItemName = "entry4.bin"
            )
            Result.success(4)
        }

        // Wait briefly for coroutine completion
        var attempts = 0
        while (attempts < 20 && controller.tasks.value.firstOrNull { it.id == taskId }?.status != OperationStatus.COMPLETED) {
            delay(25)
            attempts++
        }

        val completedTask = controller.tasks.value.firstOrNull { it.id == taskId }
        assertNotNull(completedTask)
        assertEquals(OperationStatus.COMPLETED, completedTask!!.status)
        assertEquals(100f, completedTask.progressValue, 0.01f)
        assertEquals(4, completedTask.processedItems)
    }

    @Test
    fun `verify FAB QuickActionResolver, SelectionActionResolver, read-only directory policy, clipboard priority, and FAB settings persistence`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = FileSystemRepository(context)
        val writableDir = repo.appSandboxRoot.absolutePath

        // 1. Default Main Explorer in writable directory
        val baseContext = com.explorerpro.core.domain.actions.FabResolutionContext(
            destination = com.explorerpro.core.domain.actions.ExplorerScreenDestination.MAIN_EXPLORER,
            currentPath = writableDir,
            isDirectoryWritable = com.explorerpro.core.domain.actions.FabVisibilityPolicy.isDirectoryWritable(writableDir),
            selectedItems = emptyList(),
            clipboard = com.explorerpro.domain.model.ClipboardState(),
            dualPaneEnabled = false,
            activePaneId = com.explorerpro.domain.model.PaneId.LEFT,
            isSearchActive = false,
            isRecursiveSearch = true,
            isScrollingDown = false,
            isKeyboardOpen = false
        )
        val defaultFab = com.explorerpro.core.domain.actions.QuickActionResolver.resolve(baseContext)
        assertTrue(defaultFab.visible)
        assertFalse(defaultFab.isSelectionMode)
        assertTrue(defaultFab.speedDialActions.any { it.id == com.explorerpro.core.domain.actions.QuickActionId.NEW_FOLDER })
        assertTrue(defaultFab.speedDialActions.any { it.id == com.explorerpro.core.domain.actions.QuickActionId.NEW_FILE })
        assertTrue(defaultFab.speedDialActions.any { it.id == com.explorerpro.core.domain.actions.QuickActionId.IMPORT_FILES })

        // 2. Read-only directory (/system) hides creation/import/paste actions
        assertFalse(com.explorerpro.core.domain.actions.FabVisibilityPolicy.isDirectoryWritable("/system"))
        val readOnlyFab = com.explorerpro.core.domain.actions.QuickActionResolver.resolve(
            baseContext.copy(
                currentPath = "/system",
                isDirectoryWritable = false
            )
        )
        assertTrue(readOnlyFab.visible)
        assertTrue(readOnlyFab.isReadOnlyContext)
        assertFalse(readOnlyFab.speedDialActions.any { it.id == com.explorerpro.core.domain.actions.QuickActionId.NEW_FOLDER })
        assertFalse(readOnlyFab.speedDialActions.any { it.id == com.explorerpro.core.domain.actions.QuickActionId.NEW_FILE })
        assertFalse(readOnlyFab.speedDialActions.any { it.id == com.explorerpro.core.domain.actions.QuickActionId.IMPORT_FILES })

        // 3. Clipboard Paste prioritization when clipboard contains items
        val sampleArchiveEntry = com.explorerpro.domain.model.FileEntry(
            path = "$writableDir/backup.tar.gz",
            name = "backup.tar.gz",
            extension = "gz",
            mimeType = "application/gzip",
            detectedFormat = com.explorerpro.domain.model.DetectedFileFormat.GZIP,
            sizeBytes = 2048L,
            lastModified = System.currentTimeMillis(),
            createdDate = System.currentTimeMillis(),
            isDirectory = false,
            isHidden = false,
            permissions = "-rw-r--r--"
        )
        val clipboardFab = com.explorerpro.core.domain.actions.QuickActionResolver.resolve(
            baseContext.copy(
                clipboard = com.explorerpro.domain.model.ClipboardState(
                    sourceFiles = listOf(sampleArchiveEntry),
                    action = com.explorerpro.domain.model.ClipboardAction.COPY
                )
            )
        )
        assertEquals(
            com.explorerpro.core.domain.actions.QuickActionId.PASTE_CLIPBOARD,
            clipboardFab.primaryDirectAction?.id
        )

        // 4. Single Archive Selection resolves Extract Here, Extract to Folder, Inspect, and Delete (with confirmation)
        val archiveSelectionFab = com.explorerpro.core.domain.actions.QuickActionResolver.resolve(
            baseContext.copy(
                selectedItems = listOf(sampleArchiveEntry),
                dualPaneEnabled = true
            )
        )
        assertTrue(archiveSelectionFab.isSelectionMode)
        assertEquals("Archive Actions", archiveSelectionFab.primaryTitle)
        assertTrue(archiveSelectionFab.speedDialActions.any { it.id == com.explorerpro.core.domain.actions.QuickActionId.EXTRACT_ARCHIVE_HERE })
        assertTrue(archiveSelectionFab.speedDialActions.any { it.id == com.explorerpro.core.domain.actions.QuickActionId.EXTRACT_ARCHIVE_TO_FOLDER })
        assertTrue(archiveSelectionFab.speedDialActions.any { it.id == com.explorerpro.core.domain.actions.QuickActionId.DUAL_COPY_TO_OTHER_PANE })
        val deleteAction = archiveSelectionFab.speedDialActions.first { it.id == com.explorerpro.core.domain.actions.QuickActionId.DELETE_SELECTION }
        assertTrue(deleteAction.isDestructive && deleteAction.requiresConfirmation)

        // 5. Scroll & Keyboard auto-hide visibility policy
        val scrollingHidden = com.explorerpro.core.domain.actions.QuickActionResolver.resolve(
            baseContext.copy(isScrollingDown = true)
        )
        assertFalse(scrollingHidden.visible)

        val keyboardHidden = com.explorerpro.core.domain.actions.QuickActionResolver.resolve(
            baseContext.copy(isKeyboardOpen = true)
        )
        assertFalse(keyboardHidden.visible)
    }

    @Test
    fun `verify ExplorerInputValidator rules, transfer conflict detection, and perSourceResolution in FileSystemRepository`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = FileSystemRepository(context)
        val validator = com.explorerpro.domain.model.ExplorerInputValidator

        // 1. Validate filename rules
        assertTrue(validator.validateFileOrFolderName("notes_2026.txt").isValid)
        assertFalse(validator.validateFileOrFolderName("").isValid)
        assertFalse(validator.validateFileOrFolderName("   ").isValid)
        assertFalse(validator.validateFileOrFolderName("..").isValid)
        assertFalse(validator.validateFileOrFolderName(".").isValid)
        assertFalse(validator.validateFileOrFolderName("sub/path.txt").isValid)
        assertFalse(validator.validateFileOrFolderName("sub\\path.txt").isValid)
        assertFalse(validator.validateFileOrFolderName("CON").isValid)
        assertFalse(validator.validateFileOrFolderName("NUL.txt").isValid)
        assertFalse(
            validator.validateFileOrFolderName(
                rawInput = "duplicate.txt",
                existingSiblingNames = setOf("Duplicate.txt"),
                originalName = "original.txt"
            ).isValid
        )
        assertTrue(
            validator.validateFileOrFolderName(
                rawInput = "original.txt",
                existingSiblingNames = setOf("original.txt"),
                originalName = "original.txt"
            ).isValid
        )

        // 2. Verify findTransferConflicts and perSourceResolution (REPLACE, SKIP, KEEP_BOTH, CUSTOM_RENAME)
        val conflictSuiteDir = File(repo.appSandboxRoot, "ConflictResolutionSuite").apply {
            deleteRecursively()
            mkdirs()
        }
        val srcDir = File(conflictSuiteDir, "src").apply { mkdirs() }
        val dstDir = File(conflictSuiteDir, "dst").apply { mkdirs() }

        val srcReplace = File(srcDir, "alpha.txt").apply { writeText("NEW_ALPHA_CONTENT_12345") }
        val srcSkip = File(srcDir, "beta.txt").apply { writeText("NEW_BETA_CONTENT_12345") }
        val srcKeepBoth = File(srcDir, "gamma.txt").apply { writeText("NEW_GAMMA_CONTENT_12345") }
        val srcCustom = File(srcDir, "delta.txt").apply { writeText("NEW_DELTA_CONTENT_12345") }

        File(dstDir, "alpha.txt").apply { writeText("OLD_ALPHA") }
        File(dstDir, "beta.txt").apply { writeText("OLD_BETA") }
        File(dstDir, "gamma.txt").apply { writeText("OLD_GAMMA") }
        File(dstDir, "delta.txt").apply { writeText("OLD_DELTA") }

        val sourceEntries = repo.listDirectory(srcDir.absolutePath, showHidden = true).getOrThrow()
        assertEquals(4, sourceEntries.size)

        val conflicts = repo.findTransferConflicts(
            sourceEntries = sourceEntries,
            destinationDirPath = dstDir.absolutePath
        )
        assertEquals(4, conflicts.size)

        val resolutionMap = mapOf(
            srcReplace.absolutePath to (com.explorerpro.domain.model.FileConflictDecision.REPLACE to null),
            srcSkip.absolutePath to (com.explorerpro.domain.model.FileConflictDecision.SKIP to null),
            srcKeepBoth.absolutePath to (com.explorerpro.domain.model.FileConflictDecision.KEEP_BOTH to null),
            srcCustom.absolutePath to (com.explorerpro.domain.model.FileConflictDecision.CUSTOM_RENAME to "delta_custom.txt")
        )

        val transferredCount = repo.transferFiles(
            sourceEntries = sourceEntries,
            destinationDirPath = dstDir.absolutePath,
            isMove = false,
            conflictPolicy = com.explorerpro.domain.model.ConflictResolution.RENAME,
            perSourceResolution = resolutionMap,
            onProgress = { _, _, _, _, _, _ -> }
        ).getOrThrow()

        // 3 files transferred (alpha replaced, gamma kept both as gamma_(1).txt, delta renamed to delta_custom.txt; beta skipped)
        assertEquals(3, transferredCount)
        assertEquals("NEW_ALPHA_CONTENT_12345", File(dstDir, "alpha.txt").readText())
        assertEquals("OLD_BETA", File(dstDir, "beta.txt").readText())
        assertEquals("OLD_GAMMA", File(dstDir, "gamma.txt").readText())
        assertEquals("NEW_GAMMA_CONTENT_12345", File(dstDir, "gamma_(1).txt").readText())
        assertEquals("OLD_DELTA", File(dstDir, "delta.txt").readText())
        assertEquals("NEW_DELTA_CONTENT_12345", File(dstDir, "delta_custom.txt").readText())

        // 3. Verify First-Launch Permission Status & Real OS Settings Intents
        val permStatus = repo.queryAppPermissionStatus()
        assertTrue(permStatus.requiresManageExternalStorage)
        assertTrue(permStatus.requiresNotificationPermission)
        val ungrantedModel = com.explorerpro.domain.model.AppPermissionStatus(
            sdkInt = 34,
            requiresManageExternalStorage = true,
            isAllFilesAccessGranted = false,
            requiresRuntimeStoragePermissions = false,
            requiresNotificationPermission = true,
            isNotificationPermissionGranted = false
        )
        assertFalse(ungrantedModel.areAllCorePermissionsGranted)
        assertTrue(ungrantedModel.missingRuntimePermissions().contains("android.permission.POST_NOTIFICATIONS"))

        val legacyUngranted = com.explorerpro.domain.model.AppPermissionStatus(
            sdkInt = 29,
            requiresManageExternalStorage = false,
            isAllFilesAccessGranted = false,
            requiresRuntimeStoragePermissions = true,
            isRuntimeReadStorageGranted = false,
            isRuntimeWriteStorageGranted = false,
            requiresNotificationPermission = false,
            isNotificationPermissionGranted = true
        )
        assertTrue(legacyUngranted.missingRuntimePermissions().contains("android.permission.READ_EXTERNAL_STORAGE"))
        assertTrue(legacyUngranted.missingRuntimePermissions().contains("android.permission.WRITE_EXTERNAL_STORAGE"))

        val allFilesIntent = repo.createAllFilesAccessIntent()
        assertEquals(android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, allFilesIntent.action)
        assertEquals("package:${context.packageName}", allFilesIntent.dataString)

        val notifIntent = repo.createNotificationSettingsIntent()
        assertEquals(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS, notifIntent.action)
        assertEquals(context.packageName, notifIntent.getStringExtra(android.provider.Settings.EXTRA_APP_PACKAGE))
    }

    @Test
    fun `verify PreferencesManager persists theme, explorer options, recycle bin, conflict policy, and FAB settings`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefsManager = com.explorerpro.data.local.PreferencesManager(context)

        prefsManager.setThemePreference(com.explorerpro.domain.model.ThemePreference.LIGHT)
        prefsManager.setDynamicColor(true)
        prefsManager.setShowHiddenFiles(true)
        prefsManager.setDualPaneEnabled(true)
        prefsManager.setRecycleBinEnabled(false)
        prefsManager.setConflictResolution(com.explorerpro.domain.model.ConflictResolution.REPLACE)
        prefsManager.updateFabSettings(
            com.explorerpro.domain.model.FabCustomizationSettings(
                fabStyleMode = com.explorerpro.domain.model.FabStyleMode.EXTENDED,
                speedDialDirection = com.explorerpro.domain.model.SpeedDialDirection.HORIZONTAL,
                showSpeedDialLabels = false,
                animationsEnabled = false,
                hideWhileScrolling = false,
                adaptToSelection = false,
                hideWhenKeyboardOpen = false,
                placementMode = com.explorerpro.domain.model.FabPlacementMode.BOTTOM_END,
                themeMode = com.explorerpro.domain.model.FabThemeMode.CUSTOM_ACCENT
            )
        )

        val saved = prefsManager.preferencesFlow.first()
        assertTrue(saved.isLoadedFromDisk)
        assertEquals(com.explorerpro.domain.model.ThemePreference.LIGHT, saved.themePreference)
        assertTrue(saved.dynamicColorEnabled)
        assertTrue(saved.showHiddenFiles)
        assertTrue(saved.dualPaneEnabled)
        assertFalse(saved.recycleBinEnabled)
        assertEquals(com.explorerpro.domain.model.ConflictResolution.REPLACE, saved.defaultConflictResolution)
        assertEquals(com.explorerpro.domain.model.FabStyleMode.EXTENDED, saved.fabSettings.fabStyleMode)
        assertEquals(com.explorerpro.domain.model.SpeedDialDirection.HORIZONTAL, saved.fabSettings.speedDialDirection)
        assertFalse(saved.fabSettings.showSpeedDialLabels)
        assertFalse(saved.fabSettings.animationsEnabled)
        assertFalse(saved.fabSettings.hideWhileScrolling)
        assertFalse(saved.fabSettings.adaptToSelection)
        assertFalse(saved.fabSettings.hideWhenKeyboardOpen)
        assertEquals(com.explorerpro.domain.model.FabPlacementMode.BOTTOM_END, saved.fabSettings.placementMode)
        assertEquals(com.explorerpro.domain.model.FabThemeMode.CUSTOM_ACCENT, saved.fabSettings.themeMode)
    }

    @Test
    fun `verify extended 16 archive formats, AES-256 ZIP encryption, and cross-format archive conversion`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = FileSystemRepository(context)
        val engine = ArchiveEngine()
        val testDir = File(repo.appSandboxRoot, "ExtendedArchiveSuite").apply {
            deleteRecursively()
            mkdirs()
        }

        val payloadDir = File(testDir, "Payload").apply { mkdirs() }
        val docFile = File(payloadDir, "manifest.txt").apply {
            writeText("Explorer Pro Multi-Format Archive Verification Payload\nLine Two\n")
        }
        val nestedDir = File(payloadDir, "data").apply { mkdirs() }
        val jsonFile = File(nestedDir, "settings.json").apply {
            writeText("""{"zstd":true,"lz4":true,"aes256":true}""")
        }

        // 1. Test extended multi-file container & compressor formats
        // (Note: TAR_ZSTD, ZSTD, and TAR_LZMA are verified in pure JVM ExplorerProUnitTest because
        // Robolectric's android-all-instrumented-16 jar shadows those packages with AOSP stub classes.)
        val extendedFormats = listOf(
            ArchiveCreateFormat.SEVEN_Z,
            ArchiveCreateFormat.TAR_LZ4,
            ArchiveCreateFormat.JAR,
            ArchiveCreateFormat.CPIO
        )

        for (fmt in extendedFormats) {
            val outFile = File(testDir, "extended_pack.${fmt.extension}")
            val created = engine.createArchive(
                sourceFiles = listOf(payloadDir),
                outputArchiveFile = outFile,
                format = fmt,
                compressionLevel = com.explorerpro.domain.model.ArchiveCompressionLevel.FAST
            ).getOrThrow()

            assertTrue("Created ${fmt.name} archive must exist", created.exists() && created.length() > 0L)

            val entries = engine.listArchiveEntries(created).getOrThrow()
            assertTrue("Listed entries should include manifest.txt for ${fmt.name}", entries.any { it.entryPath.endsWith("manifest.txt") })
            assertTrue("Listed entries should include settings.json for ${fmt.name}", entries.any { it.entryPath.endsWith("settings.json") })

            val verifyMsg = engine.verifyArchiveIntegrity(created).getOrThrow()
            assertTrue("Integrity check must succeed for ${fmt.name}: $verifyMsg", verifyMsg.contains("OK"))

            val extractDir = File(testDir, "Out_${fmt.name}")
            val count = engine.extractArchiveDetailed(created, extractDir).getOrThrow()
            assertTrue("Must extract at least 2 files for ${fmt.name}", count >= 2)

            val extractedDoc = extractDir.walkTopDown().firstOrNull { it.name == "manifest.txt" }
            val extractedJson = extractDir.walkTopDown().firstOrNull { it.name == "settings.json" }
            assertEquals(docFile.readText(), extractedDoc?.readText())
            assertEquals(jsonFile.readText(), extractedJson?.readText())
        }

        // 2. Test single-stream formats (.gz, .bz2, .xz, .lz4) on a single file
        val singleStreamFormats = listOf(
            ArchiveCreateFormat.GZ,
            ArchiveCreateFormat.BZ2,
            ArchiveCreateFormat.XZ,
            ArchiveCreateFormat.LZ4
        )
        for (fmt in singleStreamFormats) {
            val outFile = File(testDir, "single_doc.${fmt.extension}")
            val created = engine.createArchive(
                sourceFiles = listOf(docFile),
                outputArchiveFile = outFile,
                format = fmt,
                compressionLevel = com.explorerpro.domain.model.ArchiveCompressionLevel.NORMAL
            ).getOrThrow()
            assertTrue("Created single-stream ${fmt.name} must exist", created.exists() && created.length() > 0L)

            val extractDir = File(testDir, "OutSingle_${fmt.name}")
            val count = engine.extractArchiveDetailed(created, extractDir).getOrThrow()
            assertEquals(1, count)
            val extractedSingle = extractDir.walkTopDown().firstOrNull { it.isFile }
            assertNotNull("Extracted single-stream file missing for ${fmt.name}", extractedSingle)
            assertEquals(docFile.readText(), extractedSingle!!.readText())
        }

        // 3. Test AES-256 Encrypted ZIP creation, wrong password rejection, and right password extraction
        val secretZip = File(testDir, "encrypted_payload.zip")
        val secretPass = "ExplorerPro#2026"
        engine.createArchive(
            sourceFiles = listOf(payloadDir),
            outputArchiveFile = secretZip,
            format = ArchiveCreateFormat.ZIP,
            compressionLevel = com.explorerpro.domain.model.ArchiveCompressionLevel.MAXIMUM,
            encryptionMode = com.explorerpro.domain.model.ArchiveEncryptionMode.AES_256,
            password = secretPass
        ).getOrThrow()
        assertTrue(secretZip.exists() && secretZip.length() > 0L)

        // Wrong password must fail
        val wrongExtractResult = engine.extractArchiveDetailed(
            archiveFile = secretZip,
            destinationDir = File(testDir, "WrongPassOut"),
            password = "WrongPassword!"
        )
        assertTrue("Extraction with wrong password must fail", wrongExtractResult.isFailure)

        // Correct password must succeed
        val rightExtractDir = File(testDir, "RightPassOut")
        val rightCount = engine.extractArchiveDetailed(
            archiveFile = secretZip,
            destinationDir = rightExtractDir,
            password = secretPass
        ).getOrThrow()
        assertTrue(rightCount >= 2)
        assertEquals(docFile.readText(), rightExtractDir.walkTopDown().first { it.name == "manifest.txt" }.readText())
        assertEquals(jsonFile.readText(), rightExtractDir.walkTopDown().first { it.name == "settings.json" }.readText())

        // 4. Test cross-format Archive Conversion via Context (encrypted ZIP -> TAR.LZ4 -> 7Z)
        val convertedLz4 = File(testDir, "converted_archive.tar.lz4")
        engine.convertArchive(
            context = context,
            sourcePathOrUri = secretZip.absolutePath,
            sourceDisplayName = secretZip.name,
            outputArchiveFile = convertedLz4,
            targetFormat = ArchiveCreateFormat.TAR_LZ4,
            compressionLevel = com.explorerpro.domain.model.ArchiveCompressionLevel.FAST,
            sourcePassword = secretPass
        ).getOrThrow()
        assertTrue(convertedLz4.exists() && convertedLz4.length() > 0L)

        val converted7z = File(testDir, "converted_final.7z")
        engine.convertArchive(
            context = context,
            sourcePathOrUri = convertedLz4.absolutePath,
            sourceDisplayName = convertedLz4.name,
            outputArchiveFile = converted7z,
            targetFormat = ArchiveCreateFormat.SEVEN_Z
        ).getOrThrow()
        assertTrue(converted7z.exists() && converted7z.length() > 0L)

        val finalCheckDir = File(testDir, "FinalConvertedCheck")
        val finalCount = engine.extractArchiveDetailed(converted7z, finalCheckDir).getOrThrow()
        assertTrue(finalCount >= 2)
        assertEquals(docFile.readText(), finalCheckDir.walkTopDown().first { it.name == "manifest.txt" }.readText())
        assertEquals(jsonFile.readText(), finalCheckDir.walkTopDown().first { it.name == "settings.json" }.readText())
    }
}
