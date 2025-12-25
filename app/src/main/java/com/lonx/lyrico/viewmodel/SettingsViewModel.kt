package com.lonx.lyrico.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lonx.lyrico.utils.SettingsManager
import com.lonx.lyrico.data.model.LyricDisplayMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUiState(
    val scannedFolders: List<String> = emptyList(),
    val lyricDisplayMode: LyricDisplayMode = LyricDisplayMode.WORD_BY_WORD
)

class SettingsViewModel(
    private val settingsManager: SettingsManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                settingsManager.scannedFolders,
                settingsManager.lyricDisplayMode
            ) { folders, mode ->
                _uiState.update {
                    it.copy(
                        scannedFolders = folders.toList(),
                        lyricDisplayMode = mode
                    )
                }
            }.collect {}
        }
    }

    fun addScannedFolder(folderPath: String) {
        viewModelScope.launch {
            val updatedFolders = _uiState.value.scannedFolders.toMutableSet()
            updatedFolders.add(folderPath)
            settingsManager.saveScannedFolders(updatedFolders)
        }
    }

    fun removeScannedFolder(folderPath: String) {
        viewModelScope.launch {
            val updatedFolders = _uiState.value.scannedFolders.toMutableSet()
            updatedFolders.remove(folderPath)
            settingsManager.saveScannedFolders(updatedFolders)
        }
    }

    fun setLyricDisplayMode(mode: LyricDisplayMode) {
        viewModelScope.launch {
            settingsManager.saveLyricDisplayMode(mode)
        }
    }
}
