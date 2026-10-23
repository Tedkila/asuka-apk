# Asuka — fenêtre sur la maison

Application Android native, Android 8.0 ou plus récent. Serveur initial : `http://192.168.1.26:8765/`. Aucun abonnement, publicité, suivi ou compte en ligne dans l'application.

## Installer sur la tablette

1. Sur GitHub, ouvre ce dépôt privé, puis **Actions** et **APK Asuka**.
2. Ouvre la dernière exécution verte. En bas, dans **Artifacts**, télécharge **Asuka-APK-debug** (il faut être connecté à GitHub).
3. Ouvre le ZIP puis le fichier `app-debug.apk` sur la tablette. Android peut demander d'autoriser cette installation : **Paramètres → Autoriser depuis cette source** pour le navigateur ou le gestionnaire de fichiers. Cette autorisation peut être retirée après l'installation.
4. Appuie sur **Installer**, puis ouvre **Asuka**, sur le Wi-Fi de la maison.

Au premier lancement, choisis ton propre code de 6 à 12 chiffres. Il n'existe aucun code par défaut et aucune récupération à distance. Pour les réglages, garde le doigt environ une seconde sur le coin supérieur droit (⚙), puis entre ton code.

L'adresse HTTP en clair est autorisée uniquement pour `192.168.1.26`. Une autre adresse doit utiliser HTTPS avec un certificat reconnu par Android. Les pages et ressources Web sont limitées à l'origine configurée ; les liens externes sont bloqués. Le micro n'est pas activé et les demandes micro/caméra provenant de la page sont refusées.

## Fabrication

Java 17, Android SDK 35, Gradle 8.9 (wrapper avec contrôle SHA-256), Android Gradle Plugin 8.7.3. `python3 scripts/models.py`, puis `./gradlew testDebugUnitTest lintDebug assembleDebug`. GitHub Actions construit un APK de débogage à chaque push et pour chaque pull request. Un APK de débogage convient à l'installation manuelle, pas à une publication Play Store.

## Effacer

**Paramètres Android → Applications → Asuka → Stockage → Effacer les données** remet l'application à zéro. **Désinstaller** supprime l'application et son stockage privé. Aucun visage n'est sauvegardé dans le cloud. Le serveur de la maison peut garder ses propres données de présence : la désinstallation ne les efface pas.

## Enregistrer ton visage

1. Garde le doigt sur le coin supérieur droit (⚙), puis entre ton code.
2. Dans les réglages, touche **Enregistrer le visage de Teddy** et accepte l'autorisation caméra. Le micro n'est pas demandé.
3. Reste seul, bien éclairé, face à la tablette pendant quelques secondes : l'application réunit cinq signatures puis confirme l'enregistrement. Elle ne garde aucune photo. Si l'heure est dans la plage de sommeil, change d'abord temporairement ces horaires ; désactive aussi le mode invité.
4. Par la suite, un mouvement réveille la recherche de visage. Si Teddy est reconnu deux fois, Asuka apparaît progressivement. Sinon l'écran dit **Bonjour !** ; **C'est moi**, suivi du code, permet d'ouvrir Asuka manuellement.

Pour effacer la signature : réglages avec code → **Effacer mon visage**. Pour l'enregistrer à nouveau, efface d'abord l'ancienne signature.

## Mode invité et horaires

Appui long en haut à droite → code → active **Mode invité** → **Enregistrer**. La caméra se coupe et la page privée est masquée. Pour revenir au fonctionnement habituel, désactive ce même réglage avec ton code.

La caméra est coupée entre **01:30 et 09:00**, selon l'heure locale de la tablette. Ces heures sont modifiables ; choisir deux heures identiques désactive le sommeil. Le retour à l'écran noir se fait après **3 minutes sans visage**, réglable de 1 à 60 minutes. Le petit voyant vert signifie que la caméra fonctionne. Si tu refuses l'autorisation caméra, tu peux toujours ouvrir la page avec ton code.

Dans les réglages, colle le **jeton de la maison** si le serveur propose l'API de présence. Tu peux laisser ce champ vide pour garder le jeton existant. Un serveur indisponible ou une réponse 404 ne bloque pas l'application. Aucun secret ne doit être mis dans le dépôt GitHub.

## À savoir

La reconnaissance doit être essayée sur la vraie tablette, avec la lumière de la salle de bain. Elle peut se tromper et n'empêche pas l'utilisation d'une photo de Teddy : pour un accès strict, garde le mode invité et utilise ton code. [Vie privée, économie et contrat de présence](docs/PRIVACY.md). [Origine et licences des modèles](docs/MODELS.md).

L'APK de base initial est conservé dans le premier historique Git ; la version courante ajoute les fonctions locales. La machine de fabrication ne peut pas joindre le serveur domestique : l'affichage de la maison et les réponses de son API doivent être vérifiés sur place.

L’APK universel complet fait environ 93 Mio, car il contient les modèles hors ligne et plusieurs architectures Android. La base WebView seule était beaucoup plus petite.

[Contrôles effectués](docs/VALIDATION.md) · [Essais à faire sur la tablette](docs/TEST-TABLETTE.md).

Les APK GitHub de débogage conservent leur clé de test via le cache Actions. L’APK fabriqué dans le cloud et celui de GitHub Actions peuvent également avoir des clés différentes. Si le cache expire ou si la clé diffère, Android peut refuser une mise à jour : il faudra désinstaller l'ancienne version puis réinstaller (cela efface les réglages et le visage). Ne diffuse pas cette version comme une application de production.
