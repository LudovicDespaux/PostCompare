#!/usr/bin/env bash
# Durcissement du VPS PostCompare (Ubuntu/Debian).
# - Pare-feu UFW : SSH, 80, 443 (+ ports supplémentaires via EXTRA_PORTS)
# - SSH : clés uniquement, root interdit
# - fail2ban + mises à jour de sécurité automatiques
#
# Filet de sécurité : si tu ne confirmes pas dans les 5 minutes (depuis une
# NOUVELLE session SSH), les configurations UFW et SSH antérieures sont restaurées.
# Vérification systemd chaque minute, également après redémarrage.
#
# Usage (sur le VPS) :
#   sudo EXTRA_PORTS="8211/udp" bash harden-vps.sh     # EXTRA_PORTS optionnel
#   Tester d’abord une connexion avec PasswordAuthentication=no.
#   Puis confirmer depuis une 2e connexion avec la commande affichée par le script.
set -euo pipefail

[[ $EUID -eq 0 ]] || { echo "Lancer avec sudo."; exit 1; }

ADMIN_USER="${SUDO_USER:-ubuntu}"
AUTH_KEYS="$(getent passwd "$ADMIN_USER" | cut -d: -f6)/.ssh/authorized_keys"
DROPIN=/etc/ssh/sshd_config.d/00-postcompare-hardening.conf
# Un seul durcissement à la fois, y compris durant la fenêtre de confirmation.
exec 9>/run/postcompare-hardening.lock
flock -n 9 || { echo "Un durcissement est déjà en cours."; exit 1; }
[[ "$ADMIN_USER" != root ]] || { echo "Utiliser sudo depuis un administrateur non root."; exit 1; }
PENDING=/root/postcompare-hardening-pending
if [[ -e "$PENDING" && ! -e "$PENDING/confirmed" && ! -e "$PENDING/restored" ]]; then
  echo "Une opération attend sa confirmation ou son retour arrière : $PENDING"; exit 1
fi
[[ -n "${SSH_CONNECTION:-}" ]] || { echo "Lancer depuis SSH en préservant SSH_CONNECTION via sudo."; exit 1; }
read -r CLIENT_ADDR CLIENT_PORT SERVER_ADDR SSH_PORT <<< "$SSH_CONNECTION"
[[ "$CLIENT_ADDR" =~ ^[0-9a-fA-F:.]+$ && "$SERVER_ADDR" =~ ^[0-9a-fA-F:.]+$ && "$SSH_PORT" =~ ^[0-9]+$ ]] || exit 1
CLIENT_HOST="${SSH_CLIENT_HOST:-$CLIENT_ADDR}"
[[ "$CLIENT_HOST" =~ ^[a-zA-Z0-9_.:-]+$ ]] || exit 1
if sshd -T | grep -qx 'usedns yes'; then
  [[ -n "${SSH_CLIENT_HOST:-}" ]] || { echo "Fournir SSH_CLIENT_HOST : nom du client résolu par sshd (UseDNS yes)."; exit 1; }
fi

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

# --- Sauvegarde et retour arrière indépendant de la session SSH
STATE="$(mktemp -d /root/postcompare-hardening.XXXXXX)"
chmod 700 "$STATE"
OK_FLAG="$STATE/confirmed"
UNIT="$(basename "$STATE")-rollback"
printf '%s\n' "$UNIT" > "$STATE/unit"
printf '%s\n' "$(( $(date +%s) + 300 ))" > "$STATE/deadline"
cp -a /etc/ufw "$STATE/ufw"
cp -a /etc/default/ufw "$STATE/ufw-default"
if [[ -e "$DROPIN" ]]; then cp -a "$DROPIN" "$STATE/ssh-dropin"; fi
cat > "$STATE/rollback.sh" <<'ROLLBACK'
#!/usr/bin/env bash
set -euo pipefail
STATE="$(cd -- "$(dirname -- "$0")" && pwd)"
UNIT="$(<"$STATE/unit")"
# Le chemin ERR hérite déjà du verrou ; le timer attend la fin de l'application.
if [[ "${1:-}" != --force ]]; then
  exec 9>/run/postcompare-hardening.lock
  flock -x 9
fi
if [[ -e "$STATE/confirmed" || -e "$STATE/restored" ]]; then
  systemctl disable --now "$UNIT.timer"
  exit 0
fi
if [[ "${1:-}" != --force && $(date +%s) -lt $(<"$STATE/deadline") ]]; then exit 0; fi
DROPIN=/etc/ssh/sshd_config.d/00-postcompare-hardening.conf
if [[ -e "$STATE/ssh-dropin" ]]; then
  cp -a "$STATE/ssh-dropin" "$DROPIN"
else
  rm -f "$DROPIN"
fi
sshd -t
systemctl reload ssh || systemctl reload sshd
cp -a "$STATE/ufw/." /etc/ufw/
cp -a "$STATE/ufw-default" /etc/default/ufw
if grep -q '^ENABLED=yes' "$STATE/ufw/ufw.conf"; then
  ufw --force enable
  ufw reload
else
  ufw --force disable
fi
touch "$STATE/restored"
systemctl disable --now "$UNIT.timer"
echo 'harden-vps: configuration réseau antérieure restaurée' | systemd-cat -t harden-vps
ROLLBACK
chmod 700 "$STATE/rollback.sh"
# Unités persistantes : une échéance manquée pendant un reboot est rattrapée.
cat > "/etc/systemd/system/$UNIT.service" <<EOF
[Unit]
Description=PostCompare hardening rollback check
After=network.target
[Service]
Type=oneshot
ExecStart=/bin/bash $STATE/rollback.sh
TimeoutStartSec=10min
EOF
cat > "/etc/systemd/system/$UNIT.timer" <<EOF
[Unit]
Description=Check unconfirmed PostCompare hardening
[Timer]
OnBootSec=30s
OnCalendar=minutely
Persistent=true
AccuracySec=1s
[Install]
WantedBy=timers.target
EOF
systemctl daemon-reload
systemctl enable --now "$UNIT.timer"
ln -sfn "$STATE" "$PENDING"
trap 'bash "$STATE/rollback.sh" --force' ERR

# --- Pare-feu
# Garder une règle SSH d'urgence pendant le remplacement des anciennes règles.
EMERGENCY="$(basename "$STATE")-ssh"
ufw insert 1 allow "${SSH_PORT}/tcp" comment "$EMERGENCY"
ufw default deny incoming
ufw default allow outgoing
ufw --force enable
rules="$(ufw status numbered)"
while read -r number; do
  [[ -z "$number" ]] || ufw --force delete "$number"
done < <(printf '%s\n' "$rules" | grep -v -F "$EMERGENCY" | sed -n 's/^\[[[:space:]]*\([0-9][0-9]*\)\].*/\1/p' | sort -rn)
ufw limit "${SSH_PORT}/tcp" comment 'SSH'
ufw allow 80/tcp  comment 'HTTP'
ufw allow 443/tcp comment 'HTTPS'
for p in ${EXTRA_PORTS:-}; do ufw allow "$p"; done
rules="$(ufw status numbered)"
while read -r number; do
  [[ -z "$number" ]] || ufw --force delete "$number"
done < <(printf '%s\n' "$rules" | grep -F "$EMERGENCY" | sed -n 's/^\[[[:space:]]*\([0-9][0-9]*\)\].*/\1/p' | sort -rn)

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
for user in "$ADMIN_USER" root; do
  echo "== SSH effectif : $user depuis $CLIENT_ADDR ($CLIENT_HOST) =="
  settings="$(sshd -T -C "user=$user,host=$CLIENT_HOST,addr=$CLIENT_ADDR,laddr=$SERVER_ADDR,lport=$SSH_PORT")"
  printf '%s\n' "$settings" | grep -Ei '^(passwordauthentication|permitrootlogin|kbdinteractiveauthentication)'
  for setting in 'passwordauthentication no' 'kbdinteractiveauthentication no' 'permitrootlogin no'; do
    grep -qx "$setting" <<< "$settings" || { echo "Configuration Match incompatible"; false; }
  done
done
[[ $(date +%s) -lt $(<"$STATE/deadline") ]] || { echo "Échéance dépassée, restauration."; false; }
echo
echo ">>> Ouvre MAINTENANT une 2e session SSH depuis ton PC. Si elle marche :"
echo ">>>   sudo touch $OK_FLAG"
echo ">>> Sans confirmation : restauration UFW/SSH à l'échéance (contrôle chaque minute, même après reboot)."
echo ">>> Journal : sudo journalctl -u $UNIT.service"
echo ">>> Les paquets, fail2ban et mises à jour automatiques ne sont pas annulés."
