package com.mathworkbook.app.ui.master

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import android.net.Uri
import com.mathworkbook.app.core.AppContainer
import com.mathworkbook.app.core.database.AnswerFieldEntity
import com.mathworkbook.app.core.database.AnswerRuleEntity
import com.mathworkbook.app.core.database.AppSettingsEntity
import com.mathworkbook.app.core.database.AttemptInputLogEntity
import com.mathworkbook.app.core.database.ChapterEntity
import com.mathworkbook.app.core.database.ExamEntity
import com.mathworkbook.app.core.database.ExamSessionEntity
import com.mathworkbook.app.core.database.MathDao
import com.mathworkbook.app.core.database.PracticeAttemptEntity
import com.mathworkbook.app.core.database.ProblemEntity
import com.mathworkbook.app.core.database.WorkbookEntity
import com.mathworkbook.app.core.domain.AnswerFieldType
import com.mathworkbook.app.core.domain.AnswerType
import com.mathworkbook.app.core.domain.ProblemType
import com.mathworkbook.app.core.domain.RelativeRect
import com.mathworkbook.app.core.domain.UnitType
import com.mathworkbook.app.core.files.FileStorage
import com.mathworkbook.app.core.files.WorkbookImportService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class MasterUiState(
    val workbooks: List<WorkbookEntity> = emptyList(),
    val chapters: List<ChapterEntity> = emptyList(),
    val problems: List<ProblemEntity> = emptyList(),
    val attempts: List<PracticeAttemptEntity> = emptyList(),
    val inputLogsByAttempt: Map<String, List<AttemptInputLogEntity>> = emptyMap(),
    val answerRulesByProblem: Map<String, List<AnswerRuleEntity>> = emptyMap(),
    val correctAnswersByProblem: Map<String, List<String>> = emptyMap(),
    val examSessions: List<ExamSessionEntity> = emptyList(),
    val maxTryCount: Int = 3,
    val message: String? = null
)

class MasterViewModel(
    private val container: AppContainer,
    private val dao: MathDao,
    private val fileStorage: FileStorage,
    private val workbookImportService: WorkbookImportService
) : ViewModel() {
    private val _state = MutableStateFlow(MasterUiState())
    val state: StateFlow<MasterUiState> = _state

    init {
        viewModelScope.launch {
            container.seedData.ensure()
            val settings = dao.getAppSettings()
            _state.update { it.copy(maxTryCount = settings?.defaultMaxInputTryCount ?: 3) }
            refreshCatalog()
        }
        viewModelScope.launch {
            dao.observeAllProblems().collect { problems ->
                _state.update { it.copy(problems = problems) }
            }
        }
        viewModelScope.launch {
            dao.observePracticeAttempts().collect { attempts ->
                val logs = attempts.associate { attempt ->
                    attempt.attemptId to dao.getAttemptInputLogs(attempt.attemptId)
                }
                val rules = attempts
                    .map { it.problemId }
                    .distinct()
                    .associateWith { problemId ->
                        dao.getAnswerRules(problemId)
                    }
                val answers = rules.mapValues { (_, problemRules) ->
                    problemRules.map { it.correctAnswerRaw }
                }
                _state.update {
                    it.copy(
                        attempts = attempts,
                        inputLogsByAttempt = logs,
                        answerRulesByProblem = rules,
                        correctAnswersByProblem = answers
                    )
                }
            }
        }
        viewModelScope.launch {
            dao.observeExamSessions().collect { sessions ->
                _state.update { it.copy(examSessions = sessions) }
            }
        }
    }

    fun updateCorrectAnswer(rule: AnswerRuleEntity, rawAnswer: String) {
        viewModelScope.launch {
            val cleaned = rawAnswer.trim()
            dao.upsertAnswerRule(
                rule.copy(
                    correctAnswerRaw = cleaned,
                    normalizedAnswer = cleaned.replace(",", "")
                )
            )
            val problemRules = dao.getAnswerRules(rule.problemId)
            _state.update {
                it.copy(
                    answerRulesByProblem = it.answerRulesByProblem + (rule.problemId to problemRules),
                    correctAnswersByProblem = it.correctAnswersByProblem + (
                        rule.problemId to problemRules.map { problemRule -> problemRule.correctAnswerRaw }
                    ),
                    message = "정답이 수정되었습니다."
                )
            }
        }
    }

    fun updateDefaultMaxTryCount(value: Int) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val clamped = value.coerceIn(1, 10)
            val current = dao.getAppSettings()
            dao.upsertAppSettings(
                current?.copy(
                    defaultMaxInputTryCount = clamped,
                    updatedAt = now
                ) ?: AppSettingsEntity(
                    defaultMaxInputTryCount = clamped,
                    createdAt = now,
                    updatedAt = now
                )
            )
            _state.update { it.copy(maxTryCount = clamped, message = "입력 제한이 ${clamped}회로 저장되었습니다.") }
        }
    }

    fun importWorkbookZip(uri: Uri, onImported: () -> Unit = {}) {
        viewModelScope.launch {
            runCatching {
                workbookImportService.importZip(uri)
            }.onSuccess { workbookId ->
                refreshCatalog()
                onImported()
                _state.update { it.copy(message = "문제집을 가져왔습니다: $workbookId") }
            }.onFailure { error ->
                _state.update { it.copy(message = "가져오기 실패: ${error.message}") }
            }
        }
    }

    fun showMessage(message: String) {
        _state.update { it.copy(message = message) }
    }

    fun createExam(
        workbookId: String?,
        chapterId: String?,
        requestedProblemCount: Int,
        randomOrder: Boolean,
        wrongFirst: Boolean,
        onCreated: () -> Unit
    ) {
        viewModelScope.launch {
            val count = requestedProblemCount.coerceAtLeast(1)
            val allProblems = _state.value.problems
            val filtered = allProblems
                .filter { workbookId == null || it.workbookId == workbookId }
                .filter { chapterId == null || it.chapterId == chapterId }
            val ordered = orderExamProblems(filtered, randomOrder, wrongFirst)
            val selected = ordered.take(count)
            if (selected.isEmpty()) {
                _state.update { it.copy(message = "선택한 범위에 시험으로 만들 문제가 없습니다.") }
                return@launch
            }

            val now = System.currentTimeMillis()
            val selectedWorkbookId = workbookId ?: selected.first().workbookId
            val chapterIds = selected.map { it.chapterId }.distinct()
            val workbookTitle = _state.value.workbooks
                .firstOrNull { it.workbookId == selectedWorkbookId }
                ?.title
                ?: "문제집"
            val title = "자동 시험 - $workbookTitle ${selected.size}문제"

            dao.upsertExam(
                ExamEntity(
                    examId = "exam-$now",
                    title = title,
                    workbookId = selectedWorkbookId,
                    chapterIdsJson = stringListJson(chapterIds),
                    problemIdsJson = stringListJson(selected.map { it.problemId }),
                    timeLimitSeconds = null,
                    createdAt = now,
                    updatedAt = now
                )
            )
            _state.update {
                it.copy(
                    message = if (selected.size < count) {
                        "문제가 부족해서 가능한 ${selected.size}문제로 시험을 만들었습니다."
                    } else {
                        "${selected.size}문제 시험을 만들었습니다."
                    }
                )
            }
            onCreated()
        }
    }

    fun saveImageProblem(
        sourcePath: String,
        correctAnswer: String,
        crop: RelativeRect,
        mask: RelativeRect?
    ) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val workbookId = "workbook-demo"
            val chapterId = "chapter-demo-1"
            val problemId = "problem-image-$now"
            val imagePath = if (sourcePath.isNotBlank() && File(sourcePath).exists()) {
                fileStorage.cropProblemImage(workbookId, sourcePath, problemId, crop)
            } else {
                null
            }
            val maskJson = mask?.let { maskToJson(it) }
            dao.upsertProblem(
                ProblemEntity(
                    problemId = problemId,
                    workbookId = workbookId,
                    chapterId = chapterId,
                    problemType = ProblemType.IMAGE_BASED,
                    questionText = "사진 기반 문제",
                    questionLatex = null,
                    imagePath = imagePath,
                    sourcePageImagePath = sourcePath.ifBlank { null },
                    imageCropRectJson = rectToJson(crop),
                    maskOverlayJson = maskJson,
                    difficulty = 1,
                    orderIndex = _state.value.problems.size + 1,
                    hintText = null,
                    hasGenerationTemplate = false,
                    createdAt = now,
                    updatedAt = now
                )
            )
            val fieldId = "$problemId-field-answer"
            dao.upsertAnswerField(
                AnswerFieldEntity(
                    answerFieldId = fieldId,
                    problemId = problemId,
                    label = "답",
                    fieldType = AnswerFieldType.NUMBER,
                    orderIndex = 1,
                    positionJson = null,
                    required = true
                )
            )
            dao.upsertAnswerRule(
                AnswerRuleEntity(
                    answerRuleId = "$problemId-rule-answer",
                    problemId = problemId,
                    answerFieldId = fieldId,
                    answerType = AnswerType.INTEGER,
                    correctAnswerRaw = correctAnswer,
                    normalizedAnswer = correctAnswer.trim().replace(",", ""),
                    allowEquivalentFraction = false,
                    requireSimplifiedFraction = false,
                    decimalTolerance = null,
                    allowMultipleAnswers = false,
                    acceptedAnswersJson = null,
                    unitType = UnitType.NONE,
                    manualGradingRequired = false
                )
            )
            _state.update {
                it.copy(
                    message = if (imagePath == null) {
                        "이미지 경로 없이 문제 구조만 저장되었습니다."
                    } else {
                        "사진 문제를 저장했습니다."
                    }
                )
            }
        }
    }

    private fun rectToJson(rect: RelativeRect): String {
        return JSONObject()
            .put("left", rect.left)
            .put("top", rect.top)
            .put("width", rect.width)
            .put("height", rect.height)
            .toString()
    }

    private fun maskToJson(rect: RelativeRect): String {
        val item = JSONObject()
            .put("id", "mask-1")
            .put("rect", JSONObject(rectToJson(rect)))
            .put("color", "#FFFFFF")
        return JSONObject()
            .put("items", JSONArray().put(item))
            .toString()
    }

    private suspend fun refreshCatalog() {
        val workbooks = dao.getWorkbooksOnce()
        val chapters = workbooks.flatMap { dao.getChaptersOnce(it.workbookId) }
        _state.update { it.copy(workbooks = workbooks, chapters = chapters) }
    }

    private fun orderExamProblems(
        problems: List<ProblemEntity>,
        randomOrder: Boolean,
        wrongFirst: Boolean
    ): List<ProblemEntity> {
        fun hasWrongHistory(problem: ProblemEntity): Boolean {
            return _state.value.attempts.any {
                it.problemId == problem.problemId && (it.isCorrect == false || it.maxAttemptsReached)
            }
        }

        fun baseOrder(items: List<ProblemEntity>): List<ProblemEntity> {
            return if (randomOrder) items.shuffled() else items.sortedWith(compareBy<ProblemEntity> { it.chapterId }.thenBy { it.orderIndex })
        }

        return if (wrongFirst) {
            baseOrder(problems.filter(::hasWrongHistory)) + baseOrder(problems.filterNot(::hasWrongHistory))
        } else {
            baseOrder(problems)
        }
    }

    private fun stringListJson(values: List<String>): String {
        return JSONArray().apply {
            values.forEach { put(it) }
        }.toString()
    }
}

class MasterViewModelFactory(private val container: AppContainer) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return MasterViewModel(
            container = container,
            dao = container.dao,
            fileStorage = container.fileStorage,
            workbookImportService = container.workbookImportService
        ) as T
    }
}
