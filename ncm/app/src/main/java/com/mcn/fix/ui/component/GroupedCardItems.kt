package com.mcn.fix.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.theme.MiuixTheme

data class CardItem(
    val key: String,
    val content: @Composable () -> Unit,
)

@Composable
fun CardSegment(
    modifier: Modifier = Modifier,
    isFirst: Boolean = false,
    isLast: Boolean = false,
    insidePadding: Dp = 0.dp,
    bottomCornerRadius: Dp = 16.dp,
    content: @Composable () -> Unit,
) {
    val shape = getSegmentShape(isFirst, isLast, bottomCornerRadius)
    Box(
        modifier = modifier
            .then(
                if (isFirst || isLast) {
                    Modifier
                        .clip(shape)
                        .background(MiuixTheme.colorScheme.surfaceContainer)
                } else {
                    Modifier.background(MiuixTheme.colorScheme.surfaceContainer)
                }
            )
            .then(
                if (insidePadding.value > 0f) Modifier.padding(horizontal = insidePadding)
                else Modifier
            ),
    ) {
        content()
    }
}

private fun getSegmentShape(isFirst: Boolean, isLast: Boolean, bottomCornerRadius: Dp): Shape {
    return androidx.compose.foundation.shape.RoundedCornerShape(
        topStart = if (isFirst) 16.dp else 0.dp,
        topEnd = if (isFirst) 16.dp else 0.dp,
        bottomStart = if (isLast) bottomCornerRadius else 0.dp,
        bottomEnd = if (isLast) bottomCornerRadius else 0.dp,
    )
}

fun LazyListScope.groupedCardItems(
    keyPrefix: String,
    items: List<CardItem>,
    outerBottomPadding: Dp = 12.dp,
    insidePadding: Dp = 0.dp,
) {
    items.forEachIndexed { index, item ->
        val isFirst = index == 0
        val isLast = index == items.lastIndex
        item(key = "$keyPrefix-${item.key}") {
            CardSegment(
                isFirst = isFirst,
                isLast = isLast,
                insidePadding = insidePadding,
            ) {
                item.content()
            }
            if (isLast) {
                androidx.compose.foundation.layout.Spacer(
                    modifier = Modifier.padding(bottom = outerBottomPadding),
                )
            }
        }
    }
}
