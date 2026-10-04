package com.rootfix.app.data.model

data class ModuleScriptInfo(
    val name: String,
    val relativePath: String,
    val fullPath: String,
    val category: String = "Utility"
)

data class MagiskModule(
    val id: String,
    val name: String,
    val version: String,
    val versionCode: Long,
    val author: String,
    val description: String,
    val updateJson: String? = null,
    val isEnabled: Boolean = true,
    val isRemovePending: Boolean = false,
    val hasAction: Boolean = false,
    val hasWebUi: Boolean = false,
    val availableScripts: List<ModuleScriptInfo> = emptyList()
)

