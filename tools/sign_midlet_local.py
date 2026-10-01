#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""Create a locally signed MIDP JAD for inspection, not trusted BlackBerry COD signing.

The private development key stays in diagnostics/private and is never printed.
USB COD loading does not consume this JAD. Device acceptance requires a root
certificate assigned to a MIDP protection domain and a compatible installer.
"""
import base64
import os
from pathlib import Path
import subprocess
import zipfile

ROOT = Path(__file__).resolve().parent.parent
PRIVATE = ROOT / 'diagnostics/private/midlet-signing'
JAR = ROOT / 'app/out/CurveProbe.jar'


def run(*args):
    return subprocess.run(args, check=True, capture_output=True).stdout


def main():
    os.umask(0o077)
    PRIVATE.mkdir(parents=True, exist_ok=True)
    key, cert = PRIVATE / 'development-key.pem', PRIVATE / 'development-cert.pem'
    if key.exists() != cert.exists():
        raise RuntimeError('Incomplete signing identity; refusing to replace its files')
    if not key.exists():
        run('openssl', 'req', '-x509', '-newkey', 'rsa:2048', '-nodes', '-sha1',
            '-days', '365', '-subj', '/CN=Curve Remote Local Development/',
            '-addext', 'keyUsage=digitalSignature', '-addext', 'extendedKeyUsage=codeSigning',
            '-keyout', str(key), '-out', str(cert))
    signature = PRIVATE / 'jar-signature.bin'
    run('openssl', 'dgst', '-sha1', '-sign', str(key), '-out', str(signature), str(JAR))
    public = PRIVATE / 'development-public.pem'
    public.write_bytes(run('openssl', 'x509', '-in', str(cert), '-pubkey', '-noout'))
    run('openssl', 'dgst', '-sha1', '-verify', str(public), '-signature', str(signature), str(JAR))
    der = run('openssl', 'x509', '-in', str(cert), '-outform', 'DER')
    with zipfile.ZipFile(JAR) as archive:
        lines = archive.read('META-INF/MANIFEST.MF').decode('utf-8').splitlines()
    # Keep the MIDlet attributes byte-equivalent to the manifest values.
    attributes = [line for line in lines if line.startswith(('MIDlet-', 'MicroEdition-'))]
    attributes += ['MIDlet-Jar-URL: CurveProbe.jar', 'MIDlet-Jar-Size: ' + str(JAR.stat().st_size),
                   'MIDlet-Certificate-1-1: ' + base64.b64encode(der).decode('ascii'),
                   'MIDlet-Jar-RSA-SHA1: ' + base64.b64encode(signature.read_bytes()).decode('ascii')]
    target = ROOT / 'app/out/CurveProbe-local-signed.jad'
    target.write_text('\n'.join(attributes) + '\n', encoding='utf-8')
    print('PASS: RSA/SHA-1 MIDP JAD created; JAR signature verified with certificate public key')
    print('Device trust NOT established; installed COD remains unsigned')


if __name__ == '__main__':
    main()
