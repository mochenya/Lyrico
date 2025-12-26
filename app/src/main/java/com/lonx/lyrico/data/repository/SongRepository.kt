package com.lonx.lyrico.data.repository

import android.content.Context
import android.util.Log
import androidx.core.net.toUri
import com.lonx.audiotag.model.AudioTagData
import com.lonx.audiotag.rw.AudioTagReader
import com.lonx.lyrico.data.LyricoDatabase
import com.lonx.lyrico.data.model.SongEntity
import com.lonx.lyrico.data.model.SongFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 歌曲数据存储库 - 处理数据库和文件系统交互
 */
class SongRepository(
    private val database: LyricoDatabase,
    private val context: Context
) {
    private val songDao = database.songDao()
    private companion object {
        const val TAG = "SongRepository"
        const val COVER_CACHE_DIR = "covers"
    }

    /**
     * Get all songs as a flow.
     */
    fun getAllSongs(): Flow<List<SongEntity>> {
        return songDao.getAllSongs()
    }

    /**
     * 获取指定歌曲的详细信息（Flow）
     */
    fun getSongFlow(filePath: String): Flow<SongEntity?> = songDao.getSongByPathFlow(filePath)

    /**
     * 获取指定歌曲的详细信息
     */
    suspend fun getSong(filePath: String): SongEntity? = withContext(Dispatchers.IO) {
        songDao.getSongByPath(filePath)
    }

    /**
     * 从文件读取元数据并保存到数据库
     */
    suspend fun readAndSaveSongMetadata(
        songFile: SongFile,
        forceUpdate: Boolean = false
    ): SongEntity? = withContext(Dispatchers.IO) {
        try {
            val fileLastModified = getFileLastModified(songFile.filePath)

            if (!forceUpdate) {
                val existingSong = songDao.getSongByPath(songFile.filePath)
                if (existingSong != null && existingSong.fileLastModified == fileLastModified) {
                    return@withContext existingSong
                }
                // If song exists, we might need to delete old cover before saving new one
                deleteCoverFile(existingSong?.coverPath)
            }

            Log.d(TAG, "读取歌曲元数据: ${songFile.fileName}")
            
            val audioData = context.contentResolver.openFileDescriptor(
                songFile.filePath.toUri(), "r"
            )?.use { pfd ->
                AudioTagReader.read(pfd, readPictures = true)
            } ?: return@withContext null

            val coverPath = audioData.pictures.firstOrNull()?.data?.let {
                saveCoverToFile(it, songFile.filePath)
            }

            val songEntity = SongEntity(
                filePath = songFile.filePath,
                fileName = songFile.fileName,
                title = audioData.title,
                artist = audioData.artist,
                album = audioData.album,
                genre = audioData.genre,
                date = audioData.date,
                lyrics = audioData.lyrics,
                durationMilliseconds = audioData.durationMilliseconds,
                bitrate = audioData.bitrate,
                sampleRate = audioData.sampleRate,
                channels = audioData.channels,
                rawProperties = audioData.rawProperties.toString(),
                coverPath = coverPath,
                fileLastModified = fileLastModified,
                dbUpdateTime = System.currentTimeMillis()
            )

            songDao.insert(songEntity)
            Log.d(TAG, "歌曲元数据已保存: ${songFile.fileName}")
            return@withContext songEntity

        } catch (e: Exception) {
            Log.e(TAG, "读取歌曲元数据失败: ${songFile.fileName}", e)
            return@withContext null
        }
    }

    suspend fun scanAndSaveSongs(
        songFiles: List<SongFile>,
        forceFullScan: Boolean = false
    ): List<SongEntity> = withContext(Dispatchers.IO) {
        val results = mutableListOf<SongEntity>()
        
        for (songFile in songFiles) {
            val entity = readAndSaveSongMetadata(songFile, forceUpdate = forceFullScan)
            if (entity != null) {
                results.add(entity)
            }
        }

        val existingPaths = songFiles.map { it.filePath }
        if (existingPaths.isNotEmpty()) {
            songDao.deleteNotIn(existingPaths)
        }

        Log.d(TAG, "扫描完成: ${results.size}/${songFiles.size} 歌曲")
        return@withContext results
    }

    suspend fun updateSongMetadata(audioTagData: AudioTagData, filePath: String): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val existingSong = songDao.getSongByPath(filePath)
                    ?: return@withContext false

                val newCoverBytes = audioTagData.pictures.firstOrNull()?.data
                var newCoverPath = existingSong.coverPath

                if (newCoverBytes != null) {
                    // Delete old cover and save new one
                    deleteCoverFile(existingSong.coverPath)
                    newCoverPath = saveCoverToFile(newCoverBytes, filePath)
                }

                val updatedSong = existingSong.copy(
                    title = audioTagData.title ?: existingSong.title,
                    artist = audioTagData.artist ?: existingSong.artist,
                    album = audioTagData.album ?: existingSong.album,
                    genre = audioTagData.genre ?: existingSong.genre,
                    date = audioTagData.date ?: existingSong.date,
                    lyrics = audioTagData.lyrics ?: existingSong.lyrics,
                    rawProperties = audioTagData.rawProperties.toString(),
                    coverPath = newCoverPath,
                    dbUpdateTime = System.currentTimeMillis()
                )

                songDao.update(updatedSong)
                Log.d(TAG, "歌曲元数据已更新: $filePath")
                return@withContext true
            } catch (e: Exception) {
                Log.e(TAG, "更新歌曲元数据失败: $filePath", e)
                return@withContext false
            }
        }

    suspend fun deleteSong(filePath: String) = withContext(Dispatchers.IO) {
        try {
            val song = songDao.getSongByPath(filePath)
            deleteCoverFile(song?.coverPath)
            songDao.deleteByFilePath(filePath)
            Log.d(TAG, "歌曲已删除: $filePath")
        } catch (e: Exception) {
            Log.e(TAG, "删除歌曲失败: $filePath", e)
        }
    }

    suspend fun getSongsCount(): Int = withContext(Dispatchers.IO) {
        songDao.getSongsCount()
    }

    suspend fun clearAll() = withContext(Dispatchers.IO) {
        clearCoverCache()
        songDao.clear()
        Log.d(TAG, "所有歌曲数据已清空")
    }

    private fun getFileLastModified(filePath: String): Long {
        return try {
            context.contentResolver.query(
                filePath.toUri(),
                arrayOf("last_modified"),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    cursor.getLong(0)
                } else {
                    0L
                }
            } ?: 0L
        } catch (e: Exception) {
            Log.w(TAG, "无法获取文件修改时间: $filePath", e)
            0L
        }
    }
    
    private fun saveCoverToFile(coverData: ByteArray, songFilePath: String): String? {
        return try {
            val fileName = "cover_${songFilePath.hashCode()}.jpg"
            val coverCacheDir = File(context.cacheDir, COVER_CACHE_DIR).apply { mkdirs() }
            val file = File(coverCacheDir, fileName)
            file.writeBytes(coverData)
            file.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save cover to file for $songFilePath", e)
            null
        }
    }

    private fun deleteCoverFile(coverPath: String?) {
        if (coverPath == null) return
        try {
            val file = File(coverPath)
            if (file.exists()) {
                file.delete()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to delete cover file: $coverPath", e)
        }
    }
    
    private fun clearCoverCache() {
        try {
            val coverDir = File(context.cacheDir, COVER_CACHE_DIR)
            if (coverDir.exists()) {
                coverDir.deleteRecursively()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear cover cache", e)
        }
    }
}

