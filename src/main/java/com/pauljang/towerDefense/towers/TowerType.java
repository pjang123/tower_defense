package com.pauljang.towerDefense.towers;

import org.bukkit.Material;
import org.bukkit.ChatColor;

public enum TowerType {
    ARCHER("Archer Tower", Material.DISPENSER, Material.COBBLESTONE, Material.OAK_FENCE, ChatColor.GREEN, 15.0, 1.5, 20L, 100), // Range 15, damage 1.5, attack every 20 ticks (1s)
    MAGE("Mage Tower", Material.REDSTONE_LAMP, Material.POLISHED_BLACKSTONE, Material.NETHER_BRICK_FENCE, ChatColor.RED, 12.0, 3.0, 30L, 175),   // Range 12, damage 3.0, attack every 30 ticks (1.5s)
    FROST("Frost Tower", Material.PACKED_ICE, Material.SNOW_BLOCK, Material.SPRUCE_FENCE, ChatColor.AQUA, 15.0, 0.5, 20L, 125);   // Range 15, damage 0.5, attack every 20 ticks (1s), applies slowness

    private final String displayName;
    private final Material blockMaterial;
    private final Material baseMaterial;
    private final Material middleMaterial;
    private final ChatColor color;
    private final double range;
    private final double damage;
    private final long cooldown;
    private final int cost;

    TowerType(String displayName, Material blockMaterial, Material baseMaterial, Material middleMaterial, ChatColor color, double range, double damage, long cooldown, int cost) {
        this.displayName = displayName;
        this.blockMaterial = blockMaterial;
        this.baseMaterial = baseMaterial;
        this.middleMaterial = middleMaterial;
        this.color = color;
        this.range = range;
        this.damage = damage;
        this.cooldown = cooldown;
        this.cost = cost;
    }

    public String getDisplayName() { return displayName; }
    public Material getBlockMaterial() { return blockMaterial; }
    public Material getBaseMaterial() { return baseMaterial; }
    public Material getMiddleMaterial() { return middleMaterial; }
    public ChatColor getColor() { return color; }
    public double getRange() { return range; }
    public double getDamage() { return damage; }
    public long getCooldown() { return cooldown; }
    public int getCost() { return cost; }
}

