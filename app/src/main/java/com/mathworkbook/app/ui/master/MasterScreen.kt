package com.mathworkbook.app.ui.master

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mathworkbook.app.core.database.AnswerRuleEntity
import com.mathworkbook.app.core.database.AttemptInputLogEntity
import com.mathworkbook.app.core.database.PracticeAttemptEntity
import com.mathworkbook.app.core.database.ProblemEntity
import com.mathworkbook.app.core.domain.FinalStatus
import com.mathworkbook.app.ui.components.ProblemWorksheetBackground
import com.mathworkbook.app.ui.components.ProblemWorksheetFooterOverlay
import com.mathworkbook.app.ui.components.SolutionVectorOverlay
import com.mathworkbook.app.ui.components.estimateWorksheetContentHeightDp
import com.mathworkbook.app.ui.components.parseProblemTeacherNotes
import com.mathworkbook.app.ui.skin.SkinAssetImage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class MasterToolAction {
    ExamCreate,
    Settings,
    Import,
    Logs
}

@Composable
fun MasterToolLayer(
    viewModel: MasterViewModel,
    activeTool: MasterToolAction?,
    questionTextSizeSp: Int = 24,
    onDismiss: () -> Unit = {},
    onChangeQuestionTextSize: (Int) -> Unit = {},
    onWorkbookImported: () -> Unit = {},
    onOpenAttempt: (PracticeAttemptEntity) -> Unit = {},
    onStartExamMode: () -> Unit = {}
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val zipDocumentContract = remember { ZipFilePickerContract(Intent.ACTION_OPEN_DOCUMENT) }
    val zipContentContract = remember { ZipFilePickerContract(Intent.ACTION_GET_CONTENT) }
    val zipDocumentPicker = rememberLauncherForActivityResult(
        contract = zipDocumentContract,
        onResult = { uri -> uri?.let { viewModel.importExternalZip(it, onWorkbookImported) } }
    )
    val zipContentPicker = rememberLauncherForActivityResult(
        contract = zipContentContract,
        onResult = { uri -> uri?.let { viewModel.importExternalZip(it, onWorkbookImported) } }
    )
    val zipMimeTypes = remember {
        listOf(
            "application/zip",
            "application/x-zip-compressed",
            "application/octet-stream"
        )
    }
    val attemptGroups = remember(state.attempts, state.problems) {
        state.attempts
            .groupBy { it.problemId }
            .map { (problemId, attempts) ->
                ProblemAttemptGroup(
                    problemId = problemId,
                    problem = state.problems.firstOrNull { it.problemId == problemId },
                    attempts = attempts.sortedByDescending { it.submittedAt ?: it.startedAt }
                )
            }
            .sortedByDescending { group -> group.attempts.firstOrNull()?.let { it.submittedAt ?: it.startedAt } ?: 0L }
    }

    when (activeTool) {
        MasterToolAction.Settings -> SettingsDialog(
            state = state,
            questionTextSizeSp = questionTextSizeSp,
            onDismiss = onDismiss,
            onChangeMaxTryCount = viewModel::updateDefaultMaxTryCount,
            onChangeQuestionTextSize = onChangeQuestionTextSize,
            onSetViewerEnabled = viewModel::setViewerEnabled,
            onSetActiveSkin = viewModel::setActiveSkin,
            onClearActiveSkin = viewModel::clearActiveSkin
        )
        MasterToolAction.Import -> ImportDialog(
            onDismiss = onDismiss,
            onPickZip = {
                onDismiss()
                launchZipPickerIfAvailable(
                    context = context,
                    action = Intent.ACTION_OPEN_DOCUMENT,
                    mimeTypes = zipMimeTypes,
                    launcher = { request -> zipDocumentPicker.launch(request) },
                    onUnavailable = {
                        launchZipPickerIfAvailable(
                            context = context,
                            action = Intent.ACTION_GET_CONTENT,
                            mimeTypes = zipMimeTypes,
                            launcher = { request -> zipContentPicker.launch(request) },
                            onUnavailable = { viewModel.showMessage(ZIP_PICKER_UNAVAILABLE_MESSAGE) }
                        )
                    }
                )
            }
        )
        MasterToolAction.ExamCreate -> ExamCreateDialog(
            state = state,
            onDismiss = onDismiss,
            onCreate = { workbookId, chapterId, count, randomOrder, wrongFirst ->
                viewModel.createExam(
                    workbookId = workbookId,
                    chapterId = chapterId,
                    requestedProblemCount = count,
                    randomOrder = randomOrder,
                    wrongFirst = wrongFirst,
                    onCreated = {
                        onDismiss()
                        onStartExamMode()
                    }
                )
            }
        )
        MasterToolAction.Logs -> MasterLogDialog(
            attemptGroups = attemptGroups,
            onDismiss = onDismiss,
            onOpenAttempt = {
                onDismiss()
                onOpenAttempt(it)
            }
        )
        null -> Unit
    }
}

@Composable
fun MasterScreen(
    viewModel: MasterViewModel,
    questionTextSizeSp: Int = 24,
    onChangeQuestionTextSize: (Int) -> Unit = {},
    onExitMasterMode: () -> Unit = {},
    onStartExamMode: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showSettings by remember { mutableStateOf(false) }
    var showImport by remember { mutableStateOf(false) }
    var showExamCreate by remember { mutableStateOf(false) }
    var expandedProblemId by remember { mutableStateOf<String?>(null) }
    val attemptGroups = remember(state.attempts, state.problems) {
        state.attempts
            .groupBy { it.problemId }
            .map { (problemId, attempts) ->
                ProblemAttemptGroup(
                    problemId = problemId,
                    problem = state.problems.firstOrNull { it.problemId == problemId },
                    attempts = attempts.sortedByDescending { it.submittedAt ?: it.startedAt }
                )
            }
            .sortedByDescending { group -> group.attempts.firstOrNull()?.let { it.submittedAt ?: it.startedAt } ?: 0L }
    }
    val context = LocalContext.current
    val zipDocumentContract = remember { ZipFilePickerContract(Intent.ACTION_OPEN_DOCUMENT) }
    val zipContentContract = remember { ZipFilePickerContract(Intent.ACTION_GET_CONTENT) }
    val zipDocumentPicker = rememberLauncherForActivityResult(
        contract = zipDocumentContract,
        onResult = { uri -> uri?.let(viewModel::importExternalZip) }
    )
    val zipContentPicker = rememberLauncherForActivityResult(
        contract = zipContentContract,
        onResult = { uri -> uri?.let(viewModel::importExternalZip) }
    )
    val zipMimeTypes = remember {
        listOf(
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
                    Text("설정")
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
                if (attemptGroups.isEmpty()) {
                    item {
                        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                            Text("아직 풀이 기록이 없습니다.", modifier = Modifier.padding(16.dp))
                        }
                    }
                }
                items(attemptGroups) { group ->
                    ProblemAttemptRow(
                        group = group,
                        inputLogsByAttempt = state.inputLogsByAttempt,
                        answerRules = state.answerRulesByProblem[group.problemId].orEmpty(),
                        expanded = expandedProblemId == group.problemId,
                        questionTextSizeSp = questionTextSizeSp,
                        onClick = {
                            expandedProblemId = if (expandedProblemId == group.problemId) null else group.problemId
                        },
                        onSaveAnswer = viewModel::updateCorrectAnswer
                    )
                }
            }
        }
    }

    if (showSettings) {
        SettingsDialog(
            state = state,
            questionTextSizeSp = questionTextSizeSp,
            onDismiss = { showSettings = false },
            onChangeMaxTryCount = viewModel::updateDefaultMaxTryCount,
            onChangeQuestionTextSize = onChangeQuestionTextSize,
            onSetViewerEnabled = viewModel::setViewerEnabled,
            onSetActiveSkin = viewModel::setActiveSkin,
            onClearActiveSkin = viewModel::clearActiveSkin
        )
    }

    if (showImport) {
        ImportDialog(
            onDismiss = { showImport = false },
            onPickZip = {
                showImport = false
                launchZipPickerIfAvailable(
                    context = context,
                    action = Intent.ACTION_OPEN_DOCUMENT,
                    mimeTypes = zipMimeTypes,
                    launcher = { request -> zipDocumentPicker.launch(request) },
                    onUnavailable = {
                        launchZipPickerIfAvailable(
                            context = context,
                            action = Intent.ACTION_GET_CONTENT,
                            mimeTypes = zipMimeTypes,
                            launcher = { request -> zipContentPicker.launch(request) },
                            onUnavailable = { viewModel.showMessage(ZIP_PICKER_UNAVAILABLE_MESSAGE) }
                        )
                    }
                )
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

private const val ZIP_PICKER_UNAVAILABLE_MESSAGE =
    "ZIP 파일을 선택할 수 있는 파일 앱을 찾지 못했습니다. 태블릿 보호자 설정에서 내 파일 또는 Files 앱 사용을 허용한 뒤 다시 시도해 주세요."

private data class ZipFilePickerRequest(
    val mimeTypes: List<String>,
    val packageName: String?
)

private fun launchZipPickerIfAvailable(
    context: Context,
    action: String,
    mimeTypes: List<String>,
    launcher: (ZipFilePickerRequest) -> Unit,
    onUnavailable: () -> Unit
) {
    val packageName = resolveZipFilePickerPackage(context, action, mimeTypes)
    if (packageName == null) {
        onUnavailable()
        return
    }
    try {
        launcher(ZipFilePickerRequest(mimeTypes, packageName))
    } catch (_: ActivityNotFoundException) {
        onUnavailable()
    }
}

private fun resolveZipFilePickerPackage(
    context: Context,
    action: String,
    mimeTypes: List<String>
): String? {
    val intent = createZipFilePickerIntent(
        action = action,
        mimeTypes = mimeTypes,
        packageName = null
    )
    val candidates = context.packageManager
        .queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)

    return candidates
        .firstOrNull { info ->
            val packageName = info.activityInfo.packageName.orEmpty()
            val activityName = info.activityInfo.name.orEmpty()
            isFilePicker(packageName, activityName)
        }
        ?.activityInfo
        ?.packageName
}

private fun isFilePicker(packageName: String, activityName: String): Boolean {
    if (isMediaPicker(packageName, activityName)) return false
    val text = "$packageName/$activityName"
    return FILE_PICKER_NAME_HINTS.any { hint -> text.contains(hint, ignoreCase = true) }
}

private fun isMediaPicker(packageName: String, activityName: String): Boolean {
    val text = "$packageName/$activityName"
    return MEDIA_PICKER_NAME_HINTS.any { hint -> text.contains(hint, ignoreCase = true) }
}

private val FILE_PICKER_NAME_HINTS = listOf(
    "documentsui",
    "myfiles",
    "filemanager",
    "files",
    "document",
    "storage",
    "explorer"
)

private val MEDIA_PICKER_NAME_HINTS = listOf(
    "photopicker",
    "photo",
    "gallery",
    "soundpicker",
    "camera"
)

private class ZipFilePickerContract(
    private val action: String
) : ActivityResultContract<ZipFilePickerRequest, Uri?>() {
    override fun createIntent(context: Context, input: ZipFilePickerRequest): Intent {
        return createZipFilePickerIntent(action, input.mimeTypes, input.packageName)
    }

    override fun parseResult(resultCode: Int, intent: Intent?): Uri? {
        return if (resultCode == Activity.RESULT_OK) intent?.data else null
    }
}

private fun createZipFilePickerIntent(
    action: String,
    mimeTypes: List<String>,
    packageName: String?
): Intent {
    return Intent(action).apply {
        addCategory(Intent.CATEGORY_OPENABLE)
        type = "*/*"
        putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes.toTypedArray())
        putExtra(Intent.EXTRA_LOCAL_ONLY, false)
        packageName?.let(::setPackage)
    }
}

private data class ProblemAttemptGroup(
    val problemId: String,
    val problem: ProblemEntity?,
    val attempts: List<PracticeAttemptEntity>
)

@Composable
private fun ProblemAttemptRow(
    group: ProblemAttemptGroup,
    inputLogsByAttempt: Map<String, List<AttemptInputLogEntity>>,
    answerRules: List<AnswerRuleEntity>,
    expanded: Boolean,
    questionTextSizeSp: Int,
    onClick: () -> Unit,
    onSaveAnswer: (AnswerRuleEntity, String) -> Unit
) {
    val latestAttempt = group.attempts.first()
    val problemName = group.problem?.questionText?.take(42) ?: group.problemId
    val location = "${latestAttempt.workbookId} - ${latestAttempt.chapterId} - ${group.problem?.orderIndex ?: group.problemId}"

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onClick),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    Text(problemName, modifier = Modifier.weight(1f), maxLines = 1, fontWeight = FontWeight.SemiBold)
                    Text("${group.attempts.size}회 풀이", fontWeight = FontWeight.Bold)
                    Text(statusLabel(latestAttempt.finalStatus), color = statusColor(latestAttempt.finalStatus), fontWeight = FontWeight.Bold)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    Text(location, modifier = Modifier.weight(1f), maxLines = 1, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("최근 ${formatTimestamp(latestAttempt.submittedAt ?: latestAttempt.startedAt)}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            if (expanded) {
                var selectedAttemptId by remember(group.problemId, group.attempts.size) {
                    mutableStateOf(latestAttempt.attemptId)
                }
                val selectedAttempt = group.attempts.firstOrNull { it.attemptId == selectedAttemptId } ?: latestAttempt

                HorizontalDivider()
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    MasterProblemPreview(
                        problem = group.problem,
                        questionTextSizeSp = questionTextSizeSp
                    )
                    ProblemTeacherMeta(group.problem)

                    Text("풀이 회차", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(group.attempts) { attempt ->
                            FilterChip(
                                selected = selectedAttempt.attemptId == attempt.attemptId,
                                onClick = { selectedAttemptId = attempt.attemptId },
                                label = { Text("${attempt.attemptNumber}회 ${formatTimestamp(attempt.submittedAt ?: attempt.startedAt)}") }
                            )
                        }
                    }

                    AttemptDetail(
                        problem = group.problem,
                        questionTextSizeSp = questionTextSizeSp,
                        attempt = selectedAttempt,
                        logs = inputLogsByAttempt[selectedAttempt.attemptId].orEmpty()
                    )

                    CorrectAnswerEditor(
                        answerRules = answerRules,
                        onSaveAnswer = onSaveAnswer
                    )
                }
            }
        }
    }
}

@Composable
private fun AttemptDetail(
    problem: ProblemEntity?,
    questionTextSizeSp: Int,
    attempt: PracticeAttemptEntity,
    logs: List<AttemptInputLogEntity>
) {
    val submittedAnswer = formatSubmittedAnswer(logs)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            Text(statusLabel(attempt.finalStatus), color = statusColor(attempt.finalStatus), fontWeight = FontWeight.Bold)
            Text("답변 ${attempt.inputTryCount}회")
            Text("${attempt.elapsedSeconds}초")
            Text("힌트 ${if (attempt.hintUsed) "사용" else "미사용"}")
        }
        Text("제출: $submittedAnswer")
        Text("문제와 학생 풀이", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        StudentAttemptWorksheetPreview(
            problem = problem,
            questionTextSizeSp = questionTextSizeSp,
            attempt = attempt,
            logs = logs
        )
    }
}

@Composable
private fun StudentAttemptWorksheetPreview(
    problem: ProblemEntity?,
    questionTextSizeSp: Int,
    attempt: PracticeAttemptEntity,
    logs: List<AttemptInputLogEntity>
) {
    val worksheetHeight = estimateWorksheetContentHeightDp(problem).dp
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(520.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .background(Color(0xFFFAFAFA))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(worksheetHeight)
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val lineGap = 36.dp.toPx()
                    var y = lineGap
                    while (y < size.height) {
                        drawLine(
                            color = Color(0xFFE5E7EB),
                            start = Offset(0f, y),
                            end = Offset(size.width, y),
                            strokeWidth = 1.2f
                        )
                        y += lineGap
                    }
                }
                ProblemWorksheetBackground(
                    problem = problem,
                    questionTextSizeSp = questionTextSizeSp,
                    modifier = Modifier.fillMaxSize()
                )
                SolutionVectorOverlay(
                    path = attempt.solutionImagePath,
                    modifier = Modifier.fillMaxSize()
                )
                ProblemWorksheetFooterOverlay(
                    problem = problem,
                    questionTextSizeSp = questionTextSizeSp,
                    modifier = Modifier.fillMaxSize()
                ) {
                    AttemptAnswerOverlay(logs)
                }
            }
        }
    }
}

@Composable
private fun AttemptAnswerOverlay(logs: List<AttemptInputLogEntity>) {
    val answerText = formatSubmittedAnswer(logs)
    if (answerText.isBlank()) return
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xF2FFFFFF))
    ) {
        Text(
            text = "학생 답: $answerText",
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            color = Color(0xFF2563EB),
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun CorrectAnswerEditor(
    answerRules: List<AnswerRuleEntity>,
    onSaveAnswer: (AnswerRuleEntity, String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("정답 수정", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        if (answerRules.isEmpty()) {
            Text("등록된 정답이 없습니다.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            answerRules.forEachIndexed { index, rule ->
                var draft by remember(rule.answerRuleId, rule.correctAnswerRaw) {
                    mutableStateOf(rule.correctAnswerRaw)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = draft,
                        onValueChange = { draft = it },
                        label = { Text(if (answerRules.size == 1) "정답" else "정답 ${index + 1}") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Button(
                        onClick = { onSaveAnswer(rule, draft) },
                        enabled = draft.trim() != rule.correctAnswerRaw
                    ) {
                        Text("저장")
                    }
                }
            }
        }
    }
}

@Composable
private fun ProblemTeacherMeta(problem: ProblemEntity?) {
    val meta = remember(problem?.imageCropRectJson) { parseProblemTeacherNotes(problem) }
    if (meta.isEmpty()) return
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFBEB))
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            meta.teacherMemo?.let {
                Text("교사용 메모: $it", color = Color(0xFF78350F))
            }
            meta.answerNote?.let {
                Text("답안 메모: $it", color = Color(0xFF78350F))
            }
            meta.expectedSummary?.let {
                Text("기준: $it", color = Color(0xFF374151), fontWeight = FontWeight.SemiBold)
            }
            meta.solutionText?.let {
                Text("교사용 풀이: $it", color = Color(0xFF374151))
            }
            meta.gradingMode?.let {
                Text("채점 방식: $it", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun MasterLogDialog(
    attemptGroups: List<ProblemAttemptGroup>,
    onDismiss: () -> Unit,
    onOpenAttempt: (PracticeAttemptEntity) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("풀이 로그") },
        text = {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(520.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (attemptGroups.isEmpty()) {
                    item {
                        Text("아직 풀이 기록이 없습니다.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                items(attemptGroups) { group ->
                    val latestAttempt = group.attempts.first()
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenAttempt(latestAttempt) },
                        colors = CardDefaults.cardColors(containerColor = Color.White)
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = group.problem?.questionText?.take(36) ?: group.problemId,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text("${group.attempts.size}회", fontWeight = FontWeight.Bold)
                                Text(
                                    statusLabel(latestAttempt.finalStatus),
                                    color = statusColor(latestAttempt.finalStatus),
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Text(
                                text = "최근 ${formatTimestamp(latestAttempt.submittedAt ?: latestAttempt.startedAt)}",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("닫기")
            }
        }
    )
}

@Composable
private fun SettingsDialog(
    state: MasterUiState,
    questionTextSizeSp: Int,
    onDismiss: () -> Unit,
    onChangeMaxTryCount: (Int) -> Unit,
    onChangeQuestionTextSize: (Int) -> Unit,
    onSetViewerEnabled: (Boolean) -> Unit,
    onSetActiveSkin: (String) -> Unit,
    onClearActiveSkin: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("마스터 설정") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("문제당 최대 답 입력 횟수입니다. 권장 범위는 1회에서 10회입니다.")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { onChangeMaxTryCount(state.maxTryCount - 1) }) {
                        Text("-")
                    }
                    Text("${state.maxTryCount}회", modifier = Modifier.padding(12.dp))
                    Button(onClick = { onChangeMaxTryCount(state.maxTryCount + 1) }) {
                        Text("+")
                    }
                }
                HorizontalDivider()
                Text("문제 글씨 크기입니다. 마스터 모드에서 문제를 확인할 때 적용됩니다.")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { onChangeQuestionTextSize(questionTextSizeSp - 2) }) {
                        Text("-")
                    }
                    Text("${questionTextSizeSp}sp", modifier = Modifier.padding(12.dp))
                    Button(onClick = { onChangeQuestionTextSize(questionTextSizeSp + 2) }) {
                        Text("+")
                    }
                }
                HorizontalDivider()
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("화면 스킨", fontWeight = FontWeight.SemiBold)
                    Text(
                        state.skinManager.activeSkin?.displayName ?: "기본 화면",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (state.skinManager.installedSkins.isNotEmpty()) {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(state.skinManager.installedSkins) { skin ->
                                FilterChip(
                                    selected = state.skinManager.activeSkin?.skinId == skin.skinId,
                                    onClick = { onSetActiveSkin(skin.skinId) },
                                    label = { Text(skin.displayName) }
                                )
                            }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = onClearActiveSkin,
                            enabled = state.skinManager.activeSkin != null
                        ) {
                            Text("기본으로")
                        }
                    }
                }
                HorizontalDivider()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = state.viewerServer.running,
                        onCheckedChange = onSetViewerEnabled
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("viewer 연결", fontWeight = FontWeight.SemiBold)
                        Text(
                            "같은 Wi-Fi에서 완료된 풀이만 핸드폰으로 확인합니다.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                state.viewerServer.url?.let { url ->
                    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFEFF6FF))) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("핸드폰에서 열 주소", fontWeight = FontWeight.SemiBold, color = Color(0xFF1D4ED8))
                            Text(url, color = Color(0xFF1D4ED8))
                        }
                    }
                }
                state.viewerServer.message?.let { message ->
                    Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
private fun MasterProblemPreview(
    problem: ProblemEntity?,
    questionTextSizeSp: Int
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("문제", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(estimateWorksheetContentHeightDp(problem).dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            ProblemWorksheetBackground(
                problem = problem,
                questionTextSizeSp = questionTextSizeSp,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
private fun ImportDialog(
    onDismiss: () -> Unit,
    onPickZip: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("외부 파일 가져오기") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SkinAssetImage(
                    assetKey = "importDropzone",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp),
                    contentScale = ContentScale.Fit,
                    alpha = 0.9f
                )
                Text("문제집 ZIP(workbook.json)과 스킨 ZIP(skin.json 또는 규격 PNG)을 자동으로 구분합니다.")
                Text("스킨 ZIP은 가져오면 바로 활성 스킨으로 적용됩니다.")
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

private fun formatSubmittedAnswer(logs: List<AttemptInputLogEntity>): String {
    return logs
        .groupBy { it.tryNumber }
        .entries
        .sortedBy { it.key }
        .joinToString(" / ") { (tryNumber, tryLogs) ->
            "${tryNumber}회 ${tryLogs.joinToString(", ") { it.submittedAnswerRaw }}"
        }
        .ifBlank { "제출 답 없음" }
}

private fun formatTimestamp(timestamp: Long): String {
    return SimpleDateFormat("MM/dd HH:mm", Locale.KOREA).format(Date(timestamp))
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
