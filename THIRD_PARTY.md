# External dependencies

The source release does not bundle BlackBerry SDKs, third-party JARs or Barry executables.

- BlackBerry JDE 7.1: `rapc.jar` and `net_rim_api.jar` are supplied separately by the builder, under their applicable terms. `app/dependencies.json` records the version and checksums used during development; an archive reference is not a redistribution licence.
- MicroEmulator CLDC/MIDP API build JARs: see [MicroEmulator](https://github.com/barteo/microemu). Download locations and checksums are in `app/dependencies.json`.
- ProGuard 6.2.2: build-time preverification; see [ProGuard licensing](https://www.guardsquare.com/manual/license).
- Eclipse Temurin JDK 8: pinned build container; see [Adoptium](https://adoptium.net/about).
- Barry 0.19: optional USB installer, built separately; see [Barry macOS ARM source](https://github.com/thebillington/barry-macos-arm). Barry has its own licence, included in `tools/Barry-LICENSE` for reference.
- Codex CLI: installed separately and authenticated by each user. No account credentials are shipped.

The `tools/ui-harness` adapters are original test code for this project, not a replacement runtime distributed with the handset application. The launcher icon is original geometric artwork; it contains no vendor logo or third-party font.
