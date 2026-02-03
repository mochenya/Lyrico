package com.lonx.lyrico.viewmodel

import android.app.Application
import android.os.Handler
import android.os.Looper
import android.os.Parcelable
import android.provider.MediaStore
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lonx.audiotag.model.AudioTagData
import com.lonx.lyrico.data.model.SongEntity
import com.lonx.lyrico.data.repository.SettingsRepository
import com.lonx.lyrico.data.repository.SongRepository
import com.lonx.lyrico.utils.LyricsUtils
import com.lonx.lyrico.utils.MusicContentObserver
import com.lonx.lyrics.model.SearchSource
import com.lonx.lyrics.model.Source
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.parcelize.Parcelize
import kotlinx.coroutines.Job
import java.io.File
import kotlin.math.abs

@Parcelize
data class SongInfo(
    val filePath: String,
    val tagData: AudioTagData?
): Parcelable

data class SongListUiState(
    val isLoading: Boolean = false,
    val lastScanTime: Long = 0,
    val selectedSongs: SongEntity? = null,
    val isBatchMatching: Boolean = false,
    val batchProgress: Pair<Int, Int>? = null, // (当前第几首, 总共几首)
    val successCount: Int = 0,
    val failureCount: Int = 0,
    val currentFile: String = "",
    val loadingMessage: String = ""
)

@OptIn(FlowPreview::class)
class SongListViewModel(
    private val songRepository: SongRepository,
    private val settingsRepository: SettingsRepository,
    private val sources: List<SearchSource>,
    application: Application
) : ViewModel()
{

    private val TAG = "SongListViewModel"
    private val _uiState = MutableStateFlow(SongListUiState())
    val uiState: StateFlow<SongListUiState> = _uiState.asStateFlow()

    private val _sortInfo = MutableStateFlow(SortInfo())
    val sortInfo: StateFlow<SortInfo> = _sortInfo.asStateFlow()

    private val _allSongs = MutableStateFlow<List<SongEntity>>(emptyList())

    private val contentResolver = application.contentResolver
    private var musicContentObserver: MusicContentObserver? = null
    private val scanRequest = MutableSharedFlow<Unit>(replay = 0)
    // 存储被选中的歌曲 ID (filePath 是唯一的 key)
    private val _selectedSongIds = MutableStateFlow<Set<Long>>(emptySet())
    val selectedSongIds = _selectedSongIds.asStateFlow()

    private val _isSelectionMode = MutableStateFlow(false)
    val isSelectionMode = _isSelectionMode.asStateFlow()

    private var batchMatchJob: Job? = null

    // 搜索源顺序，从设置中读取
    private val _searchSourceOrder = MutableStateFlow(listOf(Source.KG, Source.QM, Source.NE))
    @OptIn(ExperimentalCoroutinesApi::class)
    val songs: StateFlow<List<SongEntity>> =
        sortInfo.flatMapLatest { sort ->
            songRepository.getAllSongsSorted(sort.sortBy, sort.order)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )


    init {
        Log.d(TAG, "SongListViewModel 初始化")

        viewModelScope.launch {
            settingsRepository.sortInfo.collect { savedSortInfo ->
                _sortInfo.value = savedSortInfo
            }
        }

        viewModelScope.launch {
            songRepository.getAllSongs().collect { songList ->
                _allSongs.value = songList
            }
        }

        viewModelScope.launch {
            settingsRepository.searchSourceOrder.collect { order ->
                _searchSourceOrder.value = order
                Log.d(TAG, "搜索源顺序已更新: $order")
            }
        }

        registerMusicObserver()

        viewModelScope.launch {
            scanRequest
                .debounce(2000L) // 2秒防抖
                .collect {
                    if (_uiState.value.isBatchMatching) {
                        Log.d(TAG, "正在批量匹配中，忽略自动同步请求")
                        return@collect
                    }
                    Log.d(TAG, "防抖后触发自动同步")
                    triggerSync(isAuto = true)
                }
        }
    }

    /**
     * 批量匹配歌曲，初步实现，待完善匹配逻辑和数据库同步逻辑
     *
     */
    fun batchMatchLyrics() {
        val selectedPaths = _selectedSongIds.value
        if (selectedPaths.isEmpty()) return

        batchMatchJob = viewModelScope.launch {
            _uiState.update { 
                it.copy(
                    isBatchMatching = true, 
                    loadingMessage = "准备匹配...",
                    successCount = 0,
                    failureCount = 0,
                    batchProgress = null,
                    currentFile = ""
                ) 
            }

            val songsToMatch = _allSongs.value.filter { it.mediaId in selectedPaths }

            // 记录哪些文件确实修改成功了
            val successFiles = mutableListOf<String>()

            songsToMatch.forEachIndexed { index, song ->
                _uiState.update { it.copy(batchProgress = (index + 1) to songsToMatch.size, currentFile = song.fileName) }

                val isSuccess = matchSong(song)

                if (isSuccess) {
                    successFiles.add(song.filePath)
                    _uiState.update { it.copy(successCount = it.successCount + 1) }
                } else {
                    _uiState.update { it.copy(failureCount = it.failureCount + 1) }
                }
                delay(600) // 频率限制
            }

            if (successFiles.isNotEmpty()) {
                _uiState.update { it.copy(loadingMessage = "正在同步数据库...") }

                songRepository.synchronizeWithDevice(false)

            }

            _uiState.update { it.copy(isBatchMatching = false, loadingMessage = "匹配完成") }
        }
    }
    
    fun abortBatchMatch() {
        batchMatchJob?.cancel()
        batchMatchJob = null
        _uiState.update { it.copy(isBatchMatching = false, loadingMessage = "已中止") }
    }
    
    fun closeBatchMatchDialog() {
        _uiState.update { 
            it.copy(
                batchProgress = null,
                currentFile = "",
                loadingMessage = ""
            ) 
        }
        exitSelectionMode()
    }
    /**
     * 带相似度得分的搜索结果包装类
     */
    private data class ScoredSearchResult(
        val result: com.lonx.lyrics.model.SongSearchResult,
        val score: Double,
        val source: SearchSource
    )

    /**
     * 计算两个字符串的编辑距离（Levenshtein Distance）
     */
    private fun levenshteinDistance(s1: String, s2: String): Int {
        val m = s1.length
        val n = s2.length
        val dp = Array(m + 1) { IntArray(n + 1) }

        for (i in 0..m) dp[i][0] = i
        for (j in 0..n) dp[0][j] = j

        for (i in 1..m) {
            for (j in 1..n) {
                dp[i][j] = if (s1[i - 1].lowercaseChar() == s2[j - 1].lowercaseChar()) {
                    dp[i - 1][j - 1]
                } else {
                    minOf(dp[i - 1][j - 1], dp[i - 1][j], dp[i][j - 1]) + 1
                }
            }
        }
        return dp[m][n]
    }

    /**
     * 计算字符串相似度 (0.0 - 1.0)，基于编辑距离
     */
    private fun stringSimilarity(s1: String, s2: String): Double {
        if (s1.isEmpty() && s2.isEmpty()) return 1.0
        if (s1.isEmpty() || s2.isEmpty()) return 0.0
        
        val clean1 = normalizeString(s1)
        val clean2 = normalizeString(s2)
        
        if (clean1 == clean2) return 1.0
        
        // 编辑距离相似度
        val maxLen = maxOf(clean1.length, clean2.length)
        val editDist = levenshteinDistance(clean1, clean2)
        val editSimilarity = 1.0 - (editDist.toDouble() / maxLen)
        
        // 包含关系加分
        val containsBonus = when {
            clean1.contains(clean2) || clean2.contains(clean1) -> 0.15
            else -> 0.0
        }
        
        // 词汇重叠度（分词后计算）
        val words1 = clean1.split(Regex("[\\s\\-_,，、&]+")).filter { it.isNotBlank() }.toSet()
        val words2 = clean2.split(Regex("[\\s\\-_,，、&]+")).filter { it.isNotBlank() }.toSet()
        val wordOverlap = if (words1.isNotEmpty() && words2.isNotEmpty()) {
            val intersection = words1.intersect(words2).size.toDouble()
            val union = words1.union(words2).size.toDouble()
            intersection / union * 0.2
        } else 0.0
        
        return minOf(1.0, editSimilarity + containsBonus + wordOverlap)
    }

    /**
     * 标准化字符串：去除特殊字符、统一大小写、处理常见变体
     */
    private fun normalizeString(s: String): String {
        return s.lowercase()
            .replace(Regex("""[()（）\[\]【】《》<>「」『』"']"""), "")
            .replace(Regex("\\s+"), " ")
            .replace(Regex("feat\\.?|ft\\.?|featuring", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\s*-\\s*"), " ")
            .trim()
    }

    /**
     * 从文件名解析歌曲信息
     * 支持格式：
     * - "艺术家 - 标题.mp3"
     * - "标题 - 艺术家.mp3"
     * - "标题.mp3"
     */
    private fun parseFileName(fileName: String): Pair<String?, String?> {
        // 移除文件扩展名
        val nameWithoutExt = fileName.substringBeforeLast(".")
        
        // 清理常见的无用后缀
        val cleaned = nameWithoutExt
            .replace(Regex("\\(\\d+\\)$"), "") // 移除 (1) (2) 等
            .replace(Regex("\\[\\d+]$"), "") // 移除 [1] [2] 等
            .trim()
        
        // 尝试多种分隔符
        val separators = listOf(" - ", " – ", "－", "_-_", " _ ")
        for (sep in separators) {
            if (cleaned.contains(sep)) {
                val parts = cleaned.split(sep, limit = 2)
                if (parts.size == 2) {
                    val first = parts[0].trim()
                    val second = parts[1].trim()
                    // 启发式判断：通常艺术家名较短，标题较长
                    return if (first.length <= second.length) {
                        Pair(second, first) // first是艺术家，second是标题
                    } else {
                        Pair(first, second) // first是标题，second是艺术家
                    }
                }
            }
        }
        
        // 没有分隔符，整个作为标题
        return Pair(cleaned, null)
    }

    /**
     * 构建搜索查询字符串
     */
    private fun buildSearchQueries(song: SongEntity): List<String> {
        val queries = mutableListOf<String>()

        val title = song.title?.takeIf {
            it.isNotBlank() && !it.contains("未知", ignoreCase = true)
        }
        val artist = song.artist?.takeIf {
            it.isNotBlank() && !it.contains("未知", ignoreCase = true)
        }

        val metadataValid = !title.isNullOrBlank() && !artist.isNullOrBlank()

        if (metadataValid) {
            // 标题 + 艺术家
            queries.add("$title $artist")
        } else {
            // 只要有一个为空，统一使用文件名
            val (parsedTitle, parsedArtist) = parseFileName(song.fileName)

            if (!parsedTitle.isNullOrBlank()) {
                if (!parsedArtist.isNullOrBlank()) {
                    queries.add("$parsedTitle $parsedArtist")
                }
                queries.add(parsedTitle)
            }
        }

        return queries.distinct().take(3)
    }


    /**
     * 计算搜索结果与目标歌曲的综合相似度得分
     * 
     * 权重分配：
     * - 标题相似度: 40%
     * - 艺术家相似度: 30%
     * - 时长匹配度: 20%
     * - 专辑相似度: 10%
     */
    private fun calculateMatchScore(
        result: com.lonx.lyrics.model.SongSearchResult,
        song: SongEntity,
        queryTitle: String?,
        queryArtist: String?
    ): Double {
        // 标题相似度 (40%)
        val targetTitle = queryTitle ?: song.title ?: song.fileName.substringBeforeLast(".")
        val titleScore = stringSimilarity(targetTitle, result.title) * 0.40
        
        // 艺术家相似度 (30%)
        val targetArtist = queryArtist ?: song.artist ?: ""
        val artistScore = if (targetArtist.isNotBlank()) {
            stringSimilarity(targetArtist, result.artist) * 0.30
        } else {
            0.15 // 如果没有艺术家信息，给一个中性分数
        }
        
        // 时长匹配度 (20%)
        // 时长差异越小得分越高，超过30秒差异开始扣分
        val durationDiff = abs(result.duration - song.durationMilliseconds)
        val durationScore = when {
            durationDiff <= 1000 -> 0.20       // 1秒内完美匹配
            durationDiff <= 3000 -> 0.18       // 3秒内
            durationDiff <= 5000 -> 0.15       // 5秒内
            durationDiff <= 10000 -> 0.10      // 10秒内
            durationDiff <= 30000 -> 0.05      // 30秒内
            else -> 0.0                         // 超过30秒
        }
        
        // 专辑相似度 (10%)
        val targetAlbum = song.album?.takeIf { !it.contains("未知", true) } ?: ""
        val albumScore = if (targetAlbum.isNotBlank() && result.album.isNotBlank()) {
            stringSimilarity(targetAlbum, result.album) * 0.10
        } else {
            0.05 // 如果没有专辑信息，给一个中性分数
        }
        
        return titleScore + artistScore + durationScore + albumScore
    }

    /**
     * 匹配单个歌曲，优化版本
     * 
     * 优化点：
     * 1. 当标题或艺术家为空时，使用文件名解析进行搜索
     * 2. 支持多个搜索源的结果对比，选择相似度最高的
     * 3. 使用综合相似度算法（标题、艺术家、时长、专辑）
     * 4. 多策略搜索查询构建
     */
    private suspend fun matchSong(song: SongEntity): Boolean {
        val queries = buildSearchQueries(song)
        if (queries.isEmpty()) {
            Log.w(TAG, "无法为歌曲构建搜索查询: ${song.fileName}")
            return false
        }
        
        Log.d(TAG, "搜索查询策略: $queries")
        
        // 从文件名解析信息用于相似度计算
        val (parsedTitle, parsedArtist) = parseFileName(song.fileName)
        val queryTitle = song.title?.takeIf { it.isNotBlank() && !it.contains("未知", true) } ?: parsedTitle
        val queryArtist = song.artist?.takeIf { it.isNotBlank() && !it.contains("未知", true) } ?: parsedArtist
        
        // 收集所有源的搜索结果并计算相似度
        val allScoredResults = mutableListOf<ScoredSearchResult>()
        
        // 按用户设置的优先级顺序排列搜索源
        val sourceOrder = _searchSourceOrder.value
        val orderedSources = sources.sortedBy { source ->
            val index = sourceOrder.indexOf(source.sourceType)
            if (index >= 0) index else Int.MAX_VALUE
        }
        
        Log.d(TAG, "搜索源顺序: ${orderedSources.map { it.sourceType }}")
        
        for (query in queries) {
            for (source in orderedSources) {
                try {
                    val results = source.search(query, pageSize = 2)
                    Log.d(TAG, "搜索源 ${source.sourceType} 搜索结果: $results")
                    for (result in results) {
                        val score = calculateMatchScore(result, song, queryTitle, queryArtist)
                        allScoredResults.add(ScoredSearchResult(result, score, source))
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "搜索源 ${source.sourceType} 搜索失败: ${e.message}")
                }
            }
            
            // 如果已经找到高质量匹配（>0.7），可以提前结束
            if (allScoredResults.any { it.score > 0.7 }) {
                break
            }
        }
        
        if (allScoredResults.isEmpty()) {
            Log.w(TAG, "所有搜索源均未找到结果: ${song.fileName}")
            return false
        }
        
        // 按相似度排序，选择最佳匹配
        val bestMatch = allScoredResults.maxByOrNull { it.score }!!
        
        Log.d(TAG, "最佳匹配 [得分=${String.format("%.2f", bestMatch.score)}]: " +
                "${bestMatch.result.title} - ${bestMatch.result.artist} (${bestMatch.source.sourceType})")
        
        // 相似度阈值检查，避免错误匹配
        if (bestMatch.score < 0.3) {
            Log.w(TAG, "最佳匹配相似度过低 (${String.format("%.2f", bestMatch.score)}), 跳过: ${song.fileName}")
            return false
        }
        
        try {
            val lyricsResult = bestMatch.source.getLyrics(bestMatch.result)
            
            val tagData = AudioTagData(
                title = song.title?.takeIf { !it.contains("未知", true) } ?: bestMatch.result.title,
                artist = song.artist?.takeIf { !it.contains("未知", true) } ?: bestMatch.result.artist,
                album = song.album?.takeIf { !it.contains("未知", true) } ?: bestMatch.result.album,
                lyrics = lyricsResult?.let { LyricsUtils.formatLrcResult(it) },
                picUrl = bestMatch.result.picUrl,
                date = bestMatch.result.date,
                trackerNumber = bestMatch.result.trackerNumber
            )

            // 记录旧时间戳
            val oldTime = song.fileLastModified

            // 写入物理文件
            val ok = songRepository.writeAudioTagData(song.filePath, tagData)

            if (ok) {
                // 立即恢复时间戳，确保 synchronizeWithDevice 时
                restoreFileTimestamp(song.filePath, oldTime)
                return true
            }
        } catch (e: Exception) {
            Log.e("BatchMatch", "File write failed: ${song.title}", e)
        }
        
        return false
    }
    private fun restoreFileTimestamp(path: String, timestamp: Long) {
        try {
            val file = File(path)
            if (file.exists()) {
                file.setLastModified(timestamp)
            }
        } catch (e: Exception) {
            Log.e("BatchMatch", "恢复时间戳失败", e)
        }
    }

    fun toggleSelection(mediaId: Long) {
        // 如果还没进入多选模式，点击时自动进入
        if (!_isSelectionMode.value) {
            _isSelectionMode.value = true
        }

        val current = _selectedSongIds.value
        if (current.contains( mediaId)) {
            _selectedSongIds.value = current - mediaId
        } else {
            _selectedSongIds.value = current + mediaId
        }
    }

    // 全选
    fun selectAll(songs: List<SongEntity>) {
        _selectedSongIds.value = songs.map { it.mediaId }.toSet()
    }

    // 退出多选模式
    fun exitSelectionMode() {
        _isSelectionMode.value = false      // 显式关闭模式
        _selectedSongIds.value = emptySet() // 清空选择
    }
    fun selectedSong(song: SongEntity) {
        _uiState.update { it.copy(selectedSongs = song) }
    }
    fun clearSelectedSong(){
        _uiState.update { it.copy(selectedSongs = null) }
    }
    private fun registerMusicObserver() {
        musicContentObserver = MusicContentObserver(viewModelScope, Handler(Looper.getMainLooper())) {
            Log.d(TAG, "MediaStore 变更, 请求自动同步")
            viewModelScope.launch {
                scanRequest.emit(Unit)
            }
        }
        contentResolver.registerContentObserver(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            true,
            musicContentObserver!!
        )
        Log.d(TAG, "MusicContentObserver registered.")
    }

    fun onSortChange(newSortInfo: SortInfo) {
        _sortInfo.value = newSortInfo
        viewModelScope.launch {
            settingsRepository.saveSortInfo(newSortInfo)
        }
    }

    fun initialScanIfEmpty() {
        viewModelScope.launch {
            if (songRepository.getSongsCount() == 0) {
                Log.d(TAG, "数据库为空，触发首次扫描")
                triggerSync(isAuto = false)
            }
        }
    }

    private fun triggerSync(isAuto: Boolean) {
        viewModelScope.launch {
            val message = if (isAuto) "检测到文件变化，正在更新..." else "正在扫描歌曲..."
            _uiState.update { it.copy(isLoading = true, loadingMessage = message) }
            try {
                songRepository.synchronizeWithDevice(false)
            } catch (e: Exception) {
                Log.e(TAG, "同步失败", e)
                _uiState.update { it.copy(isLoading = false, loadingMessage = "同步失败: ${e.message}") }
            }
            // Add a small delay to prevent the loading indicator from disappearing too quickly
            delay(500L)
            _uiState.update { it.copy(isLoading = false, loadingMessage = "") }
        }
    }

    fun refreshSongs() {
        if (_uiState.value.isLoading) return
        Log.d(TAG, "用户手动刷新歌曲列表")
        triggerSync(isAuto = false)
    }

    override fun onCleared() {
        super.onCleared()
        musicContentObserver?.let {
            contentResolver.unregisterContentObserver(it)
            Log.d(TAG, "MusicContentObserver unregistered.")
        }
        Log.d(TAG, "SongListViewModel 已清理")
    }
}
