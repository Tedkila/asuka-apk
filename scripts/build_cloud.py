#!/usr/bin/env python3
"""Use the retained cloud toolchain; Android Studio/CI can use gradlew directly."""
import os
from pathlib import Path
import subprocess
import sys
from urllib.parse import urlparse

root = Path(__file__).resolve().parents[1]
os.chdir(root)
environment = os.environ.copy()
tools = root / '.tools'
local_jdk = tools / 'jdk/usr/lib/jvm/msopenjdk-17-amd64'
if local_jdk.exists():
    environment['JAVA_HOME'] = str(local_jdk)
if (tools / 'android-sdk').exists():
    environment['ANDROID_HOME'] = str(tools / 'android-sdk')
    environment['ANDROID_SDK_ROOT'] = str(tools / 'android-sdk')
environment['ANDROID_USER_HOME'] = str(tools / 'android-user')
environment['GRADLE_USER_HOME'] = str(tools / 'gradle-home')
(tools / 'home').mkdir(parents=True, exist_ok=True)
args = [f'-Duser.home={tools / "home"}']
for scheme in ('http', 'https'):
    proxy = urlparse(environment.get(scheme.upper() + '_PROXY', ''))
    if proxy.hostname:
        args += [f'-D{scheme}.proxyHost={proxy.hostname}', f'-D{scheme}.proxyPort={proxy.port or 80}']
if (tools / 'cacerts').exists():
    args += [f'-Djavax.net.ssl.trustStore={tools / "cacerts"}', '-Djavax.net.ssl.trustStorePassword=changeit']
# This is the standard public trust-store password, not an application secret.
gradle = tools / 'gradle-8.9/bin/gradle'
if not gradle.exists():
    gradle = root / 'gradlew'
subprocess.run([sys.executable, 'scripts/models.py'], check=True, env=environment)
subprocess.run([str(gradle), *args, '--console=plain', 'testDebugUnitTest', 'lintDebug', 'assembleDebug'], check=True, env=environment)
