package com.rootfix.app.data.model

data class ModuleReleaseAsset(
    val name: String,
    val downloadUrl: String,
    val sizeBytes: Long = 0L
)

data class ModuleReleaseInfo(
    val tagName: String,
    val versionName: String,
    val versionCode: Long? = null,
    val releaseNotes: String = "",
    val publishedAt: String = "",
    val htmlUrl: String = "",
    val assets: List<ModuleReleaseAsset> = emptyList(),
    val primaryZipAsset: ModuleReleaseAsset? = null
)

data class TrackedModule(
    val id: String,
    val name: String,
    val author: String = "",
    val description: String = "",
    val category: String = "Utilities",
    val repoOwner: String? = null,
    val repoName: String? = null,
    val updateJsonUrl: String? = null,
    val installedVersion: String? = null,
    val installedVersionCode: Long? = null,
    val latestRelease: ModuleReleaseInfo? = null,
    val isInstalled: Boolean = false,
    val hasUpdate: Boolean = false,
    val isCustom: Boolean = false,
    val lastChecked: Long = 0L
) {
    val githubSlug: String?
        get() = if (repoOwner != null && repoName != null) "$repoOwner/$repoName" else null
}

data class InstallProgress(
    val stage: InstallStage = InstallStage.IDLE,
    val moduleName: String = "",
    val progressPercent: Float = 0f,
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = 0L,
    val installLogs: String = "",
    val isSuccess: Boolean? = null,
    val errorMessage: String? = null
)

enum class InstallStage {
    IDLE,
    FETCHING_RELEASE,
    DOWNLOADING,
    INSTALLING,
    COMPLETED,
    FAILED
}
