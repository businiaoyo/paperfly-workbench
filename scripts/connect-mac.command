#!/bin/bash
# Double-click the copy in the NAS personal share to set up this Mac.
set -euo pipefail
umask 077
remote=workbench-sync@192.168.5.53:workbench.git
state="$HOME/Library/Application Support/OpenWorkbenchClient"
project="$HOME/Developer/工作台模式"
label=cn.paperfly.open-workbench.client
domain="gui/$(id -u)"
mkdir -p "$state" "$HOME/Developer" "$HOME/Library/LaunchAgents"
cd "$state"

if ! /usr/bin/git --version >/dev/null 2>&1; then
    echo '请先在终端运行 xcode-select --install，安装完成后重新打开本文件。'
    exit 1
fi

remote_name=origin
if [[ -e "$project" ]]; then
    if [[ ! -d "$project/.git" || "$(git -C "$project" remote get-url "$remote_name" 2>/dev/null)" != "$remote" ]]; then
        echo "已有其他目录占用 $project，请另行保存后再运行。"
        exit 1
    fi
    hook=$(git -C "$project" config --get core.hooksPath || true)
    if [[ -n "$hook" && "$hook" != "$state/hooks" ]] ||
       [[ -z "$hook" && -n "$(find "$project/.git/hooks" -type f ! -name '*.sample' -print 2>/dev/null)" ]]; then
        echo '此仓库已有 Git 钩子，请先确认接入方式。'
        exit 1
    fi
fi

cat > known_hosts <<'KEY'
[192.168.5.53]:10000 ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAIB9S7B48xvaF+9GQFg8TOIjBYJWTZFarSuJiJzGNemMB
KEY
key="$state/id_ed25519"
if [[ ! -e "$key" ]]; then
    /usr/bin/ssh-keygen -q -t ed25519 -N '' -C 'OpenWorkbench Mac' -f "$key"
fi
common=(-F /dev/null -p 10000 -o StrictHostKeyChecking=yes -o HostKeyAlgorithms=ssh-ed25519 -o "UserKnownHostsFile=\"$state/known_hosts\"" -o ConnectTimeout=8)
cat > git-ssh <<'SSH'
#!/bin/bash
state="$HOME/Library/Application Support/OpenWorkbenchClient"
exec /usr/bin/ssh -F /dev/null -p 10000 -i "$state/id_ed25519" -o IdentitiesOnly=yes -o BatchMode=yes -o ConnectTimeout=8 -o ServerAliveInterval=15 -o ServerAliveCountMax=2 -o StrictHostKeyChecking=yes -o HostKeyAlgorithms=ssh-ed25519 -o "UserKnownHostsFile=\"$state/known_hosts\"" "$@"
SSH
chmod 700 git-ssh
sshcmd='"$HOME/Library/Application Support/OpenWorkbenchClient/git-ssh"'
if ! GIT_SSH_COMMAND="$sshcmd" git ls-remote --exit-code "$remote" refs/heads/main >/dev/null 2>&1; then
    account=${OPEN_WORKBENCH_NAS_USER:-}
    if [[ -z "$account" ]]; then
        read -r -p '请输入极空间 SSH 账号名：' account
    fi
    [[ "$account" =~ ^[a-zA-Z0-9_][a-zA-Z0-9_.-]*$ ]] || { echo '账号名无效'; exit 1; }
    echo '正在向个人极空间登记这台 Mac 的项目公钥。如提示密码，请输入极空间 SSH 账号的密码。'
    /usr/bin/ssh "${common[@]}" -l "$account" 192.168.5.53 'sudo -n /data_s005/data/udata/real/docker/workbench-sync/authorize-mac' < "$key.pub"
    GIT_SSH_COMMAND="$sshcmd" git ls-remote --exit-code "$remote" refs/heads/main >/dev/null
fi

if [[ ! -e "$project" ]]; then
    git clone -c "core.sshCommand=$sshcmd" "$remote" "$project"
fi
git -C "$project" config core.sshCommand "$sshcmd"

mkdir -p hooks
cat > hooks/reference-transaction <<'HOOK'
#!/bin/sh
[ "$1" = committed ] || exit 0
while read -r old new ref; do
    [ "$ref" = refs/heads/main ] || continue
    /bin/launchctl kickstart "gui/$(id -u)/cn.paperfly.open-workbench.client" 2>/dev/null || :
done
HOOK
cat > push-main <<'PUSH'
#!/bin/sh
set -eu
cd "$1"
local_head=$(/usr/bin/git rev-parse --verify refs/heads/main)
remote_head=$(/usr/bin/git rev-parse --verify -q refs/remotes/origin/main) || remote_head=''
[ "$local_head" != "$remote_head" ] || exit 0
# A behind-only checkout has no outgoing changes.
if [ -n "$remote_head" ] && /usr/bin/git merge-base --is-ancestor "$local_head" "$remote_head"; then exit 0; fi
/usr/bin/git push origin refs/heads/main:refs/heads/main
PUSH
chmod 700 hooks/reference-transaction push-main
git -C "$project" config core.hooksPath "$state/hooks"

plist="$HOME/Library/LaunchAgents/$label.plist"
temp=$(mktemp "$state/agent.XXXXXX")
trap 'rm -f "$temp"' EXIT
plutil -create xml1 "$temp"
plutil -insert Label -string "$label" "$temp"
plutil -insert ProgramArguments -json '["/bin/sh"]' "$temp"
plutil -insert ProgramArguments.1 -string "$state/push-main" "$temp"
plutil -insert ProgramArguments.2 -string "$project" "$temp"
plutil -insert RunAtLoad -bool YES "$temp"
plutil -insert StartInterval -integer 60 "$temp"
plutil -insert StandardErrorPath -string "$state/sync.log" "$temp"
plutil -lint "$temp" >/dev/null
if ! cmp -s "$temp" "$plist"; then
    if launchctl print "$domain/$label" >/dev/null 2>&1; then launchctl bootout "$domain/$label"; fi
    install -m 600 "$temp" "$plist"
fi
if ! launchctl print "$domain/$label" >/dev/null 2>&1; then launchctl bootstrap "$domain" "$plist"; fi
echo "项目已就绪：$project"
echo '换电脑开始工作前，请先在项目里运行 git pull --rebase，再编辑并提交到 main。'
if [[ -z "${OPEN_WORKBENCH_NO_OPEN:-}" ]]; then open "$project"; fi
