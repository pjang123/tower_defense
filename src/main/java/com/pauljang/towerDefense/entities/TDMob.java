package com.pauljang.towerDefense.entities;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Mob;
import java.util.List;

public class TDMob {

    private final Mob entity;
    private final List<Location> waypoints;
    private int currentWaypointIndex = 0;
    private int lastPathfindWaypointIndex = -1;
    private long lastAttackTick = 0;
    private final Location finalOffsetWaypoint;

    public TDMob(Mob entity, List<Location> waypoints) {
        this.entity = entity;
        this.waypoints = waypoints;

        if (!waypoints.isEmpty()) {
            Location last = waypoints.get(waypoints.size() - 1);
            // Random offset within a circle of radius 2.0 to spread out
            double angle = Math.random() * 2 * Math.PI;
            double radius = Math.random() * 2.0; // max 2 blocks spread out
            double offsetX = Math.cos(angle) * radius;
            double offsetZ = Math.sin(angle) * radius;
            this.finalOffsetWaypoint = last.clone().add(offsetX, 0, offsetZ);
        } else {
            this.finalOffsetWaypoint = null;
        }

        // Modern Spigot/Paper API way to wipe all vanilla AI goals
        // (stops wandering, targeting players, etc.)
        Bukkit.getMobGoals().removeAllGoals(entity);
    }

    public Mob getEntity() {
        return entity;
    }

    public int getCurrentWaypointIndex() {
        return currentWaypointIndex;
    }

    public void incrementWaypointIndex() {
        currentWaypointIndex++;
    }

    public void setCurrentWaypointIndex(int currentWaypointIndex) {
        this.currentWaypointIndex = currentWaypointIndex;
    }

    public Location getNextWaypoint() {
        if (currentWaypointIndex >= waypoints.size()) return null;
        if (currentWaypointIndex == waypoints.size() - 1) {
            return finalOffsetWaypoint;
        }
        return waypoints.get(currentWaypointIndex);
    }

    public boolean hasReachedFinalWaypoint() {
        return currentWaypointIndex >= waypoints.size();
    }

    public int getLastPathfindWaypointIndex() {
        return lastPathfindWaypointIndex;
    }

    public void setLastPathfindWaypointIndex(int lastPathfindWaypointIndex) {
        this.lastPathfindWaypointIndex = lastPathfindWaypointIndex;
    }

    public long getLastAttackTick() {
        return lastAttackTick;
    }

    public void setLastAttackTick(long lastAttackTick) {
        this.lastAttackTick = lastAttackTick;
    }

    public Location getFinalOffsetWaypoint() {
        return finalOffsetWaypoint;
    }
}