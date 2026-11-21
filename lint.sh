#!/usr/bin/env bash
# Style gate: ktlint and detekt over every tracked Kotlin file. Any finding fails it.
set -euo pipefail
cd "$(dirname "$0")"

files=$(git ls-files -co --exclude-standard '*.kt' '*.kts')
count=$(printf '%s\n' "$files" | grep -c . || true)
echo "lint: $count Kotlin files"
# a gate that sees nothing passes by accident
if [ "$count" -eq 0 ]; then
    echo "lint: no Kotlin files found" >&2
    exit 1
fi

status=0
# ktlint exits 1 on any finding; detekt's maxIssues is 0, so it exits non-zero too
ktlint --relative || status=1
detekt --config detekt.yml --build-upon-default-config || status=1

if [ "$status" -ne 0 ]; then
    echo "lint: FAILED" >&2
    exit 1
fi
echo "lint: clean"
