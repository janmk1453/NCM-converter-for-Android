package com.mcn.fix.ui.screen

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import com.mcn.fix.R
import com.mcn.fix.ui.component.CardItem
import com.mcn.fix.ui.component.CardSegment
import com.mcn.fix.ui.component.groupedCardItems

import top.yukonga.miuix.kmp.basic.DropdownItem
import androidx.compose.ui.graphics.Color
import top.yukonga.miuix.kmp.blur.BlurDefaults
import top.yukonga.miuix.kmp.blur.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.blur.textureBlur
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.OverlaySpinnerPreference
import top.yukonga.miuix.kmp.preference.SliderPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun SettingsScreen(
    contentPadding: PaddingValues,
    themeMode: Int,
    onThemeModeChange: (Int) -> Unit,
    onNavigateToAbout: () -> Unit,
    onNavigateToAutoFillLog: () -> Unit = {},
    rememberLastDir: Boolean,
    onRememberLastDirChange: (Boolean) -> Unit,
    concurrency: Int = 4,
    onConcurrencyChange: (Int) -> Unit = {},
    deleteAfterDecrypt: Boolean = false,
    onDeleteAfterDecryptChange: (Boolean) -> Unit = {},
    predictiveBackEnabled: Boolean = false,
    onPredictiveBackEnabledChange: (Boolean) -> Unit = {},
    topBarBlurEnabled: Boolean = false,
    onTopBarBlurEnabledChange: (Boolean) -> Unit = {},

    floatingBottomBarEnabled: Boolean = false,
    onFloatingBottomBarEnabledChange: (Boolean) -> Unit = {},
    autoFillPureMusic: Boolean = true,
    onAutoFillPureMusicChange: (Boolean) -> Unit = {},
    autoFillConcurrency: Int = 4,
    onAutoFillConcurrencyChange: (Int) -> Unit = {},
    mixLyricsFromResults: Boolean = false,
    onMixLyricsFromResultsChange: (Boolean) -> Unit = {},
) {

    val themeFollowSystem = stringResource(R.string.theme_follow_system)
    val themeLight = stringResource(R.string.theme_light)
    val themeDark = stringResource(R.string.theme_dark)
    val themeItems = remember(themeFollowSystem, themeLight, themeDark) {
        listOf(
            DropdownItem(text = themeFollowSystem),
            DropdownItem(text = themeLight),
            DropdownItem(text = themeDark),
        )
    }

    val shaderSupported = isRuntimeShaderSupported() && android.os.Build.VERSION.SDK_INT >= 33
    val topBarBackdrop = if (topBarBlurEnabled && shaderSupported) {
        rememberLayerBackdrop { drawContent() }
    } else null

    Column(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier.then(
                if (topBarBackdrop != null) Modifier.textureBlur(
                    backdrop = topBarBackdrop,
                    shape = RoundedCornerShape(0.dp),
                    blurRadius = 25f,
                    colors = BlurDefaults.blurColors(),
                ) else Modifier
            ),
        ) {
            SmallTopAppBar(
                title = stringResource(R.string.settings),
                color = if (topBarBackdrop != null) Color.Transparent else MiuixTheme.colorScheme.surface,
            )
        }

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .overScrollVertical()
                    .padding(bottom = contentPadding.calculateBottomPadding())
                    .padding(horizontal = 12.dp),
            ) {
            item {
                Spacer(Modifier.height(12.dp))
            }

            item {
                SmallTitle(
                    text = stringResource(R.string.conversion_settings),
                    insideMargin = PaddingValues(start = 16.dp, top = 8.dp, bottom = 8.dp),
                )
            }

            groupedCardItems(
                keyPrefix = "conversion",
                items = listOf(
                    CardItem("delete") {
                        SwitchPreference(
                            title = stringResource(R.string.delete_after_decrypt),
                            checked = deleteAfterDecrypt,
                            onCheckedChange = onDeleteAfterDecryptChange,
                        )
                    },
                    CardItem("remember_dir") {
                        SwitchPreference(
                            title = stringResource(R.string.remember_last_dir),
                            summary = stringResource(R.string.remember_last_dir_summary),
                            checked = rememberLastDir,
                            onCheckedChange = onRememberLastDirChange,
                        )
                    },
                    CardItem("concurrency") {
                        SliderPreference(
                            title = stringResource(R.string.concurrency),
                            value = concurrency.toFloat(),
                            valueRange = 1f..8f,
                            steps = 7,
                            showKeyPoints = true,
                            keyPoints = listOf(1f, 2f, 3f, 4f, 5f, 6f, 7f, 8f),
                            valueText = concurrency.toString(),
                            onValueChange = { onConcurrencyChange(it.toInt()) },
                        )
                    },
                ),
                outerBottomPadding = 12.dp,
            )

            item {
                SmallTitle(
                    text = stringResource(R.string.display),
                    insideMargin = PaddingValues(start = 16.dp, top = 8.dp, bottom = 8.dp),
                )
            }

            item {
                CardSegment(
                    isFirst = true,
                    isLast = true,
                    insidePadding = 0.dp,
                ) {
                    OverlaySpinnerPreference(
                        items = themeItems,
                        selectedIndex = themeMode,
                        title = stringResource(R.string.theme_mode),
                        onSelectedIndexChange = onThemeModeChange,
                    )
                }
            }

            item {
                Spacer(Modifier.height(12.dp))
            }

            item {
                SmallTitle(
                    text = stringResource(R.string.navigation_and_animation),
                    insideMargin = PaddingValues(start = 16.dp, top = 8.dp, bottom = 8.dp),
                )
            }

            groupedCardItems(
                keyPrefix = "nav",
                items = listOf(
                    CardItem("predictive_back") {
                        SwitchPreference(
                            title = stringResource(R.string.predictive_back_title),
                            summary = stringResource(R.string.predictive_back_summary),
                            checked = predictiveBackEnabled,
                            onCheckedChange = onPredictiveBackEnabledChange,
                        )
                    },
                    CardItem("top_bar_blur") {
                        SwitchPreference(
                            title = stringResource(R.string.top_bar_blur_title),
                            summary = stringResource(R.string.top_bar_blur_summary),
                            checked = topBarBlurEnabled,
                            onCheckedChange = onTopBarBlurEnabledChange,
                        )
                    },
                    CardItem("floating_bottom_bar") {
                        SwitchPreference(
                            title = stringResource(R.string.floating_bottom_bar_title),
                            summary = stringResource(R.string.floating_bottom_bar_summary),
                            checked = floatingBottomBarEnabled,
                            onCheckedChange = onFloatingBottomBarEnabledChange,
                        )
                    },
                ),
                outerBottomPadding = 12.dp,
            )

            item {
                Spacer(Modifier.height(12.dp))
            }

            item {
                SmallTitle(
                    text = stringResource(R.string.tag_settings),
                    insideMargin = PaddingValues(start = 16.dp, top = 8.dp, bottom = 8.dp),
                )
            }

            groupedCardItems(
                keyPrefix = "tag",
                items = listOf(
                    CardItem("pure_lyrics") {
                        SwitchPreference(
                            title = stringResource(R.string.auto_fill_pure_music_lyrics),
                            summary = stringResource(R.string.auto_fill_pure_music_lyrics_summary),
                            checked = autoFillPureMusic,
                            onCheckedChange = onAutoFillPureMusicChange,
                        )
                    },
                    CardItem("mix_lyrics") {
                        SwitchPreference(
                            title = stringResource(R.string.mix_lyrics_from_results),
                            summary = stringResource(R.string.mix_lyrics_from_results_summary),
                            checked = mixLyricsFromResults,
                            onCheckedChange = onMixLyricsFromResultsChange,
                        )
                    },
                    CardItem("concurrency") {
                        SliderPreference(
                            title = stringResource(R.string.auto_fill_concurrency),
                            value = autoFillConcurrency.toFloat(),
                            valueRange = 1f..8f,
                            steps = 7,
                            showKeyPoints = true,
                            keyPoints = listOf(1f, 2f, 3f, 4f, 5f, 6f, 7f, 8f),
                            valueText = autoFillConcurrency.toString(),
                            onValueChange = { onAutoFillConcurrencyChange(it.toInt()) },
                        )
                    },
                    CardItem("log") {
                        ArrowPreference(
                            title = stringResource(R.string.tag_auto_fill_log),
                            onClick = onNavigateToAutoFillLog,
                        )
                    },
                ),
                outerBottomPadding = 12.dp,
            )

            item {
                SmallTitle(
                    text = stringResource(R.string.about),
                    insideMargin = PaddingValues(start = 16.dp, top = 8.dp, bottom = 8.dp),
                )
            }

            item {
                CardSegment(
                    isFirst = true,
                    isLast = true,
                    insidePadding = 0.dp,
                ) {
                    val ctx = LocalContext.current
                    val versionName = remember(ctx) {
                        runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0) }.getOrNull()?.versionName ?: "1.0.0"
                    }
                    ArrowPreference(
                        title = stringResource(R.string.version),
                        summary = versionName,
                        onClick = onNavigateToAbout,
                    )
                }
            }

            item {
                Spacer(Modifier.height(24.dp).navigationBarsPadding())
            }
        }
    }
}
