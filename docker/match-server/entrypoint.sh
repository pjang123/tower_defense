#!/usr/bin/env bash
#
# Match-server entrypoint.
#
#   1. Sanity-check the bind-mounted map templates, and enable Velocity forwarding if a secret was
#      supplied. (Maps live at /opt/td-server/GAME_WORLD_TEMPLATES, mounted in — not baked into the image.)
#   2. Boot PaperMC with its console wired to a FIFO so the watchdog can type "stop".
#   3. Watch the server log; when the Tower Defense match reaches the ENDED lifecycle,
#      gracefully stop the server so the container exits and the orchestrator reaps it.
#
set -euo pipefail

SERVER_DIR="${SERVER_DIR:-/opt/td-server}"
CONSOLE_FIFO="${CONSOLE_FIFO:-/tmp/td-console}"
LOG_FILE="${SERVER_DIR}/logs/latest.log"
END_GRACE_SECONDS="${MATCH_END_GRACE_SECONDS:-15}"
JVM_OPTS="${JVM_OPTS:--Xms1G -Xmx2G}"

# Shared match-end detection (also exercised by test/watchdog-test.sh).
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=match-watchdog.sh
source "${SCRIPT_DIR}/match-watchdog.sh"

cd "${SERVER_DIR}"

# --- 1. Map templates (bind-mounted, read-only) ----------------------------------
# Maps live on the host and are mounted in, so they can be edited and dropped in without rebuilding
# the image. Just sanity-check that something is there.
TEMPLATE_ROOT="${SERVER_DIR}/GAME_WORLD_TEMPLATES"
if [ -z "$(ls -A "${TEMPLATE_ROOT}" 2>/dev/null || true)" ]; then
  echo "[entrypoint] WARNING: ${TEMPLATE_ROOT} is empty — mount your maps with" \
       "'-v <host>/GAME_WORLD_TEMPLATES:${TEMPLATE_ROOT}:ro'." >&2
fi

# Velocity modern forwarding: only when a secret is supplied (otherwise the server runs standalone).
# A partial paper-global.yml is fine — Paper backfills the remaining keys with defaults on load.
if [ -n "${TD_VELOCITY_SECRET:-}" ]; then
  mkdir -p "${SERVER_DIR}/config"
  cat > "${SERVER_DIR}/config/paper-global.yml" <<EOF
proxies:
  velocity:
    enabled: true
    online-mode: true
    secret: ${TD_VELOCITY_SECRET}
EOF
  echo "[entrypoint] Velocity modern forwarding enabled (secret supplied)." >&2
fi

# --- 2. Console FIFO -------------------------------------------------------------
# Open the pipe read-write (fd 3) so the JVM's stdin never hits EOF and self-terminates.
rm -f "${CONSOLE_FIFO}"
mkfifo "${CONSOLE_FIFO}"
exec 3<>"${CONSOLE_FIFO}"

# --- 3. Match-end watchdog -------------------------------------------------------
# Background child; inherits fd 3 (the console). GameManager emits a dedicated, machine-readable
# lifecycle sentinel from handleMatchEnd() — guarded so it fires exactly once per match, for every
# end path (castle defeat + forfeit, single/multiplayer): "[TD-LIFECYCLE] ENDED matchId=<id>".
# Detection lives in td_match_is_end_line() (match-watchdog.sh) so it is unit-tested.
(
  until [ -f "${LOG_FILE}" ]; do sleep 1; done
  # -n0: only new lines; -F: keep following across Paper's log rotation.
  tail -n0 -F "${LOG_FILE}" | while IFS= read -r line; do
    if td_match_is_end_line "${line}"; then
      echo "[match-watchdog] Match ENDED detected -> stopping server in ${END_GRACE_SECONDS}s" >&2
      sleep "${END_GRACE_SECONDS}"
      printf 'stop\n' >&3
      break
    fi
  done
) &

# --- 4. Boot PaperMC -------------------------------------------------------------
# exec so the JVM becomes PID 1 and receives SIGTERM from `docker stop` directly,
# giving Paper a clean shutdown. Its stdin is the console FIFO (fd 3).
exec java ${JVM_OPTS} -jar "${SERVER_DIR}/paper.jar" --nogui <&3
