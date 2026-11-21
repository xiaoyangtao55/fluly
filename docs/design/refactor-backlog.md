# home 重构待办：规模快照与优先级

设置页那条线（8114 → 3516 行，见 `settings-data-model.md`）收口之后，"下一步做什么"
不该每次重新调研。这份文档存两样东西：**当前规模快照**，以及**已判断过但暂不做的
事项**——后者比前者重要，它防止同一个念头被反复提起又反复放下。

数据采集于 2026-11-21，口径为 `src/main` 下的 `.kt` 行数（含空行与注释）。

## 一、模块规模

| 模块 | main 文件 | main 行数 | 测试文件 |
| --- | --- | --- | --- |
| `:app` | 273 | 62898 | 107 |
| `:watchface-editor` | 123 | 46292 | 54 |
| `:watchface-runtime` | 80 | 35105 | 39 |
| `:ui-kit` | 80 | 12793 | 25 |
| `:agent` | 75 | 10937 | 26 |
| `:studio` | 9 | 3125 | **0** |

依赖方向：`:app` → `ui-kit` / `watchface-core` / `watchface-runtime`；
`:studio` → `agent` / `ui-kit` / `watchface-core` / `watchface-runtime` / `watchface-editor`。
也就是说 **`:app` 不依赖 editor，`:studio` 才是 editor 的唯一消费者**——这条边界是干净的，
别再往 `:app` 里加 editor 依赖。

## 二、`:app` 内部的大文件

| 文件 | 行数 | 结构 |
| --- | --- | --- |
| `viewmodel/LauncherViewModel.kt` | 4464 | 246 函数 / 313 属性 / **140 组** `_flow` + `asStateFlow()` + `setXxx` |
| `ui/smartstack/SmartStackLayer.kt` | 3833 | 11 个成员函数（最长 24 行） / 265 个状态属性 |
| `SettingsActivity.kt` | 3516 | 30 个 `when` 分支（已收口，见 `settings-data-model.md`） |
| `LauncherActivity.kt` | 2935 | 未拆解 |
| `applist/nativeview/NativeAppListView.kt` | 2785 | 未拆解 |
| `ui/drawer/HoneycombScreen.kt` | 2302 | 未拆解 |
| `ui/home/WatchFaceLayer.kt` | 2084 | 未拆解 |
| `data/repository/AppRepository.kt` | 1680 | 未拆解 |
| `ui/smartstack/SideScreenMusicCard.kt` | 1607 | 未拆解 |

## 三、已判断、暂不做

### 3.1 `LauncherViewModel`：一个设置要写四处（优先级最高）

问题不在 4464 行，在**同一份数据分散在四处**：

```kotlin
private val _sideScreenEnabled = MutableStateFlow(true)   // 1. 私有源
val sideScreenEnabled = _sideScreenEnabled.asStateFlow()  // 2. 公开转发
fun setSideScreenEnabled(v: Boolean) { ... }              // 3. 写入 + 落盘
// 4. resetSettings() 里再赋一次默认值
```

140 组这样的三件套，加上 `resetSettings()` 里 261 行的逐项赋值。**第四处漏写不会
编译失败**，表现是「恢复默认之后这一项还留着旧值」——和设置页 key 改名同一种失效
方式：静默，只在用户操作后暴露。

建议分两阶段，第一阶段风险接近零：

1. 140 个 flow 按领域（外观 / 侧屏 / 应用列表 / 表盘 / 性能 / 手势通知 / 备份）重排
   成七段并加分区注释；`resetSettings()` 按同样的七段拆成七个私有函数。函数体一行
   不改，编译 + 864 用例即可验证。
2. 挑一个领域抽成 `XxxSettingsStore`（自带 flow、setter、reset），ViewModel 只转发。
   这一步会碰调用点（`SettingsActivity`、`LauncherActivity`、Compose 层），风险明显
   高于第一阶段，先用一个领域试水。

第 2 步的收益才是真正把 4464 行降下来；第 1 步只是让它可导航，别把两者混成一版。

### 3.2 `SmartStackLayer`：3833 行的单个 composable

11 个成员函数里最长只有 24 行，说明 3833 行的主体是**一个超长 composable 函数体**，
承载 265 个状态。这跟 `SettingsRootScreen` 同型，但风险更高：`remember` / `LaunchedEffect`
的作用域判断错，表现是侧屏卡住或数据错位，单元测试抓不到。

若要动，先按卡片切成独立 composable（`SideScreenMusicCard` 已经是一个先例），
每次只切一张卡，并把「跨卡共享的状态」显式列出来再动手。不要一次性重构。

### 3.3 `:studio` 零测试

3125 行、9 个文件、0 个测试文件。现在补成本最低，但 studio 还在快速迭代，测试可能
跟着改。**判断：等 studio 的界面结构稳定下来再补**，否则是在给流动的水泥立钢筋。
真要紧的是它已经复用了 `:agent` 与 `:watchface-editor`，那两块有 54 + 26 个测试文件
兜底，风险不算裸露。

### 3.4 APK 体积：先量 release，别照着 debug 判断

`debug` 包 31 MB、未压缩 dex 合计约 71 MB，这个数字没有决策价值（debug 不混淆、
不压缩、含调试信息）。要看的是 release：`assembleRelease` 后的 APK 大小、`classes*.dex`
分布、`resources.arsc` 与图片资源占比。

已知的一个可疑点是 `res/drawable-nodpi/author_avatar.png` 单文件 1.15 MB——头像不需要
这个尺寸。但这是个别资源问题，不是「25k 行打进两个 APK」那种结构问题；在没量过
release 之前，不要按体积立项。

### 3.5 设置页剩下的两屏：已决定不搬

`AOD`（11 条目 / 139 行）与 `WATCH_FACES`（7 条目 / 175 行）有意保留手写，
理由与审计 WARN 基线的读法都写在 `settings-data-model.md` 第八节。

### 3.6 根函数拆路由表：可选，不是欠账

`SettingsRootScreen` 仍是单函数 2931 行、30 个 `when` 分支。拆成
`Map<SettingsDestination, @Composable (Args) -> Unit>` 能让「加一个屏」从「改 2900 行
文件里的一个分支」变成「写一个函数 + 注册一行」，但要碰 30 个分支内部的 `remember`
归属，风险高于前面所有版本。现在的结构（路由集中、内容分层、契约测试钉 key）已经
能长期维护，所以它是**可选项**。

## 四、排序依据

按「失效是否静默」排序，而不是按行数：

| 事项 | 失效方式 | 排序 |
| --- | --- | --- |
| ViewModel 四处声明 | 漏一处不报错，用户点「恢复默认」才发现 | 最高 |
| 设置页根函数 | 改动靠人工核对 30 个分支 | 中 |
| SmartStackLayer | 运行时卡顿 / 错位，测试抓不到 | 中（风险高） |
| studio 无测试 | 改动后没人知道坏了 | 低（等结构稳定） |
| APK 体积 | 装了才知道大 | 低（先量） |
| 两屏不搬 | 无 | 已关闭 |

行数只说明「读起来累」，**静默失效才说明「会坏」**。重构顺序应当跟着后者走。
