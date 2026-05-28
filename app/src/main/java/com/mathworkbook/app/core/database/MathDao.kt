package com.mathworkbook.app.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.mathworkbook.app.core.domain.FinalStatus
import kotlinx.coroutines.flow.Flow

data class CompletedPracticeAttemptSummary(
    val attemptId: String,
    val attemptNumber: Int,
    val problemId: String,
    val problemOrder: Int,
    val problemQuestionText: String,
    val workbookTitle: String,
    val chapterTitle: String,
    val finalStatus: FinalStatus,
    val isCorrect: Boolean?,
    val eventAt: Long,
    val solutionImagePath: String?
)

@Dao
interface MathDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertWorkbook(entity: WorkbookEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertChapter(entity: ChapterEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProblem(entity: ProblemEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAnswerField(entity: AnswerFieldEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAnswerRule(entity: AnswerRuleEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertChoice(entity: ChoiceEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTemplate(entity: ProblemTemplateEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertGeneratedProblem(entity: GeneratedProblemEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertStudent(entity: StudentEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPracticeAttempt(entity: PracticeAttemptEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAttemptInputLog(entity: AttemptInputLogEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertExam(entity: ExamEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertExamSession(entity: ExamSessionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertExamAnswer(entity: ExamAnswerEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertNavigationLog(entity: ExamNavigationLogEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertReview(entity: ReviewEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAppSettings(entity: AppSettingsEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertWorkbookSettings(entity: WorkbookSettingsEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProblemSettings(entity: ProblemSettingsEntity)

    @Update
    suspend fun updatePracticeAttempt(entity: PracticeAttemptEntity)

    @Update
    suspend fun updateExamSession(entity: ExamSessionEntity)

    @Query("SELECT * FROM WorkbookEntity ORDER BY createdAt DESC")
    fun observeWorkbooks(): Flow<List<WorkbookEntity>>

    @Query("SELECT * FROM WorkbookEntity ORDER BY createdAt DESC")
    suspend fun getWorkbooksOnce(): List<WorkbookEntity>

    @Query("SELECT * FROM WorkbookEntity WHERE workbookId = :workbookId LIMIT 1")
    suspend fun getWorkbook(workbookId: String): WorkbookEntity?

    @Query("DELETE FROM AttemptInputLogEntity WHERE attemptId IN (SELECT attemptId FROM PracticeAttemptEntity WHERE workbookId = :workbookId)")
    suspend fun deleteAttemptLogsForWorkbook(workbookId: String)

    @Query("DELETE FROM AttemptInputLogEntity WHERE attemptId = :attemptId")
    suspend fun deleteAttemptInputLogs(attemptId: String)

    @Query("DELETE FROM ReviewEntity WHERE attemptId = :attemptId")
    suspend fun deleteReviewsForAttempt(attemptId: String)

    @Query("DELETE FROM PracticeAttemptEntity WHERE attemptId = :attemptId")
    suspend fun deletePracticeAttempt(attemptId: String)

    @Query("DELETE FROM ReviewEntity WHERE problemId IN (SELECT problemId FROM ProblemEntity WHERE workbookId = :workbookId) OR attemptId IN (SELECT attemptId FROM PracticeAttemptEntity WHERE workbookId = :workbookId)")
    suspend fun deleteReviewsForWorkbook(workbookId: String)

    @Query("DELETE FROM PracticeAttemptEntity WHERE workbookId = :workbookId")
    suspend fun deletePracticeAttemptsForWorkbook(workbookId: String)

    @Query("DELETE FROM ExamNavigationLogEntity WHERE problemId IN (SELECT problemId FROM ProblemEntity WHERE workbookId = :workbookId) OR examSessionId IN (SELECT examSessionId FROM ExamSessionEntity WHERE examId IN (SELECT examId FROM ExamEntity WHERE workbookId = :workbookId))")
    suspend fun deleteExamNavigationLogsForWorkbook(workbookId: String)

    @Query("DELETE FROM ExamAnswerEntity WHERE problemId IN (SELECT problemId FROM ProblemEntity WHERE workbookId = :workbookId) OR examSessionId IN (SELECT examSessionId FROM ExamSessionEntity WHERE examId IN (SELECT examId FROM ExamEntity WHERE workbookId = :workbookId))")
    suspend fun deleteExamAnswersForWorkbook(workbookId: String)

    @Query("DELETE FROM ExamSessionEntity WHERE examId IN (SELECT examId FROM ExamEntity WHERE workbookId = :workbookId)")
    suspend fun deleteExamSessionsForWorkbook(workbookId: String)

    @Query("DELETE FROM ExamEntity WHERE workbookId = :workbookId")
    suspend fun deleteExamsForWorkbook(workbookId: String)

    @Query("DELETE FROM GeneratedProblemEntity WHERE sourceProblemId IN (SELECT problemId FROM ProblemEntity WHERE workbookId = :workbookId)")
    suspend fun deleteGeneratedProblemsForWorkbook(workbookId: String)

    @Query("DELETE FROM ProblemTemplateEntity WHERE problemId IN (SELECT problemId FROM ProblemEntity WHERE workbookId = :workbookId)")
    suspend fun deleteProblemTemplatesForWorkbook(workbookId: String)

    @Query("DELETE FROM ChoiceEntity WHERE problemId IN (SELECT problemId FROM ProblemEntity WHERE workbookId = :workbookId)")
    suspend fun deleteChoicesForWorkbook(workbookId: String)

    @Query("DELETE FROM AnswerRuleEntity WHERE problemId IN (SELECT problemId FROM ProblemEntity WHERE workbookId = :workbookId)")
    suspend fun deleteAnswerRulesForWorkbook(workbookId: String)

    @Query("DELETE FROM AnswerFieldEntity WHERE problemId IN (SELECT problemId FROM ProblemEntity WHERE workbookId = :workbookId)")
    suspend fun deleteAnswerFieldsForWorkbook(workbookId: String)

    @Query("DELETE FROM ProblemSettingsEntity WHERE problemId IN (SELECT problemId FROM ProblemEntity WHERE workbookId = :workbookId)")
    suspend fun deleteProblemSettingsForWorkbook(workbookId: String)

    @Query("DELETE FROM WorkbookSettingsEntity WHERE workbookId = :workbookId")
    suspend fun deleteWorkbookSettings(workbookId: String)

    @Query("DELETE FROM ProblemEntity WHERE workbookId = :workbookId")
    suspend fun deleteProblemsForWorkbook(workbookId: String)

    @Query("DELETE FROM ChapterEntity WHERE workbookId = :workbookId")
    suspend fun deleteChaptersForWorkbook(workbookId: String)

    @Query("DELETE FROM WorkbookEntity WHERE workbookId = :workbookId")
    suspend fun deleteWorkbook(workbookId: String)

    @Query("SELECT * FROM ChapterEntity WHERE workbookId = :workbookId ORDER BY orderIndex")
    fun observeChapters(workbookId: String): Flow<List<ChapterEntity>>

    @Query("SELECT * FROM ChapterEntity WHERE workbookId = :workbookId ORDER BY orderIndex")
    suspend fun getChaptersOnce(workbookId: String): List<ChapterEntity>

    @Query("SELECT * FROM ChapterEntity WHERE chapterId = :chapterId LIMIT 1")
    suspend fun getChapter(chapterId: String): ChapterEntity?

    @Query("SELECT * FROM ProblemEntity ORDER BY workbookId, chapterId, orderIndex")
    fun observeAllProblems(): Flow<List<ProblemEntity>>

    @Query("SELECT * FROM ProblemEntity WHERE chapterId = :chapterId ORDER BY orderIndex")
    fun observeProblemsInChapter(chapterId: String): Flow<List<ProblemEntity>>

    @Query("SELECT * FROM ProblemEntity WHERE problemId = :problemId LIMIT 1")
    suspend fun getProblem(problemId: String): ProblemEntity?

    @Query("SELECT * FROM ProblemEntity WHERE chapterId = :chapterId ORDER BY orderIndex")
    suspend fun getProblemsInChapter(chapterId: String): List<ProblemEntity>

    @Query("SELECT * FROM ProblemEntity ORDER BY orderIndex")
    suspend fun getAllProblemsOnce(): List<ProblemEntity>

    @Query("SELECT * FROM AnswerFieldEntity WHERE problemId = :problemId ORDER BY orderIndex")
    suspend fun getAnswerFields(problemId: String): List<AnswerFieldEntity>

    @Query("SELECT * FROM AnswerRuleEntity WHERE problemId = :problemId")
    suspend fun getAnswerRules(problemId: String): List<AnswerRuleEntity>

    @Query("SELECT * FROM ChoiceEntity WHERE problemId = :problemId ORDER BY orderIndex")
    suspend fun getChoices(problemId: String): List<ChoiceEntity>

    @Query("SELECT * FROM ProblemTemplateEntity WHERE problemId = :problemId AND enabled = 1 LIMIT 1")
    suspend fun getEnabledTemplate(problemId: String): ProblemTemplateEntity?

    @Query("SELECT * FROM PracticeAttemptEntity WHERE studentId = :studentId AND problemId = :problemId AND finalStatus = 'IN_PROGRESS' ORDER BY startedAt DESC LIMIT 1")
    suspend fun getOpenPracticeAttempt(studentId: String, problemId: String): PracticeAttemptEntity?

    @Query("SELECT COUNT(*) FROM PracticeAttemptEntity WHERE studentId = :studentId AND problemId = :problemId")
    suspend fun countAttempts(studentId: String, problemId: String): Int

    @Query("SELECT * FROM PracticeAttemptEntity ORDER BY startedAt DESC")
    fun observePracticeAttempts(): Flow<List<PracticeAttemptEntity>>

    @Query("SELECT * FROM PracticeAttemptEntity WHERE attemptId = :attemptId LIMIT 1")
    suspend fun getPracticeAttempt(attemptId: String): PracticeAttemptEntity?

    @Query("SELECT * FROM PracticeAttemptEntity WHERE finalStatus != 'IN_PROGRESS' ORDER BY submittedAt DESC, startedAt DESC LIMIT :limit")
    suspend fun getCompletedPracticeAttempts(limit: Int): List<PracticeAttemptEntity>

    @Query(
        """
        SELECT
            attempt.attemptId AS attemptId,
            attempt.attemptNumber AS attemptNumber,
            attempt.problemId AS problemId,
            COALESCE(problem.orderIndex, 0) AS problemOrder,
            COALESCE(problem.questionText, '') AS problemQuestionText,
            COALESCE(workbook.title, '') AS workbookTitle,
            COALESCE(chapter.title, '') AS chapterTitle,
            attempt.finalStatus AS finalStatus,
            attempt.isCorrect AS isCorrect,
            COALESCE(attempt.submittedAt, attempt.startedAt) AS eventAt,
            attempt.solutionImagePath AS solutionImagePath
        FROM PracticeAttemptEntity attempt
        LEFT JOIN ProblemEntity problem ON problem.problemId = attempt.problemId
        LEFT JOIN WorkbookEntity workbook ON workbook.workbookId = attempt.workbookId
        LEFT JOIN ChapterEntity chapter ON chapter.chapterId = attempt.chapterId
        WHERE attempt.finalStatus != 'IN_PROGRESS'
        ORDER BY attempt.submittedAt DESC, attempt.startedAt DESC
        LIMIT :limit
        """
    )
    suspend fun getCompletedPracticeAttemptSummaries(limit: Int): List<CompletedPracticeAttemptSummary>

    @Query("SELECT * FROM PracticeAttemptEntity WHERE studentId = :studentId AND problemId = :problemId ORDER BY startedAt DESC")
    suspend fun getPracticeAttemptsForProblem(studentId: String, problemId: String): List<PracticeAttemptEntity>

    @Query("SELECT * FROM AttemptInputLogEntity WHERE attemptId = :attemptId ORDER BY tryNumber, submittedAt")
    suspend fun getAttemptInputLogs(attemptId: String): List<AttemptInputLogEntity>

    @Query("SELECT * FROM AttemptInputLogEntity WHERE attemptId IN (:attemptIds) ORDER BY attemptId, tryNumber, submittedAt")
    suspend fun getAttemptInputLogsForAttempts(attemptIds: List<String>): List<AttemptInputLogEntity>

    @Query("SELECT * FROM AnswerFieldEntity WHERE problemId IN (:problemIds) ORDER BY problemId, orderIndex")
    suspend fun getAnswerFieldsForProblems(problemIds: List<String>): List<AnswerFieldEntity>

    @Query("SELECT * FROM AppSettingsEntity WHERE settingId = 'default' LIMIT 1")
    suspend fun getAppSettings(): AppSettingsEntity?

    @Query("SELECT * FROM WorkbookSettingsEntity WHERE workbookId = :workbookId LIMIT 1")
    suspend fun getWorkbookSettings(workbookId: String): WorkbookSettingsEntity?

    @Query("SELECT * FROM ProblemSettingsEntity WHERE problemId = :problemId LIMIT 1")
    suspend fun getProblemSettings(problemId: String): ProblemSettingsEntity?

    @Query("SELECT * FROM ExamEntity ORDER BY createdAt DESC LIMIT 1")
    suspend fun getLatestExam(): ExamEntity?

    @Query("SELECT * FROM ExamSessionEntity WHERE examSessionId = :sessionId LIMIT 1")
    suspend fun getExamSession(sessionId: String): ExamSessionEntity?

    @Query("SELECT * FROM ExamAnswerEntity WHERE examSessionId = :sessionId")
    suspend fun getExamAnswers(sessionId: String): List<ExamAnswerEntity>

    @Query("SELECT * FROM ExamAnswerEntity WHERE examSessionId = :sessionId AND problemId = :problemId")
    suspend fun getExamAnswersForProblem(sessionId: String, problemId: String): List<ExamAnswerEntity>

    @Query("SELECT * FROM ExamSessionEntity ORDER BY startedAt DESC")
    fun observeExamSessions(): Flow<List<ExamSessionEntity>>

}
