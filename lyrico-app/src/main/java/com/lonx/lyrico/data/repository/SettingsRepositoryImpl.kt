package com.lonx.lyrico.data.repository

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.lonx.lyrico.data.model.LyricDisplayMode
import com.lonx.lyrico.viewmodel.SortBy
import com.lonx.lyrico.viewmodel.SortInfo
import com.lonx.lyrico.viewmodel.SortOrder
import com.lonx.lyrics.model.Source
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map


private val Context.settingsDataStore by preferencesDataStore(name = "settings")

class SettingsRepositoryImpl(private val context: Context) : SettingsRepository {

    private object PreferencesKeys {
        val LYRIC_DISPLAY_MODE = stringPreferencesKey("lyric_display_mode")
        val LAST_SCAN_TIME = longPreferencesKey("last_scan_time")
        val SORT_BY = stringPreferencesKey("sort_by")
        val SORT_ORDER = stringPreferencesKey("sort_order")
        val SEPARATOR = stringPreferencesKey("separator")
        val ROMA_ENABLED = booleanPreferencesKey("roma_enabled")
        val SEARCH_SOURCE_ORDER = stringPreferencesKey("search_source_order")
    }

    // 默认搜索源顺序
    private val defaultSourceOrder = listOf(Source.QM, Source.KG, Source.NE)

    override val lyricDisplayMode: Flow<LyricDisplayMode>
        get() = context.settingsDataStore.data.map { preferences ->
            LyricDisplayMode.valueOf(
                preferences[PreferencesKeys.LYRIC_DISPLAY_MODE]
                    ?: LyricDisplayMode.LINE_BY_LINE.name
            )
        }

    override val sortInfo: Flow<SortInfo>
        get() = context.settingsDataStore.data.map { preferences ->
            val sortBy = SortBy.valueOf(
                preferences[PreferencesKeys.SORT_BY] ?: SortBy.TITLE.name
            )
            val sortOrder = SortOrder.valueOf(
                preferences[PreferencesKeys.SORT_ORDER]
                    ?: SortOrder.ASC.name
            )
            SortInfo(sortBy, sortOrder)
        }

    override val separator: Flow<String>
        get() = context.settingsDataStore.data.map { preferences ->
            preferences[PreferencesKeys.SEPARATOR] ?: "/"
        }

    override val romaEnabled: Flow<Boolean>
        get() = context.settingsDataStore.data.map { preferences ->
            preferences[PreferencesKeys.ROMA_ENABLED] ?: true
        }

    override val searchSourceOrder: Flow<List<Source>>
        get() = context.settingsDataStore.data.map { preferences ->
            val orderString = preferences[PreferencesKeys.SEARCH_SOURCE_ORDER]
            if (orderString.isNullOrBlank()) {
                defaultSourceOrder
            } else {
                try {
                    orderString.split(",").mapNotNull { name ->
                        try {
                            Source.valueOf(name.trim())
                        } catch (e: IllegalArgumentException) {
                            null
                        }
                    }.ifEmpty { defaultSourceOrder }
                } catch (e: Exception) {
                    defaultSourceOrder
                }
            }
        }

    override suspend fun getLastScanTime(): Long {
        return context.settingsDataStore.data.map { preferences ->
            preferences[PreferencesKeys.LAST_SCAN_TIME] ?: 0L
        }.first()
    }

    override suspend fun saveLyricDisplayMode(mode: LyricDisplayMode) {
        context.settingsDataStore.edit { preferences ->
            preferences[PreferencesKeys.LYRIC_DISPLAY_MODE] = mode.name
        }
    }

    override suspend fun saveSortInfo(sortInfo: SortInfo) {
        context.settingsDataStore.edit { preferences ->
            preferences[PreferencesKeys.SORT_BY] = sortInfo.sortBy.name
            preferences[PreferencesKeys.SORT_ORDER] = sortInfo.order.name
        }
    }

    override suspend fun saveSeparator(separator: String) {
        context.settingsDataStore.edit { preferences ->
            preferences[PreferencesKeys.SEPARATOR] = separator
        }
    }

    override suspend fun saveRomaEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[PreferencesKeys.ROMA_ENABLED] = enabled
        }
    }

    override suspend fun saveLastScanTime(time: Long) {
        context.settingsDataStore.edit { preferences ->
            preferences[PreferencesKeys.LAST_SCAN_TIME] = time
        }
    }

    override suspend fun saveSearchSourceOrder(sources: List<Source>) {
        context.settingsDataStore.edit { preferences ->
            preferences[PreferencesKeys.SEARCH_SOURCE_ORDER] = sources.joinToString(",") { it.name }
        }
    }
}

