# 开放工作台：Agent 开发交接手册

> 2026-11-13 test.9：当前源码/Y700 安装为 **0.17.0-test.9 / 47 / schema 8**。稳定显示器缓冲区、BLAST 等待新帧与宿主几何同步提交、扩展保留密度，继续解决 test.8 录屏仍能复现的关闭叠影和内容重载。输入法对自有稳定画布使用物理屏 fallback；`HostImeLayer` 保持键盘显示层归属，交还任务时结束旧输入，避免裁掉按键、键盘不可见或关闭后残留。227 项单测与横竖屏关闭/分屏/Dock 共 450 项实机断言通过；架构、Debug/Release、Lint 通过（0 错误、47 警告）。见 [最终实现、证据和兼容范围](split-close-frame-sync-validation.md)。现场已变为四应用、三卡片：LocalSend 与浏览器分屏，番茄小说和哔哩哔哩为侧卡片；不要恢复下文历史三个独立应用。未发布 NAS。

> 2026-11-13 test.8 历史关闭修正：当时源码/Y700 安装为 0.17.0-test.8 / 46 / schema 8。关闭补位保留实时帧，位置及宽高连续运动，布局和异步画布回调不打断；非等比例变换同步修正透视中心。220 项单测、横竖屏各 112 项实机断言和录屏复核通过；覆盖普通关闭、最后成员接续及全屏中间隔 80 ms 连续关闭。已恢复本轮三个独立应用和旋转设置，未发布 NAS。先读 [关闭闪烁验证](split-close-smooth-validation.md)。不要恢复历史五应用现场；第三方应用自身尺寸重排仍可能有短暂过渡。

> 2026-11-13 test.7 窗口操作改进：用户明确否定 test.6 常驻叉号，因其遮挡应用内容。现已彻底移除；轻点既有分隔条按需管理相邻两个应用，拖动仍实时调比例，Dock 长按/右键仍可关闭单个窗口。当时 218 项单测、横竖屏各 105 项实机断言通过；它们没有覆盖绘制中途闪烁，现由 test.8 增加验证。见 [窗口操作改进验证](split-actions-validation.md)。没有发布 NAS。

> test.6 为被否定的历史方案：当时 219 项单测、横竖屏各 114 项关闭实机断言只证明逻辑运行，不代表 UI 得到认可。详见 [历史单窗口关闭记录](split-close-validation.md)。不要恢复内容上常驻关闭按钮。

> 2026-11-13 test.5 Dock 精简：0.17.0-test.5 / 43 / schema 8。Dock 内容自适应，统一图标/选中状态，添加按钮固定；用户已明确不需要总览，因此整体移除总览状态、界面、菜单、同应用入口、上滑手势及 Ctrl+Alt+O。217 项单测、横竖屏各 54 项 Dock 实机断言通过。见 [Dock 样式与总览移除验证](dock-style-validation.md)。没有发布 NAS。

> 2026-11-13 test.4 分屏跟随修正：0.17.0-test.4 / 42 / schema 8 为每对相邻应用提供分隔条；拖动中实时缩放现有画面，松手保存并完成逻辑画布重排，不盖加载层。份额按稳定窗口 ID 保存，旧比例原样迁移。218 项单测、横竖屏各 75 项实机断言通过。见 [分屏实时调节验证](split-live-validation.md)。

> 2026-11-13 test.3 历史修正：0.17.0-test.3 / 41 / schema 7 对齐默认 Dock 画布并共用整组显示变换。其仅第一分隔手柄、松手才更新画面的限制已由 test.4 替换。见 [分屏占满与比例验证](split-fill-validation.md)。

> 2026-11-13 test.2 历史更新：当前源码为 0.17.0-test.2 / 40 / schema 7，Y700 本轮已授权调试。先读 [平板测试版记录](tablet-test2-validation.md) 和 [首批测试台账](stage-manager-test-development.md)：Dock 固定/最近/自动隐藏、分页及同应用总览、最小化恢复、多指手势、包状态恢复已补齐一批。内容拖放、双屏和全局输入尚未完成；用户确认只有平板，外接硬件验收暂缓。以下旧版本记录保留为历史，不能替代本轮现场。测试版不发布 NAS。

整理于 **2026-11-13**，代码基线 **0.16.0 / versionCode 38 / 配置 schema 4**。本轮代码和自动检查完成，但荣耀设备不在线，0.16.0 未安装或发布，实际设备版本尚未重新读取。供没有参与此前对话的 Agent 接手开发、排查回归和交付使用。版本与设备状态是截至整理时的记录，开始新任务时仍需重新核实。

## 1. 接手顺序与事实来源

1. 先读根目录 [AGENTS.md](../AGENTS.md)，再读本文和 [architecture.md](architecture.md)。按下方索引阅读本次改动涉及的验证记录。
2. 查看 `git status --short`、[app/build.gradle](../app/build.gradle)、相关源码与测试，确认当前工作不是基于过期版本。
3. 把用户要求定位到具体模块；实现、实机验证、安装和发布分别按当轮任务范围执行。纯文档任务不需要操作平板或更新中心。
4. 需要操作设备时，先确认设备身份并保存**本轮新快照**，再修改或安装。历史验证记录不能用作恢复用户现场的数据源。

本文是入口和操作说明；`AGENTS.md` 维护项目约束，`architecture.md` 维护设计，源码描述实际实现，各版本验证记录描述实际测过的范围。用户的新指示优先；发现文档与实现不符时应核实并同步更新，不能机械沿用旧记录。

本机在整理时 Git 的 `main` 尚无提交，大量项目文件显示为未跟踪。不要因为 `git diff` 为空就认定没有改动，也不要执行 `git clean` 或重置来“整理”现场。提交前确认本轮文件范围；不要把整个工作目录直接加入 Git。

## 2. 产品含义与已经确定的交互

这是免 root、单 APK 的 Android 工作台：一个主卡片和可滚动侧栏承载真实应用画面。每个应用使用独立 VirtualDisplay，TextureView 呈现持续画面；不是截图模拟器。App 内无线激活启动本机 shell 辅助服务，不依赖 Shizuku 或另一个手机 App。

| 用户用语 / 操作 | 已确定的含义与行为 |
| --- | --- |
| 应用组合 | 保存一次工作台使用的应用名单和顺序，供下次一键打开；新建时只选择和保存，不启动应用、不改当前工作台。 |
| 分屏卡片 | 同一张卡片内摆放多个真实应用；每对相邻成员可独立拖动调节，画面在拖动中即时跟随。保留组内布局、焦点和稳定 ID。不要把“保存组合”实现成自动合并分屏。 |
| 启动 App / 从桌面重新进入 | 已激活、配置载入成功后直接进入工作台。未激活时进入管理与激活首页；读取失败保留原文件并显示错误。 |
| 左上角返回 / 菜单退出 | 返回管理首页并停留；不会转发为当前应用的 Back，也不会因状态刷新自动跳回工作台。 |
| 系统返回手势 | 返回当前应用；有工作台弹窗时先关闭弹窗。不要把它和左上角按钮重新绑定到同一动作。 |
| 添加应用 | 点一个立即关闭选择面板，再加入；已加入的应用则切换到它。**组合编辑继续多选**。 |
| 首次进入与最近应用 | 当前草稿为空的正常入口会导入最近最多 5 个可启动应用。非空草稿保留已有名单、顺序和分屏；显式创建空白工作台不立即自动填满。 |
| 主次交换 | 整张卡片共同切换，用现有画布做 View 变换；不能因此 resize 或重新启动任务。 |
| 侧栏左/右滑 | 移出整张侧卡片；应用内部滑动仍交给应用。移出后其他窗口应持续显示。 |
| 分屏成员关闭 | 轻点相邻分隔条，在应用图标/名称旁点“关闭”；拖动仍调比例。Dock 长按/右键也可操作单个窗口。应用内容上不放常驻叉号。其余成员按比例补满，最后一个关闭后接续侧栏；关闭是交还后台。 |
| 整个工作台全屏 | 收起顶部操作入口和系统栏，保留侧栏以及左下角菜单入口；不是只把当前应用独占整屏。 |
| 当前应用全屏 | 只放大当前焦点成员，隐藏其他卡片；左下角“返回工作台”恢复原卡片与之前的工作台模式。两种全屏切换均只变换原画布，比例不同允许留边，不能发起批量 resize。 |
| 原子工作台对标 | iQOO 用于原生观察。已对齐部分视觉、最近应用和交互，不等于具备系统 Dock、全局入口或任意自由拖动能力。 |

视觉方向已经过多次用户纠正：主内容尽量占满可用空间；不要恢复厚重深色标题栏、侧卡片整行名称或多余留白。侧卡片图标放在左下角，按四个可见预览安排侧栏。倾斜是共享透视中心的 Y 轴投影，竖边保持竖直；不要退回逐卡片平面旋转。样式和触控始终服从同一几何规则。

## 3. 修改入口速查

主源码目录为 `app/src/main/java/cn/paperfly/workbench/`，测试目录为 `app/src/test/java/cn/paperfly/workbench/`；Binder 接口位于 `app/src/main/aidl/cn/paperfly/workbench/IWorkbenchService.aidl`。下面的类名用于直接搜索，不要求先读完整个 Activity。

| 要修改的内容 | 首先查看 | 边界 |
| --- | --- | --- |
| 启动、返回首页、快捷方式入口 | `MainActivity`、`EntryRoute`、`WorkspaceShortcuts`、Manifest | 自动进入每次启动只消费一次；显式回首页和保存组合快捷方式优先。 |
| 管理首页外观 | `HomeScreen`、`HomeAppAssets`、`HomeWorkspacePreview`、`res/values/home.xml` | 图标异步读取；首页布局示意不是实时窗口。 |
| 添加与选择应用 | `AppPanel`、`PickerBackdrop`、`ApplicationSetEditor` | 单选添加与多选编辑是两种模式；关闭不重建有效 Surface。 |
| 保存组合、缺失应用、迁移 | `model/`、`data/WorkspacePersistence`、`WorkspaceRepository`、`WorkspaceCodec` | 配置唯一读写入口在 data；模型不依赖 Android。 |
| 卡片交换、组内焦点、合并拆分 | `CardSession`、`WorkspaceDraft`、`SplitCardPanel` | 当前真实多应用路径使用 CardSession；StageSlots 保留旧算法测试。 |
| 相邻分屏比例与实时拖动 | `SplitWeights`、`PaneLayout`、`SplitDivider`、`StageActivity.splitPreview` | 份额按窗口 ID 保存；MOVE 只更新临时几何，松手提交并异步调整画布，不盖加载层。 |
| 分屏单窗口关闭 | `SplitDivider`、`SplitWindowMenu`、`StageActivity.splitWindowActions/remove`、`CardSession.remove` | 分隔条轻点传递边界与锚点，菜单只列相邻成员；关闭绑定目标窗口，复用后台交还，常态不覆盖内容。 |
| Dock 样式、固定/最近项、自动隐藏 | `StageDock`、`DockModel`、`DockCatalog`、`DockAutoHide`、`DockPreferencesPanel` | StageDock 自测内容宽度、复用图标 View；添加按钮不随图标滚动。总览已按用户要求整体移除。 |
| 侧栏、主窗、比例、倾斜、点击区 | `StageLayout`、`PaneLayout`、`StageCardPose`、`StageMotion`、`RailAppBadge` | dp/比例定义几何；动画连续转向；多应用卡片保持平面。 |
| 单应用/工作台全屏、恢复原布局 | `StagePresentation`、`StageLayout`、`StageMenu`、`StageActivity.canvasLayout` | 展示区域与普通模式画布几何独立；切全屏不得 resize、重启任务或隐藏/移除有效 TextureView。 |
| 应用画布和厂商兼容 | `CanvasSize`、`CanvasCommitQueue`、`WindowCanvasPolicy`、`StageActivity.resizeCanvas/resizeSurface` | 同卡片共用像素比例，扩大不抬密度；收到对应新帧才提交几何。 |
| 稳定缓冲区与系统绘制同步 | `LiveCanvasArea`、`LiveCanvasSync`、`LiveWindowEngine` | 只操作自有显示器根区域；BLAST 等新画面，固定缓冲区；IME 在物理屏显示，输入仍送焦点应用。 |
| 打开、接续、加载节奏 | `RecentEntry`、`RecentTaskAccess`、`TaskRestore`、`WindowLoadQueue`、`StageLoadingView` | 任务和首帧状态分开；串行启动，退出/移除时撤销待办。 |
| 实时窗口、输入、服务权限 | `LiveWindowEngine`、`WorkbenchService`、`IWorkbenchService.aidl`、`BridgeAccess` | owner UID、会话 token、显示器归属检查必须保留；反射只在适配层。 |
| 退出或移除时闪出应用 | `LiveTaskHandoff`、`TaskHandoff`、`LiveWindowEngine.close/remove` | 临时隐藏 → 交还物理屏后台 → 恢复可见 → 释放空显示器。 |
| 返回手势与焦点 | `StageBackGesture`、`EdgeBackGesture`、`GestureWindowCallback`、`backLiveWindow` | 等服务返回结果后按当前界面重新确认焦点。 |
| 菜单与弹窗 | `WorkbenchDialog`、`HostModalSurface`、`WorkbenchUi`、`WorkbenchIcon`、`StageMenu` | Stage 非文本菜单使用宿主内面板；命名框保留输入法所需的独立 Dialog。 |
| 背景与系统栏 | `InsetBackdropHost`、`WorkspaceBackground`、`WallpaperBackdrop`、`WorkspaceStylePanel` | 背景覆盖系统栏，内容避让安全区；不把主题逻辑放进 shell 服务。 |
| 内存采样 | `MemorySampler`、`PssParser`、Stage 的 sampler 队列 | 低频异步；PSS 包含应用本身，不是工作台额外开销。 |
| 无线激活 | `activation/`、`ActivationHost`、`Activation`、`StandaloneServer`、`BridgeState` | 本机回环连接、固定启动脚本、私有不备份密钥；禁止任意主机或命令输入。 |
| NAS 软件更新 | `SoftwareUpdateActivity`、`update/`、`scripts/publish-update.mjs` | App UID 检查/下载/校验；系统安装器安装；管理凭据只在开发机。 |

`StageActivity` 仍较大，负责 View、生命周期和交互编排。新功能应沿已有类的职责扩展，或抽出窄接口的独立类；不要继续把配置存储、网络更新、菜单外观和厂商接口都堆进去。

## 4. 数据和生命周期需要守住什么

配置路径为 App 私有 `files/workspaces.json`。`WorkspaceLibrary.current` 是自动保存的当前草稿，`saved` 是用户明确保存的组合。名称、排序和临时 displayId 都不是身份；工作台与卡片使用稳定 ID，一张卡片可包含多个 `AppTarget`。

`SharedPreferences/workspace` 中旧组件字段仅用于迁移，`live_scene`、`rail_add`、模式、内存等字段用于诊断，不能拿来替代配置恢复。应用暂时缺失或启动失败不得从配置中删除。修改持久字段必须提升 schema，并覆盖迁移、未来版本、损坏文件、写入失败重试和保存组合隔离；当前 schema 3 → 4 只补 `wallpaperBackground=false`，保留旧背景。

运行链路可按这四步理解：

1. **入口**：Main 异步读取配置、确认服务；EntryRoute 决定进入 Stage 或停留管理首页。
2. **承载**：Stage 创建真实画面目标，服务验证权限与会话，优先接续独立的后台任务，无可接续任务才启动应用。
3. **显示**：等待打开结果与首帧；复用任务缓冲 180 ms，冷启动缓冲 900 ms，占位层一次淡出 200 ms。首帧不代表广告或网络加载已经结束。
4. **交还**：关闭/换会话只处理自己拥有的显示器与对应用户任务；释放前将任务交还后台，不能拉起桌面或把旧宿主强行置顶。

持久化、显示器操作与 PSS 采样分别排队；动画/输入路径不能同步读写磁盘。迟到回调必须检查会话、生命周期或代次，不能恢复已经退出的窗口。宿主保持可聚焦并带 `FLAG_ALT_FOCUSABLE_IM`，不要恢复 `FLAG_NOT_FOCUSABLE`，否则可能出现物理屏失焦和 ANR。

## 5. 典型问题：从哪里查，哪些旧方案不要重用

| 症状 | 首查与已知结论 | 证据 |
| --- | --- | --- |
| 左滑移除后主窗闪一下 | 不仅查任务前置，还要查布局复算是否无条件给所有窗口盖“正在调整窗口”。0.15.0 已只调整画布真正变化的成员。 | [入口、移除与更新验证](direct-entry-update-validation.md) |
| 全屏切换后应用重载、播放状态丢失 | 旧全屏按扩展后的区域批量 resize。0.16.0 使用普通安全区画布，scene 的沉浸尺寸变化只触发 View 重排；尚待荣耀实机复验。 | [单应用全屏与状态保留](app-fullscreen-validation.md) |
| Home 后又闪出工作台中的应用 | 直接释放有任务的显示器会把应用交还到前台；需要 TaskHandoff 的隐藏及后台排序，不要用强拉宿主掩盖。 | [退出回桌面](home-exit-validation.md) |
| 打开菜单整屏闪烁 | 联想上的独立 Dialog 会触发壁纸/模糊合成变化；仅删模糊不能解决。使用已有 HostModalSurface。 | [菜单闪烁](menu-flicker-validation.md) |
| 弹窗关闭后静态侧窗黑屏 | 先查 Surface 是否失效、绑定和窗口尺寸，不要每次关闭就重建所有 TextureView。 | [功能扩展及恢复](feature-expansion-validation.md) |
| 重新进入应用总像冷启动 | 区分 task/process 复用、Activity 重建和应用自行刷新；迁移中间配置可能导致重复重建。不能只凭看到开屏就断言进程重启。 | [后台接续](warm-entry-validation.md) |
| 荣耀微信缩小后空白或退出 | 厂商平行视界残留原屏坐标；WindowCanvasPolicy 为该应用保留原生画布再做 View 缩放，不对所有应用一概放大。 | [微信加载](wechat-loading-validation.md) |
| 已配对仍报证书不认识 | 先比对实际无线连接端口与 NSD 公告；荣耀曾出现同名服务旧端口残留，不能直接清密钥重配。 | [荣耀重新激活](honor-reactivation-validation.md) |
| 输入配对码后自动激活连接超时/拒绝 | 荣耀 NSD 曾把联想服务解析到本机地址。除地址还须核对 TXT 型号/API；配对后按认证 GUID 匹配连接服务。失败不能直接归因于配对码或密钥。 | [配对身份与端口验证](honor-pairing-identity-validation.md) |
| USB 连接时成功、拔线后配对成功但无法激活 | 检查荣耀“仅充电模式下允许 ADB 调试”及 USB 调试；NSD 可能把连接服务改名为 GUID (2)。用本次 failure_trace、实际尝试次数和 USB 断开时间定位，不能用插线成功代替拔线验证。 | [荣耀拔线复验](honor-wireless-unplugged-validation.md) |
| 倾斜像旋转照片、图标偏移 | 以实际投影四角比对，保留共享透视中心和矩阵映射；0.14.3 的独立平面转角已被用户否定。 | [原生透视](native-perspective-validation.md) |
| 状态栏与背景割裂 | 背景覆盖整屏、内部 scene 单独避让 Insets；不要只给内容区加背景遮罩。 | [背景衔接](statusbar-seam-validation.md) |

读历史文档时注意版本。比如 [roadmap.md](roadmap.md) 保留了早期“多应用仅模型支持”和“尚未发布”的现场，不能据此判断现在仍未实现；当前边界以架构和对应最新验证为准。

## 6. 构建和自动检查

现有配置：JDK 17、Gradle Wrapper 8.5、Android Gradle Plugin 8.2.2、compile/target SDK 34、min SDK 30。窗口与配置使用 Java；`:wireless-adb` 指向 `third_party/libadb-android-bc`，许可证需保留。不要为做小改动顺手升级工具链或改签名。

在项目根目录执行；下面是此开发机的环境路径，换机器后先定位实际 JDK/SDK：

```sh
export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
export ANDROID_HOME=/Users/paperfly/Library/Android/sdk
python3 scripts/check-architecture.py
./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug
```

修改无线协议或该依赖模块时，另跑 `./gradlew :wireless-adb:testDebugUnitTest`。不能用单元测试代替跨应用焦点、首帧或厂商窗口行为的实机检查。

| 产物 | 位置 |
| --- | --- |
| Debug APK | `app/build/outputs/apk/debug/app-debug.apk` |
| App 单元测试 XML | `app/build/test-results/testDebugUnitTest/` |
| Lint 报告 | `app/build/reports/lint-results-debug.xml` / `.html` |
| 本轮日志、截图和设备快照 | `.runtime/<任务名-日期>/` |
| 交付 APK 与可分享的验证摘要 | `dist/` |

0.16.0 本轮 App 183 项测试通过，Lint 0 错误、37 警告（无新增）；协议层未改动，未重跑，上次 0.15.2 为 26 项通过。这些是带版本的记录，不是下次可以直接复制的验证结论；读取本轮结果，新增警告要核对。`.runtime/`、`dist/`、构建产物、密钥和本地环境配置被忽略，不应假定另一台机器已经拥有这些文件。截图与转储可能含用户数据，文档引用结论即可，不把原始现场上传到公开仓库或更新中心。

## 7. 实机操作与现场保全

| 设备 | 序列号 | 用途和边界 |
| --- | --- | --- |
| 荣耀 WIN 平板 HES-W00 | `AKDPBB6A08000069` | 项目约定的调试设备；0.15.2 在此验证物理拔线后通知配对及自动激活；0.15.0 已完成 NAS 更新安装验证。 |
| 联想 TB323FU | `HA2HEFTM` | 历史 0.14.x 验证设备；不要假设仍在线或仍有旧配置，使用前核实本轮目标。 |
| iQOO Pad Pro / vivo | `403GAA02RD63200` | **仅原生研究参考**；未经用户新指示，不安装、不修改。 |

所有 ADB 命令显式指定序列号。不要依赖“只有一台设备”，不要自动选择第一台。更新 APK 前先退出工作台到管理首页，完成任务交还，再走安装流程；系统确认仍需正常完成。

下面是**已安装可调试包**的只读快照模板；先确认设备身份。首次未安装或 `run-as` 不可用时记录这一事实，不把空文件当有效备份：

```sh
device_serial=AKDPBB6A08000069
adb -s "$device_serial" get-state
adb -s "$device_serial" shell getprop ro.product.model
mkdir -p .runtime
evidence_dir="$(mktemp -d "$PWD/.runtime/agent-$(date +%Y%m%d-%H%M%S)-XXXXXX")"
adb -s "$device_serial" shell run-as cn.paperfly.workbench cat files/workspaces.json > "$evidence_dir/workspaces-before.json"
adb -s "$device_serial" shell run-as cn.paperfly.workbench cat shared_prefs/workspace.xml > "$evidence_dir/prefs-before.xml"
adb -s "$device_serial" shell dumpsys activity activities > "$evidence_dir/activities-before.txt"
adb -s "$device_serial" shell dumpsys window > "$evidence_dir/window-before.txt"
adb -s "$device_serial" shell dumpsys package cn.paperfly.workbench > "$evidence_dir/package-before.txt"
adb -s "$device_serial" shell settings get system user_rotation > "$evidence_dir/user-rotation-before.txt"
adb -s "$device_serial" shell settings get system accelerometer_rotation > "$evidence_dir/auto-rotation-before.txt"
screenshot_device="/sdcard/$(basename "$evidence_dir").png"
adb -s "$device_serial" shell screencap -p "$screenshot_device"
adb -s "$device_serial" pull "$screenshot_device" "$evidence_dir/before.png"
adb -s "$device_serial" shell rm -f "$screenshot_device"
```

同时核对画面中的当前主窗、应用顺序、普通/全屏模式。诊断字段可能是上次会话的残留，尤其 App 当前在桌面时。每次点击用新读取的 UI/截图定位；UIAutomator 有时返回虚拟显示器内的第三方应用树，不等于物理屏工作台的点击坐标。对物理屏的输入可显式使用 `input -d 0`，不能复用历史坐标盲点。

2026-11-12 荣耀验证中，`exec-out screencap` 曾得到无效 PNG，改用 `shell screencap -p` 保存到设备再 pull 得到正常图片。`logcat` 也可能不可读；如实记录，改用任务/窗口转储、实际画面、诊断字段及 `/data/local/tmp/paperfly-workbench-server.log`，不要虚构没有采集到的日志证据。

优先用 App 内“已配对，直接激活”恢复服务。普通退出重入不需要重新激活；设备重启、APK 更新或服务被终止后需要。USB 仅备用，沿项目的 [激活脚本说明](../README.md) 指定目标设备。不要清除 App 数据、卸载重装、杀第三方进程或删除配对密钥来绕过问题；不得操作账号或接受第三方首次协议来通过测试。

临时测试应用优先在 App 内加入/移除；保留原卡片稳定 ID。需要暂存组合时通过 App 管理入口保存，并在结束后删除本轮临时组合。不要用直接覆盖 `workspaces.json` 绕过 Repository 与缓存，也不要拿历史快照覆盖用户刚做的新修改。恢复本轮调整的旋转设置，核对配置及当前主窗，清理仅由本轮创建的设备端文件。

## 8. 按改动选择实机回归

| 改动范围 | 至少验证 |
| --- | --- |
| UI、布局、菜单、背景 | 横/竖屏 × 普通/整个工作台全屏；侧栏末尾加号；弹窗打开/关闭/旋转；主次交换，真实画面继续刷新。 |
| 卡片移除与释放 | 添加一个临时侧应用再移出；其他显示器、任务、画布保持；无全窗口加载遮罩；Home 后桌面保持前台。 |
| 入口与后台接续 | 未激活入口；激活后直进；左上角返回后停留；Home 后点击桌面图标直进；区分同会话交换与退出重入。 |
| 返回和输入 | 当前成员返回完成；弹窗先关闭；应用根页退出后只恢复原显示器；物理屏仍有正确焦点。不要操作账号数据。 |
| 配置与存储 | 退出重入、仅本 App 进程重启、旧版本迁移；稳定 ID/顺序/分屏保留；已保存组合隔离；损坏/未来版本保护和失败重试。 |
| 激活 | 已配对重新激活；实际端口与发现一致；连接结束后 Binder 独立存活。端口变化、拔线等只对实际测过的场景下结论。 |
| 软件更新 | 旧版查到新清单 → 下载校验 → 系统安装 → 新版本/签名/哈希核对 → 无线激活 → 配置不变；新版再检查无重复升级。 |

复现闪烁要保留问题样本，再用同类场景对比。至少同时看画面和任务/显示器状态；“没崩溃”“测试通过”不等于“没有闪烁或重载”。只改文档时验证链接和命令与项目一致，不把历史设备结果写成本轮新测试。

## 9. NAS 更新发布

现有更新中心：`http://192.168.5.53:8787`；本地服务项目：`/Users/paperfly/Desktop/AI工具/包分发`。工作台使用 `open-workbench / stable`，客户端平台 `android / universal / apk`，详情与命令见 [software-update-publishing.md](software-update-publishing.md)。

App 的入口在管理首页“帮助与设置 → 软件更新”。客户端限制同源 artifact、递增版本、大小、SHA-256、包名与签名，校验后交给系统安装器；没有静默安装或通用 shell 能力。网络与更新逻辑不进入 WorkbenchService。

有发布任务时，沿以下顺序执行：

1. 核实当前稳定版，递增 versionCode/versionName，沿用可覆盖安装的签名，完成必需检查和目标场景实机验证。当前交付是既有 **debug 签名**，不能换机生成另一把 debug key 后直接覆盖老安装。
2. 保存带版本名的 APK 和 UTF-8 更新说明，核对实际包名、版本、签名与 SHA-256。
3. `scripts/publish-update.mjs ... --draft-only` 准备草稿并验证服务端大小/哈希；草稿不对客户端发布。发布动作在当轮授权范围内完成。
4. 发布后验证旧版本得到新清单、当前版本得到 204；最好通过真实旧版 App 完成一次升级，不能拿 ADB 安装成功替代 NAS 更新验证。
5. 核对 NAS 上其他软件的应用和发布记录未被修改。已有工作台注册后，后续发 APK 不需要重复部署整个更新服务。

脚本从开发机本地环境配置加载发布凭据，默认配置位置和可覆盖变量见发布说明。只记录路径和使用方法，**不要读取后打印令牌、把凭据复制进文档/APK、把配置当 shell 脚本执行**。签名私钥与无线身份也不进入交接材料。

0.15.0 的 NAS 部署、备份路径、正式包哈希及真实升级证据见 [本版验证](direct-entry-update-validation.md)。这些值用于追查历史，后续发布必须使用自己的版本与验证结果，不覆盖已发布版本。

## 10. 尚未解决或未充分验证的范围

真实多应用卡片、保存组合、样式/背景/比例、快捷方式、后台任务接续、内置无线激活和 NAS 更新都已有实现，不应重新当成仅模型能力开发。

仍可继续推进：复杂组内布局与拖拽、图片引用资源清理、缺失应用管理、更多厂商/应用/输入法/键鼠/触控笔兼容、长期内存温升功耗、系统回收后的恢复。任意重叠自由窗口、系统级 Dock 和全局工作台手势尚无同等实现。

已知边界包括荣耀电子邮件首次使用页无帧、荣耀微信特殊画布在窄卡片可能留白、受保护视频画面及系统分屏容器任务不能保证接续。应用广告、自行刷新、必要的方向配置变化和低内存回收仍可触发加载。不要承诺“所有应用永不重载”或以一台平板通过推导全部设备兼容。

## 11. 每轮交付应留下什么

新增 `docs/<主题>-validation.md` 或更新对应记录，至少包含：

- 日期、代码/安装版本、实际设备与系统；本轮现场快照位置。
- 复现条件、原因、最终改动及涉及的模块边界；被推翻的方案保留为历史原因，不混进当前操作步骤。
- 自动检查结果、实机通过项、失败/未测项，区分已实现、模型测试覆盖和实机验证。
- 如有交付，写明 APK 路径、哈希、实际安装与激活方式、NAS 发布结果。
- 原主窗、应用顺序、卡片 ID、保存组合、旋转和临时文件的恢复结果。

行为变化同步更新 `architecture.md`、README 和本文；新的长期约束再写入 AGENTS.md。历史记录保留日期，不改写成“当时就已经成功”。用户回复可简短，但下一位 Agent 应能只靠仓库文档重建结论与操作方法。

可复制给下一位 Agent 的任务开头：

> 先读 AGENTS.md、docs/agent-handoff.md、docs/architecture.md 和本次相关验证记录。检查当前版本和未提交改动；实机操作前重新快照目标设备现场。沿现有模块边界实现，保留真实画面、稳定身份和保存组合隔离。完成必需检查和对应实机回归，按本轮范围安装/发布，并更新验证记录。设备、版本、配置均以本轮读取为准。
