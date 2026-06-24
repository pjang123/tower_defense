# TD Router — Velocity consumer (Phase 3)

The proxy half of match routing. Runs on the Velocity proxy, subscribes to the Redis channel the
lobby publishes to (`td:match:route`), and for each "match ready" message registers the new match
server and transfers the listed players to it.

Counterpart to `com.pauljang.towerDefense.orchestration` in the Paper plugin (the producer).

```
lobby (Paper plugin)                         proxy (this plugin)
  provision container ─▶ RedisPublisher ──▶  RedisSubscriber ─▶ registerServer + connect players
       (td:match:route, MatchRouteMessage JSON)
```

## ⚠️ Not compile-verified here

This module depends on `velocity-api`, which is **not** in this repo's offline Maven cache, so it was
**not** built/compiled in-repo (unlike the Paper-side code, which is). Before relying on it:

- Set `velocity-api` in `pom.xml` to the version matching your proxy.
- `mvn -f velocity-plugin/pom.xml package` and resolve any API drift (the Velocity API for
  `registerServer` / `createConnectionRequest` / event names is stable across 3.x, but verify).

The pure-JDK parts (`MatchRouteMessage` JSON parsing, `RedisSubscriber` RESP decoding) mirror the
Paper-side logic, which **is** verified in-repo (round-trip + RESP tests).

## Build & install

```bash
mvn -f velocity-plugin/pom.xml package
# drop target/td-router-0.1-SNAPSHOT.jar into the proxy's plugins/ folder
```

`@Plugin` is processed by velocity-api's annotation processor, which generates the
`velocity-plugin.json` descriptor — no manual descriptor needed.

## Configuration (env vars on the proxy)

| Var | Default | Meaning |
|-----|---------|---------|
| `TD_REDIS_HOST` | `127.0.0.1` | Redis host |
| `TD_REDIS_PORT` | `6379` | Redis port |
| `TD_REDIS_PASSWORD` | _(none)_ | Redis AUTH password, if any |

Must point at the same Redis the Paper lobby publishes to (`orchestration.redis.*` in the plugin
`config.yml`).

## Still required for a full loop

- The lobby now **waits for the match server to accept connections before publishing** (readiness
  probe), so this plugin can register and connect immediately. A brief `connect()` retry here is
  still worthwhile hardening against races.
- The **forwarding secret** is wired end-to-end (lobby `orchestration.velocity-secret` → match-server
  `paper-global.yml`); this proxy's `velocity.toml` must use `modern` forwarding with the same secret.
- The match-server container does not yet **auto-start the provisioned match** — players routed in
  currently land in that server's lobby (see `orchestration/README.md`).
