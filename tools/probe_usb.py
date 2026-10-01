# SPDX-License-Identifier: Apache-2.0
"""Answer the fixed CurveProbe ping over Barry's named USB channel."""
import pathlib
import selectors
import subprocess
import time


def handshake(timeout=30):
    tool = pathlib.Path(__file__).with_name("brawchannel")
    with subprocess.Popen(
        [str(tool), "CurveProbe"], stdin=subprocess.PIPE, stdout=subprocess.PIPE, bufsize=0
    ) as process:
        try:
            deadline = time.monotonic() + timeout
            message = bytearray()
            with selectors.DefaultSelector() as selector:
                selector.register(process.stdout, selectors.EVENT_READ)
                while time.monotonic() < deadline:
                    if not selector.select(timeout=max(0, deadline - time.monotonic())):
                        raise TimeoutError("No USB ping received")
                    value = process.stdout.read(1)
                    if not value:
                        raise ConnectionError("USB channel closed before ping")
                    message.extend(value)
                    if len(message) > 256:
                        raise ValueError("Oversized USB test message")
                    if value == b"\n":
                        break
                else:
                    raise TimeoutError("No USB ping received")
            if bytes(message) != b"PING CurveProbe\n":
                raise ValueError("Unexpected USB test message")
            process.stdin.write(b"PONG CurveProbe Mac\n")
            process.stdin.flush()
            print("PASS: CurveProbe USB ping received; PONG sent", flush=True)
            time.sleep(1)
        finally:
            if process.poll() is None:
                process.terminate()
                try:
                    process.wait(timeout=3)
                except subprocess.TimeoutExpired:
                    process.kill()
                    process.wait()


if __name__ == "__main__":
    handshake()
