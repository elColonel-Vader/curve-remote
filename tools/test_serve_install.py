# SPDX-License-Identifier: Apache-2.0
import tempfile
from pathlib import Path
import threading
import unittest
import urllib.request
import urllib.error
from http.server import ThreadingHTTPServer
from serve_install import Handler

class InstallServerTests(unittest.TestCase):
    def setUp(self):
        self.temp=tempfile.TemporaryDirectory();root=Path(self.temp.name)
        cert=root/'root.cer';cert.write_bytes(b'public certificate')
        key=root/'root-key.pem';key.write_bytes(b'private key must never be served')
        self.server=ThreadingHTTPServer(('127.0.0.1',0),Handler)
        self.server.download_secret='temporary-test-secret'
        self.server.resources={'CurveRemote-RootCA.cer':(cert,'application/x-x509-ca-cert')}
        self.thread=threading.Thread(target=self.server.serve_forever,daemon=True);self.thread.start()
        self.base='http://127.0.0.1:'+str(self.server.server_address[1])
    def tearDown(self):
        self.server.shutdown();self.server.server_close();self.thread.join();self.temp.cleanup()
    def test_certificate_has_import_mime_and_exact_bytes(self):
        with urllib.request.urlopen(self.base+'/temporary-test-secret/CurveRemote-RootCA.cer') as r:
            self.assertEqual(r.headers['Content-Type'],'application/x-x509-ca-cert')
            self.assertEqual(r.read(),b'public certificate')
    def test_private_keys_and_path_traversal_are_not_served(self):
        for path in ['/temporary-test-secret/root-key.pem','/temporary-test-secret/../root-key.pem','/temporary-test-secret/%2e%2e/root-key.pem','/wrong/CurveRemote-RootCA.cer']:
            with self.subTest(path=path),self.assertRaises(urllib.error.HTTPError) as error:
                urllib.request.urlopen(self.base+path)
            self.assertEqual(error.exception.code,404)
            error.exception.close()

if __name__=='__main__':unittest.main()
