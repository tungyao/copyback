package com.copyback.data.model

/**
 * SMB 连接配置数据类。
 * 包含连接到远程 SMB 共享目录所需的所有参数。
 */
data class SmbConfig(
    val serverAddress: String = "",
    val port: Int = 445,
    val shareName: String = "",
    val remoteDirectory: String = "/",
    val username: String = "",
    val password: String = "",
    val domain: String = "",
    val anonymousAccess: Boolean = false,
    val savePassword: Boolean = false,
    val protocolVersion: SmbProtocolVersion = SmbProtocolVersion.AUTO,
    val connectionTimeoutMs: Long = 10_000L,
    val readTimeoutMs: Long = 30_000L,
    val writeTimeoutMs: Long = 30_000L,
) {
    /** 格式化显示地址 */
    val displayAddress: String
        get() = if (port == 445) "\\\\$serverAddress\\$shareName"
        else "\\\\$serverAddress:$port\\$shareName"

    /** 完整远程路径 */
    val fullRemotePath: String
        get() {
            val dir = remoteDirectory.trimStart('/').trimEnd('/')
            return if (dir.isEmpty()) "" else dir
        }
}

/**
 * SMB 协议版本枚举。
 */
enum class SmbProtocolVersion(val label: String) {
    AUTO("自动协商"),
    SMB2("SMB 2.x"),
    SMB2_02("SMB 2.0.2"),
    SMB2_1("SMB 2.1"),
    SMB3("SMB 3.x"),
    SMB3_0("SMB 3.0"),
    SMB3_02("SMB 3.0.2"),
    SMB3_1_1("SMB 3.1.1"),
}
