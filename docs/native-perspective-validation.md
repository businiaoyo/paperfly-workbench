# 0.14.4 原生透视对照与修正

2026-11-11。用户指出 0.14.3 的倾斜与 iQOO 不同。本轮直接读取已连接 iQOO Pad Pro 的当前原生工作台截图和系统四角坐标，再修改和验证联想 TB323FU 上的开放工作台。证据目录 `.runtime/native-perspective-20261111/`，可查看图片副本 `dist/native-perspective-validation/`。

## 原生观察与偏差

iQOO `403GAA02RD63200` 当前为 2304 × 1440 横屏，哔哩哔哩主窗，侧栏依次为 LocalSend、相册、计算器和加号。本轮只执行截图、dumpsys 和读取旋转设置，没有安装、点击或修改参考设备。前后侧位、四角和旋转读数一致。

`iqoo-before-activities.txt` 的 `mPointInfoInVivoMultiTask` 给出以下实际四角（TL、TR、BR、BL）：

| 侧位 | 左上 | 右上 | 右下 | 左下 |
| --- | --- | --- | --- | --- |
| LocalSend | 145, 89 | 465, 138 | 465, 363 | 145, 335 |
| 相册 | 145, 373 | 465, 399 | 465, 624 | 145, 619 |
| 计算器 | 145, 657 | 465, 659 | 465, 884 | 145, 903 |

左右边均竖直。六对纵坐标拟合得到 `farY ≈ 0.91668 × nearY + 56.494`，共同透视中心约为 Y = 678。上方卡片两条横边向下倾斜，经过透视中心后转为向上倾斜；加号同样呈透视。0.14.3 给每张卡片附加最多 5° 平面旋转、使用各自中心轴心且加号平放，与实测不符。

## 修正

- `StageLayout.CardPose` 定义纯 Java 几何：远近边高度比 11/12，保持 28° Y 轴旋转产生的水平压缩，取消平面转角和额外 6% 整体收缩。横向压缩后仍居中放进原槽位。
- 所有侧卡片共享侧栏可用范围的垂直中点，轴心换算到各自画布；相机距离由实际渲染宽度推导，保持不同画布、密度和加号尺寸下的投影一致。右侧栏严格镜像。
- `StageCardPose` 只适配公开的 [View 变换属性](https://developer.android.com/reference/android/view/View)。相机单位换算依据 [Android View 的实现](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android14-release/core/java/android/view/View.java)，实机 `getMatrix()` 四角再次验证转换结果。
- 位置、宽度、倾斜继续走原 `StageMotion`，可中途转向，回到主位时恢复平正。图标从实际 View 矩阵取得左下角，图标自身保持正立；加号使用相同透视与平台触控映射。
- 不改变逻辑画布、任务调度或 Surface 生命周期，不增加离屏位图、显式硬件层或静态时帧循环。没有修改配置 schema、背景及系统栏处理、shell 权限或服务释放逻辑。

这是参考实测比例的设备自适应方案，不是复制 iQOO 的全屏布局坐标；联想主窗仍保留现有空间利用率。多应用分屏卡片继续保持平面，尚未实现统一组容器的透视。原生参考仅采集横屏，右侧栏镜像为模型验证，不能表述为已逐项实测所有布局和厂商。

## 验证

`python3 scripts/check-architecture.py`、`:app:testDebugUnitTest`（130 项，全部通过）、`:app:assembleDebug`、`:app:lintDebug` 通过。Lint 为 0 错误、36 警告。新增 5 项几何测试覆盖原生三组四角（误差 ≤ 2 px）、主窗不变、过渡中左右镜像、加号与不同尺寸画布投影一致，以及横竖屏/普通/全屏的四预览及末尾加号边界。

联想 `HA2HEFTM` / Android 16 实机：

- 普通横屏、普通竖屏、整个工作台全屏横竖屏均检查四张侧预览，没有互相覆盖。见 `four-side-landscape.png`、`normal-portrait.png`、`fullscreen-landscape.png`、`fullscreen-portrait.png`。
- 普通横屏点击倾斜加号打开抽屉；竖屏滚至末尾后，通过投影后的中心点击加号也成功。关闭后实时画面恢复。见 `scrolled-end-prefs.xml`。
- 直接检查实际 View 矩阵：上述四种布局和最终三应用状态均保持左右边竖直，远近边比约 0.916667，各卡片及加号推导出的共同透视中心误差小于 0.005 px。见 `projection-verification.json`。这是矩阵内部一致性验收，不代表画面与 iQOO 的逐像素误差。
- 安装后八次主次交换保持原 displayId、画布尺寸及最终应用顺序；清理恢复后另做八次，每次确认主窗确实改变才继续。后一组显示器 ID 在该组测试内保持稳定，配置与升级前 JSON 相同。见 `before-swaps-prefs.xml`、`after-swaps-prefs.xml`、`final-before-swaps-prefs.xml`、`final-after-swaps-prefs.xml`。

最后一组在交换前清空 gfxinfo，得到 961 帧、Janky frames 1（0.10%），legacy 计数 22（2.29%），CPU 帧耗时 P50/P95/P99 为 7/9/12 ms，GPU 为 6/7/8 ms。见 `eight-swaps-gfx.txt`。测试使用实时第三方页面，不是固定负载跑分，不能据此宣称零开销或比上一版性能更好。

## 清理期间的既有问题

为验证四侧窗，临时加入计算器和设置；随后通过主窗菜单移出两者。配置正确移除，但之后工作台不再位于物理屏幕前台，截图显示超级文件全屏。这与 0.14.3 清理时记录的前台跳出症状相似；现有日志未捕获可定因的崩溃栈，根因尚未确认，本版未修复。见 `cleanup-log.txt`、`cleanup-activities.txt`、`restore-check.png`。

清理后第一次自动恢复/采样没有先确认主窗变化，读到了旧诊断值，配置相等断言失败；相关数据已标为 `invalid-after-cleanup-*`，不作为有效切换或性能结果。恢复文件管理器原入口、重新进入工作台后，采用逐次确认实际主窗改变的方式完成上述最终八次交换。没有操作账号、首次使用协议或第三方数据。

## 交付状态

联想已安装 0.14.4 / versionCode 28，通过 App 内「已配对，直接激活」重新激活。最终恢复普通横屏，自动旋转 1、user_rotation 1；爱奇艺主窗、超级文件第一侧窗、哔哩哔哩第二侧窗。临时计算器和设置均移出，稳定 ID、卡片顺序、样式及已保存组合与本轮升级前快照一致。见 `delivered-workspaces.json`、`delivered-prefs.xml`、`delivered.png`。

调试签名 APK：`dist/OpenWorkbench-0.14.4-standalone-debug.apk`。

SHA-256：`c893a8d1bcff78fe30e68208ec48e19633bc9db9147a9ea344a76c7ad43777de`。
