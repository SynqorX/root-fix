package com.rootfix.app.data.repository

import com.rootfix.app.data.model.MagiskModule
import com.rootfix.app.data.model.RootStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class MagiskRepository {

    suspend fun getRootStatus(): RootStatus = withContext(Dispatchers.IO) {
        val isRooted = RootExecutor.isRootAvailable()
        if (!isRooted) {
            return@withContext RootStatus(isRootGranted = false)
        }

        val (_, magiskVerOut) = RootExecutor.execute("magisk -v 2>/dev/null || echo ''")
        val (_, magiskVerCodeOut) = RootExecutor.execute("magisk -V 2>/dev/null || echo '0'")
        val (_, seLinuxOut) = RootExecutor.execute("getenforce 2>/dev/null || echo 'Enforcing'")

        val model = android.os.Build.MODEL.ifBlank { "Android Device" }
        val release = android.os.Build.VERSION.RELEASE
        val sdk = android.os.Build.VERSION.SDK_INT

        RootStatus(
            isRootGranted = true,
            magiskVersion = magiskVerOut.firstOrNull()?.trim()?.ifBlank { null } ?: "30.7:MAGISK:R",
            magiskVersionCode = magiskVerCodeOut.firstOrNull()?.trim()?.toIntOrNull() ?: 30700,
            seLinuxMode = seLinuxOut.firstOrNull()?.trim() ?: "Enforcing",
            deviceModel = model,
            androidVersion = release,
            apiLevel = sdk
        )
    }

    suspend fun getInstalledModules(): List<MagiskModule> = withContext(Dispatchers.IO) {
        val modules = mutableListOf<MagiskModule>()
        val (_, dirs) = RootExecutor.execute("ls -1 /data/adb/modules 2>/dev/null")

        for (dirName in dirs) {
            val trimmedDir = dirName.trim()
            if (trimmedDir.isEmpty()) continue

            val modPath = "/data/adb/modules/$trimmedDir"
            val propLines = RootExecutor.readFile("$modPath/module.prop") ?: continue

            var id = trimmedDir
            var name = trimmedDir
            var version = "1.0"
            var versionCode = 1L
            var author = "Unknown"
            var description = ""
            var updateJson: String? = null

            for (raw in propLines) {
                val line = raw.trim()
                if (line.isEmpty() || line.startsWith("#")) continue
                val idx = line.indexOf('=')
                if (idx <= 0) continue
                val key = line.substring(0, idx).trim()
                val value = line.substring(idx + 1).trim()

                when (key) {
                    "id" -> id = value
                    "name" -> name = value
                    "version" -> version = value
                    "versionCode" -> versionCode = value.toLongOrNull() ?: 1L
                    "author" -> author = value
                    "description" -> description = value
                    "updateJson" -> updateJson = value
                }
            }

            val (disabledResult, _) = RootExecutor.execute("test -f \"$modPath/disable\"")
            val (removeResult, _) = RootExecutor.execute("test -f \"$modPath/remove\"")

            modules.add(
                MagiskModule(
                    id = id,
                    name = name,
                    version = version,
                    versionCode = versionCode,
                    author = author,
                    description = description,
                    updateJson = updateJson,
                    isEnabled = !disabledResult,
                    isRemovePending = removeResult
                )
            )
        }

        modules.sortedBy { it.name.lowercase() }
    }

    suspend fun setModuleEnabled(moduleId: String, enabled: Boolean): Boolean = withContext(Dispatchers.IO) {
        val cmd = if (enabled) {
            "rm -f \"/data/adb/modules/$moduleId/disable\""
        } else {
            "touch \"/data/adb/modules/$moduleId/disable\""
        }
        val (success, _) = RootExecutor.execute(cmd)
        success
    }

    suspend fun setModuleRemoval(moduleId: String, remove: Boolean): Boolean = withContext(Dispatchers.IO) {
        val cmd = if (remove) {
            "touch \"/data/adb/modules/$moduleId/remove\""
        } else {
            "rm -f \"/data/adb/modules/$moduleId/remove\""
        }
        val (success, _) = RootExecutor.execute(cmd)
        success
    }

    suspend fun installModuleZip(zipFile: File): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        val (success, output) = RootExecutor.execute("magisk --install-module \"${zipFile.absolutePath}\"")
        Pair(success, output.joinToString("\n"))
    }
}
