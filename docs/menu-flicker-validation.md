# 0.14.6 菜单出现时的闪烁

2026-11-11。用户报告点击菜单时明显闪烁。本轮在联想 TB323FU（`HA2HEFTM`，Android 16）复现、修复并安装验证；没有操作 iQOO 参考设备。证据目录为 `.runtime/menu-flicker-20261111/`，主要录屏和图片副本在 `dist/menu-flicker-validation/`。

## 复现与定位

0.14.5 的 `before.mp4` 显示：新菜单出现的瞬间，整幅应用内容突然变糊；随后应用恢复清晰，原本模糊的壁纸反而变清晰。静态截图不能发现这个短暂跳变。

宿主原有 150 px 壁纸模糊，旧菜单另开一个带 50 px blur-behind 的 Dialog。系统窗口转储与逐帧对比表明，跳变与新增窗口有关。仅移除菜单 blur-behind（`after.mp4`），或改为附属 Dialog（`attached.mp4`），均仍有跳变，未作为交付方案。这里没有逆向确认联想系统内部具体的合成代码，结论限于这些对照实验。

[Android 窗口模糊说明](https://source.android.com/docs/core/display/window-blurs)区分窗口背景模糊和整个窗口后方的模糊。本次没有修改系统设置、读取壁纸文件或更改底层合成器，最终选择避免菜单新增系统窗口。

## 最终实现

`HostModalSurface` 把主菜单及不含文字输入的工作台面板放入现有 Activity 内容容器：保留原壁纸、模糊和实时应用层，面板与深色遮罩一起进行 140 ms 淡入。关闭只移除面板层，不重建应用 Surface、不创建应用截图。

`WorkbenchDialog` 继续向调用方提供显示、关闭和回调接口；Stage 选择同窗口承载，布局仍按安全区、锚点和可用高度计算。遮罩拦截底层触控，面板内部空白不会误触关闭；显示期间隐藏底层无障碍节点，关闭后恢复原状态。动态内容变化、横竖屏均重新计算面板位置和高度。原生 Dialog 路径也改为显示前完成尺寸计算。

返回复用 Activity 已有的系统返回和边缘手势；不会向未显示的 Dialog Window 注册另一套回调。宿主保持可聚焦与 `FLAG_ALT_FOCUSABLE_IM`。输入法、窗口服务、配置 schema 和卡片模型未改变。

含 EditText 的命名框继续使用原生 Dialog，以保留独立输入法窗口；应用抽屉也保留原承载方式。这两类入口未纳入本轮同窗口消闪范围，不能宣称所有弹窗转场都已解决。命名框打开和取消已检查，但没有重新评估所有输入法行为。

## 验证

- 架构边界检查、`:app:testDebugUnitTest`（130 项）、`:app:assembleDebug`、`:app:lintDebug` 全部通过；Lint 为 0 错误、37 警告，没有新增告警。
- `host.mp4` / `host-opening.png` 中菜单淡入没有复现整屏模糊跳变，壁纸保持模糊。`host-open-window.txt` 中 Stage 仍只有一个窗口，原 150 px blur-behind 与 `ALT_FOCUSABLE_IM` 保留。
- 固定取录屏中未被菜单覆盖的应用区域，按均值归一化相邻像素梯度。旧版开场最低值约为基线的 9%；修复后单次约为 97%，另连续三次开关最低为 95.6%。这只是辅助识别画面突然模糊的检查，视频有压缩和动态内容，不是画质或性能评分。见 `before-contrast.json`、`host-contrast.json`、`repeat-contrast.json`、`repeat.mp4`。
- 系统返回关闭主菜单，应用 `back_requests` 不增加；内存面板通过右缘手势关闭，诊断为 `DIALOG_BACK`，随后可以继续打开菜单。
- 外观面板勾选右侧栏但不应用，旋转到竖屏后选项保留且面板在安全区内；取消后配置与本轮原始快照一致。`style-portrait.png`、`menu-portrait.png`。
- 普通与整个工作台全屏的横竖屏均检查；全屏保留侧卡片、末尾加号和左下角恢复入口。`fullscreen-landscape.png`、`fullscreen-portrait.png`。
- 点击侧栏末尾加号打开抽屉并完成关闭，真实画面恢复；查看内存和应用组合，进入命名框后取消。`plus-apps.png`、`memory.png`、`name.png`。
- 最终安装包上四次主次交换逐次确认：爱奇艺、哔哩哔哩、超级文件、哔哩哔哩。交换期间显示器 88/89/90 及各自 1920×1342 画布保持不变。`swaps.json` 与 `swap-*-prefs.xml`。

## 交付

联想已安装 **0.14.6 / versionCode 30**，通过 App 内无线激活重新连接。普通横屏，哔哩哔哩主窗、爱奇艺第一侧窗、超级文件第二侧窗；自动旋转和 user_rotation 均恢复为 1。当前配置、稳定 ID、排序、样式和保存组合与本轮开始时完全一致。没有新增测试应用；设备上的本轮临时录屏已删除，本地证据保留。

APK：`dist/OpenWorkbench-0.14.6-standalone-debug.apk`。实机安装文件与交付 APK 的 SHA-256 一致：

```text
cfc7a2e39c41831a5e47f2fbbee713a46c1f30cd53f2227ff069f50b3a35d0b2
```

未在其他厂商设备上做本轮回归；没有进行长时间功耗或性能测试。旧版记录的单个应用移出后偶发前台跳出问题不在本轮修复范围。
