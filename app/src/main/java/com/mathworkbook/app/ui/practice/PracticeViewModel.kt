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
import com.mathworkbook.app.core.database.WorkbookEntity
import com.mathworkbook.app.core.domain.KeyboardType
import com.mathworkbook.app.core.domain.ProblemType
import com.mathworkbook.app.core.files.FileStorage
import com.mathworkbook.app.core.usecase.SubmitPracticeAnswerUseCase
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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
    private val _state = MutableStateFlow(PracticeUiState())
    val state: StateFlow<PracticeUiState> = _state

    init {
        viewModelScope.launch {
            container.seedData.ensure()
            refreshWorkbooks()
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

    fun openProblem(workbookId: String, chapterId: String, problemId: String) {
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
            loadChapter(chapter, initialProblemId = problemId)
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

    fun showHint() {
        _state.update { current ->
            current.copy(feedback = current.currentProblem?.hintText ?: "힌트가 없는 문제입니다.")
        }
    }

    fun toggleCorrectAnswer() {
        _state.update { it.copy(showCorrectAnswer = !it.showCorrectAnswer) }
    }

    fun submit(solutionVectorJson: String? = null) {
        val current = _state.value
        val problem = current.currentProblem ?: return
        if (current.submitting) return
        viewModelScope.launch {
            _state.update { it.copy(submitting = true, feedback = null, showCorrectMark = false) }
            val solutionPath = solutionVectorJson?.let {
                fileStorage.saveSolutionVectorJson(
                    studentId = studentId,
                    attemptOrSessionId = "practice-${System.currentTimeMillis()}",
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
            val message = when {
                result.gradingResult.requiresManualReview -> "검토자가 풀이를 확인해야 합니다."
                result.gradingResult.isCorrect -> "정답입니다."
                result.autoMoveNext -> "다음 문제로 넘어갈게."
                else -> "다시 해보자. 남은 기회 ${result.remainingTryCount}번"
            }
            _state.update {
                it.copy(
                    submitting = false,
                    remainingTryCount = result.remainingTryCount,
                    feedback = message,
                    showCorrectMark = result.gradingResult.isCorrect,
                    inputByField = if (shouldClearAnswer) blankInputs(it.fields) else it.inputByField,
                    selectedChoiceIds = if (shouldClearAnswer) emptySet() else it.selectedChoiceIds
                )
            }
            if (result.autoMoveNext) {
                val delayMillis = dao.getAppSettings()?.autoMoveDelayMillis ?: 1_000L
                delay(delayMillis)
                moveNext(clearFeedback = true)
            }
        }
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
        val previous = (_state.value.currentIndex - 1).coerceAtLeast(0)
        viewModelScope.launch { loadProblem(previous, clearFeedback = true) }
    }

    private suspend fun loadChapter(chapter: ChapterEntity, initialProblemId: String? = null) {
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
            loadProblem(initialIndex, clearFeedback = true)
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

    private suspend fun loadProblem(index: Int, clearFeedback: Boolean) {
        val problems = _state.value.problems
        val problem = problems.getOrNull(index) ?: return
        val fields = dao.getAnswerFields(problem.problemId)
        val rules = dao.getAnswerRules(problem.problemId)
        val choices = dao.getChoices(problem.problemId)
        val openAttempt = dao.getOpenPracticeAttempt(studentId, problem.problemId)
        val latestAttempt = dao.getPracticeAttemptsForProblem(studentId, problem.problemId).firstOrNull()
        val latestLogs = latestAttempt?.let { dao.getAttemptInputLogs(it.attemptId) }.orEmpty()
        val maxTryCount = openAttempt?.maxInputTryCount
            ?: (dao.getAppSettings()?.defaultMaxInputTryCount ?: 3)
        val remaining = (maxTryCount - (openAttempt?.inputTryCount ?: 0)).coerceAtLeast(0)
        val activeFieldId = fields.firstOrNull()?.answerFieldId
        _state.update {
            it.copy(
                currentIndex = index,
                currentProblem = problem,
                fields = fields,
                rules = rules,
                choices = choices,
                latestAttempt = latestAttempt,
                latestLogs = latestLogs,
                showCorrectAnswer = false,
                inputByField = blankInputs(fields),
                selectedChoiceIds = emptySet(),
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

    private fun keyboardTypeFor(problem: ProblemEntity, fields: List<AnswerFieldEntity>): KeyboardType {
        return when {
            problem.problemType == ProblemType.MULTIPLE_CHOICE -> KeyboardType.MULTIPLE_CHOICE
            fields.size > 1 -> KeyboardType.MULTI_FIELD
            problem.problemType == ProblemType.MULTI_FIELD -> KeyboardType.MULTI_FIELD
            else -> KeyboardType.INTEGER
        }
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
