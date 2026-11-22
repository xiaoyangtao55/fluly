#!/usr/bin/env bash
# Retry only Maven HTTP throttling, never an ordinary compiler/test failure.
set -uo pipefail
log_dir="${RUNNER_TEMP:-${TMPDIR:-/tmp}}/flue-gradle"
mkdir -p "$log_dir"
for attempt in 1 2 3; do
  log="$log_dir/attempt-$attempt.log"
  ./gradlew "$@" 2>&1 | tee "$log"
  status=${PIPESTATUS[0]}
  if (( status == 0 )); then
    exit 0
  fi
  if (( attempt == 3 )) || ! grep -Eq 'Received status code 429|HTTP[^[:cntrl:]]*429|HTTP Error 429' "$log"; then
    exit "$status"
  fi
  delay=$((attempt * 30))
  echo "Dependency server returned HTTP 429; retrying in ${delay}s (attempt $((attempt + 1))/3)."
  sleep "$delay"
done
