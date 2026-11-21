# 设置页数据模型：现状、漂移与迁移规则

`ui/settings/SettingsHierarchy.kt` 的定位是「设置页的唯一真相源」，由
`SettingsEntryRenderer.kt` 把声明式条目渲染成圆屏列表。迁移按屏推进，本文记录
迁到哪一步、数据层与代码差在哪、以及为什么停在这里。

## 一、迁移进度

| 屏 | 状态 | 数据组 |
| --- | --- | --- |
| `APPEARANCE` | 渲染器接管 | `appearance` |
| `APP_LIST_BEHAVIOR` | 渲染器接管 | `app_list_behavior`（按屏新建） |
| `SIDE_SCREEN` | 渲染器接管 | `side_screen` |
| `NOTIFICATION_SETTINGS` | 渲染器接管 | `notifications` |
| `GESTURE_SETTINGS` | 渲染器接管 | `gestures` |
| `FEATURE_TOGGLES` | 渲染器接管 | `feature_toggles` |
| `DISPLAY_SIZE` | 独立文件手写 | 屏内自持（`appearance` 只留一条跳转） |
| `WATCH_FACE_MORE` | 渲染器接管 | `watch_face_more`（按屏新建） |
| `WATCH_FACES` | 手写 | 无（动态表盘列表，有意不搬，见第八节） |
| `APP_LIST_BACKGROUND` | 独立文件手写 | `AppListBackgroundScreen.kt` |
| `PERFORMANCE` | 渲染器接管 | `performance`（按屏新建） |
| `PERFORMANCE_RENDERING` | 渲染器接管 | `performance_rendering` |
| `PERFORMANCE_MOTION` | 渲染器接管 | `performance_motion` |
| `PERFORMANCE_SYSTEM` | 渲染器接管 | `performance_system` |
| `MOTION_PROFILE` | 独立文件手写 | `MotionProfileScreen.kt`（条目数运行时生成） |
| `MUSIC_TEXT_ANIMATION` | 手写 | 无数据组 |
| `TOOLS` | 渲染器接管 | `tools`（按屏新建） |
| `BACKUP` | 渲染器接管 | `backup` |
| `BACKUP_EXPORT` | 渲染器接管 | `backup_export`（与导入共用勾选项） |
| `BACKUP_IMPORT` | 渲染器接管 | `backup_import`（与导出共用勾选项） |
| `ABOUT` / `LANGUAGE` | 手写 | 无数据组（自定义卡片 / 动态语言列表） |
| `AOD` / `ICON_PACKS` / `HIDDEN_APPS` / `DONATE` | 手写 | 无数据组（有意保留） |

注意最后一步（`TOOLS` / `BACKUP*`）**行数反而多了 48 行**：四屏合计只有 8 个条目，
而每屏的 `binding` 至少要写三个被覆盖的方法，比手写的 `row {}` 还长。这一步的价值
不在行数，而在「模型覆盖率」——此后 `SettingsHierarchy` 与实际页面一一对应，
审计脚本的 ERROR 才有意义。**条目少于 5 个的屏，数据化是负收益**，这条经验
写进第七节的判据里。

### 1.1 文件结构

屏迁完后，`SettingsActivity.kt` 里还堆着约 2900 行通用组件——它们不服务任何单个
屏，却让文件停在六千行量级。按职责切出去之后：

| 文件 | 行数 | 内容 |
| --- | --- | --- |
| `SettingsActivity.kt` | 3516 | Activity、根 `when` 路由、跨屏状态 |
| `SettingsHierarchy.kt` | 1244 | 声明式条目模型（唯一真相源） |
| `SettingsPageScaffold.kt` | 952 | 脚手架、key→index 影子登记、圆屏缩放、分组卡 |
| `SettingsRowControls.kt` | 832 | 单选 / 开关 / 滑块 / 动作卡 |
| `SettingsAboutAndDonate.kt` | 704 | 关于、调试、捐赠三屏的自定义内容 |
| `SettingsBackupDialogs.kt` | 546 | 备份勾选弹窗、全屏阻塞进度 |
| `SettingsEntryRenderer.kt` | 399 | 数据层 → Compose 的渲染器 |
| `SettingsScrollMemory.kt` | 106 | 滚动位置账本 |
| `SettingsRoundLayoutPolicy.kt` | 35 | 圆屏首项居中策略 |
| `SettingsSearchIndex.kt` | 188 | 搜索索引 |
| 三个整页搬出的屏 | 406 / 526 / 399 | `AppListBackground` / `DisplaySize` / `MotionProfile` |

`SettingsActivity.kt` 从 8114 行降到 **3516 行**（累计 -4598，-57%）。

切分是纯搬移：函数体一行没动，只把跨文件调用的符号从 `private` 提到 `internal`。
判断符号该不该提，编译器的报错清单比 grep 可靠——它会精确指出「哪个文件第几行
访问了哪个私有符号」。

顺带把 `ui/settings/` 目录的包名统一成 `com.flue.launcher.ui.settings`：搬过来的
组件原先在 `com.flue.launcher`（和 `SettingsActivity.kt` 同包），而目录里已有的
文件都是 `ui.settings` 包，`SettingsRoundLayoutPolicy.kt` 甚至也留着旧包名。
包名与目录不一致不会编译失败，只会让「这个符号在哪」变成需要 grep 的问题。

## 二、关键发现：主题视图与屏视图已经分叉

`SettingsHierarchy` 最早按「主题」分组（外观 / 表盘 / 应用列表 / 动画 / 系统），
这是给信息架构和搜索用的视角。但渲染单位是**屏**——一个 `SettingsDestination`。

前 5 屏恰好一组对一屏，所以分叉没暴露。到了 `app_list` 就露馅了：这一个主题组
横跨 `DISPLAY_SIZE`、`APP_LIST_BACKGROUND`、`APP_LIST_BEHAVIOR` 三屏，而三屏里
只有 3 行属于组内条目。拿它去渲染任何一屏，都会画出别屏的设置项。

**结论：渲染必须按屏建组，主题分组只保留给搜索与信息架构。**
`app_list_behavior` 是第一个按屏建的组，也是后续所有组的模板。

## 三、已修正的漂移

同一设置在两个组各声明一次，`prefKey` 完全相同（7 处，已删除重复的一方，以
`appearance` 为准）。这类问题渲染出来是「两个开关互不同步」，用户会以为设置没生效：

| 设置 | 保留 | 已删 |
| --- | --- | --- |
| 顶部渐隐 | `appearance/top_fade` | `app_list/honeycomb_top_fade` |
| 底部渐隐 | `appearance/bottom_fade` | `app_list/honeycomb_bottom_fade` |
| 启动遮罩 | `appearance/splash_toggle` | `animation/splash_icon` |
| 遮罩时长 | `appearance/splash_delay` | `animation/splash_delay` |
| 高级材质 | `appearance/blur_toggle` | `animation/blur` |
| 边缘模糊 | `appearance/edge_blur_toggle` | `animation/edge_blur` |
| 模糊半径 | `appearance/edge_blur_radius` | `animation/edge_blur_radius` |

`SettingsHierarchyTest.noSettingIsModeledTwiceAcrossGroups` 把这条钉死：任意两个
条目不得共用 `prefKey`。

## 四、尚未修正的漂移

审计脚本当前输出：**ERROR 0 / WARN 7 / INFO 19**。

`app_list` 组已整组删除（13 条）。它是最后一个**按主题**建模的组，横跨的三屏都已
各有着落（`DISPLAY_SIZE` 与 `APP_LIST_BACKGROUND` 整页搬出、`APP_LIST_BEHAVIOR`
按屏建模），所以这 13 条一条也不服务任何页面。删它时连带改了三处：

1. `SettingsHierarchyTest.unmigratedGroupFailsFast` 原来拿 `"app_list"` 当「不存在的
   组」的样本，删组后它会变成「存在的组」，测试反而失败 —— 换成任意未注册的 id。
2. 审计脚本 `SCREEN_GROUP` 里两条指向 `app_list` 的映射（`DISPLAY_SIZE`、
   `APP_LIST_BACKGROUND`）已成死数据，一并清掉。
3. `SettingsHierarchy` 里 `[appList]` 的注释引用会悬空，改写成「这里曾有一个组，
   为什么删」。

这类「删数据」的改动和「加数据」一样需要跑测试：删除失败是静默的，
只有测试能保证没有别的地方还在按 id 取这个组。

WARN 7 条全部来自 `WATCH_FACES`（7 行，条目数随已安装表盘变化）。**这是已知基线，
不是待办**——为什么不处理、以及这个数字该怎么读，见第八节。

WARN 从 61 降到 7，是因为脚本不再把「有意手写的整页」算成未迁移：
`DisplaySizeScreen` / `AppListBackgroundScreen` / `MotionProfileScreen` 里的行 key
被显式排除，之前它们贡献了 30 多条噪音。

### 4.1 key 改名（渲染会静默失效）

| 数据层 id | 代码里的行 key | 屏 |
| --- | --- | --- |
| `app_list_folders` | `app_list_folders_enabled` | `APP_LIST_BEHAVIOR`（随该屏迁移修正） |
| `backup` | `backup_menu` | `TOOLS` |
| `watchface_editor` | `watchface_editor_current` / `_new` | `WATCH_FACES` |

行 key 同时是鱼眼缩放、滚动位置记忆、搜索索引三处的身份。改名不报错，只表现为
「圆屏上这几行不缩放」「返回时跳回页顶」「搜不到」。迁移时以**代码里的 key** 为准。

### 4.2 幽灵条目（代码里已经没有这一行）

- `layout_mode`、`fast_flow_animation`、`honeycomb_fast_scroll_optimization`：
  前者的 prefKey 还在 ViewModel 里（`KEY_LAYOUT`），但没有 UI 入口；后者已被
  `KEY_REMOVED_FAST_FLOW_ANIMATION` 标记为移除，只剩清理旧值的作用。
- `watchface_picker`、`builtin_watchface_config`：入口名与代码不一致，需按
  `WATCH_FACES` 屏实际行重建。
- `classic_return_animation`、`notification_access`：`PLANNED`，保留。
- `animation` 组整体的三个幽灵（`animation_curve_tier` / `animation_override` /
  `classic_return_animation`）已随性能四屏迁移删除 —— 那一组只有 6 条，其中 3 条
  在代码里根本不存在，正是「按主题建模」留下的后遗症。

### 4.3 未建模的整屏

`AOD`（11 行）、`ICON_PACKS`（2 行）、`HIDDEN_APPS`（1 行）、`DONATE`（3 行）
在数据层完全没有对应组。`appearance` 组里只有一条 `aod_settings` 跳转项，
不是 AOD 屏本身。

## 四·五、布局语义：CardPlacement

数据驱动最容易丢的不是值，是**卡片边界**。手写版用两种写法表达它：并卡的
`flueSettingsGroup { row {} }` 与独立的裸 `item {}`。迁移到数据层后换成
[CardPlacement]：

| 值 | 含义 | 手写版对应 |
| --- | --- | --- |
| `AUTO` | 开关/滑块接前一张卡，动作/导航/单选各自独立 | 默认行为 |
| `ALONE` | 独占一张卡，前后都断 | 单个 `flueSettingsGroup` / 裸 `item` |
| `START` | 开一张新卡，后续可并入 | 新起一个 `flueSettingsGroup` |
| `INLINE` | 强行并入当前卡 | 组内第二条 `row` |

另外两个配套字段：

- `Picker.independentOptions`：每个选项独占一张卡（温度单位、日期语言）。
  默认的 Picker 会把所有选项塞进同一张卡，那是「布局」屏的形态。
- `Action.icon` / `binding.icon()`：行首图标。数据层只声明语义
  （`EntryIcon.SETTINGS` / `REFRESH` / `OK` / `FOLDER`），`binding` 可按状态覆盖 ——
  授权类条目就是靠这个在「齿轮 ↔ 对勾」之间切换。

`SettingsHierarchyTest.watchFaceMoreKeepsHistoricalCardBoundaries` 把
`WATCH_FACE_MORE` 的 17 张卡逐张钉住。这类回归不崩、不报错，只是圆角和行距变样，
没有测试就只能靠肉眼发现。

## 五、迁移一屏的步骤

1. **以代码为真相源**，照抄行 key（不改名）、顺序、`groupPosition` 的成卡方式。
2. 在 `SettingsHierarchy` 新建按屏命名的组，id 用屏名的小写下划线形式
   （`app_list_behavior`），加进 `groups` 与 `renderedGroups`。
3. 文案只写 `titleRes` / `subtitleRes`，`title` 留空；状态回显类开关必须同时给
   `subtitleOnRes` / `subtitleOffRes`。
4. `SettingsActivity` 里把 `flueSettingsGroup { row(...) }` 换成
   `settingsEntryGroup(entries = renderedGroup(...), binding = object : SettingsEntryBinding {...})`，
   `binding` 只保留「值从哪来、点了做什么、什么时候置灰」。
5. 加契约测试：行 key 序列、`titleRes` 非空、双态文案、`prefKey` 不重复。
6. 门槛：`./gradlew :app:testDebugUnitTest` 全绿 + `python3 tools/settings-drift-audit.py`
   里该屏不再出现在「未迁移」。

## 六、审计工具

```bash
python3 tools/settings-drift-audit.py            # 覆盖率表 + 三类漂移
python3 tools/settings-drift-audit.py --check     # 有 ERROR 退出 1
python3 tools/settings-drift-audit.py --screen WATCH_FACE_MORE
```

分级原则：**ERROR 只放「会画错的东西」**——幽灵条目、key 改名。
「代码有、数据无」属于没迁完，归 WARN，否则 80 行噪音会把真问题淹掉。

## 七、什么时候不该数据化

`DISPLAY_SIZE` 与 `APP_LIST_BACKGROUND` 都保留了手写，判断标准是同一条：
**条目里超过一半不是「一个值 + 一个回调」**。

- `DISPLAY_SIZE`：布局档案列表（运行时才知道数量）、心流曲线滑杆（跟着档案生成）、
  形状预览（纯绘制）—— 没有一个能写成静态声明。
- `APP_LIST_BACKGROUND`：实时预览卡、四项互斥单选、两块条件项、两条带跨行 footer
  的滑块 —— 要数据化就得给模型加「动态条目」「条件组」「组尾自定义内容」三类逃生舱。

一旦为个别页面给模型开逃生舱，「用数据描述界面」就会退化成「用另一种写法写代码」，
还多了一层没必要的间接。这类页面的正确做法是**整页搬到独立文件**，让
`SettingsActivity` 只负责路由。

还有一条更简单的成本判据：**条目少于 5 个时不要数据化**。每个 `binding` 至少要
覆盖 `isChecked` / `onToggle` / `onOpen` 三个方法，再加 `scaleOf` 一行 ——
对一个 2 行的导航屏，这套壳比手写两段 `row {}` 还长（`BACKUP` 屏实测多了 6 行）。
数据化的收益随条目数线性增长，而壳的成本是常数。

搬页时有两个坑：

1. **状态归属**。纯瞬态（拖动中的滑块值）跟着页面走；需要跨页面存活的
   （选图后待裁剪的 Uri、文件浏览器请求、弹窗开关）留在根作用域，用回调传进去。
2. **预览解码的条件**。原代码靠 `destination == APP_LIST_BACKGROUND` 判断要不要解码
   背景图；搬进页面后「不在页面就不组合」天然满足，条件可以整个删掉——但要确认
   删除后没有别的调用点依赖那份 painting。

## 八、到此为止：迁移线的边界

从 8114 行到 3516 行（-57%）。十五屏交给渲染器、三屏整页搬出、五个组件文件独立
成篇。**这条线到此收口**，剩下两屏有意不动：

| 屏 | 条目 | 分支代码 | 为什么不搬 |
| --- | --- | --- | --- |
| `AOD` | 11 | 139 行 | 含实时预览与像素时钟，搬走只是换个文件放 |
| `WATCH_FACES` | 7 | 175 行 | 条目数随已安装表盘变化，搬走也不进数据层 |

依据是第七节那条：**搬迁的收益是「让主文件变小」，前提是那段代码本身值得独立**。
这两屏的代码结构正常——一个开关、几个滑块、一段预览，只是恰好住在巨型文件里。
搬完之后 `SettingsRootScreen` 从 2931 行降到 2600 行出头，仍然是**一个函数、28 个
`when` 分支挤在同一次组合里**。数字好看了，读起来没变。

这也是一条该记住的判据：**重构的收益要在「下一次需要读它的人」身上结算**，不在
行数差上结算。前十一版的每一版都在拆真实的耦合（主题视图与屏视图分叉、卡片边界
无法用值表达、组件挤在同一个文件里），剩下的两屏没有耦合可拆。

### 8.1 那两个 WARN 不是待办

审计脚本当前输出 `ERROR 0 / WARN 7 / INFO 19`。7 条 WARN 全落在 `WATCH_FACES` 上，
就是上表第二行，不是遗漏。要把这行输出当信号用，得按基线读：

| 输出 | 含义 |
| --- | --- |
| `ERROR > 0` | 会画错（幽灵条目、key 改名），必须修 |
| `WARN > 7` | 有人新写了一条没有归属的行，去看那一条 |
| `WARN == 7` | 已知状态，正常 |

这条基线目前**只存在于文档里**。要让 `--check` 真能进 CI，得在
`tools/settings-drift-audit.py` 的 `UNMODELED_SCREENS` 里加上 `AOD` 与 `WATCH_FACES`，
让这 7 条从 WARN 降到 INFO。只是几行映射，但会改掉「7」这个数字——等真接 CI 时再动，
现在动等于为了好看而改计量口径。

### 8.2 如果还要投入，投在根函数

不是搬剩下两屏，而是把「屏」变成一等公民：

```kotlin
private val screens: Map<SettingsDestination, @Composable (SettingsScreenArgs) -> Unit>
```

每个屏做成独立的 `@Composable` 扩展函数，根函数只剩「取当前 destination、查表、
传参」。这一步会碰 30 个分支，风险明显高于前几轮——每个分支内部的 `remember`
都要判断哪些是屏内瞬态、哪些必须留在根作用域（`APP_LIST_BACKGROUND` 那次踩过：
拖动值、选图 Uri、弹窗开关的归属各不相同）。

收益是根函数从 2931 行落到几百行，且每屏可单独预览与测试。**但它是可选的一步**，
不是前面工作的欠账：现在的结构（路由集中、内容分层、契约测试钉住 key）已经能
长期维护，拆表只是为了让「加一个屏」从「改 2900 行文件里的一个分支」变成「写一个
新函数 + 注册一行」。

做之前建议先补两件事：`:studio` 模块目前 0 个测试文件（拆过去的代码没有回归网），
以及 `:watchface-runtime` 的 25k 行被打进两个 APK（纯收益的体积问题，和这次重构
无关但更划算）。

设置页之外的待办（`LauncherViewModel` 的「一个设置写四处」、`SmartStackLayer` 的
3833 行巨型 composable、`:studio` 零测试、APK 体积）记在
[`refactor-backlog.md`](refactor-backlog.md)——那份文档同时也留档「判断过但不做」的
结论，避免同一个念头被反复提起又反复放下。
