package com.flue.studio

import android.app.Application
import com.flue.launcher.watchface.agent.AgentRuntimeInfo
import com.flue.launcher.watchface.editor.WatchEditorHostProvider

class StudioApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // :agent 模块拿不到宿主 BuildConfig，版本号由这里注入。
        AgentRuntimeInfo.versionName = BuildConfig.VERSION_NAME
        // 编辑器 / 商店要的宿主能力由 Studio 自己给（表盘存储 + 静态偏好）。
        StudioServices.editorHost = StudioWatchEditorHost(this)
        WatchEditorHostProvider.host = StudioServices.editorHost
        installStudioWatchEditorHostUi()
    }
}
