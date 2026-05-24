package com.copyback.data.model

import android.net.Uri

/**
 * 备份设置数据类。
 * 定义备份模式、时间范围和图片来源。
 */
data class BackupSettings(
    /** 备份模式 */
    val backupMode: BackupMode = BackupMode.FULL,
    /** 开始日期（毫秒时间戳），仅时间范围模式有效 */
    val startDate: Long? = null,
    /** 结束日期（毫秒时间戳），仅时间范围模式有效 */
    val endDate: Long? = null,
    /** 图片来源类型 */
    val imageSourceType: ImageSourceType = ImageSourceType.MEDIA_STORE,
    /** SAF 授权 URI，仅 SAF 模式有效 */
    val safTreeUri: Uri? = null,
    /** 是否记住来源目录 */
    val rememberSource: Boolean = false,
) {
    /** 检查给定时间是否在备份范围内 */
    fun isWithinRange(dateModified: Long): Boolean {
        if (backupMode == BackupMode.FULL) return true
        val start = startDate ?: return false
        val end = endDate ?: return false
        return dateModified in start..end
    }
}

/**
 * 备份模式枚举。
 */
enum class BackupMode(val label: String) {
    FULL("全量备份"),
    DATE_RANGE("时间范围备份"),
}

/**
 * 图片来源类型枚举。
 */
enum class ImageSourceType(val label: String) {
    MEDIA_STORE("系统相册 + DCIM + Pictures"),
    SAF("自定义目录 (SAF)"),
}
