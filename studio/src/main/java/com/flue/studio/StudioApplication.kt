package com.flue.studio

import android.app.Application
import com.flue.launcher.watchface.agent.AgentRuntimeInfo

class StudioApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // :agent 模块拿不到宿主 BuildConfig，版本号由这里注入。
        AgentRuntimeInfo.versionName = BuildConfig.VERSION_NAME
    }
}
