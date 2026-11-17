# Devicon Complete Offline Icon System for Explorer Pro

Integrate the full `devicons/devicon` icon collection offline into Explorer Pro so developer files, special project folders, and user-customized directories display authentic full-color Devicon artwork with smart fallback to Explorer Pro's native Material 3 icons.

## User Review & Critical Decisions

> [!IMPORTANT]
> The decisions below reflect your Phase 1 answers and govern how the entire Devicon repository is packaged and rendered in Explorer Pro without bloating build times or APK size.

- **Confirmed Placement Scope**: Devicon icons will be integrated across **file/extension thumbnails**, **contextual folder corner badges** (both automatic project detection and manual customization), and an interactive **Custom Icon Picker** accessible from folder/file context actions and Settings.
- **Confirmed Visual Style**: **Original full-color** SVGs (`-original`) are selected first for every technology, automatically falling back to `-plain`, `-line`, or `-original-wordmark` when an icon lacks an `-original` SVG, and gracefully falling back to Explorer Pro's existing Material 3 vector icons when no Devicon or custom override applies.
- **Confirmed Offline Packaging**: The entire `devicons/devicon` catalog (every technology registered in `devicon.json`) will be downloaded and bundled offline inside the app's Android assets (`svg` icons + `devicon.json` metadata index), rendered natively via Coil SVG decoding with in-memory + disk caching so zero network calls are ever needed.
- **APK Size & Build Speed Guardrail**: To include **every** Devicon technology offline while keeping the APK lightweight and avoiding slow `aapt2` vector compilation across hundreds of XML drawables, the plan bundles one canonical best-variant SVG per technology (selecting `original` first, then `plain`, then `line`) alongside `devicon.json` in Android assets rather than generating thousands of redundant XML drawables.

---

## 1. Overview & Core Concept

- **What It Does**: Transforms Explorer Pro into a developer-aware native Android file manager by mapping file extensions, special filenames (`Dockerfile`, `CMakeLists.txt`, `.gitignore`, `build.gradle.kts`, `package.json`, `Cargo.toml`, etc.), and project folders (`.git`, `node_modules`, `.vscode`, `android`, `python`, etc.) to their real Devicon logos, while allowing users to browse and assign any Devicon icon from the full repository to any folder.
- **Target Audience / Persona**: Android developers, Termux power users, students, and engineers browsing source trees, repositories, scripts, configs, and workspaces directly on their device.
- **Key Value**: Instant visual recognition across hundreds of languages, frameworks, databases, and DevOps tools—working 100% offline with zero latency and full user customization.

---

## 2. User Experience & Visual Design

### Key User Flows

1. **Automatic File & Config Icon Resolution**:
   - User opens any directory in List, Detailed, Table, or Grid view.
   - Code files, config files, scripts, stylesheets, binaries, and dotfiles automatically display their crisp full-color Devicon SVG inside the existing `UniversalFileThumbnail` slot (`24dp`–`48dp` depending on density/view mode).
   - Image previews still render live photo thumbnails; non-Devicon formats (generic folders, unknown files, media) retain Explorer Pro's existing Material 3 icons and Fluent color accents.
2. **Smart & Custom Folder Badges**:
   - Standard folders continue showing the signature `FluentFolderGold` folder icon for immediate folder vs. file contrast.
   - Folders automatically recognized by project signature (e.g., `.git`, `.github`, `.vscode`, `android`, `docker`, `terraform`, `python`, `flutter`) or manually customized by the user render a crisp **bottom-end overlay badge** (`14dp`–`18dp` rounded surface chip with subtle `1dp` border) displaying the assigned Devicon over the gold folder icon.
3. **Searchable Custom Icon Picker Flow**:
   - User long-presses a folder (or taps the overflow menu) and selects **Set Custom Icon**, or opens the **Devicon Icon Catalog** preview from Appearance / File View Settings.
   - A full-height Material 3 modal sheet / dialog opens featuring:
     - Sticky search bar filtering across all technology names, aliases, and `devicon.json` tags (e.g., searching `"web"`, `"database"`, `"android"`, `"c++"`, or `"rust"`).
     - Horizontal category filter chips (`All`, `Languages`, `Frameworks`, `DevOps & Cloud`, `Databases`, `Tools & OS`).
     - Adaptive 4-to-6-column grid (`48dp+` touch targets, `8dp` grid spacing) showing every bundled Devicon with its color icon and concise label.
     - Active selection highlight ring plus quick actions: **Apply to Folder**, **Reset to Default**, and **Toggle Devicon Icons** in Settings.

### Visual Identity & Theme

- **Aesthetic Direction**: High-craft dark/dynamic Material 3 developer workstation matching Explorer Pro's existing slate/navy surfaces and `FluentFolderGold` / `FluentCyan400` / `FluentTeal400` palette.
- **Component Styling & Layout**:
  - Original Devicon colors remain vibrant and untinted so brand marks (Kotlin, Python, Docker, Rust, Linux, Android, Termux/Bash) stay instantly recognizable.
  - Folder overlay badges sit inside a compact circular/squircle `surfaceContainerHigh` pill (`4dp` padding, subtle border) anchored at `Alignment.BottomEnd` of the folder thumbnail to ensure dark logos (e.g., GitHub, Rust, Next.js) remain legible against both light and dark Material You themes.
  - Grid cards inside the Devicon Picker enforce minimum `48dp` interactive targets, `12dp` rounded corners, `8dp` grid spacing, and dynamic width scaling (`WindowSizeClass` / `GridCells.Adaptive(76.dp)`).
- **Interactive Feedback & Motion**:
  - Selection spring scale feedback on picker tiles, smooth `Crossfade` when Coil decodes cached SVGs asynchronously, instant live refresh across both Single and Dual-Pane tabs when a folder icon is updated, and an undoable confirmation snackbar.

---

## 3. Key Product Decisions & Trade-Offs

- **Decision 1 — Offline Asset SVG Bundle + Coil SVG Decoder vs. Compiling Vector XMLs**:
  - *Chosen Approach*: Bundle `devicon.json` and one best-style `.svg` per technology (`original` → `plain` → `line` → `original-wordmark`) in Android `assets`, rendered via Coil's native `SvgDecoder` with shared `ImageLoader` memory & disk caching.
  - *Why*: Keeps APK growth minimal (a few megabytes vs. tens of megabytes for thousands of redundant XMLs), keeps Gradle `mergeResources` / `aapt2` builds fast, preserves complex SVG gradients/filters unsupported by Android `VectorDrawable`, and handles every single technology in `devicons/devicon` offline.
  - *Alternatives Considered*: Converting all SVG variants to `res/drawable/*.xml` (rejected because 600–2,000+ XML files significantly slow incremental builds and drop SVG gradients/masks) or using an icon font `.ttf` (rejected because TTF fonts are single-color monochrome glyphs and cannot render full-color `original` icons).

- **Decision 2 — Canonical One-Per-Technology Variant Selection vs. Storing All Variants**:
  - *Chosen Approach*: Bundle **every technology** in `devicons/devicon` using the user's preferred priority order (`original` first, falling back to `plain` / `line` when `original` does not exist in `devicon.json`), ensuring 100% catalog coverage with zero visual duplication.
  - *Why*: Fulfills the requirement to include all icons from the repository offline while eliminating redundant wordmark-only bloat that is unreadable at `16dp`–`32dp` file row sizes.
  - *Alternatives Considered*: Bundling every style variant (`wordmark`, `plain`, `line`, `original`) for every icon (rejected because ~1,800+ SVGs duplicate storage unnecessarily on mobile devices).

- **Decision 3 — Local Persistence for Custom Folder Icons & Preferences**:
  - *Chosen Approach*: Persist per-path folder icon overrides in Explorer Pro's existing local Room database (`ExplorerDatabase`, bumping schema safely with fallback/migration) and store global Devicon visibility/style toggles in DataStore (`PreferencesManager`).
  - *Why*: Folder-to-icon mappings survive app restarts offline, load reactively via Kotlin `Flow` into ViewModels without blocking disk directory scans, and support fast clearing/resetting per folder.
  - *Alternatives Considered*: Writing hidden `.explorerpro-icon` metadata files inside user folders (rejected to avoid polluting Git working trees and requiring extra SAF write permissions on read-only folders).

---

## 4. Technical Architecture & Data Strategy *(Technical Reference)*

### Architecture & Component Diagram

```text
┌───────────────────────────────────────────────────────────────────────────┐
│                         Bundled Offline Assets                            │
│  devicon.json (full catalog, aliases, tags) + devicon/<id>.svg (all ids)  │
└────────────────────────────────────┬──────────────────────────────────────┘
                                     │ parsed once & cached in memory
                                     ▼
┌───────────────────────────────────────────────────────────────────────────┐
│                     DeviconCatalog & Icon Resolver                        │
│  • Catalog Index (all technologies, tags, categories, alias lookup)       │
│  • Extension & Special Filename Matcher (.kt, Dockerfile, Makefile, etc.) │
│  • Smart Folder Signature Matcher (.git, android, docker, node_modules)   │
└───────────────┬────────────────────────────────────────┬──────────────────┘
                │                                        │
                ▼                                        ▼
┌───────────────────────────────┐      ┌────────────────────────────────────┐
│  Room & DataStore Persistence │      │    Shared Coil SVG ImageLoader     │
│  • Custom Folder Icon Table   │      │    • Memory & Asset URI Caching    │
│  • Devicon Feature Toggles    │      │    • Crossfade + Error Fallback    │
└───────────────┬───────────────┘      └─────────────────┬──────────────────┘
                │ reactive Flow<Map<Path, IconId>>       │
                └──────────────────┬─────────────────────┘
                                   ▼
┌───────────────────────────────────────────────────────────────────────────┐
│                        ExplorerViewModel & UI Layer                       │
│  • UniversalFileThumbnail  ──► Devicon File Icon OR Gold Folder + Badge   │
│  • Context Menu / Sheet    ──► "Set Custom Icon" Action                   │
│  • DeviconPickerModal      ──► Search, Category Chips, Adaptive Grid      │
│  • Settings Screen         ──► Toggle Devicons / Browse Full Catalog      │
└───────────────────────────────────────────────────────────────────────────┘
```

### Data Model & State

- **`DeviconItem`**: Immutable catalog entry parsed from `devicon.json` containing `id`, `displayName`, `assetFileName`, `tags`, `aliases`, `resolvedVariant` (`original`, `plain`, or `line`), and derived `category`.
- **`CustomFolderIconEntity` (Room Entity)**: Stores normalized `path` (`@PrimaryKey`), `iconId`, and `updatedAt` timestamp so custom folder icons propagate immediately across tabs, search results, favorites, and dual-pane views.
- **`ExplorerUiState` Extensions**: Exposes `customFolderIcons: Map<String, String>` (reactive map observed from Room), `enableDeviconIcons: Boolean`, `enableSmartFolderBadges: Boolean` (from `PreferencesManager`), and `iconPickerTargetEntry: FileEntry?` for opening/closing the picker dialog cleanly.

### Interactive Component & State Mapping

- **Catalog Loading**: A lazy singleton repository parses bundled `devicon.json` on `Dispatchers.IO` once, builds O(1) lookup hash maps for extensions, exact filenames, and folder signatures, and exposes the complete sorted list to the picker.
- **Thumbnail Resolution (`UniversalFileThumbnail`)**:
  1. Checks if entry is an image (live thumbnail takes priority).
  2. Checks `customFolderIcons[entry.path]` for user-assigned Devicon.
  3. Checks smart folder signature (for directories) or extension/exact filename match (for files).
  4. Renders via Coil `AsyncImage` using `file:///android_asset/devicon/<id>.svg` through a configured `SvgDecoder` `ImageLoader`, falling back to Material 3 `resolveFileIconAndTint(entry)` whenever disabled, unmatched, or loading fails.
- **Picker Interaction (`DeviconPickerDialog`)**: Search text and selected category chip filter the in-memory catalog via `derivedStateOf`; tapping an item persists it in Room via `ExplorerViewModel`, updates the active directory/favorites UI immediately, and dismisses cleanly; tapping **Reset Default** removes the Room row.

### Key Sequences & Integrations

- **Repository Asset Ingestion During Build Preparation**: Clone `https://github.com/devicons/devicon.git` into a temporary build workspace during implementation, run a deterministic sync script that reads `devicon.json`, selects the optimal SVG per entry (`original` → `plain` → `line` → wordmark fallback), copies every technology's SVG + trimmed catalog `devicon_catalog.json` into Android assets, and removes the temporary clone before compilation.
- **Coil SVG Support**: Enable `coil-svg` (matching the existing `Coil 2.7.0` dependency version) with a custom `ImageLoader` registered at `Application`/Compose scope so SVG decoding is zero-overhead across fast `LazyColumn`/`LazyVerticalGrid` scrolling.
- **Verification**: Validate asset integrity and JSON catalog completeness (verifying every `devicon.json` entry maps to an existing bundled SVG file), add unit/Robolectric tests for catalog parsing, extension/filename resolution, smart folder detection, and custom icon Room persistence, and verify `compile_applet` + `:app:testDebugUnitTest`.
