# 0.13.1 荣耀无线激活端口更新

后续：0.15.1 又发现并修复“其他设备服务被解析为本机地址”的独立路径；本页 0.13.1 的验证不能覆盖该情况，见 [2026-11-13 配对身份验证](honor-pairing-identity-validation.md)。

2026-11-11。目标设备 AKDPBB6A08000069，荣耀 WIN 平板 HES-W00，Android 17 / MagicOS 11。

## 失败现场与对照

0.13.0 在无线调试已开启、有 OpenWorkbench 配对记录且 App 私有身份未改变时，自动激活返回 AdbPairingRequiredException / SSLV3_ALERT_CERTIFICATE_UNKNOWN。自动发现页面显示连接端口 34201，而系统无线调试页及 `dumpsys adb` 显示 33551。同名 `_adb-tls-connect` 服务的 NSD 记录交替出现这两个端口。

旧实现仅在发现服务时调用一次 resolveService；后续端口更新未进入 App。配对服务消失时，发现回调的 `_tcp.` 与解析回调的 `_tcp.local.` 还会生成不同缓存键，导致已关闭的配对端口残留。

在原版 App 内手动填写系统当前端口 33551 后，同一私有身份立即无线激活成功，并自动返回首页。因此本次错误不能据异常类型直接判断成配对授权丢失。没有重新配对、删除密钥、清除应用数据或运行 USB 激活命令。

## 修复

- Android 14 及以上持续订阅 ServiceInfoCallback，跟踪端口和可用性；旧系统或订阅失败时串行刷新解析。停止发现后释放监听器。
- `AdbEndpointCatalog` 为纯 Java 的会话内发现结果，统一服务类型后缀与 DNS 大小写；丢失/重启后的旧回调不能重新发布端口。地址重新解析为非本机时清除结果。
- 同名本机服务冲突时，最多保留三个已公告连接端口，最新在前。自动激活在 20 秒选择窗口内最多尝试四个不同候选，始终连接 127.0.0.1；不扫描端口、不连接其他设备、不重复执行已开始的启动脚本。手填端口保持精确连接。
- 连接未接受证书时提示先核对系统端口，再判断是否需要重新配对；激活执行期间禁用重复点击自动激活。

API 依据：[Android NsdManager 文档](https://developer.android.com/reference/android/net/nsd/NsdManager#registerServiceInfoCallback(android.net.nsd.NsdServiceInfo,%20java.util.concurrent.Executor,%20android.net.nsd.NsdManager.ServiceInfoCallback))。一次解析结果可能立即过时，持续回调用于服务变化。

## 验证

架构检查、Debug 构建、App 与协议测试、Lint 均通过：138 项测试，0 失败、0 错误；Lint 0 错误、37 警告。新增八项测试覆盖端口变化与冲突候选、服务类型规范化、配对/连接隔离、丢失与停止后的迟到回调、非本机地址变化及候选数量/输入范围。

修复版已安装，安装 APK 与交付文件 SHA-256 相同。结束旧辅助进程后，App 自动发现 33551 并激活成功；关闭无线调试后页面清空旧端口，再开启后自动发现新的 44573，第二次自动激活成功。两次均复用原配对身份、自动返回首页，记录 adb_connection_closed=true，辅助进程为 shell UID。没有执行 USB 激活命令；USB 用于安装和观察。

激活页面横竖屏检查通过；“继续上次工作台”正常恢复当前计算器 + 时钟分屏卡片。验证横竖屏、整个工作台全屏及侧栏加号，临时加入系统设置进行主次交换后移除。交换过程中显示器 ID 与应用画布尺寸不变。恢复普通模式、原旋转和调试开关，完整配置库与本轮初始快照相同，已保存组合及主焦点未改变。

首次流式安装时 ADB 连接中断，随后使用推送安装并通过系统安装确认完成，不计作 App 激活失败。端口开关测试期间 USB 发生短暂重连，连接恢复后继续同一测试，未重新配对。

Android 14 以下的定时解析回退和多候选认证失败后的实机重试仍未单独覆盖；候选管理有单元测试，本机两次自动激活均一次连接成功。本轮未重新验证物理拔线，也未把本次结果推广到其他厂商。

原始现场与验证记录保存在 `.runtime/honor-reactivation-20261111/`；交付证据在 `dist/honor-reactivation-validation/`。
