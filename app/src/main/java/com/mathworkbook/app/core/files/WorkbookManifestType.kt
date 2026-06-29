package com.mathworkbook.app.core.files

import org.json.JSONObject

enum class WorkbookManifestType {
    LegacyProblemSet,
    ScanPageCoordinates
}

fun detectWorkbookManifestType(root: JSONObject): WorkbookManifestType {
    val rootType = root.optString("type")
    val workbookType = root.optJSONObject("workbook")?.optString("type").orEmpty()
    val schema = root.optString("schema")
    val normalized = listOf(rootType, workbookType, schema)
        .firstOrNull { it.isNotBlank() }
        ?.trim()
        ?.replace("-", "_")
        ?.lowercase()
        .orEmpty()

    return when (normalized) {
        "scan", "scanned_page", "scan_page_coordinates", "scanned_page_coordinates" ->
            WorkbookManifestType.ScanPageCoordinates
        else -> WorkbookManifestType.LegacyProblemSet
    }
}
