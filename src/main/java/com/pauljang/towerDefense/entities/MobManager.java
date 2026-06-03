package com.pauljang.towerDefense.entities;

import com.pauljang.towerDefense.TowerDefense;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
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

        // Make the mob immune to knockback via attributes
        org.bukkit.attribute.AttributeInstance kbResist = entity.getAttribute(Attribute.KNOCKBACK_RESISTANCE);
        if (kbResist != null) {
            kbResist.setBaseValue(1.0);
        }

        // Initialize healthbar
        updateHealthBar(entity);

        TDMob tdMob = new TDMob(entity, waypoints);
        activeMobs.add(tdMob);
    }

    public void updateHealthBar(Mob mob) {
        org.bukkit.attribute.AttributeInstance maxHealthAttr = mob.getAttribute(Attribute.MAX_HEALTH);
        double maxHealth = maxHealthAttr != null ? maxHealthAttr.getValue() : mob.getHealth();
        double health = mob.getHealth();
        double ratio = Math.max(0.0, Math.min(1.0, health / maxHealth));

        int totalBars = 10;
        int greenBars = (int) Math.round(ratio * totalBars);
        int grayBars = totalBars - greenBars;

        org.bukkit.ChatColor color;
        if (ratio >= 0.6) {
            color = org.bukkit.ChatColor.GREEN;
        } else if (ratio >= 0.25) {
            color = org.bukkit.ChatColor.YELLOW;
        } else {
            color = org.bukkit.ChatColor.RED;
        }

        StringBuilder bar = new StringBuilder();
        bar.append(color);
        for (int i = 0; i < greenBars; i++) {
            bar.append("■");
        }
        bar.append(org.bukkit.ChatColor.GRAY);
        for (int i = 0; i < grayBars; i++) {
            bar.append("■");
        }

        mob.setCustomName(bar.toString());
        mob.setCustomNameVisible(true);
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
