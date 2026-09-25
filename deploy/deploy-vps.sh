#!/usr/bin/env bash
# Requires SSH access and a tested jar containing the production frontend.
set -euo pipefail
root=$(cd "$(dirname "$0")/.." && pwd)
jar="$root/backend/target/postcompare-0.1.0.jar"
vps=${1:-ubuntu@91.134.138.53}
[[ "$vps" =~ ^[a-zA-Z0-9_.@-]+$ ]] && [[ "$vps" != -* ]] || exit 1
[[ -f "$jar" ]] || { echo 'Build frontend then run mvn verify in backend first.' >&2; exit 1; }
release=$(date -u +%Y%m%dT%H%M%SZ)
upload="/home/ubuntu/postcompare-upload/$release"
ssh -o BatchMode=yes "$vps" "mkdir -p '$upload'"
scp "$jar" "$vps:$upload/postcompare.jar"
scp "$root/deploy/postcompare.service" "$root/deploy/nginx.conf" "$root/deploy/activate-release.sh" "$vps:$upload/"
ssh -o BatchMode=yes "$vps" "sudo -n bash '$upload/activate-release.sh' '$release' '$upload'"
