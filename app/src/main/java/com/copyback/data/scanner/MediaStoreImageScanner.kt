package com.copyback.data.scanner

import android.content.ContentResolver
import android.content.ContentUris
import android.os.Build
import android.provider.MediaStore
import com.copyback.data.model.LocalImageInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 基于 MediaStore 的图片扫描器。
 * 只查询元数据：文件名、URI、修改时间、文件大小。
 * 禁止加载图片内容、生成缩略图、解析 EXIF。
 *
 * 扫描范围：系统识别的所有图片（包括 DCIM、Pictures 等目录）。
 */
class MediaStoreImageScanner(private val contentResolver: ContentResolver) {

    /**
     * 扫描所有图片元数据。
     * 在 IO 线程执行，不阻塞 UI。
     *
     * @return 图片元数据列表
     */
    suspend fun scanAllImages(): List<LocalImageInfo> = withContext(Dispatchers.IO) {
        val images = mutableListOf<LocalImageInfo>()

        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.DATE_MODIFIED,
            MediaStore.Images.Media.SIZE,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Images.Media.RELATIVE_PATH
            } else {
                null
            },
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Images.Media.IS_PENDING
            } else {
                null
            },
        ).filterNotNull().toTypedArray()

        val sortOrder = "${MediaStore.Images.Media.DATE_MODIFIED} DESC"

        val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }

        try {
            contentResolver.query(
                uri,
                projection,
                null,
                null,
                sortOrder,
            )?.use { cursor ->
                val idIdx = cursor.getColumnIndex(MediaStore.Images.Media._ID)
                val displayNameIdx = cursor.getColumnIndex(MediaStore.Images.Media.DISPLAY_NAME)
                val dateModifiedIdx = cursor.getColumnIndex(MediaStore.Images.Media.DATE_MODIFIED)
                val sizeIdx = cursor.getColumnIndex(MediaStore.Images.Media.SIZE)
                val relativePathIdx = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    cursor.getColumnIndex(MediaStore.Images.Media.RELATIVE_PATH)
                } else -1
                val isPendingIdx = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    cursor.getColumnIndex(MediaStore.Images.Media.IS_PENDING)
                } else -1

                while (cursor.moveToNext()) {
                    if (idIdx < 0 || displayNameIdx < 0) break

                    // 跳过未完成的文件（Android 10+）
                    if (isPendingIdx >= 0) {
                        val isPending = cursor.getInt(isPendingIdx) == 1
                        if (isPending) continue
                    }

                    val displayName = cursor.getString(displayNameIdx) ?: continue
                    val dateModified = if (dateModifiedIdx >= 0) {
                        cursor.getLong(dateModifiedIdx) * 1000L // 秒 → 毫秒
                    } else {
                        System.currentTimeMillis()
                    }
                    val size = if (sizeIdx >= 0) cursor.getLong(sizeIdx) else 0L
                    val fileSize = if (size > 0) size else 0L

                    val relativePath = if (relativePathIdx >= 0) {
                        cursor.getString(relativePathIdx) ?: ""
                    } else ""

                    // 构建 URI
                    val imageId = cursor.getLong(idIdx)
                    val imageUri = ContentUris.withAppendedId(uri, imageId)

                    images.add(
                        LocalImageInfo(
                            fileName = displayName,
                            uri = imageUri,
                            sizeBytes = fileSize,
                            dateModified = dateModified,
                            relativePath = relativePath,
                        )
                    )
                }
            }
        } catch (e: SecurityException) {
            // 权限不足时返回空列表，由 UI 层提示用户授权
        }

        images
    }

    /**
     * 按时间范围扫描图片。
     * @param startDate 开始时间（毫秒时间戳）
     * @param endDate 结束时间（毫秒时间戳）
     */
    suspend fun scanImagesByDateRange(startDate: Long, endDate: Long): List<LocalImageInfo> =
        withContext(Dispatchers.IO) {
            val images = mutableListOf<LocalImageInfo>()

            val projection = arrayOf(
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.DISPLAY_NAME,
                MediaStore.Images.Media.DATE_MODIFIED,
                MediaStore.Images.Media.SIZE,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    MediaStore.Images.Media.RELATIVE_PATH
                } else {
                    null
                },
            ).filterNotNull().toTypedArray()

            val selection = "${MediaStore.Images.Media.DATE_MODIFIED} >= ? AND ${MediaStore.Images.Media.DATE_MODIFIED} <= ?"
            val selectionArgs = arrayOf(
                (startDate / 1000).toString(), // 毫秒 → 秒
                (endDate / 1000).toString(),
            )

            val sortOrder = "${MediaStore.Images.Media.DATE_MODIFIED} DESC"

            val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
            } else {
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            }

            try {
                contentResolver.query(
                    uri,
                    projection,
                    selection,
                    selectionArgs,
                    sortOrder,
                )?.use { cursor ->
                    val idIdx = cursor.getColumnIndex(MediaStore.Images.Media._ID)
                    val displayNameIdx = cursor.getColumnIndex(MediaStore.Images.Media.DISPLAY_NAME)
                    val dateModifiedIdx = cursor.getColumnIndex(MediaStore.Images.Media.DATE_MODIFIED)
                    val sizeIdx = cursor.getColumnIndex(MediaStore.Images.Media.SIZE)
                    val relativePathIdx = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        cursor.getColumnIndex(MediaStore.Images.Media.RELATIVE_PATH)
                    } else -1

                    while (cursor.moveToNext()) {
                        if (idIdx < 0 || displayNameIdx < 0) break

                        val displayName = cursor.getString(displayNameIdx) ?: continue
                        val dateModified = if (dateModifiedIdx >= 0) {
                            cursor.getLong(dateModifiedIdx) * 1000L
                        } else {
                            System.currentTimeMillis()
                        }
                        val size = if (sizeIdx >= 0) cursor.getLong(sizeIdx) else 0L

                        val relativePath = if (relativePathIdx >= 0) {
                            cursor.getString(relativePathIdx) ?: ""
                        } else ""

                        val imageUri = ContentUris.withAppendedId(
                            uri,
                            cursor.getLong(idIdx),
                        )

                        images.add(
                            LocalImageInfo(
                                fileName = displayName,
                                uri = imageUri,
                                sizeBytes = size.takeIf { it > 0 } ?: 0L,
                                dateModified = dateModified,
                                relativePath = relativePath,
                            )
                        )
                    }
                }
            } catch (e: SecurityException) {
                // 权限不足时返回空列表
            }

            images
        }
}
