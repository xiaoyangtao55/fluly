package com.fluly.launcher

import android.app.Application
import android.appwidget.AppWidgetHost
import android.content.Context
import com.fluly.launcher.logging.FlulyLog
import com.fluly.launcher.data.repository.AppRepository
import com.fluly.launcher.data.repository.WidgetRepository
import com.fluly.launcher.diagnostics.DiagnosticsController
import com.fluly.launcher.i18n.FlulyActivityLifecycleHooks
import com.fluly.launcher.i18n.FlulyLocaleManager
import com.fluly.launcher.status.DoNotDisturbState
import com.fluly.launcher.ui.theme.ThemeMode
import com.fluly.launcher.ui.theme.UiStyle
import com.fluly.launcher.ui.theme.DEFAULT_UI_STYLE
import com.fluly.launcher.ui.style.FlulyOverlayPreferences
import com.fluly.launcher.ui.style.FlulyOverlayPreferenceSource
import com.fluly.launcher.viewmodel.LauncherViewModel
import com.fluly.launcher.viewmodel.dataStore
import com.fluly.launcher.watchface.standard.WatchMediaSessionHost
import com.fluly.launcher.watchface.standard.WatchWeatherRepository
import com.fluly.launcher.watchface.standard.WatchWeatherSources
import com.fluly.launcher.util.RecentsVisibility
import com.fluly.launcher.widget.SIDE_SCREEN_APP_WIDGET_HOST_ID
import com.fluly.launcher.widget.WidgetHostLifecycleController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class FlulyApplication : Application() {
    val repositories: FlulyRepositories by lazy { FlulyRepositories(this) }

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(FlulyLocaleManager.applyLocale(base))
    }

    override fun onCreate() {
        super.onCreate()
        com.fluly.launcher.watchface.store.ui.WatchStoreHostProvider.host =
            com.fluly.launcher.watchface.HomeWatchStoreHost(this)
        // 运行时模块不能反向依赖 HOME 的通知监听服务，媒体会话查询组件由这里注入。
        WatchMediaSessionHost.listenerComponent =
            android.content.ComponentName(this, com.fluly.launcher.service.WLauncherNotificationListener::class.java)
        // 基类 FlulyComponentActivity 已下沉到 :ui-kit，它原本在 onResume/onPause 里
        // 刷勿扰状态、给诊断控制器记账 —— 这两件事都是启动器的，所以由这里注回去。
        FlulyActivityLifecycleHooks.onResume = { activity ->
            DoNotDisturbState.refresh(activity)
            DiagnosticsController.onResume(activity)
        }
        FlulyActivityLifecycleHooks.onPause = { activity ->
            DiagnosticsController.onPause(activity)
        }
        // 覆层（对话框 / 提示）的视觉偏好存在启动器设置里，:ui-kit 不认识设置层，
        // 由这里把 DataStore 的一条投影注入进去。
        FlulyOverlayPreferenceSource.flow = dataStore.data
            .map { preferences ->
                FlulyOverlayPreferences(
                    advancedMaterial = preferences[LauncherViewModel.KEY_BLUR]
                        ?: (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S),
                    reduceMotion = preferences[LauncherViewModel.KEY_REDUCE_DYNAMIC_EFFECTS] ?: false,
                )
            }
            .catch { emit(FlulyOverlayPreferenceSource.default()) }
        FlulyLog.configureDiagnostics(this)
        com.fluly.launcher.status.DoNotDisturbState.initialize(this)
        // Standard, JB, and editor previews all resolve their null weather
        // source through this one application-owned repository. Its cached
        // snapshot and temperature-unit preference therefore survive host
        // recreation and remain identical across those surfaces.
        WatchWeatherSources.applicationSource = WatchWeatherRepository.default(this)
        // 最近任务可见性偏好在多个 Activity 的 onCreate/onResume 同步读取；
        // 在后台先把 DataStore 首读做完，主线程读方就只剩内存取值。
        repositories.applicationScope.launch {
            RecentsVisibility.warmUp(this@FlulyApplication)
        }
    }

    companion object {
        fun repositories(context: Context): FlulyRepositories =
            (context.applicationContext as? FlulyApplication)?.repositories
                ?: FlulyRepositories(context.applicationContext)
    }
}

class FlulyRepositories(context: Context) {
    private val appContext = context.applicationContext

    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val sharedState = SharedLauncherState()
    val appRepository: AppRepository by lazy { AppRepository(appContext) }
    val widgetRepository: WidgetRepository by lazy { WidgetRepository(appContext) }
    internal val widgetRefreshCoordinator by lazy { com.fluly.launcher.widget.WidgetRefreshCoordinator(appContext) }
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
