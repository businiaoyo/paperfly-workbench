# Y700 平板功能补齐：0.17.0-test.2

2026-11-13，`versionCode 40`、配置 **schema 7**。用户确认目前只有平板，先完成其余实现；外接显示器及实体键鼠验收暂缓。测试包不推送 NAS，未改更新清单或发布渠道。

## 本版已实现

| 功能 | 行为与验证 |
| --- | --- |
| Dock 固定应用 | 独立选择入口，点击只固定、不启动；支持调整顺序和取消固定。固定项绑定当前运行实例，同应用的额外窗口仍单列。实机点选浏览器，原四张卡片及焦点不变。 |
| 最近应用区 | 结合工作台使用记录和系统最近任务，按包去重，最多显示三个额外应用；不可用条目不显示在最近区。用户固定的缺失应用和已配置卡片继续保留。 |
| Dock 显示与自动隐藏 | 设置随工作台保存；空闲后隐藏，底部“显示 Dock”可唤回；弹窗、拖动和总览期间暂停计时。仅改变宿主布局，不改变应用画布。 |
| 分页总览 | 横屏每页六窗、竖屏每页四窗；前后页和当前页显示，离页窗口不接受触摸。使用原 TextureView，不生成截图替代实时画面。七窗口实机分页断言通过。 |
| 同应用总览 | 窗口菜单“此应用所有窗口”，可返回全部窗口并搜索。三个同应用独立实例过滤、切换通过。 |
| 最小化持久化 | 按稳定窗口 ID 保存；进程停止再重开仍保持最小化。显式保存的应用组合清除临时最小化与最近使用记录，避免下次打开组合看不到应用。 |
| 工作台内多指手势 | 四/五指左右切换，上滑总览，捏合回桌面；开始接管时取消原应用触摸流。菜单“手势与快捷键”可暂停本次会话的多指手势。四/五指切换及上滑通过宿主真实分发链的合成多点事件验证；没有真人多指触摸或触控板验收。 |
| 不可用应用恢复 | 监听本机包状态，异步更新名称与图标；停用/卸载保留配置和占位。启用/重装后重开对应自有显示器，其他应用的显示器、画布和 Surface 不变。迟到的元数据与打开回调不能覆盖新状态。 |
| 系统磁贴 | 通过系统磁贴点击入口启动正常桌面入口，进入工作台成功；测试临时增加的磁贴已移除，原列表保持一致。 |

四/五指左右切换的对照来源为 [Apple 多任务与手势设置](https://support.apple.com/guide/ipad/change-multitasking-gestures-settings-ipad-ipad2q9j404h2/ipados)。本软件的手势只在自己的工作台窗口内生效，上滑用于本软件总览；不代表接管 Android 全局导航或复刻系统触控板协议。

## 数据与模块边界

- `WorkspaceOptions` 是纯 Java 不可变模型，包含固定项、最近项、最小化窗口 ID 和 Dock 偏好；不保存 display/task/Surface。旧 schema 1–6 自动补默认值；非法、损坏、未来版本不覆盖原文件，提交失败可以重试。
- `WorkspaceDefinition.withCards` 仅在用户移除成员时清理相应最小化 ID；暂时缺失的应用仍在模型内。`withStyle`、重命名、最近入口与保存组合保留各自需要的偏好，草稿和保存组合相互隔离。
- `DockModel` 决定分区和去重，`DockCatalog` 在后台读取元数据并丢弃旧代次，`DockAutoHide` 只控制宿主显示；`DockPreferencesPanel` 负责设置，复用 `AppPanel` 的固定项模式。
- `OverviewPages` 是纯 Java 分页/过滤，`OverviewControls` 只呈现导航。`MultitouchGesture` 判定意图，`StageTouchGestures` 处理取消和分发；两者没有系统输入权限。
- `PackageEvents` 只监听本机包变更；恢复使用原有、校验所属显示器及 session 的服务方法。未增加 shell RPC、root、Shizuku 或其他产品运行依赖。

## 验证结果

- `python3 scripts/check-architecture.py` 通过；边界检查增加 DockModel、OverviewPages、MultitouchGesture。
- `:app:testDebugUnitTest` **206 项通过，0 失败**。新增 schema 6→7 迁移、稳定身份、保存组合隔离、删除清理、损坏/未来版本保护、写入失败重试、Dock 分区去重、分页及多指意图/取消测试。
- `:app:assembleDebug`、`:app:assembleRelease`、`:app:lintDebug` 通过。Lint **0 错误、50 警告**，test.1 为 45；新增包括自建 View 构造、文字拼接及触摸无障碍提示。不是完整无障碍验收。
- `TabletValidation` **29 项实机断言通过**。通过 App 私有文件一次性启动，无导出入口；release 为空实现。覆盖 Dock/分页/过滤、宿主多点手势路由、进程重启、停用/启用及卸载/重装恢复，同时比较未受影响窗口的 display、画布和 Surface 对象身份。
- 原 `ParityValidation` **21 项回归通过**：两个独立 task/display 的同应用实例、合并拆分、两种全屏、总览、最小化、同组焦点、侧栏和弹窗关闭。
- UI 实操检查固定与取消固定、自动隐藏及唤回、竖屏四窗总览、横竖屏持续画面、竖屏两种全屏及返回、侧栏末尾加号。修正底部唤回文字被裁切、固定选择面板混用“添加应用”文案、卸载后最近区残留图标；触摸和滚动持续刷新空闲计时，避免操作中收起 Dock。
- 后台停留约 147 秒，超过 60 秒会话保留期限：虚拟显示器全部释放，仍停留系统桌面；重入后的配置完全一致，四个原第三方任务 ID 保留并接续到新显示器，截图确认四窗画面恢复。诊断 `live_scene` 是事件触发快照，不以某次读取的旧帧计数判断黑屏。

证据仅在本机 `.runtime/y700-tablet-test2-20261113/`：`before/` 是本轮新快照，`source-before/` 是源码基线；`build-final.log`、`tablet-result.json`、`parity-result.json`、`pinned-browser.json`、`portrait*.png/xml` 和后台验证转储。截图可能包含用户内容，不上传。

## 尚未完成与验收边界

1. **跨应用原生内容拖放未实现**。test.1 的原生及指针路由试验未产生目标 DROP_SUCCESS。本轮进一步核对 Android 16 的 [IWindowSession](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android16-release/core/java/android/view/IWindowSession.aidl)、[DragState](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android16-release/services/core/java/com/android/server/wm/DragState.java) 及此前 DragDropController：正常投递依赖系统 InputChannel 目标；直接报告目标是 system_server 内部路径，公开 Binder 的 accessibility drop 又要求对应类型的拖动。[InputDispatcher](https://android.googlesource.com/platform/frameworks/native/+/refs/heads/android16-release/services/inputflinger/dispatcher/InputDispatcher.cpp) 的 `addDragEventLocked` 要求源/目标显示器相同或在同一拓扑；[DisplayTopologyCoordinator](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android16-release/services/core/java/com/android/server/display/DisplayTopologyCoordinator.java) 的普通注册规则排除 VirtualDisplay。Y700 实际 dumpsys 的拓扑图为空、display_topology 标志为 false，未修改系统实验开关或强行改写全局拓扑。现有独立 VirtualDisplay 架构和权限边界下没有已验证的完整桥接方案；不能用剪贴板、伪造接收成功或只转发坐标代替内容及 URI 授权。
2. 外接显示器独立工作区、跨屏迁移/断连恢复仍未实现；本轮用户确认缺少外接硬件，暂缓相应验收。
3. 实体键盘、鼠标、触控板未验收；快捷键仍要求宿主收到事件，不能宣称具备全局 Alt+Tab。真人多指手势也须后续触摸验收。
4. 低内存压力、长期功耗、所有第三方内部页面恢复及跨厂商兼容没有完整覆盖。一次本机验证不能推广为所有应用/设备支持。

因此本版是继续补齐后的测试版本，**不能宣称已经完全对标台前调度**。schema 7 不能由旧版本直接读取；升级前的本轮原始配置已保留，不做静默降级。

## 最终现场与交付

最终 APK 已安装到 Y700，并使用 App 内“已配对，直接激活”恢复服务。原四个应用、卡片 ID、顺序、焦点、外观及保存组合与本轮新快照一致，新增 options 恢复默认。普通模式，自动旋转 1 / user_rotation 1，USB 常亮原值 0，快捷设置列表原样恢复；临时 probe 已卸载，测试触发文件已清理。

交付 `dist/OpenWorkbench-0.17.0-test.2-standalone-debug.apk`，SHA-256：`132af662a5df8842875d2238124979f7c41575b429538d558e2a586f4089bca5`，同目录提供校验文件及本记录。测试版未发布 NAS。
