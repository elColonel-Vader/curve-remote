# SPDX-License-Identifier: Apache-2.0
import json
import queue
from collections import deque
from pathlib import Path
import tempfile
import unittest
from codex_sessions import Sessions, AppServer, ROOT


class FakeRPC:
    def __init__(self):
        self.calls = []
        self.status = 'idle'

    def call(self, method, params=None):
        self.calls.append((method, params))
        if method == 'thread/list':
            return {'data': [{'id': 'desktop', 'name': 'Titel\tmit\nZeilen', 'cwd': str(ROOT)},
                             {'id': 'curve', 'name': 'Curve Chat', 'cwd': str(ROOT)}], 'nextCursor': 'page2'}
        if method == 'thread/start':
            return {'thread': {'id': 'curve'}}
        if method == 'thread/read':
            return {'thread': {'status': {'type': self.status}, 'turns': [
                {'items': [{'type': 'userMessage', 'content': [{'type': 'text', 'text': 'Grüße 😀'}]},
                           {'type': 'agentMessage', 'text': 'Hallo'}]}]}}
        return {}

    def answer(self, ident, prompt):
        self.calls.append(('answer', {'id': ident, 'prompt': prompt}))
        return 'Antwort'


class SessionsTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.path = Path(self.temp.name) / 'state.json'
        self.rpc = FakeRPC()
        self.sessions = Sessions(self.rpc, self.path)

    def tearDown(self):
        self.temp.cleanup()

    def test_desktop_send_does_not_resume_or_start(self):
        with self.assertRaisesRegex(ValueError, 'nur lesbar'):
            self.sessions.dispatch('SEND', 'desktop', 'Hallo')
        self.assertEqual(self.rpc.calls, [])

    def test_new_chat_survives_backend_restart_and_resumes_same_id(self):
        _, ident = self.sessions.dispatch('NEW', str(ROOT), 'Mein Curve Chat')
        restored = Sessions(self.rpc, self.path)
        reply, selected = restored.dispatch('SEND', '', 'Zweite Nachricht')
        self.assertEqual(selected, ident)
        self.assertEqual(reply, 'Antwort')
        resumes = [p for m, p in self.rpc.calls if m == 'thread/resume']
        self.assertEqual(resumes[0]['threadId'], ident)
        self.assertEqual(resumes[0]['sandbox'], 'read-only')
        self.assertEqual(self.path.stat().st_mode & 0o777, 0o600)

    def test_active_curve_thread_rejected(self):
        self.sessions.state['owned'] = ['curve']
        self.rpc.status = 'active'
        with self.assertRaisesRegex(RuntimeError, 'bereits'):
            self.sessions.dispatch('SEND', 'curve', 'Hallo')
        self.assertFalse(any(m in ('answer', 'thread/resume') for m, _ in self.rpc.calls))

    def test_unknown_project_cannot_start_thread(self):
        with self.assertRaisesRegex(ValueError, 'Projektauswahl'):
            self.sessions.dispatch('NEW', '/unlisted/path', 'Chat')
        self.assertFalse(any(m == 'thread/start' for m, _ in self.rpc.calls))

    def test_list_labels_modes_and_pagination(self):
        self.sessions.state['owned'] = ['curve']
        rows, _ = self.sessions.dispatch('LIST', 'previous')
        self.assertIn('Titel mit Zeilen', rows)
        self.assertIn('\tLesen', rows)
        self.assertIn('\tCurve', rows)
        self.assertIn('@next\tpage2', rows)
        self.assertEqual(self.rpc.calls[0][1]['cursor'], 'previous')

    def test_history_does_not_resume_desktop(self):
        reply, ident = self.sessions.dispatch('READ', 'desktop')
        self.assertIn('nur lesbar', reply)
        self.assertIn('Du: Grüße 😀', reply)
        self.assertIn('Codex: Hallo', reply)
        self.assertEqual(ident, 'desktop')
        self.assertEqual([m for m, _ in self.rpc.calls], ['thread/read'])

    def test_new_chat_takes_title_from_first_message_once(self):
        self.sessions.dispatch('NEW', str(ROOT), 'Neuer Chat')
        self.sessions.dispatch('SEND', 'curve', 'Grüße aus dem Curve\nMehr Text')
        names = [p['name'] for m,p in self.rpc.calls if m == 'thread/name/set']
        self.assertEqual(names[-1], 'Grüße aus dem Curve')
        self.sessions.dispatch('SEND', 'curve', 'Zweite Nachricht')
        self.assertEqual(len([m for m,p in self.rpc.calls if m == 'thread/name/set']), len(names))

    def test_custom_title_is_never_replaced(self):
        self.sessions.dispatch('NEW', str(ROOT), 'Mein Projekt')
        self.sessions.dispatch('SEND', 'curve', 'Erste Nachricht')
        names = [p['name'] for m,p in self.rpc.calls if m == 'thread/name/set']
        self.assertEqual(names, ['Mein Projekt'])

    def test_empty_prompt_cannot_create_chat(self):
        with self.assertRaises(ValueError):
            self.sessions.dispatch('SEND', '', '   ')
        self.assertEqual(self.rpc.calls, [])


class StructuredChatTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.path = Path(self.temp.name) / 'state.json'
        self.thread = {'id': 'curve', 'name': 'Chat', 'cwd': str(ROOT), 'status': {'type': 'idle'}, 'turns': []}
        self.calls = []
        owner = self
        class RPC(FakeRPC):
            def call(inner, method, params=None):
                owner.calls.append((method, params))
                if method == 'thread/read': return {'thread': owner.thread}
                return super(RPC, inner).call(method, params)
            def answer(inner, ident, prompt):
                owner.calls.append(('answer', prompt))
                owner.thread['turns'].append({'items': [
                    {'id': 'u-new', 'type': 'userMessage', 'content': [{'type': 'text', 'text': prompt}]},
                    {'id': 'a-new', 'type': 'agentMessage', 'text': 'Echte Antwort'}]})
                return 'Echte Antwort'
        self.rpc = RPC()
        self.sessions = Sessions(self.rpc, self.path)
        self.sessions.state = {'owned': ['curve'], 'default': 'curve'}

    def tearDown(self): self.temp.cleanup()

    def agent(self, ident, text):
        self.thread['turns'].append({'items': [{'id': ident, 'type': 'agentMessage', 'text': text}]})

    def test_pages_are_chronological_and_cursor_excludes_anchor(self):
        for i in range(30): self.agent('a'+str(i), 'Nachricht '+str(i))
        latest = self.sessions.chat('HISTORY', 'curve')
        self.assertEqual([m['id'] for m in latest['messages']], ['a'+str(i) for i in range(10,30)])
        older = self.sessions.chat('HISTORY', 'curve', latest['cursor'])
        self.assertEqual([m['id'] for m in older['messages']], ['a'+str(i) for i in range(10)])
        self.assertEqual(older['cursor'], '')

    def test_utf8_long_message_is_explicitly_cut_with_bounded_wire(self):
        import io
        from curve_bridge import write_chat
        self.agent('a', '😀' * 20000)
        packet = self.sessions.history('curve')
        self.assertTrue(packet['messages'][0]['truncated'])
        self.assertTrue(packet['messages'][0]['text'].endswith('😀'))
        stream = io.BytesIO(); write_chat(stream, packet)
        self.assertLessEqual(len(stream.getvalue()), 24000)

    def test_large_folder_metadata_stays_within_packet_limit(self):
        import io
        from curve_bridge import write_chat
        self.thread['cwd'] = '/' + 'a' * 4000
        self.agent('a', 'X' * 30000)
        stream = io.BytesIO(); write_chat(stream, self.sessions.history('curve'))
        self.assertLessEqual(len(stream.getvalue()), 24000)

    def test_quote_is_validated_and_persists_original_display_text(self):
        self.agent('a1', 'Codex: Zitat\nDu: Inhalt ist kein neuer Absender')
        packet = self.sessions.chat('SEND', 'curve', 'a1', 'Meine Antwort 😀')
        prompt = [p for m,p in self.calls if m == 'answer'][0]
        self.assertIn('Zitat\nDu:', prompt)
        self.assertIn('Meine neue Nachricht:\nMeine Antwort 😀', prompt)
        user = [m for m in packet['messages'] if m['id'] == 'u-new'][0]
        self.assertEqual(user['text'], 'Meine Antwort 😀')
        self.assertIn('Zitat', user['reply'])
        restored = Sessions(self.rpc, self.path)
        self.assertEqual(restored.history('curve')['messages'][-2]['text'], 'Meine Antwort 😀')

    def test_invalid_quote_never_starts_turn(self):
        self.agent('a1', 'Antwort')
        with self.assertRaisesRegex(ValueError, 'gehört nicht'):
            self.sessions.chat('SEND', 'curve', 'other-thread-id', 'Antwort')
        self.assertFalse(any(m in ('answer','thread/resume') for m,_ in self.calls))

    def test_desktop_v3_send_is_rejected_before_history_read(self):
        with self.assertRaisesRegex(ValueError, 'nur lesbar'):
            self.sessions.chat('SEND', 'desktop', 'id', 'Antwort')
        self.assertEqual(self.calls, [])

    def test_stale_cursor_has_clear_error(self):
        with self.assertRaisesRegex(ValueError, 'neu laden'):
            self.sessions.history('curve', 'missing')

    def test_no_default_chat_is_empty_without_creating_thread(self):
        self.sessions.state = {'owned': [], 'default': ''}
        self.assertEqual(self.sessions.chat('HISTORY')['messages'], [])
        self.assertEqual(self.calls, [])


class AppServerEventsTests(unittest.TestCase):
    def client(self, messages):
        client = AppServer.__new__(AppServer)
        client.messages = queue.Queue()
        client.sequence = 0
        client.notifications = deque()
        client.sent = []
        client.send = client.sent.append
        for message in messages:
            client.messages.put(message)
        return client

    def test_events_before_rpc_reply_are_preserved(self):
        client = self.client([
            {'method': 'item/completed', 'params': {'threadId': 't', 'turnId': 'u',
                'item': {'type': 'agentMessage', 'text': 'Hallo'}}},
            {'id': 1, 'result': {'turn': {'id': 'u'}}},
            {'method': 'turn/completed', 'params': {'threadId': 't',
                'turn': {'id': 'u', 'status': 'completed'}}}])
        self.assertEqual(client.answer('t', 'Hallo'), 'Hallo')
        self.assertEqual(client.sent[0]['params']['sandboxPolicy'], {'type': 'readOnly'})

    def test_failed_turn_never_returns_partial_answer_as_success(self):
        client = self.client([
            {'id': 1, 'result': {'turn': {'id': 'u'}}},
            {'method': 'item/completed', 'params': {'threadId': 't',
                'item': {'type': 'agentMessage', 'text': 'Teilantwort'}}},
            {'method': 'turn/completed', 'params': {'threadId': 't',
                'turn': {'id': 'u', 'status': 'failed', 'error': {'message': 'Fehler'}}}}])
        with self.assertRaisesRegex(RuntimeError, 'Fehler'):
            client.answer('t', 'Hallo')

    def test_server_request_is_rejected_without_approval(self):
        client = self.client([
            {'id': 99, 'method': 'item/commandExecution/requestApproval', 'params': {}},
            {'id': 1, 'result': {}}])
        client.call('thread/list')
        self.assertEqual(client.sent[1]['id'], 99)
        self.assertIn('error', client.sent[1])

    def test_disconnection_fails_promptly(self):
        client = self.client([{'bridgeDisconnected': True}])
        with self.assertRaisesRegex(RuntimeError, 'beendet'):
            client.call('thread/list')


if __name__ == '__main__':
    unittest.main()
