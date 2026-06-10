package com.pauljang.towerDefense.towers;

import org.bukkit.Material;
import org.bukkit.ChatColor;
public enum TowerType {
    ARCHER("Archer Tower", Material.DISPENSER, Material.COBBLESTONE, Material.OAK_FENCE, ChatColor.GREEN, 15.0, 1.5, 20L, 100),
    FIRE("Fire Tower", Material.REDSTONE_LAMP, Material.POLISHED_BLACKSTONE, Material.NETHER_BRICK_FENCE, ChatColor.RED, 8.0, 1.0, 80L, 175),
    PRISMARINE("Prismarine Tower", Material.PRISMARINE_BRICKS, Material.PRISMARINE, Material.PRISMARINE_WALL, ChatColor.DARK_AQUA, 8.0, 0.5, 30L, 125),
    CHORUS("Chorus Tower", Material.CHORUS_FLOWER, Material.PURPUR_BLOCK, Material.PURPUR_PILLAR, ChatColor.LIGHT_PURPLE, 8.0, 0.0, 120L, 150),
    REDSTONE("Redstone Tower", Material.REDSTONE_BLOCK, Material.SMOOTH_STONE, Material.STONE_BRICK_WALL, ChatColor.DARK_RED, 10.0, 0.0, 20L, 200),
    POISON("Poison Tower", Material.MUD_BRICKS, Material.MOSS_BLOCK, Material.MANGROVE_FENCE, ChatColor.DARK_GREEN, 8.0, 0.5, 80L, 150),
    ICE("Ice Tower", Material.PACKED_ICE, Material.SNOW_BLOCK, Material.BLUE_ICE, ChatColor.AQUA, 8.0, 0.5, 30L, 125),
    GOLEM("Golem Tower", Material.IRON_BLOCK, Material.SMOOTH_STONE, Material.IRON_BARS, ChatColor.GRAY, 10.0, 20.0, 40L, 400),
    HAPPY_GHAST("Happy Ghast Tower", Material.GHAST_SPAWN_EGG, Material.SOUL_SAND, Material.NETHERRACK, ChatColor.LIGHT_PURPLE, 15.0, 15.0, 50L, 500),
    DRIPSTONE("Dripstone Tower", Material.POINTED_DRIPSTONE, Material.DRIPSTONE_BLOCK, Material.DRIPSTONE_BLOCK, ChatColor.GOLD, 10.0, 6.0, 60L, 200),
    THUNDER("Thunder Tower", Material.LIGHTNING_ROD, Material.COPPER_BLOCK, Material.COPPER_BLOCK, ChatColor.YELLOW, 12.0, 5.0, 80L, 200),
    TURRET("Turret", Material.OBSERVER, Material.IRON_BLOCK, Material.IRON_BARS, ChatColor.WHITE, 12.0, 0.3, 5L, 250),
    BOMBARDIER("Bombardier", Material.TNT, Material.BRICKS, Material.BRICK_WALL, ChatColor.DARK_RED, 10.0, 2.5, 50L, 175),
    BEEHIVE("Beehive", Material.BEE_NEST, Material.HONEYCOMB_BLOCK, Material.HONEY_BLOCK, ChatColor.GOLD, 7.0, 4.0, 40L, 150);

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

