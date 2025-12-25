package com.lonx.lyrico.data.repository

import android.content.Context
import android.util.Log
import androidx.core.net.toUri
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.sqlite.db.SimpleSQLiteQuery
import com.lonx.audiotag.model.AudioTagData
import com.lonx.audiotag.rw.AudioTagReader
import com.lonx.lyrico.data.LyricoDatabase
import com.lonx.lyrico.data.model.SongEntity
import com.lonx.lyrico.data.model.SongFile
import com.lonx.lyrico.viewmodel.SortInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

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
    }

    /**
     * Get all songs (paginated and sorted)
     */
    fun getSongs(sortInfo: SortInfo): Flow<PagingData<SongEntity>> {
        val query = SimpleSQLiteQuery("SELECT * FROM songs ORDER BY ${sortInfo.sortBy.dbColumn} ${sortInfo.order.name}")
        return Pager(
            config = PagingConfig(pageSize = 30, enablePlaceholders = false),
            pagingSourceFactory = { songDao.getSongsPaged(query) }
        ).flow
    }

    /**
     * Search songs (paginated and sorted)
     */
    fun searchSongs(query: String, sortInfo: SortInfo): Flow<PagingData<SongEntity>> {
        val statement = SimpleSQLiteQuery(
            """
                SELECT * FROM songs 
                WHERE title LIKE '%' || ? || '%' 
                   OR artist LIKE '%' || ? || '%'
                   OR album LIKE '%' || ? || '%'
                ORDER BY ${sortInfo.sortBy.dbColumn} ${sortInfo.order.name}
            """,
            arrayOf(query, query, query)
        )
        return Pager(
            config = PagingConfig(pageSize = 30, enablePlaceholders = false),
            pagingSourceFactory = { songDao.searchSongs(statement) }
        ).flow
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
            // 检查文件最后修改时间
            val fileLastModified = getFileLastModified(songFile.filePath)
            
            // 如果不是强制更新，检查数据库中的版本
            if (!forceUpdate) {
                val existingSong = songDao.getSongByPath(songFile.filePath)
                if (existingSong != null && existingSong.fileLastModified == fileLastModified) {
                    Log.d(TAG, "歌曲未修改，跳过重新扫描: ${songFile.fileName}")
                    return@withContext existingSong
                }
            }

            Log.d(TAG, "读取歌曲元数据: ${songFile.fileName}")
            
            // 从文件读取元数据
            val audioData = context.contentResolver.openFileDescriptor(
                songFile.filePath.toUri(),
                "r"
            )?.use { pfd ->
                AudioTagReader.read(pfd, readPictures = true)
            } ?: return@withContext null

            // 获取封面图片
            val coverData = audioData.pictures.firstOrNull()?.data

            // 创建数据库实体
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
                coverData = coverData,
                fileLastModified = fileLastModified,
                dbUpdateTime = System.currentTimeMillis()
            )

            // 保存到数据库
            songDao.insert(songEntity)
            Log.d(TAG, "歌曲元数据已保存: ${songFile.fileName}")
            return@withContext songEntity

        } catch (e: Exception) {
            Log.e(TAG, "读取歌曲元数据失败: ${songFile.fileName}", e)
            return@withContext null
        }
    }

    /**
     * 批量扫描并保存歌曲元数据（增量更新）
     */
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

        // 清理已删除的文件
        val existingPaths = songFiles.map { it.filePath }
        if (existingPaths.isNotEmpty()) {
            songDao.deleteNotIn(existingPaths)
        }

        Log.d(TAG, "扫描完成: ${results.size}/${songFiles.size} 歌曲")
        return@withContext results
    }

    /**
     * 更新歌曲元数据（编辑后直接更新数据库）
     */
    suspend fun updateSongMetadata(audioTagData: AudioTagData, filePath: String): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val existingSong = songDao.getSongByPath(filePath)
                    ?: return@withContext false

                val updatedSong = existingSong.copy(
                    title = audioTagData.title ?: existingSong.title,
                    artist = audioTagData.artist ?: existingSong.artist,
                    album = audioTagData.album ?: existingSong.album,
                    genre = audioTagData.genre ?: existingSong.genre,
                    date = audioTagData.date ?: existingSong.date,
                    lyrics = audioTagData.lyrics ?: existingSong.lyrics,
                    rawProperties = audioTagData.rawProperties.toString(),
                    coverData = audioTagData.pictures.firstOrNull()?.data
                        ?: existingSong.coverData,
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

    /**
     * 删除歌曲记录
     */
    suspend fun deleteSong(filePath: String) = withContext(Dispatchers.IO) {
        try {
            songDao.deleteByFilePath(filePath)
            Log.d(TAG, "歌曲已删除: $filePath")
        } catch (e: Exception) {
            Log.e(TAG, "删除歌曲失败: $filePath", e)
        }
    }

    /**
     * 获取歌曲总数
     */
    suspend fun getSongsCount(): Int = withContext(Dispatchers.IO) {
        songDao.getSongsCount()
    }

    /**
     * 清空所有歌曲数据
     */
    suspend fun clearAll() = withContext(Dispatchers.IO) {
        songDao.clear()
        Log.d(TAG, "所有歌曲数据已清空")
    }

    /**
     * 获取文件最后修改时间
     */
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
}
