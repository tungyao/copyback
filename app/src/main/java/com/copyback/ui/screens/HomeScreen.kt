package com.copyback.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.copyback.data.model.BackupMode
import com.copyback.viewmodel.BackupViewModel

/**
 * 首页。
 * 显示当前备份目标、备份模式、上次备份结果、开始备份入口。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToConfig: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToProgress: () -> Unit,
    onNavigateToResult: () -> Unit,
    backupViewModel: BackupViewModel = viewModel(),
) {
    val progressState by backupViewModel.progressState.collectAsState()
    val lastResult by backupViewModel.lastBackupResult.collectAsState()
    val settings by backupViewModel.backupSettings.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("CopyBack") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
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
            // 当前备份模式卡片
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                ),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "备份模式",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = settings.backupMode.label,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    if (settings.backupMode == BackupMode.DATE_RANGE) {
                        Text(
                            text = "时间范围模式",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                        )
                    }
                }
            }

            // 上次备份结果卡片
            val result = lastResult
            if (result != null && result.status != com.copyback.data.model.BackupStatus.IDLE) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onNavigateToResult,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    ),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "上次备份",
                            style = MaterialTheme.typography.labelMedium,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                        ) {
                            StatItem("扫描", "${result.totalScanned}")
                            StatItem("上传", "${result.uploadedCount}")
                            StatItem("跳过", "${result.skippedCount}")
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                        ) {
                            StatItem("重命名", "${result.renamedCount}")
                            StatItem("失败", "${result.failedCount}")
                            StatItem("用时", result.displayElapsed)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // 开始备份按钮
            Button(
                onClick = {
                    backupViewModel.resetState()
                    onNavigateToProgress()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                enabled = progressState.status == com.copyback.data.model.BackupStatus.IDLE,
            ) {
                Icon(Icons.Default.CloudUpload, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("开始备份", style = MaterialTheme.typography.titleMedium)
            }

            // 导航按钮行
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = onNavigateToConfig,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Default.Settings, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("SMB 配置")
                }
                OutlinedButton(
                    onClick = onNavigateToSettings,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Default.Tune, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("备份设置")
                }
            }
        }
    }
}

/** 统计项小组件 */
@Composable
private fun StatItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f),
        )
    }
}
