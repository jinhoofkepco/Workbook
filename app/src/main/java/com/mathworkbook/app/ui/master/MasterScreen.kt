package com.mathworkbook.app.ui.master

import android.content.ActivityNotFoundException
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mathworkbook.app.core.database.AttemptInputLogEntity
import com.mathworkbook.app.core.database.PracticeAttemptEntity
import com.mathworkbook.app.core.database.ProblemEntity
import com.mathworkbook.app.core.domain.FinalStatus
import com.mathworkbook.app.ui.components.MaskableProblemImage
import com.mathworkbook.app.ui.components.SolutionVectorPreview

@Composable
fun MasterScreen(
    viewModel: MasterViewModel,
    onExitMasterMode: () -> Unit = {},
    onStartExamMode: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showSettings by remember { mutableStateOf(false) }
    var showImport by remember { mutableStateOf(false) }
    var showExamCreate by remember { mutableStateOf(false) }
    var expandedAttemptId by remember { mutableStateOf<String?>(null) }
    val zipDocumentPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri -> uri?.let(viewModel::importWorkbookZip) }
    )
    val zipContentPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
        onResult = { uri -> uri?.let(viewModel::importWorkbookZip) }
    )
    val zipMimeTypes = remember {
        arrayOf(
            "application/zip",
            "application/x-zip-compressed",
            "application/octet-stream"
        )
    }

    Surface(modifier = modifier.fillMaxSize(), color = Color(0xFFF7F8FA)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("풀이 기록", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text("한 줄 요약을 누르면 문제, 풀이내용, 답을 확인합니다.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                OutlinedButton(onClick = onExitMasterMode) {
                    Text("학생 모드")
                }
                FilledTonalButton(onClick = { showExamCreate = true }) {
                    Text("시험 생성")
                }
                FilledTonalButton(onClick = { showSettings = true }) {
                    Text("입력 제한")
                }
                Button(onClick = { showImport = true }) {
                    Text("문제집 가져오기")
                }
            }

            state.message?.let {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                    Text(it, modifier = Modifier.padding(12.dp), color = MaterialTheme.colorScheme.onSecondaryContainer)
                }
            }

            LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (state.attempts.isEmpty()) {
                    item {
                        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                            Text("아직 풀이 기록이 없습니다.", modifier = Modifier.padding(16.dp))
                        }
                    }
                }
                items(state.attempts) { attempt ->
                    val problem = state.problems.firstOrNull { it.problemId == attempt.problemId }
                    AttemptRow(
                        attempt = attempt,
                        problem = problem,
                        logs = state.inputLogsByAttempt[attempt.attemptId].orEmpty(),
                        correctAnswers = state.correctAnswersByProblem[attempt.problemId].orEmpty(),
                        expanded = expandedAttemptId == attempt.attemptId,
                        onClick = {
                            expandedAttemptId = if (expandedAttemptId == attempt.attemptId) null else attempt.attemptId
                        }
                    )
                }
            }
        }
    }

    if (showSettings) {
        SettingsDialog(
            state = state,
            onDismiss = { showSettings = false },
            onChange = viewModel::updateDefaultMaxTryCount
        )
    }

    if (showImport) {
        ImportDialog(
            onDismiss = { showImport = false },
            onPickZip = {
                showImport = false
                try {
                    zipDocumentPicker.launch(zipMimeTypes)
                } catch (_: ActivityNotFoundException) {
                    try {
                        zipContentPicker.launch("application/zip")
                    } catch (_: ActivityNotFoundException) {
                        viewModel.showMessage("ZIP 파일을 선택할 수 있는 파일 앱을 찾지 못했습니다.")
                    }
                }
            }
        )
    }

    if (showExamCreate) {
        ExamCreateDialog(
            state = state,
            onDismiss = { showExamCreate = false },
            onCreate = { workbookId, chapterId, count, randomOrder, wrongFirst ->
                viewModel.createExam(
                    workbookId = workbookId,
                    chapterId = chapterId,
                    requestedProblemCount = count,
                    randomOrder = randomOrder,
                    wrongFirst = wrongFirst,
                    onCreated = {
                        showExamCreate = false
                        onStartExamMode()
                    }
                )
            }
        )
    }
}

@Composable
private fun AttemptRow(
    attempt: PracticeAttemptEntity,
    problem: ProblemEntity?,
    logs: List<AttemptInputLogEntity>,
    correctAnswers: List<String>,
    expanded: Boolean,
    onClick: () -> Unit
) {
    var showCorrectAnswer by remember(attempt.attemptId) { mutableStateOf(false) }
    val submittedAnswer = logs
        .groupBy { it.tryNumber }
        .entries
        .joinToString(" / ") { (tryNumber, tryLogs) ->
            "${tryNumber}회 ${tryLogs.joinToString(", ") { it.submittedAnswerRaw }}"
        }
        .ifBlank { "제출 답 없음" }
    val problemName = problem?.questionText?.take(28) ?: attempt.problemId
    val location = "${attempt.workbookId} - ${attempt.chapterId} - ${problem?.orderIndex ?: attempt.problemId}"

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                Text(problemName, modifier = Modifier.weight(1.5f), maxLines = 1, fontWeight = FontWeight.SemiBold)
                Text(statusLabel(attempt.finalStatus), color = statusColor(attempt.finalStatus), fontWeight = FontWeight.Bold)
                Text("($location)", modifier = Modifier.weight(1.2f), maxLines = 1, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("힌트 ${if (attempt.hintUsed) "사용" else "미사용"}", maxLines = 1)
                Text("${attempt.elapsedSeconds}초", maxLines = 1)
                Text("답변 ${attempt.inputTryCount}회", maxLines = 1)
            }

            if (expanded) {
                HorizontalDivider()
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("문제", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(problem?.questionText ?: attempt.problemId)
                    if (!problem?.imagePath.isNullOrBlank()) {
                        MaskableProblemImage(
                            imagePath = problem?.imagePath,
                            maskOverlayJson = problem?.maskOverlayJson,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(220.dp)
                        )
                    }

                    Text("풀이내용", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    SolutionVectorPreview(path = attempt.solutionImagePath)

                    Text("답", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("학생 답: $submittedAnswer")
                    if (attempt.isCorrect == false || attempt.maxAttemptsReached) {
                        OutlinedButton(onClick = { showCorrectAnswer = !showCorrectAnswer }) {
                            Text(if (showCorrectAnswer) "정답 숨기기" else "정답보기")
                        }
                        if (showCorrectAnswer) {
                            Text("정답: ${correctAnswers.joinToString(", ")}", color = Color(0xFFB91C1C), fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsDialog(
    state: MasterUiState,
    onDismiss: () -> Unit,
    onChange: (Int) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("연습 모드 입력 제한") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("문제당 최대 답 입력 횟수입니다. 권장 범위는 1회에서 10회입니다.")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { onChange(state.maxTryCount - 1) }) {
                        Text("-")
                    }
                    Text("${state.maxTryCount}회", modifier = Modifier.padding(12.dp))
                    Button(onClick = { onChange(state.maxTryCount + 1) }) {
                        Text("+")
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("완료")
            }
        }
    )
}

@Composable
private fun ImportDialog(
    onDismiss: () -> Unit,
    onPickZip: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("외부 문제집 가져오기") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("ZIP 안에 workbook.json과 이미지 파일을 넣으면 문제집으로 등록됩니다.")
                Text("이미지 배치와 답 규칙은 docs/workbook-import-format.md 형식을 따릅니다.")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("취소")
            }
        },
        confirmButton = {
            Button(onClick = onPickZip) {
                Text("ZIP 선택")
            }
        }
    )
}

@Composable
private fun ExamCreateDialog(
    state: MasterUiState,
    onDismiss: () -> Unit,
    onCreate: (workbookId: String?, chapterId: String?, count: Int, randomOrder: Boolean, wrongFirst: Boolean) -> Unit
) {
    var selectedWorkbookId by remember(state.workbooks) { mutableStateOf(state.workbooks.firstOrNull()?.workbookId) }
    var selectedChapterId by remember(selectedWorkbookId) { mutableStateOf<String?>(null) }
    var problemCountText by remember { mutableStateOf("10") }
    var randomOrder by remember { mutableStateOf(false) }
    var wrongFirst by remember { mutableStateOf(true) }
    val availableChapters = state.chapters
        .filter { selectedWorkbookId == null || it.workbookId == selectedWorkbookId }
        .sortedBy { it.orderIndex }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("시험 자동 생성") },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item {
                    Text("책", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                }
                item {
                    FilterChip(
                        selected = selectedWorkbookId == null,
                        onClick = {
                            selectedWorkbookId = null
                            selectedChapterId = null
                        },
                        label = { Text("전체 문제집") }
                    )
                }
                items(state.workbooks) { workbook ->
                    FilterChip(
                        selected = selectedWorkbookId == workbook.workbookId,
                        onClick = {
                            selectedWorkbookId = workbook.workbookId
                            selectedChapterId = null
                        },
                        label = { Text(workbook.title, maxLines = 1) }
                    )
                }
                item {
                    Text("범위", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                }
                item {
                    FilterChip(
                        selected = selectedChapterId == null,
                        onClick = { selectedChapterId = null },
                        label = { Text("선택한 책 전체") }
                    )
                }
                items(availableChapters) { chapter ->
                    FilterChip(
                        selected = selectedChapterId == chapter.chapterId,
                        onClick = { selectedChapterId = chapter.chapterId },
                        label = { Text("${chapter.orderIndex}. ${chapter.title}", maxLines = 1) }
                    )
                }
                item {
                    OutlinedTextField(
                        value = problemCountText,
                        onValueChange = { text -> problemCountText = text.filter { it.isDigit() }.take(3) },
                        label = { Text("문제 수") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = randomOrder, onCheckedChange = { randomOrder = it })
                        Text("랜덤 순서")
                    }
                }
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = wrongFirst, onCheckedChange = { wrongFirst = it })
                        Text("틀린 문제 우선")
                    }
                }
                item {
                    Text("문제가 부족하면 가능한 문제만으로 시험을 만듭니다.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("취소")
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onCreate(
                        selectedWorkbookId,
                        selectedChapterId,
                        problemCountText.toIntOrNull() ?: 10,
                        randomOrder,
                        wrongFirst
                    )
                }
            ) {
                Text("시험 시작")
            }
        }
    )
}

private fun statusLabel(status: FinalStatus): String {
    return when (status) {
        FinalStatus.CORRECT -> "정답"
        FinalStatus.FAILED_AFTER_MAX_ATTEMPTS -> "횟수 초과"
        FinalStatus.MANUAL_REVIEW_REQUIRED -> "검토 필요"
        FinalStatus.WRONG -> "오답"
        FinalStatus.IN_PROGRESS -> "진행 중"
    }
}

private fun statusColor(status: FinalStatus): Color {
    return when (status) {
        FinalStatus.CORRECT -> Color(0xFF15803D)
        FinalStatus.FAILED_AFTER_MAX_ATTEMPTS,
        FinalStatus.WRONG -> Color(0xFFB91C1C)
        FinalStatus.MANUAL_REVIEW_REQUIRED -> Color(0xFF92400E)
        FinalStatus.IN_PROGRESS -> Color(0xFF374151)
    }
}
