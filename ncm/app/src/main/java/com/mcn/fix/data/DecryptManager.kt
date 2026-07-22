package com.mcn.fix.data

import android.content.Context
import android.net.Uri
import android.util.Base64
import com.mcn.fix.crypto.NcmCrypto
import com.mcn.fix.crypto.NcmStreamCipher
import com.mcn.fix.data.model.NcmFileInfo
import com.mcn.fix.data.model.NcmMetadata
import com.mcn.fix.util.FileUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.io.RandomAccessFile
import org.json.JSONObject

data class DecryptProgress(
    val total: Int = 0,
    val completed: Int = 0,
    val success: Int = 0,
    val failed: Int = 0,
    val currentFile: String = "",
    val isRunning: Boolean = false,
)

data class FileResult(
    val fileName: String,
    val success: Boolean,
    val error: String? = null,
)

data class NcmParseResult(
    val streamCipher: NcmStreamCipher,
    val metadata: NcmMetadata,
    val audioOffset: Long,
    val fileSize: Long,
)

class DecryptManager(private val context: Context) {

    private val _progress = MutableStateFlow(DecryptProgress())
    val progress: StateFlow<DecryptProgress> = _progress.asStateFlow()

    private val audioBufferSize = 64 * 1024

    suspend fun decryptAll(
        files: List<NcmFileInfo>,
        outputDirUri: Uri,
        concurrency: Int = 4,
    ): List<FileResult> = withContext(Dispatchers.IO) {
        val checkedFiles = files.filter { it.checked }
        val results = mutableListOf<FileResult>()
        var successCount = 0
        var failedCount = 0

        _progress.value = DecryptProgress(
            total = checkedFiles.size,
            isRunning = true,
        )

        val semaphore = Semaphore(concurrency)

        coroutineScope {
            checkedFiles.map { fileInfo ->
                async {
                    semaphore.acquire()
                    try {
                        _progress.value = _progress.value.copy(currentFile = fileInfo.name)

                        val result = decryptSingle(fileInfo, outputDirUri)

                        if (result.success) successCount++ else failedCount++
                        results.add(result)

                        _progress.value = _progress.value.copy(
                            completed = _progress.value.completed + 1,
                            success = successCount,
                            failed = failedCount,
                        )

                        result
                    } catch (e: Exception) {
                        val result = FileResult(fileInfo.name, false, e.message)
                        failedCount++
                        results.add(result)
                        _progress.value = _progress.value.copy(
                            completed = _progress.value.completed + 1,
                            failed = failedCount,
                        )
                        result
                    } finally {
                        semaphore.release()
                    }
                }
            }.awaitAll()
        }

        _progress.value = DecryptProgress(
            total = checkedFiles.size,
            completed = checkedFiles.size,
            success = successCount,
            failed = failedCount,
            isRunning = false,
        )

        results
    }

    private fun decryptSingle(
        fileInfo: NcmFileInfo,
        outputDirUri: Uri,
    ): FileResult {
        val sourceUri = Uri.parse(fileInfo.path)
        val tempFile = File.createTempFile("ncm_", ".tmp")

        try {
            val inputStream = context.contentResolver.openInputStream(sourceUri)
                ?: throw IllegalStateException("Cannot open source file")
            inputStream.use { input ->
                tempFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }

            val raf = RandomAccessFile(tempFile, "r")
            val parseResult = try {
                parseNcmFile(raf)
            } finally {
                raf.close()
            }

            val meta = parseResult.metadata
            val detectedFormat = detectAudioFormat(tempFile, parseResult.audioOffset)
            val outputFormat = detectedFormat.ifEmpty { meta.format }

            val mimeType = when (outputFormat) {
                "flac" -> "audio/flac"
                "m4a", "aac" -> "audio/mp4"
                "ogg" -> "audio/ogg"
                "wav" -> "audio/wav"
                else -> "audio/mpeg"
            }
            val ext = when (outputFormat) {
                "flac" -> ".flac"
                "m4a", "aac" -> ".m4a"
                "ogg" -> ".ogg"
                "wav" -> ".wav"
                else -> ".mp3"
            }
            val baseName = fileInfo.name.removeSuffix(".ncm")
            val outputName = baseName + ext

            val outputFile = FileUtils.createOutputFile(context, outputDirUri, outputName, mimeType)
                ?: throw IllegalStateException("Cannot create output file")

            val outputUri = outputFile.uri
            val outputStream = context.contentResolver.openOutputStream(outputUri)
                ?: throw IllegalStateException("Cannot open output stream")

            outputStream.use { out ->
                val cipher = parseResult.streamCipher
                val audioRaf = RandomAccessFile(tempFile, "r")
                audioRaf.seek(parseResult.audioOffset)

                when {
                    outputFormat == "mp3" && hasMetadata(meta) -> {
                        writeId3v2Tag(out, meta)
                        writeDecryptedRaw(audioRaf, out, cipher)
                    }
                    outputFormat == "flac" -> {
                        writeFlacDecrypted(audioRaf, out, cipher, meta)
                    }
                    outputFormat == "m4a" && hasMetadata(meta) -> {
                        writeM4aDecrypted(audioRaf, out, cipher, meta)
                    }
                    else -> {
                        writeDecryptedRaw(audioRaf, out, cipher)
                    }
                }
                audioRaf.close()
            }

            return FileResult(fileInfo.name, true)
        } catch (e: Exception) {
            return FileResult(fileInfo.name, false, e.message ?: "Unknown error")
        } finally {
            tempFile.delete()
        }
    }

    private fun parseNcmFile(raf: RandomAccessFile): NcmParseResult {
        val magic = ByteArray(8)
        raf.readFully(magic)
        val magicStr = magic.toString(Charsets.US_ASCII)
        if (!magicStr.startsWith("CTENFDAM")) {
            throw IllegalArgumentException("Invalid NCM file: magic mismatch")
        }

        raf.skipBytes(2)

        val keyLenBytes = ByteArray(4)
        raf.readFully(keyLenBytes)
        val keyLen = leInt(keyLenBytes)
        val keyBlob = ByteArray(keyLen)
        raf.readFully(keyBlob)

        for (i in keyBlob.indices) {
            keyBlob[i] = (keyBlob[i].toInt() xor 0x64).toByte()
        }
        val decryptedKey = NcmCrypto.aesEcbDecrypt(NcmCrypto.coreKey, keyBlob)
        val keyStr = decryptedKey.toString(Charsets.UTF_8)
        val rc4KeyStart = if (keyStr.startsWith("neteasecloudmusic")) 17 else 0
        val rc4Key = decryptedKey.copyOfRange(rc4KeyStart, decryptedKey.size)
        val streamCipher = NcmStreamCipher(rc4Key)

        val metaLenBytes = ByteArray(4)
        raf.readFully(metaLenBytes)
        val metaLen = leInt(metaLenBytes)
        val metaBlob = ByteArray(metaLen)
        raf.readFully(metaBlob)

        for (i in metaBlob.indices) {
            metaBlob[i] = (metaBlob[i].toInt() xor 0x63).toByte()
        }
        val metaStr = metaBlob.toString(Charsets.UTF_8)
        val metaBase64Start = if (metaStr.startsWith("163 key(Don't modify):")) 22 else 0
        val metaBase64 = metaBlob.copyOfRange(metaBase64Start, metaBlob.size)
        val metaDecoded = Base64.decode(metaBase64, Base64.DEFAULT)
        val metaDecrypted = NcmCrypto.aesEcbDecrypt(NcmCrypto.metaKey, metaDecoded)
        val metaJsonStr = metaDecrypted.toString(Charsets.UTF_8)
        val jsonStart = if (metaJsonStr.startsWith("music:")) 6 else 0
        val jsonStr = metaJsonStr.substring(jsonStart)

        val metadata = parseMetadataJson(jsonStr)

        raf.skipBytes(4)

        raf.skipBytes(5)

        val coverLenBytes = ByteArray(4)
        raf.readFully(coverLenBytes)
        val coverLen = leInt(coverLenBytes)
        val coverData = if (coverLen > 0) {
            val data = ByteArray(coverLen)
            raf.readFully(data)
            data
        } else null

        val metadataWithCover = metadata.copy(coverData = coverData)

        return NcmParseResult(
            streamCipher = streamCipher,
            metadata = metadataWithCover,
            audioOffset = raf.filePointer,
            fileSize = raf.length(),
        )
    }

    private fun parseMetadataJson(jsonStr: String): NcmMetadata {
        val obj = JSONObject(jsonStr)
        val title = obj.optString("musicName", "")
        val artist = obj.optJSONArray("artist")?.let { arr ->
            (0 until arr.length()).joinToString(", ") {
                arr.optJSONArray(it)?.optString(0, "") ?: ""
            }
        } ?: obj.optString("artist", "")
        val album = obj.optString("album", "")
        val format = obj.optString("format", "mp3")
        val coverMime = if (format == "flac") "image/png" else "image/jpeg"
        return NcmMetadata(
            title = title,
            artist = artist,
            album = album,
            format = format,
            coverMime = coverMime,
        )
    }

    private fun detectAudioFormat(file: File, audioOffset: Long): String {
        try {
            val raf = RandomAccessFile(file, "r")
            raf.seek(audioOffset)
            val magic = ByteArray(16)
            val read = raf.read(magic)
            raf.close()
            if (read < 4) return ""

            return when {
                read >= 4 && magic[0] == 'f'.code.toByte() && magic[1] == 'L'.code.toByte() &&
                        magic[2] == 'a'.code.toByte() && magic[3] == 'C'.code.toByte() -> "flac"
                read >= 3 && magic[0] == 'I'.code.toByte() && magic[1] == 'D'.code.toByte() &&
                        magic[2] == '3'.code.toByte() -> "mp3"
                read >= 2 && (magic[0].toInt() and 0xFF) == 0xFF &&
                        (magic[1].toInt() and 0xFE) == 0xFA -> "mp3"
                read >= 4 && magic[0] == 'R'.code.toByte() && magic[1] == 'I'.code.toByte() &&
                        magic[2] == 'F'.code.toByte() && magic[3] == 'F'.code.toByte() -> "wav"
                read >= 4 && magic[0] == 'O'.code.toByte() && magic[1] == 'g'.code.toByte() &&
                        magic[2] == 'g'.code.toByte() && magic[3] == 'S'.code.toByte() -> "ogg"
                read >= 8 && magic[4] == 'f'.code.toByte() && magic[5] == 't'.code.toByte() &&
                        magic[6] == 'y'.code.toByte() && magic[7] == 'p'.code.toByte() -> "m4a"
                else -> ""
            }
        } catch (_: Exception) {
            return ""
        }
    }

    private fun writeFlacDecrypted(raf: RandomAccessFile, out: OutputStream, cipher: NcmStreamCipher, meta: NcmMetadata) {
        val streamInfoBuf = ByteArray(42)
        raf.read(streamInfoBuf)
        cipher.decryptInPlace(streamInfoBuf, 0)

        if (streamInfoBuf[0] != 0x00.toByte() || streamInfoBuf[1] != 0x00.toByte() ||
            streamInfoBuf[2] != 0x00.toByte() || streamInfoBuf[3] != 0x22.toByte()
        ) {
            raf.seek(raf.filePointer - 42)
            val buf = ByteArray(audioBufferSize)
            var totalRead = 0
            var bytesRead: Int
            while (raf.read(buf).also { bytesRead = it } != -1) {
                cipher.decryptInPlace(buf, totalRead)
                out.write(buf, 0, bytesRead)
                totalRead += bytesRead
            }
            return
        }

        out.write("fLaC".encodeToByteArray())

        val streamInfoData = ByteArray(34)
        System.arraycopy(streamInfoBuf, 4, streamInfoData, 0, 34)
        val streamInfoHeader = byteArrayOf(0x00, 0x00, 0x00, 0x22)
        out.write(streamInfoHeader)
        out.write(streamInfoData)

        val vorbisComment = createVorbisCommentBlock(meta)
        out.write(vorbisComment)

        val metadataBuf = ByteArray(audioBufferSize)
        var metadataBytesRead = 0
        var metadataPos = 0
        raf.read(metadataBuf).also { metadataBytesRead = it }
        cipher.decryptInPlace(metadataBuf, 42)

        var pos = 0
        while (pos < metadataBytesRead - 4) {
            val isLast = (metadataBuf[pos].toInt() and 0x80) != 0
            val blockType = metadataBuf[pos].toInt() and 0x7F
            val blockSize = ((metadataBuf[pos + 1].toInt() and 0xFF) shl 16) or
                    ((metadataBuf[pos + 2].toInt() and 0xFF) shl 8) or
                    (metadataBuf[pos + 3].toInt() and 0xFF)
            pos += 4

            if (blockType == 0) {
                pos += blockSize
                continue
            }

            if (pos + blockSize > metadataBytesRead) break

            val flag = if (isLast) 0x80 else 0x00
            val header = byteArrayOf((flag or blockType).toByte(), 0, 0, 0)
            out.write(header)
            out.write(metadataBuf, pos, blockSize)
            pos += blockSize

            if (isLast) break
        }

        val remainingAudio = ByteArray(audioBufferSize)
        var totalRead = pos
        var bytesRead: Int
        while (raf.read(remainingAudio).also { bytesRead = it } != -1) {
            cipher.decryptInPlace(remainingAudio, 42 + totalRead)
            out.write(remainingAudio, 0, bytesRead)
            totalRead += bytesRead
        }
    }

    private fun hasMetadata(meta: NcmMetadata) =
        meta.title.isNotBlank() || meta.artist.isNotBlank() || meta.album.isNotBlank() || meta.coverData != null

    private fun writeDecryptedRaw(raf: RandomAccessFile, out: OutputStream, cipher: NcmStreamCipher) {
        val buf = ByteArray(audioBufferSize)
        var totalRead = 0
        var bytesRead: Int
        while (raf.read(buf).also { bytesRead = it } != -1) {
            cipher.decryptInPlace(buf, totalRead)
            out.write(buf, 0, bytesRead)
            totalRead += bytesRead
        }
    }

    private fun writeM4aDecrypted(raf: RandomAccessFile, out: OutputStream, cipher: NcmStreamCipher, meta: NcmMetadata) {
        val allData = ByteArrayOutputStream()
        val readBuf = ByteArray(audioBufferSize)
        var totalRead = 0
        var bytesRead: Int
        while (raf.read(readBuf).also { bytesRead = it } != -1) {
            cipher.decryptInPlace(readBuf, totalRead)
            allData.write(readBuf, 0, bytesRead)
            totalRead += bytesRead
        }
        val data = allData.toByteArray()

        val moovStart = findBox(data, 0, "moov")
        if (moovStart < 0) {
            out.write(data)
            return
        }
        val moovSize = readIntBE(data, moovStart)
        val moovEnd = moovStart + moovSize

        val ilstBytes = createIlstBox(meta)
        val metaBytes = createMetaBoxWithIlst(ilstBytes)
        val udtaBytes = createBox("udta", metaBytes)

        val newMoov = ByteArrayOutputStream()
        newMoov.write(data, moovStart + 8, moovEnd - moovStart - 8)
        newMoov.write(udtaBytes)
        val newMoovData = newMoov.toByteArray()
        val newMoovBox = createBox("moov", newMoovData)

        out.write(data, 0, moovStart)
        out.write(newMoovBox)
        if (moovEnd < data.size) {
            out.write(data, moovEnd, data.size - moovEnd)
        }
    }

    private fun findBox(data: ByteArray, offset: Int, type: String): Int {
        var pos = offset
        val typeBytes = type.encodeToByteArray()
        while (pos + 8 <= data.size) {
            val size = readIntBE(data, pos)
            if (size < 8) break
            val matches = (0 until 4).all { data[pos + 4 + it] == typeBytes[it] }
            if (matches) return pos
            pos += size
        }
        return -1
    }

    private fun createBox(type: String, content: ByteArray): ByteArray {
        val size = 8 + content.size
        val box = ByteArray(size)
        writeIntBE(box, 0, size)
        type.encodeToByteArray().copyInto(box, 4)
        content.copyInto(box, 8)
        return box
    }

    private fun createMetaBoxWithIlst(ilst: ByteArray): ByteArray {
        val hdlrData = byteArrayOf(0, 0, 0, 0, 0, 0, 0, 0, 'm'.code.toByte(), 'd'.code.toByte(), 'i'.code.toByte(), 'r'.code.toByte(), 'a'.code.toByte(), 'p'.code.toByte(), 'p'.code.toByte(), 'l'.code.toByte(), 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0)
        val hdlr = createBox("hdlr", hdlrData)
        val metaContent = ByteArray(4) + hdlr + ilst
        return createBox("meta", metaContent)
    }

    private fun createIlstBox(meta: NcmMetadata): ByteArray {
        val items = mutableListOf<ByteArray>()
        if (meta.title.isNotBlank()) items.add(createIlstItem("©nam", 1u, meta.title.encodeToByteArray()))
        if (meta.artist.isNotBlank()) items.add(createIlstItem("©ART", 1u, meta.artist.encodeToByteArray()))
        if (meta.album.isNotBlank()) items.add(createIlstItem("©alb", 1u, meta.album.encodeToByteArray()))
        if (meta.coverData != null) {
            val typeCode = if (meta.coverMime.contains("png")) 14u else 13u
            items.add(createIlstItem("covr", typeCode, meta.coverData))
        }
        val combined = items.flatMap { it.toList() }.toByteArray()
        return createBox("ilst", combined)
    }

    private fun createIlstItem(key: String, typeCode: UInt, value: ByteArray): ByteArray {
        val dataContent = ByteArray(4) + ByteArray(4) + value
        writeIntBE(dataContent, 0, typeCode.toInt())
        val dataBox = createBox("data", dataContent)
        return createBox(key, dataBox)
    }

    private fun readIntBE(data: ByteArray, offset: Int): Int {
        return ((data[offset].toInt() and 0xFF) shl 24) or
                ((data[offset + 1].toInt() and 0xFF) shl 16) or
                ((data[offset + 2].toInt() and 0xFF) shl 8) or
                (data[offset + 3].toInt() and 0xFF)
    }

    private fun writeId3v2Tag(stream: OutputStream, meta: NcmMetadata) {
        val frames = mutableListOf<ByteArray>()

        if (meta.title.isNotBlank()) {
            frames.add(createTextFrame("TIT2", meta.title))
        }
        if (meta.artist.isNotBlank()) {
            frames.add(createTextFrame("TPE1", meta.artist))
        }
        if (meta.album.isNotBlank()) {
            frames.add(createTextFrame("TALB", meta.album))
        }
        if (meta.coverData != null) {
            frames.add(createApicFrame(meta.coverData, meta.coverMime))
        }

        if (frames.isEmpty()) return

        val framesData = frames.reduce { a, b -> a + b }
        val header = ByteArray(10)
        header[0] = 'I'.code.toByte()
        header[1] = 'D'.code.toByte()
        header[2] = '3'.code.toByte()
        header[3] = 0x03
        header[4] = 0x00
        header[5] = 0x00
        writeSyncSafeInt(header, 6, framesData.size)

        stream.write(header)
        stream.write(framesData)
    }

    private fun createTextFrame(frameId: String, text: String): ByteArray {
        val textBytes = text.toByteArray(Charsets.UTF_16BE)
        val encoding: Byte = 0x01

        val data = byteArrayOf(encoding) + textBytes
        val header = ByteArray(10)
        frameId.encodeToByteArray().copyInto(header, 0)
        writeIntBE(header, 4, data.size)
        header[8] = 0
        header[9] = 0

        return header + data
    }

    private fun createApicFrame(imageData: ByteArray, mimeType: String): ByteArray {
        val mimeBytes = mimeType.toByteArray(Charsets.US_ASCII)
        val encoding: Byte = 0x00

        val data = byteArrayOf(encoding) +
                mimeBytes + byteArrayOf(0) +
                byteArrayOf(0x03) +
                byteArrayOf(0) +
                imageData

        val header = ByteArray(10)
        "APIC".encodeToByteArray().copyInto(header, 0)
        writeIntBE(header, 4, data.size)
        header[8] = 0
        header[9] = 0

        return header + data
    }

    private fun writeSyncSafeInt(data: ByteArray, offset: Int, value: Int) {
        data[offset] = ((value shr 21) and 0x7F).toByte()
        data[offset + 1] = ((value shr 14) and 0x7F).toByte()
        data[offset + 2] = ((value shr 7) and 0x7F).toByte()
        data[offset + 3] = (value and 0x7F).toByte()
    }

    private fun writeIntBE(data: ByteArray, offset: Int, value: Int) {
        data[offset] = ((value shr 24) and 0xFF).toByte()
        data[offset + 1] = ((value shr 16) and 0xFF).toByte()
        data[offset + 2] = ((value shr 8) and 0xFF).toByte()
        data[offset + 3] = (value and 0xFF).toByte()
    }

    private fun createVorbisCommentBlock(meta: NcmMetadata): ByteArray {
        val fields = mutableListOf<ByteArray>()
        if (meta.title.isNotBlank()) fields.add("TITLE=${meta.title}".encodeToByteArray())
        if (meta.artist.isNotBlank()) fields.add("ARTIST=${meta.artist}".encodeToByteArray())
        if (meta.album.isNotBlank()) fields.add("ALBUM=${meta.album}".encodeToByteArray())

        if (meta.coverData != null) {
            val pictureBlock = createFlacPictureBlock(meta.coverData, meta.coverMime)
            val b64 = Base64.encodeToString(pictureBlock, Base64.NO_WRAP)
            fields.add("METADATA_BLOCK_PICTURE=$b64".encodeToByteArray())
        }

        val vendor = "mcn转换器".encodeToByteArray()
        val data = writeInt32LE(vendor.size) + vendor +
                writeInt32LE(fields.size) +
                fields.flatMap { field ->
                    writeInt32LE(field.size).toList() + field.toList()
                }.toByteArray()

        val blockHeader = byteArrayOf(
            (0x80 or 0x04).toByte(),
            ((data.size shr 16) and 0xFF).toByte(),
            ((data.size shr 8) and 0xFF).toByte(),
            (data.size and 0xFF).toByte(),
        )
        return blockHeader + data
    }

    private fun createFlacPictureBlock(imageData: ByteArray, mimeType: String): ByteArray {
        val mimeBytes = mimeType.toByteArray(Charsets.US_ASCII)
        val descBytes = byteArrayOf(0)

        return writeInt32LE(3) +
                writeInt32LE(mimeBytes.size) + mimeBytes +
                writeInt32LE(descBytes.size) + descBytes +
                writeInt32LE(0) +
                writeInt32LE(0) +
                writeInt32LE(0) +
                writeInt32LE(0) +
                writeInt32LE(imageData.size) + imageData
    }

    private fun writeInt32LE(value: Int): ByteArray {
        return byteArrayOf(
            (value and 0xFF).toByte(),
            ((value shr 8) and 0xFF).toByte(),
            ((value shr 16) and 0xFF).toByte(),
            ((value shr 24) and 0xFF).toByte(),
        )
    }

    private fun leInt(bytes: ByteArray): Int {
        return (bytes[0].toInt() and 0xFF) or
                ((bytes[1].toInt() and 0xFF) shl 8) or
                ((bytes[2].toInt() and 0xFF) shl 16) or
                ((bytes[3].toInt() and 0xFF) shl 24)
    }
}
