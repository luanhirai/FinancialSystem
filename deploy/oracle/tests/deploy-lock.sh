#!/usr/bin/env bash
set -euo pipefail
source_dir=$(cd "$(dirname "$0")/.." && pwd)
test_home=$(mktemp -d)
holder=""
cleanup() {
  if [ -n "$holder" ]; then kill "$holder" 2>/dev/null || true; wait "$holder" 2>/dev/null || true; fi
  rm -f -- "$test_home/lock-only.sh" "$test_home/.financial-deploy.lock" "$test_home/held" "$test_home/wait.log" "$test_home/timeout.log" "$test_home/invalid.log"
  rmdir -- "$test_home"
}
trap cleanup EXIT
# Run the actual lock code with an isolated HOME, stopping before any deployment.
sed '/^shared_env=/,$d' "$source_dir/deploy.sh" > "$test_home/lock-only.sh"
hold_lock() {
  rm -f -- "$test_home/held"
  ( flock 9; touch "$test_home/held"; sleep "$1" ) 9>"$test_home/.financial-deploy.lock" &
  holder=$!
  for attempt in $(seq 1 100); do
    [ ! -f "$test_home/held" ] || return 0
    sleep 0.05
  done
  echo "Test holder could not acquire lock"; exit 1
}
HOME="$test_home" bash "$test_home/lock-only.sh"
hold_lock 2
HOME="$test_home" DEPLOY_LOCK_TIMEOUT_SECONDS=5 bash "$test_home/lock-only.sh" > "$test_home/wait.log"
grep -q 'waiting up to 5 seconds' "$test_home/wait.log"
wait "$holder"; holder=""
hold_lock 3
if HOME="$test_home" DEPLOY_LOCK_TIMEOUT_SECONDS=1 bash "$test_home/lock-only.sh" > "$test_home/timeout.log"; then
  echo "Expected a timeout while lock is held"; exit 1
fi
grep -q 'Timed out waiting' "$test_home/timeout.log"
wait "$holder"; holder=""
if HOME="$test_home" DEPLOY_LOCK_TIMEOUT_SECONDS=invalid bash "$test_home/lock-only.sh" > "$test_home/invalid.log"; then
  echo "Expected invalid timeout rejection"; exit 1
fi
grep -q 'Invalid deployment lock timeout' "$test_home/invalid.log"
echo "Deployment lock tests passed: free lock, contention, timeout, invalid configuration."
