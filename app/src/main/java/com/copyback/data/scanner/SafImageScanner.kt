package com.copyback.data.scanner

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import com.copyback.data.model.LocalImageInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Date

/**
 * 基于 SAF (Storage Access Framework) 的图片扫描器。
 * 用于扫描用户通过 SAF 授权选择的目录。
 *
 * 只获取文件元数据，不加载图片内容。
 */
class SafImageScanner(private val contentResolver: ContentResolver) {

    /** 支持的图片扩展名集合 */
    private val imageExtensions = setOf(
        "jpg", "jpeg", "png", "gif", "bmp", "webp",
        "heic", "heif", "tiff", "tif", "avif",
        "dng", "cr2", "nef", "arw", "orf", "rw2", // RAW 格式
        "svg",
    )

    /**
     * 递归扫描 SAF 目录树中所有图片文件。
     *
     * @param treeUri SAF 授权树 URI
     * @return 图片元数据列表
     */
    suspend fun scanDirectory(treeUri: Uri): List<LocalImageInfo> = withContext(Dispatchers.IO) {
        val images = mutableListOf<LocalImageInfo>()
        val rootDoc = DocumentFile.fromTreeUri(com.copyback.CopyBackApp.instance, treeUri)
            ?: return@withContext images

        scanDocumentFile(rootDoc, images)
        images
    }

    /**
     * 按时间范围扫描。
     */
    suspend fun scanDirectoryByDateRange(
        treeUri: Uri,
        startDate: Long,
        endDate: Long,
    ): List<LocalImageInfo> = withContext(Dispatchers.IO) {
        val images = mutableListOf<LocalImageInfo>()
        val rootDoc = DocumentFile.fromTreeUri(com.copyback.CopyBackApp.instance, treeUri)
            ?: return@withContext images

        scanDocumentFile(rootDoc, images, startDate, endDate)
        images
    }

    /**
     * 递归遍历 DocumentFile 树。
     * 只收集图片文件元数据，不读取图片内容。
     */
    private fun scanDocumentFile(
        doc: DocumentFile,
        result: MutableList<LocalImageInfo>,
        startDate: Long? = null,
        endDate: Long? = null,
    ) {
        if (doc.isDirectory) {
            doc.listFiles().forEach { child ->
                scanDocumentFile(child, result, startDate, endDate)
            }
        } else if (doc.isFile && isImageFile(doc.name)) {
            val lastModified = doc.lastModified()
            val isInRange = if (startDate != null && endDate != null) {
                lastModified in startDate..endDate
            } else {
                true
            }

            if (isInRange) {
                result.add(
                    LocalImageInfo(
                        fileName = doc.name ?: "unknown",
                        uri = doc.uri,
                        sizeBytes = doc.length(),
                        dateModified = lastModified,
                        relativePath = null, // SAF 不暴露相对路径
                    )
                )
            }
        }
    }

    /**
     * 判断文件是否为图片（通过扩展名）。
     * 不读取文件内容，仅通过文件名判断。
     */
    private fun isImageFile(fileName: String?): Boolean {
        if (fileName == null) return false
        val dotIndex = fileName.lastIndexOf('.')
        if (dotIndex < 0) return false
        val ext = fileName.substring(dotIndex + 1).lowercase()
        return ext in imageExtensions
    }
}
