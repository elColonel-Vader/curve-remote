#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""Local, allowlisted MIDP installation experiment. Never serves private keys."""
import argparse
import base64
import hashlib
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import os
from pathlib import Path
import secrets
import subprocess
import zipfile

ROOT = Path(__file__).resolve().parents[1]

def run(*args):
    return subprocess.run(args, check=True, capture_output=True).stdout

def prepare():
    os.umask(0o077)
    private = ROOT/'diagnostics/private/midlet-install'
    private.mkdir(parents=True,exist_ok=True)
    ca, ca_key = private/'root.pem', private/'root-key.pem'
    signer, key = private/'signer.pem', private/'signer-key.pem'
    identity=[ca,ca_key,signer,key]
    if any(p.exists() for p in identity) and not all(p.exists() for p in identity):
        raise RuntimeError('Incomplete installation identity; refusing to replace private files')
    if not ca.exists():
        run('openssl','req','-x509','-newkey','rsa:2048','-nodes','-sha1','-days','365',
            '-subj','/CN=Curve Remote Local Install Root/',
            '-addext','basicConstraints=critical,CA:TRUE,pathlen:0',
            '-addext','keyUsage=critical,keyCertSign,cRLSign',
            '-keyout',str(ca_key),'-out',str(ca))
        csr=private/'signer.csr'; extensions=private/'signer.ext'
        extensions.write_text('basicConstraints=critical,CA:FALSE\nkeyUsage=critical,digitalSignature\nextendedKeyUsage=codeSigning\nsubjectKeyIdentifier=hash\nauthorityKeyIdentifier=keyid,issuer\n')
        run('openssl','req','-new','-newkey','rsa:2048','-nodes',
            '-subj','/CN=Curve Remote Local MIDlet Signer/', '-keyout',str(key),'-out',str(csr))
        run('openssl','x509','-req','-in',str(csr),'-CA',str(ca),'-CAkey',str(ca_key),
            '-set_serial','1','-days','365','-sha1','-extfile',str(extensions),'-out',str(signer))
    run('openssl','verify','-auth_level','0','-CAfile',str(ca),str(signer))
    jar=ROOT/'app/out/CurveProbe.jar'
    signature=private/'signature.bin'
    run('openssl','dgst','-sha1','-sign',str(key),'-out',str(signature),str(jar))
    public=private/'signer-public.pem';public.write_bytes(run('openssl','x509','-in',str(signer),'-pubkey','-noout'))
    run('openssl','dgst','-sha1','-verify',str(public),'-signature',str(signature),str(jar))
    root_der=run('openssl','x509','-in',str(ca),'-outform','DER')
    signer_der=run('openssl','x509','-in',str(signer),'-outform','DER')
    cert=ROOT/'app/out/CurveRemote-RootCA.cer';cert.write_bytes(root_der)
    with zipfile.ZipFile(jar) as archive:
        attrs=[s for s in archive.read('META-INF/MANIFEST.MF').decode().splitlines() if s.startswith(('MIDlet-','MicroEdition-'))]
    attrs += ['MIDlet-Jar-URL: CurveProbe.jar','MIDlet-Jar-Size: '+str(jar.stat().st_size),
              'MIDlet-Certificate-1-1: '+base64.b64encode(signer_der).decode(),
              'MIDlet-Jar-RSA-SHA1: '+base64.b64encode(signature.read_bytes()).decode()]
    jad=ROOT/'app/out/CurveProbe-chain-signed.jad';jad.write_text('\n'.join(attrs)+'\n')
    fingerprint=hashlib.sha256(root_der).hexdigest().upper()
    return {'CurveRemote-RootCA.cer':(cert,'application/x-x509-ca-cert'),
            'CurveProbe-chain-signed.jad':(jad,'text/vnd.sun.j2me.app-descriptor'),
            'CurveProbe.jar':(jar,'application/java-archive')}, fingerprint

class Handler(BaseHTTPRequestHandler):
    def log_message(self,*args):
        pass # Do not record URLs carrying the temporary download secret.
    def do_GET(self):
        prefix='/'+self.server.download_secret+'/'
        if not self.path.startswith(prefix):
            self.send_error(404);return
        name=self.path[len(prefix):]
        if name=='':
            body=b'<html><body><h1>Curve Remote installation experiment</h1><p>Not yet verified as trusted by this device.</p><p><a href="CurveRemote-RootCA.cer">Download local root certificate</a></p><p><a href="CurveProbe-chain-signed.jad">Try signed MIDlet installation</a></p></body></html>'
            mime='text/html; charset=utf-8'
        elif name in self.server.resources:
            file,mime=self.server.resources[name];body=file.read_bytes()
        else:
            self.send_error(404);return
        self.send_response(200);self.send_header('Content-Type',mime)
        self.send_header('Content-Length',str(len(body)));self.send_header('Cache-Control','no-store');self.end_headers();self.wfile.write(body)

if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--bind',default='127.0.0.1');parser.add_argument('--port',type=int,default=8768)
    args=parser.parse_args();resources,fingerprint=prepare()
    with ThreadingHTTPServer((args.bind,args.port),Handler) as server:
        server.resources=resources;server.download_secret=secrets.token_hex(16)
        print('Root certificate SHA-256: '+fingerprint,flush=True)
        print('Open on handset: http://'+args.bind+':'+str(args.port)+'/'+server.download_secret+'/',flush=True)
        print('Only three installation artifacts are served. Private keys are never served. Ctrl-C stops downloads.',flush=True)
        server.serve_forever()
