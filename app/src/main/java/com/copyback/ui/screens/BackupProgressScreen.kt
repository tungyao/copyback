package com.copyback.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.copyback.data.model.BackupStatus
import com.copyback.data.model.LocalImageInfo
import com.copyback.viewmodel.BackupViewModel

/**
 * 备份进度页面。
 * 实时显示扫描、上传进度，支持取消操作。
 * 进入时自动检查并请求存储权限，权限就绪后开始扫描+备份。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupProgressScreen(
    onNavigateBack: () -> Unit,
    onBackupComplete: () -> Unit,
    backupViewModel: BackupViewModel = viewModel(),
) {
    val context = LocalContext.current
    val progressState by backupViewModel.progressState.collectAsState()
    val isScanning by backupViewModel.isScanning.collectAsState()
    val backupResult by backupViewModel.backupResult.collectAsState()

    // 需要请求的权限列表
    val requiredPermissions = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(Manifest.permission.READ_MEDIA_IMAGES)
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }

    var permissionDenied by remember { mutableStateOf(false) }
    var backupTriggered by remember { mutableStateOf(false) }

    // 权限请求启动器
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { grantedMap ->
        val allGranted = requiredPermissions.all { grantedMap[it] == true }
        if (allGranted) {
            permissionDenied = false
            // 权限就绪，开始备份
            startBackupFlow(backupViewModel, backupTriggered)
        } else {
            permissionDenied = true
        }
    }

    // 检查权限并触发扫描
    LaunchedEffect(Unit) {
        val allGranted = requiredPermissions.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
        if (allGranted) {
            startBackupFlow(backupViewModel, backupTriggered)
        } else {
            permissionLauncher.launch(requiredPermissions)
        }
    }

    // 监听完成状态
    LaunchedEffect(backupResult) {
        val result = backupResult
        if (result != null && (
                    result.status == BackupStatus.COMPLETED ||
                            result.status == BackupStatus.FAILED ||
                            result.status == BackupStatus.CANCELLED
                    )
        ) {
            onBackupComplete()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("备份进度") },
                navigationIcon = {
                    IconButton(onClick = {
                        backupViewModel.cancelBackup()
                        onNavigateBack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // 权限被拒绝提示
            if (permissionDenied) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                    ),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(
                            Icons.Default.Lock,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(48.dp),
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "需要存储权限才能扫描图片",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "请在系统设置中授予「照片和媒体」或「存储」权限后重试。",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedButton(onClick = onNavigateBack) {
                                Text("返回")
                            }
                            Button(onClick = { permissionLauncher.launch(requiredPermissions) }) {
                                Text("重新授权")
                            }
                        }
                    }
                }
                return@Column
            }

            // 当前阶段标签
            val phaseLabel = when {
                progressState.status == BackupStatus.SCANNING -> "扫描中"
                progressState.status == BackupStatus.UPLOADING -> "上传中"
                progressState.status == BackupStatus.COMPLETED -> "已完成"
                progressState.status == BackupStatus.FAILED -> "失败"
                progressState.status == BackupStatus.CANCELLED -> "已取消"
                else -> "准备中"
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                ),
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        when (progressState.status) {
                            BackupStatus.COMPLETED -> Icons.Default.CheckCircle
                            BackupStatus.FAILED -> Icons.Default.Error
                            BackupStatus.CANCELLED -> Icons.Default.Cancel
                            else -> Icons.Default.CloudUpload
                        },
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(32.dp),
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            phaseLabel,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        if (progressState.errorMessage != null) {
                            Text(
                                progressState.errorMessage!!,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            }

            // 总体进度条
            if (progressState.totalEligible > 0) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("总体进度", style = MaterialTheme.typography.labelMedium)
                        Spacer(modifier = Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { progressState.overallProgress },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "${(progressState.overallProgress * 100).toInt()}% (${progressState.uploadedCount + progressState.skippedCount + progressState.renamedCount + progressState.failedCount}/${progressState.totalEligible})",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }

            // 当前文件
            if (progressState.currentFileName.isNotBlank()) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("当前文件", style = MaterialTheme.typography.labelMedium)
                        Text(
                            progressState.currentFileName,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                        )
                        if (progressState.currentFileBytesTotal > 0) {
                            Spacer(modifier = Modifier.height(4.dp))
                            LinearProgressIndicator(
                                progress = { progressState.currentFileProgress },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }

            // 统计数字
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("统计", style = MaterialTheme.typography.labelMedium)
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                    ) {
                        StatCell("扫描", progressState.totalScanned)
                        StatCell("符合条件", progressState.totalEligible)
                        StatCell("已上传", progressState.uploadedCount)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                    ) {
                        StatCell("已跳过", progressState.skippedCount)
                        StatCell("已重命名", progressState.renamedCount)
                        StatCell("失败", progressState.failedCount)
                    }
                }
            }

            // 速度和时间
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column {
                        Text("速度", style = MaterialTheme.typography.labelSmall)
                        Text(
                            progressState.displaySpeed,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("已用时间", style = MaterialTheme.typography.labelSmall)
                        Text(
                            progressState.displayElapsed,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 取消按钮
            if (progressState.status == BackupStatus.SCANNING ||
                progressState.status == BackupStatus.UPLOADING
            ) {
                OutlinedButton(
                    onClick = {
                        backupViewModel.cancelBackup()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                ) {
                    Icon(Icons.Default.Cancel, null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("取消备份")
                }
            }

            // 完成后的操作按钮
            if (progressState.status == BackupStatus.COMPLETED ||
                progressState.status == BackupStatus.FAILED ||
                progressState.status == BackupStatus.CANCELLED
            ) {
                Button(
                    onClick = onBackupComplete,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("查看结果")
                }
            }
        }
    }
}

/**
 * 触发扫描+备份流程。
 */
private fun startBackupFlow(
    backupViewModel: BackupViewModel,
    alreadyTriggered: Boolean,
) {
    if (alreadyTriggered) return
    backupViewModel.startScan { images ->
        backupViewModel.startBackup(images)
    }
}

@Composable
private fun StatCell(label: String, value: Int) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            "$value",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
