# 极空间源码同步

本项目的 Git 远端名为 `nas`。在极空间「个人空间」的 sata12 打开 `代码同步/工作台模式`，可以直接浏览当前源码。Git 仓库保留版本历史。APK 发布入口见 [软件更新发布](software-update-publishing.md)。

## 这台 Mac 上的日常使用

正常在本地编辑并提交到 `main`。本机的 `.git/hooks/reference-transaction` 在分支更新后将提交写入用户目录下的待传仓库，并唤醒登录时加载的服务 `cn.paperfly.open-workbench.sync`。Mac 离线时可以继续提交，服务每分钟检查待传版本，恢复局域网连接后补传。仅保存在编辑器或工作目录中的改动须先提交。

```sh
git status
git rev-parse main
git ls-remote nas refs/heads/main
```

最后两条命令的提交 ID 相同，表示极空间已收到本地版本。也可以直接运行 `git push nas main`。极空间已有分歧提交或有人直接改动 NAS 工作目录时，先核对并保留两边改动，再继续同步。同步日志位于 `~/Library/Application Support/OpenWorkbenchSync/sync.log`。

## 取回或迁移

在这台 Mac 的其他目录运行 `git clone open-workbench-nas:workbench.git`，可取回源码和提交历史。SSH 别名及项目专用密钥保存在本机 `~/.ssh/`。该密钥在 NAS 上只允许对此项目执行 Git 读写。新开发机须单独配置 SSH 身份，自动提交传输钩子和登录服务也属于开发机配置。
