#!/usr/bin/env bash
# Proves the verification scripts can FAIL. Copies the repo to a scratch dir, injects defects, and requires the scripts
# to exit non-zero with the expected message. A verification script that cannot fail is worthless.
#   KOTLINC=... ANDROID_JAR=... bash scripts/selftest_verify.sh
set -uo pipefail
cd "$(dirname "$0")/.."
REAL="$(pwd)"
KOTLINC="${KOTLINC:-kotlinc}"; ANDROID_JAR="${ANDROID_JAR:?set ANDROID_JAR}"
export KOTLINC ANDROID_JAR
PASS=0; FAIL=0

fresh_copy() {   # fresh_copy <dir>
    rm -rf "$1"; mkdir -p "$1"
    (cd "$REAL" && tar --exclude=build --exclude=.git -cf - .) | (cd "$1" && tar -xf -)
}

# expect_fail <name> <expected-substring-in-output> <command...>
expect_fail() {
    local name="$1" needle="$2"; shift 2
    local out rc=0
    out="$("$@" 2>&1)" || rc=$?
    if [ "$rc" -ne 0 ] && echo "$out" | grep -qF -- "$needle"; then
        echo "  PASS  $name  (exit $rc, saw: $needle)"; PASS=$((PASS+1))
    else
        echo "  FAIL  $name  (exit $rc; expected non-zero exit and output containing: $needle)"; echo "$out" | tail -5 | sed 's/^/        /'; FAIL=$((FAIL+1))
    fi
}

T="$(mktemp -d)"

echo "baseline: the unmodified copy must PASS both scripts"
fresh_copy "$T/base"
if bash "$T/base/scripts/test-core.sh" >/dev/null 2>&1 && bash "$T/base/scripts/verify_offline.sh" >/dev/null 2>&1; then
    echo "  PASS  baseline"; PASS=$((PASS+1)); else echo "  FAIL  baseline (scripts fail on an unmodified tree)"; FAIL=$((FAIL+1)); fi

echo "negative tests: each injected defect must be caught"

fresh_copy "$T/a"   # A. compile error in the Android platform layer (the case that used to be swallowed)
printf 'package com.neelaxmi.focus.platform\nval broken: Int = "not an int"\n' > "$T/a/app/src/main/kotlin/com/neelaxmi/focus/platform/Broken.kt"
expect_fail "A: platform-layer compile error fails verify_offline" "COMPILE FAILED" bash "$T/a/scripts/verify_offline.sh"

fresh_copy "$T/b"   # B. compile error in the engine
printf 'package com.neelaxmi.focus.core\nval broken: Int = "x"\n' > "$T/b/core-engine/src/main/kotlin/com/neelaxmi/focus/core/Broken.kt"
expect_fail "B: engine compile error fails test-core" "COMPILE FAILED" bash "$T/b/scripts/test-core.sh"

fresh_copy "$T/c"   # C. a real logic regression (strict mode no longer blocks pausing)
python3 - "$T/c/core-engine/src/main/kotlin/com/neelaxmi/focus/core/SessionMachine.kt" <<'PY'
import sys; p = sys.argv[1]; s = open(p).read()
n = "if (s.strict != null) return Step.Rejected(Reason.STRICT_LOCKED)\n        val a = advance(s, now)\n        if (a.session.isFinished) return a\n        return Step.Ok(a.session.copy(state = SessionState.PAUSED)"
assert n in s; open(p, 'w').write(s.replace(n, "val a = advance(s, now)\n        if (a.session.isFinished) return a\n        return Step.Ok(a.session.copy(state = SessionState.PAUSED)"))
PY
expect_fail "C: failing engine check fails test-core" "ENGINE SUITE FAILED" bash "$T/c/scripts/test-core.sh"

fresh_copy "$T/d"   # D. tests silently dropped from the run
sed -i 's/controllerTests(); dashboardTests(); timeFormatTests()//' "$T/d/core-engine/src/test/kotlin/com/neelaxmi/focus/core/Main.kt"
expect_fail "D: fewer than the minimum checks fails test-core" "CHECKS (<" bash "$T/d/scripts/test-core.sh"

fresh_copy "$T/e"   # E. wrong column name in a DAO query
sed -i 's/ORDER BY startedAtWall DESC/ORDER BY startedAtWallX DESC/' "$T/e/app/src/main/kotlin/com/neelaxmi/focus/data/SessionDao.kt"
expect_fail "E: broken DAO SQL fails verify_offline" "no such column" bash "$T/e/scripts/verify_offline.sh"

fresh_copy "$T/f"   # F. compile error in the data layer
printf 'package com.neelaxmi.focus.data\nval broken: Int = "x"\n' > "$T/f/app/src/main/kotlin/com/neelaxmi/focus/data/Broken.kt"
expect_fail "F: data-layer compile error fails verify_offline" "COMPILE FAILED" bash "$T/f/scripts/verify_offline.sh"

# G/H. a compiler that misbehaves: build a fake kotlinc next to the real lib directory
mkdir -p "$T/fake/kotlinc/bin"; ln -s "$(cd "$(dirname "$(readlink -f "$(command -v "$KOTLINC")")")/.." && pwd)/lib" "$T/fake/kotlinc/lib"
printf '#!/bin/sh\necho "warning: looks harmless"\nexit 1\n' > "$T/fake/kotlinc/bin/kotlinc"; chmod +x "$T/fake/kotlinc/bin/kotlinc"
fresh_copy "$T/g"
expect_fail "G: compiler exits 1 printing only a warning-looking line" "COMPILE FAILED" env KOTLINC="$T/fake/kotlinc/bin/kotlinc" bash "$T/g/scripts/test-core.sh"
printf '#!/bin/sh\nexit 0\n' > "$T/fake/kotlinc/bin/kotlinc"
fresh_copy "$T/h"
expect_fail "H: compiler exits 0 but produces no classes" "NO CLASS FILES" env KOTLINC="$T/fake/kotlinc/bin/kotlinc" bash "$T/h/scripts/test-core.sh"

echo
echo "self-test: $PASS passed, $FAIL failed"
[ "$FAIL" -eq 0 ]
