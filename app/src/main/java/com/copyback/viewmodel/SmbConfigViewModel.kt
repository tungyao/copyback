package com.copyback.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.copyback.data.model.SmbConfig
import com.copyback.data.model.SmbProtocolVersion
import com.copyback.data.repository.SettingsRepository
import com.copyback.data.smb.SmbClientManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * SMB 配置 ViewModel。
 * 管理 SMB 连接配置的编辑、保存、测试。
 */
class SmbConfigViewModel(application: Application) : AndroidViewModel(application) {

    private val settingsRepository = SettingsRepository(application)

    // --- 表单状态 ---

    private val _config = MutableStateFlow(SmbConfig())
    val config: StateFlow<SmbConfig> = _config.asStateFlow()

    private val _testResult = MutableStateFlow<TestResult>(TestResult.Idle)
    val testResult: StateFlow<TestResult> = _testResult.asStateFlow()

    private val _isTesting = MutableStateFlow(false)
    val isTesting: StateFlow<Boolean> = _isTesting.asStateFlow()

    private val _connectionHistory = MutableStateFlow<List<SettingsRepository.ConnectionHistoryEntry>>(emptyList())
    val connectionHistory: StateFlow<List<SettingsRepository.ConnectionHistoryEntry>> = _connectionHistory.asStateFlow()

    private val _saveSuccess = MutableStateFlow(false)
    val saveSuccess: StateFlow<Boolean> = _saveSuccess.asStateFlow()

    init {
        // 加载保存的配置
        viewModelScope.launch {
            val saved = settingsRepository.getSmbConfig()
            _config.value = saved
        }
        // 加载连接历史
        viewModelScope.launch {
            settingsRepository.connectionHistoryFlow.collect { history ->
                _connectionHistory.value = history
            }
        }
    }

    // --- 字段更新 ---

    fun updateServerAddress(value: String) {
        _config.update { it.copy(serverAddress = value) }
    }

    fun updatePort(value: String) {
        val port = value.toIntOrNull() ?: 445
        _config.update { it.copy(port = port) }
    }

    fun updateShareName(value: String) {
        _config.update { it.copy(shareName = value) }
    }

    fun updateRemoteDirectory(value: String) {
        _config.update { it.copy(remoteDirectory = value) }
    }

    fun updateUsername(value: String) {
        _config.update { it.copy(username = value) }
    }

    fun updatePassword(value: String) {
        _config.update { it.copy(password = value) }
    }

    fun updateDomain(value: String) {
        _config.update { it.copy(domain = value) }
    }

    fun updateAnonymousAccess(value: Boolean) {
        _config.update { it.copy(anonymousAccess = value) }
    }

    fun updateSavePassword(value: Boolean) {
        _config.update { it.copy(savePassword = value) }
    }

    fun updateProtocolVersion(version: SmbProtocolVersion) {
        _config.update { it.copy(protocolVersion = version) }
    }

    fun updateConnectionTimeout(value: Long) {
        _config.update { it.copy(connectionTimeoutMs = value) }
    }

    fun updateReadTimeout(value: Long) {
        _config.update { it.copy(readTimeoutMs = value) }
    }

    fun updateWriteTimeout(value: Long) {
        _config.update { it.copy(writeTimeoutMs = value) }
    }

    /** 从历史记录加载配置 */
    fun loadFromHistory(entry: SettingsRepository.ConnectionHistoryEntry) {
        // 仅填充地址，用户仍需手动填写认证信息
        _config.update { it.copy(serverAddress = entry.address) }
    }

    // --- 操作 ---

    /** 测试连接 */
    fun testConnection() {
        viewModelScope.launch {
            _isTesting.value = true
            _testResult.value = TestResult.Testing

            val clientManager = SmbClientManager(_config.value)
            val error = clientManager.testConnection()

            _testResult.value = if (error == null) {
                TestResult.Success("连接成功！服务器可达，共享存在，有写入权限。")
            } else {
                TestResult.Failure(error)
            }
            _isTesting.value = false
        }
    }

    /** 保存配置 */
    fun saveConfig() {
        viewModelScope.launch {
            settingsRepository.saveSmbConfig(_config.value)
            _saveSuccess.value = true
        }
    }

    /** 清空配置 */
    fun clearConfig() {
        viewModelScope.launch {
            settingsRepository.clearSmbConfig()
            _config.value = SmbConfig()
            _testResult.value = TestResult.Idle
        }
    }

    /** 重置保存成功标志 */
    fun resetSaveSuccess() {
        _saveSuccess.value = false
    }

    /** 清除测试结果 */
    fun clearTestResult() {
        _testResult.value = TestResult.Idle
    }
}

/**
 * 连接测试结果密封类。
 */
sealed class TestResult {
    data object Idle : TestResult()
    data object Testing : TestResult()
    data class Success(val message: String) : TestResult()
    data class Failure(val message: String) : TestResult()
}
