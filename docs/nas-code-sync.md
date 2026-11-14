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

## 在另一台 Mac 上使用

1. 新 Mac 连入能访问极空间的局域网，在 Finder 按 ⌘K，连接 `smb://192.168.5.53/sata12-135XXXX7849`，使用极空间个人账号登录。
2. 打开「代码同步」，将「接入工作台.command」复制到 Mac 后双击运行。没有 Git 时先按终端提示安装 Apple 命令行工具，再运行一次。首次登记时输入有权限的极空间 SSH 账号及密码。脚本在新 Mac 本地生成项目专用密钥，登录信息由 SSH 处理。
3. 成功后 Finder 会打开本地 `~/Developer/工作台模式`，可用 Android Studio 或其他编辑器打开。远端为 `origin`。首次使用 Git 如提示缺少作者，可在项目目录设置自己的 `git config user.name` 和 `git config user.email`。
4. 换电脑前先提交，确认本地 `git rev-parse main` 与 `git ls-remote origin refs/heads/main` 的提交 ID 一致。换到另一台电脑，编辑前运行 `git pull --rebase`。原开发 Mac 使用远端名 `nas`。两台分别产生提交时先拉取、处理冲突再推送；服务只做快进推送，保留两边的提交。
5. 接入后的 Mac 每次提交到 `main` 都会唤醒同步服务；登录期间每分钟重试。查看日志可打开 `~/Library/Application Support/OpenWorkbenchClient/sync.log`。同一台 Mac 重复运行接入脚本会复用已有的项目密钥和克隆。
