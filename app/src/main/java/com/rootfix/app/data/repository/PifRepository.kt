package com.rootfix.app.data.repository

import com.rootfix.app.data.model.PifProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

data class AutoPifDevice(
    val model: String,
    val product: String
)

class PifRepository {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private val pifPropPath = "/data/adb/pif.prop"
    private val autoPifScriptPath = "/data/adb/modules/playintegrityfix/autopif.sh"

    suspend fun getActiveProfile(): PifProfile? = withContext(Dispatchers.IO) {
        val lines = RootExecutor.readFile(pifPropPath)
        if (lines != null && lines.isNotEmpty()) {
            return@withContext PifProfile.fromPropLines(lines, pifPropPath)
        }
        null
    }

    /**
     * Atomically saves the PIF profile to /data/adb/pif.prop.
     * Enforces all 7 required spoofs:
     * spoofBuild, spoofProps, spoofProvider, spoofSignature, spoofVendingBuild, spoofVendingSdk, and DEBUG.
     */
    suspend fun saveProfile(cacheDir: File, profile: PifProfile): Boolean = withContext(Dispatchers.IO) {
        val enforcedProfile = profile.withAllSpoofsEnabled()
        val propContent = enforcedProfile.toPropString()
        RootExecutor.writeFileAtomically(
            cacheDir = cacheDir,
            targetPath = pifPropPath,
            content = propContent,
            permissions = "644"
        )
    }

    suspend fun restartGms(): Boolean = withContext(Dispatchers.IO) {
        // Kill GMS unstable and vending processes to reload Play Integrity attestation
        val (s1, _) = RootExecutor.execute("pkill -9 -f com.google.android.gms.unstable 2>/dev/null || true")
        val (s2, _) = RootExecutor.execute("pkill -9 -f com.android.vending 2>/dev/null || true")
        val (s3, _) = RootExecutor.execute("am force-stop com.google.android.gms 2>/dev/null || true")
        s1 || s2 || s3
    }

    suspend fun applyProfile(cacheDir: File, profile: PifProfile, restartGmsNow: Boolean = true): Boolean = withContext(Dispatchers.IO) {
        val saved = saveProfile(cacheDir, profile)
        if (saved && restartGmsNow) {
            restartGms()
        }
        saved
    }

    /**
     * Queries available Pixel Canary devices supported by autopif.sh.
     */
    suspend fun getAutoPifDevices(): List<AutoPifDevice> = withContext(Dispatchers.IO) {
        val devices = mutableListOf<AutoPifDevice>()
        val (success, lines) = RootExecutor.execute("sh \"$autoPifScriptPath\" --list 2>/dev/null")
        if (success && lines.isNotEmpty()) {
            val jsonLine = lines.firstOrNull { it.contains("{\"model\":") }
            if (jsonLine != null) {
                try {
                    val jsonObj = JSONObject(jsonLine)
                    val modelArr = jsonObj.optJSONArray("model") ?: JSONArray()
                    val productArr = jsonObj.optJSONArray("product") ?: JSONArray()
                    val len = minOf(modelArr.length(), productArr.length())
                    for (i in 0 until len) {
                        devices.add(
                            AutoPifDevice(
                                model = modelArr.getString(i),
                                product = productArr.getString(i)
                            )
                        )
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
        devices
    }

    /**
     * Executes autopif.sh for the given Pixel Canary device (or random if null).
     * Enforces all 7 required spoofs upon completion and reloads GMS.
     */
    suspend fun runAutoPif(
        cacheDir: File,
        device: AutoPifDevice? = null,
        restartGmsNow: Boolean = true
    ): Pair<Boolean, PifProfile?> = withContext(Dispatchers.IO) {
        val cmd = if (device != null) {
            "MODEL=\"${device.model}\" PRODUCT=\"${device.product}\" sh \"$autoPifScriptPath\""
        } else {
            "sh \"$autoPifScriptPath\""
        }

        val (success, _) = RootExecutor.execute(cmd)
        if (!success) {
            return@withContext Pair(false, null)
        }

        // Post-process /data/adb/pif.prop to guarantee all 7 spoofs are enabled
        val lines = RootExecutor.readFile(pifPropPath) ?: return@withContext Pair(false, null)
        val profile = PifProfile.fromPropLines(lines, pifPropPath).withAllSpoofsEnabled()

        val saved = saveProfile(cacheDir, profile)
        if (saved && restartGmsNow) {
            restartGms()
        }
        Pair(saved, profile)
    }

    suspend fun getAvailableProfiles(fetchRemote: Boolean = false): List<PifProfile> = withContext(Dispatchers.IO) {
        val results = mutableListOf<PifProfile>()
        results.addAll(getBuiltInPresets())

        if (fetchRemote) {
            try {
                val urls = listOf(
                    "https://raw.githubusercontent.com/chiteroman/PlayIntegrityFix/main/module/pif.json",
                    "https://raw.githubusercontent.com/KOWX712/playintegrityfix/inject_s/pif.json"
                )

                for (url in urls) {
                    try {
                        val request = Request.Builder().url(url).build()
                        val response = httpClient.newCall(request).execute()
                        if (response.isSuccessful) {
                            val body = response.body?.string()
                            if (!body.isNullOrBlank()) {
                                parseJsonToProfile(body)?.let { remoteProfile ->
                                    if (results.none { it.fingerprint == remoteProfile.fingerprint }) {
                                        results.add(0, remoteProfile)
                                    }
                                }
                                break
                            }
                        }
                    } catch (ignored: Exception) {}
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        results
    }

    private fun parseJsonToProfile(jsonStr: String): PifProfile? {
        return try {
            val json = JSONObject(jsonStr)
            val fp = json.optString("FINGERPRINT", "")
            if (fp.isBlank()) return null

            PifProfile(
                name = "${json.optString("MANUFACTURER", "Google")} ${json.optString("MODEL", "Pixel")}",
                fingerprint = fp,
                manufacturer = json.optString("MANUFACTURER", "Google"),
                model = json.optString("MODEL", "Pixel"),
                securityPatch = json.optString("SECURITY_PATCH", ""),
                spoofBuild = true,
                spoofProps = true,
                spoofProvider = true,
                spoofSignature = true,
                spoofVendingBuild = true,
                spoofVendingSdk = true,
                debug = true
            )
        } catch (e: Exception) {
            null
        }
    }

    fun getBuiltInPresets(): List<PifProfile> {
        return listOf(
            PifProfile(
                name = "Google Pixel 9 (Beta)",
                fingerprint = "google/tokay_beta/tokay:CANARY/ZP11.260821.010/16290768:user/release-keys",
                manufacturer = "Google",
                model = "Pixel 9",
                securityPatch = "2026-09-05",
                spoofBuild = true,
                spoofProps = true,
                spoofProvider = true,
                spoofSignature = true,
                spoofVendingBuild = true,
                spoofVendingSdk = true,
                debug = true
            ),
            PifProfile(
                name = "Google Pixel 8a",
                fingerprint = "google/akita_beta/akita:CANARY/ZP11.260821.010/16290768:user/release-keys",
                manufacturer = "Google",
                model = "Pixel 8a",
                securityPatch = "2026-09-05",
                spoofBuild = true,
                spoofProps = true,
                spoofProvider = true,
                spoofSignature = true,
                spoofVendingBuild = true,
                spoofVendingSdk = true,
                debug = true
            ),
            PifProfile(
                name = "Google Pixel 8 Pro",
                fingerprint = "google/husky_beta/husky:CANARY/ZP11.260821.010/16290768:user/release-keys",
                manufacturer = "Google",
                model = "Pixel 8 Pro",
                securityPatch = "2026-09-05",
                spoofBuild = true,
                spoofProps = true,
                spoofProvider = true,
                spoofSignature = true,
                spoofVendingBuild = true,
                spoofVendingSdk = true,
                debug = true
            ),
            PifProfile(
                name = "Google Pixel 7 Pro",
                fingerprint = "google/cheetah_beta/cheetah:CANARY/ZP11.260821.010/16290768:user/release-keys",
                manufacturer = "Google",
                model = "Pixel 7 Pro",
                securityPatch = "2026-09-05",
                spoofBuild = true,
                spoofProps = true,
                spoofProvider = true,
                spoofSignature = true,
                spoofVendingBuild = true,
                spoofVendingSdk = true,
                debug = true
            ),
            PifProfile(
                name = "Google Pixel 6a",
                fingerprint = "google/bluejay_beta/bluejay:CANARY/ZP11.260821.010/16290768:user/release-keys",
                manufacturer = "Google",
                model = "Pixel 6a",
                securityPatch = "2026-09-05",
                spoofBuild = true,
                spoofProps = true,
                spoofProvider = true,
                spoofSignature = true,
                spoofVendingBuild = true,
                spoofVendingSdk = true,
                debug = true
            )
        )
    }
}
