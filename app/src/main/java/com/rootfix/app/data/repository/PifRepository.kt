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

class PifRepository {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private val pifPropPath = "/data/adb/pif.prop"

    suspend fun getActiveProfile(): PifProfile? = withContext(Dispatchers.IO) {
        val lines = RootExecutor.readFile(pifPropPath)
        if (lines != null && lines.isNotEmpty()) {
            return@withContext PifProfile.fromPropLines(lines, pifPropPath)
        }
        null
    }

    suspend fun saveProfile(cacheDir: File, profile: PifProfile): Boolean = withContext(Dispatchers.IO) {
        val propContent = profile.toPropString()
        RootExecutor.writeFileAtomically(
            cacheDir = cacheDir,
            targetPath = pifPropPath,
            content = propContent,
            permissions = "644"
        )
    }

    suspend fun restartGms(): Boolean = withContext(Dispatchers.IO) {
        // Kill GMS unstable and unstable broker processes to force Play Integrity re-eval
        val (s1, _) = RootExecutor.execute("pkill -9 -f com.google.android.gms.unstable 2>/dev/null || true")
        val (s2, _) = RootExecutor.execute("am force-stop com.google.android.gms 2>/dev/null || true")
        s1 || s2
    }

    suspend fun applyProfile(cacheDir: File, profile: PifProfile, restartGmsNow: Boolean = true): Boolean = withContext(Dispatchers.IO) {
        val saved = saveProfile(cacheDir, profile)
        if (saved && restartGmsNow) {
            restartGms()
        }
        saved
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
                name = "${json.optString("MANUFACTURER", "Android")} ${json.optString("MODEL", "Device")}",
                fingerprint = fp,
                manufacturer = json.optString("MANUFACTURER", ""),
                model = json.optString("MODEL", ""),
                securityPatch = json.optString("SECURITY_PATCH", ""),
                spoofBuild = json.optBoolean("spoofBuild", true),
                spoofProps = json.optBoolean("spoofProps", false),
                spoofProvider = json.optBoolean("spoofProvider", false),
                spoofSignature = json.optBoolean("spoofSignature", false),
                spoofVendingBuild = json.optBoolean("spoofVendingBuild", true),
                spoofVendingSdk = json.optBoolean("spoofVendingSdk", false)
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
                spoofProps = false,
                spoofProvider = false,
                spoofSignature = false,
                spoofVendingBuild = true,
                spoofVendingSdk = false
            ),
            PifProfile(
                name = "Google Pixel 8a",
                fingerprint = "google/akita/akita:14/AP2A.240805.005/12025142:user/release-keys",
                manufacturer = "Google",
                model = "Pixel 8a",
                securityPatch = "2024-08-05",
                spoofBuild = true,
                spoofProps = false,
                spoofProvider = false,
                spoofSignature = false,
                spoofVendingBuild = true,
                spoofVendingSdk = false
            ),
            PifProfile(
                name = "Google Pixel 7 Pro",
                fingerprint = "google/cheetah/cheetah:14/UQ1A.240205.002/11264630:user/release-keys",
                manufacturer = "Google",
                model = "Pixel 7 Pro",
                securityPatch = "2024-02-05",
                spoofBuild = true,
                spoofProps = false,
                spoofProvider = false,
                spoofSignature = false,
                spoofVendingBuild = true,
                spoofVendingSdk = false
            ),
            PifProfile(
                name = "Xiaomi 13",
                fingerprint = "Xiaomi/fuxi_eea/fuxi:14/UKQ1.230804.001/V816.0.4.0.UMCEUXM:user/release-keys",
                manufacturer = "Xiaomi",
                model = "2211133G",
                securityPatch = "2024-03-01",
                spoofBuild = true,
                spoofProps = false,
                spoofProvider = false,
                spoofSignature = false,
                spoofVendingBuild = true,
                spoofVendingSdk = false
            )
        )
    }
}
