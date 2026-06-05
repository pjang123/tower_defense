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
  * **Functionality:** Handles startup (`onEnable`) and shutdown (`onDisable`) hooks. It instantiates the central manager classes (`GameManager`, `PlotConfigManager`, `WaypointConfigManager`, `SetupManager`, `TowerManager`, `MobManager`), registers command executors (`TDCommand`), and registers event listeners (`WandListener`, `MobListener`).

### 2. Core Game Loop & Orchestration (`core/`)
* **[GameManager.java](file:///C:/Users/pjang/IdeaProjects/Tower%20Defense/src/main/java/com/pauljang/towerDefense/core/GameManager.java)**
  * **Role:** The primary manager handling active duel matches, scoreboard renders, and player resources.
  * **Functionality:** 
    * Manages matchmaking lobbies, starting timers (10 seconds when queue is full), and player/arena mapping (Arena 1 mapping to Blue Team and Arena 2 mapping to Red Team).
    * Handles global player stats like Gold, Experience (EXP), Income rate, and upgrades (e.g. Gold Generation upgrades).
    * Manages spell cards purchase and casting (e.g., *Freeze* spell slow multiplier, *Damage Storm* area effects, *EMP* disabling opponent's towers).
    * Restricts castle damage bass sound alerts (`Sound.BLOCK_NOTE_BLOCK_BASS`) to only play for members of the affected team, and renders team-specific action bars.
* **[GameState.java](file:///C:/Users/pjang/IdeaProjects/Tower%20Defense/src/main/java/com/pauljang/towerDefense/core/GameState.java)**
  * **Role:** Game lifecycle Enum state definition.
  * **Values:** `LOBBY` (waiting for queue), `STARTING` (match countdown active), `ACTIVE` (game loop active), `ENDED` (match finalized).
* **[TDCommand.java](file:///C:/Users/pjang/IdeaProjects/Tower%20Defense/src/main/java/com/pauljang/towerDefense/core/TDCommand.java)**
  * **Role:** Administrative and player command execution backend.
  * **Functionality:** Implements command execution for `/td`. Supports commands like `start` (forcibly start duel), `stop` (abort active duel), `wand` (give setup wand), `plotmode` (toggle plot creation), `deleteplotmode` (toggle plot deletion), `waypointmode` (toggle path editing), and `upgrades`/`gui` menus.

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
    * Intercepts creature spawning to enforce queue-only custom spawns.
    * Blocks custom tower summons (Iron Golems and Happy Ghasts) from targeting players or getting targeting agro.
    * Disables player hunger decay globally across matches.
    * Governs player right-clicking to mount the `HappyGhast` mount, steering rotation velocity, and left-clicking to shoot non-destructive large fireballs.
    * Prevents fireballs from dealing knockback or damage to players by cancelling `ProjectileHitEvent` on players.
    * Restricts purchasing 5x5 towers (Golem and Happy Ghast) on plots measured under 5x5 blocks in width/length.
* **[WandListener.java](file:///C:/Users/pjang/IdeaProjects/Tower%20Defense/src/main/java/com/pauljang/towerDefense/listeners/WandListener.java)**
  * **Role:** Listener for setup wand usage.
  * **Functionality:** Handles left-click/right-click actions on the Setup Wand. Allows admins to place new waypoint nodes, select existing nodes, link multiple existing nodes to create path splits, and delete nodes.

### 6. Map Construction/Setup (`setup/`)
* **[SetupManager.java](file:///C:/Users/pjang/IdeaProjects/Tower%20Defense/src/main/java/com/pauljang/towerDefense/setup/SetupManager.java)**
  * **Role:** State machine for map setup.
  * **Functionality:** Tracks active administrative configuration modes (e.g. plot creation size toggle: 3x3/5x5, waypoint editing, plot deletion) and records temporary click positions.

### 7. Custom Towers & Summons (`towers/`)
* **[TargetingMode.java](file:///C:/Users/pjang/IdeaProjects/Tower%20Defense/src/main/java/com/pauljang/towerDefense/towers/TargetingMode.java)**
  * **Role:** Enum for tower AI target selection logic.
  * **Values:** `FIRST` (closest to target castle), `LAST` (farthest from target castle), `STRONGEST` (highest HP), `WEAKEST` (lowest HP).
* **[Tower.java](file:///C:/Users/pjang/IdeaProjects/Tower%20Defense/src/main/java/com/pauljang/towerDefense/towers/Tower.java)**
  * **Role:** Model tracking a single built tower instance.
  * **Functionality:** Holds reference to plot coordinates, type, upgrade level, targeting mode, floating hologram name tags, and active entity summons like `spawnedGhast` (typed as `HappyGhast`) and `spawnedGolem`.
* **[TowerManager.java](file:///C:/Users/pjang/IdeaProjects/Tower%20Defense/src/main/java/com/pauljang/towerDefense/towers/TowerManager.java)**
  * **Role:** Orchestrates custom tower mechanics, renders, and summons.
  * **Functionality:**
    * Parses NBT structure files from `/structures` folder to place tower blocks.
    * Handles Golem path leash restrictions (walking only on path blocks within a 7-block boundary).
    * Handles Happy Ghast steering velocity physics (smooth direction look-based flight, bounds enforcement).
    * Adjusts Iron Golem scale (0.6x) and Happy Ghast scale (0.5x) dynamically via reflection.
* **[TowerType.java](file:///C:/Users/pjang/IdeaProjects/Tower%20Defense/src/main/java/com/pauljang/towerDefense/towers/TowerType.java)**
  * **Role:** Enum cataloging default statistics and block representations for towers (Archer, Fire, Prismarine, Chorus, Redstone, Poison, Ice, Golem, Happy Ghast).
