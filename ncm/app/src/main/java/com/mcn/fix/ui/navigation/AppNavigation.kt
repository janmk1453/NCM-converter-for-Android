package com.mcn.fix.ui.navigation

import android.content.Context
import android.net.Uri
import android.view.View
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Alignment
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import top.yukonga.miuix.kmp.anim.DecelerateEasing
import androidx.navigationevent.NavigationEventDispatcher
import androidx.navigationevent.NavigationEventDispatcherOwner
import androidx.navigationevent.setViewTreeNavigationEventDispatcherOwner
import com.mcn.fix.R
import com.mcn.fix.data.model.NcmFileInfo
import com.mcn.fix.ui.component.FloatingBottomBar
import com.mcn.fix.ui.component.FloatingBottomBarItem
import com.mcn.fix.ui.component.FloatingBottomBarMode
import com.mcn.fix.ui.screen.AboutScreen
import com.mcn.fix.ui.screen.HomeScreen
import com.mcn.fix.ui.screen.SettingsScreen
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.NavigationBar
import top.yukonga.miuix.kmp.basic.NavigationBarItem
import top.yukonga.miuix.kmp.basic.Scaffold

import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Home
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController


@Composable
fun MainPage() {
    val context = LocalContext.current
    val view = LocalView.current

    DisposableEffect(view) {
        val dispatcher = NavigationEventDispatcher()
        val owner = object : NavigationEventDispatcherOwner {
            override val navigationEventDispatcher: NavigationEventDispatcher = dispatcher
        }
        view.setViewTreeNavigationEventDispatcherOwner(owner)
        onDispose {
            view.setViewTreeNavigationEventDispatcherOwner(null)
            dispatcher.dispose()
        }
    }

    val prefs = remember { context.getSharedPreferences("settings", Context.MODE_PRIVATE) }

    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var showAbout by rememberSaveable { mutableStateOf(false) }
    var themeMode by rememberSaveable { mutableIntStateOf(prefs.getInt("theme_mode", 0)) }
    var rememberLastDir by rememberSaveable { mutableStateOf(prefs.getBoolean("remember_last_dir", true)) }
    var concurrency by rememberSaveable { mutableIntStateOf(prefs.getInt("concurrency", 4)) }
    var deleteAfterDecrypt by rememberSaveable { mutableStateOf(prefs.getBoolean("delete_after_decrypt", false)) }
    var predictiveBackEnabled by rememberSaveable { mutableStateOf(prefs.getBoolean("predictive_back", false)) }
    var topBarBlurEnabled by rememberSaveable { mutableStateOf(prefs.getBoolean("top_bar_blur", false)) }
    var floatingBottomBarEnabled by rememberSaveable { mutableStateOf(prefs.getBoolean("floating_bottom_bar", false)) }

    val homeState = remember {
        HomeState(
            initialSourceUri = if (prefs.getBoolean("remember_last_dir", true))
                prefs.getString("source_dir_uri", null)?.let { Uri.parse(it) } else null,
            initialOutputUri = if (prefs.getBoolean("remember_last_dir", true))
                prefs.getString("output_dir_uri", null)?.let { Uri.parse(it) } else null,
        )
    }

    SideEffect {
        if (rememberLastDir) {
            prefs.edit()
                .putString("source_dir_uri", homeState.sourceDirUri?.toString())
                .putString("output_dir_uri", homeState.outputDirUri?.toString())
                .apply()
        }
    }

    LaunchedEffect(concurrency, deleteAfterDecrypt, predictiveBackEnabled, topBarBlurEnabled, floatingBottomBarEnabled) {
        prefs.edit()
            .putInt("concurrency", concurrency)
            .putBoolean("delete_after_decrypt", deleteAfterDecrypt)
            .putBoolean("predictive_back", predictiveBackEnabled)
            .putBoolean("top_bar_blur", topBarBlurEnabled)
            .putBoolean("floating_bottom_bar", floatingBottomBarEnabled)
            .apply()
    }

    LaunchedEffect(homeState.sourceDirUri, homeState.outputDirUri) {
        if (rememberLastDir) {
            prefs.edit()
                .putString("source_dir_uri", homeState.sourceDirUri?.toString())
                .putString("output_dir_uri", homeState.outputDirUri?.toString())
                .apply()
        }
    }

    val controller = remember(themeMode) {
        ThemeController(
            colorSchemeMode = when (themeMode) {
                1 -> ColorSchemeMode.Light
                2 -> ColorSchemeMode.Dark
                else -> ColorSchemeMode.System
            },
        )
    }

    MiuixTheme(controller = controller) {
        Box(modifier = Modifier.fillMaxSize()) {
            Scaffold(
                bottomBar = {
                    if (!floatingBottomBarEnabled) {
                        NavigationBar {
                            NavigationBarItem(
                                selected = selectedTab == 0,
                                onClick = { selectedTab = 0 },
                                icon = MiuixIcons.Home,
                                label = stringResource(R.string.home),
                            )
                            NavigationBarItem(
                                selected = selectedTab == 1,
                                onClick = { selectedTab = 1 },
                                icon = MiuixIcons.Settings,
                                label = stringResource(R.string.settings),
                            )
                        }
                    }
                },
            ) { innerPadding ->
                AnimatedContent(
                    targetState = selectedTab,
                    transitionSpec = {
                        val direction = targetState - initialState
                        slideInHorizontally(
                            animationSpec = tween(200, easing = DecelerateEasing(1.2f)),
                            initialOffsetX = { fullWidth -> fullWidth * direction },
                        ) togetherWith slideOutHorizontally(
                            animationSpec = tween(200, easing = DecelerateEasing(1.2f)),
                            targetOffsetX = { fullWidth -> -fullWidth * direction },
                        )
                    },
                    label = "tab_slide",
                ) { tab ->
                    when (tab) {
                        0 -> HomeScreen(
                            contentPadding = innerPadding,
                            homeState = homeState,
                            concurrency = concurrency,
                            deleteAfterDecrypt = deleteAfterDecrypt,
                            topBarBlurEnabled = topBarBlurEnabled,
                        )
                        1 -> SettingsScreen(
                            contentPadding = innerPadding,
                            themeMode = themeMode,
                            onThemeModeChange = {
                                themeMode = it
                                prefs.edit().putInt("theme_mode", it).apply()
                            },
                            onNavigateToAbout = { showAbout = true },
                            rememberLastDir = rememberLastDir,
                            onRememberLastDirChange = {
                                rememberLastDir = it
                                prefs.edit().putBoolean("remember_last_dir", it).apply()
                            },
                            concurrency = concurrency,
                            onConcurrencyChange = { concurrency = it },
                            deleteAfterDecrypt = deleteAfterDecrypt,
                            onDeleteAfterDecryptChange = { deleteAfterDecrypt = it },
                            predictiveBackEnabled = predictiveBackEnabled,
                            onPredictiveBackEnabledChange = { predictiveBackEnabled = it },
                            topBarBlurEnabled = topBarBlurEnabled,
                            onTopBarBlurEnabledChange = { topBarBlurEnabled = it },
                            floatingBottomBarEnabled = floatingBottomBarEnabled,
                            onFloatingBottomBarEnabledChange = { floatingBottomBarEnabled = it },
                        )
                    }
                }
            }

            if (floatingBottomBarEnabled) {
                FloatingBottomBar(
                    modifier = Modifier.align(Alignment.BottomCenter),
                    selectedIndex = { selectedTab },
                    onSelected = { selectedTab = it },
                    backdrop = null,
                    tabsCount = 2,
                    mode = FloatingBottomBarMode.None,
                ) {
                    FloatingBottomBarItem(
                        onClick = { selectedTab = 0 },
                    ) {
                        Icon(
                            imageVector = MiuixIcons.Home,
                            contentDescription = stringResource(R.string.home),
                        )
                    }
                    FloatingBottomBarItem(
                        onClick = { selectedTab = 1 },
                    ) {
                        Icon(
                            imageVector = MiuixIcons.Settings,
                            contentDescription = stringResource(R.string.settings),
                        )
                    }
                }
            }
        }

        if (showAbout) {
            AboutScreen(onBack = { showAbout = false }, predictiveBackEnabled = predictiveBackEnabled)
        }
    }
}

class HomeState(
    initialSourceUri: Uri? = null,
    initialOutputUri: Uri? = null,
) {
    var sourceDirUri by mutableStateOf(initialSourceUri)
    var outputDirUri by mutableStateOf(initialOutputUri)
    val fileList = mutableStateListOf<NcmFileInfo>()
    var allChecked by mutableStateOf(true)
    var decryptFinished by mutableStateOf(false)
    var scanVersion by mutableIntStateOf(0)
}
