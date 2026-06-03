package com.pauljang.towerDefense.entities;

import com.pauljang.towerDefense.TowerDefense;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Mob;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class MobManager {

    private final TowerDefense plugin;
    private final List<TDMob> activeMobs = new ArrayList<>();

    public MobManager(TowerDefense plugin) {
        this.plugin = plugin;
        startMobTicker();
    }

    public void spawnMob(EntityType type) {
        spawnMob(type, 1.0, -1.0, 0.0, false, false);
    }

    public void spawnMob(EntityType type, double speedMultiplier, double maxHealth, double armor, boolean immuneToSlow, boolean immuneToFire) {
        List<Location> waypoints = plugin.getWaypointConfigManager().getWaypoints();
        if (waypoints.isEmpty()) {
            plugin.getLogger().warning("Cannot spawn mob: No waypoints defined!");
            return;
        }

        Location startLocation = waypoints.get(0);
        Mob entity = (Mob) startLocation.getWorld().spawnEntity(startLocation, type);
        
        // Mark as a TD Mob so we can handle events (like sunlight burning)
        entity.getPersistentDataContainer().set(new NamespacedKey(plugin, "td_mob"), PersistentDataType.BYTE, (byte) 1);

        // Prevent zombification for nether mobs in the overworld
        if (entity instanceof org.bukkit.entity.Piglin piglin) {
            piglin.setImmuneToZombification(true);
        } else if (entity instanceof org.bukkit.entity.Hoglin hoglin) {
            hoglin.setImmuneToZombification(true);
        }

        // Mark slow and fire immunities if requested
        if (immuneToSlow) {
            entity.getPersistentDataContainer().set(new NamespacedKey(plugin, "td_slow_immune"), PersistentDataType.BYTE, (byte) 1);
        }
        if (immuneToFire) {
            entity.getPersistentDataContainer().set(new NamespacedKey(plugin, "td_fire_immune"), PersistentDataType.BYTE, (byte) 1);
        }

        // Make the mob immune to knockback via attributes
        org.bukkit.attribute.AttributeInstance kbResist = entity.getAttribute(Attribute.KNOCKBACK_RESISTANCE);
        if (kbResist != null) {
            kbResist.setBaseValue(1.0);
        }

        // Set movement speed modifier
        if (speedMultiplier != 1.0) {
            org.bukkit.attribute.AttributeInstance speedAttr = entity.getAttribute(Attribute.MOVEMENT_SPEED);
            if (speedAttr != null) {
                speedAttr.setBaseValue(speedAttr.getBaseValue() * speedMultiplier);
            }
        }

        // Set max health modifier
        if (maxHealth > 0) {
            org.bukkit.attribute.AttributeInstance maxHealthAttr = entity.getAttribute(Attribute.MAX_HEALTH);
            if (maxHealthAttr != null) {
                maxHealthAttr.setBaseValue(maxHealth);
                entity.setHealth(maxHealth);
            }
        }

        // Set armor modifier
        if (armor > 0) {
            org.bukkit.attribute.AttributeInstance armorAttr = entity.getAttribute(Attribute.ARMOR);
            if (armorAttr != null) {
                armorAttr.setBaseValue(armor);
            }
        }

        // Initialize healthbar
        updateHealthBar(entity);

        TDMob tdMob = new TDMob(entity, waypoints);
        activeMobs.add(tdMob);
    }

    public void updateHealthBar(Mob mob) {
        org.bukkit.attribute.AttributeInstance maxHealthAttr = mob.getAttribute(Attribute.MAX_HEALTH);
        double maxHealth = maxHealthAttr != null ? maxHealthAttr.getValue() : mob.getHealth();
        double health = mob.getHealth();
        double ratio = Math.max(0.0, Math.min(1.0, health / maxHealth));

        int totalBars = 10;
        int greenBars = (int) Math.round(ratio * totalBars);
        int grayBars = totalBars - greenBars;

        org.bukkit.ChatColor color;
        if (ratio >= 0.6) {
            color = org.bukkit.ChatColor.GREEN;
        } else if (ratio >= 0.25) {
            color = org.bukkit.ChatColor.YELLOW;
        } else {
            color = org.bukkit.ChatColor.RED;
        }

        StringBuilder bar = new StringBuilder();
        bar.append(color);
        for (int i = 0; i < greenBars; i++) {
            bar.append("■");
        }
        bar.append(org.bukkit.ChatColor.GRAY);
        for (int i = 0; i < grayBars; i++) {
            bar.append("■");
        }

        mob.setCustomName(bar.toString());
        mob.setCustomNameVisible(true);
    }

    private void startMobTicker() {
        new BukkitRunnable() {
            private long tickCounter = 0;

            @Override
            public void run() {
                Iterator<TDMob> iterator = activeMobs.iterator();
                while (iterator.hasNext()) {
                    TDMob mob = iterator.next();
                    
                    if (mob.getEntity().isDead() || !mob.getEntity().isValid()) {
                        iterator.remove();
                        continue;
                    }

                    handleMobMovement(mob, iterator, tickCounter);
                }
                tickCounter++;
            }
        }.runTaskTimer(plugin, 0L, 1L); // Run every tick (20 times per second) for snappy transitions
    }

    private void handleMobMovement(TDMob mob, Iterator<TDMob> iterator, long currentTick) {
        // If the mob has reached the final waypoint, run stay-centered & attack logic
        if (mob.hasReachedFinalWaypoint()) {
            Location finalTarget = mob.getFinalOffsetWaypoint();
            if (finalTarget != null) {
                // Periodically repath back to their offset spot if pushed away
                if (currentTick % 10 == 0) {
                    mob.getEntity().getPathfinder().moveTo(finalTarget, 1.0);
                }
            }

            // Attack logic: damage castle every 40 ticks (2 seconds)
            if (currentTick - mob.getLastAttackTick() >= 40) {
                mob.setLastAttackTick(currentTick);

                // Deal 1 damage to castle health
                plugin.getGameManager().damageCastle(1);

                // Play custom swing animation & strike sound/particles at the mob's position
                mob.getEntity().swingMainHand();
                mob.getEntity().getWorld().playSound(
                    mob.getEntity().getLocation(),
                    org.bukkit.Sound.ENTITY_ZOMBIE_ATTACK_WOODEN_DOOR,
                    1.0f,
                    1.0f
                );
                mob.getEntity().getWorld().spawnParticle(
                    org.bukkit.Particle.SWEEP_ATTACK,
                    mob.getEntity().getEyeLocation().add(mob.getEntity().getLocation().getDirection().multiply(0.8)),
                    1
                );
            }
            return;
        }

        Location target = mob.getNextWaypoint();
        if (target == null) {
            // Backup in case waypoints were empty
            iterator.remove();
            return;
        }

        // Re-calculate pathing only when target waypoint index changes or periodically (every 5 ticks / 250ms)
        if (mob.getCurrentWaypointIndex() != mob.getLastPathfindWaypointIndex() || currentTick % 5 == 0) {
            mob.getEntity().getPathfinder().moveTo(target, 1.0);
            mob.setLastPathfindWaypointIndex(mob.getCurrentWaypointIndex());
        }

        // Check if they are close enough to the waypoint to target the next one
        if (mob.getEntity().getLocation().distanceSquared(target) < 1.5) {
            mob.incrementWaypointIndex();
        }
    }
    
    public void cleanup() {
        for (TDMob mob : activeMobs) {
            mob.getEntity().remove();
        }
        activeMobs.clear();
    }
}
