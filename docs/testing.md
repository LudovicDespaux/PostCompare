# Vérification des informations et parcours complets

`cd frontend && npm run test:e2e` lance **360 parcours Chromium distincts** sur le vrai frontend et le JAR Spring Boot. Aucun mock des devis, aucune attente provenant de `tariffs.json`. Construire le frontend puis le JAR avant de lancer la suite. Installer Chromium avec `npx playwright install chromium` (Linux CI : `--with-deps`). Java doit être dans PATH. Le serveur de test est créé automatiquement sur 127.0.0.1:8083 et arrêté à la fin ; aucun test de charge sur le VPS.

La matrice croise 9 routes/services, 10 nombres de feuilles, recto/recto verso et noir/couleur. Elle contrôle le poids affiché, les pages envoyées à l'API, les prix originaux et EUR, les taxes, le suivi, les liens, les détails, le filtre postal, le tri et l'invalidation des anciens résultats. Chaque cas est compté une fois, pas une fois par assertion. Rapports HTML/JSON dans `frontend/playwright-report` et `frontend/test-results` ; traces et captures seulement en échec. Zéro retry pour éviter de masquer les instabilités.

## Références externes

Les données de `e2e/reference-rates.ts` ont été relevées **séparément** le 29 septembre 2026 sur :

- [La Poste, lettre verte 2026](https://www.laposte.fr/tarif-lettre-verte) : port physique France métropolitaine.
- [La Poste, international 2026](https://www.laposte.fr/tarifs-postaux-etranger) : port physique international, avec/sans suivi.
- [Postes Canada, tarifs des timbres](https://www.canadapost-postescanada.ca/cpc/en/personal/stamp-prices.page) : Canada (timbre unitaire, pas carnet), USA, autres destinations ; hors taxes. Les dimensions standard/non standard restent une limite du modèle actuel.

Les 360 tests ne prouvent pas l'exactitude de tous les 44 opérateurs. Ils certifient la concordance de ces six grilles, dans les poids couverts, avec **les relevés datés**. Les délais ne sont pas certifiés par cette suite. Le taux CAD/EUR vérifié est le taux historique de l'application (1,6047), pas le taux de change du jour. Poids/enveloppe : hypothèses du formulaire, pas une pesée réelle.

Les sites tiers ne sont pas interrogés 360 fois à chaque PR. Pour recertifier : consulter les trois pages, relever les tarifs avec date et conditions, mettre à jour les références après revue, puis corriger le catalogue si les tests révèlent une différence. Ne jamais copier automatiquement le catalogue applicatif dans les références pour faire passer les tests. Si une source est inaccessible, sa vérification reste non effectuée ; ne pas annoncer qu'elle est confirmée.
