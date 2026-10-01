# SPDX-License-Identifier: Apache-2.0
"""Local, paired Curve client for the installed Codex CLI."""
import argparse
import hmac
from pathlib import Path
import socketserver
import struct
import threading

MAGIC = b"CURVE01\n"
MAGIC_V2 = b"CURVE02\n"
MAGIC_V3 = b"CURVE03\n"
MAX_PROMPT = 8192
MAX_RESPONSE = 24000
ROOT = Path(__file__).resolve().parents[1]


def read_exact(stream, count):
    result = bytearray()
    while len(result) < count:
        block = stream.read(count - len(result))
        if not block:
            raise EOFError("Connection closed")
        result.extend(block)
    return bytes(result)


def read_frame(stream, limit):
    size = struct.unpack(">I", read_exact(stream, 4))[0]
    if size > limit:
        raise ValueError("Message exceeds limit")
    return read_exact(stream, size)


def write_frame(stream, text):
    data = text.encode("utf-8")
    if len(data) > MAX_RESPONSE:
        data = data[:MAX_RESPONSE].decode("utf-8", errors="ignore").encode("utf-8")
    stream.write(struct.pack(">I", len(data)) + data)
    stream.flush()


def write_chat(stream, chat):
    for key in ('id', 'title', 'cwd'):
        write_frame(stream, chat[key])
    stream.write(bytes([1 if chat['writable'] else 0]))
    write_frame(stream, chat['cursor'])
    stream.write(struct.pack('>I', len(chat['messages'])))
    for message in chat['messages']:
        write_frame(stream, message['id'])
        stream.write(bytes([1 if message['role'] == 'user' else 0]))
        write_frame(stream, message['text'])
        write_frame(stream, message['reply'])
        stream.write(bytes([1 if message['truncated'] else 0]))
    stream.flush()



class Handler(socketserver.StreamRequestHandler):
    def handle(self):
        self.request.settimeout(10)
        acquired = False
        version2 = False
        version3 = False
        try:
            magic = read_exact(self.rfile, len(MAGIC))
            version2 = magic == MAGIC_V2
            version3 = magic == MAGIC_V3
            if magic not in (MAGIC, MAGIC_V2, MAGIC_V3):
                return
            token = read_frame(self.rfile, 64)
            if not hmac.compare_digest(token, self.server.token):
                self.wfile.write(b"\x01")
                write_frame(self.wfile, "Kopplung ungültig.")
                return
            operation = "SEND"
            target = ""
            auxiliary = ""
            if version2 or version3:
                operation = read_frame(self.rfile, 16).decode("utf-8")
                target = read_frame(self.rfile, 4096).decode("utf-8")
            if version3:
                auxiliary = read_frame(self.rfile, 256).decode("utf-8")
            prompt = read_frame(self.rfile, MAX_PROMPT).decode("utf-8").strip()
            if operation == "SEND" and not prompt:
                raise ValueError("Bitte eine Anfrage eingeben.")
            acquired = self.server.gate.acquire(blocking=False)
            if not acquired:
                self.wfile.write(b"\x01")
                write_frame(self.wfile, "Codex arbeitet bereits. Bitte später versuchen.")
                return
            self.request.settimeout(180)
            print("Codex request accepted from paired client", flush=True)
            if version3:
                if self.server.sessions is None:
                    raise RuntimeError("Chat nicht verfügbar.")
                self.wfile.write(b"\x02")
                write_frame(self.wfile, "Codex antwortet" if operation == "SEND" else "Verlauf laden")
                chat = self.server.sessions.chat(operation, target, auxiliary, prompt)
                self.wfile.write(b"\x00")
                write_chat(self.wfile, chat)
                return
            if version2:
                if self.server.sessions is None:
                    raise RuntimeError("Chat-Auswahl nicht verfügbar.")
                response, selected = self.server.sessions.dispatch(operation, target, prompt)
            else:
                response, selected = self.server.runner(prompt), ""
            self.wfile.write(b"\x00")
            write_frame(self.wfile, response)
            if version2:
                write_frame(self.wfile, selected)
            print("Codex response delivered", flush=True)
        except (ValueError, UnicodeError, RuntimeError, TimeoutError) as error:
            try:
                self.wfile.write(b"\x01")
                write_frame(self.wfile, str(error))
            except OSError:
                pass
        except (OSError, EOFError):
            pass
        finally:
            if acquired:
                self.server.gate.release()


class Server(socketserver.ThreadingTCPServer):
    allow_reuse_address = True
    daemon_threads = True

    def __init__(self, address, token, runner=None, sessions=None):
        if not 32 <= len(token) <= 64:
            raise ValueError("Pairing token must contain 32 to 64 bytes")
        self.token = token
        self.runner = runner
        self.sessions = sessions
        self.gate = threading.Lock()
        super().__init__(address, Handler)


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--bind", default="127.0.0.1")
    parser.add_argument("--port", type=int, default=8767)
    args = parser.parse_args()
    token = (ROOT / "diagnostics/private/pairing-token").read_bytes().strip()
    from codex_sessions import AppServer, Sessions
    rpc = AppServer()
    sessions = Sessions(rpc)
    try:
        with Server((args.bind, args.port), token, sessions.legacy, sessions) as server:
            print(f"Curve Codex bridge 0.5: {args.bind}:{args.port}; saved chats; read-only", flush=True)
            server.serve_forever()
    finally:
        rpc.close()
