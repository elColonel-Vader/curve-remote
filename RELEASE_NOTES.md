## Curve Remote 0.7.1

Experimental source release with a host verification patch for BlackBerry Curve 9360 / BlackBerry OS 7.1.

- Original Curve Remote launcher icon and readable launcher label.
- Dark fixed chat UI, compact header/status, direct QWERTY input and Enter sending.
- Visible history index, older messages, chat/project selection and quoted replies.
- Full Canvas repaint removes white margins around the input after switching screens.
- Private pairing setup and Apache-2.0 source/documentation/artwork licence.

Verification patch: the server-error test now waits with a bounded timeout for cleanup to release the gate; no arbitrary sleep. Handset behaviour is unchanged from 0.7.0.

Verification: 32 Python tests and actual Java client/rendering harness at two font sizes passed. The handset user confirmed that the white border is gone and the launcher icon is visible after installing 0.7.0 on a Curve 9360 running OS 7.1.0.714. The compact header/status also reached Bereit on hardware.

Known limitations: COD remains unsigned and can prompt for network access. The local self-signed MIDP experiment is not a trusted RIM COD signature. WLAN transport is authenticated but unencrypted. Existing desktop chats are read-only; only bridge-owned chats are writable.

Source-only release: build with a private per-install pairing credential and separately supplied BlackBerry SDK tools. No pre-paired binaries, SDK binaries, private certificates or personal chat histories are included.
