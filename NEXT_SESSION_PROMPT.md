# Next-session prompt

Paste the block below into a fresh Claude Code session in this repo to continue.

---

We're mid-way through a multi-session refactor of this Minecraft Tower Defense Paper plugin, moving
it from a single-server monolith toward a distributed model: a **lobby** provisions an **ephemeral
Docker match-server per game**, **Redis** signals a **Velocity proxy** to route players, and each
match container self-destructs when the game ends. The original spec was `junk/game_system_refactor.md`
(a scratch prompt in the gitignored `junk/` folder — treat it as direction, not gospel).

## Already built and verified
- **Match-server image** — `docker/match-server/` (multi-stage Dockerfile: Java 25 + PaperMC + the
  plugin; `entrypoint.sh` wires Paper's console to a FIFO and runs a log watchdog; maps are
  **bind-mounted read-only**, not baked in).
- **Lifecycle sentinel** — `GameManager.handleMatchEnd()` logs `[TD-LIFECYCLE] ENDED matchId=…` once
  per match; the container watchdog tails for it and stops the server. Tested via
  `docker/match-server/test/watchdog-test.sh` (passes).
- **Orchestration (Phase 3)** — `src/main/java/com/pauljang/towerDefense/orchestration/` (pure JDK,
  no Bukkit/3rd-party deps): `MatchProvisioner`/`DockerMatchProvisioner` (`docker run -P` + TCP
  **readiness probe** + container **reaping** on failure), `RedisPublisher` (raw RESP), 
  `MatchRouteMessage` (`toJson`/`fromJson`, round-trip tested), `OrchestrationService` (glue, runs on
  a daemon thread).
- **Wiring** — `GameManager.startMatch` delegates to orchestration when `orchestration.enabled` is
  true. **DEFAULT OFF** — the classic single-server flow is byte-for-byte unchanged otherwise.
  Config lives in `src/main/resources/config.yml` under `orchestration:` (`match-image`, `maps-dir`,
  `velocity-secret`, `boot-timeout-seconds`, `redis.*`), all read with safe defaults (no migration).
- **Velocity consumer** — scaffolded in `velocity-plugin/` (separate Maven module). Subscribes to
  `td:match:route`, registers the server, transfers players. **NOT compile-verified** (no
  `velocity-api` in the offline `.m2`); its pure-JDK files were javac-checked.
- **Velocity forwarding secret** — `orchestration.velocity-secret` → container env
  `TD_VELOCITY_SECRET` → entrypoint writes the `proxies.velocity` section into `config/paper-global.yml`.

## NEXT TASK — close the container auto-start gap
The match-server container currently boots Paper + plugin sitting in **LOBBY** state. The provisioner
passes `TD_MATCH_ID` and `TD_MAP_ID` env vars, but the plugin never reads them, so players routed in
land in that server's lobby instead of a running game.

Make the plugin, on startup **when it's running as a match server** (i.e. those env vars are present),
read them and immediately start that specific match (resolve the `MapData` by id via `MapManager`,
single- vs multiplayer), with players placed into it as they connect through the proxy. Relevant
existing code: `GameManager.startMatch(List<UUID>, MapData, Difficulty)`, `QueueManager`,
`MobListener.onPlayerJoin`, `WorldUnloadListener`. Players arrive asynchronously, so the match
probably starts in a waiting state and players are slotted in on join.

After that: also pass the routed players' UUIDs to the container (today only matchId/mapId go);
verify the partial `paper-global.yml` against a live Paper boot; end-to-end test with a real Redis +
Docker host.

## Constraints / environment
- Keep `orchestration.enabled=false` the default; never break the existing single-server flow. Keep
  the `orchestration` package free of Bukkit/3rd-party deps.
- **graphify hook**: a hook REQUIRES running `graphify query "<question>"` before reading/grepping
  source files — honor it (also when prompting subagents).
- **Build** (no `mvn` on PATH): JDK 25 at `C:\Program Files\Eclipse Adoptium\jdk-25.0.3.9-hotspot`;
  bundled Maven at `C:\Program Files\JetBrains\IntelliJ IDEA 2026.1.2\plugins\maven\lib\maven3\bin\mvn.cmd`.
  From PowerShell: `$env:JAVA_HOME=<jdk>; & <mvn> -o -q clean compile` (offline works; deps cached).
  The shell sandbox sometimes hides `C:\Program Files\...` paths — if a known-good path reports
  missing, retry with the sandbox disabled. Use `jshell --class-path target\classes <file.jsh>`
  (BOM-free file) for quick functional checks.

Start by reading `src/main/java/com/pauljang/towerDefense/orchestration/README.md` and
`docker/match-server/README.md`, then plan the auto-start implementation before coding.

---
