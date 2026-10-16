# Commandes françaises et anglaises — 3.1.0-bilingual.1

Les deux langues sont disponibles en même temps, en solo et sur serveur. Les commandes utilisent les mêmes portails, liens, permissions et sauvegardes. Un portail créé dans une langue peut être géré dans l'autre.

| Français | English |
|---|---|
| `/portail` ou `/portail aide` | `/portal` or `/portal help` |
| `/portail creer <nom> [largeur hauteur]` | `/portal create <name> [width height]` |
| `/portail relier <premier> <second>` | `/portal link <first> <second>` |
| `/portail liste` | `/portal list` |
| `/portail supprimer <nom>` | `/portal delete <name>` |
| `/portail avance ...` | `/portal advanced ...` |

L'aide, les confirmations et les erreurs de ces raccourcis suivent la commande utilisée : français pour `/portail`, anglais pour `/portal`, quelle que soit la langue du jeu. Les noms des portails restent ceux choisis par les joueurs. Les commandes avancées redirigent vers le moteur `/wormholes` et conservent ses propres messages.

Messages and argument labels are in English under `/portal`, and in French under `/portail`, regardless of the Minecraft language setting. Portal names are never translated. Advanced commands redirect to `/wormholes` and retain the engine's own messages.

Taille par défaut : 2 × 3 blocs. Largeur : 1–16 ; hauteur : 2–16. Les noms utilisent 1 à 32 lettres ASCII, chiffres, tirets ou underscores. L'ouverture se crée trois blocs devant le joueur et doit être libre, à l'exception des rails droits alignés acceptés au seuil.

Default size: 2 × 3 blocks. Width: 1–16; height: 2–16. Names use 1–32 ASCII letters, digits, hyphens or underscores. The opening is created three blocks ahead of the player and must be clear, except for supported aligned straight rails at its base.

## Installation / Updating

Minecraft Java 26.3, Fabric Loader 0.19.5+, Java 25 et Fabric API (version de référence : 0.163.0+26.3). Remplacer l'ancien JAR du mod dans `mods`, sans conserver les deux versions. Sur serveur, installer la même version sur chaque client et sur le serveur. Ne pas installer Wormholes séparément : l'identifiant interne `wormholes` est conservé pour les sauvegardes et le protocole.

Replace the old mod JAR in `mods`; keep only one version. Install the same version on the server and all clients. Do not install standalone Wormholes alongside this fork: the internal `wormholes` identifier is retained for saved data and networking.

Ce changement porte sur les commandes et leurs messages. Les limitations de rendu, de shaders et de wagonnets documentées pour la version précédente restent applicables ; aucun nouveau scénario graphique n'est certifié par cette mise à jour.
