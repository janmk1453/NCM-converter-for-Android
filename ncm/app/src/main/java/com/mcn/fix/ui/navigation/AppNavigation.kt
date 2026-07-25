package com.mcn.fix.ui.navigation

import androidx.activity.compose.BackHandler
import android.content.Context
import android.net.Uri
import android.view.View
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import com.mcn.fix.data.tag.AudioFileEntry
import com.mcn.fix.data.tag.AutoFillLogEntry
import com.mcn.fix.ui.component.CardSegment
import com.mcn.fix.ui.component.FloatingBottomBar
import com.mcn.fix.ui.component.FloatingBottomBarItem
import com.mcn.fix.ui.component.FloatingBottomBarMode
import com.mcn.fix.ui.screen.AboutScreen
import com.mcn.fix.ui.screen.HomeScreen
import com.mcn.fix.ui.screen.SettingsScreen
import com.mcn.fix.ui.tag.TagScreen
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.NavigationBar
import top.yukonga.miuix.kmp.basic.NavigationBarItem
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import androidx.compose.ui.text.style.TextOverflow

import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Home
import top.yukonga.miuix.kmp.icon.extended.Music
import top.yukonga.miuix.kmp.icon.extended.Settings


import androidx.compose.ui.unit.dp
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
    var rememberLastDir by rememberSaveable { mutableStateOf(prefs.getBoolean("remember_last_dir", false)) }
    var concurrency by rememberSaveable { mutableIntStateOf(prefs.getInt("concurrency", 4)) }
    var deleteAfterDecrypt by rememberSaveable { mutableStateOf(prefs.getBoolean("delete_after_decrypt", false)) }
    var predictiveBackEnabled by rememberSaveable { mutableStateOf(prefs.getBoolean("predictive_back", false)) }
    var topBarBlurEnabled by rememberSaveable { mutableStateOf(prefs.getBoolean("top_bar_blur", false)) }
    var floatingBottomBarEnabled by rememberSaveable { mutableStateOf(prefs.getBoolean("floating_bottom_bar", false)) }
    var autoFillPureMusic by rememberSaveable { mutableStateOf(prefs.getBoolean("auto_fill_pure_music", true)) }
    var autoFillConcurrency by rememberSaveable { mutableIntStateOf(prefs.getInt("auto_fill_concurrency", 4)) }
    var mixLyricsFromResults by rememberSaveable { mutableStateOf(prefs.getBoolean("mix_lyrics_from_results", false)) }
    val tagAudioFiles = remember { mutableStateListOf<AudioFileEntry>() }
    var tagScanVersion by remember { mutableIntStateOf(0) }
    val autoFillLogs = remember { mutableStateListOf<AutoFillLogEntry>() }
    var showAutoFillLog by rememberSaveable { mutableStateOf(false) }

    val homeState = remember {
        HomeState(
            initialSourceUri = if (prefs.getBoolean("remember_last_dir", false))
                prefs.getString("source_dir_uri", null)?.let { Uri.parse(it) } else null,
            initialOutputUri = if (prefs.getBoolean("remember_last_dir", false))
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

    LaunchedEffect(concurrency, deleteAfterDecrypt, predictiveBackEnabled, topBarBlurEnabled, floatingBottomBarEnabled, autoFillPureMusic, autoFillConcurrency, mixLyricsFromResults) {
        prefs.edit()
            .putInt("concurrency", concurrency)
            .putBoolean("delete_after_decrypt", deleteAfterDecrypt)
            .putBoolean("predictive_back", predictiveBackEnabled)
            .putBoolean("top_bar_blur", topBarBlurEnabled)
            .putBoolean("floating_bottom_bar", floatingBottomBarEnabled)
            .putBoolean("auto_fill_pure_music", autoFillPureMusic)
            .putInt("auto_fill_concurrency", autoFillConcurrency)
            .putBoolean("mix_lyrics_from_results", mixLyricsFromResults)
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
                                icon = MiuixIcons.Music,
                                label = stringResource(R.string.tag_editor),
                            )
                            NavigationBarItem(
                                selected = selectedTab == 2,
                                onClick = { selectedTab = 2 },
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
                        1 -> TagScreen(
                            contentPadding = innerPadding,
                            autoFillPureMusic = autoFillPureMusic,
                            autoFillConcurrency = autoFillConcurrency,
                            mixLyricsFromResults = mixLyricsFromResults,
                            tagAudioFiles = tagAudioFiles,
                            tagScanVersion = tagScanVersion,
                            onTagScanVersionChange = { tagScanVersion = it },
                            autoFillLogs = autoFillLogs,
                        )
                        2 -> SettingsScreen(
                            contentPadding = innerPadding,
                            themeMode = themeMode,
                            onThemeModeChange = {
                                themeMode = it
                                prefs.edit().putInt("theme_mode", it).apply()
                            },
                            onNavigateToAbout = { showAbout = true },
                            onNavigateToAutoFillLog = { showAutoFillLog = true },
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
                            autoFillPureMusic = autoFillPureMusic,
                            onAutoFillPureMusicChange = { autoFillPureMusic = it },
                            autoFillConcurrency = autoFillConcurrency,
                            onAutoFillConcurrencyChange = { autoFillConcurrency = it },
                            mixLyricsFromResults = mixLyricsFromResults,
                            onMixLyricsFromResultsChange = { mixLyricsFromResults = it },
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
                    tabsCount = 3,
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
                            imageVector = MiuixIcons.Music,
                            contentDescription = stringResource(R.string.tag_editor),
                        )
                    }
                    FloatingBottomBarItem(
                        onClick = { selectedTab = 2 },
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

        if (showAutoFillLog) {
            AutoFillLogScreen(
                logs = autoFillLogs,
                onBack = {
                    showAutoFillLog = false
                    tagScanVersion++
                },
            )
        }
    }
}

@Composable
private fun AutoFillLogScreen(
    logs: MutableList<AutoFillLogEntry>,
    onBack: () -> Unit,
) {
    BackHandler(enabled = true, onBack = onBack)
    val context = LocalContext.current
    Scaffold(
        topBar = {
            SmallTopAppBar(
                title = stringResource(R.string.tag_auto_fill_log),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = MiuixIcons.Back,
                            contentDescription = stringResource(R.string.back),
                        )
                    }
                },
                actions = {
                    if (logs.isNotEmpty()) {
                        IconButton(onClick = {
                            logs.clear()
                            Toast.makeText(context, context.getString(R.string.tag_auto_fill_log_cleared), Toast.LENGTH_SHORT).show()
                        }) {
                            Text(
                                text = stringResource(R.string.tag_auto_fill_log_clear),
                                color = MiuixTheme.colorScheme.primary,
                            )
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        if (logs.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.tag_auto_fill_log_empty),
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 12.dp),
            ) {
                itemsIndexed(logs.reversed(), key = { i, _ -> i }) { _, entry ->
                    val date = java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.getDefault())
                        .format(java.util.Date(entry.timestamp))
                    val fieldNames = mapOf(
                        "title" to "标题", "artist" to "艺术家", "album" to "专辑",
                        "cover" to "封面", "lyrics" to "歌词",
                    )
                    val afterText = entry.missingAfter.mapNotNull { fieldNames[it] }
                    CardSegment(isFirst = true, isLast = true, insidePadding = 12.dp) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = date,
                                style = MiuixTheme.textStyles.body2,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = entry.fileName,
                                style = MiuixTheme.textStyles.body1,
                                color = MiuixTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (afterText.isNotEmpty()) {
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = "缺失: ${afterText.joinToString("、")}",
                                    style = MiuixTheme.textStyles.body2,
                                    color = MiuixTheme.colorScheme.error,
                                )
                            }
                            if (entry.pureMusicLyrics) {
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = stringResource(R.string.tag_auto_fill_log_pure_music),
                                    style = MiuixTheme.textStyles.body2,
                                    color = MiuixTheme.colorScheme.primary.copy(alpha = 0.7f),
                                )
                            }
                            if (entry.detail.isNotBlank() && afterText.isEmpty() && !entry.pureMusicLyrics) {
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = entry.detail,
                                    style = MiuixTheme.textStyles.body2,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                }
                item { Spacer(Modifier.height(24.dp).navigationBarsPadding()) }
            }
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
