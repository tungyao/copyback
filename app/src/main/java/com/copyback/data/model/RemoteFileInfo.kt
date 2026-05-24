package com.copyback.data.model

/**
 * 远程 SMB 文件信息。
 * 用于构建远程目录 HashMap，实现 O(1) 查找。
 */
data class RemoteFileInfo(
    /** 文件名 */
    val fileName: String,
    /** 文件大小（字节） */
    val sizeBytes: Long,
    /** 最后修改时间（毫秒时间戳） */
    val lastModified: Long,
    /** 是否为目录 */
    val isDirectory: Boolean = false,
)
