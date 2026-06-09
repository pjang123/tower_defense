# Tower Defense Updates: Balance, Mechanics & Progression Persistence

This document details the code updates for adjusting global movement speed, normalizing health curves, fixing golem targeting, modifying the Happy Ghast hovering detection, resetting mob tier progression per match, and guaranteeing player inventory items on server switch.

---

## 1. Global Speed Calibration & HP Normalization

### Global Speed Constant Reduction
To keep gameplay pacing under control, the base global scaling value for mob velocities must be reduced to 1/4 (25%) of its former value.

*   **Implementation (`MobManager.java`):** Locate the dynamic health-based or base speed calculations inside `handleMobMovement()` or your speed modifier method. Multiply the final calculated velocity magnitude or base constant attribute by `0.25`.

### Smoothed Mob Health Algorithm
To make health increments appear clean, readable, and balanced as level tiers advance, replace arbitrary text parameters or raw multipliers with a smooth polynomial or stepped progression curve.

*   **Implementation (`MobUpgradeRegistry.java` / `PresetMobType.java`):** When parsing health scales or applying upgrades, round the values to clean steps (e.g., nearest 5 or 10 for low tiers, nearest 50 or 100 for heavy tanks) using an algorithm like:
    $$	ext{Health}_{	ext{Level}} = 	ext{RoundToNiceValue}(	ext{BaseHealth} 	imes (1 + 	ext{Tier} 	imes 0.50))$$

---

## 2. Golem Tower Target Acquisition Fix

### Issue: Copper & Iron Golems Ignore Mobs
Golems spawned by defensive towers currently lack target goals to recognize spawned track monsters as hostile, causing them to sit idle.

*   **Implementation (`TowerManager.java` / Golem Spawning Code):** When a Golem tower constructs or ticks its defending entities, manually clear vanilla targets and append the targeting goals pointing at nearby `TDMob` instances, or use a target selector.
    ```java
    if (golem instanceof org.bukkit.entity.Creature creature) {
        // Force target the nearest valid track mob identified by metadata
        creature.setTarget(nearestTrackMob);
    }
    ```

---

## 3. Happy Ghast Hover Targeting Adjustments

### Dynamic Range Offset
When the Happy Ghast leaves its static station (e.g., while being steered or roaming), its targeting radius remains locked to the fixed location of its tower base rather than tracking dynamically with the flying entity itself.

*   **Implementation (`Tower.java` / Happy Ghast Tick Loop):** Check if the Ghast is currently detached or being ridden. If it is active away from the base, update the center of its bounding box search query from `tower.getLocation()` to `ghastEntity.getLocation()`.

---

## 4. Mob Level Reset & Session Cleanup

### Issue: Unlocked Mob Levels Persist Across Games
Progression states are not being cleared out when a game cycle transfers from `ENDED` back into `LOBBY` or `STARTING`.

*   **Implementation (`GameManager.java`):** Within your session teardown or restart sequence (`stopGame()` / `resetWorld()`), locate the collection tracking player or team unlocked upgrade tiers (such as a map inside `MobUpgradeRegistry`) and explicitly invoke `.clear()` or overwrite values back to Tier 1.

---

## 5. Player Compass Inventory Allocation

### Issue: Missing Navigation Compasses on Join/Switch
Players do not receive their essential server/game interaction compass item when initializing their connection or moving between world instances.

*   **Implementation (`MobListener.java` / Player Session Class):** Add or extend an event listener for `PlayerJoinEvent` and `PlayerChangedWorldEvent`.
    ```java
    @EventHandler
    public void onPlayerJoinOrSwitch(org.bukkit.event.player.PlayerChangedWorldEvent event) {
        org.bukkit.entity.Player player = event.getPlayer();
        if (!player.getInventory().contains(org.bukkit.Material.COMPASS)) {
            player.getInventory().addItem(new org.bukkit.ItemStack(org.bukkit.Material.COMPASS));
        }
    }
    ```
