package com.copyback.data.model

/**
 * 备份进度状态。
 * 通过 StateFlow 实时推送到 UI 层。
 */
data class BackupProgressState(
    /** 当前阶段 */
    val status: BackupStatus = BackupStatus.IDLE,
    /** 扫描总数 */
    val totalScanned: Int = 0,
    /** 符合条件数量 */
    val totalEligible: Int = 0,
    /** 已上传数量 */
    val uploadedCount: Int = 0,
    /** 已跳过数量 */
    val skippedCount: Int = 0,
    /** 已重命名数量 */
    val renamedCount: Int = 0,
    /** 失败数量 */
    val failedCount: Int = 0,
    /** 当前正在处理的文件名 */
    val currentFileName: String = "",
    /** 当前文件已上传字节数 */
    val currentFileBytesUploaded: Long = 0L,
    /** 当前文件总字节数 */
    val currentFileBytesTotal: Long = 0L,
    /** 总上传字节数（所有文件） */
    val totalBytesUploaded: Long = 0L,
    /** 总预计字节数 */
    val totalBytesEstimated: Long = 0L,
    /** 当前上传速度 (bytes/sec) */
    val uploadSpeedBytesPerSec: Long = 0L,
    /** 已用时间（毫秒） */
    val elapsedMs: Long = 0L,
    /** 错误消息（暂停或失败时） */
    val errorMessage: String? = null,
) {
    /** 总体进度 0f..1f */
    val overallProgress: Float
        get() = if (totalEligible > 0) {
            (uploadedCount + skippedCount + renamedCount + failedCount).toFloat() / totalEligible
        } else 0f

    /** 当前文件进度 0f..1f */
    val currentFileProgress: Float
        get() = if (currentFileBytesTotal > 0) {
            currentFileBytesUploaded.toFloat() / currentFileBytesTotal
        } else 0f

    /** 友好显示的上传速度 */
    val displaySpeed: String
        get() = when {
            uploadSpeedBytesPerSec >= 1024 * 1024 ->
                "${"%.1f".format(uploadSpeedBytesPerSec / (1024.0 * 1024))} MB/s"
            uploadSpeedBytesPerSec >= 1024 ->
                "${"%.1f".format(uploadSpeedBytesPerSec / 1024.0)} KB/s"
            else -> "${uploadSpeedBytesPerSec} B/s"
        }

    /** 友好显示的已用时间 */
    val displayElapsed: String
        get() {
            val sec = elapsedMs / 1000
            val min = sec / 60
            val hr = min / 60
            return when {
                hr > 0 -> "${hr}h ${min % 60}m ${sec % 60}s"
                min > 0 -> "${min}m ${sec % 60}s"
                else -> "${sec}s"
            }
        }
}
