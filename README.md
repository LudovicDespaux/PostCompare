# PostCompare

Un premier prototype de comparateur de courrier : dépôt postal ou impression avec envoi, depuis et vers les pays ISO 3166. Interface française, React + TypeScript, API Java 17 / Spring Boot, déploiement Docker sur VPS.

**Mode démo uniquement.** Les quatre prestataires sont fictifs, ainsi que tous les prix et délais. La présence d'un pays dans le formulaire ne constitue pas une couverture commerciale. Aucun appel à un prestataire réel, paiement, téléversement de PDF ou envoi physique n'est implémenté.

## Démarrer en local

Prérequis : Java **JDK** 17+, Maven 3.9+, Node 22.12+ (Node 24 recommandé).

Terminal 1 :

```sh
cd backend
mvn spring-boot:run
```

Terminal 2 :

```sh
cd frontend
npm ci
npm run dev
```

Ouvrir http://localhost:5173. Vite relaie `/api` vers le backend sur le port 8080. Aucune clé API nécessaire. Sans backend, le formulaire indique l'échec du chargement des pays et propose de réessayer.

## Docker / VPS OVH

### Installation actuellement utilisée sur le VPS

La démo est publiée sur **http://91.134.138.53** avec Nginx et le service systemd
`postcompare`. Java écoute uniquement sur `127.0.0.1:8080`. Le service fonctionne
avec un utilisateur dynamique sans privilèges, une limite mémoire de 512 Mo et
un redémarrage automatique. Palworld reste un service indépendant.

Cette URL est en HTTP. Aucun document, adresse complète, compte ou paiement ne
doit être ajouté avant la configuration HTTPS avec un domaine.

Les fichiers de déploiement sont dans `deploy/` : `nginx.conf`,
`postcompare.service`, `activate-release.sh` et `deploy-vps.sh`.
Le VPS doit disposer de `openjdk-17-jre-headless`, `nginx` et `curl`.

Pour une nouvelle version, compiler le frontend **avant** le backend :

```sh
cd frontend
npm ci
npm run build
cd ../backend
mvn clean verify
cd ..
bash deploy/deploy-vps.sh
```

Sous Windows, compiler avec Node et Maven Windows puis lancer le dernier script
dans WSL, où la connexion SSH est déjà configurée. Ne pas partager un même
`node_modules` entre Node Windows et Node Linux. Arrêter le backend local avant
le build si son processus Java verrouille le JAR.

Le script transfère seulement le JAR compilé et les configurations de déploiement.
Les versions sont conservées dans `/opt/postcompare/releases`, et `current.jar`
pointe vers la version active. La santé du backend puis la page via Nginx sont
vérifiées ; un échec déclenche le retour à la version précédente et restaure les
configurations sauvegardées. Aucun secret SSH n'est inclus dans le dépôt.

```sh
ssh ubuntu@91.134.138.53 'systemctl is-active postcompare nginx palworld-bot'
ssh ubuntu@91.134.138.53 'sudo journalctl -u postcompare -n 50 --no-pager'
```

### Alternative Docker

```sh
docker compose up -d --build
curl http://127.0.0.1:8080/actuator/health
```

Le build inclut le frontend dans le JAR Spring Boot : un seul service, une seule origine, aucune base de données. Le service est accessible sur la boucle locale du VPS. En local, ouvrir http://localhost:8080.

Pour le publier : installer Docker Engine avec Compose sur le VPS, y cloner le dépôt puis exécuter la commande ci-dessus. Installer Caddy sur **l'hôte**, remplacer le domaine d'exemple dans `deploy/Caddyfile`, configurer le DNS vers le VPS puis utiliser ce fichier comme configuration Caddy. Autoriser les ports 80/443 dans les pare-feu OVH et système et recharger Caddy ; le certificat HTTPS sera géré par Caddy. Ne pas exposer directement le port 8080. Le Caddyfile fourni suppose que Caddy s'exécute sur l'hôte, pas dans un conteneur.

Prévoir environ 2 Go de RAM pour compiler sur le VPS ; le service est limité à 512 Mo au runtime. Pas de déploiement automatique ni de secrets enregistrés. Les polices Google Fonts sont téléchargées par le navigateur, avec polices système en repli.

## API

`GET /api/countries` : pays et libellés français.

`POST /api/quotes` :

```json
{
  "origin": "FR",
  "destination": "RO",
  "pages": 2,
  "weight": 20,
  "color": false,
  "duplex": true,
  "tracking": false
}
```

Réponse : `mode`, `notice`, `quotes` classées par prix croissant en EUR. Pages : 1–50 ; poids : 1–500 g. Les offres postales fictives sont limitées à 250 g. Pour l'impression, le poids est calculé sur les feuilles (5 g) et l'enveloppe (6 g). Le dépôt postal inclut uniquement le port, les offres numériques incluent impression, enveloppe et port : ce périmètre est affiché. La sélection du suivi exclut les fournisseurs qui ne le supportent pas. Les montants sont calculés en centimes puis exposés en décimal, sans calcul monétaire en flottant côté serveur.

## Vérification

Construire d'abord le frontend : un test vérifie qu'il est inclus dans le JAR.

```sh
cd backend
mvn verify
```

```sh
cd frontend
npm ci
npm run build
```

La CI vérifie l'API, TypeScript, le build frontend et l'image Docker. Les cinq tests couvrent classement/prix, exclusion des offres sans suivi, validation des requêtes, rejet des pages décimales et présence du frontend empaqueté.

## Revue CodeRabbit

Les revues de pull requests sont gratuites pour les dépôts publics selon
[l'offre open source](https://www.coderabbit.ai/oss). Installer
[l'application GitHub CodeRabbit](https://github.com/apps/coderabbitai) en limitant
son accès à ce dépôt, puis ouvrir une pull request contenant les modifications.
La configuration `.coderabbit.yaml` demande une revue en français, avec attention
à la validation, aux prix, aux états de l'interface et au déploiement.

Pour relancer une revue de l'ensemble des changements d'une PR, y commenter
`@coderabbitai full review`. Cela concerne toute la PR, pas automatiquement tous
les fichiers inchangés du dépôt. Pour la revue initiale, conserver le commit initial
comme base et soumettre tout le prototype dans une première PR. Ne pas confondre
cette revue avec un audit de sécurité exhaustif ; les scans complets proposés par
CodeRabbit peuvent avoir une tarification distincte.

## Passer aux vrais tarifs

1. Choisir un prestataire pilote et vérifier sa documentation officielle, l'accès sandbox, les pays réellement couverts et les conditions d'utilisation des devis. Les capacités citées dans le brainstorming restent à vérifier.
2. Implémenter `MailProvider` et remplacer les fournisseurs fictifs dans `QuoteController` par des adaptateurs injectés. Conserver les clés uniquement côté serveur, via variables d'environnement.
3. Étendre le modèle avec taxes, devise d'origine, conversion datée, horodatage et expiration du devis, couverture, adresses nécessaires et délais de réponse. Gérer indépendamment timeouts et erreurs de chaque prestataire.
4. Autoriser les comparaisons réelles uniquement pour les routes supportées. Ne pas mélanger simulations et devis réels dans le classement.
5. Ajouter un parcours PDF/adresse/paiement seulement après le fonctionnement des devis sandbox ; prévoir alors stockage privé temporaire, suppression, consentement et idempotence de l'envoi.

Le MVP actuel ne collecte ni adresse complète ni document et ne persiste aucune donnée. Aucun compte utilisateur ou paiement n'est nécessaire pour tester l'idée.

Licence : voir [LICENSE](LICENSE).
