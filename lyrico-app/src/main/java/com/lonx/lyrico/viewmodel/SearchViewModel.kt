package com.lonx.lyrico.viewmodel

import android.annotation.SuppressLint
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lonx.lyrico.utils.SettingsManager
import com.lonx.lyrics.model.LyricsResult
import com.lonx.lyrics.model.LyricsLine
import com.lonx.lyrics.model.SongSearchResult
import com.lonx.lyrics.model.Source
import com.lonx.lyrics.source.kg.KgSource
import com.lonx.lyrics.source.qm.QmSource
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.abs

data class SearchUiState(
    val searchKeyword: String = "",
    val searchResults: List<SongSearchResult> = emptyList(),
    val selectedSearchSource: Source = Source.KG,
    val availableSources: List<Source> = listOf(Source.KG, Source.QM),
    val isSearching: Boolean = false,
    val searchError: String? = null,
    // 歌词预览相关
    val previewingSong: SongSearchResult? = null,
    val lyricsPreviewContent: String? = null,
    val isPreviewLoading: Boolean = false,
    val lyricsPreviewError: String? = null,
)

class SearchViewModel(
    private val kgSource: KgSource,
    private val qmSource: QmSource,
    private val settingsManager: SettingsManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    // 缓存搜索结果：Keyword -> (Source -> Results)
    private val searchResultCache =
        mutableMapOf<String, MutableMap<Source, List<SongSearchResult>>>()

    // 用于管理当前的搜索任务和歌词任务，防止并发冲突
    private var searchJob: Job? = null
    private var lyricsJob: Job? = null

    /**
     * 当输入框文字改变时调用
     * 注意：这里只更新状态，不直接触发网络搜索（避免打字时频繁请求）
     */
    fun onKeywordChanged(keyword: String) {
        _uiState.update { it.copy(searchKeyword = keyword) }
    }

    /**
     * 切换搜索源
     * 如果当前有关键词，会尝试从缓存读取或重新发起搜索
     */
    fun switchSource(source: Source) {
        val currentKeyword = _uiState.value.searchKeyword

        // 更新选中的源
        _uiState.update { it.copy(selectedSearchSource = source) }

        if (currentKeyword.isBlank()) {
            _uiState.update { it.copy(searchResults = emptyList()) }
            return
        }

        val cachedResults = getCachedResults(currentKeyword, source)
        if (cachedResults != null) {
            // 命中缓存，直接显示
            _uiState.update { it.copy(searchResults = cachedResults, searchError = null) }
        } else {
            // 未命中缓存，发起搜索
            search()
        }
    }

    /**
     * 执行搜索
     * @param keywordToSearch 可选参数，如果传入则更新状态并搜索，否则使用当前状态的关键词
     */
    fun search(keywordToSearch: String? = null) {
        val keyword = keywordToSearch ?: _uiState.value.searchKeyword

        // 如果传入了新关键词，先更新 UI
        if (keywordToSearch != null) {
            _uiState.update { it.copy(searchKeyword = keyword) }
        }

        if (keyword.isBlank()) {
            _uiState.update { it.copy(searchResults = emptyList(), isSearching = false) }
            return
        }

        // 1. 取消上一次正在进行的搜索（防止快速点击导致结果错乱）
        searchJob?.cancel()

        // 2. 开启新协程
        searchJob = viewModelScope.launch {
            _uiState.update { it.copy(isSearching = true, searchError = null) }

            try {
                // 再次检查缓存（防止在等待协程启动时状态变化，虽然在 switchSource 处理了，但双重保障无害）
                val currentSource = _uiState.value.selectedSearchSource
                val cached = getCachedResults(keyword, currentSource)
                if (cached != null) {
                    _uiState.update { it.copy(searchResults = cached, isSearching = false) }
                    return@launch
                }

                // 执行网络请求
                val separator = settingsManager.getSeparator().first()
                val results = when (currentSource) {
                    Source.KG -> kgSource.search(keyword, separator = separator)
                    Source.QM -> qmSource.search(keyword, separator = separator)
                    else -> emptyList()
                }

                // 写入缓存
                cacheSearchResults(keyword, currentSource, results)

                // 更新 UI
                _uiState.update { it.copy(searchResults = results, isSearching = false) }

            } catch (e: Exception) {
                // 如果是协程被取消（cancel），不应该视为错误显示给用户
                if (e is kotlinx.coroutines.CancellationException) throw e

                _uiState.update {
                    it.copy(
                        searchError = "搜索失败: ${e.message}",
                        isSearching = false
                    )
                }
            }
        }
    }

    // --- 缓存逻辑 ---
    private fun cacheSearchResults(
        keyword: String,
        source: Source,
        results: List<SongSearchResult>
    ) {
        val keywordCache = searchResultCache.getOrPut(keyword) { mutableMapOf() }
        keywordCache[source] = results
    }

    private fun getCachedResults(keyword: String, source: Source): List<SongSearchResult>? {
        return searchResultCache[keyword]?.get(source)
    }

    // --- 歌词预览逻辑 ---
    fun fetchLyricsForPreview(song: SongSearchResult) {
        // 取消上一次的歌词加载（防止用户快速点击不同歌曲）
        lyricsJob?.cancel()

        lyricsJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    previewingSong = song,
                    isPreviewLoading = true,
                    lyricsPreviewContent = null,
                    lyricsPreviewError = null
                )
            }
            try {
                val lyricsResult: LyricsResult? = when (song.source) {
                    Source.KG -> kgSource.getLyrics(song)
                    Source.QM -> qmSource.getLyrics(song)
                    else -> null
                }

                val romaEnabled = settingsManager.getRomaEnabled().first()
                val lyricsText = lyricsResult?.let { formatLrcResult(result = it, romaEnabled = romaEnabled) }

                _uiState.update {
                    it.copy(
                        lyricsPreviewContent = lyricsText ?: "暂无歌词",
                        isPreviewLoading = false
                    )
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                _uiState.update {
                    it.copy(
                        lyricsPreviewError = "加载失败: ${e.message}",
                        isPreviewLoading = false
                    )
                }
            }
        }
    }

    fun fetchLyricsDirectly(song: SongSearchResult, onResult: (String?) -> Unit) {
        viewModelScope.launch {
            try {
                val lyricsResult: LyricsResult? = when (song.source) {
                    Source.KG -> kgSource.getLyrics(song)
                    Source.QM -> qmSource.getLyrics(song)
                    else -> null
                }

                val romaEnabled = settingsManager.getRomaEnabled().first()
                val lyricsText = lyricsResult?.let { formatLrcResult(result = it, romaEnabled = romaEnabled) }
                onResult(lyricsText)
            } catch (e: Exception) {
                onResult(null)
            }
        }
    }

    fun clearPreview() {
        // 关闭预览时也可以取消正在进行的加载任务
        lyricsJob?.cancel()
        _uiState.update {
            it.copy(
                previewingSong = null,
                lyricsPreviewContent = null,
                isPreviewLoading = false,
                lyricsPreviewError = null
            )
        }
    }
}

// --- 辅助函数保持不变 ---

@SuppressLint("DefaultLocale")
private fun formatTimestamp(millis: Long): String {
    val totalSeconds = millis / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    val ms = millis % 1000
    return String.format("%02d:%02d.%03d", minutes, seconds, ms)
}

private fun formatLrcResult(result: LyricsResult, romaEnabled: Boolean = false): String {
    val builder = StringBuilder()
    val originalLines = result.original
    val translatedLines = result.translated
    val translatedMap = translatedLines?.associateBy { it.start } ?: emptyMap()

    originalLines.forEach { originalLine ->
        val formattedOriginalLine = originalLine.words.joinToString("") { word ->
            "[${formatTimestamp(word.start)}]${word.text}"
        }
        builder.append(formattedOriginalLine)
        builder.append("\n")

        val matchedTranslation = findMatchingTranslatedLine(originalLine, translatedMap)

        if (matchedTranslation != null) {
            val formattedTranslatedLine = "[${formatTimestamp(matchedTranslation.start)}]${
                matchedTranslation.words.joinToString(" ") { it.text }
            }"
            builder.append(formattedTranslatedLine)
            builder.append("\n")
        }
        if (romaEnabled) {
            val romanizationLines = result.romanization
            val romanizationMap = romanizationLines?.associateBy { it.start } ?: emptyMap()
            val matchedRomanization = findMatchingTranslatedLine(originalLine, romanizationMap)
            if (matchedRomanization != null) {
                val formattedRomanizationLine = "[${formatTimestamp(matchedRomanization.start)}]${
                    matchedRomanization.words.joinToString(" ") { it.text }
                }"
                builder.append(formattedRomanizationLine)
                builder.append("\n")
            }
        }
    }
    return builder.toString().trim()
}

private fun findMatchingTranslatedLine(
    originalLine: LyricsLine,
    translatedMap: Map<Long, LyricsLine>
): LyricsLine? {
    val matched = translatedMap[originalLine.start]
    if (matched != null) return matched
    return translatedMap.entries.find { abs(it.key - originalLine.start) < 500 }?.value
}