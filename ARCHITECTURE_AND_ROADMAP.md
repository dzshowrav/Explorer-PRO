# Explorer Pro — Architecture, Compatibility Matrix & Release Guide

## 1. Environment Assessment & Toolchain Compatibility Matrix

| Component | Configured Version | Notes |
| :--- | :--- | :--- |
| **Application ID** | `com.aistudio.explorerpro.kxmpzq` | Unique production package identifier |
| **Compile / Target SDK** | API 36 (minorApiLevel 1) | Edge-to-Edge & Predictive Back enabled |
| **Minimum SDK** | API 24 (Android 7.0+) | Full Storage Access Framework (SAF) & `StatFs` support |
| **Kotlin** | `2.2.10` | Coroutines, `StateFlow`, KSP `2.3.5` |
| **Android Gradle Plugin (AGP)** | `9.1.1` | Built-in Kotlin DSL (`.gradle.kts`) |
| **Jetpack Compose BOM** | `2024.09.00` | Material 3 (`androidx.compose.material3`), Adaptive Layouts |
| **Room Database + KSP** | `2.7.0` | Local persistence for Favorites, Recent Files, Tabs, Operation Logs |
| **Jetpack DataStore** | `1.1.7` | Per-pane ViewMode, Sort, GroupBy, Theme & SAF URI persistence |
| **Coil Compose** | `2.7.0` | Async downsampled image & universal thumbnail loading |
| **Testing Stack** | JUnit 4 + Coroutines Test `1.10.2` + Robolectric `4.16.1` | Local JVM & Android framework verification |

---

## 2. Clean Architecture & Package Organization

- `com.explorerpro.domain.model` (`ExplorerModels.kt`):
  - Strictly defines the **Three Primary File View Modes** (`GRID`, `SIMPLE_LIST`, `DETAILED_LIST`), `DetectedFileFormat` (with magic-byte hex signatures), `FileEntry`, `ExplorerTab`, `PaneState` (`LEFT` and `RIGHT`), `FileOperationTask`, `ArchiveEntryItem`, `BatchRenameConfig`, `FolderComparisonResult`, and `StorageAnalysisReport`.
- `com.explorerpro.core.security` (`ArchiveEngine.kt`):
  - Streaming archive browser, extractor, integrity verifier (CRC32), and creator supporting **ZIP**, **TAR**, and **GZIP (`.tar.gz` / `.tgz`)** with read-only magic header recognition for **7Z** and **RAR**.
  - Enforces **ZIP Slip & Path Traversal Defense** (`validateSafeDestinationFile`), null-byte rejection, duplicate-entry rejection, and configurable **Decompression Bomb** limits (`512 MB` max output, `25,000` max entries, `120:1` max compression ratio).
- `com.explorerpro.core.filesystem`:
  - `FormatDetectorAndMetadataEngine.kt`: Reads 64-byte magic signatures to classify files accurately regardless of extension, decodes image bounds without loading full bitmaps into memory (`inJustDecodeBounds = true`), reads PDF page counts via `PdfRenderer`, extracts APK package info, and computes streaming **SHA-256** digests.
  - `SortingAndGroupingEngine.kt`: Implements natural numeric-aware sorting (`Release_2.txt` before `Release_10.txt`), 10 sort attributes, folder placement rules, 7 grouping modes, and the multi-rule Batch Rename preview/collision engine.
  - `FileSystemRepository.kt`: Bridges the internal Explorer Pro Workspace, device storage volumes (`StatFs`), and Android **Storage Access Framework (`DocumentsContract`)** trees with buffered 64 KB streaming transfers, Recycle Bin soft-delete, asynchronous Folder Intelligence, progressive cancellable `Flow` search, dual-pane folder comparison, and SHA-256 duplicate detection.
- `com.explorerpro.data.local`:
  - `ExplorerDatabase.kt`: Room SQLite database with indexed entities (`FavoriteEntity`, `RecentFileEntity`, `SearchHistoryEntity`, `TabSessionEntity`, `OperationLogEntity`, `FolderStatsCacheEntity`, `ArchiveHistoryEntity`).
  - `PreferencesManager.kt`: Reactive DataStore preferences per pane.
- `com.explorerpro.presentation`:
  - `ExplorerViewModel.kt`, `ExplorerHeaderAndRibbon.kt`, `FilePaneView.kt`, `ExplorerModalsAndViewers.kt`, and `MainActivity.kt`.

---

## 3. Storage Permission & Scoped Storage Strategy

1. **Zero Broad Permissions by Default**: Explorer Pro complies strictly with Google Play policy and Android Scoped Storage (Android 10–16+). It does **not** request `MANAGE_EXTERNAL_STORAGE` or legacy `READ_EXTERNAL_STORAGE`.
2. **Storage Access Framework (SAF)**: External directories, SD cards, and USB OTG volumes are mounted via `ActivityResultContracts.OpenDocumentTree()`, persisted with `ContentResolver.takePersistableUriPermission()`, and queried via `DocumentsContract`.
3. **Pre-Seeded Workspace**: On first launch, Explorer Pro provisions a rich local workspace (`Documents/`, `Pictures/`, `Projects/`, `Archives/`, `Downloads/`, `.RecycleBin/`) so all features—including dual-pane comparison, archive extraction, pinch-to-zoom image viewing, batch rename, and duplicate finding—work immediately out-of-the-box.

---

## 4. Termux & GitHub Actions Production Release Workflow

### Triggering a Signed Release APK/AAB from Android (Termux)
```bash
pkg update && pkg install git openjdk-17
git add .
git commit -m "release: Explorer Pro production build"
git push origin main
```

### GitHub Actions Pipelines Included
- `.github/workflows/build-signed-apk.yml`: Runs unit & Robolectric tests (`gradle :app:testDebugUnitTest`), materializes or generates the production RSA-2048 PKCS12 keystore, runs `gradle :app:assembleRelease`, verifies the signature via `apksigner verify --verbose`, and uploads `app-release.apk`.
- `.github/workflows/build-signed-aab.yml`: Runs the full test suite and builds the Play Store signed bundle (`gradle :app:bundleRelease`) on version tags (`v*`) or manual dispatch.
