package com.lonx.lyrico.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lonx.lyrico.data.LyricoDatabase
import com.lonx.lyrico.data.model.ArtistSeparator
import com.lonx.lyrico.data.model.FolderDao
import com.lonx.lyrico.data.model.FolderEntity
import com.lonx.lyrico.data.repository.SettingsRepository
import com.lonx.lyrico.data.model.LyricDisplayMode
import com.lonx.lyrics.model.Source
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.lonx.lyrico.data.model.toArtistSeparator
import com.lonx.lyrico.data.repository.SongRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class SettingsUiState(
    val lyricDisplayMode: LyricDisplayMode = LyricDisplayMode.WORD_BY_WORD,
    val separator: ArtistSeparator = ArtistSeparator.SLASH,
    val romaEnabled: Boolean = false,
    val folders: List<FolderEntity> = emptyList(),
    val searchSourceOrder: List<Source> = emptyList()
)

class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val songRepository: SongRepository,
    private val database: LyricoDatabase
) : ViewModel() {

    private val folderDao: FolderDao = database.folderDao()
    private val _uiState = MutableStateFlow(SettingsUiState())

    val uiState: StateFlow<SettingsUiState> = combine(
        settingsRepository.lyricDisplayMode,
        settingsRepository.romaEnabled,
        settingsRepository.separator,
        folderDao.getAllFolders(),
        settingsRepository.searchSourceOrder
    ) { lyricDisplayMode, romaEnabled, separator, folders, searchSourceOrder ->
        SettingsUiState(
            lyricDisplayMode = lyricDisplayMode,
            romaEnabled = romaEnabled,
            separator = separator.toArtistSeparator(),
            folders = folders,
            searchSourceOrder = searchSourceOrder
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SettingsUiState()
    )


    fun toggleFolderIgnore(folder: FolderEntity) {
        viewModelScope.launch {
            folderDao.setIgnored(folder.id, !folder.isIgnored)
        }
    }

    fun setLyricDisplayMode(mode: LyricDisplayMode) {
        viewModelScope.launch {
            settingsRepository.saveLyricDisplayMode(mode)
            _uiState.update {
                it.copy(lyricDisplayMode = mode)
            }
        }
    }

    fun setRomaEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.saveRomaEnabled(enabled)
            _uiState.update {
                it.copy(romaEnabled = enabled)
            }
        }
    }

    fun setSeparator(separator: ArtistSeparator) {
        viewModelScope.launch {
            settingsRepository.saveSeparator(separator.toText())
            _uiState.update {
                it.copy(separator = separator)
            }
        }
    }

    fun setSearchSourceOrder(sources: List<Source>) {
        viewModelScope.launch {
            settingsRepository.saveSearchSourceOrder(sources)
            _uiState.update {
                it.copy(searchSourceOrder = sources)
            }
        }
    }

    fun moveSourceUp(source: Source) {
        val currentOrder = _uiState.value.searchSourceOrder.toMutableList()
        val index = currentOrder.indexOf(source)
        if (index > 0) {
            currentOrder.removeAt(index)
            currentOrder.add(index - 1, source)
            setSearchSourceOrder(currentOrder)
        }
    }

    fun moveSourceDown(source: Source) {
        val currentOrder = _uiState.value.searchSourceOrder.toMutableList()
        val index = currentOrder.indexOf(source)
        if (index >= 0 && index < currentOrder.size - 1) {
            currentOrder.removeAt(index)
            currentOrder.add(index + 1, source)
            setSearchSourceOrder(currentOrder)
        }
    }
}

