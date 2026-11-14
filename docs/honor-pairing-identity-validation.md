# 0.15.1 荣耀配对后自动激活：设备身份与端口匹配

后续：用户在物理拔线并切换无线调试后仍失败。0.15.2 补充厂商开关指引、NSD 自动重命名兼容和真实拔线验证，见 [最新记录](honor-wireless-unplugged-validation.md)。本页插线测试不能代表拔线场景已覆盖。

2026-11-13。设备 `AKDPBB6A08000069`，荣耀 WIN 平板 HES-W00，Android 17 / MagicOS 11。升级前 0.15.0 / 35，修复版 0.15.1 / 36，配置 schema 仍为 4。

## 这次失败与上次修复的区别

用户确认失败发生在“输入配对码后自动激活”。现场 `paired=true`，系统仍保留 OpenWorkbench 配对记录；错误阶段为 connect，App 尝试 `127.0.0.1:40123`，返回 ECONNREFUSED。系统无线调试页和 `dumpsys adb` 的实际连接端口为 `35863`。旧 `connected_port`、`connection_attempts` 是上次成功值，不能代表本次失败尝试。

NSD 原始记录中，联想服务 `adb-HA2HEFTM-qKwfb0` 的端口为 40123，TXT 为 `name=TB323FU, api=36`，但解析地址曾交替出现联想地址和荣耀本机地址。荣耀自己的 `adb-AKDPBB6A08000069-0XR5OU` 为 35863，TXT 为 `name=HES-W00, api=37.0`。旧实现只用地址判断是否属于本机，会接受被错误关联到荣耀地址的联想服务。实际套接字一直固定为回环地址，未连接或修改联想设备。

[0.13.1 的修复](honor-reactivation-validation.md) 处理持续更新、类型后缀和旧端口候选，未处理上述跨设备身份误判。此次在旧版本重新打开页面后，用原密钥直接激活成功，说明无需先删除身份或清空数据；这次恢复本身不作为修复有效的证据。

## 实现边界

- `AdbEndpointIdentity` 是纯 Java 过滤规则。仍要求本机地址，并拒绝 TXT 型号或 API 明显不匹配的服务；支持 Honor 的 `37.0` 表示。缺失 TXT 的旧公告保留地址校验，提示元数据不能代替认证。
- API 34+ 使用全部 `getHostAddresses()`，旧系统保留单地址兼容。NSD 服务被拒绝时清除其既有端口；有界私有诊断记录端口、型号/API、是否本机地址和是否接受，不含配对码或私钥。
- 连接候选保留服务名。协议库在认证解密之后校验 server peer-info 类型 1、8192 字节结构与有界 ASCII GUID；配对后自动激活只从与该 GUID 同名的 TLS-connect 服务中选择端口。其他设备即使地址或型号相同，也不能进入本次配对后的连接候选。
- GUID 作为本次调用参数传递，不持久化、不跨取消或下一次操作共享。已配对直接激活无需重新配对，继续使用原身份及本机发现过滤；所有连接仍为 127.0.0.1。候选数量、超时和固定启动脚本边界不变。
- 每次 connect 开始清理旧连接诊断，失败也记录本次尝试端口和次数；区分“配对未完成”和“配对成功但连接/启动未完成”。没有修改配置模型、窗口服务、shell 权限或签名。

依据：[Android 全部服务地址 API](https://developer.android.com/reference/android/net/nsd/NsdServiceInfo#getHostAddresses())、[AOSP NSD 型号/API 公告](https://android.googlesource.com/platform/packages/modules/adb/+/refs/heads/main/daemon/mdns.cpp)、[AOSP 用配对 GUID 匹配连接服务](https://android.googlesource.com/platform/packages/modules/adb/+/HEAD/client/adb_wifi.cpp)。协议库本地改动同时记录在 `third_party/libadb-android-bc/UPSTREAM.md`。

## 验证结果

架构检查、`:app:testDebugUnitTest`、`:wireless-adb:testDebugUnitTest`、`:app:assembleDebug`、`:app:lintDebug` 均通过。App 171 项、协议层 26 项，0 失败/错误；Lint 0 错误、37 警告，与上版数量相同。

新增 11 项测试覆盖本次错误公告回放、型号/API 校验、旧公告兼容、配对后等待正确 GUID、尝试去重、配对/连接服务隔离、拒绝后删除旧候选，以及 peer-info 的类型、长度、编码和终止符错误。测试使用实际连接选择方法，错误服务先到时不会选取其端口。

| 真机路径 | 结果 |
| --- | --- |
| 更新 APK → 已配对直接激活 | 无手填端口；连接 35863，一次成功；`paired_peer_filter=false`，`adb_connection_closed=true`，辅助进程 shell UID。 |
| 回到管理首页并结束空闲辅助进程 → 开始配对 → 系统弹窗 → 通知输入配对码 → 自动激活 | 无手填连接端口；配对端口 34109；GUID 过滤启用，连接 35863，一次成功；`paired_peer_filter=true`，`adb_connection_closed=true`，新辅助进程 shell UID。 |
| 返回 App | 自动进入原单应用工作台；没有操作第三方首次使用协议。 |
| 发现过滤 | 新版记录荣耀 accepted=true、联想 accepted=false。此次新版实机采样的联想地址为非本机；旧记录中“联想服务带本机地址”的情况通过回归测试回放覆盖。 |
| 现场保留 | 完整 workspaces.json 与本轮初始快照字节一致；当前番茄免费小说单卡片、主成员、稳定 ID、三份保存组合、样式不变；旋转、自动旋转和无线调试开关不变；配对身份文件哈希一致。 |

USB 仅用于读取、安装和 UI 操作，没有通过 USB 运行激活命令。配对码通过内存中的 `/dev/tty` UI 流读取、填入通知，未保存截图、XML 转储或输出到测试日志。验证未删除系统配对记录或私有身份，未清应用数据，未安装或操作 vivo/联想设备。

本次没有重新验证物理拔线、无线调试开关轮换、Android 14 以下解析回退或其他厂商的配对协议；不能据此声称所有设备和断连场景均已覆盖。长期 NSD 异常频率仍需实际使用观察。

## 安装与 NAS 发布

交付 `dist/OpenWorkbench-0.15.1-standalone-debug.apk`，11,046,065 字节；SHA-256 `8a8368cfe06b1b583c4cb9da0422839bc18a6320985dcd05d9fbb145539100b3`。荣耀已安装的 base.apk 与交付包哈希一致。

已发布现有 NAS 的 `open-workbench / stable`，release ID `b4f8e5be-5779-4202-99aa-43a849f08583`。旧版 code 35 公共检查返回 0.15.1 / 36，新版 code 36 返回 204；公共 APK 下载哈希与交付包一致。发布前原有 5 条版本记录均未改变。本轮实机通过 USB 更新 APK 后用 App 内无线激活，未重复执行 NAS 下载到系统安装器的整条实机升级流程，该路径上版记录见 [0.15.0 验证](direct-entry-update-validation.md)。

本轮私有现场在 `.runtime/honor-wireless-timeout-20261113/`，不随源码或 NAS 上传。开始下轮操作应重新快照，尤其不能恢复早期验证中四应用/计算器时钟的历史草稿覆盖用户目前的单应用草稿。
