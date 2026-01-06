package com.lonx.lyrico.utils

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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

val Context.dataStore by preferencesDataStore(name = "settings")

class SettingsManager(private val context: Context) {

    private object PreferencesKeys {

        // 歌词显示模式（逐字、逐行）
        val LYRIC_DISPLAY_MODE = stringPreferencesKey("lyric_display_mode")

        // 最后一次扫描时间
        val LAST_SCAN_TIME = longPreferencesKey("last_scan_time")

        // 排序字段（标题、艺术家、修改时间）
        val SORT_BY = stringPreferencesKey("sort_by")

        // 排序方式（升序、降序）
        val SORT_ORDER = stringPreferencesKey("sort_order")

        // 艺术家分隔符
        val SEPARATOR = stringPreferencesKey("separator")

        // 歌词是否包含罗马音
        val ROMA_ENABLED = booleanPreferencesKey("roma_enabled")
    }


    suspend fun saveLyricDisplayMode(mode: LyricDisplayMode) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.LYRIC_DISPLAY_MODE] = mode.name
        }
    }
    fun getLyricDisplayMode(): Flow<LyricDisplayMode> {
        return context.dataStore.data.map { preferences ->
            LyricDisplayMode.valueOf(
                preferences[PreferencesKeys.LYRIC_DISPLAY_MODE]
                    ?: LyricDisplayMode.LINE_BY_LINE.name
            )
        }
    }

    suspend fun saveSortInfo(sortInfo: SortInfo) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.SORT_BY] = sortInfo.sortBy.name
            preferences[PreferencesKeys.SORT_ORDER] = sortInfo.order.name
        }
    }

    suspend fun saveSeparator(separator: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.SEPARATOR] = separator
        }
    }

    suspend fun saveRomaEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.ROMA_ENABLED] = enabled
        }
    }

    fun getRomaEnabled(): Flow<Boolean> {
        return context.dataStore.data.map { preferences ->
            preferences[PreferencesKeys.ROMA_ENABLED] ?: true
        }
    }

    fun getSeparator(): Flow<String> {
        return context.dataStore.data.map { preferences ->
            preferences[PreferencesKeys.SEPARATOR] ?: "/"
        }
    }

    fun getSortInfo(): Flow<SortInfo> {
        return context.dataStore.data.map { preferences ->
            val sortBy = SortBy.valueOf(
                preferences[PreferencesKeys.SORT_BY] ?: SortBy.TITLE.name
            )
            val sortOrder = SortOrder.valueOf(
                preferences[PreferencesKeys.SORT_ORDER]
                    ?: SortOrder.ASC.name
            )
            SortInfo(sortBy, sortOrder)
        }
    }

    suspend fun saveLastScanTime(time: Long) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.LAST_SCAN_TIME] = time
        }
    }

    suspend fun getLastScanTime(): Long {
        return context.dataStore.data.map { preferences ->
            preferences[PreferencesKeys.LAST_SCAN_TIME] ?: 0L
        }.first()
    }
}
