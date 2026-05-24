package com.copyback.data.smb

import com.copyback.data.model.SmbConfig
import com.copyback.data.model.SmbProtocolVersion
import com.hierynomus.msdtyp.AccessMask
import com.hierynomus.msdtyp.FileTime
import com.hierynomus.msfscc.FileAttributes
import com.hierynomus.msfscc.fileinformation.FileBasicInformation
import com.hierynomus.mssmb2.SMB2ShareAccess
import com.hierynomus.mssmb2.SMB2CreateDisposition
import com.hierynomus.mssmb2.SMB2CreateOptions
import org.bouncycastle.jce.provider.BouncyCastleProvider
import com.hierynomus.smbj.SMBClient
import com.hierynomus.smbj.SmbConfig as SmbjConfig
import com.hierynomus.smbj.auth.AuthenticationContext
import com.hierynomus.smbj.connection.Connection
import com.hierynomus.smbj.session.Session
import com.hierynomus.smbj.share.DiskShare
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.Security
import java.util.EnumSet
import java.util.concurrent.TimeUnit

/**
 * SMB 客户端管理器。
 * 封装 SMBJ 库，管理 SMB 连接的完整生命周期。
 *
 * 注意：在 Application.onCreate() 中需调用 SmbClientManager.initSecurityProvider()
 * 以注册 BouncyCastle 安全提供者。
 */
class SmbClientManager(private val config: SmbConfig) {

    private var client: SMBClient? = null
    private var connection: Connection? = null
    private var session: Session? = null
    private var diskShare: DiskShare? = null

    val isConnected: Boolean
        get() = session != null && diskShare != null

    /** 上次错误消息 */
    var lastError: String? = null
        private set

    /**
     * 建立完整的 SMB 连接链路：
     * SMBClient → Connection → Session → DiskShare
     */
    suspend fun connect(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            disconnectInternal()

            // 1. 创建 SMBClient
            val smbConfig = buildSmbjConfig()
            client = SMBClient(smbConfig)

            // 2. 建立 TCP 连接
            connection = client!!.connect(config.serverAddress, config.port)
                ?: throw SmbException("连接服务器失败: ${config.serverAddress}:${config.port}")

            // 3. 认证登录
            val authContext = buildAuthContext()
            session = connection!!.authenticate(authContext)
                ?: throw SmbException("SMB 认证失败")

            // 4. 连接共享目录
            diskShare = (session as Session).connectShare(config.shareName) as? DiskShare
                ?: throw SmbException("连接共享目录失败: ${config.shareName}")

            lastError = null
            Result.success(Unit)
        } catch (e: Exception) {
            lastError = classifyError(e)
            disconnectInternal()
            Result.failure(SmbException(lastError ?: "未知错误", e))
        }
    }

    /**
     * 测试连接，返回详细的错误诊断信息。
     * 成功返回 null，失败返回错误描述字符串。
     */
    suspend fun testConnection(): String? = withContext(Dispatchers.IO) {
        var testClient: SMBClient? = null
        var testConnection: Connection? = null
        var testSession: Session? = null
        var testShare: DiskShare? = null

        try {
            // 阶段 1：创建客户端并连接服务器
            val smbConfig = buildSmbjConfig()
            testClient = SMBClient(smbConfig)
            testConnection = testClient.connect(config.serverAddress, config.port)
                ?: return@withContext "服务器不可达: ${config.serverAddress}:${config.port}"

            // 阶段 2：认证
            val authContext = buildAuthContext()
            testSession = testConnection.authenticate(authContext)
                ?: return@withContext "认证失败: 用户名或密码错误"

            // 阶段 3：连接共享
            testShare = testSession.connectShare(config.shareName) as? DiskShare
                ?: return@withContext "共享名不存在: ${config.shareName}"

            // 阶段 4：检查远程目录
            val remotePath = config.fullRemotePath
            if (remotePath.isNotEmpty()) {
                if (!testShare.folderExists(remotePath)) {
                    // 尝试自动创建
                    try {
                        testShare.mkdir(remotePath)
                    } catch (e: Exception) {
                        return@withContext "远程目录不存在且无法创建: $remotePath"
                    }
                }

                // 阶段 5：检查写入权限
                try {
                    val testFileName = "$remotePath/.copyback_test_${System.currentTimeMillis()}.tmp"
                    val testFile = testShare.openFile(
                        testFileName,
                        EnumSet.of(AccessMask.GENERIC_WRITE),
                        null,
                        SMB2ShareAccess.ALL,
                        SMB2CreateDisposition.FILE_CREATE,
                        EnumSet.noneOf(SMB2CreateOptions::class.java)
                    )
                    testFile.write(ByteArray(0), 0)
                    testFile.close()
                    testShare.rm(testFileName)
                } catch (e: Exception) {
                    return@withContext "无写入权限: $remotePath"
                }
            }

            null // 所有检查通过
        } catch (e: Exception) {
            classifyError(e)
        } finally {
            try { testShare?.close() } catch (_: Exception) {}
            try { testSession?.close() } catch (_: Exception) {}
            try { testConnection?.close() } catch (_: Exception) {}
            try { testClient?.close() } catch (_: Exception) {}
        }
    }

    /**
     * 列出远程目录中的所有文件（不含子目录）。
     * 返回 Map<文件名, RemoteFileInfo>。
     */
    suspend fun listRemoteFiles(): Map<String, com.copyback.data.model.RemoteFileInfo> =
        withContext(Dispatchers.IO) {
            val share = diskShare ?: throw SmbException("未连接到 SMB 共享")
            val path = config.fullRemotePath.ifEmpty { "." }
            val result = mutableMapOf<String, com.copyback.data.model.RemoteFileInfo>()

            try {
                val files = share.list(path)
                files?.forEach { fileInfo ->
                    if (!fileInfo.fileName.isNullOrBlank() &&
                        fileInfo.fileName != "." &&
                        fileInfo.fileName != ".."
                    ) {
                        // 排除目录
                        val isDir = (fileInfo.fileAttributes and FileAttributes.FILE_ATTRIBUTE_DIRECTORY.value) != 0L
                        if (!isDir) {
                            result[fileInfo.fileName] = com.copyback.data.model.RemoteFileInfo(
                                fileName = fileInfo.fileName,
                                sizeBytes = fileInfo.allocationSize,
                                lastModified = fileInfo.changeTime.toEpochMillis(),
                                isDirectory = false,
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                // 如果目录为空或不存在
            }

            result
        }

    /**
     * 上传文件到远程目录。
     * 使用流式复制，支持进度回调。
     */
    suspend fun uploadFile(
        localUri: android.net.Uri,
        targetFileName: String,
        totalBytes: Long,
        dateModified: Long = 0L,
        onProgress: ((Long, Long) -> Unit)? = null,
    ): Boolean = withContext(Dispatchers.IO) {
        val share = diskShare ?: throw SmbException("未连接到 SMB 共享")
        val remotePath = buildRemoteFilePath(targetFileName)

        val context = com.copyback.CopyBackApp.instance
        val inputStream = context.contentResolver.openInputStream(localUri)
            ?: throw SmbException("无法读取本地文件: $targetFileName")

        // 阶段1：写入文件内容（原始字节流复制）
        inputStream.use { input ->
            val smbFile = share.openFile(
                remotePath,
                EnumSet.of(AccessMask.GENERIC_WRITE),
                null,
                SMB2ShareAccess.ALL,
                SMB2CreateDisposition.FILE_CREATE,
                EnumSet.noneOf(SMB2CreateOptions::class.java)
            )

            smbFile.use { file ->
                val buffer = ByteArray(81920) // 8KB 缓冲区
                var bytesRead: Int
                var totalRead: Int = 0;

                while (input.read(buffer).also { bytesRead = it } != -1) {
                    file.write(buffer, totalRead.toLong(), 0, bytesRead)
                    totalRead += bytesRead
                    onProgress?.invoke(totalRead.toLong(), totalBytes)
                }
            }
        }

        // 阶段2：独立设置修改时间（写入句柄已关闭，与写入隔离）
        if (dateModified > 0L) {
            try {
                share.openFile(
                    remotePath,
                    EnumSet.of(AccessMask.GENERIC_READ, AccessMask.FILE_WRITE_ATTRIBUTES),
                    null,
                    SMB2ShareAccess.ALL,
                    SMB2CreateDisposition.FILE_OPEN,
                    EnumSet.noneOf(SMB2CreateOptions::class.java)
                ).use { attrFile ->
                    val currentInfo = attrFile.getFileInformation()
                    val srcTime = FileTime.ofEpochMillis(dateModified)
                    val updateInfo = FileBasicInformation(
               currentInfo.basicInformation.creationTime,
                      srcTime,
                     srcTime,
                 srcTime,
                     currentInfo.basicInformation.fileAttributes,
                    )
                    attrFile.setFileInformation(updateInfo)
                }
            } catch (e: Exception) {
                android.util.Log.w("SmbClientManager", "设置文件修改时间失败", e)
            }
        }

        true
    }

    /**
     * 检查远程文件是否存在。
     */
    fun remoteFileExists(fileName: String): Boolean {
        val share = diskShare ?: return false
        val remotePath = buildRemoteFilePath(fileName)
        return try {
            share.fileExists(remotePath)
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 获取远程文件最后修改时间（毫秒）。
     */
    fun getRemoteFileModifiedTime(fileName: String): Long? {
        val share = diskShare ?: return null
        val remotePath = buildRemoteFilePath(fileName)

        return try {
            val fileInfo = share.getFileInformation(remotePath)
            fileInfo.basicInformation.lastWriteTime.toEpochMillis()
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 创建远程目录（递归）。
     */
    fun createRemoteDirectory(path: String) {
        val share = diskShare ?: throw SmbException("未连接到 SMB 共享")
        val fullPath = if (config.fullRemotePath.isNotEmpty()) {
            "${config.fullRemotePath}/$path"
        } else {
            path
        }
        // SMBJ mkdir 不支持递归，需逐级创建
        val parts = fullPath.trim('/').split('/')
        var currentPath = ""
        for (part in parts) {
            if (part.isEmpty()) continue
            currentPath = if (currentPath.isEmpty()) part else "$currentPath/$part"
            if (!share.folderExists(currentPath)) {
                share.mkdir(currentPath)
            }
        }
    }

    /**
     * 断开所有连接。
     */
    fun disconnect() {
        disconnectInternal()
    }

    // --- 内部方法 ---

    private fun disconnectInternal() {
        try { diskShare?.close() } catch (_: Exception) {}
        try { session?.close() } catch (_: Exception) {}
        try { connection?.close() } catch (_: Exception) {}
        try { client?.close() } catch (_: Exception) {}
        diskShare = null
        session = null
        connection = null
        client = null
    }

    private fun buildSmbjConfig(): SmbjConfig {
        return SmbjConfig.builder()
            .withTimeout(config.connectionTimeoutMs, TimeUnit.MILLISECONDS)
            .withSoTimeout(config.readTimeoutMs.toInt().coerceAtMost(Int.MAX_VALUE).toLong(), TimeUnit.MILLISECONDS)
            .build()
    }

    private fun buildAuthContext(): AuthenticationContext {
        return if (config.anonymousAccess) {
            AuthenticationContext.anonymous()
        } else {
            AuthenticationContext(
                config.username,
                config.password.toCharArray(),
                config.domain.ifEmpty { null },
            )
        }
    }

    private fun buildRemoteFilePath(fileName: String): String {
        val dir = config.fullRemotePath
        return if (dir.isEmpty()) fileName else "$dir/$fileName"
    }

    /**
     * 错误分类：将异常映射为用户友好的中文错误消息。
     */
    private fun classifyError(e: Exception): String {
        val msg = e.message?.lowercase() ?: ""
        return when {
            msg.contains("timeout") || msg.contains("timed out") ->
                "连接超时: 服务器 ${config.serverAddress}:${config.port} 无响应"
            msg.contains("connection refused") || msg.contains("connect") ->
                "端口连接失败: ${config.serverAddress}:${config.port}"
            msg.contains("authentication") || msg.contains("logon failure") || msg.contains("access denied") ->
                "认证失败: 用户名或密码错误"
            msg.contains("share") || msg.contains("not found") ->
                "共享名不存在: ${config.shareName}"
            msg.contains("permission") || msg.contains("access denied") ->
                "无写入权限"
            msg.contains("unknown host") || msg.contains("no route") ->
                "服务器不可达: ${config.serverAddress}"
            else -> "连接错误: ${e.message ?: "未知错误"}"
        }
    }

    companion object {
        /** 初始化 BouncyCastle 安全提供者（需在 Application.onCreate 中调用） */
        fun initSecurityProvider() {
            try {
                Security.removeProvider("BC")
                Security.addProvider(BouncyCastleProvider())
            } catch (_: Exception) {
                // 可能已经注册
            }
        }
    }
}

/**
 * SMB 异常封装。
 */
class SmbException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)
