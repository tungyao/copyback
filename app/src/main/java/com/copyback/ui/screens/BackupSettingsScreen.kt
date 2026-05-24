package com.copyback.ui.screens

import android.net.Uri
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.copyback.data.model.BackupMode
import com.copyback.data.model.ImageSourceType
import com.copyback.viewmodel.BackupViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 备份设置页面。
 * 备份模式选择、时间范围、图片来源。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupSettingsScreen(
    onNavigateBack: () -> Unit,
    backupViewModel: BackupViewModel = viewModel(),
) {
    val settings by backupViewModel.backupSettings.collectAsState()

    var showStartDatePicker by remember { mutableStateOf(false) }
    var showEndDatePicker by remember { mutableStateOf(false) }

    // SAF 目录选择
    val safLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
    ) { uri: Uri? ->
        uri?.let {
            // 持久化权限
            val flags = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            com.copyback.CopyBackApp.instance.contentResolver.takePersistableUriPermission(it, flags)
            backupViewModel.setSafTreeUri(it)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("备份设置") },
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // ---- 备份模式选择 ----
            Text(
                "备份模式",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                BackupMode.entries.forEach { mode ->
                    FilterChip(
                        selected = settings.backupMode == mode,
                        onClick = { backupViewModel.setBackupMode(mode) },
                        label = { Text(mode.label) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            // ---- 时间范围 ----
            if (settings.backupMode == BackupMode.DATE_RANGE) {
                Text(
                    "时间范围",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedButton(
                        onClick = { showStartDatePicker = true },
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Default.CalendarMonth, null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("开始日期")
                    }
                    OutlinedButton(
                        onClick = { showEndDatePicker = true },
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Default.CalendarMonth, null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("结束日期")
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                    Text(
                        "开始: ${settings.startDate?.let { dateFormat.format(Date(it)) } ?: "未选择"}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        "结束: ${settings.endDate?.let { dateFormat.format(Date(it)) } ?: "未选择"}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }

                // 开始日期选择对话框
                if (showStartDatePicker) {
                    val datePickerState = rememberDatePickerState(
                        initialSelectedDateMillis = settings.startDate ?: System.currentTimeMillis(),
                    )
                    DatePickerDialog(
                        onDismissRequest = { showStartDatePicker = false },
                        confirmButton = {
                            TextButton(onClick = {
                                backupViewModel.setDateRange(
                                    startDate = datePickerState.selectedDateMillis,
                                    endDate = settings.endDate,
                                )
                                showStartDatePicker = false
                            }) { Text("确定") }
                        },
                        dismissButton = {
                            TextButton(onClick = { showStartDatePicker = false }) { Text("取消") }
                        },
                    ) {
                        DatePicker(state = datePickerState)
                    }
                }

                // 结束日期选择对话框
                if (showEndDatePicker) {
                    val datePickerState = rememberDatePickerState(
                        initialSelectedDateMillis = settings.endDate ?: System.currentTimeMillis(),
                    )
                    DatePickerDialog(
                        onDismissRequest = { showEndDatePicker = false },
                        confirmButton = {
                            TextButton(onClick = {
                                backupViewModel.setDateRange(
                                    startDate = settings.startDate,
                                    endDate = datePickerState.selectedDateMillis,
                                )
                                showEndDatePicker = false
                            }) { Text("确定") }
                        },
                        dismissButton = {
                            TextButton(onClick = { showEndDatePicker = false }) { Text("取消") }
                        },
                    ) {
                        DatePicker(state = datePickerState)
                    }
                }
            }

            Divider()

            // ---- 图片来源 ----
            Text(
                "图片来源",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )

            ImageSourceType.entries.forEach { sourceType ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (settings.imageSourceType == sourceType)
                            MaterialTheme.colorScheme.primaryContainer
                        else
                            MaterialTheme.colorScheme.surface,
                    ),
                    onClick = {
                        backupViewModel.setImageSourceType(sourceType)
                    },
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = settings.imageSourceType == sourceType,
                            onClick = { backupViewModel.setImageSourceType(sourceType) },
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(sourceType.label, fontWeight = FontWeight.Medium)
                            Text(
                                when (sourceType) {
                                    ImageSourceType.MEDIA_STORE -> "扫描系统相册、DCIM、Pictures 目录"
                                    ImageSourceType.SAF -> "手动选择文件夹"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            // SAF 目录选择按钮
            if (settings.imageSourceType == ImageSourceType.SAF) {
                Button(
                    onClick = { safLauncher.launch(null) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.FolderOpen, null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("选择目录")
                }
                if (settings.safTreeUri != null) {
                    Text(
                        "已选择: ${settings.safTreeUri}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            Divider()

            // 记住来源
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("记住来源目录", style = MaterialTheme.typography.bodyLarge)
                Switch(
                    checked = settings.rememberSource,
                    onCheckedChange = backupViewModel::setRememberSource,
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            // 保存按钮
            Button(
                onClick = { backupViewModel.saveSettings() },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Default.Save, null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("保存设置")
            }
        }
    }
}
