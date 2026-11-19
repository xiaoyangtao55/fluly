package com.flue.launcher.watchface.editor

import androidx.compose.runtime.Composable

/**
 * 编辑器里两个「从列表里挑一个」的弹窗，实现留在 app。
 *
 * 它们都不是纯 UI：
 * - 应用选择器要列已安装应用（PackageManager + 图标懒加载缓存）；
 * - 点击动作选择器必须复用「设置 → 表盘点击动作」那份名单 —— 编辑器写进图层的
 *   动作名得正好是用户能在设置页绑定应用的那个键，两边各存一份迟早会漂。
 *
 * 所以这里只留注入点。回调参数全是纯数据（字符串 / 可空字符串），不引入 app 类型。
 */
object WatchEditorHostUi {

    /**
     * 选一个已安装应用。回调给的是 `w_app:` 语法需要的三段。
     *
     * 默认实现什么都不画：没注入说明宿主没接上，这时静默不弹窗，比弹一个
     * 「暂无应用」的空壳更不容易误导。
     */
    var appPicker: @Composable (
        title: String,
        subtitle: String?,
        onPick: (label: String, packageName: String, activityClass: String) -> Unit,
        onDismiss: () -> Unit,
    ) -> Unit = { _, _, _, _ -> }

    /** 选一个表盘点击动作。 */
    var tapActionPicker: @Composable (
        current: String?,
        onPick: (String) -> Unit,
        onDismiss: () -> Unit,
    ) -> Unit = { _, _, _ -> }
}
