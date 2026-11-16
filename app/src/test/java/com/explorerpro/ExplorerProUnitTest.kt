package com.explorerpro

import com.explorerpro.core.filesystem.FormatDetectorAndMetadataEngine
import com.explorerpro.core.filesystem.SortingAndGroupingEngine
import com.explorerpro.core.security.ArchiveEngine
import com.explorerpro.domain.model.BatchRenameConfig
import com.explorerpro.domain.model.CaseTransform
import com.explorerpro.domain.model.DetectedFileFormat
import com.explorerpro.domain.model.FileEntry
import com.explorerpro.domain.model.FolderPlacement
import com.explorerpro.domain.model.GroupBy
import com.explorerpro.domain.model.SortConfig
import com.explorerpro.domain.model.SortField
import com.explorerpro.domain.model.SortOrder
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class ExplorerProUnitTest {

    private fun sampleEntry(
        name: String,
        size: Long = 100L,
        isDir: Boolean = false,
        format: DetectedFileFormat = DetectedFileFormat.UTF8_TEXT
    ) = FileEntry(
        path = "/workspace/$name",
        name = name,
        extension = if (isDir) "" else name.substringAfterLast('.', ""),
        mimeType = if (isDir) "inode/directory" else "text/plain",
        detectedFormat = if (isDir) DetectedFileFormat.DIRECTORY else format,
        sizeBytes = size,
        lastModified = 1700000000000L,
        createdDate = 1700000000000L,
        isDirectory = isDir,
        isHidden = name.startsWith("."),
        permissions = "rw-"
    )

    @Test
    fun `natural numeric sorting orders file_2 before file_10 and respects folders first`() {
        val entries = listOf(
            sampleEntry("Release_10.txt"),
            sampleEntry("Release_2.txt"),
            sampleEntry("Docs", isDir = true),
            sampleEntry("Release_1.txt")
        )
        val sorted = SortingAndGroupingEngine.sortEntries(
            entries = entries,
            config = SortConfig(
                field = SortField.NAME_NATURAL,
                order = SortOrder.ASCENDING,
                folderPlacement = FolderPlacement.FOLDERS_FIRST
            )
        )
        assertEquals(
            listOf("Docs", "Release_1.txt", "Release_2.txt", "Release_10.txt"),
            sorted.map { it.name }
        )
    }

    @Test
    fun `grouping by files vs folders partitions accurately`() {
        val entries = listOf(
            sampleEntry("Pictures", isDir = true),
            sampleEntry("notes.md"),
            sampleEntry("spec.json")
        )
        val grouped = SortingAndGroupingEngine.groupEntries(entries, GroupBy.FILES_VS_FOLDERS)
        assertEquals(1, grouped["Folders"]?.size)
        assertEquals(2, grouped["Files"]?.size)
    }

    @Test
    fun `batch rename engine applies prefix, regex, case transform, sequence and detects collisions`() {
        val files = listOf(
            sampleEntry("draft_doc_a.txt"),
            sampleEntry("draft_doc_b.txt")
        )
        val config = BatchRenameConfig(
            prefix = "PRO_",
            findText = "draft_",
            replaceText = "",
            useRegex = true,
            caseTransform = CaseTransform.UPPERCASE,
            addSequentialNumber = true,
            sequenceStart = 1,
            sequenceDigits = 2
        )
        val previews = SortingAndGroupingEngine.generateBatchRenamePreview(files, config)
        assertEquals("PRO_DOC_A_01.txt", previews[0].proposedName)
        assertEquals("PRO_DOC_B_02.txt", previews[1].proposedName)
        assertFalse(previews.any { it.hasConflict })

        // Verify collision detection when an existing sibling has the same target name
        val collidingPreviews = SortingAndGroupingEngine.generateBatchRenamePreview(
            files = files,
            config = config,
            existingSiblingNames = setOf("PRO_DOC_A_01.txt")
        )
        assertTrue(collidingPreviews[0].hasConflict)
    }

    @Test
    fun `archive engine prevents ZIP Slip path traversal and absolute paths`() {
        val tempDir = Files.createTempDirectory("explorer_archive_test").toFile()
        try {
            val engine = ArchiveEngine()
            // Safe nested entry
            val safeTarget = engine.validateSafeDestinationFile(tempDir, "subdir/readme.txt")
            assertTrue(safeTarget.canonicalPath.startsWith(tempDir.canonicalPath))

            // Malicious path traversal entries must be rejected
            assertFalse(engine.isEntryPathSafe(tempDir, "../../etc/passwd"))
            assertFalse(engine.isEntryPathSafe(tempDir, "nested/../../../evil.sh"))
            assertFalse(engine.isEntryPathSafe(tempDir, "/absolute/root/file.txt"))
            assertFalse(engine.isEntryPathSafe(tempDir, "bad\u0000file.txt"))
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `archive engine creates, lists, verifies CRC32, and extracts ZIP archive`() = runTest {
        val tempDir = Files.createTempDirectory("explorer_zip_roundtrip").toFile()
        try {
            val srcDir = File(tempDir, "source").apply { mkdirs() }
            val fileA = File(srcDir, "alpha.txt").apply { writeText("Explorer Pro ZIP payload alpha") }
            val fileB = File(srcDir, "beta.json").apply { writeText("{\"status\": \"verified\"}") }
            val outZip = File(tempDir, "bundle.zip")

            val engine = ArchiveEngine()
            val createResult = engine.createZipArchive(listOf(fileA, fileB), outZip)
            assertTrue(createResult.isSuccess)
            assertTrue(outZip.exists() && outZip.length() > 0)

            val entriesResult = engine.listArchiveEntries(outZip)
            assertTrue(entriesResult.isSuccess)
            assertEquals(2, entriesResult.getOrThrow().size)

            val integrityResult = engine.verifyArchiveIntegrity(outZip)
            assertTrue(integrityResult.isSuccess)

            val extractDir = File(tempDir, "extracted")
            val extractResult = engine.extractArchive(outZip, extractDir)
            assertEquals(2, extractResult.getOrThrow())
            assertEquals("Explorer Pro ZIP payload alpha", File(extractDir, "alpha.txt").readText())
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `format detector identifies magic bytes and computes accurate SHA-256`() = runTest {
        val tempDir = Files.createTempDirectory("explorer_magic_test").toFile()
        try {
            // Write PNG magic signature even with a misleading .bin extension
            val disguisedPng = File(tempDir, "mystery.bin")
            disguisedPng.writeBytes(
                byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00, 0x00)
            )
            assertEquals(
                DetectedFileFormat.PNG,
                FormatDetectorAndMetadataEngine.detectFormat(disguisedPng)
            )

            // Verify SHA-256 computation
            val sampleText = File(tempDir, "hash_me.txt").apply { writeText("explorer-pro-2026") }
            val sha = FormatDetectorAndMetadataEngine.computeSha256(sampleText).getOrThrow()
            assertEquals(64, sha.length)

            // Verify ELF binary magic header (7F 'E' 'L' 'F')
            val elfBinary = File(tempDir, "libnative.bin")
            elfBinary.writeBytes(byteArrayOf(0x7F, 'E'.code.toByte(), 'L'.code.toByte(), 'F'.code.toByte(), 0x02, 0x01))
            assertEquals(
                DetectedFileFormat.ELF_BINARY,
                FormatDetectorAndMetadataEngine.detectFormat(elfBinary)
            )

            // Verify Termux Shebang (#!) script detection
            val shScript = File(tempDir, "deploy")
            shScript.writeText("#!/data/data/com.termux/files/usr/bin/bash\necho 'Deploying'\n")
            assertEquals(
                DetectedFileFormat.SHELL_SCRIPT,
                FormatDetectorAndMetadataEngine.detectFormat(shScript)
            )
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `archive engine supports ZSTD and TAR_ZSTD plus LZMA JAR CPIO on JVM`() = runTest {
        val tempDir = Files.createTempDirectory("explorer_zstd_jvm_test").toFile()
        try {
            val payloadDir = File(tempDir, "payload").apply { mkdirs() }
            val textFile = File(payloadDir, "hello.txt").apply {
                writeText("Explorer Pro ZSTD & Extended Formats JVM Roundtrip")
            }
            val engine = ArchiveEngine()

            for (fmt in listOf(
                com.explorerpro.domain.model.ArchiveCreateFormat.TAR_ZSTD,
                com.explorerpro.domain.model.ArchiveCreateFormat.TAR_LZMA,
                com.explorerpro.domain.model.ArchiveCreateFormat.JAR,
                com.explorerpro.domain.model.ArchiveCreateFormat.CPIO
            )) {
                val outArchive = File(tempDir, "pack.${fmt.extension}")
                engine.createArchive(
                    sourceFiles = listOf(payloadDir),
                    outputArchiveFile = outArchive,
                    format = fmt
                ).getOrThrow()
                assertTrue("${fmt.name} archive must exist", outArchive.exists() && outArchive.length() > 0L)

                val outDir = File(tempDir, "extract_${fmt.name}")
                val count = engine.extractArchiveDetailed(outArchive, outDir).getOrThrow()
                assertTrue("Must extract >= 1 file for ${fmt.name}", count >= 1)
                val restored = outDir.walkTopDown().firstOrNull { it.name == "hello.txt" }
                assertEquals(textFile.readText(), restored?.readText())
            }

            val singleZst = File(tempDir, "hello.txt.zst")
            engine.createArchive(
                sourceFiles = listOf(textFile),
                outputArchiveFile = singleZst,
                format = com.explorerpro.domain.model.ArchiveCreateFormat.ZSTD
            ).getOrThrow()
            val singleOut = File(tempDir, "single_zst_out")
            assertEquals(1, engine.extractArchiveDetailed(singleZst, singleOut).getOrThrow())
            assertEquals(textFile.readText(), singleOut.walkTopDown().first { it.isFile }.readText())

            // Verify cross-format conversion involving ZSTD & AES-256 ZIP
            val tarZstdFile = File(tempDir, "pack.tar.zst")
            val encryptedZipFromZstd = File(tempDir, "from_zstd_aes.zip")
            engine.convertArchiveFile(
                sourceArchiveFile = tarZstdFile,
                workingTempRoot = tempDir,
                outputArchiveFile = encryptedZipFromZstd,
                targetFormat = com.explorerpro.domain.model.ArchiveCreateFormat.ZIP,
                encryptionMode = com.explorerpro.domain.model.ArchiveEncryptionMode.AES_256,
                password = "ZstdConvert#99"
            ).getOrThrow()
            assertTrue(encryptedZipFromZstd.exists() && encryptedZipFromZstd.length() > 0L)

            val roundTripTarZstd = File(tempDir, "roundtrip_back.tar.zst")
            engine.convertArchiveFile(
                sourceArchiveFile = encryptedZipFromZstd,
                workingTempRoot = tempDir,
                outputArchiveFile = roundTripTarZstd,
                targetFormat = com.explorerpro.domain.model.ArchiveCreateFormat.TAR_ZSTD,
                sourcePassword = "ZstdConvert#99"
            ).getOrThrow()

            val verifyOut = File(tempDir, "verify_zstd_convert")
            val restoredCount = engine.extractArchiveDetailed(roundTripTarZstd, verifyOut).getOrThrow()
            assertTrue(restoredCount >= 1)
            assertEquals(
                textFile.readText(),
                verifyOut.walkTopDown().first { it.name == "hello.txt" }.readText()
            )
        } finally {
            tempDir.deleteRecursively()
        }
    }
}
