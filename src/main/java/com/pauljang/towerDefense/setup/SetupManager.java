package com.pauljang.towerDefense.setup;

import org.bukkit.Location;
import java.util.HashMap;
import java.util.UUID;

public class SetupManager {

    public enum SetupState {
        IDLE,               // Not currently setting up anything
        AWAITING_PLOT_P1,   // Waiting for the admin to click the first corner
        AWAITING_PLOT_P2,    // Waiting for the admin to click the second corner
        WAYPOINT_MODE
    }

    private final HashMap<UUID, SetupState> playerStates = new HashMap<>();
    private final HashMap<UUID, Location> pos1Selections = new HashMap<>();
    // We don't necessarily need to store Pos2 in the manager if we process it immediately,
    // but we can keep it for consistency.
    private final HashMap<UUID, Location> pos2Selections = new HashMap<>();

    // --- State Management ---
    public SetupState getState(UUID uuid) {
        return playerStates.getOrDefault(uuid, SetupState.IDLE);
    }

    public void setState(UUID uuid, SetupState state) {
        playerStates.put(uuid, state);
    }

    // --- Location Management ---
    public void setPos1(UUID playerUUID, Location loc) { pos1Selections.put(playerUUID, loc); }
    public Location getPos1(UUID playerUUID) { return pos1Selections.get(playerUUID); }

    public void setPos2(UUID playerUUID, Location loc) { pos2Selections.put(playerUUID, loc); }
    public Location getPos2(UUID playerUUID) { return pos2Selections.get(playerUUID); }

    public void clearSetupData(UUID uuid) {
        pos1Selections.remove(uuid);
        pos2Selections.remove(uuid);
        playerStates.remove(uuid);
    }
}