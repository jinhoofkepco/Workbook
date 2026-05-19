package com.mathworkbook.app.ui.exam

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.mathworkbook.app.core.AppContainer
import com.mathworkbook.app.core.database.AnswerFieldEntity
import com.mathworkbook.app.core.database.ChoiceEntity
import com.mathworkbook.app.core.database.ExamAnswerEntity
import com.mathworkbook.app.core.database.ExamSessionEntity
import com.mathworkbook.app.core.database.MathDao
import com.mathworkbook.app.core.database.ProblemEntity
import com.mathworkbook.app.core.domain.ExamSessionStatus
import com.mathworkbook.app.core.domain.GradingStatus
import com.mathworkbook.app.core.domain.KeyboardType
import com.mathworkbook.app.core.domain.ProblemType
import com.mathworkbook.app.core.files.FileStorage
import com.mathworkbook.app.core.usecase.ExamSubmitResult
import com.mathworkbook.app.core.usecase.SubmitExamUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONArray

enum class ExamMode {
    TAKING,
    REVIEW,
    RESULT
}

data class ExamUiState(
    val loading: Boolean = true,
    val mode: ExamMode = ExamMode.TAKING,
    val sessionId: String? = null,
    val examTitle: String = "",
    val problems: List<ProblemEntity> = emptyList(),
    val currentIndex: Int = 0,
    val currentProblem: ProblemEntity? = null,
    val fields: List<AnswerFieldEntity> = emptyList(),
    val choices: List<ChoiceEntity> = emptyList(),
    val activeFieldId: String? = null,
    val answers: Map<String, String> = emptyMap(),
    val selectedChoiceIds: Set<String> = emptySet(),
    val starredProblemIds: Set<String> = emptySet(),
    val keyboardType: KeyboardType = KeyboardType.INTEGER,
    val result: ExamSubmitResult? = null
)

class ExamViewModel(
    private val container: AppContainer,
    private val dao: MathDao,
    private val fileStorage: FileStorage,
    private val submitExamUseCase: SubmitExamUseCase
) : ViewModel() {
    private val studentId = "student-demo"
    private val _state = MutableStateFlow(ExamUiState())
    val state: StateFlow<ExamUiState> = _state

    init {
        viewModelScope.launch {
            container.seedData.ensure()
            startNewExam()
        }
    }

    fun appendInput(token: String) {
        val state = _state.value
        val problemId = state.currentProblem?.problemId ?: return
        val fieldId = state.activeFieldId ?: return
        val key = key(problemId, fieldId)
        val nextValue = state.answers[key].orEmpty() + token
        updateDraft(problemId, fieldId, nextValue)
    }

    fun backspace() {
        val state = _state.value
        val problemId = state.currentProblem?.problemId ?: return
        val fieldId = state.activeFieldId ?: return
        val key = key(problemId, fieldId)
        updateDraft(problemId, fieldId, state.answers[key].orEmpty().dropLast(1))
    }

    fun clearActiveInput() {
        val state = _state.value
        val problemId = state.currentProblem?.problemId ?: return
        val fieldId = state.activeFieldId ?: return
        updateDraft(problemId, fieldId, "")
    }

    fun setActiveField(fieldId: String) {
        _state.update { it.copy(activeFieldId = fieldId) }
    }

    fun updateInput(fieldId: String, value: String) {
        val problemId = _state.value.currentProblem?.problemId ?: return
        updateDraft(problemId, fieldId, value)
        _state.update { it.copy(activeFieldId = fieldId) }
    }

    fun toggleChoice(choiceId: String) {
        val state = _state.value
        val problemId = state.currentProblem?.problemId ?: return
        val fieldId = state.fields.firstOrNull()?.answerFieldId ?: return
        val selected = state.selectedChoiceIds.toMutableSet()
        if (!selected.add(choiceId)) selected.remove(choiceId)
        updateDraft(problemId, fieldId, selected.sorted().joinToString(","))
        _state.update { it.copy(selectedChoiceIds = selected) }
    }

    fun toggleStar(problemId: String? = _state.value.currentProblem?.problemId) {
        if (problemId == null) return
        _state.update { current ->
            val next = current.starredProblemIds.toMutableSet()
            if (!next.add(problemId)) next.remove(problemId)
            current.copy(starredProblemIds = next)
        }
    }

    fun moveNext() {
        val last = _state.value.problems.lastIndex
        val next = _state.value.currentIndex + 1
        if (next > last) {
            goReview()
        } else {
            viewModelScope.launch { loadProblem(next) }
        }
    }

    fun movePrevious() {
        val previous = (_state.value.currentIndex - 1).coerceAtLeast(0)
        viewModelScope.launch { loadProblem(previous) }
    }

    fun moveToProblem(index: Int) {
        viewModelScope.launch {
            _state.update { it.copy(mode = ExamMode.TAKING) }
            loadProblem(index)
        }
    }

    fun goReview() {
        _state.update { it.copy(mode = ExamMode.REVIEW) }
    }

    fun submitFinal() {
        val sessionId = _state.value.sessionId ?: return
        viewModelScope.launch {
            val result = submitExamUseCase.submit(sessionId)
            _state.update { it.copy(mode = ExamMode.RESULT, result = result) }
        }
    }

    fun saveCurrentSolution(vectorJson: String) {
        val state = _state.value
        val sessionId = state.sessionId ?: return
        val problemId = state.currentProblem?.problemId ?: return
        viewModelScope.launch {
            val path = fileStorage.saveSolutionVectorJson(
                studentId = studentId,
                attemptOrSessionId = sessionId,
                problemId = problemId,
                vectorJson = vectorJson
            )
            dao.getExamAnswersForProblem(sessionId, problemId).forEach { answer ->
                dao.upsertExamAnswer(answer.copy(solutionImagePath = path))
            }
        }
    }

    private suspend fun startNewExam() {
        val exam = dao.getLatestExam() ?: return
        val problemIds = parseStringArray(exam.problemIdsJson)
        val problems = problemIds.mapNotNull { dao.getProblem(it) }
        val sessionId = "session-${System.currentTimeMillis()}"
        dao.upsertExamSession(
            ExamSessionEntity(
                examSessionId = sessionId,
                examId = exam.examId,
                studentId = studentId,
                startedAt = System.currentTimeMillis(),
                submittedAt = null,
                status = ExamSessionStatus.IN_PROGRESS,
                totalProblemCount = problems.size,
                correctCount = 0,
                wrongCount = 0,
                blankCount = 0,
                score = 0.0,
                reviewCompleted = false
            )
        )
        problems.forEach { problem ->
            dao.getAnswerFields(problem.problemId).forEach { field ->
                dao.upsertExamAnswer(
                    ExamAnswerEntity(
                        examAnswerId = answerId(sessionId, problem.problemId, field.answerFieldId),
                        examSessionId = sessionId,
                        problemId = problem.problemId,
                        answerFieldId = field.answerFieldId,
                        submittedAnswerRaw = "",
                        normalizedSubmittedAnswer = null,
                        isCorrect = null,
                        gradingStatus = GradingStatus.DRAFT,
                        lastEditedAt = System.currentTimeMillis(),
                        solutionImagePath = null
                    )
                )
            }
        }
        _state.update {
            it.copy(
                loading = false,
                sessionId = sessionId,
                examTitle = exam.title,
                problems = problems
            )
        }
        if (problems.isNotEmpty()) loadProblem(0)
    }

    private suspend fun loadProblem(index: Int) {
        val problem = _state.value.problems.getOrNull(index) ?: return
        val fields = dao.getAnswerFields(problem.problemId)
        val choices = dao.getChoices(problem.problemId)
        val currentAnswers = dao.getExamAnswersForProblem(_state.value.sessionId.orEmpty(), problem.problemId)
        val answerMap = _state.value.answers + currentAnswers.associate {
            key(problem.problemId, it.answerFieldId.orEmpty()) to it.submittedAnswerRaw.orEmpty()
        }
        val fieldId = fields.firstOrNull()?.answerFieldId
        val selected = fieldId?.let {
            answerMap[key(problem.problemId, it)].orEmpty()
                .split(",")
                .map { id -> id.trim() }
                .filter { id -> id.isNotBlank() }
                .toSet()
        } ?: emptySet()
        _state.update {
            it.copy(
                mode = ExamMode.TAKING,
                currentIndex = index,
                currentProblem = problem,
                fields = fields,
                choices = choices,
                activeFieldId = fieldId,
                answers = answerMap,
                selectedChoiceIds = selected,
                keyboardType = keyboardTypeFor(problem, fields)
            )
        }
    }

    private fun updateDraft(problemId: String, fieldId: String, value: String) {
        val sessionId = _state.value.sessionId ?: return
        _state.update { current ->
            current.copy(answers = current.answers + (key(problemId, fieldId) to value))
        }
        viewModelScope.launch {
            dao.upsertExamAnswer(
                ExamAnswerEntity(
                    examAnswerId = answerId(sessionId, problemId, fieldId),
                    examSessionId = sessionId,
                    problemId = problemId,
                    answerFieldId = fieldId,
                    submittedAnswerRaw = value,
                    normalizedSubmittedAnswer = null,
                    isCorrect = null,
                    gradingStatus = GradingStatus.DRAFT,
                    lastEditedAt = System.currentTimeMillis(),
                    solutionImagePath = null
                )
            )
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

    private fun key(problemId: String, fieldId: String): String = "$problemId:$fieldId"

    private fun answerId(sessionId: String, problemId: String, fieldId: String): String {
        return "$sessionId-$problemId-$fieldId"
    }

    private fun parseStringArray(json: String): List<String> {
        val array = JSONArray(json)
        return List(array.length()) { index -> array.getString(index) }
    }
}

class ExamViewModelFactory(private val container: AppContainer) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return ExamViewModel(
            container = container,
            dao = container.dao,
            fileStorage = container.fileStorage,
            submitExamUseCase = container.submitExamUseCase
        ) as T
    }
}
