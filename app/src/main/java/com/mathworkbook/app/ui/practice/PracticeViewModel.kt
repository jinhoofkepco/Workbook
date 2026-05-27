package com.mathworkbook.app.ui.practice

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.mathworkbook.app.core.AppContainer
import com.mathworkbook.app.core.database.AnswerFieldEntity
import com.mathworkbook.app.core.database.AnswerRuleEntity
import com.mathworkbook.app.core.database.AttemptInputLogEntity
import com.mathworkbook.app.core.database.ChapterEntity
import com.mathworkbook.app.core.database.ChoiceEntity
import com.mathworkbook.app.core.database.MathDao
import com.mathworkbook.app.core.database.PracticeAttemptEntity
import com.mathworkbook.app.core.database.ProblemEntity
import com.mathworkbook.app.core.database.ReviewEntity
import com.mathworkbook.app.core.database.WorkbookEntity
import com.mathworkbook.app.core.domain.FinalStatus
import com.mathworkbook.app.core.domain.KeyboardType
import com.mathworkbook.app.core.domain.ManualReviewStatus
import com.mathworkbook.app.core.domain.ProblemType
import com.mathworkbook.app.core.files.FileStorage
import com.mathworkbook.app.core.usecase.SubmitPracticeAnswerUseCase
import com.mathworkbook.app.core.viewer.ViewerCurrentScreenSnapshot
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File

data class PracticeUiState(
    val loading: Boolean = true,
    val workbooks: List<WorkbookEntity> = emptyList(),
    val chapters: List<ChapterEntity> = emptyList(),
    val selectedWorkbook: WorkbookEntity? = null,
    val selectedChapter: ChapterEntity? = null,
    val problems: List<ProblemEntity> = emptyList(),
    val currentIndex: Int = 0,
    val currentProblem: ProblemEntity? = null,
    val fields: List<AnswerFieldEntity> = emptyList(),
    val rules: List<AnswerRuleEntity> = emptyList(),
    val choices: List<ChoiceEntity> = emptyList(),
    val latestAttempt: PracticeAttemptEntity? = null,
    val latestLogs: List<AttemptInputLogEntity> = emptyList(),
    val visibleAnswerLogs: List<AttemptInputLogEntity> = emptyList(),
    val attemptsForProblem: List<PracticeAttemptEntity> = emptyList(),
    val logsByAttempt: Map<String, List<AttemptInputLogEntity>> = emptyMap(),
    val selectedStudentAttemptId: String? = null,
    val masterNoteVectorJson: String? = null,
    val showCorrectAnswer: Boolean = false,
    val inputByField: Map<String, String> = emptyMap(),
    val selectedChoiceIds: Set<String> = emptySet(),
    val activeFieldId: String? = null,
    val remainingTryCount: Int = 3,
    val maxTryCount: Int = 3,
    val feedback: String? = null,
    val showCorrectMark: Boolean = false,
    val submitting: Boolean = false,
    val keyboardType: KeyboardType = KeyboardType.INTEGER
)

class PracticeViewModel(
    private val container: AppContainer,
    private val dao: MathDao,
    private val fileStorage: FileStorage,
    private val submitPracticeAnswerUseCase: SubmitPracticeAnswerUseCase
) : ViewModel() {
    private val studentId = "student-demo"
    private var masterMode: Boolean = false
    private var submitInFlight: Boolean = false
    private val _state = MutableStateFlow(PracticeUiState())
    val state: StateFlow<PracticeUiState> = _state

    init {
        viewModelScope.launch {
            container.seedData.ensure()
            refreshWorkbooks()
        }
    }

    fun setMasterMode(enabled: Boolean, reloadCurrent: Boolean = true) {
        if (masterMode == enabled) return
        masterMode = enabled
        val current = _state.value
        if (reloadCurrent && current.currentProblem != null) {
            viewModelScope.launch { loadProblem(current.currentIndex, clearFeedback = true) }
        }
    }

    fun refreshWorkbooks() {
        viewModelScope.launch {
            val workbooks = dao.getWorkbooksOnce()
            _state.update {
                it.copy(
                    loading = false,
                    workbooks = workbooks,
                    selectedWorkbook = null,
                    selectedChapter = null,
                    chapters = emptyList(),
                    problems = emptyList(),
                    currentProblem = null,
                    feedback = null,
                    showCorrectMark = false
                )
            }
        }
    }

    fun selectWorkbook(workbook: WorkbookEntity) {
        viewModelScope.launch {
            val chapters = dao.getChaptersOnce(workbook.workbookId)
            _state.update {
                it.copy(
                    selectedWorkbook = workbook,
                    selectedChapter = null,
                    chapters = chapters,
                    problems = emptyList(),
                    currentProblem = null,
                    feedback = null,
                    showCorrectMark = false
                )
            }
        }
    }

    fun selectChapter(chapter: ChapterEntity) {
        viewModelScope.launch {
            loadChapter(chapter)
        }
    }

    fun openChapter(workbookId: String, chapterId: String) {
        viewModelScope.launch {
            val workbook = dao.getWorkbooksOnce().firstOrNull { it.workbookId == workbookId } ?: return@launch
            val chapters = dao.getChaptersOnce(workbookId)
            val chapter = chapters.firstOrNull { it.chapterId == chapterId } ?: return@launch
            _state.update {
                it.copy(
                    selectedWorkbook = workbook,
                    chapters = chapters,
                    selectedChapter = null,
                    problems = emptyList(),
                    currentProblem = null,
                    feedback = null,
                    showCorrectMark = false
                )
            }
            loadChapter(chapter)
        }
    }

    fun openProblem(workbookId: String, chapterId: String, problemId: String, attemptId: String? = null) {
        viewModelScope.launch {
            val workbook = dao.getWorkbooksOnce().firstOrNull { it.workbookId == workbookId } ?: return@launch
            val chapters = dao.getChaptersOnce(workbookId)
            val chapter = chapters.firstOrNull { it.chapterId == chapterId } ?: return@launch
            _state.update {
                it.copy(
                    selectedWorkbook = workbook,
                    chapters = chapters,
                    selectedChapter = null,
                    problems = emptyList(),
                    currentProblem = null,
                    feedback = null,
                    showCorrectMark = false
                )
            }
            loadChapter(chapter, initialProblemId = problemId, initialAttemptId = attemptId)
        }
    }

    fun backToWorkbooks() {
        _state.update {
            it.copy(
                selectedWorkbook = null,
                selectedChapter = null,
                chapters = emptyList(),
                problems = emptyList(),
                currentProblem = null,
                feedback = null,
                showCorrectMark = false
            )
        }
    }

    fun backToChapters() {
        _state.update {
            it.copy(
                selectedChapter = null,
                problems = emptyList(),
                currentProblem = null,
                feedback = null,
                showCorrectMark = false
            )
        }
    }

    fun updateInput(fieldId: String, value: String) {
        _state.update { current ->
            current.copy(
                inputByField = current.inputByField + (fieldId to value),
                activeFieldId = fieldId,
                feedback = null,
                showCorrectMark = false
            )
        }
    }

    fun setActiveField(fieldId: String) {
        _state.update { it.copy(activeFieldId = fieldId) }
    }

    fun appendToActiveField(value: String) {
        val current = _state.value
        val fieldId = current.activeFieldId ?: current.fields.firstOrNull()?.answerFieldId ?: return
        val currentValue = current.inputByField[fieldId].orEmpty()
        updateInput(fieldId, currentValue + value)
    }

    fun backspaceActiveField() {
        val current = _state.value
        val fieldId = current.activeFieldId ?: current.fields.firstOrNull()?.answerFieldId ?: return
        val currentValue = current.inputByField[fieldId].orEmpty()
        updateInput(fieldId, currentValue.dropLast(1))
    }

    fun toggleChoice(choiceId: String) {
        val fieldId = _state.value.fields.firstOrNull()?.answerFieldId ?: return
        _state.update { current ->
            val selected = current.selectedChoiceIds.toMutableSet()
            if (!selected.add(choiceId)) selected.remove(choiceId)
            current.copy(
                selectedChoiceIds = selected,
                inputByField = current.inputByField + (fieldId to selected.sorted().joinToString(",")),
                feedback = null,
                showCorrectMark = false
            )
        }
    }

    fun toggleStudentAttempt(attemptId: String) {
        _state.update { current ->
            current.copy(
                selectedStudentAttemptId = if (current.selectedStudentAttemptId == attemptId) null else attemptId
            )
        }
    }

    fun deleteStudentAttempt(attemptId: String) {
        val attempt = _state.value.attemptsForProblem.firstOrNull { it.attemptId == attemptId } ?: return
        viewModelScope.launch {
            dao.deleteAttemptInputLogs(attemptId)
            dao.deleteReviewsForAttempt(attemptId)
            dao.deletePracticeAttempt(attemptId)
            attempt.solutionImagePath
                ?.takeIf { it.isNotBlank() }
                ?.let { path -> runCatching { File(path).delete() } }

            _state.update { current ->
                val attempts = current.attemptsForProblem.filterNot { it.attemptId == attemptId }
                val logs = current.logsByAttempt - attemptId
                val latest = attempts.firstOrNull()
                current.copy(
                    latestAttempt = latest,
                    latestLogs = latest?.let { logs[it.attemptId].orEmpty() }.orEmpty(),
                    visibleAnswerLogs = emptyList(),
                    attemptsForProblem = attempts,
                    logsByAttempt = logs,
                    selectedStudentAttemptId = null,
                    feedback = "풀이 기록을 삭제했습니다."
                )
            }
        }
    }

    fun reviewStudentAttempt(attemptId: String, isCorrect: Boolean, note: String) {
        val attempt = _state.value.attemptsForProblem.firstOrNull { it.attemptId == attemptId } ?: return
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val cleanedNote = note.trim()
            val reviewStatus = if (isCorrect) ManualReviewStatus.GOOD else ManualReviewStatus.NEEDS_EXPLANATION
            val updatedAttempt = attempt.copy(
                finalStatus = if (isCorrect) FinalStatus.CORRECT else FinalStatus.WRONG,
                isCorrect = isCorrect,
                maxAttemptsReached = if (isCorrect) false else attempt.maxAttemptsReached,
                submittedAt = attempt.submittedAt ?: now,
                reviewerComment = cleanedNote.ifBlank { null },
                manualReviewStatus = reviewStatus
            )
            dao.updatePracticeAttempt(updatedAttempt)
            dao.upsertReview(
                ReviewEntity(
                    reviewId = "review-$attemptId",
                    attemptId = attemptId,
                    examSessionId = null,
                    problemId = attempt.problemId,
                    reviewerId = "master",
                    processScore = reviewStatus,
                    comment = cleanedNote,
                    reviewedAt = now
                )
            )

            val attemptsForProblem = dao.getPracticeAttemptsForProblem(studentId, attempt.problemId)
            val logsByAttempt = attemptsForProblem.associate { refreshedAttempt ->
                refreshedAttempt.attemptId to dao.getAttemptInputLogs(refreshedAttempt.attemptId)
            }
            val latestAttempt = attemptsForProblem.firstOrNull()
            _state.update { current ->
                current.copy(
                    latestAttempt = latestAttempt,
                    latestLogs = latestAttempt?.let { refreshedAttempt ->
                        logsByAttempt[refreshedAttempt.attemptId].orEmpty()
                    }.orEmpty(),
                    attemptsForProblem = attemptsForProblem,
                    logsByAttempt = logsByAttempt,
                    selectedStudentAttemptId = attemptId,
                    feedback = if (isCorrect) {
                        "정답 처리와 채점 노트를 저장했습니다."
                    } else {
                        "오답 처리와 채점 노트를 저장했습니다."
                    }
                )
            }
        }
    }

    fun showHint() {
        _state.update { current ->
            current.copy(feedback = current.currentProblem?.hintText ?: "힌트가 없는 문제입니다.")
        }
    }

    fun clearFeedback() {
        _state.update { it.copy(feedback = null) }
    }

    fun publishViewerCurrentScreen(solutionVectorJson: String) {
        if (!container.viewerServer.state.value.running) return
        val current = _state.value
        val problem = current.currentProblem ?: run {
            container.viewerServer.clearCurrentScreen()
            return
        }
        container.viewerServer.updateCurrentScreen(
            ViewerCurrentScreenSnapshot(
                workbookTitle = current.selectedWorkbook?.title.orEmpty(),
                chapterTitle = current.selectedChapter?.title.orEmpty(),
                positionLabel = "${current.currentIndex + 1}/${current.problems.size}",
                problem = problem,
                currentAnswer = current.inputByField.values
                    .filter { it.isNotBlank() }
                    .joinToString(", "),
                solutionVectorJson = solutionVectorJson,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    fun clearViewerCurrentScreen() {
        container.viewerServer.clearCurrentScreen()
    }

    fun isViewerRunning(): Boolean = container.viewerServer.state.value.running

    fun toggleCorrectAnswer() {
        _state.update { it.copy(showCorrectAnswer = !it.showCorrectAnswer) }
    }

    fun submit(solutionVectorJson: String? = null) {
        val current = _state.value
        val problem = current.currentProblem ?: return
        if (submitInFlight || current.submitting) return
        submitInFlight = true
        viewModelScope.launch {
            _state.update { it.copy(submitting = true, feedback = null, showCorrectMark = false) }
            val existingAttempt = dao.getOpenPracticeAttempt(studentId, problem.problemId)
            val shouldSaveSolution = !solutionVectorJson.isNullOrBlank() &&
                (solutionVectorJson.hasInkStrokes() || existingAttempt?.solutionImagePath.isNullOrBlank())
            val solutionPath = solutionVectorJson?.takeIf { shouldSaveSolution }?.let {
                fileStorage.saveSolutionVectorJson(
                    studentId = studentId,
                    attemptOrSessionId = existingAttempt?.attemptId ?: "practice-${System.currentTimeMillis()}",
                    problemId = problem.problemId,
                    vectorJson = it
                )
            }
            val result = submitPracticeAnswerUseCase.submit(
                studentId = studentId,
                problem = problem,
                generatedProblemId = null,
                submittedAnswers = _state.value.inputByField,
                solutionImagePath = solutionPath
            )
            val shouldClearAnswer = !result.gradingResult.isCorrect && !result.gradingResult.requiresManualReview
            val shouldMoveNext = result.gradingResult.isCorrect || result.gradingResult.requiresManualReview || result.autoMoveNext
            val message = when {
                result.gradingResult.requiresManualReview -> "검토자가 풀이를 확인해야 합니다."
                result.gradingResult.isCorrect -> "정답입니다."
                result.autoMoveNext -> "기회를 모두 사용해서 다음 문제로 넘어갈게."
                else -> "다시 해보자. 남은 기회 ${result.remainingTryCount}/${result.attempt.maxInputTryCount}"
            }
            val attemptsForProblem = dao.getPracticeAttemptsForProblem(studentId, problem.problemId)
            val logsByAttempt = attemptsForProblem.associate { attempt ->
                attempt.attemptId to dao.getAttemptInputLogs(attempt.attemptId)
            }
            val latestAttempt = attemptsForProblem.firstOrNull()
            _state.update {
                it.copy(
                    submitting = false,
                    latestAttempt = latestAttempt,
                    latestLogs = latestAttempt?.let { attempt -> logsByAttempt[attempt.attemptId].orEmpty() }.orEmpty(),
                    visibleAnswerLogs = logsByAttempt[result.attempt.attemptId].orEmpty(),
                    attemptsForProblem = attemptsForProblem,
                    logsByAttempt = logsByAttempt,
                    remainingTryCount = result.remainingTryCount,
                    feedback = message,
                    showCorrectMark = result.gradingResult.isCorrect,
                    inputByField = if (shouldClearAnswer) blankInputs(it.fields) else it.inputByField,
                    selectedChoiceIds = if (shouldClearAnswer) emptySet() else it.selectedChoiceIds
                )
            }
            if (shouldMoveNext) {
                delay(1_000L)
                moveNext(clearFeedback = true)
            }
            submitInFlight = false
        }.invokeOnCompletion {
            submitInFlight = false
            _state.update { state -> state.copy(submitting = false) }
        }
    }

    fun saveMasterNote(vectorJson: String) {
        val problem = _state.value.currentProblem ?: return
        viewModelScope.launch {
            fileStorage.saveMasterNoteVectorJson(problem.problemId, vectorJson)
            _state.update {
                it.copy(
                    masterNoteVectorJson = vectorJson,
                    feedback = "마스터 노트를 저장했습니다."
                )
            }
        }
    }

    fun mergeMasterDrawingIntoProblemImage(vectorJson: String) {
        val problem = _state.value.currentProblem ?: return
        val imagePath = problem.imagePath?.takeIf { it.isNotBlank() }
        if (imagePath == null) {
            _state.update { it.copy(feedback = "합쳐 저장할 문제 사진이 없습니다.") }
            return
        }
        viewModelScope.launch {
            runCatching {
                val mergedPath = fileStorage.mergeProblemImageWithVector(
                    workbookId = problem.workbookId,
                    problemId = problem.problemId,
                    imagePath = imagePath,
                    vectorJson = vectorJson
                )
                val updatedProblem = problem.copy(
                    imagePath = mergedPath,
                    updatedAt = System.currentTimeMillis()
                )
                dao.upsertProblem(updatedProblem)
                val emptyNote = """{"strokes":[]}"""
                fileStorage.saveMasterNoteVectorJson(problem.problemId, emptyNote)
                _state.update { current ->
                    current.copy(
                        currentProblem = updatedProblem,
                        problems = current.problems.map {
                            if (it.problemId == updatedProblem.problemId) updatedProblem else it
                        },
                        masterNoteVectorJson = emptyNote,
                        feedback = "사진 위 필기를 합쳐 새 문제 사진으로 저장했습니다."
                    )
                }
            }.onFailure { error ->
                _state.update {
                    it.copy(feedback = error.message ?: "사진 저장에 실패했습니다.")
                }
            }
        }
    }

    fun adjustCurrentProblemImageHeight(deltaDp: Int) {
        val problem = _state.value.currentProblem ?: return
        val updatedProblem = problem.withUpdatedImageDisplay { display ->
            val currentHeight = display.optInt("heightDp", 300)
            display.put("heightDp", (currentHeight + deltaDp).coerceIn(160, 1200))
            display.put("widthFraction", display.optDouble("widthFraction", 1.0).coerceIn(0.35, 1.0))
            if (!display.has("contentScale")) display.put("contentScale", "fit")
        }
        saveUpdatedProblem(updatedProblem, "그림 크기를 조정했습니다.")
    }

    fun setCurrentProblemImageMode(mode: String) {
        val problem = _state.value.currentProblem ?: return
        val updatedProblem = problem.withUpdatedImageDisplay { display ->
            display.put("widthFraction", 1.0)
            when (mode) {
                "crop" -> {
                    display.put("contentScale", "crop")
                    display.put("heightDp", display.optInt("heightDp", 420).coerceAtLeast(420))
                }
                else -> {
                    display.put("contentScale", "fillWidth")
                }
            }
        }
        saveUpdatedProblem(
            updatedProblem,
            if (mode == "crop") "그림을 크게 자르기 모드로 바꿨습니다." else "그림을 전체 맞춤으로 바꿨습니다."
        )
    }

    fun updateCurrentProblemImageTransform(scale: Float, offsetX: Float, offsetY: Float, heightDp: Int?) {
        val problem = _state.value.currentProblem ?: return
        val updatedProblem = problem.withUpdatedImageDisplay { display ->
            display.put("widthFraction", display.optDouble("widthFraction", 1.0).coerceIn(0.35, 1.0))
            if (!display.has("contentScale")) display.put("contentScale", "fit")
            heightDp?.let { display.put("heightDp", it.coerceIn(160, 1800)) }
            display.put("scale", scale.toDouble().coerceIn(0.6, 2.2))
            display.put("offsetX", offsetX.toDouble().coerceIn(-1600.0, 1600.0))
            display.put("offsetY", offsetY.toDouble().coerceIn(-1600.0, 1600.0))
        }
        saveUpdatedProblem(updatedProblem, "그림 위치를 저장했습니다.")
    }

    fun saveCorrectAnswersFromCurrentInput() {
        val current = _state.value
        val problem = current.currentProblem ?: return
        viewModelScope.launch {
            if (problem.problemType == ProblemType.MULTIPLE_CHOICE) {
                current.choices.forEach { choice ->
                    dao.upsertChoice(choice.copy(isCorrect = current.selectedChoiceIds.contains(choice.choiceId)))
                }
            } else {
                current.fields.filterNot { it.isDisabledForInput() }.forEach { field ->
                    val cleaned = current.inputByField[field.answerFieldId].orEmpty().trim()
                    val matchingRules = current.rules.filter { rule ->
                        rule.answerFieldId == field.answerFieldId ||
                            (current.fields.size == 1 && rule.answerFieldId == null)
                    }
                    matchingRules.firstOrNull()?.let { rule ->
                        dao.upsertAnswerRule(
                            rule.copy(
                                correctAnswerRaw = cleaned,
                                normalizedAnswer = cleaned.replace(",", "")
                            )
                        )
                    }
                }
            }
            val updatedRules = dao.getAnswerRules(problem.problemId)
            val updatedChoices = dao.getChoices(problem.problemId)
            _state.update {
                it.copy(
                    rules = updatedRules,
                    choices = updatedChoices,
                    feedback = "정답을 저장했습니다."
                )
            }
        }
    }

    private fun saveUpdatedProblem(problem: ProblemEntity, message: String) {
        viewModelScope.launch {
            dao.upsertProblem(problem)
            _state.update { current ->
                current.copy(
                    currentProblem = problem,
                    problems = current.problems.map {
                        if (it.problemId == problem.problemId) problem else it
                    },
                    feedback = message
                )
            }
        }
    }

    private fun ProblemEntity.withUpdatedImageDisplay(update: (JSONObject) -> Unit): ProblemEntity {
        val root = if (imageCropRectJson.isNullOrBlank()) {
            JSONObject()
        } else {
            runCatching { JSONObject(imageCropRectJson.orEmpty()) }.getOrDefault(JSONObject())
        }
        val display = root.optJSONObject("display") ?: JSONObject().also { root.put("display", it) }
        update(display)
        return copy(imageCropRectJson = root.toString(), updatedAt = System.currentTimeMillis())
    }

    private fun AnswerFieldEntity.isDisabledForInput(): Boolean {
        if (positionJson.isNullOrBlank()) return false
        return runCatching {
            val meta = JSONObject(positionJson.orEmpty())
            meta.optBoolean("disabled", false) || meta.optBoolean("readOnly", false)
        }.getOrDefault(false)
    }

    fun moveNext(clearFeedback: Boolean = false) {
        viewModelScope.launch {
            val current = _state.value
            val next = current.currentIndex + 1
            if (next <= current.problems.lastIndex) {
                loadProblem(next, clearFeedback)
            } else {
                moveToNextChapter()
            }
        }
    }

    fun movePrevious() {
        viewModelScope.launch {
            val current = _state.value
            val previous = current.currentIndex - 1
            if (previous >= 0) {
                loadProblem(previous, clearFeedback = true)
            } else {
                moveToPreviousChapter()
            }
        }
    }

    private suspend fun loadChapter(
        chapter: ChapterEntity,
        initialProblemId: String? = null,
        initialAttemptId: String? = null
    ) {
        val problems = dao.getProblemsInChapter(chapter.chapterId)
        val initialIndex = initialProblemId
            ?.let { problemId -> problems.indexOfFirst { it.problemId == problemId } }
            ?.takeIf { it >= 0 }
            ?: 0
        _state.update {
            it.copy(
                selectedChapter = chapter,
                problems = problems,
                currentIndex = initialIndex,
                currentProblem = null,
                feedback = null,
                showCorrectMark = false
            )
        }
        if (problems.isNotEmpty()) {
            loadProblem(initialIndex, clearFeedback = true, selectedAttemptId = initialAttemptId)
        } else {
            _state.update { it.copy(feedback = "이 단원에는 아직 문제가 없습니다.") }
        }
    }

    private suspend fun moveToNextChapter() {
        val current = _state.value
        val selectedChapter = current.selectedChapter ?: return
        val nextChapter = current.chapters
            .sortedBy { it.orderIndex }
            .dropWhile { it.chapterId != selectedChapter.chapterId }
            .drop(1)
            .firstOrNull()
        if (nextChapter != null) {
            loadChapter(nextChapter)
        } else {
            _state.update {
                it.copy(
                    feedback = "마지막 단원까지 모두 풀었습니다.",
                    showCorrectMark = false
                )
            }
        }
    }

    private suspend fun moveToPreviousChapter() {
        val current = _state.value
        val selectedChapter = current.selectedChapter ?: return
        val orderedChapters = current.chapters.sortedBy { it.orderIndex }
        val currentChapterIndex = orderedChapters.indexOfFirst { it.chapterId == selectedChapter.chapterId }
        val previousChapter = orderedChapters.getOrNull(currentChapterIndex - 1)
        if (previousChapter != null) {
            val previousProblems = dao.getProblemsInChapter(previousChapter.chapterId)
            loadChapter(previousChapter, initialProblemId = previousProblems.lastOrNull()?.problemId)
        } else {
            _state.update {
                it.copy(
                    feedback = "첫 소단원의 첫 문제입니다.",
                    showCorrectMark = false
                )
            }
        }
    }

    private suspend fun loadProblem(index: Int, clearFeedback: Boolean, selectedAttemptId: String? = null) {
        val problems = _state.value.problems
        val problem = problems.getOrNull(index) ?: return
        val fields = dao.getAnswerFields(problem.problemId)
        val rules = dao.getAnswerRules(problem.problemId)
        val choices = dao.getChoices(problem.problemId)
        val openAttempt = dao.getOpenPracticeAttempt(studentId, problem.problemId)
        val attemptsForProblem = dao.getPracticeAttemptsForProblem(studentId, problem.problemId)
        val logsByAttempt = attemptsForProblem.associate { attempt ->
            attempt.attemptId to dao.getAttemptInputLogs(attempt.attemptId)
        }
        val latestAttempt = attemptsForProblem.firstOrNull()
        val latestLogs = latestAttempt?.let { dao.getAttemptInputLogs(it.attemptId) }.orEmpty()
        val maxTryCount = openAttempt?.maxInputTryCount
            ?: (dao.getAppSettings()?.defaultMaxInputTryCount ?: 3)
        val remaining = (maxTryCount - (openAttempt?.inputTryCount ?: 0)).coerceAtLeast(0)
        val activeFieldId = fields.firstOrNull()?.answerFieldId
        val masterInputs = correctInputs(fields, rules)
        val masterChoices = choices.filter { it.isCorrect }.mapTo(mutableSetOf()) { it.choiceId }
        _state.update {
            it.copy(
                currentIndex = index,
                currentProblem = problem,
                fields = fields,
                rules = rules,
                choices = choices,
                latestAttempt = latestAttempt,
                latestLogs = latestLogs,
                visibleAnswerLogs = emptyList(),
                attemptsForProblem = attemptsForProblem,
                logsByAttempt = logsByAttempt,
                selectedStudentAttemptId = selectedAttemptId?.takeIf { attemptId ->
                    attemptsForProblem.any { it.attemptId == attemptId }
                },
                masterNoteVectorJson = if (masterMode) fileStorage.readMasterNoteVectorJson(problem.problemId) else null,
                showCorrectAnswer = false,
                inputByField = if (masterMode) masterInputs else blankInputs(fields),
                selectedChoiceIds = if (masterMode) masterChoices else emptySet(),
                activeFieldId = activeFieldId,
                remainingTryCount = remaining,
                maxTryCount = maxTryCount,
                feedback = if (clearFeedback) null else it.feedback,
                showCorrectMark = false,
                keyboardType = keyboardTypeFor(problem, fields)
            )
        }
    }

    private fun blankInputs(fields: List<AnswerFieldEntity>): Map<String, String> {
        return fields.associate { field -> field.answerFieldId to "" }
    }

    private fun correctInputs(
        fields: List<AnswerFieldEntity>,
        rules: List<AnswerRuleEntity>
    ): Map<String, String> {
        return fields.associate { field ->
            val values = rules
                .filter { rule ->
                    rule.answerFieldId == field.answerFieldId ||
                        (fields.size == 1 && rule.answerFieldId == null)
                }
                .map { it.correctAnswerRaw }
                .filter { it.isNotBlank() }
            field.answerFieldId to values.joinToString(", ")
        }
    }

    private fun keyboardTypeFor(problem: ProblemEntity, fields: List<AnswerFieldEntity>): KeyboardType {
        return when {
            problem.problemType == ProblemType.MULTIPLE_CHOICE -> KeyboardType.MULTIPLE_CHOICE
            fields.size > 1 -> KeyboardType.MULTI_FIELD
            problem.problemType == ProblemType.MULTI_FIELD -> KeyboardType.MULTI_FIELD
            else -> KeyboardType.INTEGER
        }
    }

    private fun String.hasInkStrokes(): Boolean {
        return runCatching {
            val strokes = JSONObject(this).optJSONArray("strokes") ?: return@runCatching false
            (0 until strokes.length()).any { strokeIndex ->
                val points = strokes.optJSONObject(strokeIndex)?.optJSONArray("points")
                points != null && points.length() > 0
            }
        }.getOrDefault(false)
    }
}

class PracticeViewModelFactory(private val container: AppContainer) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return PracticeViewModel(
            container = container,
            dao = container.dao,
            fileStorage = container.fileStorage,
            submitPracticeAnswerUseCase = container.submitPracticeAnswerUseCase
        ) as T
    }
}
