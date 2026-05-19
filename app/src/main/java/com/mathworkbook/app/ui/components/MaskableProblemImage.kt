package com.mathworkbook.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import org.json.JSONObject

@Composable
fun MaskableProblemImage(
    imagePath: String?,
    maskOverlayJson: String?,
    modifier: Modifier = Modifier
) {
    if (imagePath.isNullOrBlank()) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(160.dp)
                .background(Color(0xFFF3F4F6)),
            contentAlignment = Alignment.Center
        ) {
            Text("이미지 없음", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    Box(modifier = modifier) {
        AsyncImage(
            model = imagePath,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize()
        )
        Canvas(modifier = Modifier.fillMaxSize()) {
            parseMaskRects(maskOverlayJson).forEach { rect ->
                drawRect(
                    color = Color.White,
                    topLeft = Offset(rect.left * size.width, rect.top * size.height),
                    size = Size(rect.width * size.width, rect.height * size.height)
                )
            }
        }
    }
}

private data class MaskRect(val left: Float, val top: Float, val width: Float, val height: Float)

private fun parseMaskRects(json: String?): List<MaskRect> {
    if (json.isNullOrBlank()) return emptyList()
    return runCatching {
        val root = JSONObject(json)
        val items = root.optJSONArray("items") ?: return@runCatching emptyList()
        List(items.length()) { index ->
            val item = items.getJSONObject(index)
            val rect = item.getJSONObject("rect")
            MaskRect(
                left = rect.getDouble("left").toFloat(),
                top = rect.getDouble("top").toFloat(),
                width = rect.getDouble("width").toFloat(),
                height = rect.getDouble("height").toFloat()
            )
        }
    }.getOrDefault(emptyList())
}
