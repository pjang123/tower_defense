# Tower Defense Plugin Update Instructions
Please read this document carefully and implement the following changes across the Java source files and CSV data file. 

## 1. GameManager.java (`src/main/java/com/pauljang/towerDefense/core/GameManager.java`)
**Adjustments to make:**
1. **Remove Compass on Start**: In the `resetPlayerForMatch` and `giveStarterWeapons` methods, ensure you explicitly remove the lobby compass: `player.getInventory().remove(org.bukkit.Material.COMPASS);`.
2. **Action Bar Message Stacking**: To stop chat flooding, remove `player.sendMessage` for Gold and XP gains. Instead, add two maps: `private final Map<UUID, Integer> pendingGold = new HashMap<>();` and `private final Map<UUID, Integer> pendingExp = new HashMap<>();`. In `addGold` and `addExp`, add the amounts to these maps. Then, inside the main `runTaskTimer` (the 20L one in the constructor), loop through online players, check if they have pending gold/exp, send a combined `player.sendActionBar(ChatColor.GOLD + "+" + gold + " Gold | " + ChatColor.GREEN + "+" + exp + " XP")`, and clear their pending amounts.
3. **Dynamic Spell Pricing**: To make spell prices double if clicked successively within 1 second (and reset after 3 seconds), add tracking maps: `private final Map<UUID, Map<String, Long>> lastSpellCast = new HashMap<>();` and `private final Map<UUID, Map<String, Integer>> spellCostMultiplier = new HashMap<>();`. Update `openUpgradesGUI` and `castSpell` / GUI click handlers to calculate the temporary cost multiplier based on `System.currentTimeMillis()`.
4. **8 Player Support (4v4)**: In `toggleQueue()`, change the maximum queue size check from `2` to `8`. In `handleCountdown()`, alternate team assignments: `String assignedArena = (count % 2 == 0) ? "1" : "2";`.
5. **Spectator Forfeit Bug Fix**: In `handlePlayerDisconnect()`, before executing forfeit logic, verify the player is actually in the match: `if (!matchQueue.contains(player.getUniqueId())) return;`.
6. **Forfeit Method**: Create a new public method `public void forfeit(Player player)` that sets their arena's health to 0 and ends the game.

## 2. TDCommand.java (`src/main/java/com/pauljang/towerDefense/core/TDCommand.java`)
**Adjustments to make:**
1. **New Commands**: Add cases for `"lobby"`, `"forfeit"`, and `"forcestart"`.
    * `/td lobby`: If the game is active and they are playing, warn them to use `/td forfeit`. Otherwise, teleport them to the lobby world and remove them from the queue.
    * `/td forfeit`: If in an active game, call `gameManager.forfeit(player);`.
    * `/td forcestart`: Allows players (or admins) to force the lobby countdown to start if there are at least 2 players in the queue, fulfilling the request to start games before the 8-player maximum is reached. Call `gameManager.startLobbyQueueCountdown();`.

## 3. MobListener.java (`src/main/java/com/pauljang/towerDefense/listeners/MobListener.java`)
**Adjustments to make:**
1. **Skeleton Horse Despawn Bug**: In `onMobDeath()`, check if the dying mob has a vehicle. If it does (e.g., Skeleton dying on a Skeleton Horse), kill/remove the vehicle as well: `if (entity.getVehicle() != null) entity.getVehicle().remove();`.
2. **Enderman Dodging**: In `onMobDamage()`, add logic: `if (mob.getType() == EntityType.ENDERMAN) { if (Math.random() < 0.3) { event.setCancelled(true); mob.getWorld().spawnParticle(Particle.PORTAL, mob.getLocation(), 20); mob.getWorld().playSound(mob.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1f); return; } }`
3. **Queue Override Logic (Charge Difference)**: In `onInventoryClick` where the player selects a Mob Tier (around line 430), implement the requested queue override. If they queue a Lvl 5 zombie while having 5 Lvl 1s queued, they should end up with 6 Lvl 5 zombies. 
   * Calculate the cost difference: `int upgradeCost = (newTierPrice - oldTierPrice) * existingQueuedCount;`
   * `int totalCost = newTierPrice + Math.max(0, upgradeCost);`
   * Charge `totalCost`. If successful, the `setMobTier` call already globally updates all queued mobs of that chain to the new tier.

## 4. MobManager.java (`src/main/java/com/pauljang/towerDefense/entities/MobManager.java`)
**Adjustments to make:**
1. **Silverfish Movement**: In the `isVelocityDriven()` method, add `t == EntityType.SILVERFISH || t == EntityType.ENDERMITE` so they aren't frozen by broken vanilla pathfinding.
2. **Spider Invisibility**: In `spawnMobByChain()`, check if the CSV's special mechanics contain `"Invisible"`. If so, apply `new PotionEffect(PotionEffectType.INVISIBILITY, Integer.MAX_VALUE, 0, false, false)` to the spawned entity.
3. **Space Out Mob Spawns**: In `sendQueue()`, change the `runTaskTimer` interval from `10L` (0.5s) to `20L` (1.0s) to space out the mob wave.

## 5. TowerManager.java (`src/main/java/com/pauljang/towerDefense/towers/TowerManager.java`)
**Adjustments to make:**
1. **Tower Owner Name & Naming Elements**: 
   * Update the `updateHologram()` method to prepend the owner's name to the tower title. Fetch `Bukkit.getOfflinePlayer(tower.getOwnerId()).getName()`. Example: `ChatColor.GOLD + playerName + "'s " + tower.getType().getDisplayName()`.
   * Apply the same naming logic to `tower.getSpawnedGolem()` and `tower.getSpawnedGhast()` when they are spawned in `buildTowerStructure()`.
2. **Disabled Towers Obviousness**: In the main ticker, if `tower.isDisabled()` is true, update the hologram's top line to explicitly say `ChatColor.RED + "" + ChatColor.BOLD + "[DISABLED EMP]"` and spawn large smoke/redstone particles around the tower.
3. **EMP Verification**: Ensure `tower.setDisabledUntil()` correctly bypasses the attack logic (this is mostly already there, just ensure the return logic skips firing).
4. **Iron Golem Pathing to Mobs**: In the ticker, remove the complex waypoint pathing logic for `TowerType.GOLEM`. Replace it with logic that uses `findTarget(tower)` to locate the nearest mob on the track within its range. If a target is found, `golem.getPathfinder().moveTo(target.getLocation(), 1.25);`. If no target is found, `golem.getPathfinder().moveTo(tower.getCenterLocation(), 1.0);`.
5. **Breeze Immune to Archer**: In `shootTarget()`, add `if (tower.getType() == TowerType.ARCHER && target.getType() == EntityType.BREEZE) { start.getWorld().playSound(target.getLocation(), Sound.ITEM_SHIELD_BLOCK, 1f, 1f); return; }` to prevent damage.
6. **Players Stuck in Towers**: At the end of `buildTowerStructure()`, check for any players within the tower's bounding box (`getNearbyEntities`). If a player is found intersecting the blocks, teleport them to `center.clone().add(0, sizeY + 1, 0)` (the top of the tower).
7. **Poison Tag Height**: In `updateHologram()`, ensure `holoHeight` is calculated dynamically or hardcoded slightly lower for the Poison Tower, as its structure might lack a bounding box height property.

## 6. Tower.java & TowerType.java
**Adjustments to make:**
1. **Tower Names**: In `TowerType.java`, rename the display names to match the new mappings: 
   * `Mage Tower` -> `Fire Tower` (Already done in source, verify integrity).
   * `Artillery Tower` -> `Archer Tower`.
   * `Quake Tower` -> `Ice Tower`.

## 7. mob_upgrades_polymorphic.csv (`mob_upgrades_polymorphic.csv`)
**Adjustments to make:**
Replace the entire contents of the CSV file with the following text. This applies the requested HP buffs (x2.0), Price adjustments (x1.5), and renames the Immunities (MAGE->FIRE, ARTILLERY->ARCHER, QUAKE->ICE).

```csv
Upgrade Chain,Tier,Entity Type,Price,Damage,HP,Speed,EXP Reward,Immunities,Mount,Equipment,Special Mechanics
Zombie,1,ZOMBIE,22,1.0,80,2.0,7,None,None,None,None
Zombie,2,ZOMBIE,27,1.0,112,2.0,9,None,None,LEATHER_HELMET,None
Zombie,3,ZOMBIE,33,1.0,156,2.0,10,None,None,CHAINMAIL_HELMET,None
Zombie,4,ZOMBIE,39,1.5,220,2.0,13,None,None,IRON_HELMET,None
Zombie,5,ZOMBIE,46,2.0,308,1.6,30,None,None,DIAMOND_HELMET,None
Spider,1,SPIDER,37,1.0,160,2.0,30,None,None,None,None
Spider,2,SPIDER,45,1.0,224,2.0,32,None,None,None,None
Spider,3,CAVE_SPIDER,54,1.5,314,1.6,36,None,None,None,None
Spider,4,CAVE_SPIDER,64,1.0,444,1.6,38,None,None,None,Invisible
Spider,5,CAVE_SPIDER,78,1.5,614,1.6,44,None,None,None,Invisible
Skeleton,1,SKELETON,90,1.0,700,1.6,60,None,None,None,None
Skeleton,2,SKELETON,216,1.0,980,1.2,66,None,ZOMBIE_HORSE,None,None
Skeleton,3,SKELETON,259,1.0,1372,1.2,72,None,SKELETON_HORSE,None,None
Skeleton,4,WITHER_SKELETON,310,2.0,1920,1.2,80,"FIRE",ZOMBIE_HORSE,None,None
Skeleton,5,WITHER_SKELETON,373,2.5,2690,0.9,80,"FIRE",SKELETON_HORSE,None,None
Creeper,1,CREEPER,450,1.0,900,1.2,45,None,None,None,None
Creeper,2,CREEPER,540,1.0,1260,1.2,49,None,None,None,Regenerates Health
Creeper,3,CREEPER,648,1.0,1764,1.2,54,None,PIG,None,Regenerates Health
Creeper,4,CREEPER,777,1.8,2470,0.9,60,None,COW,None,Regenerates Health
Creeper,5,CREEPER,933,2.5,3458,0.9,66,None,None,None,"Spawns in Charged state, Regenerates Health"
Silverfish,1,SILVERFISH,120,1.0,360,1.6,18,"ARCHER, POISON, ICE",None,None,Flying
Silverfish,2,SILVERFISH,144,1.0,504,1.6,19,"ARCHER, POISON, ICE",None,None,Flying
Silverfish,3,SILVERFISH,172,1.0,706,1.6,19,"ARCHER, POISON, ICE",None,None,Flying
Silverfish,4,ENDERMITE,207,2.5,822,1.2,21,"ARCHER, POISON, ICE",None,None,Flying
Silverfish,5,ENDERMITE,249,3.0,1150,1.2,22,"ARCHER, POISON, ICE",None,None,Flying
Blaze,1,BLAZE,750,1.0,820,1.2,105,"FIRE, ARCHER, POISON, ICE",None,None,Flying
Blaze,2,BLAZE,900,1.0,1148,1.2,111,"FIRE, ARCHER, POISON, ICE",None,None,Flying
Blaze,3,BLAZE,1080,1.0,1608,1.2,115,"FIRE, ARCHER, POISON, ICE",None,None,Flying
Blaze,4,BLAZE,1296,1.5,2250,0.9,121,"FIRE, ARCHER, POISON, ICE",None,None,Flying
Blaze,5,BLAZE,1555,2.0,3150,0.9,127,"FIRE, ARCHER, POISON, ICE",None,None,Flying
Zombie Pigman,1,ZOMBIFIED_PIGLIN,75,1.0,300,1.6,44,"FIRE",None,None,None
Zombie Pigman,2,ZOMBIFIED_PIGLIN,180,1.0,420,1.6,48,"FIRE",None,GOLDEN_SWORD,None
Zombie Pigman,3,ZOMBIFIED_PIGLIN,216,1.0,594,1.6,50,"FIRE",None,STONE_SWORD,None
Zombie Pigman,4,ZOMBIFIED_PIGLIN,259,1.0,824,1.2,50,"FIRE",None,IRON_SWORD,None
Zombie Pigman,5,ZOMBIFIED_PIGLIN,310,1.5,1152,1.2,54,"FIRE",None,DIAMOND_SWORD,None
Witch,1,WITCH,225,1.0,600,1.6,37,None,None,None,Heals other mobs
Witch,2,WITCH,247,1.0,840,1.2,39,None,None,None,Heals other mobs
Witch,3,WITCH,273,1.0,1176,1.2,42,None,None,None,Heals other mobs
Witch,4,WITCH,300,1.5,1646,1.2,43,None,None,None,Heals other mobs
Witch,5,WITCH,330,2.0,2304,0.9,45,None,None,None,Heals other mobs
Slime,1,SLIME,2250,1.0,1600,1.2,150,None,None,None,Splits 1x on death
Slime,2,SLIME,1500,1.5,1760,1.2,157,None,None,None,Splits 2x on death
Slime,3,SLIME,3240,2.0,1936,1.2,165,None,None,None,Splits 3x on death
Slime,4,MAGMA_CUBE,3888,2.5,2130,0.9,174,"FIRE",None,None,Splits 4x on death
Slime,5,MAGMA_CUBE,4665,3.0,2342,0.9,183,"FIRE",None,None,Splits 5x on death
Giant,1,GIANT,7500,10.0,6000,0.6,300,"ICE",None,None,"Summons zombies, Regenerates"
Giant,2,GIANT,8625,20.0,8400,0.6,306,"ICE",None,LEATHER_HELMET,"Summons zombies, Regenerates"
Giant,3,GIANT,9919,30.0,11776,0.6,312,"ICE",None,CHAINMAIL_HELMET,"Summons zombies, Regenerates"
Giant,4,GIANT,11406,40.0,16464,0.6,318,"ICE",None,IRON_HELMET,"Summons zombies, Regenerates"
Giant,5,GIANT,13117,50.0,23050,0.6,324,"ICE",None,DIAMOND_HELMET,"Summons zombies, Regenerates"
Warden,1,WARDEN,6000,5.0,5000,0.9,250,None,None,None,High health tank
Warden,2,WARDEN,6900,10.0,7000,0.6,260,None,None,None,Increased damage resistance
Warden,3,WARDEN,7935,15.0,9800,0.6,270,None,None,None,Sonic boom attack potential
Warden,4,WARDEN,9124,20.0,13720,0.6,280,None,None,None,Heavy tank
Warden,5,WARDEN,10492,25.0,19200,0.6,290,None,None,None,Unstoppable force
Ravager,1,RAVAGER,3000,3.0,2400,0.9,180,None,None,None,Heavy beast
Ravager,2,RAVAGER,3600,4.0,3360,0.9,190,None,None,None,Armored plates
Ravager,3,RAVAGER,4320,5.0,4704,0.9,200,None,None,None,Roar stun resistance
Ravager,4,RAVAGER,5184,6.0,6584,0.6,210,None,None,None,Siege beast
Ravager,5,RAVAGER,6220,7.0,9220,0.6,220,None,None,None,Elite siege beast
Hoglin,1,HOGLIN,900,2.0,1000,1.2,80,None,None,None,Brutal charge
Hoglin,2,HOGLIN,1080,3.0,1400,1.2,85,None,None,None,Knockback resistance
Hoglin,3,HOGLIN,1296,4.0,1960,1.2,90,None,None,None,Fierce tusks
Hoglin,4,HOGLIN,1554,5.0,2744,0.9,95,None,None,None,Enraged
Hoglin,5,ZOGLIN,1864,6.0,3840,0.9,100,"FIRE",None,None,Fire resistant
Enderman,1,ENDERMAN,1200,2.0,1200,1.2,100,"CHORUS",None,None,Teleport Dodge
Enderman,2,ENDERMAN,1440,2.5,1680,1.2,110,"CHORUS",None,None,Teleport Dodge
Enderman,3,ENDERMAN,1728,3.0,2352,0.9,120,"CHORUS",None,None,Teleport Dodge
Enderman,4,ENDERMAN,2073,3.5,3292,0.9,130,"CHORUS",None,None,High Dodge Chance
Enderman,5,ENDERMAN,2487,4.0,4600,0.9,140,"CHORUS",None,None,High Dodge Chance
Endermite,1,ENDERMITE,150,1.0,300,1.6,20,"CHORUS",None,None,Swarm
Endermite,2,ENDERMITE,180,1.5,420,1.6,22,"CHORUS",None,None,Swarm
Endermite,3,ENDERMITE,216,2.0,588,1.6,24,"CHORUS",None,None,Swarm
Endermite,4,ENDERMITE,258,2.5,822,1.2,26,"CHORUS",None,None,Swarm
Endermite,5,ENDERMITE,309,3.0,1150,1.2,28,"CHORUS",None,None,Swarm Leader
Breeze,1,BREEZE,1125,1.5,1100,1.2,90,None,None,None,"Hovering, Fast"
Breeze,2,BREEZE,1350,2.0,1540,1.2,95,None,None,None,"Hovering, Fast"
Breeze,3,BREEZE,1620,2.5,2156,0.9,100,None,None,None,Deflects Projectiles
Breeze,4,BREEZE,1944,3.0,3018,0.9,105,None,None,None,High Deflection
Breeze,5,BREEZE,2332,3.5,4224,0.9,110,None,None,None,Storm Conjurer