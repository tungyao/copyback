package com.copyback.data.repository

import android.content.Context
import android.content.SharedPreferences
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import com.copyback.data.model.BackupMode
import com.copyback.data.model.BackupSettings
import com.copyback.data.model.ImageSourceType
import com.copyback.data.model.SmbConfig
import com.copyback.data.model.SmbProtocolVersion
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** DataStore 扩展属性 */
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "copyback_settings")

/**
 * 应用设置仓库。
 *
 * 持久化：
 * - SMB 连接配置（DataStore）
 * - 密码（EncryptedSharedPreferences，仅在用户选择保存时）
 * - 连接历史（DataStore，最近 10 条）
 * - 备份设置（DataStore）
 */
class SettingsRepository(private val context: Context) {

    // --- Key 定义 ---

    private object Keys {
        // SMB 配置
        val SERVER_ADDRESS = stringPreferencesKey("smb_server_address")
        val PORT = intPreferencesKey("smb_port")
        val SHARE_NAME = stringPreferencesKey("smb_share_name")
        val REMOTE_DIRECTORY = stringPreferencesKey("smb_remote_directory")
        val USERNAME = stringPreferencesKey("smb_username")
        val DOMAIN = stringPreferencesKey("smb_domain")
        val ANONYMOUS_ACCESS = booleanPreferencesKey("smb_anonymous_access")
        val SAVE_PASSWORD = booleanPreferencesKey("smb_save_password")
        val PROTOCOL_VERSION = stringPreferencesKey("smb_protocol_version")
        val CONNECTION_TIMEOUT = longPreferencesKey("smb_connection_timeout")
        val READ_TIMEOUT = longPreferencesKey("smb_read_timeout")
        val WRITE_TIMEOUT = longPreferencesKey("smb_write_timeout")

        // 备份设置
        val BACKUP_MODE = stringPreferencesKey("backup_mode")
        val START_DATE = longPreferencesKey("backup_start_date")
        val END_DATE = longPreferencesKey("backup_end_date")
        val IMAGE_SOURCE_TYPE = stringPreferencesKey("image_source_type")
        val REMEMBER_SOURCE = booleanPreferencesKey("remember_source")

        // 连接历史（JSON 数组字符串）
        val CONNECTION_HISTORY = stringPreferencesKey("connection_history")
    }

    // EncryptedSharedPreferences 用于密码存储
    private val encryptedPrefs: SharedPreferences by lazy {
        val masterKey = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
        EncryptedSharedPreferences.create(
            "copyback_secure_prefs",
            masterKey,
            context,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    // --- SMB 配置读写 ---

    /** 获取保存的 SMB 配置 Flow */
    val smbConfigFlow: Flow<SmbConfig> = context.dataStore.data.map { prefs ->
        SmbConfig(
            serverAddress = prefs[Keys.SERVER_ADDRESS] ?: "",
            port = prefs[Keys.PORT] ?: 445,
            shareName = prefs[Keys.SHARE_NAME] ?: "",
            remoteDirectory = prefs[Keys.REMOTE_DIRECTORY] ?: "/",
            username = prefs[Keys.USERNAME] ?: "",
            password = loadPassword(),
            domain = prefs[Keys.DOMAIN] ?: "",
            anonymousAccess = prefs[Keys.ANONYMOUS_ACCESS] ?: false,
            savePassword = prefs[Keys.SAVE_PASSWORD] ?: false,
            protocolVersion = parseProtocolVersion(prefs[Keys.PROTOCOL_VERSION]),
            connectionTimeoutMs = prefs[Keys.CONNECTION_TIMEOUT] ?: 10_000L,
            readTimeoutMs = prefs[Keys.READ_TIMEOUT] ?: 30_000L,
            writeTimeoutMs = prefs[Keys.WRITE_TIMEOUT] ?: 30_000L,
        )
    }

    /** 获取保存的 SMB 配置（挂起） */
    suspend fun getSmbConfig(): SmbConfig {
        return smbConfigFlow.first()
    }

    /** 保存 SMB 配置 */
    suspend fun saveSmbConfig(config: SmbConfig) {
        context.dataStore.edit { prefs ->
            prefs[Keys.SERVER_ADDRESS] = config.serverAddress
            prefs[Keys.PORT] = config.port
            prefs[Keys.SHARE_NAME] = config.shareName
            prefs[Keys.REMOTE_DIRECTORY] = config.remoteDirectory
            prefs[Keys.USERNAME] = config.username
            prefs[Keys.DOMAIN] = config.domain
            prefs[Keys.ANONYMOUS_ACCESS] = config.anonymousAccess
            prefs[Keys.SAVE_PASSWORD] = config.savePassword
            prefs[Keys.PROTOCOL_VERSION] = config.protocolVersion.name
            prefs[Keys.CONNECTION_TIMEOUT] = config.connectionTimeoutMs
            prefs[Keys.READ_TIMEOUT] = config.readTimeoutMs
            prefs[Keys.WRITE_TIMEOUT] = config.writeTimeoutMs
        }
        savePassword(config)
        addConnectionHistory(config)
    }

    /** 清空配置 */
    suspend fun clearSmbConfig() {
        context.dataStore.edit { it.clear() }
        encryptedPrefs.edit().clear().apply()
    }

    // --- 密码管理 ---

    /** 加载密码 */
    private fun loadPassword(): String {
        return encryptedPrefs.getString("smb_password", "") ?: ""
    }

    /** 保存密码 */
    private fun savePassword(config: SmbConfig) {
        if (config.savePassword) {
            encryptedPrefs.edit().putString("smb_password", config.password).apply()
        } else {
            encryptedPrefs.edit().remove("smb_password").apply()
        }
    }

    // --- 连接历史 ---

    /** 最近连接历史记录 */
    data class ConnectionHistoryEntry(
        val address: String,
        val timestamp: Long = System.currentTimeMillis(),
    )

    /** 添加连接历史（最近 10 条） */
    private suspend fun addConnectionHistory(config: SmbConfig) {
        if (config.serverAddress.isBlank()) return
        context.dataStore.edit { prefs ->
            val history = loadHistoryFromPrefs(prefs[Keys.CONNECTION_HISTORY]).toMutableList()
            // 移除重复项
            history.removeAll { it.address == config.serverAddress }
            // 添加到头部
            history.add(0, ConnectionHistoryEntry(config.serverAddress))
            // 保留最近 10 条
            val trimmed = history.take(10)
            prefs[Keys.CONNECTION_HISTORY] = serializeHistory(trimmed)
        }
    }

    /** 获取连接历史 Flow */
    val connectionHistoryFlow: Flow<List<ConnectionHistoryEntry>> = context.dataStore.data.map { prefs ->
        loadHistoryFromPrefs(prefs[Keys.CONNECTION_HISTORY])
    }

    /** 挂起获取连接历史 */
    suspend fun getConnectionHistory(): List<ConnectionHistoryEntry> {
        return connectionHistoryFlow.first()
    }

    /** 清除连接历史 */
    suspend fun clearConnectionHistory() {
        context.dataStore.edit { prefs ->
            prefs[Keys.CONNECTION_HISTORY] = "[]"
        }
    }

    // --- 备份设置 ---

    val backupSettingsFlow: Flow<BackupSettings> = context.dataStore.data.map { prefs ->
        BackupSettings(
            backupMode = parseBackupMode(prefs[Keys.BACKUP_MODE]),
            startDate = prefs[Keys.START_DATE],
            endDate = prefs[Keys.END_DATE],
            imageSourceType = parseImageSourceType(prefs[Keys.IMAGE_SOURCE_TYPE]),
            rememberSource = prefs[Keys.REMEMBER_SOURCE] ?: false,
        )
    }

    suspend fun saveBackupSettings(settings: BackupSettings) {
        context.dataStore.edit { prefs ->
            prefs[Keys.BACKUP_MODE] = settings.backupMode.name
            if (settings.startDate != null) prefs[Keys.START_DATE] = settings.startDate
            if (settings.endDate != null) prefs[Keys.END_DATE] = settings.endDate
            prefs[Keys.IMAGE_SOURCE_TYPE] = settings.imageSourceType.name
            prefs[Keys.REMEMBER_SOURCE] = settings.rememberSource
        }
    }

    // --- 辅助方法 ---

    private fun parseProtocolVersion(name: String?): SmbProtocolVersion {
        return try {
            name?.let { SmbProtocolVersion.valueOf(it) } ?: SmbProtocolVersion.AUTO
        } catch (_: Exception) {
            SmbProtocolVersion.AUTO
        }
    }

    private fun parseBackupMode(name: String?): BackupMode {
        return try {
            name?.let { BackupMode.valueOf(it) } ?: BackupMode.FULL
        } catch (_: Exception) {
            BackupMode.FULL
        }
    }

    private fun parseImageSourceType(name: String?): ImageSourceType {
        return try {
            name?.let { ImageSourceType.valueOf(it) } ?: ImageSourceType.MEDIA_STORE
        } catch (_: Exception) {
            ImageSourceType.MEDIA_STORE
        }
    }

    private fun loadHistoryFromPrefs(json: String?): List<ConnectionHistoryEntry> {
        if (json.isNullOrBlank()) return emptyList()
        return try {
            // 简单的手动解析（避免引入 Gson/Moshi）
            val entries = mutableListOf<ConnectionHistoryEntry>()
            // 格式: "address1|timestamp1,address2|timestamp2"
            json.trim('[', ']').split("},{").forEach { part ->
                val clean = part.trim('{', '}', '"')
                val parts = clean.split("\",\"")
                if (parts.size >= 2) {
                    val addr = parts[0].substringAfter("address\":\"").trimEnd('"')
                    val ts = parts[1].substringAfter("timestamp\":").trimEnd('"').toLongOrNull()
                    if (addr.isNotBlank()) {
                        entries.add(ConnectionHistoryEntry(addr, ts ?: 0))
                    }
                }
            }
            entries
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun serializeHistory(history: List<ConnectionHistoryEntry>): String {
        val items = history.joinToString(",") { entry ->
            """{"address":"${entry.address}","timestamp":${entry.timestamp}}"""
        }
        return "[$items]"
    }
}
