#!/usr/bin/env bash
# One-time administration via the existing trusted SSH connection.
# Usage: sudo bash install-ci-access.sh public-key.pub ci-entry.sh ci-receive.sh
set -euo pipefail
[[ $EUID -eq 0 && $# -eq 3 ]] || exit 1
key_file=$(realpath "$1")
entry_file=$(realpath "$2")
receiver_file=$(realpath "$3")
ssh-keygen -lf "$key_file" > /dev/null
[[ $(wc -l < "$key_file") -eq 1 ]] || exit 1
read -r kind key comment < "$key_file"
[[ "$kind" == ssh-ed25519 && "$key" =~ ^[A-Za-z0-9+/=]+$ ]] || exit 1
[[ -d /opt/postcompare/releases && -L /opt/postcompare/current.jar ]] || exit 1
if ! id postcompare-deploy >/dev/null 2>&1; then
  useradd --system --create-home --home-dir /var/lib/postcompare-deploy --shell /bin/bash postcompare-deploy
fi
[[ $(getent passwd postcompare-deploy | cut -d: -f6) == /var/lib/postcompare-deploy ]] || exit 1
chown root:root /var/lib/postcompare-deploy
chmod 755 /var/lib/postcompare-deploy
install -o root -g root -m 755 "$entry_file" /usr/local/bin/postcompare-ci-entry
install -o root -g root -m 755 "$receiver_file" /usr/local/sbin/postcompare-ci-receive
install -d -o root -g root -m 755 /var/lib/postcompare-deploy/.ssh
printf 'restrict,command="/usr/local/bin/postcompare-ci-entry" %s %s postcompare-ci\n' "$kind" "$key" > /var/lib/postcompare-deploy/.ssh/authorized_keys
chown root:root /var/lib/postcompare-deploy/.ssh/authorized_keys
chmod 644 /var/lib/postcompare-deploy/.ssh/authorized_keys
sudoers=$(mktemp /etc/sudoers.d/.postcompare-ci.XXXXXX)
trap 'rm -f "$sudoers"' EXIT
printf 'postcompare-deploy ALL=(root) NOPASSWD: /usr/local/sbin/postcompare-ci-receive *\n' > "$sudoers"
chmod 440 "$sudoers"
visudo -cf "$sudoers"
install -o root -g root -m 440 "$sudoers" /etc/sudoers.d/postcompare-ci
echo 'Restricted PostCompare deployment account installed.'
