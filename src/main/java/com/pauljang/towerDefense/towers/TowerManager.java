package com.pauljang.towerDefense.towers;

import com.pauljang.towerDefense.TowerDefense;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.ChatColor;
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

        // Clean up only ghost armor stands (not belonging to any currently placed tower) within a 5-block radius
        center.getWorld().getNearbyEntities(center, 5.0, 5.0, 5.0).stream()
            .filter(e -> e instanceof ArmorStand)
            .filter(as -> {
                for (Tower activeTower : placedTowers.values()) {
                    if (activeTower.getHolograms().contains(as)) {
                        return false;
                    }
                }
                return true;
            })
            .forEach(org.bukkit.entity.Entity::remove);

        Tower tower = new Tower(plotId, center, type);
        placedTowers.put(plotId, tower);

        buildTowerStructure(tower);
        updateHologram(tower);

        if (type == TowerType.REDSTONE) {
            updateAllTowerHologramsInArena(plugin.getPlotConfigManager().getPlotArena(plotId));
        }
    }

    public void buildTowerStructure(Tower tower) {
        Location center = tower.getCenterLocation();
        TowerType type = tower.getType();
        int level = tower.getLevel();

        // 1. Clear old structure blocks if they exist
        clearTowerBlocks(tower);

        // 2. Try loading level-specific NBT structure (e.g., structures/archer_1.nbt)
        String fileName = type.name().toLowerCase();
        if (type == TowerType.HAPPY_GHAST) {
            fileName = "happy";
        }
        File levelSpecificFile = new File(plugin.getDataFolder(), "structures/" + fileName + "_" + level + ".nbt");
        File genericFile = new File(plugin.getDataFolder(), "structures/" + fileName + ".nbt");
        File targetFile = levelSpecificFile.exists() ? levelSpecificFile : (genericFile.exists() ? genericFile : null);
        
        Structure structure = null;
        if (targetFile != null) {
            try {
                structure = org.bukkit.Bukkit.getStructureManager().loadStructure(targetFile);
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to load structure file: " + targetFile.getName() + " - " + e.getMessage());
            }
        }

        if (structure != null) {
            BlockVector size = structure.getSize();
            tower.setStructureSize(size);
            
            int sizeX = size.getBlockX();
            int sizeY = size.getBlockY();
            int sizeZ = size.getBlockZ();
            
            Location placementLoc = center.clone().subtract(sizeX / 2, 0, sizeZ / 2).add(0, 1, 0);
            try {
                structure.place(placementLoc, false, StructureRotation.NONE, Mirror.NONE, 0, 1.0f, new Random());
            } catch (Exception e) {
                plugin.getLogger().severe("Failed to place structure " + type.name() + " (Level " + level + "): " + e.getMessage());
                e.printStackTrace();
            }
        } else {
            // Fallback default 3-block multiblock
            tower.setStructureSize(null);
            org.bukkit.Material baseMat = type.getBaseMaterial();
            org.bukkit.Material midMat = type.getMiddleMaterial();
            org.bukkit.Material topMat = type.getBlockMaterial();
            
            center.clone().add(0, 1, 0).getBlock().setType(baseMat.isBlock() ? baseMat : org.bukkit.Material.SOUL_SAND, false);
            center.clone().add(0, 2, 0).getBlock().setType(midMat.isBlock() ? midMat : org.bukkit.Material.NETHERRACK, false);
            center.clone().add(0, 3, 0).getBlock().setType(topMat.isBlock() ? topMat : org.bukkit.Material.RED_NETHER_BRICKS, false);
        }
        
        isolateTowerBlocks(center, tower.getStructureSize());
        clearNearbyDroppedItemsLater(center);

        // Handle Golem and Happy Ghast entity spawns
        if (type == TowerType.GOLEM) {
            if (tower.getSpawnedGolem() != null && tower.getSpawnedGolem().isValid()) {
                tower.getSpawnedGolem().remove();
            }
            
            String towerArena = plugin.getPlotConfigManager().getPlotArena(tower.getPlotId());
            java.util.List<Location> wps = plugin.getWaypointConfigManager().getWaypoints(towerArena);
            Location spawnLoc = (!wps.isEmpty()) ? wps.get(0).clone().add(0, 1.0, 0) : center.clone().add(0, 1.5, 0);
            
            org.bukkit.entity.LivingEntity golem = null;
            if (level == 1) {
                try {
                    org.bukkit.entity.EntityType typeEnum = org.bukkit.entity.EntityType.valueOf("COPPER_GOLEM");
                    org.bukkit.entity.Entity spawnedEnt = center.getWorld().spawn(spawnLoc, typeEnum.getEntityClass());
                    if (spawnedEnt instanceof org.bukkit.entity.LivingEntity le) {
                        golem = le;
                        if (golem instanceof org.bukkit.entity.Mob m) {
                            m.setAI(true);
                        }
                        golem.setInvulnerable(true);
                        golem.setGravity(true);
                        golem.setCollidable(false);
                        golem.setPersistent(true);
                        golem.setCustomName(org.bukkit.ChatColor.GOLD + "Copper Golem");
                        golem.setCustomNameVisible(true);
                    }
                } catch (Exception ignored) {
                    golem = center.getWorld().spawn(spawnLoc, org.bukkit.entity.IronGolem.class, g -> {
                        g.setAI(true);
                        g.setInvulnerable(true);
                        g.setGravity(true);
                        g.setCollidable(false);
                        g.setPersistent(true);
                        g.setCustomName(org.bukkit.ChatColor.GOLD + "Copper Golem");
                        setEntityScale(g, 0.6);
                        g.setCustomNameVisible(true);
                    });
                }
            } else {
                golem = center.getWorld().spawn(spawnLoc, org.bukkit.entity.IronGolem.class, g -> {
                    g.setAI(true);
                    g.setInvulnerable(true);
                    g.setGravity(true);
                    g.setCollidable(false);
                    g.setPersistent(true);
                    g.setCustomName(org.bukkit.ChatColor.GRAY + "Iron Golem");
                    setEntityScale(g, 1.0);
                    g.setCustomNameVisible(true);
                });
            }
            tower.setSpawnedGolem(golem);
        } else if (type == TowerType.HAPPY_GHAST) {
            if (tower.getSpawnedGhast() != null && tower.getSpawnedGhast().isValid()) {
                tower.getSpawnedGhast().getPassengers().clear();
                tower.getSpawnedGhast().remove();
            }
            double height = tower.getStructureSize() != null ? tower.getStructureSize().getBlockY() : 4.0;
            Location spawnLoc = center.clone().add(0, height + 1.0, 0);
            
            org.bukkit.entity.HappyGhast ghast = center.getWorld().spawn(spawnLoc, org.bukkit.entity.HappyGhast.class, gh -> {
                gh.setAI(false);
                gh.setInvulnerable(true);
                gh.setGravity(false);
                gh.setCollidable(false);
                gh.setPersistent(true);
                gh.setCustomName(org.bukkit.ChatColor.LIGHT_PURPLE + "Happy Ghast [Lvl " + level + "]");
                gh.setCustomNameVisible(true);
                setEntityScale(gh, 0.5);
            });
            tower.setSpawnedGhast(ghast);
        }
    }

    private void isolateTowerBlocks(Location center, BlockVector size) {
        int sizeX = size != null ? size.getBlockX() : 1;
        int sizeY = size != null ? size.getBlockY() : 3;
        int sizeZ = size != null ? size.getBlockZ() : 1;
        
        Location scanStart = size != null 
            ? center.clone().subtract(sizeX / 2, 0, sizeZ / 2).add(0, 1, 0)
            : center.clone().add(0, 1, 0);
            
        // Expand the scan boundaries by 1 block in X and Z to disconnect neighbors
        for (int dx = -1; dx <= sizeX; dx++) {
            for (int dy = 0; dy < sizeY; dy++) {
                for (int dz = -1; dz <= sizeZ; dz++) {
                    Location loc = scanStart.clone().add(dx, dy, dz);
                    org.bukkit.block.Block block = loc.getBlock();
                    org.bukkit.block.data.BlockData blockData = block.getBlockData();
                    
                    boolean modified = false;
                    if (blockData instanceof org.bukkit.block.data.MultipleFacing multipleFacing) {
                        for (org.bukkit.block.BlockFace face : multipleFacing.getAllowedFaces()) {
                            multipleFacing.setFace(face, false);
                        }
                        modified = true;
                    } else if (blockData instanceof org.bukkit.block.data.type.Wall wall) {
                        wall.setHeight(org.bukkit.block.BlockFace.NORTH, org.bukkit.block.data.type.Wall.Height.NONE);
                        wall.setHeight(org.bukkit.block.BlockFace.SOUTH, org.bukkit.block.data.type.Wall.Height.NONE);
                        wall.setHeight(org.bukkit.block.BlockFace.EAST, org.bukkit.block.data.type.Wall.Height.NONE);
                        wall.setHeight(org.bukkit.block.BlockFace.WEST, org.bukkit.block.data.type.Wall.Height.NONE);
                        wall.setUp(true);
                        modified = true;
                    }
                    
                    if (modified) {
                        block.setBlockData(blockData, false);
                    }
                }
            }
        }
    }

    private void clearTowerBlocks(Tower tower) {
        Location center = tower.getCenterLocation();
        if (tower.getStructureSize() != null) {
            BlockVector size = tower.getStructureSize();
            int sizeX = size.getBlockX();
            int sizeY = size.getBlockY();
            int sizeZ = size.getBlockZ();
            Location placementLoc = center.clone().subtract(sizeX / 2, 0, sizeZ / 2).add(0, 1, 0);
            for (int dx = 0; dx < sizeX; dx++) {
                for (int dy = 0; dy < sizeY; dy++) {
                    for (int dz = 0; dz < sizeZ; dz++) {
                        Location blockLoc = placementLoc.clone().add(dx, dy, dz);
                        blockLoc.getBlock().setType(Material.AIR, false);
                    }
                }
            }
        } else {
            // Fallback clear
            center.clone().add(0, 1, 0).getBlock().setType(Material.AIR, false);
            center.clone().add(0, 2, 0).getBlock().setType(Material.AIR, false);
            center.clone().add(0, 3, 0).getBlock().setType(Material.AIR, false);
        }
        clearNearbyDroppedItemsLater(center);
    }

    public void updateHologram(Tower tower) {
        Location center = tower.getCenterLocation();
        double holoHeight = 4.2; // Default fallback height
        if (tower.getStructureSize() != null) {
            holoHeight = tower.getStructureSize().getBlockY() + 1.2;
        }

        long cooldown = tower.getCooldown();
        boolean isBoosted = isRedstoneBoosted(tower);
        if (isBoosted) {
            cooldown = Math.max(1L, (long)(cooldown * 0.7));
        }
        String speedStr = String.format("%.1fs", cooldown / 20.0);
        if (isBoosted) {
            speedStr += ChatColor.RED + " ⚡";
        }

        java.util.List<String> lines = new java.util.ArrayList<>();
        lines.add(tower.getType().getColor().toString() + ChatColor.BOLD.toString() + tower.getType().name().toLowerCase() + "_" + tower.getLevel());
        
        if (tower.getType() == TowerType.REDSTONE) {
            lines.add(ChatColor.RED + "✦ " + ChatColor.GRAY + "Boost Range: " + ChatColor.GREEN + tower.getRange() + "m" +
                      ChatColor.GRAY + " | " + ChatColor.AQUA + "Boost: " + ChatColor.RED + "30% ⚡");
        } else {
            lines.add(ChatColor.RED + "❤ " + String.format("%.1f", tower.getDamage()) + " DMG" + 
                      ChatColor.GRAY + " | " + ChatColor.AQUA + "⚡ " + speedStr + 
                      ChatColor.GRAY + " | " + ChatColor.GREEN + "✦ " + String.format("%.1f", tower.getRange()) + "m");
        }
        lines.add(ChatColor.GRAY + "Priority: " + ChatColor.GOLD + tower.getTargetingMode().getDisplayName());

        java.util.List<ArmorStand> stands = tower.getHolograms();
        double spacing = 0.28;

        for (int i = 0; i < lines.size(); i++) {
            String text = lines.get(i);
            double lineY = holoHeight - (i * spacing);
            Location lineLoc = center.clone().add(0, lineY, 0);

            if (i < stands.size()) {
                ArmorStand as = stands.get(i);
                if (as != null && as.isValid()) {
                    as.teleport(lineLoc);
                    as.setCustomName(text);
                    as.setCustomNameVisible(true);
                } else {
                    final String nameText = text;
                    ArmorStand newAs = center.getWorld().spawn(lineLoc, ArmorStand.class, asSetup -> {
                        asSetup.setVisible(false);
                        asSetup.setGravity(false);
                        asSetup.setMarker(true);
                        asSetup.setInvulnerable(true);
                        asSetup.setPersistent(false);
                        asSetup.setCustomName(nameText);
                        asSetup.setCustomNameVisible(true);
                    });
                    stands.set(i, newAs);
                }
            } else {
                final String nameText = text;
                ArmorStand newAs = center.getWorld().spawn(lineLoc, ArmorStand.class, asSetup -> {
                    asSetup.setVisible(false);
                    asSetup.setGravity(false);
                    asSetup.setMarker(true);
                    asSetup.setInvulnerable(true);
                    asSetup.setPersistent(false);
                    asSetup.setCustomName(nameText);
                    asSetup.setCustomNameVisible(true);
                });
                stands.add(newAs);
            }
        }

        while (stands.size() > lines.size()) {
            ArmorStand extra = stands.remove(stands.size() - 1);
            if (extra != null && extra.isValid()) {
                extra.remove();
            }
        }
    }

    public void updateAllTowerHologramsInArena(String arena) {
        for (Tower tower : placedTowers.values()) {
            String towerArena = plugin.getPlotConfigManager().getPlotArena(tower.getPlotId());
            if (arena.equals(towerArena)) {
                updateHologram(tower);
            }
        }
    }

    public Tower getTower(String plotId) {
        return placedTowers.get(plotId);
    }

    public void removeTower(String plotId) {
        Tower tower = placedTowers.remove(plotId);
        if (tower != null) {
            clearTowerBlocks(tower);
            
            // Clean up spawned entities
            if (tower.getSpawnedGolem() != null && tower.getSpawnedGolem().isValid()) {
                tower.getSpawnedGolem().remove();
            }
            if (tower.getSpawnedGhast() != null && tower.getSpawnedGhast().isValid()) {
                tower.getSpawnedGhast().getPassengers().clear();
                tower.getSpawnedGhast().remove();
            }

            // Clean up hologram ArmorStands
            for (ArmorStand hologram : tower.getHolograms()) {
                if (hologram != null && hologram.isValid()) {
                    hologram.remove();
                }
            }
            tower.getHolograms().clear();

            if (tower.getType() == TowerType.REDSTONE) {
                updateAllTowerHologramsInArena(plugin.getPlotConfigManager().getPlotArena(plotId));
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
                    if (tower.isDisabled()) {
                        if (tick % 10 == 0) {
                            Location center = tower.getCenterLocation();
                            center.getWorld().spawnParticle(org.bukkit.Particle.SMOKE, center.clone().add(0, 2, 0), 2, 0.2, 0.2, 0.2, 0.0);
                        }
                        continue;
                    }

                    // Redstone passive towers don't fire active attacks, they only boost nearby towers
                    if (tower.getType() == TowerType.REDSTONE) {
                        if (tick % 20 == 0) {
                            Location center = tower.getCenterLocation();
                            double range = tower.getRange();
                            String arena = plugin.getPlotConfigManager().getPlotArena(tower.getPlotId());
                            for (Tower other : placedTowers.values()) {
                                if (other != tower && other.getType() != TowerType.REDSTONE) {
                                    String otherArena = plugin.getPlotConfigManager().getPlotArena(other.getPlotId());
                                    if (arena.equals(otherArena) && other.getCenterLocation().distanceSquared(center) <= range * range) {
                                        Location otherHolo = other.getCenterLocation().clone().add(0, 3.5, 0);
                                        otherHolo.getWorld().spawnParticle(
                                            org.bukkit.Particle.DUST,
                                            otherHolo,
                                            3, 0.2, 0.2, 0.2,
                                            new org.bukkit.Particle.DustOptions(org.bukkit.Color.RED, 1.0f)
                                        );
                                    }
                                }
                            }
                        }
                        continue;
                    }

                    // If Happy Ghast is ridden, handle boundaries and skip autopilot attack
                    if (tower.getType() == TowerType.HAPPY_GHAST && tower.getSpawnedGhast() != null && tower.getSpawnedGhast().isValid()) {
                        java.util.List<org.bukkit.entity.Entity> passengers = tower.getSpawnedGhast().getPassengers();
                        if (!passengers.isEmpty()) {
                            tower.setAutopilot(false);
                            org.bukkit.entity.Entity passenger = passengers.get(0);
                            if (passenger instanceof Player rider) {
                                Location centerLoc = tower.getCenterLocation();
                                Location ghastLoc = tower.getSpawnedGhast().getLocation();
                                if (ghastLoc.distanceSquared(centerLoc) > 25.0 * 25.0) {
                                    org.bukkit.util.Vector pushBack = centerLoc.toVector().subtract(ghastLoc.toVector()).normalize().multiply(0.5);
                                    tower.getSpawnedGhast().setVelocity(pushBack);
                                    if (tick % 20 == 0) {
                                        rider.sendMessage(org.bukkit.ChatColor.RED + "⚠ You are reaching the edge of the arena boundary! Pushing back.");
                                    }
                                }
                            }
                            continue; // Skip automated attack
                        } else {
                            tower.setAutopilot(true);
                        }
                    }

                    // Golem pathing movement tick
                    if (tower.getType() == TowerType.GOLEM && tower.getSpawnedGolem() != null && tower.getSpawnedGolem().isValid()) {
                        org.bukkit.entity.LivingEntity golem = tower.getSpawnedGolem();
                        org.bukkit.NamespacedKey wpKey = new org.bukkit.NamespacedKey(plugin, "golem_wp_index");
                        org.bukkit.NamespacedKey dirKey = new org.bukkit.NamespacedKey(plugin, "golem_wp_dir");
                        
                        int wpIndex = 0;
                        int dirVal = 1;
                        if (golem.getPersistentDataContainer().has(wpKey, org.bukkit.persistence.PersistentDataType.INTEGER)) {
                            wpIndex = golem.getPersistentDataContainer().get(wpKey, org.bukkit.persistence.PersistentDataType.INTEGER);
                        }
                        if (golem.getPersistentDataContainer().has(dirKey, org.bukkit.persistence.PersistentDataType.INTEGER)) {
                            dirVal = golem.getPersistentDataContainer().get(dirKey, org.bukkit.persistence.PersistentDataType.INTEGER);
                        }
                        
                        String towerArena = plugin.getPlotConfigManager().getPlotArena(tower.getPlotId());
                        java.util.List<Location> wps = plugin.getWaypointConfigManager().getWaypoints(towerArena);
                        if (!wps.isEmpty()) {
                            Location centerLoc = tower.getCenterLocation();
                            double range = tower.getRange();
                            
                            // Find closest waypoint on the path to the tower center
                            int closestIndex = 0;
                            double closestDistSq = Double.MAX_VALUE;
                            for (int i = 0; i < wps.size(); i++) {
                                Location wp = wps.get(i);
                                if (wp.getWorld().equals(centerLoc.getWorld())) {
                                    double distSq = wp.distanceSquared(centerLoc);
                                    if (distSq < closestDistSq) {
                                        closestDistSq = distSq;
                                        closestIndex = i;
                                    }
                                }
                            }
                            
                            int minIndex = closestIndex;
                            int maxIndex = closestIndex;
                            
                            // Expand left (downwards in indices) while within range
                            while (minIndex > 0) {
                                Location wp = wps.get(minIndex - 1);
                                if (wp.getWorld().equals(centerLoc.getWorld()) && wp.distanceSquared(centerLoc) <= range * range) {
                                    minIndex--;
                                } else {
                                    break;
                                }
                            }
                            
                            // Expand right (upwards in indices) while within range
                            while (maxIndex < wps.size() - 1) {
                                Location wp = wps.get(maxIndex + 1);
                                if (wp.getWorld().equals(centerLoc.getWorld()) && wp.distanceSquared(centerLoc) <= range * range) {
                                    maxIndex++;
                                } else {
                                    break;
                                }
                            }
                            
                            // Adjust wpIndex if it falls outside of [minIndex, maxIndex]
                            if (wpIndex < minIndex || wpIndex > maxIndex) {
                                wpIndex = closestIndex;
                                dirVal = 1;
                                golem.getPersistentDataContainer().set(wpKey, org.bukkit.persistence.PersistentDataType.INTEGER, wpIndex);
                                golem.getPersistentDataContainer().set(dirKey, org.bukkit.persistence.PersistentDataType.INTEGER, dirVal);
                            }
                            
                            Location targetWp = wps.get(wpIndex);
                            
                            if (golem instanceof org.bukkit.entity.Mob mob) {
                                if (tick % 5 == 0) {
                                    mob.getPathfinder().moveTo(targetWp, 1.25);
                                }
                            }
                            
                            if (golem.getLocation().distanceSquared(targetWp) < 2.25) {
                                if (minIndex == maxIndex) {
                                    wpIndex = minIndex;
                                } else {
                                    wpIndex += dirVal;
                                    if (wpIndex > maxIndex) {
                                        dirVal = -1;
                                        wpIndex = maxIndex - 1;
                                        if (wpIndex < minIndex) wpIndex = minIndex;
                                    } else if (wpIndex < minIndex) {
                                        dirVal = 1;
                                        wpIndex = minIndex + 1;
                                        if (wpIndex > maxIndex) wpIndex = maxIndex;
                                    }
                                }
                                golem.getPersistentDataContainer().set(wpKey, org.bukkit.persistence.PersistentDataType.INTEGER, wpIndex);
                                golem.getPersistentDataContainer().set(dirKey, org.bukkit.persistence.PersistentDataType.INTEGER, dirVal);
                            }
                        }
                    }

                    long cooldown = tower.getCooldown();
                    String towerArena = plugin.getPlotConfigManager().getPlotArena(tower.getPlotId());
                    if (plugin.getGameManager().isSpellActive(towerArena, "OVERCHARGE")) {
                        cooldown = Math.max(1, cooldown / 2);
                    }

                    // Redstone passive cooldown reduction boost
                    if (isRedstoneBoosted(tower)) {
                        cooldown = (long) (cooldown * 0.7); // 30% speed boost
                        cooldown = Math.max(1L, cooldown);
                    }

                    if (tick - tower.getLastAttackTick() >= cooldown) {
                        Mob target = findTarget(tower);
                        // For AoE towers, they attack if there is any target in range
                        if (tower.getType() == TowerType.FIRE || tower.getType() == TowerType.PRISMARINE || tower.getType() == TowerType.POISON || tower.getType() == TowerType.ICE) {
                            java.util.List<Mob> inRadius = getMobsInRadius(tower.getCenterLocation(), tower.getRange(), towerArena);
                            if (!inRadius.isEmpty()) {
                                shootTarget(tower, inRadius.get(0));
                                tower.setLastAttackTick(tick);
                            }
                        } else if (target != null) {
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
        Location towerLoc = tower.getCenterLocation();
        double rangeSquared = Math.pow(tower.getRange(), 2);
        String towerArena = plugin.getPlotConfigManager().getPlotArena(tower.getPlotId());

        java.util.List<com.pauljang.towerDefense.entities.TDMob> candidates = new java.util.ArrayList<>();

        for (com.pauljang.towerDefense.entities.TDMob tdMob : plugin.getMobManager().getActiveMobs()) {
            Mob mob = tdMob.getEntity();
            if (mob == null || mob.isDead() || !mob.isValid()) continue;

            if (!mob.getWorld().equals(towerLoc.getWorld())) continue;

            String mobArena = mob.getPersistentDataContainer().get(
                new org.bukkit.NamespacedKey(plugin, "td_arena"),
                org.bukkit.persistence.PersistentDataType.STRING
            );
            if (mobArena == null) mobArena = "1";
            if (!towerArena.equals(mobArena)) continue;

            double distSq = mob.getLocation().distanceSquared(towerLoc);
            if (distSq <= rangeSquared) {
                if (tower.getType() == TowerType.GOLEM) {
                    org.bukkit.entity.LivingEntity golem = tower.getSpawnedGolem();
                    if (golem == null || !golem.isValid() || mob.getLocation().distanceSquared(golem.getLocation()) > 3.0 * 3.0) {
                        continue;
                    }
                }
                candidates.add(tdMob);
            }
        }

        if (candidates.isEmpty()) return null;

        TargetingMode mode = tower.getTargetingMode();
        com.pauljang.towerDefense.entities.TDMob bestMob = null;

        switch (mode) {
            case FIRST: {
                double bestProgress = -1.0;
                for (com.pauljang.towerDefense.entities.TDMob tdMob : candidates) {
                    double progress = getMobProgress(tdMob);
                    if (progress > bestProgress) {
                        bestProgress = progress;
                        bestMob = tdMob;
                    }
                }
                break;
            }
            case LAST: {
                double worstProgress = Double.MAX_VALUE;
                for (com.pauljang.towerDefense.entities.TDMob tdMob : candidates) {
                    double progress = getMobProgress(tdMob);
                    if (progress < worstProgress) {
                        worstProgress = progress;
                        bestMob = tdMob;
                    }
                }
                break;
            }
            case STRONG: {
                double maxHealth = -1.0;
                for (com.pauljang.towerDefense.entities.TDMob tdMob : candidates) {
                    double hp = tdMob.getEntity().getHealth();
                    if (hp > maxHealth) {
                        maxHealth = hp;
                        bestMob = tdMob;
                    }
                }
                break;
            }
            case WEAK: {
                double minHealth = Double.MAX_VALUE;
                for (com.pauljang.towerDefense.entities.TDMob tdMob : candidates) {
                    double hp = tdMob.getEntity().getHealth();
                    if (hp < minHealth) {
                        minHealth = hp;
                        bestMob = tdMob;
                    }
                }
                break;
            }
            case CLOSE: {
                double minDistanceSq = Double.MAX_VALUE;
                for (com.pauljang.towerDefense.entities.TDMob tdMob : candidates) {
                    double distSq = tdMob.getEntity().getLocation().distanceSquared(towerLoc);
                    if (distSq < minDistanceSq) {
                        minDistanceSq = distSq;
                        bestMob = tdMob;
                    }
                }
                break;
            }
        }

        return bestMob != null ? bestMob.getEntity() : null;
    }

    private double getMobProgress(com.pauljang.towerDefense.entities.TDMob tdMob) {
        double progress = tdMob.getPathHistory().size() * 10000.0;
        Location nextWp = tdMob.getNextWaypoint();
        Mob mob = tdMob.getEntity();
        if (nextWp != null && mob.getWorld().equals(nextWp.getWorld())) {
            progress -= mob.getLocation().distance(nextWp);
        }
        return progress;
    }

    private void shootTarget(Tower tower, Mob target) {
        double height = 2.5; // Default for 3-block multiblock
        if (tower.getStructureSize() != null) {
            height = tower.getStructureSize().getBlockY() - 0.5;
        }
        Location start = tower.getCenterLocation().clone().add(0, height, 0);
        String towerArena = plugin.getPlotConfigManager().getPlotArena(tower.getPlotId());
        double range = tower.getRange();

        switch (tower.getType()) {
            case ARCHER -> {
                target.damage(tower.getDamage());
                drawParticleLine(start, target.getEyeLocation(), org.bukkit.Particle.CRIT);
                start.getWorld().playSound(start, Sound.ENTITY_ARROW_SHOOT, 0.8f, 1.2f);
            }
            case FIRE -> {
                int fireTicks = plugin.getConfig().getInt("towers.fire_" + tower.getLevel() + ".fire-ticks", 60 + (tower.getLevel() - 1) * 40);
                double damage = tower.getDamage();
                java.util.List<Mob> targets = getMobsInRadius(tower.getCenterLocation(), range, towerArena);
                org.bukkit.NamespacedKey fireDmgKey = new org.bukkit.NamespacedKey(plugin, "td_fire_damage");
                for (Mob mob : targets) {
                    mob.setFireTicks(fireTicks);
                    mob.getPersistentDataContainer().set(fireDmgKey, org.bukkit.persistence.PersistentDataType.DOUBLE, damage);
                    drawParticleLine(start, mob.getEyeLocation(), org.bukkit.Particle.FLAME);
                }
                start.getWorld().playSound(start, Sound.ENTITY_BLAZE_SHOOT, 0.8f, 1.0f);
            }
            case PRISMARINE -> {
                int slowLvl = plugin.getConfig().getInt("towers.prismarine_" + tower.getLevel() + ".slowness-level", tower.getLevel());
                int slowDur = plugin.getConfig().getInt("towers.prismarine_" + tower.getLevel() + ".slowness-duration", 40 + (tower.getLevel() - 1) * 20);
                double damage = tower.getDamage();
                java.util.List<Mob> targets = getMobsInRadius(tower.getCenterLocation(), range, towerArena);
                for (Mob mob : targets) {
                    mob.damage(damage);
                    mob.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, slowDur, slowLvl - 1));
                    drawParticleLine(start, mob.getEyeLocation(), org.bukkit.Particle.BUBBLE);
                }
                start.getWorld().playSound(start, Sound.ENTITY_PLAYER_SPLASH, 0.8f, 1.2f);
            }
            case CHORUS -> {
                target.damage(tower.getDamage());
                
                com.pauljang.towerDefense.entities.TDMob tdMob = null;
                for (com.pauljang.towerDefense.entities.TDMob active : plugin.getMobManager().getActiveMobs()) {
                    if (active.getEntity().equals(target)) {
                        tdMob = active;
                        break;
                    }
                }
                if (tdMob != null) {
                    java.util.Map<String, com.pauljang.towerDefense.data.TDWaypoint> graph = tdMob.getWaypointGraph();
                    java.util.List<String> history = tdMob.getPathHistory();
                    
                    if (history.size() >= 1) {
                        Location mobLoc = target.getLocation();
                        double remaining = 8.0;
                        Location newTargetLocation = null;
                        String newWpId = tdMob.getCurrentWaypointId();
                        
                        // We trace backward along the visited path history
                        int currentWpIdx = history.size() - 1; // index of current target waypoint
                        int prevWpIdx = history.size() - 2; // index of the waypoint the mob just left
                        
                        Location currentStartLoc = mobLoc;
                        int nextTargetIdxInHistory = currentWpIdx;
                        
                        // Step 1: Trace from current location back to the previous waypoint
                        if (prevWpIdx >= 0) {
                            com.pauljang.towerDefense.data.TDWaypoint prevWp = graph.get(history.get(prevWpIdx));
                            if (prevWp != null) {
                                Location prevLoc = prevWp.getLocation();
                                double dist = currentStartLoc.distance(prevLoc);
                                if (dist >= remaining) {
                                    org.bukkit.util.Vector dir = prevLoc.toVector().subtract(currentStartLoc.toVector()).normalize();
                                    newTargetLocation = currentStartLoc.clone().add(dir.multiply(remaining));
                                    newWpId = history.get(currentWpIdx);
                                    nextTargetIdxInHistory = currentWpIdx;
                                    remaining = 0;
                                } else {
                                    remaining -= dist;
                                    currentStartLoc = prevLoc;
                                    newWpId = history.get(prevWpIdx);
                                    nextTargetIdxInHistory = prevWpIdx;
                                    prevWpIdx--;
                                }
                            }
                        } else {
                            // No previous waypoint (still on first segment)
                            newTargetLocation = graph.get("0").getLocation().clone();
                            newWpId = "0";
                            remaining = 0;
                        }
                        
                        // Step 2: Trace back from prevWpIdx to prevWpIdx - 1
                        while (prevWpIdx >= 0 && remaining > 0) {
                            com.pauljang.towerDefense.data.TDWaypoint prevWp = graph.get(history.get(prevWpIdx));
                            if (prevWp == null) break;
                            
                            Location prevLoc = prevWp.getLocation();
                            double dist = currentStartLoc.distance(prevLoc);
                            
                            if (dist >= remaining) {
                                org.bukkit.util.Vector dir = prevLoc.toVector().subtract(currentStartLoc.toVector()).normalize();
                                newTargetLocation = currentStartLoc.clone().add(dir.multiply(remaining));
                                newWpId = history.get(nextTargetIdxInHistory);
                                remaining = 0;
                                break;
                            } else {
                                remaining -= dist;
                                currentStartLoc = prevLoc;
                                newWpId = history.get(prevWpIdx);
                                nextTargetIdxInHistory = prevWpIdx;
                                prevWpIdx--;
                            }
                        }
                        
                        if (newTargetLocation == null) {
                            newTargetLocation = graph.get("0").getLocation().clone();
                            newWpId = "0";
                            nextTargetIdxInHistory = 0;
                        }
                        
                        // Clean up history to match the rolled-back target index
                        while (history.size() > nextTargetIdxInHistory + 1) {
                            history.remove(history.size() - 1);
                        }
                        
                        tdMob.setCurrentWaypointId(newWpId);
                        
                        target.getWorld().spawnParticle(org.bukkit.Particle.PORTAL, target.getLocation(), 15, 0.5, 0.5, 0.5, 0.1);
                        target.getWorld().playSound(target.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);
                        
                        String presetKey = target.getPersistentDataContainer().get(
                            new org.bukkit.NamespacedKey(plugin, "td_preset"),
                            org.bukkit.persistence.PersistentDataType.STRING
                        );
                        if (presetKey == null) presetKey = target.getType().name().toLowerCase();
                        double heightOffset = plugin.getConfig().getDouble("mobs." + presetKey + ".height-offset", 0.0);
                        
                        Location teleportLoc = newTargetLocation.clone();
                        if (heightOffset > 0.0) {
                            teleportLoc.add(0, heightOffset, 0);
                        }
                        
                        target.teleport(teleportLoc);
                        target.getWorld().spawnParticle(org.bukkit.Particle.PORTAL, teleportLoc, 15, 0.5, 0.5, 0.5, 0.1);
                        
                        double speed = 1.0;
                        org.bukkit.attribute.AttributeInstance speedAttr = target.getAttribute(org.bukkit.attribute.Attribute.MOVEMENT_SPEED);
                        if (speedAttr != null) {
                            speed = speedAttr.getValue();
                        }
                        target.getPathfinder().moveTo(newTargetLocation, speed);
                    }
                }
            }
            case POISON -> {
                int poisonLvl = plugin.getConfig().getInt("towers.poison_" + tower.getLevel() + ".poison-level", tower.getLevel());
                int poisonDur = plugin.getConfig().getInt("towers.poison_" + tower.getLevel() + ".poison-duration", 60 + (tower.getLevel() - 1) * 20);
                double damage = tower.getDamage();
                java.util.List<Mob> targets = getMobsInRadius(tower.getCenterLocation(), range, towerArena);
                
                // Draw radial/area poison cloud instead of focused witch particle beams
                Location center = tower.getCenterLocation();
                for (double r = 1.0; r <= range; r += 1.5) {
                    int count = (int) (2 * Math.PI * r * 1.5);
                    for (int i = 0; i < count; i++) {
                        double angle = (2 * Math.PI / count) * i;
                        double x = Math.cos(angle) * r;
                        double z = Math.sin(angle) * r;
                        Location pLoc = center.clone().add(x, 1.0, z);
                        center.getWorld().spawnParticle(org.bukkit.Particle.WITCH, pLoc, 1, 0.1, 0.1, 0.1, 0.0);
                    }
                }

                org.bukkit.NamespacedKey poisonDmgKey = new org.bukkit.NamespacedKey(plugin, "td_poison_damage");
                for (Mob mob : targets) {
                    if (mob.getType() == org.bukkit.entity.EntityType.SPIDER) {
                        continue; // Spider immune to poison tower
                    }
                    mob.getPersistentDataContainer().set(poisonDmgKey, org.bukkit.persistence.PersistentDataType.DOUBLE, damage);
                    mob.addPotionEffect(new PotionEffect(PotionEffectType.POISON, poisonDur, poisonLvl - 1));
                }
                start.getWorld().playSound(start, Sound.BLOCK_BREWING_STAND_BREW, 0.8f, 1.0f);
            }
            case ICE -> {
                int slowDur = plugin.getConfig().getInt("towers.ice_" + tower.getLevel() + ".slowness-duration", 40 + (tower.getLevel() - 1) * 20);
                double damage = tower.getDamage();
                java.util.List<Mob> targets = getMobsInRadius(tower.getCenterLocation(), range, towerArena);
                org.bukkit.NamespacedKey freezeKey = new org.bukkit.NamespacedKey(plugin, "td_frozen_until");
                org.bukkit.NamespacedKey slowImmuneKey = new org.bukkit.NamespacedKey(plugin, "td_slow_immune");

                for (Mob mob : targets) {
                    if (mob.getPersistentDataContainer().has(slowImmuneKey, org.bukkit.persistence.PersistentDataType.BYTE)) {
                        continue; // Skip slow-immune mobs
                    }
                    mob.damage(damage);
                    
                    // Freeze for slowDur ticks (50ms per tick)
                    long freezeEndTime = System.currentTimeMillis() + (slowDur * 50L);
                    mob.getPersistentDataContainer().set(freezeKey, org.bukkit.persistence.PersistentDataType.LONG, freezeEndTime);
                    
                    // Apply slowness II to visually slow/halt animations and trigger the snowflake indicator
                    mob.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, slowDur, 2));
                    drawParticleLine(start, mob.getEyeLocation(), org.bukkit.Particle.SNOWFLAKE);
                }
                start.getWorld().playSound(start, Sound.BLOCK_POWDER_SNOW_BREAK, 0.8f, 1.2f);
            }
            case GOLEM -> {
                double damage = tower.getDamage();
                if (tower.getLevel() == 1) {
                    damage = plugin.getConfig().getDouble("towers.golem_1.damage", 20.0);
                } else {
                    damage = plugin.getConfig().getDouble("towers.golem_2.damage", 40.0);
                }
                
                target.damage(damage);
                
                if (tower.getSpawnedGolem() != null && tower.getSpawnedGolem().isValid()) {
                    tower.getSpawnedGolem().swingMainHand();
                    Location golemLoc = tower.getSpawnedGolem().getLocation();
                    org.bukkit.util.Vector dir = target.getEyeLocation().toVector().subtract(golemLoc.toVector()).normalize();
                    golemLoc.setDirection(dir);
                    tower.getSpawnedGolem().teleport(golemLoc);
                }
                
                if (tower.getLevel() == 2) {
                    target.setVelocity(new org.bukkit.util.Vector(0, 0.8, 0));
                    start.getWorld().playSound(target.getLocation(), Sound.ENTITY_IRON_GOLEM_ATTACK, 1.0f, 1.0f);
                    start.getWorld().spawnParticle(org.bukkit.Particle.CLOUD, target.getLocation(), 5, 0.2, 0.2, 0.2, 0.1);
                } else {
                    start.getWorld().playSound(target.getLocation(), Sound.ENTITY_IRON_GOLEM_ATTACK, 0.8f, 1.3f);
                }
            }
            case HAPPY_GHAST -> {
                if (tower.getSpawnedGhast() != null && tower.getSpawnedGhast().isValid()) {
                    org.bukkit.entity.HappyGhast ghast = tower.getSpawnedGhast();
                    
                    Location ghastLoc = ghast.getLocation();
                    org.bukkit.util.Vector dir = target.getEyeLocation().toVector().subtract(ghastLoc.toVector()).normalize();
                    ghastLoc.setDirection(dir);
                    ghast.teleport(ghastLoc);
                    
                    org.bukkit.Location spawnLoc = ghast.getLocation().add(dir.clone().multiply(3.0));
                    org.bukkit.entity.LargeFireball fireball = spawnLoc.getWorld().spawn(spawnLoc, org.bukkit.entity.LargeFireball.class, fb -> {
                        fb.setShooter(ghast);
                        fb.setDirection(dir);
                        fb.setIsIncendiary(false);
                        fb.setYield(0.0f);
                        fb.setMetadata("td_happy_fireball", new org.bukkit.metadata.FixedMetadataValue(plugin, tower));
                    });
                    ghast.getWorld().playSound(ghast.getLocation(), Sound.ENTITY_GHAST_SHOOT, 1.0f, 1.0f);
                }
            }
            case REDSTONE -> {
                // Passive Redstone Tower
            }
        }
    }

    private void setEntityScale(org.bukkit.entity.LivingEntity entity, double value) {
        try {
            org.bukkit.attribute.Attribute scaleAttr = null;
            String foundFieldName = "";
            try {
                java.lang.reflect.Field field = org.bukkit.attribute.Attribute.class.getField("GENERIC_SCALE");
                scaleAttr = (org.bukkit.attribute.Attribute) field.get(null);
                foundFieldName = "GENERIC_SCALE";
            } catch (Exception e) {
                try {
                    java.lang.reflect.Field field = org.bukkit.attribute.Attribute.class.getField("SCALE");
                    scaleAttr = (org.bukkit.attribute.Attribute) field.get(null);
                    foundFieldName = "SCALE";
                } catch (Exception e2) {
                    plugin.getLogger().warning("Failed to find GENERIC_SCALE or SCALE attribute via reflection!");
                }
            }
            if (scaleAttr != null) {
                org.bukkit.attribute.AttributeInstance instance = entity.getAttribute(scaleAttr);
                if (instance != null) {
                    instance.setBaseValue(value);
                    plugin.getLogger().info("Successfully set " + entity.getType() + " scale to " + value + " using attribute " + foundFieldName);
                } else {
                    plugin.getLogger().warning("AttributeInstance for " + foundFieldName + " was null on " + entity.getType());
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Error setting entity scale: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public java.util.List<Mob> getMobsInRadius(Location center, double radius, String arena) {
        java.util.List<Mob> result = new java.util.ArrayList<>();
        double radiusSq = radius * radius;
        for (com.pauljang.towerDefense.entities.TDMob tdMob : plugin.getMobManager().getActiveMobs()) {
            Mob mob = tdMob.getEntity();
            if (mob == null || mob.isDead() || !mob.isValid()) continue;
            if (!mob.getWorld().equals(center.getWorld())) continue;

            String mobArena = mob.getPersistentDataContainer().get(
                new org.bukkit.NamespacedKey(plugin, "td_arena"),
                org.bukkit.persistence.PersistentDataType.STRING
            );
            if (mobArena == null) mobArena = "1";
            if (!arena.equals(mobArena)) continue;

            if (mob.getLocation().distanceSquared(center) <= radiusSq) {
                result.add(mob);
            }
        }
        return result;
    }

    private boolean isRedstoneBoosted(Tower targetTower) {
        if (targetTower.getType() == TowerType.REDSTONE) return false;
        Location targetLoc = targetTower.getCenterLocation();
        String targetArena = plugin.getPlotConfigManager().getPlotArena(targetTower.getPlotId());
        
        for (Tower tower : placedTowers.values()) {
            if (tower.getType() == TowerType.REDSTONE && !tower.isDisabled()) {
                String redstoneArena = plugin.getPlotConfigManager().getPlotArena(tower.getPlotId());
                if (!targetArena.equals(redstoneArena)) continue;
                
                double range = tower.getRange();
                if (tower.getCenterLocation().distanceSquared(targetLoc) <= range * range) {
                    return true;
                }
            }
        }
        return false;
    }

    private void drawParticleLine(Location start, Location end, org.bukkit.Particle particle) {
        double distance = start.distance(end);
        org.bukkit.util.Vector direction = end.toVector().subtract(start.toVector()).normalize();

        for (double d = 0; d < distance; d += 0.25) {
            Location point = start.clone().add(direction.clone().multiply(d));
            start.getWorld().spawnParticle(particle, point, 1, 0.0, 0.0, 0.0, 0.0);
        }
    }

    public void openManageTowerGUI(Player player, String plotId) {
        Tower tower = getTower(plotId);
        if (tower == null) return;

        Inventory gui = org.bukkit.Bukkit.createInventory(null, 54, ChatColor.DARK_BLUE + "Manage Tower: " + plotId);

        // Fill background with gray stained glass pane
        ItemStack filler = createGUIItem(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 54; i++) {
            gui.setItem(i, filler);
        }

        // Slot 13: Tower Info
        if (tower.getType() == TowerType.REDSTONE) {
            gui.setItem(13, createGUIItem(
                Material.BOOK,
                ChatColor.GREEN + "Tower Stats (" + tower.getType().getDisplayName() + ")",
                ChatColor.GRAY + "Level: " + ChatColor.YELLOW + tower.getLevel(),
                ChatColor.GRAY + "Boost Range: " + ChatColor.YELLOW + tower.getRange() + " blocks",
                ChatColor.GRAY + "Speed Boost: " + ChatColor.YELLOW + "30% (x0.7 Cooldown)"
            ));
        } else {
            gui.setItem(13, createGUIItem(
                Material.BOOK,
                ChatColor.GREEN + "Tower Stats (" + tower.getType().getDisplayName() + ")",
                ChatColor.GRAY + "Level: " + ChatColor.YELLOW + tower.getLevel(),
                ChatColor.GRAY + "Range: " + ChatColor.YELLOW + tower.getRange(),
                ChatColor.GRAY + "Damage: " + ChatColor.YELLOW + String.format("%.1f", tower.getDamage()),
                ChatColor.GRAY + "Attack Speed: " + ChatColor.YELLOW + String.format("%.1fs", tower.getCooldown() / 20.0),
                ChatColor.GRAY + "Targeting: " + ChatColor.YELLOW + tower.getTargetingMode().getDisplayName()
            ));
        }

        // Slot 22: Upgrade Tower
        int nextLvl = tower.getLevel() + 1;
        int cost = tower.getUpgradeCost();
        if (cost != -1) {
            String nextKey = tower.getType().name().toLowerCase() + "_" + nextLvl;
            double nextRange = plugin.getConfig().getDouble("towers." + nextKey + ".range", tower.getRange() + 1.5);
            double nextDamage = plugin.getConfig().getDouble("towers." + nextKey + ".damage", tower.getDamage() + 1.5);
            long nextCooldown = plugin.getConfig().getLong("towers." + nextKey + ".cooldown", Math.max(8L, tower.getCooldown() - 2L));
            
            if (tower.getType() == TowerType.REDSTONE) {
                gui.setItem(22, createGUIItem(
                    Material.ANVIL,
                    ChatColor.GOLD + "Upgrade Tower (Lvl " + tower.getLevel() + " -> " + nextLvl + ")",
                    ChatColor.GRAY + "Cost: " + ChatColor.YELLOW + cost + " Gold",
                    "",
                    ChatColor.GRAY + "Stats Increase:",
                    ChatColor.GRAY + " - Boost Range: " + ChatColor.YELLOW + tower.getRange() + ChatColor.GREEN + " -> " + nextRange
                ));
            } else {
                gui.setItem(22, createGUIItem(
                    Material.ANVIL,
                    ChatColor.GOLD + "Upgrade Tower (Lvl " + tower.getLevel() + " -> " + nextLvl + ")",
                    ChatColor.GRAY + "Cost: " + ChatColor.YELLOW + cost + " Gold",
                    "",
                    ChatColor.GRAY + "Stats Increase:",
                    ChatColor.GRAY + " - Range: " + ChatColor.YELLOW + tower.getRange() + ChatColor.GREEN + " -> " + nextRange,
                    ChatColor.GRAY + " - Damage: " + ChatColor.YELLOW + String.format("%.1f", tower.getDamage()) + ChatColor.GREEN + " -> " + String.format("%.1f", nextDamage),
                    ChatColor.GRAY + " - Speed: " + ChatColor.YELLOW + String.format("%.1fs", tower.getCooldown() / 20.0) + ChatColor.GREEN + " -> " + String.format("%.1fs", nextCooldown / 20.0)
                ));
            }
        } else {
            gui.setItem(22, createGUIItem(
                Material.BEDROCK,
                ChatColor.RED + "Tower Maxed Out",
                ChatColor.GRAY + "This tower is at the maximum level."
            ));
        }

        // Slot 31: Targeting Mode / Affected Towers for Redstone
        if (tower.getType() == TowerType.REDSTONE) {
            java.util.List<String> affectedLores = new java.util.ArrayList<>();
            affectedLores.add(ChatColor.GRAY + "Provides a " + ChatColor.RED + "30% attack speed boost" + ChatColor.GRAY + " (x0.7 Cooldown) to nearby towers.");
            affectedLores.add(ChatColor.GRAY + "Range: " + ChatColor.YELLOW + tower.getRange() + " blocks");
            affectedLores.add("");
            affectedLores.add(ChatColor.GOLD + "Affected Towers:");
            
            Location rsLoc = tower.getCenterLocation();
            double range = tower.getRange();
            String rsArena = plugin.getPlotConfigManager().getPlotArena(tower.getPlotId());
            int count = 0;
            
            for (Tower other : placedTowers.values()) {
                if (other != tower && other.getType() != TowerType.REDSTONE) {
                    String otherArena = plugin.getPlotConfigManager().getPlotArena(other.getPlotId());
                    if (rsArena.equals(otherArena) && other.getCenterLocation().distanceSquared(rsLoc) <= range * range) {
                        affectedLores.add(ChatColor.YELLOW + " - " + other.getType().getDisplayName() + " (" + other.getPlotId() + ") [Lvl " + other.getLevel() + "]");
                        count++;
                    }
                }
            }
            if (count == 0) {
                affectedLores.add(ChatColor.RED + " (No towers in range)");
            }
            
            gui.setItem(31, createGUIItem(
                Material.REDSTONE,
                ChatColor.RED + "" + ChatColor.BOLD + "Redstone Boost Status",
                affectedLores.toArray(new String[0])
            ));
        } else {
            gui.setItem(31, createGUIItem(
                Material.COMPASS,
                ChatColor.AQUA + "Targeting Mode",
                ChatColor.GRAY + "Current Priority: " + ChatColor.YELLOW + tower.getTargetingMode().getDisplayName(),
                "",
                ChatColor.GRAY + "Click to cycle priorities:",
                ChatColor.GRAY + " -> FIRST -> LAST -> STRONG -> WEAK -> CLOSE"
            ));
        }

        // Slot 40: Destroy/Sell Tower
        int refund = tower.getTotalValue() / 2;
        gui.setItem(40, createGUIItem(
            Material.RED_WOOL,
            ChatColor.RED + "" + ChatColor.BOLD + "Destroy Tower",
            ChatColor.GRAY + "Demolishes the tower on this plot.",
            ChatColor.GOLD + "Refund: " + ChatColor.YELLOW + refund + " Gold"
        ));

        player.openInventory(gui);
        player.playSound(player.getLocation(), Sound.BLOCK_CHEST_OPEN, 0.8f, 1.2f);
    }

    public void openBuyTowerGUI(Player player, String plotId) {
        Inventory gui = org.bukkit.Bukkit.createInventory(null, 27, ChatColor.DARK_BLUE + "Buy Tower: " + plotId);

        // Background filler
        ItemStack filler = createGUIItem(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 27; i++) {
            gui.setItem(i, filler);
        }

        // Slot 10: Archer Tower
        int archerCost = plugin.getConfig().getInt("towers.archer_1.cost", 100);
        double archerRange = plugin.getConfig().getDouble("towers.archer_1.range", 15.0);
        double archerDamage = plugin.getConfig().getDouble("towers.archer_1.damage", 1.5);
        double archerSpeed = plugin.getConfig().getLong("towers.archer_1.cooldown", 20L) / 20.0;
        gui.setItem(10, createGUIItem(
            Material.DISPENSER,
            ChatColor.GREEN + "Archer Tower",
            ChatColor.GRAY + "Base Cost: " + ChatColor.YELLOW + archerCost + " Gold",
            ChatColor.GRAY + "Range: " + ChatColor.YELLOW + archerRange + " blocks",
            ChatColor.GRAY + "Damage: " + ChatColor.YELLOW + archerDamage + " HP",
            ChatColor.GRAY + "Attack Speed: " + ChatColor.YELLOW + archerSpeed + "s",
            "",
            ChatColor.GRAY + "Shoots single targets fast."
        ));

        // Slot 11: Fire Tower
        int fireCost = plugin.getConfig().getInt("towers.fire_1.cost", 175);
        double fireRange = plugin.getConfig().getDouble("towers.fire_1.range", 8.0);
        double fireDamage = plugin.getConfig().getDouble("towers.fire_1.damage", 1.0);
        double fireSpeed = plugin.getConfig().getLong("towers.fire_1.cooldown", 30L) / 20.0;
        gui.setItem(11, createGUIItem(
            Material.REDSTONE_LAMP,
            ChatColor.RED + "Fire Tower",
            ChatColor.GRAY + "Base Cost: " + ChatColor.YELLOW + fireCost + " Gold",
            ChatColor.GRAY + "Range: " + ChatColor.YELLOW + fireRange + " blocks",
            ChatColor.GRAY + "Damage: " + ChatColor.YELLOW + fireDamage + " HP",
            ChatColor.GRAY + "Attack Speed: " + ChatColor.YELLOW + fireSpeed + "s",
            "",
            ChatColor.GRAY + "Lights all mobs in radius on fire."
        ));

        // Slot 12: Prismarine Tower
        int prismarineCost = plugin.getConfig().getInt("towers.prismarine_1.cost", 125);
        double prismarineRange = plugin.getConfig().getDouble("towers.prismarine_1.range", 8.0);
        double prismarineDamage = plugin.getConfig().getDouble("towers.prismarine_1.damage", 0.5);
        double prismarineSpeed = plugin.getConfig().getLong("towers.prismarine_1.cooldown", 30L) / 20.0;
        gui.setItem(12, createGUIItem(
            Material.PRISMARINE_BRICKS,
            ChatColor.DARK_AQUA + "Prismarine Tower",
            ChatColor.GRAY + "Base Cost: " + ChatColor.YELLOW + prismarineCost + " Gold",
            ChatColor.GRAY + "Range: " + ChatColor.YELLOW + prismarineRange + " blocks",
            ChatColor.GRAY + "Damage: " + ChatColor.YELLOW + prismarineDamage + " HP",
            ChatColor.GRAY + "Attack Speed: " + ChatColor.YELLOW + prismarineSpeed + "s",
            "",
            ChatColor.GRAY + "Applies Slowness to all mobs in radius."
        ));

        // Slot 13: Chorus Tower
        int chorusCost = plugin.getConfig().getInt("towers.chorus_1.cost", 150);
        double chorusRange = plugin.getConfig().getDouble("towers.chorus_1.range", 8.0);
        double chorusDamage = plugin.getConfig().getDouble("towers.chorus_1.damage", 0.0);
        double chorusSpeed = plugin.getConfig().getLong("towers.chorus_1.cooldown", 120L) / 20.0;
        gui.setItem(13, createGUIItem(
            Material.CHORUS_FLOWER,
            ChatColor.LIGHT_PURPLE + "Chorus Tower",
            ChatColor.GRAY + "Base Cost: " + ChatColor.YELLOW + chorusCost + " Gold",
            ChatColor.GRAY + "Range: " + ChatColor.YELLOW + chorusRange + " blocks",
            ChatColor.GRAY + "Damage: " + ChatColor.YELLOW + chorusDamage + " HP",
            ChatColor.GRAY + "Teleport Cooldown: " + ChatColor.YELLOW + chorusSpeed + "s",
            "",
            ChatColor.GRAY + "Teleports leading mob backwards on path."
        ));

        // Slot 14: Redstone Tower
        int redstoneCost = plugin.getConfig().getInt("towers.redstone_1.cost", 200);
        double redstoneRange = plugin.getConfig().getDouble("towers.redstone_1.range", 10.0);
        gui.setItem(14, createGUIItem(
            Material.REDSTONE_BLOCK,
            ChatColor.DARK_RED + "Redstone Tower",
            ChatColor.GRAY + "Base Cost: " + ChatColor.YELLOW + redstoneCost + " Gold",
            ChatColor.GRAY + "Boost Radius: " + ChatColor.YELLOW + redstoneRange + " blocks",
            "",
            ChatColor.GRAY + "Passive: Boosts attack speed of nearby towers."
        ));

        // Slot 15: Poison Tower
        int poisonCost = plugin.getConfig().getInt("towers.poison_1.cost", 150);
        double poisonRange = plugin.getConfig().getDouble("towers.poison_1.range", 8.0);
        double poisonDamage = plugin.getConfig().getDouble("towers.poison_1.damage", 0.5);
        double poisonSpeed = plugin.getConfig().getLong("towers.poison_1.cooldown", 30L) / 20.0;
        gui.setItem(15, createGUIItem(
            Material.MUD_BRICKS,
            ChatColor.DARK_GREEN + "Poison Tower",
            ChatColor.GRAY + "Base Cost: " + ChatColor.YELLOW + poisonCost + " Gold",
            ChatColor.GRAY + "Range: " + ChatColor.YELLOW + poisonRange + " blocks",
            ChatColor.GRAY + "Damage: " + ChatColor.YELLOW + poisonDamage + " HP",
            ChatColor.GRAY + "Attack Speed: " + ChatColor.YELLOW + poisonSpeed + "s",
            "",
            ChatColor.GRAY + "Poisons all mobs in radius (spiders immune)."
        ));

        // Slot 16: Ice Tower
        int iceCost = plugin.getConfig().getInt("towers.ice_1.cost", 125);
        double iceRange = plugin.getConfig().getDouble("towers.ice_1.range", 8.0);
        double iceDamage = plugin.getConfig().getDouble("towers.ice_1.damage", 0.5);
        double iceSpeed = plugin.getConfig().getLong("towers.ice_1.cooldown", 30L) / 20.0;
        gui.setItem(16, createGUIItem(
            Material.PACKED_ICE,
            ChatColor.AQUA + "Ice Tower",
            ChatColor.GRAY + "Base Cost: " + ChatColor.YELLOW + iceCost + " Gold",
            ChatColor.GRAY + "Range: " + ChatColor.YELLOW + iceRange + " blocks",
            ChatColor.GRAY + "Damage: " + ChatColor.YELLOW + iceDamage + " HP",
            ChatColor.GRAY + "Attack Speed: " + ChatColor.YELLOW + iceSpeed + "s",
            "",
            ChatColor.GRAY + "Applies Slowness to all mobs in radius."
        ));

        // Slot 19: Golem Tower
        int golemCost = plugin.getConfig().getInt("towers.golem_1.cost", 400);
        double golemRange = plugin.getConfig().getDouble("towers.golem_1.range", 10.0);
        double golemDamage = plugin.getConfig().getDouble("towers.golem_1.damage", 20.0);
        double golemSpeed = plugin.getConfig().getLong("towers.golem_1.cooldown", 40L) / 20.0;
        gui.setItem(19, createGUIItem(
            Material.IRON_BLOCK,
            ChatColor.GRAY + "" + ChatColor.BOLD + "Golem Tower",
            ChatColor.GRAY + "Base Cost: " + ChatColor.YELLOW + golemCost + " Gold",
            ChatColor.GRAY + "Range: " + ChatColor.YELLOW + golemRange + " blocks",
            ChatColor.GRAY + "Damage: " + ChatColor.YELLOW + golemDamage + " HP",
            ChatColor.GRAY + "Attack Speed: " + ChatColor.YELLOW + golemSpeed + "s",
            "",
            ChatColor.GRAY + "Spawns a Copper Golem (T1) / Iron Golem (T2)",
            ChatColor.GRAY + "that attacks single targets. Iron Golem knocks up."
        ));

        // Slot 20: Happy Ghast Tower
        int happyCost = plugin.getConfig().getInt("towers.happy_ghast_1.cost", 500);
        double happyRange = plugin.getConfig().getDouble("towers.happy_ghast_1.range", 15.0);
        double happyDamage = plugin.getConfig().getDouble("towers.happy_ghast_1.damage", 15.0);
        double happySpeed = plugin.getConfig().getLong("towers.happy_ghast_1.cooldown", 50L) / 20.0;
        gui.setItem(20, createGUIItem(
            Material.GHAST_SPAWN_EGG,
            ChatColor.LIGHT_PURPLE + "" + ChatColor.BOLD + "Happy Ghast Tower",
            ChatColor.GRAY + "Base Cost: " + ChatColor.YELLOW + happyCost + " Gold",
            ChatColor.GRAY + "Range: " + ChatColor.YELLOW + happyRange + " blocks",
            ChatColor.GRAY + "Damage: " + ChatColor.YELLOW + happyDamage + " HP (AoE)",
            ChatColor.GRAY + "Attack Speed: " + ChatColor.YELLOW + happySpeed + "s",
            "",
            ChatColor.GRAY + "Ridable ghast. Shoots fireballs that deal AoE damage.",
            ChatColor.GRAY + "Has 3 tiers. Autopilot mode when unridden."
        ));

        player.openInventory(gui);
        player.playSound(player.getLocation(), Sound.BLOCK_CHEST_OPEN, 0.8f, 1.0f);
    }

    private ItemStack createGUIItem(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            meta.setLore(java.util.Arrays.asList(lore));
            item.setItemMeta(meta);
        }
        return item;
    }

    private void clearNearbyDroppedItemsLater(Location center) {
        org.bukkit.Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (center.getWorld() != null) {
                center.getWorld().getNearbyEntities(center, 6.0, 8.0, 6.0).stream()
                    .filter(e -> e instanceof org.bukkit.entity.Item)
                    .forEach(org.bukkit.entity.Entity::remove);
            }
        }, 1L);
    }

    public void cleanup() {
        java.util.List<String> plotIds = new java.util.ArrayList<>(placedTowers.keySet());
        for (String plotId : plotIds) {
            removeTower(plotId);
        }
        placedTowers.clear();
    }

    public Map<String, Tower> getPlacedTowers() {
        return placedTowers;
    }
}
