package com.lonx.lyrico.screens

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lonx.lyrico.data.model.LyricsSearchResult
import com.lonx.lyrico.ui.components.bar.SearchBar
import com.lonx.lyrico.viewmodel.SearchViewModel
import com.lonx.lyrics.model.SongSearchResult
import com.moriafly.salt.ui.ItemDivider
import com.moriafly.salt.ui.SaltTheme
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.result.ResultBackNavigator
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Destination<RootGraph>(route = "search_results")
fun SearchResultsScreen(
    keyword: String?,
    resultNavigator: ResultBackNavigator<LyricsSearchResult>
) {
    val viewModel: SearchViewModel = koinViewModel()
    val uiState by viewModel.uiState.collectAsState()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(keyword) {
        if (keyword != null) {
            viewModel.search(keyword)
        }
    }

    Scaffold(
        modifier = Modifier.background(SaltTheme.colors.background),
        topBar = {
            Row(
                modifier = Modifier
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .fillMaxWidth()
                    .background(SaltTheme.colors.background)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {

                SearchBar(
                    value = uiState.searchKeyword,
                    onValueChange = { viewModel.onKeywordChanged(it) },
                    placeholder = "搜索歌词...",
                    modifier = Modifier.weight(1f)
                )

                Spacer(modifier = Modifier.width(12.dp))

                // “搜索”按钮
                Text(
                    text = "搜索",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = SaltTheme.colors.highlight,
                    modifier = Modifier.clickable {
                        viewModel.search()
                        keyboardController?.hide()
                    }
                )
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(SaltTheme.colors.background)
        ){
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            )
            {
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp, top = 4.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(uiState.availableSources) { source ->
                        val isSelected = source == uiState.selectedSearchSource
                        FilterChip(
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = SaltTheme.colors.subBackground,
                                selectedContainerColor = SaltTheme.colors.highlight.copy(alpha = 0.1f), // 适配主题色
                                labelColor = SaltTheme.colors.text,
                                selectedLabelColor = SaltTheme.colors.highlight
                            ),
                            selected = isSelected,
                            onClick = { viewModel.switchSource(source) },
                            label = {
                                Text(
                                    source.name,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            leadingIcon = {
                                if (isSelected) {
                                    Icon(
                                        Icons.Default.Check,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                        tint = SaltTheme.colors.highlight
                                    )
                                }
                            },
                            border = null
                        )
                    }
                }

                HorizontalDivider(
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                )

                // 3. 结果列表
                Box(modifier = Modifier.fillMaxSize()) {
                    if (uiState.isSearching) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(32.dp),
                                color = SaltTheme.colors.highlight
                            )
                        }
                    } else if (uiState.searchError != null) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = uiState.searchError!!,
                                color = MaterialTheme.colorScheme.error,
                                fontSize = 14.sp
                            )
                        }
                    } else if (uiState.searchResults.isEmpty()) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                Icons.Default.SearchOff,
                                contentDescription = "No results",
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("未找到相关结果", color = MaterialTheme.colorScheme.outline)
                        }
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(bottom = 16.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(uiState.searchResults) { result ->
                                SearchResultItem(
                                    song = result,
                                    onPreviewClick = { viewModel.fetchLyricsForPreview(result) },
                                    onApplyClick = {
                                        viewModel.fetchLyricsDirectly(result) { lyrics ->
                                            Log.d("Lyrics", "Lyrics: $lyrics")
                                            if (lyrics != null) {
                                                resultNavigator.navigateBack(
                                                    LyricsSearchResult(
                                                        title = result.title,
                                                        artist = result.artist,
                                                        album = result.album,
                                                        lyrics = lyrics,
                                                        date = result.date,

                                                        )
                                                )
                                            }
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (uiState.previewingSong != null) {
        ModalBottomSheet(
            onDismissRequest = { viewModel.clearPreview() },
            sheetState = sheetState,
            containerColor = SaltTheme.colors.background, // 适配主题背景
            tonalElevation = 0.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(uiState.previewingSong!!.title, style = MaterialTheme.typography.titleMedium, color = SaltTheme.colors.text)
                Text(
                    uiState.previewingSong!!.artist,
                    style = MaterialTheme.typography.bodyMedium,
                    color = SaltTheme.colors.subText
                )
                Text(
                    uiState.previewingSong!!.album,
                    style = MaterialTheme.typography.bodyMedium,
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 300.dp)
                        .weight(1f, fill = false),
                    contentAlignment = Alignment.Center
                ) {
                    if (uiState.isPreviewLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(32.dp),
                            color = SaltTheme.colors.highlight
                        )
                    } else if (uiState.lyricsPreviewError != null) {
                        Text(
                            uiState.lyricsPreviewError!!,
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 14.sp
                        )
                    } else if (uiState.lyricsPreviewContent != null) {
                        val scrollState = rememberScrollState()
                        Text(
                            text = uiState.lyricsPreviewContent!!,
                            modifier = Modifier.verticalScroll(scrollState),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp,
                            lineHeight = 20.sp,
                            color = SaltTheme.colors.text.copy(alpha = 0.8f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = {
                        val song = uiState.previewingSong
                        val lyrics = uiState.lyricsPreviewContent
                        if (song != null && lyrics != null) {
                            resultNavigator.navigateBack(
                                LyricsSearchResult(
                                    title = song.title,
                                    artist = song.artist,
                                    album = song.album,
                                    lyrics = lyrics,
                                    date = song.date
                                )
                            )
                        }
                        viewModel.clearPreview()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = uiState.lyricsPreviewContent != null,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = SaltTheme.colors.highlight
                    )
                ) {
                    Text("使用此歌词", color = Color.White)
                }
            }
        }
    }
}

@Composable
fun SearchResultItem(
    song: SongSearchResult,
    onPreviewClick: () -> Unit,
    onApplyClick: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = song.title,
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp),
                    fontWeight = FontWeight.Medium,
                    color = SaltTheme.colors.text, // 使用 SaltTheme
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = song.artist,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                    color = SaltTheme.colors.subText, // 使用 SaltTheme
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (song.album.isNotBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = song.album,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                        color = SaltTheme.colors.subText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (song.date.isNotBlank()){
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = song.date,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                        color = SaltTheme.colors.subText, // 使用 SaltTheme
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                TextButton(
                    onClick = onPreviewClick,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                    modifier = Modifier.height(36.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("预览", fontSize = 13.sp, color = SaltTheme.colors.highlight)
                }

                Button(
                    onClick = onApplyClick,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                    modifier = Modifier.height(36.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = SaltTheme.colors.highlight.copy(alpha = 0.1f),
                        contentColor = SaltTheme.colors.highlight
                    ),
                    elevation = ButtonDefaults.buttonElevation(0.dp)
                ) {
                    Text("应用", fontSize = 13.sp)
                }
            }
        }

        ItemDivider()
    }
}