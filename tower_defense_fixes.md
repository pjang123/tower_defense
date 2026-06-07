# Tower Defense System Fixes: Pathfinding & Entity Management

This document provides a comprehensive analysis and resolution strategy for the critical entity behavior bugs observed during the recent playtest, specifically targeting the broken pathfinding system, the "Happy Ghast" initialization failure, and recent balance/GUI adjustments.

---

## Part 1: Pathfinding System and Movement Fixes

### The Root Cause of Frozen Mobs
The pathfinding system currently utilizes two strategies: Vanilla Pathfinding (`moveTo`) and Velocity Overrides (`setVelocity`). During the playtest, mobs like the Warden, Giant, and Hoglin spawned but remained entirely stationary.

**The bug is caused by the `setAI(false)` method.** 
In Spigot/Paper servers, setting an entity's AI to `false` entirely removes them from the server's physics and behavioral tick loop for horizontal movement. When you disable their AI and subsequently attempt to apply a directional vector via `setVelocity()`, the server ignores the horizontal momentum updates. This permanently freezes any mob that relies on the velocity engine. 

Additionally, `MAGMA_CUBE` and `WITHER_SKELETON` were completely omitted from the velocity check array, causing them to default to Vanilla Pathfinding (which also failed because their AI was disabled).

### Required Code Changes

#### 1. Stop Disabling AI (`MobManager.java`)
You must remove all instances of `setAI(false)` for pathfinding mobs. Because your plugin manually overrides their velocity 20 ticks a second, they will be forcefully pushed along the track regardless of their active AI goals.

**Remove these lines in `spawnMob()`:**
```java
// SLIMES
// slime.setAI(false); 

// CREEPERS
// entity.setAI(false);

// BRAIN AI MOBS
/*
if (type == EntityType.GIANT || type == EntityType.WARDEN || type == EntityType.ENDERMAN || 
    type == EntityType.ZOMBIFIED_PIGLIN || type == EntityType.HOGLIN || type == EntityType.ZOGLIN || 
    type == EntityType.BREEZE) {
    // entity.setAI(false); 
}
*/

// WITHER SKELETONS
// entity.setAI(false);
```

**Remove in `spawnMobByChain()` for mounts:**
```java
if (mount instanceof Mob mountMob) {
    org.bukkit.Bukkit.getMobGoals().removeAllGoals(mountMob); 
    // mountMob.setAI(false); <-- REMOVE THIS
}
```

#### 2. Consolidate and Fix the Velocity Condition
To include the missing Magma Cubes and Wither Skeletons, and to clean up your movement tick loop, implement a centralized helper method for checking velocity-driven mobs.

**Add this to `MobManager.java`:**
```java
private boolean isVelocityDriven(org.bukkit.entity.Mob entity, double heightOffset) {
    org.bukkit.entity.EntityType t = entity.getType();
    return t == org.bukkit.entity.EntityType.GIANT || 
           t == org.bukkit.entity.EntityType.SLIME || 
           t == org.bukkit.entity.EntityType.MAGMA_CUBE || 
           t == org.bukkit.entity.EntityType.WITHER_SKELETON || 
           t == org.bukkit.entity.EntityType.CREEPER || 
           t == org.bukkit.entity.EntityType.WARDEN || 
           t == org.bukkit.entity.EntityType.ENDERMAN || 
           t == org.bukkit.entity.EntityType.ZOMBIFIED_PIGLIN || 
           t == org.bukkit.entity.EntityType.HOGLIN || 
           t == org.bukkit.entity.EntityType.ZOGLIN || 
           t == org.bukkit.entity.EntityType.BREEZE || 
           entity.getVehicle() instanceof org.bukkit.entity.Mob || 
           heightOffset > 0.0;
}
```
*You can now replace the massive `if` statements in your `handleMobMovement` task with `if (isVelocityDriven(mob.getEntity(), heightOffset))`.*

#### 3. Prevent Endermen Teleporting (`MobListener.java`)
Because Endermen now have their AI re-enabled, they will natively try to teleport away when taking damage, breaking the pathflow. 

**Add this event handler:**
```java
@EventHandler
public void onMobTeleport(org.bukkit.event.entity.EntityTeleportEvent event) {
    // Block teleportation for TD mobs
    if (event.getEntity().getPersistentDataContainer().has(new org.bukkit.NamespacedKey(plugin, "td_mob"), org.bukkit.persistence.PersistentDataType.BYTE)) {
        event.setCancelled(true);
    }
}
```

---

## Part 2: The Happy Ghast Initialization Fix

### Analysis of the Harness Crash
During the playtest, the console threw the following exception:
```text
[01:13:14] [Server thread/WARN]: [Tower_Defense] [HappyGhast] BODY slot equip failed: java.lang.IllegalArgumentException: No enum constant org.bukkit.Material.HARNESS
```

The game attempted to apply an item named `Material.HARNESS` to the Ghast so the player could steer it. While the game registry clearly supports specific colored harnesses (e.g., `minecraft:red_harness` and `minecraft:black_harness`), the Bukkit API does not have a generic `Material.HARNESS` enum. Because it failed to find this generic enum, the plugin threw an exception and aborted, leaving the Ghast unsteerable.

### Required Code Changes

#### Fix the Material Enum (`TowerManager` / `HappyGhast` initialization class)
Locate the initialization logic for the Happy Ghast and change the equipment assignment to dynamically select `RED_HARNESS` or `BLUE_HARNESS` based on the team that spawned it.

**Replace the broken code with this:**
```java
// Determine the correct harness color based on the spawning team
Material harnessMaterial = (team == Team.BLUE) ? Material.BLUE_HARNESS : Material.RED_HARNESS;

// Equip the exact valid Bukkit enum
ItemStack harness = new ItemStack(harnessMaterial);
ghast.getEquipment().setBody(harness); 
```

By using the specific color enum based on the team, the Bukkit API will successfully resolve the material, equip it to the body slot, provide visual team clarity, and re-enable the native vanilla steering mechanics.

---

## Part 3: GUI and Mob Upgrade Adjustments

### 1. Removing the Standalone Endermite from GUI
The standalone Endermite must be removed from the mob spawning menus so it cannot be individually selected by players.
*   **Locate the GUI Builder:** Search in your GUI initialization class (likely tied to `TDCommand.java` or `SetupManager.java`).
*   **Remove the Item:** Find where `PresetMobType.ENDERMITE` (or the equivalent material representing the Endermite) is added to the inventory/menu layout and delete or comment out that specific configuration block.

### 2. Updating Silverfish Lvl 4 & 5 Attributes
The Level 4 and Level 5 Silverfish in the polymorphic upgrade chain need to inherit the attributes from the Level 4 and Level 5 Endermites to ensure balance consistency.
*   **Update the Upgrade Registry:** Open `mob_upgrades_polymorphic.csv`.
*   **Modify Attributes:** Locate the rows for Silverfish Level 4 and Level 5. Overwrite their health, speed, damage, and armor values (or relevant custom attributes) to perfectly match the values listed for Endermite Level 4 and Endermite Level 5.
