package com.rootfix.app.data.model

data class PifProfile(
    val name: String = "Active Profile",
    val fingerprint: String = "",
    val manufacturer: String = "",
    val model: String = "",
    val securityPatch: String = "",
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
    fun toPropString(): String {
        val sb = StringBuilder()
        if (fingerprint.isNotBlank()) sb.append("FINGERPRINT=").append(fingerprint.trim()).append("\n")
        if (manufacturer.isNotBlank()) sb.append("MANUFACTURER=").append(manufacturer.trim()).append("\n")
        if (model.isNotBlank()) sb.append("MODEL=").append(model.trim()).append("\n")
        if (securityPatch.isNotBlank()) sb.append("SECURITY_PATCH=").append(securityPatch.trim()).append("\n")
        sb.append("spoofBuild=").append(spoofBuild).append("\n")
        sb.append("spoofProps=").append(spoofProps).append("\n")
        sb.append("spoofProvider=").append(spoofProvider).append("\n")
        sb.append("spoofSignature=").append(spoofSignature).append("\n")
        sb.append("spoofVendingBuild=").append(spoofVendingBuild).append("\n")
        sb.append("spoofVendingSdk=").append(spoofVendingSdk).append("\n")
        sb.append("DEBUG=").append(debug).append("\n")
        for ((k, v) in additionalProps) {
            sb.append(k).append("=").append(v).append("\n")
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
        )
    }

    companion object {
        fun fromPropLines(lines: List<String>, path: String = "/data/adb/pif.prop"): PifProfile {
            var fp = ""
            var mfr = ""
            var mdl = ""
            var secPatch = ""
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
                    "SECURITY_PATCH" -> secPatch = value
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
                securityPatch = secPatch,
                spoofBuild = sBuild,
                spoofProps = sProps,
                spoofProvider = sProvider,
                spoofSignature = sSig,
                spoofVendingBuild = sVendingBuild,
                spoofVendingSdk = sVendingSdk,
                debug = sDebug,
                additionalProps = extras,
                sourcePath = path
            )
        }
    }
}
