# Tower Defense: Project Summary & Implementation Details

This document provides a detailed, exact report of all the new features, gameplay mechanics, bug fixes, and directory structures implemented for the Spigot/Paper 1v1 Tower Defense plugin.

---

## 1. Newly Implemented Features

### Golem Tower (5x5 Size)
* **Structure Loading**: Loads `golem.nbt` (or tier-specific `golem_1.nbt` and `golem_2.nbt`). Visual block isolation disconnects neighboring blocks dynamically.
* **Mob Behavior**:
  * **Spawning**: Spawns directly on the lane path (at Waypoint 0) of the tower's arena, instead of static placement on top of the tower. Spawns with AI and gravity enabled, and collision disabled so it doesn't block pathing mobs.
  * **Patrol Pathfinding**: Ticked inside the main tower loop. It moves along the waypoints from the start to the end. Upon reaching the final waypoint, it reverses direction and walks back, patrolling back and forth continuously along the lane path.
  * **Tiers & Combat**:
    * **Tier 1 (Copper Golem)**: Safely attempts to spawn a custom `COPPER_GOLEM` entity type if registered by a mod/plugin. Otherwise, it falls back to a baby-sized `IronGolem` (scaled to `0.6` model size) named "Copper Golem" in gold. Deals 20 HP single-target damage. Does not knock up.
    * **Tier 2 (Iron Golem)**: Spawns a full-sized `IronGolem` (size `1.0`) named "Iron Golem" in gray. Deals 40 HP single-target damage and knocks enemies up (Y-velocity = 0.8).

### Happy Ghast Tower (5x5 Size)
* **Structure Loading**: Maps to `happy.nbt` (or tier-specific `happy_1.nbt`, `happy_2.nbt`, `happy_3.nbt`). Fixes a block placement crash by safely placing placeholder blocks if a spawn egg is set in the default fallback layout.
* **Mob Behavior**:
  * **Ridable Summon**: Spawns an invulnerable, AI-disabled Happy Ghast. The purchasing player can right-click to mount and steer it (flies in their look direction).
  * **Fireball Attacks**: Left-clicking while riding fires non-destructive fireballs (`isIncendiary=false`, `yield=0.0`) dealing tier-scaled AoE damage to hostile mobs (T1: 15 HP, T2: 25 HP, T3: 40 HP) in a 5-block impact radius.
  * **Autopilot Mode**: When unridden, automatically targets and shoots fireballs using standard tower targeting priorities.
  * **Tiers (1 to 3)**: Speed scales (T1: 0.3, T2: 0.45, T3: 0.6) and damage scales with each tier upgrade.

### Lobby Selector GUI & Matchmaking
* **Dynamic Compass**:
  * **In `lobby_world`**: Compass is named **"Game Selector"** (Gold). Left or right clicking it opens the selector GUI.
  * **In `game_world`** (before start): Compass is named **"Return to Lobby"** (Red). Left or right clicking it teleports the player back to the lobby spawn.
* **GUI "Open Games"**: Shows the open `game_world` in slot 13 (as a Map item). Hovering displays the waiting player count: `Players waiting: X/2`. Clicking it teleports the player to `game_world`.
* **Automated Match Flow**:
  * Once $\ge 2$ players enter the `game_world`, the game state changes to `STARTING` (10-second countdown).
  * If a player leaves the `game_world` or disconnects during the countdown, the countdown is cancelled and the state reverts to `LOBBY`.
  * When the match transitions to `ACTIVE`, players are teleported to their respective bases (`teleportToArenaStart`).

### Hotbar Offhand Protection
* Completely disabled the player's ability to offhand items from their hotbar or inventory.
* Cancelled `PlayerSwapHandItemsEvent`, offhand slot clicks in `InventoryClickEvent` (slot `40`/raw slot `45`), swap offhand click types (`ClickType.SWAP_OFFHAND`), and offhand slot drags in `InventoryDragEvent`.

### Sidebar Scoreboard Game Timer
* Displays a running match timer in `MM:SS` format (e.g. `Time: 01:45`) during active games on the sidebar scoreboard, defaulting to `Time: 00:00` otherwise.

---

## 2. Code Modifications

The modifications were made in the following classes:

### [GameManager.java](file:///C:/Users/pjang/IdeaProjects/Tower%20Defense/src/main/java/com/pauljang/towerDefense/core/GameManager.java)
* Added a `matchStartTime` timestamp field.
* Configured `handleGameStart()` to initialize the start time and teleport players to their arena starts.
* Modified `handleCountdown()` to clear the queue and populate it with the players physically in the `game_world`.
* Updated `giveLobbyItems()` to dynamically rename and re-lore the Compass based on whether the player is in `lobby_world` or `game_world`.
* Implemented `openGamesGUI()`, `checkGameStartConditions()`, and `checkGameStopConditions()`.
* Updated `updateScoreboard()` to format and display the elapsed time as `MM:SS`.

### [MobListener.java](file:///C:/Users/pjang/IdeaProjects/Tower%20Defense/src/main/java/com/pauljang/towerDefense/listeners/MobListener.java)
* Intercepted click types (`SWAP_OFFHAND`, slots `40`/`45`) in `onInventoryClick(...)` to cancel offhand item placements.
* Added `onPlayerSwapHandItems(...)` and `onInventoryDrag(...)` events to cancel offhand swaps and drags.
* Updated `onPlayerInteract(...)` to handle the dynamic Compass click behaviors (opening the GUI in the lobby, and returning to the lobby spawn in the game world).
* Added `onWorldChange(...)` to update player inventories with the proper world-specific Compass items and trigger start/stop matchmaking checks.
* Merged `onPlayerQuit(...)` logic to ensure game stop conditions check on player disconnects.

### [TowerManager.java](file:///C:/Users/pjang/IdeaProjects/Tower%20Defense/src/main/java/com/pauljang/towerDefense/towers/TowerManager.java)
* Exposed `getMobsInRadius(...)` as a `public` method so it can be called from listener events.
* Added the compilation-safe reflection method `setEntityScale(...)` to adjust model size.
* Modified `buildTowerStructure()`:
  * Safe-check for item block types in fallback placements to prevent spawn-egg crashes.
  * Map `TowerType.HAPPY_GHAST` to lookup `happy.nbt` structure files.
  * Spawn Golems directly on Waypoint 0 with AI/gravity enabled.
  * Safely lookup and spawn modded `COPPER_GOLEM` entities, falling back to a baby-sized scaled Iron Golem.
* Added Golem path patrol movement ticking logic inside the main tower ticker loop.

---

## 3. Server Directory & Troubleshooting

### Directory Structure
To run the server correctly, you must place your world folders directly in your Minecraft server's root directory:
```
Server Root/
├── server.properties
├── bukkit.yml
├── plugins/
│   └── TowerDefense.jar
├── lobby_world/            <-- Your Lobby World folder
└── game_world_template/    <-- Your Template Game World folder
```

### Troubleshooting: Plugin is Disabled
If you receive the error `Cannot execute command 'td' in plugin Tower_Defense - plugin is disabled`, it means the plugin crashed during `onEnable()` on startup and was automatically disabled by Paper/Spigot.

**Common Causes & Fixes:**
1. **Missing World Folders**: Ensure both `lobby_world` and `game_world_template` are placed directly in the server root folder. If the plugin cannot find them, it will throw an exception and disable.
2. **File Lock Issues**: If a world folder is locked by another process (or a crashed server run), Paper/Spigot cannot delete or copy the folders. Restart your server machine or check Task Manager to kill any stuck Java processes, then delete the temporary `game_world` folder manually.
3. **Inspect Server Logs**: Open the file **`logs/latest.log`** in your server directory and scroll to the top where the plugin is enabled. Look for the error traceback starting with `[Tower_Defense] Enabling Tower_Defense` to see the exact missing file or configuration error.
