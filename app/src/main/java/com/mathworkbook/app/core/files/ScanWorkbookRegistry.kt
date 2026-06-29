package com.mathworkbook.app.core.files

const val SCAN_MVP_ASSET_MANIFEST = "scan_mvp/workbook.json"
const val SCAN_MVP_WORKBOOK_ID = "olympiad-2-scan-mvp"

fun isScanWorkbookId(workbookId: String?): Boolean {
    return workbookId == SCAN_MVP_WORKBOOK_ID
}
