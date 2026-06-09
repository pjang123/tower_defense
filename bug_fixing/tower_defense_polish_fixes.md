# Tower Defense Plugin: Polish & Critical Fixes for Claude Code

Please implement the following fixes and feature adjustments across the codebase.

## 1. Chorus Tower Teleportation Fix
**Files:** `TowerManager.java`
* **Issue:** Mobs are turning around and walking back instead of instantly teleporting.
* **Fix:** In the `CHORUS` case inside `shootTarget()`, after calculating `newTargetLocation`:
  1. Manually zero out the physical momentum: `target.setVelocity(new org.bukkit.util.Vector(0, 0, 0));`
  2. Set the rotation of `teleportLoc` to face the next waypoint direction.
  3. Execute `target.teleport(teleportLoc);`
  4. Explicitly call `target.getPathfinder().stopPathfinding();` before issuing the new `moveTo()` command.

## 2. Witch & Warden AI Fixes
**Files:** `MobManager.java`, `MobListener.java`
* **Issue:** Witches stop to drink potions, and Wardens aggro onto other mobs on the path.
* **Fix 1 (MobManager.java):** Because all mobs are now velocity-driven, fully strip their vanilla AI goals. Inside `spawnMob()`, add: `org.bukkit.Bukkit.getMobGoals().removeAllGoals(entity);`
* **Fix 2 (MobListener.java):** Listen for `EntityTargetEvent`. If the entity has the `td_mob` persistent data tag, immediately call `event.setCancelled(true);`. This prevents Wardens and Witches from locking onto targets.

## 3. Mob Spawning GUI Overhaul
**Files:** `MobManager.java`
* **Issue:** The Spawner GUI is cramped and doesn't utilize the 54-slot space well.
* **Fix:** Update the `MOB_SLOTS` array to cleanly center the 15 mobs in a 3x5 grid. 
  * Replace the array with: `private static final int[] MOB_SLOTS = {11, 12, 13, 14, 15, 20, 21, 22, 23, 24, 29, 30, 31, 32, 33};`
* **Issue:** The Mob Tier GUI shows weird `(+200 HP)` difference comparisons.
* **Fix:** Remove any dynamic comparison strings (like `(+X HP)` or `(+X DMG)`). Just display the raw static values from the profile.
* **Issue:** No description of the tier upgrade.
* **Fix:** In `createTierItem()`, prominently append `profile.getSpecialMechanics()` to the top of the lore, labeled as "Description: [mechanics]".

## 4. Unlock Queue Bug
**Files:** Event Listener (e.g., `GUIListener.java` or `TowerDefense.java`)
* **Issue:** Clicking to unlock a mob tier simultaneously queues the mob.
* **Fix:** In the `InventoryClickEvent` handling the Mob Tier GUI, separate the logic. If the player is spending EXP to unlock the tier, run the unlock logic and immediately `return;`. They should have to click again to queue it.

## 5. Breeze Flying Fix
**Files:** `MobManager.java`
* **Issue:** Breezes are not hovering in the air like Blazes.
* **Fix:** In `spawnMobByChain()`, if `isFlying` is true, forcefully override the `heightOffset` to `2.0` and execute `entity.setGravity(false);`. Do not rely solely on the `config.yml` preset key to determine flight height.

## 6. Slow vs. Freeze Disambiguation
**Files:** `TowerManager.java`, `MobManager.java`
* **Issue:** Slow and Freeze share the same immunities and UI symbols.
* **Fix 1 (TowerManager.java):** Ensure the Ice Tower checks for `td_freeze_immune` (instead of `td_slow_immune`). Prismarine Tower keeps `td_slow_immune`.
* **Fix 2 (MobManager.java):** In `updateHealthBar()`, render distinct symbols.
  * If the mob has the `td_frozen_until` PDC key, display `ChatColor.AQUA + "❄"`.
  * If the mob has a standard `SLOWNESS` potion effect (applied by Prismarine), display `ChatColor.GRAY + "🐌"`.

## 7. Giant Zombie Summoning
**Files:** `MobManager.java`
* **Issue:** The Giant tier does not actually spawn zombies.
* **Fix:** Inside the `startMobTicker()` loop, check if the active mob is a `GIANT`. If `tickCounter % 100 == 0` (every 5 seconds), programmatically spawn a Tier 1 Zombie at the Giant's location, assigning it to the same arena.

## 8. Invisible Spider Health Bars & Tower Targeting
**Files:** `MobManager.java`, `TowerManager.java`
* **Issue:** Tier 4 and 5 Spiders are invisible, which natively hides their custom name (health bar). 
* **Fix (MobManager.java):** If `profile.getSpecialMechanics().contains("Invisible")`, spawn an invisible, marker `ArmorStand` and add it as a passenger to the spider. In `updateHealthBar()`, if the mob has an ArmorStand passenger, update the passenger's custom name instead of the spider's.
* **Fix (TowerManager.java):** In `findTarget()`, if the evaluated mob possesses the `INVISIBILITY` potion effect (or the invisible mechanic flag), immediately `continue;` and skip targeting it *unless* the evaluating tower is `TowerType.FIRE`.

## 9. Gold/XP Message Restoration
**Files:** `GameManager.java`
* **Issue:** Players cannot see Gold/XP messages because Action Bar overrides or fails.
* **Fix:** Revert the pending Gold/XP logic to standard chat messages. In the 20-tick loop, check the pending maps, and send a consolidated chat message: `player.sendMessage(ChatColor.GOLD + "+" + pendingGold + " Gold | " + ChatColor.GREEN + "+" + pendingExp + " XP");`, then clear the maps.
