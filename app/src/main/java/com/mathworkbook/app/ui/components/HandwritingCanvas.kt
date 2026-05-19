package com.mathworkbook.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.Stroke as DrawStroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.sqrt

data class Stroke(
    val points: List<Offset>,
    val color: Color = Color(0xFF111827),
    val width: Float = 5f,
    val kind: StrokeKind = StrokeKind.Pen
)

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

@Stable
class HandwritingState {
    val strokes = mutableStateListOf<Stroke>()

    fun clear() {
        strokes.clear()
    }

    fun start(position: Offset, color: Color, width: Float, kind: StrokeKind) {
        strokes += Stroke(points = listOf(position), color = color, width = width, kind = kind)
    }

    fun append(position: Offset) {
        val last = strokes.lastOrNull() ?: return
        strokes.removeAt(strokes.lastIndex)
        strokes += last.copy(points = last.points + position)
    }

    fun eraseAt(position: Offset, radius: Float) {
        if (strokes.isEmpty()) return
        val updated = mutableListOf<Stroke>()
        strokes.forEach { stroke ->
            var segment = mutableListOf<Offset>()
            stroke.points.forEachIndexed { index, point ->
                val previous = stroke.points.getOrNull(index - 1)
                val shouldErase = distance(point, position) <= radius ||
                    (previous != null && distanceToSegment(position, previous, point) <= radius)

                if (shouldErase) {
                    if (segment.size > 1) {
                        updated += stroke.copy(points = segment.toList())
                    }
                    segment = mutableListOf()
                } else {
                    segment += point
                }
            }
            if (segment.size > 1) {
                updated += stroke.copy(points = segment.toList())
            }
        }
        strokes.clear()
        strokes.addAll(updated)
    }

    fun toVectorJson(): String {
        val strokeArray = JSONArray()
        strokes.forEach { stroke ->
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
    var eraserCenter by remember { mutableStateOf<Offset?>(null) }
    val eraserRadius = 22.dp

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
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .then(
                            if (drawingEnabled) {
                                Modifier.pointerInput(currentTool) {
                                    val radius = eraserRadius.toPx()
                                    detectDragGestures(
                                        onDragStart = { position ->
                                            if (currentTool.kind == ToolKind.Eraser) {
                                                eraserCenter = position
                                                state.eraseAt(position, radius)
                                            } else {
                                                eraserCenter = null
                                                state.start(
                                                    position = position,
                                                    color = currentTool.color,
                                                    width = currentTool.width,
                                                    kind = currentTool.strokeKind()
                                                )
                                            }
                                        },
                                        onDrag = { change, _ ->
                                            if (currentTool.kind == ToolKind.Eraser) {
                                                eraserCenter = change.position
                                                state.eraseAt(change.position, radius)
                                            } else {
                                                state.append(change.position)
                                            }
                                        },
                                        onDragEnd = { eraserCenter = null },
                                        onDragCancel = { eraserCenter = null }
                                    )
                                }
                            } else {
                                Modifier
                            }
                        )
                ) {
                    state.strokes.forEach { stroke ->
                        val path = stroke.points.toPath()
                        if (path != null) {
                            val isHighlighter = stroke.kind == StrokeKind.Highlighter
                            drawPath(
                                path = path,
                                color = stroke.color,
                                style = DrawStroke(
                                    width = stroke.width,
                                    cap = if (isHighlighter) StrokeCap.Butt else StrokeCap.Round,
                                    join = if (isHighlighter) StrokeJoin.Bevel else StrokeJoin.Round
                                )
                            )
                        }
                    }
                    if (drawingEnabled && currentTool.kind == ToolKind.Eraser) {
                        eraserCenter?.let { center ->
                            drawCircle(
                                color = Color(0xFF374151),
                                radius = eraserRadius.toPx(),
                                center = center,
                                style = DrawStroke(width = 2.dp.toPx())
                            )
                        }
                    }
                }
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
            DrawingTools.forEach { tool ->
                ToolCircleButton(
                    tool = tool,
                    selected = drawingEnabled && currentTool.id == tool.id,
                    onClick = {
                        currentTool = tool
                        drawingEnabled = true
                    }
                )
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

private fun List<Offset>.toPath(): Path? {
    if (size < 2) return null
    return Path().also { path ->
        path.moveTo(first().x, first().y)
        drop(1).forEach { point -> path.lineTo(point.x, point.y) }
    }
}

private fun Color.toHexString(): String = "#%08X".format(toArgb())

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
