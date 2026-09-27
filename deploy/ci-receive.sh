#!/usr/bin/env bash
# Installed root-owned at /usr/local/sbin/postcompare-ci-receive.
# Receives one JAR on stdin; never runs scripts or configuration from the upload.
set -euo pipefail
[[ $EUID -eq 0 && $# -eq 1 ]] || exit 1
[[ "$1" =~ ^deploy\ ([0-9a-f]{40})\ ([0-9a-f]{64})$ ]] || { echo 'Invalid deployment command' >&2; exit 1; }
revision=${BASH_REMATCH[1]}
expected=${BASH_REMATCH[2]}
base=/opt/postcompare
exec 9>"$base/deploy.lock"
flock -w 180 9 || exit 1
# Prevent delayed/old workflow runs from replacing a newer main revision.
current=$(curl --fail --silent --show-error --max-time 20 https://api.github.com/repos/LudovicDespaux/PostCompare/commits/main | python3 -c 'import json,sys; print(json.load(sys.stdin)["sha"])')
[[ "$revision" == "$current" ]] || { echo 'Refusing a revision other than current main' >&2; exit 1; }
umask 077
incoming=$(mktemp "$base/releases/.incoming.XXXXXX")
trap 'rm -f "$incoming"' EXIT
timeout 120 head -c 67108865 > "$incoming"
[[ $(stat -c %s "$incoming") -le 67108864 ]] || { echo 'Artifact exceeds 64 MiB' >&2; exit 1; }
actual=$(sha256sum "$incoming" | cut -d ' ' -f 1)
[[ "$actual" == "$expected" ]] || { echo 'Artifact checksum mismatch' >&2; exit 1; }
python3 - "$incoming" "$revision" <<'PY'
import json, sys, zipfile
with zipfile.ZipFile(sys.argv[1]) as jar:
    if sum(i.file_size for i in jar.infolist()) > 512 * 1024 * 1024:
        raise SystemExit('Uncompressed archive too large')
    jar.getinfo('BOOT-INF/classes/static/index.html')
    info = jar.getinfo('BOOT-INF/classes/static/version.json')
    if info.file_size > 1024:
        raise SystemExit('Invalid build metadata')
    if json.loads(jar.read(info))['commit'] != sys.argv[2]:
        raise SystemExit('Artifact revision mismatch')
PY
previous=$(readlink "$base/current.jar")
[[ "$previous" == "$base/releases/"*.jar && -f "$previous" ]] || { echo 'Missing rollback version' >&2; exit 1; }
release="$base/releases/$(date -u +%Y%m%dT%H%M%SZ)-$revision.jar"
[[ ! -e "$release" ]] || { echo 'Release already exists' >&2; exit 1; }
install -m 644 "$incoming" "$release"
rollback() {
  echo 'Activation failed; restoring the previous JAR' >&2
  ln -sfn "$previous" "$base/current.jar"
  systemctl restart postcompare
}
trap rollback ERR
ln -sfn "$release" "$base/current.jar"
systemctl restart postcompare
ready=false
for attempt in $(seq 1 30); do
  if curl --fail --silent --max-time 5 http://127.0.0.1:8080/actuator/health | grep -q '"status":"UP"'; then
    if curl --fail --silent --max-time 5 http://127.0.0.1:8080/version.json | python3 -c 'import json,sys; assert json.load(sys.stdin)["commit"] == sys.argv[1]' "$revision"; then
      ready=true
      break
    fi
  fi
  sleep 2
done
[[ "$ready" == true ]]
curl --fail --silent --max-time 10 -H 'Host: 91.134.138.53' http://127.0.0.1/version.json | python3 -c 'import json,sys; assert json.load(sys.stdin)["commit"] == sys.argv[1]' "$revision"
systemctl is-active postcompare nginx
trap - ERR
printf 'Deployed %s SHA256 %s (previous: %s)\n' "$revision" "$actual" "$previous"

