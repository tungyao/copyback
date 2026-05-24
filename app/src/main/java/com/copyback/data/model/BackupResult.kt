package com.copyback.data.model

/**
 * 备份结果摘要。
 * 在备份完成后生成，展示统计信息。
 */
data class BackupResult(
    /** 扫描图片总数 */
    val totalScanned: Int = 0,
    /** 符合日期条件的数量 */
    val totalEligible: Int = 0,
    /** 实际上传成功数量 */
    val uploadedCount: Int = 0,
    /** 跳过的数量（同名文件且本地不更新） */
    val skippedCount: Int = 0,
    /** 重命名后上传的数量（冲突解决） */
    val renamedCount: Int = 0,
    /** 上传失败数量 */
    val failedCount: Int = 0,
    /** 备份开始时间（毫秒时间戳） */
    val startTime: Long = System.currentTimeMillis(),
    /** 备份结束时间（毫秒时间戳） */
    val endTime: Long = System.currentTimeMillis(),
    /** 已用时间（毫秒） */
    val elapsedMs: Long = endTime - startTime,
    /** 失败详情列表 */
    val failedItems: List<FailedItem> = emptyList(),
    /** 备份是否被取消 */
    val isCancelled: Boolean = false,
    /** 备份状态 */
    val status: BackupStatus = BackupStatus.IDLE,
) {
    /** 人类可读的已用时间 */
    val displayElapsed: String
        get() {
            val seconds = elapsedMs / 1000
            val minutes = seconds / 60
            val hours = minutes / 60
            return when {
                hours > 0 -> "${hours}小时${minutes % 60}分${seconds % 60}秒"
                minutes > 0 -> "${minutes}分${seconds % 60}秒"
                else -> "${seconds}秒"
            }
        }
}

/**
 * 备份失败项详情。
 */
data class FailedItem(
    /** 本地文件名 */
    val fileName: String,
    /** 目标远程文件名 */
    val targetFileName: String,
    /** 失败原因 */
    val reason: String,
)

/**
 * 备份状态枚举。
 */
enum class BackupStatus {
    IDLE,
    SCANNING,
    COMPARING,
    UPLOADING,
    COMPLETED,
    FAILED,
    CANCELLED,
}
