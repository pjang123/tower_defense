package com.pauljang.towerDefense.orchestration;

/**
 * A provisioned match server — what a {@link MatchProvisioner} hands back once the match is reachable.
 *
 * @param matchId     the originating {@link MatchRequest#matchId()}
 * @param containerId the Docker container id (empty for non-container providers)
 * @param host        host/ip players connect through
 * @param port        the port the container's 25565 was dynamically published to
 */
public record MatchInstance(String matchId, String containerId, String host, int port) {

    public MatchInstance {
        if (matchId == null || matchId.isBlank()) throw new IllegalArgumentException("matchId required");
        if (host == null || host.isBlank()) throw new IllegalArgumentException("host required");
        if (port <= 0 || port > 65535) throw new IllegalArgumentException("port out of range: " + port);
    }
}
