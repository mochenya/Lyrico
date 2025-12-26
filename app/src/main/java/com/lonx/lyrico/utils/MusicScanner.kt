package com.lonx.lyrico.utils

import android.content.ContentUris
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import com.lonx.lyrico.data.model.SongFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 音乐文件扫描器 - 使用 MediaStore API 进行高效扫描
 */
class MusicScanner(private val context: Context) {

    private val TAG = "MusicScanner"

    /**
     * Scans for all music files on the device using MediaStore.
     * The folderUris parameter is ignored as MediaStore scans all indexed media.
     */
    suspend fun scanMusicFiles(folderUris: List<String>): List<SongFile> =
        withContext(Dispatchers.IO) {
            val songFiles = mutableListOf<SongFile>()

            // MediaStore is the single source of truth for media, so we ignore folderUris
            if (folderUris.isEmpty()) {
                Log.d(TAG, "No folders configured to scan, but MediaStore will scan all media.")
            }

            val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
            } else {
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            }

            val projection = arrayOf(
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.DISPLAY_NAME,
            )

            // Get only music files
            val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"

            Log.d(TAG, "Querying MediaStore for audio files...")

            try {
                context.contentResolver.query(
                    collection,
                    projection,
                    selection,
                    null,
                    "${MediaStore.Audio.Media.DATE_ADDED} DESC"
                )?.use { cursor ->
                    val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                    val nameColumn =
                        cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)

                    while (cursor.moveToNext()) {
                        val id = cursor.getLong(idColumn)
                        val name = cursor.getString(nameColumn)
                        val contentUri = ContentUris.withAppendedId(
                            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                            id
                        )

                        songFiles.add(SongFile(contentUri.toString(), name))
                    }
                    Log.d(TAG, "MediaStore scan found ${songFiles.size} music files.")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to query MediaStore", e)
                // Return empty list or handle error as appropriate
            }

            songFiles
        }

    /**
     * Caching is no longer needed with MediaStore, but we keep the methods for API compatibility.
     */
    fun clearCache() {
        Log.d(TAG, "clearCache() called, but no longer necessary with MediaStore.")
    }
}