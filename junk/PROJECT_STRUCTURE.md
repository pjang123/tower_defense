# Tower Defense Plugin - Project Structure & Architecture

This document provides a comprehensive overview of the Tower Defense plugin codebase. It outlines the overall directory layout, the exact role of every class, and the interactions between different modules.

---

## Directory Layout
```
Tower Defense/
├── src/main/java/com/pauljang/towerDefense/
│   ├── TowerDefense.java (Main Entrypoint)
│   ├── core/
│   │   ├── GameManager.java
│   │   ├── GameState.java
│   │   └── TDCommand.java
│   ├── data/
│   │   ├── PlotConfigManager.java
│   │   ├── TDWaypoint.java
│   │   └── WaypointConfigManager.java
│   ├── entities/
│   │   ├── MobManager.java
│   │   ├── PresetMobType.java
│   │   └── TDMob.java
│   ├── listeners/
│   │   ├── MobListener.java
│   │   └── WandListener.java
│   ├── setup/
│   │   └── SetupManager.java
│   └── towers/
│       ├── TargetingMode.java
│       ├── Tower.java
│       ├── TowerManager.java
│       └── TowerType.java
├── src/main/resources/
│   ├── config.yml (Upgrades, towers, spells settings)
│   └── plugin.yml (Plugin metadata and commands definition)
└── pom.xml (Maven dependency and build specification)
```

---

## Class Breakdown & Descriptions

### 1. Main Entrypoint
* **[TowerDefense.java](file:///C:/Users/pjang/IdeaProjects/Tower%20Defense/src/main/java/com/pauljang/towerDefense/TowerDefense.java)**
  * **Role:** The main Bukkit plugin loader class.
  * **Functionality:** Handles startup (`onEnable`) and shutdown (`onDisable`) hooks. It instantiates the central manager classes (`GameManager`, `PlotConfigManager`, `WaypointConfigManager`, `SetupManager`, `TowerManager`, `MobManager`), registers command executors (`TDCommand`), and registers event listeners (`WandListener`, `MobListener`). Initializes base directories like `/structures`.

### 2. Core Game Loop & Orchestration (`core/`)
* **[GameManager.java](file:///C:/Users/pjang/IdeaProjects/Tower%20Defense/src/main/java/com/pauljang/towerDefense/core/GameManager.java)**
  * **Role:** The primary manager handling active duel matches, scoreboard renders, and player resources.
  * **Functionality:** 
    * **Matchmaking & Lobbies:** Automatically triggers a 10-second match starting countdown when $\ge 2$ players enter the `game_world`. Cancels the timer if players leave or disconnect, reverting to the `LOBBY` state. Teleports players to blue/red arena locations upon game start.
    * **Match Timer:** Tracks the elapsed match duration (`matchStartTime`) and displays it formatted as `MM:SS` (e.g., `Time: 02:15`) on the sidebar scoreboard for active players.
    * **Lobby Compass & Selector GUI:** Manages the game selector GUI (`openGamesGUI`) displaying open lobbies in slot 13 as a Map item with wait counts (`Players waiting: X/2`). Initializes/configures player compasses based on current worlds.
    * **Player Stats & Shop:** Handles gold, EXP, income tick rates, and upgrades (e.g. Gold Generation).
    * **Spells:** Governs spell cards purchase and casting mechanics (Freeze, Damage Storm, EMP).
    * **Castle Damage Alerts:** Plays castle damage note-block bass sound indicators restricted only to the affected team.
* **[GameState.java](file:///C:/Users/pjang/IdeaProjects/Tower%20Defense/src/main/java/com/pauljang/towerDefense/core/GameState.java)**
  * **Role:** Game lifecycle Enum state definition.
  * **Values:** `LOBBY` (waiting for queue), `STARTING` (match countdown active), `ACTIVE` (game loop active), `ENDED` (match finalized).
* **[TDCommand.java](file:///C:/Users/pjang/IdeaProjects/Tower%20Defense/src/main/java/com/pauljang/towerDefense/core/TDCommand.java)**
  * **Role:** Administrative and player command execution backend.
  * **Functionality:** Implements command execution for `/td`. Supports subcommands including `start`, `stop`, `status`, `wand`, `plotmode`, `saveplot`, `deleteplotmode`, `waypointmode`, `clearwaypoints`, `gui`, `upgrades`, `giveitems` / `menuitems`, `givegold`, `givexp`, `setarena`, `challenge`, `accept`, `list`, and `spawnmob`.

### 3. Data & Configuration Persistence (`data/`)
* **[PlotConfigManager.java](file:///C:/Users/pjang/IdeaProjects/Tower%20Defense/src/main/java/com/pauljang/towerDefense/data/PlotConfigManager.java)**
  * **Role:** Configuration interface for saving and querying player build grids (`plots.yml`).
  * **Functionality:** Serializes Pos1/Pos2 bounding boxes. Determines if coordinates overlap with existing grids, retrieves a plot's center for tower rendering, and measures dimensions (e.g. 3x3 vs 5x5 width/length checks).
* **[TDWaypoint.java](file:///C:/Users/pjang/IdeaProjects/Tower%20Defense/src/main/java/com/pauljang/towerDefense/data/TDWaypoint.java)**
  * **Role:** Data structure model for path waypoints.
  * **Functionality:** Represents a coordinate location inside an arena path graph, keeping track of its adjacent connection lists (next waypoints) to support non-linear branching paths.
* **[WaypointConfigManager.java](file:///C:/Users/pjang/IdeaProjects/Tower%20Defense/src/main/java/com/pauljang/towerDefense/data/WaypointConfigManager.java)**
  * **Role:** Configuration interface for saving path waypoints (`waypoints.yml`).
  * **Functionality:** Reads and writes waypoints. Builds memory-cached directed graphs representing paths for both Arena 1 (Blue Team) and Arena 2 (Red Team).

### 4. Custom Mobs & Traversal System (`entities/`)
* **[MobManager.java](file:///C:/Users/pjang/IdeaProjects/Tower%20Defense/src/main/java/com/pauljang/towerDefense/entities/MobManager.java)**
  * **Role:** Handles custom entity pathfinding and damage loops.
  * **Functionality:**
    * Keeps track of all active spawned mobs (`TDMob` objects).
    * Guides mobs along the waypoint graph toward the castle.
    * Governs castle attack damage ticks (damaging the castle by 1 HP every 2 seconds once mobs reach the final offset coordinate).
    * Limits the wooden door strike sound (`Sound.ENTITY_ZOMBIE_ATTACK_WOODEN_DOOR`) to only play for team members corresponding to the damaged castle.
* **[PresetMobType.java](file:///C:/Users/pjang/IdeaProjects/Tower%20Defense/src/main/java/com/pauljang/towerDefense/entities/PresetMobType.java)**
  * **Role:** Enum cataloging default properties for purchasable/sendable mobs.
  * **Fields:** Includes base details like `EntityType` (Zombie, Skeleton, Silverfish, Spider, Pigman, Slime, Creeper, Blaze, Magma Cube, Ghast, Giant), base speed, health, armor, rewards (gold/EXP), and purchase cost.
* **[TDMob.java](file:///C:/Users/pjang/IdeaProjects/Tower%20Defense/src/main/java/com/pauljang/towerDefense/entities/TDMob.java)**
  * **Role:** Active wrapper instance for a spawned Minecraft mob.
  * **Functionality:** Keeps track of the Bukkit `Entity` reference, its current target waypoint index, freeze status, speed modifiers, and attack cooldown timestamps.

### 5. Event Listeners (`listeners/`)
* **[MobListener.java](file:///C:/Users/pjang/IdeaProjects/Tower%20Defense/src/main/java/com/pauljang/towerDefense/listeners/MobListener.java)**
  * **Role:** General event listener for interaction, menus, and combat.
  * **Functionality:**
    * **Offhand Protection:** Disables using the offhand slot by cancelling hand swap events, offhand clicks/drags in inventories, and offhand swap click actions.
    * **Compass Interaction:** Handles dynamic compass clicks. Right/left clicking opens the Lobby Selector GUI in the lobby world, or teleports the player back to the lobby spawn when clicked in the game world.
    * **World Change Handler:** Detects world changes to automatically update the player's hotbar with the appropriate compass item and triggers matchmaking checks.
    * **Custom Summon Protection:** Prevents custom tower entities (Golems and Happy Ghasts) from taking targeting aggro or targeting/attacking players.
    * **Projectile Protections:** Blocks large fireball damage and knockback on players by cancelling `ProjectileHitEvent` for players. Inflicts AOE damage in a 5-block radius on mobs instead.
    * **Summon Scale Checks:** Cancels purchasing 5x5 towers (Golem and Happy Ghast) on plots with smaller measurements.
    * **Environmental Adjustments:** Prevents block fading/melting (ice, frosted ice, snow) and handles Ghast dismount AI reset.
    * **Game Controls:** Restricts natural mob spawning, manages Slime splitting attributes/health updates on death, and clears drops/XP upon TD mob deaths to award bounty gold instead.
* **[WandListener.java](file:///C:/Users/pjang/IdeaProjects/Tower%20Defense/src/main/java/com/pauljang/towerDefense/listeners/WandListener.java)**
  * **Role:** Listener for setup wand usage.
  * **Functionality:** 
    * Handles left-click/right-click actions on the Setup Wand. Allows admins to place new waypoint nodes, select existing nodes, link multiple existing nodes to create path splits, and delete nodes.
    * **Dust Particles Preview:** Starts a periodic preview task (every 5 ticks) to display visual particles (dust particles) to outline targeted plots (yellow for 5x5, aqua for 3x3) and waypoint graphs for admins.

### 6. Map Construction/Setup (`setup/`)
* **[SetupManager.java](file:///C:/Users/pjang/IdeaProjects/Tower%20Defense/src/main/java/com/pauljang/towerDefense/setup/SetupManager.java)**
  * **Role:** State machine for map setup.
  * **Functionality:** Tracks active administrative configuration modes (e.g. plot creation size toggle: 3x3/5x5, waypoint editing, plot deletion, idle), records temporary edit arenas, selected sizes, and the currently selected waypoint ID for making path connections.

### 7. Custom Towers & Summons (`towers/`)
* **[TargetingMode.java](file:///C:/Users/pjang/IdeaProjects/Tower%20Defense/src/main/java/com/pauljang/towerDefense/towers/TargetingMode.java)**
  * **Role:** Enum for tower AI target selection logic.
  * **Values:** `FIRST` (closest to target castle), `LAST` (farthest from target castle), `STRONGEST` (highest HP), `WEAKEST` (lowest HP).
* **[Tower.java](file:///C:/Users/pjang/IdeaProjects/Tower%20Defense/src/main/java/com/pauljang/towerDefense/towers/Tower.java)**
  * **Role:** Model tracking a single built tower instance.
  * **Functionality:** Holds reference to plot coordinates, type, upgrade level, targeting mode, floating hologram name tags, and active entity summons like `spawnedGhast` and `spawnedGolem`.
* **[TowerManager.java](file:///C:/Users/pjang/IdeaProjects/Tower%20Defense/src/main/java/com/pauljang/towerDefense/towers/TowerManager.java)**
  * **Role:** Orchestrates custom tower mechanics, renders, and summons.
  * **Functionality:**
    * **Structure Loading:** Parses NBT structure files from the `/structures` folder (`golem.nbt`, `happy.nbt` tiers). Contains safe fallback checks for spawn eggs in structure configuration data to prevent block-placement crashes.
    * **Golem Patrolling Behavior:** Spawns Golems on the lane path at Waypoint 0. Ticks pathfinding to move the golem along the waypoint path, patrolling back and forth continuously within the boundary of its tower's range.
    * **Happy Ghast Mount:** Spawns an invulnerable AI-disabled Ghast. Purchasing players can right-click to mount and steer it (flies in their look direction). Left-clicking fires fireballs dealing AoE damage. Automatically targets and shoots fireballs on autopilot when unridden.
    * **Dynamic Entity Scaling:** Uses reflection to safely scale Iron Golem sizes (0.6x Copper Golem fallback) and Happy Ghasts (0.5x).
    * **Upgrades & Combat:** Adjusts target selection and damage based on tower types and upgrade tiers.
* **[TowerType.java](file:///C:/Users/pjang/IdeaProjects/Tower%20Defense/src/main/java/com/pauljang/towerDefense/towers/TowerType.java)**
  * **Role:** Enum cataloging default statistics and block representations for towers (Archer, Fire, Prismarine, Chorus, Redstone, Poison, Ice, Golem, Happy Ghast).
