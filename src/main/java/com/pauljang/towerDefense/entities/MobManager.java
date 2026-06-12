package com.pauljang.towerDefense.entities;

import com.pauljang.towerDefense.TowerDefense;
import com.pauljang.towerDefense.TDKeys;
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
import com.pauljang.towerDefense.core.Match;
import com.pauljang.towerDefense.entities.MobStateProfile;
import com.pauljang.towerDefense.entities.MobUpgradeRegistry;
import com.pauljang.towerDefense.entities.PresetMobType;
import com.pauljang.towerDefense.data.TDWaypoint;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class MobManager {

    /**
     * Global scaling coefficient applied to every mob's movement-speed attribute. Reduced to 0.25
     * (one quarter of the former 1.0) during the post-playtest rebalance to slow overall pacing.
     * Applied once at spawn so it flows through to both velocity-driven and pathfinder-driven mobs.
     */
    private static final double GLOBAL_SPEED_COEFFICIENT = 0.25;

    private final TowerDefense plugin;
    private final MobUpgradeRegistry upgradeRegistry;
    // Per-player queue tracks the exact tier of each queued mob: chain -> (tier -> count).
    // This preserves mixed-tier waves (e.g. five Lvl 1 Zombies plus one Lvl 5 Zombie).
    private final Map<UUID, Map<String, Map<Integer, Integer>>> playerQueues = new HashMap<>();

    public MobManager(TowerDefense plugin) {
        this.plugin = plugin;
        this.upgradeRegistry = new MobUpgradeRegistry(plugin);
        startMobTicker();
    }

    // Legacy methods - these require a Match parameter
    public void spawnMob(Match match, EntityType type) {
        spawnMob(match, "1", type);
    }

    public void spawnMob(Match match, String arena, EntityType type) {
        spawnMob(match, arena, type, 1.0, -1.0, 0.0, false, false, 15, 5);
    }

    public void spawnMob(Match match, EntityType type, double speedMultiplier, double maxHealth, double armor, boolean immuneToSlow, boolean immuneToFire) {
        spawnMob(match, "1", type, speedMultiplier, maxHealth, armor, immuneToSlow, immuneToFire, 15, 5);
    }

    public void spawnMob(Match match, String arena, EntityType type, double speedMultiplier, double maxHealth, double armor, boolean immuneToSlow, boolean immuneToFire) {
        spawnMob(match, arena, type, speedMultiplier, maxHealth, armor, immuneToSlow, immuneToFire, 15, 5);
    }

    public void spawnMob(Match match, EntityType type, double speedMultiplier, double maxHealth, double armor, boolean immuneToSlow, boolean immuneToFire, int goldReward) {
        int xpReward = Math.max(1, goldReward / 3);
        spawnMob(match, "1", type, speedMultiplier, maxHealth, armor, immuneToSlow, immuneToFire, goldReward, xpReward);
    }

    public void spawnMob(Match match, String arena, EntityType type, double speedMultiplier, double maxHealth, double armor, boolean immuneToSlow, boolean immuneToFire, int goldReward) {
        int xpReward = Math.max(1, goldReward / 3);
        spawnMob(match, arena, type, speedMultiplier, maxHealth, armor, immuneToSlow, immuneToFire, goldReward, xpReward);
    }

    public void spawnMob(Match match, EntityType type, double speedMultiplier, double maxHealth, double armor, boolean immuneToSlow, boolean immuneToFire, int goldReward, int xpReward) {
        spawnMob(match, "1", type, speedMultiplier, maxHealth, armor, immuneToSlow, immuneToFire, goldReward, xpReward);
    }

    public void spawnMob(Match match, String arena, EntityType type, double speedMultiplier, double maxHealth, double armor, boolean immuneToSlow, boolean immuneToFire, int goldReward, int xpReward) {
        spawnMob(match, arena, type, speedMultiplier, maxHealth, armor, immuneToSlow, immuneToFire, goldReward, xpReward, type.name().toLowerCase());
    }

    public MobUpgradeRegistry getUpgradeRegistry() {
        return upgradeRegistry;
    }

    // Slot → chain index mapping for the main GUI: a centered 3x5 grid for the 15 mob chains.
    private static final int[] MOB_SLOTS = {11, 12, 13, 14, 15, 20, 21, 22, 23, 24, 29, 30, 31, 32, 33};

    /**
     * Chains shown in the spawner GUI. The standalone Endermite is hidden — players can no longer
     * select it individually; it only appears as the Tier 4-5 polymorph of the Silverfish chain.
     */
    private List<String> getGuiChains() {
        List<String> chains = new ArrayList<>(upgradeRegistry.getAvailableChains());
        chains.remove("endermite");
        return chains;
    }

    public String getChainForSlot(int slot) {
        List<String> chains = getGuiChains();
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

    public Mob spawnMob(Match match, String arena, EntityType type, double speedMultiplier, double maxHealth, double armor, boolean immuneToSlow, boolean immuneToFire, int goldReward, int xpReward, String presetKey) {
        java.util.Map<String, com.pauljang.towerDefense.data.TDWaypoint> graph = plugin.getWaypointConfigManager().getWaypointGraph(match, arena);
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

        // Mobs never push each other off the track or clump up.
        // Use scoreboard team collision rules instead of setCollidable(false) so projectiles can still hit.
        org.bukkit.scoreboard.Scoreboard scoreboard = org.bukkit.Bukkit.getScoreboardManager().getMainScoreboard();
        org.bukkit.scoreboard.Team tdMobTeam = scoreboard.getTeam("td_mobs");
        if (tdMobTeam == null) {
            tdMobTeam = scoreboard.registerNewTeam("td_mobs");
            tdMobTeam.setOption(org.bukkit.scoreboard.Team.Option.COLLISION_RULE, org.bukkit.scoreboard.Team.OptionStatus.NEVER);
        }
        tdMobTeam.addEntry(entity.getUniqueId().toString());

        // Force persistence so vanilla's despawn/garbage-collection never wipes a TD mob (Giants in
        // particular vanish otherwise, since their huge hitbox keeps them far from any player).
        entity.setRemoveWhenFarAway(false);
        entity.setPersistent(true);

        // Every TD mob is velocity-driven, so fully strip its vanilla AI goals. This stops Witches
        // from pausing to drink potions and Wardens from aggroing onto other mobs along the path.
        org.bukkit.Bukkit.getMobGoals().removeAllGoals(entity);

        // Scrub any random vanilla equipment (armor/weapons) so only CSV-defined equipment shows.
        if (entity.getEquipment() != null) {
            entity.getEquipment().clear();
        }

        // Mark as a TD Mob so we can handle events (like sunlight burning)
        entity.getPersistentDataContainer().set(TDKeys.MOB, PersistentDataType.BYTE, (byte) 1);

        // Store preset key
        entity.getPersistentDataContainer().set(TDKeys.PRESET, PersistentDataType.STRING, presetKey);

        // Store gold reward amount in container
        entity.getPersistentDataContainer().set(TDKeys.GOLD_REWARD, PersistentDataType.INTEGER, goldReward);

        // Store xp reward amount in container
        entity.getPersistentDataContainer().set(TDKeys.XP_REWARD, PersistentDataType.INTEGER, xpReward);

        // Store arena ID in container
        entity.getPersistentDataContainer().set(TDKeys.ARENA, PersistentDataType.STRING, arena);

        // Prevent zombification for nether mobs in the overworld
        if (entity instanceof org.bukkit.entity.Piglin piglin) {
            piglin.setImmuneToZombification(true);
        } else if (entity instanceof org.bukkit.entity.Hoglin hoglin) {
            hoglin.setImmuneToZombification(true);
        }

        // Set size if it's a Slime/Magma Cube. AI stays ENABLED: the movement task overrides the
        // entity's velocity every tick to push it along the track. setAI(false) removes the entity
        // from the horizontal-movement tick loop, so setVelocity() would be ignored and the mob
        // would freeze in place. Brain-AI mobs (Giant, Warden, Enderman, Zombified Piglin, Hoglin,
        // Zoglin, Breeze), Creepers, Magma Cubes, and Wither Skeletons are all driven the same way.
        if (entity instanceof org.bukkit.entity.Slime slime) {
            slime.setSize(4);
        }

        // Mark slow and fire immunities if requested or SLOW_SHIELD is active on the arena
        boolean isSlowShieldActive = plugin.getGameManager().isSpellActive(arena, "SLOW_SHIELD");
        if (immuneToSlow || isSlowShieldActive) {
            // Slow (Prismarine) and Freeze (Ice) now use distinct immunity keys; a slow-immune mob
            // resists both, so tag it with each.
            entity.getPersistentDataContainer().set(TDKeys.SLOW_IMMUNE, PersistentDataType.BYTE, (byte) 1);
            entity.getPersistentDataContainer().set(TDKeys.FREEZE_IMMUNE, PersistentDataType.BYTE, (byte) 1);
        }
        if (immuneToFire) {
            entity.getPersistentDataContainer().set(TDKeys.FIRE_IMMUNE, PersistentDataType.BYTE, (byte) 1);
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
                speedAttr.setBaseValue(base * speedMultiplier * GLOBAL_SPEED_COEFFICIENT);
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
        tdMob.setArena(arena);
        match.getActiveMobs().add(tdMob);
        return entity;
    }

    /**
     * Spawns a mob using the upgrade chain + tier from the registry,
     * applying equipment, mount, and flying attributes from the CSV profile.
     */
    public void spawnMobByChain(Match match, String arena, String upgradeChain, int tier) {
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
        // Flying mobs dodge ground-based slow effects; mobs with an ICE/SLOW immunity (e.g. Warden)
        // also fully resist Freeze spells and Ice Towers.
        boolean slowImmune = isFlying
                || profile.getImmunities().contains("ICE")
                || profile.getImmunities().contains("SLOW");

        // Kill gold reward = 10% of spawn price, minimum 1
        int killGold = Math.max(1, (int) (profile.getPrice() / 10.0));
        int xpReward = profile.getExpReward();

        // presetKey drives height-offset config lookup; use "flying" for flying mobs
        String presetKey = isFlying ? chain : chain.replace(" ", "_");

        // Flying mobs (e.g. Breeze) must hover like Blazes. Don't rely solely on a config.yml preset
        // key — many flying chains have none — so forcefully pin a 2.0 height-offset for this preset
        // before spawning. spawnMob() then raises the spawn point and the movement ticker keeps it
        // airborne via this same config value.
        if (isFlying) {
            plugin.getConfig().set("mobs." + presetKey + ".height-offset", 5.0);
        }

        Mob entity = spawnMob(match, arena, profile.getEntityType(), profile.getSpeed(), profile.getHp(),
                0.0, slowImmune, fireImmune, killGold, xpReward, presetKey);
        if (entity == null) return;

        // Override gravity for flying mobs so they don't sink toward the ground.
        if (isFlying) {
            entity.setGravity(false);
        }

        // Store tower immunities in PDC so TowerManager can check them
        if (!profile.getImmunities().isEmpty()) {
            String immunityStr = String.join(",", profile.getImmunities());
            entity.getPersistentDataContainer().set(
                TDKeys.IMMUNITIES,
                org.bukkit.persistence.PersistentDataType.STRING, immunityStr);
        }

        // Store the castle damage this mob deals when it reaches the end of the track.
        entity.getPersistentDataContainer().set(
            TDKeys.CASTLE_DAMAGE,
            org.bukkit.persistence.PersistentDataType.INTEGER, Math.max(1, (int) Math.round(profile.getDamage())));

        // Record this mob's tier on its TDMob wrapper (the most recent entry spawnMob appended).
        if (!match.getActiveMobs().isEmpty()) {
            TDMob spawned = match.getActiveMobs().get(match.getActiveMobs().size() - 1);
            if (spawned.getEntity().equals(entity)) {
                spawned.setTier(tier);
            }
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

        // Invisibility special mechanic (e.g. higher-tier Spiders). An invisible entity also hides
        // its own custom name (the health bar), so attach an invisible marker ArmorStand as a
        // passenger to carry a visible health bar; updateHealthBar() renders onto the passenger.
        if (profile.getSpecialMechanics().contains("Invisible")) {
            entity.addPotionEffect(new org.bukkit.potion.PotionEffect(
                    org.bukkit.potion.PotionEffectType.INVISIBILITY, Integer.MAX_VALUE, 0, false, false));

            org.bukkit.Location asLoc = entity.getLocation();
            if (asLoc.getWorld() != null) {
                org.bukkit.entity.ArmorStand nameTag = asLoc.getWorld().spawn(asLoc, org.bukkit.entity.ArmorStand.class, as -> {
                    as.setVisible(false);
                    as.setMarker(true);
                    as.setGravity(false);
                    as.setInvulnerable(true);
                    as.setPersistent(false);
                    as.setSmall(true);
                });
                entity.addPassenger(nameTag);
            }
        }

        // Spawn mount entity and set the mob as its passenger
        if (profile.getMountType() != null) {
            org.bukkit.Location spawnLoc = entity.getLocation();
            if (spawnLoc.getWorld() != null) {
                org.bukkit.entity.Entity mount = spawnLoc.getWorld().spawnEntity(spawnLoc, profile.getMountType());
                // Strip the mount's wander/combat goals so it doesn't fight the velocity override,
                // but keep its AI ENABLED so it physically moves when the tick loop sets its velocity.
                if (mount instanceof Mob mountMob) {
                    org.bukkit.Bukkit.getMobGoals().removeAllGoals(mountMob);
                }
                // Mark ALL mounts as TD mobs (prevents burning in sunlight AND protects from player damage)
                mount.getPersistentDataContainer().set(
                    TDKeys.MOB,
                    PersistentDataType.BYTE, (byte) 1);
                mount.addPassenger(entity);
            }
        }
    }

    /**
     * Spawns a fully-equipped Tier 1 Zombie at the Giant's current location and makes it inherit
     * the Giant's pathing state so it continues along the track from that position.
     *
     * ROOT CAUSE: Teleporting after spawn doesn't work - the teleport fails (likely due to world/chunk
     * validation issues when called from within the ticker).
     *
     * SOLUTION: Spawn the zombie directly at the Giant's location by manually creating it instead of
     * using spawnMobByChain which hardcodes waypoint 0 as the spawn point.
     */
    public void summonZombieAt(Match match, TDMob giant, String arena) {
        Mob giantEntity = giant.getEntity();
        if (giantEntity.isDead() || !giantEntity.isValid()) return;

        // Get the waypoint graph for this arena
        java.util.Map<String, com.pauljang.towerDefense.data.TDWaypoint> graph =
            plugin.getWaypointConfigManager().getWaypointGraph(match, arena);
        if (graph.isEmpty()) {
            plugin.getLogger().warning("Cannot summon zombie: No waypoints for arena " + arena);
            return;
        }

        // Spawn the zombie entity directly at the Giant's location
        Location spawnLoc = giantEntity.getLocation();
        if (spawnLoc.getWorld() == null) return;

        // Ensure chunk is loaded
        if (!spawnLoc.getChunk().isLoaded()) {
            spawnLoc.getChunk().load();
        }

        // Spawn a basic zombie
        org.bukkit.entity.Zombie zombie = (org.bukkit.entity.Zombie) spawnLoc.getWorld().spawnEntity(
            spawnLoc, EntityType.ZOMBIE);

        // Apply standard TD mob configuration
        zombie.setBaby(false);
        // Use scoreboard team collision rules instead of setCollidable(false) so projectiles can still hit
        org.bukkit.scoreboard.Scoreboard scoreboard = org.bukkit.Bukkit.getScoreboardManager().getMainScoreboard();
        org.bukkit.scoreboard.Team tdMobTeam = scoreboard.getTeam("td_mobs");
        if (tdMobTeam == null) {
            tdMobTeam = scoreboard.registerNewTeam("td_mobs");
            tdMobTeam.setOption(org.bukkit.scoreboard.Team.Option.COLLISION_RULE, org.bukkit.scoreboard.Team.OptionStatus.NEVER);
        }
        tdMobTeam.addEntry(zombie.getUniqueId().toString());
        zombie.setRemoveWhenFarAway(false);
        zombie.setPersistent(true);
        org.bukkit.Bukkit.getMobGoals().removeAllGoals(zombie);

        if (zombie.getEquipment() != null) {
            zombie.getEquipment().clear();
        }

        // Mark as TD mob
        zombie.getPersistentDataContainer().set(
            TDKeys.MOB, PersistentDataType.BYTE, (byte) 1);
        zombie.getPersistentDataContainer().set(
            TDKeys.PRESET, PersistentDataType.STRING, "zombie");
        zombie.getPersistentDataContainer().set(
            TDKeys.GOLD_REWARD, PersistentDataType.INTEGER, 2);
        zombie.getPersistentDataContainer().set(
            TDKeys.XP_REWARD, PersistentDataType.INTEGER, 7);
        zombie.getPersistentDataContainer().set(
            TDKeys.ARENA, PersistentDataType.STRING, arena);
        zombie.getPersistentDataContainer().set(
            TDKeys.CASTLE_DAMAGE, PersistentDataType.INTEGER, 1);

        // Set zombie stats (Tier 1 from CSV: HP=80, Speed=2.0)
        org.bukkit.attribute.AttributeInstance kbResist = zombie.getAttribute(Attribute.KNOCKBACK_RESISTANCE);
        if (kbResist != null) kbResist.setBaseValue(1.0);

        org.bukkit.attribute.AttributeInstance speedAttr = zombie.getAttribute(Attribute.MOVEMENT_SPEED);
        if (speedAttr != null) {
            double base = speedAttr.getBaseValue();
            if (base < 0.05) base = 0.25;
            speedAttr.setBaseValue(base * 2.0 * GLOBAL_SPEED_COEFFICIENT);
        }

        org.bukkit.attribute.AttributeInstance maxHealthAttr = zombie.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealthAttr != null) {
            maxHealthAttr.setBaseValue(80.0);
            zombie.setHealth(80.0);
        }

        // Initialize healthbar
        updateHealthBar(zombie);

        // Create TDMob wrapper with the Giant's state
        TDMob zombieTDMob = new TDMob(zombie, graph);
        zombieTDMob.setArena(arena);
        zombieTDMob.setTier(1);

        // Copy the Giant's pathing state EXACTLY
        String giantCurrentWaypoint = giant.getCurrentWaypointId();
        List<String> giantHistory = giant.getPathHistory();
        List<String> zombieHistory = new java.util.ArrayList<>(giantHistory);

        zombieTDMob.setPathHistory(zombieHistory);
        zombieTDMob.setCurrentWaypointId(giantCurrentWaypoint);

        // Add to active mobs - the zombie is already at the correct location
        match.getActiveMobs().add(zombieTDMob);
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

        // Status icons stack, each carrying its own distinct colour code.
        StringBuilder status = new StringBuilder();
        if (mob.getFireTicks() > 0) {
            status.append(" ").append(org.bukkit.ChatColor.GOLD).append("🔥");
        }
        // Poison is tracked via a PDC timestamp (undead mobs are immune to the vanilla POISON effect),
        // but still honour a real POISON effect if some other source applied one.
        boolean poisoned = mob.hasPotionEffect(org.bukkit.potion.PotionEffectType.POISON);
        if (!poisoned) {
            org.bukkit.NamespacedKey poisonedUntilKey = TDKeys.POISONED_UNTIL;
            if (mob.getPersistentDataContainer().has(poisonedUntilKey, PersistentDataType.LONG)) {
                long poisonUntil = mob.getPersistentDataContainer().get(poisonedUntilKey, PersistentDataType.LONG);
                if (System.currentTimeMillis() < poisonUntil) poisoned = true;
            }
        }
        if (poisoned) {
            status.append(" ").append(org.bukkit.ChatColor.DARK_GREEN).append("🤢");
        }
        // Freeze (Ice Tower, PDC key). The Ice Tower also natively applies SLOWNESS, so the snail is
        // suppressed while frozen to avoid showing both ❄ and 🐌 for the same effect.
        org.bukkit.NamespacedKey frozenKey = TDKeys.FROZEN_UNTIL;
        boolean isFrozen = false;
        if (mob.getPersistentDataContainer().has(frozenKey, PersistentDataType.LONG)) {
            long freezeEnd = mob.getPersistentDataContainer().get(frozenKey, PersistentDataType.LONG);
            if (System.currentTimeMillis() < freezeEnd) {
                status.append(" ").append(org.bukkit.ChatColor.AQUA).append("❄");
                isFrozen = true;
            }
        }
        // Slow (Prismarine, SLOWNESS effect) — only shown when the mob isn't already frozen.
        if (!isFrozen && mob.hasPotionEffect(org.bukkit.potion.PotionEffectType.SLOWNESS)) {
            status.append(" ").append(org.bukkit.ChatColor.BLUE).append("🐌");
        }
        // Vulnerable (Dripstone hazard tiles, +15% damage taken)
        org.bukkit.NamespacedKey vulnKey = TDKeys.VULNERABLE_UNTIL;
        if (mob.getPersistentDataContainer().has(vulnKey, PersistentDataType.LONG)) {
            long vulnUntil = mob.getPersistentDataContainer().get(vulnKey, PersistentDataType.LONG);
            if (System.currentTimeMillis() < vulnUntil) {
                status.append(" ").append(org.bukkit.ChatColor.LIGHT_PURPLE).append("💔");
            }
        }
        if (status.length() > 0) {
            bar.append(status);
        }

        // Invisible mobs (e.g. higher-tier Spiders) hide their own name, so an ArmorStand passenger
        // carries the visible health bar instead.
        org.bukkit.entity.ArmorStand nameHolder = null;
        for (org.bukkit.entity.Entity passenger : mob.getPassengers()) {
            if (passenger instanceof org.bukkit.entity.ArmorStand as) {
                nameHolder = as;
                break;
            }
        }
        if (nameHolder != null) {
            nameHolder.setCustomName(bar.toString());
            nameHolder.setCustomNameVisible(true);
            mob.setCustomNameVisible(false);
        } else {
            mob.setCustomName(bar.toString());
            mob.setCustomNameVisible(true);
        }
    }

    private void startMobTicker() {
        new BukkitRunnable() {
            private long tickCounter = 0;

            @Override
            public void run() {
                for (Match match : plugin.getGameManager().getActiveMatches()) {
                    Iterator<TDMob> iterator = match.getActiveMobs().iterator();
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
                        String mobArena = mob.getArena();
                        if (plugin.getGameManager().isSpellActive(match, mobArena, "DAMAGE_STORM")) {
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

                        // Poison damage-over-time.
                        org.bukkit.NamespacedKey poisonedUntilKey = TDKeys.POISONED_UNTIL;
                        if (mob.getEntity().getPersistentDataContainer().has(poisonedUntilKey, org.bukkit.persistence.PersistentDataType.LONG)) {
                            long poisonUntil = mob.getEntity().getPersistentDataContainer().get(poisonedUntilKey, org.bukkit.persistence.PersistentDataType.LONG);
                            if (System.currentTimeMillis() < poisonUntil) {
                                if (tickCounter % 20 == 0) {
                                    double pdmg = mob.getEntity().getPersistentDataContainer().getOrDefault(
                                            TDKeys.POISON_DAMAGE,
                                            org.bukkit.persistence.PersistentDataType.DOUBLE, 1.0);
                                    mob.getEntity().damage(pdmg);
                                    mob.getEntity().getWorld().spawnParticle(org.bukkit.Particle.WITCH,
                                            mob.getEntity().getLocation().add(0, 0.5, 0), 6, 0.25, 0.4, 0.25, 0.0);
                                }
                            } else {
                                mob.getEntity().getPersistentDataContainer().remove(poisonedUntilKey);
                            }
                        }

                        // Witches heal nearby allied mobs every 3 seconds
                        if (mob.getEntity() instanceof org.bukkit.entity.Witch witchHealer && tickCounter % 60 == 0) {
                            double healAmount = mob.getTier() * 10.0;
                            witchHealer.getWorld().spawnParticle(org.bukkit.Particle.HEART, witchHealer.getLocation().add(0, 2, 0), 3);
                            for (TDMob ally : match.getActiveMobs()) {
                                org.bukkit.entity.Mob allyEnt = ally.getEntity();
                                if (allyEnt == null || allyEnt.isDead() || !allyEnt.isValid()) continue;
                                if (!ally.getArena().equals(mob.getArena())) continue;
                                if (allyEnt.getLocation().distanceSquared(witchHealer.getLocation()) >= 25.0) continue;
                                org.bukkit.attribute.AttributeInstance maxHpAttr = allyEnt.getAttribute(Attribute.MAX_HEALTH);
                                if (maxHpAttr == null) continue;
                                double maxHp = maxHpAttr.getValue();
                                allyEnt.setHealth(Math.min(maxHp, allyEnt.getHealth() + healAmount));
                                allyEnt.getWorld().spawnParticle(org.bukkit.Particle.HAPPY_VILLAGER, allyEnt.getLocation().add(0, 1, 0), 5);
                            }
                        }

                        // Giants periodically summon a Tier 1 Zombie
                        if (mob.getEntity().getType() == EntityType.GIANT && tickCounter % 100 == 0) {
                            final TDMob giantMob = mob;
                            final String giantArena = mobArena;
                            final Match currentMatch = match;
                            org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> {
                                org.bukkit.entity.Mob giantEntity = giantMob.getEntity();
                                if (!giantEntity.isDead() && giantEntity.isValid()) {
                                    summonZombieAt(currentMatch, giantMob, giantArena);
                                }
                            });
                        }

                        handleMobMovement(match, mob, iterator, tickCounter);
                    }
                }
                tickCounter++;
            }
        }.runTaskTimer(plugin, 0L, 1L); // Run every tick
    }

    /**
     * All TD mobs are driven by manual velocity overrides rather than vanilla AI pathfinding, which
     * keeps every mob moving smoothly along the track regardless of entity type. Any entity carrying
     * the {@code td_mob} persistent-data key is treated as velocity-driven.
     */
    private boolean isVelocityDriven(org.bukkit.entity.Mob entity, double heightOffset) {
        return entity.getPersistentDataContainer().has(
                TDKeys.MOB,
                org.bukkit.persistence.PersistentDataType.BYTE);
    }

    private void handleMobMovement(Match match, TDMob mob, Iterator<TDMob> iterator, long currentTick) {
        // Skip movement if mob was recently teleported by Chorus Tower
        if (mob.isTeleported()) {
            mob.getEntity().setVelocity(new org.bukkit.util.Vector(0, 0, 0));
            return;
        }

        String mobArena = mob.getArena();

        // Witches will otherwise stop walking to drink potions — a hardcoded vanilla mechanic that
        // applies a heavy internal slowness while the drink animation plays. Continuously strip any
        // held potion and wipe the target so they never enter the drinking state.
        if (mob.getEntity() instanceof org.bukkit.entity.Witch witch) {
            org.bukkit.inventory.EntityEquipment eq = witch.getEquipment();
            if (eq != null) {
                org.bukkit.inventory.ItemStack mainHand = eq.getItemInMainHand();
                if (mainHand != null && mainHand.getType() == org.bukkit.Material.POTION) {
                    eq.setItemInMainHand(null);
                }
            }
            witch.setTarget(null);
        }

        // Freeze status check (applied by Ice Towers)
        boolean isFrozen = false;
        org.bukkit.NamespacedKey freezeUntilKey = TDKeys.FROZEN_UNTIL;
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
                        plugin.getGameManager().damageCastle(match, mobArena, 1);
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

        boolean isFreezeActive = plugin.getGameManager().isSpellActive(match, mobArena, "FREEZE");
        boolean isHasteActive = plugin.getGameManager().isSpellActive(match, mobArena, "HASTE_RUSH");
        boolean isSlowImmune = mob.getEntity().getPersistentDataContainer().has(
            TDKeys.SLOW_IMMUNE,
            org.bukkit.persistence.PersistentDataType.BYTE
        );

        double slowMult = 1.0;
        if (isFreezeActive && !isSlowImmune) {
            slowMult = plugin.getConfig().getDouble("spells.freeze.slow-multiplier", 0.7);
        }

        double hasteMult = 1.0;
        if (isHasteActive) {
            hasteMult = plugin.getConfig().getDouble("spells.haste-rush.speed-multiplier", 1.6);
        }

        String presetKey = mob.getEntity().getPersistentDataContainer().get(
            TDKeys.PRESET,
            org.bukkit.persistence.PersistentDataType.STRING
        );
        if (presetKey == null) presetKey = mob.getEntity().getType().name().toLowerCase();

        double heightOffset = plugin.getConfig().getDouble("mobs." + presetKey + ".height-offset", 0.0);

        // End of track: the mob reached the final waypoint (a node with no outgoing connections) —
        // the castle door. The mob stays alive and continuously sieges the castle on an attack
        // cooldown rather than despawning or draining health 20x/second.
        if (mob.hasReachedFinalWaypoint()) {
            // Creepers are suicide bombers: they detonate once for their (larger) damage and are
            // consumed, instead of standing at the door.
            if (mob.getEntity().getType() == EntityType.CREEPER) {
                Location loc = mob.getEntity().getLocation();
                loc.getWorld().spawnParticle(org.bukkit.Particle.EXPLOSION, loc, 1);
                loc.getWorld().playSound(loc, org.bukkit.Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 1.0f);
                plugin.getGameManager().damageCastle(match, mobArena, getCastleDamage(mob.getEntity()));

                org.bukkit.entity.Entity creeperVehicle = mob.getEntity().getVehicle();
                if (creeperVehicle != null) creeperVehicle.remove();
                for (org.bukkit.entity.Entity passenger : mob.getEntity().getPassengers()) {
                    if (passenger instanceof org.bukkit.entity.ArmorStand) passenger.remove();
                }
                // remove(), not setHealth(0)/damage(), so no death/reward event fires at the door.
                mob.getEntity().remove();
                iterator.remove();
                return;
            }

            // Stand still at the door — zero momentum on the mob and any vehicle it rides.
            mob.getEntity().setVelocity(new org.bukkit.util.Vector(0, 0, 0));
            if (mob.getEntity().getVehicle() instanceof org.bukkit.entity.Mob vehicleMob) {
                vehicleMob.setVelocity(new org.bukkit.util.Vector(0, 0, 0));
            }

            // Attack the castle once every 1.5 seconds with a visual/audio cue.
            long now = System.currentTimeMillis();
            if (now - mob.getLastCastleAttackTime() >= 1500L) {
                mob.setLastCastleAttackTime(now);
                plugin.getGameManager().damageCastle(match, mobArena, getCastleDamage(mob.getEntity()));
                mob.getEntity().getWorld().playSound(mob.getEntity().getLocation(),
                        org.bukkit.Sound.ENTITY_ZOMBIE_ATTACK_WOODEN_DOOR, 1.0f, 0.8f);
                mob.getEntity().swingMainHand();
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

        if (isVelocityDriven(mob.getEntity(), heightOffset)) {
            // Velocity-based movement: Brain AI mobs, velocity-only mobs, mounted mobs, and flying mobs
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

        // Check if they are close enough to the waypoint to target the next one. Use only the X/Z
        // plane: flying and tall mobs sit at a different Y than the node, so a 3D distance would
        // never collapse and they'd get stuck. A generous radius keeps fast mobs from overshooting
        // the node in a single tick.
        Location mobLoc = mob.getEntity().getLocation();
        double reachDistSq = Math.pow(mobLoc.getX() - targetLoc.getX(), 2)
                + Math.pow(mobLoc.getZ() - targetLoc.getZ(), 2);
        double reachThreshold = isVelocityDriven(mob.getEntity(), heightOffset) ? 0.5 : 0.25;
        if (reachDistSq < reachThreshold) {
            mob.advanceToNextWaypoint();
        }
    }

    /**
     * Castle damage a mob deals when it reaches the end of the track. Creepers use their configured
     * (larger) explosion damage; otherwise the value stored at spawn from the profile, defaulting to 1.
     */
    private int getCastleDamage(Mob entity) {
        if (entity.getType() == EntityType.CREEPER) {
            return plugin.getConfig().getInt("mobs.creeper.castle-damage", 5);
        }
        org.bukkit.NamespacedKey key = TDKeys.CASTLE_DAMAGE;
        if (entity.getPersistentDataContainer().has(key, org.bukkit.persistence.PersistentDataType.INTEGER)) {
            return entity.getPersistentDataContainer().get(key, org.bukkit.persistence.PersistentDataType.INTEGER);
        }
        return 1;
    }

    public void cleanup(Match match) {
        for (TDMob mob : match.getActiveMobs()) {
            // Remove any health-bar ArmorStand passenger (invisible mobs) so none are left floating.
            for (org.bukkit.entity.Entity passenger : mob.getEntity().getPassengers()) {
                if (passenger instanceof org.bukkit.entity.ArmorStand) {
                    passenger.remove();
                }
            }
            mob.getEntity().remove();
        }
        match.getActiveMobs().clear();
    }

    public void cleanup() {
        for (Match match : plugin.getGameManager().getActiveMatches()) {
            cleanup(match);
        }
    }

    public List<TDMob> getActiveMobs(Match match) {
        return match.getActiveMobs();
    }

    /**
     * Returns a combined list of all active mobs across all currently active matches.
     * Note: Modifications to this returned list (like .add() or .remove()) will NOT 
     * affect the underlying Match state. Use addActiveMob or removeActiveMob instead.
     */
    public List<TDMob> getActiveMobs() {
        List<TDMob> allMobs = new ArrayList<>();
        for (Match match : plugin.getGameManager().getActiveMatches()) {
            allMobs.addAll(match.getActiveMobs());
        }
        return allMobs;
    }

    /**
     * Removes a TDMob from whichever Match it currently belongs to.
     */
    public boolean removeActiveMob(TDMob mob) {
        for (Match match : plugin.getGameManager().getActiveMatches()) {
            if (match.getActiveMobs().remove(mob)) return true;
        }
        return false;
    }

    /**
     * Re-adds a TDMob to the Match corresponding to its current world.
     */
    public void addActiveMob(TDMob mob) {
        if (mob.getEntity() == null) return;
        org.bukkit.World world = mob.getEntity().getWorld();
        for (Match match : plugin.getGameManager().getActiveMatches()) {
            if (match.getWorld().equals(world)) {
                if (!match.getActiveMobs().contains(mob)) {
                    match.getActiveMobs().add(mob);
                }
                return;
            }
        }
    }

    // --- GUI & Queue System ---

    /** A single queued mob preserving its exact tier. */
    private record QueuedSpawn(String chain, int tier) {}

    /** Full queue view for a player: chain -> (tier -> count). Lazily created. */
    public Map<String, Map<Integer, Integer>> getQueueByTier(UUID uuid) {
        return playerQueues.computeIfAbsent(uuid, k -> new LinkedHashMap<>());
    }

    /** Total queued count for a chain, summed across all tiers. */
    public int getQueueTotal(UUID uuid, String chain) {
        Map<Integer, Integer> tiers = getQueueByTier(uuid).get(chain.toLowerCase());
        if (tiers == null) return 0;
        int total = 0;
        for (int c : tiers.values()) total += c;
        return total;
    }

    /** Queues one mob of the given chain at the exact tier selected. */
    public void addToQueue(UUID uuid, String chain, int tier) {
        Map<Integer, Integer> tiers = getQueueByTier(uuid)
                .computeIfAbsent(chain.toLowerCase(), k -> new java.util.TreeMap<>());
        tiers.merge(tier, 1, Integer::sum);
    }

    /**
     * Removes one queued mob of the chain (highest tier first) and returns the tier removed,
     * or -1 if the chain had nothing queued.
     */
    public int removeOneFromQueue(UUID uuid, String chain) {
        Map<Integer, Integer> tiers = getQueueByTier(uuid).get(chain.toLowerCase());
        if (tiers == null || tiers.isEmpty()) return -1;
        int topTier = java.util.Collections.max(tiers.keySet());
        int count = tiers.getOrDefault(topTier, 0);
        if (count <= 1) {
            tiers.remove(topTier);
        } else {
            tiers.put(topTier, count - 1);
        }
        return topTier;
    }

    /**
     * Removes one queued mob of the chain at the exact tier given. Returns true if one was removed,
     * false if none of that tier were queued.
     */
    public boolean removeFromQueue(UUID uuid, String chain, int tier) {
        Map<Integer, Integer> tiers = getQueueByTier(uuid).get(chain.toLowerCase());
        if (tiers == null) return false;
        int count = tiers.getOrDefault(tier, 0);
        if (count <= 0) return false;
        if (count <= 1) {
            tiers.remove(tier);
        } else {
            tiers.put(tier, count - 1);
        }
        return true;
    }

    /** Legacy overloads kept for any existing callers; default to tier 1. */
    public void addToQueue(UUID uuid, PresetMobType type) {
        addToQueue(uuid, type.name().toLowerCase(), 1);
    }

    /** Legacy overload kept for any existing callers. */
    public void removeFromQueue(UUID uuid, PresetMobType type) {
        removeOneFromQueue(uuid, type.name().toLowerCase());
    }

    public void clearQueue(UUID uuid) {
        getQueueByTier(uuid).clear();
    }

    public void clearAllQueues() {
        playerQueues.clear();
    }

    public void sendQueue(UUID uuid) {
        Map<String, Map<Integer, Integer>> queue = playerQueues.get(uuid);
        if (queue == null || queue.isEmpty()) return;

        // Flatten the queue into a spawn list preserving each mob's exact tier.
        List<QueuedSpawn> spawnList = new ArrayList<>();
        for (Map.Entry<String, Map<Integer, Integer>> chainEntry : queue.entrySet()) {
            for (Map.Entry<Integer, Integer> tierEntry : chainEntry.getValue().entrySet()) {
                for (int i = 0; i < tierEntry.getValue(); i++) {
                    spawnList.add(new QueuedSpawn(chainEntry.getKey(), tierEntry.getKey()));
                }
            }
        }

        if (spawnList.isEmpty()) return;

        // Determine opponent's arena
        String playerArena = plugin.getGameManager().getPlayerArena(uuid);
        String targetArena = playerArena.equals("1") ? "2" : "1";
        Match match = plugin.getGameManager().getPlayerMatch(uuid);
        if (match == null) return;

        // Spawn mobs spaced 20 ticks (1.0s) apart to space out the wave
        new BukkitRunnable() {
            int index = 0;

            @Override
            public void run() {
                if (index >= spawnList.size()) {
                    cancel();
                    return;
                }
                QueuedSpawn qs = spawnList.get(index);
                spawnMobByChain(match, targetArena, qs.chain(), qs.tier());
                index++;
            }
        }.runTaskTimer(plugin, 0L, 20L);

        // Reset the player's queue after spawning
        clearQueue(uuid);
    }

    public void openMobSpawnerGUI(Player player) {
        com.pauljang.towerDefense.ui.TDMenuHolder holder =
                new com.pauljang.towerDefense.ui.TDMenuHolder(com.pauljang.towerDefense.ui.TDMenuHolder.MenuType.MOB_SPAWNER);
        org.bukkit.inventory.Inventory gui = org.bukkit.Bukkit.createInventory(holder, 54, ChatColor.DARK_RED + "TD Mob Spawner");
        holder.setInventory(gui);
        Map<String, Map<Integer, Integer>> queue = getQueueByTier(player.getUniqueId());

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

        // Place all selectable mob chains in order (standalone Endermite is excluded)
        List<String> chains = getGuiChains();
        for (int i = 0; i < MOB_SLOTS.length && i < chains.size(); i++) {
            String chain = chains.get(i);
            int queuedCount = getQueueTotal(player.getUniqueId(), chain);
            gui.setItem(MOB_SLOTS[i], createChainGUIItem(player, chain, queuedCount));
        }

        // Compute total queue cost, total XP payout, and a per-tier breakdown for the Send Wave lore
        int totalCost = 0;
        int totalXpPayout = 0;
        List<String> queuedMobLines = new ArrayList<>();
        for (Map.Entry<String, Map<Integer, Integer>> chainEntry : queue.entrySet()) {
            for (Map.Entry<Integer, Integer> tierEntry : chainEntry.getValue().entrySet()) {
                int count = tierEntry.getValue();
                if (count <= 0) continue;
                int tier = tierEntry.getKey();
                MobStateProfile profile = upgradeRegistry.getProfile(chainEntry.getKey(), tier);
                if (profile == null) continue;
                totalCost += (int) profile.getPrice() * count;
                totalXpPayout += profile.getExpReward() * count;
                queuedMobLines.add(ChatColor.DARK_GRAY + "- " + ChatColor.YELLOW + "x" + count + " "
                        + ChatColor.WHITE + getChainDisplayName(chainEntry.getKey())
                        + ChatColor.GRAY + " (Lvl " + tier + ")");
            }
        }

        // Build the Send Wave lore: description, cost, the queued-mob breakdown, and total XP payout
        List<String> sendWaveLore = new ArrayList<>();
        sendWaveLore.add(ChatColor.GRAY + "Spawn all queued mobs on the opponent's track.");
        sendWaveLore.add(ChatColor.GOLD + "Total Cost: " + ChatColor.YELLOW + totalCost + " Gold");
        sendWaveLore.add("");
        if (queuedMobLines.isEmpty()) {
            sendWaveLore.add(ChatColor.GRAY + "Queued Mobs: " + ChatColor.DARK_GRAY + "(none)");
        } else {
            sendWaveLore.add(ChatColor.GRAY + "Queued Mobs:");
            sendWaveLore.addAll(queuedMobLines);
            sendWaveLore.add("");
            sendWaveLore.add(ChatColor.GREEN + "Total Payout: " + ChatColor.DARK_GREEN + "+" + totalXpPayout + " XP");
        }

        gui.setItem(38, createGUIItem(Material.RED_WOOL,
                ChatColor.RED + "Clear Queue",
                ChatColor.GRAY + "Dequeue all mobs and refund Gold."));
        gui.setItem(40, createGUIItem(Material.LIME_WOOL,
                ChatColor.GREEN + "Send Wave",
                sendWaveLore.toArray(new String[0])));
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
                    + "  Dmg: " + ChatColor.WHITE + profile.getDamage());
            lore.add(ChatColor.DARK_GRAY + "Speed: " + ChatColor.WHITE
                    + String.format("%.1f", profile.getSpeed()) + " Blocks/sec");
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
        lore.add(ChatColor.GREEN + "Click " + ChatColor.GRAY + "→ choose tier (queue & dequeue inside)");

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

    /** XP cost to unlock each tier. Tier 1 is always free. High tiers are intentionally expensive. */
    public static int getTierUnlockCost(int tier) {
        return switch (tier) {
            case 2 -> 400;
            case 3 -> 1200;
            case 4 -> 3000;
            case 5 -> 6500;
            default -> 0;
        };
    }

    public void openMobTierGUI(Player player, String chain) {
        String displayName = getChainDisplayName(chain);
        // Carry the canonical chain key on the holder so the click handler no longer has to
        // reverse it from the localized display name in the title.
        com.pauljang.towerDefense.ui.TDMenuHolder holder =
                new com.pauljang.towerDefense.ui.TDMenuHolder(com.pauljang.towerDefense.ui.TDMenuHolder.MenuType.MOB_TIER, chain);
        org.bukkit.inventory.Inventory gui = org.bukkit.Bukkit.createInventory(holder, 27,
                ChatColor.DARK_PURPLE + "Mob Tier: " + displayName);
        holder.setInventory(gui);

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
        // Readable ability explanation mapped from the raw special-mechanics string (no ugly raw text).
        String mech = profile.getSpecialMechanics();
        if (mech != null && !mech.isEmpty() && !mech.equals("None")) {
            List<String> explain = new ArrayList<>();
            if (mech.contains("Heals other mobs")) explain.add(ChatColor.GRAY + "- Heals nearby allies every 3 seconds (scales with tier)");
            if (mech.contains("Teleport Dodge")) explain.add(ChatColor.GRAY + "- 30% chance to dodge an attack and teleport");
            if (mech.contains("Deflects Projectiles") || mech.contains("High Deflection") || mech.contains("High Dodge Chance"))
                explain.add(ChatColor.GRAY + "- Frequently deflects or dodges projectiles");
            if (mech.contains("Flying")) explain.add(ChatColor.GRAY + "- Hovers above the track");
            if (mech.contains("Splits")) explain.add(ChatColor.GRAY + "- Splits into smaller slimes on death");
            if (mech.contains("Summons zombies")) explain.add(ChatColor.GRAY + "- Spawns zombies periodically");
            if (mech.contains("Regenerates")) explain.add(ChatColor.GRAY + "- Slowly regenerates its own health");
            if (mech.contains("Invisible")) explain.add(ChatColor.GRAY + "- Invisible; only Fire Towers can target it");
            if (!explain.isEmpty()) {
                lore.add(ChatColor.GOLD + "Ability Explanation:");
                lore.addAll(explain);
                lore.add("");
            }
        }
        if (!isUnlocked) {
            lore.add(ChatColor.RED + "Unlock Cost: " + ChatColor.YELLOW + getTierUnlockCost(tier) + " EXP");
            lore.add("");
        }
        lore.add(ChatColor.GOLD + "Cost: " + ChatColor.YELLOW + (int) profile.getPrice() + " Gold");
        lore.add("");

        // Raw static stats from the profile (no cross-tier comparisons).
        lore.add(ChatColor.RED + "HP: " + ChatColor.WHITE + (int) profile.getHp());
        lore.add(ChatColor.DARK_RED + "Damage: " + ChatColor.WHITE + profile.getDamage());
        lore.add(ChatColor.GREEN + "Speed: " + ChatColor.WHITE
                + String.format("%.1f", profile.getSpeed()) + " Blocks/sec");
        lore.add(ChatColor.LIGHT_PURPLE + "EXP Payout: " + ChatColor.WHITE + profile.getExpReward());
        lore.add("");

        boolean hasTraits = !profile.getImmunities().isEmpty() || profile.getMountType() != null
                || profile.getEquipment() != null;
        if (hasTraits) {
            if (!profile.getImmunities().isEmpty()) {
                lore.add(ChatColor.AQUA + "Immune: " + String.join(", ", profile.getImmunities()));
            }
            if (profile.getMountType() != null) {
                lore.add(ChatColor.YELLOW + "Mount: " + profile.getMountType().name());
            }
            if (profile.getEquipment() != null) {
                lore.add(ChatColor.YELLOW + "Equipment: " + profile.getEquipment().name());
            }
            lore.add("");
        }

        if (isUnlocked) {
            lore.add(ChatColor.GREEN + "Left-Click " + ChatColor.GRAY + "→ queue 1 at this tier");
            lore.add(ChatColor.GREEN + "Shift+Left " + ChatColor.GRAY + "→ queue 5 at this tier");
            lore.add(ChatColor.RED + "Right-Click " + ChatColor.GRAY + "→ dequeue 1 at this tier");
            lore.add(ChatColor.RED + "Shift+Right " + ChatColor.GRAY + "→ dequeue 5 at this tier");
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
