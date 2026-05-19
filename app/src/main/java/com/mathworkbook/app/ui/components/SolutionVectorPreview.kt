package com.mathworkbook.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import org.json.JSONObject
import java.io.File
import kotlin.math.max
import kotlin.math.min

@Composable
fun SolutionVectorPreview(
    path: String?,
    modifier: Modifier = Modifier
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
            .height(160.dp)
            .background(Color(0xFFFAFAFA), RoundedCornerShape(8.dp))
    ) {
        val maxX = strokes.flatMap { it }.maxOfOrNull { it.x } ?: 1f
        val maxY = strokes.flatMap { it }.maxOfOrNull { it.y } ?: 1f
        val scale = min(size.width / max(maxX, 1f), size.height / max(maxY, 1f)) * 0.92f
        val offset = Offset(8f, 8f)
        strokes.forEach { stroke ->
            stroke.zipWithNext().forEach { (from, to) ->
                drawLine(
                    color = Color(0xFF111827),
                    start = Offset(from.x * scale, from.y * scale) + offset,
                    end = Offset(to.x * scale, to.y * scale) + offset,
                    strokeWidth = 3.5f,
                    cap = StrokeCap.Round
                )
            }
        }
    }
}

private fun readStrokes(path: String): List<List<Offset>> {
    return runCatching {
        val file = File(path)
        if (!file.exists()) return@runCatching emptyList()
        val root = JSONObject(file.readText())
        val strokes = root.optJSONArray("strokes") ?: return@runCatching emptyList()
        List(strokes.length()) { strokeIndex ->
            val points = strokes.getJSONObject(strokeIndex).optJSONArray("points")
            if (points == null) {
                emptyList()
            } else {
                List(points.length()) { pointIndex ->
                    val point = points.getJSONObject(pointIndex)
                    Offset(
                        x = point.optDouble("x", 0.0).toFloat(),
                        y = point.optDouble("y", 0.0).toFloat()
                    )
                }
            }
        }
    }.getOrDefault(emptyList())
}
