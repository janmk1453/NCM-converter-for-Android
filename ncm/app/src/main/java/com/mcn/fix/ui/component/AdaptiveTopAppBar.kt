package com.mcn.fix.ui.component

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.ScrollBehavior

@Composable
fun AdaptiveTopAppBar(
    title: String,
    modifier: Modifier = Modifier,
    color: androidx.compose.ui.graphics.Color = androidx.compose.ui.graphics.Color.Unspecified,
    scrollBehavior: ScrollBehavior? = null,
    navigationIcon: @Composable (() -> Unit)? = null,
    actions: @Composable () -> Unit = {},
    bottomContent: @Composable () -> Unit = {},
) {
    Column(modifier = modifier) {
        if (isWideScreen) {
            SmallTopAppBar(
                title = title,
                color = color,
                scrollBehavior = scrollBehavior,
                navigationIcon = { navigationIcon?.invoke() },
                actions = { actions() },
            )
        } else {
            TopAppBar(
                title = title,
                color = color,
                scrollBehavior = scrollBehavior,
                navigationIcon = { navigationIcon?.invoke() },
                actions = { actions() },
                bottomContent = bottomContent,
            )
        }
    }
}

@Composable
fun rememberIsWideScreen(): Boolean {
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    return configuration.screenWidthDp >= 600
}

private val isWideScreen: Boolean
    @Composable
    get() = rememberIsWideScreen()
