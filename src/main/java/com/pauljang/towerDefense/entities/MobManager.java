package com.pauljang.towerDefense.entities;

import com.pauljang.towerDefense.TowerDefense;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Mob;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class MobManager {

    private final TowerDefense plugin;
    private final List<TDMob> activeMobs = new ArrayList<>();

    public MobManager(TowerDefense plugin) {
        this.plugin = plugin;
        startMobTicker();
    }

    public void spawnMob(EntityType type) {
        List<Location> waypoints = plugin.getWaypointConfigManager().getWaypoints();
        if (waypoints.isEmpty()) {
            plugin.getLogger().warning("Cannot spawn mob: No waypoints defined!");
            return;
        }

        Location startLocation = waypoints.get(0);
        Mob entity = (Mob) startLocation.getWorld().spawnEntity(startLocation, type);
        
        // Mark as a TD Mob so we can handle events (like sunlight burning)
        entity.getPersistentDataContainer().set(new NamespacedKey(plugin, "td_mob"), PersistentDataType.BYTE, (byte) 1);

        TDMob tdMob = new TDMob(entity, waypoints);
        activeMobs.add(tdMob);
    }

    private void startMobTicker() {
        new BukkitRunnable() {
            @Override
            public void run() {
                Iterator<TDMob> iterator = activeMobs.iterator();
                while (iterator.hasNext()) {
                    TDMob mob = iterator.next();
                    
                    if (mob.getEntity().isDead() || !mob.getEntity().isValid()) {
                        iterator.remove();
                        continue;
                    }

                    handleMobMovement(mob, iterator);
                }
            }
        }.runTaskTimer(plugin, 0L, 10L); // Check every 10 ticks
    }

    private void handleMobMovement(TDMob mob, Iterator<TDMob> iterator) {
        Location target = mob.getNextWaypoint();
        
        if (target == null) {
            // Reached the end!
            // mob.getEntity().remove(); // Removed despawn logic
            iterator.remove(); // Stop tracking in activeMobs list so we don't keep checking it
            plugin.getLogger().info("A mob reached the final waypoint and is now idle.");
            // TODO: Deal damage to castle health here in Phase 2 Objective C
            return;
        }

        // Move the entity towards the target
        // We use the Paper Pathfinder API if possible
        mob.getEntity().getPathfinder().moveTo(target, 1.0);

        // Check if they are close enough to the waypoint to target the next one
        if (mob.getEntity().getLocation().distanceSquared(target) < 1.5) {
            mob.incrementWaypointIndex();
        }
    }
    
    public void cleanup() {
        for (TDMob mob : activeMobs) {
            mob.getEntity().remove();
        }
        activeMobs.clear();
    }
}
