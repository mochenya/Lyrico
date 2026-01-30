package com.lonx.lyrico.viewmodel

import android.app.Application
import android.os.Handler
import android.os.Looper
import android.os.Parcelable
import android.provider.MediaStore
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lonx.audiotag.model.AudioTagData
import com.lonx.lyrico.data.model.SongEntity
import com.lonx.lyrico.data.repository.SongRepository
import com.lonx.lyrico.utils.MusicContentObserver
import com.lonx.lyrico.utils.SettingsManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.parcelize.Parcelize

@Parcelize
data class SongInfo(
    val filePath: String,
    val tagData: AudioTagData?
): Parcelable

data class SongListUiState(
    val isLoading: Boolean = false,
    val lastScanTime: Long = 0,
    val selectedSongs: SongEntity? = null,
    val loadingMessage: String? = null
)

@OptIn(FlowPreview::class)
class SongListViewModel(
    private val songRepository: SongRepository,
    private val settingsManager: SettingsManager,
    application: Application
) : ViewModel() {

    private val TAG = "SongListViewModel"
    private val _uiState = MutableStateFlow(SongListUiState())
    val uiState: StateFlow<SongListUiState> = _uiState.asStateFlow()

    private val _sortInfo = MutableStateFlow(SortInfo())
    val sortInfo: StateFlow<SortInfo> = _sortInfo.asStateFlow()

    private val _allSongs = MutableStateFlow<List<SongEntity>>(emptyList())

    private val contentResolver = application.contentResolver
    private var musicContentObserver: MusicContentObserver? = null
    private val scanRequest = MutableSharedFlow<Unit>(replay = 0)
    // 存储被选中的歌曲 ID (filePath 是唯一的 key)
    private val _selectedSongPaths = MutableStateFlow<Set<String>>(emptySet())
    val selectedSongPaths = _selectedSongPaths.asStateFlow()

    private val _isSelectionMode = MutableStateFlow(false)
    val isSelectionMode = _isSelectionMode.asStateFlow()
    @OptIn(ExperimentalCoroutinesApi::class)
    val songs: StateFlow<List<SongEntity>> =
        sortInfo.flatMapLatest { sort ->
            songRepository.getAllSongsSorted(sort.sortBy, sort.order)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )


    init {
        Log.d(TAG, "SongListViewModel 初始化")

        viewModelScope.launch {
            settingsManager.getSortInfo().collect { savedSortInfo ->
                _sortInfo.value = savedSortInfo
            }
        }

        viewModelScope.launch {
            songRepository.getAllSongs().collect { songList ->
                _allSongs.value = songList
            }
        }
        registerMusicObserver()

        viewModelScope.launch {
            scanRequest
                .debounce(2000L) // 2秒防抖
                .collect {
                    Log.d(TAG, "防抖后触发自动同步")
                    triggerSync(isAuto = true)
                }
        }
    }
    fun enterSelectionMode(firstPath: String? = null) {
        _isSelectionMode.value = true
        firstPath?.let {
            _selectedSongPaths.value = setOf(it)
        }
    }
    fun toggleSelection(path: String) {
        // 如果还没进入多选模式，点击时自动进入
        if (!_isSelectionMode.value) {
            _isSelectionMode.value = true
        }

        val current = _selectedSongPaths.value
        if (current.contains(path)) {
            _selectedSongPaths.value = current - path
        } else {
            _selectedSongPaths.value = current + path
        }
    }

    // 全选
    fun selectAll(songs: List<SongEntity>) {
        _selectedSongPaths.value = songs.map { it.filePath }.toSet()
    }

    // 退出多选模式
    fun exitSelectionMode() {
        _isSelectionMode.value = false      // 显式关闭模式
        _selectedSongPaths.value = emptySet() // 清空选择
    }
    fun selectedSong(song: SongEntity) {
        _uiState.update { it.copy(selectedSongs = song) }
    }
    fun clearSelectedSong(){
        _uiState.update { it.copy(selectedSongs = null) }
    }
    private fun registerMusicObserver() {
        musicContentObserver = MusicContentObserver(viewModelScope, Handler(Looper.getMainLooper())) {
            Log.d(TAG, "MediaStore 变更, 请求自动同步")
            viewModelScope.launch {
                scanRequest.emit(Unit)
            }
        }
        contentResolver.registerContentObserver(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            true,
            musicContentObserver!!
        )
        Log.d(TAG, "MusicContentObserver registered.")
    }

    fun onSortChange(newSortInfo: SortInfo) {
        _sortInfo.value = newSortInfo
        viewModelScope.launch {
            settingsManager.saveSortInfo(newSortInfo)
        }
    }

    fun initialScanIfEmpty() {
        viewModelScope.launch {
            if (songRepository.getSongsCount() == 0) {
                Log.d(TAG, "数据库为空，触发首次扫描")
                triggerSync(isAuto = false)
            }
        }
    }

    private fun triggerSync(isAuto: Boolean) {
        viewModelScope.launch {
            val message = if (isAuto) "检测到文件变化，正在更新..." else "正在扫描歌曲..."
            _uiState.update { it.copy(isLoading = true, loadingMessage = message) }
            try {
                songRepository.synchronizeWithDevice()
            } catch (e: Exception) {
                Log.e(TAG, "同步失败", e)
                _uiState.update { it.copy(isLoading = false, loadingMessage = "同步失败: ${e.message}") }
            }
            // Add a small delay to prevent the loading indicator from disappearing too quickly
            delay(500L)
            _uiState.update { it.copy(isLoading = false) }
        }
    }

    fun refreshSongs() {
        if (_uiState.value.isLoading) return
        Log.d(TAG, "用户手动刷新歌曲列表")
        triggerSync(isAuto = false)
    }

    override fun onCleared() {
        super.onCleared()
        musicContentObserver?.let {
            contentResolver.unregisterContentObserver(it)
            Log.d(TAG, "MusicContentObserver unregistered.")
        }
        Log.d(TAG, "SongListViewModel 已清理")
    }
}
