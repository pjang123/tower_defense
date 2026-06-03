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
        TowerDefense plugin = org.bukkit.plugin.java.JavaPlugin.getPlugin(TowerDefense.class);
        String name = type.name().toLowerCase();
        java.util.List<String> names = plugin.getConfig().getStringList("towers." + name + ".tier-names");
        if (names != null && level >= 1 && level <= names.size()) {
            return names.get(level - 1);
        }
        return type.getDisplayName() + " T" + level;
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
        String name = type.name().toLowerCase();
        double baseRange = plugin.getConfig().getDouble("towers." + name + ".range", type.getRange());
        double rangeInc = plugin.getConfig().getDouble("towers." + name + ".range-increase", 1.5);
        return baseRange + (level - 1) * rangeInc;
    }

    public double getDamage() {
        TowerDefense plugin = org.bukkit.plugin.java.JavaPlugin.getPlugin(TowerDefense.class);
        String name = type.name().toLowerCase();
        double baseDamage = plugin.getConfig().getDouble("towers." + name + ".damage", type.getDamage());
        double scale = plugin.getConfig().getDouble("towers." + name + ".damage-increase", type == TowerType.MAGE ? 2.5 : (type == TowerType.FROST ? 0.8 : 1.5));
        return baseDamage + (level - 1) * scale;
    }

    public long getCooldown() {
        TowerDefense plugin = org.bukkit.plugin.java.JavaPlugin.getPlugin(TowerDefense.class);
        String name = type.name().toLowerCase();
        long baseCooldown = plugin.getConfig().getLong("towers." + name + ".cooldown", type.getCooldown());
        long step = plugin.getConfig().getLong("towers." + name + ".cooldown-decrease", type == TowerType.MAGE ? 3L : 2L);
        return Math.max(8L, baseCooldown - (level - 1) * step);
    }

    public int getUpgradeCost() {
        TowerDefense plugin = org.bukkit.plugin.java.JavaPlugin.getPlugin(TowerDefense.class);
        String name = type.name().toLowerCase();
        java.util.List<Integer> costs = plugin.getConfig().getIntegerList("towers." + name + ".upgrade-costs");
        if (costs == null || costs.isEmpty()) {
            costs = java.util.Arrays.asList(150, 300, 500, 800);
        }
        if (level >= 1 && level <= costs.size()) {
            return costs.get(level - 1);
        }
        return -1; // Max level reached
    }

    public int getTotalValue() {
        TowerDefense plugin = org.bukkit.plugin.java.JavaPlugin.getPlugin(TowerDefense.class);
        String name = type.name().toLowerCase();
        int baseCost = plugin.getConfig().getInt("towers." + name + ".cost", type.getCost());
        java.util.List<Integer> costs = plugin.getConfig().getIntegerList("towers." + name + ".upgrade-costs");
        if (costs == null || costs.isEmpty()) {
            costs = java.util.Arrays.asList(150, 300, 500, 800);
        }
        int val = baseCost;
        for (int i = 1; i < level; i++) {
            if (i <= costs.size()) {
                val += costs.get(i - 1);
            }
        }
        return val;
    }
}
