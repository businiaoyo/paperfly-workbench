# 关闭补位的稳定画布与帧同步复验

2026-11-13，0.17.0-test.9 / versionCode 47 / schema 8。当前测试设备为联想 Y700 TB323FU `HA2HEFTM`、Android 16。已安装测试 APK 并通过 App 内“已配对，直接激活”启动，未发布 NAS。

## 本轮发现

用户要求再次测试关闭时的闪烁。原 test.8 的横竖屏关闭、分屏和 Dock 共 426 项逻辑断言通过，但实际录屏仍有叠影、短暂露出背景和内容清空。因此历史记录把这些现象仅归因于第三方内容重排不充分。

1. VirtualDisplay 改尺寸触发系统显示器 CHANGE 过渡，WindowManagerShell 日志包含 Display transition snapshot；系统旧截图与工作台补位动画叠加。
2. 先改宿主裁剪和比例、后收到应用新帧，会在新旧像素和几何错配的两帧内露出背景。
3. 每个成员分别归一化像素和密度，删除邻窗会抬高剩余成员密度。番茄小说主 Activity 处理尺寸变化但不处理 density，因而重新创建并清空内容。

## 实现与边界

`LiveCanvasArea` 在每个自有虚拟显示器内创建根级 TaskDisplayArea；VirtualDisplay 与 TextureView 缓冲区固定为 1920 × 1920，实际应用区域独立改变宽高并裁剪。不再用显示器 resize 驱动普通成员补位。创建区域前先由 WindowManager 建立 DisplayContent，避免刚创建的显示器尚未注册而误入兼容回退。区域是 DefaultTaskDisplayArea 的同级，不能改为嵌套空区域；本机厂商在嵌套区域最后任务交还时存在异常路径。

`LiveCanvasSync` 在服务操作队列提交 BLAST 同步事务，等应用完成新尺寸绘制；`CanvasCommitQueue` 按实际 SurfaceTexture 时间戳选择已到达的提交。Activity 取得对应帧时同步更新父容器裁剪、布局和变换，持续复用 Surface/TextureView、显示器和真实画面。快速连续关闭允许队列跳过已过时的中间提交。动画与输入线程不等待系统事务。

`CanvasSize.paneWithin` 让同卡片各成员共用像素比例，扩展空间时保留旧密度，避免无谓 Activity 重建。缩小或旋转仍按实际可用空间调整；没有改变配置格式和保存组合。

输入检查还发现固定方形缓冲区会使本地 IME 部分按键落到裁剪范围外。稳定画布对其自有显示器设置 IME fallback 策略，由物理屏幕完整显示键盘，输入仍交给焦点应用；原显示器路径保留本地 IME。没有修改全局输入法、全局动画、第三方设置或 shell 权限。此路径依据 Android 的多显示器输入法机制，见 [AOSP IME 支持](https://source.android.com/docs/core/display/multi_display/ime-support)。

隐藏 API 均留在服务适配层。区域创建不注册全局 organizer；任务接续仍校验组件、用户、来源和显示器归属。关闭按原顺序交还任务到后台、释放显示器，再清理所属区域。API 不支持或厂商兼容画布超过固定边长时保留原显示器调整路径；其平滑性未在其他设备验证。

输入回归还发现，迁回物理屏的浏览器可能恢复原输入目标，导致窗口已经关闭但 fallback 键盘仍在。`LiveWindowEngine` 在应用获得输入时记录已确认的任务 token，避免打开菜单后只看关闭瞬间的焦点而遗漏；这些 token 不持久化。`LiveTaskHandoff` 现在在隐藏任务的事务中，暂时禁用已确认取得焦点的任务输入；在迁回后台的恢复事务中同时恢复可聚焦状态。原有失败清理同样恢复该状态。不改未知任务的焦点属性，不改宿主标志，也不长期禁用返回给用户的任务。只对所属区域取消焦点不能解决此问题，因为任务迁出时会失去该限制。

还复现了 fallback 键盘仍依附隐藏的管理首页，系统状态为已显示但画面不可见。新增独立 `HostImeLayer`：一个不接收触摸和输入焦点的透明 1 px 子窗口，作为键盘层级目标，随 Activity 销毁。主承载窗口继续可聚焦并保留 `FLAG_ALT_FOCUSABLE_IM`；没有为显示键盘改变应用画布或替换 Surface。

## 验证

架构检查、227 项单元测试、Debug/Release 构建和 Lint 通过（0 错误、47 警告）。新增测试覆盖过期帧、连续提交、跳帧、迟到回调，以及共享像素比例、扩展保留密度和缩小可用空间。

| 实机回归 | 横屏 | 竖屏 |
| --- | ---: | ---: |
| 单窗口关闭及快速连续关闭 | 125 | 125 |
| 相邻分隔条、实时跟随和取消恢复 | 46 | 46 |
| Dock 样式、切换、末尾加号和总览移除 | 54 | 54 |
| 合计 | 225 | 225 |

覆盖普通模式、整个工作台全屏、当前应用全屏、主次交换、侧栏加号、三成员中间/焦点/最后成员关闭，以及间隔 80 ms 连续关闭。关闭断言新增固定显示器与消费缓冲区、实际新尺寸帧提交检查；同时逐帧查看录屏，不能只用通过数证明画面流畅。

本轮横竖屏录屏中，浏览器关闭后，番茄小说保持原列表扩展；LocalSend 连续补位；最后成员关闭后侧卡片连续接续。未再观察到原先的显示器截图叠影、窗口内部背景缺口或密度导致的番茄小说清空重载。

以上 450 项均以最终安装包重新执行。最终包还完成输入复验：横竖屏键盘完整可见；通过屏幕键盘向浏览器输入 `0` 后删除，确认输入交给当前应用；键盘可见时关闭浏览器，键盘随之消失；竖屏键盘可见时退出整个工作台，管理首页无键盘残留，且只剩物理显示器；重新进入原组合后再次点输入框，键盘正常显示。结束时关闭输入弹窗并恢复普通工作台。

最终安装后的日志共核对 7255 行，未发现工作台崩溃、ANR、任务交还失败、稳定画布回退、释放失败或 resize 失败。安装包与 `dist` 文件的 SHA-256 一致；现场无待执行验证或临时恢复文件。

## 现场和证据

材料位于 `.runtime/retest-20261113/`。`remaining-close-artifact.mp4` 是旧版本剩余缺陷；`final-window-close-landscape.mp4`、`final-window-close-portrait.mp4` 和对应 JSON 保存关闭过程，`final-split-*`、`final-dock-*` 保存其他回归。开发中的 `test9-*`、输入法策略探针和旧轮次录像不能替代最终安装验证。

输入证据包括 `accepted-input-zero.png`、`accepted-visible-close-after.png`、`accepted-portrait-keyboard.png`、`accepted-exit-home.png`、`accepted-reentry-keyboard.png` 及对应 IME 状态。`accepted-final-state.png`、`accepted-final-workspaces.json` 保存最终现场；`accepted-runtime-log.txt` 仅包含最终安装后的日志。浏览器调试时误触产生的一条临时搜索记录，已通过浏览器逐条删除入口移除，保留原有记录，见 `history-test-entry-removed.png`。

本轮现场为 LocalSend 与浏览器同一主卡片、番茄小说与哔哩哔哩两个侧卡片。每轮临时组合都通过 App 草稿恢复；不直接覆盖配置文件。用户明确比例和焦点无所谓，保留测试后的当前比例，不恢复早期历史比例。最终配置逐项核对，应用名单、卡片稳定 ID、顺序、分屏结构、样式和保存组合均保留。自动旋转与 user_rotation 原值均为 1，验证结束恢复原值。没有操作第三方账号、接受首次协议或清空应用数据，也没有操作 vivo；荣耀未接入。

仅验证本台 Y700 和上述应用。真实旋转、缩小及应用自身不支持的配置变化仍可能触发内部重排或重建；不声称所有 Android 版本、设备和应用均无过渡。

测试包：`dist/OpenWorkbench-0.17.0-test.9-standalone-debug.apk`。

SHA-256：`5a1e5533b9c6e52fcb565fafc31e106e490064aff5c7b1d5959623c92bbb1a9f`。
