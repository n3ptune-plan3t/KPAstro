#!/bin/sh
# Kotlin-only host regression check. Requires JDK headers, gcc, and kotlinc.
set -eu
H=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
R=$(CDPATH= cd -- "$H/../.." && pwd)
JH=${JAVA_HOME:-/usr/lib/jvm/java-21-openjdk-amd64}
O=${OUT:-/tmp/kpcheck}
C="$R/app/src/main/cpp"
mkdir -p "$O"
(
  cd "$C"
  gcc -O2 -w -shared -fPIC -I"$JH/include" -I"$JH/include/linux" -o "$O/libkpswe.so" \
    swe_jni.c swisseph/sweph.c swisseph/swephlib.c swisseph/swecl.c swisseph/swehouse.c \
    swisseph/swedate.c swisseph/swejpl.c swisseph/swemmoon.c swisseph/swemplan.c swisseph/swehel.c -lm
)
kotlinc "$R"/app/src/main/java/dev/plawan/kp/engine/*.kt "$H/GoldenCheck.kt" -d "$O/check.jar" -include-runtime
java -Djava.library.path="$O" -cp "$O/check.jar" GoldenCheckKt "$H/golden.json" "$R/app/src/main/assets/rules.json"
