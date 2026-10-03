package com.rootfix.app.data.repository

import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object RootExecutor {

    suspend fun isRootAvailable(): Boolean = withContext(Dispatchers.IO) {
        try {
            Shell.getShell().isRoot
        } catch (e: Exception) {
            false
        }
    }

    suspend fun execute(cmd: String): Pair<Boolean, List<String>> = withContext(Dispatchers.IO) {
        val result = Shell.cmd(cmd).exec()
        Pair(result.isSuccess, result.out)
    }

    suspend fun executeBatch(cmds: List<String>): Pair<Boolean, List<String>> = withContext(Dispatchers.IO) {
        val shellCmd = Shell.cmd(*cmds.toTypedArray())
        val result = shellCmd.exec()
        Pair(result.isSuccess, result.out)
    }

    suspend fun readFile(path: String): List<String>? = withContext(Dispatchers.IO) {
        val check = Shell.cmd("test -r \"$path\"").exec()
        if (!check.isSuccess) return@withContext null

        val result = Shell.cmd("cat \"$path\"").exec()
        if (result.isSuccess) result.out else null
    }

    /**
     * Atomically writes content to a target file in /data/adb/ or system locations:
     * 1. Writes content to a cache file
     * 2. Copies to target.tmp
     * 3. Sets permissions
     * 4. Moves atomically to target destination
     * 5. Restores SELinux context
     */
    suspend fun writeFileAtomically(
        cacheDir: File,
        targetPath: String,
        content: String,
        permissions: String = "644"
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val tempLocalFile = File(cacheDir, "rootfix_${System.currentTimeMillis()}.tmp")
            tempLocalFile.writeText(content)

            val tempRemotePath = "$targetPath.tmp"
            val backupPath = "$targetPath.bak"

            val cmds = mutableListOf<String>()
            // Backup existing if exists
            cmds.add("if [ -f \"$targetPath\" ]; then cp -f \"$targetPath\" \"$backupPath\"; fi")
            // Copy local cache to remote temp
            cmds.add("cp -f \"${tempLocalFile.absolutePath}\" \"$tempRemotePath\"")
            // Set permissions
            cmds.add("chmod $permissions \"$tempRemotePath\"")
            // Move atomically
            cmds.add("mv -f \"$tempRemotePath\" \"$targetPath\"")
            // Restore SELinux context
            cmds.add("restorecon \"$targetPath\"")

            val result = Shell.cmd(*cmds.toTypedArray()).exec()
            tempLocalFile.delete()
            result.isSuccess
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun killProcess(processName: String): Boolean = withContext(Dispatchers.IO) {
        val result = Shell.cmd(
            "pkill -f \"$processName\" 2>/dev/null || killall -9 \"$processName\" 2>/dev/null || true"
        ).exec()
        result.isSuccess
    }
}
