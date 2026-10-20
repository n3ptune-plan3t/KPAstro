#!/bin/sh
# Host check: builds libkpswe for Linux, compiles the engine with kotlinc, runs it against golden.json from the Python reference.
set -e
H=$(cd "$(dirname "$0")" && pwd); R=$H/../..; JH=${JAVA_HOME:-/usr/lib/jvm/java-21-openjdk-amd64}; O=${OUT:-/tmp/kpcheck}; mkdir -p $O
C=$R/app/src/main/cpp; (cd $C && gcc -O2 -w -shared -fPIC -I$JH/include -I$JH/include/linux -o $O/libkpswe.so swe_jni.c swisseph/sweph.c swisseph/swephlib.c swisseph/swecl.c swisseph/swehouse.c swisseph/swedate.c swisseph/swejpl.c swisseph/swemmoon.c swisseph/swemplan.c swisseph/swehel.c -lm)
kotlinc $R/app/src/main/java/dev/plawan/kp/engine/*.kt $H/GoldenCheck.kt -d $O/check.jar -include-runtime 2>&1 | grep -v "^warning" || true
java -Djava.library.path=$O -cp $O/check.jar GoldenCheckKt $R/tools/reference/golden.json $R/app/src/main/assets/rules.json
