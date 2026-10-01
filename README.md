# Curve Remote

![Curve Remote icon](app/assets/icon-128.png)

A Java chat client for BlackBerry Curve 9360 (BlackBerry OS 7.1), connected over local Wi-Fi to a computer running Codex CLI. Shared Java ME chat code with a native BlackBerry UI adapter, dark transcript, direct QWERTY entry and trackpad navigation.

**Experimental source release.** Version 0.8.0 uses a native BlackBerry Java entry point and UI adapter. On the tested Curve 9360 / OS 7.1.0.714, the user confirmed chat operation and no network prompt on initial launch or after closing and reopening. The COD remains unsigned; this does not establish certificate trust or guarantee the same permission behavior on other devices/policies. No private credentials, personal chat data, pre-paired binary or third-party SDK binaries are distributed.

The local-root signing experiment failed on the Curve 9360 with 909 / 2-114 even after certificate import and explicit trust. The native build resolves the observed repeated-prompt problem on this handset without using that signing experiment; see [signing and native permissions](docs/SIGNING.md).

## Features

- Compact chat/project header with status at top-right and original launcher icon.
- Dark composer, Enter to send and native text-editor fallback for complex input.
- Permanent Chats, Verlauf, older messages, project and new-chat buttons.
- History message index: choose a message to jump to it; choose a Codex message to quote it.
- Unicode transport, per-chat drafts, saved chat lists and paginated history.
- Existing desktop chats are readable; only bridge-owned chats are writable, avoiding concurrent writers.
- Dedicated Codex app-server with read-only sandbox and no handset approval actions.

## Installation on the handset

Follow the complete [phone installation guide](docs/INSTALL.md): prerequisites, private pairing, SDK checks, build, Barry USB loader, deployment, first connection and rollback. For certificate imports and the signed-MIDlet test, see [signing and trust](docs/SIGNING.md).

## Setup

Requires Python 3, Java, Docker, authenticated Codex CLI and a Wi-Fi capable BlackBerry OS 7.1 handset. Supply BlackBerry JDE 7.1 `rapc.jar` and `net_rim_api.jar` in `app/sdk/` under their applicable terms.

```sh
python3 tools/setup_pairing.py
python3 tools/fetch_build_dependencies.py
# Supply the two BlackBerry SDK JARs in app/sdk/.
bash app/build_native.sh
python3 tools/curve_bridge.py --bind YOUR_COMPUTER_LAN_IP --port 8767
```

Pairing setup generates a unique private token for both bridge and handset. Keep `diagnostics/private/` and `app/src/Pairing.java` private; rebuild after changing the token. Set the computer IP in the handset settings (source example default: 192.168.1.10). Close the app before installing:

```sh
/path/to/bjavaloader load app/out/native/CurveProbe.cod
```

Barry JavaLoader must be built separately. The launcher label is **Curve Remote**; the module name remains CurveProbe. Chats are saved on the computer; preserve pairing and session files during updates. The build embeds `icon.png`. This project does not restore BIS, BBM or discontinued messaging services.

## Security and signing

WLAN transport uses a random shared token but is **not encrypted**. Keep port 8767 off the internet and use a trusted local network. The bridge uses your local Codex account and shares saved sessions, not the desktop app's live runtime. It rejects handset approval requests and keeps existing desktop chats read-only.

`python3 tools/sign_midlet_local.py` creates an experimental self-signed MIDP JAD and verifies its RSA/SHA-1 JAR signature. The private key stays in ignored diagnostics. USB COD loading does not consume this JAD signature. Importing a web/TLS certificate does not prove application-signing trust: MIDP needs an accepted protection-domain root, and RIM COD signing is a separate mechanism. A network-dialog approval is not certificate trust.

References: [MIDP trust rules](https://docs.oracle.com/javame/config/cldc/ref-impl/midp2.0/jsr118/javax/microedition/midlet/doc-files/PKITrust.html), [BlackBerry signing architecture](https://www.blackberry.com/solutions/resources/Protecting_the_BlackBerry_device_platform_against_malware.pdf), [legacy-service end of life](https://www.blackberry.com/en/secure-communications/support/end-of-life).

## Verification

```sh
python3 -m unittest discover -s tools -p 'test_*.py'
bash tools/test_chat_ui.sh
```

Python tests cover pairing, frame limits, sessions, paginated history and quotes. The Java harness runs the actual shared client/controller/rendering code using host adapters, checking keyboard navigation, drafts, history selection and packet decoding at two font sizes. Every rendered pixel is compared on white- and black-prefilled backgrounds to catch unpainted margins. Native SDK compilation and real-device checks are separate from these host tests; CI does not bundle the proprietary SDK or test BlackBerry permission persistence.

The v0.8.0 handset test confirmed chat operation without network prompts before and after restart. Two consecutive handset messages and their replies were also read back as two completed turns through the desktop thread API. An already-open desktop view may need reopening because the bridge runs its own app-server process rather than sharing desktop live events.

## Icon source

The original geometric PNG artwork is included. Regenerate it with:

```sh
javac -d /tmp/curve-icon-build tools/CreateIcon.java
java -Djava.awt.headless=true -cp /tmp/curve-icon-build CreateIcon app/assets
```

## Contributing and licence

Keep handset code compatible with Java 1.3 syntax, MIDP 2.0 and CLDC 1.1. See [CONTRIBUTING.md](CONTRIBUTING.md).

Original source, documentation and artwork: **Apache-2.0**. See [LICENSE](LICENSE), [NOTICE](NOTICE) and [THIRD_PARTY.md](THIRD_PARTY.md). Independent project, not affiliated with BlackBerry or OpenAI.
