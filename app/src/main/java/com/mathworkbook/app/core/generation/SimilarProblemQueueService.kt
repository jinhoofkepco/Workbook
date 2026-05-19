package com.mathworkbook.app.core.generation

import com.mathworkbook.app.core.database.MathDao
import com.mathworkbook.app.core.database.ProblemEntity

class SimilarProblemQueueService(
    private val dao: MathDao,
    private val generator: SimilarProblemGenerator
) {
    suspend fun enqueueIfAllowed(
        problem: ProblemEntity,
        studentId: String,
        reason: String
    ): Boolean {
        if (!problem.hasGenerationTemplate) return false
        val settings = dao.getAppSettings()
        val enabled = when (reason) {
            "MAX_ATTEMPTS" -> settings?.enableSimilarProblemAfterMaxAttempts ?: true
            "WRONG", "EXAM_WRONG" -> settings?.enableSimilarProblemAfterWrong ?: true
            else -> true
        }
        if (!enabled) return false
        val template = dao.getEnabledTemplate(problem.problemId) ?: return false
        val generated = generator.generate(template, studentId, reason) ?: return false
        dao.upsertGeneratedProblem(generated)
        return true
    }
}
