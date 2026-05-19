package com.mathworkbook.app.core.usecase

import com.mathworkbook.app.core.database.MathDao

class AttemptLimitResolver(private val dao: MathDao) {
    suspend fun resolve(problemId: String, workbookId: String): Int {
        val problemLimit = dao.getProblemSettings(problemId)?.maxInputTryCount
        val workbookLimit = dao.getWorkbookSettings(workbookId)?.maxInputTryCount
        val appLimit = dao.getAppSettings()?.defaultMaxInputTryCount ?: 3
        return (problemLimit ?: workbookLimit ?: appLimit).coerceIn(1, 10)
    }
}
