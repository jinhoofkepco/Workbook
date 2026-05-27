package com.mathworkbook.app.ui.components

import android.graphics.BitmapFactory
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mathworkbook.app.core.database.ProblemEntity
import com.mathworkbook.app.ui.skin.SkinAssetImage
import org.json.JSONObject
import kotlin.math.roundToInt

data class WorksheetImageBounds(
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float
)

enum class WorksheetImageAdjustmentMode {
    None,
    Image,
    Frame
}

data class WorksheetImageTransform(
    val scale: Float = 1f,
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
    val heightDp: Int? = null
) {
    fun normalized(): WorksheetImageTransform {
        return copy(
            scale = scale.coerceIn(0.6f, 2.2f),
            offsetX = offsetX.coerceIn(-1600f, 1600f),
            offsetY = offsetY.coerceIn(-1600f, 1600f),
            heightDp = heightDp?.coerceIn(160, 1800)
        )
    }
}

fun parseWorksheetImageTransform(json: String?): WorksheetImageTransform {
    return WorksheetImageConfig.fromJson(json).transform
}

fun estimateWorksheetContentHeightDp(problem: ProblemEntity?): Int {
    if (problem?.imagePath.isNullOrBlank()) return 1320
    val config = WorksheetImageConfig.fromJson(problem?.imageCropRectJson)
    val imageOnly = problem?.questionText.isNullOrBlank() && problem?.questionLatex.isNullOrBlank()
    return if (imageOnly) {
        val naturalHeightEstimate = readImageAspectRatio(problem?.imagePath)
            ?.let { aspectRatio -> (820f / aspectRatio).toInt() }
            ?: 0
        maxOf(1320, config.resolveHeightDp(problem!!) + 420, naturalHeightEstimate + 420)
    } else {
        1320
    }
}

@Composable
fun ProblemWorksheetBackground(
    problem: ProblemEntity?,
    questionTextSizeSp: Int = 24,
    modifier: Modifier = Modifier,
    onImageBoundsChanged: ((WorksheetImageBounds) -> Unit)? = null,
    imageAdjustMode: Boolean = false,
    imageAdjustmentMode: WorksheetImageAdjustmentMode = if (imageAdjustMode) {
        WorksheetImageAdjustmentMode.Image
    } else {
        WorksheetImageAdjustmentMode.None
    },
    imageTransformOverride: WorksheetImageTransform? = null,
    onImageTransformChanged: ((WorksheetImageTransform) -> Unit)? = null,
    footerContent: @Composable ColumnScope.() -> Unit = {}
) {
    ProblemWorksheetLayout(
        problem = problem,
        questionTextSizeSp = questionTextSizeSp,
        modifier = modifier,
        bodyMode = WorksheetBodyMode.Visible,
        onImageBoundsChanged = onImageBoundsChanged,
        imageAdjustmentMode = imageAdjustmentMode,
        imageTransformOverride = imageTransformOverride,
        onImageTransformChanged = onImageTransformChanged,
        footerContent = footerContent
    )
}

@Composable
fun ProblemWorksheetFooterOverlay(
    problem: ProblemEntity?,
    questionTextSizeSp: Int = 24,
    modifier: Modifier = Modifier,
    onImageBoundsChanged: ((WorksheetImageBounds) -> Unit)? = null,
    imageAdjustMode: Boolean = false,
    imageAdjustmentMode: WorksheetImageAdjustmentMode = if (imageAdjustMode) {
        WorksheetImageAdjustmentMode.Image
    } else {
        WorksheetImageAdjustmentMode.None
    },
    imageTransformOverride: WorksheetImageTransform? = null,
    onImageTransformChanged: ((WorksheetImageTransform) -> Unit)? = null,
    footerContent: @Composable ColumnScope.() -> Unit = {}
) {
    ProblemWorksheetLayout(
        problem = problem,
        questionTextSizeSp = questionTextSizeSp,
        modifier = modifier,
        bodyMode = WorksheetBodyMode.Placeholder,
        onImageBoundsChanged = onImageBoundsChanged,
        imageAdjustmentMode = imageAdjustmentMode,
        imageTransformOverride = imageTransformOverride,
        onImageTransformChanged = onImageTransformChanged,
        footerContent = footerContent
    )
}

@Composable
private fun ProblemWorksheetLayout(
    problem: ProblemEntity?,
    questionTextSizeSp: Int,
    modifier: Modifier,
    bodyMode: WorksheetBodyMode,
    onImageBoundsChanged: ((WorksheetImageBounds) -> Unit)?,
    imageAdjustmentMode: WorksheetImageAdjustmentMode,
    imageTransformOverride: WorksheetImageTransform?,
    onImageTransformChanged: ((WorksheetImageTransform) -> Unit)?,
    footerContent: @Composable ColumnScope.() -> Unit
) {
    val imageConfig = remember(problem?.imageCropRectJson) {
        WorksheetImageConfig.fromJson(problem?.imageCropRectJson)
    }
    var rootLeft by remember { mutableStateOf(0f) }
    var rootTop by remember { mutableStateOf(0f) }
    val bodyColor = if (bodyMode == WorksheetBodyMode.Visible) {
        Color(0xFF111827)
    } else {
        Color.Transparent
    }
    val emptyColor = if (bodyMode == WorksheetBodyMode.Visible) {
        MaterialTheme.colorScheme.onSurfaceVariant
    } else {
        Color.Transparent
    }

    val imageOnly = problem?.imagePath?.isNotBlank() == true &&
        problem.questionText.isNullOrBlank() &&
        problem.questionLatex.isNullOrBlank()
    val horizontalPadding = if (imageOnly) 0.dp else 24.dp

    Box(
        modifier = modifier
            .fillMaxSize()
            .onGloballyPositioned { coordinates ->
                val rootPosition = coordinates.positionInRoot()
                rootLeft = rootPosition.x
                rootTop = rootPosition.y
            }
    ) {
        if (bodyMode == WorksheetBodyMode.Visible) {
            SkinAssetImage(
                assetKey = "problemPaperBackground",
                modifier = Modifier.matchParentSize(),
                contentScale = ContentScale.Crop,
                alpha = 0.34f
            )
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = horizontalPadding, end = horizontalPadding, top = 36.dp, bottom = 22.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
        if (problem == null) {
            Text("문제 없음", color = emptyColor)
            footerContent()
            return@Column
        }

        if (imageConfig.placement == "aboveText") {
            WorksheetProblemImageSlot(
                problem,
                imageConfig,
                bodyMode,
                rootLeft,
                rootTop,
                onImageBoundsChanged,
                imageAdjustmentMode,
                imageTransformOverride,
                onImageTransformChanged
            )
        }

        problem.questionText?.takeIf { it.isNotBlank() }?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontSize = questionTextSizeSp.sp,
                    lineHeight = (questionTextSizeSp + 8).sp
                ),
                fontWeight = FontWeight.Medium,
                color = bodyColor
            )
        }

        problem.questionLatex?.takeIf { it.isNotBlank() }?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontSize = (questionTextSizeSp - 4).coerceAtLeast(14).sp,
                    lineHeight = (questionTextSizeSp + 4).sp
                ),
                color = bodyColor
            )
        }

        if (imageConfig.placement != "aboveText") {
            WorksheetProblemImageSlot(
                problem,
                imageConfig,
                bodyMode,
                rootLeft,
                rootTop,
                onImageBoundsChanged,
                imageAdjustmentMode,
                imageTransformOverride,
                onImageTransformChanged
            )
        }

        footerContent()
    }
    }
}

private enum class WorksheetBodyMode {
    Visible,
    Placeholder
}

@Composable
private fun WorksheetProblemImageSlot(
    problem: ProblemEntity,
    config: WorksheetImageConfig,
    bodyMode: WorksheetBodyMode,
    rootLeft: Float,
    rootTop: Float,
    onImageBoundsChanged: ((WorksheetImageBounds) -> Unit)?,
    imageAdjustmentMode: WorksheetImageAdjustmentMode,
    imageTransformOverride: WorksheetImageTransform?,
    onImageTransformChanged: ((WorksheetImageTransform) -> Unit)?
) {
    if (bodyMode == WorksheetBodyMode.Visible) {
        WorksheetProblemImage(
            problem,
            config,
            rootLeft,
            rootTop,
            onImageBoundsChanged,
            imageAdjustmentMode,
            imageTransformOverride,
            onImageTransformChanged
        )
    } else {
        WorksheetProblemImagePlaceholder(problem, config)
    }
}

@Composable
private fun WorksheetProblemImage(
    problem: ProblemEntity,
    config: WorksheetImageConfig,
    rootLeft: Float,
    rootTop: Float,
    onImageBoundsChanged: ((WorksheetImageBounds) -> Unit)?,
    imageAdjustmentMode: WorksheetImageAdjustmentMode,
    imageTransformOverride: WorksheetImageTransform?,
    onImageTransformChanged: ((WorksheetImageTransform) -> Unit)?
) {
    if (problem.imagePath.isNullOrBlank()) return
    val alignment = when (config.align) {
        "start" -> Alignment.CenterStart
        "end" -> Alignment.CenterEnd
        else -> Alignment.Center
    }
    val transform = (imageTransformOverride ?: config.transform).normalized()
    val density = LocalDensity.current
    val heightDp = transform.heightDp ?: config.resolveHeightDp(problem)
    val imageAspectRatio = remember(problem.imagePath) { readImageAspectRatio(problem.imagePath) }
    val sizeModifier = Modifier
        .fillMaxWidth(config.widthFraction)
        .let { base ->
            if (transform.heightDp == null && config.useNaturalAspectRatio(problem, imageAspectRatio) && imageAspectRatio != null) {
                base.aspectRatio(imageAspectRatio)
            } else {
                base.height(heightDp.dp)
            }
        }
    val transformModifier = Modifier
        .graphicsLayer {
            scaleX = transform.scale
            scaleY = transform.scale
            translationX = transform.offsetX
            translationY = transform.offsetY
            transformOrigin = TransformOrigin(0f, 0f)
        }
        .then(
            if (imageAdjustmentMode == WorksheetImageAdjustmentMode.Image) {
                Modifier.pointerInput(imageAdjustmentMode) {
                    var currentTransform = transform
                    detectTransformGestures { _, pan, zoom, _ ->
                        val next = currentTransform.copy(
                            scale = currentTransform.scale * zoom,
                            offsetX = currentTransform.offsetX + pan.x,
                            offsetY = currentTransform.offsetY + pan.y
                        ).normalized()
                        currentTransform = next
                        onImageTransformChanged?.invoke(next)
                    }
                }
            } else {
                Modifier
            }
        )
    val frameAdjustModifier = if (imageAdjustmentMode == WorksheetImageAdjustmentMode.Frame) {
        Modifier.pointerInput(imageAdjustmentMode) {
            var currentTransform = transform
            detectTransformGestures { _, pan, zoom, _ ->
                val currentHeight = currentTransform.heightDp ?: heightDp
                val panDp = pan.y / density.density
                val next = currentTransform.copy(
                    heightDp = (currentHeight * zoom + panDp).roundToInt()
                ).normalized()
                currentTransform = next
                onImageTransformChanged?.invoke(next)
            }
        }
    } else {
        Modifier
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clipToBounds()
            .then(frameAdjustModifier)
            .then(
                when (imageAdjustmentMode) {
                    WorksheetImageAdjustmentMode.Image -> Modifier.border(2.dp, Color(0xFF2563EB), RoundedCornerShape(8.dp))
                    WorksheetImageAdjustmentMode.Frame -> Modifier.border(2.dp, Color(0xFFDC2626), RoundedCornerShape(8.dp))
                    WorksheetImageAdjustmentMode.None -> Modifier
                }
            ),
        contentAlignment = alignment
    ) {
        MaskableProblemImage(
            imagePath = problem.imagePath,
            maskOverlayJson = problem.maskOverlayJson,
            contentScale = config.resolveContentScale(problem),
            modifier = sizeModifier
                .onGloballyPositioned { coordinates ->
                    val position = coordinates.positionInRoot()
                    onImageBoundsChanged?.invoke(
                        WorksheetImageBounds(
                            left = position.x - rootLeft + transform.offsetX,
                            top = position.y - rootTop + transform.offsetY,
                            width = coordinates.size.width.toFloat() * transform.scale,
                            height = coordinates.size.height.toFloat() * transform.scale
                        )
                    )
                }
                .then(transformModifier)
                .background(Color.White, RoundedCornerShape(8.dp))
        )
    }
}

@Composable
private fun WorksheetProblemImagePlaceholder(problem: ProblemEntity, config: WorksheetImageConfig) {
    if (problem.imagePath.isNullOrBlank()) return
    val alignment = when (config.align) {
        "start" -> Alignment.CenterStart
        "end" -> Alignment.CenterEnd
        else -> Alignment.Center
    }
    val transform = config.transform.normalized()
    val heightDp = transform.heightDp ?: config.resolveHeightDp(problem)
    val imageAspectRatio = remember(problem.imagePath) { readImageAspectRatio(problem.imagePath) }
    val sizeModifier = Modifier
        .fillMaxWidth(config.widthFraction)
        .let { base ->
            if (transform.heightDp == null && config.useNaturalAspectRatio(problem, imageAspectRatio) && imageAspectRatio != null) {
                base.aspectRatio(imageAspectRatio)
            } else {
                base.height(heightDp.dp)
            }
        }
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = alignment) {
        Spacer(
            modifier = sizeModifier
        )
    }
}

private data class WorksheetImageConfig(
    val heightDp: Int = 300,
    val widthFraction: Float = 1f,
    val align: String = "center",
    val placement: String = "belowText",
    val contentScale: String = "auto",
    val transform: WorksheetImageTransform = WorksheetImageTransform()
) {
    fun resolveHeightDp(problem: ProblemEntity): Int {
        val hasText = !problem.questionText.isNullOrBlank() || !problem.questionLatex.isNullOrBlank()
        return if (!hasText && heightDp == DefaultHeightDp) 900 else heightDp
    }

    fun resolveContentScale(problem: ProblemEntity): ContentScale {
        return when (contentScale.lowercase()) {
            "fit" -> ContentScale.Fit
            "fillwidth", "fill_width", "width" -> ContentScale.Fit
            "crop" -> ContentScale.Crop
            else -> ContentScale.Fit
        }
    }

    fun useNaturalAspectRatio(problem: ProblemEntity, imageAspectRatio: Float?): Boolean {
        val imageOnly = problem.questionText.isNullOrBlank() && problem.questionLatex.isNullOrBlank()
        val wideFullWidthImage = widthFraction >= 0.95f && (imageAspectRatio ?: 0f) >= 1.55f
        return when (contentScale.lowercase()) {
            "fillwidth", "fill_width", "width" -> true
            "fit", "crop" -> false
            else -> imageOnly || wideFullWidthImage
        }
    }

    companion object {
        private const val DefaultHeightDp = 300

        fun fromJson(json: String?): WorksheetImageConfig {
            if (json.isNullOrBlank()) return WorksheetImageConfig()
            return runCatching {
                val root = JSONObject(json)
                val display = root.optJSONObject("display") ?: root
                WorksheetImageConfig(
                    heightDp = display.optInt("heightDp", DefaultHeightDp).coerceIn(160, 1800),
                    widthFraction = display.optDouble("widthFraction", 1.0).toFloat().coerceIn(0.35f, 1f),
                    align = display.optString("align", "center"),
                    placement = display.optString("placement", "belowText"),
                    contentScale = display.optString("contentScale", "auto"),
                    transform = WorksheetImageTransform(
                        scale = display.optDouble("scale", display.optDouble("transformScale", 1.0)).toFloat(),
                        offsetX = display.optDouble("offsetX", 0.0).toFloat(),
                        offsetY = display.optDouble("offsetY", 0.0).toFloat(),
                        heightDp = if (display.has("heightDp")) display.optInt("heightDp", DefaultHeightDp) else null
                    ).normalized()
                )
            }.getOrDefault(WorksheetImageConfig())
        }
    }
}

private fun readImageAspectRatio(path: String?): Float? {
    if (path.isNullOrBlank()) return null
    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, options)
    val width = options.outWidth
    val height = options.outHeight
    return if (width > 0 && height > 0) width.toFloat() / height.toFloat() else null
}
