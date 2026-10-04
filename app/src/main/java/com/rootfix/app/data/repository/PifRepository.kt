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

    /**
     * Atomically saves the PIF profile to /data/adb/pif.prop and synchronizes
     * with /data/adb/modules/playintegrityfix/pif.prop.
     * Enforces all 7 required spoofs:
     * spoofBuild, spoofProps, spoofProvider, spoofSignature, spoofVendingBuild, spoofVendingSdk, and DEBUG.
     */
    suspend fun saveProfile(cacheDir: File, profile: PifProfile): Boolean = withContext(Dispatchers.IO) {
        val enforcedProfile = profile.withAllSpoofsEnabled()
        val propContent = enforcedProfile.toPropString()
        val saved = RootExecutor.writeFileAtomically(
            cacheDir = cacheDir,
            targetPath = pifPropPath,
            content = propContent,
            permissions = "644"
        )
        if (saved) {
            // Keep module directory in sync so PIF zygisk injection reads identical properties
            RootExecutor.execute("cp -f /data/adb/pif.prop /data/adb/modules/playintegrityfix/pif.prop 2>/dev/null && chmod 644 /data/adb/modules/playintegrityfix/pif.prop 2>/dev/null || true")
        }
        saved
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
                brand = json.optString("BRAND", ""),
                product = json.optString("PRODUCT", ""),
                device = json.optString("DEVICE", ""),
                release = json.optString("RELEASE", ""),
                id = json.optString("ID", json.optString("BUILD_ID", "")),
                incremental = json.optString("INCREMENTAL", ""),
                type = json.optString("TYPE", "user"),
                tags = json.optString("TAGS", "release-keys"),
                securityPatch = json.optString("SECURITY_PATCH", ""),
                deviceInitialSdkInt = json.optString("DEVICE_INITIAL_SDK_INT", json.optString("FIRST_API_LEVEL", "")),
                spoofBuild = true,
                spoofProps = true,
                spoofProvider = true,
                spoofSignature = true,
                spoofVendingBuild = true,
                spoofVendingSdk = true,
                debug = true
            ).withAllSpoofsEnabled()
        } catch (e: Exception) {
            null
        }
    }

    fun getBuiltInPresets(): List<PifProfile> {
        return listOf(
            PifProfile(
                name = "Google Pixel 7 Pro (Certified)",
                fingerprint = "google/cheetah/cheetah:14/UP1A.231105.003/11010452:user/release-keys",
                manufacturer = "Google",
                model = "Pixel 7 Pro",
                brand = "google",
                product = "cheetah",
                device = "cheetah",
                release = "14",
                id = "UP1A.231105.003",
                incremental = "11010452",
                type = "user",
                tags = "release-keys",
                securityPatch = "2023-11-05",
                deviceInitialSdkInt = "33",
                spoofBuild = true,
                spoofProps = true,
                spoofProvider = true,
                spoofSignature = true,
                spoofVendingBuild = true,
                spoofVendingSdk = true,
                debug = true
            ).withAllSpoofsEnabled(),
            PifProfile(
                name = "Google Pixel 8a (Certified)",
                fingerprint = "google/akita/akita:14/UD2A.240505.001.B1/11812836:user/release-keys",
                manufacturer = "Google",
                model = "Pixel 8a",
                brand = "google",
                product = "akita",
                device = "akita",
                release = "14",
                id = "UD2A.240505.001.B1",
                incremental = "11812836",
                type = "user",
                tags = "release-keys",
                securityPatch = "2024-05-05",
                deviceInitialSdkInt = "34",
                spoofBuild = true,
                spoofProps = true,
                spoofProvider = true,
                spoofSignature = true,
                spoofVendingBuild = true,
                spoofVendingSdk = true,
                debug = true
            ).withAllSpoofsEnabled(),
            PifProfile(
                name = "Google Pixel 6a (Certified)",
                fingerprint = "google/bluejay/bluejay:14/UP1A.231005.007/10754064:user/release-keys",
                manufacturer = "Google",
                model = "Pixel 6a",
                brand = "google",
                product = "bluejay",
                device = "bluejay",
                release = "14",
                id = "UP1A.231005.007",
                incremental = "10754064",
                type = "user",
                tags = "release-keys",
                securityPatch = "2023-10-05",
                deviceInitialSdkInt = "32",
                spoofBuild = true,
                spoofProps = true,
                spoofProvider = true,
                spoofSignature = true,
                spoofVendingBuild = true,
                spoofVendingSdk = true,
                debug = true
            ).withAllSpoofsEnabled(),
            PifProfile(
                name = "Google Pixel 5 (Certified)",
                fingerprint = "google/redfin/redfin:13/TQ3A.230901.001/10750766:user/release-keys",
                manufacturer = "Google",
                model = "Pixel 5",
                brand = "google",
                product = "redfin",
                device = "redfin",
                release = "13",
                id = "TQ3A.230901.001",
                incremental = "10750766",
                type = "user",
                tags = "release-keys",
                securityPatch = "2023-09-01",
                deviceInitialSdkInt = "30",
                spoofBuild = true,
                spoofProps = true,
                spoofProvider = true,
                spoofSignature = true,
                spoofVendingBuild = true,
                spoofVendingSdk = true,
                debug = true
            ).withAllSpoofsEnabled(),
            PifProfile(
                name = "Google Pixel (Nougat Legacy)",
                fingerprint = "google/sailfish/sailfish:8.1.0/OPM1.171019.011/4448085:user/release-keys",
                manufacturer = "Google",
                model = "Pixel",
                brand = "google",
                product = "sailfish",
                device = "sailfish",
                release = "8.1.0",
                id = "OPM1.171019.011",
                incremental = "4448085",
                type = "user",
                tags = "release-keys",
                securityPatch = "2017-12-05",
                deviceInitialSdkInt = "25",
                spoofBuild = true,
                spoofProps = true,
                spoofProvider = true,
                spoofSignature = true,
                spoofVendingBuild = true,
                spoofVendingSdk = true,
                debug = true
            ).withAllSpoofsEnabled()
        )
    }
}
