package com.rootfix.app.data.repository

import android.content.Context
import com.rootfix.app.data.model.MagiskModule
import com.rootfix.app.data.model.ModuleScriptInfo
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
            val (webUiResult, _) = RootExecutor.execute(
                "test -f \"/data/adb/modules/$trimmedDir/webroot/index.html\" -o -f \"/data/adb/modules_update/$trimmedDir/webroot/index.html\""
            )
            val availableScripts = getModuleScripts(trimmedDir)

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
                    hasAction = actionResult,
                    hasWebUi = webUiResult,
                    availableScripts = availableScripts
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
            "specter" -> listOf("specter", "Specter")
            "teesim", "teesimulator" -> listOf("teesim", "TEESimulator")
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
            "specter" -> listOf("specter", "Specter")
            "teesim", "teesimulator" -> listOf("teesim", "TEESimulator")
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

    suspend fun getModuleScripts(moduleId: String): List<ModuleScriptInfo> = withContext(Dispatchers.IO) {
        val scripts = mutableListOf<ModuleScriptInfo>()
        val candidateDirs = listOf("/data/adb/modules/$moduleId", "/data/adb/modules_update/$moduleId")
        var activeDir: String? = null
        for (d in candidateDirs) {
            val (dirExists, _) = RootExecutor.execute("test -d \"$d\"")
            if (dirExists) {
                activeDir = d
                break
            }
        }
        if (activeDir == null) return@withContext emptyList()

        val findCmd = "find \"$activeDir\" -maxdepth 2 -type f -name \"*.sh\" ! -name \"uninstall.sh\" ! -name \"common_*.sh\" ! -path \"*/lib/*\" ! -path \"*/deps/*\" ! -path \"*/webroot/*\" 2>/dev/null | sort"
        val (_, lines) = RootExecutor.execute(findCmd)

        for (path in lines) {
            val trimmedPath = path.trim()
            if (trimmedPath.isBlank()) continue
            scripts.add(resolveScriptInfo(activeDir, trimmedPath))
        }

        scripts.sortedWith(compareBy({ it.category != "Feature" }, { it.name }))
    }

    private fun resolveScriptInfo(modDir: String, fullPath: String): ModuleScriptInfo {
        val relPath = fullPath.removePrefix("$modDir/").trimStart('/')
        val fileName = fullPath.substringAfterLast('/')

        val (name, category) = when (relPath) {
            "features/kill_play_store.sh" -> "Kill Google Play Store" to "Feature"
            "features/kill_all.sh" -> "Kill Play Store & Target Apps" to "Feature"
            "features/target.sh" -> "Sync App Targeting (target.txt)" to "Feature"
            "features/auto_target.sh" -> "Auto-detect Banking Targets" to "Feature"
            "features/keybox.sh" -> "Sync / Update Keybox" to "Keybox"
            "features/keybox_info.sh" -> "Check Keybox Details" to "Keybox"
            "features/keystore_info.sh" -> "Keystore Diagnostics" to "Keybox"
            "features/teesim_mode.sh" -> "Toggle TEESimulator Mode" to "Attestation"
            "features/pif.sh" -> "Sync Play Integrity Fix Props" to "Play Integrity"
            "features/pif_props.sh" -> "Inspect PIF Props" to "Play Integrity"
            "features/rom_fingerprint.sh" -> "Sync ROM Fingerprint" to "Spoofing"
            "features/security_patch.sh" -> "Update Security Patch Level" to "Spoofing"
            "features/boot_state_props.sh" -> "Sync Boot State Props" to "Spoofing"
            "features/boot_hash.sh" -> "Sync Boot Hash" to "Spoofing"
            "features/crom_props.sh" -> "Sync Custom ROM Props" to "Spoofing"
            "features/widevine.sh" -> "Configure Widevine DRM" to "DRM"
            "features/monet.sh" -> "Toggle Monet Theming" to "UI"
            "features/export_logs.sh" -> "Export Diagnostics & Logs" to "Diagnostics"
            "features/debug.sh" -> "Run Module Debug Diagnostics" to "Diagnostics"
            "features/app_info.sh" -> "Inspect App Target Info" to "Diagnostics"
            "features/restore_defaults.sh" -> "Reset Module to Defaults" to "Maintenance"
            "features/restore_backups.sh" -> "Restore Backup Files" to "Maintenance"
            "features/cleanup.sh" -> "Run Cache Cleanup" to "Maintenance"
            "features/adb_disabler.sh" -> "Toggle ADB Detection Disabler" to "Feature"
            "features/gms.sh" -> "Toggle GMS Profile" to "Feature"
            "features/hma.sh" -> "Sync HMA Config" to "Feature"
            "features/zygisk_next.sh" -> "Configure Zygisk Next" to "Feature"
            "features/omk_restart_keymint.sh" -> "Restart KeyMint Service" to "Service"
            "features/omk_restart_injector.sh" -> "Restart Injector Service" to "Service"
            "features/omk_trust.sh" -> "Configure OMK Trust" to "Security"
            "features/first_boot_setup.sh" -> "Run First Boot Setup" to "Setup"
            "autopif4.sh" -> "Auto PIF Injector (autopif4)" to "Automation"
            "killpi.sh" -> "Kill GMS / Play Services" to "Utility"
            "migrate.sh" -> "Migrate Legacy Configuration" to "Maintenance"
            "cleanup.sh" -> "Run Module Cleanup" to "Maintenance"
            "emulated-soft-reboot.sh" -> "Emulated Soft Reboot" to "System"
            "refresh_desc.sh" -> "Refresh Module Description" to "Utility"
            "hotinstall.sh" -> "Run Hot Installation" to "Setup"
            "service.sh" -> "Execute service.sh (Daemon)" to "Lifecycle"
            "post-fs-data.sh" -> "Execute post-fs-data.sh" to "Lifecycle"
            "action.sh" -> "Execute action.sh (Module Action)" to "Action"
            else -> {
                val baseName = fileName.removeSuffix(".sh").replace('_', ' ').replace('-', ' ')
                val titleCased = baseName.split(' ')
                    .filter { it.isNotBlank() }
                    .joinToString(" ") { word -> word.replaceFirstChar { c -> c.uppercase() } }
                titleCased to if (relPath.startsWith("features/")) "Feature" else "Utility"
            }
        }
        return ModuleScriptInfo(
            name = name,
            relativePath = relPath,
            fullPath = fullPath,
            category = category
        )
    }

    suspend fun executeScript(moduleId: String, scriptPath: String): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        val (exists, _) = RootExecutor.execute("test -f \"$scriptPath\" -a -s \"$scriptPath\"")
        if (!exists) {
            return@withContext Pair(false, "Script not found or empty: $scriptPath")
        }
        val dir = scriptPath.substringBeforeLast('/')
        val modDir = if (dir.contains("/features")) dir.substringBeforeLast("/features") else dir
        val cmd = "cd \"$modDir\" && MODDIR=\"$modDir\" sh \"$scriptPath\""
        val (success, output) = RootExecutor.execute(cmd)
        Pair(success, output.joinToString("\n"))
    }

    suspend fun launchModuleWebUi(context: Context, moduleId: String): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        val checkHtml = "test -f \"/data/adb/modules/$moduleId/webroot/index.html\" -o -f \"/data/adb/modules_update/$moduleId/webroot/index.html\""
        val (htmlExists, _) = RootExecutor.execute(checkHtml)
        if (!htmlExists) {
            return@withContext Pair(false, "Module '$moduleId' does not have a WebUI (webroot/index.html not found).")
        }

        // Check if KSU WebUI host package is installed
        val (pkgCheck, pkgOut) = RootExecutor.execute("pm path io.github.a13e300.ksuwebui 2>/dev/null")
        val isKsuWebUiInstalled = pkgCheck && pkgOut.any { it.startsWith("package:") }

        if (!isKsuWebUiInstalled) {
            val (ksuCheck, ksuOut) = RootExecutor.execute("pm path me.weishu.kernelsu 2>/dev/null")
            val isKsuInstalled = ksuCheck && ksuOut.any { it.startsWith("package:") }
            if (isKsuInstalled) {
                val (kLaunch, _) = RootExecutor.execute("am start -n me.weishu.kernelsu/.ui.webui.WebUIActivity -d 'ksuwebui://webui/$moduleId' 2>/dev/null")
                if (kLaunch) return@withContext Pair(true, "WebUI launched via KernelSU manager.")
            }

            return@withContext Pair(
                false,
                "KernelSU WebUI host (io.github.a13e300.ksuwebui) is not installed.\nPlease install the WebUI standalone host APK to open module web interfaces."
            )
        }

        val (launchSuccess, launchOut) = RootExecutor.execute("am start -n io.github.a13e300.ksuwebui/.WebUIActivity -d 'ksuwebui://webui/$moduleId'")
        if (launchSuccess) {
            Pair(true, "WebUI launched successfully.")
        } else {
            Pair(false, "Failed to launch WebUI: ${launchOut.joinToString("\n")}")
        }
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


