# 表盘商店与 Studio 创作边界

## 模块职责

当前工程为九模块、两应用：`:app`、`:studio`、`:ui-kit`、`:watchface-core`、`:watchface-runtime`、`:watchface-editor`、`:watchface-store-client`、`:watchface-store`、`:agent`。

- HOME（`:app`）承载商店浏览、安装、设置和已安装表盘消费，通过 `WatchStoreHost` 连接商店；不引入 editor、Agent 或 FFmpeg。设置页不再提供“编辑当前安装表盘”行，卡片选择器仅保留内置表盘的 `canEdit` 配置入口；商店按钮直接启动 `WatchStoreActivity`，不绕行 Studio。
- Studio（`:studio`）只承载创作编辑器和 Agent 设置，不声明商店 Activity，也不依赖商店 UI。
- `:watchface-store-client` 承载 API、凭据、元数据、目录与安装事务，是 editor 发布和 store UI 共用的数据层。
- `:watchface-store` 承载商店 Activity、Web 页面、浏览与上传 UI，不提供打开表盘编辑器的入口，不导入 `WatchEditorSession`。
- `:watchface-editor` 通过 client 发布作品，不反向依赖 store UI。
- `:watchface-runtime` 的 `WatchArchiveService` 负责已安装表盘归档，商店不能借用 editor Session 导出。

## 回归保护

- app 的 `WatchStoreModuleBoundaryContractTest` 扫描项目依赖闭包、生产源码 import 与 Studio manifest，保护 HOME/store 与创作依赖边界；依赖解析不限定 `implementation`，同时覆盖 `api(project(...))`，并有独立解析哨兵。商店评论编辑、上传元数据修改不等于表盘创作入口，仍可保留。
- HOME 入口清理后的本轮设置审计基线为 **ERROR 0 / WARN 6 / INFO 8**；减少的一行是 `watchface_editor_current`。这是当前拆分基线，不回写历史设计记录。
- app 的 `TestSourceRoots` 与圆屏 Activity 契约按实际模块读取源码，兼容仓库根或 app 模块工作目录。
- ui-kit 的 `RepositorySourceScan` 覆盖九模块 `src/main/kotlin`、`src/main/java`，不扫描 build 产物。全屏 Dialog 白名单只迁移商店文件位置，不扩大真实规则。
- runtime 的 `RegexIcuCompatibilityTest` 扫描九模块生产 Kotlin 正则，保留原 ICU 花括号规则。
- editor 的 `FeedbackSurfaceContractTest` 从实际九模块源码根定位文件。

## CI 语义

push（含 `v*` 标签）和 PR 只运行 test。test 命令包含九模块的 `testDebugUnitTest`，新增 client/store 均有独立测试任务。APK 构建和上传只由 `workflow_dispatch` 手动触发；签名发布还要求选择 `v*` 标签。拆分不改变这一触发语义。

## 本次静态验证范围

只执行源码扫描、XML 解析与 `git diff --check`，不编译、不运行 Gradle/JVM 测试、不提交。初次九模块等价扫描读取 405 个 Kotlin 生产文件、176 个正则字面量：依赖边界与 ICU 扫描通过；初次发现 Studio 创作页新增原生 `AlertDialog`，生产代码负责人已替换为 `FlulyAlertDialog` 并补同窗口 `FlulyOverlayHost`；复扫 405 个 Kotlin 文件的瞬时提示/原生 Dialog/菜单策略为零违规，未增加白名单或放宽检测。

合并前应重新执行静态策略扫描，并在允许构建的环境运行九模块 JVM 测试。静态等价检查不等于 Kotlin 编译或设备验证通过。
