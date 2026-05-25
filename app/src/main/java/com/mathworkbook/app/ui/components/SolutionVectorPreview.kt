package com.mathworkbook.app.ui.components

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import org.json.JSONObject
import java.io.File
import kotlin.math.max
import kotlin.math.min

@Composable
fun SolutionVectorPreview(
    path: String?,
    modifier: Modifier = Modifier,
    height: Dp = 160.dp
) {
    if (path.isNullOrBlank()) {
        Text("풀이 기록 없음")
        return
    }
    val strokes = remember(path) { readStrokes(path) }
    if (strokes.isEmpty()) {
        Text("풀이 기록 없음")
        return
    }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .background(Color(0xFFFAFAFA), RoundedCornerShape(8.dp))
    ) {
        val maxX = strokes.flatMap { it.points }.maxOfOrNull { it.x } ?: 1f
        val maxY = strokes.flatMap { it.points }.maxOfOrNull { it.y } ?: 1f
        val scale = min(size.width / max(maxX, 1f), size.height / max(maxY, 1f)) * 0.92f
        val offset = Offset(8f, 8f)
        strokes.forEach { stroke ->
            val path = stroke.points
                .map { point -> Offset(point.x * scale, point.y * scale) + offset }
                .toPath()
            if (path != null) {
                val isHighlighter = stroke.kind == StrokeKind.Highlighter
                drawPath(
                    path = path,
                    color = stroke.color,
                    style = Stroke(
                        width = (stroke.width * 0.7f).coerceIn(3.5f, 16f),
                        cap = if (isHighlighter) StrokeCap.Butt else StrokeCap.Round,
                        join = if (isHighlighter) StrokeJoin.Bevel else StrokeJoin.Round
                    )
                )
            }
        }
    }
}

@Composable
fun SolutionVectorOverlay(
    path: String?,
    modifier: Modifier = Modifier
) {
    if (path.isNullOrBlank()) return
    val strokes = remember(path) { readStrokes(path) }
    if (strokes.isEmpty()) return

    Canvas(modifier = modifier.fillMaxSize()) {
        strokes.forEach { stroke ->
            val path = stroke.points.toPath()
            if (path != null) {
                val isHighlighter = stroke.kind == StrokeKind.Highlighter
                drawPath(
                    path = path,
                    color = stroke.color,
                    style = Stroke(
                        width = stroke.width,
                        cap = if (isHighlighter) StrokeCap.Butt else StrokeCap.Round,
                        join = if (isHighlighter) StrokeJoin.Bevel else StrokeJoin.Round
                    )
                )
            }
        }
    }
}

private data class PreviewStroke(
    val points: List<Offset>,
    val color: Color,
    val width: Float,
    val kind: StrokeKind
)

private fun readStrokes(path: String): List<PreviewStroke> {
    return runCatching {
        val file = File(path)
        if (!file.exists()) return@runCatching emptyList()
        val root = JSONObject(file.readText())
        val strokes = root.optJSONArray("strokes") ?: return@runCatching emptyList()
        List(strokes.length()) { strokeIndex ->
            val stroke = strokes.getJSONObject(strokeIndex)
            val points = stroke.optJSONArray("points")
            if (points == null) {
                PreviewStroke(emptyList(), Color(0xFF111827), 5f, StrokeKind.Pen)
            } else {
                PreviewStroke(
                    points = List(points.length()) { pointIndex ->
                        val point = points.getJSONObject(pointIndex)
                        Offset(
                            x = point.optDouble("x", 0.0).toFloat(),
                            y = point.optDouble("y", 0.0).toFloat()
                        )
                    },
                    color = parseStrokeColor(stroke.optString("color")),
                    width = stroke.optDouble("width", 5.0).toFloat(),
                    kind = stroke.optString("kind")
                        .takeIf { it.isNotBlank() }
                        ?.let { runCatching { StrokeKind.valueOf(it) }.getOrNull() }
                        ?: if (stroke.optDouble("width", 5.0) >= 12.0) StrokeKind.Highlighter else StrokeKind.Pen
                )
            }
        }.filter { it.points.size > 1 }
    }.getOrDefault(emptyList())
}

private fun parseStrokeColor(value: String?): Color {
    if (value.isNullOrBlank()) return Color(0xFF111827)
    return runCatching { Color(AndroidColor.parseColor(value)) }.getOrDefault(Color(0xFF111827))
}

private fun List<Offset>.toPath(): Path? {
    if (size < 2) return null
    return Path().also { path ->
        path.moveTo(first().x, first().y)
        drop(1).forEach { point -> path.lineTo(point.x, point.y) }
    }
}
