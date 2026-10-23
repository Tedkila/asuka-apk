# Vérifications sur la tablette

Ces essais demandent la vraie tablette et le réseau domestique. Ils ne sont pas remplacés par les tests automatisés de fabrication.

1. Installer l'APK, choisir deux fois un code personnel. Vérifier que le micro n'est pas demandé et que la page privée n'apparaît pas avant le code/visage.
2. « C'est moi » + code ouvre le serveur de la maison. Couper le Wi-Fi : attendre environ 18 secondes, vérifier le message prévu et le bouton Réessayer. Rétablir le Wi-Fi, réessayer.
3. Appui long en haut à droite : un mauvais code refuse les réglages ; cinq tentatives déclenchent une minute d'attente. Le bon code ouvre les réglages.
4. Enregistrer le visage, seul et bien éclairé, après autorisation caméra. Vérifier la confirmation et le voyant. Aucun micro ne doit être actif.
5. Partir plus de trois minutes : écran noir au minimum de luminosité. Revenir : Teddy réveille la page progressivement. Une autre personne voit seulement Bonjour et le bouton C'est moi. Tester aussi les lunettes et l'éclairage réel.
6. Activer le mode invité avec le code, enregistrer : page masquée, caméra coupée et voyant éteint. Revenir à l'application ne réactive pas la caméra. Désactiver ce mode pour reprendre.
7. Placer une plage de sommeil contenant l'heure actuelle : caméra coupée. L'ouverture par code reste possible sans caméra. Tester aussi le passage à l'heure de début et de fin, dont une plage traversant minuit.
8. Ouvrir les réglages, appuyer sur Accueil, revenir : les réglages doivent être fermés et la page personnelle masquée. Tourner la tablette : la caméra doit être réinitialisée et l'affichage privé masqué.
9. Configurer le jeton de la maison : vérifier côté serveur le POST décrit dans PRIVACY.md. Tester une réponse 404, une panne et un mauvais jeton : l'application doit continuer sans plantage. Un jeton vide au premier lancement désactive les POST.
10. Effacer le visage : aucune reconnaissance automatique ensuite ; le code fonctionne toujours. Désinstaller puis réinstaller : code, visage, pièce et jeton sont réinitialisés.

Le serveur HTTP doit rester exclusivement sur le Wi-Fi de confiance. Le jeton et les données de présence circulent en clair avec HTTP. Une image de Teddy peut tromper SFace : il ne s'agit pas d'une preuve de vie.
