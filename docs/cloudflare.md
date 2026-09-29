# Cloudflare pour les sites du VPS

## État et prérequis

Préparation, pas protection active tant que le compte, le domaine, le tunnel et la fermeture de l'accès direct ne sont pas validés. Le 29/09/2026, le VPS expose TCP 80 et 22 ; PostCompare écoute uniquement sur 127.0.0.1:8080. Nginx partage son hôte IP avec un autre site via un snippet. Ne pas remplacer cette configuration par `deploy/nginx.conf` : cela supprimerait cette inclusion.

Le connecteur officiel cloudflared 2026.9.3 et le service cloudflared-vps ont été installés sur le VPS le 29/09/2026. Le service est désactivé, sans jeton : aucun trafic ne passe encore par Cloudflare. Les services PostCompare, Nginx et Palworld restent actifs.

Un seul domaine acheté peut porter plusieurs sous-domaines sans acheter un domaine par application. Le plan Cloudflare Free suffit pour commencer ; domaine à payer séparément. La création du compte, la validation des conditions et l'achat restent à réaliser par le propriétaire. Aucun secret ne doit être collé dans Git, les logs CI ou le chat.

## Architecture proposée

Navigateur HTTPS → Cloudflare → tunnel sortant chiffré → Nginx local → application. Le tunnel peut desservir plusieurs noms et conserver les chemins actuels. Ne pas router directement vers Java : cela contournerait les règles Nginx et le blocage d'Actuator.

1. Créer le compte Cloudflare et ajouter le domaine choisi au plan Free. Vérifier les DNS existants, notamment MX/TXT, avant tout changement de nameservers.
2. Créer un tunnel administré à distance pour ce VPS. Installer `cloudflared` via le dépôt officiel signé : https://pkg.cloudflare.com/index.html ; ne pas utiliser de tunnel temporaire comme publication durable.
3. Stocker le jeton dans `/etc/cloudflared/vps.token` (root:root, mode 0600 ; dossier 0700). Le service fourni utilise `LoadCredential` pour ne pas exposer le jeton dans sa ligne de commande. Ne pas le transmettre à GitHub Actions.
4. Installer `deploy/cloudflare/cloudflared.service` sous `/etc/systemd/system/cloudflared-vps.service`, puis démarrer ce service seulement après ajout du jeton.
5. Dans les routes du tunnel, associer chaque nom retenu à `http://127.0.0.1:80`, avec HTTP Host Header `91.134.138.53` pour préserver le routage actuel. Ne pas activer Access sur un site public sauf volonté de le rendre privé. HTTPS obligatoire côté navigateur ; pas de cache des `/api/*` et `/version.json`.
6. Vérifier chaque application depuis son hostname HTTPS (et ses chemins), les appels API, les en-têtes, la version et le refus de `/actuator/health`. La limitation Nginx voit actuellement la connexion locale du tunnel : configurer un listener réservé au tunnel pour faire confiance à `CF-Connecting-IP` uniquement depuis cloudflared, avant usage public intensif. Ne jamais faire confiance globalement à cet en-tête sur le listener IP public.
7. Mettre à jour l'URL publique du workflow de déploiement. Le récepteur continue ses contrôles locaux par Host IP. Quand **tous** les sites fonctionnent par le tunnel, retirer les listeners web publics au profit de localhost (ou appliquer un pare-feu testé). Garder SSH et son accès CI opérationnels. Vérifier IPv4 **et** IPv6 depuis l'extérieur.

L'IP actuelle est connue : tant que le port 80 reste accessible publiquement, Cloudflare peut être contourné. Le tunnel ne protège pas automatiquement SSH, ni d'autres protocoles ou services du VPS. Leur durcissement reste distinct. Aucun port ne doit être fermé avant inventaire et vérification de chaque application.

## Retour arrière

Conserver les fichiers Nginx et leurs droits avant modification. Tester `nginx -t` avant reload. En cas de panne pendant la migration, restaurer le listener antérieur, vérifier chaque site et suspendre le tunnel. Ne pas supprimer les DNS mail. Une fois l'accès direct fermé, rétablir d'abord une route administrateur fonctionnelle avant toute autre modification réseau.

Sources : [Tunnel](https://developers.cloudflare.com/tunnel/), [installation](https://developers.cloudflare.com/tunnel/get-started/), [protection de l'origine](https://developers.cloudflare.com/fundamentals/security/protect-your-origin-server/).
