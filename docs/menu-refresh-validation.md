# 0.14.5 图标与工作台菜单

2026-11-11。针对左上角排列、图标不齐，以及工作台弹出菜单陈旧的问题。本轮在联想 TB323FU（`HA2HEFTM`，Android 16）安装和验证，iQOO 原生参考设备未操作。原始记录位于 `.runtime/menu-refresh-20261111/`，可查看图片副本 `dist/menu-refresh-validation/`。

## 实现范围

- 侧栏顶部返回与更多入口合成半透明胶囊，统一 24 单位矢量图标、线宽和中心，去掉文字字符充当图标造成的基线差异。外观高度 40 dp，触控高度 48 dp；窄侧栏隐藏返回按钮，返回操作仍在菜单内。
- 主菜单贴近胶囊展开；全屏、添加应用、外观为三项快捷入口，其余功能为紧凑图标列表，退出与移出位于底部。右侧栏时菜单限制在右侧安全边界内。
- 外观、应用组合管理、组合命名/确认、分屏卡片与内存面板复用统一深色圆角面板、薄边框、文字层级和薄荷色主操作。外观选项改成开关、分段按钮和背景色块；长内容可滚动，取消与应用常驻底部。
- `WorkbenchDialog`、`WorkbenchUi` 和 `WorkbenchIcon` 维护公共外观；`StageMenu`、`SplitCardPanel` 承担具体呈现。原有配置、卡片操作及焦点恢复路径保留；未修改 schema、服务权限、应用画布规则及 TextureView/Surface 生命周期。

## 自动检查

以下全部通过，日志见 `build.txt`：

```text
python3 scripts/check-architecture.py
:app:testDebugUnitTest — 130 tests, 0 failures, 0 errors
:app:assembleDebug
:app:lintDebug — 0 errors, 37 warnings
```

Lint 仍有文本资源、API、无障碍等告警；本轮新增的程序化分段控件有 ViewConstructor 告警。没有为单纯配色和图标增加镜像实现的单元测试。无线客户端代码未变，本轮未重复运行其独立测试。

## 实机验证

| 检查 | 结果与证据 |
| --- | --- |
| 普通横竖屏 | 顶部图标垂直居中，主菜单贴近入口，内容和关闭/底部按钮在屏幕内。`menu-landscape.png`、`menu-portrait.png` |
| 左右侧栏 | 临时应用右侧栏，确认菜单靠右展开且不越界，再恢复左侧。`right-rail.png`、`menu-right.png` |
| 整个工作台全屏 | 横竖屏均隐藏顶部胶囊和系统栏，保留侧卡片、末尾加号及左下角菜单；菜单按钮可回到普通模式。`fullscreen-landscape.png`、`fullscreen-portrait.png`、`normal-after-fullscreen.png` |
| 面板与长内容 | 查看组合管理、分屏及内存面板；外观面板能滚动到背景色块，文字/色块居中、底部按钮仍可用。`combinations.png`、`split.png`、`memory.png`、`style-backgrounds.png` |
| 弹窗中旋转 | 在外观面板勾选右侧栏但不应用，旋转至竖屏后面板重新适配且选项保留；取消后配置 JSON 与升级前完全一致。`rotation-check.png`、`after-style-cancel-workspaces.json` |
| 命名与取消 | 命名框可获取焦点并显示用户当前浮动输入法；返回关闭后配置未改变。`name-keyboard.png`、`after-name-workspaces.json` |
| 侧栏末尾加号 | 按实际投影四角的中心点击，可打开应用抽屉；点完成后主窗和两张侧窗恢复显示。`before-add-prefs.xml`、`add-apps.png` |
| 主次交换 | 最终安装包上依次切换至爱奇艺、哔哩哔哩、超级文件、哔哩哔哩；每一步确认实际主位改变，三个显示器 ID 79/80/81 及各自 1920×1342 画布保持不变。`swaps.json`、`before-swaps-prefs.xml`、`swap-1-prefs.xml` 至 `swap-4-prefs.xml` |
| 配置保留 | 右侧栏测试恢复、四次交换后，稳定 ID、应用顺序、样式和 saved 数组与本轮新快照一致。`preinstall-workspaces.json`、`delivered-workspaces.json` |

旋转期间默认 UIAutomator 曾返回虚拟显示器内的应用节点；物理屏幕截图和 Activity 状态确认工作台弹窗仍在，并未据此判为弹窗丢失。超级文件的诊断帧计数也曾为 0，但物理截图中已有有效画面；验收没有仅凭该计数推断黑屏。

## 边界与交付

本轮没有实际新建/删除保存组合、合并/拆分卡片，避免更改用户配置；这些动作的既有模型测试通过，不代表本轮已逐一重走所有实机分支。只查看了当前浮动输入法，未覆盖所有停靠键盘和字体缩放。启动页激活与诊断弹窗不属于本次视觉改版。未做功耗、GPU 性能或其他厂商回归；上一版记录的单个应用移出后前台跳出问题不在本轮修复范围。

联想已安装 **0.14.5 / versionCode 29**，通过 App 内“已配对，直接激活”重新激活。最终恢复普通横屏、自动旋转 1 / user_rotation 1，哔哩哔哩主窗、爱奇艺第一侧窗、超级文件第二侧窗。未添加临时应用，已保存组合保持空数组。以本轮新快照为准，没有覆盖用户相对于上轮做的主窗/顺序改动。

APK：`dist/OpenWorkbench-0.14.5-standalone-debug.apk`。已核对实机安装的 APK 与交付文件 SHA-256 相同：

```text
8395ce6a100dcf2e69752f331bc0938471bd71298977bdcb699e92fa609105b5
```
