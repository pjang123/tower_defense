package com.pauljang.towerDefense.entities;

import com.pauljang.towerDefense.TowerDefense;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class MobManager {

    private final TowerDefense plugin;
    private final List<TDMob> activeMobs = new ArrayList<>();
    private final Map<UUID, Map<PresetMobType, Integer>> playerQueues = new HashMap<>();

    public MobManager(TowerDefense plugin) {
        this.plugin = plugin;
        startMobTicker();
    }

    public void spawnMob(EntityType type) {
        spawnMob("1", type);
    }

    public void spawnMob(String arena, EntityType type) {
        spawnMob(arena, type, 1.0, -1.0, 0.0, false, false, 15, 5);
    }

    public void spawnMob(EntityType type, double speedMultiplier, double maxHealth, double armor, boolean immuneToSlow, boolean immuneToFire) {
        spawnMob("1", type, speedMultiplier, maxHealth, armor, immuneToSlow, immuneToFire, 15, 5);
    }

    public void spawnMob(String arena, EntityType type, double speedMultiplier, double maxHealth, double armor, boolean immuneToSlow, boolean immuneToFire) {
        spawnMob(arena, type, speedMultiplier, maxHealth, armor, immuneToSlow, immuneToFire, 15, 5);
    }

    public void spawnMob(EntityType type, double speedMultiplier, double maxHealth, double armor, boolean immuneToSlow, boolean immuneToFire, int goldReward) {
        int xpReward = Math.max(1, goldReward / 3);
        spawnMob("1", type, speedMultiplier, maxHealth, armor, immuneToSlow, immuneToFire, goldReward, xpReward);
    }

    public void spawnMob(String arena, EntityType type, double speedMultiplier, double maxHealth, double armor, boolean immuneToSlow, boolean immuneToFire, int goldReward) {
        int xpReward = Math.max(1, goldReward / 3);
        spawnMob(arena, type, speedMultiplier, maxHealth, armor, immuneToSlow, immuneToFire, goldReward, xpReward);
    }

    public void spawnMob(EntityType type, double speedMultiplier, double maxHealth, double armor, boolean immuneToSlow, boolean immuneToFire, int goldReward, int xpReward) {
        spawnMob("1", type, speedMultiplier, maxHealth, armor, immuneToSlow, immuneToFire, goldReward, xpReward);
    }

    public void spawnMob(String arena, EntityType type, double speedMultiplier, double maxHealth, double armor, boolean immuneToSlow, boolean immuneToFire, int goldReward, int xpReward) {
        spawnMob(arena, type, speedMultiplier, maxHealth, armor, immuneToSlow, immuneToFire, goldReward, xpReward, type.name().toLowerCase());
    }

    public void spawnMob(String arena, EntityType type, double speedMultiplier, double maxHealth, double armor, boolean immuneToSlow, boolean immuneToFire, int goldReward, int xpReward, String presetKey) {
        List<Location> waypoints = plugin.getWaypointConfigManager().getWaypoints(arena);
        if (waypoints.isEmpty()) {
            plugin.getLogger().warning("Cannot spawn mob: No waypoints defined for arena " + arena + "!");
            return;
        }

        Location startLocation = waypoints.get(0);
        
        // Flying check: spawn slightly higher if height-offset > 0
        double heightOffset = plugin.getConfig().getDouble("mobs." + presetKey + ".height-offset", 0.0);
        Location spawnLoc = startLocation.clone();
        if (heightOffset > 0.0) {
            spawnLoc.add(0, heightOffset, 0);
        }

        Mob entity = (Mob) spawnLoc.getWorld().spawnEntity(spawnLoc, type);

        // Force spawned mobs to be their adult versions
        if (entity instanceof org.bukkit.entity.Ageable ageable) {
            ageable.setAdult();
        }
        if (entity instanceof org.bukkit.entity.Zombie zombie) {
            zombie.setBaby(false);
        }
        if (entity instanceof org.bukkit.entity.Piglin piglin) {
            piglin.setBaby(false);
        }
        
        // Mark as a TD Mob so we can handle events (like sunlight burning)
        entity.getPersistentDataContainer().set(new NamespacedKey(plugin, "td_mob"), PersistentDataType.BYTE, (byte) 1);

        // Store preset key
        entity.getPersistentDataContainer().set(new NamespacedKey(plugin, "td_preset"), PersistentDataType.STRING, presetKey);

        // Store gold reward amount in container
        entity.getPersistentDataContainer().set(new NamespacedKey(plugin, "td_gold_reward"), PersistentDataType.INTEGER, goldReward);

        // Store xp reward amount in container
        entity.getPersistentDataContainer().set(new NamespacedKey(plugin, "td_xp_reward"), PersistentDataType.INTEGER, xpReward);

        // Store arena ID in container
        entity.getPersistentDataContainer().set(new NamespacedKey(plugin, "td_arena"), PersistentDataType.STRING, arena);

        // Prevent zombification for nether mobs in the overworld
        if (entity instanceof org.bukkit.entity.Piglin piglin) {
            piglin.setImmuneToZombification(true);
        } else if (entity instanceof org.bukkit.entity.Hoglin hoglin) {
            hoglin.setImmuneToZombification(true);
        }

        // Set size if it's a Slime/Magma Cube (start at size 4, the largest)
        if (entity instanceof org.bukkit.entity.Slime slime) {
            slime.setSize(4);
        }

        // Mark slow and fire immunities if requested or SLOW_SHIELD is active on the arena
        boolean isSlowShieldActive = plugin.getGameManager().isSpellActive(arena, "SLOW_SHIELD");
        if (immuneToSlow || isSlowShieldActive) {
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

        // Give Giant extra step height so it doesn't get stuck on 1-block steps
        if (type == EntityType.GIANT) {
            org.bukkit.attribute.AttributeInstance stepAttr = entity.getAttribute(Attribute.STEP_HEIGHT);
            if (stepAttr != null) {
                stepAttr.setBaseValue(1.5);
            }
        }

        // Handle gravity for flying mobs
        if (heightOffset > 0.0) {
            entity.setGravity(false);
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

        StringBuilder status = new StringBuilder();
        if (mob.getFireTicks() > 0) {
            status.append(" ").append(org.bukkit.ChatColor.GOLD).append("🔥");
        }
        if (mob.hasPotionEffect(org.bukkit.potion.PotionEffectType.POISON)) {
            status.append(" ").append(org.bukkit.ChatColor.GREEN).append("🤢");
        }
        if (mob.hasPotionEffect(org.bukkit.potion.PotionEffectType.SLOWNESS)) {
            status.append(" ").append(org.bukkit.ChatColor.AQUA).append("❄");
        }
        if (status.length() > 0) {
            bar.append(status);
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

                    if (tickCounter % 5 == 0) {
                        updateHealthBar(mob.getEntity());
                    }

                    // Damage Storm check
                    String mobArena = mob.getEntity().getPersistentDataContainer().get(
                        new org.bukkit.NamespacedKey(plugin, "td_arena"),
                        org.bukkit.persistence.PersistentDataType.STRING
                    );
                    if (mobArena == null) mobArena = "1";
                    if (plugin.getGameManager().isSpellActive(mobArena, "DAMAGE_STORM")) {
                        if (tickCounter % 20 == 0) {
                            double dps = plugin.getConfig().getDouble("spells.damage-storm.damage-per-second", 2.0);
                            mob.getEntity().damage(dps);
                            mob.getEntity().getWorld().spawnParticle(
                                org.bukkit.Particle.LAVA,
                                mob.getEntity().getLocation().add(0, 0.5, 0),
                                5, 0.2, 0.2, 0.2, 0.05
                            );
                        }
                    }

                    handleMobMovement(mob, iterator, tickCounter);
                }
                tickCounter++;
            }
        }.runTaskTimer(plugin, 0L, 1L); // Run every tick (20 times per second) for snappy transitions
    }

    private void handleMobMovement(TDMob mob, Iterator<TDMob> iterator, long currentTick) {
        String mobArena = mob.getEntity().getPersistentDataContainer().get(
            new org.bukkit.NamespacedKey(plugin, "td_arena"),
            org.bukkit.persistence.PersistentDataType.STRING
        );
        if (mobArena == null) mobArena = "1";

        // Freeze status check (applied by Ice Towers)
        boolean isFrozen = false;
        org.bukkit.NamespacedKey freezeUntilKey = new org.bukkit.NamespacedKey(plugin, "td_frozen_until");
        if (mob.getEntity().getPersistentDataContainer().has(freezeUntilKey, org.bukkit.persistence.PersistentDataType.LONG)) {
            long frozenUntil = mob.getEntity().getPersistentDataContainer().get(freezeUntilKey, org.bukkit.persistence.PersistentDataType.LONG);
            if (System.currentTimeMillis() < frozenUntil) {
                isFrozen = true;
            }
        }

        if (isFrozen) {
            // Stop pathfinding
            mob.getEntity().getPathfinder().stopPathfinding();
            // Zero out horizontal velocity, keep vertical velocity (Y) so gravity still works
            org.bukkit.util.Vector currentVel = mob.getEntity().getVelocity();
            mob.getEntity().setVelocity(new org.bukkit.util.Vector(0, currentVel.getY(), 0));

            // Spawn ice/snowflake particles periodically
            if (currentTick % 5 == 0) {
                mob.getEntity().getWorld().spawnParticle(
                    org.bukkit.Particle.SNOWFLAKE,
                    mob.getEntity().getLocation().add(0, 0.5, 0),
                    3, 0.2, 0.2, 0.2, 0.02
                );
            }
            
            // Bypass progression and target-setting
            if (mob.hasReachedFinalWaypoint()) {
                Location finalTarget = mob.getFinalOffsetWaypoint();
                if (finalTarget != null) {
                    // Attack logic: damage castle every 40 ticks
                    if (currentTick - mob.getLastAttackTick() >= 40) {
                        mob.setLastAttackTick(currentTick);
                        plugin.getGameManager().damageCastle(mobArena, 1);
                        mob.getEntity().swingMainHand();
                        mob.getEntity().getWorld().playSound(mob.getEntity().getLocation(), org.bukkit.Sound.ENTITY_ZOMBIE_ATTACK_WOODEN_DOOR, 1.0f, 1.0f);
                    }
                }
            }
            return;
        }

        boolean isFreezeActive = plugin.getGameManager().isSpellActive(mobArena, "FREEZE");
        boolean isHasteActive = plugin.getGameManager().isSpellActive(mobArena, "HASTE_RUSH");
        boolean isSlowImmune = mob.getEntity().getPersistentDataContainer().has(
            new org.bukkit.NamespacedKey(plugin, "td_slow_immune"),
            org.bukkit.persistence.PersistentDataType.BYTE
        );

        double slowMult = 1.0;
        if (isFreezeActive && !isSlowImmune) {
            slowMult = plugin.getConfig().getDouble("spells.freeze.slow-multiplier", 0.4);
        }

        double hasteMult = 1.0;
        if (isHasteActive) {
            hasteMult = plugin.getConfig().getDouble("spells.haste-rush.speed-multiplier", 1.6);
        }

        String presetKey = mob.getEntity().getPersistentDataContainer().get(
            new org.bukkit.NamespacedKey(plugin, "td_preset"),
            org.bukkit.persistence.PersistentDataType.STRING
        );
        if (presetKey == null) presetKey = mob.getEntity().getType().name().toLowerCase();

        double heightOffset = plugin.getConfig().getDouble("mobs." + presetKey + ".height-offset", 0.0);

        // If the mob has reached the final waypoint, run stay-centered & attack/explode logic
        if (mob.hasReachedFinalWaypoint()) {
            // Check if it's a Creeper - explode and deal massive damage!
            if (mob.getEntity().getType() == EntityType.CREEPER) {
                int castleDamage = plugin.getConfig().getInt("mobs.creeper.castle-damage", 5);
                plugin.getGameManager().damageCastle(mobArena, castleDamage);
                
                Location loc = mob.getEntity().getLocation();
                loc.getWorld().spawnParticle(org.bukkit.Particle.EXPLOSION, loc, 1);
                loc.getWorld().playSound(loc, org.bukkit.Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 1.0f);
                
                mob.getEntity().remove();
                iterator.remove();
                return;
            }

            Location finalTarget = mob.getFinalOffsetWaypoint();
            if (finalTarget != null) {
                Location finalTargetLoc = finalTarget.clone().add(0, heightOffset, 0);
                // Periodically repath back to their offset spot if pushed away
                if (currentTick % 10 == 0) {
                    if (mob.getEntity().getType() == EntityType.GIANT || mob.getEntity().getType() == EntityType.SLIME || mob.getEntity().getType() == EntityType.MAGMA_CUBE || heightOffset > 0.0) {
                        Location loc = mob.getEntity().getLocation();
                        org.bukkit.util.Vector dir = finalTargetLoc.clone().subtract(loc).toVector();
                        if (dir.lengthSquared() > 0.01) {
                            if (heightOffset <= 0.0) dir.setY(0);
                            dir.normalize();
                            float yaw = (float) Math.toDegrees(Math.atan2(-dir.getX(), dir.getZ()));
                            float pitch = heightOffset > 0.0 ? (float) Math.toDegrees(Math.atan2(-dir.getY(), Math.sqrt(dir.getX()*dir.getX() + dir.getZ()*dir.getZ()))) : 0.0f;
                            mob.getEntity().setRotation(yaw, pitch);
                            double speed = 0.1;
                            org.bukkit.attribute.AttributeInstance speedAttr = mob.getEntity().getAttribute(Attribute.MOVEMENT_SPEED);
                            if (speedAttr != null) {
                                speed = speedAttr.getValue();
                            }
                            if (isFreezeActive && !isSlowImmune) {
                                speed = speed * slowMult;
                            }
                            if (isHasteActive) {
                                speed = speed * hasteMult;
                            }
                            if (heightOffset > 0.0) {
                                mob.getEntity().setVelocity(dir.multiply(speed));
                            } else {
                                mob.getEntity().setVelocity(dir.multiply(speed).setY(mob.getEntity().getVelocity().getY()));
                            }
                        }
                    } else {
                        double pathfinderSpeed = 1.0;
                        if (isFreezeActive && !isSlowImmune) {
                            pathfinderSpeed = pathfinderSpeed * slowMult;
                        }
                        if (isHasteActive) {
                            pathfinderSpeed = pathfinderSpeed * hasteMult;
                        }
                        mob.getEntity().getPathfinder().moveTo(finalTargetLoc, pathfinderSpeed);
                    }
                }
            }

            // Attack logic: damage castle every 40 ticks (2 seconds)
            if (currentTick - mob.getLastAttackTick() >= 40) {
                mob.setLastAttackTick(currentTick);

                // Deal 1 damage to the specific arena's castle health
                plugin.getGameManager().damageCastle(mobArena, 1);

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

        Location targetLoc = target.clone().add(0, heightOffset, 0);

        if (mob.getEntity().getType() == EntityType.GIANT || mob.getEntity().getType() == EntityType.SLIME || mob.getEntity().getType() == EntityType.MAGMA_CUBE || heightOffset > 0.0) {
            // Manually move the giant or flying mob towards the target
            Location loc = mob.getEntity().getLocation();
            org.bukkit.util.Vector dir = targetLoc.clone().subtract(loc).toVector();
            double distanceSq = dir.lengthSquared();

            double speed = 0.1;
            org.bukkit.attribute.AttributeInstance speedAttr = mob.getEntity().getAttribute(Attribute.MOVEMENT_SPEED);
            if (speedAttr != null) {
                speed = speedAttr.getValue();
            }

            if (isFreezeActive && !isSlowImmune) {
                speed = speed * slowMult;
                if (currentTick % 20 == 0) {
                    mob.getEntity().addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.SLOWNESS, 40, 2, false, false, true));
                }
            }
            if (isHasteActive) {
                speed = speed * hasteMult;
                if (currentTick % 20 == 0) {
                    mob.getEntity().addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.SPEED, 40, 0, false, false, true));
                }
            }

            if (distanceSq > 0.01) {
                if (heightOffset <= 0.0) dir.setY(0);
                dir.normalize();

                // Update rotation smoothly
                float yaw = (float) Math.toDegrees(Math.atan2(-dir.getX(), dir.getZ()));
                float pitch = heightOffset > 0.0 ? (float) Math.toDegrees(Math.atan2(-dir.getY(), Math.sqrt(dir.getX()*dir.getX() + dir.getZ()*dir.getZ()))) : 0.0f;
                mob.getEntity().setRotation(yaw, pitch);

                // Move towards target
                if (heightOffset > 0.0) {
                    mob.getEntity().setVelocity(dir.multiply(speed));
                } else {
                    mob.getEntity().setVelocity(dir.multiply(speed).setY(mob.getEntity().getVelocity().getY()));
                }
            }
        } else {
            double pathfinderSpeed = 1.0;
            if (isFreezeActive && !isSlowImmune) {
                pathfinderSpeed = pathfinderSpeed * slowMult;
                if (currentTick % 20 == 0) {
                    mob.getEntity().addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.SLOWNESS, 40, 2, false, false, true));
                }
            }
            if (isHasteActive) {
                pathfinderSpeed = pathfinderSpeed * hasteMult;
                if (currentTick % 20 == 0) {
                    mob.getEntity().addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.SPEED, 40, 0, false, false, true));
                }
            }

            // Re-calculate pathing only when target waypoint index changes or periodically (every 5 ticks / 250ms)
            if (mob.getCurrentWaypointIndex() != mob.getLastPathfindWaypointIndex() || currentTick % 5 == 0) {
                mob.getEntity().getPathfinder().moveTo(targetLoc, pathfinderSpeed);
                mob.setLastPathfindWaypointIndex(mob.getCurrentWaypointIndex());
            }
        }

        // Check if they are close enough to the waypoint to target the next one
        double reachDistance = (mob.getEntity().getType() == EntityType.GIANT || mob.getEntity().getType() == EntityType.SLIME || mob.getEntity().getType() == EntityType.MAGMA_CUBE || heightOffset > 0.0) ? 4.0 : 1.5;
        if (mob.getEntity().getLocation().distanceSquared(targetLoc) < reachDistance) {
            mob.incrementWaypointIndex();
        }
    }
    
    public void cleanup() {
        for (TDMob mob : activeMobs) {
            mob.getEntity().remove();
        }
        activeMobs.clear();
    }

    public List<TDMob> getActiveMobs() {
        return activeMobs;
    }

    // --- GUI & Queue System ---

    public Map<PresetMobType, Integer> getQueue(UUID uuid) {
        return playerQueues.computeIfAbsent(uuid, k -> {
            Map<PresetMobType, Integer> map = new HashMap<>();
            for (PresetMobType type : PresetMobType.values()) {
                map.put(type, 0);
            }
            return map;
        });
    }

    public void addToQueue(UUID uuid, PresetMobType type) {
        Map<PresetMobType, Integer> queue = getQueue(uuid);
        queue.put(type, queue.getOrDefault(type, 0) + 1);
    }

    public void removeFromQueue(UUID uuid, PresetMobType type) {
        Map<PresetMobType, Integer> queue = getQueue(uuid);
        int current = queue.getOrDefault(type, 0);
        if (current > 0) {
            queue.put(type, current - 1);
        }
    }

    public void clearQueue(UUID uuid) {
        Map<PresetMobType, Integer> queue = getQueue(uuid);
        for (PresetMobType type : PresetMobType.values()) {
            queue.put(type, 0);
        }
    }

    public void clearAllQueues() {
        playerQueues.clear();
    }

    public void sendQueue(UUID uuid) {
        Map<PresetMobType, Integer> queue = playerQueues.get(uuid);
        if (queue == null || queue.isEmpty()) return;

        List<PresetMobType> spawnList = new ArrayList<>();
        for (Map.Entry<PresetMobType, Integer> entry : queue.entrySet()) {
            for (int i = 0; i < entry.getValue(); i++) {
                spawnList.add(entry.getKey());
            }
        }

        if (spawnList.isEmpty()) return;

        // Determine opponent's arena
        String playerArena = plugin.getGameManager().getPlayerArena(uuid);
        String targetArena = playerArena.equals("1") ? "2" : "1";

        // Spawn mobs spaced 10 ticks (0.5s) apart
        new BukkitRunnable() {
            int index = 0;

            @Override
            public void run() {
                if (index >= spawnList.size()) {
                    cancel();
                    return;
                }
                PresetMobType preset = spawnList.get(index);
                String nameKey = preset.name().toLowerCase();
                double speed = plugin.getConfig().getDouble("mobs." + nameKey + ".speed", preset.getSpeed());
                double health = plugin.getConfig().getDouble("mobs." + nameKey + ".health", preset.getHealth());
                double armor = plugin.getConfig().getDouble("mobs." + nameKey + ".armor", preset.getArmor());
                boolean slowImmune = plugin.getConfig().getBoolean("mobs." + nameKey + ".slow-immune", preset.isSlowImmune());
                boolean fireImmune = plugin.getConfig().getBoolean("mobs." + nameKey + ".fire-immune", preset.isFireImmune());
                int goldReward = plugin.getConfig().getInt("mobs." + nameKey + ".gold-reward", preset.getGoldReward());
                int xpReward = plugin.getConfig().getInt("mobs." + nameKey + ".xp-reward", preset.getXpReward());

                spawnMob(targetArena, preset.getEntityType(), speed, health, armor, slowImmune, fireImmune, goldReward, xpReward);
                index++;
            }
        }.runTaskTimer(plugin, 0L, 10L);

        // Reset the player's queue after spawning
        clearQueue(uuid);
    }

    public void openMobSpawnerGUI(Player player) {
        org.bukkit.inventory.Inventory gui = org.bukkit.Bukkit.createInventory(null, 54, ChatColor.DARK_RED + "TD Mob Spawner");

        Map<PresetMobType, Integer> queue = getQueue(player.getUniqueId());

        // Fill background with gray stained glass panes
        org.bukkit.inventory.ItemStack border = createGUIItem(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 54; i++) {
            if (i < 9 || i >= 45 || i % 9 == 0 || i % 9 == 8) {
                gui.setItem(i, border);
            }
        }

        // Place spawn items (slots 10-16 for Row 1, and slots 20-23 for Row 2)
        gui.setItem(10, createMobGUIItem(PresetMobType.ZOMBIE, queue.getOrDefault(PresetMobType.ZOMBIE, 0)));
        gui.setItem(11, createMobGUIItem(PresetMobType.SKELETON, queue.getOrDefault(PresetMobType.SKELETON, 0)));
        gui.setItem(12, createMobGUIItem(PresetMobType.SILVERFISH, queue.getOrDefault(PresetMobType.SILVERFISH, 0)));
        gui.setItem(13, createMobGUIItem(PresetMobType.SPIDER, queue.getOrDefault(PresetMobType.SPIDER, 0)));
        gui.setItem(14, createMobGUIItem(PresetMobType.PIGMAN, queue.getOrDefault(PresetMobType.PIGMAN, 0)));
        gui.setItem(15, createMobGUIItem(PresetMobType.SLIME, queue.getOrDefault(PresetMobType.SLIME, 0)));
        gui.setItem(16, createMobGUIItem(PresetMobType.CREEPER, queue.getOrDefault(PresetMobType.CREEPER, 0)));

        gui.setItem(20, createMobGUIItem(PresetMobType.BLAZE, queue.getOrDefault(PresetMobType.BLAZE, 0)));
        gui.setItem(21, createMobGUIItem(PresetMobType.MAGMA_CUBE, queue.getOrDefault(PresetMobType.MAGMA_CUBE, 0)));
        gui.setItem(22, createMobGUIItem(PresetMobType.GHAST, queue.getOrDefault(PresetMobType.GHAST, 0)));
        gui.setItem(23, createMobGUIItem(PresetMobType.GIANT, queue.getOrDefault(PresetMobType.GIANT, 0)));

        // Place control buttons (slots 38, 40, and 42)
        int totalCost = 0;
        for (Map.Entry<PresetMobType, Integer> entry : queue.entrySet()) {
            int cost = plugin.getConfig().getInt("mobs." + entry.getKey().name().toLowerCase() + ".spawn-cost", entry.getKey().getSpawnCost());
            totalCost += cost * entry.getValue();
        }
        gui.setItem(38, createGUIItem(Material.RED_WOOL, ChatColor.RED + "Clear Queue", ChatColor.GRAY + "Reset all counts and refund Gold."));
        gui.setItem(40, createGUIItem(Material.LIME_WOOL, ChatColor.GREEN + "Send Wave", ChatColor.GRAY + "Spawn all queued mobs.", ChatColor.GOLD + "Total Wave Value: " + ChatColor.YELLOW + totalCost + " Gold"));
        gui.setItem(42, createGUIItem(Material.NETHER_STAR, ChatColor.GOLD + "Player Upgrades", ChatColor.GRAY + "Open weapons & upgrades screen."));

        player.openInventory(gui);
    }

    private org.bukkit.inventory.ItemStack createMobGUIItem(PresetMobType preset, int count) {
        int amount = Math.max(1, count);
        org.bukkit.inventory.ItemStack item = new org.bukkit.inventory.ItemStack(preset.getMaterial(), amount);
        org.bukkit.inventory.meta.ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(preset.getColor() + preset.getDisplayName());
            
            List<String> lore = new ArrayList<>();
            String nameKey = preset.name().toLowerCase();
            int spawnCost = plugin.getConfig().getInt("mobs." + nameKey + ".spawn-cost", preset.getSpawnCost());
            double speed = plugin.getConfig().getDouble("mobs." + nameKey + ".speed", preset.getSpeed());
            double health = plugin.getConfig().getDouble("mobs." + nameKey + ".health", preset.getHealth());
            double armor = plugin.getConfig().getDouble("mobs." + nameKey + ".armor", preset.getArmor());
            boolean slowImmune = plugin.getConfig().getBoolean("mobs." + nameKey + ".slow-immune", preset.isSlowImmune());
            boolean fireImmune = plugin.getConfig().getBoolean("mobs." + nameKey + ".fire-immune", preset.isFireImmune());

            lore.add(ChatColor.GREEN + "Left-Click" + ChatColor.GRAY + " to add +1 to queue.");
            lore.add(ChatColor.GREEN + "Shift + Left-Click" + ChatColor.GRAY + " to add +10 to queue.");
            lore.add(ChatColor.RED + "Right-Click" + ChatColor.GRAY + " to remove -1 from queue.");
            lore.add(ChatColor.RED + "Shift + Right-Click" + ChatColor.GRAY + " to remove -10 from queue.");
            lore.add(ChatColor.GOLD + "Cost: " + ChatColor.YELLOW + spawnCost + " Gold");
            lore.add(ChatColor.GOLD + "Queued Count: " + ChatColor.YELLOW + count);
            lore.add("");
            
            lore.add(ChatColor.DARK_GRAY + "Stats:");
            lore.add(ChatColor.DARK_GRAY + " - Speed: " + speed + "x");
            lore.add(ChatColor.DARK_GRAY + " - HP: " + (health > 0 ? health : "Default"));
            lore.add(ChatColor.DARK_GRAY + " - Armor: " + armor);
            if (slowImmune) lore.add(ChatColor.AQUA + " * Immune to Slow");
            if (fireImmune) lore.add(ChatColor.RED + " * Immune to Fire");

            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private org.bukkit.inventory.ItemStack createGUIItem(Material material, String name, String... lore) {
        org.bukkit.inventory.ItemStack item = new org.bukkit.inventory.ItemStack(material);
        org.bukkit.inventory.meta.ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            meta.setLore(java.util.Arrays.asList(lore));
            item.setItemMeta(meta);
        }
        return item;
    }
}
