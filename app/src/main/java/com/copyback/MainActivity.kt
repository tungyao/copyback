package com.copyback

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import com.copyback.data.smb.SmbClientManager
import com.copyback.navigation.AppNavHost
import com.copyback.ui.theme.CopyBackTheme

/**
 * 主 Activity。
 * CopyBack 应用的唯一 Activity，使用 Jetpack Compose 渲染全部 UI。
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 初始化 BouncyCastle 安全提供者（SMBJ 依赖）
        SmbClientManager.initSecurityProvider()

        enableEdgeToEdge()

        setContent {
            CopyBackTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val navController = rememberNavController()
                    AppNavHost(navController = navController)
                }
            }
        }
    }
}
