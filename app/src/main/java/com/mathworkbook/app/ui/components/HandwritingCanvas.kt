package com.mathworkbook.app.ui.components

import android.content.Context
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.Path as AndroidPath
import android.view.MotionEvent
import android.view.View
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import org.json.JSONArray
import org.json.JSONObject
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
    DrawingTool("green", "초록 펜", Color(0xFF16A34A), 5f, ToolKind.Pen),
    DrawingTool("highlight_red", "형광 빨강", Color(0x44FF1744), 22f, ToolKind.Highlighter),
    DrawingTool("highlight_blue", "형광 파랑", Color(0x4400B8FF), 22f, ToolKind.Highlighter),
    DrawingTool("highlight_green", "형광 초록", Color(0x4400D084), 22f, ToolKind.Highlighter)
)

private val EraserTool = DrawingTool("eraser", "영역 지우개", Color(0xFF6B7280), 44f, ToolKind.Eraser)
private val PenWidthOptions = listOf(3f, 5f, 8f, 11f)

@Stable
class HandwritingState {
    private val _strokes = mutableListOf<Stroke>()
    val strokes: List<Stroke> get() = _strokes
    internal var onChanged: (() -> Unit)? = null

    fun clear() {
        _strokes.clear()
        onChanged?.invoke()
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
        return JSONObject().put("strokes", strokeArray).toString()
    }
}

@Composable
fun rememberHandwritingState(): HandwritingState = remember { HandwritingState() }

@Composable
fun HandwritingCanvas(
    state: HandwritingState,
    modifier: Modifier = Modifier,
    contentHeight: Dp = 1200.dp,
    backgroundContent: @Composable () -> Unit = {}
) {
    val scrollState = rememberScrollState()
    var drawingEnabled by remember { mutableStateOf(true) }
    var currentTool by remember { mutableStateOf(DrawingTools.first()) }
    var penWidth by remember { mutableStateOf(5f) }
    val eraserRadius = 22.dp
    val density = LocalDensity.current

    BoxWithConstraints(
        modifier = modifier.background(Color(0xFFFAFAFA), RoundedCornerShape(8.dp))
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(contentHeight)
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
                    },
                    modifier = Modifier
                        .fillMaxSize()
                )
            }
        }

        Canvas(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .width(10.dp)
                .padding(vertical = 8.dp, horizontal = 2.dp)
        ) {
            drawRoundRect(color = Color(0xFFE5E7EB), cornerRadius = CornerRadius(8f, 8f))
            val maxScroll = scrollState.maxValue
            if (maxScroll > 0) {
                val contentPixels = size.height + maxScroll
                val thumbHeight = (size.height * size.height / contentPixels).coerceAtLeast(48f)
                val y = scrollState.value.toFloat() / maxScroll.toFloat() * (size.height - thumbHeight)
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
                .align(Alignment.TopEnd)
                .padding(top = 8.dp, end = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Button(onClick = { drawingEnabled = !drawingEnabled }) {
                Text(if (drawingEnabled) "스크롤" else "필기")
            }
            DrawingTools.forEachIndexed { index, tool ->
                ToolCircleButton(
                    tool = tool,
                    selected = drawingEnabled && currentTool.id == tool.id,
                    onClick = {
                        currentTool = tool
                        drawingEnabled = true
                    }
                )
                if (index == 0) {
                    PenWidthButton(
                        width = penWidth,
                        onClick = { penWidth = penWidth.nextPenWidth() }
                    )
                }
            }
            EraserButton(
                selected = drawingEnabled && currentTool.kind == ToolKind.Eraser,
                onClick = {
                    currentTool = EraserTool
                    drawingEnabled = true
                }
            )
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
            .size(34.dp)
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
            .size(34.dp)
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
            .size(38.dp)
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
        Canvas(modifier = Modifier.size(24.dp)) {
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
    private var eraserCenter: Offset? = null

    var drawingEnabled: Boolean = true
        set(value) {
            field = value
            isClickable = value
            if (!value) {
                activePointerId = MotionEvent.INVALID_POINTER_ID
                eraserCenter = null
            }
            postInvalidateOnAnimation()
        }

    var currentTool: DrawingTool = DrawingTools.first()
    var penWidth: Float = 5f
    var eraserRadiusPx: Float = 22f

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
        if (!drawingEnabled) return false
        val currentState = state ?: return false
        val action = event.actionMasked
        when (action) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                requestUnbufferedDispatch(event)
                val pointerIndex = event.actionIndex
                activePointerId = event.getPointerId(pointerIndex)
                activeToolKind = eventToolKind(event, pointerIndex)
                handlePoint(currentState, event.xFor(pointerIndex), event.yFor(pointerIndex), start = true)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val pointerIndex = event.findPointerIndex(activePointerId)
                if (pointerIndex < 0) return true
                for (historyIndex in 0 until event.historySize) {
                    handlePoint(
                        currentState,
                        event.getHistoricalX(pointerIndex, historyIndex),
                        event.getHistoricalY(pointerIndex, historyIndex),
                        start = false
                    )
                }
                handlePoint(currentState, event.xFor(pointerIndex), event.yFor(pointerIndex), start = false)
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val pointerIndex = event.findPointerIndex(activePointerId).takeIf { it >= 0 } ?: event.actionIndex
                if (action == MotionEvent.ACTION_UP && pointerIndex in 0 until event.pointerCount) {
                    handlePoint(currentState, event.xFor(pointerIndex), event.yFor(pointerIndex), start = false)
                }
                finishStroke()
                return true
            }
        }
        return true
    }

    override fun onDraw(canvas: AndroidCanvas) {
        super.onDraw(canvas)
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

    private fun finishStroke() {
        activePointerId = MotionEvent.INVALID_POINTER_ID
        eraserCenter = null
        parent?.requestDisallowInterceptTouchEvent(false)
        postInvalidateOnAnimation()
    }
}

private fun MotionEvent.xFor(pointerIndex: Int): Float = getX(pointerIndex)

private fun MotionEvent.yFor(pointerIndex: Int): Float = getY(pointerIndex)

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
