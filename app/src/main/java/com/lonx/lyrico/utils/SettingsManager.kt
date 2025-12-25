package com.lonx.lyrico.utils

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.lonx.lyrico.data.model.LyricDisplayMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore by preferencesDataStore(name = "settings")

class SettingsManager(private val context: Context) {

    private object PreferencesKeys {
        val SCANNED_FOLDERS = stringSetPreferencesKey("scanned_folders")
        val LYRIC_DISPLAY_MODE = stringPreferencesKey("lyric_display_mode")
    }

    val scannedFolders: Flow<Set<String>> = context.dataStore.data
        .map { preferences ->
            preferences[PreferencesKeys.SCANNED_FOLDERS] ?: emptySet()
        }

    val lyricDisplayMode: Flow<LyricDisplayMode> = context.dataStore.data
        .map { preferences ->
            LyricDisplayMode.valueOf(
                preferences[PreferencesKeys.LYRIC_DISPLAY_MODE] ?: LyricDisplayMode.WORD_BY_WORD.name
            )
        }

    suspend fun saveScannedFolders(folders: Set<String>) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.SCANNED_FOLDERS] = folders
        }
    }

    suspend fun saveLyricDisplayMode(mode: LyricDisplayMode) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.LYRIC_DISPLAY_MODE] = mode.name
        }
    }
}
