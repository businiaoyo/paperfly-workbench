#!/bin/sh
# Installed root-owned. The existing NAS administrator authenticates over SSH.
set -eu
umask 077
state=/data_s005/data/udata/real/docker/workbench-sync
auth="$state/.ssh/authorized_keys"
IFS=' ' read -r kind blob comment
[ "$kind" = ssh-ed25519 ] && [ "${#blob}" -eq 68 ] || exit 2
case "$blob" in *[!A-Za-z0-9+/=]*) exit 2 ;; esac
pub="$kind $blob"
printf '%s\n' "$pub" | ssh-keygen -lf - >/dev/null
exec 9>"$state/.ssh/enroll.lock"
flock -x 9
if awk -v b="$blob" '$2 == "ssh-ed25519" && $3 == b {found=1} END {exit !found}' "$auth"; then exit 0; fi
tmp=$(mktemp "$state/.ssh/authorized_keys.XXXXXX")
trap 'rm -f "$tmp"' EXIT
cat "$auth" > "$tmp"
printf 'restrict,command="%s/git-access" %s OpenWorkbench-Mac\n' "$state" "$pub" >> "$tmp"
chmod 644 "$tmp"
mv -f "$tmp" "$auth"
