package com.pauljang.towerDefense.towers;

import com.pauljang.towerDefense.TowerDefense;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Mob;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.Map;

public class TowerManager {

    private final TowerDefense plugin;
    private final Map<String, Tower> placedTowers = new HashMap<>();

    public TowerManager(TowerDefense plugin) {
        this.plugin = plugin;
        startTowerTicker();
    }

    public boolean hasTower(String plotId) {
        return placedTowers.containsKey(plotId);
    }

    public void placeTower(String plotId, TowerType type) {
        // Remove existing tower if any
        if (hasTower(plotId)) {
            removeTower(plotId);
        }

        Location center = plugin.getPlotConfigManager().getPlotCenter(plotId);
        if (center == null) return;

        // Place physical tower block
        center.getBlock().setType(type.getBlockMaterial());

        // Spawn holographic text using ArmorStand
        Location holoLoc = center.clone().add(0, 1.2, 0);
        ArmorStand hologram = center.getWorld().spawn(holoLoc, ArmorStand.class, as -> {
            as.setVisible(false);
            as.setGravity(false);
            as.setMarker(true);
            as.setCustomName(type.getColor() + type.getDisplayName() + " (Lvl 1)");
            as.setCustomNameVisible(true);
        });

        Tower tower = new Tower(plotId, center, type);
        tower.setHologram(hologram);
        placedTowers.put(plotId, tower);
    }

    public void removeTower(String plotId) {
        Tower tower = placedTowers.remove(plotId);
        if (tower != null) {
            // Revert center block to grass/air or whatever default
            tower.getCenterLocation().getBlock().setType(Material.GRASS_BLOCK);
            
            // Clean up hologram ArmorStand
            if (tower.getHologram() != null && tower.getHologram().isValid()) {
                tower.getHologram().remove();
            }
        }
    }

    private void startTowerTicker() {
        new BukkitRunnable() {
            private long tick = 0;

            @Override
            public void run() {
                // Only attack when game is active
                if (plugin.getGameManager().getCurrentState() != com.pauljang.towerDefense.core.GameState.ACTIVE) {
                    return;
                }

                for (Tower tower : placedTowers.values()) {
                    if (tick - tower.getLastAttackTick() >= tower.getType().getCooldown()) {
                        Mob target = findTarget(tower);
                        if (target != null) {
                            shootTarget(tower, target);
                            tower.setLastAttackTick(tick);
                        }
                    }
                }
                tick++;
            }
        }.runTaskTimer(plugin, 0L, 1L); // Tick every 50ms
    }

    private Mob findTarget(Tower tower) {
        Mob bestTarget = null;
        double bestProgress = -1;

        Location towerLoc = tower.getCenterLocation();
        double rangeSquared = Math.pow(tower.getType().getRange(), 2);

        for (com.pauljang.towerDefense.entities.TDMob tdMob : plugin.getMobManager().getActiveMobs()) {
            Mob mob = tdMob.getEntity();
            if (mob.isDead() || !mob.isValid()) continue;

            // Prevent task crash by ensuring entities are in the same world before distance check
            if (!mob.getWorld().equals(towerLoc.getWorld())) continue;

            double distSq = mob.getLocation().distanceSquared(towerLoc);
            if (distSq <= rangeSquared) {
                // Calculate progress value: higher index and closer to next waypoint means higher priority (First targeting)
                double progress = tdMob.getCurrentWaypointIndex() * 10000.0;
                Location nextWp = tdMob.getNextWaypoint();
                if (nextWp != null && mob.getWorld().equals(nextWp.getWorld())) {
                    progress -= mob.getLocation().distance(nextWp);
                }

                if (progress > bestProgress) {
                    bestProgress = progress;
                    bestTarget = mob;
                }
            }
        }
        return bestTarget;
    }

    private void shootTarget(Tower tower, Mob target) {
        Location start = tower.getCenterLocation().clone().add(0, 1.5, 0); // shoot from top of the block
        Location end = target.getEyeLocation();

        // 1. Damage target
        target.damage(tower.getType().getDamage());

        // 2. Play particle and sound effects based on tower type
        org.bukkit.Particle particle = org.bukkit.Particle.CRIT;
        Sound sound = Sound.ENTITY_ARROW_SHOOT;

        switch (tower.getType()) {
            case MAGE -> {
                particle = org.bukkit.Particle.FLAME;
                sound = Sound.ENTITY_BLAZE_SHOOT;
            }
            case FROST -> {
                particle = org.bukkit.Particle.SNOWFLAKE;
                sound = Sound.BLOCK_GLASS_BREAK;
                // Apply slowness effect (slow-immune listener will cancel it if applicable)
                target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, 1));
            }
        }

        drawParticleLine(start, end, particle);
        start.getWorld().playSound(start, sound, 0.8f, 1.2f);
    }

    private void drawParticleLine(Location start, Location end, org.bukkit.Particle particle) {
        double distance = start.distance(end);
        org.bukkit.util.Vector direction = end.toVector().subtract(start.toVector()).normalize();

        for (double d = 0; d < distance; d += 0.25) {
            Location point = start.clone().add(direction.clone().multiply(d));
            start.getWorld().spawnParticle(particle, point, 1, 0.0, 0.0, 0.0, 0.0);
        }
    }

    public void cleanup() {
        for (Tower tower : placedTowers.values()) {
            if (tower.getHologram() != null && tower.getHologram().isValid()) {
                tower.getHologram().remove();
            }
        }
        placedTowers.clear();
    }
}
