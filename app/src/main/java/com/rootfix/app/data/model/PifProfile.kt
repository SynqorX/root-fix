package com.rootfix.app.data.model

data class PifProfile(
    val name: String = "Active Profile",
    val fingerprint: String = "",
    val manufacturer: String = "",
    val model: String = "",
    val brand: String = "",
    val product: String = "",
    val device: String = "",
    val release: String = "",
    val id: String = "",
    val incremental: String = "",
    val type: String = "user",
    val tags: String = "release-keys",
    val securityPatch: String = "",
    val deviceInitialSdkInt: String = "",
    val spoofBuild: Boolean = true,
    val spoofProps: Boolean = true,
    val spoofProvider: Boolean = true,
    val spoofSignature: Boolean = true,
    val spoofVendingBuild: Boolean = true,
    val spoofVendingSdk: Boolean = true,
    val debug: Boolean = true,
    val additionalProps: Map<String, String> = emptyMap(),
    val sourcePath: String = "/data/adb/pif.prop"
) {
    /**
     * Resolves all missing build fields by decomposing the fingerprint:
     * Format: BRAND/PRODUCT/DEVICE:RELEASE/ID/INCREMENTAL:TYPE/TAGS
     */
    fun withDerivedFields(): PifProfile {
        var b = brand
        var p = product
        var d = device
        var r = release
        var i = id
        var inc = incremental
        var t = type.ifBlank { "user" }
        var tg = tags.ifBlank { "release-keys" }

        if (fingerprint.isNotBlank()) {
            val parts = fingerprint.split(":")
            if (parts.size >= 3) {
                val left = parts[0].split("/")
                if (left.size >= 3) {
                    if (b.isBlank()) b = left[0]
                    if (p.isBlank()) p = left[1]
                    if (d.isBlank()) d = left[2]
                }
                val mid = parts[1].split("/")
                if (mid.size >= 3) {
                    if (r.isBlank()) r = mid[0]
                    if (i.isBlank()) i = mid[1]
                    if (inc.isBlank()) inc = mid[2]
                }
                val right = parts[2].split("/")
                if (right.size >= 2) {
                    if (t.isBlank()) t = right[0]
                    if (tg.isBlank()) tg = right[1]
                }
            }
        }

        return copy(
            brand = b,
            product = p,
            device = d,
            release = r,
            id = i,
            incremental = inc,
            type = t,
            tags = tg
        )
    }

    fun toPropString(): String {
        val resolved = withDerivedFields()
        val sb = StringBuilder()

        // Core Build Fields
        if (resolved.fingerprint.isNotBlank()) sb.append("FINGERPRINT=").append(resolved.fingerprint.trim()).append("\n")
        if (resolved.manufacturer.isNotBlank()) sb.append("MANUFACTURER=").append(resolved.manufacturer.trim()).append("\n")
        if (resolved.model.isNotBlank()) sb.append("MODEL=").append(resolved.model.trim()).append("\n")
        if (resolved.brand.isNotBlank()) sb.append("BRAND=").append(resolved.brand.trim()).append("\n")
        if (resolved.product.isNotBlank()) sb.append("PRODUCT=").append(resolved.product.trim()).append("\n")
        if (resolved.device.isNotBlank()) sb.append("DEVICE=").append(resolved.device.trim()).append("\n")
        if (resolved.release.isNotBlank()) sb.append("RELEASE=").append(resolved.release.trim()).append("\n")
        if (resolved.id.isNotBlank()) sb.append("ID=").append(resolved.id.trim()).append("\n")
        if (resolved.incremental.isNotBlank()) sb.append("INCREMENTAL=").append(resolved.incremental.trim()).append("\n")
        if (resolved.type.isNotBlank()) sb.append("TYPE=").append(resolved.type.trim()).append("\n")
        if (resolved.tags.isNotBlank()) sb.append("TAGS=").append(resolved.tags.trim()).append("\n")
        if (resolved.securityPatch.isNotBlank()) sb.append("SECURITY_PATCH=").append(resolved.securityPatch.trim()).append("\n")
        if (resolved.deviceInitialSdkInt.isNotBlank()) sb.append("DEVICE_INITIAL_SDK_INT=").append(resolved.deviceInitialSdkInt.trim()).append("\n")

        // System Properties
        if (resolved.id.isNotBlank()) sb.append("*.build.id=").append(resolved.id.trim()).append("\n")
        if (resolved.securityPatch.isNotBlank()) sb.append("*.security_patch=").append(resolved.securityPatch.trim()).append("\n")
        if (resolved.deviceInitialSdkInt.isNotBlank()) sb.append("*api_level=").append(resolved.deviceInitialSdkInt.trim()).append("\n")

        // 7 Enforced Spoofs
        sb.append("spoofBuild=").append(resolved.spoofBuild).append("\n")
        sb.append("spoofProps=").append(resolved.spoofProps).append("\n")
        sb.append("spoofProvider=").append(resolved.spoofProvider).append("\n")
        sb.append("spoofSignature=").append(resolved.spoofSignature).append("\n")
        sb.append("spoofVendingBuild=").append(resolved.spoofVendingBuild).append("\n")
        sb.append("spoofVendingSdk=").append(resolved.spoofVendingSdk).append("\n")
        sb.append("DEBUG=").append(resolved.debug).append("\n")

        // Additional / custom properties
        for ((k, v) in resolved.additionalProps) {
            if (!k.startsWith("*") && !k.equals("spoofBuild", ignoreCase = true)
                && !k.equals("spoofProps", ignoreCase = true)
                && !k.equals("spoofProvider", ignoreCase = true)
                && !k.equals("spoofSignature", ignoreCase = true)
                && !k.equals("spoofVendingBuild", ignoreCase = true)
                && !k.equals("spoofVendingSdk", ignoreCase = true)
                && !k.equals("DEBUG", ignoreCase = true)
            ) {
                sb.append(k).append("=").append(v).append("\n")
            }
        }
        return sb.toString()
    }

    fun withAllSpoofsEnabled(): PifProfile {
        return copy(
            spoofBuild = true,
            spoofProps = true,
            spoofProvider = true,
            spoofSignature = true,
            spoofVendingBuild = true,
            spoofVendingSdk = true,
            debug = true
        ).withDerivedFields()
    }

    companion object {
        fun fromPropLines(lines: List<String>, path: String = "/data/adb/pif.prop"): PifProfile {
            var fp = ""
            var mfr = ""
            var mdl = ""
            var brd = ""
            var prod = ""
            var dev = ""
            var rel = ""
            var buildId = ""
            var inc = ""
            var typ = "user"
            var tgs = "release-keys"
            var secPatch = ""
            var initSdk = ""
            var sBuild = true
            var sProps = true
            var sProvider = true
            var sSig = true
            var sVendingBuild = true
            var sVendingSdk = true
            var sDebug = true
            val extras = mutableMapOf<String, String>()

            for (raw in lines) {
                val line = raw.trim()
                if (line.isEmpty() || line.startsWith("#")) continue
                val idx = line.indexOf('=')
                if (idx <= 0) continue
                val key = line.substring(0, idx).trim()
                val value = line.substring(idx + 1).trim()

                when (key.uppercase()) {
                    "FINGERPRINT" -> fp = value
                    "MANUFACTURER" -> mfr = value
                    "MODEL" -> mdl = value
                    "BRAND" -> brd = value
                    "PRODUCT" -> prod = value
                    "DEVICE" -> dev = value
                    "RELEASE" -> rel = value
                    "ID", "BUILD_ID" -> buildId = value
                    "INCREMENTAL" -> inc = value
                    "TYPE" -> typ = value
                    "TAGS" -> tgs = value
                    "SECURITY_PATCH" -> secPatch = value
                    "DEVICE_INITIAL_SDK_INT", "FIRST_API_LEVEL" -> initSdk = value
                    "SPOOFBUILD" -> sBuild = value.toBoolean()
                    "SPOOFPROPS" -> sProps = value.toBoolean()
                    "SPOOFPROVIDER" -> sProvider = value.toBoolean()
                    "SPOOFSIGNATURE" -> sSig = value.toBoolean()
                    "SPOOFVENDINGBUILD" -> sVendingBuild = value.toBoolean()
                    "SPOOFVENDINGSDK" -> sVendingSdk = value.toBoolean()
                    "DEBUG" -> sDebug = value.toBoolean()
                    else -> extras[key] = value
                }
            }

            return PifProfile(
                name = if (mdl.isNotBlank()) mdl else "Custom Profile",
                fingerprint = fp,
                manufacturer = mfr,
                model = mdl,
                brand = brd,
                product = prod,
                device = dev,
                release = rel,
                id = buildId,
                incremental = inc,
                type = typ,
                tags = tgs,
                securityPatch = secPatch,
                deviceInitialSdkInt = initSdk,
                spoofBuild = sBuild,
                spoofProps = sProps,
                spoofProvider = sProvider,
                spoofSignature = sSig,
                spoofVendingBuild = sVendingBuild,
                spoofVendingSdk = sVendingSdk,
                debug = sDebug,
                additionalProps = extras,
                sourcePath = path
            ).withDerivedFields()
        }
    }
}
