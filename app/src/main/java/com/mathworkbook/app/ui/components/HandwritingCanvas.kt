package com.mathworkbook.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.json.JSONArray
import org.json.JSONObject

data class Stroke(val points: List<Offset>, val color: Color = Color(0xFF111827), val width: Float = 5f)

@Stable
class HandwritingState {
    val strokes = mutableStateListOf<Stroke>()

    fun clear() {
        strokes.clear()
    }

    fun start(position: Offset) {
        strokes += Stroke(points = listOf(position))
    }

    fun append(position: Offset) {
        val last = strokes.lastOrNull() ?: return
        strokes.removeAt(strokes.lastIndex)
        strokes += last.copy(points = last.points + position)
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
                    .put("color", "#111827")
                    .put("width", stroke.width)
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
    contentHeight: Dp = 960.dp
) {
    val scrollState = rememberScrollState()

    BoxWithConstraints(
        modifier = modifier.background(Color(0xFFFAFAFA), RoundedCornerShape(8.dp))
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(contentHeight)
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { state.start(it) },
                            onDrag = { change, _ -> state.append(change.position) }
                        )
                    }
            ) {
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
                state.strokes.forEach { stroke ->
                    stroke.points.zipWithNext().forEach { (from, to) ->
                        drawLine(
                            color = stroke.color,
                            start = from,
                            end = to,
                            strokeWidth = stroke.width,
                            cap = StrokeCap.Round
                        )
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

        Button(
            onClick = state::clear,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 8.dp, end = 18.dp)
        ) {
            Text("지우기")
        }
    }
}
