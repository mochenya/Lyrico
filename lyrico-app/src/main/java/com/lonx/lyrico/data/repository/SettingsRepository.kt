package com.lonx.lyrico.data.repository

import com.lonx.lyrico.data.model.LyricDisplayMode
import com.lonx.lyrico.viewmodel.SortInfo
import com.lonx.lyrics.model.Source
import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    // Flow properties
    val lyricDisplayMode: Flow<LyricDisplayMode>
    val sortInfo: Flow<SortInfo>
    val separator: Flow<String>
    val romaEnabled: Flow<Boolean>
    val searchSourceOrder: Flow<List<Source>>
    
    // Suspend functions for operations that might block or are one-off
    suspend fun getLastScanTime(): Long
    
    // Save functions
    suspend fun saveLyricDisplayMode(mode: LyricDisplayMode)
    suspend fun saveSortInfo(sortInfo: SortInfo)
    suspend fun saveSeparator(separator: String)
    suspend fun saveRomaEnabled(enabled: Boolean)
    suspend fun saveLastScanTime(time: Long)
    suspend fun saveSearchSourceOrder(sources: List<Source>)
}
