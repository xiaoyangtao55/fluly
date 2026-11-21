# 锁屏表盘：在 Keyguard 之上放一层表盘

需求：熄屏再亮屏，第一眼看到的应该是表盘，而不是系统时钟加通知列表。

结论先说：**系统 Keyguard 本身换不掉**。它由 SystemUI 的 `KeyguardViewMediator`
掌管，第三方 App 没有任何 API 能替换那层视图。能做到的是在它**上面**盖一个
`showWhenLocked` 的 Activity。所以真实形态是：

```
按电源键 → 表盘（盖在 Keyguard 上）→ 上滑 → 认证 → 桌面
```

## 一、与 AOD 的分界线

AOD 被删的原因是它劫持了锁屏流程：`FLAG_KEEP_SCREEN_ON` 让屏幕永不熄灭，
`onScreenOff()` 无条件 `ACQUIRE_CAUSES_WAKEUP` 把屏幕重新点亮，`lockScreenWithPlatform`
被 `enterAodNow()` 拦住。结果是系统不再锁屏，耗电和安全双输。

锁屏表盘与它是同一片地雷区，但必须守住一条线：

> **不调用 `setTurnScreenOn`，不持有 `FLAG_KEEP_SCREEN_ON`。**

前者一旦加上，熄屏瞬间屏幕会被重新点亮，立刻退化成 AOD；后者会让屏幕永不熄灭。
两条都不碰，屏幕该灭就灭、系统该锁屏就锁屏，这个 Activity 只是「锁屏时最上面那张脸」。

`LockScreenWatchFaceActivity` 的类注释里重复了这条约束 —— 因为它是这个功能唯一
容易被后人「顺手加上去」的地方。

## 二、三条链路

### 启动：`ACTION_SCREEN_OFF`

`LauncherActivity` 在 `onCreate` 注册、`onDestroy` 反注册熄屏广播。

receiver 注册存在不等于启动器在前台：动态 receiver 在其他 App 前台时仍可能收到广播。
`LockScreenForegroundGate` 在 HOME resume 时置入一次资格；亮屏状态下 pause/stop（打开
其他 App、设置或最近任务）立即撤销；熄屏导致的 pause/stop 保留资格，直到
`ACTION_SCREEN_OFF` 原子消费。这样不会简单在 onPause 清空而漏掉「先 pause、后广播」。
同一次前台周期最多触发一次，功能开关在消费后现读。启动仍为 best effort：厂商及
Android 版本的后台启动限制可能拒绝，不能把广播到达当作系统保证的前台启动豁免。

三个容易踩的点：

| 点 | 说明 |
| --- | --- |
| 注册点不能放在 `onPause` | 熄屏时 Activity 先 pause 再派发广播，放在 `onPause` 反注册就永远收不到 |
| 不能改成亮屏后才拉起 | 那时进程已在后台，会撞上后台启动拦截，要多一套悬浮窗权限引导 |
| 只从启动器熄屏才生效 | 在别的 App 里也可能收到广播，必须由前台资格门禁拒绝（见第四节） |

### 渲染：复用主桌面的三个 Host

内置表盘走 `WatchFaceLayer`；从相同的 JB/WatchMaker 存储扫描得到完整 descriptor，
分别交给主桌面使用的 `JbWatchFaceHost` / `WatchmakerWatchFaceHost`，不能只把自定义
ID 塞进内置层。失效 ID 或 Host 加载失败仅在锁屏本地回退默认内置表盘，不改写用户
选择。WatchMaker 画布遵循每表盘覆盖 → 全局默认 → 旧 force-fill → 出厂默认优先级，
并复用重力视差/背景模糊偏好。

三个 Host 共用 `resumed && PowerManager.isInteractive` 可见性，pause 或 SCREEN_OFF
立即停播；resume 或 SCREEN_ON 重新求值。receiver 到 destroy 才注销；不增加唤醒锁、
屏幕唤醒或 keep-screen-on。锁屏 Activity 消费触摸，仅识别退出手势；自定义表盘的
点击动作也不下发，避免认证前跳转应用或执行表盘动作。

渲染 Host 纯参数化，锁屏 Activity 不需要再养一个 LauncherViewModel，只要从
DataStore 读一份最小快照（另包含画布和重力选项）：

```
LockScreenFaceConfig(watchFaceId, photoPath, videoPath,
                     photoOptions, videoOptions, uiStyle,
                     themeMode, globalUiScalePercent)
```

`LockScreenFaceConfig.kt` 里的缺省值与 `LauncherViewModel` 加载偏好时的缺省值逐项对齐。
两处一旦分叉，表现是「桌面一个样、息屏一个样」——只有熄屏才看得见的静默 bug，
所以 `LockScreenFaceConfigTest` 把默认值和越界裁剪都钉住了。

锁屏上禁掉两样东西：`hasNotifications`（认证之前不该出现通知角标）、
`onLongPress`/`onDoubleTap`（这两个动作属于桌面）。

### 退出：上滑 / 返回露出 Keyguard，`ACTION_USER_PRESENT` 加一次自查

Activity 层识别单指向上滑动（至少 48dp 且纵向位移大于横向），跨 Compose 与原生
Host 一致处理。只调用 `finish()` 移除自己的覆盖层，不 dismiss/disable Keyguard，
不申请跳过认证。此时显示系统锁屏，是否继续上滑或 PIN/图案/生物认证完全由系统决定。

解锁完成时系统发这条广播，收到就 `finish()`，把桌面让出来。注册在
`onCreate`/`onDestroy` 而不是 `onResume`/`onPause`：认证界面弹出时本 Activity 会
`onPause`，那时反注册就收不到广播，解锁后表盘会一直盖着桌面。

但广播不是百分之百可靠 —— 裁过 SystemUI 的 ROM 上有几率不发。所以每次 `onResume`
后再挂一个 2 秒的一次性自查：屏幕亮着、且 `KeyguardManager.isKeyguardLocked` 已是
false，就主动退出。只查一次、不轮询，因为正常路径由广播负责，这条只是补漏。

返回键也 `finish()`，给三键导航设备留一条退回 Keyguard 看通知的路。

### 开关

「功能开关 → 锁屏表盘」，存在 `lock_screen_watch_face_enabled`，默认开。

留这个开关不是因为功能可选，而是因为**这个功能的成败取决于厂商 SystemUI 的行为，
本仓库静态验证不了**。用户的设备上表现一旦不对（熄屏不出现、或者出现后不解锁），
必须有一个不用重装 APK 就能退回去的入口。receiver 每次熄屏时读一次这个值，
改完不需要重启 Activity。

## 三、Activity 的窗口属性

| 属性 | 值 | 理由 |
| --- | --- | --- |
| `launchMode` | `singleInstance` | 自成任务，不与 HOME 抢栈 |
| `taskAffinity` | `""` | 同上 |
| `excludeFromRecents` | `true` | 一个锁屏皮肤出现在多任务列表里很怪 |
| theme | `Theme.WatchLauncher` | 已有全屏、黑底、`windowSwipeToDismiss=false` |

## 四、已知限制

1. **只在从启动器熄屏时生效。** 在微信、浏览器等 App 里熄屏，锁屏表盘不会出现。
   当前不申请后台启动或悬浮窗权限；即使前台资格满足，厂商也可能拒绝启动，必须真机验证。
2. **认证之前不显示通知。** 锁屏上不能暴露通知内容和日程详情，这是安全边界，
   不是遗漏。
3. **想直接看通知要多一步。** 表盘盖着时，用户需要先上滑（或按返回）才看到 Keyguard。
   这是「锁屏第一眼是表盘」的固有代价。
4. **没有任何代码能在本机验证它。** 画面是不是真的盖住 Keyguard、解锁后是不是真的
   退回桌面，只能在真机上看。下面第五节是给这件事准备的最小清单。

## 五、验证清单

装到真机后按顺序看：

1. 桌面熄屏 → 立即亮屏：第一眼是表盘，不是系统时钟
2. 上滑：先移除表盘，露系统 Keyguard；有 PIN/图案时不能跳过认证，无安全锁时按系统流程进入桌面
3. 认证通过：表盘消失，露出桌面（而不是表盘继续盖着）
4. 表盘上按返回键：露出 Keyguard（不是卡死）
5. 在别的 App 里熄屏：不出现锁屏表盘（预期行为，见限制 1）
6. 息屏后放 10 分钟再亮屏：表盘仍在，且电量没有明显下滑
7. 视频表盘、WatchMaker/JB 动画：熄屏及 pause 停止，亮屏 resume 续播；自定义表盘与桌面相同 Host，点击不能启动应用
8. 验证两种时序：HOME → SCREEN_OFF，以及 HOME → 非交互 pause/stop → SCREEN_OFF；都只触发一次。HOME → 亮屏 pause → 其他 App → SCREEN_OFF 不触发
9. 设置里关掉「锁屏表盘」→ 熄屏再亮屏：不再出现表盘，系统锁屏照常

任一条不符合，Logcat 过滤 `LockScreenWatchFace` 与 `Background activity start`。
