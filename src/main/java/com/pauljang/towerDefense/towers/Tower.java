package com.pauljang.towerDefense.towers;

import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;
import org.bukkit.util.BlockVector;
import com.pauljang.towerDefense.TowerDefense;

public class Tower {
    private final String plotId;
    private final Location centerLocation;
    private final TowerType type;
    private String pathId = TowerConfigManager.DEFAULT_PATH;
    private int level = 1;
    private long lastAttackTick = 0;
    private final java.util.List<ArmorStand> holograms = new java.util.ArrayList<>();
    private BlockVector structureSize = null;
    private TargetingMode targetingMode = TargetingMode.FIRST;
    private long disabledUntil = 0;
    // True when this tower was permanently disabled by an Armageddon Wither (distinct display from EMP).
    private boolean armageddonDisabled = false;
    private org.bukkit.entity.LivingEntity spawnedGolem = null;
    private org.bukkit.entity.HappyGhast spawnedGhast = null;
    private boolean autopilot = true;
    private java.util.UUID ownerId = null;
    private boolean empDisplayed = false;
    private final java.util.List<org.bukkit.entity.Bee> spawnedBees = new java.util.ArrayList<>();
    private final java.util.List<ArmorStand> landmines = new java.util.ArrayList<>();
    // Gold Tower: the gold-bundle barrels (BlockDisplay + Interaction pairs) it has spawned on the track.
    private final java.util.List<GoldBarrel> goldBarrels = new java.util.ArrayList<>();
    // Transient combat modifiers from the Gold Tower's gambling path. Apply to getDamage()/getCooldown()
    // so every call site respects them; they lapse automatically once the wall-clock deadline passes.
    private long damageBuffUntil = 0;
    private double damageBuffMult = 1.0;
    private long cooldownPenaltyUntil = 0;
    private double cooldownPenaltyMult = 1.0;
    // Direct references to the spawned Dripstone hazard displays so the ticker never has to scan
    // the world (getNearbyEntities) to find them.
    private final java.util.List<org.bukkit.entity.BlockDisplay> hazardDisplays = new java.util.ArrayList<>();

    public Tower(String plotId, Location centerLocation, TowerType type) {
        this.plotId = plotId;
        this.centerLocation = centerLocation;
        this.type = type;
    }

    public long getDisabledUntil() { return disabledUntil; }
    public void setDisabledUntil(long disabledUntil) { this.disabledUntil = disabledUntil; }
    public boolean isDisabled() { return System.currentTimeMillis() < disabledUntil; }

    public boolean isArmageddonDisabled() { return armageddonDisabled; }
    public void setArmageddonDisabled(boolean armageddonDisabled) { this.armageddonDisabled = armageddonDisabled; }

    public String getPlotId() { return plotId; }
    public Location getCenterLocation() { return centerLocation; }
    public TowerType getType() { return type; }

    public String getPathId() { return pathId; }
    public void setPathId(String pathId) {
        this.pathId = (pathId == null) ? TowerConfigManager.DEFAULT_PATH : pathId;
    }
    public boolean hasPath() { return !TowerConfigManager.DEFAULT_PATH.equals(pathId); }

    public String getDisplayPathSuffix() {
        if (!hasPath()) return "";
        String[] words = pathId.split("_");
        StringBuilder sb = new StringBuilder(" (");
        for (int i = 0; i < words.length; i++) {
            if (i > 0) sb.append(' ');
            sb.append(Character.toUpperCase(words[i].charAt(0))).append(words[i].substring(1));
        }
        return sb.append(')').toString();
    }
    
    public int getLevel() { return level; }
    public void incrementLevel() { this.level++; }
    
    public long getLastAttackTick() { return lastAttackTick; }
    public void setLastAttackTick(long lastAttackTick) { this.lastAttackTick = lastAttackTick; }
    
    public java.util.List<ArmorStand> getHolograms() { return holograms; }

    public String getTierName() {
        return hasPath()
                ? type.name().toLowerCase() + "_" + pathId + "_" + level
                : type.name().toLowerCase() + "_" + level;
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

    private static TowerConfigManager towerConfig() {
        return org.bukkit.plugin.java.JavaPlugin.getPlugin(TowerDefense.class).getTowerConfigManager();
    }

    public double getRange() {
        return towerConfig().getRange(type, pathId, level, type.getRange() + (level - 1) * 1.5);
    }

    public double getDamage() {
        double base = towerConfig().getDamage(type, pathId, level, type.getDamage() + (level - 1) * 1.5);
        return base * getDamageMultiplierNow();
    }

    public long getCooldown() {
        long base = towerConfig().getCooldown(type, pathId, level, Math.max(8L, type.getCooldown() - (level - 1) * 2));
        return Math.max(1L, Math.round(base * getCooldownMultiplierNow()));
    }

    // --- Gold Tower gambling: transient tower-wide buff/penalty applied to all of a player's towers ---

    public java.util.List<GoldBarrel> getGoldBarrels() { return goldBarrels; }

    /** Grants this tower a temporary damage multiplier (>1 = buff) for the given duration in millis. */
    public void applyDamageBuff(double multiplier, long durationMillis) {
        this.damageBuffMult = multiplier;
        this.damageBuffUntil = System.currentTimeMillis() + durationMillis;
    }

    /** Saddles this tower with a temporary cooldown multiplier (>1 = slower) for the given millis. */
    public void applyCooldownPenalty(double multiplier, long durationMillis) {
        this.cooldownPenaltyMult = multiplier;
        this.cooldownPenaltyUntil = System.currentTimeMillis() + durationMillis;
    }

    public boolean hasDamageBuff() { return System.currentTimeMillis() < damageBuffUntil && damageBuffMult != 1.0; }
    public boolean hasCooldownPenalty() { return System.currentTimeMillis() < cooldownPenaltyUntil && cooldownPenaltyMult != 1.0; }

    public double getDamageMultiplierNow() {
        return System.currentTimeMillis() < damageBuffUntil ? damageBuffMult : 1.0;
    }

    public double getCooldownMultiplierNow() {
        return System.currentTimeMillis() < cooldownPenaltyUntil ? cooldownPenaltyMult : 1.0;
    }

    public int getUpgradeCost() {
        int maxLvl = towerConfig().getMaxLevel(type, pathId);
        if (level >= maxLvl) {
            return -1; // Max level reached
        }
        return towerConfig().getCost(type, pathId, level + 1, (level + 1) * 150);
    }

    public int getTotalValue() {
        int total = 0;
        for (int l = 1; l <= level; l++) {
            total += towerConfig().getCost(type, pathId, l, l == 1 ? type.getCost() : l * 100);
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

    public java.util.List<org.bukkit.entity.Bee> getSpawnedBees() { return spawnedBees; }
    public java.util.List<ArmorStand> getLandmines() { return landmines; }
    public java.util.List<org.bukkit.entity.BlockDisplay> getHazardDisplays() { return hazardDisplays; }
}
