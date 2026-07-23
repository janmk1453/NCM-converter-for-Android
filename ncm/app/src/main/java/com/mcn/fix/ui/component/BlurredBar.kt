package com.mcn.fix.ui.component

import android.os.Build
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurBlendMode
import top.yukonga.miuix.kmp.blur.BlurDefaults
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.blur.textureBlur
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun rememberBlurBackdrop(): LayerBackdrop? {
    if (Build.VERSION.SDK_INT < 33) return null
    val surface = MiuixTheme.colorScheme.surface
    return rememberLayerBackdrop {
        drawRect(surface)
        drawContent()
    }
}

@Composable
fun BlurredBar(
    backdrop: LayerBackdrop?,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(0.dp),
    blurRadius: Float = BlurDefaults.BlurRadius,
    content: @Composable () -> Unit,
) {
    if (backdrop != null) {
        Box(
            modifier = modifier.textureBlur(
                backdrop = backdrop,
                shape = shape,
                blurRadius = blurRadius,
                colors = BlurDefaults.blurColors(
                    blendColors = listOf(
                        BlendColorEntry(
                            color = MiuixTheme.colorScheme.surface.copy(alpha = 0.6f),
                            mode = BlurBlendMode.SrcOver,
                        )
                    )
                ),
            ),
        ) {
            content()
        }
    } else {
        Box(modifier = modifier) {
            content()
        }
    }
}

@Composable
fun BlurBackdropContent(
    backdrop: LayerBackdrop?,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier.then(
            if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier
        ),
    ) {
        content()
    }
}
