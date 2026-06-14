package com.pauljang.towerDefense.entities;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Mob;
import com.pauljang.towerDefense.data.TDWaypoint;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

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

    // Vex splitting: generation 0 = original. A vex splits (spawning one copy and incrementing both to
    // the next generation) when slowed/frozen/teleported, capped at generation 4 so a lineage tops out
    // at 16 leaf vexes that can no longer split. lastVexSplit debounces a single AoE pulse.
    private int vexGeneration = 0;
    private long lastVexSplit = 0L;

    // Evoker shield: a spinning ring of invisible armor stands that absorbs incoming damage as one HP
    // pool. When it hits 0 the ring vanishes and shieldRespawnAt is set to (now + 60s); the ticker
    // refills the shield and respawns the ring at that time. shieldAngle drives the slow rotation.
    private double shieldHp = 0.0;
    private double shieldMaxHp = 0.0;
    private long shieldRespawnAt = 0L;
    private double shieldAngle = 0.0;
    private final List<UUID> shieldStands = new ArrayList<>();

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

    // --- Vex splitting ---
    public int getVexGeneration() { return vexGeneration; }
    public void setVexGeneration(int vexGeneration) { this.vexGeneration = vexGeneration; }
    public long getLastVexSplit() { return lastVexSplit; }
    public void setLastVexSplit(long lastVexSplit) { this.lastVexSplit = lastVexSplit; }

    // --- Evoker shield ---
    public double getShieldHp() { return shieldHp; }
    public void setShieldHp(double shieldHp) { this.shieldHp = shieldHp; }
    public double getShieldMaxHp() { return shieldMaxHp; }
    public void setShieldMaxHp(double shieldMaxHp) { this.shieldMaxHp = shieldMaxHp; }
    public long getShieldRespawnAt() { return shieldRespawnAt; }
    public void setShieldRespawnAt(long shieldRespawnAt) { this.shieldRespawnAt = shieldRespawnAt; }
    public double getShieldAngle() { return shieldAngle; }
    public void setShieldAngle(double shieldAngle) { this.shieldAngle = shieldAngle; }
    public List<UUID> getShieldStands() { return shieldStands; }
    public boolean isShieldActive() { return shieldHp > 0.0; }
}