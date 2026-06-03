package com.pauljang.towerDefense.towers;

import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;
import org.bukkit.util.BlockVector;

public class Tower {
    private final String plotId;
    private final Location centerLocation;
    private final TowerType type;
    private int level = 1;
    private long lastAttackTick = 0;
    private ArmorStand hologram = null;
    private BlockVector structureSize = null;
    private TargetingMode targetingMode = TargetingMode.FIRST;

    public Tower(String plotId, Location centerLocation, TowerType type) {
        this.plotId = plotId;
        this.centerLocation = centerLocation;
        this.type = type;
    }

    public String getPlotId() { return plotId; }
    public Location getCenterLocation() { return centerLocation; }
    public TowerType getType() { return type; }
    
    public int getLevel() { return level; }
    public void incrementLevel() { this.level++; }
    
    public long getLastAttackTick() { return lastAttackTick; }
    public void setLastAttackTick(long lastAttackTick) { this.lastAttackTick = lastAttackTick; }
    
    public ArmorStand getHologram() { return hologram; }
    public void setHologram(ArmorStand hologram) { this.hologram = hologram; }

    public BlockVector getStructureSize() { return structureSize; }
    public void setStructureSize(BlockVector structureSize) { this.structureSize = structureSize; }

    public TargetingMode getTargetingMode() { return targetingMode; }
    public void setTargetingMode(TargetingMode targetingMode) { this.targetingMode = targetingMode; }

    public double getRange() {
        return type.getRange() + (level - 1) * 1.5;
    }

    public double getDamage() {
        double scale = type == TowerType.MAGE ? 2.5 : (type == TowerType.FROST ? 0.8 : 1.5);
        return type.getDamage() + (level - 1) * scale;
    }

    public long getCooldown() {
        long step = type == TowerType.MAGE ? 3L : 2L;
        return Math.max(8L, type.getCooldown() - (level - 1) * step);
    }

    public int getUpgradeCost() {
        switch (level) {
            case 1: return 150;
            case 2: return 300;
            case 3: return 500;
            case 4: return 800;
            default: return -1; // Max level reached
        }
    }

    public int getTotalValue() {
        int val = type.getCost(); // Base cost
        if (level > 1) val += 150;
        if (level > 2) val += 300;
        if (level > 3) val += 500;
        if (level > 4) val += 800;
        return val;
    }
}
