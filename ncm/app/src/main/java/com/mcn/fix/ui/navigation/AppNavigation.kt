package com.mcn.fix.ui.navigation

import android.net.Uri
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.navigationevent.NavigationEventDispatcher
import androidx.navigationevent.NavigationEventDispatcherOwner
import androidx.navigationevent.setViewTreeNavigationEventDispatcherOwner
import com.mcn.fix.R
import com.mcn.fix.data.model.NcmFileInfo
import com.mcn.fix.ui.screen.AboutScreen
import com.mcn.fix.ui.screen.HomeScreen
import com.mcn.fix.ui.screen.SettingsScreen
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

    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var showAbout by rememberSaveable { mutableStateOf(false) }
    var themeMode by rememberSaveable { mutableIntStateOf(0) }

    val homeState = remember { HomeState() }

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
        if (showAbout) {
            AboutScreen(onBack = { showAbout = false })
        } else {
            Scaffold(
                bottomBar = {
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
                },
            ) { innerPadding ->
                when (selectedTab) {
                    0 -> HomeScreen(
                        contentPadding = innerPadding,
                        homeState = homeState,
                    )
                    1 -> SettingsScreen(
                        contentPadding = innerPadding,
                        themeMode = themeMode,
                        onThemeModeChange = { themeMode = it },
                        onNavigateToAbout = { showAbout = true },
                    )
                }
            }
        }
    }
}

class HomeState {
    var sourceDirUri by mutableStateOf<Uri?>(null)
    var outputDirUri by mutableStateOf<Uri?>(null)
    val fileList = mutableStateListOf<NcmFileInfo>()
    var allChecked by mutableStateOf(true)
    var decryptFinished by mutableStateOf(false)
}
