package com.fluly.launcher.watchface.agent

/**
 * 宿主注入的运行时信息。
 *
 * Agent 拆成独立模块后拿不到 App 的 BuildConfig，HTTP User-Agent 这类
 * 需要版本号的地方统一从这里读；宿主在 Application.onCreate 里赋值。
 */
object AgentRuntimeInfo {
    @Volatile
    var versionName: String = "0"

    val userAgent: String
        get() = "Fluly-Agent/$versionName"
}
