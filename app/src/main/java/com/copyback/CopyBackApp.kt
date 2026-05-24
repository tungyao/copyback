package com.copyback

import android.app.Application

/**
 * CopyBack 应用入口。
 * 轻量图片备份工具 - 将安卓照片备份到远程 SMB 服务器。
 */
class CopyBackApp : Application() {

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    companion object {
        lateinit var instance: CopyBackApp
            private set
    }
}
