package com.mathworkbook.app.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.Path as AndroidPath
import android.os.SystemClock
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import com.mathworkbook.app.ui.skin.SkinAssetImage
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

internal data class StrokeBounds(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
) {
    fun expanded(amount: Float): StrokeBounds {
        return StrokeBounds(left - amount, top - amount, right + amount, bottom + amount)
    }

    fun include(point: Offset, padding: Float): StrokeBounds {
        return StrokeBounds(
            left = min(left, point.x - padding),
            top = min(top, point.y - padding),
            right = max(right, point.x + padding),
            bottom = max(bottom, point.y + padding)
        )
    }

    fun intersects(other: StrokeBounds): Boolean {
        return left <= other.right && right >= other.left && top <= other.bottom && bottom >= other.top
    }

    companion object {
        fun from(points: List<Offset>, padding: Float): StrokeBounds {
            if (points.isEmpty()) return StrokeBounds(0f, 0f, 0f, 0f)
            var left = points.first().x
            var top = points.first().y
            var right = points.first().x
            var bottom = points.first().y
            points.drop(1).forEach { point ->
                left = min(left, point.x)
                top = min(top, point.y)
                right = max(right, point.x)
                bottom = max(bottom, point.y)
            }
            return StrokeBounds(left - padding, top - padding, right + padding, bottom + padding)
        }
    }
}

class Stroke(
    points: List<Offset>,
    val color: Color = Color(0xFF111827),
    val width: Float = 5f,
    val kind: StrokeKind = StrokeKind.Pen
) {
    val points: MutableList<Offset> = points.toMutableList()
    internal val path = AndroidPath()
    internal var bounds: StrokeBounds = StrokeBounds.from(points, width / 2f)
        private set

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
        bounds = bounds.include(position, width / 2f)
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

    fun replacePoints(nextPoints: List<Offset>) {
        points.clear()
        points.addAll(nextPoints)
        rebuildPath()
        bounds = StrokeBounds.from(points, width / 2f)
    }

    fun deepCopy(): Stroke {
        return copyWithPoints(points.toList())
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

private const val ShapeSnapDefaultEnabled = true
private const val ShapeSnapDwellMillis = 600L
private const val ShapeSnapDwellRadiusDp = 6f
private const val ShapeSnapCancelRadiusDp = 10f
private const val ShapeSnapMinimumLengthPx = 28f
private const val ShapeSnapLineTolerancePx = 8f
private const val ShapeSnapClosedTolerancePx = 34f
private const val ShapeSnapEllipsePointCount = 72

@Stable
class HandwritingState {
    private val _strokes = mutableListOf<Stroke>()
    private val undoSnapshots = mutableListOf<List<Stroke>>()
    val strokes: List<Stroke> get() = _strokes
    internal var onChanged: (() -> Unit)? = null
    private var contentWidthPx: Float = 0f
    private var contentHeightPx: Float = 0f
    private var imageBounds: WorksheetImageBounds? = null
    private var _changeVersion: Long = 0L
    private var _renderCacheVersion: Long = 0L
    private var _lastChangedAtMillis: Long = 0L

    val changeVersion: Long get() = _changeVersion
    val lastChangedAtMillis: Long get() = _lastChangedAtMillis
    internal val renderCacheVersion: Long get() = _renderCacheVersion

    fun clear() {
        _strokes.clear()
        undoSnapshots.clear()
        notifyChanged(renderCacheDirty = true)
    }

    fun undoLastStroke() {
        val snapshot = undoSnapshots.lastOrNull()
        if (snapshot != null) {
            undoSnapshots.removeAt(undoSnapshots.lastIndex)
            _strokes.clear()
            _strokes.addAll(snapshot.map { it.deepCopy() })
            notifyChanged(renderCacheDirty = true)
            return
        }
        if (_strokes.isEmpty()) return
        _strokes.removeAt(_strokes.lastIndex)
        notifyChanged(renderCacheDirty = true)
    }

    fun updateContentSize(widthPx: Float, heightPx: Float) {
        if (contentWidthPx == widthPx && contentHeightPx == heightPx) return
        contentWidthPx = widthPx
        contentHeightPx = heightPx
        markRenderCacheDirty()
        onChanged?.invoke()
    }

    fun updateImageBounds(bounds: WorksheetImageBounds) {
        imageBounds = bounds
    }

    fun start(position: Offset, color: Color, width: Float, kind: StrokeKind) {
        rememberUndoSnapshot()
        _strokes += Stroke(points = listOf(position), color = color, width = width, kind = kind)
        notifyChanged(renderCacheDirty = false)
    }

    fun append(position: Offset) {
        val changed = _strokes.lastOrNull()?.addPoint(position) == true
        if (changed) {
            notifyChanged(renderCacheDirty = false)
        }
    }

    internal fun replaceLastStrokePoints(points: List<Offset>) {
        val stroke = _strokes.lastOrNull() ?: return
        if (points.isEmpty()) return
        stroke.replacePoints(points)
        notifyChanged(renderCacheDirty = false)
    }

    internal fun removeLastStrokeIfSinglePoint() {
        val lastStroke = _strokes.lastOrNull() ?: return
        if (lastStroke.points.size != 1) return
        _strokes.removeAt(_strokes.lastIndex)
        if (undoSnapshots.isNotEmpty()) {
            undoSnapshots.removeAt(undoSnapshots.lastIndex)
        }
        notifyChanged(renderCacheDirty = true)
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
        notifyChanged(renderCacheDirty = true)
    }

    fun erasePath(eraserPoints: List<Offset>, radius: Float): Boolean {
        if (_strokes.isEmpty() || eraserPoints.isEmpty()) return false
        val eraserBounds = StrokeBounds.from(eraserPoints, radius)
        val updated = mutableListOf<Stroke>()
        var changed = false
        _strokes.forEach { stroke ->
            val hitRadius = radius + stroke.width / 2f
            if (!stroke.bounds.intersects(eraserBounds.expanded(stroke.width / 2f))) {
                updated += stroke
                return@forEach
            }
            var segment = mutableListOf<Offset>()
            stroke.points.forEachIndexed { index, point ->
                val previous = stroke.points.getOrNull(index - 1)
                val shouldErase = eraserPathHitsPoint(point, eraserPoints, hitRadius) ||
                    (previous != null && eraserPathHitsSegment(previous, point, eraserPoints, hitRadius))

                if (shouldErase) {
                    changed = true
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
        if (!changed) return false
        rememberUndoSnapshot()
        _strokes.clear()
        _strokes.addAll(updated)
        notifyChanged(renderCacheDirty = true)
        return true
    }

    fun loadFromVectorJson(vectorJson: String?) {
        _strokes.clear()
        undoSnapshots.clear()
        if (!vectorJson.isNullOrBlank()) {
            _strokes.addAll(parseStrokes(vectorJson))
        }
        notifyChanged(renderCacheDirty = true)
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

    fun isIdleFor(minIdleMillis: Long): Boolean {
        val lastChanged = _lastChangedAtMillis
        return lastChanged == 0L || SystemClock.uptimeMillis() - lastChanged >= minIdleMillis
    }

    private fun notifyChanged(renderCacheDirty: Boolean) {
        if (renderCacheDirty) {
            markRenderCacheDirty()
        }
        _changeVersion += 1
        _lastChangedAtMillis = SystemClock.uptimeMillis()
        onChanged?.invoke()
    }

    private fun markRenderCacheDirty() {
        _renderCacheVersion += 1
    }

    private fun rememberUndoSnapshot() {
        undoSnapshots += _strokes.map { it.deepCopy() }
        if (undoSnapshots.size > MaxUndoSnapshots) {
            undoSnapshots.removeAt(0)
        }
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
    onDrawingStart: () -> Unit = {},
    backgroundContent: @Composable () -> Unit = {},
    foregroundContent: @Composable BoxScope.() -> Unit = {}
) {
    var drawingEnabled by remember { mutableStateOf(true) }
    var currentTool by remember { mutableStateOf(DrawingTools.first()) }
    var lastDrawingTool by remember { mutableStateOf(DrawingTools.first()) }
    var penWidth by remember { mutableStateOf(PenWidthOptions.first()) }
    var shapeSnapEnabled by remember { mutableStateOf(ShapeSnapDefaultEnabled) }
    var toolMenuExpanded by remember { mutableStateOf(false) }
    var floatingToolMenuPosition by remember { mutableStateOf<Offset?>(null) }
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
                    view.shapeSnapEnabled = shapeSnapEnabled
                    view.eraserRadiusPx = with(density) { eraserRadius.toPx() }
                    view.stylusOnlyDrawing = stylusOnlyDrawing
                    view.contentScrollOffsetPx = scrollOffsetPx
                    view.onDrawingStart = onDrawingStart
                    view.onStylusButtonPressed = { x, y ->
                        floatingToolMenuPosition = Offset(x, y)
                        toolMenuExpanded = false
                    }
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

        SkinAssetImage(
            assetKey = "toolbarStrip",
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(48.dp)
                .zIndex(3f),
            contentScale = ContentScale.FillBounds,
            alpha = 0.9f
        )

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
                .fillMaxWidth(0.42f)
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
            val visibleDrawingTool = if (currentTool.kind == ToolKind.Eraser) lastDrawingTool else currentTool
            PenWidthButton(
                width = penWidth,
                onClick = { penWidth = penWidth.nextPenWidth() }
            )
            ShapeSnapButton(
                enabled = shapeSnapEnabled,
                onClick = { shapeSnapEnabled = !shapeSnapEnabled }
            )
            Box {
                ToolCircleButton(
                    tool = visibleDrawingTool,
                    selected = drawingEnabled && currentTool.kind != ToolKind.Eraser,
                    onClick = {
                        if (currentTool.kind == ToolKind.Eraser) {
                            currentTool = lastDrawingTool
                            drawingEnabled = true
                            toolMenuExpanded = false
                        } else {
                            toolMenuExpanded = !toolMenuExpanded
                        }
                    }
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
                                    lastDrawingTool = tool
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

        floatingToolMenuPosition?.let { position ->
            FloatingToolMenu(
                anchor = position,
                viewportWidthPx = with(density) { maxWidth.toPx() },
                viewportHeightPx = viewportHeightPx,
                currentTool = currentTool,
                onSelectTool = { tool ->
                    currentTool = tool
                    if (tool.kind != ToolKind.Eraser) {
                        lastDrawingTool = tool
                    }
                    drawingEnabled = true
                    floatingToolMenuPosition = null
                }
            )
        }
    }
}

@Composable
private fun FloatingToolMenu(
    anchor: Offset,
    viewportWidthPx: Float,
    viewportHeightPx: Float,
    currentTool: DrawingTool,
    onSelectTool: (DrawingTool) -> Unit
) {
    val density = LocalDensity.current
    val menuWidthPx = with(density) { 186.dp.toPx() }
    val menuHeightPx = with(density) { 42.dp.toPx() }
    val marginPx = with(density) { 8.dp.toPx() }
    val offsetX = (anchor.x - menuWidthPx / 2f).coerceIn(marginPx, viewportWidthPx - menuWidthPx - marginPx)
    val aboveY = anchor.y - menuHeightPx - marginPx
    val belowY = anchor.y + marginPx
    val offsetY = if (aboveY >= marginPx) {
        aboveY
    } else {
        belowY.coerceIn(marginPx, viewportHeightPx - menuHeightPx - marginPx)
    }
    Row(
        modifier = Modifier
            .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
            .zIndex(8f)
            .background(Color.White, RoundedCornerShape(22.dp))
            .border(1.dp, Color(0xFFE5E7EB), RoundedCornerShape(22.dp))
            .padding(horizontal = 6.dp, vertical = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        DrawingTools.forEach { tool ->
            ToolCircleButton(
                tool = tool,
                selected = currentTool.id == tool.id && currentTool.kind != ToolKind.Eraser,
                onClick = { onSelectTool(tool) }
            )
        }
        EraserButton(
            selected = currentTool.kind == ToolKind.Eraser,
            onClick = { onSelectTool(EraserTool) }
        )
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
private fun ShapeSnapButton(
    enabled: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(30.dp)
            .semantics { contentDescription = if (enabled) "도형 스냅 켜짐" else "도형 스냅 꺼짐" }
            .clickable(onClick = onClick)
            .background(Color.White, CircleShape)
            .border(
                width = if (enabled) 3.dp else 1.dp,
                color = if (enabled) MaterialTheme.colorScheme.primary else Color(0xFFE5E7EB),
                shape = CircleShape
            ),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(20.dp)) {
            val color = if (enabled) Color(0xFF2563EB) else Color(0xFF6B7280)
            val stroke = 1.7.dp.toPx()
            drawRoundRect(
                color = color,
                topLeft = Offset(size.width * 0.18f, size.height * 0.22f),
                size = Size(size.width * 0.42f, size.height * 0.42f),
                cornerRadius = CornerRadius(1.5.dp.toPx(), 1.5.dp.toPx()),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = stroke)
            )
            drawCircle(
                color = color.copy(alpha = if (enabled) 0.82f else 0.45f),
                radius = size.minDimension * 0.18f,
                center = Offset(size.width * 0.66f, size.height * 0.66f),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = stroke)
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

private data class ShapeSnapPreview(
    val activationPosition: Offset
)

private data class ShapeSnapCandidate(
    val points: List<Offset>
)

private data class PointBounds(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
    val diagonal: Float get() = sqrt(width * width + height * height)
    val center: Offset get() = Offset((left + right) / 2f, (top + bottom) / 2f)
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
    private val eraserOverlayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = AndroidColor.argb(76, 96, 165, 250)
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val bitmapPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val eraserPreviewPath = AndroidPath()
    private val pendingEraserPoints = mutableListOf<Offset>()
    private var state: HandwritingState? = null
    private var strokeCacheBitmap: Bitmap? = null
    private var strokeCacheCanvas: AndroidCanvas? = null
    private var strokeCacheWidth = 0
    private var strokeCacheHeight = 0
    private var strokeCacheScrollOffsetPx = Float.NaN
    private var strokeCacheRenderVersion = -1L
    private var strokeCacheStrokeCount = 0
    private val shapeSnapDwellRadiusPx = resources.displayMetrics.density * ShapeSnapDwellRadiusDp
    private val shapeSnapCancelRadiusPx = resources.displayMetrics.density * ShapeSnapCancelRadiusDp
    private val freehandStrokePoints = mutableListOf<Offset>()
    private var shapeSnapPreview: ShapeSnapPreview? = null
    private var shapeSnapDwellAnchor: Offset? = null
    private var shapeSnapDwellStartedAt = 0L
    private val shapeSnapRunnable = Runnable { tryActivateShapeSnap() }
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
                cancelShapeSnapPreview(restoreFreehand = true)
                clearShapeSnapTracking()
                clearPendingEraser()
                twoFingerScrollActive = false
            }
            postInvalidateOnAnimation()
        }

    var currentTool: DrawingTool = DrawingTools.first()
    var penWidth: Float = PenWidthOptions.first()
    var shapeSnapEnabled: Boolean = ShapeSnapDefaultEnabled
        set(value) {
            if (field == value) return
            field = value
            if (!value) {
                cancelShapeSnapPreview(restoreFreehand = true)
                clearShapeSnapTracking()
            }
        }
    var eraserRadiusPx: Float = 22f
    var stylusOnlyDrawing: Boolean = true
    var contentScrollOffsetPx: Float = 0f
        set(value) {
            if (field == value) return
            field = value
            postInvalidateOnAnimation()
        }
    var onDrawingStart: (() -> Unit)? = null
    var onStylusButtonPressed: ((Float, Float) -> Unit)? = null
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
        resetStrokeCache(recycle = true)
        clearShapeSnapTracking()
        clearPendingEraser()
        postInvalidateOnAnimation()
    }

    override fun onDetachedFromWindow() {
        if (state?.onChanged === invalidation) {
            state?.onChanged = null
        }
        resetStrokeCache(recycle = true)
        clearShapeSnapTracking()
        super.onDetachedFromWindow()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w != oldw || h != oldh) {
            resetStrokeCache(recycle = true)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val currentState = state ?: return false
        val action = event.actionMasked
        if (handleStylusButtonEvent(event)) {
            return true
        }
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
                    onDrawingStart?.invoke()
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
                onDrawingStart?.invoke()
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

    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        if (handleStylusButtonEvent(event)) {
            return true
        }
        return super.onGenericMotionEvent(event)
    }

    override fun onHoverEvent(event: MotionEvent): Boolean {
        if (handleStylusButtonEvent(event)) {
            return true
        }
        return super.onHoverEvent(event)
    }

    override fun onDraw(canvas: AndroidCanvas) {
        super.onDraw(canvas)
        val currentState = state ?: return
        val committedStrokeCount = committedStrokeCountFor(currentState)
        val cacheReady = ensureStrokeCache(currentState, committedStrokeCount)
        if (cacheReady) {
            strokeCacheBitmap?.let { cache ->
                canvas.drawBitmap(cache, 0f, 0f, bitmapPaint)
            }
        }
        canvas.save()
        canvas.translate(0f, -contentScrollOffsetPx)
        if (cacheReady) {
            drawStrokeRange(canvas, currentState.strokes, strokeCacheStrokeCount, currentState.strokes.size)
        } else {
            drawStrokeRange(canvas, currentState.strokes, 0, currentState.strokes.size)
        }
        if (drawingEnabled && currentTool.kind == ToolKind.Eraser) {
            if (pendingEraserPoints.isNotEmpty()) {
                eraserOverlayPaint.strokeWidth = eraserRadiusPx * 2f
                canvas.drawPath(eraserPreviewPath, eraserOverlayPaint)
            }
            eraserCenter?.let { center ->
                canvas.drawCircle(center.x, center.y, eraserRadiusPx, eraserPaint)
            }
        }
        canvas.restore()
    }

    private fun committedStrokeCountFor(state: HandwritingState): Int {
        return if (hasActiveInkStroke() && state.strokes.isNotEmpty()) {
            state.strokes.lastIndex
        } else {
            state.strokes.size
        }
    }

    private fun hasActiveInkStroke(): Boolean {
        return activePointerId != MotionEvent.INVALID_POINTER_ID && activeToolKind != ToolKind.Eraser
    }

    private fun ensureStrokeCache(state: HandwritingState, committedStrokeCount: Int): Boolean {
        if (width <= 0 || height <= 0) return false
        val cache = ensureStrokeCacheBitmap() ?: return false
        val needsRebuild = strokeCacheScrollOffsetPx != contentScrollOffsetPx ||
            strokeCacheRenderVersion != state.renderCacheVersion ||
            strokeCacheStrokeCount > committedStrokeCount
        if (needsRebuild) {
            rebuildStrokeCache(cache, state, committedStrokeCount)
        } else if (strokeCacheStrokeCount < committedStrokeCount) {
            drawStrokeRangeToCache(state, strokeCacheStrokeCount, committedStrokeCount)
            strokeCacheStrokeCount = committedStrokeCount
        }
        return true
    }

    private fun ensureStrokeCacheBitmap(): Bitmap? {
        val existing = strokeCacheBitmap
        if (
            existing != null &&
            !existing.isRecycled &&
            strokeCacheWidth == width &&
            strokeCacheHeight == height
        ) {
            return existing
        }
        resetStrokeCache(recycle = true)
        return try {
            Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { bitmap ->
                bitmap.eraseColor(AndroidColor.TRANSPARENT)
                strokeCacheBitmap = bitmap
                strokeCacheCanvas = AndroidCanvas(bitmap)
                strokeCacheWidth = width
                strokeCacheHeight = height
            }
        } catch (error: OutOfMemoryError) {
            resetStrokeCache(recycle = true)
            null
        }
    }

    private fun rebuildStrokeCache(cache: Bitmap, state: HandwritingState, committedStrokeCount: Int) {
        cache.eraseColor(AndroidColor.TRANSPARENT)
        strokeCacheScrollOffsetPx = contentScrollOffsetPx
        strokeCacheRenderVersion = state.renderCacheVersion
        strokeCacheStrokeCount = 0
        drawStrokeRangeToCache(state, 0, committedStrokeCount)
        strokeCacheStrokeCount = committedStrokeCount
    }

    private fun drawStrokeRangeToCache(state: HandwritingState, fromIndex: Int, toIndex: Int) {
        val cacheCanvas = strokeCacheCanvas ?: return
        val from = fromIndex.coerceIn(0, state.strokes.size)
        val to = toIndex.coerceIn(from, state.strokes.size)
        cacheCanvas.save()
        cacheCanvas.translate(0f, -strokeCacheScrollOffsetPx)
        drawStrokeRange(cacheCanvas, state.strokes, from, to)
        cacheCanvas.restore()
    }

    private fun drawStrokeRange(canvas: AndroidCanvas, strokes: List<Stroke>, fromIndex: Int, toIndex: Int) {
        val from = fromIndex.coerceIn(0, strokes.size)
        val to = toIndex.coerceIn(from, strokes.size)
        for (index in from until to) {
            drawStroke(canvas, strokes[index])
        }
    }

    private fun drawStroke(canvas: AndroidCanvas, stroke: Stroke) {
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

    private fun commitActiveStrokeToCache() {
        val currentState = state ?: return
        if (!hasActiveInkStroke() || currentState.strokes.isEmpty()) return
        if (!ensureStrokeCache(currentState, currentState.strokes.lastIndex)) return
        drawStrokeRangeToCache(currentState, strokeCacheStrokeCount, currentState.strokes.size)
        strokeCacheStrokeCount = currentState.strokes.size
        postInvalidateOnAnimation()
    }

    private fun resetStrokeCache(recycle: Boolean) {
        if (recycle) {
            strokeCacheBitmap?.recycle()
        }
        strokeCacheBitmap = null
        strokeCacheCanvas = null
        strokeCacheWidth = 0
        strokeCacheHeight = 0
        strokeCacheScrollOffsetPx = Float.NaN
        strokeCacheRenderVersion = -1L
        strokeCacheStrokeCount = 0
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
            clearShapeSnapTracking()
            eraserCenter = position
            recordEraserPoint(position, start)
            return
        }
        if (handleShapeSnapPoint(state, position, start)) {
            return
        }
        if (start) {
            state.start(
                position = position,
                color = currentTool.color,
                width = currentTool.activeWidth(penWidth),
                kind = currentTool.strokeKind()
            )
            beginShapeSnapTracking(position)
        } else {
            state.append(position)
            recordFreehandPoint(position)
        }
    }

    private fun handleShapeSnapPoint(state: HandwritingState, position: Offset, start: Boolean): Boolean {
        if (start || !shapeSnapEnabled || activeToolKind == ToolKind.Eraser) return false
        val preview = shapeSnapPreview
        if (preview != null) {
            recordFreehandPoint(position)
            if (distance(position, preview.activationPosition) >= shapeSnapCancelRadiusPx) {
                cancelShapeSnapPreview(restoreFreehand = true)
                resetShapeSnapDwell(position)
            }
            return true
        }
        recordFreehandPoint(position)
        state.append(position)
        updateShapeSnapDwell(position)
        return true
    }

    private fun beginShapeSnapTracking(position: Offset) {
        freehandStrokePoints.clear()
        shapeSnapPreview = null
        if (shapeSnapEnabled && activeToolKind != ToolKind.Eraser) {
            freehandStrokePoints += position
            resetShapeSnapDwell(position)
        } else {
            removeCallbacks(shapeSnapRunnable)
        }
    }

    private fun recordFreehandPoint(position: Offset) {
        if (!shapeSnapEnabled || activeToolKind == ToolKind.Eraser) return
        val last = freehandStrokePoints.lastOrNull()
        if (last == null || distance(last, position) >= MinInkPointDistancePx) {
            freehandStrokePoints += position
        }
    }

    private fun updateShapeSnapDwell(position: Offset) {
        if (!shapeSnapEnabled || activeToolKind == ToolKind.Eraser) return
        val anchor = shapeSnapDwellAnchor
        if (anchor == null || distance(anchor, position) > shapeSnapDwellRadiusPx) {
            resetShapeSnapDwell(position)
        } else {
            scheduleShapeSnapCheck()
        }
    }

    private fun resetShapeSnapDwell(position: Offset) {
        shapeSnapDwellAnchor = position
        shapeSnapDwellStartedAt = SystemClock.uptimeMillis()
        scheduleShapeSnapCheck()
    }

    private fun scheduleShapeSnapCheck() {
        removeCallbacks(shapeSnapRunnable)
        postDelayed(shapeSnapRunnable, ShapeSnapDwellMillis)
    }

    private fun tryActivateShapeSnap() {
        val currentState = state ?: return
        if (!shapeSnapEnabled || activeToolKind == ToolKind.Eraser || shapeSnapPreview != null) return
        if (activePointerId == MotionEvent.INVALID_POINTER_ID || freehandStrokePoints.size < 2) return
        val anchor = shapeSnapDwellAnchor ?: return
        val last = freehandStrokePoints.lastOrNull() ?: return
        val now = SystemClock.uptimeMillis()
        if (now - shapeSnapDwellStartedAt < ShapeSnapDwellMillis) {
            scheduleShapeSnapCheck()
            return
        }
        if (distance(anchor, last) > shapeSnapDwellRadiusPx) return
        val candidate = recognizeShapeSnap(freehandStrokePoints) ?: return
        currentState.replaceLastStrokePoints(candidate.points)
        shapeSnapPreview = ShapeSnapPreview(activationPosition = last)
        postInvalidateOnAnimation()
    }

    private fun cancelShapeSnapPreview(restoreFreehand: Boolean) {
        val preview = shapeSnapPreview ?: return
        if (restoreFreehand && freehandStrokePoints.isNotEmpty()) {
            state?.replaceLastStrokePoints(freehandStrokePoints.toList())
        }
        shapeSnapPreview = null
        shapeSnapDwellAnchor = preview.activationPosition
        shapeSnapDwellStartedAt = SystemClock.uptimeMillis()
        postInvalidateOnAnimation()
    }

    private fun clearShapeSnapTracking() {
        removeCallbacks(shapeSnapRunnable)
        freehandStrokePoints.clear()
        shapeSnapPreview = null
        shapeSnapDwellAnchor = null
        shapeSnapDwellStartedAt = 0L
    }

    private fun handleStylusButtonEvent(event: MotionEvent): Boolean {
        val pointerIndex = event.actionIndex.coerceIn(0, event.pointerCount - 1)
        if (!event.isStylusLike(pointerIndex)) return false
        if (!event.hasStylusSideButton()) return false
        finishStroke()
        onStylusButtonPressed?.invoke(event.xFor(pointerIndex), event.yFor(pointerIndex))
        return true
    }

    private fun MotionEvent.contentXFor(pointerIndex: Int): Float = getX(pointerIndex)

    private fun MotionEvent.contentYFor(pointerIndex: Int): Float = getY(pointerIndex) + contentScrollOffsetPx

    private fun startTwoFingerScroll(state: HandwritingState, event: MotionEvent) {
        if (activePointerStartedByFinger) {
            state.removeLastStrokeIfSinglePoint()
        }
        cancelShapeSnapPreview(restoreFreehand = true)
        clearShapeSnapTracking()
        activePointerId = MotionEvent.INVALID_POINTER_ID
        activePointerStartedByFinger = false
        fingerScrollPointerId = MotionEvent.INVALID_POINTER_ID
        fingerScrollActive = false
        eraserCenter = null
        clearPendingEraser()
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
        cancelShapeSnapPreview(restoreFreehand = true)
        clearShapeSnapTracking()
        activePointerId = MotionEvent.INVALID_POINTER_ID
        activePointerStartedByFinger = false
        eraserCenter = null
        clearPendingEraser()
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
            clearShapeSnapTracking()
            postInvalidateOnAnimation()
        }
    }

    private fun finishStroke() {
        if (activeToolKind == ToolKind.Eraser && pendingEraserPoints.isNotEmpty()) {
            state?.erasePath(pendingEraserPoints.toList(), eraserRadiusPx)
        } else {
            commitActiveStrokeToCache()
        }
        activePointerId = MotionEvent.INVALID_POINTER_ID
        activePointerStartedByFinger = false
        fingerScrollPointerId = MotionEvent.INVALID_POINTER_ID
        fingerScrollActive = false
        eraserCenter = null
        clearPendingEraser()
        clearShapeSnapTracking()
        twoFingerScrollActive = false
        parent?.requestDisallowInterceptTouchEvent(false)
        postInvalidateOnAnimation()
    }

    private fun recordEraserPoint(position: Offset, start: Boolean) {
        if (start) {
            pendingEraserPoints.clear()
            eraserPreviewPath.reset()
            pendingEraserPoints += position
            eraserPreviewPath.moveTo(position.x, position.y)
            postInvalidateOnAnimation()
            return
        }
        val last = pendingEraserPoints.lastOrNull()
        if (last != null && distanceSquared(last, position) < EraserSampleDistancePx * EraserSampleDistancePx) {
            return
        }
        if (last == null) {
            eraserPreviewPath.moveTo(position.x, position.y)
        } else {
            eraserPreviewPath.lineTo(position.x, position.y)
        }
        pendingEraserPoints += position
        postInvalidateOnAnimation()
    }

    private fun clearPendingEraser() {
        pendingEraserPoints.clear()
        eraserPreviewPath.reset()
    }
}

private fun MotionEvent.xFor(pointerIndex: Int): Float = getX(pointerIndex)

private fun MotionEvent.yFor(pointerIndex: Int): Float = getY(pointerIndex)

private fun MotionEvent.hasStylusSideButton(): Boolean {
    val stylusButtons = MotionEvent.BUTTON_STYLUS_PRIMARY or MotionEvent.BUTTON_STYLUS_SECONDARY
    return (buttonState and stylusButtons) != 0 || actionButton == MotionEvent.BUTTON_STYLUS_PRIMARY ||
        actionButton == MotionEvent.BUTTON_STYLUS_SECONDARY
}

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
private const val EraserSampleDistancePx = 9f
private const val MaxUndoSnapshots = 40

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

private fun distanceSquared(a: Offset, b: Offset): Float {
    val dx = a.x - b.x
    val dy = a.y - b.y
    return dx * dx + dy * dy
}

private fun recognizeShapeSnap(points: List<Offset>): ShapeSnapCandidate? {
    val cleaned = points.withoutNearDuplicates(MinInkPointDistancePx)
    if (cleaned.size < 2) return null
    val first = cleaned.first()
    val last = cleaned.last()
    val length = pathLength(cleaned)
    if (length < ShapeSnapMinimumLengthPx) return null
    val directDistance = distance(first, last)
    val bounds = pointBounds(cleaned) ?: return null
    val closedThreshold = max(ShapeSnapClosedTolerancePx, bounds.diagonal * 0.16f)

    if (directDistance > closedThreshold) {
        val maxDeviation = cleaned.maxOf { point ->
            distanceToSegment(point, first, last)
        }
        val lineTolerance = max(ShapeSnapLineTolerancePx, directDistance * 0.12f)
        if (directDistance >= ShapeSnapMinimumLengthPx &&
            length <= directDistance * 1.35f &&
            maxDeviation <= lineTolerance
        ) {
            return ShapeSnapCandidate(listOf(first, last))
        }
        return null
    }

    if (bounds.width < ShapeSnapMinimumLengthPx * 0.7f || bounds.height < ShapeSnapMinimumLengthPx * 0.7f) {
        return null
    }

    val closedPoints = cleaned.dropLastIfNearFirst(closedThreshold)
    if (closedPoints.size < 4) return null
    val simplifyTolerance = max(ShapeSnapLineTolerancePx, bounds.diagonal * 0.075f)
    val corners = simplifyClosedCorners(closedPoints, simplifyTolerance)

    if (corners.size == 3) {
        return ShapeSnapCandidate(closePolyline(corners))
    }
    if (corners.size == 4 && isRectangleLike(corners)) {
        return ShapeSnapCandidate(closePolyline(corners))
    }
    if (looksEllipseLike(closedPoints, bounds)) {
        return ShapeSnapCandidate(sampleEllipse(bounds, first))
    }
    return null
}

private fun List<Offset>.withoutNearDuplicates(minDistance: Float): List<Offset> {
    if (isEmpty()) return emptyList()
    val result = mutableListOf(first())
    val minDistanceSquared = minDistance * minDistance
    for (index in 1 until size) {
        val point = this[index]
        if (distanceSquared(result.last(), point) >= minDistanceSquared) {
            result += point
        }
    }
    return result
}

private fun List<Offset>.dropLastIfNearFirst(threshold: Float): List<Offset> {
    if (size <= 1) return this
    return if (distance(first(), last()) <= threshold) dropLast(1) else this
}

private fun pathLength(points: List<Offset>): Float {
    var length = 0f
    for (index in 1 until points.size) {
        length += distance(points[index - 1], points[index])
    }
    return length
}

private fun pointBounds(points: List<Offset>): PointBounds? {
    val first = points.firstOrNull() ?: return null
    var left = first.x
    var top = first.y
    var right = first.x
    var bottom = first.y
    for (point in points) {
        left = min(left, point.x)
        top = min(top, point.y)
        right = max(right, point.x)
        bottom = max(bottom, point.y)
    }
    return PointBounds(left, top, right, bottom)
}

private fun simplifyClosedCorners(points: List<Offset>, tolerance: Float): List<Offset> {
    if (points.size <= 3) return points
    val rotated = points.rotateForStableClosedSimplify()
    val simplified = simplifyPolyline(rotated + rotated.first(), tolerance)
        .dropLastIfNearFirst(tolerance)
        .withoutNearDuplicates(tolerance)
    if (simplified.size <= 1) return simplified
    return if (distance(simplified.first(), simplified.last()) <= tolerance) simplified.dropLast(1) else simplified
}

private fun List<Offset>.rotateForStableClosedSimplify(): List<Offset> {
    if (size <= 2) return this
    val bounds = pointBounds(this) ?: return this
    val center = bounds.center
    val startIndex = indices.maxByOrNull { index ->
        distanceSquared(this[index], center)
    } ?: 0
    return drop(startIndex) + take(startIndex)
}

private fun simplifyPolyline(points: List<Offset>, tolerance: Float): List<Offset> {
    if (points.size <= 2) return points
    var maxDistance = -1f
    var splitIndex = 0
    val first = points.first()
    val last = points.last()
    for (index in 1 until points.lastIndex) {
        val distance = distanceToSegment(points[index], first, last)
        if (distance > maxDistance) {
            maxDistance = distance
            splitIndex = index
        }
    }
    if (maxDistance <= tolerance || splitIndex == 0) {
        return listOf(first, last)
    }
    val left = simplifyPolyline(points.subList(0, splitIndex + 1), tolerance)
    val right = simplifyPolyline(points.subList(splitIndex, points.size), tolerance)
    return left.dropLast(1) + right
}

private fun isRectangleLike(corners: List<Offset>): Boolean {
    if (corners.size != 4) return false
    val sides = corners.indices.map { index ->
        distance(corners[index], corners[(index + 1) % corners.size])
    }
    if (sides.any { it < ShapeSnapMinimumLengthPx * 0.45f }) return false
    val diagonals = listOf(
        distance(corners[0], corners[2]),
        distance(corners[1], corners[3])
    )
    val diagonalDelta = abs(diagonals[0] - diagonals[1])
    val longestDiagonal = max(diagonals[0], diagonals[1]).coerceAtLeast(1f)
    return diagonalDelta / longestDiagonal <= 0.35f
}

private fun looksEllipseLike(points: List<Offset>, bounds: PointBounds): Boolean {
    val rx = (bounds.width / 2f).coerceAtLeast(1f)
    val ry = (bounds.height / 2f).coerceAtLeast(1f)
    val center = bounds.center
    var totalDeviation = 0f
    var outsideCount = 0
    for (point in points) {
        val normalizedX = (point.x - center.x) / rx
        val normalizedY = (point.y - center.y) / ry
        val radial = sqrt(normalizedX * normalizedX + normalizedY * normalizedY)
        val deviation = abs(radial - 1f)
        totalDeviation += deviation
        if (deviation > 0.42f) outsideCount += 1
    }
    val averageDeviation = totalDeviation / points.size.toFloat()
    return averageDeviation <= 0.24f && outsideCount <= points.size / 5
}

private fun closePolyline(points: List<Offset>): List<Offset> {
    if (points.isEmpty()) return emptyList()
    return if (points.size == 1 || distance(points.first(), points.last()) <= 0.01f) {
        points
    } else {
        points + points.first()
    }
}

private fun sampleEllipse(bounds: PointBounds, start: Offset): List<Offset> {
    val center = bounds.center
    val rx = (bounds.width / 2f).coerceAtLeast(1f)
    val ry = (bounds.height / 2f).coerceAtLeast(1f)
    val startAngle = atan2((start.y - center.y) / ry, (start.x - center.x) / rx)
    val points = mutableListOf<Offset>()
    for (index in 0..ShapeSnapEllipsePointCount) {
        val angle = startAngle + (2.0 * PI * index.toDouble() / ShapeSnapEllipsePointCount.toDouble())
        points += Offset(
            x = center.x + (cos(angle) * rx).toFloat(),
            y = center.y + (sin(angle) * ry).toFloat()
        )
    }
    return points
}

private fun distanceToSegment(point: Offset, start: Offset, end: Offset): Float {
    return sqrt(distanceToSegmentSquared(point, start, end))
}

private fun distanceToSegmentSquared(point: Offset, start: Offset, end: Offset): Float {
    val dx = end.x - start.x
    val dy = end.y - start.y
    if (dx == 0f && dy == 0f) return distanceSquared(point, start)
    val t = (((point.x - start.x) * dx + (point.y - start.y) * dy) / (dx * dx + dy * dy)).coerceIn(0f, 1f)
    val projection = Offset(start.x + t * dx, start.y + t * dy)
    return distanceSquared(point, projection)
}

private fun eraserPathHitsPoint(point: Offset, eraserPoints: List<Offset>, radius: Float): Boolean {
    val radiusSquared = radius * radius
    if (eraserPoints.size == 1) {
        return distanceSquared(point, eraserPoints.first()) <= radiusSquared
    }
    for (index in 1 until eraserPoints.size) {
        if (distanceToSegmentSquared(point, eraserPoints[index - 1], eraserPoints[index]) <= radiusSquared) {
            return true
        }
    }
    return false
}

private fun eraserPathHitsSegment(start: Offset, end: Offset, eraserPoints: List<Offset>, radius: Float): Boolean {
    val radiusSquared = radius * radius
    if (eraserPoints.size == 1) {
        return distanceToSegmentSquared(eraserPoints.first(), start, end) <= radiusSquared
    }
    for (index in 1 until eraserPoints.size) {
        if (segmentDistanceSquared(start, end, eraserPoints[index - 1], eraserPoints[index]) <= radiusSquared) {
            return true
        }
    }
    return false
}

private fun segmentDistanceSquared(a: Offset, b: Offset, c: Offset, d: Offset): Float {
    if (segmentsIntersect(a, b, c, d)) return 0f
    return min(
        min(distanceToSegmentSquared(a, c, d), distanceToSegmentSquared(b, c, d)),
        min(distanceToSegmentSquared(c, a, b), distanceToSegmentSquared(d, a, b))
    )
}

private fun segmentsIntersect(a: Offset, b: Offset, c: Offset, d: Offset): Boolean {
    val abC = orientation(a, b, c)
    val abD = orientation(a, b, d)
    val cdA = orientation(c, d, a)
    val cdB = orientation(c, d, b)
    if (abC == 0f && c.isOnSegment(a, b)) return true
    if (abD == 0f && d.isOnSegment(a, b)) return true
    if (cdA == 0f && a.isOnSegment(c, d)) return true
    if (cdB == 0f && b.isOnSegment(c, d)) return true
    return (abC > 0f) != (abD > 0f) && (cdA > 0f) != (cdB > 0f)
}

private fun orientation(a: Offset, b: Offset, c: Offset): Float {
    val value = (b.x - a.x) * (c.y - a.y) - (b.y - a.y) * (c.x - a.x)
    return when {
        value > 0.0001f -> 1f
        value < -0.0001f -> -1f
        else -> 0f
    }
}

private fun Offset.isOnSegment(start: Offset, end: Offset): Boolean {
    return x >= min(start.x, end.x) - 0.0001f &&
        x <= max(start.x, end.x) + 0.0001f &&
        y >= min(start.y, end.y) - 0.0001f &&
        y <= max(start.y, end.y) + 0.0001f
}
