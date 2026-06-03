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
            as.setCustomName("");
            as.setCustomNameVisible(true);
        });

        tower.setHologram(hologram);
        updateHologram(tower);
        placedTowers.put(plotId, tower);
    }

    public void updateHologram(Tower tower) {
        ArmorStand hologram = tower.getHologram();
        if (hologram != null && hologram.isValid()) {
            hologram.setCustomName(
                tower.getType().getColor() + tower.getType().getDisplayName() + 
                " Lvl " + tower.getLevel() + " " + 
                ChatColor.GRAY + "[" + tower.getTargetingMode().getDisplayName() + "]"
            );
        }
    }

    public Tower getTower(String plotId) {
        return placedTowers.get(plotId);
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
                    if (tick - tower.getLastAttackTick() >= tower.getCooldown()) {
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
        double progress = tdMob.getCurrentWaypointIndex() * 10000.0;
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
        Location end = target.getEyeLocation();

        // 1. Damage target
        target.damage(tower.getDamage());

        // 2. Play particle and sound effects based on tower type & level
        org.bukkit.Particle particle = org.bukkit.Particle.CRIT;
        Sound sound = Sound.ENTITY_ARROW_SHOOT;

        switch (tower.getType()) {
            case ARCHER -> {
                particle = tower.getLevel() >= 4 ? org.bukkit.Particle.SOUL_FIRE_FLAME : org.bukkit.Particle.CRIT;
                sound = Sound.ENTITY_ARROW_SHOOT;
            }
            case MAGE -> {
                particle = org.bukkit.Particle.FLAME;
                sound = Sound.ENTITY_BLAZE_SHOOT;
            }
            case FROST -> {
                particle = org.bukkit.Particle.SNOWFLAKE;
                sound = Sound.BLOCK_GLASS_BREAK;
                int amp = tower.getLevel() >= 5 ? 2 : 1; // Slowness III for Level 5
                target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, amp));
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
        gui.setItem(13, createGUIItem(
            Material.BOOK,
            ChatColor.GREEN + "Tower Stats (" + tower.getType().getDisplayName() + ")",
            ChatColor.GRAY + "Level: " + ChatColor.YELLOW + tower.getLevel(),
            ChatColor.GRAY + "Range: " + ChatColor.YELLOW + tower.getRange(),
            ChatColor.GRAY + "Damage: " + ChatColor.YELLOW + String.format("%.1f", tower.getDamage()),
            ChatColor.GRAY + "Attack Speed: " + ChatColor.YELLOW + String.format("%.1fs", tower.getCooldown() / 20.0),
            ChatColor.GRAY + "Targeting: " + ChatColor.YELLOW + tower.getTargetingMode().getDisplayName()
        ));

        // Slot 22: Upgrade Tower
        int nextLvl = tower.getLevel() + 1;
        int cost = tower.getUpgradeCost();
        if (cost != -1) {
            double nextRange = tower.getRange() + 1.5;
            double nextDamage = tower.getDamage() + (tower.getType() == TowerType.MAGE ? 2.5 : (tower.getType() == TowerType.FROST ? 0.8 : 1.5));
            long nextCooldown = Math.max(8L, tower.getCooldown() - (tower.getType() == TowerType.MAGE ? 3L : 2L));
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
        } else {
            gui.setItem(22, createGUIItem(
                Material.BEDROCK,
                ChatColor.RED + "Tower Maxed Out",
                ChatColor.GRAY + "This tower is at the maximum level."
            ));
        }

        // Slot 31: Targeting Mode
        gui.setItem(31, createGUIItem(
            Material.COMPASS,
            ChatColor.AQUA + "Targeting Mode",
            ChatColor.GRAY + "Current Priority: " + ChatColor.YELLOW + tower.getTargetingMode().getDisplayName(),
            "",
            ChatColor.GRAY + "Click to cycle priorities:",
            ChatColor.GRAY + " -> FIRST -> LAST -> STRONG -> WEAK -> CLOSE"
        ));

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

        // Slot 11: Archer Tower
        gui.setItem(11, createGUIItem(
            Material.DISPENSER,
            ChatColor.GREEN + "Archer Tower",
            ChatColor.GRAY + "Base Cost: " + ChatColor.YELLOW + "100 Gold",
            ChatColor.GRAY + "Range: " + ChatColor.YELLOW + "15.0 blocks",
            ChatColor.GRAY + "Damage: " + ChatColor.YELLOW + "1.5 HP",
            ChatColor.GRAY + "Attack Speed: " + ChatColor.YELLOW + "1.0s",
            "",
            ChatColor.GRAY + "Shoots single targets fast."
        ));

        // Slot 13: Mage Tower
        gui.setItem(13, createGUIItem(
            Material.REDSTONE_LAMP,
            ChatColor.RED + "Mage Tower",
            ChatColor.GRAY + "Base Cost: " + ChatColor.YELLOW + "175 Gold",
            ChatColor.GRAY + "Range: " + ChatColor.YELLOW + "12.0 blocks",
            ChatColor.GRAY + "Damage: " + ChatColor.YELLOW + "3.0 HP",
            ChatColor.GRAY + "Attack Speed: " + ChatColor.YELLOW + "1.5s",
            "",
            ChatColor.GRAY + "Slow but deals heavy fire damage."
        ));

        // Slot 15: Frost Tower
        gui.setItem(15, createGUIItem(
            Material.PACKED_ICE,
            ChatColor.AQUA + "Frost Tower",
            ChatColor.GRAY + "Base Cost: " + ChatColor.YELLOW + "125 Gold",
            ChatColor.GRAY + "Range: " + ChatColor.YELLOW + "15.0 blocks",
            ChatColor.GRAY + "Damage: " + ChatColor.YELLOW + "0.5 HP",
            ChatColor.GRAY + "Attack Speed: " + ChatColor.YELLOW + "1.0s",
            "",
            ChatColor.GRAY + "Applies Slowness II (2s) on hit."
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

    public void cleanup() {
        for (Tower tower : placedTowers.values()) {
            if (tower.getHologram() != null && tower.getHologram().isValid()) {
                tower.getHologram().remove();
            }
        }
        placedTowers.clear();
    }
}
