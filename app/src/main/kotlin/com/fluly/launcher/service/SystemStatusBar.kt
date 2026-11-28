package com.fluly.launcher.service

import android.content.Context
import android.util.Log
import java.lang.reflect.InvocationTargetException

/**
 * 通过 `StatusBarManager` 拉下系统通知栏 / 快捷设置面板。
 *
 * 关键事实：**`EXPAND_STATUS_BAR` 是 normal 级权限**，不是签名权限。AOSP 里它的定义是
 *
 * ```xml
 * <permission android:name="android.permission.EXPAND_STATUS_BAR"
 *             android:protectionLevel="normal" />
 * ```
 *
 * 服务端唯一的检查是 `StatusBarManagerService.enforceExpandStatusBar()`，查的正是它：
 *
 * ```java
 * public void expandSettingsPanel(String subPanel) {
 *     enforceExpandStatusBar();
 *     if (mBar != null) { mBar.animateExpandSettingsPanel(subPanel); }
 * }
 * ```
 *
 * 所以 manifest 声明一次、安装即授予，**不需要无障碍、不需要任何运行时授权**。
 * 容易被误导的地方是 `StatusBarManager` 这个类本身带 `@hide` —— 那只是编译期不暴露
 * 给 SDK，运行时它仍是 public class、方法仍是 public method，反射可以正常调用。
 *
 * [FlulyAccessibilityService.openQuickSettings] 保留为兜底：某些 ROM 会改写这段
 * 权限校验，那时反射调用会抛 SecurityException，再退回无障碍的全局动作。
 */
internal object SystemStatusBar {

    private const val TAG = "SystemStatusBar"
    private const val STATUS_BAR_SERVICE = "statusbar"

    /**
     * 展开快捷设置面板（完整下拉，即「控制中心」）。
     *
     * 参数 `subPanel` 只认 `"network"` / `"internet"` / `"volume"`，其余值在服务端
     * 会被归一成 null；传 null 就是展开默认页，这里要的正是默认页。
     */
    fun expandSettingsPanel(context: Context): Boolean = invoke(
        context = context,
        methodName = "expandSettingsPanel",
        parameterTypes = arrayOf(String::class.java),
        arguments = arrayOf<Any?>(null),
    )

    /** 展开通知栏（浅层遮罩，停在通知列表，不进入 QS 页）。 */
    fun expandNotificationsPanel(context: Context): Boolean = invoke(
        context = context,
        methodName = "expandNotificationsPanel",
        parameterTypes = emptyArray(),
        arguments = emptyArray(),
    )

    /**
     * 拉下系统面板：优先完整 QS 页，被拒再退通知栏。
     *
     * 这是一次「尝试 + 降级」，返回值只表示有没有一个调用被服务端接受 ——
     * 面板有没有真的显示出来，这个进程看不到。
     */
    fun expandControlCenter(context: Context): Boolean =
        expandSettingsPanel(context) || expandNotificationsPanel(context)

    /**
     * 刻意不缓存 [java.lang.reflect.Method]：`statusbar` 的 binder 在系统重启后会换，
     * 缓存跨进程对象没有收益；两次反射相对于一次手势的开销也可以忽略。
     */
    private fun invoke(
        context: Context,
        methodName: String,
        parameterTypes: Array<Class<*>>,
        arguments: Array<Any?>,
    ): Boolean = runCatching {
        val service = context.getSystemService(STATUS_BAR_SERVICE) ?: run {
            Log.w(TAG, "statusbar 服务不可用")
            return false
        }
        service.javaClass.getMethod(methodName, *parameterTypes)
            .invoke(service, *arguments)
        true
    }.onFailure { error ->
        // 反射调用把真实异常包在 InvocationTargetException 里，拆开才看得到原因。
        val cause = (error as? InvocationTargetException)?.targetException ?: error
        Log.w(TAG, "StatusBarManager.$methodName 调用失败: ${cause.javaClass.simpleName}: ${cause.message}")
    }.getOrDefault(false)
}
