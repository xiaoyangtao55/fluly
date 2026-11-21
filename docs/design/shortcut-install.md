# 添加快捷方式：两条入口与它们的边界

其它应用请求把快捷方式放进 fluly 的列表，有两条互不替代的通道。这个仓库两条都实现，
因为它们覆盖的是不同年代的应用。

## 一、两条通道

| | 现代：Pin 请求 | 旧式：`INSTALL_SHORTCUT` 广播 |
| --- | --- | --- |
| 发起方 | `ShortcutManager.requestPinShortcut()` | `sendBroadcast(INSTALL_SHORTCUT)` |
| 到达方 | `ShortcutReceiverActivity`（`CONFIRM_PIN_SHORTCUT`） | `ShortcutInstallReceiver` |
| 引入版本 | Android 8.0 (API 26) | Android 1.6，8.0 起废弃 |
| 前置条件 | **本应用必须是默认桌面** | 无（系统不校验） |
| 用户确认 | **启动器弹窗，用户明确点“添加”后才 accept/落库** | 没有（兼容策略保持不变） |
| 图标来源 | `LauncherApps.getShortcutIconDrawable` | `EXTRA_SHORTCUT_ICON` 或 `EXTRA_SHORTCUT_ICON_RESOURCE` |
| 启动方式 | `launcherApps.startShortcut(pkg, id, …)` | 存的 Intent URI 还原后 `startActivity` |

Android 8.0 之后系统**自己**不再发那条旧广播，但应用自己发的照旧能到。
国内工具类应用大量还停在旧路径上，所以两条都要留着。

## 二、必须声明 `INSTALL_SHORTCUT` 权限

这是整套实现里最容易漏、又最难查的一处：

```xml
<uses-permission android:name="com.android.launcher.permission.INSTALL_SHORTCUT" />
```

权限本身是 **normal** 级，安装即授予，用户无感。问题不在授权，而在**投递过滤**。

绝大多数应用是这样发的：

```java
Intent i = new Intent("com.android.launcher.action.INSTALL_SHORTCUT");
i.putExtra(Intent.EXTRA_SHORTCUT_INTENT, target);
sendBroadcast(i, "com.android.launcher.permission.INSTALL_SHORTCUT");  // ← 带权限
```

`sendBroadcast(intent, permission)` 的第二个参数意思是「只投递给**持有该权限**的接收者」。
ActivityManager 在派发前逐个 `checkComponentPermission`，不持有就跳过。

于是会出现这种状态：manifest 里 receiver 声明了、`exported="true"` 也开着、代码逻辑完全正确，
**广播一条都到不了**。没有异常、没有日志、`adb shell dumpsys activity broadcasts` 里只能看到
「某接收者因权限不足被跳过」。

漏了这一行，功能表现为「应用点了添加到桌面，fluly 里什么都没发生」——和没写这个功能一模一样。

## 三、数据怎么落

```
Pin 请求 ──校验──用户确认──复核──accept()──┐
                            ├─→ AppRepository.addPinnedShortcut / addLegacyShortcut
旧广播 ──极简 Intent 解析───┘             │
                                          ├─→ AppListItemStorage.addShortcut
                                          │     · 图标压成 PNG 落盘（重启后还在）
                                          │     · 去重：pinned 按 pkg + shortcutId，legacy 按 Intent URI
                                          └─→ refreshAsync() → 进 catalogItems → 列表里出现
```

两条路最终落到同一张表，`AppInfo.itemType = SHORTCUT`，`componentKey = shortcut:<id>`。
所以长按菜单里的「移除」、拖进文件夹、放进快捷启动，这些对普通应用成立的操作对它一样成立。

### 现代 pin：明确同意后才 accept，再落库

`onCreate` 只解析和验证现代请求并显示确认弹窗，绝不安装。只有用户点击“添加”才调用
`confirmPinnedShortcut`；“取消”、返回键及点弹窗外部都退出且不 accept、不落库。
确认时重新校验 `isValid`、请求类型及 shortcutInfo；等待期间过期的请求会被拒绝。
旧 `INSTALL_SHORTCUT` 广播继续自动处理，不借本次修改改变历史兼容策略。

Activity 已使用可显示的 `Theme.WatchLauncher`（`android:Theme.Material.NoActionBar`），
不是 `NoDisplay`，无需改 manifest/主题。现有 `noHistory=true` 保留：离开后不保留确认页。
`onNewIntent` 先取消旧同意状态、销毁弹窗，再 `setIntent` 展示新请求；旧弹窗回调不能同意新请求。
`onDestroy` 释放内存中的请求闭包和弹窗。重建只会再次展示确认，不会从保存状态恢复“已同意”。

弹窗只用静态本地化文案，不回显外部 label、Intent、extras 或 token；不写 Bundle、日志、偏好或文件。
待确认请求仅存于当前 Activity 内存，只有同意后才沿既有仓库存储最小快捷方式数据。

### 顺序保证

Pin 请求那侧刻意把 `request.accept()` 放在 `addPinnedShortcut()` **之前**：

- accept 是让系统把快捷方式标记为 pinned 的那一步，也是唯一可能失败的一步（请求过期、
  已被处理、启动器不再是默认桌面）；
- 反过来先落库再 accept，一旦 accept 失败，列表里就多一个 `startShortcut` 必抛异常的条目；
- 而「已 pinned 但没落库」这种失败，下次用户再触发一次会被去重逻辑接住，代价小得多。

`request.isValid` 也提前判一次。它和 accept 失败在用户侧看起来都是「没加上」，
但成因不同：过期请求该提示「无效或已过期」，落库失败才是「无法添加」。

## 四、失效条目会被清掉

应用更新后删掉某个快捷方式，`LauncherApps` 不再返回它，但条目还躺在存储里。
点一下：`startShortcut` 抛异常 → 触发一次 refresh → 条目依旧在。
于是变成点不开、看着像坏了的僵尸图标。

`LauncherApps.Callback.onShortcutsChanged` 给的是该包**当前完整**的可用清单
（manifest + dynamic + pinned），所以「清单里没有的 pinned id」就是失效的，直接删。
删的时候连图标 PNG 和文件夹引用一起清（复用 `AppListItemStorage.removeShortcut`）。

一条保守规则：**清单为空时不动手**。应用刚更新、shortcut 还没重新发布的那一小段也可能
给空列表，那时候删会把用户正常添加的条目一起抹掉。留一个僵尸，好过删一个还能用的。

## 五、边界

1. **必须是默认桌面。** Pin 请求由系统只发给当前默认 launcher；`getShortcuts` 也要求
   `hasShortcutHostPermission()`。用户装了别的桌面并切过去，两条路都静默失效。
   这是平台行为，不是缺陷。
2. **旧广播没有来源校验。** `ShortcutInstallReceiver` 没设 `android:permission`，
   任何应用都能往里塞条目，且不经过用户确认。这是 8.0 前的历史设计：当年所有 launcher
   都这么收。收紧成 `android:permission="com.android.launcher.permission.INSTALL_SHORTCUT"`
   能挡住没声明权限的发送方，但也会漏掉一部分本来能用的应用——兼容性优先，保持宽松。
3. **小组件请求会被拒。** `CONFIRM_PIN_SHORTCUT` 同一条通道也送 widget pin 请求
   （`REQUEST_TYPE_WIDGET`）。本启动器没有 widget 宿主，按无效处理，不落库。
4. **只进列表，不进表盘。** 添加完成后条目出现在应用列表/蜂窝里，按现有序规则排序。
   表盘和侧屏不受影响。
5. **图标是快照。** 存的是添加那一刻的 PNG。应用后来换了图标，列表里的快捷方式不会跟着变，
   除非删掉重加。

## 六、怎么验

```bash
# ① 当前是不是默认桌面（不是的话 pin 请求根本不会来）
adb shell cmd package resolve-activity --brief -a android.intent.action.MAIN \
  -c android.intent.category.HOME

# ② 手动打一条旧广播，绕开应用直接验证 receiver 通不通
adb shell am broadcast -a com.android.launcher.action.INSTALL_SHORTCUT \
  --es android.intent.extra.shortcut.NAME "测试快捷方式" \
  --es android.intent.extra.shortcut.INTENT \
  'intent:#Intent;action=android.intent.action.VIEW;end'

# ③ 看有没有被权限过滤器拦掉
adb logcat | grep -i "permission.*INSTALL_SHORTCUT\|Background activity start"
```

第 ② 条要注意：`am broadcast` 从 shell 发，shell 用户不持有普通应用的权限集，
所以它能到不代表带权限投递的应用能到。真正的判据是装一个已知会用旧路径的应用
（老版工具类）试一次。

Pin 路径最省事的验证方式：Chrome 打开任意网页 → 菜单 → 添加到主屏幕。

### 本轮静态检查与待运行验证

本轮按要求**未启动编译、未运行 Gradle/JVM 测试、未提交**。静态检查覆盖独立中英日
资源 XML 的键一致性、补丁空白检查，以及确认/取消代码路径。

新增 `ShortcutPinConfirmationTest` 钉住：打开不安装、明确确认只执行一次、取消后迟到确认
无作用、替换请求不继承同意、安装异常不自动重试。`ShortcutInstallContractTest` 补充：
自动 dispatcher 不接受现代请求、正负按钮/系统取消接线、确认时再次验证、onNewIntent 和
销毁清理，以及可显示主题。以上测试已补代码，**尚未执行**。

用户允许后再运行相关测试，并在设备上验证：

1. 发起 pin 后不操作：应用列表和系统 pinned 状态均不应改变。
2. 点击添加：系统 accept 成功后才新增一条，重复回调不重复安装。
3. 分别点击取消、返回、点弹窗外部：无新增，也无成功 Toast。
4. 等请求过期再确认：提示无效或过期，不落库；widget/缺失/畸形请求同样拒绝。
5. 弹窗期间旋转/销毁重建、离开页面、投递新 Intent：不自动接受，不继承旧同意，不泄漏窗口。
6. 旧式广播依旧自动添加，现有来源与权限宽松策略不变。
7. 在手表小屏及中英日语言下检查按钮可点、文案可读。
