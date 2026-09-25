#!/usr/bin/env bash
# Durcissement du VPS PostCompare (Ubuntu/Debian).
# - Pare-feu UFW : SSH, 80, 443 (+ ports supplémentaires via EXTRA_PORTS)
# - SSH : clés uniquement, root interdit
# - fail2ban + mises à jour de sécurité automatiques
#
# Filet de sécurité : si tu ne confirmes pas dans les 5 minutes (depuis une
# NOUVELLE session SSH), le pare-feu est désactivé et la config SSH restaurée.
#
# Usage (sur le VPS) :
#   sudo EXTRA_PORTS="8211/udp" bash harden-vps.sh     # EXTRA_PORTS optionnel
#   puis, depuis une 2e connexion SSH :  sudo touch /root/harden-ok
set -euo pipefail

[[ $EUID -eq 0 ]] || { echo "Lancer avec sudo."; exit 1; }

ADMIN_USER="${SUDO_USER:-ubuntu}"
AUTH_KEYS="$(getent passwd "$ADMIN_USER" | cut -d: -f6)/.ssh/authorized_keys"
DROPIN=/etc/ssh/sshd_config.d/00-postcompare-hardening.conf
OK_FLAG=/root/harden-ok
SSH_PORT="$(sshd -T 2>/dev/null | awk '/^port /{print $2; exit}')"; SSH_PORT="${SSH_PORT:-22}"

echo "== Ports actuellement en écoute (hors boucle locale) =="
ss -tulpnH | awk '$5 !~ /^(127\.|\[::1\])/ {print "  " $1, $5, $7}'
echo
echo "Seront autorisés : ${SSH_PORT}/tcp (SSH), 80/tcp, 443/tcp ${EXTRA_PORTS:-}"
echo "Tout autre port public ci-dessus sera BLOQUÉ (ex. serveur de jeu Palworld : ajouter EXTRA_PORTS)."
read -rp "Continuer ? [o/N] " ans; [[ "$ans" =~ ^[oOyY]$ ]] || exit 1

# --- Garde-fou : ne jamais couper l'accès par mot de passe sans clé en place
if [[ ! -s "$AUTH_KEYS" ]]; then
  echo "ERREUR : aucune clé dans $AUTH_KEYS."
  echo "Depuis ton PC : ssh-copy-id $ADMIN_USER@<ip>  (ou type ~/.ssh/id_ed25519.pub | ssh ... 'cat >> ~/.ssh/authorized_keys')"
  exit 1
fi

apt-get update -qq
DEBIAN_FRONTEND=noninteractive apt-get install -y -qq ufw fail2ban unattended-upgrades

# --- Retour arrière automatique
rm -f "$OK_FLAG"
nohup bash -c "
  sleep 300
  if [[ ! -f $OK_FLAG ]]; then
    ufw --force disable
    rm -f $DROPIN
    systemctl reload ssh || systemctl reload sshd
    echo 'harden-vps: pas de confirmation, retour arrière effectué' | systemd-cat -t harden-vps
  fi" >/dev/null 2>&1 &

# --- Pare-feu
ufw default deny incoming
ufw default allow outgoing
ufw limit "${SSH_PORT}/tcp" comment 'SSH'
ufw allow 80/tcp  comment 'HTTP'
ufw allow 443/tcp comment 'HTTPS'
for p in ${EXTRA_PORTS:-}; do ufw allow "$p"; done
ufw --force enable

# --- SSH : clés uniquement (fichier 00- pour passer avant 50-cloud-init.conf)
cat > "$DROPIN" <<EOF
PasswordAuthentication no
KbdInteractiveAuthentication no
PermitRootLogin no
PubkeyAuthentication yes
MaxAuthTries 4
EOF
sshd -t
systemctl reload ssh 2>/dev/null || systemctl reload sshd

# --- fail2ban (jail SSH)
cat > /etc/fail2ban/jail.d/sshd.local <<EOF
[sshd]
enabled = true
port = ${SSH_PORT}
maxretry = 5
bantime = 1h
EOF
systemctl enable --now fail2ban
systemctl restart fail2ban

# --- Mises à jour de sécurité automatiques
dpkg-reconfigure -f noninteractive unattended-upgrades

echo
echo "== Terminé =="
ufw status verbose
sshd -T | grep -Ei '^(passwordauthentication|permitrootlogin|kbdinteractiveauthentication)'
echo
echo ">>> Ouvre MAINTENANT une 2e session SSH depuis ton PC. Si elle marche :"
echo ">>>   sudo touch $OK_FLAG"
echo ">>> Sinon, dans 5 min tout sera annulé automatiquement."
