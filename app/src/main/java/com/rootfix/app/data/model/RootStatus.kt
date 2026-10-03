package com.rootfix.app.data.model

data class RootStatus(
    val isRootGranted: Boolean = false,
    val magiskVersion: String = "Unknown",
    val magiskVersionCode: Int = 0,
    val seLinuxMode: String = "Unknown",
    val deviceModel: String = "",
    val androidVersion: String = "",
    val apiLevel: Int = 0
)
