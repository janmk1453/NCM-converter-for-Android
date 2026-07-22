package com.mcn.fix.data.model

data class NcmMetadata(
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val format: String = "mp3",
    val coverData: ByteArray? = null,
    val coverMime: String = "image/jpeg",
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is NcmMetadata) return false
        return title == other.title &&
                artist == other.artist &&
                album == other.album &&
                format == other.format &&
                coverData.contentEquals(other.coverData) &&
                coverMime == other.coverMime
    }

    override fun hashCode(): Int {
        var result = title.hashCode()
        result = 31 * result + artist.hashCode()
        result = 31 * result + album.hashCode()
        result = 31 * result + format.hashCode()
        result = 31 * result + (coverData?.contentHashCode() ?: 0)
        result = 31 * result + coverMime.hashCode()
        return result
    }
}
