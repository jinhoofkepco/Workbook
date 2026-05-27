package com.mathworkbook.app.core.skin

data class WorkbookSkin(
    val skinId: String,
    val displayName: String,
    val version: Int,
    val assets: Map<String, String>
) {
    fun assetPath(key: String): String? = assets[key]
}

data class SkinManagerState(
    val installedSkins: List<WorkbookSkin> = emptyList(),
    val activeSkin: WorkbookSkin? = null,
    val message: String? = null
)

class NotSkinZipException(message: String) : IllegalArgumentException(message)
