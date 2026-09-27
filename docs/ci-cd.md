# Tests, revue et déploiement automatiques

GitHub Actions exécute les contrôles sur chaque PR vers `main`. CodeRabbit examine le dernier commit et approuve lorsque ses remarques sont résolues. La protection de `main` impose une PR, un contrôle `test / build` réussi, une approbation et la résolution des conversations. Les nouvelles modifications invalident les approbations précédentes.

Après fusion, le workflow `Verify` reconstruit depuis le commit fusionné, exécute les tests et produit un JAR avec `version.json`. Le job de déploiement récupère cet artefact du même run, vérifie l'approbation CodeRabbit du dernier commit de la PR, puis publie sur le VPS. Il refuse un commit qui n'est plus la tête de `main`. Il n'existe pas de correction automatique aveugle du code : les remarques sont traitées dans la PR avant fusion.

## Accès au VPS

- Environnement GitHub : `production`, limité explicitement à la branche `main`.
- Variables : `DEPLOY_HOST=91.134.138.53`, `DEPLOY_USER=postcompare-deploy`.
- Secrets : `DEPLOY_SSH_KEY` (clé dédiée), `DEPLOY_KNOWN_HOSTS` (clé publique du serveur vérifiée via la connexion administrateur existante).
- Compte dédié : commande SSH imposée et options `restrict`. Le compte ne reçoit ni terminal ni port forwarding. Il peut seulement transmettre un JAR au récepteur installé par l'administrateur.
- Le récepteur vérifie commande, SHA de `main`, taille (64 Mio maximum), SHA-256 et révision intégrée au JAR. Il ne lance aucun script reçu et ne modifie ni Nginx ni les autres applications.
- Si le service ou la vérification locale échoue, le lien vers le JAR précédent est restauré. Un test public supplémentaire contrôle `/version.json` depuis GitHub.

Les fichiers d'administration `ci-entry.sh` et `ci-receive.sh` sont installés en root via `install-ci-access.sh` par la connexion SSH administrateur existante. Leur mise à jour demande la même opération d'administration ; le compte CI ne peut pas les remplacer. Ne jamais stocker la clé privée dans Git.

Le compte CI détient la capacité de publier le code applicatif. Les modifications de workflow et les personnes ayant le droit de fusionner doivent donc rester contrôlées. Le service Java conserve l'utilisateur dynamique et les restrictions systemd déjà configurés.

## Utilisation quotidienne

1. Pousser les changements sur une branche et ouvrir une PR.
2. Corriger les tests et les remarques CodeRabbit, puis résoudre les discussions réellement traitées.
3. Fusionner quand les contrôles sont verts. Le déploiement démarre automatiquement sur `main`.
4. Consulter le run `Verify` et ouvrir `http://91.134.138.53/version.json` pour connaître le commit publié.

En cas de panne réseau temporaire, relancer le workflow depuis GitHub Actions sur `main` avec **Run workflow**. Un ancien run ne doit pas déployer une ancienne révision par-dessus la nouvelle. Le durcissement SSH/UFW ne fait jamais partie d'un déploiement applicatif.

Les runners standards GitHub sont gratuits pour un dépôt public. Les artefacts ont une rétention d'un jour pour limiter le stockage ; aucun runner payant ni achat CodeRabbit n'est configuré.

## Réutiliser pour un autre dépôt

Les workflows `reusable-verify.yml` et `reusable-deploy.yml` sont appelables avec `workflow_call`. Référencer un **SHA immuable** du dépôt PostCompare, puis fournir les commandes, les versions des runtimes et le chemin de l'artefact :

```yaml
jobs:
  test:
    uses: LudovicDespaux/PostCompare/.github/workflows/reusable-verify.yml@REMPLACER_PAR_UN_SHA
    with:
      command: npm ci && npm test && npm run build
      artifact-path: release.zip
      npm-lock: package-lock.json
```

Le déploiement réutilisable transporte un seul fichier vers un récepteur SSH propre au projet. Pour un autre site, créer **son propre compte, sa clé, ses secrets et son récepteur** : `ci-receive.sh` est volontairement limité à PostCompare et à son JAR. Le nouveau récepteur doit accepter `deploy <sha> <sha256>`, vérifier l'artefact et exposer `/version.json` avec la propriété `commit`. Aucun droit PostCompare ne doit être partagé avec un autre projet.

Créer l'environnement `production`, protéger `main`, installer CodeRabbit et activer `reviews.request_changes_workflow: true` dans chaque dépôt concerné. Adapter la commande de build et la vérification publique à l'application. Cette livraison ne configure pas les autres dépôts.

## Vérifications locales

```sh
for script in deploy/*.sh; do bash -n "$script"; done
python3 deploy/test-ci-receive.py
python3 deploy/test-hardening.py
```

Ces tests simulent les opérations dans des dossiers temporaires : ils ne durcissent pas la machine et ne redémarrent aucun service réel.
