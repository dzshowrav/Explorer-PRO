package com.explorerpro.core.icons

import android.content.Context
import com.explorerpro.domain.model.FileEntry
import org.json.JSONArray

enum class DeviconCategory(val label: String) {
    ALL("All"),
    LANGUAGES("Languages"),
    FRAMEWORKS("Frameworks & Libraries"),
    DEVOPS_CLOUD("DevOps & Cloud"),
    DATABASES("Databases"),
    TOOLS_OS("Tools & OS");

    companion object {
        fun fromCatalogLabel(raw: String): DeviconCategory {
            return entries.firstOrNull { it.label.equals(raw, ignoreCase = true) } ?: TOOLS_OS
        }
    }
}

data class DeviconItem(
    val id: String,
    val displayName: String,
    val assetFile: String,
    val variant: String,
    val category: DeviconCategory,
    val colorHex: String,
    val tags: List<String>,
    val altnames: List<String>,
    val needsLightBacking: Boolean
) {
    val assetUri: String
        get() = "file:///android_asset/devicon/$assetFile"
}

object DeviconCatalogRepository {

    @Volatile
    private var cachedItems: List<DeviconItem>? = null

    @Volatile
    private var cachedById: Map<String, DeviconItem>? = null

    fun getCatalog(context: Context): List<DeviconItem> {
        cachedItems?.let { return it }
        return synchronized(this) {
            cachedItems?.let { return it }
            val loaded = parseCatalogFromAssets(context.applicationContext)
            cachedItems = loaded
            cachedById = loaded.associateBy { it.id }
            loaded
        }
    }

    fun getById(context: Context, id: String?): DeviconItem? {
        if (id.isNullOrBlank()) return null
        if (cachedById == null) {
            getCatalog(context)
        }
        return cachedById?.get(id.lowercase())
    }

    fun search(
        items: List<DeviconItem>,
        query: String,
        category: DeviconCategory
    ): List<DeviconItem> {
        val byCategory = if (category == DeviconCategory.ALL) {
            items
        } else {
            items.filter { it.category == category }
        }
        val cleanQuery = query.trim().lowercase()
        if (cleanQuery.isEmpty()) return byCategory

        return byCategory
            .mapNotNull { item ->
                val score = matchScore(item, cleanQuery)
                if (score > 0) score to item else null
            }
            .sortedWith(compareByDescending<Pair<Int, DeviconItem>> { it.first }.thenBy { it.second.displayName.lowercase() })
            .map { it.second }
    }

    private fun matchScore(item: DeviconItem, q: String): Int {
        val idLower = item.id.lowercase()
        val nameLower = item.displayName.lowercase()
        return when {
            idLower == q || nameLower == q -> 100
            idLower.startsWith(q) || nameLower.startsWith(q) -> 80
            item.altnames.any { it.lowercase() == q } -> 75
            idLower.contains(q) || nameLower.contains(q) -> 60
            item.altnames.any { it.lowercase().contains(q) } -> 50
            item.tags.any { it.lowercase() == q } -> 40
            item.tags.any { it.lowercase().contains(q) } -> 25
            else -> 0
        }
    }

    private fun parseCatalogFromAssets(context: Context): List<DeviconItem> {
        val jsonText = runCatching {
            context.assets.open("devicon/catalog.json").bufferedReader().use { it.readText() }
        }.getOrNull() ?: return emptyList()

        val array = runCatching { JSONArray(jsonText) }.getOrNull() ?: return emptyList()
        val result = ArrayList<DeviconItem>(array.length())

        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            val id = obj.optString("id").trim()
            val assetFile = obj.optString("assetFile").trim()
            if (id.isEmpty() || assetFile.isEmpty()) continue

            val tagsJson = obj.optJSONArray("tags")
            val tags = buildList {
                if (tagsJson != null) {
                    for (t in 0 until tagsJson.length()) {
                        tagsJson.optString(t).takeIf { it.isNotBlank() }?.let(::add)
                    }
                }
            }

            val altJson = obj.optJSONArray("altnames")
            val altnames = buildList {
                if (altJson != null) {
                    for (a in 0 until altJson.length()) {
                        altJson.optString(a).takeIf { it.isNotBlank() }?.let(::add)
                    }
                }
            }

            val colorHex = obj.optString("color", "#607D8B").ifBlank { "#607D8B" }
            result.add(
                DeviconItem(
                    id = id,
                    displayName = obj.optString("displayName", id),
                    assetFile = assetFile,
                    variant = obj.optString("variant", "original"),
                    category = DeviconCategory.fromCatalogLabel(obj.optString("category")),
                    colorHex = colorHex,
                    tags = tags,
                    altnames = altnames,
                    needsLightBacking = isDarkBrandColor(colorHex) || id in FORCE_LIGHT_BACKING_IDS
                )
            )
        }
        return result.sortedBy { it.displayName.lowercase() }
    }

    private fun isDarkBrandColor(hex: String): Boolean {
        val clean = hex.trim().removePrefix("#")
        val normalized = when (clean.length) {
            3 -> clean.map { "$it$it" }.joinToString("")
            6 -> clean
            8 -> clean.substring(2)
            else -> return false
        }
        val rgb = normalized.toLongOrNull(16) ?: return false
        val r = ((rgb shr 16) and 0xFF) / 255.0
        val g = ((rgb shr 8) and 0xFF) / 255.0
        val b = (rgb and 0xFF) / 255.0
        val luminance = 0.2126 * r + 0.7152 * g + 0.0722 * b
        return luminance < 0.16
    }

    private val FORCE_LIGHT_BACKING_IDS = setOf(
        "github", "nextjs", "nuxtjs", "rust", "apple", "bash", "express",
        "unity", "vercel", "markdown", "flask", "django", "symfony",
        "threejs", "prisma", "solidity", "tomcat"
    )
}

object DeviconResolver {

    /**
     * Exact lowercase filenames mapped directly to curated Devicon IDs.
     */
    val EXACT_FILENAME_TO_ICON_ID: Map<String, String> = mapOf(
        "dockerfile" to "docker",
        "containerfile" to "docker",
        ".dockerignore" to "docker",
        "docker-compose.yml" to "docker",
        "docker-compose.yaml" to "docker",
        "compose.yml" to "docker",
        "compose.yaml" to "docker",
        "cmakelists.txt" to "cmake",
        "makefile" to "gcc",
        "gnumakefile" to "gcc",
        "build.gradle" to "gradle",
        "build.gradle.kts" to "gradle",
        "settings.gradle" to "gradle",
        "settings.gradle.kts" to "gradle",
        "gradlew" to "gradle",
        "gradle.properties" to "gradle",
        "gradle-wrapper.properties" to "gradle",
        "pom.xml" to "maven",
        "package.json" to "npm",
        "package-lock.json" to "npm",
        "yarn.lock" to "yarn",
        ".yarnrc" to "yarn",
        ".yarnrc.yml" to "yarn",
        "pnpm-lock.yaml" to "pnpm",
        "pnpm-workspace.yaml" to "pnpm",
        "tsconfig.json" to "typescript",
        "jsconfig.json" to "javascript",
        "vite.config.js" to "vitejs",
        "vite.config.ts" to "vitejs",
        "vite.config.mjs" to "vitejs",
        "webpack.config.js" to "webpack",
        "webpack.config.ts" to "webpack",
        "tailwind.config.js" to "tailwindcss",
        "tailwind.config.ts" to "tailwindcss",
        "tailwind.config.cjs" to "tailwindcss",
        "next.config.js" to "nextjs",
        "next.config.mjs" to "nextjs",
        "next.config.ts" to "nextjs",
        "nuxt.config.js" to "nuxtjs",
        "nuxt.config.ts" to "nuxtjs",
        "svelte.config.js" to "svelte",
        "astro.config.mjs" to "astro",
        "astro.config.ts" to "astro",
        "cargo.toml" to "rust",
        "cargo.lock" to "rust",
        "go.mod" to "go",
        "go.sum" to "go",
        "go.work" to "go",
        "requirements.txt" to "python",
        "pyproject.toml" to "python",
        "setup.py" to "python",
        "pipfile" to "python",
        "pipfile.lock" to "python",
        "poetry.lock" to "python",
        "pubspec.yaml" to "flutter",
        "pubspec.lock" to "flutter",
        "gemfile" to "ruby",
        "gemfile.lock" to "ruby",
        "rakefile" to "ruby",
        "composer.json" to "composer",
        "composer.lock" to "composer",
        ".gitignore" to "git",
        ".gitattributes" to "git",
        ".gitmodules" to "git",
        ".eslintrc" to "eslint",
        ".eslintrc.js" to "eslint",
        ".eslintrc.json" to "eslint",
        ".eslintrc.cjs" to "eslint",
        "eslint.config.js" to "eslint",
        "eslint.config.mjs" to "eslint",
        ".babelrc" to "babel",
        "babel.config.js" to "babel",
        "babel.config.json" to "babel",
        "jenkinsfile" to "jenkins",
        "nginx.conf" to "nginx",
        ".bashrc" to "bash",
        ".zshrc" to "bash",
        ".bash_profile" to "bash",
        ".profile" to "bash",
        "firebase.json" to "firebase",
        ".firebaserc" to "firebase",
        "vercel.json" to "vercel",
        "netlify.toml" to "netlify",
        "readme.md" to "markdown",
        "changelog.md" to "markdown",
        "contributing.md" to "markdown",
        "androidmanifest.xml" to "android"
    )

    /**
     * Lowercase file extensions (without leading dot) mapped to curated Devicon IDs.
     */
    val EXTENSION_TO_ICON_ID: Map<String, String> = mapOf(
        "kt" to "kotlin",
        "kts" to "kotlin",
        "java" to "java",
        "jar" to "java",
        "class" to "java",
        "jsp" to "java",
        "py" to "python",
        "pyw" to "python",
        "pyc" to "python",
        "ipynb" to "jupyter",
        "js" to "javascript",
        "mjs" to "javascript",
        "cjs" to "javascript",
        "jsx" to "react",
        "tsx" to "react",
        "ts" to "typescript",
        "mts" to "typescript",
        "cts" to "typescript",
        "c" to "c",
        "h" to "c",
        "cpp" to "cplusplus",
        "cc" to "cplusplus",
        "cxx" to "cplusplus",
        "hpp" to "cplusplus",
        "hxx" to "cplusplus",
        "hh" to "cplusplus",
        "cs" to "csharp",
        "fs" to "fsharp",
        "fsx" to "fsharp",
        "vb" to "visualbasic",
        "go" to "go",
        "rs" to "rust",
        "rb" to "ruby",
        "erb" to "ruby",
        "php" to "php",
        "swift" to "swift",
        "m" to "objectivec",
        "mm" to "objectivec",
        "dart" to "dart",
        "scala" to "scala",
        "sc" to "scala",
        "groovy" to "groovy",
        "gvy" to "groovy",
        "gradle" to "gradle",
        "lua" to "lua",
        "r" to "r",
        "rmd" to "r",
        "jl" to "julia",
        "ex" to "elixir",
        "exs" to "elixir",
        "erl" to "erlang",
        "hrl" to "erlang",
        "hs" to "haskell",
        "lhs" to "haskell",
        "clj" to "clojure",
        "cljs" to "clojure",
        "cljc" to "clojure",
        "edn" to "clojure",
        "pl" to "perl",
        "pm" to "perl",
        "sh" to "bash",
        "bash" to "bash",
        "zsh" to "bash",
        "fish" to "bash",
        "ps1" to "powershell",
        "psm1" to "powershell",
        "psd1" to "powershell",
        "bat" to "windows11",
        "cmd" to "windows11",
        "html" to "html5",
        "htm" to "html5",
        "xhtml" to "html5",
        "css" to "css3",
        "scss" to "sass",
        "sass" to "sass",
        "less" to "less",
        "vue" to "vuejs",
        "svelte" to "svelte",
        "astro" to "astro",
        "json" to "json",
        "jsonc" to "json",
        "json5" to "json",
        "xml" to "xml",
        "xsd" to "xml",
        "xsl" to "xml",
        "yaml" to "yaml",
        "yml" to "yaml",
        "toml" to "toml",
        "sql" to "azuresqldatabase",
        "sqlite" to "sqlite",
        "sqlite3" to "sqlite",
        "db" to "sqlite",
        "md" to "markdown",
        "mdx" to "markdown",
        "tex" to "latex",
        "latex" to "latex",
        "graphql" to "graphql",
        "gql" to "graphql",
        "tf" to "terraform",
        "tfvars" to "terraform",
        "hcl" to "terraform",
        "wasm" to "wasm",
        "wat" to "wasm",
        "apk" to "android",
        "aab" to "android",
        "psd" to "photoshop",
        "ai" to "illustrator",
        "fig" to "figma",
        "xd" to "xd",
        "blend" to "blender",
        "unity" to "unity",
        "nix" to "nixos",
        "zig" to "zig",
        "nim" to "nim",
        "v" to "vlang",
        "sol" to "solidity",
        "prisma" to "prisma"
    )

    /**
     * Lowercase directory names recognized for automatic Smart Folder Technology Badges.
     */
    val SMART_FOLDER_NAME_TO_ICON_ID: Map<String, String> = mapOf(
        ".git" to "git",
        ".github" to "github",
        ".gitlab" to "gitlab",
        ".vscode" to "vscode",
        ".idea" to "intellij",
        "android" to "android",
        ".android" to "android",
        "ios" to "apple",
        "flutter" to "flutter",
        "node_modules" to "nodejs",
        ".npm" to "npm",
        ".yarn" to "yarn",
        ".pnpm-store" to "pnpm",
        ".gradle" to "gradle",
        "gradle" to "gradle",
        ".docker" to "docker",
        "docker" to "docker",
        "k8s" to "kubernetes",
        "kubernetes" to "kubernetes",
        ".kube" to "kubernetes",
        "terraform" to "terraform",
        ".terraform" to "terraform",
        ".venv" to "python",
        "venv" to "python",
        "__pycache__" to "python",
        "python" to "python",
        "anaconda" to "anaconda",
        "rust" to "rust",
        ".cargo" to "rust",
        ".termux" to "bash",
        ".ssh" to "ssh",
        "linux" to "linux",
        ".firebase" to "firebase",
        "firebase" to "firebase",
        "supabase" to "supabase",
        "react" to "react",
        "angular" to "angularjs",
        "vue" to "vuejs",
        ".next" to "nextjs",
        "nextjs" to "nextjs",
        ".nuxt" to "nuxtjs",
        "svelte" to "svelte",
        "nginx" to "nginx",
        "apache" to "apache",
        "mysql" to "mysql",
        "postgres" to "postgresql",
        "postgresql" to "postgresql",
        "mongodb" to "mongodb",
        "redis" to "redis"
    )

    fun resolveFileIconId(entry: FileEntry): String? {
        if (entry.isDirectory) return null
        val lowerName = entry.name.lowercase()
        EXACT_FILENAME_TO_ICON_ID[lowerName]?.let { return it }

        if (lowerName.endsWith(".blade.php")) return "laravel"
        if (lowerName.endsWith(".d.ts")) return "typescript"
        if (lowerName.endsWith(".spec.ts") || lowerName.endsWith(".test.ts")) return "typescript"
        if (lowerName.endsWith(".spec.js") || lowerName.endsWith(".test.js")) return "javascript"
        if (lowerName.endsWith(".component.ts")) return "angularjs"

        val ext = entry.extension.trim().trimStart('.').lowercase()
            .ifEmpty { lowerName.substringAfterLast('.', "") }
        return EXTENSION_TO_ICON_ID[ext]
    }

    fun resolveFileIcon(context: Context, entry: FileEntry): DeviconItem? {
        val id = resolveFileIconId(entry) ?: return null
        return DeviconCatalogRepository.getById(context, id)
    }

    fun resolveFolderBadgeId(
        entry: FileEntry,
        customFolderIcons: Map<String, String>,
        enableSmartFolderBadges: Boolean
    ): String? {
        if (!entry.isDirectory) return null
        val custom = customFolderIcons[entry.path]
        if (!custom.isNullOrBlank()) return custom
        if (!enableSmartFolderBadges) return null
        return SMART_FOLDER_NAME_TO_ICON_ID[entry.name.lowercase()]
    }

    fun resolveFolderBadgeIcon(
        context: Context,
        entry: FileEntry,
        customFolderIcons: Map<String, String>,
        enableSmartFolderBadges: Boolean
    ): DeviconItem? {
        val id = resolveFolderBadgeId(entry, customFolderIcons, enableSmartFolderBadges) ?: return null
        return DeviconCatalogRepository.getById(context, id)
    }
}
