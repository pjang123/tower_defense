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
}
