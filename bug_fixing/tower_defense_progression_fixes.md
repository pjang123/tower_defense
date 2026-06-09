# Tower Defense Plugin: Critical Progression & Mechanics Fixes

Please implement the following fixes across the Java source code to resolve the lobby compass issue, the Giant's zombie pathing, and universal castle attacking.

## 1. Remove Compass on Game Start
**File:** `src/main/java/com/pauljang/towerDefense/core/GameManager.java`
* **Issue:** The "Return to Lobby" compass is still remaining in the player's inventory when the match starts.
* **Fix:** In the `handleGameStart()` method, locate the loop iterating over `matchQueue`. Replace the call to `ensureCompass(player);` with a direct removal command: 
  `player.getInventory().remove(org.bukkit.Material.COMPASS);`

## 2. Giant's Zombie Spawning & Pathing Fix
**File:** `src/main/java/com/pauljang/towerDefense/entities/MobManager.java`
* **Issue:** Zombies spawned by the Giant continuously spawn even if the Giant is dead, and they don't follow the waypoints.
* **Fix 1 (Liveness Check):** Wrap the Giant's zombie summoning logic (the `tick % 100 == 0` check) in a strict condition ensuring the Giant is alive and valid: `if (!giantEntity.isDead() && giantEntity.isValid())`.
* **Fix 2 (Waypoint Inheritance):** When the Giant summons a Zombie, you must use the plugin's official spawn method (e.g., `spawnMobByChain` or equivalent) to generate the fully equipped Tier 1 Zombie and its `TDMob` wrapper. 
  * Immediately after spawning it, teleport the new Zombie entity to the Giant's current location.
  * Retrieve the newly created `TDMob` wrapper for the Zombie and inherit the Giant's pathing data:
    `zombieTDMob.setCurrentWaypointId(giantTDMob.getCurrentWaypointId());`
    `zombieTDMob.setPathHistory(new java.util.ArrayList<>(giantTDMob.getPathHistory()));`
  * This guarantees the Zombie is tracked by the velocity engine and proceeds toward the castle from the Giant's exact spot, rather than running back to the start.

## 3. Universal Castle Attacking (Waypoint Reach Logic)
**Files:** `MobManager.java` / `TDMob.java`
* **Issue:** Certain mobs (fast mobs, flying mobs like Breezes, or large hitboxes) miss the final waypoint trigger and get stuck at the end of the track instead of damaging the castle.
* **Fix 1 (2D Distance Check):** Update the waypoint "reach" detection in the movement loop. Do not use standard 3D `distanceSquared()` because flying/tall mobs have mismatched Y-coordinates that prevent the distance from ever reaching zero. 
  * Change the distance calculation to strictly evaluate the X and Z axes: 
    `double distSq = Math.pow(mobLoc.getX() - targetLoc.getX(), 2) + Math.pow(mobLoc.getZ() - targetLoc.getZ(), 2);`
  * Increase the reach threshold slightly (e.g., `if (distSq < 2.25)` which equates to a 1.5 block radius) to ensure fast velocity-driven mobs don't overshoot the node in a single tick.
* **Fix 2 (End of Track Execution):** When a `TDMob` successfully reaches a waypoint that has *no next connections* (i.e., an empty or null `nextIds` list), it has reached the castle door. Enforce this strict execution block:
  1. Retrieve the mob's damage value from its state profile (or default to a set amount).
  2. Call `plugin.getGameManager().damageCastle(targetArena, damageAmount);`
  3. Despawn the entity cleanly using `mob.getEntity().remove();` (CRITICAL: Do NOT use `.setHealth(0)` or `.damage()`, as that will trigger Slime/Magma cube splitting mechanics and death events at the castle door).
  4. Remove the `TDMob` from the `activeMobs` tracking list.
