package com.mathworkbook.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mathworkbook.app.core.AppContainer
import com.mathworkbook.app.ui.dashboard.DashboardScreen
import com.mathworkbook.app.ui.dashboard.DashboardViewModel
import com.mathworkbook.app.ui.dashboard.DashboardViewModelFactory
import com.mathworkbook.app.ui.exam.ExamScreen
import com.mathworkbook.app.ui.exam.ExamViewModel
import com.mathworkbook.app.ui.exam.ExamViewModelFactory
import com.mathworkbook.app.ui.master.MasterScreen
import com.mathworkbook.app.ui.master.MasterViewModel
import com.mathworkbook.app.ui.master.MasterViewModelFactory
import com.mathworkbook.app.ui.practice.PracticeScreen
import com.mathworkbook.app.ui.practice.PracticeViewModel
import com.mathworkbook.app.ui.practice.PracticeViewModelFactory

private enum class RootTab(val label: String) {
    Dashboard("진도판"),
    Problem("문제"),
    Master("마스터")
}

private enum class ProblemMode {
    Practice,
    Exam
}

@Composable
fun MathWorkbookApp(container: AppContainer) {
    LaunchedEffect(Unit) {
        container.seedData.ensure()
    }

    var selectedTab by remember { mutableStateOf(RootTab.Dashboard) }
    var problemMode by remember { mutableStateOf(ProblemMode.Practice) }
    var isMasterMode by remember { mutableStateOf(false) }
    var menuExpanded by remember { mutableStateOf(true) }
    var showMasterLogin by remember { mutableStateOf(false) }
    var pin by remember { mutableStateOf("") }
    var pinError by remember { mutableStateOf<String?>(null) }
    var pendingPracticeWorkbookId by remember { mutableStateOf<String?>(null) }
    var pendingPracticeChapterId by remember { mutableStateOf<String?>(null) }
    var pendingPracticeProblemId by remember { mutableStateOf<String?>(null) }
    var examLaunchKey by remember { mutableIntStateOf(0) }
    var questionTextSizeSp by remember {
        mutableIntStateOf(container.appPreferences.getInt(PREF_PROBLEM_TEXT_SIZE_SP, DEFAULT_PROBLEM_TEXT_SIZE_SP))
    }
    val updateQuestionTextSizeSp: (Int) -> Unit = { value ->
        val clamped = value.coerceIn(MIN_PROBLEM_TEXT_SIZE_SP, MAX_PROBLEM_TEXT_SIZE_SP)
        questionTextSizeSp = clamped
        container.appPreferences.edit().putInt(PREF_PROBLEM_TEXT_SIZE_SP, clamped).apply()
    }

    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(modifier = Modifier.fillMaxSize()) {
                when (selectedTab) {
                    RootTab.Dashboard -> {
                        val viewModel: DashboardViewModel = viewModel(
                            factory = DashboardViewModelFactory(container)
                        )
                        DashboardScreen(
                            viewModel = viewModel,
                            isMasterMode = isMasterMode,
                            onOpenPracticeChapter = { workbookId, chapterId ->
                                pendingPracticeWorkbookId = workbookId
                                pendingPracticeChapterId = chapterId
                                pendingPracticeProblemId = null
                                problemMode = ProblemMode.Practice
                                selectedTab = RootTab.Problem
                            },
                            onOpenMasterProblem = { workbookId, chapterId, problemId ->
                                pendingPracticeWorkbookId = workbookId
                                pendingPracticeChapterId = chapterId
                                pendingPracticeProblemId = problemId
                                problemMode = ProblemMode.Practice
                                selectedTab = RootTab.Problem
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    RootTab.Problem -> {
                        when (problemMode) {
                            ProblemMode.Practice -> {
                                val viewModel: PracticeViewModel = viewModel(
                                    factory = PracticeViewModelFactory(container)
                                )
                                PracticeScreen(
                                    viewModel = viewModel,
                                    isMasterMode = isMasterMode,
                                    questionTextSizeSp = questionTextSizeSp,
                                    initialWorkbookId = pendingPracticeWorkbookId,
                                    initialChapterId = pendingPracticeChapterId,
                                    initialProblemId = pendingPracticeProblemId,
                                    onInitialChapterHandled = {
                                        pendingPracticeWorkbookId = null
                                        pendingPracticeChapterId = null
                                        pendingPracticeProblemId = null
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            ProblemMode.Exam -> {
                                val viewModel: ExamViewModel = viewModel(
                                    key = "exam-$examLaunchKey",
                                    factory = ExamViewModelFactory(container)
                                )
                                ExamScreen(
                                    viewModel = viewModel,
                                    isMasterMode = isMasterMode,
                                    questionTextSizeSp = questionTextSizeSp,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                    RootTab.Master -> {
                        val viewModel: MasterViewModel = viewModel(
                            factory = MasterViewModelFactory(container)
                        )
                        MasterScreen(
                            viewModel = viewModel,
                            questionTextSizeSp = questionTextSizeSp,
                            onChangeQuestionTextSize = updateQuestionTextSizeSp,
                            onExitMasterMode = {
                                isMasterMode = false
                                problemMode = ProblemMode.Practice
                                selectedTab = RootTab.Dashboard
                            },
                            onStartExamMode = {
                                isMasterMode = false
                                problemMode = ProblemMode.Exam
                                examLaunchKey += 1
                                selectedTab = RootTab.Problem
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                BottomMenu(
                    selectedTab = selectedTab,
                    menuExpanded = menuExpanded,
                    onToggleMenu = { menuExpanded = !menuExpanded },
                    onSelectTab = { tab ->
                        if (tab == RootTab.Master && !isMasterMode) {
                            pin = ""
                            pinError = null
                            showMasterLogin = true
                        } else {
                            selectedTab = tab
                        }
                    }
                )
            }
        }
    }

    if (showMasterLogin) {
        AlertDialog(
            onDismissRequest = { showMasterLogin = false },
            title = { Text("마스터 전환") },
            text = {
                Column {
                    Text("마스터 모드로 전환하려면 PIN을 입력하세요.")
                    OutlinedTextField(
                        value = pin,
                        onValueChange = {
                            pin = it
                            pinError = null
                        },
                        label = { Text("PIN") },
                        isError = pinError != null
                    )
                    pinError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showMasterLogin = false }) {
                    Text("취소")
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (pin == "1234") {
                            isMasterMode = true
                            selectedTab = RootTab.Master
                            showMasterLogin = false
                        } else {
                            pinError = "PIN이 맞지 않습니다."
                        }
                    }
                ) {
                    Text("전환")
                }
            }
        )
    }
}

private const val PREF_PROBLEM_TEXT_SIZE_SP = "problem_text_size_sp"
private const val DEFAULT_PROBLEM_TEXT_SIZE_SP = 24
private const val MIN_PROBLEM_TEXT_SIZE_SP = 18
private const val MAX_PROBLEM_TEXT_SIZE_SP = 36

@Composable
private fun BottomMenu(
    selectedTab: RootTab,
    menuExpanded: Boolean,
    onToggleMenu: () -> Unit,
    onSelectTab: (RootTab) -> Unit
) {
    Surface(
        tonalElevation = 3.dp,
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .heightIn(min = 44.dp)
                .padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(
                onClick = onToggleMenu,
                modifier = Modifier.widthIn(min = 92.dp)
            ) {
                Text(if (menuExpanded) "메뉴접기" else "메뉴펴기")
            }
            if (menuExpanded) {
                MenuButton(
                    label = RootTab.Dashboard.label,
                    selected = selectedTab == RootTab.Dashboard,
                    onClick = { onSelectTab(RootTab.Dashboard) },
                    modifier = Modifier.weight(1f)
                )
                MenuButton(
                    label = RootTab.Problem.label,
                    selected = selectedTab == RootTab.Problem,
                    onClick = { onSelectTab(RootTab.Problem) },
                    modifier = Modifier.weight(1f)
                )
                MenuButton(
                    label = RootTab.Master.label,
                    selected = selectedTab == RootTab.Master,
                    onClick = { onSelectTab(RootTab.Master) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun MenuButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (selected) {
        Button(onClick = onClick, modifier = modifier) {
            Text(label)
        }
    } else {
        OutlinedButton(onClick = onClick, modifier = modifier) {
            Text(label)
        }
    }
}
