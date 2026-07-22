package com.mcn.fix.ui.component

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private val MaxContentWidth = 800.dp
private val WideScreenMinWidth = 600.dp

@Composable
fun WideContentBox(
    modifier: Modifier = Modifier,
    content: @Composable (sidePadding: Dp) -> Unit,
) {
    BoxWithConstraints(modifier = modifier) {
        val isWide = maxWidth >= WideScreenMinWidth
        val sidePadding = if (isWide) {
            ((maxWidth - MaxContentWidth) / 2).coerceAtLeast(0.dp)
        } else {
            0.dp
        }
        content(sidePadding)
    }
}
