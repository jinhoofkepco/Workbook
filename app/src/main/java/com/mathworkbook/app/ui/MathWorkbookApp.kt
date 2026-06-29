package com.mathworkbook.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mathworkbook.app.core.AppContainer
import com.mathworkbook.app.core.database.PracticeAttemptEntity
import com.mathworkbook.app.ui.dashboard.DashboardScreen
import com.mathworkbook.app.ui.dashboard.DashboardViewModel
import com.mathworkbook.app.ui.dashboard.DashboardViewModelFactory
import com.mathworkbook.app.ui.exam.ExamScreen
import com.mathworkbook.app.ui.exam.ExamViewModel
import com.mathworkbook.app.ui.exam.ExamViewModelFactory
import com.mathworkbook.app.ui.master.MasterToolAction
import com.mathworkbook.app.ui.master.MasterToolLayer
import com.mathworkbook.app.ui.master.MasterViewModel
import com.mathworkbook.app.ui.master.MasterViewModelFactory
import com.mathworkbook.app.ui.practice.PracticeScreen
import com.mathworkbook.app.ui.practice.PracticeViewModel
import com.mathworkbook.app.ui.practice.PracticeViewModelFactory
import com.mathworkbook.app.ui.scan.ScanWorkbookMvpScreen
import com.mathworkbook.app.ui.skin.LocalWorkbookSkin
import com.mathworkbook.app.ui.skin.SkinAssetImage

private enum class RootTab(val label: String) {
    Dashboard("진도판"),
    Problem("문제")
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
    var masterUnlockedThisRun by remember { mutableStateOf(false) }
    var showMasterLogin by remember { mutableStateOf(false) }
    var keepMasterLogin by remember { mutableStateOf(false) }
    var pin by remember { mutableStateOf("") }
    var pinError by remember { mutableStateOf<String?>(null) }
    var toolsExpanded by remember { mutableStateOf(false) }
    var activeMasterTool by remember { mutableStateOf<MasterToolAction?>(null) }
    var dashboardRefreshKey by remember { mutableIntStateOf(0) }
    var pendingPracticeWorkbookId by remember { mutableStateOf<String?>(null) }
    var pendingPracticeChapterId by remember { mutableStateOf<String?>(null) }
    var pendingPracticeProblemId by remember { mutableStateOf<String?>(null) }
    var pendingPracticeAttemptId by remember { mutableStateOf<String?>(null) }
    var currentWorkbookId by remember { mutableStateOf<String?>(null) }
    var currentChapterId by remember { mutableStateOf<String?>(null) }
    var currentProblemId by remember { mutableStateOf<String?>(null) }
    var activeScanWorkbookId by remember { mutableStateOf<String?>(null) }
    var examLaunchKey by remember { mutableIntStateOf(0) }
    var questionTextSizeSp by remember {
        mutableIntStateOf(container.appPreferences.getInt(PREF_PROBLEM_TEXT_SIZE_SP, DEFAULT_PROBLEM_TEXT_SIZE_SP))
    }
    val updateQuestionTextSizeSp: (Int) -> Unit = { value ->
        val clamped = value.coerceIn(MIN_PROBLEM_TEXT_SIZE_SP, MAX_PROBLEM_TEXT_SIZE_SP)
        questionTextSizeSp = clamped
        container.appPreferences.edit().putInt(PREF_PROBLEM_TEXT_SIZE_SP, clamped).apply()
    }

    fun openAttempt(attempt: PracticeAttemptEntity) {
        pendingPracticeWorkbookId = attempt.workbookId
        pendingPracticeChapterId = attempt.chapterId
        pendingPracticeProblemId = attempt.problemId
        pendingPracticeAttemptId = attempt.attemptId
        activeScanWorkbookId = null
        problemMode = ProblemMode.Practice
        selectedTab = RootTab.Problem
        activeMasterTool = null
        toolsExpanded = false
    }

    fun toggleMasterMode() {
        if (isMasterMode) {
            isMasterMode = false
            toolsExpanded = false
        } else if (masterUnlockedThisRun) {
            isMasterMode = true
        } else {
            pin = ""
            pinError = null
            keepMasterLogin = false
            showMasterLogin = true
            toolsExpanded = false
        }
    }

    val skinState by container.skinManager.state.collectAsStateWithLifecycle()

    MaterialTheme {
        CompositionLocalProvider(LocalWorkbookSkin provides skinState.activeSkin) {
        val masterViewModel: MasterViewModel = viewModel(factory = MasterViewModelFactory(container))
        Box(modifier = Modifier.fillMaxSize()) {
            Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                if (selectedTab == RootTab.Problem && activeScanWorkbookId != null) {
                    ScanWorkbookMvpScreen(
                        container = container,
                        workbookId = activeScanWorkbookId.orEmpty(),
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Column(modifier = Modifier.fillMaxSize()) {
                        when (selectedTab) {
                            RootTab.Dashboard -> {
                                val viewModel: DashboardViewModel = viewModel(
                                    factory = DashboardViewModelFactory(container)
                                )
                                DashboardScreen(
                                    viewModel = viewModel,
                                    isMasterMode = isMasterMode,
                                    refreshKey = dashboardRefreshKey,
                                    focusedWorkbookId = currentWorkbookId,
                                    focusedChapterId = currentChapterId,
                                    focusedProblemId = currentProblemId,
                                    onOpenPracticeChapter = { workbookId, chapterId ->
                                        pendingPracticeWorkbookId = workbookId
                                        pendingPracticeChapterId = chapterId
                                        pendingPracticeProblemId = null
                                        pendingPracticeAttemptId = null
                                        activeScanWorkbookId = null
                                        problemMode = ProblemMode.Practice
                                        selectedTab = RootTab.Problem
                                    },
                                    onOpenMasterProblem = { workbookId, chapterId, problemId ->
                                        pendingPracticeWorkbookId = workbookId
                                        pendingPracticeChapterId = chapterId
                                        pendingPracticeProblemId = problemId
                                        pendingPracticeAttemptId = null
                                        activeScanWorkbookId = null
                                        problemMode = ProblemMode.Practice
                                        selectedTab = RootTab.Problem
                                    },
                                    onOpenScanWorkbook = { workbookId ->
                                        pendingPracticeWorkbookId = null
                                        pendingPracticeChapterId = null
                                        pendingPracticeProblemId = null
                                        pendingPracticeAttemptId = null
                                        activeScanWorkbookId = workbookId
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
                                            initialAttemptId = pendingPracticeAttemptId,
                                            onProblemLocationChanged = { workbookId, chapterId, problemId ->
                                                currentWorkbookId = workbookId
                                                currentChapterId = chapterId
                                                currentProblemId = problemId
                                            },
                                            onOpenProgress = {
                                                selectedTab = RootTab.Dashboard
                                                activeMasterTool = null
                                                toolsExpanded = false
                                            },
                                            onInitialChapterHandled = {
                                                pendingPracticeWorkbookId = null
                                                pendingPracticeChapterId = null
                                                pendingPracticeProblemId = null
                                                pendingPracticeAttemptId = null
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
                        }

                        BottomMenu(
                            isMasterMode = isMasterMode,
                            toolsExpanded = toolsExpanded,
                            onToggleTools = {
                                toolsExpanded = !toolsExpanded
                            },
                            onSelectTool = { tool ->
                                activeMasterTool = tool
                                toolsExpanded = false
                            },
                            onToggleMasterMode = ::toggleMasterMode
                        )
                    }
                }
            }

            MasterToolLayer(
                viewModel = masterViewModel,
                activeTool = activeMasterTool,
                questionTextSizeSp = questionTextSizeSp,
                onDismiss = { activeMasterTool = null },
                onChangeQuestionTextSize = updateQuestionTextSizeSp,
                onWorkbookImported = { dashboardRefreshKey += 1 },
                onOpenAttempt = ::openAttempt,
                onStartExamMode = {
                    isMasterMode = false
                    activeScanWorkbookId = null
                    problemMode = ProblemMode.Exam
                    examLaunchKey += 1
                    selectedTab = RootTab.Problem
                    activeMasterTool = null
                }
            )
        }
        }
    }

    if (showMasterLogin) {
        AlertDialog(
            onDismissRequest = { showMasterLogin = false },
            title = { Text("마스터 모드") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("PIN을 입력하면 마스터 모드로 전환합니다.")
                    OutlinedTextField(
                        value = pin,
                        onValueChange = {
                            pin = it
                            pinError = null
                        },
                        label = { Text("PIN") },
                        isError = pinError != null,
                        singleLine = true
                    )
                    pinError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = keepMasterLogin, onCheckedChange = { keepMasterLogin = it })
                        Text("프로그램 종료 전까지 로그인 유지")
                    }
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
                            if (keepMasterLogin) masterUnlockedThisRun = true
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
    isMasterMode: Boolean,
    toolsExpanded: Boolean,
    onToggleTools: () -> Unit,
    onSelectTool: (MasterToolAction) -> Unit,
    onToggleMasterMode: () -> Unit
) {
    Surface(
        tonalElevation = 3.dp,
        color = Color.Transparent
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .heightIn(min = 44.dp)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Spacer(modifier = Modifier.weight(1f))
                if (isMasterMode) {
                    if (toolsExpanded) {
                        RoundToolButton("시험") { onSelectTool(MasterToolAction.ExamCreate) }
                        RoundToolButton("설정") { onSelectTool(MasterToolAction.Settings) }
                        RoundToolButton("ZIP") { onSelectTool(MasterToolAction.Import) }
                        RoundToolButton("로그") { onSelectTool(MasterToolAction.Logs) }
                    }
                    RoundToolButton(if (toolsExpanded) "닫기" else "도구", selected = toolsExpanded, onClick = onToggleTools)
                }
                RoundToolButton(
                    label = "마",
                    selected = isMasterMode,
                    badgeText = if (isMasterMode) "on" else null,
                    useSkinFrame = true,
                    onClick = onToggleMasterMode
                )
            }
        }
    }
}

@Composable
private fun RoundToolButton(
    label: String,
    selected: Boolean = false,
    badgeText: String? = null,
    useSkinFrame: Boolean = false,
    onClick: () -> Unit
) {
    val skin = LocalWorkbookSkin.current
    val preferredSkinKey = when {
        !useSkinFrame -> null
        selected && skin?.assetPath("masterButtonActive") != null -> "masterButtonActive"
        !selected && skin?.assetPath("masterButtonIdle") != null -> "masterButtonIdle"
        else -> null
    }
    Box(modifier = Modifier.size(48.dp)) {
        if (preferredSkinKey != null) {
            SkinAssetImage(
                assetKey = preferredSkinKey,
                modifier = Modifier.matchParentSize(),
                contentScale = ContentScale.FillBounds
            )
        }
        OutlinedButton(
            onClick = onClick,
            modifier = Modifier.size(48.dp),
            shape = CircleShape,
            border = BorderStroke(1.5.dp, if (selected) MaterialTheme.colorScheme.primary else Color(0xFF7C7585)),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
        ) {
            if (preferredSkinKey == null) {
                Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1)
            }
        }
        if (preferredSkinKey == null) badgeText?.let {
            Surface(
                modifier = Modifier.align(Alignment.TopEnd),
                shape = CircleShape,
                color = Color(0xFF2563EB)
            ) {
                Text(
                    text = it,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
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
