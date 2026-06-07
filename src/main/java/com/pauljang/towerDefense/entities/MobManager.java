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
import com.pauljang.towerDefense.entities.MobStateProfile;
import com.pauljang.towerDefense.entities.MobUpgradeRegistry;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class MobManager {

    private final TowerDefense plugin;
    private final MobUpgradeRegistry upgradeRegistry;
    private final List<TDMob> activeMobs = new ArrayList<>();
    private final Map<UUID, Map<String, Integer>> playerQueues = new HashMap<>();

    public MobManager(TowerDefense plugin) {
        this.plugin = plugin;
        this.upgradeRegistry = new MobUpgradeRegistry(plugin);
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

    public MobUpgradeRegistry getUpgradeRegistry() {
        return upgradeRegistry;
    }

    // Slot → chain index mapping for the main GUI
    private static final int[] MOB_SLOTS = {10,11,12,13,14,15,16, 19,20,21,22,23,24,25, 28,29};

    public String getChainForSlot(int slot) {
        List<String> chains = upgradeRegistry.getAvailableChains();
        for (int i = 0; i < MOB_SLOTS.length && i < chains.size(); i++) {
            if (MOB_SLOTS[i] == slot) return chains.get(i);
        }
        return null;
    }

    public String getChainDisplayName(String chainKey) {
        String[] words = chainKey.split(" ");
        StringBuilder sb = new StringBuilder();
        for (String word : words) {
            if (sb.length() > 0) sb.append(" ");
            if (!word.isEmpty()) {
                sb.append(Character.toUpperCase(word.charAt(0)));
                if (word.length() > 1) sb.append(word.substring(1));
            }
        }
        return sb.toString();
    }

    private org.bukkit.Material getChainMaterial(String chainKey) {
        return switch (chainKey) {
            case "zombie"       -> org.bukkit.Material.ZOMBIE_SPAWN_EGG;
            case "spider"       -> org.bukkit.Material.SPIDER_SPAWN_EGG;
            case "skeleton"     -> org.bukkit.Material.SKELETON_SPAWN_EGG;
            case "creeper"      -> org.bukkit.Material.CREEPER_SPAWN_EGG;
            case "silverfish"   -> org.bukkit.Material.SILVERFISH_SPAWN_EGG;
            case "blaze"        -> org.bukkit.Material.BLAZE_SPAWN_EGG;
            case "zombie pigman"-> org.bukkit.Material.ZOMBIFIED_PIGLIN_SPAWN_EGG;
            case "witch"        -> org.bukkit.Material.WITCH_SPAWN_EGG;
            case "slime"        -> org.bukkit.Material.SLIME_SPAWN_EGG;
            case "giant"        -> org.bukkit.Material.ROTTEN_FLESH;
            case "warden"       -> org.bukkit.Material.WARDEN_SPAWN_EGG;
            case "ravager"      -> org.bukkit.Material.RAVAGER_SPAWN_EGG;
            case "hoglin"       -> org.bukkit.Material.HOGLIN_SPAWN_EGG;
            case "enderman"     -> org.bukkit.Material.ENDERMAN_SPAWN_EGG;
            case "endermite"    -> org.bukkit.Material.ENDERMITE_SPAWN_EGG;
            case "breeze"       -> org.bukkit.Material.BREEZE_SPAWN_EGG;
            default             -> org.bukkit.Material.ZOMBIE_SPAWN_EGG;
        };
    }

    public Mob spawnMob(String arena, EntityType type, double speedMultiplier, double maxHealth, double armor, boolean immuneToSlow, boolean immuneToFire, int goldReward, int xpReward, String presetKey) {
        java.util.Map<String, com.pauljang.towerDefense.data.TDWaypoint> graph = plugin.getWaypointConfigManager().getWaypointGraph(arena);
        if (graph.isEmpty() || !graph.containsKey("0")) {
            plugin.getLogger().warning("Cannot spawn mob: No starting waypoint '0' defined for arena " + arena + "!");
            return null;
        }

        Location startLocation = graph.get("0").getLocation();
        
        // Flying check: spawn slightly higher if height-offset > 0
        double heightOffset = plugin.getConfig().getDouble("mobs." + presetKey + ".height-offset", 0.0);
        Location spawnLoc = startLocation.clone();
        if (heightOffset > 0.0) {
            spawnLoc.add(0, heightOffset, 0);
        }

        // Ensure the chunk is loaded before spawning
        if (spawnLoc.getWorld() != null && !spawnLoc.getChunk().isLoaded()) {
            spawnLoc.getChunk().load();
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

        // Set size if it's a Slime/Magma Cube; both need setAI(false) since movement is velocity-driven
        if (entity instanceof org.bukkit.entity.Slime slime) {
            slime.setSize(4);
            slime.setAI(false);
        }

        // Disable Creeper explosion AI; movement is controlled via velocity
        if (entity instanceof org.bukkit.entity.Creeper) {
            entity.setAI(false);
        }

        // Brain AI mobs ignore removeAllGoals() and override velocity; teleporting Enderman breaks path
        if (type == EntityType.GIANT
                || type == EntityType.WARDEN
                || type == EntityType.ENDERMAN
                || type == EntityType.ZOMBIFIED_PIGLIN
                || type == EntityType.HOGLIN
                || type == EntityType.ZOGLIN
                || type == EntityType.BREEZE) {
            entity.setAI(false);
        }

        // WitherSkeleton (Skeleton T4-T5) is mounted; setAI prevents combat goals fighting vehicle velocity
        if (type == EntityType.WITHER_SKELETON) {
            entity.setAI(false);
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

        // Set movement speed modifier — floor prevents entities with 0 base speed (e.g. Giant) from being immobile
        {
            org.bukkit.attribute.AttributeInstance speedAttr = entity.getAttribute(Attribute.MOVEMENT_SPEED);
            if (speedAttr != null) {
                double base = speedAttr.getBaseValue();
                if (base < 0.05) base = 0.25;
                speedAttr.setBaseValue(base * speedMultiplier);
            }
        }

        // Set max health modifier
        // Paper 1.21 enforces a hard 1024 ceiling on setHealth() regardless of attribute base value,
        // so clamp to the attribute's effective value to avoid IllegalArgumentException.
        if (maxHealth > 0) {
            org.bukkit.attribute.AttributeInstance maxHealthAttr = entity.getAttribute(Attribute.MAX_HEALTH);
            if (maxHealthAttr != null) {
                maxHealthAttr.setBaseValue(maxHealth);
                entity.setHealth(Math.min(maxHealth, maxHealthAttr.getValue()));
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

        TDMob tdMob = new TDMob(entity, graph);
        activeMobs.add(tdMob);
        return entity;
    }

    /**
     * Spawns a mob using the upgrade chain + tier from the registry,
     * applying equipment, mount, and flying attributes from the CSV profile.
     */
    public void spawnMobByChain(String arena, String upgradeChain, int tier) {
        String chain = upgradeChain.toLowerCase();
        MobStateProfile profile = upgradeRegistry.getProfile(chain, tier);
        if (profile == null) {
            plugin.getLogger().warning("No mob profile for '" + chain + "' tier " + tier);
            return;
        }

        boolean isFlying = profile.getSpecialMechanics().contains("Flying") || profile.getSpecialMechanics().contains("flying");
        boolean fireImmune = profile.getEntityType() == EntityType.BLAZE
                || profile.getEntityType() == EntityType.WITHER_SKELETON
                || profile.getSpecialMechanics().toLowerCase().contains("fire resistant");
        boolean slowImmune = isFlying; // flying mobs dodge ground-based slow effects

        // Kill gold reward = 10% of spawn price, minimum 1
        int killGold = Math.max(1, (int) (profile.getPrice() / 10.0));
        int xpReward = profile.getExpReward();

        // presetKey drives height-offset config lookup; use "flying" for flying mobs
        String presetKey = isFlying ? chain : chain.replace(" ", "_");

        Mob entity = spawnMob(arena, profile.getEntityType(), profile.getSpeed(), profile.getHp(),
                0.0, slowImmune, fireImmune, killGold, xpReward, presetKey);
        if (entity == null) return;

        // Override gravity for flying mobs (spawnMob already checks height-offset, but we set it here too)
        if (isFlying) {
            entity.setGravity(false);
            // Move entity up 3 blocks if it spawned at ground level
            org.bukkit.Location loc = entity.getLocation();
            entity.teleport(loc.add(0, 3, 0));
        }

        // Store tower immunities in PDC so TowerManager can check them
        if (!profile.getImmunities().isEmpty()) {
            String immunityStr = String.join(",", profile.getImmunities());
            entity.getPersistentDataContainer().set(
                new org.bukkit.NamespacedKey(plugin, "td_immunities"),
                org.bukkit.persistence.PersistentDataType.STRING, immunityStr);
        }

        // Apply equipment (helmet or hand item based on material type)
        if (profile.getEquipment() != null) {
            org.bukkit.inventory.ItemStack equipItem = new org.bukkit.inventory.ItemStack(profile.getEquipment());
            String matName = profile.getEquipment().name();
            org.bukkit.inventory.EntityEquipment eq = entity.getEquipment();
            if (eq != null) {
                if (matName.endsWith("_HELMET")) {
                    eq.setHelmet(equipItem);
                    eq.setHelmetDropChance(0.0f);
                } else if (matName.endsWith("_SWORD") || matName.endsWith("_AXE")) {
                    eq.setItemInMainHand(equipItem);
                    eq.setItemInMainHandDropChance(0.0f);
                }
            }
        }

        // Charged creeper check
        if (profile.getEntityType() == EntityType.CREEPER
                && profile.getSpecialMechanics().contains("Charged")) {
            ((org.bukkit.entity.Creeper) entity).setPowered(true);
        }

        // Spawn mount entity and set the mob as its passenger
        if (profile.getMountType() != null) {
            org.bukkit.Location spawnLoc = entity.getLocation();
            if (spawnLoc.getWorld() != null) {
                org.bukkit.entity.Entity mount = spawnLoc.getWorld().spawnEntity(spawnLoc, profile.getMountType());
                // Prevent the mount from wandering or being tracked as a TD mob by towers
                if (mount instanceof Mob mountMob) {
                    mountMob.setAI(false);
                }
                mount.addPassenger(entity);
            }
        }
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
                        for (org.bukkit.entity.Player p : org.bukkit.Bukkit.getOnlinePlayers()) {
                            if (mobArena.equals(plugin.getGameManager().getPlayerArena(p.getUniqueId()))) {
                                p.playSound(mob.getEntity().getLocation(), org.bukkit.Sound.ENTITY_ZOMBIE_ATTACK_WOODEN_DOOR, 1.0f, 1.0f);
                            }
                        }
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

                // Also remove any mount (e.g. Pig/Cow for T3/T4 Creeper)
                org.bukkit.entity.Entity creeperVehicle = mob.getEntity().getVehicle();
                if (creeperVehicle != null) creeperVehicle.remove();
                mob.getEntity().remove();
                iterator.remove();
                return;
            }

            Location finalTarget = mob.getFinalOffsetWaypoint();
            if (finalTarget != null) {
                Location finalTargetLoc = finalTarget.clone().add(0, heightOffset, 0);
                // Periodically repath back to their offset spot if pushed away
                if (currentTick % 10 == 0) {
                    if (mob.getEntity().getType() == EntityType.GIANT || mob.getEntity().getType() == EntityType.SLIME || mob.getEntity().getType() == EntityType.CREEPER || mob.getEntity().getType() == EntityType.WARDEN || mob.getEntity().getType() == EntityType.ENDERMAN || mob.getEntity().getType() == EntityType.ZOMBIFIED_PIGLIN || mob.getEntity().getType() == EntityType.HOGLIN || mob.getEntity().getType() == EntityType.ZOGLIN || mob.getEntity().getType() == EntityType.BREEZE || mob.getEntity().getVehicle() instanceof org.bukkit.entity.Mob || heightOffset > 0.0) {
                        org.bukkit.entity.Mob physicalMoverF = mob.getEntity();
                        if (mob.getEntity().getVehicle() instanceof org.bukkit.entity.Mob vm) physicalMoverF = vm;
                        Location loc = physicalMoverF.getLocation();
                        org.bukkit.util.Vector dir = finalTargetLoc.clone().subtract(loc).toVector();
                        if (dir.lengthSquared() > 0.01) {
                            if (heightOffset <= 0.0) dir.setY(0);
                            dir.normalize();
                            float yaw = (float) Math.toDegrees(Math.atan2(-dir.getX(), dir.getZ()));
                            float pitch = heightOffset > 0.0 ? (float) Math.toDegrees(Math.atan2(-dir.getY(), Math.sqrt(dir.getX()*dir.getX() + dir.getZ()*dir.getZ()))) : 0.0f;
                            physicalMoverF.setRotation(yaw, pitch);
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
                                physicalMoverF.setVelocity(dir.multiply(speed));
                            } else {
                                physicalMoverF.setVelocity(dir.multiply(speed).setY(physicalMoverF.getVelocity().getY()));
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
                for (org.bukkit.entity.Player p : org.bukkit.Bukkit.getOnlinePlayers()) {
                    if (mobArena.equals(plugin.getGameManager().getPlayerArena(p.getUniqueId()))) {
                        p.playSound(
                            mob.getEntity().getLocation(),
                            org.bukkit.Sound.ENTITY_ZOMBIE_ATTACK_WOODEN_DOOR,
                            1.0f,
                            1.0f
                        );
                    }
                }
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

        if (mob.getEntity().getType() == EntityType.GIANT || mob.getEntity().getType() == EntityType.SLIME || mob.getEntity().getType() == EntityType.CREEPER || mob.getEntity().getType() == EntityType.WARDEN || mob.getEntity().getType() == EntityType.ENDERMAN || mob.getEntity().getType() == EntityType.ZOMBIFIED_PIGLIN || mob.getEntity().getType() == EntityType.HOGLIN || mob.getEntity().getType() == EntityType.ZOGLIN || mob.getEntity().getType() == EntityType.BREEZE || mob.getEntity().getVehicle() instanceof org.bukkit.entity.Mob || heightOffset > 0.0) {
            // Velocity-based movement: Brain AI mobs, NoAI mobs, mounted mobs, and flying mobs
            // For mounted Creepers (T3/T4), drive the vehicle entity so it carries the passenger
            org.bukkit.entity.Mob physicalMover = mob.getEntity();
            if (mob.getEntity().getVehicle() instanceof org.bukkit.entity.Mob vehicleMob) physicalMover = vehicleMob;
            Location loc = physicalMover.getLocation();
            org.bukkit.util.Vector dir = targetLoc.clone().subtract(loc).toVector();
            double distanceSq = dir.lengthSquared();

            double speed = 0.1;
            org.bukkit.attribute.AttributeInstance speedAttr = mob.getEntity().getAttribute(Attribute.MOVEMENT_SPEED);
            if (speedAttr != null) {
                speed = speedAttr.getValue();
            }
            if (mob.getEntity().getType() == EntityType.SLIME) {
                speed = 0.05; // Slimes move slowly
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
                physicalMover.setRotation(yaw, pitch);

                // Move towards target
                if (heightOffset > 0.0) {
                    physicalMover.setVelocity(dir.multiply(speed));
                } else {
                    physicalMover.setVelocity(dir.multiply(speed).setY(physicalMover.getVelocity().getY()));
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

            // Re-calculate pathing only when target waypoint ID changes or periodically (every 5 ticks / 250ms)
            String currentWpId = mob.getCurrentWaypointId();
            if (currentWpId == null || !currentWpId.equals(mob.getLastPathfindWaypointId()) || currentTick % 5 == 0) {
                mob.getEntity().getPathfinder().moveTo(targetLoc, pathfinderSpeed);
                mob.setLastPathfindWaypointId(currentWpId);
            }


        }

        // Check if they are close enough to the waypoint to target the next one
        double reachDistance = (mob.getEntity().getType() == EntityType.GIANT || mob.getEntity().getType() == EntityType.SLIME || mob.getEntity().getType() == EntityType.CREEPER || mob.getEntity().getType() == EntityType.WARDEN || mob.getEntity().getType() == EntityType.ENDERMAN || mob.getEntity().getType() == EntityType.ZOMBIFIED_PIGLIN || mob.getEntity().getType() == EntityType.HOGLIN || mob.getEntity().getType() == EntityType.ZOGLIN || mob.getEntity().getType() == EntityType.BREEZE || mob.getEntity().getVehicle() instanceof org.bukkit.entity.Mob || heightOffset > 0.0) ? 4.0 : 1.5;
        if (mob.getEntity().getLocation().distanceSquared(targetLoc) < reachDistance) {
            mob.advanceToNextWaypoint();
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

    public Map<String, Integer> getQueue(UUID uuid) {
        return playerQueues.computeIfAbsent(uuid, k -> {
            Map<String, Integer> map = new LinkedHashMap<>();
            for (String chain : upgradeRegistry.getAvailableChains()) {
                map.put(chain, 0);
            }
            return map;
        });
    }

    public void addToQueue(UUID uuid, String chain) {
        String key = chain.toLowerCase();
        Map<String, Integer> queue = getQueue(uuid);
        queue.put(key, queue.getOrDefault(key, 0) + 1);
    }

    public void removeFromQueue(UUID uuid, String chain) {
        String key = chain.toLowerCase();
        Map<String, Integer> queue = getQueue(uuid);
        int current = queue.getOrDefault(key, 0);
        if (current > 0) {
            queue.put(key, current - 1);
        }
    }

    /** Legacy overload kept for any existing callers. */
    public void addToQueue(UUID uuid, PresetMobType type) {
        addToQueue(uuid, type.name().toLowerCase());
    }

    /** Legacy overload kept for any existing callers. */
    public void removeFromQueue(UUID uuid, PresetMobType type) {
        removeFromQueue(uuid, type.name().toLowerCase());
    }

    public void clearQueue(UUID uuid) {
        Map<String, Integer> queue = getQueue(uuid);
        for (String key : queue.keySet()) {
            queue.put(key, 0);
        }
    }

    public void clearAllQueues() {
        playerQueues.clear();
    }

    public void sendQueue(UUID uuid) {
        Map<String, Integer> queue = playerQueues.get(uuid);
        if (queue == null || queue.isEmpty()) return;

        List<String> spawnList = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : queue.entrySet()) {
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
                String upgradeChain = spawnList.get(index);
                int tier = plugin.getGameManager().getMobTier(uuid, upgradeChain);
                spawnMobByChain(targetArena, upgradeChain, tier);
                index++;
            }
        }.runTaskTimer(plugin, 0L, 10L);

        // Reset the player's queue after spawning
        clearQueue(uuid);
    }

    public void openMobSpawnerGUI(Player player) {
        org.bukkit.inventory.Inventory gui = org.bukkit.Bukkit.createInventory(null, 54, ChatColor.DARK_RED + "TD Mob Spawner");
        Map<String, Integer> queue = getQueue(player.getUniqueId());

        // Border: top row, bottom row, left and right columns
        org.bukkit.inventory.ItemStack border = createGUIItem(Material.GRAY_STAINED_GLASS_PANE, " ");
        org.bukkit.inventory.ItemStack filler = createGUIItem(Material.BLACK_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 54; i++) {
            if (i < 9 || i >= 45 || i % 9 == 0 || i % 9 == 8) {
                gui.setItem(i, border);
            } else {
                gui.setItem(i, filler);
            }
        }

        // Place all 16 mob chains in order
        List<String> chains = upgradeRegistry.getAvailableChains();
        for (int i = 0; i < MOB_SLOTS.length && i < chains.size(); i++) {
            String chain = chains.get(i);
            int queuedCount = queue.getOrDefault(chain, 0);
            gui.setItem(MOB_SLOTS[i], createChainGUIItem(player, chain, queuedCount));
        }

        // Compute total queue cost for display
        int totalCost = 0;
        for (Map.Entry<String, Integer> entry : queue.entrySet()) {
            if (entry.getValue() <= 0) continue;
            int tier = plugin.getGameManager().getMobTier(player.getUniqueId(), entry.getKey());
            MobStateProfile profile = upgradeRegistry.getProfile(entry.getKey(), tier);
            if (profile != null) totalCost += (int) profile.getPrice() * entry.getValue();
        }

        gui.setItem(38, createGUIItem(Material.RED_WOOL,
                ChatColor.RED + "Clear Queue",
                ChatColor.GRAY + "Dequeue all mobs and refund Gold."));
        gui.setItem(40, createGUIItem(Material.LIME_WOOL,
                ChatColor.GREEN + "Send Wave",
                ChatColor.GRAY + "Spawn all queued mobs on the opponent's track.",
                ChatColor.GOLD + "Total Cost: " + ChatColor.YELLOW + totalCost + " Gold"));
        gui.setItem(42, createGUIItem(Material.NETHER_STAR,
                ChatColor.GOLD + "Player Upgrades",
                ChatColor.GRAY + "Open weapons & upgrades screen."));

        player.openInventory(gui);
    }

    private org.bukkit.inventory.ItemStack createChainGUIItem(Player player, String chain, int queuedCount) {
        int tier = plugin.getGameManager().getMobTier(player.getUniqueId(), chain);
        MobStateProfile profile = upgradeRegistry.getProfile(chain, tier);

        org.bukkit.Material mat = getChainMaterial(chain);
        int stackAmt = Math.max(1, Math.min(queuedCount, 64));
        org.bukkit.inventory.ItemStack item = new org.bukkit.inventory.ItemStack(mat, stackAmt);
        org.bukkit.inventory.meta.ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        String displayName = getChainDisplayName(chain);
        ChatColor nameColor = getTierColor(tier);
        meta.setDisplayName(nameColor + displayName + ChatColor.GRAY + " [Tier " + tier + "]");

        List<String> lore = new ArrayList<>();
        if (profile != null) {
            lore.add(ChatColor.GOLD + "Cost: " + ChatColor.YELLOW + (int) profile.getPrice() + " Gold/mob");
            lore.add(ChatColor.GOLD + "EXP Payout: " + ChatColor.LIGHT_PURPLE + profile.getExpReward() + " XP");
            lore.add("");
            lore.add(ChatColor.DARK_GRAY + "HP: " + ChatColor.WHITE + (int) profile.getHp()
                    + "  Speed: " + ChatColor.WHITE + profile.getSpeed()
                    + "  Dmg: " + ChatColor.WHITE + profile.getDamage());
            if (!profile.getImmunities().isEmpty()) {
                lore.add(ChatColor.AQUA + "Immune: " + String.join(", ", profile.getImmunities()));
            }
            if (!profile.getSpecialMechanics().isEmpty()) {
                lore.add(ChatColor.LIGHT_PURPLE + profile.getSpecialMechanics());
            }
        }
        lore.add("");
        if (queuedCount > 0) {
            lore.add(ChatColor.GREEN + "Queued: " + ChatColor.YELLOW + queuedCount);
        }
        lore.add(ChatColor.GREEN + "Left-Click " + ChatColor.GRAY + "→ choose tier & queue");
        lore.add(ChatColor.RED + "Right-Click " + ChatColor.GRAY + "→ dequeue 1");
        lore.add(ChatColor.RED + "Shift+Right " + ChatColor.GRAY + "→ dequeue 10");

        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ChatColor getTierColor(int tier) {
        return switch (tier) {
            case 1 -> ChatColor.GRAY;
            case 2 -> ChatColor.GREEN;
            case 3 -> ChatColor.AQUA;
            case 4 -> ChatColor.LIGHT_PURPLE;
            case 5 -> ChatColor.GOLD;
            default -> ChatColor.WHITE;
        };
    }

    /** XP cost to unlock each tier. Tier 1 is always free. */
    public static int getTierUnlockCost(int tier) {
        return switch (tier) {
            case 2 -> 100;
            case 3 -> 300;
            case 4 -> 600;
            case 5 -> 1200;
            default -> 0;
        };
    }

    public void openMobTierGUI(Player player, String chain) {
        String displayName = getChainDisplayName(chain);
        org.bukkit.inventory.Inventory gui = org.bukkit.Bukkit.createInventory(null, 27,
                ChatColor.DARK_PURPLE + "Mob Tier: " + displayName);

        org.bukkit.inventory.ItemStack border = createGUIItem(Material.GRAY_STAINED_GLASS_PANE, " ");
        org.bukkit.inventory.ItemStack filler = createGUIItem(Material.BLACK_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 27; i++) {
            if (i < 9 || i >= 18 || i % 9 == 0 || i % 9 == 8) {
                gui.setItem(i, border);
            } else {
                gui.setItem(i, filler);
            }
        }

        int currentTier = plugin.getGameManager().getMobTier(player.getUniqueId(), chain);
        Map<Integer, MobStateProfile> allTiers = upgradeRegistry.getAllTiers(chain);

        // Tiers at slots 10–14 (up to 5 tiers)
        for (int t = 1; t <= 5; t++) {
            MobStateProfile profile = allTiers.get(t);
            if (profile == null) continue;
            boolean unlocked = plugin.getGameManager().isTierUnlocked(player.getUniqueId(), chain, t);
            gui.setItem(9 + t, createTierItem(profile, t == currentTier, unlocked));
        }

        // Back button at slot 22
        gui.setItem(22, createGUIItem(Material.ARROW,
                ChatColor.GRAY + "← Back",
                ChatColor.GRAY + "Return to the mob spawner."));

        player.openInventory(gui);
    }

    private org.bukkit.inventory.ItemStack createTierItem(MobStateProfile profile, boolean isSelected, boolean isUnlocked) {
        int tier = profile.getTier();
        ChatColor color = getTierColor(tier);
        org.bukkit.Material mat = isUnlocked ? getChainMaterial(profile.getUpgradeChain()) : org.bukkit.Material.BARRIER;
        org.bukkit.inventory.ItemStack item = new org.bukkit.inventory.ItemStack(mat);
        org.bukkit.inventory.meta.ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        String selectedTag = isSelected ? ChatColor.YELLOW + " ★ SELECTED" : "";
        String lockedTag = !isUnlocked ? ChatColor.RED + " 🔒 LOCKED" : "";
        meta.setDisplayName(color + "Tier " + tier + ": " + profile.getEntityType().name() + selectedTag + lockedTag);

        List<String> lore = new ArrayList<>();
        if (!isUnlocked) {
            lore.add(ChatColor.RED + "Unlock Cost: " + ChatColor.YELLOW + getTierUnlockCost(tier) + " EXP");
            lore.add("");
        }
        lore.add(ChatColor.GOLD + "Cost: " + ChatColor.YELLOW + (int) profile.getPrice() + " Gold");
        lore.add(ChatColor.RED + "HP: " + ChatColor.WHITE + (int) profile.getHp()
                + "  " + ChatColor.GREEN + "Speed: " + ChatColor.WHITE + profile.getSpeed());
        lore.add(ChatColor.DARK_RED + "Damage: " + ChatColor.WHITE + profile.getDamage()
                + "  " + ChatColor.LIGHT_PURPLE + "EXP: " + ChatColor.WHITE + profile.getExpReward());
        if (!profile.getImmunities().isEmpty()) {
            lore.add(ChatColor.AQUA + "Immune: " + String.join(", ", profile.getImmunities()));
        }
        if (profile.getMountType() != null) {
            lore.add(ChatColor.YELLOW + "Mount: " + profile.getMountType().name());
        }
        if (profile.getEquipment() != null) {
            lore.add(ChatColor.YELLOW + "Equipment: " + profile.getEquipment().name());
        }
        if (!profile.getSpecialMechanics().isEmpty()) {
            lore.add(ChatColor.LIGHT_PURPLE + profile.getSpecialMechanics());
        }
        lore.add("");
        if (isUnlocked) {
            lore.add(ChatColor.GREEN + "Left-Click " + ChatColor.GRAY + "→ queue 1 at this tier");
            lore.add(ChatColor.GREEN + "Shift+Left " + ChatColor.GRAY + "→ queue 5 at this tier");
        } else {
            lore.add(ChatColor.YELLOW + "Left-Click " + ChatColor.GRAY + "→ spend EXP to unlock");
        }

        meta.setLore(lore);
        item.setItemMeta(meta);
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
