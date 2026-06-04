package com.pauljang.towerDefense.entities;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;

public enum PresetMobType {
    ZOMBIE(EntityType.ZOMBIE, 1.0, 20.0, 2.0, false, false, Material.ZOMBIE_SPAWN_EGG, ChatColor.GREEN, "Zombie", 15, 5, 15),
    SKELETON(EntityType.SKELETON, 1.1, 30.0, 2.0, false, false, Material.SKELETON_SPAWN_EGG, ChatColor.GRAY, "Skeleton", 45, 15, 60),
    SILVERFISH(EntityType.SILVERFISH, 1.6, 8.0, 0.0, false, false, Material.SILVERFISH_SPAWN_EGG, ChatColor.DARK_GRAY, "Silverfish", 80, 25, 120),
    SPIDER(EntityType.SPIDER, 1.4, 40.0, 2.0, false, false, Material.SPIDER_SPAWN_EGG, ChatColor.RED, "Spider", 100, 30, 150),
    PIGMAN(EntityType.ZOMBIFIED_PIGLIN, 0.7, 100.0, 4.0, false, true, Material.ZOMBIFIED_PIGLIN_SPAWN_EGG, ChatColor.GOLD, "Pigman", 130, 45, 200),
    SLIME(EntityType.SLIME, 0.3, 50.0, 0.0, false, false, Material.SLIME_SPAWN_EGG, ChatColor.GREEN, "Slime", 160, 50, 250),
    CREEPER(EntityType.CREEPER, 1.0, 60.0, 0.0, false, false, Material.CREEPER_SPAWN_EGG, ChatColor.DARK_GREEN, "Creeper", 260, 80, 400),
    BLAZE(EntityType.BLAZE, 1.0, 80.0, 0.0, true, true, Material.BLAZE_SPAWN_EGG, ChatColor.GOLD, "Blaze", 320, 100, 500),
    MAGMA_CUBE(EntityType.MAGMA_CUBE, 0.8, 90.0, 6.0, true, true, Material.MAGMA_CUBE_SPAWN_EGG, ChatColor.DARK_RED, "Magma Cube", 380, 120, 600),
    GHAST(EntityType.GHAST, 0.7, 200.0, 0.0, true, true, Material.GHAST_SPAWN_EGG, ChatColor.WHITE, "Ghast", 500, 160, 1000),
    GIANT(EntityType.GIANT, 0.4, 500.0, 10.0, true, false, Material.ROTTEN_FLESH, ChatColor.DARK_RED, "Giant Zombie", 600, 200, 1000);

    private final EntityType entityType;
    private final double speed;
    private final double health;
    private final double armor;
    private final boolean slowImmune;
    private final boolean fireImmune;
    private final Material material;
    private final ChatColor color;
    private final String displayName;
    private final int goldReward;
    private final int xpReward;
    private final int spawnCost;

    PresetMobType(EntityType entityType, double speed, double health, double armor, boolean slowImmune, boolean fireImmune, Material material, ChatColor color, String displayName, int goldReward, int xpReward, int spawnCost) {
        this.entityType = entityType;
        this.speed = speed;
        this.health = health;
        this.armor = armor;
        this.slowImmune = slowImmune;
        this.fireImmune = fireImmune;
        this.material = material;
        this.color = color;
        this.displayName = displayName;
        this.goldReward = goldReward;
        this.xpReward = xpReward;
        this.spawnCost = spawnCost;
    }

    public EntityType getEntityType() { return entityType; }
    public double getSpeed() { return speed; }
    public double getHealth() { return health; }
    public double getArmor() { return armor; }
    public boolean isSlowImmune() { return slowImmune; }
    public boolean isFireImmune() { return fireImmune; }
    public Material getMaterial() { return material; }
    public ChatColor getColor() { return color; }
    public String getDisplayName() { return displayName; }
    public int getGoldReward() { return goldReward; }
    public int getXpReward() { return xpReward; }
    public int getSpawnCost() { return spawnCost; }
}
