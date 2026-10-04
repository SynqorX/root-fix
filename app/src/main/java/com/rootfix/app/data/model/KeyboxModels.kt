package com.rootfix.app.data.model

data class KeyboxStatus(
    val isTrickyStoreInstalled: Boolean = false,
    val trickyStoreVersion: String? = null,
    val hasKeyboxFile: Boolean = false,
    val keyboxFilePath: String = "/data/adb/tricky_store/keybox.xml",
    val deviceId: String? = null,
    val keyAlgorithm: String? = null, // "EC" or "RSA"
    val hasPrivateKey: Boolean = false,
    val certificateCount: Int = 0,
    val targetPackages: List<String> = emptyList(),
    val securityPatchOverride: String? = null,
    val isValidSchema: Boolean = false,
    val rawXml: String = ""
)
