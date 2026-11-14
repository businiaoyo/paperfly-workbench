# NAS 更新发布

更新中心沿用本地项目 `/Users/paperfly/Desktop/AI工具/包分发`，地址 `http://192.168.5.53:8787`，应用 ID `open-workbench`，渠道 `stable`。App 只调用公共检查与下载 API；管理令牌保存在开发机，不进源码、APK 或日志。平板需要能访问 NAS 所在网络。

## App 内更新

工作台左上角返回首页 → 帮助与设置 → 软件更新。页面检查新版本、展示说明，下载并验证后打开系统安装器。首次使用按提示允许开放工作台安装应用。安装完成重新打开 App，使用已配对的无线激活；组合和设置保留。未发现更高版本、NAS 不可达、安装取消和校验失败均有状态及重试入口。

## 发布步骤

1. 提升 `app/build.gradle` 的 versionCode 与稳定版 versionName，完成架构检查、单元测试、构建、Lint 及目标设备验证。保留与已安装版本相同的签名密钥；当前渠道使用既有 debug 签名，切换正式签名须另行迁移。
2. 将 APK 复制到 dist，单独保存 UTF-8 更新说明。
3. 在 JDK 17 / Node 22+ 环境运行：

```sh
node scripts/publish-update.mjs dist/OpenWorkbench-0.15.0-standalone-debug.apk dist/OpenWorkbench-0.15.0-notes.txt --draft-only
node scripts/publish-update.mjs dist/OpenWorkbench-0.15.0-standalone-debug.apk dist/OpenWorkbench-0.15.0-notes.txt
```

脚本默认以 Node 的 env 文件解析器读取开发机 `.env.deploy.local`；可用 `PAPERFLY_UPDATE_CONFIG` 指定本地配置路径，或在进程环境提供 `PUBLISH_TOKEN` 与 `UPDATE_CENTER_URL`。不把配置文件当 shell 执行，不输出令牌。SDK 默认为 `~/Library/Android/sdk`，build-tools 34.0.0，可通过 `ANDROID_HOME` / `ANDROID_BUILD_TOOLS` 指定。

脚本验证签名和 APK 包名，读取 APK 的实际版本号，拒绝预览版本进入 stable；上传后比较服务器与本地的大小、SHA-256，再发布。相同已发布版本与同一文件重复调用不重复发布；同版本不同内容拒绝覆盖。`--draft-only` 只准备可核对的草稿，公共检查接口看不到草稿。

首次接入在更新中心存储初始化中幂等注册应用，保留其他应用及发布记录。部署和元数据备份位置见验证文档；后续 APK 更新不需要重新部署服务。发布新版本前后核对其他应用记录未被修改。

## 客户端边界

`SoftwareUpdateActivity` 独立于工作台 UI 和 shell 服务，检查、下载、校验均在工作线程进行；页面销毁会取消网络。`update/UpdateRelease` 与 `UpdateIntegrity` 是可测试的清单与完整性规则，`UpdateClient` 负责 App UID 下的网络和 Android 包身份检查。

仅接受递增版本、Android universal APK、同源 artifact 地址；禁止重定向，限制超时和文件大小。下载到私有 cache，核对大小、SHA-256、包名、实际版本、最低系统及当前签名（含签名轮换历史），安装前再次核对。非导出的 `UpdateApkProvider` 只共享一个已验证文件，使用系统安装器的只读 URI 授权。

内网 HTTP 例外仅允许固定主机 `192.168.5.53`，其他明文流量默认拒绝。传输哈希检测完整性，APK 签名和系统安装器负责安装身份验证。没有远程命令、任意下载地址、后台静默安装或 shell 安装接口。更新不增加工作台持久字段，配置 schema 仍为 4。
