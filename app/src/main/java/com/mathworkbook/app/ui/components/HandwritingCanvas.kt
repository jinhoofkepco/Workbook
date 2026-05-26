package com.mathworkbook.app.ui.components

import android.content.Context
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.Path as AndroidPath
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.roundToInt
import kotlin.math.sqrt

class Stroke(
    points: List<Offset>,
    val color: Color = Color(0xFF111827),
    val width: Float = 5f,
    val kind: StrokeKind = StrokeKind.Pen
) {
    val points: MutableList<Offset> = points.toMutableList()
    internal val path = AndroidPath()

    init {
        rebuildPath()
    }

    fun addPoint(position: Offset): Boolean {
        val last = points.lastOrNull()
        if (last != null && distance(last, position) < MinInkPointDistancePx) return false
        if (last == null) {
            path.moveTo(position.x, position.y)
        } else {
            path.lineTo(position.x, position.y)
        }
        points += position
        return true
    }

    private fun rebuildPath() {
        path.reset()
        val first = points.firstOrNull() ?: return
        path.moveTo(first.x, first.y)
        points.drop(1).forEach { point ->
            path.lineTo(point.x, point.y)
        }
    }

    fun copyWithPoints(points: List<Offset>): Stroke {
        return Stroke(points = points, color = color, width = width, kind = kind)
    }
}

enum class StrokeKind {
    Pen,
    Highlighter
}

private enum class ToolKind {
    Pen,
    Highlighter,
    Eraser
}

private data class DrawingTool(
    val id: String,
    val label: String,
    val color: Color,
    val width: Float,
    val kind: ToolKind
)

private val DrawingTools = listOf(
    DrawingTool("black", "검정 펜", Color(0xFF111827), 5f, ToolKind.Pen),
    DrawingTool("red", "빨강 펜", Color(0xFFDC2626), 5f, ToolKind.Pen),
    DrawingTool("blue", "파랑 펜", Color(0xFF2563EB), 5f, ToolKind.Pen),
    DrawingTool("highlight_yellow", "노랑 형광펜", Color(0x44FFD400), 18f, ToolKind.Highlighter)
)

private val EraserTool = DrawingTool("eraser", "영역 지우개", Color(0xFF6B7280), 44f, ToolKind.Eraser)
private val PenWidthOptions = listOf(3f, 5f, 8f, 11f)

@Stable
class HandwritingState {
    private val _strokes = mutableListOf<Stroke>()
    val strokes: List<Stroke> get() = _strokes
    internal var onChanged: (() -> Unit)? = null
    private var contentWidthPx: Float = 0f
    private var contentHeightPx: Float = 0f
    private var imageBounds: WorksheetImageBounds? = null

    fun clear() {
        _strokes.clear()
        onChanged?.invoke()
    }

    fun undoLastStroke() {
        if (_strokes.isEmpty()) return
        _strokes.removeAt(_strokes.lastIndex)
        onChanged?.invoke()
    }

    fun updateContentSize(widthPx: Float, heightPx: Float) {
        contentWidthPx = widthPx
        contentHeightPx = heightPx
    }

    fun updateImageBounds(bounds: WorksheetImageBounds) {
        imageBounds = bounds
    }

    fun start(position: Offset, color: Color, width: Float, kind: StrokeKind) {
        _strokes += Stroke(points = listOf(position), color = color, width = width, kind = kind)
        onChanged?.invoke()
    }

    fun append(position: Offset) {
        val changed = _strokes.lastOrNull()?.addPoint(position) == true
        if (changed) {
            onChanged?.invoke()
        }
    }

    internal fun removeLastStrokeIfSinglePoint() {
        val lastStroke = _strokes.lastOrNull() ?: return
        if (lastStroke.points.size != 1) return
        _strokes.removeAt(_strokes.lastIndex)
        onChanged?.invoke()
    }

    fun eraseAt(position: Offset, radius: Float) {
        if (_strokes.isEmpty()) return
        val updated = mutableListOf<Stroke>()
        _strokes.forEach { stroke ->
            var segment = mutableListOf<Offset>()
            stroke.points.forEachIndexed { index, point ->
                val previous = stroke.points.getOrNull(index - 1)
                val shouldErase = distance(point, position) <= radius ||
                    (previous != null && distanceToSegment(position, previous, point) <= radius)

                if (shouldErase) {
                    if (segment.size > 1) {
                        updated += stroke.copyWithPoints(segment)
                    }
                    segment = mutableListOf()
                } else {
                    segment += point
                }
            }
            if (segment.size > 1) {
                updated += stroke.copyWithPoints(segment)
            }
        }
        _strokes.clear()
        _strokes.addAll(updated)
        onChanged?.invoke()
    }

    fun loadFromVectorJson(vectorJson: String?) {
        _strokes.clear()
        if (!vectorJson.isNullOrBlank()) {
            _strokes.addAll(parseStrokes(vectorJson))
        }
        onChanged?.invoke()
    }

    fun toVectorJson(): String {
        val strokeArray = JSONArray()
        _strokes.forEach { stroke ->
            val points = JSONArray()
            stroke.points.forEach { point ->
                points.put(JSONObject().put("x", point.x).put("y", point.y))
            }
            strokeArray.put(
                JSONObject()
                    .put("color", stroke.color.toHexString())
                    .put("width", stroke.width)
                    .put("kind", stroke.kind.name)
                    .put("points", points)
            )
        }
        val root = JSONObject()
            .put("strokes", strokeArray)
            .put("contentWidth", contentWidthPx)
            .put("contentHeight", contentHeightPx)
        imageBounds?.let { bounds ->
            root.put(
                "imageBounds",
                JSONObject()
                    .put("left", bounds.left)
                    .put("top", bounds.top)
                    .put("width", bounds.width)
                    .put("height", bounds.height)
            )
        }
        return root.toString()
    }

    private fun parseStrokes(vectorJson: String): List<Stroke> {
        return runCatching {
            val root = JSONObject(vectorJson)
            val strokes = root.optJSONArray("strokes") ?: return@runCatching emptyList()
            List(strokes.length()) { strokeIndex ->
                val stroke = strokes.getJSONObject(strokeIndex)
                val points = stroke.optJSONArray("points")
                Stroke(
                    points = if (points == null) {
                        emptyList()
                    } else {
                        List(points.length()) { pointIndex ->
                            val point = points.getJSONObject(pointIndex)
                            Offset(
                                x = point.optDouble("x", 0.0).toFloat(),
                                y = point.optDouble("y", 0.0).toFloat()
                            )
                        }
                    },
                    color = parseStrokeColor(stroke.optString("color")),
                    width = stroke.optDouble("width", 5.0).toFloat(),
                    kind = stroke.optString("kind")
                        .takeIf { it.isNotBlank() }
                        ?.let { runCatching { StrokeKind.valueOf(it) }.getOrNull() }
                        ?: if (stroke.optDouble("width", 5.0) >= 12.0) StrokeKind.Highlighter else StrokeKind.Pen
                )
            }.filter { it.points.isNotEmpty() }
        }.getOrDefault(emptyList())
    }

    private fun parseStrokeColor(value: String?): Color {
        if (value.isNullOrBlank()) return Color(0xFF111827)
        return runCatching { Color(AndroidColor.parseColor(value)) }.getOrDefault(Color(0xFF111827))
    }
}

@Composable
fun rememberHandwritingState(): HandwritingState = remember { HandwritingState() }

@Composable
fun HandwritingCanvas(
    state: HandwritingState,
    modifier: Modifier = Modifier,
    contentHeight: Dp = 1200.dp,
    stylusOnlyDrawing: Boolean = true,
    inputOverlayEnabled: Boolean = true,
    toolbarLeadingContent: @Composable RowScope.() -> Unit = {},
    toolbarCenterContent: @Composable BoxScope.() -> Unit = {},
    toolbarTrailingContent: @Composable RowScope.() -> Unit = {},
    backgroundContent: @Composable () -> Unit = {},
    foregroundContent: @Composable BoxScope.() -> Unit = {}
) {
    var drawingEnabled by remember { mutableStateOf(true) }
    var currentTool by remember { mutableStateOf(DrawingTools.first()) }
    var penWidth by remember { mutableStateOf(PenWidthOptions.first()) }
    var toolMenuExpanded by remember { mutableStateOf(false) }
    var scrollOffsetPx by remember { mutableStateOf(0f) }
    val eraserRadius = 22.dp
    val density = LocalDensity.current

    BoxWithConstraints(
        modifier = modifier
            .background(Color(0xFFFAFAFA), RoundedCornerShape(8.dp))
            .clipToBounds()
    ) {
        val contentHeightPx = with(density) { contentHeight.toPx() }
        val viewportHeightPx = with(density) { maxHeight.toPx() }
        state.updateContentSize(with(density) { maxWidth.toPx() }, contentHeightPx)
        val maxScrollPx = (contentHeightPx - viewportHeightPx).coerceAtLeast(0f)
        scrollOffsetPx = scrollOffsetPx.coerceIn(0f, maxScrollPx)

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(contentHeight)
                .offset { IntOffset(0, -scrollOffsetPx.roundToInt()) }
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
            Box(modifier = Modifier.fillMaxSize()) {
                backgroundContent()
            }
        }

        if (inputOverlayEnabled) {
            AndroidView(
                factory = { context ->
                    InkDrawingView(context).apply {
                        bindState(state)
                    }
                },
                update = { view ->
                    view.bindState(state)
                    view.drawingEnabled = drawingEnabled
                    view.currentTool = currentTool
                    view.penWidth = penWidth
                    view.eraserRadiusPx = with(density) { eraserRadius.toPx() }
                    view.stylusOnlyDrawing = stylusOnlyDrawing
                    view.contentScrollOffsetPx = scrollOffsetPx
                    view.onFingerScroll = { delta ->
                        scrollOffsetPx = (scrollOffsetPx + delta).coerceIn(0f, maxScrollPx)
                    }
                },
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(1f)
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(contentHeight)
                .offset { IntOffset(0, -scrollOffsetPx.roundToInt()) }
                .zIndex(2f)
        ) {
            foregroundContent()
        }

        Canvas(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .width(10.dp)
                .padding(vertical = 8.dp, horizontal = 2.dp)
        ) {
            drawRoundRect(color = Color(0xFFE5E7EB), cornerRadius = CornerRadius(8f, 8f))
            if (maxScrollPx > 0f) {
                val thumbHeight = (size.height * size.height / contentHeightPx).coerceAtLeast(48f)
                val y = scrollOffsetPx / maxScrollPx * (size.height - thumbHeight)
                drawRoundRect(
                    color = Color(0xFF6B7280),
                    topLeft = Offset(0f, y),
                    size = Size(size.width, thumbHeight),
                    cornerRadius = CornerRadius(8f, 8f)
                )
            }
        }

        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(top = 2.dp, start = 8.dp)
                .zIndex(4f),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            toolbarLeadingContent()
        }

        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 2.dp)
                .fillMaxWidth(0.30f)
                .zIndex(4f),
            contentAlignment = Alignment.Center
        ) {
            toolbarCenterContent()
        }

        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 2.dp, end = 8.dp)
                .zIndex(4f),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            val visibleDrawingTool = if (currentTool.kind == ToolKind.Eraser) DrawingTools.first() else currentTool
            PenWidthButton(
                width = penWidth,
                onClick = { penWidth = penWidth.nextPenWidth() }
            )
            Box {
                ToolCircleButton(
                    tool = visibleDrawingTool,
                    selected = drawingEnabled && currentTool.kind != ToolKind.Eraser,
                    onClick = { toolMenuExpanded = !toolMenuExpanded }
                )
                if (toolMenuExpanded) {
                    Column(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 36.dp)
                            .zIndex(6f),
                        verticalArrangement = Arrangement.spacedBy(7.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        DrawingTools.forEach { tool ->
                            ToolCircleButton(
                                tool = tool,
                                selected = currentTool.id == tool.id && currentTool.kind != ToolKind.Eraser,
                                onClick = {
                                    currentTool = tool
                                    drawingEnabled = true
                                    toolMenuExpanded = false
                                }
                            )
                        }
                    }
                }
            }
            EraserButton(
                selected = drawingEnabled && currentTool.kind == ToolKind.Eraser,
                onClick = {
                    currentTool = EraserTool
                    drawingEnabled = true
                }
            )
            UndoButton(onClick = state::undoLastStroke)
            toolbarTrailingContent()
        }
    }
}

@Composable
private fun PenWidthButton(
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
                cap = androidx.compose.ui.graphics.StrokeCap.Round
            )
        }
    }
}

@Composable
private fun ToolCircleButton(
    tool: DrawingTool,
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
private fun EraserButton(
    selected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(30.dp)
            .semantics { contentDescription = EraserTool.label }
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
private fun UndoButton(
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(30.dp)
            .semantics { contentDescription = "뒤로가기" }
            .clickable(onClick = onClick)
            .background(Color.White, RoundedCornerShape(19.dp))
            .border(1.dp, Color(0xFFE5E7EB), RoundedCornerShape(19.dp)),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(20.dp)) {
            val color = Color(0xFF374151)
            val stroke = 2.2.dp.toPx()
            val centerY = size.height * 0.52f
            val leftX = size.width * 0.22f
            val rightX = size.width * 0.78f
            drawLine(
                color = color,
                start = Offset(rightX, centerY),
                end = Offset(leftX, centerY),
                strokeWidth = stroke,
                cap = androidx.compose.ui.graphics.StrokeCap.Round
            )
            drawLine(
                color = color,
                start = Offset(leftX, centerY),
                end = Offset(leftX + 6.dp.toPx(), centerY - 5.dp.toPx()),
                strokeWidth = stroke,
                cap = androidx.compose.ui.graphics.StrokeCap.Round
            )
            drawLine(
                color = color,
                start = Offset(leftX, centerY),
                end = Offset(leftX + 6.dp.toPx(), centerY + 5.dp.toPx()),
                strokeWidth = stroke,
                cap = androidx.compose.ui.graphics.StrokeCap.Round
            )
            drawLine(
                color = color.copy(alpha = 0.55f),
                start = Offset(rightX, centerY),
                end = Offset(rightX, size.height * 0.30f),
                strokeWidth = stroke,
                cap = androidx.compose.ui.graphics.StrokeCap.Round
            )
        }
    }
}

private fun DrawingTool.strokeKind(): StrokeKind {
    return if (kind == ToolKind.Highlighter) StrokeKind.Highlighter else StrokeKind.Pen
}

private class InkDrawingView(context: Context) : View(context) {
    private val invalidation = { postInvalidateOnAnimation() }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }
    private val eraserPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = AndroidColor.rgb(55, 65, 81)
        strokeWidth = 2f
    }
    private var state: HandwritingState? = null
    private var activePointerId = MotionEvent.INVALID_POINTER_ID
    private var activeToolKind = ToolKind.Pen
    private var activePointerStartedByFinger = false
    private var fingerScrollPointerId = MotionEvent.INVALID_POINTER_ID
    private var fingerScrollActive = false
    private var lastFingerScrollY = 0f
    private var eraserCenter: Offset? = null
    private var twoFingerScrollActive = false
    private var lastTwoFingerScrollY = 0f

    var drawingEnabled: Boolean = true
        set(value) {
            field = value
            isClickable = value
            if (!value) {
                activePointerId = MotionEvent.INVALID_POINTER_ID
                activePointerStartedByFinger = false
                fingerScrollPointerId = MotionEvent.INVALID_POINTER_ID
                fingerScrollActive = false
                eraserCenter = null
                twoFingerScrollActive = false
            }
            postInvalidateOnAnimation()
        }

    var currentTool: DrawingTool = DrawingTools.first()
    var penWidth: Float = PenWidthOptions.first()
    var eraserRadiusPx: Float = 22f
    var stylusOnlyDrawing: Boolean = true
    var contentScrollOffsetPx: Float = 0f
        set(value) {
            if (field == value) return
            field = value
            postInvalidateOnAnimation()
        }
    var onFingerScroll: ((Float) -> Unit)? = null

    init {
        setWillNotDraw(false)
        setBackgroundColor(AndroidColor.TRANSPARENT)
        isFocusable = false
    }

    fun bindState(newState: HandwritingState) {
        if (state === newState) return
        state?.onChanged = null
        state = newState
        newState.onChanged = invalidation
        postInvalidateOnAnimation()
    }

    override fun onDetachedFromWindow() {
        if (state?.onChanged === invalidation) {
            state?.onChanged = null
        }
        super.onDetachedFromWindow()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val currentState = state ?: return false
        val action = event.actionMasked
        if (event.fingerPointerCount() >= 2 && (action == MotionEvent.ACTION_POINTER_DOWN || action == MotionEvent.ACTION_MOVE)) {
            if (!twoFingerScrollActive) {
                startTwoFingerScroll(currentState, event)
            } else if (action == MotionEvent.ACTION_MOVE) {
                handleTwoFingerScroll(event)
            }
            return true
        }
        if (twoFingerScrollActive) {
            when (action) {
                MotionEvent.ACTION_POINTER_UP -> {
                    continueTwoFingerScrollAfterPointerUp(event)
                    return true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    finishStroke()
                    return true
                }
            }
        }
        when (action) {
            MotionEvent.ACTION_DOWN -> {
                val pointerIndex = event.actionIndex
                if (event.isStylusLike(pointerIndex) && drawingEnabled) {
                    parent?.requestDisallowInterceptTouchEvent(true)
                    requestUnbufferedDispatch(event)
                    activePointerId = event.getPointerId(pointerIndex)
                    activeToolKind = eventToolKind(event, pointerIndex)
                    activePointerStartedByFinger = false
                    handlePoint(currentState, event.contentXFor(pointerIndex), event.contentYFor(pointerIndex), start = true)
                    return true
                }
                if (event.getToolType(pointerIndex) == MotionEvent.TOOL_TYPE_FINGER) {
                    startFingerScroll(event, pointerIndex)
                    return true
                }
                if (stylusOnlyDrawing) {
                    return false
                }
                parent?.requestDisallowInterceptTouchEvent(true)
                requestUnbufferedDispatch(event)
                activePointerId = event.getPointerId(pointerIndex)
                activeToolKind = eventToolKind(event, pointerIndex)
                activePointerStartedByFinger = event.getToolType(pointerIndex) == MotionEvent.TOOL_TYPE_FINGER
                handlePoint(currentState, event.contentXFor(pointerIndex), event.contentYFor(pointerIndex), start = true)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (fingerScrollActive) {
                    handleFingerScroll(event)
                    return true
                }
                val pointerIndex = event.findPointerIndex(activePointerId)
                if (pointerIndex < 0) return true
                for (historyIndex in 0 until event.historySize) {
                    handlePoint(
                        currentState,
                        event.getHistoricalX(pointerIndex, historyIndex),
                        event.getHistoricalY(pointerIndex, historyIndex) + contentScrollOffsetPx,
                        start = false
                    )
                }
                handlePoint(currentState, event.contentXFor(pointerIndex), event.contentYFor(pointerIndex), start = false)
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (fingerScrollActive) {
                    finishFingerScroll()
                    return true
                }
                val pointerIndex = event.findPointerIndex(activePointerId).takeIf { it >= 0 } ?: event.actionIndex
                if (action == MotionEvent.ACTION_UP && pointerIndex in 0 until event.pointerCount) {
                    handlePoint(currentState, event.contentXFor(pointerIndex), event.contentYFor(pointerIndex), start = false)
                }
                finishStroke()
                return true
            }
        }
        return true
    }

    override fun onDraw(canvas: AndroidCanvas) {
        super.onDraw(canvas)
        canvas.save()
        canvas.translate(0f, -contentScrollOffsetPx)
        state?.strokes?.forEach { stroke ->
            strokePaint.color = stroke.color.toArgb()
            strokePaint.strokeWidth = stroke.width
            if (stroke.kind == StrokeKind.Highlighter) {
                strokePaint.strokeCap = Paint.Cap.BUTT
                strokePaint.strokeJoin = Paint.Join.BEVEL
            } else {
                strokePaint.strokeCap = Paint.Cap.ROUND
                strokePaint.strokeJoin = Paint.Join.ROUND
            }
            if (stroke.points.size == 1) {
                val point = stroke.points.first()
                canvas.drawCircle(point.x, point.y, stroke.width / 2f, strokePaint)
            } else {
                canvas.drawPath(stroke.path, strokePaint)
            }
        }
        if (drawingEnabled && currentTool.kind == ToolKind.Eraser) {
            eraserCenter?.let { center ->
                canvas.drawCircle(center.x, center.y, eraserRadiusPx, eraserPaint)
            }
        }
        canvas.restore()
    }

    private fun eventToolKind(event: MotionEvent, pointerIndex: Int): ToolKind {
        return if (event.getToolType(pointerIndex) == MotionEvent.TOOL_TYPE_ERASER) {
            ToolKind.Eraser
        } else {
            currentTool.kind
        }
    }

    private fun handlePoint(state: HandwritingState, x: Float, y: Float, start: Boolean) {
        val position = Offset(x, y)
        if (activeToolKind == ToolKind.Eraser) {
            eraserCenter = position
            state.eraseAt(position, eraserRadiusPx)
            return
        }
        if (start) {
            state.start(
                position = position,
                color = currentTool.color,
                width = currentTool.activeWidth(penWidth),
                kind = currentTool.strokeKind()
            )
        } else {
            state.append(position)
        }
    }

    private fun MotionEvent.contentXFor(pointerIndex: Int): Float = getX(pointerIndex)

    private fun MotionEvent.contentYFor(pointerIndex: Int): Float = getY(pointerIndex) + contentScrollOffsetPx

    private fun startTwoFingerScroll(state: HandwritingState, event: MotionEvent) {
        if (activePointerStartedByFinger) {
            state.removeLastStrokeIfSinglePoint()
        }
        activePointerId = MotionEvent.INVALID_POINTER_ID
        activePointerStartedByFinger = false
        fingerScrollPointerId = MotionEvent.INVALID_POINTER_ID
        fingerScrollActive = false
        eraserCenter = null
        twoFingerScrollActive = true
        lastTwoFingerScrollY = event.averageFingerY()
        parent?.requestDisallowInterceptTouchEvent(true)
        postInvalidateOnAnimation()
    }

    private fun handleTwoFingerScroll(event: MotionEvent) {
        val y = event.averageFingerY()
        val deltaY = y - lastTwoFingerScrollY
        if (deltaY != 0f) {
            onFingerScroll?.invoke(-deltaY)
            lastTwoFingerScrollY = y
        }
    }

    private fun startFingerScroll(event: MotionEvent, pointerIndex: Int) {
        activePointerId = MotionEvent.INVALID_POINTER_ID
        activePointerStartedByFinger = false
        eraserCenter = null
        fingerScrollPointerId = event.getPointerId(pointerIndex)
        fingerScrollActive = true
        lastFingerScrollY = event.getY(pointerIndex)
        parent?.requestDisallowInterceptTouchEvent(true)
        postInvalidateOnAnimation()
    }

    private fun handleFingerScroll(event: MotionEvent) {
        val pointerIndex = event.findPointerIndex(fingerScrollPointerId)
        if (pointerIndex < 0) return
        val y = event.getY(pointerIndex)
        val deltaY = y - lastFingerScrollY
        if (deltaY != 0f) {
            onFingerScroll?.invoke(-deltaY)
            lastFingerScrollY = y
        }
    }

    private fun finishFingerScroll() {
        fingerScrollPointerId = MotionEvent.INVALID_POINTER_ID
        fingerScrollActive = false
        parent?.requestDisallowInterceptTouchEvent(false)
        postInvalidateOnAnimation()
    }

    private fun continueTwoFingerScrollAfterPointerUp(event: MotionEvent) {
        val liftedPointerId = event.getPointerId(event.actionIndex)
        if (event.fingerPointerCount(excludingPointerId = liftedPointerId) >= 2) {
            lastTwoFingerScrollY = event.averageFingerY(excludingPointerId = liftedPointerId)
        } else {
            activePointerId = MotionEvent.INVALID_POINTER_ID
            activePointerStartedByFinger = false
            fingerScrollPointerId = MotionEvent.INVALID_POINTER_ID
            fingerScrollActive = false
            eraserCenter = null
            twoFingerScrollActive = false
            postInvalidateOnAnimation()
        }
    }

    private fun finishStroke() {
        activePointerId = MotionEvent.INVALID_POINTER_ID
        activePointerStartedByFinger = false
        fingerScrollPointerId = MotionEvent.INVALID_POINTER_ID
        fingerScrollActive = false
        eraserCenter = null
        twoFingerScrollActive = false
        parent?.requestDisallowInterceptTouchEvent(false)
        postInvalidateOnAnimation()
    }
}

private fun MotionEvent.xFor(pointerIndex: Int): Float = getX(pointerIndex)

private fun MotionEvent.yFor(pointerIndex: Int): Float = getY(pointerIndex)

private fun MotionEvent.isStylusLike(pointerIndex: Int): Boolean {
    if (isFromSource(InputDevice.SOURCE_STYLUS)) return true
    return when (getToolType(pointerIndex)) {
        MotionEvent.TOOL_TYPE_STYLUS,
        MotionEvent.TOOL_TYPE_ERASER -> true
        else -> false
    }
}

private fun MotionEvent.fingerPointerCount(
    excludingPointerId: Int = MotionEvent.INVALID_POINTER_ID
): Int {
    var count = 0
    for (index in 0 until pointerCount) {
        if (getPointerId(index) != excludingPointerId && getToolType(index) == MotionEvent.TOOL_TYPE_FINGER) {
            count += 1
        }
    }
    return count
}

private fun MotionEvent.averageFingerY(
    excludingPointerId: Int = MotionEvent.INVALID_POINTER_ID
): Float {
    var total = 0f
    var count = 0
    for (index in 0 until pointerCount) {
        if (getPointerId(index) != excludingPointerId && getToolType(index) == MotionEvent.TOOL_TYPE_FINGER) {
            total += getY(index)
            count += 1
        }
    }
    return if (count == 0) 0f else total / count.toFloat()
}

private fun Color.toHexString(): String = "#%08X".format(toArgb())

private const val MinInkPointDistancePx = 0.7f

private fun Float.nextPenWidth(): Float {
    val index = PenWidthOptions.indexOfFirst { it == this }
    return PenWidthOptions[(index + 1).floorMod(PenWidthOptions.size)]
}

private fun Int.floorMod(divisor: Int): Int {
    return ((this % divisor) + divisor) % divisor
}

private fun DrawingTool.activeWidth(penWidth: Float): Float {
    return if (kind == ToolKind.Pen) penWidth else width
}

private fun distance(a: Offset, b: Offset): Float {
    val dx = a.x - b.x
    val dy = a.y - b.y
    return sqrt(dx * dx + dy * dy)
}

private fun distanceToSegment(point: Offset, start: Offset, end: Offset): Float {
    val dx = end.x - start.x
    val dy = end.y - start.y
    if (dx == 0f && dy == 0f) return distance(point, start)
    val t = (((point.x - start.x) * dx + (point.y - start.y) * dy) / (dx * dx + dy * dy)).coerceIn(0f, 1f)
    val projection = Offset(start.x + t * dx, start.y + t * dy)
    return distance(point, projection)
}
