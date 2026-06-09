# Tower Defense Plugin: Advanced UI, Pathing, and Entity Persistence Fixes

Please review this document and implement the following code adjustments to resolve the GUI dequeueing logic, health bar symbol stacking, Giant despawns, and Giant-summoned Zombie pathing.

## 1. GUI Queue/Dequeue Logic Relocation & Shift-Click Support
**Files:** GUI Listener (e.g., `GUIListener.java` or wherever `InventoryClickEvent` is handled)
* **Issue:** Dequeueing is currently in the main Spawner GUI, and lacks bulk dequeue support. It should only be in the specific Mob Tier GUI.
* **Fix (Main Spawner GUI):** * Remove all logic that allows right-clicking or shift-clicking to remove mobs from the queue in the primary menu. The Main GUI should strictly navigate to the tier menus.
* **Fix (Mob Tier GUI):** * Implement the following comprehensive click-handling inside the specific Tier GUI:
    * `if (event.isShiftClick() && event.isLeftClick())` -> Queue 5 of this tier.
    * `else if (event.isLeftClick())` -> Queue 1 of this tier.
    * `else if (event.isShiftClick() && event.isRightClick())` -> Dequeue 5 of this tier (ensure queue doesn't drop below 0).
    * `else if (event.isRightClick())` -> Dequeue 1 of this tier.
  * Always trigger an immediate GUI refresh so the player sees the updated queue numbers.

## 2. Health Bar Symbol Uncapping (Fixing the 3-Symbol Limit)
**File:** `MobManager.java` (Inside `updateHealthBar()`)
* **Issue:** The health bar currently caps at around 3 status symbols, likely due to residual `else if` chains or premature string termination.
* **Fix:** Completely decouple every status check into its own isolated `if` statement. Do not use `else if` for any status effect.
  ```java
  StringBuilder symbols = new StringBuilder();
  
  // 1. Fire
  if (mob.getFireTicks() > 0) {
      symbols.append(ChatColor.GOLD).append("🔥");
  }
  
  // 2. Poison
  if (mob.hasPotionEffect(org.bukkit.potion.PotionEffectType.POISON)) {
      symbols.append(ChatColor.DARK_GREEN).append("🤢");
  }
  
  // 3. Freeze (from PersistentDataContainer)
  org.bukkit.NamespacedKey frozenKey = new org.bukkit.NamespacedKey(plugin, "td_frozen_until");
  if (mob.getPersistentDataContainer().has(frozenKey, org.bukkit.persistence.PersistentDataType.LONG)) {
      long freezeEnd = mob.getPersistentDataContainer().get(frozenKey, org.bukkit.persistence.PersistentDataType.LONG);
      if (System.currentTimeMillis() < freezeEnd) {
          symbols.append(ChatColor.AQUA).append("❄");
      }
  }
  
  // 4. Slow (from PotionEffect)
  if (mob.hasPotionEffect(org.bukkit.potion.PotionEffectType.SLOWNESS)) {
      symbols.append(ChatColor.BLUE).append("🐌"); 
  }
  
  // Combine base health string with all collected symbols
  String customName = baseHealthString + " " + symbols.toString();
  mob.getEntity().setCustomName(customName);
  ```

## 3. Giant Sudden Disappearance (Despawn Prevention)
**File:** `MobManager.java` (Inside `spawnMob()`)
* **Issue:** Giants randomly vanish. Because of their huge hitboxes and distance from players, vanilla Minecraft's garbage collector/despawn algorithm periodically wipes them if the chunk unloads or they step too far from a player.
* **Fix:** When spawning ANY mob (but crucially the Giant), force persistence flags on the entity.
  ```java
  entity.setRemoveWhenFarAway(false);
  
  // Optionally cast to living entity to ensure persistence
  if (entity instanceof org.bukkit.entity.LivingEntity living) {
      living.setPersistent(true);
  }
  ```

## 4. Giant's Summoned Zombie Perfect Pathing
**File:** `MobManager.java` (Inside the Giant's 100-tick zombie summon block)
* **Issue:** Zombies summoned by the Giant are ignoring the track, walking backward, or walking straight to the giant instead of following the node lines.
* **Fix:** To make the summoned zombie perfectly follow the waypoints starting from the Giant's current progression, you must deeply link their history.
  1. Call `spawnMobByChain()` or your equivalent to generate the Zombie.
  2. Immediately teleport the newly generated Zombie to the Giant's exact current location: `newZombieEntity.teleport(giantEntity.getLocation());`
  3. Retrieve the `TDMob` wrappers for both entities.
  4. Force the Zombie to inherit the Giant's exact pathing state:
     ```java
     zombieTDMob.setCurrentWaypointId(giantTDMob.getCurrentWaypointId());
     // Must create a deep copy of the history list so they don't share the same reference
     zombieTDMob.setPathHistory(new java.util.ArrayList<>(giantTDMob.getPathHistory()));
     ```
  5. By doing this, on the very next movement tick, the engine calculates the distance from the Zombie's physical location (at the Giant) to the Giant's target waypoint, forcing the Zombie to walk forward along the exact same track line.
