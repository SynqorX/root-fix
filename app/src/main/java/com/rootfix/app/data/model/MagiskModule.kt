package com.rootfix.app.data.model

data class MagiskModule(
    val id: String,
    val name: String,
    val version: String,
    val versionCode: Long,
    val author: String,
    val description: String,
    val updateJson: String? = null,
    val isEnabled: Boolean = true,
    val isRemovePending: Boolean = false
)
