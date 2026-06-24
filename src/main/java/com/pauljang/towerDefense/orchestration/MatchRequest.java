package com.pauljang.towerDefense.orchestration;

import java.util.List;
import java.util.UUID;

/**
 * Immutable description of a match that needs a server. Built by the lobby when a queue fills and
 * handed to a {@link MatchProvisioner} to stand the match up.
 *
 * @param matchId      stable id shared across the lobby, the container, and Redis routing
 * @param mapId        map template id (the {@code GAME_WORLD_TEMPLATES/<sub>/<id>} folder name)
 * @param singlePlayer whether this is a single-player match
 * @param players      players to route into the match once it boots
 */
public record MatchRequest(String matchId, String mapId, boolean singlePlayer, List<UUID> players) {

    public MatchRequest {
        if (matchId == null || matchId.isBlank()) throw new IllegalArgumentException("matchId required");
        if (mapId == null || mapId.isBlank()) throw new IllegalArgumentException("mapId required");
        players = players == null ? List.of() : List.copyOf(players);
    }
}
