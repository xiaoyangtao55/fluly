# 下拉控制中心：把下滑还给系统面板

需求：控制中心那一屏删掉之后，表盘上竖向向下滑没有任何反应。希望下滑直接拉出
系统自己的控制中心（快捷设置面板），而不是在启动器里重画一套。

结论：**能做，而且不需要用户开任何权限**。首选反射 `StatusBarManager`，无障碍只是兜底。
两条路都做不到逐帧跟手。

```
表盘下滑 80px → StatusBarManager.expandSettingsPanel(null) → 系统面板自己滑下来
```

## 一、首选 `StatusBarManager`：它是 normal 权限

直觉上这条路走不通，因为 `android.app.StatusBarManager` 这个类带 `@hide`。但 `@hide`
只是编译期不暴露给 SDK，运行时它仍是 public class、方法也仍是 public method，反射能调到。
真正决定能不能用的是权限：

```xml
<permission android:name="android.permission.EXPAND_STATUS_BAR"
            android:protectionLevel="normal" />
```

normal 级，manifest 声明一次、安装即授予，**用户不用做任何操作，adb 也不用授**。
服务端的检查只有一处：

```java
@Override
public void expandSettingsPanel(String subPanel) {
    enforceExpandStatusBar();          // 查的就是 EXPAND_STATUS_BAR
    if (mBar != null) { mBar.animateExpandSettingsPanel(subPanel); }
}
```

所以反射 `expandSettingsPanel(null)` 就是完整的控制中心。

> 这条一开始被我判成了「签名级权限、普通应用申请不到」，于是绕道去写无障碍。
> 判断依据应该是 `enforceExpandStatusBar()` 里查的是哪个权限，而不是类上有没有 `@hide`。

`subPanel` 参数只认 `"network"` / `"internet"` / `"volume"`，其余值在服务端被归一成
null，传 null 就是展开默认页 —— 要的正是默认页。

## 二、无障碍为什么还留着

它不是主路径，是**兜底**。个别 ROM 会把 `enforceExpandStatusBar()` 改成只认系统签名，
那时反射调用抛 `SecurityException`，需要另一条不经过 `StatusBarManager` 的入口：

```kotlin
performGlobalAction(GLOBAL_ACTION_QUICK_SETTINGS)   // API 17 起就有
```

无障碍的全局动作由 `AccessibilityManagerService` 在 system_server 内部转调
`StatusBarManagerInternal`，调用方是系统自己，不受上面那条校验限制 —— 代价是要用户
手动启用无障碍服务。

调用顺序按代价排：能走反射就不碰无障碍，因为前者零成本、后者要用户去设置页。

`openQuickSettings()` 内部还有一层降级：快设被拒时再试 `GLOBAL_ACTION_NOTIFICATIONS`。
裁掉 QS 的手表固件往往还留着通知栏，这样下滑至少不是一个死手势。

## 三、为什么不能跟手

侧屏和小组件页是逐帧跟手的：`GestureHost` 上报 0..1 的进度，宿主用 `Animatable`
驱动 `translationY` 和 `alpha`。系统面板不归这个进程绘制，没有任何「把进度喂给它」的
接口 —— `expandSettingsPanel` 的全集就是「展开 / 收起」两个离散动作。

所以这里退化成**阈值触发**：竖向向下超过 80px 派发一次，把 `consumed` 置真，
同一次拖动里不再重复派发。放弃跟手换来的是：不需要一套新的转场状态机，
也不需要在面板下来的过程中让我们的 Activity 保持前台。

一个已知的观感代价：手指已经滑到屏幕下沿，面板才从顶部滑下，中间有一下断裂感。
所有第三方 launcher 的下拉通知栏都是这个手感，没有更好的做法。

## 四、失败分支

三条路径依次尝试，只有全部落空才打扰用户：

| 顺序 | 路径 | 失败原因 |
| --- | --- | --- |
| 1 | `StatusBarManager.expandSettingsPanel` | 方法不存在 / ROM 改写了权限校验 |
| 2 | 无障碍 `GLOBAL_ACTION_QUICK_SETTINGS` | 无障碍没开 / 服务没连上 |
| 3 | 无障碍 `GLOBAL_ACTION_NOTIFICATIONS` | 系统连通知栏都没有 |

落空之后的提示要区分「没授权」和「系统真没有面板」，不能一律让用户去开无障碍 ——
明明给了权限却提示去授权，是最容易让人失去信任的一种错。

`performGlobalAction` 返回 false 的两种含义（没连上 / 没有面板）在这里分不清，
所以判断顺序是「先动作、后查授权」：`isEnabled()` 读的是 `Settings.Secure` 里的字符串，
服务刚启用、还没连上时它已经是 true 但动作会失败 —— 那种情况提示「无面板」比提示
「去授权」更接近真相。

## 五、开关为什么必须有

这个功能占用的是「下滑」这个高频手势。设备上不生效时，用户的直觉会是「启动器坏了」，
而不是「这个 ROM 没有快设面板」。所以设置 → 功能开关 → 下拉控制中心，默认开。

开关的读取位置也有一处讲究：`GestureHost` 的手势识别器挂在 `pointerInput` 上，
它的 key 不包含 `onPullDownShade`（lambda 每次重组都是新对象，进 key 会导致识别器
反复重建）。因此**不能在调用点用 `if (enabled) callback else null`**——
传 null 不会让识别器重建，关掉开关后旧闭包照样生效。

正确做法是始终传同一个 `remember` 出来的 lambda，开关在 lambda 内部用
`vm.pullDownControlCenterEnabled.value` 现读。改完立即生效，无需重启。

## 六、设备上的验证顺序

```bash
# ① 不碰无障碍，直接验证反射路径（预期：QS 面板完整展开）
#    打开应用 → 表盘下滑

# ② 面板出来后再下滑（预期：不重复触发，要先收起）
# ③ 关闭开关后再下滑（预期：什么都不发生）
```

第 ① 步就能区分三种固件：

| 现象 | 结论 |
| --- | --- |
| QS 面板完整展开 | 正常，事情结束 |
| 只下来通知列表 | 该固件裁了 QS，但通知栏可用 |
| 什么都不发生 | 连反射都被拒，看 `adb logcat -s SystemStatusBar` 的失败原因 |

如果 Logcat 里是 `SecurityException`，说明这个 ROM 改了权限校验，此时再去开无障碍
走第 2 条路；如果两个都失败，说明 SystemUI 被裁得只剩状态栏，这个功能在设备上
不成立，关掉开关即可。

## 七、涉及文件

| 文件 | 改动 |
| --- | --- |
| `service/SystemStatusBar.kt` | 反射 `expandSettingsPanel` / `expandNotificationsPanel`，含降级 |
| `AndroidManifest.xml` | 声明 `EXPAND_STATUS_BAR`（normal 级） |
| `service/FlueAccessibilityService.kt` | `openQuickSettings()`，作为兜底 |
| `ui/navigation/GestureHost.kt` | `onPullDownShade` 回调 + `Face` 分支的下滑判定 |
| `LauncherActivity.kt` | `pullDownSystemShade` 三级降级、接到 `GestureHost` |
| `viewmodel/LauncherViewModel.kt` | `pull_down_control_center_enabled` 键与状态 |
| `ui/settings/SettingsHierarchy.kt` | 功能开关屏新增条目 |
| `SettingsActivity.kt` | 开关绑定 |
| `res/values*/strings.xml` | 三语文案 |
