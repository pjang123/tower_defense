/**
 * Phase 3 orchestration: stand up an ephemeral match server per match and tell the proxy where to
 * route its players.
 *
 * <p>Flow on the lobby server:
 * <ol>
 *   <li>A queue fills &rarr; build a {@link com.pauljang.towerDefense.orchestration.MatchRequest}.</li>
 *   <li>{@link com.pauljang.towerDefense.orchestration.MatchProvisioner#provision} stands the match
 *       up. {@link com.pauljang.towerDefense.orchestration.DockerMatchProvisioner} runs one container
 *       of the {@code docker/match-server} image and reads back its published port.</li>
 *   <li>Publish {@link com.pauljang.towerDefense.orchestration.MatchRouteMessage} on
 *       {@link com.pauljang.towerDefense.orchestration.MatchRouteMessage#CHANNEL} via
 *       {@link com.pauljang.towerDefense.orchestration.RedisPublisher}; the Velocity-side plugin
 *       registers the server and transfers the players.</li>
 *   <li>The container self-stops on the {@code [TD-LIFECYCLE] ENDED} sentinel (Phase 2 watchdog).</li>
 * </ol>
 *
 * <p>This package replaces the monolith's in-process world copying (TDCommand.copyDirectoryNIO /
 * GameManager world creation). It is decoupled from Bukkit and adds no third-party dependencies.
 * It is intentionally <em>not</em> wired into the live match flow yet — see {@code README.md} in
 * this package for the integration point.
 */
package com.pauljang.towerDefense.orchestration;
