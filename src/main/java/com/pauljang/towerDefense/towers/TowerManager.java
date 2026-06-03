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
import org.bukkit.structure.Structure;
import org.bukkit.util.BlockVector;
import org.bukkit.block.structure.StructureRotation;
import org.bukkit.block.structure.Mirror;
import java.io.File;
import java.util.Random;

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

        // Try loading NBT structure
        File structureFile = new File(plugin.getDataFolder(), "structures/" + type.name().toLowerCase() + ".nbt");
        Structure structure = null;
        if (structureFile.exists()) {
            try {
                structure = org.bukkit.Bukkit.getStructureManager().loadStructure(structureFile);
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to load structure file: " + structureFile.getName() + " - " + e.getMessage());
            }
        }

        Tower tower = new Tower(plotId, center, type);
        double holoHeight = 4.2; // Default height for 3-block multiblock shifted 1 block up
        
        if (structure != null) {
            BlockVector size = structure.getSize();
            tower.setStructureSize(size);
            
            int sizeX = size.getBlockX();
            int sizeY = size.getBlockY();
            int sizeZ = size.getBlockZ();
            
            // Center the structure on the plot center coordinate, shifted 1 block up (keeps floor)
            Location placementLoc = center.clone().subtract(sizeX / 2, 0, sizeZ / 2).add(0, 1, 0);
            try {
                structure.place(placementLoc, false, StructureRotation.NONE, Mirror.NONE, 0, 1.0f, new Random());
            } catch (Exception e) {
                plugin.getLogger().severe("Failed to place structure " + type.name() + ": " + e.getMessage());
                e.printStackTrace();
            }
            
            holoHeight = sizeY + 1.2;
        } else {
            // Fallback to the 3-block-tall multiblock tower shifted 1 block up
            center.clone().add(0, 1, 0).getBlock().setType(type.getBaseMaterial());
            center.clone().add(0, 2, 0).getBlock().setType(type.getMiddleMaterial());
            center.clone().add(0, 3, 0).getBlock().setType(type.getBlockMaterial());
        }

        // Spawn holographic text using ArmorStand
        Location holoLoc = center.clone().add(0, holoHeight, 0); // Positioned above the tower
        ArmorStand hologram = center.getWorld().spawn(holoLoc, ArmorStand.class, as -> {
            as.setVisible(false);
            as.setGravity(false);
            as.setMarker(true);
            as.setCustomName(type.getColor() + type.getDisplayName() + " (Lvl 1)");
            as.setCustomNameVisible(true);
        });

        tower.setHologram(hologram);
        placedTowers.put(plotId, tower);
    }

    public void removeTower(String plotId) {
        Tower tower = placedTowers.remove(plotId);
        if (tower != null) {
            Location center = tower.getCenterLocation();
            if (tower.getStructureSize() != null) {
                BlockVector size = tower.getStructureSize();
                int sizeX = size.getBlockX();
                int sizeY = size.getBlockY();
                int sizeZ = size.getBlockZ();
                // Clear the bounding box starting 1 block up
                Location placementLoc = center.clone().subtract(sizeX / 2, 0, sizeZ / 2).add(0, 1, 0);
                for (int dx = 0; dx < sizeX; dx++) {
                    for (int dy = 0; dy < sizeY; dy++) {
                        for (int dz = 0; dz < sizeZ; dz++) {
                            Location blockLoc = placementLoc.clone().add(dx, dy, dz);
                            blockLoc.getBlock().setType(Material.AIR);
                        }
                    }
                }
            } else {
                // Revert all 3 tiers starting 1 block up (keeps floor)
                center.clone().add(0, 1, 0).getBlock().setType(Material.AIR);
                center.clone().add(0, 2, 0).getBlock().setType(Material.AIR);
                center.clone().add(0, 3, 0).getBlock().setType(Material.AIR);
            }
            
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
        
        String towerArena = plugin.getPlotConfigManager().getPlotArena(tower.getPlotId());

        for (com.pauljang.towerDefense.entities.TDMob tdMob : plugin.getMobManager().getActiveMobs()) {
            Mob mob = tdMob.getEntity();
            if (mob.isDead() || !mob.isValid()) continue;

            // Prevent task crash by ensuring entities are in the same world before distance check
            if (!mob.getWorld().equals(towerLoc.getWorld())) continue;

            // Prevent targeting mobs on different arena tracks
            String mobArena = mob.getPersistentDataContainer().get(
                new org.bukkit.NamespacedKey(plugin, "td_arena"),
                org.bukkit.persistence.PersistentDataType.STRING
            );
            if (mobArena == null) mobArena = "1";
            if (!towerArena.equals(mobArena)) continue;

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
        double height = 2.5; // Default for 3-block multiblock
        if (tower.getStructureSize() != null) {
            height = tower.getStructureSize().getBlockY() - 0.5;
        }
        Location start = tower.getCenterLocation().clone().add(0, height, 0);
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
