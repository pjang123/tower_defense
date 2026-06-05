package com.pauljang.towerDefense.setup;

import org.bukkit.Location;
import java.util.HashMap;
import java.util.UUID;

public class SetupManager {

    public enum SetupState {
        IDLE,               // Not currently setting up anything
        AWAITING_PLOT,      // Admin simply left-clicks to set the plot
        WAYPOINT_MODE,
        DELETING_PLOT
    }

    private final HashMap<UUID, SetupState> playerStates = new HashMap<>();
    private final HashMap<UUID, String> playerEditingArenas = new HashMap<>();
    private final HashMap<UUID, Integer> playerPlotSizes = new HashMap<>();
    private final HashMap<UUID, String> selectedWaypointIds = new HashMap<>();

    // --- State Management ---
    public SetupState getState(UUID uuid) {
        return playerStates.getOrDefault(uuid, SetupState.IDLE);
    }

    public void setState(UUID uuid, SetupState state) {
        playerStates.put(uuid, state);
    }

    public String getEditingArena(UUID playerUUID) {
        return playerEditingArenas.getOrDefault(playerUUID, "1");
    }

    public void setEditingArena(UUID playerUUID, String arena) {
        playerEditingArenas.put(playerUUID, arena);
    }

    // --- Plot Size Management ---
    public int getPlotSize(UUID uuid) {
        return playerPlotSizes.getOrDefault(uuid, 3); // Default to 3 (3x3)
    }

    public void setPlotSize(UUID uuid, int size) {
        playerPlotSizes.put(uuid, size);
    }

    // --- Selected Waypoint ID for Connections ---
    public String getSelectedWaypointId(UUID uuid) {
        return selectedWaypointIds.get(uuid);
    }

    public void setSelectedWaypointId(UUID uuid, String id) {
        selectedWaypointIds.put(uuid, id);
    }

    public void clearSelectedWaypointId(UUID uuid) {
        selectedWaypointIds.remove(uuid);
    }

    public void clearSetupData(UUID uuid) {
        playerStates.remove(uuid);
        playerEditingArenas.remove(uuid);
        playerPlotSizes.remove(uuid);
        selectedWaypointIds.remove(uuid);
    }
}