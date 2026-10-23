# Validation de fabrication

Vérifications effectuées dans l'environnement cloud, le 23 octobre 2026 :

- `python3 scripts/build_cloud.py` : réussite de la fabrication debug, de Lint et des tests.
- 12 tests exécutés : 4 tests de politique de présence/sommeil, 7 tests de parcours Android avec Robolectric, 1 test HTTP de présence contre un serveur local. Aucun échec ni test ignoré.
- Tests de confidentialité : démarrage verrouillé sans demande micro, masquage de la WebView, mode invité, fermeture des réglages en arrière-plan, serveur sans réponse, expiration de session et validation des adresses.
- Test HTTP : JSON et jeton présents, changements de présence true/false, doublons supprimés, poursuite après 404, redirections non suivies. Il s'agit d'un serveur de test local, pas du serveur domestique.
- Empreintes SHA-256 des deux modèles conformes aux pointeurs Git LFS officiels. Les deux fichiers exacts sont présents dans l'APK final.
- Essai des modèles avec OpenCV 4.10 sur Linux : une image d'exemple publique OpenCV produit un visage et une signature de 128 nombres ; autocorrespondance > 0.99 ; une image vide ne donne aucun visage. Cela valide les poids et leur compatibilité OpenCV, pas la caméra Android.
- `apksigner verify --verbose` : signature APK v2 valide.
- APK universel debug d'environ 93 Mio. Les modèles hors ligne et les bibliothèques pour plusieurs architectures expliquent sa taille. Aucun téléchargement de modèle n'a lieu sur la tablette.

Lint ne signale aucune erreur. Ses avertissements concernent JavaScript nécessaire à la page domestique, des textes volontairement français et l'effacement synchrone de la signature avant confirmation à l'utilisateur.

Non validé dans cette machine : la caméra physique de la tablette, son orientation et sa consommation, le stockage Android Keystore sur ce matériel, les performances réelles de reconnaissance et le serveur LAN `192.168.1.26`. Aucun appareil Android ou émulateur accéléré n'est disponible. Suivre [le parcours sur tablette](TEST-TABLETTE.md) après installation.
