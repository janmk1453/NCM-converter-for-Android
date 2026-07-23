package com.mcn.fix.ui.component

import android.os.Build
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurColors
import top.yukonga.miuix.kmp.blur.BlurDefaults
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.blur.textureBlur
import top.yukonga.miuix.kmp.shader.isRenderEffectSupported
import top.yukonga.miuix.kmp.blur.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun rememberBlurBackdrop(enableBlur: Boolean = true): LayerBackdrop? {
    if (!enableBlur || Build.VERSION.SDK_INT < 33 ||
        !isRenderEffectSupported() || !isRuntimeShaderSupported()
    ) return null
    val surface = MiuixTheme.colorScheme.surface
    return rememberLayerBackdrop {
        drawRect(surface)
        drawContent()
    }
}

@Composable
fun Modifier.miuixBarBlur(backdrop: LayerBackdrop?, enabled: Boolean = true): Modifier {
    if (!enabled || backdrop == null) return this
    val blendColor = MiuixTheme.colorScheme.surface.copy(alpha = 0.8f)
    return then(
        Modifier.textureBlur(
            backdrop = backdrop,
            shape = RectangleShape,
            blurRadius = 25f,
            colors = BlurColors(
                blendColors = listOf(
                    BlendColorEntry(blendColor),
                ),
            ),
        ),
    )
}

@Composable
fun LayerBackdrop?.getAppBarColor(): Color =
    this?.let { Color.Transparent } ?: MiuixTheme.colorScheme.surface

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
