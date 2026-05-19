package com.mathworkbook.app.core

import android.content.Context
import com.mathworkbook.app.core.database.AppDatabase
import com.mathworkbook.app.core.files.FileStorage
import com.mathworkbook.app.core.files.WorkbookImportService
import com.mathworkbook.app.core.generation.SafeFormulaEvaluator
import com.mathworkbook.app.core.generation.SimilarProblemGenerator
import com.mathworkbook.app.core.generation.SimilarProblemQueueService
import com.mathworkbook.app.core.grading.DefaultGradingEngine
import com.mathworkbook.app.core.usecase.AttemptLimitResolver
import com.mathworkbook.app.core.usecase.SubmitExamUseCase
import com.mathworkbook.app.core.usecase.SubmitPracticeAnswerUseCase

class AppContainer(context: Context) {
    val database = AppDatabase.get(context)
    val dao = database.mathDao()
    val fileStorage = FileStorage(context)
    val workbookImportService = WorkbookImportService(context, dao, fileStorage)

    private val gradingEngine = DefaultGradingEngine()
    private val generator = SimilarProblemGenerator(SafeFormulaEvaluator())
    private val similarProblemQueueService = SimilarProblemQueueService(dao, generator)
    private val attemptLimitResolver = AttemptLimitResolver(dao)

    val submitPracticeAnswerUseCase = SubmitPracticeAnswerUseCase(
        dao = dao,
        gradingEngine = gradingEngine,
        attemptLimitResolver = attemptLimitResolver,
        similarProblemQueueService = similarProblemQueueService
    )

    val submitExamUseCase = SubmitExamUseCase(
        dao = dao,
        gradingEngine = gradingEngine,
        similarProblemQueueService = similarProblemQueueService
    )

    val seedData = SeedData(dao)
}
