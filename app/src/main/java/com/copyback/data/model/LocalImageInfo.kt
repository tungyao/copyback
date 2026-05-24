package com.copyback.data.model

import android.net.Uri

/**
 * 本地图片信息。
 * 扫描阶段只获取元数据，不加载图片内容。
 */
data class LocalImageInfo(
    /** 文件名（含扩展名），如 "IMG_0001.jpg" */
    val fileName: String,
    /** 文件 Uri */
    val uri: Uri,
    /** 文件大小（字节） */
    val sizeBytes: Long,
    /** 最后修改时间（毫秒时间戳） */
    val dateModified: Long,
    /** 相对路径（如 "DCIM/Camera/"），可为空 */
    val relativePath: String? = null,
) {
    /** 文件扩展名（小写，含点），如 ".jpg" */
    val extension: String
        get() {
            val dotIndex = fileName.lastIndexOf('.')
            return if (dotIndex >= 0) fileName.substring(dotIndex).lowercase() else ""
        }

    /** 不含扩展名的文件名 */
    val nameWithoutExtension: String
        get() {
            val dotIndex = fileName.lastIndexOf('.')
            return if (dotIndex >= 0) fileName.substring(0, dotIndex) else fileName
        }

    /** 友好显示的文件大小 */
    val displaySize: String
        get() = formatSize(sizeBytes)

    companion object {
        fun formatSize(bytes: Long): String {
            return when {
                bytes < 1024 -> "$bytes B"
                bytes < 1024 * 1024 -> "${"%.1f".format(bytes / 1024.0)} KB"
                bytes < 1024 * 1024 * 1024 -> "${"%.1f".format(bytes / (1024.0 * 1024))} MB"
                else -> "${"%.2f".format(bytes / (1024.0 * 1024 * 1024))} GB"
            }
        }
    }
}
