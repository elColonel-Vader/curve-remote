#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""Fetch and checksum the three declared build jars; BlackBerry SDK is user-supplied."""
import hashlib
import json
from pathlib import Path
import urllib.request

ROOT = Path(__file__).resolve().parents[1]

def main():
    manifest = json.loads((ROOT/'app/dependencies.json').read_text())
    for item in manifest['artifacts']:
        target = ROOT/'app'/item['path']
        if target.exists() and hashlib.sha256(target.read_bytes()).hexdigest()==item['sha256']:
            continue
        with urllib.request.urlopen(item['url'],timeout=60) as response:
            content=response.read(20*1024*1024+1)
        if hashlib.sha256(content).hexdigest()!=item['sha256']:
            raise ValueError('Checksum mismatch for '+item['path'])
        target.parent.mkdir(parents=True,exist_ok=True)
        target.write_bytes(content)
        print('Verified '+item['path'])

if __name__=='__main__':
    main()
