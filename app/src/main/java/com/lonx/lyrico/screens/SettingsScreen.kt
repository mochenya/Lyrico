package com.lonx.lyrico.screens

import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import com.lonx.lyrico.data.model.LyricDisplayMode
import com.lonx.lyrico.viewmodel.SettingsViewModel
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBackClick: () -> Unit,
    onAddFolderClick: () -> Unit,
    viewModel: SettingsViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val scannedFolders = uiState.scannedFolders
    val lyricDisplayMode = uiState.lyricDisplayMode

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Scan Folders Section
            SettingSectionTitle("扫描文件夹")
            ListItem(
                headlineContent = { Text("添加文件夹") },
                leadingContent = { Icon(Icons.Filled.Add, contentDescription = null) },
                modifier = Modifier.clickable { onAddFolderClick() }
            )

            if (scannedFolders.isNotEmpty()) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                ) {
                    items(scannedFolders) { folder ->
                        FolderListItem(
                            folderPath = folder,
                            onRemoveClick = { viewModel.removeScannedFolder(it) }
                        )
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // Lyric Display Mode Section
            SettingSectionTitle("歌词模式")
            LyricDisplayModeItem(
                mode = LyricDisplayMode.WORD_BY_WORD,
                selectedMode = lyricDisplayMode,
                onClick = { viewModel.setLyricDisplayMode(it) }
            )
            LyricDisplayModeItem(
                mode = LyricDisplayMode.LINE_BY_LINE,
                selectedMode = lyricDisplayMode,
                onClick = { viewModel.setLyricDisplayMode(it) }
            )
        }
    }
}

@Composable
private fun SettingSectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        color = MaterialTheme.colorScheme.primary
    )
}

@Composable
fun FolderListItem(folderPath: String, onRemoveClick: (String) -> Unit) {
    val context = LocalContext.current
    val displayName = remember(folderPath) {
        try {
            val uri = Uri.parse(folderPath)
            DocumentFile.fromTreeUri(context, uri)?.name ?: folderPath
        } catch (e: Exception) {
            folderPath
        }
    }

    ListItem(
        headlineContent = { Text(displayName) },
        leadingContent = { Icon(Icons.Filled.Folder, contentDescription = null) },
        trailingContent = {
            IconButton(onClick = { onRemoveClick(folderPath) }) {
                Icon(Icons.Filled.Delete, contentDescription = "移除")
            }
        }
    )
}

@Composable
fun LyricDisplayModeItem(
    mode: LyricDisplayMode,
    selectedMode: LyricDisplayMode,
    onClick: (LyricDisplayMode) -> Unit
) {
    val title = when (mode) {
        LyricDisplayMode.WORD_BY_WORD -> "逐字歌词"
        LyricDisplayMode.LINE_BY_LINE -> "逐行歌词"
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick(mode) }
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = selectedMode == mode,
            onClick = { onClick(mode) }
        )
        Spacer(Modifier.width(8.dp))
        Text(title)
    }
}