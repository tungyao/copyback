package com.copyback.data.repository

import com.copyback.data.model.BackupResult
import com.copyback.data.model.BackupSettings
import com.copyback.data.model.LocalImageInfo
import com.copyback.data.model.RemoteFileInfo
import kotlinx.coroutines.flow.Flow

/**
 * 备份仓库抽象接口。
 * 定义备份操作的核心契约，与具体传输协议解耦。
 */
interface BackupRepository {

    /** 测试连接是否可用，返回错误消息（成功时返回 null） */
    suspend fun testConnection(): String?

    /** 连接远程存储 */
    suspend fun connect()

    /** 断开连接 */
    suspend fun disconnect()

    /** 是否已连接 */
    val isConnected: Boolean

    /**
     * 列出远程目录中所有文件。
     * 返回以文件名为 key 的 HashMap，实现 O(1) 查找。
     * 排除子目录。
     */
    suspend fun listRemoteFiles(): Map<String, RemoteFileInfo>

    /** 检查远程文件是否存在 */
    suspend fun remoteFileExists(fileName: String): Boolean

    /** 获取远程文件最后修改时间（毫秒），不存在时返回 null */
    suspend fun getRemoteFileModifiedTime(fileName: String): Long?

    /** 创建远程目录（递归），已存在时跳过 */
    suspend fun createRemoteDirectory(path: String)

    /**
     * 上传文件。
     * @param localImage 本地图片信息
     * @param targetFileName 目标远程文件名（可能与原始文件名不同）
     * @param onProgress 进度回调 (bytesUploaded, totalBytes)
     * @return 上传成功返回 true
     */
    suspend fun uploadFile(
        localImage: LocalImageInfo,
        targetFileName: String,
        onProgress: ((Long, Long) -> Unit)? = null,
    ): Boolean

    /**
     * 执行完整备份流程。
     * 返回 Flow 以便 UI 层观察进度。
     */
    fun executeBackup(
        images: List<LocalImageInfo>,
        settings: BackupSettings,
    ): Flow<BackupResult>
}
