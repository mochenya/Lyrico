package com.lonx.lyrico.router

sealed class Screen(val route: String) {
    object SongList : Screen("songList")
    object EditMetadata : Screen("editMetadata")
    object SearchResults : Screen("searchResults?keyword={keyword}") {
        fun createRoute(keyword: String?) = "searchResults?keyword=$keyword"
    }
    object Settings : Screen("settings")
}