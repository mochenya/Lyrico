package com.lonx.lyrico

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.lonx.lyrico.data.SongDataHolder
import com.lonx.lyrico.router.Screen
import com.lonx.lyrico.screens.EditMetadataScreen
import com.lonx.lyrico.screens.SearchResultsScreen
import com.lonx.lyrico.screens.SettingsScreen
import com.lonx.lyrico.screens.SongListScreen
import com.lonx.lyrics.model.SongSearchResult
import org.koin.androidx.compose.koinViewModel

@Composable
fun LyricoApp(launchDirectoryPicker: () -> Unit) {
    val navController = rememberNavController()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        val showSearch = remember { mutableStateOf(false) }
        val searchQuery = remember { mutableStateOf("") }
        NavHost(navController = navController, startDestination = Screen.SongList.route) {
            composable(Screen.SongList.route) {
                SongListScreen(
                    showSearch = showSearch,
                    onSongClick = { songInfo ->
                        SongDataHolder.selectedSongInfo = songInfo
                        navController.navigate(Screen.EditMetadata.route)
                    },
                    onSettingsClick = {
                        navController.navigate(Screen.Settings.route)
                    },
                    viewModel = koinViewModel()
                )
            }
            composable(Screen.EditMetadata.route) { backStackEntry ->
                val searchResult = backStackEntry.savedStateHandle.get<SongSearchResult>("selectedResult")
                val selectedLyrics = backStackEntry.savedStateHandle.get<String>("selectedLyrics")

                EditMetadataScreen(
                    searchResult = searchResult,
                    selectedLyrics = selectedLyrics,
                    onBackClick = { navController.popBackStack() },
                    onSearchClick = { keyword ->
                        navController.navigate(Screen.SearchResults.createRoute(keyword))
                    },
                    onSearchResult = {
                        backStackEntry.savedStateHandle.remove<SongSearchResult>("selectedResult")
                    },
                    onLyricsResult = {
                        backStackEntry.savedStateHandle.remove<String>("selectedLyrics")
                    },
                    onSaveSuccess = {
                        navController.popBackStack()
                    }
                )
            }
            composable(Screen.SearchResults.route) { backStackEntry ->
                val keyword = backStackEntry.arguments?.getString("keyword")
                SearchResultsScreen(
                    keyword = keyword,
                    onBackClick = { navController.popBackStack() },
                    onResultSelect = { result, lyrics ->
                        navController.previousBackStackEntry?.savedStateHandle?.set("selectedResult", result)
                        navController.previousBackStackEntry?.savedStateHandle?.set("selectedLyrics", lyrics)
                        navController.popBackStack()
                    }
                )
            }
            composable(Screen.Settings.route) {
                SettingsScreen(
                    onBackClick = { navController.popBackStack() },
                    onAddFolderClick = { launchDirectoryPicker() }
                )
            }
        }
    }
}
