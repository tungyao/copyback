package com.copyback.navigation

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.copyback.ui.screens.*
import com.copyback.viewmodel.BackupViewModel

/**
 * 应用导航图。
 * 定义所有页面路由和导航逻辑。
 */
object Routes {
    const val HOME = "home"
    const val SMB_CONFIG = "smb_config"
    const val BACKUP_SETTINGS = "backup_settings"
    const val BACKUP_PROGRESS = "backup_progress"
    const val BACKUP_RESULT = "backup_result"
}

@Composable
fun AppNavHost(navController: NavHostController) {
    // 在 NavHost 层级共享 BackupViewModel，确保跨页面状态一致
    val backupViewModel: BackupViewModel = viewModel()

    NavHost(
        navController = navController,
        startDestination = Routes.HOME,
    ) {
        // 首页
        composable(Routes.HOME) {
            HomeScreen(
                backupViewModel = backupViewModel,
                onNavigateToConfig = {
                    navController.navigate(Routes.SMB_CONFIG)
                },
                onNavigateToSettings = {
                    navController.navigate(Routes.BACKUP_SETTINGS)
                },
                onNavigateToProgress = {
                    navController.navigate(Routes.BACKUP_PROGRESS)
                },
                onNavigateToResult = {
                    navController.navigate(Routes.BACKUP_RESULT)
                },
            )
        }

        // SMB 配置页
        composable(Routes.SMB_CONFIG) {
            SmbConfigScreen(
                onNavigateBack = {
                    navController.popBackStack()
                },
            )
        }

        // 备份设置页
        composable(Routes.BACKUP_SETTINGS) {
            BackupSettingsScreen(
                onNavigateBack = {
                    navController.popBackStack()
                },
            )
        }

        // 备份进度页
        composable(Routes.BACKUP_PROGRESS) {
            BackupProgressScreen(
                backupViewModel = backupViewModel,
                onNavigateBack = {
                    navController.popBackStack(Routes.HOME, inclusive = false)
                },
                onBackupComplete = {
                    navController.navigate(Routes.BACKUP_RESULT) {
                        popUpTo(Routes.HOME) { inclusive = false }
                    }
                },
            )
        }

        // 备份结果页
        composable(Routes.BACKUP_RESULT) {
            BackupResultScreen(
                backupViewModel = backupViewModel,
                onNavigateBack = {
                    navController.popBackStack(Routes.HOME, inclusive = false)
                },
                onNewBackup = {
                    navController.navigate(Routes.BACKUP_PROGRESS) {
                        popUpTo(Routes.HOME) { inclusive = false }
                    }
                },
            )
        }
    }
}
