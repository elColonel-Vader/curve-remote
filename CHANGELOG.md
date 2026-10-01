# Changelog

## 0.7.1 — host verification patch

Wait up to one second for the server cleanup to release its concurrency gate instead of racing it immediately after reading the error response. No handset rendering or icon behaviour changed.

## 0.7.0 — initial experimental source release

- Original launcher icon and Curve Remote launcher label.
- Complete background repaint fixes white margins around the composer.
- Compact header/status, bottom navigation, Enter sending and history message index.
- Pending connection survives MIDlet pause during permission dialogs.
- Apache-2.0 source distribution, private pairing setup and host verification.

Known limitations: unsigned COD, network permission prompts, unencrypted local WLAN, read-only existing desktop chats. No pre-paired binary or third-party SDK is shipped.

Hardware confirmation: the user verified the white margin is gone and the launcher icon is visible on Curve 9360 / OS 7.1.0.714.
