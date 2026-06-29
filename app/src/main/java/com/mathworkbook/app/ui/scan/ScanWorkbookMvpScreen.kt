package com.mathworkbook.app.ui.scan

import android.content.Context
import android.graphics.BitmapFactory
import android.view.MotionEvent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.mathworkbook.app.core.AppContainer
import com.mathworkbook.app.core.files.SCAN_MVP_WORKBOOK_ID
import com.mathworkbook.app.core.files.WorkbookManifestType
import com.mathworkbook.app.core.files.detectWorkbookManifestType
import com.mathworkbook.app.ui.skin.LocalWorkbookSkin
import com.mathworkbook.app.ui.skin.SkinAssetImage
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

@Composable
fun ScanWorkbookMvpScreen(
    container: AppContainer,
    workbookId: String = SCAN_MVP_WORKBOOK_ID,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val workbook = remember(workbookId) { loadScanWorkbookMvp(context, workbookId) }
    var pageIndex by remember { mutableIntStateOf(0) }
    var answerPanelExpanded by remember { mutableStateOf(true) }
    var currentTool by remember { mutableStateOf(ScanDrawingTools.first()) }
    var lastDrawingTool by remember { mutableStateOf(ScanDrawingTools.first()) }
    var penWidth by remember { mutableStateOf(ScanPenWidthOptions.first()) }
    var toolMenuExpanded by remember { mutableStateOf(false) }
    var answers by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var grades by remember { mutableStateOf<Map<String, Boolean>>(emptyMap()) }
    var submittingFields by remember { mutableStateOf<Set<String>>(emptySet()) }
    var strokesByPage by remember { mutableStateOf<Map<String, List<ScanInkStroke>>>(emptyMap()) }
    var activeStroke by remember { mutableStateOf<ScanInkStroke?>(null) }
    var message by remember { mutableStateOf<String?>(null) }

    val page = workbook.pages[pageIndex]
    val pageStrokes = strokesByPage[page.pageId].orEmpty()

    fun movePage(delta: Int) {
        val next = (pageIndex + delta).coerceIn(0, workbook.pages.lastIndex)
        if (next == pageIndex) return
        pageIndex = next
        activeStroke = null
        message = null
    }

    Surface(modifier = modifier.fillMaxSize(), color = Color(0xFFE5E7EB)) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val answerPanelBottomPadding = maxHeight * 0.15f
            ScanPageViewer(
                page = page,
                currentTool = currentTool,
                penWidth = penWidth,
                strokes = pageStrokes,
                activeStroke = activeStroke,
                onStrokeStart = { point, tool, width ->
                    activeStroke = ScanInkStroke(
                        points = listOf(point),
                        color = tool.color,
                        widthFraction = width / ScanReferencePageWidthPx,
                        kind = tool.kind
                    )
                },
                onStrokeMove = { point ->
                    activeStroke?.let { stroke ->
                        activeStroke = stroke.copy(points = stroke.points + point)
                    }
                },
                onStrokeEnd = {
                    activeStroke?.takeIf { it.points.size > 1 }?.let { stroke ->
                        strokesByPage = strokesByPage.withUpdatedPageStrokes(page.pageId) { strokes ->
                            strokes + stroke
                        }
                    }
                    activeStroke = null
                },
                onErase = { point ->
                    strokesByPage = strokesByPage.withUpdatedPageStrokes(page.pageId) { strokes ->
                        eraseScanStrokes(strokes, point, ScanEraserRadiusFraction)
                    }
                    activeStroke = null
                }
            )

            ScanTopNavigation(
                title = workbook.title,
                pageNumber = page.pageNumber,
                position = "${pageIndex + 1}/${workbook.pages.size}",
                canGoPrevious = pageIndex > 0,
                canGoNext = pageIndex < workbook.pages.lastIndex,
                currentTool = currentTool,
                lastDrawingTool = lastDrawingTool,
                penWidth = penWidth,
                toolMenuExpanded = toolMenuExpanded,
                canUndoInk = pageStrokes.isNotEmpty(),
                onPrevious = { movePage(-1) },
                onNext = { movePage(1) },
                onCyclePenWidth = {
                    penWidth = penWidth.nextScanPenWidth()
                },
                onToggleToolMenu = {
                    if (currentTool.kind == ScanToolKind.Eraser) {
                        currentTool = lastDrawingTool
                        toolMenuExpanded = false
                    } else {
                        toolMenuExpanded = !toolMenuExpanded
                    }
                },
                onSelectTool = { tool ->
                    currentTool = tool
                    if (tool.kind != ScanToolKind.Eraser) {
                        lastDrawingTool = tool
                    }
                    toolMenuExpanded = false
                },
                onSelectEraser = {
                    currentTool = ScanEraserTool
                    toolMenuExpanded = false
                },
                onUndoInk = {
                    strokesByPage = strokesByPage.withUpdatedPageStrokes(page.pageId) { strokes ->
                        strokes.dropLast(1)
                    }
                    activeStroke = null
                },
                modifier = Modifier.align(Alignment.TopCenter)
            )

            ScanAnswerPanel(
                page = page,
                expanded = answerPanelExpanded,
                answers = answers,
                grades = grades,
                submittingFields = submittingFields,
                onToggleExpanded = { answerPanelExpanded = !answerPanelExpanded },
                onAnswerChange = { fieldKey, value ->
                    answers = answers + (fieldKey to value)
                    grades = grades - fieldKey
                    message = null
                },
                onSubmitAnswer = { problem, field ->
                    val submitKey = fieldKey(page, field)
                    if (submitKey !in submittingFields) {
                        submittingFields = submittingFields + submitKey
                        scope.launch {
                            runCatching {
                                val dbProblem = container.dao.getProblem(problem.problemId)
                                    ?: error("등록된 문제가 없습니다.")
                                val submittedAnswers = problem.answerFields.associate { answerField ->
                                    answerField.fieldId to answers[fieldKey(page, answerField)].orEmpty()
                                }
                                container.submitPracticeAnswerUseCase.submit(
                                    studentId = "student-demo",
                                    problem = dbProblem,
                                    generatedProblemId = null,
                                    submittedAnswers = submittedAnswers,
                                    solutionImagePath = null
                                )
                            }.onSuccess { result ->
                                val fieldGrades = result.gradingResult.fieldResults
                                    .filter { !it.answerFieldId.isNullOrBlank() }
                                    .associate { fieldResult ->
                                        "${page.pageId}:${fieldResult.answerFieldId}" to fieldResult.isCorrect
                                    }
                                grades = grades + fieldGrades
                                message = when {
                                    result.gradingResult.requiresManualReview -> "확인 필요"
                                    result.gradingResult.isCorrect -> "정답입니다."
                                    result.autoMoveNext -> "오답입니다."
                                    else -> "다시 확인해보세요. ${result.remainingTryCount}회 남음"
                                }
                            }.onFailure { error ->
                                message = "제출 실패: ${error.message ?: "알 수 없는 오류"}"
                            }
                            submittingFields = submittingFields - submitKey
                        }
                    }
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = answerPanelBottomPadding)
            )

            message?.let {
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = answerPanelBottomPadding + if (answerPanelExpanded) 286.dp else 82.dp),
                    color = Color(0xEE111827),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = it,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
private fun ScanTopNavigation(
    title: String,
    pageNumber: Int,
    position: String,
    canGoPrevious: Boolean,
    canGoNext: Boolean,
    currentTool: ScanDrawingTool,
    lastDrawingTool: ScanDrawingTool,
    penWidth: Float,
    toolMenuExpanded: Boolean,
    canUndoInk: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onCyclePenWidth: () -> Unit,
    onToggleToolMenu: () -> Unit,
    onSelectTool: (ScanDrawingTool) -> Unit,
    onSelectEraser: () -> Unit,
    onUndoInk: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .zIndex(4f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ScanNavButton(
                text = "‹",
                direction = ScanNavDirection.Previous,
                enabled = canGoPrevious,
                onClick = onPrevious
            )
            ScanLocationLabel(
                title = title,
                pageInfo = "${pageNumber}쪽 · $position",
                modifier = Modifier.weight(1f)
            )
            ScanPenWidthButton(width = penWidth, onClick = onCyclePenWidth)
            val visibleTool = if (currentTool.kind == ScanToolKind.Eraser) lastDrawingTool else currentTool
            ScanToolCircleButton(
                tool = visibleTool,
                selected = currentTool.kind != ScanToolKind.Eraser,
                onClick = onToggleToolMenu
            )
            ScanEraserButton(
                selected = currentTool.kind == ScanToolKind.Eraser,
                onClick = onSelectEraser
            )
            ScanUndoButton(
                enabled = canUndoInk,
                onClick = onUndoInk
            )
            ScanNavButton(
                text = "›",
                direction = ScanNavDirection.Next,
                enabled = canGoNext,
                onClick = onNext
            )
        }
        if (toolMenuExpanded) {
            Column(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 42.dp, end = 126.dp)
                    .zIndex(8f),
                verticalArrangement = Arrangement.spacedBy(7.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                ScanDrawingTools.forEach { tool ->
                    ScanToolCircleButton(
                        tool = tool,
                        selected = currentTool.id == tool.id && currentTool.kind != ScanToolKind.Eraser,
                        onClick = { onSelectTool(tool) }
                    )
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalComposeUiApi::class)
private fun ScanPageViewer(
    page: ScanPage,
    currentTool: ScanDrawingTool,
    penWidth: Float,
    strokes: List<ScanInkStroke>,
    activeStroke: ScanInkStroke?,
    onStrokeStart: (Offset, ScanDrawingTool, Float) -> Unit,
    onStrokeMove: (Offset) -> Unit,
    onStrokeEnd: () -> Unit,
    onErase: (Offset) -> Unit
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val focusManager = LocalFocusManager.current
    val pageBitmap = remember(page.assetPath) {
        context.assets.open(page.assetPath).use { input ->
            BitmapFactory.decodeStream(input).asImageBitmap()
        }
    }
    var userScale by remember(page.pageId) { mutableFloatStateOf(1f) }
    var pan by remember(page.pageId) { mutableStateOf(Offset.Zero) }
    var lastTransformCentroid by remember(page.pageId) { mutableStateOf<Offset?>(null) }
    var lastTransformSpan by remember(page.pageId) { mutableFloatStateOf(0f) }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFE5E7EB))
    ) {
        val viewportWidthPx = constraints.maxWidth.toFloat().coerceAtLeast(1f)
        val viewportHeightPx = constraints.maxHeight.toFloat().coerceAtLeast(1f)
        val topInsetPx = with(density) { 82.dp.toPx() }
        val bottomInsetPx = with(density) { 104.dp.toPx() }
        val fitScale = viewportWidthPx / page.width.toFloat()
        val basePageWidthPx = page.width * fitScale
        val basePageHeightPx = page.height * fitScale
        val baseOffset = Offset(
            x = (viewportWidthPx - basePageWidthPx) / 2f,
            y = topInsetPx
        )
        val pageWidthDp = with(density) { basePageWidthPx.toDp() }
        val pageHeightDp = with(density) { basePageHeightPx.toDp() }

        fun clampPan(rawPan: Offset, scale: Float): Offset {
            val scaledSize = Size(basePageWidthPx * scale, basePageHeightPx * scale)
            return clampScanPan(
                pan = rawPan,
                viewportWidth = viewportWidthPx,
                viewportHeight = viewportHeightPx - bottomInsetPx,
                baseOffset = baseOffset,
                contentSize = scaledSize
            )
        }

        fun resetTransformTracking() {
            lastTransformCentroid = null
            lastTransformSpan = 0f
        }

        val transformModifier = Modifier.pointerInteropFilter { event ->
            if (event.hasStylusLikePointer()) {
                resetTransformTracking()
                return@pointerInteropFilter false
            }
            val fingerCount = event.fingerPointerCount()
            if (fingerCount == 0) {
                resetTransformTracking()
                return@pointerInteropFilter false
            }
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN,
                MotionEvent.ACTION_POINTER_DOWN,
                MotionEvent.ACTION_MOVE -> {
                    focusManager.clearFocus(force = true)
                    val centroid = event.fingerCentroid()
                    val span = if (fingerCount >= 2) event.fingerSpan(centroid) else 0f
                    val previousCentroid = lastTransformCentroid
                    if (previousCentroid != null) {
                        val panDelta = Offset(
                            x = centroid.x - previousCentroid.x,
                            y = centroid.y - previousCentroid.y
                        )
                        val oldScale = userScale
                        val zoom = if (span > 0f && lastTransformSpan > 0f) {
                            span / lastTransformSpan
                        } else {
                            1f
                        }
                        val newScale = (oldScale * zoom).coerceIn(MinScanUserScale, MaxScanUserScale)
                        val nextPan = if (newScale != oldScale) {
                            val localBefore = Offset(
                                x = (centroid.x - baseOffset.x - pan.x) / oldScale,
                                y = (centroid.y - baseOffset.y - pan.y) / oldScale
                            )
                            Offset(
                                x = centroid.x - baseOffset.x - localBefore.x * newScale + panDelta.x,
                                y = centroid.y - baseOffset.y - localBefore.y * newScale + panDelta.y
                            )
                        } else {
                            Offset(pan.x + panDelta.x, pan.y + panDelta.y)
                        }
                        userScale = newScale
                        pan = clampPan(nextPan, newScale)
                    }
                    lastTransformCentroid = centroid
                    lastTransformSpan = span
                    true
                }
                MotionEvent.ACTION_POINTER_UP,
                MotionEvent.ACTION_UP,
                MotionEvent.ACTION_CANCEL -> {
                    resetTransformTracking()
                    true
                }
                else -> true
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(transformModifier)
        ) {
            Box(
                modifier = Modifier
                    .offset { IntOffset(baseOffset.x.roundToInt(), baseOffset.y.roundToInt()) }
                    .size(width = pageWidthDp, height = pageHeightDp)
                    .graphicsLayer {
                        scaleX = userScale
                        scaleY = userScale
                        translationX = pan.x
                        translationY = pan.y
                        transformOrigin = TransformOrigin(0f, 0f)
                    }
                    .background(Color.White)
            ) {
                Image(
                    bitmap = pageBitmap,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.FillBounds
                )
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInteropFilter { event ->
                            val pointerIndex = event.actionIndex.coerceAtLeast(0)
                            if (!event.isStylusLike(pointerIndex)) {
                                return@pointerInteropFilter false
                            }
                            val point = normalizedPagePoint(
                                Offset(event.x, event.y),
                                basePageWidthPx,
                                basePageHeightPx
                            ) ?: return@pointerInteropFilter true
                            when (event.actionMasked) {
                                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                                    focusManager.clearFocus(force = true)
                                    if (currentTool.kind == ScanToolKind.Eraser) {
                                        onErase(point)
                                    } else {
                                        onStrokeStart(point, currentTool, penWidth)
                                    }
                                    true
                                }
                                MotionEvent.ACTION_MOVE -> {
                                    if (currentTool.kind == ScanToolKind.Eraser) {
                                        onErase(point)
                                    } else {
                                        onStrokeMove(point)
                                    }
                                    true
                                }
                                MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_CANCEL -> {
                                    onStrokeEnd()
                                    true
                                }
                                else -> true
                            }
                        }
                ) {
                    (strokes + listOfNotNull(activeStroke?.takeIf { it.points.size > 1 })).forEach { stroke ->
                        stroke.points.zipWithNext().forEach { (a, b) ->
                            drawLine(
                                color = stroke.color,
                                start = Offset(a.x * size.width, a.y * size.height),
                                end = Offset(b.x * size.width, b.y * size.height),
                                strokeWidth = (stroke.widthFraction * size.width).coerceAtLeast(
                                    if (stroke.kind == ScanToolKind.Highlighter) 10f else 2.4f
                                ),
                                cap = StrokeCap.Round
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ScanAnswerPanel(
    page: ScanPage,
    expanded: Boolean,
    answers: Map<String, String>,
    grades: Map<String, Boolean>,
    submittingFields: Set<String>,
    onToggleExpanded: () -> Unit,
    onAnswerChange: (String, String) -> Unit,
    onSubmitAnswer: (ScanProblem, ScanAnswerField) -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val rows = remember(page.pageId) {
        page.problems.flatMap { problem ->
            problem.answerFields.map { field -> problem to field }
        }
    }
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 10.dp, vertical = 8.dp)
            .zIndex(4f),
        color = Color(0xF7FFFFFF),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, Color(0x332563EB)),
        tonalElevation = 4.dp
    ) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(38.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    modifier = Modifier
                        .width(96.dp)
                        .fillMaxHeight()
                        .clickable(onClick = onToggleExpanded),
                    color = Color(0xFFEFF6FF),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, Color(0x552563EB))
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = if (expanded) "답안 접기" else "답안 열기",
                            color = Color(0xFF2563EB),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Text(
                    text = "${page.pageNumber}쪽 · ${rows.size}칸",
                    color = Color(0xFF4B5563),
                    fontSize = 11.sp,
                    maxLines = 1
                )
            }
            if (expanded) {
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 260.dp)
                ) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(end = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(7.dp)
                    ) {
                        items(rows, key = { (problem, field) -> "${problem.problemId}:${field.fieldId}" }) { (problem, field) ->
                            val key = fieldKey(page, field)
                            ScanAnswerPanelRow(
                                problem = problem,
                                field = field,
                                value = answers[key].orEmpty(),
                                grade = grades[key],
                                enabled = key !in submittingFields,
                                onValueChange = { onAnswerChange(key, it) },
                                onSubmit = { onSubmitAnswer(problem, field) }
                            )
                        }
                    }
                    ScanAnswerScrollbar(
                        state = listState,
                        modifier = Modifier.align(Alignment.CenterEnd)
                    )
                }
            }
        }
    }
}

@Composable
private fun ScanAnswerPanelRow(
    problem: ScanProblem,
    field: ScanAnswerField,
    value: String,
    grade: Boolean?,
    enabled: Boolean,
    onValueChange: (String) -> Unit,
    onSubmit: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = problem.label.ifBlank { "문제" },
            modifier = Modifier.width(54.dp),
            color = Color(0xFF374151),
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        ScanAnswerBox(
            field = field,
            value = value,
            enabled = enabled,
            grade = grade,
            onValueChange = onValueChange,
            modifier = Modifier
                .weight(1f)
                .height(58.dp)
        )
        ScanSubmitButton(
            enabled = enabled,
            grade = grade,
            onClick = onSubmit,
            modifier = Modifier.size(width = 52.dp, height = 58.dp)
        )
    }
}

@Composable
private fun ScanAnswerScrollbar(
    state: androidx.compose.foundation.lazy.LazyListState,
    modifier: Modifier = Modifier
) {
    val layoutInfo = state.layoutInfo
    val totalItems = layoutInfo.totalItemsCount
    val visibleItems = layoutInfo.visibleItemsInfo.size
    val canScroll = state.canScrollBackward || state.canScrollForward
    if (!canScroll || totalItems == 0 || visibleItems == 0) return

    BoxWithConstraints(
        modifier = modifier
            .width(4.dp)
            .fillMaxHeight()
    ) {
        val trackHeight = maxHeight
        val visibleFraction = (visibleItems.toFloat() / totalItems.toFloat()).coerceIn(0.15f, 1f)
        val thumbHeight = trackHeight * visibleFraction
        val maxFirst = (totalItems - visibleItems).coerceAtLeast(1)
        val progress = (state.firstVisibleItemIndex.toFloat() / maxFirst.toFloat()).coerceIn(0f, 1f)
        val thumbOffset = (trackHeight - thumbHeight) * progress

        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(99.dp))
                .background(Color(0x112563EB))
        )
        Box(
            modifier = Modifier
                .offset(y = thumbOffset)
                .fillMaxWidth()
                .height(thumbHeight)
                .clip(RoundedCornerShape(99.dp))
                .background(Color(0x882563EB))
        )
    }
}

@Composable
private fun ScanAnswerBox(
    field: ScanAnswerField,
    value: String,
    enabled: Boolean,
    grade: Boolean?,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val borderColor = when (grade) {
        true -> Color(0xFF2563EB)
        false -> Color(0xFFDC2626)
        null -> if (enabled) Color(0xFF111827) else Color(0xFF6B7280)
    }
    Surface(
        modifier = modifier.border(1.5.dp, borderColor, RoundedCornerShape(7.dp)),
        color = Color(0xEEFFFFFF),
        shape = RoundedCornerShape(7.dp),
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 5.dp, vertical = 3.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = field.label,
                color = borderColor,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                enabled = enabled,
                singleLine = true,
                textStyle = TextStyle(
                    color = Color(0xFF111827),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                ),
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun ScanSubmitButton(
    enabled: Boolean,
    grade: Boolean?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val color = when (grade) {
        true -> Color(0xFF2563EB)
        false -> Color(0xFFDC2626)
        null -> Color(0xFF374151)
    }
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.defaultMinSize(minWidth = 0.dp, minHeight = 0.dp),
        shape = RoundedCornerShape(7.dp),
        border = BorderStroke(1.2.dp, color),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = Color(0xEEFFFFFF),
            contentColor = color,
            disabledContainerColor = Color(0xAAFFFFFF),
            disabledContentColor = Color(0x886B7280)
        ),
        contentPadding = PaddingValues(0.dp)
    ) {
        Text("✓", fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ScanPenWidthButton(
    width: Float,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(30.dp)
            .semantics { contentDescription = "펜 굵기 ${width.toInt()}" }
            .clickable(onClick = onClick)
            .background(Color.White, CircleShape)
            .border(1.dp, Color(0xFFE5E7EB), CircleShape)
            .padding(4.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawLine(
                color = Color(0xFF111827),
                start = Offset(4.dp.toPx(), size.height / 2f),
                end = Offset(size.width - 4.dp.toPx(), size.height / 2f),
                strokeWidth = width.coerceIn(3f, 11f),
                cap = StrokeCap.Round
            )
        }
    }
}

@Composable
private fun ScanToolCircleButton(
    tool: ScanDrawingTool,
    selected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(30.dp)
            .semantics { contentDescription = tool.label }
            .clickable(onClick = onClick)
            .border(
                width = if (selected) 3.dp else 1.dp,
                color = if (selected) MaterialTheme.colorScheme.primary else Color(0xFFE5E7EB),
                shape = CircleShape
            )
            .padding(4.dp)
            .background(tool.color, CircleShape)
    )
}

@Composable
private fun ScanEraserButton(
    selected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(30.dp)
            .semantics { contentDescription = ScanEraserTool.label }
            .clickable(onClick = onClick)
            .background(Color.White, RoundedCornerShape(19.dp))
            .border(
                width = if (selected) 3.dp else 1.dp,
                color = if (selected) MaterialTheme.colorScheme.primary else Color(0xFFE5E7EB),
                shape = RoundedCornerShape(19.dp)
            ),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(20.dp)) {
            rotate(degrees = -28f) {
                val eraserSize = Size(18.dp.toPx(), 12.dp.toPx())
                val topLeft = Offset(3.dp.toPx(), 6.dp.toPx())
                drawRoundRect(
                    color = Color(0xFFFFE4E6),
                    topLeft = topLeft,
                    size = eraserSize,
                    cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx())
                )
                drawLine(
                    color = Color(0xFF9CA3AF),
                    start = Offset(topLeft.x + 7.dp.toPx(), topLeft.y),
                    end = Offset(topLeft.x + 7.dp.toPx(), topLeft.y + eraserSize.height),
                    strokeWidth = 1.2.dp.toPx()
                )
            }
        }
    }
}

@Composable
private fun ScanUndoButton(
    enabled: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(30.dp)
            .semantics { contentDescription = "뒤로가기" }
            .clickable(enabled = enabled, onClick = onClick)
            .background(Color.White, RoundedCornerShape(19.dp))
            .border(1.dp, Color(0xFFE5E7EB), RoundedCornerShape(19.dp)),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(20.dp)) {
            val color = if (enabled) Color(0xFF374151) else Color(0xFFCBD5E1)
            val stroke = 2.2.dp.toPx()
            val centerY = size.height * 0.52f
            val leftX = size.width * 0.22f
            val rightX = size.width * 0.78f
            drawLine(color, Offset(rightX, centerY), Offset(leftX, centerY), stroke, cap = StrokeCap.Round)
            drawLine(color, Offset(leftX, centerY), Offset(leftX + 6.dp.toPx(), centerY - 5.dp.toPx()), stroke, cap = StrokeCap.Round)
            drawLine(color, Offset(leftX, centerY), Offset(leftX + 6.dp.toPx(), centerY + 5.dp.toPx()), stroke, cap = StrokeCap.Round)
            drawLine(
                color = color.copy(alpha = 0.55f),
                start = Offset(rightX, centerY),
                end = Offset(rightX, size.height * 0.30f),
                strokeWidth = stroke,
                cap = StrokeCap.Round
            )
        }
    }
}

@Composable
private fun ScanLocationLabel(
    title: String,
    pageInfo: String,
    modifier: Modifier = Modifier
) {
    val hasSkinHeader = LocalWorkbookSkin.current?.assetPath("problemHeaderPill") != null
    Box(
        modifier = modifier
            .height(44.dp)
            .clip(RoundedCornerShape(14.dp)),
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
                text = title,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = pageInfo,
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
private fun ScanNavButton(
    text: String,
    direction: ScanNavDirection,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val assetKey = if (direction == ScanNavDirection.Previous) "navArrowPrevious" else "navArrowNext"
    val hasSkinArrow = LocalWorkbookSkin.current?.assetPath(assetKey) != null
    val shape = if (direction == ScanNavDirection.Previous) PreviousScanNavShape else NextScanNavShape
    Box(
        modifier = Modifier.size(width = 48.dp, height = 44.dp),
        contentAlignment = Alignment.Center
    ) {
        SkinAssetImage(
            assetKey = assetKey,
            modifier = Modifier
                .align(Alignment.Center)
                .size(width = 44.dp, height = 38.dp),
            contentScale = ContentScale.FillBounds,
            alpha = if (enabled) 1f else 0.34f
        )
        OutlinedButton(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier
                .matchParentSize()
                .defaultMinSize(minWidth = 0.dp, minHeight = 0.dp),
            shape = shape,
            border = if (hasSkinArrow) null else BorderStroke(1.4.dp, Color(0xFF2563EB)),
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = if (hasSkinArrow) Color.Transparent else Color(0xF2FFFFFF),
                contentColor = Color(0xFF2563EB),
                disabledContainerColor = if (hasSkinArrow) Color.Transparent else Color(0xAAF9FAFB),
                disabledContentColor = Color(0x662563EB)
            ),
            contentPadding = PaddingValues(0.dp)
        ) {
            if (!hasSkinArrow) {
                Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
        }
    }
}

private fun loadScanWorkbookMvp(context: Context, workbookId: String): ScanWorkbook {
    val root = context.assets.open("scan_mvp/workbook.json").use { input ->
        JSONObject(input.bufferedReader(Charsets.UTF_8).readText())
    }
    require(detectWorkbookManifestType(root) == WorkbookManifestType.ScanPageCoordinates) {
        "scan_mvp/workbook.json은 스캔형 문제집 JSON이어야 합니다."
    }
    val workbookObject = root.optJSONObject("workbook")
    val manifestWorkbookId = workbookObject?.optString("workbookId").orEmpty()
    return ScanWorkbook(
        workbookId = manifestWorkbookId.ifBlank { workbookId },
        title = root.optString("title")
            .ifBlank { workbookObject?.optString("title").orEmpty() }
            .ifBlank { "스캔 문제집" },
        pages = root.getJSONArray("pages").mapObjects { pageJson ->
            ScanPage(
                pageId = pageJson.getString("pageId"),
                pageNumber = pageJson.getInt("pageNumber"),
                assetPath = pageJson.getString("assetPath"),
                width = pageJson.getInt("width"),
                height = pageJson.getInt("height"),
                problems = pageJson.getJSONArray("problems").mapObjects { problemJson ->
                    ScanProblem(
                        problemId = problemJson.getString("problemId"),
                        label = problemJson.optString("label"),
                        problemRect = problemJson.getJSONObject("problemRect").toScanRect(),
                        answerFields = problemJson.getJSONArray("answerFields").mapObjects { fieldJson ->
                            ScanAnswerField(
                                fieldId = fieldJson.getString("fieldId"),
                                label = fieldJson.optString("label", "답"),
                                answer = fieldJson.optString("answer"),
                                rect = fieldJson.getJSONObject("rect").toScanRect()
                            )
                        }
                    )
                }
            )
        }
    )
}

private fun gradePage(page: ScanPage, answers: Map<String, String>): Map<String, Boolean> {
    return page.problems
        .flatMap { it.answerFields }
        .associate { field ->
            val key = fieldKey(page, field)
            key to (answers[key].orEmpty().canonicalAnswer() == field.answer.canonicalAnswer())
        }
}

private fun normalizedPagePoint(point: Offset, pageWidth: Float, pageHeight: Float): Offset? {
    val x = point.x / pageWidth
    val y = point.y / pageHeight
    return if (x in 0f..1f && y in 0f..1f) Offset(x, y) else null
}

private fun clampScanPan(
    pan: Offset,
    viewportWidth: Float,
    viewportHeight: Float,
    baseOffset: Offset,
    contentSize: Size
): Offset {
    val slackX = viewportWidth * 0.38f
    val slackY = viewportHeight * 0.38f
    val minX = min(0f, viewportWidth - baseOffset.x - contentSize.width) - slackX
    val maxX = max(0f, viewportWidth - baseOffset.x) + slackX
    val minY = min(0f, viewportHeight - baseOffset.y - contentSize.height) - slackY
    val maxY = max(0f, viewportHeight - baseOffset.y) + slackY
    return Offset(
        x = pan.x.coerceIn(minX, maxX),
        y = pan.y.coerceIn(minY, maxY)
    )
}

private fun fieldKey(page: ScanPage, field: ScanAnswerField): String = "${page.pageId}:${field.fieldId}"

private fun String.canonicalAnswer(): String {
    return trim()
        .replace("，", ",")
        .replace("、", ",")
        .replace(" ", "")
        .replace("\n", "")
        .lowercase()
}

private fun Map<String, List<ScanInkStroke>>.withUpdatedPageStrokes(
    pageId: String,
    update: (List<ScanInkStroke>) -> List<ScanInkStroke>
): Map<String, List<ScanInkStroke>> {
    return this + (pageId to update(this[pageId].orEmpty()))
}

private fun eraseScanStrokes(
    strokes: List<ScanInkStroke>,
    point: Offset,
    radius: Float
): List<ScanInkStroke> {
    val updated = mutableListOf<ScanInkStroke>()
    strokes.forEach { stroke ->
        var segment = mutableListOf<Offset>()
        stroke.points.forEachIndexed { index, strokePoint ->
            val previous = stroke.points.getOrNull(index - 1)
            val shouldErase = distance(strokePoint, point) <= radius ||
                (previous != null && distanceToSegment(point, previous, strokePoint) <= radius)
            if (shouldErase) {
                if (segment.size > 1) {
                    updated += stroke.copy(points = segment)
                }
                segment = mutableListOf()
            } else {
                segment += strokePoint
            }
        }
        if (segment.size > 1) {
            updated += stroke.copy(points = segment)
        }
    }
    return updated
}

private fun distance(a: Offset, b: Offset): Float {
    val dx = a.x - b.x
    val dy = a.y - b.y
    return sqrt(dx * dx + dy * dy)
}

private fun distanceToSegment(point: Offset, a: Offset, b: Offset): Float {
    val dx = b.x - a.x
    val dy = b.y - a.y
    if (dx == 0f && dy == 0f) return distance(point, a)
    val t = (((point.x - a.x) * dx + (point.y - a.y) * dy) / (dx * dx + dy * dy)).coerceIn(0f, 1f)
    return distance(point, Offset(a.x + t * dx, a.y + t * dy))
}

private fun MotionEvent.isStylusLike(pointerIndex: Int): Boolean {
    if (pointerIndex !in 0 until pointerCount) return false
    return when (getToolType(pointerIndex)) {
        MotionEvent.TOOL_TYPE_STYLUS,
        MotionEvent.TOOL_TYPE_ERASER -> true
        else -> false
    }
}

private fun MotionEvent.hasStylusLikePointer(): Boolean {
    for (index in 0 until pointerCount) {
        if (isStylusLike(index)) return true
    }
    return false
}

private fun MotionEvent.fingerPointerCount(): Int {
    var count = 0
    for (index in 0 until pointerCount) {
        if (getToolType(index) == MotionEvent.TOOL_TYPE_FINGER) count += 1
    }
    return count
}

private fun MotionEvent.fingerCentroid(): Offset {
    var x = 0f
    var y = 0f
    var count = 0
    for (index in 0 until pointerCount) {
        if (getToolType(index) == MotionEvent.TOOL_TYPE_FINGER) {
            x += getX(index)
            y += getY(index)
            count += 1
        }
    }
    return if (count == 0) Offset.Zero else Offset(x / count, y / count)
}

private fun MotionEvent.fingerSpan(centroid: Offset): Float {
    var total = 0f
    var count = 0
    for (index in 0 until pointerCount) {
        if (getToolType(index) == MotionEvent.TOOL_TYPE_FINGER) {
            total += distance(Offset(getX(index), getY(index)), centroid)
            count += 1
        }
    }
    return if (count == 0) 0f else total / count
}

private fun Float.nextScanPenWidth(): Float {
    val index = ScanPenWidthOptions.indexOfFirst { it == this }
    return if (index == -1 || index == ScanPenWidthOptions.lastIndex) {
        ScanPenWidthOptions.first()
    } else {
        ScanPenWidthOptions[index + 1]
    }
}

private fun JSONObject.toScanRect(): ScanRect {
    return ScanRect(
        x = optDouble("x").toFloat(),
        y = optDouble("y").toFloat(),
        width = optDouble("w").toFloat(),
        height = optDouble("h").toFloat()
    )
}

private inline fun <T> JSONArray.mapObjects(block: (JSONObject) -> T): List<T> {
    return List(length()) { index -> block(getJSONObject(index)) }
}

private enum class ScanNavDirection {
    Previous,
    Next
}

private enum class ScanToolKind {
    Pen,
    Highlighter,
    Eraser
}

private data class ScanDrawingTool(
    val id: String,
    val label: String,
    val color: Color,
    val kind: ScanToolKind
)

private val ScanDrawingTools = listOf(
    ScanDrawingTool("black", "검정 펜", Color(0xFF111827), ScanToolKind.Pen),
    ScanDrawingTool("red", "빨강 펜", Color(0xFFDC2626), ScanToolKind.Pen),
    ScanDrawingTool("blue", "파랑 펜", Color(0xFF2563EB), ScanToolKind.Pen),
    ScanDrawingTool("highlight_yellow", "노랑 형광펜", Color(0x44FFD400), ScanToolKind.Highlighter)
)

private val ScanEraserTool = ScanDrawingTool("eraser", "영역 지우개", Color(0xFF6B7280), ScanToolKind.Eraser)
private val ScanPenWidthOptions = listOf(3f, 5f, 8f, 11f)

private val PreviousScanNavShape = GenericShape { size, _ ->
    moveTo(0f, size.height / 2f)
    lineTo(size.width * 0.20f, 0f)
    lineTo(size.width, 0f)
    lineTo(size.width, size.height)
    lineTo(size.width * 0.20f, size.height)
    close()
}

private val NextScanNavShape = GenericShape { size, _ ->
    moveTo(0f, 0f)
    lineTo(size.width * 0.80f, 0f)
    lineTo(size.width, size.height / 2f)
    lineTo(size.width * 0.80f, size.height)
    lineTo(0f, size.height)
    close()
}

private data class ScanWorkbook(
    val workbookId: String,
    val title: String,
    val pages: List<ScanPage>
)

private data class ScanPage(
    val pageId: String,
    val pageNumber: Int,
    val assetPath: String,
    val width: Int,
    val height: Int,
    val problems: List<ScanProblem>
)

private data class ScanProblem(
    val problemId: String,
    val label: String,
    val problemRect: ScanRect,
    val answerFields: List<ScanAnswerField>
)

private data class ScanAnswerField(
    val fieldId: String,
    val label: String,
    val answer: String,
    val rect: ScanRect
)

private data class ScanRect(
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float
)

private data class ScanInkStroke(
    val points: List<Offset>,
    val color: Color = Color(0xFF111827),
    val widthFraction: Float = 0.0042f,
    val kind: ScanToolKind = ScanToolKind.Pen
)

private const val MinScanUserScale = 0.45f
private const val MaxScanUserScale = 8f
private const val ScanReferencePageWidthPx = 720f
private const val ScanEraserRadiusFraction = 0.026f
