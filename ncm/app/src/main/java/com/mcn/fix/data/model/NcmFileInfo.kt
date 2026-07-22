package com.mcn.fix.data.model

data class NcmFileInfo(
    val path: String,
    val name: String,
    val size: Long,
    val lastModified: Long = 0,
    var checked: Boolean = true,
)
