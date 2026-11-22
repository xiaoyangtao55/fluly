package com.flue.launcher

import android.app.Application
import android.appwidget.AppWidgetHost
import android.content.Context
import com.flue.launcher.logging.FlueLog
import com.flue.launcher.data.repository.AppRepository
import com.flue.launcher.data.repository.WidgetRepository
import com.flue.launcher.diagnostics.DiagnosticsController
import com.flue.launcher.i18n.FlueActivityLifecycleHooks
import com.flue.launcher.i18n.FlueLocaleManager
import com.flue.launcher.status.DoNotDisturbState
import com.flue.launcher.ui.theme.ThemeMode
import com.flue.launcher.ui.theme.UiStyle
import com.flue.launcher.ui.theme.DEFAULT_UI_STYLE
import com.flue.launcher.ui.style.FlueOverlayPreferences
import com.flue.launcher.ui.style.FlueOverlayPreferenceSource
import com.flue.launcher.viewmodel.LauncherViewModel
import com.flue.launcher.viewmodel.dataStore
import com.flue.launcher.watchface.standard.WatchMediaSessionHost
import com.flue.launcher.watchface.standard.WatchWeatherRepository
import com.flue.launcher.watchface.standard.WatchWeatherSources
import com.flue.launcher.util.RecentsVisibility
import com.flue.launcher.widget.SIDE_SCREEN_APP_WIDGET_HOST_ID
import com.flue.launcher.widget.WidgetHostLifecycleController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class FlueApplication : Application() {
    val repositories: FlueRepositories by lazy { FlueRepositories(this) }

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(FlueLocaleManager.applyLocale(base))
    }

    override fun onCreate() {
        super.onCreate()
        com.flue.launcher.watchface.store.ui.WatchStoreHostProvider.host =
            com.flue.launcher.watchface.HomeWatchStoreHost(this)
        // 运行时模块不能反向依赖 HOME 的通知监听服务，媒体会话查询组件由这里注入。
        WatchMediaSessionHost.listenerComponent =
            android.content.ComponentName(this, com.flue.launcher.service.WLauncherNotificationListener::class.java)
        // 基类 FlueComponentActivity 已下沉到 :ui-kit，它原本在 onResume/onPause 里
        // 刷勿扰状态、给诊断控制器记账 —— 这两件事都是启动器的，所以由这里注回去。
        FlueActivityLifecycleHooks.onResume = { activity ->
            DoNotDisturbState.refresh(activity)
            DiagnosticsController.onResume(activity)
        }
        FlueActivityLifecycleHooks.onPause = { activity ->
            DiagnosticsController.onPause(activity)
        }
        // 覆层（对话框 / 提示）的视觉偏好存在启动器设置里，:ui-kit 不认识设置层，
        // 由这里把 DataStore 的一条投影注入进去。
        FlueOverlayPreferenceSource.flow = dataStore.data
            .map { preferences ->
                FlueOverlayPreferences(
                    advancedMaterial = preferences[LauncherViewModel.KEY_BLUR]
                        ?: (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S),
                    reduceMotion = preferences[LauncherViewModel.KEY_REDUCE_DYNAMIC_EFFECTS] ?: false,
                )
            }
            .catch { emit(FlueOverlayPreferenceSource.default()) }
        FlueLog.configureDiagnostics(this)
        com.flue.launcher.status.DoNotDisturbState.initialize(this)
        // Standard, JB, and editor previews all resolve their null weather
        // source through this one application-owned repository. Its cached
        // snapshot and temperature-unit preference therefore survive host
        // recreation and remain identical across those surfaces.
        WatchWeatherSources.applicationSource = WatchWeatherRepository.default(this)
        // 最近任务可见性偏好在多个 Activity 的 onCreate/onResume 同步读取；
        // 在后台先把 DataStore 首读做完，主线程读方就只剩内存取值。
        repositories.applicationScope.launch {
            RecentsVisibility.warmUp(this@FlueApplication)
        }
    }

    companion object {
        fun repositories(context: Context): FlueRepositories =
            (context.applicationContext as? FlueApplication)?.repositories
                ?: FlueRepositories(context.applicationContext)
    }
}

class FlueRepositories(context: Context) {
    private val appContext = context.applicationContext

    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val sharedState = SharedLauncherState()
    val appRepository: AppRepository by lazy { AppRepository(appContext) }
    val widgetRepository: WidgetRepository by lazy { WidgetRepository(appContext) }
    internal val widgetRefreshCoordinator by lazy { com.flue.launcher.widget.WidgetRefreshCoordinator(appContext) }
    val appWidgetHost: AppWidgetHost by lazy {
        AppWidgetHost(appContext, SIDE_SCREEN_APP_WIDGET_HOST_ID)
    }
    internal val widgetHostLifecycle: WidgetHostLifecycleController by lazy {
        WidgetHostLifecycleController(
            startListening = appWidgetHost::startListening,
            stopListening = appWidgetHost::stopListening,
        )
    }
}

class SharedLauncherState {
    /**
     * 默认深色（老师定）。
     *
     * 这里与 `LauncherViewModel` 读偏好时的兜底值**必须一致** —— 不一致的话冷启动会先按
     * 一种主题画一帧、读到偏好再跳成另一种，就是一次刺眼的闪白。
     */
    val themeMode = MutableStateFlow(ThemeMode.DARK)
    val uiStyle = MutableStateFlow(DEFAULT_UI_STYLE)
}
