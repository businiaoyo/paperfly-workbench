# 本机窗口验证应用

`:probe` 是可删除的调试夹具，不被 `:app` 依赖或打包。只包含计数、输入、心跳和 Android 原生拖放源/目标，无网络、存储、账号或后台权限。

构建：`./gradlew :probe:assembleDebug`。安装、操作与卸载都要显式指定经用户授权的设备序列号。使用后从工作台移除并卸载 `cn.paperfly.workbench.probe`。

主应用 Debug 的 `ParityValidation` 只有私有文件触发，没有导出测试入口；Release 为空实现。默认有限步骤结束后还原测试前草稿；`hold` 仅用于最多五分钟的人工触控验证。详情与本轮证据见 `docs/stage-manager-test-development.md`。
