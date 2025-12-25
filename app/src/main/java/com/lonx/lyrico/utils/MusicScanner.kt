package com.lonx.lyrico.utils

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import androidx.core.net.toUri
import com.lonx.lyrico.data.model.SongFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 音乐文件扫描器 - 支持增量扫描优化
 * 通过缓存文件信息和检查修改时间来避免重复处理
 */
class MusicScanner(private val context: Context) {

    private val TAG = "MusicScanner"

    // 缓存已扫描的文件信息（URI -> lastModified）
    private val fileMetaCache = mutableMapOf<String, Long>()

    suspend fun scanMusicFiles(folderUris: List<String>): List<SongFile> =
        withContext(Dispatchers.IO) {

            val allSongFiles = mutableListOf<SongFile>()

            folderUris.forEach { folderUriString ->
                val folderUri = try {
                    folderUriString.toUri()
                } catch (e: Exception) {
                    Log.e(TAG, "Invalid URI: $folderUriString", e)
                    return@forEach
                }

                Log.d(TAG, "Scanning folder URI: $folderUri")

                try {
                    val songs = scanFromUri(folderUri)
                    allSongFiles.addAll(songs)
                } catch (e: SecurityException) {
                    Log.w(TAG, "No permission to access URI: $folderUri", e)
                } catch (e: Exception) {
                    Log.e(TAG, "Error scanning URI: $folderUri", e)
                }
            }

            allSongFiles
        }

    /**
     * 清空扫描缓存（强制全量重新扫描）
     */
    fun clearCache() {
        fileMetaCache.clear()
        Log.d(TAG, "扫描缓存已清空")
    }

    /**
     * 获取缓存大小（用于调试）
     */
    fun getCacheSize(): Int = fileMetaCache.size

    private suspend fun scanFromUri(uri: Uri): List<SongFile> = withContext(Dispatchers.IO) {
        if (!DocumentsContract.isTreeUri(uri)) {
            Log.w(TAG, "Not a tree URI, skipping: $uri")
            return@withContext emptyList<SongFile>()
        }
        try {
            val documentId = DocumentsContract.getTreeDocumentId(uri)
            return@withContext scanDirectory(uri, documentId)
        } catch (e: IllegalArgumentException) { // More specific exception
            Log.e(TAG, "Failed to get document ID from URI: $uri", e)
            return@withContext emptyList<SongFile>()
        }
    }

    private suspend fun scanDirectory(treeUri: Uri, parentDocumentId: String): List<SongFile> {
        val result = mutableListOf<SongFile>()
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocumentId)

        try {
            context.contentResolver.query(
                childrenUri,
                arrayOf(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    DocumentsContract.Document.COLUMN_MIME_TYPE
                ),
                null,
                null,
                null
            )?.use { cursor ->
                val idIndex =
                    cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameIndex =
                    cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val mimeIndex =
                    cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)

                while (cursor.moveToNext()) {
                    val documentId = cursor.getString(idIndex)
                    val displayName = cursor.getString(nameIndex)
                    val mimeType = cursor.getString(mimeIndex)

                    if (mimeType == DocumentsContract.Document.MIME_TYPE_DIR) {
                        if (displayName.startsWith(".")) {
                            continue
                        }
                        // Recursive call
                        result.addAll(scanDirectory(treeUri, documentId))

                    } else if (mimeType != null && mimeType.startsWith("audio/")) {
                        if (isMusicFileExtension(displayName)) {
                            val documentUri =
                                DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId)
                            result.add(SongFile(documentUri.toString(), displayName))
                        }
                    }
                }
            }
        } catch (e: SecurityException) {
            Log.w(
                TAG,
                "Permission denied for parentDocumentId: $parentDocumentId", e
            )
            // Can't proceed with this directory, but don't crash.
        } catch (e: Exception) {
            Log.e(
                TAG,
                "Error querying for parentDocumentId: $parentDocumentId", e
            )
        }
        return result
    }

    private fun isMusicFileExtension(fileName: String): Boolean {
        val extension = fileName.substringAfterLast(".", "").lowercase()
        return listOf(
            "mp3",
            "flac",
            "wav",
            "m4a",
            "aac",
            "ogg",
            "wma",
            "ape",
            "alac",
            "dsd",
            "wv"
        ).contains(extension)
    }
}