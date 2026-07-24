package com.mcn.fix.ui.tag

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import com.mcn.fix.R
import com.mcn.fix.data.tag.AudioFileEntry
import com.mcn.fix.data.tag.AudioTagInfo
import com.mcn.fix.data.tag.TagReaderWriter
import com.mcn.fix.data.tag.TagSearchApi
import com.mcn.fix.data.tag.TagSearchResult
import com.mcn.fix.ui.component.CardSegment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Checkbox
import top.yukonga.miuix.kmp.basic.DropdownDefaults
import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.InputField
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Image
import top.yukonga.miuix.kmp.icon.extended.SelectAll
import top.yukonga.miuix.kmp.icon.extended.Sort
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.popup.OverlayDropdownPopup
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical

private val SUPPORTED_EXTS = setOf(
    "flac", "ape", "wav", "aiff", "wv", "tta",
    "mp3", "mp4", "m4a", "ogg", "mpc", "opus",
    "wma", "dsf", "dff",
)

enum class TagSortOrder(val labelRes: Int) {
    NAME_ASC(R.string.sort_name_asc),
    NAME_DESC(R.string.sort_name_desc),
    DATE_ASC(R.string.sort_date_asc),
    DATE_DESC(R.string.sort_date_desc),
}

data class AutoFillProgress(
    val total: Int = 0,
    val completed: Int = 0,
    val success: Int = 0,
    val fail: Int = 0,
    val skip: Int = 0,
    val startTime: Long = 0L,
)

private data class TagPresenceInfo(
    val hasArtist: Boolean = false,
    val hasAlbum: Boolean = false,
    val hasLyrics: Boolean = false,
)

@Composable
fun TagScreen(
    contentPadding: PaddingValues,
    autoFillPureMusic: Boolean = true,
    autoFillConcurrency: Int = 4,
    mixLyricsFromResults: Boolean = true,
    tagAudioFiles: MutableList<AudioFileEntry> = mutableListOf(),
    tagScanVersion: Int = 0,
    onTagScanVersionChange: (Int) -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var editingIndex by remember { mutableIntStateOf(-1) }
    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<TagSearchResult>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }
    var isReading by remember { mutableStateOf(false) }
    var showConfirmSave by remember { mutableStateOf(false) }
    var showCoverPicker by remember { mutableStateOf(false) }
    var showLyricsEditor by remember { mutableStateOf(false) }
    var editLyricsText by remember { mutableStateOf("") }
    var sortOrder by remember { mutableStateOf(TagSortOrder.NAME_ASC) }
    var showSortDialog by remember { mutableStateOf(false) }
    var isAutoFilling by remember { mutableStateOf(false) }
    var autoFillProgress by remember { mutableStateOf(AutoFillProgress()) }
    var elapsedSeconds by remember { mutableIntStateOf(0) }
    var searchSource by remember { mutableStateOf(TagSearchApi.Source.ALL) }
    var isSelectMode by remember { mutableStateOf(false) }
    var selectedFiles by remember { mutableStateOf(setOf<String>()) }
    var showBatchDialog by remember { mutableStateOf(false) }
    var showSelectModeDialog by remember { mutableStateOf(false) }
    var fileFilterQuery by remember { mutableStateOf("") }
    val fileMetadataCache = remember { mutableStateMapOf<String, TagPresenceInfo>() }

    val coverCache = remember { LinkedHashMap<String, ByteArray?>(128, 0.75f, true) }

    LaunchedEffect(isAutoFilling) {
        if (isAutoFilling) {
            while (true) {
                delay(1000)
                elapsedSeconds = ((System.currentTimeMillis() - autoFillProgress.startTime) / 1000).toInt()
            }
        } else {
            elapsedSeconds = 0
        }
    }

    val audioFiles = tagAudioFiles
    var scanVersion by remember { mutableIntStateOf(tagScanVersion) }
    var tagInfo by remember { mutableStateOf(AudioTagInfo()) }

    LaunchedEffect(audioFiles.toList(), scanVersion) {
        fileMetadataCache.clear()
        for (entry in audioFiles) {
            launch(Dispatchers.IO) {
                try {
                    val info = TagReaderWriter.readTags(context, Uri.parse(entry.uri), entry.name)
                    fileMetadataCache[entry.uri] = TagPresenceInfo(
                        hasArtist = info.artist.isNotBlank(),
                        hasAlbum = info.album.isNotBlank(),
                        hasLyrics = info.lyrics.isNotBlank(),
                    )
                } catch (_: Exception) { }
            }
        }
    }

    suspend fun runAutoFill() {
        val files = audioFiles.toList()
        if (files.isEmpty()) return
        val startTime = System.currentTimeMillis()
        isAutoFilling = true
        autoFillProgress = AutoFillProgress(total = files.size, startTime = startTime)

        val semaphore = kotlinx.coroutines.sync.Semaphore(autoFillConcurrency)
        val successCount = java.util.concurrent.atomic.AtomicInteger(0)
        val failCount = java.util.concurrent.atomic.AtomicInteger(0)
        val skipCount = java.util.concurrent.atomic.AtomicInteger(0)
        val completedCount = java.util.concurrent.atomic.AtomicInteger(0)

        suspend fun processOne(entry: AudioFileEntry) {
            try {
                val info = withContext(Dispatchers.IO) {
                    TagReaderWriter.readTags(context, Uri.parse(entry.uri), entry.name)
                }
                val hasTitle = info.title.isNotBlank()
                val hasArtist = info.artist.isNotBlank()
                val hasAlbum = info.album.isNotBlank()
                val hasCover = info.coverData != null
                val hasLyrics = info.lyrics.isNotBlank()
                if (hasTitle && hasArtist && hasAlbum && hasCover && hasLyrics) {
                    skipCount.incrementAndGet()
                    return
                }

                val name = entry.name.substringBeforeLast('.')
                val (parsedArtist, parsedTitle) = TagSearchApi.parseFileName(entry.name)
                val query = if (parsedTitle.isNotBlank()) parsedTitle else name
                val searchArtist = if (parsedArtist.isNotBlank()) parsedArtist else info.artist
                val results = try { TagSearchApi.search(query, searchArtist) } catch (_: Exception) { emptyList() }
                if (results.isEmpty()) {
                    skipCount.incrementAndGet()
                    return
                }

                var newInfo = info
                if (!hasTitle) newInfo = newInfo.copy(title = results.first().title.ifBlank { newInfo.title })
                if (!hasArtist) newInfo = newInfo.copy(artist = results.first().artist.ifBlank { newInfo.artist })
                if (!hasAlbum) newInfo = newInfo.copy(album = results.first().album.ifBlank { newInfo.album })
                if (!hasCover) {
                    results.firstOrNull { it.coverData != null }?.let { r ->
                        newInfo = newInfo.copy(coverData = r.coverData, coverMime = r.coverMime)
                    }
                }
                if (!hasLyrics) {
                    val lyrics = if (mixLyricsFromResults) {
                        results.firstOrNull { it.lyrics.isNotBlank() }?.lyrics
                    } else {
                        results.firstOrNull()?.lyrics?.takeIf { it.isNotBlank() }
                    } ?: if (autoFillPureMusic) TagSearchApi.PURE_MUSIC_LYRICS else ""
                    if (lyrics.isNotBlank()) newInfo = newInfo.copy(lyrics = lyrics)
                }

                if (newInfo != info) {
                    val ok = withContext(Dispatchers.IO) {
                        TagReaderWriter.writeTags(context, Uri.parse(entry.uri), entry.name, newInfo)
                    }
                    if (ok) {
                        withContext(Dispatchers.IO) { TagReaderWriter.triggerMediaScan(context, Uri.parse(entry.uri)) }
                        successCount.incrementAndGet()
                    } else {
                        failCount.incrementAndGet()
                    }
                } else {
                    skipCount.incrementAndGet()
                }
            } catch (_: Exception) {
                failCount.incrementAndGet()
            }
        }

        coroutineScope<Unit> {
            val deferredList = files.map { entry ->
                async {
                    semaphore.acquire()
                    try { processOne(entry) } finally { semaphore.release() }
                    completedCount.incrementAndGet()
                    withContext(Dispatchers.Main) {
                        autoFillProgress = autoFillProgress.copy(
                            completed = completedCount.get(),
                            success = successCount.get(),
                            fail = failCount.get(),
                            skip = skipCount.get(),
                        )
                    }
                }
            }
            deferredList.joinAll()
        }

        isAutoFilling = false
        val updated = successCount.get()
        val msg = if (updated > 0) context.getString(R.string.tag_auto_fill_complete, updated)
                  else context.getString(R.string.tag_auto_fill_skip)
        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
    }

    suspend fun runBatchFill(fields: Set<String>) {
        val files = audioFiles.filter { it.uri in selectedFiles }
        isAutoFilling = true
        val startTime = System.currentTimeMillis()
        autoFillProgress = AutoFillProgress(total = files.size, startTime = startTime)
        val semaphore = kotlinx.coroutines.sync.Semaphore(4)
        val successCount = java.util.concurrent.atomic.AtomicInteger(0)
        val failCount = java.util.concurrent.atomic.AtomicInteger(0)
        val skipCount = java.util.concurrent.atomic.AtomicInteger(0)
        val completedCount = java.util.concurrent.atomic.AtomicInteger(0)

        suspend fun processOne(entry: AudioFileEntry) {
            try {
                val info = withContext(Dispatchers.IO) {
                    TagReaderWriter.readTags(context, Uri.parse(entry.uri), entry.name)
                }
                val name = entry.name.substringBeforeLast('.')
                val (parsedArtist, parsedTitle) = TagSearchApi.parseFileName(entry.name)
                val query = if (parsedTitle.isNotBlank()) parsedTitle else name
                val searchArtist = if (parsedArtist.isNotBlank()) parsedArtist else info.artist
                val results = try { TagSearchApi.search(query, searchArtist) } catch (_: Exception) { emptyList() }
                if (results.isEmpty()) { skipCount.incrementAndGet(); return }

                var newInfo = info
                if ("title" in fields && info.title.isBlank())
                    newInfo = newInfo.copy(title = results.first().title.ifBlank { newInfo.title })
                if ("artist" in fields && info.artist.isBlank())
                    newInfo = newInfo.copy(artist = results.first().artist.ifBlank { newInfo.artist })
                if ("album" in fields && info.album.isBlank())
                    newInfo = newInfo.copy(album = results.first().album.ifBlank { newInfo.album })
                if ("cover" in fields && info.coverData == null) {
                    results.firstOrNull { it.coverData != null }?.let { r ->
                        newInfo = newInfo.copy(coverData = r.coverData, coverMime = r.coverMime)
                    }
                }
                if ("lyrics" in fields && info.lyrics.isBlank()) {
                    val lyrics = if (mixLyricsFromResults) {
                        results.firstOrNull { it.lyrics.isNotBlank() }?.lyrics
                    } else {
                        results.firstOrNull()?.lyrics?.takeIf { it.isNotBlank() }
                    } ?: if (autoFillPureMusic) TagSearchApi.PURE_MUSIC_LYRICS else ""
                    if (lyrics.isNotBlank()) newInfo = newInfo.copy(lyrics = lyrics)
                }

                if (newInfo != info) {
                    val ok = withContext(Dispatchers.IO) {
                        TagReaderWriter.writeTags(context, Uri.parse(entry.uri), entry.name, newInfo)
                    }
                    if (ok) {
                        withContext(Dispatchers.IO) { TagReaderWriter.triggerMediaScan(context, Uri.parse(entry.uri)) }
                        successCount.incrementAndGet()
                    } else { failCount.incrementAndGet() }
                } else { skipCount.incrementAndGet() }
            } catch (_: Exception) { failCount.incrementAndGet() }
        }

        coroutineScope<Unit> {
            val deferredList = files.map { entry ->
                async {
                    semaphore.acquire()
                    try { processOne(entry) } finally { semaphore.release() }
                    completedCount.incrementAndGet()
                    withContext(Dispatchers.Main) {
                        autoFillProgress = autoFillProgress.copy(
                            completed = completedCount.get(), success = successCount.get(),
                            fail = failCount.get(), skip = skipCount.get(),
                        )
                    }
                }
            }
            deferredList.joinAll()
        }

        isAutoFilling = false
        selectedFiles = emptySet()
        isSelectMode = false
        val updated = successCount.get()
        val msg = if (updated > 0) context.getString(R.string.tag_auto_fill_complete, updated)
                  else context.getString(R.string.tag_auto_fill_skip)
        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
    }

    var batchFields by remember { mutableStateOf(setOf("title", "artist", "album", "cover", "lyrics")) }

    BackHandler(enabled = editingIndex >= 0) {
        if (editingIndex >= 0) showConfirmSave = true
    }

    val dirLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        uri?.let {
            try {
                context.contentResolver.takePersistableUriPermission(
                    it, Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            } catch (_: Exception) {}
            val v = scanVersion + 1
            scanVersion = v
            onTagScanVersionChange(v)
            scope.launch {
                val files = withContext(Dispatchers.IO) { scanAudioFiles(context, it) }
                audioFiles.clear()
                audioFiles.addAll(files)
            }
        }
    }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri ->
        uri?.let {
            scope.launch {
                try {
                    val data = withContext(Dispatchers.IO) {
                        context.contentResolver.openInputStream(it)?.use { stream ->
                            stream.readBytes()
                        }
                    }
                    if (data != null) {
                        tagInfo = tagInfo.copy(coverData = data, coverMime = "image/jpeg")
                    }
                } catch (_: Exception) {}
            }
        }
    }

    val displayList by remember(audioFiles.toList(), sortOrder, scanVersion, fileFilterQuery) {
        derivedStateOf {
            val list = audioFiles.toList().filter { file ->
                fileFilterQuery.isBlank() || file.name.contains(fileFilterQuery, ignoreCase = true)
            }
            when (sortOrder) {
                TagSortOrder.NAME_ASC -> list.sortedBy { it.name }
                TagSortOrder.NAME_DESC -> list.sortedByDescending { it.name }
                TagSortOrder.DATE_ASC -> list.sortedBy { it.lastModified }
                TagSortOrder.DATE_DESC -> list.sortedByDescending { it.lastModified }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (editingIndex < 0) {
            TagFileListView(
                contentPadding = contentPadding,
                audioFiles = audioFiles,
                displayList = displayList,
                sortOrder = sortOrder,
                showSortDialog = showSortDialog,
                showSelectModeDialog = showSelectModeDialog,
                coverCache = coverCache,
                fileMetadataCache = fileMetadataCache,
                fileFilterQuery = fileFilterQuery,
                isSelectMode = isSelectMode,
                selectedFiles = selectedFiles,
                onShowSortDialogChange = { showSortDialog = it },
                onShowSelectModeDialogChange = { showSelectModeDialog = it },
                onSortOrderChange = { sortOrder = it },
                onFileFilterQueryChange = { fileFilterQuery = it },
                onSelectDir = { dirLauncher.launch(null) },
                onSelectionChange = { selectedFiles = it },
                onSelectModeChange = { isSelectMode = it },
                onFileClick = { index ->
                    editingIndex = index
                    isReading = true
                    scope.launch {
                        val entry = audioFiles[index]
                        val info = withContext(Dispatchers.IO) {
                            TagReaderWriter.readTags(context, Uri.parse(entry.uri), entry.name)
                        }
                        tagInfo = info
                        val name = entry.name.substringBeforeLast('.')
                        val (parsedArtist, parsedTitle) = TagSearchApi.parseFileName(entry.name)
                        searchQuery = if (parsedTitle.isNotBlank()) parsedTitle else name
                        searchResults = emptyList()
                        isReading = false
                    }
                },
            )
            if (audioFiles.isNotEmpty() && !isSelectMode) {
                Button(
                    onClick = { scope.launch { runAutoFill() } },
                    enabled = !isAutoFilling,
                    colors = ButtonDefaults.buttonColorsPrimary(),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 16.dp, bottom = 16.dp + contentPadding.calculateBottomPadding()),
                ) {
                    Text(
                        if (isAutoFilling) context.getString(R.string.tag_auto_filling, autoFillProgress.completed, autoFillProgress.total)
                        else stringResource(R.string.tag_auto_fill),
                    )
                }
            }
            if (isSelectMode && selectedFiles.isNotEmpty()) {
                Button(
                    onClick = { showBatchDialog = true },
                    colors = ButtonDefaults.buttonColorsPrimary(),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 16.dp, bottom = 16.dp + contentPadding.calculateBottomPadding()),
                ) {
                    Text(stringResource(R.string.tag_batch_fill))
                }
            }
        } else {
            TagEditorView(
                contentPadding = contentPadding,
                tagInfo = tagInfo,
                isReading = isReading,
                isSearching = isSearching,
                isSaving = isSaving,
                searchQuery = searchQuery,
                searchResults = searchResults,
                searchSource = searchSource,
                onBack = { showConfirmSave = true },
                onTagInfoChange = { tagInfo = it },
                onSearchQueryChange = {
                    searchQuery = it
                    searchResults = emptyList()
                },
                onSearchSourceChange = { searchSource = it },
                onStartSearch = {
                    if (searchQuery.isNotBlank()) {
                        isSearching = true
                        scope.launch {
                            try {
                                val query = searchQuery
                                val artist = tagInfo.artist
                                val results = TagSearchApi.search(query, artist, source = searchSource)
                                searchResults = results
                            } catch (_: Exception) {}
                            isSearching = false
                        }
                    }
                },
                onApplySearchResult = { result ->
                    val lyrics = if (result.lyrics.isNotBlank()) result.lyrics
                    else if (autoFillPureMusic && tagInfo.lyrics.isBlank()) TagSearchApi.PURE_MUSIC_LYRICS
                    else tagInfo.lyrics
                    tagInfo = tagInfo.copy(
                        title = result.title.ifBlank { tagInfo.title },
                        artist = result.artist.ifBlank { tagInfo.artist },
                        album = result.album.ifBlank { tagInfo.album },
                        lyrics = lyrics,
                        coverData = result.coverData ?: tagInfo.coverData,
                        coverMime = result.coverMime,
                    )
                },
                onPickCover = { showCoverPicker = true },
                onEditLyrics = {
                    editLyricsText = tagInfo.lyrics
                    showLyricsEditor = true
                },
                onSave = {
                    isSaving = true
                    scope.launch {
                        val entry = audioFiles[editingIndex]
                        val success = withContext(Dispatchers.IO) {
                            TagReaderWriter.writeTags(
                                context, Uri.parse(entry.uri), entry.name, tagInfo,
                            )
                        }
                        if (success) {
                            withContext(Dispatchers.IO) {
                                TagReaderWriter.triggerMediaScan(context, Uri.parse(entry.uri))
                            }
                            Toast.makeText(context, context.getString(R.string.tag_save_success), Toast.LENGTH_SHORT).show()
                            editingIndex = -1
                        } else {
                            Toast.makeText(context, context.getString(R.string.tag_save_failed), Toast.LENGTH_SHORT).show()
                        }
                        isSaving = false
                    }
                },
            )
        }
    }

    if (showConfirmSave) {
        OverlayDialog(
            title = stringResource(R.string.tag_unsaved_title),
            summary = stringResource(R.string.tag_unsaved_summary),
            show = showConfirmSave,
            onDismissRequest = { showConfirmSave = false },
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TextButton(
                    text = stringResource(R.string.cancel),
                    onClick = { showConfirmSave = false },
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    text = stringResource(R.string.tag_discard),
                    onClick = {
                        showConfirmSave = false
                        editingIndex = -1
                    },
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    text = stringResource(R.string.confirm),
                    onClick = {
                        showConfirmSave = false
                        scope.launch {
                            val entry = audioFiles[editingIndex]
                            withContext(Dispatchers.IO) {
                                TagReaderWriter.writeTags(context, Uri.parse(entry.uri), entry.name, tagInfo)
                            }
                            editingIndex = -1
                        }
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                )
            }
        }
    }

    if (showCoverPicker) {
        showCoverPicker = false
        imagePickerLauncher.launch("image/*")
    }

    if (showLyricsEditor) {
        OverlayDialog(
            title = stringResource(R.string.tag_lyrics),
            summary = "",
            show = showLyricsEditor,
            onDismissRequest = { showLyricsEditor = false },
        ) {
            Column(modifier = Modifier.heightIn(max = 400.dp)) {
                InputField(
                    query = editLyricsText,
                    onQueryChange = { editLyricsText = it },
                    onSearch = { _ -> },
                    expanded = false,
                    onExpandedChange = {},
                    label = stringResource(R.string.tag_lyrics_hint),
                )
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = {
                        tagInfo = tagInfo.copy(lyrics = editLyricsText)
                        showLyricsEditor = false
                    },
                    colors = ButtonDefaults.buttonColorsPrimary(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.confirm))
                }
            }
        }
    }

    if (isAutoFilling) {
        OverlayDialog(
            title = stringResource(R.string.tag_auto_fill),
            summary = "",
            show = isAutoFilling,
            onDismissRequest = {},
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.height(8.dp))
                val p = autoFillProgress
                LinearProgressIndicator(
                    progress = if (p.total > 0) p.completed.toFloat() / p.total.toFloat() else 0f,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "${p.completed} / ${p.total}",
                    style = MiuixTheme.textStyles.body1,
                    color = MiuixTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(8.dp))
                val min = elapsedSeconds / 60
                val sec = elapsedSeconds % 60
                Text(
                    text = "${stringResource(R.string.tag_auto_fill_elapsed)} ${String.format("%02d:%02d", min, sec)}",
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
                Text(
                    text = "${stringResource(R.string.tag_auto_fill_success)} ${p.success}    ${stringResource(R.string.tag_auto_fill_fail)} ${p.fail}    ${stringResource(R.string.tag_auto_fill_skipped)} ${p.skip}",
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
                Spacer(Modifier.height(8.dp))
            }
        }
    }

    if (showBatchDialog) {
        OverlayDialog(
            title = stringResource(R.string.tag_batch_fill_title),
            summary = "",
            show = showBatchDialog,
            onDismissRequest = { showBatchDialog = false },
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(R.string.tag_batch_fill_fields),
                    style = MiuixTheme.textStyles.body1,
                    color = MiuixTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(8.dp))
                val fieldLabels = mapOf(
                    "title" to stringResource(R.string.tag_title),
                    "artist" to stringResource(R.string.tag_artist),
                    "album" to stringResource(R.string.tag_album),
                    "cover" to stringResource(R.string.tag_cover),
                    "lyrics" to stringResource(R.string.tag_lyrics),
                )
                fieldLabels.forEach { (key, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                batchFields = if (key in batchFields) batchFields - key
                                else batchFields + key
                            }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            state = if (key in batchFields) ToggleableState.On else ToggleableState.Off,
                            onClick = {
                                batchFields = if (key in batchFields) batchFields - key
                                else batchFields + key
                            },
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(text = label, style = MiuixTheme.textStyles.body1, color = MiuixTheme.colorScheme.onSurface)
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TextButton(
                        text = stringResource(R.string.cancel),
                        onClick = { showBatchDialog = false },
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        text = stringResource(R.string.tag_auto_fill),
                        onClick = {
                            showBatchDialog = false
                            scope.launch { runBatchFill(batchFields) }
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.textButtonColorsPrimary(),
                    )
                }
            }
        }
    }
}

@Composable
private fun TagFileListView(
    contentPadding: PaddingValues,
    audioFiles: List<AudioFileEntry>,
    displayList: List<AudioFileEntry>,
    sortOrder: TagSortOrder,
    showSortDialog: Boolean,
    showSelectModeDialog: Boolean,
    coverCache: MutableMap<String, ByteArray?>,
    fileMetadataCache: Map<String, TagPresenceInfo>,
    fileFilterQuery: String,
    isSelectMode: Boolean,
    selectedFiles: Set<String>,
    onShowSortDialogChange: (Boolean) -> Unit,
    onShowSelectModeDialogChange: (Boolean) -> Unit,
    onSortOrderChange: (TagSortOrder) -> Unit,
    onFileFilterQueryChange: (String) -> Unit,
    onSelectDir: () -> Unit,
    onSelectionChange: (Set<String>) -> Unit,
    onSelectModeChange: (Boolean) -> Unit,
    onFileClick: (Int) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        SmallTopAppBar(title = stringResource(R.string.tag_editor))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MiuixTheme.colorScheme.surface)
                .padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Card(
                modifier = Modifier.weight(1f),
                onClick = onSelectDir,
            ) {
                Text(
                    text = stringResource(R.string.tag_select_dir),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    style = MiuixTheme.textStyles.body1,
                    color = MiuixTheme.colorScheme.onSurface,
                )
            }
            if (audioFiles.isNotEmpty()) {
                IconButton(
                    modifier = Modifier.padding(start = 4.dp),
                    onClick = { onShowSelectModeDialogChange(true) },
                ) {
                    Icon(
                        imageVector = MiuixIcons.SelectAll,
                        contentDescription = stringResource(R.string.tag_select_files),
                    )
                }
                OverlayDropdownPopup(
                    entries = listOf(
                        DropdownEntry(
                            items = listOf(
                                DropdownItem(
                                    text = stringResource(R.string.tag_select_files),
                                    onClick = {
                                        onSelectModeChange(true)
                                        onShowSelectModeDialogChange(false)
                                    },
                                ),
                                DropdownItem(
                                    text = stringResource(R.string.select_all),
                                    onClick = {
                                        onSelectionChange(audioFiles.map { it.uri }.toSet())
                                        onSelectModeChange(true)
                                        onShowSelectModeDialogChange(false)
                                    },
                                ),
                                DropdownItem(
                                    text = stringResource(R.string.tag_cancel_select),
                                    onClick = {
                                        onSelectionChange(emptySet())
                                        onSelectModeChange(false)
                                        onShowSelectModeDialogChange(false)
                                    },
                                ),
                            ),
                        ),
                    ),
                    show = showSelectModeDialog,
                    onDismiss = { onShowSelectModeDialogChange(false) },
                    onDismissFinished = {},
                    maxHeight = null,
                    dropdownColors = DropdownDefaults.dropdownColors(),
                    renderInRootScaffold = true,
                    collapseOnSelection = true,
                )
            }
            IconButton(
                modifier = Modifier.padding(start = 4.dp),
                onClick = { onShowSortDialogChange(true) },
            ) {
                Icon(
                    imageVector = MiuixIcons.Sort,
                    contentDescription = stringResource(R.string.sort),
                )
            }
            OverlayDropdownPopup(
                entries = listOf(
                    DropdownEntry(
                        items = TagSortOrder.entries.map { order ->
                            DropdownItem(
                                text = stringResource(order.labelRes),
                                selected = order == sortOrder,
                                onClick = { onSortOrderChange(order) },
                            )
                        },
                    ),
                ),
                show = showSortDialog,
                onDismiss = { onShowSortDialogChange(false) },
                onDismissFinished = {},
                maxHeight = null,
                dropdownColors = DropdownDefaults.dropdownColors(),
                renderInRootScaffold = true,
                collapseOnSelection = true,
            )
        }

        if (audioFiles.isNotEmpty()) {
            InputField(
                query = fileFilterQuery,
                onQueryChange = onFileFilterQueryChange,
                onSearch = { _ -> },
                expanded = false,
                onExpandedChange = {},
                label = stringResource(R.string.tag_filter_hint),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }

        HorizontalDivider(color = MiuixTheme.colorScheme.dividerLine.copy(alpha = 0.3f))

        if (audioFiles.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.tag_no_files),
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    textAlign = TextAlign.Center,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .overScrollVertical()
                    .padding(bottom = contentPadding.calculateBottomPadding())
                    .padding(horizontal = 12.dp),
            ) {
                item {
                    SmallTitle(
                        text = stringResource(R.string.tag_file_list, audioFiles.size),
                        insideMargin = PaddingValues(start = 16.dp, top = 8.dp, bottom = 8.dp),
                    )
                }

                itemsIndexed(
                    items = displayList,
                    key = { _, file -> file.uri },
                ) { index, file ->
                    val isSelected = file.uri in selectedFiles
                    CardSegment(
                        isFirst = index == 0,
                        isLast = index == displayList.lastIndex,
                        insidePadding = 0.dp,
                    ) {
                        ArrowPreference(
                            title = file.name,
                            summary = buildString {
                                append(file.format.uppercase())
                                fileMetadataCache[file.uri]?.let { presence ->
                                    if (presence.hasArtist) append("  ·")
                                    if (presence.hasAlbum) append("  ⊞")
                                    if (presence.hasLyrics) append("  ♪")
                                }
                            },
                            startAction = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (isSelectMode) {
                                        Checkbox(
                                            state = if (isSelected) ToggleableState.On else ToggleableState.Off,
                                            onClick = {
                                                onSelectionChange(
                                                    if (isSelected) selectedFiles - file.uri
                                                    else selectedFiles + file.uri
                                                )
                                            },
                                            modifier = Modifier.padding(end = 8.dp),
                                        )
                                    }
                                    FileCoverThumbnail(file.uri, coverCache)
                                }
                            },
                            onClick = {
                                if (isSelectMode) {
                                    onSelectionChange(
                                        if (isSelected) selectedFiles - file.uri
                                        else selectedFiles + file.uri
                                    )
                                } else {
                                    onFileClick(audioFiles.indexOf(file))
                                }
                            },
                        )
                    }
                }

                item {
                    Spacer(Modifier.height(24.dp).navigationBarsPadding())
                }
            }
        }
    }
}

@Composable
private fun TagEditorView(
    contentPadding: PaddingValues,
    tagInfo: AudioTagInfo,
    isReading: Boolean,
    isSearching: Boolean,
    isSaving: Boolean,
    searchQuery: String,
    searchResults: List<TagSearchResult>,
    searchSource: TagSearchApi.Source,
    onBack: () -> Unit,
    onTagInfoChange: (AudioTagInfo) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onSearchSourceChange: (TagSearchApi.Source) -> Unit,
    onStartSearch: () -> Unit,
    onApplySearchResult: (TagSearchResult) -> Unit,
    onPickCover: () -> Unit,
    onEditLyrics: () -> Unit,
    onSave: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            SmallTopAppBar(
                title = tagInfo.fileName,
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = MiuixIcons.Back,
                            contentDescription = stringResource(R.string.back),
                        )
                    }
                },
            )

        if (isReading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.tag_reading),
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .overScrollVertical()
                    .padding(bottom = contentPadding.calculateBottomPadding())
                    .padding(horizontal = 12.dp),
            ) {
                item { Spacer(Modifier.height(12.dp)) }

                item {
                    CoverEditor(
                        coverData = tagInfo.coverData,
                        coverMime = tagInfo.coverMime,
                        onPickCover = onPickCover,
                        onClearCover = { onTagInfoChange(tagInfo.copy(coverData = null)) },
                    )
                }

                item {
                    Spacer(Modifier.height(12.dp))
                    SmallTitle(
                        text = stringResource(R.string.tag_basic_info),
                        insideMargin = PaddingValues(start = 16.dp, top = 8.dp, bottom = 8.dp),
                    )
                }

                item {
                    CardSegment(isFirst = true, isLast = false, insidePadding = 0.dp) {
                        InputField(
                            query = tagInfo.title,
                            onQueryChange = { onTagInfoChange(tagInfo.copy(title = it)) },
                            onSearch = { _ -> },
                            expanded = false,
                            onExpandedChange = {},
                            label = stringResource(R.string.tag_title),
                        )
                    }
                }

                item {
                    CardSegment(isFirst = false, isLast = false, insidePadding = 0.dp) {
                        InputField(
                            query = tagInfo.artist,
                            onQueryChange = { onTagInfoChange(tagInfo.copy(artist = it)) },
                            onSearch = { _ -> },
                            expanded = false,
                            onExpandedChange = {},
                            label = stringResource(R.string.tag_artist),
                        )
                    }
                }

                item {
                    CardSegment(isFirst = false, isLast = false, insidePadding = 0.dp) {
                        InputField(
                            query = tagInfo.album,
                            onQueryChange = { onTagInfoChange(tagInfo.copy(album = it)) },
                            onSearch = { _ -> },
                            expanded = false,
                            onExpandedChange = {},
                            label = stringResource(R.string.tag_album),
                        )
                    }
                }

                item {
                    CardSegment(isFirst = false, isLast = false, insidePadding = 0.dp) {
                        InputField(
                            query = tagInfo.genre,
                            onQueryChange = { onTagInfoChange(tagInfo.copy(genre = it)) },
                            onSearch = { _ -> },
                            expanded = false,
                            onExpandedChange = {},
                            label = stringResource(R.string.tag_genre),
                        )
                    }
                }

                item {
                    CardSegment(isFirst = false, isLast = false, insidePadding = 0.dp) {
                        InputField(
                            query = tagInfo.year,
                            onQueryChange = { onTagInfoChange(tagInfo.copy(year = it)) },
                            onSearch = { _ -> },
                            expanded = false,
                            onExpandedChange = {},
                            label = stringResource(R.string.tag_year),
                        )
                    }
                }

                item {
                    CardSegment(isFirst = false, isLast = true, insidePadding = 0.dp) {
                        InputField(
                            query = tagInfo.trackNumber,
                            onQueryChange = { onTagInfoChange(tagInfo.copy(trackNumber = it)) },
                            onSearch = { _ -> },
                            expanded = false,
                            onExpandedChange = {},
                            label = stringResource(R.string.tag_track_number),
                        )
                    }
                }

                item {
                    Spacer(Modifier.height(12.dp))
                    SmallTitle(
                        text = stringResource(R.string.tag_lyrics),
                        insideMargin = PaddingValues(start = 16.dp, top = 8.dp, bottom = 8.dp),
                    )
                }

                item {
                    CardSegment(isFirst = true, isLast = true, insidePadding = 0.dp) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onEditLyrics() }
                                .padding(horizontal = 12.dp, vertical = 16.dp),
                        ) {
                            if (tagInfo.lyrics.isNotBlank()) {
                                Text(
                                    text = tagInfo.lyrics.take(200).replace('\n', ' ') +
                                            if (tagInfo.lyrics.length > 200) "…" else "",
                                    style = MiuixTheme.textStyles.body2,
                                    color = MiuixTheme.colorScheme.onSurface,
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            } else {
                                Text(
                                    text = stringResource(R.string.tag_lyrics_hint),
                                    style = MiuixTheme.textStyles.body2,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                )
                            }
                        }
                    }
                }

                item {
                    Spacer(Modifier.height(12.dp))
                    SmallTitle(
                        text = stringResource(R.string.tag_search_online),
                        insideMargin = PaddingValues(start = 16.dp, top = 8.dp, bottom = 8.dp),
                    )
                }

                item {
                    CardSegment(isFirst = true, isLast = true, insidePadding = 0.dp) {
                        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Button(
                                    onClick = onStartSearch,
                                    enabled = !isSearching && searchQuery.isNotBlank(),
                                    colors = ButtonDefaults.buttonColorsPrimary(),
                                ) {
                                    Text(
                                        if (isSearching) stringResource(R.string.tag_searching)
                                        else stringResource(R.string.tag_search),
                                    )
                                }
                                Spacer(Modifier.width(8.dp))
                                InputField(
                                    modifier = Modifier.weight(1f),
                                    query = searchQuery,
                                    onQueryChange = onSearchQueryChange,
                                    onSearch = { _ -> onStartSearch() },
                                    expanded = false,
                                    onExpandedChange = {},
                                    label = stringResource(R.string.tag_search_hint),
                                )
                            }

                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(
                                            if (searchSource == TagSearchApi.Source.ALL) MiuixTheme.colorScheme.primary.copy(alpha = 0.12f)
                                            else MiuixTheme.colorScheme.surfaceContainer
                                        )
                                        .clickable { onSearchSourceChange(TagSearchApi.Source.ALL) }
                                        .padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = stringResource(R.string.search_source_all),
                                        style = MiuixTheme.textStyles.body2,
                                        color = if (searchSource == TagSearchApi.Source.ALL) MiuixTheme.colorScheme.primary
                                        else MiuixTheme.colorScheme.onSurface,
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(
                                            if (searchSource == TagSearchApi.Source.NETEASE) MiuixTheme.colorScheme.primary.copy(alpha = 0.12f)
                                            else MiuixTheme.colorScheme.surfaceContainer
                                        )
                                        .clickable { onSearchSourceChange(TagSearchApi.Source.NETEASE) }
                                        .padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = stringResource(R.string.search_source_netease),
                                        style = MiuixTheme.textStyles.body2,
                                        color = if (searchSource == TagSearchApi.Source.NETEASE) MiuixTheme.colorScheme.primary
                                        else MiuixTheme.colorScheme.onSurface,
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(
                                            if (searchSource == TagSearchApi.Source.QQ_MUSIC) MiuixTheme.colorScheme.primary.copy(alpha = 0.12f)
                                            else MiuixTheme.colorScheme.surfaceContainer
                                        )
                                        .clickable { onSearchSourceChange(TagSearchApi.Source.QQ_MUSIC) }
                                        .padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = stringResource(R.string.search_source_qq),
                                        style = MiuixTheme.textStyles.body2,
                                        color = if (searchSource == TagSearchApi.Source.QQ_MUSIC) MiuixTheme.colorScheme.primary
                                        else MiuixTheme.colorScheme.onSurface,
                                    )
                                }
                            }

                            if (isSearching) {
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    text = stringResource(R.string.tag_searching),
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                )
                            }

                            if (searchResults.isNotEmpty()) {
                                Spacer(Modifier.height(8.dp))
                                HorizontalDivider(color = MiuixTheme.colorScheme.dividerLine.copy(alpha = 0.3f))
                                Spacer(Modifier.height(8.dp))
                                searchResults.forEach { result ->
                                    TagSearchResultItem(
                                        result = result,
                                        onApply = { onApplySearchResult(result) },
                                    )
                                    if (result != searchResults.last()) {
                                        HorizontalDivider(
                                            color = MiuixTheme.colorScheme.dividerLine.copy(alpha = 0.15f),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                item {
                    Spacer(Modifier.height(32.dp).navigationBarsPadding())
                }
            }
        }
    }

        Button(
            onClick = onSave,
            enabled = !isSaving,
            colors = ButtonDefaults.buttonColorsPrimary(),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = 16.dp + contentPadding.calculateBottomPadding()),
        ) {
            Text(
                if (isSaving) stringResource(R.string.tag_saving)
                else stringResource(R.string.tag_save),
            )
        }
    }
}

@Composable
private fun CoverEditor(
    coverData: ByteArray?,
    coverMime: String,
    onPickCover: () -> Unit,
    onClearCover: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (coverData != null) {
                val bitmap = remember(coverData) {
                    BitmapFactory.decodeByteArray(coverData, 0, coverData.size)
                }
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = stringResource(R.string.tag_cover),
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(12.dp)),
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    emptyCoverPlaceholder()
                }
            } else {
                emptyCoverPlaceholder()
            }

            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onPickCover) {
                    Text(stringResource(R.string.tag_change_cover))
                }
                if (coverData != null) {
                    Button(onClick = onClearCover) {
                        Text(stringResource(R.string.tag_remove_cover))
                    }
                }
            }
        }
    }
}

@Composable
private fun emptyCoverPlaceholder() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(12.dp))
            .background(MiuixTheme.colorScheme.surfaceContainer),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = MiuixIcons.Image,
            contentDescription = stringResource(R.string.tag_cover),
            modifier = Modifier.size(64.dp),
            tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
    }
}

@Composable
private fun TagSearchResultItem(
    result: TagSearchResult,
    onApply: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val coverBitmap = remember(result.coverData) {
            result.coverData?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
        }
        if (coverBitmap != null) {
            Image(
                bitmap = coverBitmap.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop,
            )
        } else {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MiuixTheme.colorScheme.surfaceContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = MiuixIcons.Image,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = result.title,
                style = MiuixTheme.textStyles.body1,
                color = MiuixTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${result.artist} · ${result.album}",
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = result.source,
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.primary,
                )
                if (result.artist.isNotBlank()) {
                    Spacer(Modifier.width(4.dp))
                    Text(text = "·", style = MiuixTheme.textStyles.body2, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                }
                if (result.album.isNotBlank()) {
                    Spacer(Modifier.width(4.dp))
                    Text(text = "⊞", style = MiuixTheme.textStyles.body2, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                }
                if (result.lyrics.isNotBlank()) {
                    Spacer(Modifier.width(4.dp))
                    Text(text = "♪", style = MiuixTheme.textStyles.body2, color = MiuixTheme.colorScheme.primary)
                }
            }
        }
        Button(
            onClick = onApply,
            modifier = Modifier.padding(start = 8.dp),
        ) {
            Text(stringResource(R.string.tag_apply))
        }
    }
}

@Composable
private fun FileCoverThumbnail(uri: String, coverCache: MutableMap<String, ByteArray?>) {
    var coverData by remember(uri) { mutableStateOf(coverCache[uri]) }
    val context = LocalContext.current
    LaunchedEffect(uri) {
        if (!coverCache.containsKey(uri)) {
            val data = withContext(Dispatchers.IO) {
                try {
                    val retriever = MediaMetadataRetriever()
                    retriever.setDataSource(context, Uri.parse(uri))
                    val d = retriever.embeddedPicture
                    retriever.release()
                    d
                } catch (_: Exception) { null }
            }
            coverCache[uri] = data
            coverData = data
        }
    }
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (coverData != null) Color.Transparent
                else MiuixTheme.colorScheme.surfaceContainer
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (coverData != null) {
            val bitmap = remember(coverData) {
                BitmapFactory.decodeByteArray(coverData, 0, coverData!!.size)
            }
            if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            }
        } else {
            Icon(
                imageVector = MiuixIcons.Image,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        }
    }
}

private fun scanAudioFiles(context: Context, treeUri: Uri): List<AudioFileEntry> {
    val dir = DocumentFile.fromTreeUri(context, treeUri) ?: return emptyList()
    return dir.listFiles()
        .filter { it.isFile && it.name != null }
        .mapNotNull { file ->
            val name = file.name ?: return@mapNotNull null
            val ext = name.substringAfterLast('.', "").lowercase()
            if (ext !in SUPPORTED_EXTS) return@mapNotNull null
            AudioFileEntry(
                uri = file.uri.toString(),
                name = name,
                size = file.length(),
                format = ext,
                lastModified = file.lastModified(),
            )
        }
        .sortedBy { it.name }
}
