#!/usr/bin/env bash
# SPDX-License-Identifier: Apache-2.0
set -euo pipefail
cd "$(dirname "$0")/.."
task_ui_out="${1:-app/out/ui-check}"
mkdir -p "$task_ui_out/classes"
javac --release 8 -d "$task_ui_out/classes" tools/ui-harness/javax/microedition/lcdui/*.java tools/ui-harness/javax/microedition/io/*.java tools/ui-harness/javax/microedition/midlet/*.java tools/ui-harness/javax/microedition/rms/*.java app/src/ChatMessage.java app/src/ChatView.java app/src/ChatScreen.java app/src/CurveProbe.java tools/ui-harness/Pairing.java tools/ui-harness/ChatViewHarness.java tools/ui-harness/ClientStateHarness.java tools/ui-harness/ChatScreenHarness.java
java -Djava.awt.headless=true -cp "$task_ui_out/classes" ChatViewHarness "$task_ui_out"
python3 tools/ui-harness/protocol_fixture.py "$task_ui_out/protocol.bin"
java -Djava.awt.headless=true -cp "$task_ui_out/classes" ClientStateHarness "$task_ui_out/protocol.bin"
java -Djava.awt.headless=true -cp "$task_ui_out/classes" ChatScreenHarness "$task_ui_out"
java -Djava.awt.headless=true -Dmidp.fontSize=26 -Dmidp.height=360 -cp "$task_ui_out/classes" ChatScreenHarness "$task_ui_out"
