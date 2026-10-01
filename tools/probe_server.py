# SPDX-License-Identifier: Apache-2.0
"""Fixed-message connection test; never executes commands or accesses files."""
import argparse
import socketserver


class Handler(socketserver.StreamRequestHandler):
    def handle(self):
        self.request.settimeout(10)
        try:
            message = self.rfile.readline(257)
            if message == b"PING CurveProbe\n":
                self.wfile.write(b"PONG CurveProbe Mac\n")
                self.wfile.flush()
                print("PASS: CurveProbe handshake", flush=True)
            else:
                self.wfile.write(b"ERROR\n")
        except (TimeoutError, ConnectionError, OSError):
            return


class Server(socketserver.ThreadingTCPServer):
    allow_reuse_address = True
    daemon_threads = True


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--bind", default="127.0.0.1")
    parser.add_argument("--port", type=int, default=8766)
    args = parser.parse_args()
    with Server((args.bind, args.port), Handler) as server:
        print(f"CurveProbe test server: {args.bind}:{args.port}", flush=True)
        server.serve_forever()
