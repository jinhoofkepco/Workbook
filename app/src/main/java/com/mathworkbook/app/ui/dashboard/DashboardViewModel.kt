package com.mathworkbook.app.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.mathworkbook.app.core.AppContainer
import com.mathworkbook.app.core.database.ChapterEntity
import com.mathworkbook.app.core.database.ExamSessionEntity
import com.mathworkbook.app.core.database.MathDao
import com.mathworkbook.app.core.database.PracticeAttemptEntity
import com.mathworkbook.app.core.database.ProblemEntity
import com.mathworkbook.app.core.database.WorkbookEntity
import com.mathworkbook.app.core.domain.FinalStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class WorkbookProgressSummary(
    val workbook: WorkbookEntity,
    val totalProblems: Int,
    val solvedProblems: Int,
    val correctProblems: Int,
    val wrongProblems: Int,
    val attemptCount: Int
) {
    val progressPercent: Int
        get() = if (totalProblems == 0) 0 else (solvedProblems * 100 / totalProblems)
}

data class ChapterProgressSummary(
    val chapter: ChapterEntity,
    val totalProblems: Int,
    val solvedProblems: Int,
    val correctProblems: Int,
    val wrongProblems: Int,
    val attemptCount: Int
) {
    val progressPercent: Int
        get() = if (totalProblems == 0) 0 else (solvedProblems * 100 / totalProblems)
}

data class ProblemProgressSummary(
    val problem: ProblemEntity,
    val latestStatus: FinalStatus?,
    val attemptCount: Int,
    val isCorrect: Boolean?,
    val maxAttemptsReached: Boolean
)

data class DashboardUiState(
    val loading: Boolean = true,
    val selectedWorkbookId: String? = null,
    val selectedChapterId: String? = null,
    val workbookSummaries: List<WorkbookProgressSummary> = emptyList(),
    val chapterSummaries: List<ChapterProgressSummary> = emptyList(),
    val problemSummaries: List<ProblemProgressSummary> = emptyList(),
    val attempts: List<PracticeAttemptEntity> = emptyList(),
    val examSessions: List<ExamSessionEntity> = emptyList()
)

class DashboardViewModel(
    private val container: AppContainer,
    private val dao: MathDao
) : ViewModel() {
    private val _state = MutableStateFlow(DashboardUiState())
    val state: StateFlow<DashboardUiState> = _state

    private var workbooks: List<WorkbookEntity> = emptyList()
    private var chapters: List<ChapterEntity> = emptyList()
    private var problems: List<ProblemEntity> = emptyList()
    private var attempts: List<PracticeAttemptEntity> = emptyList()

    init {
        viewModelScope.launch {
            container.seedData.ensure()
            loadStaticData()
            rebuild()
        }
        viewModelScope.launch {
            dao.observePracticeAttempts().collect { latestAttempts ->
                attempts = latestAttempts
                rebuild()
            }
        }
        viewModelScope.launch {
            dao.observeExamSessions().collect { sessions ->
                _state.update { it.copy(examSessions = sessions) }
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            loadStaticData()
            rebuild()
        }
    }

    fun selectWorkbook(workbookId: String) {
        _state.update { it.copy(selectedWorkbookId = workbookId, selectedChapterId = null, problemSummaries = emptyList()) }
        rebuild()
    }

    fun toggleChapterDetails(chapterId: String) {
        _state.update {
            it.copy(selectedChapterId = if (it.selectedChapterId == chapterId) null else chapterId)
        }
        rebuild()
    }

    fun clearSelectedWorkbook() {
        _state.update {
            it.copy(
                selectedWorkbookId = null,
                selectedChapterId = null,
                chapterSummaries = emptyList(),
                problemSummaries = emptyList()
            )
        }
    }

    private suspend fun loadStaticData() {
        workbooks = dao.getWorkbooksOnce()
        chapters = workbooks.flatMap { dao.getChaptersOnce(it.workbookId) }
        problems = dao.getAllProblemsOnce()
    }

    private fun rebuild() {
        val attemptsByProblem = attempts.groupBy { it.problemId }
        val summaries = workbooks.map { workbook ->
            val workbookProblems = problems.filter { it.workbookId == workbook.workbookId }
            val problemIds = workbookProblems.map { it.problemId }.toSet()
            val solvedIds = problemIds.filter { problemId ->
                attemptsByProblem[problemId]?.any { it.isCorrect != null || it.maxAttemptsReached } == true
            }.toSet()
            val correctIds = problemIds.filter { problemId ->
                attemptsByProblem[problemId]?.any { it.isCorrect == true } == true
            }.toSet()
            val wrongIds = problemIds.filter { problemId ->
                attemptsByProblem[problemId]?.any { it.isCorrect == false || it.maxAttemptsReached } == true
            }.toSet()
            WorkbookProgressSummary(
                workbook = workbook,
                totalProblems = problemIds.size,
                solvedProblems = solvedIds.size,
                correctProblems = correctIds.size,
                wrongProblems = wrongIds.size,
                attemptCount = attempts.count { it.workbookId == workbook.workbookId }
            )
        }

        val selectedWorkbookId = _state.value.selectedWorkbookId
        val selectedChapterId = _state.value.selectedChapterId
        val chapterSummaries = if (selectedWorkbookId == null) {
            emptyList()
        } else {
            chapters
                .filter { it.workbookId == selectedWorkbookId }
                .sortedBy { it.orderIndex }
                .map { chapter ->
                    val chapterProblems = problems.filter { it.chapterId == chapter.chapterId }
                    val problemIds = chapterProblems.map { it.problemId }.toSet()
                    val solvedIds = problemIds.filter { problemId ->
                        attemptsByProblem[problemId]?.any { it.isCorrect != null || it.maxAttemptsReached } == true
                    }.toSet()
                    val correctIds = problemIds.filter { problemId ->
                        attemptsByProblem[problemId]?.any { it.isCorrect == true } == true
                    }.toSet()
                    val wrongIds = problemIds.filter { problemId ->
                        attemptsByProblem[problemId]?.any { it.isCorrect == false || it.maxAttemptsReached } == true
                    }.toSet()
                    ChapterProgressSummary(
                        chapter = chapter,
                        totalProblems = problemIds.size,
                        solvedProblems = solvedIds.size,
                        correctProblems = correctIds.size,
                        wrongProblems = wrongIds.size,
                        attemptCount = attempts.count { it.chapterId == chapter.chapterId }
                    )
                }
        }
        val problemSummaries = if (selectedChapterId == null) {
            emptyList()
        } else {
            problems
                .filter { it.chapterId == selectedChapterId }
                .sortedBy { it.orderIndex }
                .map { problem ->
                    val problemAttempts = attemptsByProblem[problem.problemId].orEmpty()
                    val latest = problemAttempts.maxByOrNull { it.startedAt }
                    ProblemProgressSummary(
                        problem = problem,
                        latestStatus = latest?.finalStatus,
                        attemptCount = problemAttempts.size,
                        isCorrect = latest?.isCorrect,
                        maxAttemptsReached = latest?.maxAttemptsReached == true
                    )
                }
        }

        _state.update {
            it.copy(
                loading = false,
                workbookSummaries = summaries,
                chapterSummaries = chapterSummaries,
                problemSummaries = problemSummaries,
                attempts = attempts
            )
        }
    }
}

class DashboardViewModelFactory(private val container: AppContainer) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return DashboardViewModel(container = container, dao = container.dao) as T
    }
}
