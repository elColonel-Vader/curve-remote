# Install Curve Remote on a BlackBerry Curve 9360

This guide covers the computer, private pairing, handset build, USB deployment and first chat. The verified path is an **unsigned COD installed over USB**. The separate certificate experiment below is not yet a verified way to remove the network prompt.

## 1. Prepare the computer

Use a computer and phone on the same trusted Wi-Fi network. The tested handset is Curve 9360 / OS 7.1.0.714. Install Git, Python 3, a Java JDK, Docker and Codex CLI. Start Docker, sign in to Codex on the computer and verify `codex --version`. You do not enter Codex account credentials on the handset.

```sh
git clone https://github.com/elColonel-Vader/curve-remote.git
cd curve-remote
python3 --version
java -version
docker version
codex --version
```

Find the computer's LAN IPv4 address. On macOS, `networksetup -listallhardwareports` identifies the Wi-Fi interface; use `ipconfig getifaddr INTERFACE` for that interface. Do not assume Wi-Fi is always en0.

## 2. Prepare private pairing

```sh
python3 tools/setup_pairing.py
```

This creates the private token at `diagnostics/private/pairing-token` and a matching ignored `app/src/Pairing.java`. Each installation needs its own token. Preserve this directory across bridge restarts. Changing the token requires rebuilding/reinstalling the client. Never upload these files or a personally paired JAR/COD to a public release.

## 3. Supply and check build dependencies

```sh
python3 tools/fetch_build_dependencies.py
```

This verifies the declared CLDC, MIDP and ProGuard JAR checksums. Obtain a legally usable BlackBerry JDE 7.1 SDK separately; place its `rapc.jar` and `net_rim_api.jar` at:

```text
app/sdk/rapc.jar
app/sdk/net_rim_api.jar
```

The SDK is not bundled. Version, known archive provenance and SHA-256 checksums are in `app/dependencies.json`. Check the supplied files with `shasum -a 256 app/sdk/rapc.jar app/sdk/net_rim_api.jar` (macOS), or `sha256sum` on Linux, against that manifest.

## 4. Build the handset application

```sh
bash app/build.sh
```

Expected outputs are `app/out/CurveProbe.cod` and `app/out/CurveProbe.jar`. Build logs are in `app/out/compiler.log`. The launcher icon is embedded as `/icon.png`. A missing SDK or stopped Docker engine is a build error, not a phone failure.

## 5. Obtain the USB loader

Use a separately supplied Barry `bjavaloader` for your computer architecture. The macOS ARM fork used here is [barry-macos-arm](https://github.com/thebillington/barry-macos-arm), commit `caf70e86752dcd5ec7032c977a296235dfec21da`. Do not use a random loader binary from an unrelated source.

### Build Barry on an Apple Silicon Mac

Install Xcode Command Line Tools and Homebrew, then:

```sh
xcode-select --install
brew install autoconf automake libtool pkg-config gettext libusb
git clone https://github.com/thebillington/barry-macos-arm.git
cd barry-macos-arm
git checkout caf70e86752dcd5ec7032c977a296235dfec21da
./buildgen.sh
./configure --disable-gui --disable-desktop --disable-sync --disable-boost \
  --disable-dependency-tracking --with-libusb1_0="$(brew --prefix libusb)" \
  CC=/usr/bin/clang CXX=/usr/bin/clang++ \
  CXXFLAGS='-std=c++11 -D_DARWIN_C_SOURCE'
make -C src
```

The tested build links the loader directly to the static Barry library, avoiding unresolved legacy shared-library symbols:

```sh
/usr/bin/clang++ -std=c++11 -D_DARWIN_C_SOURCE -DHAVE_CONFIG_H \
  '-DLOCALEDIR="/usr/local/share/locale"' \
  -I. -I"$(brew --prefix libusb)/include/libusb-1.0" \
  tools/bjavaloader.cc src/.libs/libbarry.a \
  -L"$(brew --prefix libusb)/lib" -lusb-1.0 -lz -o tools/bjavaloader
./tools/bjavaloader -h
```

Keep this checkout separate from curve-remote. In the steps below, `LOADER` means the absolute path to the executable you built. Linux users can use Barry's loader built for their distribution; the macOS commands are not a universal Linux build guide.

## 6. Start the paired bridge

In the curve-remote directory:

```sh
python3 tools/curve_bridge.py --bind YOUR_COMPUTER_LAN_IP --port 8767
```

Keep this terminal running. If macOS asks about incoming connections, allow access for the local Python bridge on your trusted network. The computer and phone must be on the same LAN without client isolation. Do not forward port 8767 on your router.

## 7. Install over USB

1. Connect the Curve with a data-capable USB cable and unlock it.
2. Close Curve Remote through **Beenden** before replacing it.
3. Run these commands from curve-remote, substituting the real loader path:

```sh
LOADER=/absolute/path/to/barry-macos-arm/tools/bjavaloader
"$LOADER" deviceinfo
"$LOADER" load app/out/CurveProbe.cod
"$LOADER" dir
```

The load must finish with `done`; the directory must list module **CurveProbe** with the expected version. The launcher label is **Curve Remote** with the blue chat/terminal icon. The module name stays CurveProbe to retain local state. Do not force-load over a running app.

## 8. Configure and verify on the phone

1. Open Curve Remote. In the BlackBerry menu, choose **Einstellungen**.
2. Set **Mac IP** to the computer's actual LAN address; save/return.
3. If the operating system asks to allow the low-level network connection, approve this app's connection to your local bridge. This approval does not mean the code is signed.
4. Wait for **Bereit** in the header.
5. Send a short message with Enter. Check that Codex replies.
6. Open **Chats**, then return. Open **Verlauf** and select a message. Verify the input remains dark and the message index works.

The bridge runs only while the computer/server is on. Existing desktop chats are read-only; use **+** for a bridge-owned chat you can send to. Use the native text editor via the menu or trackpad click for complex characters/multiple lines.

## Updates, recovery and removal

Preserve pairing/session files, close the phone app, rebuild and load the new COD; confirm the module version. Keep a private copy of a known-good COD before updating. If a signed MIDlet experiment fails, close the app and reload your own known-good COD using the same command. Do not reset the whole phone or erase the trust store to fix a single app.

Remove Curve Remote through the handset application-management UI if no longer needed. Stop the bridge with Ctrl-C. Do not delete pairing/session state unless you intend to discard it.

## Troubleshooting

| Symptom | Check |
|---|---|
| Loader finds no device | Data cable, unlocked phone, USB permissions, correct loader architecture; close other USB managers. |
| Build fails | Docker running, Pairing.java generated, all five dependency JARs present; inspect compiler.log. |
| Verbinde stays on screen | Correct Mac IP, bridge terminal active, local firewall, same Wi-Fi, pending OS permission dialog; restart app after correcting. |
| Pairing rejected | Bridge token and built handset token must match; rebuild after setup changes. |
| Permission editing unavailable | OS/device policy may hide it; do not claim a certificate import bypasses that policy. |
| No chats visible | Codex CLI account authenticated on the computer; existing chat list may be paginated. |
| Can read but cannot send | Existing desktop chat is intentionally read-only; create a bridge-owned chat. |
| Icon absent or old version | Verify module directory and that the new COD actually loaded; launcher may need refresh. |

## Certificate and signed-MIDlet experiment

See [SIGNING.md](SIGNING.md). That section describes a specific import/installation test and its acceptance criteria. It is not a requirement for the verified unsigned USB path, and the current release does not promise prompt-free code signing.
