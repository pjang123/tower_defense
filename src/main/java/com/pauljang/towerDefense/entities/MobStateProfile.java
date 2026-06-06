package com.pauljang.towerDefense.entities;

import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import java.util.List;

/**
 * Represents a mob variant profile loaded from the CSV configuration.
 * It contains all attributes required for spawning and configuring a mob.
 */
public class MobStateProfile {
    private final String upgradeChain;
    private final int tier;
    private final EntityType entityType;
    private final double price;
    private final double damage;
    private final double hp;
    private final double speed;
    private final int expReward;
    private final List<String> immunities; // e.g., ["MAGE", "ZEUS"]
    private final EntityType mountType; // may be null
    private final Material equipment; // may be null
    private final String specialMechanics; // raw string description

    public MobStateProfile(String upgradeChain, int tier, EntityType entityType, double price,
                           double damage, double hp, double speed, int expReward,
                           List<String> immunities, EntityType mountType,
                           Material equipment, String specialMechanics) {
        this.upgradeChain = upgradeChain;
        this.tier = tier;
        this.entityType = entityType;
        this.price = price;
        this.damage = damage;
        this.hp = hp;
        this.speed = speed;
        this.expReward = expReward;
        this.immunities = immunities;
        this.mountType = mountType;
        this.equipment = equipment;
        this.specialMechanics = specialMechanics;
    }

    public String getUpgradeChain() { return upgradeChain; }
    public int getTier() { return tier; }
    public EntityType getEntityType() { return entityType; }
    public double getPrice() { return price; }
    public double getDamage() { return damage; }
    public double getHp() { return hp; }
    public double getSpeed() { return speed; }
    public int getExpReward() { return expReward; }
    public List<String> getImmunities() { return immunities; }
    public EntityType getMountType() { return mountType; }
    public Material getEquipment() { return equipment; }
    public String getSpecialMechanics() { return specialMechanics; }
}
