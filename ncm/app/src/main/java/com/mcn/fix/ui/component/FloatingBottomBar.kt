package com.mcn.fix.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastCoerceIn
import androidx.compose.ui.util.fastRoundToInt
import com.mcn.fix.ui.interaction.DampedDragAnimation
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.blur.blur
import top.yukonga.miuix.kmp.blur.drawBackdrop
import top.yukonga.miuix.kmp.theme.MiuixTheme
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.spring
import kotlin.math.abs
import kotlin.math.sign
import top.yukonga.miuix.kmp.theme.LocalContentColor as MiuixLocalContentColor

val LocalFloatingBottomBarContentColor = staticCompositionLocalOf { Color.Unspecified }

@Immutable
class FloatingBottomBarColors(
    val containerColor: Color,
    val indicatorColor: Color,
    val contentColor: Color,
    val activeContentColor: Color,
)

object FloatingBottomBarDefaults {
    @Composable
    fun colors(
        containerColor: Color = MiuixTheme.colorScheme.surfaceContainer,
        indicatorColor: Color = MiuixTheme.colorScheme.primary,
        contentColor: Color = MiuixTheme.colorScheme.onSurface,
        activeContentColor: Color = indicatorColor,
    ): FloatingBottomBarColors = FloatingBottomBarColors(
        containerColor = containerColor,
        indicatorColor = indicatorColor,
        contentColor = contentColor,
        activeContentColor = activeContentColor,
    )
}

enum class FloatingBottomBarMode {
    Blur,
    None,
}

@Composable
fun RowScope.FloatingBottomBarItem(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val contentColor = LocalFloatingBottomBarContentColor.current

    Column(
        modifier
            .clip(CircleShape)
            .clickable(
                interactionSource = null,
                indication = null,
                role = Role.Tab,
                onClick = onClick,
            )
            .fillMaxHeight()
            .weight(1f),
        verticalArrangement = Arrangement.spacedBy(1.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CompositionLocalProvider(MiuixLocalContentColor provides contentColor) {
            content()
        }
    }
}

@Composable
fun FloatingBottomBar(
    modifier: Modifier = Modifier,
    selectedIndex: () -> Int,
    onSelected: (index: Int) -> Unit,
    backdrop: Backdrop?,
    tabsCount: Int,
    mode: FloatingBottomBarMode = FloatingBottomBarMode.None,
    colors: FloatingBottomBarColors = FloatingBottomBarDefaults.colors(),
    content: @Composable RowScope.() -> Unit,
) {
    val isInDark = MiuixTheme.colorScheme.surface.let { c ->
        (0.299 * c.red * 255 + 0.587 * c.green * 255 + 0.114 * c.blue * 255) < 128
    }
    val pillShape = remember { CircleShape }
    val isBlurMode = mode == FloatingBottomBarMode.Blur && backdrop != null
    val containerColor = colors.containerColor
    val density = LocalDensity.current
    val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
    val animationScope = rememberCoroutineScope()

    var tabWidthPx by remember { mutableFloatStateOf(0f) }
    var totalWidthPx by remember { mutableFloatStateOf(0f) }
    val offsetAnimation = remember { androidx.compose.animation.core.Animatable(0f) }
    val rubberBandPx = with(density) { 4.dp.toPx() }
    val panelOffset by remember(rubberBandPx) {
        derivedStateOf {
            if (totalWidthPx == 0f) 0f else {
                val fraction = (offsetAnimation.value / totalWidthPx).fastCoerceIn(-1f, 1f)
                rubberBandPx * sign(fraction) * EaseOut.transform(abs(fraction))
            }
        }
    }
    var currentIndex by remember(selectedIndex) { mutableIntStateOf(selectedIndex()) }

    class DampedDragAnimationHolder {
        var instance: DampedDragAnimation? = null
    }

    val holder = remember { DampedDragAnimationHolder() }
    val dampedDragAnimation = remember(animationScope, tabsCount, density, isLtr) {
        DampedDragAnimation(
            animationScope = animationScope,
            initialValue = selectedIndex().toFloat(),
            valueRange = 0f..(tabsCount - 1).toFloat(),
            visibilityThreshold = 0.001f,
            initialScale = 1f,
            pressedScale = 78f / 56f,
            canDrag = { offset ->
                val anim = holder.instance ?: return@DampedDragAnimation true
                if (tabWidthPx == 0f) return@DampedDragAnimation false

                val indicatorX = anim.value * tabWidthPx
                val padding = with(density) { 4.dp.toPx() }
                val globalTouchX = if (isLtr) {
                    padding + indicatorX + offset.x
                } else {
                    totalWidthPx - padding - tabWidthPx - indicatorX + offset.x
                }
                globalTouchX in 0f..totalWidthPx
            },
            onDragStarted = {},
            onDragStopped = {
                val targetIndex = targetValue.fastRoundToInt().fastCoerceIn(0, tabsCount - 1)
                currentIndex = targetIndex
                animateToValue(targetIndex.toFloat())
                animationScope.launch {
                    offsetAnimation.animateTo(0f, spring(1f, 300f, 0.5f))
                }
            },
            onDrag = { _, dragAmount ->
                if (tabWidthPx > 0) {
                    updateValue(
                        (targetValue + dragAmount.x / tabWidthPx * if (isLtr) 1f else -1f)
                            .fastCoerceIn(0f, (tabsCount - 1).toFloat()),
                    )
                    animationScope.launch {
                        offsetAnimation.snapTo(offsetAnimation.value + dragAmount.x)
                    }
                }
            },
        ).also { holder.instance = it }
    }

    LaunchedEffect(selectedIndex) {
        snapshotFlow { selectedIndex() }.collectLatest { currentIndex = it }
    }
    LaunchedEffect(dampedDragAnimation) {
        snapshotFlow { currentIndex }.drop(1).collectLatest { index ->
            dampedDragAnimation.animateToValue(index.toFloat())
            onSelected(index)
        }
    }

    Box(
        modifier = modifier.width(IntrinsicSize.Min),
        contentAlignment = Alignment.CenterStart,
    ) {
        CompositionLocalProvider(LocalFloatingBottomBarContentColor provides colors.contentColor) {
            Row(
                Modifier
                    .onGloballyPositioned { coords ->
                        totalWidthPx = coords.size.width.toFloat()
                        val contentWidthPx = totalWidthPx - with(density) { 8.dp.toPx() }
                        tabWidthPx = (contentWidthPx / tabsCount)
                            .fastRoundToInt()
                            .coerceAtLeast(0)
                            .toFloat()
                    }
                    .graphicsLayer { translationX = panelOffset }
                    .dropShadow(
                        shape = pillShape,
                        shadow = Shadow(
                            radius = 10.dp,
                            color = Color.Black,
                            alpha = if (isInDark) 0.2f else 0.1f,
                        ),
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {},
                    )
                    .then(
                        if (isBlurMode) {
                            Modifier.drawBackdrop(
                                backdrop = backdrop,
                                shape = { pillShape },
                                effects = { blur(25.dp.toPx(), 25.dp.toPx()) },
                                onDrawSurface = { drawRect(containerColor.copy(alpha = 0.65f)) },
                            )
                        } else {
                            Modifier.background(containerColor, pillShape)
                        }
                    )
                    .height(64.dp)
                    .padding(4.dp),
                verticalAlignment = Alignment.CenterVertically,
                content = content,
            )
        }

        if (tabWidthPx > 0f) {
            val tabWidthDp = with(density) { tabWidthPx.toDp() }
            Box(
                modifier = Modifier
                    .padding(horizontal = 4.dp)
                    .graphicsLayer {
                        val progressOffset = dampedDragAnimation.value * tabWidthPx
                        translationX = if (isLtr) progressOffset + panelOffset else -progressOffset + panelOffset
                    }
                    .then(dampedDragAnimation.modifier)
                    .clip(pillShape)
                    .background(colors.indicatorColor.copy(alpha = 0.15f), pillShape)
                    .height(56.dp)
                    .width(tabWidthDp),
                contentAlignment = Alignment.CenterStart,
            ) {
                CompositionLocalProvider(LocalFloatingBottomBarContentColor provides colors.activeContentColor) {
                    Row(
                        Modifier
                            .clearAndSetSemantics {}
                            .wrapContentWidth(align = Alignment.Start, unbounded = true)
                            .requiredWidth(with(density) { (totalWidthPx - 8.dp.toPx()).toDp() })
                            .height(56.dp)
                            .graphicsLayer {
                                val progressOffset = dampedDragAnimation.value * tabWidthPx
                                translationX = if (isLtr) -progressOffset else progressOffset
                            },
                        verticalAlignment = Alignment.CenterVertically,
                        content = content,
                    )
                }
            }
        }
    }
}
