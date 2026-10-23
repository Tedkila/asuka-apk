# Modèles embarqués et licences

Sources officielles : [OpenCV Zoo](https://github.com/opencv/opencv_zoo), révision figée `48b10fa505aeb51fbd127fc60a5c660040e823b7`.

| Modèle | Fichier officiel | SHA-256 | Licence |
| --- | --- | --- | --- |
| YuNet | `face_detection_yunet_2023mar.onnx` | `8f2383e4dd3cfbb4553ea8718107fc0423210dc964f9f4280604804ed2552fa4` | MIT, [texte](licenses/YuNet-LICENSE) |
| SFace | `face_recognition_sface_2021dec.onnx` | `0ba9fbfa01b5270c96627c4ef784da859931e02f04419c829e83484087c34e79` | Apache 2.0, [texte](licenses/SFace-LICENSE) |

Les empreintes proviennent des pointeurs Git LFS de la révision officielle. `python3 scripts/models.py` télécharge les poids officiels et vérifie leur SHA-256 **avant** de les écrire dans les assets. La fabrication GitHub Actions les inclut dans l'APK : l'application ne télécharge jamais de modèles à l'exécution. Les poids ne sont pas enregistrés comme gros fichiers Git ; le script, les empreintes et les licences sont versionnés.

OpenCV Android 4.10.0 est utilisé via Maven Central (`org.opencv:opencv`), sous licence Apache 2.0. Les avis de licence des dépendances sont conservés dans les packages distribués. SFace pèse environ 37 Mo ; l'APK complet est donc plus volumineux que la première WebView seule.
