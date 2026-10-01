# SPDX-License-Identifier: Apache-2.0
"""Documented app-server client; existing desktop conversations are read-only."""
import json
from collections import deque
import os
from pathlib import Path
import queue
import shutil
import subprocess
import threading
import time

ROOT = Path(__file__).resolve().parents[1]
SOURCES = ['cli', 'vscode', 'appServer', 'exec', 'unknown']


class AppServer:
    def __init__(self):
        executable = shutil.which('codex')
        if not executable:
            raise RuntimeError('Codex CLI fehlt.')
        self.process = subprocess.Popen(
            [executable, 'app-server', '-c', 'mcp_servers={}',
             '-c', 'sandbox_mode="read-only"', '-c', 'approval_policy="never"'],
            stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.DEVNULL,
            text=True, bufsize=1)
        self.messages = queue.Queue(maxsize=4096)
        self.sequence = 0
        self.notifications = deque()
        threading.Thread(target=self._read, daemon=True).start()
        self.call('initialize', {'clientInfo': {'name': 'curve_remote',
                   'title': 'Curve Remote', 'version': '0.5.0'}})
        self.send({'method': 'initialized'})

    def _read(self):
        try:
            for line in self.process.stdout:
                self.messages.put(json.loads(line))
        finally:
            self.messages.put({'bridgeDisconnected': True})

    def send(self, message):
        try:
            self.process.stdin.write(json.dumps(message) + '\n')
            self.process.stdin.flush()
        except (OSError, ValueError) as error:
            raise RuntimeError('Codex-Verbindung unterbrochen. Bridge neu starten.') from error

    def receive(self, deadline, include_notifications=True):
        if include_notifications and self.notifications:
            return self.notifications.popleft()
        try:
            message = self.messages.get(timeout=max(0.01, deadline - time.monotonic()))
        except queue.Empty as error:
            raise TimeoutError('Codex-Zeitlimit erreicht.') from error
        if message.get('bridgeDisconnected'):
            raise RuntimeError('Codex-App-Server beendet. Bridge neu starten.')
        # Never approve execution, edits, or connector requests from the handset.
        if 'method' in message and 'id' in message:
            self.send({'id': message['id'], 'error': {'code': -32601,
                       'message': 'Curve Remote does not support approval requests'}})
        return message

    def call(self, method, params=None):
        self.sequence += 1
        ident = self.sequence
        self.send({'id': ident, 'method': method, 'params': params or {}})
        deadline = time.monotonic() + 30
        while True:
            message = self.receive(deadline, include_notifications=False)
            if message.get("method") in ("item/completed", "turn/completed") and "id" not in message:
                self.notifications.append(message)
            if message.get('id') == ident and 'method' not in message:
                if 'error' in message:
                    raise RuntimeError(message['error'].get('message', 'Codex-Fehler'))
                return message['result']

    def answer(self, thread_id, prompt):
        result = self.call('turn/start', {'threadId': thread_id,
            'input': [{'type': 'text', 'text': prompt}], 'approvalPolicy': 'never',
            'sandboxPolicy': {'type': 'readOnly'}})
        turn_id = result['turn']['id']
        deadline = time.monotonic() + 150
        answers = []
        try:
            while True:
                message = self.receive(deadline)
                params = message.get('params', {})
                if params.get('threadId') != thread_id or params.get('turnId', turn_id) != turn_id:
                    continue
                if message.get('method') == 'item/completed':
                    item = params.get('item', {})
                    if item.get('type') == 'agentMessage':
                        answers.append(item.get('text', ''))
                if message.get('method') == 'turn/completed' and params['turn']['id'] == turn_id:
                    turn = params['turn']
                    if turn['status'] != 'completed':
                        raise RuntimeError((turn.get('error') or {}).get('message', 'Codex-Anfrage abgebrochen.'))
                    if not answers:
                        raise RuntimeError('Keine Textantwort von Codex.')
                    return '\n\n'.join(answers)
        except TimeoutError:
            self.call('turn/interrupt', {'threadId': thread_id, 'turnId': turn_id})
            raise

    def close(self):
        self.process.terminate()
        try:
            self.process.wait(timeout=5)
        except subprocess.TimeoutExpired:
            self.process.kill()
            self.process.wait()
        self.process.stdin.close()
        self.process.stdout.close()


def clean(value, limit=160):
    return str(value or '').replace('\t', ' ').replace('\r', ' ').replace('\n', ' ')[:limit]


class Sessions:
    def __init__(self, rpc, state_path=None):
        self.rpc = rpc
        self.state_path = state_path or ROOT / 'diagnostics/private/curve-sessions.json'
        self.state = json.loads(self.state_path.read_text()) if self.state_path.exists() else {'owned': [], 'default': ''}

    def save(self):
        temporary = self.state_path.with_suffix('.tmp')
        fd = os.open(temporary, os.O_WRONLY | os.O_CREAT | os.O_TRUNC, 0o600)
        with os.fdopen(fd, 'w') as stream:
            json.dump(self.state, stream)
        os.replace(temporary, self.state_path)

    def list_page(self, cursor='', limit=20):
        params = {'limit': limit, 'sourceKinds': SOURCES, 'sortKey': 'updated_at'}
        if cursor:
            params['cursor'] = cursor
        return self.rpc.call('thread/list', params)

    def projects(self):
        paths = {str(ROOT)}
        cursor = ''
        for _ in range(20):
            page = self.list_page(cursor, 100)
            for thread in page['data']:
                path = thread.get('cwd')
                if path and Path(path).is_dir():
                    paths.add(path)
            cursor = page.get('nextCursor')
            if not cursor:
                break
        return sorted(paths)

    def create(self, cwd, title):
        cwd = cwd or str(ROOT)
        if cwd not in self.projects():
            raise ValueError('Projektordner nicht in der Projektauswahl.')
        thread = self.rpc.call('thread/start', {'cwd': cwd, 'sandbox': 'read-only',
            'approvalPolicy': 'never', 'ephemeral': False,
            'developerInstructions': 'Antworte auf Deutsch, kurz und als einfacher Text für ein BlackBerry Curve. Verwende Werkzeuge nur wenn die Anfrage es erfordert.'})['thread']
        ident = thread['id']
        # Record ownership before any later operation that could fail.
        self.state['owned'].append(ident)
        if title in ('', 'Neuer Chat', 'BlackBerry Curve Chat'):
            self.state.setdefault('auto_titles', []).append(ident)
        self.state['default'] = ident
        self.save()
        self.rpc.call('thread/name/set', {'threadId': ident, 'name': clean(title or 'BlackBerry Curve Chat', 80)})
        return ident

    def dispatch(self, operation, target='', text=''):
        if operation == 'LIST':
            page = self.list_page(target)
            rows = []
            for t in page['data']:
                label = t.get('name') or t.get('preview') or 'Unbenannter Chat'
                kind = 'Curve' if t['id'] in self.state['owned'] else 'Lesen'
                rows.append(t['id'] + '\t' + clean(label, 100) + '\t' + clean(t.get('cwd'), 512) + '\t' + kind)
            rows.append('@next\t' + (page.get('nextCursor') or ''))
            return '\n'.join(rows), ''
        if operation == 'PROJECTS':
            return '\n'.join(self.projects()), ''
        if operation == 'NEW':
            ident = self.create(target, text)
            return 'Gespeicherter Curve-Chat erstellt. Du kannst jetzt eine Anfrage senden.', ident
        if operation == 'READ':
            thread = self.rpc.call('thread/read', {'threadId': target, 'includeTurns': True})['thread']
            entries = []
            for turn in thread.get('turns', []):
                for item in turn.get('items', []):
                    if item.get('type') == 'userMessage':
                        value = '\n'.join(c.get('text', '') for c in item.get('content', []) if c.get('type') == 'text')
                        entries.append('Du: ' + value)
                    elif item.get('type') == 'agentMessage':
                        entries.append('Codex: ' + item.get('text', ''))
            history = '\n\n'.join(entries) or 'Dieser Chat hat noch keine Nachrichten.'
            # Keep the latest messages on the small display, within wire limits.
            history = history.encode('utf-8')[-22000:].decode('utf-8', errors='ignore')
            mode = 'Curve-Chat: Antworten moeglich.' if target in self.state['owned'] else 'Desktop-Chat: hier momentan nur lesbar.'
            return mode + '\n\n' + history, target
        if operation == 'SEND':
            if not text.strip():
                raise ValueError('Bitte eine Anfrage eingeben.')
            ident = target or self.state['default']
            if not ident:
                ident = self.create(str(ROOT), 'BlackBerry Curve Chat')
            if ident not in self.state['owned']:
                raise ValueError('Desktop-Chat nur lesbar. Erstelle einen neuen Curve-Chat im gewünschten Projekt.')
            thread = self.rpc.call('thread/read', {'threadId': ident})['thread']
            if thread.get('status', {}).get('type') == 'active':
                raise RuntimeError('Dieser Chat arbeitet bereits.')
            self.rpc.call('thread/resume', {'threadId': ident, 'sandbox': 'read-only', 'approvalPolicy': 'never'})
            if ident in self.state.get('auto_titles', []):
                self.rpc.call('thread/name/set', {'threadId': ident, 'name': clean(text.strip().split('\n')[0], 65)})
                self.state['auto_titles'].remove(ident)
                self.save()
            return self.rpc.answer(ident, text), ident
        raise ValueError('Unbekannte Curve-Aktion.')

    def messages(self, thread):
        result = []
        metadata = self.state.get('message_meta', {}).get(thread['id'], {})
        for turn in thread.get('turns', []):
            for item in turn.get('items', []):
                kind = item.get('type')
                if kind == 'userMessage':
                    text = '\n'.join(c.get('text', '') for c in item.get('content', []) if c.get('type') == 'text')
                    role = 'user'
                elif kind == 'agentMessage':
                    text, role = item.get('text', ''), 'codex'
                else:
                    continue
                # App-server IDs, never IDs inferred from message text.
                ident = item.get('id')
                if not ident:
                    continue
                meta = metadata.get(ident, {})
                result.append({'id': ident, 'role': role, 'text': meta.get('text', text),
                    'reply': meta.get('reply', ''), 'truncated': False})
        return result

    def history(self, ident, cursor=''):
        thread = self.rpc.call('thread/read', {'threadId': ident, 'includeTurns': True})['thread']
        messages = self.messages(thread)
        end = len(messages)
        if cursor:
            indexes = [i for i, message in enumerate(messages) if message['id'] == cursor]
            if not indexes:
                raise ValueError('Verlauf wurde geändert. Bitte neu laden.')
            end = indexes[0]
        page, used = [], 0
        title = clean(thread.get('name') or thread.get('preview') or 'Curve Chat', 100)
        cwd = thread.get('cwd') or str(ROOT)
        limit = min(21000, 24000 - len(ident.encode('utf-8')) - len(title.encode('utf-8')) - len(cwd.encode('utf-8')) - 320)
        if limit < 1024:
            raise ValueError('Chat-Metadaten zu gross.')
        start = end
        while start > 0 and len(page) < 20:
            candidate = dict(messages[start - 1])
            # Wire overhead, ID, role and quote are included in the budget.
            cost = sum(len(candidate[k].encode('utf-8')) for k in ('id', 'text', 'reply')) + 32
            if used + cost > limit:
                if page:
                    break
                budget = max(0, limit - 32 - len(candidate['reply'].encode('utf-8')) - len(candidate['id'].encode('utf-8')))
                candidate['text'] = candidate['text'].encode('utf-8')[:budget].decode('utf-8', errors='ignore')
                candidate['truncated'] = True
                cost = limit
            page.insert(0, candidate)
            used += cost
            start -= 1
        return {'id': ident, 'title': title,
            'cwd': cwd, 'writable': ident in self.state['owned'],
            'cursor': page[0]['id'] if start > 0 and page else '', 'messages': page}

    def chat(self, operation, ident='', auxiliary='', text=''):
        if operation == 'NEW':
            ident = self.create(ident, text)
            return self.history(ident)
        if operation == 'HISTORY':
            if not ident:
                ident = self.state.get('default', '')
            if not ident:
                return {'id': '', 'title': 'Neuer Chat', 'cwd': str(ROOT), 'writable': True, 'cursor': '', 'messages': []}
            return self.history(ident, auxiliary)
        if operation != 'SEND':
            raise ValueError('Unbekannte Chat-Aktion.')
        if not text.strip():
            raise ValueError('Bitte eine Nachricht eingeben.')
        ident = ident or self.state.get('default', '')
        if ident and ident not in self.state['owned']:
            raise ValueError('Desktop-Chat nur lesbar.')
        original = text
        reply = ''
        if auxiliary:
            if not ident:
                raise ValueError('Zitat ohne Chat ist ungültig.')
            thread = self.rpc.call('thread/read', {'threadId': ident, 'includeTurns': True})['thread']
            matches = [m for m in self.messages(thread) if m['id'] == auxiliary and m['role'] == 'codex']
            if not matches:
                raise ValueError('Zitierte Codex-Nachricht gehört nicht zu diesem Chat.')
            quote = matches[0]['text'][:2000]
            reply = quote[:180]
            text = 'Antwort auf die folgende frühere Codex-Nachricht (Zitat):\n' + quote + '\n\nMeine neue Nachricht:\n' + original
        _, selected = self.dispatch('SEND', ident, text)
        if reply:
            thread = self.rpc.call('thread/read', {'threadId': selected, 'includeTurns': True})['thread']
            users = [m for m in self.messages(thread) if m['role'] == 'user' and m['text'] == text]
            if users:
                meta = self.state.setdefault('message_meta', {}).setdefault(selected, {})
                meta[users[-1]['id']] = {'text': original, 'reply': reply}
                self.save()
        return self.history(selected)

    def legacy(self, prompt):
        return self.dispatch('SEND', '', prompt)[0]
