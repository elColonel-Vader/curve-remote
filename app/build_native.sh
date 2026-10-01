#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
for dependency in sdk/net_rim_api.jar sdk/rapc.jar lib/proguard.jar src/Pairing.java; do
  test -f "$dependency" || { printf 'Missing build dependency: %s\n' "$dependency" >&2; exit 1; }
done
rm -rf out/native-src out/native-classes
mkdir -p out/native-src out/native-classes out/native
python3 - <<'PY'
from pathlib import Path
import re
for source in Path('src').glob('*.java'):
    text=source.read_text().replace('javax.microedition.lcdui','curve.ui').replace('javax.microedition.midlet.MIDlet','curve.ui.MIDlet')
    if source.name=='CurveProbe.java':
        text=text.replace('public final class CurveProbe extends MIDlet implements CommandListener, ChatView.Actions {','public final class CurveProbe extends MIDlet implements CommandListener, ChatView.Actions {\n    public static void main(String[] args) { new CurveProbe().launch(); }')
        text=re.sub(r'Curve Remote [0-9.]+ / lokales WLAN','Curve Remote 0.8.0 / native WLAN',text)
    Path('out/native-src',source.name).write_text(text)
PY
task_native_dir="$PWD"
docker run --rm --network none -v "$task_native_dir:/work" -w /work \
  eclipse-temurin:8-jdk@sha256:6f9f640cdc1a35e64b77daf1f48fcf7527953d9aa804916c8557e1aae954ecd5 \
  javac -encoding UTF-8 -source 1.3 -target 1.3 -bootclasspath sdk/net_rim_api.jar \
  -d out/native-classes out/native-src/*.java native/curve/ui/*.java
cp assets/icon.png out/native-classes/icon.png
jar cf out/native/classes.jar -C out/native-classes .
java -cp lib/proguard.jar proguard.ProGuard -injars out/native/classes.jar \
  -outjars out/native/classes-pv.jar -libraryjars sdk/net_rim_api.jar \
  -dontshrink -dontobfuscate -dontoptimize -microedition
cd out/native
cat > CurveProbe.rapc <<'EOF'
MIDlet-Name: CurveProbe
MIDlet-Version: 0.8.0
MIDlet-Vendor: CurveProject
MIDlet-Jar-URL: CurveProbe.jar
MIDlet-Jar-Size: 0
MicroEdition-Profile: MIDP-2.0
MicroEdition-Configuration: CLDC-1.1
MIDlet-1: Curve Remote,icon.png,
RIM-MIDlet-Flags-1: 0
EOF
java -jar ../../sdk/rapc.jar import=../../sdk/net_rim_api.jar codename=CurveProbe \
  CurveProbe.rapc classes-pv.jar > compiler.log 2>&1
test -s CurveProbe.cod
printf 'Built native Curve Remote: %s/out/native/CurveProbe.cod\n' "$task_native_dir"
