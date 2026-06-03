package com.pauljang.towerDefense.entities;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Mob;
import java.util.List;

public class TDMob {

    private final Mob entity;
    private final List<Location> waypoints;
    private int currentWaypointIndex = 0;

    public TDMob(Mob entity, List<Location> waypoints) {
        this.entity = entity;
        this.waypoints = waypoints;

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

    public Location getNextWaypoint() {
        if (currentWaypointIndex >= waypoints.size()) return null;
        return waypoints.get(currentWaypointIndex);
    }

    public boolean hasReachedFinalWaypoint() {
        return currentWaypointIndex >= waypoints.size();
    }
}