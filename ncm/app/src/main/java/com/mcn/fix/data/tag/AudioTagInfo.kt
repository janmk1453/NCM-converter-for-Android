package com.mcn.fix.data.tag

data class AudioTagInfo(
    val fileUri: String = "",
    val fileName: String = "",
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val genre: String = "",
    val year: String = "",
    val trackNumber: String = "",
    val lyrics: String = "",
    val coverData: ByteArray? = null,
    val coverMime: String = "image/jpeg",
    val format: String = "",
    val duration: Long = 0,
    val fileSize: Long = 0,
    val sampleRate: String = "",
    val bitrate: String = "",
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AudioTagInfo) return false
        return fileUri == other.fileUri &&
                fileName == other.fileName &&
                title == other.title &&
                artist == other.artist &&
                album == other.album &&
                genre == other.genre &&
                year == other.year &&
                trackNumber == other.trackNumber &&
                lyrics == other.lyrics &&
                coverData.contentEquals(other.coverData) &&
                coverMime == other.coverMime &&
                format == other.format &&
                duration == other.duration &&
                fileSize == other.fileSize &&
                sampleRate == other.sampleRate &&
                bitrate == other.bitrate
    }

    override fun hashCode(): Int {
        var result = fileUri.hashCode()
        result = 31 * result + fileName.hashCode()
        result = 31 * result + title.hashCode()
        result = 31 * result + artist.hashCode()
        result = 31 * result + album.hashCode()
        result = 31 * result + genre.hashCode()
        result = 31 * result + year.hashCode()
        result = 31 * result + trackNumber.hashCode()
        result = 31 * result + lyrics.hashCode()
        result = 31 * result + (coverData?.contentHashCode() ?: 0)
        result = 31 * result + coverMime.hashCode()
        result = 31 * result + format.hashCode()
        result = 31 * result + duration.hashCode()
        result = 31 * result + fileSize.hashCode()
        result = 31 * result + sampleRate.hashCode()
        result = 31 * result + bitrate.hashCode()
        return result
    }
}

data class TagPresenceInfo(
    val hasArtist: Boolean = false,
    val hasAlbum: Boolean = false,
    val hasLyrics: Boolean = false,
)

data class AudioFileEntry(
    val uri: String,
    val name: String,
    val size: Long,
    val format: String,
    val lastModified: Long = 0,
)

data class AutoFillLogEntry(
    val timestamp: Long = System.currentTimeMillis(),
    val fileName: String,
    val missingBefore: List<String> = emptyList(),
    val missingAfter: List<String> = emptyList(),
    val status: String = "skipped",
    val detail: String = "",
    val pureMusicLyrics: Boolean = false,
)
