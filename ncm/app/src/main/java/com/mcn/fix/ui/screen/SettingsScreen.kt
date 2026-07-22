package com.mcn.fix.ui.screen

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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mcn.fix.R
import com.mcn.fix.ui.component.CardItem
import com.mcn.fix.ui.component.CardSegment
import com.mcn.fix.ui.component.groupedCardItems
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.OverlaySpinnerPreference
import top.yukonga.miuix.kmp.preference.SliderPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference

@Composable
fun SettingsScreen(
    contentPadding: PaddingValues,
    themeMode: Int,
    onThemeModeChange: (Int) -> Unit,
    onNavigateToAbout: () -> Unit,
) {
    var deleteAfterDecrypt by remember { mutableStateOf(false) }
    var concurrency by remember { mutableIntStateOf(4) }

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

    Column(modifier = Modifier.fillMaxSize()) {
        SmallTopAppBar(title = stringResource(R.string.settings))

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = contentPadding.calculateBottomPadding())
                .padding(horizontal = 12.dp),
        ) {
            item {
                Spacer(Modifier.height(12.dp))
            }

            item {
                SmallTitle(text = stringResource(R.string.conversion_settings))
            }

            groupedCardItems(
                keyPrefix = "conversion",
                items = listOf(
                    CardItem("delete") {
                        SwitchPreference(
                            title = stringResource(R.string.delete_after_decrypt),
                            checked = deleteAfterDecrypt,
                            onCheckedChange = { deleteAfterDecrypt = it },
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
                            onValueChange = { concurrency = it.toInt() },
                        )
                    },
                ),
                outerBottomPadding = 12.dp,
            )

            item {
                SmallTitle(text = stringResource(R.string.display))
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
                SmallTitle(text = stringResource(R.string.about))
            }

            item {
                CardSegment(
                    isFirst = true,
                    isLast = true,
                    insidePadding = 0.dp,
                ) {
                    ArrowPreference(
                        title = stringResource(R.string.version),
                        summary = stringResource(R.string.version_value),
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
