#!/usr/bin/env bash
#
# Regression test for the match-end watchdog.
#
#   bash docker/match-server/test/watchdog-test.sh
#
# Part 1 unit-tests td_match_is_end_line() (the exact predicate the container uses).
# Part 2 drives a real tail -F follow of a growing log file and asserts the stop action fires
# once — and only after — the [TD-LIFECYCLE] ENDED sentinel appears. The stop action writes to a
# file here instead of the server console FIFO; the FIFO/console wiring is container-only.
#
set -uo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=../match-watchdog.sh
source "${HERE}/../match-watchdog.sh"

fail=0
pass() { echo "  PASS: $1"; }
die()  { echo "  FAIL: $1"; fail=1; }

echo "[1] predicate — positive cases"
real_line='[12:34:56 INFO]: [Tower_Defense] [TD-LIFECYCLE] ENDED matchId=8f3a-1'
td_match_is_end_line "${real_line}"                   && pass "matches real Paper log line" || die "should match real Paper log line"
td_match_is_end_line "[TD-LIFECYCLE] ENDED matchId=x" && pass "matches bare sentinel"       || die "should match bare sentinel"

echo "[2] predicate — negative cases (must NOT match)"
td_match_is_end_line "[12:00:00 INFO]: [Tower_Defense] Game state changed to: ACTIVE"        && die "must not match ACTIVE state"  || pass "ignores ACTIVE state line"
td_match_is_end_line "[12:00:00 INFO]: [Tower_Defense] [TD-LIFECYCLE] STARTED matchId=x"     && die "must not match STARTED"       || pass "ignores STARTED sentinel"
td_match_is_end_line "random server chatter"                                                && die "must not match noise"         || pass "ignores unrelated lines"
# Guard against the brackets being treated as a glob character class: a bracketed single char
# that is NOT the literal sentinel phrase must not match.
td_match_is_end_line "player typed [T] in chat"                                             && die "bracket glob-class regression" || pass "brackets matched literally, not as a class"

echo "[3] end-to-end — tail follows the log; stop fires once, only after ENDED"
tmp="$(mktemp -d)"
log="${tmp}/latest.log"
stopfile="${tmp}/stop.signal"
: > "${log}"

# Mirror entrypoint.sh's watcher loop. timeout bounds tail -F so nothing lingers; stdout/stderr
# are detached so the background job never holds this script's pipe open.
(
  timeout 15 tail -n0 -F "${log}" | while IFS= read -r line; do
    if td_match_is_end_line "${line}"; then
      printf 'stop\n' >> "${stopfile}"
      break
    fi
  done
) >/dev/null 2>&1 &
watcher=$!

sleep 0.5
echo "[12:00:01 INFO]: [Tower_Defense] Game state changed to: ACTIVE" >> "${log}"
echo "[12:00:02 INFO]: [Tower_Defense] Wave 3 spawned"                >> "${log}"
sleep 0.5
if [ -s "${stopfile}" ]; then die "stop fired before the match ended"; else pass "no premature stop while the match runs"; fi

echo "[12:34:56 INFO]: [Tower_Defense] [TD-LIFECYCLE] ENDED matchId=8f3a-1" >> "${log}"

# Wait up to ~6s for the watcher to react to the appended sentinel.
for _ in $(seq 1 60); do [ -s "${stopfile}" ] && break; sleep 0.1; done
kill "${watcher}" 2>/dev/null

if [ "$(tr -d '[:space:]' < "${stopfile}" 2>/dev/null)" = "stop" ]; then
  pass "watcher issued exactly one 'stop' after the ENDED sentinel"
else
  die "watcher did not issue a single 'stop' after the ENDED sentinel"
fi

rm -rf "${tmp}"
echo
if [ "${fail}" -eq 0 ]; then echo "ALL TESTS PASSED"; else echo "TESTS FAILED"; exit 1; fi
