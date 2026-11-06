#!/usr/bin/env bash
# Compiles and runs the pure-Kotlin engine checks with only kotlinc + a JDK (no Gradle, no Android SDK, no JUnit).
# Usage:  KOTLINC=/path/to/kotlinc/bin/kotlinc bash scripts/test-core.sh
set -euo pipefail
cd "$(dirname "$0")/.."
source scripts/lib.sh

W="$(mktemp -d)"
echo "[engine] compile main + tests"
compile "engine (main + tests)" "$W/engine" core-engine/src/main/kotlin $(engine_test_sources)
echo "[engine] run"
run_engine_suite "$W/engine"
echo "ENGINE CHECKS PASSED"
