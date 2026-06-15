# Tower Defense Plugin: Advanced Bug Fixes & Polish (Batch 4)

Please implement the following fixes across the Java source code to resolve the remaining issues with the Chorus Tower, Giant zombie pathing, Witch AI, health bar symbols, and GUI dequeueing.

## 1. Chorus Tower Teleportation Rewrite
**File:** `TowerManager.java`
* **Issue:** Mobs are still acting erratically, turning around, or stopping completely.
* **Why:** Because we recently made *all* mobs velocity-driven, calling `target.getPathfinder().stopPathfinding()` and `moveTo()` creates fatal engine conflicts. The vanilla pathfinder is fighting our custom velocity loop.
* **Fix:** In the `CHORUS` case inside `shootTarget()`:
  1. Remove **all** code referencing `target.getPathfinder()`. Completely delete `stopPathfinding()` and `moveTo()`.
  2. After setting `tdMob.setCurrentWaypointId(newWpId);`, simply execute `target.teleport(teleportLoc);` and zero out the momentum with `target.setVelocity(new org.bukkit.util.Vector(0,0,0));`.
  3. Our custom `MobManager` movement loop will automatically detect the new location and waypoint on the very next tick and seamlessly resume pushing the mob forward.

## 2. Giant's Zombie Pathing Fix
**File:** `MobManager.java` (Inside the Movement Loop / Giant Summon Logic)
* **Issue:** Zombies spawned by the Giant walk off the path/track.
* **Why:** The Giant has a huge hitbox and may be physically offset from the exact center line of the track. If a zombie spawns at the Giant's exact offset coordinates, the directional vector to the next waypoint will pull the zombie diagonally, cutting corners and walking onto the grass.
* **Fix:** When spawning the Tier 1 Zombie for the Giant:
  1. Do not use the Giant's exact physical location. Instead, retrieve the location of the Giant's *current* or *previous* waypoint: `Location spawnLoc = tdMob.getWaypointGraph().get(tdMob.getCurrentWaypointId()).getLocation().clone();`
  2. Spawn the zombie at this perfectly centered track location.
  3. Apply `zombieTDMob.setCurrentWaypointId(giantTDMob.getCurrentWaypointId());` so they resume pathing perfectly aligned with the track vectors.

## 3. Health Bar Status Symbol Stacking & Coloring
**File:** `MobManager.java`
* **Issue:** The slow symbol is uncolored, and symbols overwrite each other instead of stacking.
* **Fix:** In `updateHealthBar()`, use a `StringBuilder` to dynamically append status icons with their own distinct color codes. 
* **Execution:**
  ```java
  StringBuilder symbols = new StringBuilder();
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
  } else if (mob.hasPotionEffect(org.bukkit.potion.PotionEffectType.SLOWNESS)) {
      symbols.append(ChatColor.MAGENTA).append("🐌");
  }
  
  String finalName = baseHealthString + " " + symbols.toString();
  ```

## 4. Witch Potion Drinking Prevention
**Files:** `MobManager.java` (Movement Loop) AND/OR `MobListener.java`
* **Issue:** Witches still stop walking to drink potions.
* **Why:** Drinking potions is a hardcoded vanilla survival mechanic that applies a massive internal slowness modifier to the entity while the drinking animation plays.
* **Fix:** Inside the `MobManager.java` 1-tick movement loop, actively strip the Witch of any potion items it tries to hold.
  ```java
  if (entity instanceof org.bukkit.entity.Witch witch) {
      org.bukkit.inventory.ItemStack mainHand = witch.getEquipment().getItemInMainHand();
      if (mainHand != null && mainHand.getType() == org.bukkit.Material.POTION) {
          witch.getEquipment().setItemInMainHand(null);
      }
      witch.setTarget(null); // Ensure target is wiped continually
  }
  ```

## 5. Mob Level GUI Dequeueing
**File:** GUI Listener (e.g., `GUIListener.java` or wherever `InventoryClickEvent` is handled for the Tier View)
* **Issue:** Players cannot dequeue mobs from inside the specific Mob Level View GUI.
* **Fix:** Update the `InventoryClickEvent` logic for the Tier GUI. Check the `ClickType`:
  * If `event.isLeftClick()`, execute the standard `addToQueue()` logic.
  * If `event.isRightClick()`, execute the `removeFromQueue()` logic.
  * Ensure the GUI dynamically refreshes immediately after to show the updated queue count for that specific tier.
