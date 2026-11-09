#!/usr/bin/env bash
# Shared helpers for the offline verification scripts. SOURCE this file; do not execute it.
# Callers must `cd` to the repository root first and run with `set -euo pipefail`.

KOTLINC="${KOTLINC:-kotlinc}"
command -v "$KOTLINC" >/dev/null 2>&1 || { echo "ERROR: kotlinc not found. Set KOTLINC=/path/to/kotlinc/bin/kotlinc" >&2; exit 2; }
KOTLIN_HOME="$(cd "$(dirname "$(readlink -f "$(command -v "$KOTLINC")")")/.." && pwd)"
STDLIB="$KOTLIN_HOME/lib/kotlin-stdlib.jar"
[ -f "$STDLIB" ] || { echo "ERROR: kotlin-stdlib.jar not found at $STDLIB" >&2; exit 2; }

# Minimum number of engine checks that must run. Guards against a test file silently dropping out of the build.
MIN_ENGINE_CHECKS=119

# Engine test sources WITHOUT the Gradle/JUnit wrapper (EngineSuiteTest.kt needs JUnit, which the offline
# scripts deliberately do not require). Single definition so every script uses the same list.
engine_test_sources() { find core-engine/src/test/kotlin -name '*.kt' ! -name EngineSuiteTest.kt | sort; }

# compile <label> <outdir> <kotlinc args...>
# Fails the whole script (non-zero exit, compiler output printed) unless kotlinc exits 0 AND produced class files.
# Only a successful compile prints "compiled OK" - nothing else in the scripts may claim a compile succeeded.
compile() {
    local label="$1" out="$2"; shift 2
    rm -rf "$out"; mkdir -p "$out"
    local log; log="$(mktemp)"
    local rc=0
    "$KOTLINC" "$@" -d "$out" >"$log" 2>&1 || rc=$?
    if [ "$rc" -ne 0 ]; then
        echo "COMPILE FAILED (kotlinc exit $rc): $label" >&2
        cat "$log" >&2
        exit 1
    fi
    local classes; classes="$(find "$out" -name '*.class' | wc -l | tr -d ' ')"
    if [ "$classes" -eq 0 ]; then
        echo "COMPILE PRODUCED NO CLASS FILES: $label" >&2
        cat "$log" >&2
        exit 1
    fi
    local warns; warns="$(grep -c 'warning:' "$log" || true)"
    echo "  compiled OK: $label ($classes classes, $warns compiler warnings)"
}

# run_engine_suite <classes-dir>   — runs the engine checks and enforces the minimum count.
run_engine_suite() {
    local dir="$1" out rc=0
    out="$(java -cp "$dir:$STDLIB" com.neelaxmi.focus.core.MainKt 2>&1)" || rc=$?
    echo "$out"
    if [ "$rc" -ne 0 ]; then echo "ENGINE SUITE FAILED (exit $rc)" >&2; exit 1; fi
    local n; n="$(echo "$out" | sed -n 's/^passed: \([0-9]*\), failed: 0$/\1/p' | head -1)"
    if [ -z "$n" ]; then echo "ENGINE SUITE: could not find a 'passed: N, failed: 0' line" >&2; exit 1; fi
    if [ "$n" -lt "$MIN_ENGINE_CHECKS" ]; then echo "ENGINE SUITE RAN ONLY $n CHECKS (< $MIN_ENGINE_CHECKS required)" >&2; exit 1; fi
}
