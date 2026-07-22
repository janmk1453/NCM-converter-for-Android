package com.mcn.fix.util

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import com.mcn.fix.data.model.NcmFileInfo

object FileUtils {

    private const val BUFFER_SIZE = 64 * 1024

    fun listNcmFiles(context: Context, treeUri: Uri): List<NcmFileInfo> {
        val dir = DocumentFile.fromTreeUri(context, treeUri) ?: return emptyList()
        val files = dir.listFiles()
            .filter { it.isFile && it.name?.endsWith(".ncm", true) == true }
            .mapNotNull { file ->
                val name = file.name ?: return@mapNotNull null
                NcmFileInfo(
                    path = file.uri.toString(),
                    name = name,
                    size = file.length(),
                    lastModified = file.lastModified(),
                    checked = true,
                )
            }
            .sortedBy { it.name }
        return files
    }

    fun listOutputFileNames(context: Context, treeUri: Uri): Set<String> {
        val dir = DocumentFile.fromTreeUri(context, treeUri) ?: return emptySet()
        return dir.listFiles()
            .mapNotNull { it.name }
            .toSet()
    }

    fun createOutputFile(context: Context, parentUri: Uri, fileName: String, mimeType: String): DocumentFile? {
        val parent = DocumentFile.fromTreeUri(context, parentUri) ?: return null
        parent.findFile(fileName)?.delete()
        return parent.createFile(mimeType, fileName.removeSuffix(".ncm"))
    }

    fun readBytes(context: Context, uri: Uri): ByteArray {
        val inputStream = context.contentResolver.openInputStream(uri)
            ?: throw IllegalStateException("Cannot open input stream: $uri")
        return inputStream.use { stream ->
            stream.readBytes()
        }
    }

    fun readBytesRange(context: Context, uri: Uri, offset: Long, length: Int): ByteArray {
        val inputStream = context.contentResolver.openInputStream(uri)
            ?: throw IllegalStateException("Cannot open input stream: $uri")
        return inputStream.use { stream ->
            stream.skip(offset)
            val data = ByteArray(length)
            var totalRead = 0
            while (totalRead < length) {
                val read = stream.read(data, totalRead, length - totalRead)
                if (read == -1) throw IllegalStateException("Unexpected EOF")
                totalRead += read
            }
            data
        }
    }

    fun writeBytes(context: Context, uri: Uri, data: ByteArray) {
        val outputStream = context.contentResolver.openOutputStream(uri)
            ?: throw IllegalStateException("Cannot open output stream: $uri")
        outputStream.use { stream ->
            stream.write(data)
        }
    }

    fun writeStream(context: Context, uri: Uri, inputProvider: (android.content.ContentResolver) -> Unit) {
        val outputStream = context.contentResolver.openOutputStream(uri)
            ?: throw IllegalStateException("Cannot open output stream: $uri")
        outputStream.use { stream ->
            inputProvider(context.contentResolver)
        }
    }

    fun deleteFile(context: Context, uri: Uri): Boolean {
        return DocumentsContract.deleteDocument(context.contentResolver, uri)
    }
}
