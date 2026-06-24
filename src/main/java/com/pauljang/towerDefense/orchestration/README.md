# Orchestration (Phase 3)

Replaces the monolith's in-process world copying with "one ephemeral container per match", and
signals the Velocity proxy where to send the players. Pure JDK — no Docker-Java, Jedis, or Bukkit
dependencies, so it compiles into the existing plugin without touching `pom.xml`.

## Pieces

| Class | Phase 3 deliverable |
|-------|---------------------|
| `MatchProvisioner` | The abstraction layer that replaces `TDCommand.copyDirectoryNIO` / local world creation. |
| `DockerMatchProvisioner` | `docker run` the match-server image on queue-fill; reads back the published port. |
| `MatchRouteMessage` + `RedisPublisher` | The Redis pub/sub payload + transport that tells Velocity to route players. |
| `MatchRequest` / `MatchInstance` | Request/result DTOs. |

## Flow

```
queue fills ─▶ MatchRequest ─▶ provisioner.provision() ─▶ MatchInstance(host, port)
                                      │ (docker run -P; docker port)
                                      ▼
        RedisPublisher.publish(MatchRouteMessage.CHANNEL, msg.toJson())
                                      │
                                      ▼
        Velocity plugin registers host:port and transfers the players
                                      │
        match plays out ... GameManager logs [TD-LIFECYCLE] ENDED
                                      ▼
        container watchdog stops the server → container exits (Phase 2)
```

## Integration point (wired, default-off)

`OrchestrationService` ties provision → readiness-wait → publish together, and
`GameManager.startMatch` delegates to it when `orchestration.enabled` is true — otherwise the
classic in-process world-copy flow runs unchanged. Everything is driven from the `orchestration:`
block in `config.yml`; the blocking work runs on a private daemon thread.

```java
// GameManager.startMatch(...), gated:
if (orchestration.isEnabled()) {
    orchestration.provisionAndRoute(matchId, mapData.getId(), mapData.isSinglePlayer(), playerIds);
    return; // a container plays the match; the proxy moves the players
}
```

## Status

Done:
- Provision (`docker run -P`) + read the published port, with a **TCP readiness probe** so the proxy
  is told only once the match server actually accepts connections.
- **Container reaping** if a started container can't be made reachable or the route publish fails.
- **Maps bind-mounted** read-only from `orchestration.maps-dir` (edit on the host, no rebuild).
- **Velocity forwarding secret** passed through to the container (`orchestration.velocity-secret`).
- **Velocity-side consumer** scaffolded in `velocity-plugin/` (not compile-verified offline).

Done (match-server side):
- The container **auto-starts the specific match it was provisioned for**. On boot the plugin reads
  `TD_MATCH_ID` / `TD_MAP_ID` (`GameManager.maybeStartAsMatchServer`, called from `onEnable`),
  resolves the `MapData`, clones that one map and waits. The Velocity proxy transfers the routed
  players in and each is slotted into the match as they connect (`MobListener.onPlayerJoin` →
  `GameManager.handleMatchServerJoin`); the first arrival wakes the match (ACTIVE + build-phase grace
  + single-player waves). It then ends via the normal `[TD-LIFECYCLE] ENDED` path, so the watchdog
  stops the server. All gated on the env vars, so the single-server lobby flow is untouched.

Still required for a real deployment:
- A live Redis + Docker host, and the match image built (`docker/match-server`).
- Passing the routed players' **UUIDs** to the container (today only `matchId`/`mapId` are sent), so
  the match can pre-assign teams and know its full roster instead of activating on the first join.
- Verifying the partial `paper-global.yml` against your Paper build (see `docker/match-server`).
