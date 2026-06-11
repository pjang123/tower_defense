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
    // Tower type a player is choosing an upgrade path for (set when the path picker GUI opens)
    private final Map<java.util.UUID, TowerType> pendingPathChoice = new HashMap<>();

    public TowerManager(TowerDefense plugin) {
        this.plugin = plugin;
        startTowerTicker();
    }

    public boolean hasTower(String plotId) {
        return placedTowers.containsKey(plotId);
    }

    public void placeTower(String plotId, TowerType type) {
        placeTower(plotId, type, null);
    }

    public void placeTower(String plotId, TowerType type, java.util.UUID ownerId) {
        placeTower(plotId, type, ownerId, null);
    }

    public void placeTower(String plotId, TowerType type, java.util.UUID ownerId, String pathId) {
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
                    if (activeTower.getHolograms().contains(as) || activeTower.getLandmines().contains(as)) {
                        return false;
                    }
                }
                return true;
            })
            .forEach(org.bukkit.entity.Entity::remove);

        Tower tower = new Tower(plotId, center, type);
        tower.setPathId(pathId);
        tower.setOwnerId(ownerId);

        // If an EMP is currently active in this arena, the freshly placed tower starts disabled
        // for the remainder of the EMP window so it can't be used to dodge the disruption.
        String empArena = plugin.getPlotConfigManager().getPlotArena(plotId);
        long empEnd = plugin.getGameManager().getSpellEndTime(empArena, "TOWER_EMP");
        if (empEnd > 0L) {
            tower.setDisabledUntil(empEnd);
        }

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

        // 2. Try loading level-specific NBT structure (e.g., structures/archer_1.nbt).
        // Path towers may provide per-path variants (e.g., structures/turret_gatling_1.nbt).
        String fileName = plugin.getTowerConfigManager().getStructureFile(type);
        File pathSpecificFile = tower.hasPath()
                ? new File(plugin.getDataFolder(), "structures/" + fileName + "_" + tower.getPathId() + "_" + level + ".nbt")
                : null;
        File levelSpecificFile = new File(plugin.getDataFolder(), "structures/" + fileName + "_" + level + ".nbt");
        File genericFile = new File(plugin.getDataFolder(), "structures/" + fileName + ".nbt");
        File targetFile = (pathSpecificFile != null && pathSpecificFile.exists()) ? pathSpecificFile
                : levelSpecificFile.exists() ? levelSpecificFile
                : (genericFile.exists() ? genericFile : null);
        
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

        // Dripstone T2+: visible 3D pointed-dripstone hazard spikes on the track that tag passing
        // mobs as vulnerable (+15% damage taken). Clear any previously-spawned hazard displays first.
        for (Location loc : tower.getHazardTiles()) {
            if (loc.getWorld() == null) continue;
            loc.getWorld().getNearbyEntities(loc, 1.0, 2.0, 1.0).stream()
                .filter(e -> e.getScoreboardTags().contains("td_hazard"))
                .forEach(org.bukkit.entity.Entity::remove);
        }
        tower.getHazardTiles().clear();
        tower.getLandmines().removeIf(s -> s == null || !s.isValid());

        if (type == TowerType.DRIPSTONE && level >= 2) {
            java.util.List<Location> track = getTrackLocationsWithinRange(tower);
            int count = plugin.getTowerConfigManager().getStat(TowerType.DRIPSTONE, level, "hazard_count", 6);
            int placed = 0;

            for (int i = 0; i < 30 && placed < count && !track.isEmpty(); i++) { // up to 30 attempts to find random spots
                Location baseLoc = track.get(new Random().nextInt(track.size())).clone();
                // Randomly offset along the path within the tower radius
                baseLoc.add((Math.random() - 0.5) * 2.5, 0, (Math.random() - 0.5) * 2.5);

                if (baseLoc.distanceSquared(tower.getCenterLocation()) > tower.getRange() * tower.getRange()) {
                    continue;
                }

                Location hazardLoc = baseLoc.clone();
                org.bukkit.entity.BlockDisplay hazardStand = hazardLoc.getWorld().spawn(hazardLoc, org.bukkit.entity.BlockDisplay.class, bd -> {
                    org.bukkit.block.data.type.PointedDripstone data =
                            (org.bukkit.block.data.type.PointedDripstone) Material.POINTED_DRIPSTONE.createBlockData();
                    data.setVerticalDirection(org.bukkit.block.BlockFace.UP); // Point upwards out of the ground
                    bd.setBlock(data);
                    bd.addScoreboardTag("td_hazard");
                    bd.setPersistent(false);
                    // Center the 1x1 block on the location.
                    bd.setTransformation(new org.bukkit.util.Transformation(
                            new org.joml.Vector3f(-0.5f, 0f, -0.5f),
                            new org.joml.Quaternionf(),
                            new org.joml.Vector3f(1f, 1f, 1f),
                            new org.joml.Quaternionf()
                    ));
                });
                tower.getHazardTiles().add(hazardStand.getLocation());
                placed++;
            }
        }

        // Owner-name prefix applied to spawned Golems/Ghasts (e.g. "Steve's Iron Golem").
        String ownerPrefix = "";
        if (tower.getOwnerId() != null) {
            String ownerName = org.bukkit.Bukkit.getOfflinePlayer(tower.getOwnerId()).getName();
            if (ownerName != null) {
                ownerPrefix = org.bukkit.ChatColor.GOLD + ownerName + "'s ";
            }
        }
        final String ownerNamePrefix = ownerPrefix;

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
                        golem.setCustomName(ownerNamePrefix + org.bukkit.ChatColor.GOLD + "Copper Golem");
                        golem.setCustomNameVisible(true);
                    }
                } catch (Exception ignored) {
                    golem = center.getWorld().spawn(spawnLoc, org.bukkit.entity.IronGolem.class, g -> {
                        g.setAI(true);
                        g.setInvulnerable(true);
                        g.setGravity(true);
                        g.setCollidable(false);
                        g.setPersistent(true);
                        g.setCustomName(ownerNamePrefix + org.bukkit.ChatColor.GOLD + "Copper Golem");
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
                    g.setCustomName(ownerNamePrefix + org.bukkit.ChatColor.GRAY + "Iron Golem");
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
                gh.setCustomName(ownerNamePrefix + org.bukkit.ChatColor.LIGHT_PURPLE + "Happy Ghast [Lvl " + level + "]");
                gh.setCustomNameVisible(true);
                setEntityScale(gh, 0.5);
            });
            tower.setSpawnedGhast(ghast);

            // Equip harness so the Happy Ghast can be ridden.
            // Approach 1: setHarnessed(boolean) direct API (Paper 1.21.5+)
            boolean harnessApplied = false;
            try {
                java.lang.reflect.Method m = ghast.getClass().getMethod("setHarnessed", boolean.class);
                m.invoke(ghast, true);
                harnessApplied = true;
                plugin.getLogger().info("[HappyGhast] Harness applied via setHarnessed()");
            } catch (NoSuchMethodException e) {
                plugin.getLogger().info("[HappyGhast] setHarnessed() not found, trying item equip");
            } catch (java.lang.reflect.InvocationTargetException e) {
                plugin.getLogger().warning("[HappyGhast] setHarnessed() threw: " + e.getCause());
            } catch (Exception e) {
                plugin.getLogger().warning("[HappyGhast] setHarnessed() reflection error: " + e);
            }

            // Approach 2: equip a colored harness item in the BODY slot (Happy Ghast wears it like
            // a saddle). There is no generic Material.HARNESS enum calling Material.valueOf("HARNESS")
            // threw IllegalArgumentException and left the ghast unsteerable. We must pick a concrete
            // colored variant; choose it by the ghast's arena (this plugin's team distinction) so the
            // harness also gives a clear visual team color: arena "2" -> RED, otherwise BLUE.
            if (!harnessApplied) {
                try {
                    String ghastArena = plugin.getPlotConfigManager().getPlotArena(tower.getPlotId());
                    org.bukkit.Material harnessMaterial = "2".equals(ghastArena)
                            ? org.bukkit.Material.RED_HARNESS
                            : org.bukkit.Material.BLUE_HARNESS;
                    org.bukkit.inventory.EntityEquipment eq = ghast.getEquipment();
                    if (eq != null) {
                        eq.setItem(org.bukkit.inventory.EquipmentSlot.BODY, new org.bukkit.inventory.ItemStack(harnessMaterial));
                        eq.setDropChance(org.bukkit.inventory.EquipmentSlot.BODY, 0.0f);
                        harnessApplied = true;
                        plugin.getLogger().info("[HappyGhast] " + harnessMaterial.name() + " applied via BODY slot");
                    }
                } catch (Exception e) {
                    plugin.getLogger().warning("[HappyGhast] BODY slot equip failed: " + e);
                }
            }

            if (!harnessApplied) {
                plugin.getLogger().warning("[HappyGhast] All harness approaches failed â€” ghast will not be rideable. Check logs above for details.");
            }
        }

        // Push out any player who ended up inside the tower's bounding box (e.g. standing on the plot
        // while the structure was raised), teleporting them safely to the top of the tower.
        int boundsY = tower.getStructureSize() != null ? tower.getStructureSize().getBlockY() : 3;
        int boundsX = tower.getStructureSize() != null ? tower.getStructureSize().getBlockX() : 1;
        int boundsZ = tower.getStructureSize() != null ? tower.getStructureSize().getBlockZ() : 1;
        Location boxCenter = center.clone().add(0, boundsY / 2.0, 0);
        for (org.bukkit.entity.Entity nearby : center.getWorld().getNearbyEntities(
                boxCenter, boundsX / 2.0 + 0.5, boundsY / 2.0 + 1.0, boundsZ / 2.0 + 0.5)) {
            if (nearby instanceof Player trapped) {
                trapped.teleport(center.clone().add(0, boundsY + 1, 0));
                trapped.sendMessage(ChatColor.YELLOW + "You were moved to the top of the tower.");
            }
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
        // The Poison Tower's structure lacks a tall bounding box, so its hologram floats too high.
        // Pin it slightly lower so it sits just above the tower instead.
        if (tower.getType() == TowerType.POISON) {
            holoHeight = (tower.getStructureSize() != null ? tower.getStructureSize().getBlockY() : 3) + 0.3;
        }

        long cooldown = tower.getCooldown();
        boolean isBoosted = isRedstoneBoosted(tower);
        if (isBoosted) {
            cooldown = Math.max(1L, (long)(cooldown * 0.7));
        }
        String speedStr = String.format("%.1fs", cooldown / 20.0);
        if (isBoosted) {
            speedStr += ChatColor.RED + " âš¡";
        }

        // Prepend the owner's name to the tower title, e.g. "Steve's Archer Tower".
        String ownerPrefix = "";
        if (tower.getOwnerId() != null) {
            String ownerName = org.bukkit.Bukkit.getOfflinePlayer(tower.getOwnerId()).getName();
            if (ownerName != null) {
                ownerPrefix = ChatColor.GOLD + ownerName + "'s ";
            }
        }

        java.util.List<String> lines = new java.util.ArrayList<>();
        lines.add(ownerPrefix + tower.getType().getColor().toString() + ChatColor.BOLD.toString()
                + tower.getType().getDisplayName() + tower.getDisplayPathSuffix() + " " + tower.getRomanLevel());
        
        if (tower.getType() == TowerType.REDSTONE) {
            lines.add(ChatColor.RED + "âœ¦ " + ChatColor.GRAY + "Boost Range: " + ChatColor.GREEN + tower.getRange() + "m" +
                      ChatColor.GRAY + " | " + ChatColor.AQUA + "Boost: " + ChatColor.RED + "30% âš¡");
        } else {
            // ❤ DMG | ⚡  Speed | ✦ Range
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
            for (org.bukkit.entity.Bee bee : tower.getSpawnedBees()) {
                if (bee != null && bee.isValid()) {
                    bee.remove();
                }
            }
            tower.getSpawnedBees().clear();
            for (ArmorStand mine : tower.getLandmines()) {
                if (mine != null && mine.isValid()) {
                    mine.remove();
                }
            }
            tower.getLandmines().clear();

            // Clean up Dripstone hazard BlockDisplays (tracked only by location now).
            for (Location loc : tower.getHazardTiles()) {
                if (loc.getWorld() != null) {
                    loc.getWorld().getNearbyEntities(loc, 1.0, 2.0, 1.0).stream()
                        .filter(e -> e.getScoreboardTags().contains("td_hazard"))
                        .forEach(org.bukkit.entity.Entity::remove);
                }
            }
            tower.getHazardTiles().clear();

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
                        tower.setEmpDisplayed(true);
                        // Make the disabled state obvious: relabel the top hologram line and emit heavy
                        // smoke + redstone particles around the tower.
                        java.util.List<ArmorStand> empStands = tower.getHolograms();
                        if (!empStands.isEmpty() && empStands.get(0) != null && empStands.get(0).isValid()) {
                            empStands.get(0).setCustomName(ChatColor.RED + "" + ChatColor.MAGIC + "||| " + ChatColor.RED + ChatColor.BOLD + "[DISABLED EMP] " + ChatColor.RED + ChatColor.MAGIC + "|||");
                            empStands.get(0).setCustomNameVisible(true);
                        }
                        if (tick % 5 == 0) {
                            Location center = tower.getCenterLocation();
                            center.getWorld().spawnParticle(org.bukkit.Particle.LARGE_SMOKE, center.clone().add(0, 2, 0), 12, 0.5, 0.7, 0.5, 0.02);
                            center.getWorld().spawnParticle(org.bukkit.Particle.DUST, center.clone().add(0, 2.5, 0), 10, 0.5, 0.7, 0.5,
                                    new org.bukkit.Particle.DustOptions(org.bukkit.Color.RED, 1.5f));
                        }
                        continue;
                    }

                    // Tower just came back online after an EMP restore its normal hologram.
                    if (tower.isEmpDisplayed()) {
                        tower.setEmpDisplayed(false);
                        updateHologram(tower);
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
                                        rider.sendMessage(org.bukkit.ChatColor.RED + "âš  You are reaching the edge of the arena boundary! Pushing back.");
                                    }
                                }
                            }
                            continue; // Skip automated attack
                        } else {
                            tower.setAutopilot(true);
                        }
                    }

                    // Golem pathing movement tick: chase the nearest mob on the track within range,
                    // otherwise return to the tower center.
                    if (tower.getType() == TowerType.GOLEM && tower.getSpawnedGolem() != null && tower.getSpawnedGolem().isValid()) {
                        org.bukkit.entity.LivingEntity golem = tower.getSpawnedGolem();
                        if (golem instanceof org.bukkit.entity.Mob golemMob && tick % 5 == 0) {
                            String golemArena = plugin.getPlotConfigManager().getPlotArena(tower.getPlotId());
                            Mob nearestTrackMob = null;
                            double nearestDistSq = Double.MAX_VALUE;
                            for (Mob candidate : getMobsInRadius(tower.getCenterLocation(), tower.getRange(), golemArena)) {
                                double dSq = candidate.getLocation().distanceSquared(golem.getLocation());
                                if (dSq < nearestDistSq) {
                                    nearestDistSq = dSq;
                                    nearestTrackMob = candidate;
                                }
                            }
                            if (nearestTrackMob != null) {
                                golemMob.getPathfinder().moveTo(nearestTrackMob.getLocation(), 1.25);
                                golemMob.setTarget(nearestTrackMob);
                            } else {
                                golemMob.getPathfinder().moveTo(tower.getCenterLocation(), 1.0);
                                golemMob.setTarget(null);
                            }
                        }
                    }

                    // Dripstone T2+: hazard tiles tag passing mobs as vulnerable (+15% damage taken)
                    if (tower.getType() == TowerType.DRIPSTONE && !tower.getHazardTiles().isEmpty()) {
                        tickDripstoneHazards(tower, tick);
                    }

                    // Bombardier landmines path: mines are the attack, skip the generic targeting flow
                    if (tower.getType() == TowerType.BOMBARDIER && "landmines".equals(tower.getPathId())) {
                        tickLandmines(tower, tick);
                        continue;
                    }

                    // Beehive: bees are the attack, skip the generic targeting flow
                    if (tower.getType() == TowerType.BEEHIVE) {
                        tickBeehive(tower, tick);
                        continue;
                    }

                    long cooldown = tower.getCooldown();
                    String towerArena = plugin.getPlotConfigManager().getPlotArena(tower.getPlotId());
                    if (plugin.getGameManager().isSpellActive(towerArena, "OVERCHARGE")) {
                        cooldown = Math.max(1L, (long) (cooldown * 0.85)); // 15% attack-speed boost
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

    // Cooldown with the same overcharge/redstone modifiers the generic attack flow applies
    private long getEffectiveCooldown(Tower tower) {
        long cooldown = tower.getCooldown();
        String arena = plugin.getPlotConfigManager().getPlotArena(tower.getPlotId());
        if (plugin.getGameManager().isSpellActive(arena, "OVERCHARGE")) {
            cooldown = Math.max(1L, (long) (cooldown * 0.85));
        }
        if (isRedstoneBoosted(tower)) {
            cooldown = Math.max(1L, (long) (cooldown * 0.7));
        }
        return cooldown;
    }

    // Dripstone T2+: tag mobs standing on hazard tiles as vulnerable (+15% damage taken, 4s)
    private void tickDripstoneHazards(Tower tower, long tick) {
        if (tick % 10 == 0) {
            for (Location tile : tower.getHazardTiles()) {
                tile.getWorld().spawnParticle(org.bukkit.Particle.BLOCK, tile, 6, 0.4, 0.2, 0.4,
                        Material.POINTED_DRIPSTONE.createBlockData());
            }
        }
        if (tick % 2 != 0) return;
        String arena = plugin.getPlotConfigManager().getPlotArena(tower.getPlotId());
        org.bukkit.NamespacedKey vulnKey = new org.bukkit.NamespacedKey(plugin, "td_vulnerable_until");
        long until = System.currentTimeMillis() + 4000L;
        for (Location tile : tower.getHazardTiles()) {
            for (Mob mob : getMobsInRadius(tile, 1.5, arena)) {
                mob.getPersistentDataContainer().set(vulnKey, org.bukkit.persistence.PersistentDataType.LONG, until);
            }
        }
    }

    // Bombardier landmines: detonate mines on mob contact every tick; deploy a new mine on cooldown (max 3)
    private void tickLandmines(Tower tower, long tick) {
        String arena = plugin.getPlotConfigManager().getPlotArena(tower.getPlotId());

        java.util.Iterator<ArmorStand> it = tower.getLandmines().iterator();
        while (it.hasNext()) {
            ArmorStand mine = it.next();
            if (mine == null || !mine.isValid()) {
                it.remove();
                continue;
            }
            // Mobs walk above the sunk stand's origin, so check slightly higher with a wider radius.
            Location trigger = mine.getLocation().clone().add(0, 1.5, 0);
            java.util.List<Mob> hit = getMobsInRadius(trigger, 2.0, arena);
            if (!hit.isEmpty()) {
                trigger.getWorld().spawnParticle(org.bukkit.Particle.EXPLOSION_EMITTER, trigger, 1);
                trigger.getWorld().playSound(trigger, Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 1.0f);
                hit.get(0).damage(tower.getDamage());
                mine.remove();
                it.remove();
            }
        }

        if (tower.getLandmines().size() >= 3) return;
        if (tick - tower.getLastAttackTick() < getEffectiveCooldown(tower)) return;

        java.util.List<Location> track = getTrackLocationsWithinRange(tower);
        if (track.isEmpty()) return;

        // Pick a random in-range waypoint, then scatter the mine 1-2 blocks off the path center so
        // mines spread out across the lane instead of stacking on waypoint centers.
        Location wp1 = track.get(new Random().nextInt(track.size()));
        Location mineLoc = wp1.clone();
        mineLoc.add((Math.random() - 0.5) * 3.0, 0, (Math.random() - 0.5) * 3.0);

        // Ensure the offset mine is still inside the tower's shooting radius
        if (mineLoc.distanceSquared(tower.getCenterLocation()) > tower.getRange() * tower.getRange()) {
            return; // Abort tick, will try again next tick
        }

        // Abort this tick if the chosen spot is too close to an existing mine
        for (ArmorStand existing : tower.getLandmines()) {
            if (existing.isValid() && existing.getLocation().distanceSquared(mineLoc) < 3.0) {
                return;
            }
        }

        // Sink the invisible stand so its TNT helmet renders on the ground
        Location standLoc = mineLoc.clone().add(0, -1.4, 0);
        ArmorStand stand = standLoc.getWorld().spawn(standLoc, ArmorStand.class, as -> {
            as.setInvisible(true);
            as.setMarker(true);
            as.setGravity(false);
            as.setInvulnerable(true);
            as.setPersistent(false);
            if (as.getEquipment() != null) {
                as.getEquipment().setHelmet(new ItemStack(Material.TNT));
            }
        });
        tower.getLandmines().add(stand);
        tower.setLastAttackTick(tick);
        mineLoc.getWorld().playSound(mineLoc, Sound.ENTITY_TNT_PRIMED, 0.6f, 1.5f);
    }

    // Beehive: spawn bees on cooldown up to the path's cap, steer them at mobs, detonate on contact
    private void tickBeehive(Tower tower, long tick) {
        String arena = plugin.getPlotConfigManager().getPlotArena(tower.getPlotId());
        tower.getSpawnedBees().removeIf(bee -> bee == null || !bee.isValid() || bee.isDead());

        boolean goliath = "goliath".equals(tower.getPathId());
        int cap = goliath ? 1 : plugin.getTowerConfigManager().getStat(
                TowerType.BEEHIVE, tower.getPathId(), tower.getLevel(), "bee_count", 1);

        // Base Level 1 (default path) always fields a single starter bee.
        if (tower.getLevel() == 1) cap = 1;

        double structureHeight = tower.getStructureSize() != null ? tower.getStructureSize().getBlockY() : 3.0;
        Location hiveTop = tower.getCenterLocation().clone().add(0, structureHeight + 1.0, 0);

        // Spawn the entire swarm at once once the previous one has been spent and the hive is ready.
        if (tower.getSpawnedBees().isEmpty() && tick - tower.getLastAttackTick() >= getEffectiveCooldown(tower)) {
            for (int i = 0; i < cap; i++) {
                org.bukkit.entity.Bee bee = hiveTop.getWorld().spawn(hiveTop, org.bukkit.entity.Bee.class, b -> {
                    b.setInvulnerable(true);
                    b.setCollidable(false);
                    b.setPersistent(true);
                    b.setRemoveWhenFarAway(false);
                    // td_tower_pet (not td_mob): keeps bees out of mob rewards/health-bar systems
                    b.getPersistentDataContainer().set(new org.bukkit.NamespacedKey(plugin, "td_tower_pet"),
                            org.bukkit.persistence.PersistentDataType.BYTE, (byte) 1);
                    try {
                        org.bukkit.Bukkit.getMobGoals().removeAllGoals(b);
                    } catch (Throwable ignored) {
                    }
                });
                if (goliath) {
                    setEntityScale(bee, plugin.getTowerConfigManager().getStat(
                            TowerType.BEEHIVE, tower.getPathId(), tower.getLevel(), "scale", 2.0));
                }
                tower.getSpawnedBees().add(bee);
            }
            tower.setLastAttackTick(tick);
        }

        if (tick % 2 != 0) return;
        java.util.List<Mob> targets = getMobsInRadius(tower.getCenterLocation(), tower.getRange(), arena);

        int targetIndex = 0;
        int index = 0;
        java.util.Iterator<org.bukkit.entity.Bee> beeIt = tower.getSpawnedBees().iterator();
        while (beeIt.hasNext()) {
            org.bukkit.entity.Bee bee = beeIt.next();
            index++;

            // Swarm bees each take a different target; the single Goliath bee always uses the lead.
            Mob assignedTarget = null;
            if (targetIndex < targets.size()) {
                assignedTarget = targets.get(targetIndex);
                if (!goliath) {
                    targetIndex++;
                }
            }

            if (assignedTarget == null || !assignedTarget.isValid()) {
                // No target available for this bee, orbit smoothly around the hive while idle
                double orbitSpeed = 0.05;
                double radius = 1.5 + (index * 0.2); // slight offset per bee
                double angle = (tick * orbitSpeed) + (index * Math.PI / 2);
                Location orbitLoc = hiveTop.clone().add(Math.cos(angle) * radius, Math.sin(tick * 0.05) * 0.5, Math.sin(angle) * radius);

                org.bukkit.util.Vector toOrbit = orbitLoc.toVector().subtract(bee.getLocation().toVector());
                if (toOrbit.lengthSquared() > 0.01) {
                    bee.setVelocity(toOrbit.normalize().multiply(0.3));
                }
                continue;
            }

            double distSq = assignedTarget.getLocation().distanceSquared(bee.getLocation());
            if (distSq <= 1.44) {
                Location pop = bee.getLocation();
                pop.getWorld().spawnParticle(org.bukkit.Particle.EXPLOSION, pop, 1);
                pop.getWorld().playSound(pop, Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 1.0f);
                // Clear i-frames first so multiple bees landing the same tick all deal damage.
                assignedTarget.setNoDamageTicks(0);
                assignedTarget.damage(tower.getDamage());
                bee.remove();
                beeIt.remove();
            } else {
                org.bukkit.util.Vector dir = assignedTarget.getLocation().add(0, 0.5, 0).toVector()
                        .subtract(bee.getLocation().toVector()).normalize();
                bee.setVelocity(dir.multiply(0.45));
            }
        }
    }

    private Mob findTarget(Tower tower) {
        Location towerLoc = tower.getCenterLocation();

        // Happy Ghasts can leave their base (ridden or roaming on autopilot). When the ghast has
        // drifted away horizontally from its tower, sweep for targets around the Ghast's live
        // location instead of the static tower block so its detection radius tracks with it.
        if (tower.getType() == TowerType.HAPPY_GHAST
                && tower.getSpawnedGhast() != null && tower.getSpawnedGhast().isValid()) {
            Location ghastLoc = tower.getSpawnedGhast().getLocation();
            double dx = ghastLoc.getX() - towerLoc.getX();
            double dz = ghastLoc.getZ() - towerLoc.getZ();
            if (dx * dx + dz * dz > 9.0) { // >3 blocks horizontal: ghast is off-station
                towerLoc = ghastLoc;
            }
        }

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

            // Invisible mobs (e.g. higher-tier Spiders) are untargetable by every tower except Fire.
            if (tower.getType() != TowerType.FIRE
                    && mob.hasPotionEffect(org.bukkit.potion.PotionEffectType.INVISIBILITY)) {
                continue;
            }

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
                // Breeze deflects single-target Archer projectiles.
                if (target.getType() == org.bukkit.entity.EntityType.BREEZE) {
                    start.getWorld().playSound(target.getLocation(), Sound.ITEM_SHIELD_BLOCK, 1f, 1f);
                    return;
                }
                target.damage(tower.getDamage());
                drawParticleLine(start, target.getEyeLocation(), org.bukkit.Particle.CRIT);
                start.getWorld().playSound(start, Sound.ENTITY_ARROW_SHOOT, 0.8f, 1.2f);
            }
            case FIRE -> {
                int fireTicks = plugin.getTowerConfigManager().getStat(TowerType.FIRE, tower.getLevel(), "fire_ticks", 60 + (tower.getLevel() - 1) * 40);
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
                int slowLvl = plugin.getTowerConfigManager().getStat(TowerType.PRISMARINE, tower.getLevel(), "slowness_level", tower.getLevel());
                int slowDur = plugin.getTowerConfigManager().getStat(TowerType.PRISMARINE, tower.getLevel(), "slowness_duration", 40 + (tower.getLevel() - 1) * 20);
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
                // Chorus-immune mobs (e.g. Enderman) skip the rollback teleportation entirely.
                if (isMobImmuneToTower(target, "CHORUS")) {
                    return;
                }
                target.damage(tower.getDamage());

                com.pauljang.towerDefense.entities.TDMob tdMob = null;
                for (com.pauljang.towerDefense.entities.TDMob active : plugin.getMobManager().getActiveMobs()) {
                    if (active.getEntity().equals(target)) {
                        tdMob = active;
                        break;
                    }
                }
                if (tdMob != null) {
                    // Get the teleport-back distance from config (in blocks, not waypoints)
                    double blocksBack = plugin.getTowerConfigManager().getStat(TowerType.CHORUS, tower.getLevel(), "teleport_back", 10.0);

                    Location currentLoc = target.getLocation();
                    java.util.List<String> history = tdMob.getPathHistory();
                    java.util.Map<String, com.pauljang.towerDefense.data.TDWaypoint> graph = tdMob.getWaypointGraph();

                    plugin.getLogger().info("[CHORUS DEBUG] Tier: " + tower.getLevel() + ", BlocksBack: " + blocksBack + ", Current: " + currentLoc + ", History size: " + history.size());

                    if (history.isEmpty()) {
                        plugin.getLogger().warning("[CHORUS DEBUG] History is empty, cannot teleport!");
                        return;
                    }

                    // Walk backwards along the path by blocksBack distance.
                    // pathHistory includes currentWaypointId at the end (the waypoint the mob is targeting),
                    // so the second-to-last entry is the waypoint the mob actually last passed through.

                    if (history.size() < 2) {
                        plugin.getLogger().warning("[CHORUS DEBUG] Not enough history to teleport back!");
                        return;
                    }

                    // Start from the last waypoint the mob actually passed (not the current target)
                    String lastPassedWpId = history.get(history.size() - 2);
                    com.pauljang.towerDefense.data.TDWaypoint lastPassedWp = graph.get(lastPassedWpId);
                    if (lastPassedWp == null) {
                        plugin.getLogger().warning("[CHORUS DEBUG] Last passed waypoint not found!");
                        return;
                    }

                    Location lastPassedLoc = lastPassedWp.getLocation();

                    // Distance from current location back to the last passed waypoint
                    double distToLastPassed = currentLoc.distance(lastPassedLoc);
                    double remainingDist = blocksBack;

                    Location teleportLoc = null;
                    String newTargetWpId = null; // The waypoint the mob should target after teleport

                    // Case 1: blocksBack is less than distance to last passed waypoint
                    // Mob stays on current segment (between lastPassed and current target)
                    if (remainingDist <= distToLastPassed) {
                        org.bukkit.util.Vector direction = lastPassedLoc.toVector().subtract(currentLoc.toVector()).normalize();
                        teleportLoc = currentLoc.clone().add(direction.multiply(remainingDist));
                        // Mob is still on same segment, keep current target waypoint
                        newTargetWpId = history.get(history.size() - 1);
                        plugin.getLogger().info("[CHORUS DEBUG] Interpolating on current segment, target stays: " + newTargetWpId);
                    } else {
                        // Case 2: Need to go back past the last waypoint
                        remainingDist -= distToLastPassed;
                        Location walkbackLoc = lastPassedLoc.clone();

                        // Walk backwards through earlier waypoints
                        for (int i = history.size() - 2; i > 0; i--) {
                            String currentWpId = history.get(i);
                            String prevWpId = history.get(i - 1);

                            com.pauljang.towerDefense.data.TDWaypoint prevWp = graph.get(prevWpId);
                            if (prevWp == null) continue;

                            Location prevLoc = prevWp.getLocation();
                            double segmentDist = walkbackLoc.distance(prevLoc);

                            if (remainingDist <= segmentDist) {
                                // Found the segment - interpolate
                                org.bukkit.util.Vector direction = prevLoc.toVector().subtract(walkbackLoc.toVector()).normalize();
                                teleportLoc = walkbackLoc.clone().add(direction.multiply(remainingDist));
                                // Mob is now between prevWp and currentWp, so it should target currentWp
                                newTargetWpId = currentWpId;
                                plugin.getLogger().info("[CHORUS DEBUG] Found target on segment between " + prevWpId + " and " + currentWpId + ", new target: " + newTargetWpId);
                                break;
                            } else {
                                // Keep going backwards
                                remainingDist -= segmentDist;
                                walkbackLoc = prevLoc.clone();
                            }
                        }

                        // Fallback: if we ran out of path, go to waypoint 0 and target waypoint 1
                        if (teleportLoc == null) {
                            String firstWpId = history.get(0);
                            com.pauljang.towerDefense.data.TDWaypoint firstWp = graph.get(firstWpId);
                            if (firstWp != null) {
                                teleportLoc = firstWp.getLocation().clone();
                                // If there's a waypoint after 0, target it; otherwise target 0
                                newTargetWpId = history.size() > 1 ? history.get(1) : firstWpId;
                                plugin.getLogger().info("[CHORUS DEBUG] Ran out of path, teleporting to waypoint 0, new target: " + newTargetWpId);
                            }
                        }
                    }

                    if (teleportLoc == null || newTargetWpId == null) {
                        plugin.getLogger().warning("[CHORUS DEBUG] Could not calculate teleport location!");
                        return;
                    }

                    // Store the new target waypoint to update after respawn
                    final String finalNewTargetWpId = newTargetWpId;

                    // Adjust Y coordinate - waypoints are at block level, mobs need to be higher
                    // Check if this is a flying mob first
                    String presetKey = target.getPersistentDataContainer().get(
                        new org.bukkit.NamespacedKey(plugin, "td_preset"),
                        org.bukkit.persistence.PersistentDataType.STRING
                    );
                    if (presetKey == null) presetKey = target.getType().name().toLowerCase();
                    double heightOffset = plugin.getConfig().getDouble("mobs." + presetKey + ".height-offset", 0.0);

                    if (heightOffset > 0.0) {
                        // Flying mob - use heightOffset only (already includes base height)
                        teleportLoc.add(0, heightOffset, 0);
                    } else {
                        // Ground mob - add 1 block to stand on top of waypoint block
                        teleportLoc.add(0, 1, 0);
                    }

                    // CRITICAL: Do NOT change currentWaypointId or pathHistory!
                    // The mob should keep targeting the same waypoint it was heading toward.
                    // This way, after teleporting backwards, it must walk forward again to reach it.

                    // Visual and audio effects at current location
                    target.getWorld().spawnParticle(org.bukkit.Particle.PORTAL, currentLoc, 15, 0.5, 0.5, 0.5, 0.1);
                    target.getWorld().playSound(currentLoc, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);

                    plugin.getLogger().info("[CHORUS DEBUG] Teleporting from " + currentLoc + " to " + teleportLoc + " (distance: " + currentLoc.distance(teleportLoc) + " blocks)");

                    // Store the final teleport location
                    final Location finalTeleportLoc = teleportLoc;
                    final Mob finalTarget = target;
                    final com.pauljang.towerDefense.entities.TDMob finalTDMob = tdMob;

                    // Pause movement for 10 ticks (0.5 seconds) to let the teleport stick
                    tdMob.setTeleportedUntil(System.currentTimeMillis() + 500);

                    // Defer teleport to next tick to avoid conflicts with movement system
                    org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> {
                        // Stop pathfinder and zero velocity
                        finalTarget.getPathfinder().stopPathfinding();
                        finalTarget.setVelocity(new org.bukkit.util.Vector(0, 0, 0));

                        // Ensure chunk is loaded
                        if (!finalTeleportLoc.getChunk().isLoaded()) {
                            finalTeleportLoc.getChunk().load();
                        }

                        // Check if entity is still valid
                        if (!finalTarget.isValid() || finalTarget.isDead()) {
                            plugin.getLogger().warning("[CHORUS DEBUG] Entity is dead/invalid, cannot teleport");
                            return;
                        }

                        // Check if destination is safe (not inside blocks)
                        org.bukkit.block.Block atFeet = finalTeleportLoc.getBlock();
                        org.bukkit.block.Block atHead = finalTeleportLoc.clone().add(0, 1, 0).getBlock();
                        plugin.getLogger().info("[CHORUS DEBUG] Destination blocks - Feet: " + atFeet.getType() + ", Head: " + atHead.getType());

                        // FORCE teleport by removing and re-spawning at new location
                        // This bypasses all teleport checks and events
                        org.bukkit.entity.EntityType entityType = finalTarget.getType();
                        double health = finalTarget.getHealth();
                        org.bukkit.attribute.AttributeInstance maxHealthAttr = finalTarget.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
                        double maxHealth = maxHealthAttr != null ? maxHealthAttr.getValue() : 20.0;

                        // Get all PDC data
                        org.bukkit.persistence.PersistentDataContainer pdc = finalTarget.getPersistentDataContainer();

                        // Check if mob is riding a vehicle (e.g., skeleton on skeleton horse)
                        org.bukkit.entity.Entity vehicle = finalTarget.getVehicle();
                        org.bukkit.entity.EntityType vehicleType = null;
                        if (vehicle != null) {
                            vehicleType = vehicle.getType();
                            plugin.getLogger().info("[CHORUS DEBUG] Mob has vehicle: " + vehicleType);
                        }

                        // Check that TDMob is in activeMobs before removing
                        boolean wasInList = plugin.getMobManager().getActiveMobs().remove(finalTDMob);
                        if (!wasInList) {
                            plugin.getLogger().warning("[CHORUS DEBUG] TDMob was not in activeMobs list!");
                        }

                        // Remove old entity and vehicle (but keep reference for copying data)
                        if (vehicle != null) {
                            vehicle.remove();
                        }
                        finalTarget.remove();

                        // Spawn new vehicle first if there was one
                        org.bukkit.entity.Entity newVehicle = null;
                        if (vehicleType != null) {
                            newVehicle = finalTeleportLoc.getWorld().spawnEntity(finalTeleportLoc, vehicleType);
                            // Configure vehicle - use scoreboard team for collision
                            if (newVehicle instanceof org.bukkit.entity.Mob vehicleMob) {
                                org.bukkit.Bukkit.getMobGoals().removeAllGoals(vehicleMob);
                                org.bukkit.scoreboard.Scoreboard scoreboard = org.bukkit.Bukkit.getScoreboardManager().getMainScoreboard();
                                org.bukkit.scoreboard.Team tdMobTeam = scoreboard.getTeam("td_mobs");
                                if (tdMobTeam == null) {
                                    tdMobTeam = scoreboard.registerNewTeam("td_mobs");
                                    tdMobTeam.setOption(org.bukkit.scoreboard.Team.Option.COLLISION_RULE, org.bukkit.scoreboard.Team.OptionStatus.NEVER);
                                }
                                tdMobTeam.addEntry(vehicleMob.getUniqueId().toString());
                                vehicleMob.setRemoveWhenFarAway(false);
                                vehicleMob.setPersistent(true);
                            }
                            // Mark the mount as a TD mob so it doesn't burn in sunlight (existing listener handles td_mob tag)
                            if (newVehicle instanceof org.bukkit.entity.AbstractHorse horse) {
                                horse.getPersistentDataContainer().set(
                                    new org.bukkit.NamespacedKey(plugin, "td_mob"),
                                    org.bukkit.persistence.PersistentDataType.BYTE, (byte) 1);
                            }
                            plugin.getLogger().info("[CHORUS DEBUG] Spawned new vehicle");
                        }

                        // Spawn new entity at target location
                        org.bukkit.entity.Mob newEntity = (org.bukkit.entity.Mob) finalTeleportLoc.getWorld().spawnEntity(
                            finalTeleportLoc, entityType);

                        // Mount the new entity on the new vehicle
                        if (newVehicle != null) {
                            newVehicle.addPassenger(newEntity);
                            plugin.getLogger().info("[CHORUS DEBUG] Mounted entity on vehicle");
                        }

                        // Restore properties - use scoreboard team for collision instead of setCollidable
                        org.bukkit.scoreboard.Scoreboard scoreboard = org.bukkit.Bukkit.getScoreboardManager().getMainScoreboard();
                        org.bukkit.scoreboard.Team tdMobTeam = scoreboard.getTeam("td_mobs");
                        if (tdMobTeam == null) {
                            tdMobTeam = scoreboard.registerNewTeam("td_mobs");
                            tdMobTeam.setOption(org.bukkit.scoreboard.Team.Option.COLLISION_RULE, org.bukkit.scoreboard.Team.OptionStatus.NEVER);
                        }
                        tdMobTeam.addEntry(newEntity.getUniqueId().toString());
                        newEntity.setRemoveWhenFarAway(false);
                        newEntity.setPersistent(true);
                        org.bukkit.Bukkit.getMobGoals().removeAllGoals(newEntity);

                        // Copy ALL attributes from old entity to new entity
                        if (maxHealthAttr != null) {
                            org.bukkit.attribute.AttributeInstance newMaxHealthAttr = newEntity.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
                            if (newMaxHealthAttr != null) {
                                newMaxHealthAttr.setBaseValue(maxHealth);
                                newEntity.setHealth(health);
                            }
                        }

                        // Copy movement speed
                        org.bukkit.attribute.AttributeInstance oldSpeed = finalTarget.getAttribute(org.bukkit.attribute.Attribute.MOVEMENT_SPEED);
                        if (oldSpeed != null) {
                            org.bukkit.attribute.AttributeInstance newSpeed = newEntity.getAttribute(org.bukkit.attribute.Attribute.MOVEMENT_SPEED);
                            if (newSpeed != null) {
                                newSpeed.setBaseValue(oldSpeed.getBaseValue());
                            }
                        }

                        // Copy knockback resistance
                        org.bukkit.attribute.AttributeInstance oldKB = finalTarget.getAttribute(org.bukkit.attribute.Attribute.KNOCKBACK_RESISTANCE);
                        if (oldKB != null) {
                            org.bukkit.attribute.AttributeInstance newKB = newEntity.getAttribute(org.bukkit.attribute.Attribute.KNOCKBACK_RESISTANCE);
                            if (newKB != null) {
                                newKB.setBaseValue(oldKB.getBaseValue());
                            }
                        }

                        // Copy armor
                        org.bukkit.attribute.AttributeInstance oldArmor = finalTarget.getAttribute(org.bukkit.attribute.Attribute.ARMOR);
                        if (oldArmor != null) {
                            org.bukkit.attribute.AttributeInstance newArmor = newEntity.getAttribute(org.bukkit.attribute.Attribute.ARMOR);
                            if (newArmor != null) {
                                newArmor.setBaseValue(oldArmor.getBaseValue());
                            }
                        }

                        // Copy equipment
                        if (finalTarget.getEquipment() != null && newEntity.getEquipment() != null) {
                            newEntity.getEquipment().setHelmet(finalTarget.getEquipment().getHelmet());
                            newEntity.getEquipment().setItemInMainHand(finalTarget.getEquipment().getItemInMainHand());
                        }

                        // Copy baby/adult state for zombies
                        if (newEntity instanceof org.bukkit.entity.Zombie newZombie && finalTarget instanceof org.bukkit.entity.Zombie oldZombie) {
                            newZombie.setBaby(oldZombie.isBaby());
                        }

                        // Copy gravity
                        newEntity.setGravity(finalTarget.hasGravity());

                        // Copy all PDC data to new entity
                        for (org.bukkit.NamespacedKey key : pdc.getKeys()) {
                            // Copy each data type
                            if (pdc.has(key, org.bukkit.persistence.PersistentDataType.STRING)) {
                                newEntity.getPersistentDataContainer().set(key, org.bukkit.persistence.PersistentDataType.STRING,
                                    pdc.get(key, org.bukkit.persistence.PersistentDataType.STRING));
                            } else if (pdc.has(key, org.bukkit.persistence.PersistentDataType.INTEGER)) {
                                newEntity.getPersistentDataContainer().set(key, org.bukkit.persistence.PersistentDataType.INTEGER,
                                    pdc.get(key, org.bukkit.persistence.PersistentDataType.INTEGER));
                            } else if (pdc.has(key, org.bukkit.persistence.PersistentDataType.BYTE)) {
                                newEntity.getPersistentDataContainer().set(key, org.bukkit.persistence.PersistentDataType.BYTE,
                                    pdc.get(key, org.bukkit.persistence.PersistentDataType.BYTE));
                            } else if (pdc.has(key, org.bukkit.persistence.PersistentDataType.LONG)) {
                                newEntity.getPersistentDataContainer().set(key, org.bukkit.persistence.PersistentDataType.LONG,
                                    pdc.get(key, org.bukkit.persistence.PersistentDataType.LONG));
                            } else if (pdc.has(key, org.bukkit.persistence.PersistentDataType.DOUBLE)) {
                                newEntity.getPersistentDataContainer().set(key, org.bukkit.persistence.PersistentDataType.DOUBLE,
                                    pdc.get(key, org.bukkit.persistence.PersistentDataType.DOUBLE));
                            }
                        }

                        // Update TDMob to point to new entity using reflection
                        java.lang.reflect.Field entityField;
                        try {
                            entityField = finalTDMob.getClass().getDeclaredField("entity");
                            entityField.setAccessible(true);
                            entityField.set(finalTDMob, newEntity);
                            plugin.getLogger().info("[CHORUS DEBUG] Successfully updated TDMob entity reference");
                        } catch (Exception e) {
                            plugin.getLogger().severe("[CHORUS DEBUG] Failed to update TDMob entity: " + e.getMessage());
                            e.printStackTrace();
                            // If reflection fails, the mob will be lost - clean up new entity
                            newEntity.remove();
                            return;
                        }

                        // Update the mob's waypoint state to match its new position on the path
                        // Trim history to only include waypoints before the new target
                        java.util.List<String> newHistory = new java.util.ArrayList<>();
                        for (String wpId : finalTDMob.getPathHistory()) {
                            if (wpId.equals(finalNewTargetWpId)) {
                                break;
                            }
                            newHistory.add(wpId);
                        }
                        finalTDMob.setPathHistory(newHistory);
                        finalTDMob.setCurrentWaypointId(finalNewTargetWpId);
                        plugin.getLogger().info("[CHORUS DEBUG] Updated waypoint state - target: " + finalNewTargetWpId + ", history size: " + newHistory.size());

                        // Re-add to active mobs - CRITICAL for mob to continue existing
                        if (!plugin.getMobManager().getActiveMobs().contains(finalTDMob)) {
                            plugin.getMobManager().getActiveMobs().add(finalTDMob);
                            plugin.getLogger().info("[CHORUS DEBUG] Re-added TDMob to activeMobs list");
                        } else {
                            plugin.getLogger().warning("[CHORUS DEBUG] TDMob already in activeMobs list!");
                        }

                        // Update health bar
                        plugin.getMobManager().updateHealthBar(newEntity);

                        plugin.getLogger().info("[CHORUS DEBUG] Force respawn successful, new location: " + newEntity.getLocation());

                        // Effects at destination
                        newEntity.getWorld().spawnParticle(org.bukkit.Particle.PORTAL, finalTeleportLoc, 15, 0.5, 0.5, 0.5, 0.1);
                        newEntity.getWorld().playSound(finalTeleportLoc, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.2f);

                        // Zero velocity
                        newEntity.setVelocity(new org.bukkit.util.Vector(0, 0, 0));
                    });
                }
            }
            case POISON -> {
                int poisonDur = plugin.getTowerConfigManager().getStat(TowerType.POISON, tower.getLevel(), "poison_duration", 60 + (tower.getLevel() - 1) * 20);
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
                org.bukkit.NamespacedKey poisonedUntilKey = new org.bukkit.NamespacedKey(plugin, "td_poisoned_until");
                long poisonEndTime = System.currentTimeMillis() + (poisonDur * 50L);
                for (Mob mob : targets) {
                    if (isMobImmuneToTower(mob, "POISON")) {
                        continue; // Poison-immune mobs take no damage and show no ðŸ¤¢ indicator
                    }
                    mob.getPersistentDataContainer().set(poisonDmgKey, org.bukkit.persistence.PersistentDataType.DOUBLE, damage);
                    // Drive poison through a PDC timestamp rather than the vanilla POISON effect: undead
                    // mobs (Zombies, Skeletons, â€¦) are immune to that effect, which silently dropped both
                    // the damage-over-time and the ðŸ¤¢ health-bar symbol. Extend the window if longer.
                    long existing = mob.getPersistentDataContainer().getOrDefault(poisonedUntilKey, org.bukkit.persistence.PersistentDataType.LONG, 0L);
                    mob.getPersistentDataContainer().set(poisonedUntilKey, org.bukkit.persistence.PersistentDataType.LONG, Math.max(existing, poisonEndTime));
                }
                start.getWorld().playSound(start, Sound.BLOCK_BREWING_STAND_BREW, 0.8f, 1.0f);
            }
            case ICE -> {
                int slowDur = plugin.getTowerConfigManager().getStat(TowerType.ICE, tower.getLevel(), "freeze_duration", 40 + (tower.getLevel() - 1) * 20);
                double damage = tower.getDamage();
                java.util.List<Mob> targets = getMobsInRadius(tower.getCenterLocation(), range, towerArena);
                org.bukkit.NamespacedKey freezeKey = new org.bukkit.NamespacedKey(plugin, "td_frozen_until");
                // Ice Tower (Freeze) checks td_freeze_immune; Prismarine (Slow) keeps td_slow_immune.
                org.bukkit.NamespacedKey freezeImmuneKey = new org.bukkit.NamespacedKey(plugin, "td_freeze_immune");

                for (Mob mob : targets) {
                    if (mob.getPersistentDataContainer().has(freezeImmuneKey, org.bukkit.persistence.PersistentDataType.BYTE)
                            || isMobImmuneToTower(mob, "ICE") || isMobImmuneToTower(mob, "SLOW")) {
                        continue; // Skip freeze/ice-immune mobs (e.g. Warden) no damage, freeze, or slowness
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
                    damage = plugin.getTowerConfigManager().getDamage(TowerType.GOLEM, 1, 150.0); // Copper Golem
                } else {
                    damage = plugin.getTowerConfigManager().getDamage(TowerType.GOLEM, 2, 300.0); // Iron Golem
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
            case DRIPSTONE -> {
                playDripstoneStrike(tower, target.getLocation(), tower.getDamage(), target, false);

                // T3 "Cave-In": a 3-wide, 5-long wave of spikes sweeps back down the path behind
                // the target, damaging everything in the lane.
                if (tower.getLevel() >= 3) {
                    com.pauljang.towerDefense.entities.TDMob tdMob = plugin.getMobManager().getActiveMobs().stream()
                            .filter(m -> m.getEntity().equals(target)).findFirst().orElse(null);

                    if (tdMob != null && tdMob.getPathHistory().size() >= 2) {
                        Location currentLoc = target.getLocation();
                        String prevWpId = tdMob.getPathHistory().get(tdMob.getPathHistory().size() - 2);
                        com.pauljang.towerDefense.data.TDWaypoint prevWp = tdMob.getWaypointGraph().get(prevWpId);

                        if (prevWp != null && prevWp.getLocation() != null) {
                            org.bukkit.util.Vector backDir = prevWp.getLocation().toVector().subtract(currentLoc.toVector()).normalize();
                            org.bukkit.util.Vector rightDir = new org.bukkit.util.Vector(-backDir.getZ(), 0, backDir.getX()).normalize();

                            double waveDamage = tower.getDamage() * 0.5;

                            // 5 blocks back, 3 blocks wide
                            for (int length = 1; length <= 5; length++) {
                                for (int width = -1; width <= 1; width++) {
                                    Location strikeLoc = currentLoc.clone()
                                            .add(backDir.clone().multiply(length))
                                            .add(rightDir.clone().multiply(width));

                                    long delay = length * 3L;
                                    org.bukkit.Bukkit.getScheduler().runTaskLater(plugin, () -> {
                                        // isWave=true: the falling block applies its own AoE damage on impact.
                                        playDripstoneStrike(tower, strikeLoc, waveDamage, null, true);
                                    }, delay);
                                }
                            }
                        }
                    }
                }
            }
            case THUNDER -> {
                boolean global = plugin.getTowerConfigManager().getStat(TowerType.THUNDER, tower.getLevel(), "is_global", false);
                if (global) {
                    // T4 Global Strike: hit every valid mob in the arena (range is 999 in config)
                    for (Mob mob : getMobsInRadius(tower.getCenterLocation(), range, towerArena)) {
                        mob.getWorld().strikeLightningEffect(mob.getLocation());
                        mob.damage(tower.getDamage());
                    }
                    return;
                }
                target.getWorld().strikeLightningEffect(target.getLocation());
                target.damage(tower.getDamage());

                // T2/T3 Chain Lightning: arc to nearby mobs for 50% damage
                int chains = plugin.getTowerConfigManager().getStat(TowerType.THUNDER, tower.getLevel(), "chain_count", 0);
                if (chains > 0) {
                    Location targetLoc = target.getLocation();
                    java.util.List<Mob> near = getMobsInRadius(targetLoc, 6.0, towerArena);
                    near.remove(target);
                    near.sort(java.util.Comparator.comparingDouble(m -> m.getLocation().distanceSquared(targetLoc)));
                    // Chain lightning now deals full damage (was 50%).
                    double chainDamage = tower.getDamage() * 1.0;
                    for (int i = 0; i < Math.min(chains, near.size()); i++) {
                        Mob chained = near.get(i);
                        drawParticleLine(target.getEyeLocation(), chained.getEyeLocation(), org.bukkit.Particle.ELECTRIC_SPARK);
                        chained.damage(chainDamage);
                    }
                }
            }
            case TURRET -> {
                if ("scatter".equals(tower.getPathId())) {
                    int arrows = plugin.getTowerConfigManager().getStat(TowerType.TURRET, tower.getPathId(), tower.getLevel(), "arrows", 5);
                    double damage = tower.getDamage();

                    org.bukkit.util.Vector dir = target.getEyeLocation().toVector().subtract(start.toVector()).normalize();

                    // Push the spawn point 1.5 blocks forward so arrows clear the tower's own collision box.
                    Location safeStart = start.clone().add(dir.clone().multiply(1.5));

                    for (int i = 0; i < arrows; i++) {
                        // Offset spawn locations slightly so the arrows don't instantly collide with each other.
                        Location spawnLoc = safeStart.clone().add((Math.random() - 0.5) * 0.5, (Math.random() - 0.5) * 0.5, (Math.random() - 0.5) * 0.5);
                        org.bukkit.entity.Arrow arrow = safeStart.getWorld().spawn(spawnLoc, org.bukkit.entity.Arrow.class);
                        arrow.setDamage(damage);
                        arrow.setShooter(null);

                        // Apply a strong randomized spread to the velocity.
                        double spread = 0.35;
                        org.bukkit.util.Vector spreadDir = dir.clone().add(new org.bukkit.util.Vector(
                                (Math.random() - 0.5) * spread,
                                (Math.random() - 0.5) * spread,
                                (Math.random() - 0.5) * spread
                        )).normalize();

                        arrow.setVelocity(spreadDir.multiply(2.5));
                    }
                    start.getWorld().playSound(start, Sound.ENTITY_ARROW_SHOOT, 0.8f, 0.8f);
                } else { // gatling
                    if (target.getType() == org.bukkit.entity.EntityType.BREEZE) {
                        start.getWorld().playSound(target.getLocation(), Sound.ITEM_SHIELD_BLOCK, 1f, 1f);
                        return;
                    }
                    target.damage(tower.getDamage());
                    drawParticleLine(start, target.getEyeLocation(), org.bukkit.Particle.CRIT);
                    // Fires up to 20x/s keep the click quiet
                    start.getWorld().playSound(start, Sound.BLOCK_LEVER_CLICK, 0.25f, 1.8f);
                }
            }
            case BOMBARDIER -> {
                if ("landmines".equals(tower.getPathId())) return; // handled in the ticker
                double radius = plugin.getTowerConfigManager().getStat(TowerType.BOMBARDIER, tower.getPathId(), tower.getLevel(), "radius", 3.5);
                double damage = tower.getDamage();
                Location targetLoc = target.getLocation().clone();

                // Spawn higher up so the bomb clears the tower's own structure instead of sticking inside it.
                Location bombStart = start.clone().add(0, 1.5, 0);

                org.bukkit.entity.TNTPrimed tnt = bombStart.getWorld().spawn(bombStart, org.bukkit.entity.TNTPrimed.class, t -> {
                    t.setFuseTicks(25);
                    t.setYield(0.0f);
                    t.setIsIncendiary(false);
                    t.setMetadata("td_bombardier", new org.bukkit.metadata.FixedMetadataValue(plugin, true));
                });
                org.bukkit.util.Vector dir = targetLoc.toVector().subtract(bombStart.toVector());
                double dist = Math.max(1.0, dir.length());
                // Boost the Y velocity so it arcs overhead and away from the tower.
                tnt.setVelocity(dir.normalize().multiply(0.07 * dist).setY(0.6));
                bombStart.getWorld().playSound(bombStart, Sound.ENTITY_TNT_PRIMED, 1.0f, 1.0f);

                // Detonate ourselves at fuse end (yield 0 means vanilla does no damage or block changes)
                org.bukkit.Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    Location blast = tnt.isValid() ? tnt.getLocation() : targetLoc;
                    if (tnt.isValid()) tnt.remove();
                    blast.getWorld().spawnParticle(org.bukkit.Particle.EXPLOSION_EMITTER, blast, 1);
                    blast.getWorld().playSound(blast, Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 0.9f);
                    for (Mob mob : getMobsInRadius(blast, radius, towerArena)) {
                        mob.damage(damage);
                    }
                }, 25L);
            }
            case BEEHIVE -> {
                // Bees are managed in the ticker
            }
        }
    }

    // A 3D pointed-dripstone falls from above (rendered with a BlockDisplay so it's a true block in
    // the world without disrupting pathfinding) and deals damage exactly on impact. The T3 cave-in
    // wave ({@code isWave == true}) applies AoE damage within 1.5 blocks of the landing point.
    private void playDripstoneStrike(Tower tower, Location targetLoc, double damage, Mob target, boolean isWave) {
        Location startLoc = targetLoc.clone().add(0, 5.0, 0);
        if (startLoc.getWorld() == null) return;

        // Spawn a BlockDisplay for true 3D visuals: a Pointed Dripstone pointing downwards.
        org.bukkit.entity.BlockDisplay fallingSpike = startLoc.getWorld().spawn(startLoc, org.bukkit.entity.BlockDisplay.class, bd -> {
            org.bukkit.block.data.type.PointedDripstone data =
                    (org.bukkit.block.data.type.PointedDripstone) Material.POINTED_DRIPSTONE.createBlockData();
            data.setVerticalDirection(org.bukkit.block.BlockFace.DOWN); // Point downwards
            bd.setBlock(data);
            bd.setPersistent(false);
            // Center the 1x1 block on the location.
            bd.setTransformation(new org.bukkit.util.Transformation(
                    new org.joml.Vector3f(-0.5f, 0f, -0.5f),
                    new org.joml.Quaternionf(),
                    new org.joml.Vector3f(1f, 1f, 1f),
                    new org.joml.Quaternionf()
            ));
        });

        startLoc.getWorld().playSound(startLoc, Sound.BLOCK_POINTED_DRIPSTONE_FALL, 1.0f, 1.0f);
        String arena = plugin.getPlotConfigManager().getPlotArena(tower.getPlotId());

        new BukkitRunnable() {
            int ticks = 0;
            @Override
            public void run() {
                if (ticks > 25 || !fallingSpike.isValid()) {
                    fallingSpike.remove();
                    this.cancel();
                    return;
                }

                Location current = fallingSpike.getLocation();
                current.subtract(0, 0.4, 0);
                fallingSpike.teleport(current);

                if (current.getY() <= targetLoc.getY() + 0.5) {
                    current.getWorld().spawnParticle(org.bukkit.Particle.BLOCK, current, 15, 0.2, 0.1, 0.2, Material.POINTED_DRIPSTONE.createBlockData());
                    current.getWorld().playSound(current, Sound.BLOCK_POINTED_DRIPSTONE_LAND, 1.0f, 0.9f);

                    // Damage lands exactly when the block hits the ground.
                    if (isWave) {
                        for (Mob m : getMobsInRadius(current, 1.5, arena)) {
                            m.damage(damage);
                        }
                    } else if (target != null && target.isValid() && !target.isDead()) {
                        target.damage(damage);
                    }

                    fallingSpike.remove();
                    this.cancel();
                }
                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
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

    /**
     * Walks the tower's arena waypoint graph from waypoint "0" (following splits) and returns
     * the track locations within the tower's range, in path order.
     */
    private java.util.List<Location> getTrackLocationsWithinRange(Tower tower) {
        java.util.List<Location> result = new java.util.ArrayList<>();
        String arena = plugin.getPlotConfigManager().getPlotArena(tower.getPlotId());
        java.util.Map<String, com.pauljang.towerDefense.data.TDWaypoint> graph =
                plugin.getWaypointConfigManager().getWaypointGraph(arena);
        if (graph == null || graph.isEmpty()) return result;

        double rangeSq = tower.getRange() * tower.getRange();
        java.util.Set<String> visited = new java.util.HashSet<>();
        java.util.ArrayDeque<String> queue = new java.util.ArrayDeque<>();
        queue.add("0");
        while (!queue.isEmpty()) {
            String id = queue.poll();
            if (!visited.add(id)) continue;
            com.pauljang.towerDefense.data.TDWaypoint wp = graph.get(id);
            if (wp == null) continue;
            Location loc = wp.getLocation();
            if (loc != null && loc.getWorld() != null
                    && loc.getWorld().equals(tower.getCenterLocation().getWorld())
                    && loc.distanceSquared(tower.getCenterLocation()) <= rangeSq) {
                result.add(loc.clone());
            }
            queue.addAll(wp.getNextIds());
        }
        return result;
    }

    /**
     * Returns true if the mob's {@code td_immunities} persistent-data string lists the given tower
     * keyword (case-insensitive), e.g. "POISON", "CHORUS", "ICE", "SLOW".
     */
    private boolean isMobImmuneToTower(Mob mob, String towerKeyword) {
        String immunities = mob.getPersistentDataContainer().get(
                new org.bukkit.NamespacedKey(plugin, "td_immunities"),
                org.bukkit.persistence.PersistentDataType.STRING);
        if (immunities == null || immunities.isEmpty()) return false;
        return immunities.toUpperCase().contains(towerKeyword.toUpperCase());
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
                ChatColor.GREEN + "Tower Stats (" + tower.getType().getDisplayName() + tower.getDisplayPathSuffix() + ")",
                ChatColor.GRAY + "Level: " + ChatColor.YELLOW + tower.getLevel(),
                ChatColor.GRAY + "Range: " + ChatColor.YELLOW + tower.getRange(),
                ChatColor.GRAY + "Damage: " + ChatColor.YELLOW + String.format("%.1f", tower.getDamage()),
                ChatColor.GRAY + "Attack Speed: " + ChatColor.YELLOW + String.format("%.1fs", tower.getCooldown() / 20.0),
                ChatColor.GRAY + "Targeting: " + ChatColor.YELLOW + tower.getTargetingMode().getDisplayName()
            ));
        }

        // Slot 15: Return Ghast button (Happy Ghast towers only)
        if (tower.getType() == TowerType.HAPPY_GHAST) {
            boolean ghastValid = tower.getSpawnedGhast() != null && tower.getSpawnedGhast().isValid();
            String ghastStatus = ghastValid ? ChatColor.GREEN + "Ghast is active" : ChatColor.RED + "Ghast not found";
            gui.setItem(15, createGUIItem(
                Material.FEATHER,
                ChatColor.AQUA + "Return Ghast to Tower",
                ghastStatus,
                ChatColor.GRAY + "Click to teleport the Ghast back to its post",
                ChatColor.GRAY + "and eject any passengers."
            ));
        }

        // Slot 22: Upgrade Tower
        // Branching towers at base Level 1: upgrading opens the path picker (Gatling/Scatter, etc.).
        // getUpgradeCost() would report -1 here (the default path tops out at Level 1), so this case
        // must be handled before the normal upgrade/maxed logic.
        int nextLvl = tower.getLevel() + 1;
        int cost = tower.getUpgradeCost();
        if (isBranchingType(tower.getType()) && tower.getLevel() == 1 && !tower.hasPath()) {
            gui.setItem(22, createGUIItem(
                Material.ANVIL,
                ChatColor.GOLD + "Choose Upgrade Path",
                ChatColor.GRAY + "This tower branches into two paths at Level 2.",
                "",
                ChatColor.GREEN + "Click to pick a path and upgrade!"
            ));
        } else if (cost != -1) {
            double nextRange = plugin.getTowerConfigManager().getRange(tower.getType(), tower.getPathId(), nextLvl, tower.getRange() + 1.5);
            double nextDamage = plugin.getTowerConfigManager().getDamage(tower.getType(), tower.getPathId(), nextLvl, tower.getDamage() + 1.5);
            long nextCooldown = plugin.getTowerConfigManager().getCooldown(tower.getType(), tower.getPathId(), nextLvl, Math.max(8L, tower.getCooldown() - 2L));
            
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
        int archerCost = plugin.getTowerConfigManager().getCost(TowerType.ARCHER, 1, 100);
        double archerRange = plugin.getTowerConfigManager().getRange(TowerType.ARCHER, 1, 15.0);
        double archerDamage = plugin.getTowerConfigManager().getDamage(TowerType.ARCHER, 1, 1.5);
        double archerSpeed = plugin.getTowerConfigManager().getCooldown(TowerType.ARCHER, 1, 20L) / 20.0;
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
        int fireCost = plugin.getTowerConfigManager().getCost(TowerType.FIRE, 1, 175);
        double fireRange = plugin.getTowerConfigManager().getRange(TowerType.FIRE, 1, 8.0);
        double fireDamage = plugin.getTowerConfigManager().getDamage(TowerType.FIRE, 1, 1.0);
        double fireSpeed = plugin.getTowerConfigManager().getCooldown(TowerType.FIRE, 1, 30L) / 20.0;
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
        int prismarineCost = plugin.getTowerConfigManager().getCost(TowerType.PRISMARINE, 1, 125);
        double prismarineRange = plugin.getTowerConfigManager().getRange(TowerType.PRISMARINE, 1, 8.0);
        double prismarineDamage = plugin.getTowerConfigManager().getDamage(TowerType.PRISMARINE, 1, 0.5);
        double prismarineSpeed = plugin.getTowerConfigManager().getCooldown(TowerType.PRISMARINE, 1, 30L) / 20.0;
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
        int chorusCost = plugin.getTowerConfigManager().getCost(TowerType.CHORUS, 1, 150);
        double chorusRange = plugin.getTowerConfigManager().getRange(TowerType.CHORUS, 1, 8.0);
        double chorusDamage = plugin.getTowerConfigManager().getDamage(TowerType.CHORUS, 1, 0.0);
        double chorusSpeed = plugin.getTowerConfigManager().getCooldown(TowerType.CHORUS, 1, 120L) / 20.0;
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
        int redstoneCost = plugin.getTowerConfigManager().getCost(TowerType.REDSTONE, 1, 200);
        double redstoneRange = plugin.getTowerConfigManager().getRange(TowerType.REDSTONE, 1, 10.0);
        gui.setItem(14, createGUIItem(
            Material.REDSTONE_BLOCK,
            ChatColor.DARK_RED + "Redstone Tower",
            ChatColor.GRAY + "Base Cost: " + ChatColor.YELLOW + redstoneCost + " Gold",
            ChatColor.GRAY + "Boost Radius: " + ChatColor.YELLOW + redstoneRange + " blocks",
            "",
            ChatColor.GRAY + "Passive: Boosts attack speed of nearby towers."
        ));

        // Slot 15: Poison Tower
        int poisonCost = plugin.getTowerConfigManager().getCost(TowerType.POISON, 1, 150);
        double poisonRange = plugin.getTowerConfigManager().getRange(TowerType.POISON, 1, 8.0);
        double poisonDamage = plugin.getTowerConfigManager().getDamage(TowerType.POISON, 1, 0.5);
        double poisonSpeed = plugin.getTowerConfigManager().getCooldown(TowerType.POISON, 1, 30L) / 20.0;
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
        int iceCost = plugin.getTowerConfigManager().getCost(TowerType.ICE, 1, 125);
        double iceRange = plugin.getTowerConfigManager().getRange(TowerType.ICE, 1, 8.0);
        double iceDamage = plugin.getTowerConfigManager().getDamage(TowerType.ICE, 1, 0.5);
        double iceSpeed = plugin.getTowerConfigManager().getCooldown(TowerType.ICE, 1, 30L) / 20.0;
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
        int golemCost = plugin.getTowerConfigManager().getCost(TowerType.GOLEM, 1, 400);
        double golemRange = plugin.getTowerConfigManager().getRange(TowerType.GOLEM, 1, 10.0);
        double golemDamage = plugin.getTowerConfigManager().getDamage(TowerType.GOLEM, 1, 20.0);
        double golemSpeed = plugin.getTowerConfigManager().getCooldown(TowerType.GOLEM, 1, 40L) / 20.0;
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
        int happyCost = plugin.getTowerConfigManager().getCost(TowerType.HAPPY_GHAST, 1, 500);
        double happyRange = plugin.getTowerConfigManager().getRange(TowerType.HAPPY_GHAST, 1, 15.0);
        double happyDamage = plugin.getTowerConfigManager().getDamage(TowerType.HAPPY_GHAST, 1, 15.0);
        double happySpeed = plugin.getTowerConfigManager().getCooldown(TowerType.HAPPY_GHAST, 1, 50L) / 20.0;
        gui.setItem(20, createGUIItem(
            Material.GHAST_SPAWN_EGG,
            ChatColor.LIGHT_PURPLE + "" + ChatColor.BOLD + "Happy Ghast Tower",
            ChatColor.GRAY + "Base Cost: " + ChatColor.YELLOW + happyCost + " Gold",
            ChatColor.GRAY + "Range: " + ChatColor.YELLOW + happyRange + " blocks",
            ChatColor.GRAY + "Damage: " + ChatColor.YELLOW + happyDamage + " HP (AoE)",
            ChatColor.GRAY + "Attack Speed: " + ChatColor.YELLOW + happySpeed + "s",
            "",
            ChatColor.GRAY + "Rideable ghast. Shoots fireballs that deal AoE damage.",
            ChatColor.GRAY + "Has 3 tiers. Autopilot mode when unridden."
        ));

        // --- Dripstone Tower ---
        int dripCost = plugin.getTowerConfigManager().getCost(TowerType.DRIPSTONE, 1, 200);
        double dripRange = plugin.getTowerConfigManager().getRange(TowerType.DRIPSTONE, 1, 10.0);
        double dripDamage = plugin.getTowerConfigManager().getDamage(TowerType.DRIPSTONE, 1, 6.0);
        double dripSpeed = plugin.getTowerConfigManager().getCooldown(TowerType.DRIPSTONE, 1, 60L) / 20.0;
        gui.setItem(21, createGUIItem(
            Material.POINTED_DRIPSTONE,
            ChatColor.GOLD + "" + ChatColor.BOLD + "Dripstone Tower",
            ChatColor.GRAY + "Base Cost: " + ChatColor.YELLOW + dripCost + " Gold",
            ChatColor.GRAY + "Range: " + ChatColor.YELLOW + dripRange + " blocks",
            ChatColor.GRAY + "Damage: " + ChatColor.YELLOW + dripDamage + " HP",
            ChatColor.GRAY + "Attack Speed: " + ChatColor.YELLOW + dripSpeed + "s",
            "",
            ChatColor.GRAY + "Heavy single-target spike strikes. Has 4 tiers.",
            ChatColor.GRAY + "T2+: hazard tiles (+15% dmg taken). T3+: cave-in sweep."
        ));

        // --- Thunder Tower ---
        int thunderCost = plugin.getTowerConfigManager().getCost(TowerType.THUNDER, 1, 200);
        double thunderRange = plugin.getTowerConfigManager().getRange(TowerType.THUNDER, 1, 12.0);
        double thunderDamage = plugin.getTowerConfigManager().getDamage(TowerType.THUNDER, 1, 5.0);
        double thunderSpeed = plugin.getTowerConfigManager().getCooldown(TowerType.THUNDER, 1, 80L) / 20.0;
        gui.setItem(22, createGUIItem(
            Material.LIGHTNING_ROD,
            ChatColor.YELLOW + "" + ChatColor.BOLD + "Thunder Tower",
            ChatColor.GRAY + "Base Cost: " + ChatColor.YELLOW + thunderCost + " Gold",
            ChatColor.GRAY + "Range: " + ChatColor.YELLOW + thunderRange + " blocks",
            ChatColor.GRAY + "Damage: " + ChatColor.YELLOW + thunderDamage + " HP",
            ChatColor.GRAY + "Attack Speed: " + ChatColor.YELLOW + thunderSpeed + "s",
            "",
            ChatColor.GRAY + "Lightning strikes. Has 4 tiers.",
            ChatColor.GRAY + "T2+: chain lightning. T4: global strike."
        ));

        // --- Turret (Base Level 1; branches at Level 2) ---
        int turretCost = plugin.getTowerConfigManager().getCost(TowerType.TURRET, 1, 200);
        double turretRange = plugin.getTowerConfigManager().getRange(TowerType.TURRET, 1, 12.0);
        double turretDamage = plugin.getTowerConfigManager().getDamage(TowerType.TURRET, 1, 2.0);
        double turretSpeed = plugin.getTowerConfigManager().getCooldown(TowerType.TURRET, 1, 10L) / 20.0;
        gui.setItem(23, createGUIItem(
            Material.OBSERVER,
            ChatColor.WHITE + "" + ChatColor.BOLD + "Turret",
            ChatColor.GRAY + "Base Cost: " + ChatColor.YELLOW + turretCost + " Gold",
            ChatColor.GRAY + "Range: " + ChatColor.YELLOW + turretRange + " blocks",
            ChatColor.GRAY + "Damage: " + ChatColor.YELLOW + turretDamage + " HP",
            ChatColor.GRAY + "Attack Speed: " + ChatColor.YELLOW + turretSpeed + "s",
            "",
            ChatColor.GRAY + "Shoots rapid single arrows.",
            ChatColor.GREEN + "Upgrades into Gatling or Scatter paths at Level 2."
        ));

        // --- Bombardier (Base Level 1; branches at Level 2) ---
        int bombCost = plugin.getTowerConfigManager().getCost(TowerType.BOMBARDIER, 1, 150);
        double bombRange = plugin.getTowerConfigManager().getRange(TowerType.BOMBARDIER, 1, 10.0);
        double bombDamage = plugin.getTowerConfigManager().getDamage(TowerType.BOMBARDIER, 1, 4.0);
        double bombSpeed = plugin.getTowerConfigManager().getCooldown(TowerType.BOMBARDIER, 1, 60L) / 20.0;
        gui.setItem(24, createGUIItem(
            Material.TNT,
            ChatColor.DARK_RED + "" + ChatColor.BOLD + "Bombardier",
            ChatColor.GRAY + "Base Cost: " + ChatColor.YELLOW + bombCost + " Gold",
            ChatColor.GRAY + "Range: " + ChatColor.YELLOW + bombRange + " blocks",
            ChatColor.GRAY + "Damage: " + ChatColor.YELLOW + bombDamage + " HP",
            ChatColor.GRAY + "Attack Speed: " + ChatColor.YELLOW + bombSpeed + "s",
            "",
            ChatColor.GRAY + "Throws explosive bombs.",
            ChatColor.GREEN + "Upgrades into Bigger Bombs or Landmines at Level 2."
        ));

        // --- Beehive (Base Level 1; branches at Level 2) ---
        int beeCost = plugin.getTowerConfigManager().getCost(TowerType.BEEHIVE, 1, 100);
        double beeRange = plugin.getTowerConfigManager().getRange(TowerType.BEEHIVE, 1, 5.0);
        double beeDamage = plugin.getTowerConfigManager().getDamage(TowerType.BEEHIVE, 1, 2.0);
        double beeSpeed = plugin.getTowerConfigManager().getCooldown(TowerType.BEEHIVE, 1, 50L) / 20.0;
        gui.setItem(25, createGUIItem(
            Material.BEE_NEST,
            ChatColor.GOLD + "" + ChatColor.BOLD + "Beehive",
            ChatColor.GRAY + "Base Cost: " + ChatColor.YELLOW + beeCost + " Gold",
            ChatColor.GRAY + "Range: " + ChatColor.YELLOW + beeRange + " blocks",
            ChatColor.GRAY + "Damage: " + ChatColor.YELLOW + beeDamage + " HP",
            ChatColor.GRAY + "Attack Speed: " + ChatColor.YELLOW + beeSpeed + "s",
            "",
            ChatColor.GRAY + "Spawns a basic bee to attack mobs.",
            ChatColor.GREEN + "Upgrades into Goliath or Swarm paths at Level 2."
        ));

        player.openInventory(gui);
        player.playSound(player.getLocation(), Sound.BLOCK_CHEST_OPEN, 0.8f, 1.0f);
    }

    public TowerType getPendingPathChoice(java.util.UUID playerId) {
        return pendingPathChoice.get(playerId);
    }

    public void clearPendingPathChoice(java.util.UUID playerId) {
        pendingPathChoice.remove(playerId);
    }

    /**
     * Returns the path names of a branching tower in towers.yaml order,
     * matching the slot order used by the path picker GUI (slots 11 and 15).
     */
    public java.util.List<String> getPathNamesInOrder(TowerType type) {
        TowerConfigManager.TowerDefinition def = plugin.getTowerConfigManager().getDefinition(type);
        if (def == null) return java.util.Collections.emptyList();
        return new java.util.ArrayList<>(def.getPathNames());
    }

    /** Branching towers carry a shared base Level 1 and split into named paths at Level 2. */
    public static boolean isBranchingType(TowerType type) {
        return type == TowerType.TURRET || type == TowerType.BOMBARDIER || type == TowerType.BEEHIVE;
    }

    public void openPathPickerGUI(Player player, String plotId, TowerType type) {
        java.util.List<String> paths = getPathNamesInOrder(type);
        if (paths.isEmpty()) return;

        // The picker is the Level 1 -> Level 2 upgrade step: show each path's Level 2 stats/cost.
        Tower existing = getTower(plotId);
        int targetLevel = existing != null ? existing.getLevel() + 1 : 2;

        pendingPathChoice.put(player.getUniqueId(), type);
        Inventory gui = org.bukkit.Bukkit.createInventory(null, 27, ChatColor.DARK_BLUE + "Choose Path: " + plotId);

        ItemStack filler = createGUIItem(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 27; i++) {
            gui.setItem(i, filler);
        }

        int[] slots = {11, 15};
        for (int i = 0; i < paths.size() && i < slots.length; i++) {
            String path = paths.get(i);
            int cost = plugin.getTowerConfigManager().getCost(type, path, targetLevel, type.getCost());
            double pRange = plugin.getTowerConfigManager().getRange(type, path, targetLevel, type.getRange());
            double pDamage = plugin.getTowerConfigManager().getDamage(type, path, targetLevel, type.getDamage());
            double pSpeed = plugin.getTowerConfigManager().getCooldown(type, path, targetLevel, type.getCooldown()) / 20.0;
            int maxLevel = plugin.getTowerConfigManager().getMaxLevel(type, path);

            java.util.List<String> lore = new java.util.ArrayList<>();
            lore.add(ChatColor.GRAY + "Upgrade Cost: " + ChatColor.YELLOW + cost + " Gold");
            lore.add(ChatColor.GRAY + "Range: " + ChatColor.YELLOW + pRange + " blocks");
            lore.add(ChatColor.GRAY + "Damage: " + ChatColor.YELLOW + String.format("%.1f", pDamage) + " HP");
            lore.add(ChatColor.GRAY + "Attack Speed: " + ChatColor.YELLOW + String.format("%.2fs", pSpeed));
            lore.add(ChatColor.GRAY + "Max Level: " + ChatColor.YELLOW + maxLevel);
            appendPathSpecialStat(lore, type, path, targetLevel);
            lore.add("");
            lore.add(ChatColor.GREEN + "Click to upgrade and lock in this path!");

            String displayPath = formatPathName(path);
            gui.setItem(slots[i], createGUIItem(
                pathIcon(type, path),
                type.getColor() + "" + ChatColor.BOLD + type.getDisplayName() + ": " + displayPath,
                lore.toArray(new String[0])
            ));
        }

        player.openInventory(gui);
        player.playSound(player.getLocation(), Sound.BLOCK_CHEST_OPEN, 0.8f, 1.2f);
    }

    private String formatPathName(String path) {
        String[] words = path.split("_");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < words.length; i++) {
            if (i > 0) sb.append(' ');
            sb.append(Character.toUpperCase(words[i].charAt(0))).append(words[i].substring(1));
        }
        return sb.toString();
    }

    private Material pathIcon(TowerType type, String path) {
        return switch (path) {
            case "gatling" -> Material.CROSSBOW;
            case "scatter" -> Material.ARROW;
            case "bigger_bombs" -> Material.TNT;
            case "landmines" -> Material.STONE_PRESSURE_PLATE;
            case "goliath" -> Material.HONEY_BLOCK;
            case "swarm" -> Material.BEE_SPAWN_EGG;
            default -> type.getBlockMaterial();
        };
    }

    private void appendPathSpecialStat(java.util.List<String> lore, TowerType type, String path, int level) {
        switch (path) {
            case "scatter" -> lore.add(ChatColor.GRAY + "Shots per Attack: " + ChatColor.YELLOW
                    + plugin.getTowerConfigManager().getStat(type, path, level, "arrows", 5));
            case "bigger_bombs" -> lore.add(ChatColor.GRAY + "Blast Radius: " + ChatColor.YELLOW
                    + plugin.getTowerConfigManager().getStat(type, path, level, "radius", 3.5) + " blocks");
            case "landmines" -> lore.add(ChatColor.GRAY + "Max Active Mines: " + ChatColor.YELLOW + 3);
            case "goliath" -> lore.add(ChatColor.GRAY + "Bee Scale: " + ChatColor.YELLOW
                    + plugin.getTowerConfigManager().getStat(type, path, level, "scale", 2.0) + "x");
            case "swarm" -> lore.add(ChatColor.GRAY + "Max Bees: " + ChatColor.YELLOW
                    + plugin.getTowerConfigManager().getStat(type, path, level, "bee_count", 1));
        }
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
