#!/usr/bin/env bash
# Runs as root on the VPS, after uploading the jar and deployment files.
set -euo pipefail
release=${1:?Release ID required}
upload=${2:?Upload directory required}
[[ "$release" =~ ^[0-9]{8}T[0-9]{6}Z$ ]] || { echo 'Invalid release ID' >&2; exit 1; }
[[ "$upload" == /home/ubuntu/postcompare-upload/"$release" ]] || { echo 'Invalid upload path' >&2; exit 1; }
[[ -f "$upload/postcompare.jar" ]] || { echo 'Missing jar' >&2; exit 1; }
install -d -m 755 /opt/postcompare/releases
exec 9>/opt/postcompare/deploy.lock
flock -n 9 || { echo 'Another deployment is running' >&2; exit 1; }

previous=$(readlink /opt/postcompare/current.jar || true)
nginx_enabled=false
[[ ! -L /etc/nginx/sites-enabled/postcompare ]] || nginx_enabled=true
backup="/opt/postcompare/releases/$release-config"
install -d -m 700 "$backup"
for item in /etc/systemd/system/postcompare.service /etc/nginx/sites-available/postcompare; do
    if [[ -f "$item" ]]; then cp "$item" "$backup/$(basename "$item")"; fi
done
rollback() {
    echo 'Deployment failed; restoring previous configuration.' >&2
    if [[ -f "$backup/postcompare.service" ]]; then
        cp "$backup/postcompare.service" /etc/systemd/system/postcompare.service
    fi
    if [[ -f "$backup/postcompare" ]]; then
        cp "$backup/postcompare" /etc/nginx/sites-available/postcompare
    fi
    if [[ "$nginx_enabled" == false ]]; then
        rm -f /etc/nginx/sites-enabled/postcompare
    fi
    systemctl daemon-reload
    if [[ -n "$previous" ]]; then
        ln -sfn "$previous" /opt/postcompare/current.jar
        systemctl restart postcompare
    else
        systemctl disable --now postcompare || true
        rm -f /opt/postcompare/current.jar
    fi
    nginx -t && systemctl reload nginx
}
trap rollback ERR
install -m 644 "$upload/postcompare.jar" "/opt/postcompare/releases/$release.jar"
install -m 644 "$upload/postcompare.service" /etc/systemd/system/postcompare.service
install -m 644 "$upload/nginx.conf" /etc/nginx/sites-available/postcompare
ln -sfn /etc/nginx/sites-available/postcompare /etc/nginx/sites-enabled/postcompare
nginx -t
ln -sfn "/opt/postcompare/releases/$release.jar" /opt/postcompare/current.jar
systemctl daemon-reload
systemctl enable postcompare
systemctl restart postcompare
healthy=false
for attempt in $(seq 1 30); do
    if curl -fsS http://127.0.0.1:8080/actuator/health 2>/dev/null | grep -q '"status":"UP"'; then
        healthy=true
        break
    fi
    sleep 2
done
[[ "$healthy" == true ]]
systemctl enable --now nginx
systemctl reload nginx
# Reload is asynchronous: old workers can still serve the default site briefly.
public_ready=false
for attempt in $(seq 1 10); do
    if curl -fsS -H 'Host: 91.134.138.53' http://127.0.0.1/ 2>/dev/null | grep -q 'PostCompare'; then
        public_ready=true
        break
    fi
    sleep 1
done
[[ "$public_ready" == true ]]
trap - ERR
echo "Activated $release (previous: ${previous:-none})"
systemctl is-active postcompare nginx
