# SPDX-License-Identifier: Apache-2.0
"""Exercise Python encoder against the production Java packet decoder."""
from pathlib import Path
import sys
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from curve_bridge import write_chat
with open(sys.argv[1], 'wb') as stream:
    write_chat(stream, {'id': 'fixture-chat', 'title': 'Grüße äöü', 'cwd': '/tmp/projekt',
        'writable': True, 'cursor': 'older-cursor', 'messages': [
            {'id': 'a1', 'role': 'codex', 'text': 'Codex: Text\nDu: bleibt derselbe Absender 😀', 'reply': '', 'truncated': False},
            {'id': 'u1', 'role': 'user', 'text': 'Meine Antwort', 'reply': 'Zitat äöü', 'truncated': True}]})
