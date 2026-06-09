# Tower Defense Plugin: Comprehensive Update & Fixes Guide for Claude Code

Please review this document and implement the following adjustments and bug fixes across the Java source code and the CSV configuration file.

## 1. Queueing Mechanics & Mixed Mob Levels
**Files:** `MobManager.java`
* **Issue:** Currently, queueing a mob uses the universally selected tier. Queueing a Level 5 overrides previously queued Level 1s.
* **Fix:** Refactor `playerQueues` from `Map<UUID, Map<String, Integer>>` to track specific tiers, such as `Map<UUID, List<QueuedMob>>` or `Map<UUID, Map<String, Map<Integer, Integer>>>`.
* **Execution:** Update `addToQueue`, `removeFromQueue`, `clearQueue`, and `sendQueue` so that the exact tier the player has selected when they click "Queue" is preserved and spawned. The spawner GUI should accurately display the total counts.

## 2. Universal Velocity Movement & Collision
**Files:** `MobManager.java`
* **Velocity Overrides:** Make *all* TD mobs use the custom velocity-driven movement instead of vanilla AI pathfinding. In `isVelocityDriven()`, simply return `true` for any entity that possesses the `td_mob` persistent data key. 
* **Collision Removal:** Inside `spawnMob()`, explicitly set `entity.setCollidable(false);` so mobs do not push each other off the track or get clumped up.

## 3. Spell Cooldowns & Dynamic Cost Scaling
**Files:** `GameManager.java`
* **Global Cooldown:** Implement a strict 0.5-second (500ms) global cooldown between *any* spell usage per player to prevent spamming.
* **Dynamic Pricing:** If a spell is clicked multiple times in succession, double its Gold cost for each subsequent click. 
* **Reset Timer:** The doubled cost should reset back to its baseline price if the player does not cast that specific spell for 10 seconds. Track timestamps and current multiplier per player, per spell.

## 4. Spell Adjustments (Overcharge & Freeze)
**Files:** `GameManager.java`, `config.yml` (if applicable)
* **Overcharge:** Change the attack speed boost percentage from 50% to **15%**. (Modify the cooldown reduction math in `TowerManager.java` from `cooldown / 2` to `(long)(cooldown * 0.85)`).
* **Freeze:** * Reduce the slow percentage from 60% down to **30%** (multiplier from 0.4 to 0.7).
  * Enforce a hard **30-second cooldown** on the Freeze spell before it can be cast again by that player.

## 5. Mob Immunities & Status Display Fixes
**Files:** `TowerManager.java`, `MobManager.java`
* **Poison Immunity Consistency:** In `TowerManager.java` (POISON case), stop hardcoding `EntityType.SPIDER`. Instead, check if the mob's `td_immunities` PersistentDataContainer string contains `"POISON"`. If immune, do not apply the damage, and do not apply the vanilla Poison PotionEffect (which stops the 🤢 from showing on the health bar).
* **Chorus Immunity:** In `TowerManager.java` (CHORUS case), check if the mob's `td_immunities` string contains `"CHORUS"` (e.g., Enderman). If immune, completely skip the rollback teleportation logic.
* **Freeze Immunity Check:** Ensure Wardens are fully immune to Freeze/Ice towers. If a mob is immune to ICE or SLOW, ensure they do not receive the Slowness PotionEffect.

## 6. Chorus Tower Teleportation & Visual Glitch Fix
**Files:** `TowerManager.java`
* **Issue:** Mobs affected by the Chorus Tower appear to slide backwards or turn around awkwardly instead of teleporting instantaneously due to residual velocity and pathfinder conflicts.
* **Execution:** In `shootTarget()` under the `CHORUS` case, after calculating `newTargetLocation`, update the teleportation execution block to do the following:
  1. Calculate the look direction (`lookDir`) towards the new target and apply it using `teleportLoc.setDirection(lookDir)`.
  2. Execute the physical teleport: `target.teleport(teleportLoc);`.
  3. Immediately zero out all lingering physical velocity: `target.setVelocity(new org.bukkit.util.Vector(0, 0, 0));`.
  4. Explicitly stop the stale pathfinding: `target.getPathfinder().stopPathfinding();` before issuing the new `target.getPathfinder().moveTo(...)` command.

## 7. Tower Fixes (EMP)
**Files:** `GameManager.java`
* **EMP Duplicate Target Fix:** In `disableRandomTower()`, when compiling the list of valid `arenaTowers` to disable, filter out any towers that are *already* disabled: `if (tower.isDisabled()) continue;`.

## 8. Mob Spawning & Equipment Clearing
**Files:** `MobManager.java`
* **Equipment Scrubbing:** Vanilla mobs sometimes spawn with random armor or weapons. In `spawnMob()`, explicitly clear all equipment (`entity.getEquipment().clear()`) before applying the custom equipment defined in the CSV profile.

## 9. General QoL & GUI Updates
**Files:** `MobManager.java`, `GameManager.java`, `TDCommand.java`
* **Lobby Compass Removal:** In `GameManager.java` (`resetPlayerForMatch` and `giveStarterWeapons`), ensure you explicitly call `player.getInventory().remove(Material.COMPASS);`.
* **Mob Unlock Difficulty:** In `MobManager.getTierUnlockCost()`, increase the EXP requirements significantly to make high tiers harder to obtain.
* **Speed Display Format:** In the Spawner and Tier GUIs, change the Lore string for speed to say `"Speed: X.X Blocks/sec"` to make it intuitive.
* **Level-Up Descriptions & Whitespace:** In `openMobTierGUI`, improve readability by adding blank lines (whitespace) between stat categories. Dynamically compare the current profile to the previous tier (Tier - 1) and append explicit upgrade descriptions (e.g., `ChatColor.GREEN + "(+200 HP)"`).

## 10. CSV Adjustments (mob_upgrades_polymorphic.csv)
**Files:** `mob_upgrades_polymorphic.csv`
Please parse and update the CSV to reflect these changes:
* **Silverfish:** Remove the `"Flying"` tag from their Special Mechanics on all levels.
* **Breeze:** Add the `"Flying"` tag to their Special Mechanics so they float above the track like Blazes.
* **Warden:** Increase their HP across all tiers significantly. Add `"ICE"` and `"POISON"` (or "SLOW") to their Immunities column so they resist all slows and don't get frozen.
