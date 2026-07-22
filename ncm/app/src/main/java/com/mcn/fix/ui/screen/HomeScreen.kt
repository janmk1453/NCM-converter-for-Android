package com.mcn.fix.ui.screen

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.mcn.fix.R
import com.mcn.fix.data.DecryptManager
import com.mcn.fix.data.model.NcmFileInfo
import com.mcn.fix.ui.component.CardSegment
import com.mcn.fix.ui.navigation.HomeState
import com.mcn.fix.util.FileUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.DropdownColors
import top.yukonga.miuix.kmp.basic.DropdownDefaults
import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.InputField
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.basic.SearchBar
import top.yukonga.miuix.kmp.basic.SearchBarDefaults
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.popup.OverlayDropdownPopup
import top.yukonga.miuix.kmp.icon.extended.SelectAll
import top.yukonga.miuix.kmp.icon.extended.Sort
import top.yukonga.miuix.kmp.preference.CheckboxPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType

enum class SortOrder(val labelRes: Int) {
    NAME_ASC(R.string.sort_name_asc),
    NAME_DESC(R.string.sort_name_desc),
    DATE_ASC(R.string.sort_date_asc),
    DATE_DESC(R.string.sort_date_desc),
}

@Composable
fun HomeScreen(
    contentPadding: PaddingValues,
    homeState: HomeState,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val decryptManager = remember { DecryptManager(context) }
    val decryptProgress by decryptManager.progress.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var searchExpanded by remember { mutableStateOf(false) }
    var sortOrder by remember { mutableStateOf(SortOrder.NAME_ASC) }
    var showSortDialog by remember { mutableStateOf(false) }
    var showSelectModeDialog by remember { mutableStateOf(false) }
    var showProgressDialog by remember { mutableStateOf(false) }
    var isLoadingFiles by remember { mutableStateOf(false) }

    var completedTotal by remember { mutableStateOf(0) }
    LaunchedEffect(decryptProgress.total) {
        if (decryptProgress.total > 0 && completedTotal != decryptProgress.total) {
            showProgressDialog = true
            completedTotal = decryptProgress.total
        }
    }

    val pathToIndex by remember(homeState.fileList) {
        derivedStateOf {
            homeState.fileList.withIndex().associate { (i, f) -> f.path to i }
        }
    }

    val displayList by remember(homeState.fileList, searchQuery, sortOrder) {
        derivedStateOf {
            var list = homeState.fileList.toList()
            if (searchQuery.isNotBlank()) {
                list = list.filter { it.name.contains(searchQuery, ignoreCase = true) }
            }
            when (sortOrder) {
                SortOrder.NAME_ASC -> list.sortedBy { it.name }
                SortOrder.NAME_DESC -> list.sortedByDescending { it.name }
                SortOrder.DATE_ASC -> list.sortedBy { it.lastModified }
                SortOrder.DATE_DESC -> list.sortedByDescending { it.lastModified }
            }
        }
    }

    val sourceDirLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        uri?.let {
            isLoadingFiles = true
            homeState.sourceDirUri = it
            homeState.decryptFinished = false
            scope.launch {
                try {
                    val files = withContext(Dispatchers.IO) { FileUtils.listNcmFiles(context, it) }
                    homeState.fileList.clear()
                    homeState.fileList.addAll(files)
                    homeState.allChecked = true
                    if (homeState.outputDirUri != null) {
                        val outputNames = FileUtils.listOutputFileNames(context, homeState.outputDirUri!!)
                        uncheckConvertedFiles(homeState, outputNames)
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, e.message ?: "Error", Toast.LENGTH_SHORT).show()
                } finally {
                    isLoadingFiles = false
                }
            }
        }
    }

    val outputDirLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        homeState.outputDirUri = uri
        homeState.decryptFinished = false
        if (uri != null && homeState.fileList.isNotEmpty()) {
            val outputNames = FileUtils.listOutputFileNames(context, uri)
            uncheckConvertedFiles(homeState, outputNames)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            SmallTopAppBar(title = stringResource(R.string.home))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MiuixTheme.colorScheme.surface)
                    .padding(start = 12.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SearchBar(
                    modifier = Modifier.weight(1f),
                    inputField = {
                        InputField(
                            query = searchQuery,
                            onQueryChange = { searchQuery = it },
                            onSearch = {},
                            expanded = searchExpanded,
                            onExpandedChange = { searchExpanded = it },
                            label = stringResource(R.string.search_hint),
                        )
                    },
                    expanded = searchExpanded,
                    onExpandedChange = { searchExpanded = it },
                    insideMargin = DpSize(0.dp, 0.dp),
                ) {}
                IconButton(
                    modifier = Modifier.padding(start = 4.dp),
                    onClick = { showSelectModeDialog = true },
                ) {
                    Icon(
                        imageVector = MiuixIcons.SelectAll,
                        contentDescription = stringResource(R.string.select_all_action),
                    )
                }
                IconButton(
                    modifier = Modifier.padding(start = 4.dp),
                    onClick = { showSortDialog = true },
                ) {
                    Icon(
                        imageVector = MiuixIcons.Sort,
                        contentDescription = stringResource(R.string.sort),
                    )
                }
                OverlayDropdownPopup(
                    entries = listOf(
                        DropdownEntry(
                            items = listOf(
                                DropdownItem(
                                    text = stringResource(R.string.select_all_action),
                                    onClick = {
                                        homeState.allChecked = true
                                        for (i in homeState.fileList.indices) {
                                            homeState.fileList[i] = homeState.fileList[i].copy(checked = true)
                                        }
                                    },
                                ),
                                DropdownItem(
                                    text = stringResource(R.string.select_unconverted),
                                    onClick = {
                                        if (homeState.outputDirUri != null) {
                                            val outputNames = FileUtils.listOutputFileNames(
                                                context, homeState.outputDirUri!!
                                            )
                                            uncheckConvertedFiles(homeState, outputNames)
                                        }
                                    },
                                ),
                            ),
                        ),
                    ),
                    show = showSelectModeDialog,
                    onDismiss = { showSelectModeDialog = false },
                    onDismissFinished = {},
                    maxHeight = null,
                    dropdownColors = DropdownDefaults.dropdownColors(),
                    renderInRootScaffold = true,
                    collapseOnSelection = true,
                )
                OverlayDropdownPopup(
                    entries = listOf(
                        DropdownEntry(
                            items = SortOrder.entries.map { order ->
                                DropdownItem(
                                    text = stringResource(order.labelRes),
                                    selected = order == sortOrder,
                                    onClick = { sortOrder = order },
                                )
                            },
                        ),
                    ),
                    show = showSortDialog,
                    onDismiss = { showSortDialog = false },
                    onDismissFinished = {},
                    maxHeight = null,
                    dropdownColors = DropdownDefaults.dropdownColors(),
                    renderInRootScaffold = true,
                    collapseOnSelection = true,
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MiuixTheme.colorScheme.surface)
                    .padding(start = 12.dp, end = 12.dp, top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Card(
                    modifier = Modifier.weight(1f),
                    pressFeedbackType = PressFeedbackType.Sink,
                    onClick = { sourceDirLauncher.launch(null) },
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = stringResource(R.string.select_source_dir),
                            style = MiuixTheme.textStyles.body1,
                            color = MiuixTheme.colorScheme.onSurface,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = homeState.sourceDirUri?.lastPathSegment
                                ?: stringResource(R.string.choose_directory),
                            style = MiuixTheme.textStyles.body2,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                Card(
                    modifier = Modifier.weight(1f),
                    pressFeedbackType = PressFeedbackType.Sink,
                    onClick = { outputDirLauncher.launch(null) },
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = stringResource(R.string.select_output_dir),
                            style = MiuixTheme.textStyles.body1,
                            color = MiuixTheme.colorScheme.onSurface,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = homeState.outputDirUri?.lastPathSegment
                                ?: stringResource(R.string.choose_directory),
                            style = MiuixTheme.textStyles.body2,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight()
                    .padding(bottom = contentPadding.calculateBottomPadding())
                    .padding(horizontal = 12.dp),
            ) {
                item {
                    Spacer(Modifier.height(12.dp))
                }

                if (isLoadingFiles) {
                    item {
                        Spacer(Modifier.height(24.dp))
                        Text(
                            text = stringResource(R.string.scanning_files),
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            modifier = Modifier.padding(horizontal = 12.dp),
                        )
                    }
                }

                if (homeState.fileList.isNotEmpty()) {
                    item {
                        SmallTitle(text = stringResource(R.string.file_list))
                    }

                    if (displayList.isEmpty() && searchQuery.isNotBlank()) {
                        item {
                            Spacer(Modifier.height(24.dp))
                            Text(
                                text = stringResource(R.string.no_ncm_found),
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                modifier = Modifier.padding(horizontal = 12.dp),
                            )
                        }
                    }

                    itemsIndexed(
                        items = displayList,
                        key = { _, file -> file.path },
                    ) { index, file ->
                        val globalIndex = pathToIndex[file.path] ?: -1
                        CardSegment(
                            isFirst = index == 0,
                            isLast = index == displayList.lastIndex,
                            insidePadding = 0.dp,
                        ) {
                            CheckboxPreference(
                                title = file.name,
                                summary = formatFileSize(file.size),
                                checked = file.checked,
                                onCheckedChange = { checked ->
                                    if (globalIndex >= 0) {
                                        homeState.fileList[globalIndex] = homeState.fileList[globalIndex].copy(checked = checked)
                                        homeState.allChecked = homeState.fileList.all { it.checked }
                                    }
                                },
                            )
                        }
                    }
                } else if (homeState.sourceDirUri != null && !isLoadingFiles) {
                    item {
                        Spacer(Modifier.height(24.dp))
                        Text(
                            text = stringResource(R.string.no_ncm_found),
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            modifier = Modifier.padding(horizontal = 12.dp),
                        )
                    }
                }

                item {
                    Spacer(Modifier.height(24.dp).navigationBarsPadding())
                }
            }
        }

        if (homeState.fileList.isNotEmpty()) {
            val enabled = homeState.outputDirUri != null
                && !decryptProgress.isRunning
                && !homeState.decryptFinished
                && homeState.fileList.any { it.checked }
            Button(
                onClick = {
                    if (homeState.outputDirUri != null) {
                        scope.launch {
                            homeState.decryptFinished = false
                            decryptManager.decryptAll(
                                files = homeState.fileList.toList(),
                                outputDirUri = homeState.outputDirUri!!,
                            )
                            homeState.decryptFinished = true
                        }
                    }
                },
                enabled = enabled,
                colors = ButtonDefaults.buttonColorsPrimary(),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = 16.dp + contentPadding.calculateBottomPadding()),
            ) {
                if (decryptProgress.isRunning) {
                    Text(text = stringResource(R.string.decrypting))
                } else {
                    val count = homeState.fileList.count { it.checked }
                    Text(text = "${stringResource(R.string.start_decrypt)} ($count)")
                }
            }
        }
    }

    if (showProgressDialog && decryptProgress.total > 0) {
        Dialog(
            onDismissRequest = { },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f))
                    .clickable(enabled = false) { },
                contentAlignment = Alignment.Center,
            ) {
                Card(
                    modifier = Modifier
                        .padding(horizontal = 24.dp)
                        .fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(24.dp)) {
                        Text(
                            text = if (decryptProgress.isRunning) {
                                stringResource(R.string.decrypting)
                            } else {
                                stringResource(R.string.decrypt_complete)
                            },
                            style = MiuixTheme.textStyles.title3,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "${decryptProgress.completed}/${decryptProgress.total}",
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                        Spacer(Modifier.height(16.dp))
                        if (decryptProgress.total > 0) {
                            LinearProgressIndicator(
                                progress = decryptProgress.completed.toFloat() / decryptProgress.total.toFloat(),
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (decryptProgress.total > 0) {
                                val pct = (decryptProgress.completed * 100 / decryptProgress.total.coerceAtLeast(1))
                                Text(
                                    text = "$pct%",
                                    color = MiuixTheme.colorScheme.primary,
                                )
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            Text(
                                text = "${stringResource(R.string.success_count, decryptProgress.success)}",
                                color = MiuixTheme.colorScheme.primary,
                            )
                            Text(
                                text = "${stringResource(R.string.failed_count, decryptProgress.failed)}",
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            )
                        }
                        Spacer(Modifier.height(20.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                        ) {
                            Button(
                                onClick = { showProgressDialog = false },
                                colors = ButtonDefaults.buttonColorsPrimary(),
                            ) {
                                Text(stringResource(R.string.confirm))
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun uncheckConvertedFiles(homeState: HomeState, outputFileNames: Set<String>) {
    val extensions = setOf(".flac", ".mp3", ".m4a", ".ogg", ".wav")
    for (i in homeState.fileList.indices) {
        val baseName = homeState.fileList[i].name.removeSuffix(".ncm")
        val hasOutput = extensions.any { ext -> (baseName + ext) in outputFileNames }
        if (hasOutput) {
            homeState.fileList[i] = homeState.fileList[i].copy(checked = false)
        }
    }
    homeState.allChecked = homeState.fileList.all { it.checked }
}

private fun formatFileSize(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        else -> "${"%.1f".format(bytes.toDouble() / (1024 * 1024))} MB"
    }
}
