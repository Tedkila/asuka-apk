# Vie privée et limites

## Ce qui reste sur la tablette

- Caméra avant uniquement, en premier plan seulement. Les pixels YUV/BGR restent dans la mémoire vive. Aucun fichier photo/vidéo, aperçu sauvegardé, journal biométrique ou upload d'image n'est créé.
- Cinq signatures cohérentes sont réunies pour le seul enregistrement de Teddy. La signature moyenne SFace (128 nombres) est chiffrée en AES-256/GCM, clé dans Android Keystore, dans le stockage privé. La sauvegarde et le transfert Android sont exclus. « Effacer mon visage » retire la signature ; réenregistrer demande d'abord de l'effacer.
- Le code est dérivé par PBKDF2-HMAC-SHA256 avec sel aléatoire, 120 000 itérations. Le jeton de présence est chiffré avec Android Keystore. Pas de code préconfiguré, pas d'export de signature ni de récupération distante.
- Le voyant vert « Caméra locale » signifie que la caméra fonctionne. Android 12+ peut aussi afficher son propre indicateur. Le mode invité et les heures de sommeil ferment réellement la caméra. Quitter l'application la ferme aussi et masque immédiatement la page.
- La WebView n'obtient jamais les permissions caméra, micro ou localisation. Aucun pont JavaScript natif n'est exposé. Le micro est déclaré pour une évolution future, jamais demandé ou ouvert.

## Présence envoyée uniquement à la maison

Après configuration du jeton, chaque changement d'état produit un POST à `/api/presence` sur le serveur configuré, sans suivre les redirections. Exemple de contrat :

```json
{"appareil":"asuka-identifiant-aleatoire","piece":"salle de bain","teddy":true,"heure":"2026-10-23T20:00:00Z","etat":"teddy"}
```

En-tête `Authorization: Bearer <jeton>`. `teddy` est un booléen JSON ; l'heure est en UTC ISO 8601. Les états supplémentaires comprennent `standby`, `unknown`, `sleep`, `guest`, `locked`, `fault`, `enrolling`. Aucune photo, signature ou score n'est transmis. Sans jeton, aucun POST n'est fait. Une erreur HTTP (dont 404), une panne et un délai d'attente sont ignorés. Pas de file persistante ni de rejeu d'événements anciens. Les demandes en attente sont bornées.

Le code peut également déverrouiller la page : `teddy: true` signifie donc « session Teddy autorisée par visage ou code ». Le service domestique doit vérifier le jeton. Sur HTTP, les échanges et le jeton ne sont pas chiffrés sur le Wi-Fi : utilise uniquement le réseau de confiance de la maison. Seule l'IP `192.168.1.26` autorise HTTP ; une autre adresse requiert HTTPS avec un certificat système valide.

## Veille et économie

Écran maintenu éveillé en premier plan ; écran noir, luminosité minimale Android (le minimum physique dépend de la tablette). Traitement à environ 2 images/seconde, image de 640 pixels maximum. Le plus petit format caméra entre 320 et 640 pixels est privilégié, ainsi que la cadence matérielle disponible la plus basse. Différence d'images miniatures pour déclencher YuNet ; sondage toutes les 5 secondes pour une personne déjà immobile. Deux correspondances SFace successives sont exigées.

Une fois Teddy autorisé, SFace est en pause. YuNet vérifie seulement la présence d'un visage toutes les 5 secondes. Après 3 minutes sans visage (réglable), la page est détruite et l'écran redevient noir. Plusieurs visages masquent la page. Cette économie implique qu'un remplacement immédiat d'une personne par une autre sans période d'absence peut ne pas être détecté : ce n'est pas un verrou de haute sécurité. Le code est disponible pour un réveil manuel, y compris pendant le sommeil ; la caméra reste alors coupée et la session expire automatiquement.

## Limites de la reconnaissance

YuNet/SFace ne fournit pas de preuve de vie : une photo de Teddy peut tromper cette reconnaissance. Éclairage, angle, buée et reflets peuvent aussi provoquer des erreurs. Le seuil initial de cosinus est 0.50 (réglable 0.30–0.90) ; l'augmenter réduit les acceptations et peut augmenter les refus. Pour une confidentialité stricte, utilise le mode invité et le code. Cette application ne remplace pas le verrouillage Android.

Aucun service distant, SDK publicitaire ou de télémétrie n'est intégré à l'application. Le contenu fourni par le serveur domestique relève de ce serveur ; les navigations et requêtes Web HTTP(S) sortantes sont filtrées à l'origine configurée. Le serveur domestique doit lui aussi éviter les scripts et services de suivi.
