# Portails Immersifs KB — Fabric 26.3

Version personnalisée du moteur libre Wormholes, développée pour KB. Ce projet ne constitue pas une réécriture intégrale d'Immersive Portals 5.2.0. Le moteur de rendu, la synchronisation, les traversées et la majorité des fonctionnalités proviennent de Wormholes et de sa bibliothèque Optics ; les sources et crédits sont conservés sous GPL-3.0.

## Ajouts de cette version

- `/portail` et `/portail aide` : aide française.
- `/portail creer <nom>` : ouverture de 2 × 3 blocs à trois blocs devant le joueur.
- `/portail creer <nom> <largeur> <hauteur>` : largeur de 1 à 16, hauteur de 2 à 16.
- `/portail relier <nom1> <nom2>` : liaison dans les deux sens, y compris entre dimensions.
- `/portail liste` : portails accessibles et destinations.
- `/portail supprimer <nom>` : suppression d'un portail ordinaire accessible.
- `/portail avance ...` : accès aux commandes du moteur Wormholes.
- Noms personnels insensibles à la casse ; résolution en priorité parmi ses propres portails. Les noms ambigus sont refusés.
- Respect des permissions et des protections de placement. Les ouvertures occupées sont refusées sans supprimer de blocs.
- Annulation de la première liaison si la seconde est refusée.
- Nom du mod et du JAR personnalisés, huit tests des commandes et procédure d'essais GPU sous Windows.
- Sauvegarde atomique : nouvelles tentatives limitées en cas de verrouillage temporaire sous Windows, sans supprimer préalablement le document existant.
- Sodium Fabric fixé à 0.9.2, la version requise par Iris 1.11.7, à la place de la version alpha utilisée par les sources d'origine.

L'identifiant interne `wormholes` est conservé pour les données et le protocole. Ne pas installer Wormholes et cette version simultanément.

## Mise à jour 3.1.0-kb.2 : minecarts

- Les rails droits existants sont acceptés au bas de l'ouverture créée par `/portail creer`.
- Alignement du wagon sur le rail de destination, rotation de sa vitesse et conservation de son identité/inventaire par la téléportation native.
- Immobilisation pendant le chargement de destination et refus des mouvements de véhicule du client pendant cette attente.
- Freinage si le rail de destination est absent, mal orienté ou obstrué.
- Historique de position du wagon entre les ticks pour les franchissements pilotés par le client.
- Resynchronisation du wagon avant ses passagers après le changement de dimension.
- Départ rapide autorisé après sortie complète de l'ouverture, avec maintien de la protection géométrique contre les rebonds.
- Vues et rails supportés : portails verticaux droits, rails horizontaux rectilignes au seuil. Les courbes/pentes au seuil, portails couchés et changements de gravité/taille ne font pas partie de ce raccord sécurisé.

Les essais et instructions spécifiques sont fournis dans la livraison minecarts.

## Provenance du moteur

### Correctif kb.3 : vrais rails dans les portails

Les objets de rail contournent désormais `MinecraftPortalSurfaces.interact`, qui les traitait auparavant comme des décorations. La pose par clic droit atteint ainsi la mécanique vanilla et crée un vrai bloc de rail. Les anciennes surfaces de rail sont retirées lors du chargement, sans changer les liens. Les rails physiques doivent être reposés sur le seuil. Le test `KBNetherRailClientGameTest` allume un cadre au briquet, pose les rails par l'interaction du joueur et vérifie les traversées avec coffre et joueur assis, en solo et sur serveur dédié.

- Wormholes : https://github.com/VolmitSoftware/Wormholes — commit `6edf02bc427d079b1a2b3a64b4f1672ddf9bf56e`.
- Optics : https://github.com/VolmitSoftware/Optics — commit `6dbb91a4d79e0ccca34df9bf02c351cf1c0efc57`.
- VolmLib : https://github.com/VolmitSoftware/VolmLib — commit `db2fd466ebdb757ce1ebf3fb318a5c8560b71ad8`.

VolmLib doit être extrait à côté du dossier de ce projet pour que la compilation réutilise les sources des modules `localization` et `web` sans dépendre de leurs versions SNAPSHOT publiées.

## Compilation

Avec un JDK 25 dans `JAVA_HOME`, lancer `COMPILER.ps1`. Pour les tests des ajouts : `./gradlew.bat -p adapters/fabric :test --tests '*ImmersivePortalCommandsTest' --configure-on-demand`.

Pour toute la suite : `./gradlew.bat -p adapters/fabric build --configure-on-demand`. Les résultats et limites observés sont fournis dans la livraison. La création du JAR et la réussite de la totalité des tests sont deux résultats distincts.

Les outils de test de `qa/` et `src/test/client/` ne sont jamais intégrés au JAR distribué.
