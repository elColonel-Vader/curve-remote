#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""Generate a private, per-install pairing credential for the bridge and MIDlet."""
from pathlib import Path
import re
import secrets

ROOT = Path(__file__).resolve().parents[1]

def main():
    private = ROOT / 'diagnostics/private'
    private.mkdir(parents=True, exist_ok=True)
    token_path = private / 'pairing-token'
    if token_path.exists():
        token = token_path.read_text().strip()
        if not re.fullmatch(r'[0-9a-f]{64}', token):
            raise ValueError('Existing token must be 64 hexadecimal characters; not replacing it')
    else:
        token = secrets.token_hex(32)
        token_path.touch(mode=0o600, exist_ok=False)
        token_path.write_text(token+'\n')
    token_path.chmod(0o600)
    target = ROOT / 'app/src/Pairing.java'
    target.write_text('public final class Pairing { public static final String TOKEN="'+token+'"; }\n')
    target.chmod(0o600)
    print('Private pairing credential prepared. Rebuild the handset client after setup.')

if __name__ == '__main__':
    main()
