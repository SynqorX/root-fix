package com.rootfix.app.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.rootfix.app.data.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

class ModuleUpdateRepository(
    private val context: Context,
    private val magiskRepo: MagiskRepository
) {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private val prefs: SharedPreferences = context.getSharedPreferences("rootfix_module_updater", Context.MODE_PRIVATE)
    private val PREF_KEY_CUSTOM_MODULES = "custom_tracked_modules"
    private val PREF_KEY_REMOVED_CATALOG = "removed_catalog_module_ids"

    /**
     * Curated catalog of essential root & integrity modules.
     * Includes Project InfinityX fundamentals: PIF and TrickyStore (Keybox Attestation).
     */
    private val curatedCatalog = listOf(
        TrackedModule(
            id = "integrity_box",
            name = "Integrity Box (Automated Keybox & PIF)",
            author = "MEOWna (MeowDump)",
            description = "All-in-one toolkit providing automated unrevoked keyboxes, TEE simulation, and Play Integrity bypass.",
            category = "Keybox & Integrity",
            repoOwner = "MeowDump",
            repoName = "Integrity-Box",
            updateJsonUrl = "https://raw.githubusercontent.com/MeowDump/Integrity-Box/refs/heads/main/release.json"
        ),
        TrackedModule(
            id = "playintegrityfix",
            name = "Play Integrity Fix [INJECT]",
            author = "chiteroman, KOWX712",
            description = "Fixes Play Integrity verdict with 13-tuple build property spoofing and GMS injection.",
            category = "Play Integrity",
            repoOwner = "KOWX712",
            repoName = "PlayIntegrityFix",
            updateJsonUrl = "https://fastly.jsdelivr.net/gh/KOWX712/playintegrityfix@inject_s/update.json"
        ),
        TrackedModule(
            id = "tricky_store",
            name = "TrickyStore (Keybox Attestation)",
            author = "5ec1cff",
            description = "Injects custom keybox.xml into Keystore to satisfy MEETS_DEVICE_INTEGRITY & MEETS_STRONG_INTEGRITY (Project InfinityX fundamental).",
            category = "Keystore Attestation (InfinityX)",
            repoOwner = "5ec1cff",
            repoName = "TrickyStore"
        ),
        TrackedModule(
            id = "zygisk-detach",
            name = "Zygisk Detach",
            author = "j-hc",
            description = "Detaches apps from Google Play Store to prevent forced app updates.",
            category = "Root Utilities",
            repoOwner = "j-hc",
            repoName = "zygisk-detach",
            updateJsonUrl = "https://raw.githubusercontent.com/j-hc/zygisk-detach/master/update.json"
        ),
        TrackedModule(
            id = "playintegrityfork",
            name = "Play Integrity Fork",
            author = "osm0sis",
            description = "Configurable open-source fork of Play Integrity Fix with custom pif.json parsing.",
            category = "Play Integrity",
            repoOwner = "osm0sis",
            repoName = "PlayIntegrityFork",
            updateJsonUrl = "https://raw.githubusercontent.com/osm0sis/PlayIntegrityFork/main/update.json"
        ),
        TrackedModule(
            id = "zygisksu",
            name = "Zygisk Next",
            author = "LSPosed",
            description = "Standalone next-generation Zygisk implementation for Magisk, KernelSU, and APatch.",
            category = "Zygisk Core",
            repoOwner = "LSPosed",
            repoName = "ZygiskNext",
            updateJsonUrl = "https://lsposed.zip/zygisk-next/update.json"
        ),
        TrackedModule(
            id = "playintegritynext",
            name = "PlayIntegrityNEXT",
            author = "daboynb",
            description = "Automated fingerprint updater and integrity bypass tool.",
            category = "Play Integrity",
            repoOwner = "daboynb",
            repoName = "PlayIntegrityNEXT"
        )
    )

    /**
     * Retrieves all tracked modules, merging installed modules, curated catalog,
     * and user-added custom repositories.
     */
    suspend fun getTrackedModules(): List<TrackedModule> = withContext(Dispatchers.IO) {
        val installedList = magiskRepo.getInstalledModules()
        val installedMap = installedList.associateBy { it.id.lowercase() }

        val customList = getCustomTrackedList()
        val removedIds = getRemovedCatalogIds().map { it.lowercase() }.toSet()
        val combinedMap = mutableMapOf<String, TrackedModule>()

        // 1. Add curated catalog
        for (item in curatedCatalog) {
            val key = item.id.lowercase()
            if (removedIds.contains(key)) continue

            val installed = when (item.id) {
                "integrity_box" -> installedMap["playintegrityfix"]?.takeIf {
                    it.name.contains("Integrity Box", ignoreCase = true) || it.author.contains("Meow", ignoreCase = true)
                } ?: installedMap["integrity_box"]
                "playintegrityfix" -> installedMap["playintegrityfix"]?.takeUnless {
                    it.name.contains("Integrity Box", ignoreCase = true) || it.author.contains("Meow", ignoreCase = true)
                }
                "zygisksu", "zygisknext" -> installedMap["zygisksu"] ?: installedMap["zygisknext"]
                else -> installedMap[key]
            }

            val module = item.copy(
                isInstalled = installed != null,
                installedVersion = installed?.version,
                installedVersionCode = installed?.versionCode,
                hasAction = installed?.hasAction ?: false,
                // Inherit updateJson from installed module if present
                updateJsonUrl = installed?.updateJson?.ifBlank { null } ?: item.updateJsonUrl
            )
            combinedMap[key] = module
        }

        // 2. Add user-added custom modules
        for (item in customList) {
            val key = item.id.lowercase()
            if (removedIds.contains(key)) continue

            val installed = installedMap[key]
            val module = item.copy(
                isInstalled = installed != null,
                installedVersion = installed?.version,
                installedVersionCode = installed?.versionCode,
                hasAction = installed?.hasAction ?: false,
                isCustom = true
            )
            combinedMap[key] = module
        }

        // 3. Add any installed modules that are not in catalog or custom
        for (mod in installedList) {
            val key = mod.id.lowercase()
            if (removedIds.contains(key)) continue
            if (!combinedMap.containsKey(key)) {
                combinedMap[key] = TrackedModule(
                    id = mod.id,
                    name = mod.name,
                    author = mod.author,
                    description = mod.description,
                    category = "Installed",
                    updateJsonUrl = mod.updateJson,
                    installedVersion = mod.version,
                    installedVersionCode = mod.versionCode,
                    isInstalled = true,
                    hasAction = mod.hasAction
                )
            }
        }

        combinedMap.values.sortedWith(
            compareByDescending<TrackedModule> { it.hasUpdate }
                .thenByDescending { it.isInstalled }
                .thenBy { it.name.lowercase() }
        )
    }

    /**
     * Checks for updates for all tracked modules.
     */
    suspend fun checkAllUpdates(
        onModuleChecked: (TrackedModule) -> Unit = {}
    ): List<TrackedModule> = withContext(Dispatchers.IO) {
        val modules = getTrackedModules()
        val updatedModules = mutableListOf<TrackedModule>()

        for (module in modules) {
            val updated = checkModuleUpdate(module)
            updatedModules.add(updated)
            onModuleChecked(updated)
        }

        updatedModules.sortedWith(
            compareByDescending<TrackedModule> { it.hasUpdate }
                .thenByDescending { it.isInstalled }
                .thenBy { it.name.lowercase() }
        )
    }

    /**
     * Checks updates for a single module via GitHub Releases API or update.json.
     */
    suspend fun checkModuleUpdate(module: TrackedModule): TrackedModule = withContext(Dispatchers.IO) {
        var releaseInfo: ModuleReleaseInfo? = null

        // 1. Try GitHub Releases API if repo is configured
        if (!module.repoOwner.isNullOrBlank() && !module.repoName.isNullOrBlank()) {
            releaseInfo = fetchGitHubRelease(module.repoOwner, module.repoName)
        }

        // 2. If no GitHub release info or failed, try updateJsonUrl
        if (releaseInfo == null && !module.updateJsonUrl.isNullOrBlank()) {
            releaseInfo = fetchUpdateJsonRelease(module.updateJsonUrl)
        }

        if (releaseInfo == null) {
            return@withContext module.copy(lastChecked = System.currentTimeMillis())
        }

        val hasNewerVersion = isRemoteNewer(
            installedVer = module.installedVersion,
            installedCode = module.installedVersionCode,
            remoteVer = releaseInfo.versionName,
            remoteCode = releaseInfo.versionCode,
            remoteTag = releaseInfo.tagName
        )

        module.copy(
            latestRelease = releaseInfo,
            hasUpdate = hasNewerVersion && module.isInstalled,
            lastChecked = System.currentTimeMillis()
        )
    }

    private fun parseGitHubReleaseJson(json: JSONObject): ModuleReleaseInfo? {
        return try {
            val tagName = json.optString("tag_name", "")
            if (tagName.isBlank()) return null

            val name = json.optString("name", tagName)
            val notes = json.optString("body", "")
            val publishedAt = json.optString("published_at", "")
            val htmlUrl = json.optString("html_url", "")

            val assetsArr = json.optJSONArray("assets") ?: JSONArray()
            val assetsList = mutableListOf<ModuleReleaseAsset>()
            for (i in 0 until assetsArr.length()) {
                val assetObj = assetsArr.getJSONObject(i)
                val assetName = assetObj.optString("name", "")
                val downloadUrl = assetObj.optString("browser_download_url", "")
                val size = assetObj.optLong("size", 0L)
                if (assetName.endsWith(".zip", ignoreCase = true)) {
                    assetsList.add(ModuleReleaseAsset(assetName, downloadUrl, size))
                }
            }

            // Select primary zip asset: prefer release over debug or first zip
            val primaryZip = assetsList.firstOrNull {
                !it.name.contains("debug", ignoreCase = true)
            } ?: assetsList.firstOrNull()

            ModuleReleaseInfo(
                tagName = tagName,
                versionName = if (name.isNotBlank()) name else tagName,
                versionCode = null,
                releaseNotes = notes,
                publishedAt = publishedAt,
                htmlUrl = htmlUrl,
                assets = assetsList,
                primaryZipAsset = primaryZip
            )
        } catch (e: Exception) {
            null
        }
    }

    private fun fetchGitHubRelease(owner: String, repo: String): ModuleReleaseInfo? {
        return try {
            val url = "https://api.github.com/repos/$owner/$repo/releases/latest"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "RootFix/1.0 (Android)")
                .header("Accept", "application/vnd.github.v3+json")
                .build()

            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) return null

            val body = response.body?.string() ?: return null
            val json = JSONObject(body)
            parseGitHubReleaseJson(json)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Fetches up to 15 past releases from GitHub Releases API for version selection.
     */
    suspend fun fetchModuleReleaseHistory(module: TrackedModule): List<ModuleReleaseInfo> = withContext(Dispatchers.IO) {
        val list = mutableListOf<ModuleReleaseInfo>()
        val owner = module.repoOwner
        val repo = module.repoName

        if (!owner.isNullOrBlank() && !repo.isNullOrBlank()) {
            try {
                val url = "https://api.github.com/repos/$owner/$repo/releases?per_page=15"
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "RootFix/1.0 (Android)")
                    .header("Accept", "application/vnd.github.v3+json")
                    .build()

                val response = httpClient.newCall(request).execute()
                if (response.isSuccessful) {
                    val body = response.body?.string()
                    if (!body.isNullOrBlank()) {
                        val arr = JSONArray(body)
                        for (i in 0 until arr.length()) {
                            val json = arr.getJSONObject(i)
                            val rel = parseGitHubReleaseJson(json)
                            if (rel != null && rel.primaryZipAsset != null) {
                                list.add(rel)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // If no GitHub releases or module uses update.json, fallback to latest release
        if (list.isEmpty()) {
            val target = if (module.latestRelease != null) module else checkModuleUpdate(module)
            target.latestRelease?.let { if (it.primaryZipAsset != null) list.add(it) }
        }
        list
    }

    private fun fetchUpdateJsonRelease(updateJsonUrl: String): ModuleReleaseInfo? {
        return try {
            val request = Request.Builder()
                .url(updateJsonUrl)
                .header("User-Agent", "RootFix/1.0 (Android)")
                .build()

            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) return null

            val body = response.body?.string() ?: return null
            val json = JSONObject(body)

            val version = json.optString("version", "")
            val versionCode = if (json.has("versionCode")) json.optLong("versionCode") else null
            val zipUrl = json.optString("zipUrl", "")
            val changelog = json.optString("changelog", "")

            val assetName = zipUrl.substringAfterLast('/', "module.zip")
            val primaryZip = if (zipUrl.isNotBlank()) ModuleReleaseAsset(assetName, zipUrl, 0L) else null

            ModuleReleaseInfo(
                tagName = version,
                versionName = version,
                versionCode = versionCode,
                releaseNotes = changelog,
                primaryZipAsset = primaryZip
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Determines whether the remote release is newer than the locally installed module.
     */
    fun isRemoteNewer(
        installedVer: String?,
        installedCode: Long?,
        remoteVer: String,
        remoteCode: Long?,
        remoteTag: String
    ): Boolean {
        if (installedVer.isNullOrBlank()) return false

        // 1. Compare version codes if both are available
        if (installedCode != null && remoteCode != null && installedCode > 0 && remoteCode > 0) {
            return remoteCode > installedCode
        }

        // 2. Semantic version comparison
        val cleanInstalled = cleanVersionString(installedVer)
        val cleanRemote = cleanVersionString(remoteVer.ifBlank { remoteTag })

        val installedParts = cleanInstalled.split(".").mapNotNull { it.toIntOrNull() }
        val remoteParts = cleanRemote.split(".").mapNotNull { it.toIntOrNull() }

        val maxLen = maxOf(installedParts.size, remoteParts.size)
        for (i in 0 until maxLen) {
            val inst = installedParts.getOrElse(i) { 0 }
            val rem = remoteParts.getOrElse(i) { 0 }
            if (rem > inst) return true
            if (rem < inst) return false
        }

        // If string equality fails but version parts are identical, check if remote tag is completely different
        return !cleanInstalled.equals(cleanRemote, ignoreCase = true) && remoteTag.isNotBlank() && !installedVer.contains(remoteTag)
    }

    private fun cleanVersionString(ver: String): String {
        return ver.trim()
            .removePrefix("v")
            .removePrefix("V")
            .substringBefore("-")
            .substringBefore(" ")
            .filter { it.isDigit() || it == '.' }
    }

    /**
     * Adds and tracks a custom GitHub repository or update.json (Obtainium style).
     * Accepts:
     * - "owner/repo" (e.g. "Dr-TSNG/ZygiskNext")
     * - "https://github.com/owner/repo"
     * - "https://.../update.json"
     */
    suspend fun addCustomTrackedModule(input: String): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        val trimmed = input.trim()
        if (trimmed.isBlank()) {
            return@withContext Pair(false, "Repository input cannot be empty.")
        }

        var owner: String? = null
        var repo: String? = null
        var updateJsonUrl: String? = null
        var customId = ""

        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            if (trimmed.contains("github.com/")) {
                val path = trimmed.substringAfter("github.com/").trim('/')
                val parts = path.split("/")
                if (parts.size >= 2) {
                    owner = parts[0]
                    repo = parts[1]
                    customId = repo.lowercase()
                } else {
                    return@withContext Pair(false, "Invalid GitHub repository URL.")
                }
            } else if (trimmed.endsWith(".json", ignoreCase = true)) {
                updateJsonUrl = trimmed
                customId = trimmed.substringAfterLast('/').substringBeforeLast('.')
            } else {
                return@withContext Pair(false, "Unsupported URL: Must be a GitHub repo or update.json URL.")
            }
        } else if (trimmed.contains("/")) {
            val parts = trimmed.split("/")
            if (parts.size == 2) {
                owner = parts[0].trim()
                repo = parts[1].trim()
                customId = repo.lowercase()
            } else {
                return@withContext Pair(false, "Invalid format. Use 'owner/repo' or full GitHub URL.")
            }
        } else {
            return@withContext Pair(false, "Please provide 'owner/repo' (e.g. '5ec1cff/TrickyStore') or a valid URL.")
        }

        val tracked = TrackedModule(
            id = customId,
            name = repo ?: customId,
            category = "Custom Tracked",
            repoOwner = owner,
            repoName = repo,
            updateJsonUrl = updateJsonUrl,
            isCustom = true
        )

        // Check release to verify it works
        val updated = checkModuleUpdate(tracked)
        if (updated.latestRelease?.primaryZipAsset == null) {
            return@withContext Pair(false, "Could not find a downloadable .zip release asset in $trimmed.")
        }

        // Save to preferences
        saveCustomTrackedModule(updated)
        Pair(true, "Successfully tracking ${updated.name} (Latest: ${updated.latestRelease.tagName})")
    }

    private fun getCustomTrackedList(): List<TrackedModule> {
        val jsonStr = prefs.getString(PREF_KEY_CUSTOM_MODULES, null) ?: return emptyList()
        return try {
            val arr = JSONArray(jsonStr)
            val list = mutableListOf<TrackedModule>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(
                    TrackedModule(
                        id = obj.getString("id"),
                        name = obj.getString("name"),
                        author = obj.optString("author", "Custom"),
                        description = obj.optString("description", ""),
                        category = obj.optString("category", "Custom Tracked"),
                        repoOwner = obj.optString("repoOwner").ifBlank { null },
                        repoName = obj.optString("repoName").ifBlank { null },
                        updateJsonUrl = obj.optString("updateJsonUrl").ifBlank { null },
                        isCustom = true
                    )
                )
            }
            list
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun saveCustomTrackedModule(module: TrackedModule) {
        val list = getCustomTrackedList().toMutableList()
        list.removeAll { it.id.equals(module.id, ignoreCase = true) }
        list.add(module)

        val arr = JSONArray()
        for (m in list) {
            val obj = JSONObject()
            obj.put("id", m.id)
            obj.put("name", m.name)
            obj.put("author", m.author)
            obj.put("description", m.description)
            obj.put("category", m.category)
            obj.put("repoOwner", m.repoOwner ?: "")
            obj.put("repoName", m.repoName ?: "")
            obj.put("updateJsonUrl", m.updateJsonUrl ?: "")
            arr.put(obj)
        }
        prefs.edit().putString(PREF_KEY_CUSTOM_MODULES, arr.toString()).apply()
    }

    suspend fun removeCustomTrackedModule(moduleId: String) = withContext(Dispatchers.IO) {
        val list = getCustomTrackedList().toMutableList()
        list.removeAll { it.id.equals(moduleId, ignoreCase = true) }

        val arr = JSONArray()
        for (m in list) {
            val obj = JSONObject()
            obj.put("id", m.id)
            obj.put("name", m.name)
            obj.put("author", m.author)
            obj.put("description", m.description)
            obj.put("category", m.category)
            obj.put("repoOwner", m.repoOwner ?: "")
            obj.put("repoName", m.repoName ?: "")
            obj.put("updateJsonUrl", m.updateJsonUrl ?: "")
            arr.put(obj)
        }
        prefs.edit().putString(PREF_KEY_CUSTOM_MODULES, arr.toString()).apply()
    }

    /**
     * Downloads the module ZIP asset with progress reporting and installs it
     * via Magisk CLI (magisk --install-module).
     */
    suspend fun downloadAndInstallModule(
        module: TrackedModule,
        releaseOverride: ModuleReleaseInfo? = null,
        onProgress: (InstallProgress) -> Unit
    ): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        val release = releaseOverride ?: module.latestRelease
        val zipAsset = release?.primaryZipAsset

        if (zipAsset == null || zipAsset.downloadUrl.isBlank()) {
            val err = "No valid zip download URL found for ${module.name}."
            onProgress(InstallProgress(InstallStage.FAILED, module.name, errorMessage = err))
            return@withContext Pair(false, err)
        }

        onProgress(InstallProgress(InstallStage.DOWNLOADING, module.name, progressPercent = 0.05f))

        // 1. Download file to app cache
        val destFile = File(context.cacheDir, "module_${module.id}_${System.currentTimeMillis()}.zip")
        try {
            val request = Request.Builder()
                .url(zipAsset.downloadUrl)
                .header("User-Agent", "RootFix/1.0 (Android)")
                .build()

            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                val err = "Download failed: HTTP ${response.code}"
                onProgress(InstallProgress(InstallStage.FAILED, module.name, errorMessage = err))
                return@withContext Pair(false, err)
            }

            val responseBody = response.body ?: throw Exception("Empty response body")
            val totalBytes = responseBody.contentLength()
            var downloadedBytes = 0L

            responseBody.byteStream().use { input ->
                FileOutputStream(destFile).use { output ->
                    val buffer = ByteArray(8 * 1024)
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        downloadedBytes += bytesRead
                        val progress = if (totalBytes > 0) downloadedBytes.toFloat() / totalBytes else 0.5f
                        onProgress(
                            InstallProgress(
                                stage = InstallStage.DOWNLOADING,
                                moduleName = module.name,
                                progressPercent = progress.coerceIn(0f, 1f),
                                downloadedBytes = downloadedBytes,
                                totalBytes = totalBytes
                            )
                        )
                    }
                    output.flush()
                }
            }

            // 2. Install module via Magisk CLI
            onProgress(
                InstallProgress(
                    stage = InstallStage.INSTALLING,
                    moduleName = module.name,
                    progressPercent = 1.0f,
                    downloadedBytes = downloadedBytes,
                    totalBytes = totalBytes
                )
            )

            // Make sure file is readable by root
            destFile.setReadable(true, false)

            val (success, logs) = magiskRepo.installModuleZip(destFile)

            if (success) {
                onProgress(
                    InstallProgress(
                        stage = InstallStage.COMPLETED,
                        moduleName = module.name,
                        progressPercent = 1.0f,
                        installLogs = logs,
                        isSuccess = true
                    )
                )
            } else {
                onProgress(
                    InstallProgress(
                        stage = InstallStage.FAILED,
                        moduleName = module.name,
                        installLogs = logs,
                        isSuccess = false,
                        errorMessage = "Magisk module installation reported errors."
                    )
                )
            }

            Pair(success, logs)
        } catch (e: Exception) {
            val err = "Installation error: ${e.localizedMessage ?: e.message}"
            onProgress(InstallProgress(InstallStage.FAILED, module.name, errorMessage = err))
            Pair(false, err)
        } finally {
            destFile.delete()
        }
    }

    /**
     * Set of repository IDs (lowercase) that the user removed from the catalog.
     */
    fun getRemovedCatalogIds(): Set<String> {
        return prefs.getStringSet(PREF_KEY_REMOVED_CATALOG, emptySet()) ?: emptySet()
    }

    fun hasRemovedCatalogRepositories(): Boolean {
        return getRemovedCatalogIds().isNotEmpty()
    }

    /**
     * Removes a repository from the tracked catalog (either custom or predefined).
     */
    suspend fun removeRepository(module: TrackedModule): Boolean = withContext(Dispatchers.IO) {
        val idLower = module.id.lowercase()
        if (module.isCustom) {
            removeCustomTrackedModule(module.id)
            true
        } else {
            val current = getRemovedCatalogIds().toMutableSet()
            current.add(idLower)
            if (idLower == "integrity_box" || idLower == "playintegrityfix") {
                current.add("integrity_box")
                current.add("playintegrityfix")
            }
            if (idLower == "zygisksu" || idLower == "zygisknext") {
                current.add("zygisksu")
                current.add("zygisknext")
            }
            prefs.edit().putStringSet(PREF_KEY_REMOVED_CATALOG, current).commit()
            true
        }
    }

    /**
     * Restores all predefined / curated repositories back into the catalog.
     */
    suspend fun restorePredefinedRepositories(): Boolean = withContext(Dispatchers.IO) {
        prefs.edit().remove(PREF_KEY_REMOVED_CATALOG).commit()
        true
    }
}
