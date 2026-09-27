#!/usr/bin/env bash
# Manual recovery path through the same restricted receiver used by CI.
# Build from clean main first, including scripts/write-build-info.cjs before Maven.
set -euo pipefail
root=$(cd "$(dirname "$0")/.." && pwd)
jar="$root/backend/target/postcompare-0.1.0.jar"
vps=${1:-ubuntu@91.134.138.53}
[[ "$vps" =~ ^[a-zA-Z0-9_.@-]+$ && "$vps" != -* ]] || exit 1
[[ -z $(git -C "$root" status --porcelain) ]] || { echo 'Build and deploy a clean checkout.' >&2; exit 1; }
revision=$(git -C "$root" rev-parse HEAD)
[[ "$revision" =~ ^[0-9a-f]{40}$ && -f "$jar" ]] || exit 1
digest=$(sha256sum "$jar" | cut -d ' ' -f 1)
timeout 300 ssh -T -o BatchMode=yes "$vps" \
  "sudo -n /usr/local/sbin/postcompare-ci-receive 'deploy $revision $digest'" < "$jar"
