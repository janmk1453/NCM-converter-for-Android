package com.mcn.fix.data.tag

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class TagSearchResult(
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val coverData: ByteArray? = null,
    val coverMime: String = "image/jpeg",
    val lyrics: String = "",
    val source: String = "",
)

object TagSearchApi {

    const val PURE_MUSIC_LYRICS = "[00:00.00]纯音乐，请欣赏"
    enum class Source { ALL, NETEASE, QQ_MUSIC }
    private const val TIMEOUT = 10000

    fun parseFileName(fileName: String): Pair<String, String> {
        val name = fileName.substringBeforeLast('.')
        val separators = listOf(" · ", "・", " - ", " – ", " — ", " _ ", " / ")
        for (sep in separators) {
            val parts = name.split(sep, limit = 2)
            if (parts.size == 2) {
                val a = parts[0].trim()
                val b = parts[1].trim()
                if (a.length in 2..50 && b.length in 2..80) {
                    return a to b
                }
            }
        }
        return "" to name.trim()
    }

    suspend fun search(query: String, artist: String = "", album: String = "", source: Source = Source.ALL): List<TagSearchResult> =
        withContext(Dispatchers.IO) {
            val allResults = mutableListOf<TagSearchResult>()
            val seen = mutableSetOf<Pair<String, String>>()

            fun addResults(results: List<TagSearchResult>) {
                for (r in results) {
                    val key = r.title.trim().replace(" ", "").lowercase() to
                            r.artist.trim().replace(" ", "").lowercase()
                    if (r.title.isNotBlank() && key !in seen) {
                        seen.add(key)
                        allResults.add(r)
                    }
                }
            }

            val queries = mutableListOf<Pair<String, String>>()
            queries.add(query to artist)
            if (artist.isNotBlank()) queries.add(query to "")
            if (artist.isNotBlank() && query != artist) queries.add(artist to "")

            for ((q, a) in queries) {
                if (allResults.size >= 10) break
                if (source != Source.QQ_MUSIC)
                    addResults(try { searchNetease(q, a, maxResults = 10) } catch (_: Exception) { emptyList() })
                if (source != Source.NETEASE)
                    addResults(try { searchQQMusic(q, a, maxResults = 10) } catch (_: Exception) { emptyList() })
            }

            allResults.sortedBy { if (it.source == "网易云") 0 else 1 }
        }

    private fun searchNetease(query: String, artist: String, maxResults: Int = 15): List<TagSearchResult> {
        val q = buildString {
            append(query)
            if (artist.isNotBlank()) append(" $artist")
        }
        val encoded = URLEncoder.encode(q.trim().take(80), "UTF-8")
        val url = URL("https://music.163.com/api/cloudsearch/pc")
        val conn = url.openConnection() as HttpURLConnection
        conn.apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = TIMEOUT
            readTimeout = TIMEOUT
            setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
            setRequestProperty("Referer", "https://music.163.com/")
            setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
        }
        conn.outputStream.use { it.write("s=$encoded&type=1&offset=0&limit=$maxResults".toByteArray()) }
        val response = conn.inputStream.bufferedReader().readText()
        conn.disconnect()
        val json = JSONObject(response)
        if (json.optInt("code", -1) != 200) return emptyList()
        val result = json.optJSONObject("result")
        val songs = result?.optJSONArray("songs") ?: return emptyList()
        val indices = (0 until songs.length()).toList()
        return indices.mapNotNull { i ->
            try {
                val song = songs.getJSONObject(i)
                val songId = song.optLong("id")
                val albumObj = song.optJSONObject("al")
                val artistsArr = song.optJSONArray("ar")
                val artistName = if (artistsArr != null && artistsArr.length() > 0)
                    artistsArr.getJSONObject(0).optString("name", "") else ""
                val picUrl = albumObj?.optString("picUrl", "") ?: ""
                val coverData = if (picUrl.isNotBlank()) downloadCover(picUrl) else null
                val lyrics = if (songId > 0) fetchNeteaseLyrics(songId) else ""
                TagSearchResult(
                    title = song.optString("name", ""),
                    artist = artistName,
                    album = albumObj?.optString("name", "") ?: "",
                    coverData = coverData,
                    coverMime = "image/jpeg",
                    lyrics = lyrics,
                    source = "网易云",
                )
            } catch (_: Exception) { null }
        }
    }

    private fun fetchNeteaseLyrics(songId: Long): String {
        return try {
            val url = URL("https://music.163.com/api/song/lyric?id=$songId&lv=1&kv=1&tv=-1")
            val conn = url.openConnection() as HttpURLConnection
            conn.apply {
                requestMethod = "GET"
                connectTimeout = 5000
                readTimeout = 5000
                setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                setRequestProperty("Referer", "https://music.163.com/")
            }
            val response = conn.inputStream.bufferedReader().readText()
            conn.disconnect()
            val json = JSONObject(response)
            val lrc = json.optJSONObject("lrc")
            val text = lrc?.optString("lyric", "") ?: ""
            text.trim()
        } catch (_: Exception) { "" }
    }

    private fun searchQQMusic(query: String, artist: String, maxResults: Int = 15): List<TagSearchResult> {
        val q = buildString {
            append(query)
            if (artist.isNotBlank()) append(" $artist")
        }
        val encoded = URLEncoder.encode(q.trim().take(80), "UTF-8")
        val url = URL("https://c.y.qq.com/soso/fcgi-bin/client_search_cp?w=$encoded&format=json&p=1&n=$maxResults")
        val conn = url.openConnection() as HttpURLConnection
        conn.apply {
            requestMethod = "GET"
            connectTimeout = TIMEOUT
            readTimeout = TIMEOUT
            setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
            setRequestProperty("Referer", "https://y.qq.com/")
        }
        val response = conn.inputStream.bufferedReader().readText()
        conn.disconnect()
        val json = JSONObject(response)
        val data = json.optJSONObject("data")
        val songList = data?.optJSONObject("song")?.optJSONArray("list") ?: return emptyList()
        val indices = (0 until songList.length()).toList()
        return indices.mapNotNull { i ->
            try {
                val song = songList.getJSONObject(i)
                val mid = song.optString("songmid", "")
                val albumMid = song.optString("albummid", "")
                val coverUrl = if (albumMid.isNotBlank())
                    "https://y.gtimg.cn/music/photo_new/T002R300x300M000${albumMid}.jpg" else ""
                val coverData = if (coverUrl.isNotBlank()) downloadCover(coverUrl) else null
                val singerArr = song.optJSONArray("singer")
                val artistName = if (singerArr != null && singerArr.length() > 0)
                    singerArr.getJSONObject(0).optString("name", "") else ""
                val lyrics = if (mid.isNotBlank()) fetchQQMusicLyrics(mid) else ""
                TagSearchResult(
                    title = song.optString("songname", ""),
                    artist = artistName,
                    album = song.optString("albumname", ""),
                    coverData = coverData,
                    coverMime = "image/jpeg",
                    lyrics = lyrics,
                    source = "QQ音乐",
                )
            } catch (_: Exception) { null }
        }
    }

    private fun fetchQQMusicLyrics(mid: String): String {
        return try {
            val encoded = URLEncoder.encode(mid, "UTF-8")
            val url = URL("https://c.y.qq.com/lyric/fcgi-bin/fcg_query_lyric_new.fcg?songmid=$encoded&format=json&nobase64=1")
            val conn = url.openConnection() as HttpURLConnection
            conn.apply {
                requestMethod = "GET"
                connectTimeout = 5000
                readTimeout = 5000
                setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                setRequestProperty("Referer", "https://y.qq.com/")
            }
            val response = conn.inputStream.bufferedReader().readText()
            conn.disconnect()
            val json = JSONObject(response)
            val lyricStr = json.optString("lyric", "")
            if (lyricStr.isNotBlank()) {
                java.util.Base64.getDecoder().decode(lyricStr).let { String(it) }.trim()
            } else ""
        } catch (_: Exception) { "" }
    }

    private fun downloadCover(urlStr: String): ByteArray? {
        return try {
            val safeUrl = urlStr.replace("http://", "https://")
            val url = URL(safeUrl)
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = TIMEOUT
            conn.readTimeout = TIMEOUT
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
            val referer = if (safeUrl.contains("gtimg.cn")) "https://y.qq.com/" else "https://music.163.com/"
            conn.setRequestProperty("Referer", referer)
            val input = conn.inputStream
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            var read: Int
            while (input.read(buffer).also { read = it } != -1) {
                output.write(buffer, 0, read)
            }
            input.close()
            conn.disconnect()
            val raw = output.toByteArray()
            if (raw.size > 512 * 1024) {
                val bmp = BitmapFactory.decodeByteArray(raw, 0, raw.size) ?: return raw
                val maxSize = 1000
                val scale = minOf(maxSize.toFloat() / bmp.width, maxSize.toFloat() / bmp.height, 1f)
                if (scale < 1f) {
                    val scaled = Bitmap.createScaledBitmap(bmp, (bmp.width * scale).toInt(), (bmp.height * scale).toInt(), true)
                    val out = ByteArrayOutputStream()
                    scaled.compress(Bitmap.CompressFormat.JPEG, 85, out)
                    scaled.recycle()
                    bmp.recycle()
                    return out.toByteArray()
                }
                bmp.recycle()
            }
            raw
        } catch (_: Exception) { null }
    }
}

