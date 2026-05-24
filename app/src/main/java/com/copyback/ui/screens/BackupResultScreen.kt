package com.copyback.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.copyback.data.model.BackupStatus
import com.copyback.data.model.FailedItem
import com.copyback.viewmodel.BackupViewModel

/**
 * 备份结果页面。
 * 显示完整备份统计和失败列表。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupResultScreen(
    onNavigateBack: () -> Unit,
    onNewBackup: () -> Unit,
    backupViewModel: BackupViewModel = viewModel(),
) {
    val result by backupViewModel.backupResult.collectAsState()
    val lastResult by backupViewModel.lastBackupResult.collectAsState()

    // 使用 result 或 lastResult
    val displayResult = result ?: lastResult

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when (displayResult?.status) {
                            BackupStatus.COMPLETED -> "备份完成"
                            BackupStatus.FAILED -> "备份失败"
                            BackupStatus.CANCELLED -> "已取消"
                            else -> "备份结果"
                        }
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
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
        if (displayResult == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Text("无备份结果", style = MaterialTheme.typography.bodyLarge)
            }
            return@Scaffold
        }

        val r = displayResult

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // 状态卡片
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = when (r.status) {
                            BackupStatus.COMPLETED -> MaterialTheme.colorScheme.primaryContainer
                            BackupStatus.FAILED -> MaterialTheme.colorScheme.errorContainer
                            BackupStatus.CANCELLED -> MaterialTheme.colorScheme.surfaceVariant
                            else -> MaterialTheme.colorScheme.surface
                        },
                    ),
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            when (r.status) {
                                BackupStatus.COMPLETED -> Icons.Default.CheckCircle
                                BackupStatus.FAILED -> Icons.Default.Error
                                BackupStatus.CANCELLED -> Icons.Default.Cancel
                                else -> Icons.Default.Info
                            },
                            contentDescription = null,
                            tint = when (r.status) {
                                BackupStatus.COMPLETED -> MaterialTheme.colorScheme.primary
                                BackupStatus.FAILED -> MaterialTheme.colorScheme.error
                                else -> MaterialTheme.colorScheme.outline
                            },
                            modifier = Modifier.size(36.dp),
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                when (r.status) {
                                    BackupStatus.COMPLETED -> "备份成功"
                                    BackupStatus.FAILED -> "备份失败"
                                    BackupStatus.CANCELLED -> "已取消"
                                    else -> ""
                                },
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                "用时 ${r.displayElapsed}",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }

            // 统计明细
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("统计明细", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(12.dp))

                        StatRow("扫描总数", "${r.totalScanned}")
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                        StatRow("符合条件", "${r.totalEligible}")
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                        StatRow("成功上传", "${r.uploadedCount}", MaterialTheme.colorScheme.primary)
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                        StatRow("已跳过", "${r.skippedCount}", MaterialTheme.colorScheme.outline)
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                        StatRow("重命名后上传", "${r.renamedCount}", MaterialTheme.colorScheme.secondary)
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                        StatRow("上传失败", "${r.failedCount}", MaterialTheme.colorScheme.error)
                    }
                }
            }

            // 失败列表
            if (r.failedItems.isNotEmpty()) {
                item {
                    Text(
                        "失败详情 (${r.failedItems.size})",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error,
                    )
                }

                items(r.failedItems) { item ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                        ),
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                item.fileName,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                            )
                            Text(
                                "目标: ${item.targetFileName}",
                                style = MaterialTheme.typography.bodySmall,
                            )
                            Text(
                                "原因: ${item.reason}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            }

            // 操作按钮
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = {
                        backupViewModel.resetState()
                        onNewBackup()
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.Refresh, null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("开始新备份")
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = onNavigateBack,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.Home, null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("返回首页")
                }
            }
        }
    }
}

@Composable
private fun StatRow(
    label: String,
    value: String,
    valueColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Text(
            value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = valueColor,
        )
    }
}
