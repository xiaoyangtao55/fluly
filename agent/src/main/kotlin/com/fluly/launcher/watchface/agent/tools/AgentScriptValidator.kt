package com.fluly.launcher.watchface.agent.tools

/**
 * 表盘全局脚本的语法/运行时检查入口。
 *
 * 具体实现必须复用渲染期的 Lua 白名单沙箱（见 App 侧 `watchface/standard`），
 * 但沙箱依赖渲染层类型，所以这里只暴露函数式接口、由宿主注入 —— agent 模块
 * 因此不必反向依赖渲染模块。
 */
fun interface AgentScriptValidator {
    /** 返回 null 表示脚本可编译，且首个时钟 tick 未抛错。 */
    fun validate(source: String): WatchAgentDiagnostic?
}
