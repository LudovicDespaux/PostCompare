# PostCompare

Comparateur de courrier en français : affranchissement à déposer soi-même, transport express et impression avec envoi. React/TypeScript côté navigateur, Java 17/Spring Boot côté serveur. Aucun compte, PDF, paiement ou envoi physique n'est géré par l'application.

## État de cette mise à jour

Les quatre fournisseurs fictifs du prototype ont été remplacés par un **catalogue statique de tarifs publiés**, avec sources et dates. Les résultats restent des **estimations**, pas des devis obtenus en temps réel. Une source renseignée ne garantit pas que le prix est encore applicable à une adresse, un format ou une option précise.

- 39 opérateurs postaux dans 39 pays, plus DHL Express au départ de France.
- 4 opérateurs en ligne dans `backend/src/main/resources/tariffs.json` : LetterStream, Japan Post Webレター, Poste Italiane Postaonline et PostMyDoc.
- 2 calculs spécifiques dans `PrintAndMailProvider` : Merci Facteur et e-lettre rouge.
- Résultats en euros avec prix/devise d'origine, périmètre du prix, régime fiscal déclaré, source et date d'effet renseignée.
- Filtres dépôt postal / envoi en ligne / express, tri par montant ou délai et exigence facultative de suivi.

La couverture est partielle : certains pays n'ont que du national, un palier ou certaines zones internationales. Pour un pays de départ sans grille postale, les services d'impression restent disponibles selon leur destination réelle.

### Limites importantes du calcul

- Le catalogue indique une collecte au 25 septembre 2026 et des taux de change au 24 septembre 2026. BCE principalement ; source complémentaire dans `fx.secondarySource` pour TWD et ARS. Aucune mise à jour automatique.
- Certains tarifs sont HT ou excluent des suppléments (notamment carburant DHL). Le classement porte sur les **montants affichés**, qui ne constituent pas tous un total TTC comparable.
- Les dimensions, épaisseurs, conditions de dépôt, restrictions locales et suppléments ne sont pas complètement modélisés. Certaines grilles anciennes restent identifiées comme telles dans les notes. L'ensemble des 39 pays n'a pas été recertifié lors de ce build.
- Merci Facteur combine les frais publiés avec un affranchissement estimé au tarif La Poste. Les suppléments suivi distinguent France, UE et reste du monde. Les tarifs couleur non renseignés sont exclus au lieu d'être considérés gratuits.
- LetterStream est volontairement limité à une page monochrome : son supplément verso et les paliers d'affranchissement multipage ne sont pas encore complètement modélisés. La [page du prestataire](https://www.letterstream.com/pricing/) distingue ces coûts.
- Sans exigence de suivi, les offres avec et sans suivi sont proposées. Avec exigence de suivi, seules les offres suivies restent éligibles.

## Démarrer en développement

Prérequis : JDK 17+, Maven 3.9+, Node 22.12+ (Node 24 recommandé).

Deux terminaux :

```sh
cd backend
mvn spring-boot:run
```

```sh
cd frontend
npm ci
npm run dev
```

Ouvrir http://localhost:5173. Vite relaie `/api` vers `127.0.0.1:8080`. Aucune clé API nécessaire. Sous Windows, utiliser `npm.cmd` si PowerShell bloque `npm.ps1`. Le projet utilise la distribution WebAssembly officielle de Rollup pour éviter les restrictions Windows sur son module natif.

## Construire et tester une livraison

Compiler le frontend **avant** le backend : Maven intègre `frontend/dist` au JAR et un test vérifie sa présence.

```sh
cd frontend
npm ci
npm run build
cd ../backend
mvn clean verify
```

Artefact : `backend/target/postcompare-0.1.0.jar`. Les tests couvrent les routes, paliers de poids, conversion, zones, suivi, services locaux, options couleur, validation des requêtes et du catalogue, et présence du frontend. La CI vérifie aussi l'image Docker.

Ne pas partager `node_modules` entre Node Windows et Node Linux. Arrêter uniquement le serveur local concerné si Vite verrouille esbuild ou Java verrouille le JAR avant un rebuild.

## API

| Route | Réponse |
|---|---|
| `GET /api/countries` | Codes/libellés de pays ; `postalRates` indique une grille au départ du pays, sans garantir toutes les destinations. |
| `GET /api/carriers` | Les 44 opérateurs du fichier JSON et leurs sources ; les deux calculs spécifiques sont décrits ci-dessus. |
| `POST /api/quotes` | `mode: PUBLIC_RATES`, notice et liste d'offres triées par montant EUR puis identifiant. |

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

Pages : entier 1–50 ; poids avec enveloppe : entier 1–2000 g. Le poids fourni sert au dépôt postal ; les services d'impression utilisent leurs unités (page/feuille) et limites. Pour Merci Facteur : environ 5 g par feuille, enveloppe de 6 g ou 15 g au-delà de cinq feuilles. `tracking: true` est une exigence, `false` ne masque pas les offres suivies. Pays inconnus, champs manquants ou nombres invalides : HTTP 400. Route sans offre couverte : HTTP 200 avec `quotes: []`.

Chaque offre expose `id`, `provider`, `service`, `method`, `price`, `currency`, `originalPrice`, `originalCurrency`, `minDays`, `maxDays`, `tracking`, `description`, `priceScope`, `priceBasis`, `source` et `validFrom`. Les montants serveur utilisent `BigDecimal` ; la conversion EUR est arrondie à deux décimales.

## Maintenir le catalogue

Modifier `backend/src/main/resources/tariffs.json` :

- `groups` : codes ISO et références `@EU`, `@EUROPE`, etc. Les cycles sont rejetés.
- `fx.perEuro` : unités de devise pour un euro, taux strictement positifs ; EUR doit valoir 1.
- `POSTAL` / `EXPRESS` : origines, services `DOMESTIC` ou `INTERNATIONAL`, zones et tranches `[poids maximal inclus en g, prix]`.
- `ONLINE` : pays d'impression `postsFrom`, unité `PAGE` ou `SHEET`, `included`, `max`, puis `base + extra × max(0, unités − included)` ; paire `colorBase`/`colorExtra` pour la couleur.
- Les zones sont examinées dans l'ordre ; placer les zones spécifiques avant `*`. `exclude` retire des destinations.

Le chargement vérifie dates, sources HTTPS, pays/références, taux, identifiants, limites, délais, prix et tranches croissantes. Une grille incohérente doit empêcher le démarrage. Vérifier la source et ajouter un test de palier/zone lors d'une modification tarifaire. Les tarifs e-lettre rouge et Merci Facteur sont encore dans `PrintAndMailProvider` et doivent être maintenus séparément.

## Déploiement VPS OVH

Site : **http://91.134.138.53**. Architecture actuelle : Nginx → `127.0.0.1:8080` → JAR comprenant le frontend. Service systemd `postcompare`, utilisateur dynamique sans privilèges, limite de 512 Mio et redémarrage automatique. Palworld est indépendant.

Prérequis VPS : `openjdk-17-jre-headless`, `nginx`, `curl`. Après un build/test réussi, depuis WSL Ubuntu-26.04 où SSH est configuré :

```sh
bash deploy/deploy-vps.sh
```

Le script transfère le JAR et les configurations, garde les versions dans `/opt/postcompare/releases`, met à jour le lien `current.jar`, puis vérifie la santé et la page via Nginx. Un échec déclenche le retour à la version précédente. Ne pas transférer de secrets, de fichiers `.env` ou de données Palworld.

```sh
ssh ubuntu@91.134.138.53 'systemctl is-active postcompare nginx palworld-bot'
ssh ubuntu@91.134.138.53 'sudo journalctl -u postcompare -n 50 --no-pager'
```

La publication est **publique et en HTTP**. Aucun document personnel ni compte ne doit être introduit avant HTTPS. `deploy/harden-vps.sh` est une opération d'administration distincte : ne pas la lancer automatiquement lors d'une livraison. Lire ses prérequis, vérifier une connexion SSH par clé indépendante et préserver un accès de secours avant tout durcissement. Un script présent dans Git ne signifie pas qu'il a été appliqué sur le VPS.

### Alternative Docker

```sh
docker compose up -d --build
curl http://127.0.0.1:8080/actuator/health
```

Le port 8080 est lié à la boucle locale. `deploy/Caddyfile` fournit une alternative avec Caddy installé sur l'hôte et un domaine à remplacer. Ne pas faire écouter Caddy et Nginx simultanément sur le même port. Prévoir environ 2 Go pour le build et 512 Mo pour le service.

## Cycle des mises à jour et CodeRabbit

Le skill personnel **`$postcompare-release`**, versionné dans `skills/postcompare-release`, exécute : bilan des changements → README → build/tests → PR → examen CodeRabbit → corrections → fusion → déploiement → vérification publique. Exemple : « Utilise $postcompare-release pour publier mes changements. » Il se sélectionne lors d'une demande de mise à jour, sans surveiller automatiquement le disque.

CodeRabbit est installé sur GitHub ; `.coderabbit.yaml` demande une revue en français. Une nouvelle PR contient le diff depuis `main`. Attendre la revue et les contrôles du dernier commit avant fusion. Si besoin, commenter une fois `@coderabbitai full review` ; la commande revoit la PR, pas tous les fichiers inchangés. Un accusé de réception n'est pas un rapport terminé.

## Prochaines étapes

Affiner les formats et frais manquants, automatiser le rafraîchissement des sources avec validation, puis brancher de vrais devis prestataires. Un futur parcours PDF/adresse/paiement nécessitera HTTPS, stockage privé temporaire, suppression et protection contre les doubles envois. L'application actuelle ne persiste aucune donnée utilisateur.

Licence : [GPL-3.0](LICENSE).
