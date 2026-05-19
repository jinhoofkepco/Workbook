package com.mathworkbook.app.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

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

    @Query("SELECT * FROM ChapterEntity WHERE workbookId = :workbookId ORDER BY orderIndex")
    fun observeChapters(workbookId: String): Flow<List<ChapterEntity>>

    @Query("SELECT * FROM ChapterEntity WHERE workbookId = :workbookId ORDER BY orderIndex")
    suspend fun getChaptersOnce(workbookId: String): List<ChapterEntity>

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

    @Query("SELECT * FROM PracticeAttemptEntity WHERE studentId = :studentId AND problemId = :problemId ORDER BY startedAt DESC")
    suspend fun getPracticeAttemptsForProblem(studentId: String, problemId: String): List<PracticeAttemptEntity>

    @Query("SELECT * FROM AttemptInputLogEntity WHERE attemptId = :attemptId ORDER BY tryNumber, submittedAt")
    suspend fun getAttemptInputLogs(attemptId: String): List<AttemptInputLogEntity>

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
