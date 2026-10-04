package com.rootfix.app.data.repository

import com.rootfix.app.data.model.KeyboxStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.File
import java.io.StringReader

class KeyboxRepository {

    private val trickyStoreDir = "/data/adb/tricky_store"
    private val keyboxPath = "$trickyStoreDir/keybox.xml"
    private val targetPath = "$trickyStoreDir/target.txt"
    private val patchPath = "$trickyStoreDir/security_patch.txt"

    suspend fun getKeyboxStatus(): KeyboxStatus = withContext(Dispatchers.IO) {
        // 1. Check if TrickyStore module is installed
        var isInstalled = false
        var modVersion: String? = null

        val (mod1Check, _) = RootExecutor.execute("test -d /data/adb/modules/tricky_store")
        val (mod2Check, _) = RootExecutor.execute("test -d /data/adb/modules/TrickyStore")

        val activeModDir = when {
            mod1Check -> "/data/adb/modules/tricky_store"
            mod2Check -> "/data/adb/modules/TrickyStore"
            else -> null
        }

        if (activeModDir != null) {
            isInstalled = true
            val propLines = RootExecutor.readFile("$activeModDir/module.prop")
            if (propLines != null) {
                for (line in propLines) {
                    val trimmed = line.trim()
                    if (trimmed.startsWith("version=")) {
                        modVersion = trimmed.substringAfter("version=").trim()
                        break
                    }
                }
            }
        }

        // 2. Check if keybox.xml exists
        val (hasKeybox, _) = RootExecutor.execute("test -f \"$keyboxPath\"")
        var rawXml = ""
        var deviceId: String? = null
        var keyAlgorithm: String? = null
        var hasPrivateKey = false
        var certCount = 0
        var isValidSchema = false

        if (hasKeybox) {
            val lines = RootExecutor.readFile(keyboxPath)
            if (lines != null && lines.isNotEmpty()) {
                rawXml = lines.joinToString("\n")
                try {
                    val factory = XmlPullParserFactory.newInstance()
                    factory.isNamespaceAware = false
                    val parser = factory.newPullParser()
                    parser.setInput(StringReader(rawXml))

                    var eventType = parser.eventType
                    while (eventType != XmlPullParser.END_DOCUMENT) {
                        if (eventType == XmlPullParser.START_TAG) {
                            when (parser.name.lowercase()) {
                                "keybox" -> {
                                    for (i in 0 until parser.attributeCount) {
                                        if (parser.getAttributeName(i).equals("DeviceID", ignoreCase = true)) {
                                            deviceId = parser.getAttributeValue(i)
                                        }
                                    }
                                }
                                "key" -> {
                                    for (i in 0 until parser.attributeCount) {
                                        if (parser.getAttributeName(i).equals("algorithm", ignoreCase = true)) {
                                            keyAlgorithm = parser.getAttributeValue(i).uppercase()
                                        }
                                    }
                                }
                                "privatekey" -> {
                                    hasPrivateKey = true
                                }
                                "certificate" -> {
                                    certCount++
                                }
                            }
                        }
                        eventType = parser.next()
                    }
                    isValidSchema = hasPrivateKey && certCount > 0
                } catch (e: Exception) {
                    // Fallback substring checks
                    if (rawXml.contains("<PrivateKey", ignoreCase = true) && rawXml.contains("-----BEGIN", ignoreCase = true)) {
                        hasPrivateKey = true
                    }
                    certCount = rawXml.split(Regex("(?i)<Certificate")).size - 1
                    isValidSchema = hasPrivateKey && certCount > 0
                }
            }
        }

        // 3. Read target.txt
        val targetPackages = mutableListOf<String>()
        val targetLines = RootExecutor.readFile(targetPath)
        if (targetLines != null) {
            for (line in targetLines) {
                val pkg = line.trim()
                if (pkg.isNotEmpty() && !pkg.startsWith("#")) {
                    targetPackages.add(pkg)
                }
            }
        }

        // 4. Read security_patch.txt
        var patchOverride: String? = null
        val patchLines = RootExecutor.readFile(patchPath)
        if (patchLines != null) {
            patchOverride = patchLines.firstOrNull { it.isNotBlank() && !it.startsWith("#") }?.trim()
        }

        KeyboxStatus(
            isTrickyStoreInstalled = isInstalled,
            trickyStoreVersion = modVersion,
            hasKeyboxFile = hasKeybox && rawXml.isNotBlank(),
            keyboxFilePath = keyboxPath,
            deviceId = deviceId,
            keyAlgorithm = keyAlgorithm ?: if (rawXml.contains("EC PRIVATE", ignoreCase = true)) "ECDSA" else if (rawXml.contains("RSA PRIVATE", ignoreCase = true)) "RSA" else null,
            hasPrivateKey = hasPrivateKey,
            certificateCount = certCount,
            targetPackages = targetPackages,
            securityPatchOverride = patchOverride,
            isValidSchema = isValidSchema,
            rawXml = rawXml
        )
    }

    suspend fun saveKeyboxXml(cacheDir: File, xmlContent: String): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        val trimmed = xmlContent.trim()
        if (trimmed.isEmpty()) {
            return@withContext Pair(false, "Keybox XML content cannot be empty.")
        }

        if (!trimmed.contains("<Keybox", ignoreCase = true) ||
            !trimmed.contains("PrivateKey", ignoreCase = true) ||
            !trimmed.contains("Certificate", ignoreCase = true)
        ) {
            return@withContext Pair(false, "Invalid Keybox XML: must contain <Keybox>, <PrivateKey>, and <Certificate> tags.")
        }

        // Ensure directory exists
        RootExecutor.execute("mkdir -p \"$trickyStoreDir\" && chmod 755 \"$trickyStoreDir\"")

        val saved = RootExecutor.writeFileAtomically(
            cacheDir = cacheDir,
            targetPath = keyboxPath,
            content = trimmed,
            permissions = "644"
        )

        if (!saved) {
            return@withContext Pair(false, "Failed to write keybox.xml atomically.")
        }

        // Ensure default target packages exist if not present
        val (targetExists, _) = RootExecutor.execute("test -f \"$targetPath\"")
        if (!targetExists) {
            val defaultTargets = "com.google.android.gms\ncom.android.vending\ngr.nikolasspyr.integritycheck\n"
            RootExecutor.writeFileAtomically(cacheDir, targetPath, defaultTargets, "644")
        }

        // Restart GMS so Keystore hooks immediately reload the keybox
        RootExecutor.execute("pkill -9 -f com.google.android.gms.unstable 2>/dev/null || true")

        Pair(true, "Keybox XML successfully deployed to $keyboxPath.")
    }

    suspend fun saveTargetPackages(cacheDir: File, packages: List<String>): Boolean = withContext(Dispatchers.IO) {
        RootExecutor.execute("mkdir -p \"$trickyStoreDir\"")
        val content = packages.map { it.trim() }.filter { it.isNotEmpty() }.joinToString("\n") + "\n"
        val saved = RootExecutor.writeFileAtomically(
            cacheDir = cacheDir,
            targetPath = targetPath,
            content = content,
            permissions = "644"
        )
        if (saved) {
            RootExecutor.execute("pkill -9 -f com.google.android.gms.unstable 2>/dev/null || true")
        }
        saved
    }

    suspend fun saveSecurityPatch(cacheDir: File, patch: String): Boolean = withContext(Dispatchers.IO) {
        RootExecutor.execute("mkdir -p \"$trickyStoreDir\"")
        val content = patch.trim() + "\n"
        RootExecutor.writeFileAtomically(
            cacheDir = cacheDir,
            targetPath = patchPath,
            content = content,
            permissions = "644"
        )
    }

    suspend fun deleteKeybox(): Boolean = withContext(Dispatchers.IO) {
        val (success, _) = RootExecutor.execute("rm -f \"$keyboxPath\"")
        if (success) {
            RootExecutor.execute("pkill -9 -f com.google.android.gms.unstable 2>/dev/null || true")
        }
        success
    }
}
