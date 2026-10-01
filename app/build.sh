#!/usr/bin/env bash
# SPDX-License-Identifier: Apache-2.0
set -euo pipefail
cd "$(dirname "$0")"
for dependency in lib/cldcapi11.jar lib/midpapi20.jar lib/proguard.jar sdk/rapc.jar sdk/net_rim_api.jar; do
    if [[ ! -f "$dependency" ]]; then
        printf 'Missing build dependency: %s\n' "$dependency" >&2
        exit 1
    fi
done
task_app_dir="$PWD"
task_java_image='eclipse-temurin:8-jdk@sha256:6f9f640cdc1a35e64b77daf1f48fcf7527953d9aa804916c8557e1aae954ecd5'
rm -rf classes classes-pv
mkdir -p classes out
docker run --rm --network none -v "$task_app_dir:/work" -w /work "$task_java_image" \
    javac -encoding UTF-8 -source 1.3 -target 1.3 -bootclasspath lib/cldcapi11.jar:lib/midpapi20.jar \
    -d classes src/*.java
java -cp lib/proguard.jar proguard.ProGuard \
    -injars classes -outjars classes-pv -libraryjars lib/cldcapi11.jar -libraryjars lib/midpapi20.jar \
    -dontshrink -dontobfuscate -dontoptimize -microedition \
    -keep 'public class * extends javax.microedition.midlet.MIDlet'
cp assets/icon.png classes-pv/icon.png
jar cfm out/CurveProbe.jar manifest.mf -C classes-pv .
cd out
java -jar ../sdk/rapc.jar import=../sdk/net_rim_api.jar codename=CurveProbe -midlet CurveProbe.jar > compiler.log 2>&1
test -s CurveProbe.cod
printf 'Built %s/out/CurveProbe.cod\n' "$task_app_dir"
