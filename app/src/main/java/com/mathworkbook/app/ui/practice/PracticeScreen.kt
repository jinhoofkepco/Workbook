package com.mathworkbook.app.ui.practice

import android.webkit.WebView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.KeyboardType as ImeKeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mathworkbook.app.core.database.AnswerFieldEntity
import com.mathworkbook.app.core.database.AttemptInputLogEntity
import com.mathworkbook.app.core.database.ChapterEntity
import com.mathworkbook.app.core.database.PracticeAttemptEntity
import com.mathworkbook.app.core.database.WorkbookEntity
import com.mathworkbook.app.core.domain.FinalStatus
import com.mathworkbook.app.core.domain.AnswerFieldType
import com.mathworkbook.app.core.domain.AnswerType
import com.mathworkbook.app.core.domain.ProblemType
import com.mathworkbook.app.core.gpt.SavedGptExplanation
import com.mathworkbook.app.core.gpt.parseGptExplanations
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
import com.mathworkbook.app.ui.gpt.GptProblemContext
import com.mathworkbook.app.ui.skin.LocalWorkbookSkin
import com.mathworkbook.app.ui.skin.SkinAssetImage
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
private const val ViewerPublishIntervalMillis = 1_500L
private const val ViewerProblemStableMillis = 1_500L
private const val ViewerMinIdleMillis = 1_500L

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
    onGptProblemContextChanged: (GptProblemContext?) -> Unit = {},
    onOpenProgress: () -> Unit = {},
    onInitialChapterHandled: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val latestState by rememberUpdatedState(state)
    val latestMasterMode by rememberUpdatedState(isMasterMode)
    val handwritingState = rememberHandwritingState()
    val focusManager = LocalFocusManager.current
    var imageAdjustmentMode by remember { mutableStateOf(WorksheetImageAdjustmentMode.None) }
    var imageTransformDraft by remember { mutableStateOf(WorksheetImageTransform()) }
    var lastStudentInkProblemId by remember { mutableStateOf<String?>(null) }
    var stashedStudentInk by remember { mutableStateOf<Pair<String, String>?>(null) }
    var previousMasterMode by remember { mutableStateOf(isMasterMode) }
    var viewerProblemOpenedAtMillis by remember { mutableStateOf(System.currentTimeMillis()) }
    val latestViewerProblemOpenedAtMillis by rememberUpdatedState(viewerProblemOpenedAtMillis)

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
        viewerProblemOpenedAtMillis = System.currentTimeMillis()
        state.currentProblem?.let { problem ->
            onProblemLocationChanged(problem.workbookId, problem.chapterId, problem.problemId)
        }
    }

    LaunchedEffect(
        state.currentProblem,
        state.selectedWorkbook,
        state.selectedChapter,
        state.currentIndex,
        state.problems.size,
        state.fields,
        state.rules,
        state.choices,
        state.inputByField,
        state.selectedChoiceIds
    ) {
        onGptProblemContextChanged(state.toGptProblemContext())
    }

    LaunchedEffect(isMasterMode, state.currentProblem?.problemId) {
        viewModel.clearViewerCurrentScreen()
    }

    LaunchedEffect(isMasterMode, state.currentProblem?.problemId, state.masterNoteVectorJson) {
        val problemId = state.currentProblem?.problemId
        if (isMasterMode) {
            if (!previousMasterMode && problemId != null) {
                stashedStudentInk = problemId to handwritingState.toVectorJson()
            }
            handwritingState.loadFromVectorJson(state.masterNoteVectorJson)
            lastStudentInkProblemId = null
        } else if (problemId != null) {
            val stash = stashedStudentInk
            if (previousMasterMode && stash?.first == problemId) {
                handwritingState.loadFromVectorJson(stash.second)
                stashedStudentInk = null
                lastStudentInkProblemId = problemId
            } else if (lastStudentInkProblemId != problemId) {
                handwritingState.clear()
                stashedStudentInk = null
                lastStudentInkProblemId = problemId
            }
        }
        previousMasterMode = isMasterMode
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

    LaunchedEffect(Unit) {
        var lastPublishedKey = ""
        var lastSeenInkVersion = handwritingState.changeVersion
        var lastInkChangedAtMillis = 0L
        while (true) {
            delay(ViewerPublishIntervalMillis)
            val now = System.currentTimeMillis()
            val inkVersion = handwritingState.changeVersion
            if (inkVersion != lastSeenInkVersion) {
                lastSeenInkVersion = inkVersion
                lastInkChangedAtMillis = now
            }
            val current = latestState
            val problem = current.currentProblem
            if (latestMasterMode || problem == null) {
                viewModel.clearViewerCurrentScreen()
                lastPublishedKey = ""
                continue
            }
            if (!viewModel.isViewerRunning() || current.submitting) {
                continue
            }
            if (now - latestViewerProblemOpenedAtMillis < ViewerProblemStableMillis) {
                continue
            }
            if (lastInkChangedAtMillis > 0L && now - lastInkChangedAtMillis < ViewerMinIdleMillis) {
                continue
            }
            val publishKey = buildString {
                append(problem.problemId)
                append('|')
                append(current.inputByField.toSortedMap())
                append('|')
                append(current.selectedChoiceIds.sorted())
                append('|')
                append(inkVersion)
            }
            if (publishKey != lastPublishedKey) {
                viewModel.publishViewerCurrentScreen(
                    solutionVectorJson = handwritingState.toVectorJson(),
                    snapshotRevision = now
                )
                lastPublishedKey = publishKey
            }
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
                            onDrawingStart = {
                                focusManager.clearFocus(force = true)
                            },
                            toolbarLeadingContent = {
                                ToolbarNavButton(
                                    text = "‹",
                                    direction = NavDirection.Previous,
                                    onClick = {
                                        focusManager.clearFocus(force = true)
                                        viewModel.movePrevious()
                                    }
                                )
                                ToolbarHintButton(onClick = viewModel::showHint)
                            },
                            toolbarCenterContent = {
                                WorksheetLocationLabel(
                                    workbookTitle = state.selectedWorkbook?.title.orEmpty(),
                                    chapterTitle = state.selectedChapter?.title.orEmpty(),
                                    position = "${state.currentIndex + 1}/${state.problems.size}",
                                    onClick = onOpenProgress
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
                                            isMasterMode -> Unit
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
                                } else if (isMasterMode) {
                                    ProblemWorksheetFooterOverlay(
                                        problem = state.currentProblem,
                                        questionTextSizeSp = questionTextSizeSp,
                                        modifier = Modifier.fillMaxSize()
                                    ) {
                                        MasterCorrectSolutionFooter(
                                            state = state,
                                            onDeleteGptExplanation = viewModel::deleteGptExplanation
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
    var historyInfoAttempt by remember(state.currentProblem?.problemId) { mutableStateOf<PracticeAttemptEntity?>(null) }

    LaunchedEffect(historyInfoAttempt?.attemptId, isMasterMode) {
        if (!isMasterMode && historyInfoAttempt != null) {
            delay(3_000L)
            historyInfoAttempt = null
        }
    }

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
                    ProblemAttemptHistoryStrip(
                        attempts = state.attemptsForProblem,
                        selectedAttemptId = if (isMasterMode) state.selectedStudentAttemptId else null,
                        onClick = { attempt ->
                            if (isMasterMode) {
                                viewModel.toggleStudentAttempt(attempt.attemptId)
                            } else {
                                historyInfoAttempt = attempt
                            }
                        },
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(start = 8.dp, top = 64.dp)
                    )
                    val visibleHistoryInfo = if (isMasterMode) selectedStudentAttempt else historyInfoAttempt
                    visibleHistoryInfo?.let { attempt ->
                        AttemptHistoryInfoOverlay(
                            attempt = attempt,
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(start = 34.dp, top = 64.dp)
                        )
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
    position: String,
    onClick: () -> Unit
) {
    val hasSkinHeader = LocalWorkbookSkin.current?.assetPath("problemHeaderPill") != null
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .height(36.dp)
                .clip(RoundedCornerShape(13.dp))
                .background(if (hasSkinHeader) Color.Transparent else Color(0xF7FFFFFF))
                .border(
                    width = if (hasSkinHeader) 0.dp else 1.dp,
                    color = Color(0x1F2563EB),
                    shape = RoundedCornerShape(13.dp)
                )
        )
        SkinAssetImage(
            assetKey = "problemHeaderPill",
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .height(38.dp),
            contentScale = ContentScale.FillBounds,
            alpha = 1f
        )
        Row(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .height(36.dp)
                .padding(horizontal = 12.dp, vertical = 5.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = workbookTitle.ifBlank { "문제집" },
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${chapterTitle.ifBlank { "단원" }} · $position",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium,
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
    val assetKey = if (direction == NavDirection.Previous) "navArrowPrevious" else "navArrowNext"
    val hasSkinArrow = LocalWorkbookSkin.current?.assetPath(assetKey) != null
    val shape = if (direction == NavDirection.Previous) PreviousNavShape else NextNavShape
    Box(
        modifier = modifier.size(width = 48.dp, height = 44.dp),
        contentAlignment = Alignment.Center
    ) {
        SkinAssetImage(
            assetKey = assetKey,
            modifier = Modifier
                .align(Alignment.Center)
                .size(width = 44.dp, height = 38.dp),
            contentScale = ContentScale.FillBounds,
            alpha = 1f
        )
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier
            .matchParentSize()
            .defaultMinSize(minWidth = 0.dp, minHeight = 0.dp),
        shape = shape,
        border = if (hasSkinArrow) null else BorderStroke(1.4.dp, Color(0xFF2563EB)),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = if (hasSkinArrow) Color.Transparent else Color(0xF2FFFFFF),
            contentColor = Color(0xFF2563EB)
        ),
        contentPadding = PaddingValues(0.dp)
    ) {
        if (!hasSkinArrow) {
            Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
    }
    }
}

@Composable
private fun ToolbarHintButton(
    onClick: () -> Unit
) {
    val hasSkinButton = LocalWorkbookSkin.current?.assetPath("hintButton") != null
    Box(
        modifier = Modifier.size(44.dp),
        contentAlignment = Alignment.Center
    ) {
        SkinAssetImage(
            assetKey = "hintButton",
            modifier = Modifier
                .align(Alignment.Center)
                .size(38.dp),
            contentScale = ContentScale.FillBounds,
            alpha = 1f
        )
        OutlinedButton(
            onClick = onClick,
            modifier = Modifier
                .matchParentSize()
                .defaultMinSize(minWidth = 0.dp, minHeight = 0.dp),
            shape = CircleShape,
            border = if (hasSkinButton) null else BorderStroke(1.2.dp, Color(0xFF7C3AED)),
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = if (hasSkinButton) Color.Transparent else Color(0xF2FFFFFF),
                contentColor = Color(0xFF7C3AED)
            ),
            contentPadding = PaddingValues(0.dp)
        ) {
            if (!hasSkinButton) {
                Text("?", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            }
        }
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
                val inputPrefix = field.inputPrefixForInput()
                val inputSuffix = field.inputSuffixForInput()
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
                    prefix = inputPrefix.takeIf { it.isNotBlank() }?.let { text -> { Text(text) } },
                    suffix = inputSuffix.takeIf { it.isNotBlank() }?.let { text -> { Text(text) } },
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
private fun ProblemAttemptHistoryStrip(
    attempts: List<PracticeAttemptEntity>,
    selectedAttemptId: String?,
    onClick: (PracticeAttemptEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    val visibleAttempts = attempts
        .filter { it.inputTryCount > 0 || it.finalStatus != FinalStatus.IN_PROGRESS }
        .sortedByDescending { it.submittedAt ?: it.startedAt }
        .take(12)
    if (visibleAttempts.isEmpty()) return
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(5.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        visibleAttempts.forEach { attempt ->
            val selected = selectedAttemptId == attempt.attemptId
            Box(
                modifier = Modifier
                    .size(width = 18.dp, height = 38.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(attemptHistoryFill(attempt))
                    .border(
                        width = if (selected) 2.dp else 1.dp,
                        color = if (selected) attemptHistoryStroke(attempt) else Color(0x66FFFFFF),
                        shape = RoundedCornerShape(5.dp)
                    )
                    .clickable { onClick(attempt) }
            )
        }
    }
}

@Composable
private fun AttemptHistoryInfoOverlay(
    attempt: PracticeAttemptEntity,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = Color(0xCCFFFFFF)),
        shape = RoundedCornerShape(10.dp)
    ) {
        Text(
            text = "${attemptHistoryLabel(attempt)} · ${formatAttemptDate(attempt.submittedAt ?: attempt.startedAt)}",
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
            color = attemptHistoryStroke(attempt),
            fontWeight = FontWeight.SemiBold,
            style = MaterialTheme.typography.labelMedium
        )
    }
}

private fun attemptHistoryFill(attempt: PracticeAttemptEntity): Color {
    return when {
        attempt.finalStatus == FinalStatus.CORRECT || attempt.isCorrect == true -> Color(0xFFDBEAFE)
        attempt.finalStatus == FinalStatus.MANUAL_REVIEW_REQUIRED -> Color(0xFFFEF3C7)
        attempt.inputTryCount > 0 || attempt.isCorrect == false -> Color(0xFFFEE2E2)
        else -> Color(0xFFE5E7EB)
    }
}

private fun attemptHistoryStroke(attempt: PracticeAttemptEntity): Color {
    return when {
        attempt.finalStatus == FinalStatus.CORRECT || attempt.isCorrect == true -> Color(0xFF2563EB)
        attempt.finalStatus == FinalStatus.MANUAL_REVIEW_REQUIRED -> Color(0xFFD97706)
        attempt.inputTryCount > 0 || attempt.isCorrect == false -> Color(0xFFDC2626)
        else -> Color(0xFF6B7280)
    }
}

private fun attemptHistoryLabel(attempt: PracticeAttemptEntity): String {
    return when {
        attempt.finalStatus == FinalStatus.CORRECT || attempt.isCorrect == true -> "정답"
        attempt.finalStatus == FinalStatus.MANUAL_REVIEW_REQUIRED -> "검토 필요"
        attempt.inputTryCount > 0 || attempt.isCorrect == false -> "오답"
        else -> "진행 중"
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
private fun MasterCorrectSolutionFooter(
    state: PracticeUiState,
    onDeleteGptExplanation: (String) -> Unit
) {
    val answerText = formatCurrentAnswer(state)
    val notes = parseProblemTeacherNotes(state.currentProblem)
    val gptExplanations = remember(state.currentProblem?.imageCropRectJson) {
        parseGptExplanations(state.currentProblem?.imageCropRectJson)
    }
    val hintText = state.currentProblem?.hintText.orEmpty()
    if (answerText.isBlank() && hintText.isBlank() && notes.isEmpty() && gptExplanations.isEmpty()) return
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
            if (gptExplanations.isNotEmpty()) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                Text("GPT 저장 설명", color = Color(0xFF1D4ED8), fontWeight = FontWeight.SemiBold)
                gptExplanations.forEach { explanation ->
                    SavedGptExplanationItem(
                        explanation = explanation,
                        onDelete = { onDeleteGptExplanation(explanation.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun SavedGptExplanationItem(
    explanation: SavedGptExplanation,
    onDelete: () -> Unit
) {
    var showFullDialog by remember(explanation.id) { mutableStateOf(false) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFEFF6FF)),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(explanation.title, color = Color(0xFF1E3A8A), fontWeight = FontWeight.SemiBold)
                    Text(
                        formatAttemptDate(explanation.savedAt.takeIf { it > 0L } ?: explanation.updatedAt),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF64748B)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(
                        onClick = { showFullDialog = true },
                        modifier = Modifier.height(32.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)
                    ) {
                        Text("보기")
                    }
                    OutlinedButton(
                        onClick = onDelete,
                        modifier = Modifier.height(32.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)
                    ) {
                        Text("삭제", color = Color(0xFFB91C1C))
                    }
                }
            }
            Text(
                text = explanation.explanationText,
                color = Color(0xFF172554),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
    if (showFullDialog) {
        AlertDialog(
            onDismissRequest = { showFullDialog = false },
            title = {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(explanation.title, fontWeight = FontWeight.SemiBold)
                    Text(
                        formatAttemptDate(explanation.savedAt.takeIf { it > 0L } ?: explanation.updatedAt),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            text = {
                if (explanation.explanationHtml.isNotBlank()) {
                    GptExplanationHtmlView(
                        html = explanation.explanationHtml,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 420.dp, max = 620.dp)
                    )
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 560.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(explanation.explanationText, color = Color(0xFF111827))
                        if (explanation.prompt.isNotBlank()) {
                            HorizontalDivider()
                            Text("프롬프트", style = MaterialTheme.typography.labelSmall, color = Color(0xFF64748B))
                            Text(explanation.prompt, style = MaterialTheme.typography.bodySmall, color = Color(0xFF475569))
                        }
                    }
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = {
                        showFullDialog = false
                        onDelete()
                    }
                ) {
                    Text("삭제", color = Color(0xFFB91C1C))
                }
            },
            confirmButton = {
                Button(onClick = { showFullDialog = false }) {
                    Text("닫기")
                }
            }
        )
    }
}

@Composable
private fun GptExplanationHtmlView(
    html: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    AndroidView(
        modifier = modifier,
        factory = {
            WebView(context).apply {
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                settings.javaScriptEnabled = false
                settings.domStorageEnabled = false
                settings.loadWithOverviewMode = true
                settings.useWideViewPort = true
                settings.builtInZoomControls = true
                settings.displayZoomControls = false
                loadDataWithBaseURL(
                    "https://chatgpt.com/",
                    gptExplanationDocument(html),
                    "text/html",
                    "UTF-8",
                    null
                )
            }
        },
        update = { view ->
            view.loadDataWithBaseURL(
                "https://chatgpt.com/",
                gptExplanationDocument(html),
                "text/html",
                "UTF-8",
                null
            )
        }
    )
}

private fun gptExplanationDocument(contentHtml: String): String {
    return """
        <!doctype html>
        <html>
        <head>
          <meta name="viewport" content="width=device-width, initial-scale=1.0">
          <style>
            :root { color-scheme: light; }
            body {
              margin: 0;
              padding: 4px 2px 16px 2px;
              background: transparent;
              color: #111827;
              font-family: system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif;
              font-size: 16px;
              line-height: 1.62;
              word-break: keep-all;
              overflow-wrap: anywhere;
            }
            p { margin: 0 0 0.9em; }
            strong, b { font-weight: 700; }
            em { font-style: italic; }
            h1, h2, h3, h4 { margin: 1em 0 0.45em; line-height: 1.25; }
            h1 { font-size: 1.45em; }
            h2 { font-size: 1.28em; }
            h3 { font-size: 1.14em; }
            ul, ol { margin: 0.5em 0 1em 1.35em; padding: 0; }
            li { margin: 0.25em 0; }
            code {
              background: #f3f4f6;
              border-radius: 5px;
              padding: 0.08em 0.32em;
              font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
              font-size: 0.92em;
            }
            pre {
              background: #111827;
              color: #f9fafb;
              border-radius: 10px;
              padding: 12px;
              overflow-x: auto;
              white-space: pre-wrap;
            }
            pre code { background: transparent; color: inherit; padding: 0; }
            table {
              border-collapse: collapse;
              width: 100%;
              margin: 0.75em 0;
              font-size: 0.95em;
            }
            th, td {
              border: 1px solid #d1d5db;
              padding: 7px 8px;
              vertical-align: top;
            }
            th { background: #f3f4f6; font-weight: 700; }
            blockquote {
              margin: 0.75em 0;
              padding: 0.2em 0 0.2em 0.9em;
              border-left: 4px solid #c7d2fe;
              color: #374151;
            }
            .katex, .math, [class*="math"] { font-size: 1.04em; }
          </style>
        </head>
        <body>
          <main class="gpt-answer">
            $contentHtml
          </main>
        </body>
        </html>
    """.trimIndent()
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
                    val inputPrefix = field.inputPrefixForInput()
                    val inputSuffix = field.inputSuffixForInput()
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
                        prefix = inputPrefix.takeIf { it.isNotBlank() }?.let { text -> { Text(text) } },
                        suffix = inputSuffix.takeIf { it.isNotBlank() }?.let { text -> { Text(text) } },
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
        if (shouldShowFractionQuickButtons(state)) {
            FractionQuickButtons(
                onSpace = { viewModel.appendToActiveField(" ") },
                onFraction = { viewModel.appendToActiveField("/") }
            )
        }
        SubmitGraphicButton(
            submitting = state.submitting,
            onSubmit = onSubmit
        )
    }
}

@Composable
private fun FractionQuickButtons(
    onSpace: () -> Unit,
    onFraction: () -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.height(56.dp)
    ) {
        OutlinedButton(
            onClick = onSpace,
            modifier = Modifier.size(width = 48.dp, height = 26.dp),
            shape = RoundedCornerShape(8.dp),
            contentPadding = PaddingValues(0.dp)
        ) {
            Text("공백", fontSize = 10.sp, maxLines = 1)
        }
        OutlinedButton(
            onClick = onFraction,
            modifier = Modifier.size(width = 48.dp, height = 26.dp),
            shape = RoundedCornerShape(8.dp),
            contentPadding = PaddingValues(0.dp)
        ) {
            Text("/", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun SubmitGraphicButton(
    submitting: Boolean,
    onSubmit: () -> Unit
) {
    val skin = LocalWorkbookSkin.current
    val assetKey = when {
        submitting && skin?.assetPath("gradingButton") != null -> "gradingButton"
        !submitting && skin?.assetPath("submitButton") != null -> "submitButton"
        else -> null
    }
    if (assetKey != null) {
        Box(
            modifier = Modifier
                .size(width = 124.dp, height = 58.dp)
                .clip(RoundedCornerShape(14.dp)),
            contentAlignment = Alignment.Center
        ) {
            SkinAssetImage(
                assetKey = assetKey,
                modifier = Modifier.matchParentSize(),
                contentScale = ContentScale.FillBounds,
                alpha = 1f
            )
            Button(
                onClick = onSubmit,
                enabled = !submitting,
                modifier = Modifier.matchParentSize(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.Transparent,
                    disabledContainerColor = Color.Transparent,
                    contentColor = Color(0xFF111827),
                    disabledContentColor = Color(0xFF6B7280)
                ),
                contentPadding = PaddingValues(0.dp)
            ) {}
        }
    } else {
        Button(
            onClick = onSubmit,
            enabled = !submitting,
            modifier = Modifier.height(56.dp)
        ) {
            Text(if (submitting) "채점중" else "제출")
        }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun InlineChoiceSelector(
    options: List<InlineChoiceOption>,
    currentValue: String,
    onToggle: (InlineChoiceOption) -> Unit
) {
    if (options.isEmpty()) return
    val selectedKeys = selectedInlineChoiceKeys(currentValue, options)
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
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

private fun shouldShowFractionQuickButtons(state: PracticeUiState): Boolean {
    if (state.currentProblem?.problemType == ProblemType.MULTIPLE_CHOICE) return false
    val hasEditableAnswerField = state.fields
        .filterNot { it.fieldType.isWorksheetOnlyField() }
        .any { !it.isDisabledForInput() }
    if (!hasEditableAnswerField) return false
    return state.fields.any { it.fieldType == AnswerFieldType.FRACTION } ||
        state.rules.any { rule ->
            rule.answerType == AnswerType.FRACTION ||
                rule.correctAnswerRaw.contains("/") ||
                rule.normalizedAnswer.contains("/") ||
                rule.acceptedAnswersJson.orEmpty().contains("/")
        }
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
    val selected = orderedSelectedInlineChoiceKeys(currentValue, options).toMutableList()
    if (selected.contains(option.key)) {
        selected.removeAll { it == option.key }
    } else {
        selected.add(option.key)
    }
    val optionsByKey = options.associateBy { it.key }
    return selected
        .mapNotNull { optionsByKey[it] }
        .joinToString(", ") { it.value }
}

private fun selectedInlineChoiceKeys(
    currentValue: String,
    options: List<InlineChoiceOption>
): Set<String> {
    return orderedSelectedInlineChoiceKeys(currentValue, options).toSet()
}

private fun orderedSelectedInlineChoiceKeys(
    currentValue: String,
    options: List<InlineChoiceOption>
): List<String> {
    val optionKeys = options.map { it.key }.toSet()
    val seen = mutableSetOf<String>()
    return currentValue
        .split(",", " ", "/", "·")
        .mapNotNull { token -> canonicalChoiceToken(token) ?: token.trim().takeIf { it.isNotBlank() } }
        .filter { key -> optionKeys.contains(key) && seen.add(key) }
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

private fun PracticeUiState.toGptProblemContext(): GptProblemContext? {
    val problem = currentProblem ?: return null
    val notes = parseProblemTeacherNotes(problem)
    val fieldLines = fields
        .sortedBy { it.orderIndex }
        .joinToString("\n") { field ->
            val meta = answerFieldMeta(field)
            val suffix = meta?.optString("displaySuffix")?.ifBlank { meta.optString("suffix") }.orEmpty()
            val disabled = if (field.isDisabledForInput()) " / 비활성: ${field.disabledDisplayValue()}" else ""
            "- ${field.label.ifBlank { field.answerFieldId }} (${field.fieldType})${
                suffix.takeIf { it.isNotBlank() }?.let { " / 단위: $it" }.orEmpty()
            }$disabled"
        }
    val choiceLines = choices
        .sortedBy { it.orderIndex }
        .joinToString("\n") { choice ->
            "- ${choice.choiceText} (${choice.choiceValue})"
        }
    val noteLines = buildList {
        notes.solutionText?.takeIf { it.isNotBlank() }?.let { add("풀이: $it") }
        notes.expectedSummary?.takeIf { it.isNotBlank() }?.let { add("기준: $it") }
        notes.answerNote?.takeIf { it.isNotBlank() }?.let { add("답안 메모: $it") }
        notes.teacherMemo?.takeIf { it.isNotBlank() }?.let { add("교사용 메모: $it") }
    }.joinToString("\n")
    return GptProblemContext(
        problemId = problem.problemId,
        workbookTitle = selectedWorkbook?.title.orEmpty(),
        chapterTitle = selectedChapter?.title.orEmpty(),
        problemPosition = "${currentIndex + 1}/${problems.size.coerceAtLeast(1)}",
        questionText = problem.questionText.orEmpty(),
        imagePath = problem.imagePath,
        fieldsSummary = fieldLines,
        choicesSummary = choiceLines,
        currentAnswer = formatTypedAnswer(this),
        storedAnswer = rules
            .map { it.correctAnswerRaw }
            .filter { it.isNotBlank() }
            .distinct()
            .joinToString(", "),
        teacherNotes = noteLines,
        hintText = problem.hintText.orEmpty()
    )
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

private fun AnswerFieldEntity.inputPrefixForInput(): String {
    val meta = answerFieldMeta(this) ?: return ""
    return meta.inputAffixForInput("showPrefixInInput", "inputPrefix", "displayPrefix", "prefix")
}

private fun AnswerFieldEntity.inputSuffixForInput(): String {
    val meta = answerFieldMeta(this) ?: return ""
    return meta.inputAffixForInput("showSuffixInInput", "inputSuffix", "displaySuffix", "suffix")
}

private fun JSONObject.inputAffixForInput(
    showKey: String,
    inputKey: String,
    displayKey: String,
    fallbackKey: String
): String {
    if (!optBoolean(showKey, false) && !optBoolean("showAffixInInput", false)) return ""
    return optString(inputKey)
        .ifBlank { optString(displayKey) }
        .ifBlank { optString(fallbackKey) }
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
