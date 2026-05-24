package com.copyback.data.smb

import android.content.Context
import com.copyback.data.model.BackupResult
import com.copyback.data.model.BackupSettings
import com.copyback.data.model.BackupStatus
import com.copyback.data.model.FailedItem
import com.copyback.data.model.LocalImageInfo
import com.copyback.data.model.RemoteFileInfo
import com.copyback.data.repository.BackupRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlin.coroutines.coroutineContext

/**
 * SMB 备份仓库实现。
 * 封装完整的备份流程：连接 → 索引 → 比较 → 上传。
 */
class SmbBackupRepository(
    private val clientManager: SmbClientManager,
) : BackupRepository {

    override val isConnected: Boolean
        get() = clientManager.isConnected

    override suspend fun testConnection(): String? {
        return clientManager.testConnection()
    }

    override suspend fun connect() {
        val result = clientManager.connect()
        if (result.isFailure) {
            throw result.exceptionOrNull() ?: SmbException("连接失败")
        }
    }

    override suspend fun disconnect() {
        clientManager.disconnect()
    }

    override suspend fun listRemoteFiles(): Map<String, RemoteFileInfo> {
        return clientManager.listRemoteFiles()
    }

    override suspend fun remoteFileExists(fileName: String): Boolean {
        return clientManager.remoteFileExists(fileName)
    }

    override suspend fun getRemoteFileModifiedTime(fileName: String): Long? {
        return clientManager.getRemoteFileModifiedTime(fileName)
    }

    override suspend fun createRemoteDirectory(path: String) {
        clientManager.createRemoteDirectory(path)
    }

    override suspend fun uploadFile(
        localImage: LocalImageInfo,
        targetFileName: String,
        onProgress: ((Long, Long) -> Unit)?,
    ): Boolean {
        return clientManager.uploadFile(
            localUri = localImage.uri,
            targetFileName = targetFileName,
            totalBytes = localImage.sizeBytes,
            dateModified = localImage.dateModified,
            onProgress = onProgress,
        )
    }

    /**
     * 执行完整备份流程。
     *
     * 流程：
     * 1. 连接 SMB
     * 2. 索引远程目录 → HashMap<String, RemoteFileInfo>
     * 3. 遍历本地图片列表
     * 4. 对每张图片进行冲突解决
     * 5. 上传或跳过
     * 6. 记录统计和失败原因
     */
    override fun executeBackup(
        images: List<LocalImageInfo>,
        settings: BackupSettings,
    ): Flow<BackupResult> = flow {
        val startTime = System.currentTimeMillis()
        val failedItems = mutableListOf<FailedItem>()

        var uploadedCount = 0
        var skippedCount = 0
        var renamedCount = 0
        var failedCount = 0
        var totalBytesUploaded = 0L

        try {
            // 阶段 1：连接
            connect()

            // 阶段 2：索引远程目录
            val remoteFiles = listRemoteFiles()

            // 阶段 3：遍历本地图片
            val eligibleImages = if (settings.backupMode == com.copyback.data.model.BackupMode.DATE_RANGE) {
                images.filter { settings.isWithinRange(it.dateModified) }
            } else {
                images
            }

            val totalEligible = eligibleImages.size

            for ((index, localImage) in eligibleImages.withIndex()) {
                // 检查协程是否被取消
                if (!coroutineContext.isActive) {
                    throw kotlinx.coroutines.CancellationException("备份已取消")
                }

                try {
                    val remoteFile = remoteFiles[localImage.fileName]
                    val resolution = FilenameConflictResolver.resolve(
                        local = localImage,
                        remoteModifiedTime = remoteFile?.lastModified,
                    )

                    when (resolution) {
                        is ConflictResolution.Upload -> {
                            val success = uploadFile(
                                localImage = localImage,
                                targetFileName = resolution.fileName,
                            ) { uploaded, _ ->
                                totalBytesUploaded += uploaded
                            }
                            if (success) {
                                uploadedCount++
                            } else {
                                failedCount++
                                failedItems.add(
                                    FailedItem(
                                        fileName = localImage.fileName,
                                        targetFileName = resolution.fileName,
                                        reason = "上传失败",
                                    )
                                )
                            }
                        }
                        is ConflictResolution.RenameUpload -> {
                            val success = uploadFile(
                                localImage = localImage,
                                targetFileName = resolution.newFileName,
                            ) { uploaded, _ ->
                                totalBytesUploaded += uploaded
                            }
                            if (success) {
                                renamedCount++
                            } else {
                                failedCount++
                                failedItems.add(
                                    FailedItem(
                                        fileName = localImage.fileName,
                                        targetFileName = resolution.newFileName,
                                        reason = "上传失败（重命名后）",
                                    )
                                )
                            }
                        }
                        is ConflictResolution.Skip -> {
                            skippedCount++
                        }
                    }
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    failedCount++
                    failedItems.add(
                        FailedItem(
                            fileName = localImage.fileName,
                            targetFileName = localImage.fileName,
                            reason = e.message ?: "未知错误",
                        )
                    )
                }

                // 每处理完一个文件就发送中间结果
                val endTime = System.currentTimeMillis()
                emit(
                    BackupResult(
                        totalScanned = images.size,
                        totalEligible = totalEligible,
                        uploadedCount = uploadedCount,
                        skippedCount = skippedCount,
                        renamedCount = renamedCount,
                        failedCount = failedCount,
                        startTime = startTime,
                        endTime = endTime,
                        elapsedMs = endTime - startTime,
                        failedItems = failedItems.toList(),
                        status = BackupStatus.UPLOADING,
                    )
                )
            }

            // 完成
            val finalEndTime = System.currentTimeMillis()
            emit(
                BackupResult(
                    totalScanned = images.size,
                    totalEligible = totalEligible,
                    uploadedCount = uploadedCount,
                    skippedCount = skippedCount,
                    renamedCount = renamedCount,
                    failedCount = failedCount,
                    startTime = startTime,
                    endTime = finalEndTime,
                    elapsedMs = finalEndTime - startTime,
                    failedItems = failedItems.toList(),
                    status = BackupStatus.COMPLETED,
                )
            )

        } catch (e: kotlinx.coroutines.CancellationException) {
            emit(
                BackupResult(
                    totalScanned = images.size,
                    uploadedCount = uploadedCount,
                    skippedCount = skippedCount,
                    renamedCount = renamedCount,
                    failedCount = failedCount,
                    startTime = startTime,
                    endTime = System.currentTimeMillis(),
                    failedItems = failedItems.toList(),
                    status = BackupStatus.CANCELLED,
                    isCancelled = true,
                )
            )
        } catch (e: Exception) {
            emit(
                BackupResult(
                    totalScanned = images.size,
                    uploadedCount = uploadedCount,
                    skippedCount = skippedCount,
                    renamedCount = renamedCount,
                    failedCount = failedCount,
                    startTime = startTime,
                    endTime = System.currentTimeMillis(),
                    failedItems = failedItems.toList(),
                    status = BackupStatus.FAILED,
                )
            )
        } finally {
            try { disconnect() } catch (_: Exception) {}
        }
    }.flowOn(Dispatchers.IO)
}
