# CopyBack — Android Photo Backup to SMB

**CopyBack** 是一款轻量级的 Android 照片备份工具。它扫描设备上的本地照片并通过 SMB（Samba/Windows 共享）协议上传到远程服务器，让你的照片安全地脱离手机。

## ✨ 功能特性

- **多种扫描模式** — 全量备份所有照片，或按日期范围筛选
- **双源扫描** — 通过系统 MediaStore 扫描相册，或通过 SAF（存储访问框架）扫描任意目录树
- **SMB v2/v3 支持** — 基于 SMBJ 库，兼容主流 NAS、Samba 服务器和 Windows 共享
- **智能冲突处理** — 自动跳过远程已存在且更新的文件；若本地更新则重命名后上传（避免覆盖）
- **实时进度** — 逐文件和总进度条、上传速度、耗时统计
- **加密存储** — 密码通过 EncryptedSharedPreferences（AES-256-GCM）保存（用户可选）
- **连接测试** — 配置 SMB 后可直接测试连通性
- **Material 3 设计** — 浅色/深色主题自适应

## 🧱 技术栈

| 层 | 技术 | 版本 |
|---|---|---|
| 语言 | Kotlin | 2.1.0 |
| 构建系统 | Gradle (Kotlin DSL) | 9.0.0 |
| Android 插件 | AGP | 8.7.3 |
| UI | Jetpack Compose + Material 3 | BOM 2024.12.01 |
| 导航 | Navigation Compose | 2.8.5 |
| 架构 | MVVM (AndroidViewModel + StateFlow) | — |
| 异步 | Kotlin Coroutines | 1.9.0 |
| 持久化 | DataStore + EncryptedSharedPreferences | 1.1.1 |
| SMB 客户端 | SMBJ（hierynomus/smbj） | 0.14.0 |
| 加密 | BouncyCastle（bcprov-jdk18on） | 1.78.1 |

## 🚀 快速开始

### 前置条件

- Android Studio Ladybug (2024.2.1+) 或 IntelliJ IDEA
- JDK 17
- Android SDK (compileSdk 35)

### 构建

```bash
# 调试 APK
./gradlew assembleDebug

# 发布 APK（R8 混淆）
./gradlew assembleRelease

# 安装到设备
./gradlew installDebug
```

## 📱 使用流程

1. 配置 SMB 服务器地址、端口、共享名称和凭据 → 可选"测试连接"
2. 选择备份模式：**全量** 或 **日期范围**
3. 选择图片来源：**MediaStore**（系统相册）或 **SAF**（自定义文件夹）
4. 点击"开始备份"→ 实时查看进度
5. 完成后查看统计：上传/跳过/重命名/失败数量及耗时

## 🏗 项目结构

```
app/src/main/java/com/copyback/
├── CopyBackApp.kt                 # Application 类（注册 BouncyCastle）
├── MainActivity.kt                # 单 Activity + Compose 入口
├── navigation/
│   └── AppNavHost.kt              # 5 个路由页面导航
├── data/
│   ├── model/                     # 数据模型（配置、照片元信息、进度、结果）
│   ├── repository/                # 仓库接口 + 持久化实现
│   ├── scanner/                   # MediaStore 和 SAF 扫描器
│   └── smb/                       # SMBJ 连接管理 + 备份执行 + 冲突解决
├── ui/
│   ├── screens/                   # 5 个 Compose 页面
│   └── theme/                     # Material 3 主题（品牌色 #1565C0）
└── viewmodel/                     # BackupViewModel + SmbConfigViewModel
```

## 🔒 安全说明

- 密码存储使用 **EncryptedSharedPreferences**（AES-256-GCM），仅当用户勾选"记住密码"时保存
- SMB 连接凭证在内存中使用后即弃
- 应用包含 BouncyCastle 加密提供者，注册为 JCA provider

## 📄 License

本项目仅用于个人学习和备份用途。
