package com.pauljang.towerDefense.entities;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Mob;
import com.pauljang.towerDefense.data.TDWaypoint;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class TDMob {

    private final Mob entity;
    private final Map<String, TDWaypoint> waypointGraph;
    private String currentWaypointId;
    private final List<String> pathHistory = new ArrayList<>();
    private String lastPathfindWaypointId = null;
    private long lastAttackTick = 0;
    private long lastCastleAttackTime = 0L;
    private int tier = 1;
    private String arena = "1";
    private final Location finalOffsetWaypoint;
    private boolean settledAtEnd = false; // true once the mob has walked to its fan-out spot at the end
    private int endFanTicks = 0; // ticks spent trying to reach the fan-out spot (settle timeout safety net)
    private long teleportedUntil = 0; // Timestamp to pause movement after Chorus teleport

    public TDMob(Mob entity, Map<String, TDWaypoint> waypointGraph) {
        this.entity = entity;
        this.waypointGraph = waypointGraph;

        // Start at waypoint "0"
        this.currentWaypointId = "0";
        this.pathHistory.add("0");

        // Random offset for final waypoint (find an end waypoint - i.e. a waypoint with no outgoing connections)
        String endId = findEndWaypointId();
        if (endId != null && waypointGraph.containsKey(endId)) {
            Location last = waypointGraph.get(endId).getLocation();
            double angle = Math.random() * 2 * Math.PI;
            double radius = 1.0 + Math.random() * 2.5; // 1.0–3.5 blocks: mobs fan out around the door
            double offsetX = Math.cos(angle) * radius;
            double offsetZ = Math.sin(angle) * radius;
            this.finalOffsetWaypoint = last.clone().add(offsetX, 0, offsetZ);
        } else {
            this.finalOffsetWaypoint = null;
        }

        Bukkit.getMobGoals().removeAllGoals(entity);
    }

    private String findEndWaypointId() {
        for (TDWaypoint wp : waypointGraph.values()) {
            if (wp.getNextIds().isEmpty()) {
                return wp.getId();
            }
        }
        return null;
    }

    public Mob getEntity() {
        return entity;
    }

    public String getCurrentWaypointId() {
        return currentWaypointId;
    }

    public void setCurrentWaypointId(String currentWaypointId) {
        this.currentWaypointId = currentWaypointId;
        if (currentWaypointId != null && !pathHistory.contains(currentWaypointId)) {
            pathHistory.add(currentWaypointId);
        }
    }

    public List<String> getPathHistory() {
        return pathHistory;
    }

    /** Replaces this mob's visited-waypoint history (used when a summoned mob inherits a summoner's path). */
    public void setPathHistory(List<String> history) {
        this.pathHistory.clear();
        if (history != null) {
            this.pathHistory.addAll(history);
        }
    }

    public void advanceToNextWaypoint() {
        if (currentWaypointId == null) return;
        TDWaypoint current = waypointGraph.get(currentWaypointId);
        if (current == null) return;

        List<String> nextIds = current.getNextIds();
        if (nextIds.isEmpty()) {
            currentWaypointId = null; // Reached end
        } else {
            // Select a random next waypoint (implements SPLIT)
            int index = (int) (Math.random() * nextIds.size());
            String nextId = nextIds.get(index);
            currentWaypointId = nextId;
            pathHistory.add(nextId);
        }
    }

    public Location getNextWaypoint() {
        if (currentWaypointId == null) {
            return finalOffsetWaypoint;
        }
        TDWaypoint wp = waypointGraph.get(currentWaypointId);
        return wp != null ? wp.getLocation() : null;
    }

    public boolean hasReachedFinalWaypoint() {
        return currentWaypointId == null;
    }

    public String getLastPathfindWaypointId() {
        return lastPathfindWaypointId;
    }

    public void setLastPathfindWaypointId(String lastPathfindWaypointId) {
        this.lastPathfindWaypointId = lastPathfindWaypointId;
    }

    public long getLastAttackTick() {
        return lastAttackTick;
    }

    public void setLastAttackTick(long lastAttackTick) {
        this.lastAttackTick = lastAttackTick;
    }

    public long getLastCastleAttackTime() {
        return lastCastleAttackTime;
    }

    public void setLastCastleAttackTime(long lastCastleAttackTime) {
        this.lastCastleAttackTime = lastCastleAttackTime;
    }

    public int getTier() {
        return tier;
    }

    public void setTier(int tier) {
        this.tier = tier;
    }

    public String getArena() {
        return arena;
    }

    public void setArena(String arena) {
        this.arena = arena;
    }

    public Location getFinalOffsetWaypoint() {
        return finalOffsetWaypoint;
    }

    public boolean isSettledAtEnd() {
        return settledAtEnd;
    }

    public void setSettledAtEnd(boolean settledAtEnd) {
        this.settledAtEnd = settledAtEnd;
    }

    public int incrementEndFanTicks() {
        return ++endFanTicks;
    }

    /**
     * Sends the mob back to the first waypoint to march the track again (used to loop the Armageddon
     * Wither so its huge hitbox never camps the castle door and block players hitting other mobs).
     */
    public void resetToStart() {
        this.currentWaypointId = "0";
        this.settledAtEnd = false;
        this.endFanTicks = 0;
    }

    public Map<String, TDWaypoint> getWaypointGraph() {
        return waypointGraph;
    }

    public long getTeleportedUntil() {
        return teleportedUntil;
    }

    public void setTeleportedUntil(long teleportedUntil) {
        this.teleportedUntil = teleportedUntil;
    }

    public boolean isTeleported() {
        return System.currentTimeMillis() < teleportedUntil;
    }
}