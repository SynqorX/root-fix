package com.rootfix.app.data.repository

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class GooglePackageInfo(
    val packageName: String,
    val displayName: String,
    val isInstalled: Boolean,
    val isSelected: Boolean = true
)

class GoogleServicesRepository {

    private val targetPackages = listOf(
        "com.google.android.gms" to "Google Play Services",
        "com.android.vending" to "Google Play Store",
        "com.google.android.gsf" to "Google Services Framework",
        "gr.nikolasspyr.integritycheck" to "Play Integrity Checker",
        "com.google.android.safetycore" to "Android Safety Core",
        "com.google.android.verifier" to "Google Play Protect Service"
    )

    suspend fun getInstalledGooglePackages(): List<GooglePackageInfo> = withContext(Dispatchers.IO) {
        val (success, lines) = RootExecutor.execute("pm list packages")
        val installedSet = lines.map { it.removePrefix("package:").trim() }.toSet()

        targetPackages.map { (pkg, name) ->
            GooglePackageInfo(
                packageName = pkg,
                displayName = name,
                isInstalled = installedSet.contains(pkg),
                isSelected = true
            )
        }
    }

    suspend fun clearCache(packages: List<String>): Boolean = withContext(Dispatchers.IO) {
        val cmds = mutableListOf<String>()
        for (pkg in packages) {
            cmds.add("rm -rf /data/data/$pkg/cache/* /data/data/$pkg/code_cache/* /data/user_de/0/$pkg/cache/* 2>/dev/null || true")
            cmds.add("am force-stop $pkg 2>/dev/null || true")
        }
        cmds.add("pkill -9 -f com.google.android.gms.unstable 2>/dev/null || true")
        cmds.add("pkill -9 -f com.android.vending 2>/dev/null || true")
        val (success, _) = RootExecutor.executeBatch(cmds)
        success
    }

    suspend fun clearFullData(packages: List<String>): Boolean = withContext(Dispatchers.IO) {
        val cmds = mutableListOf<String>()
        for (pkg in packages) {
            cmds.add("pm clear $pkg")
            cmds.add("am force-stop $pkg 2>/dev/null || true")
        }
        cmds.add("pkill -9 -f com.google.android.gms.unstable 2>/dev/null || true")
        cmds.add("pkill -9 -f com.android.vending 2>/dev/null || true")
        val (success, _) = RootExecutor.executeBatch(cmds)
        success
    }
}
