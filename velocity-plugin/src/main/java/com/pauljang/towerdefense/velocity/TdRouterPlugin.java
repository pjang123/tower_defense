package com.pauljang.towerdefense.velocity;

import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import com.velocitypowered.api.proxy.server.ServerInfo;
import org.slf4j.Logger;

import java.net.InetSocketAddress;
import java.util.Optional;
import java.util.UUID;

/**
 * Subscribes to the Tower Defense match-route channel on Redis and, for each "match ready" message
 * the lobby publishes, registers the new match server with the proxy and transfers the listed
 * players to it. This is the proxy half of the Phase 3 routing loop (the lobby half is
 * {@code com.pauljang.towerDefense.orchestration} in the Paper plugin).
 *
 * <p>Redis connection comes from env vars: {@code TD_REDIS_HOST} (default 127.0.0.1),
 * {@code TD_REDIS_PORT} (default 6379), {@code TD_REDIS_PASSWORD} (optional).
 */
@Plugin(id = "td-router", name = "TD Router", version = "0.1",
        description = "Routes Tower Defense match players to dynamically provisioned match servers.")
public final class TdRouterPlugin {

    private final ProxyServer proxy;
    private final Logger logger;
    private RedisSubscriber subscriber;

    @Inject
    public TdRouterPlugin(ProxyServer proxy, Logger logger) {
        this.proxy = proxy;
        this.logger = logger;
    }

    @Subscribe
    public void onProxyInit(ProxyInitializeEvent event) {
        String host = env("TD_REDIS_HOST", "127.0.0.1");
        int port = Integer.parseInt(env("TD_REDIS_PORT", "6379"));
        String password = System.getenv("TD_REDIS_PASSWORD"); // nullable -> no AUTH

        subscriber = new RedisSubscriber(host, port, password, MatchRouteMessage.CHANNEL, logger, this::onMatchReady);
        subscriber.start();
        logger.info("[td-router] subscribed to '{}' on {}:{}", MatchRouteMessage.CHANNEL, host, port);
    }

    @Subscribe
    public void onProxyShutdown(ProxyShutdownEvent event) {
        if (subscriber != null) subscriber.stop();
    }

    /** Registers the match server (idempotently) and connects each listed player that is online. */
    private void onMatchReady(MatchRouteMessage msg) {
        String name = "td-" + msg.matchId();
        ServerInfo info = new ServerInfo(name, new InetSocketAddress(msg.host(), msg.port()));
        RegisteredServer server = proxy.getServer(name).orElseGet(() -> proxy.registerServer(info));

        logger.info("[td-router] match {} ready at {}; routing {} player(s)",
                msg.matchId(), info.getAddress(), msg.players().size());

        for (UUID id : msg.players()) {
            Optional<Player> player = proxy.getPlayer(id);
            if (player.isPresent()) {
                player.get().createConnectionRequest(server).connect();
            } else {
                logger.warn("[td-router] player {} for match {} is not connected to the proxy; skipping",
                        id, msg.matchId());
            }
        }
    }

    private static String env(String key, String def) {
        String v = System.getenv(key);
        return (v == null || v.isEmpty()) ? def : v;
    }
}
