package com.mathworkbook.app.ui.practice

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType as ImeKeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mathworkbook.app.core.database.ChapterEntity
import com.mathworkbook.app.core.database.WorkbookEntity
import com.mathworkbook.app.core.domain.FinalStatus
import com.mathworkbook.app.core.domain.AnswerFieldType
import com.mathworkbook.app.core.domain.ProblemType
import com.mathworkbook.app.ui.components.SolutionVectorPreview
import com.mathworkbook.app.ui.components.HandwritingCanvas
import com.mathworkbook.app.ui.components.MaskableProblemImage
import com.mathworkbook.app.ui.components.rememberHandwritingState
import org.json.JSONObject
import kotlin.math.min

@Composable
fun PracticeScreen(
    viewModel: PracticeViewModel,
    isMasterMode: Boolean = false,
    initialWorkbookId: String? = null,
    initialChapterId: String? = null,
    initialProblemId: String? = null,
    onInitialChapterHandled: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val handwritingState = rememberHandwritingState()

    LaunchedEffect(initialWorkbookId, initialChapterId, initialProblemId) {
        if (initialWorkbookId != null && initialChapterId != null) {
            if (initialProblemId != null) {
                viewModel.openProblem(initialWorkbookId, initialChapterId, initialProblemId)
            } else {
                viewModel.openChapter(initialWorkbookId, initialChapterId)
            }
            onInitialChapterHandled()
        }
    }

    LaunchedEffect(state.currentProblem?.problemId) {
        handwritingState.clear()
    }

    Surface(modifier = modifier.fillMaxSize(), color = Color(0xFFF7F8FA)) {
        if (state.loading) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            when {
                state.selectedWorkbook == null -> WorkbookSelectionScreen(
                    workbooks = state.workbooks,
                    onRefresh = viewModel::refreshWorkbooks,
                    onSelectWorkbook = viewModel::selectWorkbook
                )
                state.selectedChapter == null -> ChapterSelectionScreen(
                    workbook = state.selectedWorkbook,
                    chapters = state.chapters,
                    onBack = viewModel::backToWorkbooks,
                    onSelectChapter = viewModel::selectChapter
                )
                else -> ProblemSolvingScreen(
                    state = state,
                    viewModel = viewModel,
                    isMasterMode = isMasterMode,
                    solutionJson = handwritingState::toVectorJson,
                    handwriting = {
                        HandwritingCanvas(
                            state = handwritingState,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                )
            }
        }
    }
}

@Composable
private fun WorkbookSelectionScreen(
    workbooks: List<WorkbookEntity>,
    onRefresh: () -> Unit,
    onSelectWorkbook: (WorkbookEntity) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("문제집 선택", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text("풀 문제집을 고르면 세부 진도가 이어서 표시됩니다.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                OutlinedButton(onClick = onRefresh) {
                    Text("새로고침")
                }
            }
        }
        items(workbooks) { workbook ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelectWorkbook(workbook) },
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(workbook.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    if (workbook.description.isNotBlank()) {
                        Text(workbook.description, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text("학년 ${workbook.grade} · 버전 ${workbook.version}", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

@Composable
private fun ChapterSelectionScreen(
    workbook: WorkbookEntity?,
    chapters: List<ChapterEntity>,
    onBack: () -> Unit,
    onSelectChapter: (ChapterEntity) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(workbook?.title.orEmpty(), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text("세부 진도를 선택하세요.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                OutlinedButton(onClick = onBack) {
                    Text("문제집")
                }
            }
        }
        items(chapters) { chapter ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelectChapter(chapter) },
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Row(modifier = Modifier.padding(18.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("${chapter.orderIndex}", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                    Text(chapter.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun ProblemSolvingScreen(
    state: PracticeUiState,
    viewModel: PracticeViewModel,
    isMasterMode: Boolean,
    solutionJson: () -> String,
    handwriting: @Composable () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(onClick = viewModel::backToChapters) {
                Text("진도")
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = state.selectedWorkbook?.title.orEmpty(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "${state.selectedChapter?.title.orEmpty()} · ${state.currentIndex + 1}/${state.problems.size}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = if (isMasterMode) "마스터 · 연습" else "학생 · 연습",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
        }

        ProblemCard(
            state = state,
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.35f)
        )

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.65f),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("풀이 및 답", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    if (isMasterMode) {
                        SolutionVectorPreview(path = state.latestAttempt?.solutionImagePath, modifier = Modifier.fillMaxSize())
                    } else {
                        handwriting()
                    }
                }
                if (isMasterMode) {
                    MasterPracticeReviewPanel(
                        state = state,
                        viewModel = viewModel,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    AnswerArea(state = state, viewModel = viewModel, modifier = Modifier.fillMaxWidth())
                }
                state.feedback?.let {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                        Text(
                            text = it,
                            modifier = Modifier.padding(10.dp),
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(onClick = viewModel::movePrevious, modifier = Modifier.weight(1f)) {
                        Text("이전")
                    }
                    if (!isMasterMode) {
                        OutlinedButton(onClick = viewModel::showHint, modifier = Modifier.weight(1f)) {
                            Text("힌트")
                        }
                        Button(onClick = { viewModel.submit(solutionJson()) }, modifier = Modifier.weight(1f)) {
                            Text("제출")
                        }
                    }
                    Button(onClick = { viewModel.moveNext(clearFeedback = true) }, modifier = Modifier.weight(1f)) {
                        Text("다음")
                    }
                }
            }
        }
    }
}

@Composable
private fun MasterPracticeReviewPanel(
    state: PracticeUiState,
    viewModel: PracticeViewModel,
    modifier: Modifier = Modifier
) {
    val attempt = state.latestAttempt
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (attempt == null) {
            Text("아직 학생 풀이 기록이 없습니다.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            return
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            ReviewChip("결과", statusLabel(attempt.finalStatus), statusColor(attempt.finalStatus), Modifier.weight(1f))
            ReviewChip("시도", "${attempt.inputTryCount}/${attempt.maxInputTryCount}", Color(0xFF374151), Modifier.weight(1f))
            ReviewChip("시간", "${attempt.elapsedSeconds}초", Color(0xFF374151), Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            ReviewChip("제한", if (attempt.maxAttemptsReached) "도달" else "미도달", Color(0xFF374151), Modifier.weight(1f))
            ReviewChip("이동", if (attempt.movedToNextByLimit) "자동" else "수동", Color(0xFF374151), Modifier.weight(1f))
            ReviewChip("힌트", if (attempt.hintUsed) "사용" else "미사용", Color(0xFF374151), Modifier.weight(1f))
        }
        Text("학생 답", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
        Text(
            text = state.latestLogs
                .groupBy { it.tryNumber }
                .entries
                .joinToString(" / ") { (tryNumber, logs) ->
                    "${tryNumber}회 ${logs.joinToString(", ") { it.submittedAnswerRaw }}"
                }
                .ifBlank { "제출 답 없음" }
        )
        if (attempt.isCorrect == false) {
            OutlinedButton(onClick = viewModel::toggleCorrectAnswer) {
                Text(if (state.showCorrectAnswer) "정답 숨기기" else "정답보기")
            }
            if (state.showCorrectAnswer) {
                Text("정답: ${state.rules.joinToString(", ") { it.correctAnswerRaw }}", color = Color(0xFFB91C1C))
            }
        }
        Text("학생 풀이가 위 영역에 읽기 전용으로 표시됩니다.", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ReviewChip(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(modifier = modifier, colors = CardDefaults.cardColors(containerColor = Color(0xFFF3F4F6))) {
        Column(modifier = Modifier.padding(10.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, color = color, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
    }
}

@Composable
private fun ProblemCard(
    state: PracticeUiState,
    modifier: Modifier = Modifier
) {
    Card(modifier = modifier, colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 18.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("문제", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                val imageConfig = remember(state.currentProblem?.imageCropRectJson) {
                    ImageDisplayConfig.fromJson(state.currentProblem?.imageCropRectJson)
                }
                if (imageConfig.placement == "aboveText") {
                    ProblemImage(state, imageConfig)
                }
                state.currentProblem?.questionText?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.headlineSmall,
                        lineHeight = 32.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
                if (!state.currentProblem?.questionLatex.isNullOrBlank()) {
                    Text(text = state.currentProblem?.questionLatex.orEmpty(), style = MaterialTheme.typography.titleMedium)
                }
                if (imageConfig.placement != "aboveText") {
                    ProblemImage(state, imageConfig)
                }
            }
            if (state.showCorrectMark) {
                CorrectCircleOverlay()
            }
        }
    }
}

@Composable
private fun ProblemImage(state: PracticeUiState, config: ImageDisplayConfig) {
    if (state.currentProblem?.imagePath.isNullOrBlank()) return
    val alignment = when (config.align) {
        "start" -> Alignment.CenterStart
        "end" -> Alignment.CenterEnd
        else -> Alignment.Center
    }
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = alignment) {
        MaskableProblemImage(
            imagePath = state.currentProblem?.imagePath,
            maskOverlayJson = state.currentProblem?.maskOverlayJson,
            modifier = Modifier
                .fillMaxWidth(config.widthFraction)
                .height(config.heightDp.dp)
                .background(Color(0xFFF9FAFB), RoundedCornerShape(8.dp))
        )
    }
}

@Composable
private fun CorrectCircleOverlay() {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val radius = min(size.width, size.height) * 0.36f
        drawCircle(
            color = Color(0xFFE53935),
            radius = radius,
            center = Offset(size.width / 2f, size.height / 2f),
            style = Stroke(width = 18f)
        )
    }
}

@Composable
private fun AnswerArea(
    state: PracticeUiState,
    viewModel: PracticeViewModel,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("남은 기회 ${state.remainingTryCount}/${state.maxTryCount}", style = MaterialTheme.typography.labelLarge)

        if (state.currentProblem?.problemType == ProblemType.MULTIPLE_CHOICE) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                state.choices.forEach { choice ->
                    FilterChip(
                        selected = state.selectedChoiceIds.contains(choice.choiceId),
                        onClick = { viewModel.toggleChoice(choice.choiceId) },
                        label = { Text(choice.choiceText) }
                    )
                }
            }
            return
        }

        state.fields.forEach { field ->
            OutlinedTextField(
                value = state.inputByField[field.answerFieldId].orEmpty(),
                onValueChange = { viewModel.updateInput(field.answerFieldId, it) },
                label = { Text(field.label) },
                singleLine = true,
                keyboardOptions = keyboardOptionsFor(field.fieldType),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

private fun keyboardOptionsFor(fieldType: AnswerFieldType): KeyboardOptions {
    val keyboardType = when (fieldType) {
        AnswerFieldType.NUMBER,
        AnswerFieldType.MONEY,
        AnswerFieldType.ANGLE -> ImeKeyboardType.Decimal
        else -> ImeKeyboardType.Text
    }
    return KeyboardOptions(keyboardType = keyboardType)
}

private fun statusLabel(status: FinalStatus): String {
    return when (status) {
        FinalStatus.CORRECT -> "정답"
        FinalStatus.WRONG -> "오답"
        FinalStatus.FAILED_AFTER_MAX_ATTEMPTS -> "횟수 초과"
        FinalStatus.MANUAL_REVIEW_REQUIRED -> "검토 필요"
        FinalStatus.IN_PROGRESS -> "진행 중"
    }
}

private fun statusColor(status: FinalStatus): Color {
    return when (status) {
        FinalStatus.CORRECT -> Color(0xFF15803D)
        FinalStatus.WRONG,
        FinalStatus.FAILED_AFTER_MAX_ATTEMPTS -> Color(0xFFB91C1C)
        FinalStatus.MANUAL_REVIEW_REQUIRED -> Color(0xFF92400E)
        FinalStatus.IN_PROGRESS -> Color(0xFF374151)
    }
}

private data class ImageDisplayConfig(
    val heightDp: Int = 240,
    val widthFraction: Float = 1f,
    val align: String = "center",
    val placement: String = "belowText"
) {
    companion object {
        fun fromJson(json: String?): ImageDisplayConfig {
            if (json.isNullOrBlank()) return ImageDisplayConfig()
            return runCatching {
                val root = JSONObject(json)
                val display = root.optJSONObject("display") ?: root
                ImageDisplayConfig(
                    heightDp = display.optInt("heightDp", 240).coerceIn(120, 420),
                    widthFraction = display.optDouble("widthFraction", 1.0).toFloat().coerceIn(0.35f, 1f),
                    align = display.optString("align", "center"),
                    placement = display.optString("placement", "belowText")
                )
            }.getOrDefault(ImageDisplayConfig())
        }
    }
}
