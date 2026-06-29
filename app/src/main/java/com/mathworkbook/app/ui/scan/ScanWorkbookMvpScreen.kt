package com.mathworkbook.app.ui.scan

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.mathworkbook.app.core.files.WorkbookManifestType
import com.mathworkbook.app.core.files.detectWorkbookManifestType
import com.mathworkbook.app.ui.skin.LocalWorkbookSkin
import com.mathworkbook.app.ui.skin.SkinAssetImage
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

@Composable
fun ScanWorkbookMvpScreen(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val workbook = remember { loadScanWorkbookMvp(context) }
    var pageIndex by remember { mutableIntStateOf(0) }
    var inkEnabled by remember { mutableStateOf(false) }
    var answers by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var grades by remember { mutableStateOf<Map<String, Boolean>>(emptyMap()) }
    var strokesByPage by remember { mutableStateOf<Map<String, List<ScanInkStroke>>>(emptyMap()) }
    var activeStroke by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var message by remember { mutableStateOf<String?>(null) }

    val page = workbook.pages[pageIndex]
    val pageStrokes = strokesByPage[page.pageId].orEmpty()

    fun movePage(delta: Int) {
        val next = (pageIndex + delta).coerceIn(0, workbook.pages.lastIndex)
        if (next == pageIndex) return
        pageIndex = next
        activeStroke = emptyList()
        message = null
    }

    Surface(modifier = modifier.fillMaxSize(), color = Color(0xFFE5E7EB)) {
        Box(modifier = Modifier.fillMaxSize()) {
            ScanPageViewer(
                page = page,
                inkEnabled = inkEnabled,
                answers = answers,
                grades = grades,
                strokes = pageStrokes,
                activeStroke = activeStroke,
                onAnswerChange = { fieldKey, value ->
                    answers = answers + (fieldKey to value)
                    grades = grades - fieldKey
                    message = null
                },
                onStrokeStart = { point ->
                    activeStroke = listOf(point)
                },
                onStrokeMove = { point ->
                    if (activeStroke.isNotEmpty()) {
                        activeStroke = activeStroke + point
                    }
                },
                onStrokeEnd = {
                    if (activeStroke.size > 1) {
                        val stroke = ScanInkStroke(points = activeStroke)
                        strokesByPage = strokesByPage.withUpdatedPageStrokes(page.pageId) { strokes ->
                            strokes + stroke
                        }
                    }
                    activeStroke = emptyList()
                }
            )

            ScanTopNavigation(
                title = workbook.title,
                pageNumber = page.pageNumber,
                position = "${pageIndex + 1}/${workbook.pages.size}",
                canGoPrevious = pageIndex > 0,
                canGoNext = pageIndex < workbook.pages.lastIndex,
                onPrevious = { movePage(-1) },
                onNext = { movePage(1) },
                modifier = Modifier.align(Alignment.TopCenter)
            )

            ScanFloatingActions(
                inkEnabled = inkEnabled,
                canUndoInk = pageStrokes.isNotEmpty(),
                onToggleInk = {
                    inkEnabled = !inkEnabled
                    activeStroke = emptyList()
                },
                onUndoInk = {
                    strokesByPage = strokesByPage.withUpdatedPageStrokes(page.pageId) { strokes ->
                        strokes.dropLast(1)
                    }
                    activeStroke = emptyList()
                },
                onGrade = {
                    val result = gradePage(page, answers)
                    grades = grades + result
                    val correctCount = result.values.count { it }
                    message = "$correctCount/${result.size} 정답"
                },
                modifier = Modifier.align(Alignment.BottomEnd)
            )

            message?.let {
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 16.dp),
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
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .zIndex(4f),
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
        ScanNavButton(
            text = "›",
            direction = ScanNavDirection.Next,
            enabled = canGoNext,
            onClick = onNext
        )
    }
}

@Composable
private fun ScanFloatingActions(
    inkEnabled: Boolean,
    canUndoInk: Boolean,
    onToggleInk: () -> Unit,
    onUndoInk: () -> Unit,
    onGrade: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .padding(end = 12.dp, bottom = 14.dp)
            .zIndex(5f),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (inkEnabled && canUndoInk) {
            ScanRoundButton(
                label = "↶",
                selected = false,
                onClick = onUndoInk
            )
        }
        ScanRoundButton(
            label = "필",
            selected = inkEnabled,
            onClick = onToggleInk
        )
        ScanRoundButton(
            label = "채",
            selected = true,
            onClick = onGrade
        )
    }
}

@Composable
private fun ScanPageViewer(
    page: ScanPage,
    inkEnabled: Boolean,
    answers: Map<String, String>,
    grades: Map<String, Boolean>,
    strokes: List<ScanInkStroke>,
    activeStroke: List<Offset>,
    onAnswerChange: (String, String) -> Unit,
    onStrokeStart: (Offset) -> Unit,
    onStrokeMove: (Offset) -> Unit,
    onStrokeEnd: () -> Unit
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

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFE5E7EB))
    ) {
        val viewportWidthPx = constraints.maxWidth.toFloat().coerceAtLeast(1f)
        val viewportHeightPx = constraints.maxHeight.toFloat().coerceAtLeast(1f)
        val topInsetPx = with(density) { 82.dp.toPx() }
        val bottomInsetPx = with(density) { 14.dp.toPx() }
        val fitScale = viewportWidthPx / page.width.toFloat()
        val basePageWidthPx = page.width * fitScale
        val basePageHeightPx = page.height * fitScale
        val baseOffset = Offset(
            x = (viewportWidthPx - basePageWidthPx) / 2f,
            y = topInsetPx
        )
        val pageScale = fitScale * userScale
        val pageWidthPx = page.width * pageScale
        val pageHeightPx = page.height * pageScale
        val pageLeftPx = baseOffset.x + pan.x
        val pageTopPx = baseOffset.y + pan.y
        val pageWidthDp = with(density) { pageWidthPx.toDp() }
        val pageHeightDp = with(density) { pageHeightPx.toDp() }

        fun clampPan(rawPan: Offset, scale: Float): Offset {
            val scaledSize = Size(page.width * fitScale * scale, page.height * fitScale * scale)
            return clampScanPan(
                pan = rawPan,
                viewportWidth = viewportWidthPx,
                viewportHeight = viewportHeightPx - bottomInsetPx,
                baseOffset = baseOffset,
                contentSize = scaledSize
            )
        }

        val transformModifier = Modifier.pointerInput(
            page.pageId,
            fitScale,
            userScale,
            pan,
            viewportWidthPx,
            viewportHeightPx
        ) {
            detectTransformGestures { centroid, gesturePan, gestureZoom, _ ->
                focusManager.clearFocus(force = true)
                val oldScale = userScale
                val newScale = (oldScale * gestureZoom).coerceIn(MinScanUserScale, MaxScanUserScale)
                val oldPageScale = fitScale * oldScale
                val newPageScale = fitScale * newScale
                val localBefore = Offset(
                    x = (centroid.x - baseOffset.x - pan.x) / oldPageScale,
                    y = (centroid.y - baseOffset.y - pan.y) / oldPageScale
                )
                val centeredPan = Offset(
                    x = centroid.x - baseOffset.x - localBefore.x * newPageScale,
                    y = centroid.y - baseOffset.y - localBefore.y * newPageScale
                )
                userScale = newScale
                pan = clampPan(centeredPan + gesturePan, newScale)
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(transformModifier)
        ) {
            Box(
                modifier = Modifier
                    .offset { IntOffset(pageLeftPx.roundToInt(), pageTopPx.roundToInt()) }
                    .size(width = pageWidthDp, height = pageHeightDp)
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
                        .then(
                            if (inkEnabled) {
                                Modifier.pointerInput(page.pageId, pageWidthPx, pageHeightPx) {
                                    detectDragGestures(
                                        onDragStart = { start ->
                                            normalizedPagePoint(start, pageWidthPx, pageHeightPx)?.let(onStrokeStart)
                                        },
                                        onDrag = { change, _ ->
                                            normalizedPagePoint(change.position, pageWidthPx, pageHeightPx)?.let(onStrokeMove)
                                            change.consume()
                                        },
                                        onDragEnd = onStrokeEnd,
                                        onDragCancel = onStrokeEnd
                                    )
                                }
                            } else {
                                Modifier
                            }
                        )
                ) {
                    page.problems.forEach { problem ->
                        drawRoundRect(
                            color = Color(0x662563EB),
                            topLeft = Offset(problem.problemRect.x * size.width, problem.problemRect.y * size.height),
                            size = Size(problem.problemRect.width * size.width, problem.problemRect.height * size.height),
                            style = Stroke(width = 2.2f)
                        )
                    }
                    (strokes + listOfNotNull(activeStroke.takeIf { it.size > 1 }?.let(::ScanInkStroke))).forEach { stroke ->
                        stroke.points.zipWithNext().forEach { (a, b) ->
                            drawLine(
                                color = stroke.color,
                                start = Offset(a.x * size.width, a.y * size.height),
                                end = Offset(b.x * size.width, b.y * size.height),
                                strokeWidth = (stroke.widthFraction * size.width).coerceAtLeast(2.4f),
                                cap = StrokeCap.Round
                            )
                        }
                    }
                }
                page.problems.forEach { problem ->
                    problem.answerFields.forEach { field ->
                        val key = fieldKey(page, field)
                        ScanAnswerBox(
                            field = field,
                            value = answers[key].orEmpty(),
                            enabled = !inkEnabled,
                            grade = grades[key],
                            onValueChange = { onAnswerChange(key, it) },
                            modifier = Modifier
                                .offset {
                                    IntOffset(
                                        (field.rect.x * pageWidthPx).roundToInt(),
                                        (field.rect.y * pageHeightPx).roundToInt()
                                    )
                                }
                                .size(
                                    width = with(density) {
                                        max(field.rect.width * pageWidthPx, 52.dp.toPx()).toDp()
                                    },
                                    height = with(density) {
                                        max(field.rect.height * pageHeightPx, 28.dp.toPx()).toDp()
                                    }
                                )
                        )
                    }
                }
            }
        }
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
                fontSize = 10.sp,
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
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                ),
                modifier = Modifier.weight(1f)
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

@Composable
private fun ScanRoundButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.size(48.dp),
        shape = CircleShape,
        border = BorderStroke(1.5.dp, if (selected) MaterialTheme.colorScheme.primary else Color(0xFF7C7585)),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = if (selected) Color(0xF2FFFFFF) else Color(0xEFFFFFFF),
            contentColor = if (selected) MaterialTheme.colorScheme.primary else Color(0xFF4B5563)
        ),
        contentPadding = PaddingValues(0.dp)
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1, fontWeight = FontWeight.Bold)
    }
}

private fun loadScanWorkbookMvp(context: Context): ScanWorkbook {
    val root = context.assets.open("scan_mvp/workbook.json").use { input ->
        JSONObject(input.bufferedReader(Charsets.UTF_8).readText())
    }
    require(detectWorkbookManifestType(root) == WorkbookManifestType.ScanPageCoordinates) {
        "scan_mvp/workbook.json은 스캔형 문제집 JSON이어야 합니다."
    }
    val workbookObject = root.optJSONObject("workbook")
    return ScanWorkbook(
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
    val widthFraction: Float = 0.0042f
)

private const val MinScanUserScale = 0.45f
private const val MaxScanUserScale = 8f
