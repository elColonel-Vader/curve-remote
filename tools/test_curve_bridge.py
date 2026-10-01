# SPDX-License-Identifier: Apache-2.0
import io
import socket
import struct
import threading
import unittest
from curve_bridge import MAGIC, MAGIC_V2, MAGIC_V3, MAX_PROMPT, Server, read_frame, write_frame


TOKEN = b"a" * 48


def frame(data):
    return struct.pack(">I", len(data)) + data


class BridgeTests(unittest.TestCase):
    def setUp(self):
        self.calls = []
        def runner(prompt):
            self.calls.append(prompt)
            return "Grüße vom Mac: " + prompt
        self.server = Server(("127.0.0.1", 0), TOKEN, runner)
        self.thread = threading.Thread(target=self.server.serve_forever, daemon=True)
        self.thread.start()

    def tearDown(self):
        self.server.shutdown()
        self.server.server_close()
        self.thread.join(timeout=2)

    def request(self, token=TOKEN, prompt=b"Hallo"):
        with socket.create_connection(self.server.server_address, timeout=3) as connection:
            connection.sendall(MAGIC + frame(token) + frame(prompt))
            stream = connection.makefile("rb")
            status = stream.read(1)
            return status, read_frame(stream, 24000).decode("utf-8")

    def test_unicode_round_trip_and_prompt_as_data(self):
        prompt = "Grüße 😀 `touch /tmp/never` $(false)"
        status, answer = self.request(prompt=prompt.encode("utf-8"))
        self.assertEqual(status, b"\x00")
        self.assertEqual(answer, "Grüße vom Mac: " + prompt)
        self.assertEqual(self.calls, [prompt])

    def test_wrong_pairing_never_runs_codex(self):
        status, answer = self.request(token=b"b" * 48)
        self.assertEqual(status, b"\x01")
        self.assertIn("ungültig", answer)
        self.assertEqual(self.calls, [])

    def test_oversized_prompt_never_runs_codex(self):
        status, answer = self.request(prompt=b"x" * (MAX_PROMPT + 1))
        self.assertEqual(status, b"\x01")
        self.assertIn("limit", answer)
        self.assertEqual(self.calls, [])

    def test_empty_prompt_rejected(self):
        self.assertEqual(self.request(prompt=b"  ")[0], b"\x01")
        self.assertEqual(self.calls, [])

    def test_busy_rejects_second_request(self):
        self.server.gate.acquire()
        try:
            self.assertEqual(self.request()[0], b"\x01")
            self.assertEqual(self.calls, [])
        finally:
            self.server.gate.release()
        self.assertEqual(self.request()[0], b"\x00")

    def test_runner_error_releases_gate(self):
        def fail(prompt):
            raise RuntimeError("runner failed")
        self.server.runner = fail
        self.assertEqual(self.request()[0], b"\x01")
        self.assertTrue(self.server.gate.acquire(blocking=False))
        self.server.gate.release()

    def test_version2_list_has_selected_id_frame(self):
        class Sessions:
            def dispatch(inner, operation, target, text):
                self.calls.append((operation, target, text))
                return "Chatliste", "chosen-id"
        self.server.sessions = Sessions()
        with socket.create_connection(self.server.server_address, timeout=3) as connection:
            connection.sendall(MAGIC_V2 + frame(TOKEN) + frame(b"LIST") + frame(b"cursor") + frame(b""))
            stream = connection.makefile("rb")
            self.assertEqual(stream.read(1), b"\x00")
            self.assertEqual(read_frame(stream, 24000), b"Chatliste")
            self.assertEqual(read_frame(stream, 24000), b"chosen-id")
        self.assertEqual(self.calls, [("LIST", "cursor", "")])

    def test_version2_wrong_pairing_cannot_access_history(self):
        with socket.create_connection(self.server.server_address, timeout=3) as connection:
            connection.sendall(MAGIC_V2 + frame(b"b" * 48) + frame(b"READ") + frame(b"id") + frame(b""))
            stream = connection.makefile("rb")
            self.assertEqual(stream.read(1), b"\x01")
            self.assertIn("ungültig".encode("utf-8"), read_frame(stream, 24000))
        self.assertEqual(self.calls, [])

    def test_version3_progress_and_structured_messages(self):
        class Sessions:
            def chat(inner, operation, target, auxiliary, text):
                self.calls.append((operation,target,auxiliary,text))
                return {'id':'t', 'title':'Titel', 'cwd':'/tmp', 'writable':True, 'cursor':'', 'messages':[
                    {'id':'a1', 'role':'codex', 'text':'Grüße 😀', 'reply':'', 'truncated':False}]}
        self.server.sessions = Sessions()
        with socket.create_connection(self.server.server_address, timeout=3) as connection:
            connection.sendall(MAGIC_V3 + frame(TOKEN) + frame(b"SEND") + frame(b"t") + frame(b"a0") + frame(b"Antwort"))
            stream=connection.makefile('rb')
            self.assertEqual(stream.read(1),b"\x02"); read_frame(stream,256)
            self.assertEqual(stream.read(1),b"\x00")
            self.assertEqual(read_frame(stream,256),b"t");read_frame(stream,512);read_frame(stream,4096)
            self.assertEqual(stream.read(1),b"\x01");read_frame(stream,256)
            self.assertEqual(struct.unpack('>I',stream.read(4))[0],1)
            self.assertEqual(read_frame(stream,256),b"a1");self.assertEqual(stream.read(1),b"\x00")
            self.assertEqual(read_frame(stream,24000).decode(),'Grüße 😀')
            self.assertEqual(read_frame(stream,1024),b"");self.assertEqual(stream.read(1),b"\x00")
        self.assertEqual(self.calls,[('SEND','t','a0','Antwort')])

    def test_version3_bad_quote_returns_error_after_progress(self):
        class Sessions:
            def chat(inner,*args): raise ValueError('Zitat ungueltig')
        self.server.sessions=Sessions()
        with socket.create_connection(self.server.server_address,timeout=3) as connection:
            connection.sendall(MAGIC_V3+frame(TOKEN)+frame(b'SEND')+frame(b't')+frame(b'bad')+frame(b'Hi'))
            stream=connection.makefile('rb')
            self.assertEqual(stream.read(1),b'\x02');read_frame(stream,256)
            self.assertEqual(stream.read(1),b'\x01');self.assertIn(b'Zitat',read_frame(stream,24000))

    def test_utf8_truncation_remains_valid(self):
        output = io.BytesIO()
        write_frame(output, "😀" * 9000)
        output.seek(0)
        payload = read_frame(output, 24000)
        self.assertLessEqual(len(payload), 24000)
        self.assertEqual(payload.decode("utf-8"), "😀" * 6000)


if __name__ == "__main__":
    unittest.main()
