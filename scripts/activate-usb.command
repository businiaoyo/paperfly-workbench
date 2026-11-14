#!/bin/bash
set -euo pipefail

finish() {
  result=$?
  if [ "$result" -ne 0 ]; then echo '激活未完成。请检查 USB 调试授权，并查看上方错误。'; fi
  if [ -t 0 ]; then read -r -p '按回车关闭此窗口…' _reply || true; fi
}
trap finish EXIT

adb_bin="${ANDROID_HOME:-$HOME/Library/Android/sdk}/platform-tools/adb"
if [ ! -x "$adb_bin" ]; then adb_bin=$(command -v adb || true); fi
if [ -z "$adb_bin" ] || [ ! -x "$adb_bin" ]; then
  echo '未找到 Android SDK platform-tools/adb。当前电脑安装目录为 ~/Library/Android/sdk。'
  exit 1
fi
devices=$("$adb_bin" devices)
count=$(printf '%s\n' "$devices" | awk '$2 == "device" { n++ } END { print n+0 }')
serial="${1:-}"
if [ -n "$serial" ]; then
  if ! printf '%s\n' "$devices" | awk -v target="$serial" '$1 == target && $2 == "device" { ok=1 } END { exit !ok }'; then
    echo "指定设备尚未连接或未授权：$serial"; exit 1
  fi
elif [ "$count" -eq 1 ]; then
  serial=$(printf '%s\n' "$devices" | awk '$2 == "device" { print $1 }')
elif [ "$count" -gt 1 ]; then
  # Select only when exactly one connected device has this application installed.
  candidates=()
  while IFS= read -r candidate; do
    if "$adb_bin" -s "$candidate" shell pm path cn.paperfly.workbench </dev/null 2>/dev/null | awk '/^package:/ { ok=1 } END { exit !ok }'; then
      candidates+=("$candidate")
    fi
  done < <(printf '%s\n' "$devices" | awk '$2 == "device" { print $1 }')
  if [ "${#candidates[@]}" -eq 1 ]; then serial="${candidates[0]}";
  else
    echo '多台设备已连接，请将目标设备序列号作为本脚本的第一个参数。当前连接：'
    printf '%s\n' "$devices"; exit 1
  fi
else
  echo '没有已授权的设备，请连接 USB、解锁设备并允许 USB 调试。'
  printf '%s\n' "$devices"; exit 1
fi
adb_device() { "$adb_bin" -s "$serial" "$@"; }

echo "正在激活设备 $serial 上的开放工作台…"
adb_device shell am start -n cn.paperfly.workbench/.MainActivity >/dev/null
sleep 1
adb_device shell sh /sdcard/Android/data/cn.paperfly.workbench/files/activate.sh
for attempt in {1..15}; do
  if adb_device shell cat /data/local/tmp/paperfly-workbench-server.log 2>/dev/null |
      awk '/Attached to app process/ { ok=1 } END { exit !ok }'; then
    echo '已激活。平板显示“已激活”后，可以拔线，只使用开放工作台。'
    echo '设备重启或更新 APK 后，再连接 USB 运行本文件即可。'
    exit 0
  fi
  sleep 1
done
adb_device shell cat /data/local/tmp/paperfly-workbench-server.log
echo '尚未确认与 App 连接，请保持平板解锁，重新运行本文件。'
exit 1
