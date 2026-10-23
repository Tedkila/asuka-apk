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

Java 17, Android SDK 35, Gradle 8.9 (wrapper avec contrôle SHA-256), Android Gradle Plugin 8.7.3. `./gradlew testDebugUnitTest lintDebug assembleDebug`. GitHub Actions construit un APK de débogage à chaque push et pour chaque pull request. Un APK de débogage convient à l'installation manuelle, pas à une publication Play Store.

## Effacer

**Paramètres Android → Applications → Asuka → Stockage → Effacer les données** remet l'application à zéro. **Désinstaller** supprime l'application et son stockage privé. Aucun visage n'est sauvegardé dans le cloud. Le serveur de la maison peut garder ses propres données de présence : la désinstallation ne les efface pas.

## État de la première base

Plein écran, serveur local, écran Réessayer, réglages protégés et icône cœur. Le serveur domestique ne peut pas être joint depuis la machine de fabrication : sa réponse réelle doit être vérifiée sur place. Les fonctions de caméra/reconnaissance sont ajoutées dans l'étape suivante.
