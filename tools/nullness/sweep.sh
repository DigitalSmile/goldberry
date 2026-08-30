#!/usr/bin/env bash
# Adopts JSpecify one module at a time, driven by NullAway's own diagnostics.
#
# Each pass compiles, feeds what NullAway reported to `annotate.py`, and compiles
# again. It stops when the module is clean or when a pass changes nothing, which
# means what is left needs a person. See docs/testing.md §2.
set -uo pipefail
module="${1:?usage: sweep.sh <module> [max-passes]}"
passes="${2:-8}"
log=$(mktemp)

for i in $(seq 1 "$passes"); do
    ./gradlew ":${module}:compileJava" -Pgoldberry.lenient -Pgoldberry.nullaway=warn --no-daemon >"$log" 2>&1
    status=$?
    count=$(grep -c 'error: \[NullAway\]' "$log" || true)
    other=$(( $(grep -c 'error:' "$log" || true) - count ))
    echo "pass $i: $count NullAway, $other other errors"
    # Counting only NullAway made a syntax error this tool introduced look like
    # success. The build's own verdict is what "clean" means.
    if [ "$other" -gt 0 ]; then
        echo "the previous pass broke the build -- stopping so it can be read:"
        grep 'error:' "$log" | grep -v NullAway | sed 's|.*/goldberry/||' | sort -u | head -20
        rm -f "$log"; exit 2
    fi
    if [ "$status" -eq 0 ] && [ "$count" -eq 0 ]; then echo "clean"; rm -f "$log"; exit 0; fi
    before=$(git diff --stat -- "$module" | tail -1)
    python3 tools/nullness/annotate.py "$log"
    after=$(git diff --stat -- "$module" | tail -1)
    [ "$before" = "$after" ] && { echo "no progress; the rest needs a person"; break; }
done
grep 'error: \[NullAway\]' "$log" | sed 's|.*/goldberry/||' | sort -u | head -30
rm -f "$log"
exit 1
