package com.mathworkbook.app.ui.practice

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.KeyboardType as ImeKeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mathworkbook.app.core.database.AnswerFieldEntity
import com.mathworkbook.app.core.database.AttemptInputLogEntity
import com.mathworkbook.app.core.database.ChapterEntity
import com.mathworkbook.app.core.database.PracticeAttemptEntity
import com.mathworkbook.app.core.database.WorkbookEntity
import com.mathworkbook.app.core.domain.FinalStatus
import com.mathworkbook.app.core.domain.AnswerFieldType
import com.mathworkbook.app.core.domain.ProblemType
import com.mathworkbook.app.ui.components.SolutionVectorPreview
import com.mathworkbook.app.ui.components.SolutionVectorOverlay
import com.mathworkbook.app.ui.components.HandwritingCanvas
import com.mathworkbook.app.ui.components.ProblemWorksheetBackground
import com.mathworkbook.app.ui.components.ProblemWorksheetFooterOverlay
import com.mathworkbook.app.ui.components.WorksheetImageAdjustmentMode
import com.mathworkbook.app.ui.components.WorksheetImageTransform
import com.mathworkbook.app.ui.components.estimateWorksheetContentHeightDp
import com.mathworkbook.app.ui.components.parseWorksheetImageTransform
import com.mathworkbook.app.ui.components.parseProblemTeacherNotes
import com.mathworkbook.app.ui.components.rememberHandwritingState
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.min

private enum class AnswerInputMode {
    WorksheetInline,
    FixedBottomCustomKeypad
}

// Switch this to FixedBottomCustomKeypad to restore the previous bottom answer area.
private val answerInputMode = AnswerInputMode.WorksheetInline

@Composable
fun PracticeScreen(
    viewModel: PracticeViewModel,
    isMasterMode: Boolean = false,
    questionTextSizeSp: Int = 24,
    initialWorkbookId: String? = null,
    initialChapterId: String? = null,
    initialProblemId: String? = null,
    initialAttemptId: String? = null,
    onProblemLocationChanged: (workbookId: String, chapterId: String, problemId: String) -> Unit = { _, _, _ -> },
    onInitialChapterHandled: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val handwritingState = rememberHandwritingState()
    val focusManager = LocalFocusManager.current
    var imageAdjustmentMode by remember { mutableStateOf(WorksheetImageAdjustmentMode.None) }
    var imageTransformDraft by remember { mutableStateOf(WorksheetImageTransform()) }

    LaunchedEffect(isMasterMode, initialWorkbookId, initialChapterId, initialProblemId, initialAttemptId) {
        viewModel.setMasterMode(
            enabled = isMasterMode,
            reloadCurrent = initialWorkbookId == null || initialChapterId == null
        )
        if (initialWorkbookId != null && initialChapterId != null) {
            if (initialProblemId != null) {
                viewModel.openProblem(initialWorkbookId, initialChapterId, initialProblemId, initialAttemptId)
            } else {
                viewModel.openChapter(initialWorkbookId, initialChapterId)
            }
            onInitialChapterHandled()
        }
    }

    LaunchedEffect(state.currentProblem?.problemId) {
        state.currentProblem?.let { problem ->
            onProblemLocationChanged(problem.workbookId, problem.chapterId, problem.problemId)
        }
    }

    LaunchedEffect(isMasterMode, state.currentProblem?.problemId, state.masterNoteVectorJson) {
        if (isMasterMode) {
            handwritingState.loadFromVectorJson(state.masterNoteVectorJson)
        } else {
            handwritingState.clear()
        }
    }

    LaunchedEffect(state.currentProblem?.problemId, state.currentProblem?.imageCropRectJson) {
        imageAdjustmentMode = WorksheetImageAdjustmentMode.None
        imageTransformDraft = parseWorksheetImageTransform(state.currentProblem?.imageCropRectJson)
    }

    LaunchedEffect(state.feedback) {
        if (state.feedback != null) {
            delay(3_000L)
            viewModel.clearFeedback()
        }
    }

    Surface(modifier = modifier.fillMaxSize().statusBarsPadding(), color = Color(0xFFF7F8FA)) {
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
                    questionTextSizeSp = questionTextSizeSp,
                    solutionJson = handwritingState::toVectorJson,
                    imageAdjustmentMode = imageAdjustmentMode,
                    onStartImageAdjust = {
                        imageTransformDraft = parseWorksheetImageTransform(state.currentProblem?.imageCropRectJson)
                        imageAdjustmentMode = WorksheetImageAdjustmentMode.Image
                    },
                    onStartFrameAdjust = {
                        imageTransformDraft = parseWorksheetImageTransform(state.currentProblem?.imageCropRectJson)
                        imageAdjustmentMode = WorksheetImageAdjustmentMode.Frame
                    },
                    onConfirmImageAdjust = {
                        viewModel.updateCurrentProblemImageTransform(
                            scale = imageTransformDraft.scale,
                            offsetX = imageTransformDraft.offsetX,
                            offsetY = imageTransformDraft.offsetY,
                            heightDp = imageTransformDraft.heightDp
                        )
                        imageAdjustmentMode = WorksheetImageAdjustmentMode.None
                    },
                    handwriting = {
                        val worksheetContentHeight = estimateWorksheetContentHeightDp(state.currentProblem).dp
                        val selectedStudentAttempt = state.selectedStudentAttempt()
                        HandwritingCanvas(
                            state = handwritingState,
                            modifier = Modifier.fillMaxSize(),
                            contentHeight = worksheetContentHeight,
                            inputOverlayEnabled = imageAdjustmentMode == WorksheetImageAdjustmentMode.None,
                            toolbarLeadingContent = {
                                ToolbarNavButton(
                                    text = "‹",
                                    direction = NavDirection.Previous,
                                    onClick = {
                                        focusManager.clearFocus(force = true)
                                        viewModel.movePrevious()
                                    }
                                )
                                OutlinedButton(
                                    onClick = viewModel::showHint,
                                    modifier = Modifier
                                        .size(30.dp)
                                        .defaultMinSize(minWidth = 0.dp, minHeight = 0.dp),
                                    shape = CircleShape,
                                    contentPadding = PaddingValues(0.dp)
                                ) {
                                    Text("H", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                }
                            },
                            toolbarCenterContent = {
                                WorksheetLocationLabel(
                                    workbookTitle = state.selectedWorkbook?.title.orEmpty(),
                                    chapterTitle = state.selectedChapter?.title.orEmpty(),
                                    position = "${state.currentIndex + 1}/${state.problems.size}"
                                )
                            },
                            toolbarTrailingContent = {
                                ToolbarNavButton(
                                    text = "›",
                                    direction = NavDirection.Next,
                                    onClick = {
                                        focusManager.clearFocus(force = true)
                                        viewModel.moveNext(clearFeedback = true)
                                    }
                                )
                            },
                            backgroundContent = {
                                Box(modifier = Modifier.fillMaxSize()) {
                                    ProblemWorksheetBackground(
                                        problem = state.currentProblem,
                                        questionTextSizeSp = questionTextSizeSp,
                                        onImageBoundsChanged = handwritingState::updateImageBounds,
                                        imageAdjustmentMode = if (isMasterMode) imageAdjustmentMode else WorksheetImageAdjustmentMode.None,
                                        imageTransformOverride = if (isMasterMode && imageAdjustmentMode != WorksheetImageAdjustmentMode.None) imageTransformDraft else null,
                                        onImageTransformChanged = { imageTransformDraft = it }
                                    ) {
                                        when {
                                            isMasterMode && selectedStudentAttempt != null -> Unit
                                            isMasterMode -> {
                                                MasterCorrectSolutionFooter(state)
                                            }
                                            answerInputMode == AnswerInputMode.FixedBottomCustomKeypad -> {
                                                StudentAnswerStamp(state)
                                            }
                                        }
                                    }
                                    if (isMasterMode && selectedStudentAttempt != null) {
                                        selectedStudentAttempt.solutionImagePath?.let { path ->
                                            SolutionVectorOverlay(
                                                path = path,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        }
                                    }
                                }
                            },
                            foregroundContent = {
                                if (isMasterMode && selectedStudentAttempt != null) {
                                    ProblemWorksheetFooterOverlay(
                                        problem = state.currentProblem,
                                        questionTextSizeSp = questionTextSizeSp,
                                        modifier = Modifier.fillMaxSize()
                                    ) {
                                        SubmittedAnswerHistory(
                                            logs = state.logsByAttempt[selectedStudentAttempt.attemptId].orEmpty(),
                                            fields = state.fields,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                } else if (!isMasterMode && answerInputMode == AnswerInputMode.WorksheetInline) {
                                    ProblemWorksheetFooterOverlay(
                                        problem = state.currentProblem,
                                        questionTextSizeSp = questionTextSizeSp,
                                        modifier = Modifier.fillMaxSize()
                                    ) {
                                        AnswerArea(
                                            state = state,
                                            viewModel = viewModel,
                                            onSubmit = { viewModel.submit(handwritingState.toVectorJson()) },
                                            showCustomKeypad = false,
                                            useSystemKeyboard = true,
                                            showSubmittedAnswers = true,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                }
                            }
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
    onSelectWorkbook: (WorkbookEntity) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("문제집 선택", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text("풀 문제집을 고르면 세부 진도가 이어서 표시됩니다.", color = MaterialTheme.colorScheme.onSurfaceVariant)
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
    questionTextSizeSp: Int,
    solutionJson: () -> String,
    imageAdjustmentMode: WorksheetImageAdjustmentMode,
    onStartImageAdjust: () -> Unit,
    onStartFrameAdjust: () -> Unit,
    onConfirmImageAdjust: () -> Unit,
    handwriting: @Composable () -> Unit
) {
    val selectedStudentAttempt = state.selectedStudentAttempt()
    val focusManager = LocalFocusManager.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(4.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                    handwriting()
                    if (state.showCorrectMark && !isMasterMode) {
                        CorrectCircleOverlay()
                    }
                    if (isMasterMode) {
                        MasterAttemptButtons(
                            attempts = state.attemptsForProblem,
                            selectedAttemptId = state.selectedStudentAttemptId,
                            onToggle = viewModel::toggleStudentAttempt,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(top = 58.dp, end = 18.dp)
                        )
                        selectedStudentAttempt?.let { attempt ->
                            MasterAttemptInfoOverlay(
                                attempt = attempt,
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .padding(start = 12.dp, top = 42.dp)
                            )
                        }
                    }
                }
                if (isMasterMode) {
                    if (selectedStudentAttempt == null) {
                        MasterAnswerArea(
                            state = state,
                            viewModel = viewModel,
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        MasterAttemptReviewPanel(
                            attempt = selectedStudentAttempt,
                            onReview = viewModel::reviewStudentAttempt,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                } else if (answerInputMode == AnswerInputMode.FixedBottomCustomKeypad) {
                    AnswerArea(
                        state = state,
                        viewModel = viewModel,
                        onSubmit = { viewModel.submit(solutionJson()) },
                        showCustomKeypad = true,
                        useSystemKeyboard = false,
                        showSubmittedAnswers = false,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                }
                state.feedback?.let { message ->
                    PracticeFeedbackOverlay(
                        message = message,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(
                                start = 18.dp,
                                end = 18.dp,
                                bottom = if (isMasterMode) 72.dp else 112.dp
                            )
                    )
                }
            }
        }
        if (isMasterMode) {
            MasterBottomActionBar(
                selectedStudentAttempt = selectedStudentAttempt,
                imageAdjustmentMode = imageAdjustmentMode,
                onSaveCorrectAnswers = viewModel::saveCorrectAnswersFromCurrentInput,
                onStartImageAdjust = onStartImageAdjust,
                onStartFrameAdjust = onStartFrameAdjust,
                onConfirmImageAdjust = onConfirmImageAdjust,
                onMergeIntoImage = { viewModel.mergeMasterDrawingIntoProblemImage(solutionJson()) },
                onSaveNote = { viewModel.saveMasterNote(solutionJson()) },
                onDeleteAttempt = { selectedStudentAttempt?.let { viewModel.deleteStudentAttempt(it.attemptId) } },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

private enum class NavDirection {
    Previous,
    Next
}

private val PreviousNavShape = GenericShape { size, _ ->
    moveTo(0f, size.height / 2f)
    lineTo(size.width * 0.20f, 0f)
    lineTo(size.width, 0f)
    lineTo(size.width, size.height)
    lineTo(size.width * 0.20f, size.height)
    close()
}

private val NextNavShape = GenericShape { size, _ ->
    moveTo(0f, 0f)
    lineTo(size.width * 0.80f, 0f)
    lineTo(size.width, size.height / 2f)
    lineTo(size.width * 0.80f, size.height)
    lineTo(0f, size.height)
    close()
}

@Composable
private fun WorksheetLocationLabel(
    workbookTitle: String,
    chapterTitle: String,
    position: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xEFFFFFFF)),
        shape = RoundedCornerShape(10.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = workbookTitle.ifBlank { "문제집" },
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${chapterTitle.ifBlank { "단원" }} · $position",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ToolbarNavButton(
    text: String,
    direction: NavDirection,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier
            .size(width = 30.dp, height = 28.dp)
            .defaultMinSize(minWidth = 0.dp, minHeight = 0.dp),
        shape = if (direction == NavDirection.Previous) PreviousNavShape else NextNavShape,
        border = BorderStroke(1.4.dp, Color(0xFF2563EB)),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = Color(0xF2FFFFFF),
            contentColor = Color(0xFF2563EB)
        ),
        contentPadding = PaddingValues(0.dp)
    ) {
        Text(text, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun PracticeFeedbackOverlay(
    message: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = Color(0xEE111827)),
        shape = RoundedCornerShape(8.dp)
    ) {
        Text(
            text = message,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
            color = Color.White,
            fontWeight = FontWeight.SemiBold,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun StudentAnswerStamp(state: PracticeUiState) {
    val answerText = formatTypedAnswer(state)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        if (answerText.isNotBlank()) {
            Card(
                border = BorderStroke(1.5.dp, Color(0xFF2563EB)),
                colors = CardDefaults.cardColors(containerColor = Color(0xF2FFFFFF)),
                shape = RoundedCornerShape(6.dp)
            ) {
                Text(
                    text = "답: $answerText",
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                    color = Color(0xFF2563EB),
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }
    }
}

@Composable
private fun SubmittedAnswerHistory(
    logs: List<AttemptInputLogEntity>,
    fields: List<AnswerFieldEntity>,
    currentPreview: String = "",
    modifier: Modifier = Modifier
) {
    val submittedLines = submittedAnswerLines(logs, fields)
    val preview = currentPreview
        .trim()
        .takeIf { it.isNotBlank() && it != submittedLines.lastOrNull()?.text }
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        submittedLines.forEach { line ->
            SubmittedAnswerChip(text = line.text, crossedOut = !line.isCorrect)
        }
        preview?.let {
            SubmittedAnswerChip(text = it, crossedOut = false)
        }
    }
}

@Composable
private fun SubmittedAnswerChip(
    text: String,
    crossedOut: Boolean,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        border = BorderStroke(1.5.dp, Color(0xFF2563EB)),
        colors = CardDefaults.cardColors(containerColor = Color(0xF2FFFFFF)),
        shape = RoundedCornerShape(6.dp)
    ) {
        Text(
            text = "답 $text",
            modifier = Modifier
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .drawWithContent {
                    drawContent()
                    if (crossedOut) {
                        val y = size.height / 2f
                        drawLine(
                            color = Color(0xFFE53935),
                            start = Offset(0f, y),
                            end = Offset(size.width, y),
                            strokeWidth = 4.dp.toPx()
                        )
                    }
                },
            color = Color(0xFF2563EB),
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.titleMedium
        )
    }
}

private data class SubmittedAnswerLine(
    val text: String,
    val isCorrect: Boolean
)

@Composable
private fun StudentAttemptProblemView(
    state: PracticeUiState,
    questionTextSizeSp: Int,
    attempt: PracticeAttemptEntity,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .background(Color(0xFFFAFAFA), RoundedCornerShape(8.dp))
            .verticalScroll(rememberScrollState())
    ) {
        val worksheetContentHeight = estimateWorksheetContentHeightDp(state.currentProblem).dp
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(worksheetContentHeight)
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
                problem = state.currentProblem,
                questionTextSizeSp = questionTextSizeSp,
                modifier = Modifier.fillMaxSize()
            )
            SolutionVectorOverlay(
                path = attempt.solutionImagePath,
                modifier = Modifier.fillMaxSize()
            )
            ProblemWorksheetFooterOverlay(
                problem = state.currentProblem,
                questionTextSizeSp = questionTextSizeSp,
                modifier = Modifier.fillMaxSize()
            ) {
                SubmittedAnswerHistory(
                    logs = state.logsByAttempt[attempt.attemptId].orEmpty(),
                    fields = state.fields,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun MasterBottomActionBar(
    selectedStudentAttempt: PracticeAttemptEntity?,
    imageAdjustmentMode: WorksheetImageAdjustmentMode,
    onSaveCorrectAnswers: () -> Unit,
    onStartImageAdjust: () -> Unit,
    onStartFrameAdjust: () -> Unit,
    onConfirmImageAdjust: () -> Unit,
    onMergeIntoImage: () -> Unit,
    onSaveNote: () -> Unit,
    onDeleteAttempt: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    Surface(
        modifier = modifier,
        color = Color(0xF7FFFFFF),
        shape = RoundedCornerShape(10.dp),
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .horizontalScroll(scrollState)
                .padding(horizontal = 6.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            MasterActionGroup {
                MasterActionButton("정답저장", primary = true, enabled = selectedStudentAttempt == null, onClick = onSaveCorrectAnswers)
                MasterActionButton("노트저장", primary = true, onClick = onSaveNote)
            }
            MasterActionGroup {
                MasterActionButton("그림조정", enabled = selectedStudentAttempt == null, onClick = onStartImageAdjust)
                MasterActionButton("테두리조정", enabled = selectedStudentAttempt == null, onClick = onStartFrameAdjust)
                MasterActionButton(
                    "조정확정",
                    enabled = selectedStudentAttempt == null && imageAdjustmentMode != WorksheetImageAdjustmentMode.None,
                    onClick = onConfirmImageAdjust
                )
                MasterActionButton("사진합쳐저장", enabled = selectedStudentAttempt == null, onClick = onMergeIntoImage)
            }
            if (selectedStudentAttempt != null) {
                MasterActionGroup {
                    MasterActionButton("풀이삭제", onClick = onDeleteAttempt)
                }
            }
        }
    }
}

@Composable
private fun MasterActionGroup(content: @Composable RowScope.() -> Unit) {
    Surface(
        color = Color(0xFFF4F7FB),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 3.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = content
        )
    }
}

@Composable
private fun MasterActionButton(
    label: String,
    primary: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val modifier = Modifier.height(30.dp)
    val contentPadding = PaddingValues(horizontal = 9.dp, vertical = 0.dp)
    if (primary) {
        Button(
            onClick = onClick,
            enabled = enabled,
            modifier = modifier,
            contentPadding = contentPadding
        ) {
            Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1)
        }
    } else {
        OutlinedButton(
            onClick = onClick,
            enabled = enabled,
            modifier = modifier,
            contentPadding = contentPadding
        ) {
            Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1)
        }
    }
}

@Composable
private fun MasterAnswerArea(
    state: PracticeUiState,
    viewModel: PracticeViewModel,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("정답", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
        if (state.currentProblem?.problemType == ProblemType.MULTIPLE_CHOICE) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                state.choices.forEach { choice ->
                    FilterChip(
                        selected = state.selectedChoiceIds.contains(choice.choiceId),
                        onClick = { viewModel.toggleChoice(choice.choiceId) },
                        label = { Text(choice.choiceText) }
                    )
                }
            }
        } else {
            state.fields.forEach { field ->
                val disabled = field.isDisabledForInput()
                val fieldValue = if (disabled) {
                    field.disabledDisplayValue()
                } else {
                    state.inputByField[field.answerFieldId].orEmpty()
                }
                if (!disabled) {
                    InlineChoiceSelector(
                        options = inlineChoiceOptionsFor(field, state.rules),
                        currentValue = state.inputByField[field.answerFieldId].orEmpty(),
                        onToggle = { option ->
                            viewModel.updateInput(
                                field.answerFieldId,
                                toggleInlineChoiceAnswer(
                                    currentValue = state.inputByField[field.answerFieldId].orEmpty(),
                                    options = inlineChoiceOptionsFor(field, state.rules),
                                    option = option
                                )
                            )
                        }
                    )
                }
                OutlinedTextField(
                    value = fieldValue,
                    onValueChange = { if (!disabled) viewModel.updateInput(field.answerFieldId, it) },
                    enabled = !disabled,
                    label = { Text(field.label) },
                    singleLine = true,
                    keyboardOptions = keyboardOptionsFor(field.fieldType),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun MasterAttemptReviewPanel(
    attempt: PracticeAttemptEntity,
    onReview: (attemptId: String, isCorrect: Boolean, note: String) -> Unit,
    modifier: Modifier = Modifier
) {
    var reviewNote by remember(attempt.attemptId, attempt.reviewerComment) {
        mutableStateOf(attempt.reviewerComment.orEmpty())
    }
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFBEB)),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("마스터 채점", fontWeight = FontWeight.SemiBold)
                Text(
                    text = statusLabel(attempt.finalStatus),
                    color = statusColor(attempt.finalStatus),
                    fontWeight = FontWeight.SemiBold
                )
            }
            OutlinedTextField(
                value = reviewNote,
                onValueChange = { reviewNote = it },
                label = { Text("채점 노트") },
                minLines = 2,
                maxLines = 4,
                modifier = Modifier.fillMaxWidth()
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
            ) {
                OutlinedButton(
                    onClick = { onReview(attempt.attemptId, false, reviewNote) },
                    modifier = Modifier.height(36.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                ) {
                    Text("오답 처리")
                }
                Button(
                    onClick = { onReview(attempt.attemptId, true, reviewNote) },
                    modifier = Modifier.height(36.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                ) {
                    Text("정답 처리")
                }
            }
        }
    }
}

@Composable
private fun StudentSolutionPanel(
    attempt: PracticeAttemptEntity,
    logs: List<AttemptInputLogEntity>
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("${attempt.attemptNumber}회 학생 풀이", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            ReviewChip("결과", statusLabel(attempt.finalStatus), statusColor(attempt.finalStatus), Modifier.weight(1f))
            ReviewChip("날짜", formatAttemptDate(attempt.submittedAt ?: attempt.startedAt), Color(0xFF374151), Modifier.weight(1f))
            ReviewChip("시도", "${attempt.inputTryCount}/${attempt.maxInputTryCount}", Color(0xFF374151), Modifier.weight(1f))
        }
        Text("학생 답: ${formatSubmittedAnswer(logs)}")
        SolutionVectorPreview(path = attempt.solutionImagePath, height = 240.dp)
    }
}

@Composable
private fun MasterAttemptButtons(
    attempts: List<PracticeAttemptEntity>,
    selectedAttemptId: String?,
    onToggle: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val visibleAttempts = attempts.sortedBy { it.attemptNumber }.take(4)
    if (visibleAttempts.isEmpty()) return
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        visibleAttempts.forEach { attempt ->
            FilterChip(
                selected = selectedAttemptId == attempt.attemptId,
                onClick = { onToggle(attempt.attemptId) },
                label = { Text("${attempt.attemptNumber}") }
            )
        }
    }
}

@Composable
private fun MasterAttemptInfoOverlay(
    attempt: PracticeAttemptEntity,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = Color(0x88FFFFFF))
    ) {
        Text(
            text = "${attempt.attemptNumber}회 · ${statusLabel(attempt.finalStatus)} · ${formatAttemptDate(attempt.submittedAt ?: attempt.startedAt)}",
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
            color = statusColor(attempt.finalStatus).copy(alpha = 0.82f),
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun MasterCorrectSolutionFooter(state: PracticeUiState) {
    val answerText = formatCurrentAnswer(state)
    val notes = parseProblemTeacherNotes(state.currentProblem)
    val hintText = state.currentProblem?.hintText.orEmpty()
    if (answerText.isBlank() && hintText.isBlank() && notes.isEmpty()) return
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 32.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFBEB))
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (answerText.isNotBlank()) {
                Text("정답: $answerText", color = Color(0xFFB91C1C), fontWeight = FontWeight.SemiBold)
            }
            notes.solutionText?.let {
                Text("교사용 풀이: $it", color = Color(0xFF374151))
            }
            notes.expectedSummary?.let {
                Text("기준: $it", color = Color(0xFF374151), fontWeight = FontWeight.SemiBold)
            }
            notes.answerNote?.let {
                Text("답안 메모: $it", color = Color(0xFF78350F))
            }
            notes.teacherMemo?.let {
                Text("교사용 메모: $it", color = Color(0xFF78350F))
            }
            if (hintText.isNotBlank()) {
                Text("힌트: $hintText", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
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
    questionTextSizeSp: Int,
    modifier: Modifier = Modifier
) {
    Card(modifier = modifier, colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(estimateWorksheetContentHeightDp(state.currentProblem).dp)
            ) {
                ProblemWorksheetBackground(
                    problem = state.currentProblem,
                    questionTextSizeSp = questionTextSizeSp,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

@Composable
private fun MasterImageAdjustControls(
    mode: WorksheetImageAdjustmentMode,
    onStartImage: () -> Unit,
    onStartFrame: () -> Unit,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
    ) {
        OutlinedButton(
            onClick = onStartImage,
            modifier = Modifier.height(34.dp),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)
        ) {
            Text("그림 조정")
        }
        OutlinedButton(
            onClick = onStartFrame,
            modifier = Modifier.height(34.dp),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)
        ) {
            Text("테두리 조정")
        }
        Button(
            onClick = onConfirm,
            enabled = mode != WorksheetImageAdjustmentMode.None,
            modifier = Modifier.height(34.dp),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)
        ) {
            Text("조정 확정")
        }
    }
}

@Composable
private fun MasterImageSizeControls(
    viewModel: PracticeViewModel,
    modifier: Modifier = Modifier
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
    ) {
        Text("그림", style = MaterialTheme.typography.labelMedium, color = Color(0xFF6B7280))
        OutlinedButton(
            onClick = { viewModel.adjustCurrentProblemImageHeight(-80) },
            modifier = Modifier.height(34.dp),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)
        ) {
            Text("-")
        }
        OutlinedButton(
            onClick = { viewModel.adjustCurrentProblemImageHeight(80) },
            modifier = Modifier.height(34.dp),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)
        ) {
            Text("+")
        }
        OutlinedButton(
            onClick = { viewModel.setCurrentProblemImageMode("crop") },
            modifier = Modifier.height(34.dp),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)
        ) {
            Text("자르기 크게")
        }
        OutlinedButton(
            onClick = { viewModel.setCurrentProblemImageMode("fit") },
            modifier = Modifier.height(34.dp),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)
        ) {
            Text("맞춤")
        }
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
    onSubmit: () -> Unit,
    showCustomKeypad: Boolean,
    useSystemKeyboard: Boolean,
    showSubmittedAnswers: Boolean,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (showSubmittedAnswers) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.Top,
                modifier = Modifier.fillMaxWidth()
            ) {
                SubmittedAnswerHistory(
                    logs = state.visibleAnswerLogs,
                    fields = state.fields,
                    currentPreview = formatTypedAnswer(state),
                    modifier = Modifier.weight(1f)
                )
                AnswerInputControls(
                    state = state,
                    viewModel = viewModel,
                    onSubmit = onSubmit,
                    useSystemKeyboard = useSystemKeyboard,
                    modifier = Modifier.weight(1f)
                )
            }
        } else {
            AnswerInputControls(
                state = state,
                viewModel = viewModel,
                onSubmit = onSubmit,
                useSystemKeyboard = useSystemKeyboard,
                modifier = Modifier.fillMaxWidth()
            )
        }
        if (showCustomKeypad && state.currentProblem?.problemType != ProblemType.MULTIPLE_CHOICE) {
            AnswerKeypad(
                onInput = viewModel::appendToActiveField,
                onBackspace = viewModel::backspaceActiveField,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun AnswerInputControls(
    state: PracticeUiState,
    viewModel: PracticeViewModel,
    onSubmit: () -> Unit,
    useSystemKeyboard: Boolean,
    modifier: Modifier = Modifier
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Bottom,
        modifier = modifier
    ) {
        if (state.currentProblem?.problemType == ProblemType.MULTIPLE_CHOICE) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                state.choices.forEach { choice ->
                    FilterChip(
                        selected = state.selectedChoiceIds.contains(choice.choiceId),
                        onClick = { viewModel.toggleChoice(choice.choiceId) },
                        label = { Text(choice.choiceText) }
                    )
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                state.fields.filterNot { it.fieldType.isWorksheetOnlyField() }.forEach { field ->
                    val choiceOptions = inlineChoiceOptionsFor(field, state.rules)
                    val disabled = field.isDisabledForInput()
                    val fieldValue = if (disabled) {
                        field.disabledDisplayValue()
                    } else {
                        state.inputByField[field.answerFieldId].orEmpty()
                    }
                    if (!disabled) {
                        InlineChoiceSelector(
                            options = choiceOptions,
                            currentValue = state.inputByField[field.answerFieldId].orEmpty(),
                            onToggle = { option ->
                                viewModel.updateInput(
                                    field.answerFieldId,
                                    toggleInlineChoiceAnswer(
                                        currentValue = state.inputByField[field.answerFieldId].orEmpty(),
                                        options = choiceOptions,
                                        option = option
                                    )
                                )
                            }
                        )
                    }
                    OutlinedTextField(
                        value = fieldValue,
                        onValueChange = { if (!disabled) viewModel.updateInput(field.answerFieldId, it) },
                        enabled = !disabled,
                        label = { Text(field.label) },
                        singleLine = true,
                        keyboardOptions = if (useSystemKeyboard) {
                            keyboardOptionsFor(field.fieldType)
                        } else {
                            keyboardOptionsFor(field.fieldType).copy(showKeyboardOnFocus = false)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .onFocusChanged { focusState ->
                                if (!disabled && focusState.isFocused) {
                                    viewModel.setActiveField(field.answerFieldId)
                                }
                            }
                    )
                }
            }
        }
        Button(
            onClick = onSubmit,
            enabled = !state.submitting,
            modifier = Modifier.height(56.dp)
        ) {
            Text(if (state.submitting) "채점중" else "제출")
        }
    }
}

@Composable
private fun InlineChoiceSelector(
    options: List<InlineChoiceOption>,
    currentValue: String,
    onToggle: (InlineChoiceOption) -> Unit
) {
    if (options.isEmpty()) return
    val selectedKeys = selectedInlineChoiceKeys(currentValue, options)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        options.forEach { option ->
            val selected = selectedKeys.contains(option.key)
            OutlinedButton(
                onClick = { onToggle(option) },
                modifier = Modifier.size(42.dp),
                shape = CircleShape,
                border = BorderStroke(
                    width = 1.4.dp,
                    color = if (selected) Color(0xFF2563EB) else Color(0xFFCBD5E1)
                ),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = if (selected) Color(0xFF2563EB) else Color.White,
                    contentColor = if (selected) Color.White else Color(0xFF111827)
                ),
                contentPadding = PaddingValues(0.dp)
            ) {
                Text(option.display, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun AnswerKeypad(
    onInput: (String) -> Unit,
    onBackspace: () -> Unit,
    modifier: Modifier = Modifier
) {
    val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0", ".", ",", "/", "←")
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = modifier.fillMaxWidth()) {
        keys.forEach { key ->
            OutlinedButton(
                onClick = {
                    if (key == "←") {
                        onBackspace()
                    } else {
                        onInput(key)
                    }
                },
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(40.dp),
                contentPadding = PaddingValues(horizontal = 0.dp, vertical = 0.dp)
            ) {
                Text(key)
            }
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

private fun AnswerFieldType.isWorksheetOnlyField(): Boolean {
    return this == AnswerFieldType.DRAWING || this == AnswerFieldType.TABLE
}

private data class InlineChoiceOption(
    val display: String,
    val value: String,
    val key: String
)

private fun inlineChoiceOptionsFor(
    field: AnswerFieldEntity,
    rules: List<com.mathworkbook.app.core.database.AnswerRuleEntity>
): List<InlineChoiceOption> {
    explicitInlineChoiceOptions(field.positionJson)?.let { return it }

    val candidateTexts = rules
        .filter { rule -> rule.answerFieldId == field.answerFieldId || rule.answerFieldId == null }
        .flatMap { rule ->
            buildList {
                add(rule.correctAnswerRaw)
                add(rule.normalizedAnswer)
                addAll(parseAcceptedAnswerTexts(rule.acceptedAnswersJson))
            }
        }
    val maxCircled = candidateTexts.maxOfOrNull { text ->
        text.maxOfOrNull { char -> circledNumberValue(char) ?: 0 } ?: 0
    } ?: 0
    if (maxCircled == 0) return emptyList()
    return (1..maxOf(4, maxCircled)).map { number ->
        val circled = circledNumber(number)
        InlineChoiceOption(display = circled, value = circled, key = number.toString())
    }
}

private fun explicitInlineChoiceOptions(positionJson: String?): List<InlineChoiceOption>? {
    if (positionJson.isNullOrBlank()) return null
    return runCatching {
        val meta = JSONObject(positionJson)
        val options = meta.optJSONArray("choiceOptions") ?: return@runCatching null
        val style = meta.optString("choiceStyle")
        val valueStyle = meta.optString("choiceValueStyle", style)
        List(options.length()) { index ->
            val item = options.get(index)
            val raw = when (item) {
                is JSONObject -> item.optString("value")
                    .ifBlank { item.optString("label") }
                    .ifBlank { item.optString("display") }
                    .ifBlank { "${index + 1}" }
                else -> item.toString()
            }
            val number = raw.toIntOrNull()
            val display = if (style == "circled" && number != null) circledNumber(number) else raw
            val value = if (valueStyle == "circled" && number != null) circledNumber(number) else raw
            InlineChoiceOption(
                display = display,
                value = value,
                key = canonicalChoiceToken(value) ?: canonicalChoiceToken(display) ?: raw
            )
        }
    }.getOrNull()
}

private fun toggleInlineChoiceAnswer(
    currentValue: String,
    options: List<InlineChoiceOption>,
    option: InlineChoiceOption
): String {
    val selected = selectedInlineChoiceKeys(currentValue, options).toMutableSet()
    if (!selected.add(option.key)) {
        selected.remove(option.key)
    }
    return options
        .filter { selected.contains(it.key) }
        .joinToString(", ") { it.value }
}

private fun selectedInlineChoiceKeys(
    currentValue: String,
    options: List<InlineChoiceOption>
): Set<String> {
    val tokens = currentValue
        .split(",", " ", "/", "·")
        .mapNotNull { token -> canonicalChoiceToken(token) ?: token.trim().takeIf { it.isNotBlank() } }
        .toSet()
    return options.filter { tokens.contains(it.key) }.mapTo(mutableSetOf()) { it.key }
}

private fun canonicalChoiceToken(token: String): String? {
    val cleaned = token
        .trim()
        .removeSuffix("번")
        .trim(' ', '.', ',', '(', ')', '[', ']', '{', '}')
    if (cleaned.isEmpty()) return null
    if (cleaned.all { it.isDigit() }) return cleaned
    if (cleaned.length == 1) {
        circledNumberValue(cleaned.first())?.let { return it.toString() }
    }
    return null
}

private fun parseAcceptedAnswerTexts(json: String?): List<String> {
    if (json.isNullOrBlank()) return emptyList()
    return runCatching {
        val array = JSONArray(json)
        List(array.length()) { index -> array.getString(index) }
    }.getOrDefault(emptyList())
}

private fun circledNumber(number: Int): String {
    return CircledNumbers.getOrNull(number) ?: number.toString()
}

private fun circledNumberValue(char: Char): Int? {
    return CircledNumbers.indexOf(char.toString()).takeIf { it > 0 }
}

private val CircledNumbers = listOf(
    "",
    "①", "②", "③", "④", "⑤",
    "⑥", "⑦", "⑧", "⑨", "⑩",
    "⑪", "⑫", "⑬", "⑭", "⑮",
    "⑯", "⑰", "⑱", "⑲", "⑳"
)

private fun PracticeUiState.selectedStudentAttempt(): PracticeAttemptEntity? {
    return attemptsForProblem.firstOrNull { it.attemptId == selectedStudentAttemptId }
}

private fun formatCurrentAnswer(state: PracticeUiState): String {
    return if (state.currentProblem?.problemType == ProblemType.MULTIPLE_CHOICE) {
        state.choices
            .filter { state.selectedChoiceIds.contains(it.choiceId) }
            .joinToString(", ") { it.choiceText }
    } else {
        val inputFields = state.fields.filterNot { it.fieldType.isWorksheetOnlyField() }
        inputFields
            .mapNotNull { field ->
                state.inputByField[field.answerFieldId]
                    ?.takeIf { it.isNotBlank() }
                    ?.let { formatFieldAnswerValue(field, it, includeLabel = inputFields.size > 1) }
            }
            .joinToString(", ")
            .ifBlank { state.rules.joinToString(", ") { it.correctAnswerRaw } }
    }
}

private fun formatTypedAnswer(state: PracticeUiState): String {
    return if (state.currentProblem?.problemType == ProblemType.MULTIPLE_CHOICE) {
        state.choices
            .filter { state.selectedChoiceIds.contains(it.choiceId) }
            .joinToString(", ") { it.choiceText }
    } else {
        val inputFields = state.fields.filterNot { it.fieldType.isWorksheetOnlyField() }
        inputFields
            .mapNotNull { field ->
                state.inputByField[field.answerFieldId]
                    ?.trim()
                    ?.takeIf { it.isNotBlank() }
                    ?.let { formatFieldAnswerValue(field, it, includeLabel = inputFields.size > 1) }
            }
            .joinToString(", ")
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

private fun submittedAnswerLines(
    logs: List<AttemptInputLogEntity>,
    fields: List<AnswerFieldEntity>
): List<SubmittedAnswerLine> {
    val fieldById = fields.associateBy { it.answerFieldId }
    return logs
        .groupBy { it.tryNumber }
        .entries
        .sortedBy { it.key }
        .mapNotNull { (_, tryLogs) ->
            val includeLabels = tryLogs.count { !it.answerFieldId.isNullOrBlank() } > 1
            val text = tryLogs
                .sortedBy { it.answerFieldId.orEmpty() }
                .joinToString(", ") { log ->
                    val field = log.answerFieldId?.let { fieldById[it] }
                    if (field == null) {
                        log.submittedAnswerRaw.trim()
                    } else {
                        formatFieldAnswerValue(field, log.submittedAnswerRaw, includeLabel = includeLabels)
                    }
                }
                .trim()
            text.takeIf { it.isNotBlank() }?.let {
                SubmittedAnswerLine(
                    text = it,
                    isCorrect = tryLogs.all { log -> log.isCorrect }
                )
            }
        }
}

private fun formatFieldAnswerValue(
    field: AnswerFieldEntity,
    rawValue: String,
    includeLabel: Boolean
): String {
    val trimmed = rawValue.trim()
    if (trimmed.isBlank()) return ""
    val meta = answerFieldMeta(field)
    val prefix = meta
        ?.let { it.optString("displayPrefix").ifBlank { it.optString("prefix") } }
        .orEmpty()
    val suffix = meta
        ?.let { it.optString("displaySuffix").ifBlank { it.optString("suffix") } }
        .orEmpty()
    val value = "$prefix$trimmed$suffix"
    return if (includeLabel && field.label.isNotBlank()) {
        "${field.label} $value"
    } else {
        value
    }
}

private fun answerFieldMeta(field: AnswerFieldEntity): JSONObject? {
    if (field.positionJson.isNullOrBlank()) return null
    return runCatching { JSONObject(field.positionJson.orEmpty()) }.getOrNull()
}

private fun AnswerFieldEntity.isDisabledForInput(): Boolean {
    val meta = answerFieldMeta(this) ?: return false
    return meta.optBoolean("disabled", false) || meta.optBoolean("readOnly", false)
}

private fun AnswerFieldEntity.disabledDisplayValue(): String {
    val meta = answerFieldMeta(this)
    return meta?.optString("displayValue")
        ?.ifBlank { meta.optString("placeholder") }
        ?.ifBlank { label }
        ?: label
}

private fun formatAttemptDate(timestamp: Long): String {
    return SimpleDateFormat("MM/dd HH:mm", Locale.KOREA).format(Date(timestamp))
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
