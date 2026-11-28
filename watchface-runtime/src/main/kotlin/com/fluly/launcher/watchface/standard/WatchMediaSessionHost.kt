package com.fluly.launcher.watchface.standard

import android.content.ComponentName

/**
 * 活跃媒体会话查询所需的「已授权通知监听组件」。
 *
 * `MediaSessionManager.getActiveSessions()` 只认一个已授予通知访问权限的
 * `NotificationListenerService`，而那个服务属于 HOME 进程。运行时是共享模块，
 * 不能反向 import 宿主类，所以由宿主在 `Application.onCreate` 里注入；
 * 没注入（例如 Studio）时 [WatchMediaTransport] 会自动退回媒体键。
 */
object WatchMediaSessionHost {
    @Volatile
    var listenerComponent: ComponentName? = null
}
