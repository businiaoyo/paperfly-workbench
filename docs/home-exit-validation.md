# 0.14.7 上滑退出直接回桌面

2026-11-11。在联想 TB323FU（`HA2HEFTM`，Android 16）复现、修复并安装验证。iQOO 仅为原生参考，本轮没有操作；荣耀未连接。证据在 `.runtime/home-exit-20261111/`，录屏、逐帧图和验证摘要副本在 `dist/home-exit-validation/`。

## 复现和实现

0.14.6 的 `before.mp4` 显示，上滑后工作台缩回桌面，桌面已出现，随后黑屏并跳到超级文件且停留。它不只影响主应用：释放仍有任务的虚拟显示器，会把这些任务迁到物理显示器前面。

整个会话的关闭顺序现为：

1. 在服务队列及原会话所有权保护下，读取当前根任务，仅选择本会话仍拥有的显示器和创建时用户。
2. 通过任务令牌批量临时隐藏这些任务；每个任务在迁回物理显示器前重新确认令牌、显示器及用户。
3. 同一窗口事务内将任务放到底部并取消隐藏，再释放空显示器。

`TaskHandoff` 负责纯 Java 的选择、顺序与异常清理，`LiveTaskHandoff` 封装 shell 侧系统接口。部分隐藏或迁移失败仍执行恢复；批量恢复失败会逐项重试，必要时单独取消隐藏。平台不支持或拒绝时记录异常并保留原显示器释放路径，不以一次联想验证承诺其他系统均可消闪。没有强制启动桌面或拉回工作台，因此菜单退出仍能返回启动页。

任务转移复用原 task，不清理第三方数据、不结束任务、不重新启动应用来掩盖闪现；退出时任务切换回物理屏仍可能收到系统配置变化。没有新增权限或接受外部任务 ID 的 Binder 接口，没有改配置 schema、Surface 恢复、输入窗口标志或主次交换路径。单个应用“移出”仍是原有独立路径，本轮未宣称修复它的历史问题。

系统行为参考 AOSP Android 16 的 [窗口事务实现](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android16-release/services/core/java/com/android/server/wm/WindowOrganizerController.java)与 [任务迁移实现](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android16-release/services/core/java/com/android/server/wm/RootWindowContainer.java)。具体联想行为以上述实测为准。

## 验证

- `python3 scripts/check-architecture.py`、`:app:testDebugUnitTest`（136 项）、`:app:assembleDebug`、`:app:lintDebug` 均通过。Lint 0 错误、37 项既有警告。新增 6 项测试覆盖显示器/用户隔离、无效令牌、会话结束、部分隐藏/迁移失败及清理异常报告。
- 横屏普通模式、哔哩哔哩主窗：`after.mp4` 中桌面出现后持续保持，无应用闪现。服务记录三任务的 `hide` / `behind-and-show`，本次间隔约 51 ms。
- 竖屏普通模式，先将爱奇艺切为主窗：`portrait.mp4` 同样直接回桌面。重新进入后交换回哔哩哔哩，再恢复横屏。
- 横屏整个工作台全屏：系统先用一次上滑唤出临时导航栏（`fullscreen.mp4`），随后测试连续两次上滑完成系统 Home 手势（`fullscreen-home.mp4`）。桌面出现后没有成员应用覆盖；保留系统原有全屏手势规则。
- 每次成功退出后的系统转储均确认：唯一 resumed Activity 为桌面，物理窗口焦点为桌面，活动 OpenWorkbench 显示器数量为 0。三应用原 task ID 始终为哔哩哔哩 41、爱奇艺 46、超级文件 47，返回主显示器后台；多次重入均能显示原任务，未残留不可见任务。见 `verification.json` 和各次 `*-activities.txt` / `*-window.txt` / `*-display.txt`。
- 主次交换及旋转后真实画面恢复；侧栏末尾加号打开应用抽屉，完成关闭后仍为原三个应用。菜单“退出工作台”返回 `MainActivity`，没有被成员应用盖住。UIAutomator 有一次因动态内容未能取得 idle，未执行退出；重新读取画面并重试后完成，不将该失败当作通过。

## 交付状态

联想已安装 **0.14.7 / versionCode 31**，通过 App 内“已配对，直接激活”重新连接。最后停留桌面，应用在后台；当前草稿、卡片 ID、主窗、应用顺序、样式与保存组合文件和本轮首次快照逐字节一致。哔哩哔哩仍为主窗，爱奇艺、超级文件按原顺序位于侧栏；自动旋转和 user_rotation 均恢复为 1。没有添加测试应用，设备上的本轮临时录屏已删除。

APK：`dist/OpenWorkbench-0.14.7-standalone-debug.apk`。已比对设备安装文件与交付文件，SHA-256 均为：

```text
6a20d172f0042b0c98134354495cf742f360168161b835c82733e4bd9fcd9d01
```

本轮未验证其他厂商、多个 Android 用户或服务在迁移途中被强杀；未做长期功耗测试。
