package com.mathworkbook.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mathworkbook.app.core.database.ProblemEntity
import org.json.JSONObject

@Composable
fun ProblemWorksheetBackground(
    problem: ProblemEntity?,
    modifier: Modifier = Modifier
) {
    val imageConfig = remember(problem?.imageCropRectJson) {
        WorksheetImageConfig.fromJson(problem?.imageCropRectJson)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 22.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("문제", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)

        if (problem == null) {
            Text("문제 없음", color = MaterialTheme.colorScheme.onSurfaceVariant)
            return@Column
        }

        if (imageConfig.placement == "aboveText") {
            WorksheetProblemImage(problem, imageConfig)
        }

        problem.questionText?.takeIf { it.isNotBlank() }?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.headlineSmall,
                lineHeight = 32.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF111827)
            )
        }

        problem.questionLatex?.takeIf { it.isNotBlank() }?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.titleMedium,
                color = Color(0xFF111827)
            )
        }

        if (imageConfig.placement != "aboveText") {
            WorksheetProblemImage(problem, imageConfig)
        }
    }
}

@Composable
private fun WorksheetProblemImage(problem: ProblemEntity, config: WorksheetImageConfig) {
    if (problem.imagePath.isNullOrBlank()) return
    val alignment = when (config.align) {
        "start" -> Alignment.CenterStart
        "end" -> Alignment.CenterEnd
        else -> Alignment.Center
    }
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = alignment) {
        MaskableProblemImage(
            imagePath = problem.imagePath,
            maskOverlayJson = problem.maskOverlayJson,
            modifier = Modifier
                .fillMaxWidth(config.widthFraction)
                .height(config.heightDp.dp)
                .background(Color.White, RoundedCornerShape(8.dp))
        )
    }
}

private data class WorksheetImageConfig(
    val heightDp: Int = 300,
    val widthFraction: Float = 1f,
    val align: String = "center",
    val placement: String = "belowText"
) {
    companion object {
        fun fromJson(json: String?): WorksheetImageConfig {
            if (json.isNullOrBlank()) return WorksheetImageConfig()
            return runCatching {
                val root = JSONObject(json)
                val display = root.optJSONObject("display") ?: root
                WorksheetImageConfig(
                    heightDp = display.optInt("heightDp", 300).coerceIn(160, 620),
                    widthFraction = display.optDouble("widthFraction", 1.0).toFloat().coerceIn(0.35f, 1f),
                    align = display.optString("align", "center"),
                    placement = display.optString("placement", "belowText")
                )
            }.getOrDefault(WorksheetImageConfig())
        }
    }
}
