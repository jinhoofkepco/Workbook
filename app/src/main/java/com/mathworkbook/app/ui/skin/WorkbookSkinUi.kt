package com.mathworkbook.app.ui.skin

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import com.mathworkbook.app.core.skin.WorkbookSkin

val LocalWorkbookSkin = staticCompositionLocalOf<WorkbookSkin?> { null }

@Composable
fun SkinAssetImage(
    assetKey: String,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    alpha: Float = 1f
) {
    val assetPath = LocalWorkbookSkin.current?.assetPath(assetKey) ?: return
    AsyncImage(
        model = assetPath,
        contentDescription = null,
        contentScale = contentScale,
        modifier = modifier.alpha(alpha)
    )
}
