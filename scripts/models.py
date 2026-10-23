#!/usr/bin/env python3
"""Fetch pinned OpenCV Zoo models for packaging, never at application runtime."""
import hashlib
from pathlib import Path
import urllib.request
import argparse

REF = '48b10fa505aeb51fbd127fc60a5c660040e823b7'
MODELS = {
    'yunet.onnx': ('face_detection_yunet/face_detection_yunet_2023mar.onnx', '8f2383e4dd3cfbb4553ea8718107fc0423210dc964f9f4280604804ed2552fa4'),
    'sface.onnx': ('face_recognition_sface/face_recognition_sface_2021dec.onnx', '0ba9fbfa01b5270c96627c4ef784da859931e02f04419c829e83484087c34e79'),
}

def main():
    parser=argparse.ArgumentParser()
    parser.add_argument('--verify', action='store_true')
    args=parser.parse_args()
    root=Path(__file__).resolve().parents[1]/'app/src/main/assets/models'
    root.mkdir(parents=True,exist_ok=True)
    for name,(source,digest) in MODELS.items():
        dest=root/name
        if not dest.exists() and not args.verify:
            url=f'https://media.githubusercontent.com/media/opencv/opencv_zoo/{REF}/models/{source}'
            with urllib.request.urlopen(url,timeout=90) as response:
                content=response.read()
            if hashlib.sha256(content).hexdigest()!=digest:
                raise SystemExit(f'Checksum incorrect: {name}')
            dest.write_bytes(content)
        if not dest.exists() or hashlib.sha256(dest.read_bytes()).hexdigest()!=digest:
            raise SystemExit(f'Modèle absent ou corrompu : {name}. Exécuter python3 scripts/models.py')
        print(f'{name}: SHA-256 OK')

if __name__=='__main__':
    main()
