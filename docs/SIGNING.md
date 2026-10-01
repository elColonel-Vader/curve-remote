# Certificates, signing and a handset acceptance test

## What works and what is still unverified

The verified deployment path is an unsigned COD loaded through USB. A root certificate is not required for that path, and its network prompt has not been eliminated.

A local signature proves that the JAR matches the local signer's key. It does not prove the phone accepts that signer. RIM COD signatures and MIDP JAR/JAD signatures are different: the USB loader does not consume a MIDP JAD signature. A certificate trusted for TLS or secure email is not automatically assigned an application-signing protection domain.

The first handset trust-store inspection did not find our local certificate among 98 records. That is evidence of absence, not evidence that importing it would unlock COD signing.

### Observed Curve 9360 acceptance result

The local root was subsequently imported. A read-only USB inspection found the exact exported root certificate in the device's trust store, now containing 99 records. The signed MIDlet installation nevertheless failed with **909: application authentication failure** and **2-114: MIDlet certificate chain error**. Host verification of the certificate chain and JAR signature passes; JAR size and shared manifest/JAD attributes match.

This is a failed handset acceptance test, not a prompt-free signing solution. Device clock, explicit certificate trust and eligibility as an application-signing protection-domain root remain to be checked. The error alone does not identify which of these checks failed.

## Prepare a proper test chain

After private pairing and the JAR build described in [INSTALL.md](INSTALL.md), run:

```sh
python3 tools/serve_install.py --bind YOUR_COMPUTER_LAN_IP --port 8768
```

This generates a dedicated local installation identity with a CA root and a separate code-signing leaf certificate. It verifies the chain and JAR signature, exports the public root certificate and prepares the signed JAD. The displayed root SHA-256 fingerprint identifies the certificate you are about to trust. Inspect its SHA-1 fingerprint if the old phone only displays that algorithm:

```sh
openssl x509 -in diagnostics/private/midlet-install/root.pem -noout -fingerprint -sha1
```

Private keys remain in `diagnostics/private/midlet-install/` and are never served. The temporary HTTP server exposes only the root `.cer`, signed `.jad` and paired `.jar` behind the printed temporary URL. Treat that URL as private: the JAR contains your pairing credential. Keep it on your trusted LAN and stop it with Ctrl-C after the test.

The RSA/SHA-1 profile is solely for this legacy MIDP acceptance experiment. It is not a recommended modern TLS certificate or a general-purpose root CA. Do not install this root on unrelated devices.

## Import on the Curve

1. Open the printed **http** URL in the handset browser. Plain HTTP is intentional so that downloading the test certificate does not require a working modern TLS stack.
2. Download `CurveRemote-RootCA.cer` and save it on the handset or media card. Alternatively copy this public file over USB mass storage if available; never copy `root-key.pem` or `signer-key.pem`.
3. In **Files/Dateien** or **Media/Medien**, highlight the `.cer` file and use the BlackBerry menu → **Import Certificate/Zertifikat importieren**. The device may ask for its key-store password. On first use it can ask you to choose and confirm a new password; retain it privately. Enter passwords on the phone, not into an issue or README.
4. Open **Options/Optionen → Security/Sicherheit → Advanced Security Settings/Erweiterte Sicherheitseinstellungen → Certificates/Zertifikate**.
5. Find **Curve Remote Local Install Root**, inspect its certificate/fingerprint and compare it with the computer's output.
6. If the fingerprint matches and the menu offers **Trust/Vertrauen**, trust this selected certificate. Do not trust unrelated certificates or alter all device roots.
7. Inspect certificate details and record the trust status. A missing or disabled Trust menu is a device/policy restriction; do not claim success or modify the raw key-store database to bypass it.
8. Check the handset's date, time and time zone against the certificate's validity interval. A freshly generated certificate is not valid before its issuance time; an old or incorrect phone clock can cause chain validation to fail. Correct the clock rather than disabling validity checks.

These UI actions are based on the original [Curve 9360 user guide](https://fcc.report/FCC-ID/L6ARDX70UW/1524306.pdf), certificate sections around pages 287–292. Menu translations and available actions can differ with OS and policy.

## Try the signed application installer

1. Preserve your own known-good COD. End Curve Remote before replacing the app.
2. Return to the temporary page and open **Try signed MIDlet installation** (`CurveProbe-chain-signed.jad`). The JAD references the paired JAR on the same server.
3. Record the complete installer result. An authentication error, untrusted-certificate warning or refusal is a failed acceptance test, even though host verification passed.
4. If installation succeeds, inspect the signer/application information. Start the app, test a message, close it and start it again. Check whether the exact unsigned-source/network prompt returns.

Only call this successful if the certificate is accepted, the application installer validates the signature and the restart test behaves as intended. Mere presence in the device trust store is insufficient. Existing network permission prompts may remain even for an accepted signer.

If the MIDP installer rejects the chain or does not allow user roots for application signing, this local method cannot supply RIM COD signatures. Return to the verified unsigned USB path and retain the limitation in release notes. Obtaining an accepted application-signing authority is a separate prerequisite; self-generating a key does not provide it.

## Recovery

Stop the installer server. If the experiment replaced the client unsuccessfully, close it and reload your own saved COD with Barry JavaLoader. Remove the experimental root through the device certificate UI if you no longer use it. Never erase the whole trust store or disable the global firewall for this test.

## References

- [MIDP PKI trust and application protection domains](https://docs.oracle.com/javame/config/cldc/ref-impl/midp2.0/jsr118/javax/microedition/midlet/doc-files/PKITrust.html)
- [BlackBerry platform signing architecture](https://www.blackberry.com/solutions/resources/Protecting_the_BlackBerry_device_platform_against_malware.pdf)
- [Curve 9360 user guide](https://fcc.report/FCC-ID/L6ARDX70UW/1524306.pdf)
