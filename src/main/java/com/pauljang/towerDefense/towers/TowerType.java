package com.pauljang.towerDefense.towers;

import org.bukkit.Material;
import org.bukkit.ChatColor;

public enum TowerType {
    ARCHER("Archer Tower", Material.DISPENSER, ChatColor.GREEN, 15.0, 1.5, 20L), // Range 15, damage 1.5, attack every 20 ticks (1s)
    MAGE("Mage Tower", Material.REDSTONE_LAMP, ChatColor.RED, 12.0, 3.0, 30L),   // Range 12, damage 3.0, attack every 30 ticks (1.5s)
    FROST("Frost Tower", Material.PACKED_ICE, ChatColor.AQUA, 15.0, 0.5, 20L);   // Range 15, damage 0.5, attack every 20 ticks (1s), applies slowness

    private final String displayName;
    private final Material blockMaterial;
    private final ChatColor color;
    private final double range;
    private final double damage;
    private final long cooldown;

    TowerType(String displayName, Material blockMaterial, ChatColor color, double range, double damage, long cooldown) {
        this.displayName = displayName;
        this.blockMaterial = blockMaterial;
        this.color = color;
        this.range = range;
        this.damage = damage;
        this.cooldown = cooldown;
    }

    public String getDisplayName() { return displayName; }
    public Material getBlockMaterial() { return blockMaterial; }
    public ChatColor getColor() { return color; }
    public double getRange() { return range; }
    public double getDamage() { return damage; }
    public long getCooldown() { return cooldown; }
}
