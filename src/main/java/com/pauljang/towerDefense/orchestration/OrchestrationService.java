package com.pauljang.towerDefense.orchestration;

import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

/**
 * Glue that turns a "start this match" request into a provisioned container plus a proxy-route
 * signal. {@code GameManager.startMatch} delegates here when orchestration is enabled: the lobby
 * provisions an ephemeral match server (instead of cloning a world in-process), waits for it to be
 * reachable, and publishes a {@link MatchRouteMessage} so the Velocity proxy transfers the players.
 *
 * <p>Provisioning does blocking I/O (the docker CLI, a readiness poll, and a Redis socket) and
 * touches no Bukkit API, so it runs on a private daemon thread rather than the main server thread.
 * Disabled by default — when {@link #isEnabled()} is false this class is inert and the caller keeps
 * its existing local flow.
 */
public final class OrchestrationService {

    private final boolean enabled;
    private final String matchImage;
    private final String mapsDir;
    private final String velocitySecret;
    private final RedisPublisher redis;
    private final long readinessTimeoutSeconds;
    private final Logger log;

    private MatchProvisioner provisioner; // lazy
    private ExecutorService worker;       // lazy — only spun up when actually enabled and used

    public OrchestrationService(boolean enabled, String matchImage, String mapsDir, String velocitySecret,
                                String redisHost, int redisPort, String redisPassword,
                                long readinessTimeoutSeconds, Logger log) {
        this.enabled = enabled;
        this.matchImage = matchImage;
        this.mapsDir = mapsDir;
        this.velocitySecret = velocitySecret;
        this.readinessTimeoutSeconds = readinessTimeoutSeconds;
        this.log = log;
        String pw = (redisPassword == null || redisPassword.isEmpty()) ? null : redisPassword;
        this.redis = new RedisPublisher(redisHost, redisPort, pw, 3000);
    }

    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Asynchronously provisions a match server and publishes its route to the proxy. Returns
     * immediately; failures are logged, and a container that came up but couldn't be routed is reaped.
     */
    public void provisionAndRoute(String matchId, String mapId, boolean singlePlayer, List<UUID> players) {
        if (!enabled) return;
        List<UUID> roster = List.copyOf(players);
        worker().submit(() -> doProvisionAndRoute(matchId, mapId, singlePlayer, roster));
    }

    private void doProvisionAndRoute(String matchId, String mapId, boolean singlePlayer, List<UUID> players) {
        MatchInstance instance = null;
        try {
            instance = provisioner().provision(new MatchRequest(matchId, mapId, singlePlayer, players));
            long subscribers = redis.publish(MatchRouteMessage.CHANNEL,
                    MatchRouteMessage.of(instance, players).toJson());
            log.info("[orchestration] match " + matchId + " ready on " + instance.host() + ":" + instance.port()
                    + "; routed " + players.size() + " player(s) to " + subscribers + " proxy subscriber(s)");
        } catch (MatchProvisioner.ProvisionException e) {
            log.severe("[orchestration] could not provision match " + matchId + ": " + e.getMessage());
        } catch (IOException e) {
            log.severe("[orchestration] match " + matchId + " started but the route publish failed; reaping its "
                    + "container so it does not orphan: " + e.getMessage());
            if (instance != null) provisioner().shutdown(instance);
        }
    }

    /** Frees the background worker. Call from the plugin's onDisable. */
    public void close() {
        if (worker != null) worker.shutdownNow();
    }

    private synchronized MatchProvisioner provisioner() {
        if (provisioner == null) {
            provisioner = new DockerMatchProvisioner(matchImage, mapsDir, velocitySecret, readinessTimeoutSeconds, log);
        }
        return provisioner;
    }

    private synchronized ExecutorService worker() {
        if (worker == null) {
            worker = Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "td-orchestration");
                t.setDaemon(true);
                return t;
            });
        }
        return worker;
    }
}
