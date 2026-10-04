package com.rootfix.app.data.repository

import com.rootfix.app.data.model.MagiskModule
import com.rootfix.app.data.model.RootStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class MagiskRepository {

    suspend fun getRootStatus(): RootStatus = withContext(Dispatchers.IO) {
        val model = android.os.Build.MODEL.ifBlank { "Android Device" }
        val release = android.os.Build.VERSION.RELEASE
        val sdk = android.os.Build.VERSION.SDK_INT

        val isRooted = RootExecutor.isRootAvailable()
        if (!isRooted) {
            return@withContext RootStatus(
                isRootGranted = false,
                magiskVersion = "Not Granted",
                seLinuxMode = "Unknown",
                deviceModel = model,
                androidVersion = release,
                apiLevel = sdk
            )
        }

        val (_, magiskVerOut) = RootExecutor.execute("magisk -v 2>/dev/null || echo ''")
        val (_, magiskVerCodeOut) = RootExecutor.execute("magisk -V 2>/dev/null || echo '0'")
        val (_, seLinuxOut) = RootExecutor.execute("getenforce 2>/dev/null || echo 'Enforcing'")

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

    suspend fun requestRootAccess(): Boolean = withContext(Dispatchers.IO) {
        RootExecutor.requestRoot()
    }

    suspend fun getInstalledModules(): List<MagiskModule> = withContext(Dispatchers.IO) {
        val modules = mutableListOf<MagiskModule>()
        val (_, dirs) = RootExecutor.execute("{ ls -1 /data/adb/modules 2>/dev/null; ls -1 /data/adb/modules_update 2>/dev/null; } | sort -u")

        for (dirName in dirs) {
            val trimmedDir = dirName.trim()
            if (trimmedDir.isEmpty() || trimmedDir.startsWith(".")) continue

            var modPath = "/data/adb/modules/$trimmedDir"
            var propLines = RootExecutor.readFile("$modPath/module.prop")
            if (propLines == null) {
                modPath = "/data/adb/modules_update/$trimmedDir"
                propLines = RootExecutor.readFile("$modPath/module.prop") ?: continue
            }

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

            val (disabledResult, _) = RootExecutor.execute("test -f \"/data/adb/modules/$trimmedDir/disable\" -o -f \"/data/adb/modules_update/$trimmedDir/disable\"")
            val (removeResult, _) = RootExecutor.execute("test -f \"/data/adb/modules/$trimmedDir/remove\" -o -f \"/data/adb/modules_update/$trimmedDir/remove\"")
            val (actionResult, _) = RootExecutor.execute(
                "test -f \"/data/adb/modules/$trimmedDir/action.sh\" -a -s \"/data/adb/modules/$trimmedDir/action.sh\" " +
                "-o -f \"/data/adb/modules_update/$trimmedDir/action.sh\" -a -s \"/data/adb/modules_update/$trimmedDir/action.sh\""
            )

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
                    isRemovePending = removeResult,
                    hasAction = actionResult
                )
            )
        }

        modules.sortedBy { it.name.lowercase() }
    }

    suspend fun setModuleEnabled(moduleId: String, enabled: Boolean): Boolean = withContext(Dispatchers.IO) {
        val cmd = if (enabled) {
            "rm -f \"/data/adb/modules/$moduleId/disable\" \"/data/adb/modules_update/$moduleId/disable\""
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
            "rm -f \"/data/adb/modules/$moduleId/remove\" \"/data/adb/modules_update/$moduleId/remove\""
        }
        val (success, _) = RootExecutor.execute(cmd)
        success
    }

    suspend fun installModuleZip(zipFile: File): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        val (success, output) = RootExecutor.execute("magisk --install-module \"${zipFile.absolutePath}\"")
        Pair(success, output.joinToString("\n"))
    }

    suspend fun isMagiskZygiskEnabled(): Boolean = withContext(Dispatchers.IO) {
        val (success, output) = RootExecutor.execute("magisk --sqlite \"SELECT value FROM settings WHERE key='zygisk';\" 2>/dev/null")
        if (!success) return@withContext false
        output.any { it.contains("value=1") }
    }

    suspend fun setMagiskZygiskEnabled(enabled: Boolean): Boolean = withContext(Dispatchers.IO) {
        val v = if (enabled) 1 else 0
        val (success, _) = RootExecutor.execute("magisk --sqlite \"REPLACE INTO settings (key, value) VALUES ('zygisk', $v);\"")
        success
    }

    suspend fun removeModuleImmediately(moduleId: String): Boolean = withContext(Dispatchers.IO) {
        val candidateIds = when (moduleId.lowercase()) {
            "integrity_box" -> listOf("playintegrity", "integrity_box")
            "playintegrity" -> listOf("playintegrity", "integrity_box")
            "zygisknext" -> listOf("zygisksu", "zygisknext")
            "zygisksu" -> listOf("zygisksu", "zygisknext")
            "yurikey" -> listOf("Yurikey", "yurikey")
            else -> listOf(moduleId)
        }
        val cmd = candidateIds.joinToString(" ; ") { id ->
            "test -f \"/data/adb/modules/$id/uninstall.sh\" && sh \"/data/adb/modules/$id/uninstall.sh\" 2>/dev/null || true; rm -rf \"/data/adb/modules/$id\" \"/data/adb/modules_update/$id\""
        }
        val (success, _) = RootExecutor.execute(cmd)
        success
    }

    suspend fun executeModuleAction(moduleId: String): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        val candidateIds = when (moduleId.lowercase()) {
            "integrity_box" -> listOf("playintegrity", "integrity_box")
            "playintegrity" -> listOf("playintegrity", "integrity_box")
            "zygisknext" -> listOf("zygisksu", "zygisknext")
            "zygisksu" -> listOf("zygisksu", "zygisknext")
            "tricky_store" -> listOf("tricky_store", "TA_utl", "ta_utl")
            "ta_utl" -> listOf("TA_utl", "ta_utl", "tricky_store")
            "yurikey" -> listOf("Yurikey", "yurikey")
            else -> listOf(moduleId)
        }

        var actionScript: String? = null
        for (id in candidateIds) {
            val pathUpdate = "/data/adb/modules_update/$id/action.sh"
            if (RootExecutor.execute("test -f \"$pathUpdate\" -a -s \"$pathUpdate\"").first) {
                actionScript = pathUpdate
                break
            }
            val pathNormal = "/data/adb/modules/$id/action.sh"
            if (RootExecutor.execute("test -f \"$pathNormal\" -a -s \"$pathNormal\"").first) {
                actionScript = pathNormal
                break
            }
        }

        if (actionScript == null) {
            return@withContext Pair(false, "No executable action.sh found for module: $moduleId")
        }

        val dir = actionScript.substringBeforeLast('/')
        val (success, output) = RootExecutor.execute("cd \"$dir\" && sh \"$actionScript\"")
        Pair(success, output.joinToString("\n"))
    }

    suspend fun rebootDevice(mode: RebootMode = RebootMode.STANDARD): Boolean = withContext(Dispatchers.IO) {
        val (success, _) = RootExecutor.execute(mode.command)
        success
    }
}

enum class RebootMode(val label: String, val description: String, val command: String) {
    STANDARD("System Reboot", "Full device restart", "/system/bin/svc power reboot || reboot || /system/bin/reboot"),
    SOFT("Soft Reboot (Userspace)", "Quickly restarts Zygote and Android UI without hardware reboot", "setprop ctl.restart zygote"),
    RECOVERY("Recovery Mode", "Reboots into recovery (TWRP/OrangeFox)", "/system/bin/reboot recovery || reboot recovery"),
    BOOTLOADER("Bootloader / Download", "Reboots into bootloader or download mode", "/system/bin/reboot bootloader || reboot bootloader")
}


