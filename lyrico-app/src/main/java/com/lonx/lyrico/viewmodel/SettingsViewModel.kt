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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.lonx.lyrico.data.model.toArtistSeparator
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class SettingsUiState(
    val lyricDisplayMode: LyricDisplayMode = LyricDisplayMode.WORD_BY_WORD,
    val separator: ArtistSeparator = ArtistSeparator.SLASH,
    val romaEnabled: Boolean = false,
    val folders: List<FolderEntity> = emptyList(),
    val searchSourceOrder: List<Source> = emptyList(),
    val searchPageSize: Int = 20
)

class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val database: LyricoDatabase
) : ViewModel() {

    private val folderDao: FolderDao = database.folderDao()
    private val _uiState = MutableStateFlow(SettingsUiState())

    val uiState: StateFlow<SettingsUiState> = combine(
        settingsRepository.settingsFlow,
        folderDao.getAllFolders()
    ) { settings, folders ->
        SettingsUiState(
            lyricDisplayMode = settings.lyricDisplayMode,
            romaEnabled = settings.romaEnabled,
            separator = settings.separator.toArtistSeparator(),
            folders = folders,
            searchSourceOrder = settings.searchSourceOrder,
            searchPageSize = settings.searchPageSize
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
    fun setSearchPageSize(size: Int) {
        viewModelScope.launch {
            settingsRepository.saveSearchPageSize(size)
            _uiState.update {
                it.copy(searchPageSize = size)
            }
        }
    }

}

