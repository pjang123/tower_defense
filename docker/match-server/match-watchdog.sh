#!/usr/bin/env bash
#
# Shared match-end detection for the ephemeral match server.
# Sourced by both entrypoint.sh (production watchdog) and test/watchdog-test.sh, so the test
# exercises the exact predicate the container runs — no drift between the two.
#

# The lifecycle sentinel GameManager.handleMatchEnd() prints exactly once per match.
TD_END_SENTINEL='[TD-LIFECYCLE] ENDED'

# td_match_is_end_line <log-line>
# Returns 0 (true) when the given server-log line is the end-of-match sentinel.
# The expansion is double-quoted inside the case pattern, so the brackets in the sentinel are
# matched literally rather than as a glob character class.
td_match_is_end_line() {
  case "$1" in
    *"${TD_END_SENTINEL}"*) return 0 ;;
    *) return 1 ;;
  esac
}
