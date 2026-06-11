# Tower Defense Plugin: Comprehensive Bug Fixes & Polish (Batch 6)

Please implement the following fixes across the codebase to resolve queue logic, GUI lore, Witch/Warden AI, Chorus Tower teleportation, and Player Combat.

## 1. Matchmaking Queue Timer & /td forcestart
**File:** `src/main/java/com/pauljang/towerDefense/core/GameManager.java`
* **Issue:** Timer only starts when full. `/td forcestart` just starts the slow countdown instead of transporting.
* **Fix (Timer Start):** In `toggleQueue()`, change the condition for starting the timer to trigger when 2 players join: 
  `if (matchQueue.size() >= 2 && lobbyQueueTask == null) { startLobbyQueueCountdown(); }`
* **Fix (Timer Length):** Inside `startLobbyQueueCountdown()`, set `lobbyQueueSecondsLeft = 45;` (45 seconds to allow others to join comfortably).
* **Fix (/td forcestart):** Add a public method `public void forceStartMatch()`:
  ```java
  public void forceStartMatch() {
      if (matchQueue.size() < 2) return;
      if (lobbyQueueTask == null) startLobbyQueueCountdown();
      lobbyQueueSecondsLeft = 3; // Force transport in 3 seconds
  }
  ```
  *Update `src/main/java/com/pauljang/towerDefense/core/TDCommand.java` to call `plugin.getGameManager().forceStartMatch();` for the `"forcestart"` case.*

## 2. /td challenge "Game Ended" Screen Flash
**File:** `src/main/java/com/pauljang/towerDefense/core/GameManager.java`
* **Issue:** `startPvPMatch()` calls `setGameState(GameState.ENDED)`, which broadcasts the victory screen.
* **Fix:** Inside `startPvPMatch()`, replace `setGameState(GameState.ENDED);` with a silent cleanup routine:
  ```java
  // Silent cleanup instead of triggering ENDED state
  plugin.getMobManager().cleanup();
  plugin.getTowerManager().cleanup();
  cleanupSpells();
  cleanupCastleHolograms();
  ```

## 3. Chorus Tower Teleportation Fail
**File:** `src/main/java/com/pauljang/towerDefense/towers/TowerManager.java` (Inside `shootTarget()` -> `CHORUS` case)
* **Issue:** The tower fires, but the zombie doesn't move backwards. The complex vector math is failing to roll back the coordinate.
* **Fix:** Simplify the rollback logic to strictly walk back through the `pathHistory` list.
  Replace the complex distance math with this exact block:
  ```java
  int stepsBack = 3; // Number of nodes to roll back
  int targetIndex = Math.max(0, history.size() - 1 - stepsBack);
  
  String newWpId = history.get(targetIndex);
  com.pauljang.towerDefense.data.TDWaypoint newWp = graph.get(newWpId);
  org.bukkit.Location teleportLoc = newWp.getLocation().clone();
  
  // Clean up history tracking
  while (history.size() > targetIndex + 1) {
      history.remove(history.size() - 1);
  }
  tdMob.setCurrentWaypointId(newWpId);
  
  // Set orientation and execute jump
  teleportLoc.setDirection(target.getLocation().toVector().subtract(teleportLoc.toVector()));
  target.setVelocity(new org.bukkit.util.Vector(0, 0, 0));
  target.teleport(teleportLoc);
  ```

## 4. Ice Tower Slowness Symbol Bug
**File:** `src/main/java/com/pauljang/towerDefense/entities/MobManager.java` (Inside `updateHealthBar()`)
* **Issue:** Ice tower applies the freeze symbol (❄) but also triggers the slowness symbol (🐌) natively.
* **Fix:** Make the slow symbol an `else if` strictly attached to the freeze check, so a frozen mob doesn't also show the snail.
  ```java
  boolean isFrozen = false;
  if (mob.getPersistentDataContainer().has(frozenKey, org.bukkit.persistence.PersistentDataType.LONG)) {
      long freezeEnd = mob.getPersistentDataContainer().get(frozenKey, org.bukkit.persistence.PersistentDataType.LONG);
      if (System.currentTimeMillis() < freezeEnd) {
          symbols.append(org.bukkit.ChatColor.AQUA).append("❄");
          isFrozen = true;
      }
  }
  
  if (!isFrozen && mob.hasPotionEffect(org.bukkit.potion.PotionEffectType.SLOWNESS)) {
      symbols.append(org.bukkit.ChatColor.BLUE).append("🐌");
  }
  ```

## 5. Witch Potion Drinking & Healing Mechanics
**File 1 (MobListener.java):** * **Issue:** Witches still stop to drink potions natively.
* **Fix:** Listen to `EntityPotionEffectEvent`.
  ```java
  @org.bukkit.event.EventHandler
  public void onWitchDrink(org.bukkit.event.entity.EntityPotionEffectEvent event) {
      if (event.getEntity() instanceof org.bukkit.entity.Witch && event.getCause() == org.bukkit.event.entity.EntityPotionEffectEvent.Cause.WITCH_DRINK) {
          event.setCancelled(true);
      }
  }
  ```
**File 2 (MobManager.java):** * **Issue:** Witches don't heal allies.
* **Fix:** Inside the 1-tick movement loop, add a custom healing logic block for witches:
  ```java
  if (entity instanceof org.bukkit.entity.Witch && tickCounter % 60 == 0) { // Every 3 seconds
      double healAmount = tdMob.getTier() * 10.0; // Tier 1 heals 10, Tier 5 heals 50
      entity.getWorld().spawnParticle(org.bukkit.Particle.HEART, entity.getLocation().add(0, 2, 0), 3);
      for (com.pauljang.towerDefense.entities.TDMob ally : activeMobs) {
          if (ally.getArena().equals(tdMob.getArena()) && ally.getEntity().getLocation().distanceSquared(entity.getLocation()) < 25.0) {
              org.bukkit.entity.LivingEntity allyEnt = ally.getEntity();
              double maxHp = allyEnt.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue();
              allyEnt.setHealth(Math.min(maxHp, allyEnt.getHealth() + healAmount));
              allyEnt.getWorld().spawnParticle(org.bukkit.Particle.VILLAGER_HAPPY, allyEnt.getLocation().add(0, 1, 0), 5);
          }
      }
  }
  ```

## 6. Warden Aggression & Sonic Boom
**File:** `src/main/java/com/pauljang/towerDefense/listeners/MobListener.java`
* **Issue:** Wardens send sonic blasts at allied mobs on the path.
* **Fix:** Listen to `EntityDamageByEntityEvent`. If the damager is a Warden using a sonic boom, and the target is on the same team, cancel it.
  ```java
  if (event.getDamager() instanceof org.bukkit.entity.Warden damager) {
      if (damager.getPersistentDataContainer().has(new org.bukkit.NamespacedKey(plugin, "td_mob"), org.bukkit.persistence.PersistentDataType.BYTE)) {
          event.setCancelled(true); // Cancel all Warden native attacks to stop sonic booms
      }
  }
  ```

## 7. Giant Zombie Summoning Coordinates & Pathing
**File:** `src/main/java/com/pauljang/towerDefense/entities/MobManager.java` (Inside Giant's 100-tick summon logic)
* **Issue:** Zombies were walking off the path due to previous vector projection, or walking back to the Giant.
* **Fix:** Spawn directly on the Giant's exact physical location, but retain the exact path history inheritance. The custom movement loop automatically handles offsets by pulling them straight toward the next waypoint.
  ```java
  org.bukkit.Location spawnLoc = giantEntity.getLocation().clone();
  // Spawn the zombie...
  // Inherit exactly:
  zombieTDMob.setCurrentWaypointId(giantTDMob.getCurrentWaypointId());
  zombieTDMob.setPathHistory(new java.util.ArrayList<>(giantTDMob.getPathHistory()));
  ```

## 8. Player Bow Does No Damage
**File:** `src/main/java/com/pauljang/towerDefense/listeners/MobListener.java` (Inside `EntityDamageByEntityEvent`)
* **Issue:** The player's custom arrows deal no damage to mobs.
* **Fix:** Find the logic that blocks friendly fire or native entity damage on TD mobs. Add an explicit exception to ALLOW damage if the damager is a `Projectile` shot by a `Player`.
  ```java
  if (event.getDamager() instanceof org.bukkit.entity.Projectile proj) {
      if (proj.getShooter() instanceof org.bukkit.entity.Player) {
          return; // Let the player's arrow damage the mob natively
      }
  }
  ```

## 9. GUI Explanations & Upgrade Lore
**File:** `src/main/java/com/pauljang/towerDefense/entities/MobManager.java` (GUI Generation)
* **Issue:** Raw mechanics strings are ugly. Needs readable explanations.
* **Fix:** When generating the Lore for the Tier items, map the `Special Mechanics` string to readable explanations and remove generic "Description:" text.
  ```java
  String mech = profile.getSpecialMechanics();
  if (mech != null && !mech.equals("None")) {
      lore.add(org.bukkit.ChatColor.GOLD + "Ability Explanation:");
      if (mech.contains("Heals other mobs")) lore.add(org.bukkit.ChatColor.GRAY + "- Heals nearby allies every 3 seconds (scales with tier)");
      if (mech.contains("Teleport Dodge")) lore.add(org.bukkit.ChatColor.GRAY + "- 30% chance to dodge and teleport");
      if (mech.contains("Flying")) lore.add(org.bukkit.ChatColor.GRAY + "- Hovers above the track");
      if (mech.contains("Splits")) lore.add(org.bukkit.ChatColor.GRAY + "- Splits into smaller slimes on death");
      if (mech.contains("Summons zombies")) lore.add(org.bukkit.ChatColor.GRAY + "- Spawns zombies periodically");
      lore.add("");
  }
  ```
