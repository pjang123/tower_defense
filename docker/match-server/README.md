# Ephemeral Match-Server Image (Phase 2 prototype)

A standalone Docker image that runs **one Tower Defense match per container**. PaperMC
boots, the `Tower_Defense` plugin builds the match world from the **bind-mounted** map
templates, and when the match reaches the `ENDED` lifecycle the container shuts itself
down so an orchestrator can reap it.

This is the match-server image. The lobby-side orchestration that launches these containers
lives in the plugin's `orchestration/` package; the proxy that routes players lives in
`velocity-plugin/`.

## Build & run

```bash
# Build context MUST be the repo root (the plugin is compiled from pom.xml + src/).
docker build -f docker/match-server/Dockerfile -t td-match:latest .

# Boot a throwaway match server. Maps are NOT baked in — bind-mount your templates dir
# (edit them on the host and drop them in anytime; no rebuild needed).
docker run --rm -p 25565:25565 \
  -v "$PWD/GAME_WORLD_TEMPLATES:/opt/td-server/GAME_WORLD_TEMPLATES:ro" \
  td-match:latest
```

## What the image does

| Step | Where | Notes |
|------|-------|-------|
| Compile plugin | `Dockerfile` stage 1 | `maven:3-eclipse-temurin-25`, normalises the timestamped jar to `tower-defense.jar`. |
| Fetch PaperMC | `Dockerfile` stage 2 | Resolves the latest build for `PAPER_VERSION` via `api.papermc.io`. |
| Inject plugin | `Dockerfile` | `target/tower-defense-*.jar` → `plugins/Tower_Defense.jar`. |
| Maps | bind mount | Host `GAME_WORLD_TEMPLATES/` → `/opt/td-server/GAME_WORLD_TEMPLATES` (`:ro`). Each map is `{SINGLE_PLAYER,MULTI_PLAYER}/<id>/` with `map.yml` + world data. |
| Velocity forwarding | `entrypoint.sh` | If `TD_VELOCITY_SECRET` is set, writes the modern-forwarding `proxies.velocity` section into `config/paper-global.yml`. |
| Auto-start match | plugin `onEnable` | Reads `TD_MATCH_ID` / `TD_MAP_ID` (and the optional `TD_PLAYER_UUIDS` roster), resolves the map, clones it and waits. Proxy-routed players are slotted into the match as they join with their pre-assigned team; the match wakes once the full roster arrives (or on the first join when no roster was passed, or after a ~45s safety deadline). See `GameManager.maybeStartAsMatchServer`. |
| Auto-shutdown | `entrypoint.sh` | Tails the server log; on match `ENDED`, types `stop` into the console FIFO. |

## Build args (override with `--build-arg`)

| Arg | Default | Why |
|-----|---------|-----|
| `JAVA_VERSION` | `25` | Matches `pom.xml` (`maven.compiler.target`). |
| `RUNTIME_IMAGE` | `eclipse-temurin:25-jdk` | Temurin ships `-jre` only for LTS. Set `eclipse-temurin:25-jre` if available. |
| `PAPER_VERSION` | `26.1.2` | Matches `plugin.yml` `api-version`. |

## Decisions & assumptions you should review

- **Java 25, not Java 21.** The original refactor note assumed a "Java 21 JRE base", but
  the project actually targets Java 25 (`pom.xml`) and Paper `26.1.2` (`plugin.yml`). The
  image follows the real toolchain.
- **PaperMC `26.1.2` download.** I could not verify that `api.papermc.io` serves builds for
  `26.1.2`. The fetch script discovers the build number dynamically; if that version isn't
  published, pass `--build-arg PAPER_VERSION=<a-real-version>`.
- **Maps are bind-mounted, not baked in.** Mount your `GAME_WORLD_TEMPLATES/` tree read-only at
  `/opt/td-server/GAME_WORLD_TEMPLATES` — each map a `{SINGLE_PLAYER,MULTI_PLAYER}/<id>/` folder with
  `map.yml` plus the world's `level.dat`/`region/`. Edit on the host and drop in anytime; no rebuild.
  The lobby provisioner adds this `-v` automatically from `orchestration.maps-dir`. (`map_yamls/` in
  the repo is just example metadata stubs.)
- **Velocity forwarding.** `server.properties` runs `online-mode=false` (the proxy handles auth).
  Pass `-e TD_VELOCITY_SECRET=<secret>` (the lobby does this from `orchestration.velocity-secret`) and
  the entrypoint writes the modern-forwarding `proxies.velocity` section into `config/paper-global.yml`;
  the secret must match the proxy's `forwarding.secret`. **Not verified against a live boot** — if Paper
  rejects the partial `paper-global.yml`, add its `_version` for your Paper build.

## The auto-shutdown hook

`GameManager.handleMatchEnd()` emits a dedicated, machine-readable lifecycle sentinel:

```java
plugin.getLogger().info("[TD-LIFECYCLE] ENDED matchId=" + match.getMatchId());
```

It sits immediately after the method's once-only guard (`finishedMatches.add(...)`), so it
fires **exactly once per match** and covers every live end path — castle defeat and forfeit,
single- and multiplayer — all of which converge on `handleMatchEnd` in the per-match model.
The container's `entrypoint.sh` watchdog tails `logs/latest.log` for `[TD-LIFECYCLE] ENDED`
and types `stop` into the server console, so the JVM shuts down and the container exits.

Tune the delay before shutdown with `-e MATCH_END_GRACE_SECONDS=15` (time for the post-match
stats screen / proxy to pull players back to the lobby before the JVM stops).

> The legacy global end paths (`handlePlayerDisconnect` via `matchQueue`, the 2-arg
> `damageCastle` → `setGameState(ENDED)`) are inert in a per-match server, so they don't carry
> the sentinel. If you ever run the old global game mode in a container, add a guarded copy of
> the line in `setGameState` when `newState == ENDED`.
