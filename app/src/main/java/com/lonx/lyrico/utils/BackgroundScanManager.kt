package com.lonx.lyrico.utils

import android.content.Context
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.lonx.lyrico.data.LyricoDatabase
import com.lonx.lyrico.data.repository.SongRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * 后台扫描和更新歌曲元数据的Worker
 */
class SongScanWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {
    
    private companion object {
        const val TAG = "SongScanWorker"
        const val WORK_NAME = "song_scan_work"
        const val FORCE_FULL_SCAN = "force_full_scan"
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        return@withContext try {
            Log.d(TAG, "开始后台扫描歌曲元数据")
            
            val database = LyricoDatabase.getInstance(applicationContext)
            val repository = SongRepository(database, applicationContext)
            val musicScanner = MusicScanner(applicationContext)
            val settingsManager = SettingsManager(applicationContext)
            
            // 获取配置的扫描文件夹
            val scannedFolders = settingsManager.scannedFolders.first().toList()
            if (scannedFolders.isEmpty()) {
                Log.d(TAG, "未配置扫描文件夹，跳过扫描")
                return@withContext Result.success()
            }

            // 判断是否需要全量扫描
            val forceFullScan = inputData.getBoolean(FORCE_FULL_SCAN, false)

            // 扫描文件系统
            val songFiles = musicScanner.scanMusicFiles(scannedFolders)
            Log.d(TAG, "发现 ${songFiles.size} 个音乐文件")

            // 扫描并保存到数据库（增量更新）
            repository.scanAndSaveSongs(songFiles, forceFullScan)

            Log.d(TAG, "后台扫描完成")
            Result.success()
            
        } catch (e: Exception) {
            Log.e(TAG, "后台扫描失败", e)
            Result.retry()
        }
    }
}

/**
 * 后台扫描任务管理器
 */
class BackgroundScanManager(private val context: Context) {
    companion object {
        private const val TAG = "BackgroundScanManager"
        private const val WORK_NAME = "song_scan_work"
    }

    /**
     * 启动后台扫描任务（带延迟和退避策略）
     */
    fun startBackgroundScan(
        forceFullScan: Boolean = false,
        initialDelayMinutes: Long = 5,
        backoffDelayMinutes: Long = 1
    ) {
        Log.d(TAG, "调度后台扫描任务 (forceFullScan=$forceFullScan)")

        val constraints = Constraints.Builder()
            .setRequiresDeviceIdle(false)
            .build()

        val dataBuilder = androidx.work.Data.Builder()
        if (forceFullScan) {
            dataBuilder.putBoolean("FORCE_FULL_SCAN", true)
        }
        
        val scanRequest = OneTimeWorkRequestBuilder<SongScanWorker>()
            .setConstraints(constraints)
            .setInitialDelay(initialDelayMinutes, TimeUnit.MINUTES)
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                backoffDelayMinutes,
                TimeUnit.MINUTES
            )
            .setInputData(dataBuilder.build())
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            WORK_NAME,
            ExistingWorkPolicy.KEEP,
            scanRequest
        )
    }

    /**
     * 立即执行扫描任务
     */
    fun startImmediateScan(forceFullScan: Boolean = false) {
        Log.d(TAG, "立即执行扫描任务 (forceFullScan=$forceFullScan)")
        startBackgroundScan(forceFullScan, 0L)
    }

    /**
     * 取消后台扫描任务
     */
    fun cancelBackgroundScan() {
        Log.d(TAG, "取消后台扫描任务")
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }

    /**
     * 获取扫描任务的工作状态
     */
    fun getWorkStatus() = WorkManager.getInstance(context).getWorkInfosForUniqueWorkLiveData(WORK_NAME)
}
