package com.pauljang.towerDefense.entities;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;

public enum PresetMobType {
    DEFAULT_ZOMBIE(EntityType.ZOMBIE, 1.0, -1.0, 0.0, false, false, Material.ZOMBIE_SPAWN_EGG, ChatColor.GREEN, "Default Zombie", 15, 5, 10),
    GIANT(EntityType.GIANT, 0.5, 150.0, 8.0, true, false, Material.ROTTEN_FLESH, ChatColor.DARK_RED, "Giant Zombie", 80, 40, 50),
    FIRE_ZOMBIE(EntityType.ZOMBIE, 1.2, 25.0, 2.0, true, true, Material.MAGMA_CREAM, ChatColor.GOLD, "Nether Fire Zombie", 40, 20, 25),
    PIGLIN(EntityType.PIGLIN, 1.0, -1.0, 0.0, false, false, Material.PIGLIN_SPAWN_EGG, ChatColor.LIGHT_PURPLE, "Piglin Attacker", 25, 10, 15),
    HOGLIN(EntityType.HOGLIN, 1.0, -1.0, 0.0, false, false, Material.HOGLIN_SPAWN_EGG, ChatColor.DARK_PURPLE, "Hoglin Attacker", 60, 30, 40);

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
