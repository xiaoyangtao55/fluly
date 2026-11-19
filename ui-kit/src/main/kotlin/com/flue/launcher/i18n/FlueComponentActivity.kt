package com.flue.launcher.i18n

import android.content.Context
import androidx.activity.ComponentActivity

/**
 * 所有 Flue Activity 的基类。
 *
 * 覆写 [attachBaseContext] 以注入语言覆盖，使 [stringResource]、
 * [getString] 等 API 返回正确语言的文本。
 */
abstract class FlueComponentActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(FlueLocaleManager.applyLocale(newBase))
    }

    override fun onResume() {
        super.onResume()
        FlueActivityLifecycleHooks.onResume(this)
    }

    override fun onPause() {
        FlueActivityLifecycleHooks.onPause(this)
        super.onPause()
    }
}

/**
 * 所有 Flue Activity 的 resume / pause 附加动作。
 *
 * 这个基类原本还负责刷新勿扰状态、给诊断控制器记一笔，但那是启动器的活
 * （`DoNotDisturbState`、`DiagnosticsController` 都在 app 里）。基类下沉到
 * :ui-kit 之后改成由宿主在 `Application.onCreate` 注入，没注入就是空实现。
 */
object FlueActivityLifecycleHooks {
    @Volatile
    var onResume: (activity: ComponentActivity) -> Unit = {}

    @Volatile
    var onPause: (activity: ComponentActivity) -> Unit = {}
}
