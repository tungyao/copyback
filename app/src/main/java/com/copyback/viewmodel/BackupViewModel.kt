package com.copyback.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.copyback.data.model.BackupMode
import com.copyback.data.model.BackupProgressState
import com.copyback.data.model.BackupResult
import com.copyback.data.model.BackupSettings
import com.copyback.data.model.BackupStatus
import com.copyback.data.model.FailedItem
import com.copyback.data.model.ImageSourceType
import com.copyback.data.model.LocalImageInfo
import com.copyback.data.repository.BackupRepository
import com.copyback.data.repository.SettingsRepository
import com.copyback.data.scanner.MediaStoreImageScanner
import com.copyback.data.scanner.SafImageScanner
import com.copyback.data.smb.SmbBackupRepository
import com.copyback.data.smb.SmbClientManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 备份 ViewModel。
 * 管理扫描、备份执行、进度跟踪。
 */
class BackupViewModel(application: Application) : AndroidViewModel(application) {

    private val settingsRepository = SettingsRepository(application)
    private val mediaStoreScanner = MediaStoreImageScanner(application.contentResolver)
    private val safScanner = SafImageScanner(application.contentResolver)

    // --- 状态 ---

    private val _progressState = MutableStateFlow(BackupProgressState())
    val progressState: StateFlow<BackupProgressState> = _progressState.asStateFlow()

    private val _backupResult = MutableStateFlow<BackupResult?>(null)
    val backupResult: StateFlow<BackupResult?> = _backupResult.asStateFlow()

    private val _backupSettings = MutableStateFlow(BackupSettings())
    val backupSettings: StateFlow<BackupSettings> = _backupSettings.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    /** 最近一次备份结果摘要（用于首页显示） */
    private val _lastBackupResult = MutableStateFlow<BackupResult?>(null)
    val lastBackupResult: StateFlow<BackupResult?> = _lastBackupResult.asStateFlow()

    private var backupJob: Job? = null
    private var smbConfig = com.copyback.data.model.SmbConfig()

    /** 当前备份仓库 */
    private var backupRepository: BackupRepository? = null

    init {
        // 加载保存的设置
        viewModelScope.launch {
            settingsRepository.backupSettingsFlow.collect { settings ->
                _backupSettings.value = settings
            }
        }
        viewModelScope.launch {
            settingsRepository.smbConfigFlow.collect { config ->
                smbConfig = config
            }
        }
    }

    // --- 备份设置 ---

    fun setBackupMode(mode: BackupMode) {
        _backupSettings.update { it.copy(backupMode = mode) }
    }

    fun setDateRange(startDate: Long?, endDate: Long?) {
        _backupSettings.update { it.copy(startDate = startDate, endDate = endDate) }
    }

    fun setImageSourceType(type: ImageSourceType) {
        _backupSettings.update { it.copy(imageSourceType = type) }
    }

    fun setSafTreeUri(uri: Uri?) {
        _backupSettings.update { it.copy(safTreeUri = uri) }
    }

    fun setRememberSource(remember: Boolean) {
        _backupSettings.update { it.copy(rememberSource = remember) }
    }

    fun saveSettings() {
        viewModelScope.launch {
            settingsRepository.saveBackupSettings(_backupSettings.value)
        }
    }

    /** 更新 SMB 配置引用（从 SmbConfigViewModel 获取后调用） */
    fun updateSmbConfig(config: com.copyback.data.model.SmbConfig) {
        smbConfig = config
    }

    // --- 扫描 ---

    /**
     * 扫描本地图片元数据。
     * 根据图片来源设置选择扫描器。
     */
    fun startScan(onComplete: (List<LocalImageInfo>) -> Unit) {
        viewModelScope.launch {
            _isScanning.value = true
            _progressState.update { it.copy(status = BackupStatus.SCANNING) }

            try {
                val settings = _backupSettings.value
                val images = when (settings.imageSourceType) {
                    ImageSourceType.MEDIA_STORE -> {
                        if (settings.backupMode == BackupMode.DATE_RANGE &&
                            settings.startDate != null && settings.endDate != null
                        ) {
                            mediaStoreScanner.scanImagesByDateRange(
                                settings.startDate!!,
                                settings.endDate!!,
                            )
                        } else {
                            mediaStoreScanner.scanAllImages()
                        }
                    }
                    ImageSourceType.SAF -> {
                        val treeUri = settings.safTreeUri
                        if (treeUri != null) {
                            if (settings.backupMode == BackupMode.DATE_RANGE &&
                                settings.startDate != null && settings.endDate != null
                            ) {
                                safScanner.scanDirectoryByDateRange(
                                    treeUri,
                                    settings.startDate!!,
                                    settings.endDate!!,
                                )
                            } else {
                                safScanner.scanDirectory(treeUri)
                            }
                        } else {
                            emptyList()
                        }
                    }
                }

                _progressState.update {
                    it.copy(
                        totalScanned = images.size,
                        totalEligible = images.size,
                        status = BackupStatus.IDLE,
                    )
                }
                onComplete(images)
            } catch (e: Exception) {
                _progressState.update {
                    it.copy(
                        status = BackupStatus.FAILED,
                        errorMessage = "扫描失败: ${e.message}",
                    )
                }
            } finally {
                _isScanning.value = false
            }
        }
    }

    // --- 备份执行 ---

    /**
     * 开始备份。
     * 创建 SMB 连接并执行备份流程。
     */
    fun startBackup(images: List<LocalImageInfo>) {
        backupJob?.cancel()

        val clientManager = SmbClientManager(smbConfig)
        backupRepository = SmbBackupRepository(clientManager)

        backupJob = viewModelScope.launch {
            _progressState.update {
                it.copy(
                    status = BackupStatus.SCANNING,
                    totalScanned = images.size,
                    totalEligible = 0,
                )
            }

            backupRepository!!.executeBackup(images, _backupSettings.value)
                .catch { e ->
                    _progressState.update {
                        it.copy(
                            status = BackupStatus.FAILED,
                            errorMessage = e.message ?: "备份失败",
                        )
                    }
                }
                .collectLatest { result ->
                    // 更新进度状态
                    _progressState.update { state ->
                        state.copy(
                            status = result.status,
                            totalScanned = result.totalScanned,
                            totalEligible = result.totalEligible,
                            uploadedCount = result.uploadedCount,
                            skippedCount = result.skippedCount,
                            renamedCount = result.renamedCount,
                            failedCount = result.failedCount,
                            elapsedMs = result.elapsedMs,
                        )
                    }

                    // 更新最终结果
                    _backupResult.value = result

                    if (result.status == BackupStatus.COMPLETED ||
                        result.status == BackupStatus.FAILED ||
                        result.status == BackupStatus.CANCELLED
                    ) {
                        _lastBackupResult.value = result
                    }
                }
        }
    }

    /** 取消备份 */
    fun cancelBackup() {
        backupJob?.cancel()
        _progressState.update {
            it.copy(status = BackupStatus.CANCELLED)
        }
    }

    /** 重置状态 */
    fun resetState() {
        backupJob?.cancel()
        backupJob = null
        _progressState.value = BackupProgressState()
        _backupResult.value = null
        backupRepository = null
    }
}
