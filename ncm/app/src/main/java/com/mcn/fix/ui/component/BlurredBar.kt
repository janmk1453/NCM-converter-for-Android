package com.mcn.fix.ui.component

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import top.yukonga.miuix.kmp.theme.MiuixTheme

class BlurBackdrop

@Composable
fun rememberBlurBackdrop(): BlurBackdrop? {
    return remember {
        if (Build.VERSION.SDK_INT >= 33) BlurBackdrop() else null
    }
}

@Composable
fun BlurredBar(
    backdrop: BlurBackdrop?,
    blurActive: Boolean,
    content: @Composable () -> Unit,
) {
    if (blurActive && backdrop != null) {
        BoxWithBlur(
            content = content,
        )
    } else {
        content()
    }
}

@Composable
private fun BoxWithBlur(
    content: @Composable () -> Unit,
) {
    androidx.compose.foundation.layout.Box(
        modifier = Modifier.background(Color.Transparent),
    ) {
        content()
    }
}

@Composable
fun Modifier.layerBackdrop(backdrop: BlurBackdrop?): Modifier {
    return this
}
