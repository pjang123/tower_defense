# Project Context: Minecraft Tower Defense Plugin

## 1. Project Overview
* **Name:** Tower_Defense
* **Platform:** Spigot / PaperMC API
* **Target Version:** 26.1.2
* **Language:** Java 25
* **Build Tool:** Maven
* **Goal:** A custom multiplayer Tower Defense minigame where players buy towers on predefined plots to stop custom mobs from walking down a sequential path.

## 2. Architecture & File Structure
The project is divided into modular packages to separate data management, event listening, and core game logic.

### Root Package: `com.pauljang.towerDefense`
* **`TowerDefense.java`**: The main `JavaPlugin` class. It initializes all managers, registers commands, and registers event listeners in `onEnable()`.

### Package: `.core` (Game Engine & Commands)
* **`GameState.java`**: An Enum tracking the match state (`LOBBY`, `STARTING`, `ACTIVE`, `ENDED`).
* **`GameManager.java`**: A state machine that stores the current `GameState` and handles the transition logic when states change.
* **`TDCommand.java`**: Implements `CommandExecutor`. Handles all `/td` subcommands (`start`, `stop`, `status`, `wand`, `plotmode`, `waypointmode`, `clearwaypoints`).

### Package: `.setup` (Admin Setup State)
* **`SetupManager.java`**: Temporarily stores data in memory while an admin is building the map.
    * Tracks player UUIDs mapped to a `SetupState` Enum (`IDLE`, `AWAITING_PLOT_P1`, `AWAITING_PLOT_P2`, `WAYPOINT_MODE`).
    * Temporarily stores `Location` data for P1 and P2 clicks.

### Package: `.listeners` (Event Handling)
* **`WandListener.java`**: Implements `Listener`. Listens for `PlayerInteractEvent`.
    * Checks if the user is holding the "TD Setup Wand" (Blaze Rod).
    * Routes their Left/Right clicks based on their current `SetupState`.
    * **Validation:** Forces plots to be exactly flat (same Y-level), exactly 3x3 or 5x5 in size, and checks for overlapping plots before triggering a save.

### Package: `.data` (File Persistence)
* **`PlotConfigManager.java`**: Manages `plots.yml`. Generates unique UUIDs for valid plots and saves their bounding box coordinates (Pos1 and Pos2). Contains `isPlotOverlapping()` to prevent intersecting plots.
* **`WaypointConfigManager.java`**: Manages `waypoints.yml`. Saves exact sequential coordinate nodes (X, Y, Z centered on the block) for custom mob pathfinding.

## 3. Current Progress (Phase 1 Completed)
Phase 1 (The Canvas) is fully built. We have a robust system to define the physical geometry of the arena.
* The plugin successfully compiles and runs.
* Admins can toggle into `/td plotmode` to mark 3x3 or 5x5 grids. Valid grids are saved to YAML.
* Admins can toggle into `/td waypointmode` to mark the exact path mobs should walk. Path nodes are appended sequentially to YAML.

## 4. Immediate Next Steps (Phase 2)
The next step is **Phase 2: The Attackers**.
We need to utilize the data saved in `waypoints.yml` to spawn and move custom entities.
* **Objective A:** Create a custom entity class (e.g., overriding the NMS Zombie or using Bukkit's API to strip vanilla AI).
* **Objective B:** Write a pathfinding loop that forces the custom entity to walk directly to Waypoint 0, then Waypoint 1, etc., until it reaches the final node.
* **Objective C:** Add logic to despawn the mob and deal damage to a "Castle Health" integer when the final waypoint is reached.
