package com.rootfix.app.data.model

data class RootStatus(
    val isRootGranted: Boolean = false,
    val magiskVersion: String = "Unknown",
    val magiskVersionCode: Int = 0,
    val seLinuxMode: String = "Unknown",
    val deviceModel: String = android.os.Build.MODEL,
    val androidVersion: String = android.os.Build.VERSION.RELEASE,
    val apiLevel: Int = android.os.Build.VERSION.SDK_INT
)
