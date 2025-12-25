package com.lonx.lyrico.viewmodel

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.lonx.audiotag.model.AudioTagData
import com.lonx.lyrico.data.model.SongEntity
import com.lonx.lyrico.data.model.SongFile
import com.lonx.lyrico.data.repository.SongRepository
import com.lonx.lyrico.utils.BackgroundScanManager
import com.lonx.lyrico.utils.MusicScanner
import com.lonx.lyrico.utils.SettingsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Collections

data class SongInfo(
    val filePath: String,
    val fileName: String,
    val tagData: AudioTagData?,
    val coverBitmap: Bitmap?
)

data class SongListUiState(
    val isLoading: Boolean = false,
    val lastScanTime: Long = 0,
    val searchQuery: String = "",
    val sortInfo: SortInfo = SortInfo()
)

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class SongListViewModel(
    private val musicScanner: MusicScanner,
    private val settingsManager: SettingsManager,
    private val songRepository: SongRepository,
    private val backgroundScanManager: BackgroundScanManager,
    private val context: Context
) : ViewModel() {

    private val TAG = "SongListViewModel"
    private val _uiState = MutableStateFlow(SongListUiState())
    val uiState: StateFlow<SongListUiState> = _uiState.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _sortInfo = MutableStateFlow(SortInfo())
    val sortInfo: StateFlow<SortInfo> = _sortInfo.asStateFlow()

    val songs: Flow<PagingData<SongEntity>> = combine(_searchQuery, _sortInfo) { query, sort ->
        Pair(query, sort)
    }.debounce(300)
        .flatMapLatest { (query, sort) ->
            if (query.isBlank()) {
                songRepository.getSongs(sort)
            } else {
                songRepository.searchSongs(query, sort)
            }
        }.cachedIn(viewModelScope)

    private val songInfoCache = Collections.synchronizedMap(mutableMapOf<String, StateFlow<SongInfo?>>())

    private var lastFullScanTime = 0L
    private val FULL_SCAN_INTERVAL_MS = 60 * 60 * 1000 // 1小时

    init {
        Log.d(TAG, "SongListViewModel 初始化")

        viewModelScope.launch {
            settingsManager.scannedFolders.collect { folders ->
                Log.d(TAG, "扫描文件夹配置已变更: $folders")

                val currentTime = System.currentTimeMillis()
                if (lastFullScanTime == 0L || (currentTime - lastFullScanTime) > FULL_SCAN_INTERVAL_MS) {
                    lastFullScanTime = currentTime
                    scanMusicFilesInBackground()
                }
            }
        }
    }

    fun onSortChange(newSortInfo: SortInfo) {
        _sortInfo.value = newSortInfo
        _uiState.update { it.copy(sortInfo = newSortInfo) }
    }

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
        _uiState.update { it.copy(searchQuery = query) }
    }

    private fun scanMusicFilesInBackground() {
        viewModelScope.launch(Dispatchers.Default) {
            try {
                Log.d(TAG, "开始后台扫描文件")
                _uiState.update { it.copy(isLoading = true) }

                val scannedFolders = settingsManager.scannedFolders.first()
                if (scannedFolders.isEmpty()) {
                    _uiState.update { it.copy(isLoading = false) }
                    return@launch
                }

                val songFiles = musicScanner.scanMusicFiles(scannedFolders.toList())
                Log.d(TAG, "扫描发现 ${songFiles.size} 个音乐文件")

                withContext(Dispatchers.IO) {
                    songRepository.scanAndSaveSongs(songFiles, forceFullScan = false)
                }

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        lastScanTime = System.currentTimeMillis()
                    )
                }
                Log.d(TAG, "后台扫描完成")

            } catch (e: Exception) {
                Log.e(TAG, "后台扫描失败", e)
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun refreshSongs(forceFullScan: Boolean = false) {
        Log.d(TAG, "用户手动刷新歌曲列表 (forceFullScan=$forceFullScan)")

        if (forceFullScan) {
            musicScanner.clearCache()
            lastFullScanTime = 0
        }

        scanMusicFilesInBackground()
    }
    
    fun getSongDetails(songFile: SongFile): Flow<SongInfo?> {
        return songRepository.getSongFlow(songFile.filePath)
            .map { songEntity ->
                if (songEntity == null) {
                    null
                } else {
                    val bitmap = songInfoCache[songFile.filePath]?.value?.coverBitmap
                        ?: songEntity.coverData?.let {
                            BitmapFactory.decodeByteArray(it, 0, it.size)
                        }
                    
                    val audioData = AudioTagData(
                        title = songEntity.title,
                        artist = songEntity.artist,
                        album = songEntity.album,
                        genre = songEntity.genre,
                        date = songEntity.date,
                        lyrics = songEntity.lyrics,
                        durationMilliseconds = songEntity.durationMilliseconds,
                        bitrate = songEntity.bitrate,
                        sampleRate = songEntity.sampleRate,
                        channels = songEntity.channels,
                        rawProperties = emptyMap()
                    )
                    
                    SongInfo(
                        filePath = songEntity.filePath,
                        fileName = songEntity.fileName,
                        tagData = audioData,
                        coverBitmap = bitmap
                    )
                }
            }
    }

    fun startBackgroundScan() {
        Log.d(TAG, "启动定期后台扫描任务")
        backgroundScanManager.startBackgroundScan(
            forceFullScan = false,
            initialDelayMinutes = 5,
            backoffDelayMinutes = 30
        )
    }

    fun stopBackgroundScan() {
        Log.d(TAG, "停止后台扫描任务")
        backgroundScanManager.cancelBackgroundScan()
    }

    override fun onCleared() {
        super.onCleared()
        Log.d(TAG, "SongListViewModel 已清理")
        stopBackgroundScan()
    }
}
