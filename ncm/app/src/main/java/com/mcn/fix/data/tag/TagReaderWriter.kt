package com.mcn.fix.data.tag

import android.content.Context
import android.media.MediaMetadataRetriever
import android.media.MediaScannerConnection
import android.net.Uri
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import org.jaudiotagger.tag.flac.FlacTag
import org.jaudiotagger.tag.images.AndroidArtwork
import java.io.File

object TagReaderWriter {

    private val SUPPORTED_EXTENSIONS = setOf(
        "flac", "ape", "wav", "aiff", "wv", "tta",
        "mp3", "mp4", "m4a", "ogg", "mpc", "opus",
        "wma", "dsf", "dff",
    )

    private val AUDIO_MIME_TYPES = mapOf(
        "flac" to "audio/flac", "ape" to "audio/ape", "wav" to "audio/wav",
        "aiff" to "audio/aiff", "wv" to "audio/wavpack", "tta" to "audio/tta",
        "mp3" to "audio/mpeg", "mp4" to "audio/mp4", "m4a" to "audio/mp4",
        "ogg" to "audio/ogg", "mpc" to "audio/musepack", "opus" to "audio/opus",
        "wma" to "audio/x-ms-wma", "dsf" to "audio/dsf", "dff" to "audio/dff",
    )

    fun isSupported(fileName: String): Boolean {
        return SUPPORTED_EXTENSIONS.any { fileName.lowercase().endsWith(".$it") }
    }

    fun readTags(context: Context, uri: Uri, fileName: String): AudioTagInfo {
        val tempFile = copyToCache(context, uri, fileName)
        try {
            val fallback = readWithMediaMetadataRetriever(context, uri, fileName)
            val format = fileName.substringAfterLast('.', "").lowercase()

            val audioFile = AudioFileIO.read(tempFile)
            val tag = audioFile.tag ?: return fallback
            val header = audioFile.audioHeader

            return AudioTagInfo(
                fileUri = uri.toString(),
                fileName = fileName,
                title = tag.getFirst(FieldKey.TITLE).ifBlank { fallback.title },
                artist = tag.getFirst(FieldKey.ARTIST).ifBlank { fallback.artist },
                album = tag.getFirst(FieldKey.ALBUM).ifBlank { fallback.album },
                genre = tag.getFirst(FieldKey.GENRE),
                year = tag.getFirst(FieldKey.YEAR),
                trackNumber = tag.getFirst(FieldKey.TRACK),
                lyrics = readLyrics(tag),
                coverData = readCoverData(tag) ?: fallback.coverData,
                coverMime = readCoverMime(tag) ?: fallback.coverMime,
                format = format,
                duration = header?.trackLength?.toLong()?.times(1000) ?: fallback.duration,
                fileSize = tempFile.length(),
                sampleRate = header?.sampleRateAsNumber?.toString() ?: fallback.sampleRate,
                bitrate = header?.bitRateAsNumber?.toString() ?: fallback.bitrate,
            )
        } catch (_: Exception) {
            return readWithMediaMetadataRetriever(context, uri, fileName)
        } finally {
            tempFile.delete()
        }
    }

    fun writeTags(context: Context, uri: Uri, fileName: String, info: AudioTagInfo): Boolean {
        val tempFile = copyToCache(context, uri, fileName)
        try {
            val audioFile = AudioFileIO.read(tempFile)
            val tag = audioFile.tag ?: return false

            setField(tag, FieldKey.TITLE, info.title)
            setField(tag, FieldKey.ARTIST, info.artist)
            setField(tag, FieldKey.ALBUM, info.album)
            setField(tag, FieldKey.GENRE, info.genre)
            setField(tag, FieldKey.YEAR, info.year)
            setField(tag, FieldKey.TRACK, info.trackNumber)
            setField(tag, FieldKey.LYRICS, info.lyrics)
            setCover(tag, info.coverData, info.coverMime)

            AudioFileIO.write(audioFile)
            writeBackFromCache(context, uri, tempFile)
            return true
        } catch (e: Exception) {
            return false
        } finally {
            tempFile.delete()
        }
    }

    fun readPresenceAndCover(context: Context, uri: Uri): Pair<TagPresenceInfo, ByteArray?> {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            val artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST) ?: ""
            val album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM) ?: ""
            val cover = retriever.embeddedPicture
            TagPresenceInfo(
                hasArtist = artist.isNotBlank(),
                hasAlbum = album.isNotBlank(),
                hasLyrics = false,
            ) to cover
        } catch (_: Exception) {
            TagPresenceInfo() to null
        } finally {
            try { retriever.release() } catch (_: Exception) {}
        }
    }

    fun deleteFields(context: Context, uri: Uri, fileName: String, fields: Set<String>): Boolean {
        val tempFile = copyToCache(context, uri, fileName)
        try {
            val audioFile = AudioFileIO.read(tempFile)
            val tag = audioFile.tag ?: return false
            if ("title" in fields) tag.deleteField(FieldKey.TITLE)
            if ("artist" in fields) tag.deleteField(FieldKey.ARTIST)
            if ("album" in fields) tag.deleteField(FieldKey.ALBUM)
            if ("genre" in fields) tag.deleteField(FieldKey.GENRE)
            if ("year" in fields) tag.deleteField(FieldKey.YEAR)
            if ("track" in fields) tag.deleteField(FieldKey.TRACK)
            if ("cover" in fields) tag.deleteArtworkField()
            if ("lyrics" in fields) tag.deleteField(FieldKey.LYRICS)
            AudioFileIO.write(audioFile)
            writeBackFromCache(context, uri, tempFile)
            return true
        } catch (_: Exception) { return false }
        finally { tempFile.delete() }
    }

    fun triggerMediaScan(context: Context, uri: Uri) {
        val path = uri.path
        if (path != null) {
            MediaScannerConnection.scanFile(context, arrayOf(path), null, null)
        }
    }

    private fun readWithMediaMetadataRetriever(context: Context, uri: Uri, fileName: String): AudioTagInfo {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            val title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE) ?: ""
            val artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST) ?: ""
            val album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM) ?: ""
            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0
            val coverData = retriever.embeddedPicture
            val coverMime = if (coverData != null) detectMime(coverData) else "image/jpeg"
            val format = fileName.substringAfterLast('.', "").lowercase()
            AudioTagInfo(
                fileUri = uri.toString(),
                fileName = fileName,
                title = title,
                artist = artist,
                album = album,
                format = format,
                duration = duration,
                coverData = coverData,
                coverMime = coverMime,
            )
        } catch (_: Exception) {
            AudioTagInfo(
                fileUri = uri.toString(),
                fileName = fileName,
                format = fileName.substringAfterLast('.', "").lowercase(),
            )
        } finally {
            try { retriever.release() } catch (_: Exception) {}
        }
    }

    private fun copyToCache(context: Context, uri: Uri, fileName: String): File {
        val cacheDir = File(context.cacheDir, "tag_editor")
        if (!cacheDir.exists()) cacheDir.mkdirs()
        val tempFile = File(cacheDir, "temp_${System.nanoTime()}_$fileName")
        context.contentResolver.openInputStream(uri)?.use { input ->
            tempFile.outputStream().use { output ->
                input.copyTo(output)
            }
        } ?: throw IllegalStateException("Cannot open $uri")
        return tempFile
    }

    private fun writeBackFromCache(context: Context, uri: Uri, tempFile: File) {
        context.contentResolver.openOutputStream(uri, "wt")?.use { output ->
            tempFile.inputStream().use { input ->
                input.copyTo(output)
            }
        } ?: throw IllegalStateException("Cannot write to $uri")
    }

    private fun readLyrics(tag: org.jaudiotagger.tag.Tag): String {
        for (key in listOf(FieldKey.LYRICS)) {
            try {
                val text = tag.getFirst(key)
                if (text.isNotBlank()) return text
            } catch (_: Exception) {}
        }
        try {
            val fields = tag.getFields(FieldKey.LYRICS)
            if (fields.isNotEmpty()) {
                val sb = StringBuilder()
                for (f in fields) sb.appendLine(f.toString())
                return sb.toString().trim()
            }
        } catch (_: Exception) {}
        return ""
    }

    private fun readCoverData(tag: org.jaudiotagger.tag.Tag): ByteArray? {
        return try {
            val artList = tag.artworkList
            if (artList.isNotEmpty()) artList[0].binaryData else null
        } catch (_: Exception) { null }
    }

    private fun readCoverMime(tag: org.jaudiotagger.tag.Tag): String? {
        return try {
            val artList = tag.artworkList
            if (artList.isNotEmpty()) artList[0].mimeType else null
        } catch (_: Exception) { null }
    }

    private fun setCover(tag: org.jaudiotagger.tag.Tag, data: ByteArray?, mime: String) {
        if (data == null) return
        tag.deleteArtworkField()
        if (tag is FlacTag) {
            tag.setField(tag.createArtworkField(data, 3, mime, "", 0, 0, 0, 0))
        } else {
            val artwork = AndroidArtwork()
            artwork.binaryData = data
            artwork.mimeType = mime
            artwork.pictureType = 3
            tag.setField(artwork)
        }
    }

    private fun setField(tag: org.jaudiotagger.tag.Tag, key: FieldKey, value: String) {
        if (value.isBlank()) return
        try {
            tag.deleteField(key)
            tag.setField(key, value.trim())
        } catch (_: Exception) {}
    }

    private fun detectMime(data: ByteArray): String {
        return when {
            data.size > 2 && data[0] == 0xFF.toByte() && data[1] == 0xD8.toByte() -> "image/jpeg"
            data.size > 4 && data[0] == 0x89.toByte() && data[1] == 0x50.toByte() && data[2] == 0x4E.toByte() && data[3] == 0x47.toByte() -> "image/png"
            data.size > 4 && data[0] == 0x52.toByte() && data[1] == 0x49.toByte() && data[2] == 0x46.toByte() && data[3] == 0x46.toByte() -> "image/webp"
            else -> "image/jpeg"
        }
    }
}
