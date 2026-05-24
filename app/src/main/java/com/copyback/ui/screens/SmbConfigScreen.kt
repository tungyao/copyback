package com.copyback.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.copyback.data.model.SmbProtocolVersion
import com.copyback.viewmodel.SmbConfigViewModel
import com.copyback.viewmodel.TestResult
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * SMB 配置页面。
 * 完整填写 SMB 连接信息，支持测试连接、保存、清空、历史记录。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmbConfigScreen(
    onNavigateBack: () -> Unit,
    viewModel: SmbConfigViewModel = viewModel(),
) {
    val config by viewModel.config.collectAsState()
    val testResult by viewModel.testResult.collectAsState()
    val isTesting by viewModel.isTesting.collectAsState()
    val connectionHistory by viewModel.connectionHistory.collectAsState()
    val saveSuccess by viewModel.saveSuccess.collectAsState()

    var passwordVisible by remember { mutableStateOf(false) }
    var showProtocolPicker by remember { mutableStateOf(false) }

    // 保存成功提示
    LaunchedEffect(saveSuccess) {
        if (saveSuccess) {
            kotlinx.coroutines.delay(2000)
            viewModel.resetSaveSuccess()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("SMB 配置") },
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
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // ---- 服务器信息 ----
            SectionHeader("服务器信息")

            OutlinedTextField(
                value = config.serverAddress,
                onValueChange = viewModel::updateServerAddress,
                label = { Text("服务器地址") },
                placeholder = { Text("192.168.1.10") },
                leadingIcon = { Icon(Icons.Default.Dns, null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = if (config.port == 445) "" else config.port.toString(),
                onValueChange = viewModel::updatePort,
                label = { Text("端口") },
                placeholder = { Text("445") },
                leadingIcon = { Icon(Icons.Default.Tag, null) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = config.shareName,
                onValueChange = viewModel::updateShareName,
                label = { Text("共享名") },
                placeholder = { Text("backup") },
                leadingIcon = { Icon(Icons.Default.FolderShared, null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = config.remoteDirectory,
                onValueChange = viewModel::updateRemoteDirectory,
                label = { Text("远程目录") },
                placeholder = { Text("/photos/android") },
                leadingIcon = { Icon(Icons.Default.Folder, null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(4.dp))

            // ---- 认证信息 ----
            SectionHeader("认证信息")

            // 匿名访问开关
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("匿名访问", style = MaterialTheme.typography.bodyLarge)
                Switch(
                    checked = config.anonymousAccess,
                    onCheckedChange = viewModel::updateAnonymousAccess,
                )
            }

            if (!config.anonymousAccess) {
                OutlinedTextField(
                    value = config.username,
                    onValueChange = viewModel::updateUsername,
                    label = { Text("用户名") },
                    placeholder = { Text("user") },
                    leadingIcon = { Icon(Icons.Default.Person, null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                OutlinedTextField(
                    value = config.password,
                    onValueChange = viewModel::updatePassword,
                    label = { Text("密码") },
                    placeholder = { Text("password") },
                    leadingIcon = { Icon(Icons.Default.Lock, null) },
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (passwordVisible) "隐藏密码" else "显示密码",
                            )
                        }
                    },
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                OutlinedTextField(
                    value = config.domain,
                    onValueChange = viewModel::updateDomain,
                    label = { Text("域名（可选）") },
                    placeholder = { Text("WORKGROUP") },
                    leadingIcon = { Icon(Icons.Default.Domain, null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                // 保存密码开关
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        "保存密码",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Switch(
                        checked = config.savePassword,
                        onCheckedChange = viewModel::updateSavePassword,
                    )
                }
                if (config.savePassword) {
                    Text(
                        "密码将使用 Android Keystore 加密存储",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // ---- 高级设置 ----
            SectionHeader("高级设置")

            // 协议版本选择
            OutlinedCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showProtocolPicker = true },
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("SMB 协议版本", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        config.protocolVersion.label,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            // 协议版本选择对话框
            if (showProtocolPicker) {
                AlertDialog(
                    onDismissRequest = { showProtocolPicker = false },
                    title = { Text("选择 SMB 协议版本") },
                    text = {
                        Column {
                            SmbProtocolVersion.entries.forEach { version ->
                                TextButton(
                                    onClick = {
                                        viewModel.updateProtocolVersion(version)
                                        showProtocolPicker = false
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text(
                                        version.label,
                                        fontWeight = if (version == config.protocolVersion) FontWeight.Bold else FontWeight.Normal,
                                    )
                                }
                            }
                        }
                    },
                    confirmButton = {},
                )
            }

            // 超时设置
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = "${config.connectionTimeoutMs / 1000}",
                    onValueChange = { viewModel.updateConnectionTimeout((it.toLongOrNull() ?: 10) * 1000) },
                    label = { Text("连接超时(秒)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = "${config.readTimeoutMs / 1000}",
                    onValueChange = { viewModel.updateReadTimeout((it.toLongOrNull() ?: 30) * 1000) },
                    label = { Text("读写超时(秒)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // ---- 测试结果 ----
            when (val result = testResult) {
                is TestResult.Idle -> {}
                is TestResult.Testing -> {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Text("正在测试连接...", style = MaterialTheme.typography.bodyMedium)
                }
                is TestResult.Success -> {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(result.message)
                        }
                    }
                }
                is TestResult.Failure -> {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Default.Error,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                result.message,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // ---- 连接历史 ----
            if (connectionHistory.isNotEmpty()) {
                SectionHeader("最近连接")
                connectionHistory.forEach { entry ->
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.loadFromHistory(entry) },
                        tonalElevation = 1.dp,
                        shape = MaterialTheme.shapes.small,
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Default.History, null, tint = MaterialTheme.colorScheme.outline)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(entry.address, modifier = Modifier.weight(1f))
                            Text(
                                SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(entry.timestamp)),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline,
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // ---- 操作按钮 ----
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = viewModel::testConnection,
                    enabled = !isTesting && config.serverAddress.isNotBlank(),
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Default.NetworkCheck, null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("测试连接")
                }
                FilledTonalButton(
                    onClick = viewModel::clearConfig,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Default.DeleteSweep, null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("清空")
                }
            }

            Button(
                onClick = viewModel::saveConfig,
                modifier = Modifier.fillMaxWidth(),
                enabled = config.serverAddress.isNotBlank(),
            ) {
                Icon(Icons.Default.Save, null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("保存配置")
            }

            if (saveSuccess) {
                Text(
                    "✅ 配置已保存",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
    )
}
