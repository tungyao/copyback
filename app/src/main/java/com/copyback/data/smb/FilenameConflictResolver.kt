package com.copyback.data.smb

import com.copyback.data.model.LocalImageInfo
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 文件名冲突解决器。
 * 当远程已存在同名文件时，生成带时间戳的新文件名。
 *
 * 规则：
 * - 如果远程不存在同名文件 → 直接使用原文件名
 * - 如果远程存在同名文件 → 比较修改时间
 *   - 本地更新 → 生成新文件名: 原文件名_yyyyMMdd_HHmmss.扩展名
 *   - 本地不更新 → 跳过
 */
object FilenameConflictResolver {

    private val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)

    /**
     * 生成重命名后的文件名。
     * 格式: 原名_yyyyMMdd_HHmmss.扩展名
     *
     * 例如 IMG_0001.jpg → IMG_0001_20260524_143000.jpg
     */
    fun generateRenamedFileName(original: LocalImageInfo): String {
        val timestamp = dateFormat.format(Date(original.dateModified))
        val nameWithoutExt = original.nameWithoutExtension
        val ext = original.extension
        return "${nameWithoutExt}_$timestamp$ext"
    }

    /**
     * 解析冲突结果：决定是否上传、用什么文件名。
     *
     * @return 冲突解决结果
     */
    fun resolve(
        local: LocalImageInfo,
        remoteModifiedTime: Long?,
    ): ConflictResolution {
        // 情况 1：远程不存在同名文件 → 直接上传
        if (remoteModifiedTime == null) {
            return ConflictResolution.Upload(local.fileName)
        }

        // 情况 2：远程存在同名文件，比较修改时间
        return if (local.dateModified > remoteModifiedTime) {
            // 本地更新 → 生成新文件名上传
            val newName = generateRenamedFileName(local)
            ConflictResolution.RenameUpload(newName)
        } else {
            // 本地不更新 → 跳过
            ConflictResolution.Skip
        }
    }
}

/**
 * 冲突解决结果密封类。
 */
sealed class ConflictResolution {
    /** 直接上传，使用指定文件名 */
    data class Upload(val fileName: String) : ConflictResolution()

    /** 生成新文件名后上传 */
    data class RenameUpload(val newFileName: String) : ConflictResolution()

    /** 跳过（远程文件更新或相同） */
    data object Skip : ConflictResolution()
}
