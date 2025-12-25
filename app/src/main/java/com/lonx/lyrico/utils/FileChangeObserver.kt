package com.lonx.lyrico.utils

import android.content.Context
import android.os.FileObserver
import android.util.Log
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import java.io.File
import kotlin.collections.flatten

/**
 * 文件变动事件
 */
sealed class FileChangeEvent {
    data class FileCreated(val path: String) : FileChangeEvent()
    data class FileModified(val path: String) : FileChangeEvent()
    data class FileDeleted(val path: String) : FileChangeEvent()
}

/**
 * 文件监听器 - 监听音乐文件的新增/修改/删除
 */
class MusicFileObserver(
    private val context: Context,
    private val musicExtensions: Set<String> = setOf(
        "mp3", "flac", "wav", "m4a", "aac", "ogg", "wma", "ape", "alac", "dsd", "wv"
    )
) {
    private companion object {
        const val TAG = "MusicFileObserver"
    }

    private val eventChannel = Channel<FileChangeEvent>(capacity = Channel.BUFFERED)
    val fileChangeEvents: Flow<FileChangeEvent> = eventChannel.receiveAsFlow()

    private val observers = mutableMapOf<String, FileObserver>()
    private var isMonitoring = false

    /**
     * 开始监听指定路径
     */
    fun startMonitoring(folderPaths: List<String>) {
        if (isMonitoring) {
            Log.d(TAG, "已在监听中，跳过重复启动")
            return
        }

        isMonitoring = true
        Log.d(TAG, "启动文件监听: $folderPaths")

        folderPaths.forEach { folderPath ->
            try {
                val observer = createFileObserver(folderPath)
                observers[folderPath] = observer
                observer.startWatching()
            } catch (e: Exception) {
                Log.e(TAG, "无法监听路径: $folderPath", e)
            }
        }
    }

    /**
     * 停止监听
     */
    fun stopMonitoring() {
        if (!isMonitoring) {
            Log.d(TAG, "未在监听，跳过停止")
            return
        }

        isMonitoring = false
        Log.d(TAG, "停止文件监听")

        observers.values.forEach {
            try {
                it.stopWatching()
            } catch (e: Exception) {
                Log.e(TAG, "停止监听失败", e)
            }
        }
        observers.clear()
    }

    /**
     * 创建文件观察器
     */
    private fun createFileObserver(path: String): FileObserver {
        return object : FileObserver(path, CREATE or MODIFY or DELETE or CLOSE_WRITE) {
            override fun onEvent(event: Int, path: String?) {
                if (path == null) return
                
                val fullPath = File(this@MusicFileObserver.context.filesDir, path).absolutePath
                
                // 检查是否是音乐文件
                if (!isMusicFile(path)) {
                    return
                }

                try {
                    when (event) {
                        CREATE -> {
                            Log.d(TAG, "文件新增: $path")
                            eventChannel.trySend(FileChangeEvent.FileCreated(fullPath))
                        }
                        MODIFY, CLOSE_WRITE -> {
                            Log.d(TAG, "文件修改: $path")
                            eventChannel.trySend(FileChangeEvent.FileModified(fullPath))
                        }
                        DELETE -> {
                            Log.d(TAG, "文件删除: $path")
                            eventChannel.trySend(FileChangeEvent.FileDeleted(fullPath))
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "处理文件事件失败: $path", e)
                }
            }
        }
    }

    /**
     * 检查是否是音乐文件
     */
    private fun isMusicFile(fileName: String): Boolean {
        val extension = fileName.substringAfterLast(".", "").lowercase()
        return musicExtensions.contains(extension)
    }

    /**
     * 关闭资源
     */
    fun close() {
        stopMonitoring()
        try {
            eventChannel.close()
        } catch (e: Exception) {
            Log.e(TAG, "关闭事件通道失败", e)
        }
    }
}

/**
 * 轻量级文件轮询监听器
 * 用于低频率的间隔检查文件变动
 */
//class FilePollingObserver(
//    private val context: Context,
//    private val checkIntervalSeconds: Long = 60,
//    private val musicExtensions: Set<String> = setOf(
//        "mp3", "flac", "wav", "m4a", "aac", "ogg", "wma", "ape", "alac", "dsd", "wv"
//    )) {
//    private companion object {
//        const val TAG = "FilePollingObserver"
//    }
//
//    private val eventChannel = Channel<FileChangeEvent>(capacity = Channel.BUFFERED)
//    val fileChangeEvents: Flow<FileChangeEvent> = eventChannel.receiveAsFlow()
//
//    private var isPolling = false
//    private val fileModificationCache = mutableMapOf<String, Long>()
//
//    /**
//     * 启动轮询
//     */
//    fun startPolling(folderPaths: List<String>) {
//        if (isPolling) {
//            Log.d(TAG, "已在轮询中，跳过重复启动")
//            return
//        }
//
//        isPolling = true
//        Log.d(TAG, "启动文件轮询: $folderPaths")
//
//        // 初始化缓存
//        fileModificationCache.clear()
//        folderPaths.forEach { folderPath ->
//            try {
//                val folder = File(folderPath)
//                if (folder.exists() && folder.isDirectory) {
//                    folder.walkBottomUp()
//                        .filter { isMusicFile(it.name) }
//                        .forEach { file ->
//                            fileModificationCache[file.absolutePath] = file.lastModified()
//                        }
//                }
//            } catch (e: Exception) {
//                Log.e(TAG, "初始化轮询缓存失败: $folderPath", e)
//            }
//        }
//    }
//
//    /**
//     * 执行一次轮询检查
//     */
//    suspend fun poll(folderPaths: List<String>) {
//        if (!isPolling) return
//
//        try {
//            folderPaths.forEach { folderPath ->
//                val folder = File(folderPath)
//                if (!folder.exists() || !folder.isDirectory) {
//                    return@forEach
//                }
//
//                // 检查新增或修改的文件
//                folder.walkBottomUp()
//                    .filter { isMusicFile(it.name) }
//                    .forEach { file ->
//                        val currentModTime = file.lastModified()
//                        val cachedModTime = fileModificationCache[file.absolutePath]
//
//                        when {
//                            cachedModTime == null -> {
//                                Log.d(TAG, "发现新文件: ${file.name}")
//                                eventChannel.send(FileChangeEvent.FileCreated(file.absolutePath))
//                                fileModificationCache[file.absolutePath] = currentModTime
//                            }
//                            cachedModTime != currentModTime -> {
//                                Log.d(TAG, "发现修改的文件: ${file.name}")
//                                eventChannel.send(FileChangeEvent.FileModified(file.absolutePath))
//                                fileModificationCache[file.absolutePath] = currentModTime
//                            }
//                        }
//                    }
//            }
//
//            // 检查删除的文件
//            val currentFiles = folderPaths
//                .mapNotNull { folderPath ->
//                    val folder = File(folderPath)
//                    if (folder.exists() && folder.isDirectory) {
//                        folder.walkBottomUp()
//                            .filter { isMusicFile(it.name) }
//                            .map { it.absolutePath }
//                    } else {
//                        null
//                    }
//                }
//                .flatten()
//                .toSet()
//
//            fileModificationCache.keys
//                .filter { it !in currentFiles }
//                .forEach { deletedPath ->
//                    Log.d(TAG, "检测到已删除的文件: $deletedPath")
//                    eventChannel.send(FileChangeEvent.FileDeleted(deletedPath))
//                    fileModificationCache.remove(deletedPath)
//                }
//
//        } catch (e: Exception) {
//            Log.e(TAG, "轮询检查失败", e)
//        }
//    }
//
//    /**
//     * 停止轮询
//     */
//    fun stopPolling() {
//        isPolling = false
//        fileModificationCache.clear()
//        Log.d(TAG, "停止文件轮询")
//    }
//
//    /**
//     * 关闭资源
//     */
//    fun close() {
//        stopPolling()
//        try {
//            eventChannel.close()
//        } catch (e: Exception) {
//            Log.e(TAG, "关闭事件通道失败", e)
//        }
//    }
//
//    private fun isMusicFile(fileName: String): Boolean {
//        val extension = fileName.substringAfterLast(".", "").lowercase()
//        return musicExtensions.contains(extension)
//    }
//}
