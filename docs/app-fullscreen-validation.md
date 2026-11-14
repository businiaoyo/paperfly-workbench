# 0.16.0 单应用全屏与状态保留

日期：2026-11-13。代码与本地 APK：versionName **0.16.0** / versionCode **38**，配置 schema **4**，沿用 debug 签名。

## 范围与原因

用户要求加入看视频时使用的单个应用全屏，并指出既有全屏会重载所有应用、没有保留状态。

代码检查确认旧 `setFullscreen → relayout → resizeCanvas` 路径随主显示区域扩大重新计算每个成员的画布；系统栏导致的 scene 尺寸变化还会设置 `layoutDirty`。实际宽高改变后调用 `resizeLiveWindow`，应用可以按配置变化策略重建 Activity。旧方案即使保留了 task/display ID，也不能据此声称页面或播放状态未重载。

最终修改：

- `StagePresentation` 只管理会话内展示模式。普通/工作台全屏中都可通过菜单进入“当前应用全屏”；目标是当前焦点成员。分屏卡片成员不合并、不拆散，其他卡片顺序与已保存组合不变。
- 单应用模式隐藏侧栏和其他成员、以黑色填充留边；左下角“返回工作台”恢复之前的工作台模式。全工作台模式的“菜单”直接打开菜单，提供“退出全屏”与单应用全屏入口。
- 应用画布按当前物理窗口的普通安全区计算，使用忽略栏可见性的 Insets；与实际展示区域分离。scene 因栏显隐变化只重排 View。两种全屏的进入/退出均不主动 resize 应用、不重建显示器/任务、不替换有效 Surface、不重新盖加载层。
- `StageLayout.fullscreenApp / fitCanvas / member` 统一目标、等比例缩放和组内几何。全屏保留应用原比例，比例不同时允许留边，不拉伸、不裁切内容。实际旋转、物理窗口尺寸、外观比例或分屏布局变化仍可能 resize 并触发应用自己的配置处理。
- 隐藏其他窗口使用透明度，保留附着的 TextureView，避免 `GONE/INVISIBLE` 销毁 SurfaceTexture；屏蔽隐藏窗口输入和无障碍访问，当前成员层级高于其他成员。系统返回仍走带结果的 `backLiveWindow`，弹窗优先关闭。
- `StageActivity` 只编排 View 与生命周期；不改 model/data、Binder 接口或 shell 权限。单应用全屏状态不持久化，新增偏好 `live_app_fullscreen` 仅为诊断。

## 自动验证

本轮命令：

```sh
python3 scripts/check-architecture.py
JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home \
ANDROID_HOME=/Users/paperfly/Library/Android/sdk \
./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug
```

- 架构检查通过；`StagePresentation` 加入纯 Java 边界检查。
- App **183 项测试，0 失败、0 错误**，包括 6 项新增测试：两种原模式的反复进入/退出、目标失效与迟到选择、分屏焦点/稳定 ID/顺序/保存快照隔离、临时状态不跨会话、不同方向/侧栏/比例下的单应用区域、原画布在普通/工作台全屏/单应用全屏的等比例适配与恢复。
- Debug APK 构建通过。
- Lint **0 错误、37 警告**；按检查 ID、消息及文件与本轮修改前报告比较，无新增或消失的警告。
- 无线协议未修改，本轮未重跑其独立测试。没有将单元测试当作真实应用持续播放、Surface 存活或厂商焦点的验证证据。

构建日志与修改前源码快照：`.runtime/app-fullscreen-20261113/`。可分享摘要与日志：`dist/app-fullscreen-validation/`。

## 实机状态与待验证项

本轮两次执行 `adb -s AKDPBB6A08000069 get-state` 均返回 `device not found`。荣耀不在线，未能读取本轮设备型号、系统、安装版本或用户现场；没有安装、启动、操作或恢复设备，没有使用历史快照。vivo 未访问、安装或修改。未发布到 NAS。

设备重新在线后先保存本轮新的配置、主窗/顺序/分屏、模式、旋转及任务快照，再安装并优先通过 App 内无线激活。以下均为**待实机验证**：

1. 用户已有播放/浏览页面在普通 ↔ 工作台全屏、普通 ↔ 单应用全屏、工作台全屏 ↔ 单应用全屏中保持状态；反复切换比较 task/display/Activity、画布尺寸、帧数与 Surface 生命周期，排除“ID 没变但页面重建”的假阳性。
2. 分屏卡片选择非第一个成员全屏；触控和系统返回作用于该成员，退出后组内布局、顺序、焦点及其他成员持续画面保留。
3. 横竖屏 × 普通/工作台全屏/单应用全屏的布局与返回按钮；旋转后恢复 Surface。旋转导致的应用自身重建单独记录，不能与仅全屏切换混为一谈。
4. 全屏工作台菜单打开/关闭/返回，侧栏末尾加号、滚动与主次交换；退出单应用后点击、滚动和切换正常，无隐藏窗口抢输入。
5. 临时测试应用移除、退出重入；逐字段比较原用户配置与保存组合，恢复本轮旋转和主窗，清理本轮临时文件。

## 交付包

本地待实机验证 APK：`dist/OpenWorkbench-0.16.0-standalone-debug.apk`。

SHA-256：`70508df3b427905c3862d12f5c74bb003e7867987b371a6e353b9fa6e681d333`。

上述已实现内容不能表述为已在全部应用或设备上验证状态保持。应用自身刷新、方向处理、系统回收及受保护视频画面仍有各自限制；本轮未作兼容性扩展结论。
