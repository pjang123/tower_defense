# Tower Defense Plugin: UI, Pathing, & Castle Siege Polish

Please implement the following fixes across the Java source code to resolve the queueing GUI, health bar symbols, Giant zombie pathing, and continuous castle siege mechanics.

## 1. Shift-Click Dequeue in Mob Spawning GUI
**Files:** GUI Listener (wherever `InventoryClickEvent` is handled for the Spawner Menu)
* **Issue:** Players cannot dequeue mobs using Shift-Click in the main spawning GUI.
* **Fix:** Update the `InventoryClickEvent` logic for the Spawner Menu. Add a condition: `if (event.isShiftClick() || event.isRightClick())`. When this triggers on a mob icon, execute the `removeFromQueue()` logic for that specific mob tier instead of `addToQueue()`. Ensure the GUI refreshes to display the updated queue count.

## 2. Health Bar Symbol Stacking
**File:** `MobManager.java`
* **Issue:** The slow symbol does not appear if other symbols are present because of an `else if` chain.
* **Fix:** In `updateHealthBar()`, ensure all status checks are independent `if` statements so the `StringBuilder` can append multiple icons simultaneously.
  ```java
  // Example Structure:
  if (mob.getFireTicks() > 0) {
      symbols.append(ChatColor.GOLD).append("🔥");
  }
  if (mob.hasPotionEffect(org.bukkit.potion.PotionEffectType.POISON)) {
      symbols.append(ChatColor.DARK_GREEN).append("🤢");
  }
  
  org.bukkit.NamespacedKey frozenKey = new org.bukkit.NamespacedKey(plugin, "td_frozen_until");
  if (mob.getPersistentDataContainer().has(frozenKey, org.bukkit.persistence.PersistentDataType.LONG)) {
      long freezeEnd = mob.getPersistentDataContainer().get(frozenKey, org.bukkit.persistence.PersistentDataType.LONG);
      if (System.currentTimeMillis() < freezeEnd) {
          symbols.append(ChatColor.AQUA).append("❄");
      }
  }
  
  // Independent IF statement, so it stacks with Fire/Poison/Freeze
  if (mob.hasPotionEffect(org.bukkit.potion.PotionEffectType.SLOWNESS)) {
      symbols.append(ChatColor.BLUE).append("🐌"); 
  }
  ```

## 3. Giant's Zombie Spawning (Track Projection)
**File:** `MobManager.java` (Inside the Giant zombie summon logic)
* **Issue:** Zombies spawn at the previous waypoint and walk towards the Giant, or spawn at the Giant and walk off-path because the Giant's hitbox is wide.
* **Fix:** Calculate the mathematically perfect center of the track relative to the Giant's exact progress using vector projection.
  * Retrieve the Giant's previous waypoint (`prevWp`) and current target waypoint (`nextWp`).
  * Apply this projection math to snap the spawn location to the track:
    ```java
    org.bukkit.util.Vector line = nextWp.toVector().subtract(prevWp.toVector());
    double lengthSq = line.lengthSquared();
    Location spawnLoc = giantEntity.getLocation().clone();
    
    if (lengthSq > 0) {
        org.bukkit.util.Vector pa = giantEntity.getLocation().toVector().subtract(prevWp.toVector());
        double t = Math.max(0, Math.min(1, pa.dot(line) / lengthSq));
        org.bukkit.util.Vector projected = prevWp.toVector().add(line.multiply(t));
        // Keep the Giant's Y (height) but snap X and Z to the perfect track line
        spawnLoc.setX(projected.getX());
        spawnLoc.setZ(projected.getZ());
    }
    ```
  * Spawn the Tier 1 Zombie at this calculated `spawnLoc`. Set its `currentWaypointId` to the Giant's `currentWaypointId` so it continues moving in the correct direction perfectly aligned with the path.

## 4. Add Attack Cooldown Tracking to TDMob
**File:** `src/main/java/com/pauljang/towerDefense/entities/TDMob.java`
* **Issue:** We need a way to track the attack cooldown for each individual mob so they don't drain the castle's health instantly (20 times a second) when they reach the end of the track.
* **Fix:** Add a new variable to track the last attack timestamp.
  ```java
  private long lastCastleAttackTime = 0L;

  public long getLastCastleAttackTime() {
      return lastCastleAttackTime;
  }

  public void setLastCastleAttackTime(long lastCastleAttackTime) {
      this.lastCastleAttackTime = lastCastleAttackTime;
  }
  ```

## 5. Continuous Attack Logic at the Castle Door
**File:** `src/main/java/com/pauljang/towerDefense/entities/MobManager.java` (Inside the 1-tick movement loop)
* **Issue:** Mobs need to stay alive and continuously siege the castle when they reach the final waypoint instead of disappearing or dying instantly.
* **Fix:** Locate the block where a mob reaches the final waypoint (where `nextIds` is empty or null). Replace that block with the following logic:
  1. Do **NOT** remove the mob from the `activeMobs` list (`iterator.remove()`). Leave them in the loop.
  2. Do **NOT** remove or kill the entity (`.remove()` or `.setHealth(0)`).
  3. Zero out their momentum so they stand directly at the castle door: `mob.getEntity().setVelocity(new org.bukkit.util.Vector(0, 0, 0));`
  4. Implement an attack cooldown (e.g., 1 attack every 1.5 seconds / 1500ms):
     ```java
     long now = System.currentTimeMillis();
     if (now - tdMob.getLastCastleAttackTime() >= 1500L) {
         // Apply damage to the castle
         plugin.getGameManager().damageCastle(targetArena, damageAmount);
         
         // Update the cooldown tracker
         tdMob.setLastCastleAttackTime(now);
         
         // Play a visual/audio cue that the mob is attacking the door
         mob.getEntity().getWorld().playSound(mob.getEntity().getLocation(), org.bukkit.Sound.ENTITY_ZOMBIE_ATTACK_WOODEN_DOOR, 1.0f, 0.8f);
         mob.getEntity().swingMainHand(); // Triggers the physical attack animation
     }
     ```
