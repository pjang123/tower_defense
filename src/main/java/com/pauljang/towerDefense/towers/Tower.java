package com.pauljang.towerDefense.towers;

import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;
import org.bukkit.util.BlockVector;
import com.pauljang.towerDefense.TowerDefense;

public class Tower {
    private final String plotId;
    private final Location centerLocation;
    private final TowerType type;
    private int level = 1;
    private long lastAttackTick = 0;
    private final java.util.List<ArmorStand> holograms = new java.util.ArrayList<>();
    private BlockVector structureSize = null;
    private TargetingMode targetingMode = TargetingMode.FIRST;
    private long disabledUntil = 0;
    private org.bukkit.entity.LivingEntity spawnedGolem = null;
    private org.bukkit.entity.HappyGhast spawnedGhast = null;
    private boolean autopilot = true;
    private java.util.UUID ownerId = null;
    private boolean empDisplayed = false;

    public Tower(String plotId, Location centerLocation, TowerType type) {
        this.plotId = plotId;
        this.centerLocation = centerLocation;
        this.type = type;
    }

    public long getDisabledUntil() { return disabledUntil; }
    public void setDisabledUntil(long disabledUntil) { this.disabledUntil = disabledUntil; }
    public boolean isDisabled() { return System.currentTimeMillis() < disabledUntil; }

    public String getPlotId() { return plotId; }
    public Location getCenterLocation() { return centerLocation; }
    public TowerType getType() { return type; }
    
    public int getLevel() { return level; }
    public void incrementLevel() { this.level++; }
    
    public long getLastAttackTick() { return lastAttackTick; }
    public void setLastAttackTick(long lastAttackTick) { this.lastAttackTick = lastAttackTick; }
    
    public java.util.List<ArmorStand> getHolograms() { return holograms; }

    public String getTierName() {
        return type.name().toLowerCase() + "_" + level;
    }

    public String getRomanLevel() {
        return switch (level) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            case 5 -> "V";
            default -> String.valueOf(level);
        };
    }

    public BlockVector getStructureSize() { return structureSize; }
    public void setStructureSize(BlockVector structureSize) { this.structureSize = structureSize; }

    public TargetingMode getTargetingMode() { return targetingMode; }
    public void setTargetingMode(TargetingMode targetingMode) { this.targetingMode = targetingMode; }

    public double getRange() {
        TowerDefense plugin = org.bukkit.plugin.java.JavaPlugin.getPlugin(TowerDefense.class);
        String nameKey = type.name().toLowerCase() + "_" + level;
        return plugin.getConfig().getDouble("towers." + nameKey + ".range", type.getRange() + (level - 1) * 1.5);
    }

    public double getDamage() {
        TowerDefense plugin = org.bukkit.plugin.java.JavaPlugin.getPlugin(TowerDefense.class);
        String nameKey = type.name().toLowerCase() + "_" + level;
        return plugin.getConfig().getDouble("towers." + nameKey + ".damage", type.getDamage() + (level - 1) * 1.5);
    }

    public long getCooldown() {
        TowerDefense plugin = org.bukkit.plugin.java.JavaPlugin.getPlugin(TowerDefense.class);
        String nameKey = type.name().toLowerCase() + "_" + level;
        return plugin.getConfig().getLong("towers." + nameKey + ".cooldown", Math.max(8L, type.getCooldown() - (level - 1) * 2));
    }

    public int getUpgradeCost() {
        int maxLvl = (type == TowerType.GOLEM) ? 2 : 3;
        if (level >= maxLvl) {
            return -1; // Max level reached
        }
        TowerDefense plugin = org.bukkit.plugin.java.JavaPlugin.getPlugin(TowerDefense.class);
        String nextKey = type.name().toLowerCase() + "_" + (level + 1);
        return plugin.getConfig().getInt("towers." + nextKey + ".cost", (level + 1) * 150);
    }

    public int getTotalValue() {
        TowerDefense plugin = org.bukkit.plugin.java.JavaPlugin.getPlugin(TowerDefense.class);
        int total = 0;
        for (int l = 1; l <= level; l++) {
            String key = type.name().toLowerCase() + "_" + l;
            total += plugin.getConfig().getInt("towers." + key + ".cost", l == 1 ? type.getCost() : l * 100);
        }
        return total;
    }

    public org.bukkit.entity.LivingEntity getSpawnedGolem() { return spawnedGolem; }
    public void setSpawnedGolem(org.bukkit.entity.LivingEntity spawnedGolem) { this.spawnedGolem = spawnedGolem; }

    public org.bukkit.entity.HappyGhast getSpawnedGhast() { return spawnedGhast; }
    public void setSpawnedGhast(org.bukkit.entity.HappyGhast spawnedGhast) { this.spawnedGhast = spawnedGhast; }

    public boolean isAutopilot() { return autopilot; }
    public void setAutopilot(boolean autopilot) { this.autopilot = autopilot; }

    public java.util.UUID getOwnerId() { return ownerId; }
    public void setOwnerId(java.util.UUID ownerId) { this.ownerId = ownerId; }

    public boolean isEmpDisplayed() { return empDisplayed; }
    public void setEmpDisplayed(boolean empDisplayed) { this.empDisplayed = empDisplayed; }
}
