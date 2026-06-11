# Graph Report - Tower Defense  (2026-06-11)

## Corpus Check
- 55 files · ~72,661 words
- Verdict: corpus is large enough that graph structure adds value.

## Summary
- 1166 nodes · 2636 edges · 68 communities (63 shown, 5 thin omitted)
- Extraction: 73% EXTRACTED · 27% INFERRED · 0% AMBIGUOUS · INFERRED: 721 edges (avg confidence: 0.8)
- Token cost: 0 input · 0 output

## Graph Freshness
- Built from commit: `c6a5e8d8`
- Run `git rev-parse HEAD` and compare to check if the graph is stale.
- Run `graphify update .` after code changes (no API cost).

## Community Hubs (Navigation)
- [[_COMMUNITY_Tower Placement & Lifecycle|Tower Placement & Lifecycle]]
- [[_COMMUNITY_Game Manager Core|Game Manager Core]]
- [[_COMMUNITY_Mob Spawning & Upgrades|Mob Spawning & Upgrades]]
- [[_COMMUNITY_Mob Event Listeners|Mob Event Listeners]]
- [[_COMMUNITY_Docs & Design Notes|Docs & Design Notes]]
- [[_COMMUNITY_Wave & Tower Config|Wave & Tower Config]]
- [[_COMMUNITY_Plugin Entrypoint & World Mgmt|Plugin Entrypoint & World Mgmt]]
- [[_COMMUNITY_Mob Pathfinding & Waypoints|Mob Pathfinding & Waypoints]]
- [[_COMMUNITY_Queue & Map Selection|Queue & Map Selection]]
- [[_COMMUNITY_Setup & Wand Tooling|Setup & Wand Tooling]]
- [[_COMMUNITY_Match State|Match State]]
- [[_COMMUNITY_Plot Configuration|Plot Configuration]]
- [[_COMMUNITY_Waypoint Config|Waypoint Config]]
- [[_COMMUNITY_Preset Mob Types|Preset Mob Types]]
- [[_COMMUNITY_Tower Configs (Batch 1)|Tower Configs (Batch 1)]]
- [[_COMMUNITY_Tower Configs (Batch 2)|Tower Configs (Batch 2)]]
- [[_COMMUNITY_Tower Type Enum|Tower Type Enum]]
- [[_COMMUNITY_Game Config & Spells|Game Config & Spells]]
- [[_COMMUNITY_Wave Mob Roster|Wave Mob Roster]]
- [[_COMMUNITY_Pathing & Siege Fixes|Pathing & Siege Fixes]]
- [[_COMMUNITY_Velocity & Wave Fixes|Velocity & Wave Fixes]]
- [[_COMMUNITY_Plugin Manifest|Plugin Manifest]]
- [[_COMMUNITY_Balancing Docs|Balancing Docs]]
- [[_COMMUNITY_Targeting Modes|Targeting Modes]]
- [[_COMMUNITY_Multiplayer Maps|Multiplayer Maps]]
- [[_COMMUNITY_Singleplayer Map|Singleplayer Map]]
- [[_COMMUNITY_Community 28|Community 28]]
- [[_COMMUNITY_Community 29|Community 29]]
- [[_COMMUNITY_Community 30|Community 30]]
- [[_COMMUNITY_Community 31|Community 31]]
- [[_COMMUNITY_Community 32|Community 32]]
- [[_COMMUNITY_Community 33|Community 33]]
- [[_COMMUNITY_Community 34|Community 34]]
- [[_COMMUNITY_Community 35|Community 35]]
- [[_COMMUNITY_Community 36|Community 36]]
- [[_COMMUNITY_Community 37|Community 37]]
- [[_COMMUNITY_Community 38|Community 38]]
- [[_COMMUNITY_Community 39|Community 39]]
- [[_COMMUNITY_Community 40|Community 40]]
- [[_COMMUNITY_Community 41|Community 41]]
- [[_COMMUNITY_Community 42|Community 42]]
- [[_COMMUNITY_Community 43|Community 43]]
- [[_COMMUNITY_Community 44|Community 44]]
- [[_COMMUNITY_Community 45|Community 45]]
- [[_COMMUNITY_Community 46|Community 46]]
- [[_COMMUNITY_Community 47|Community 47]]
- [[_COMMUNITY_Community 48|Community 48]]
- [[_COMMUNITY_Community 49|Community 49]]
- [[_COMMUNITY_Community 50|Community 50]]
- [[_COMMUNITY_Community 51|Community 51]]
- [[_COMMUNITY_Community 52|Community 52]]
- [[_COMMUNITY_Community 53|Community 53]]
- [[_COMMUNITY_Community 54|Community 54]]
- [[_COMMUNITY_Community 55|Community 55]]
- [[_COMMUNITY_Community 56|Community 56]]
- [[_COMMUNITY_Community 57|Community 57]]
- [[_COMMUNITY_Community 58|Community 58]]
- [[_COMMUNITY_Community 59|Community 59]]
- [[_COMMUNITY_Community 60|Community 60]]
- [[_COMMUNITY_Community 61|Community 61]]
- [[_COMMUNITY_Community 62|Community 62]]
- [[_COMMUNITY_Community 63|Community 63]]
- [[_COMMUNITY_Community 64|Community 64]]
- [[_COMMUNITY_Community 65|Community 65]]
- [[_COMMUNITY_Community 66|Community 66]]

## God Nodes (most connected - your core abstractions)
1. `GameManager` - 82 edges
2. `Tower` - 42 edges
3. `TowerManager` - 40 edges
4. `MobListener` - 37 edges
5. `EventHandler` - 34 edges
6. `MobManager` - 32 edges
7. `UUID` - 28 edges
8. `TDMob` - 26 edges
9. `String` - 24 edges
10. `String` - 22 edges

## Surprising Connections (you probably didn't know these)
- `TowerConfigManager.java` --used_by--> `TowerManager.java`  [INFERRED]
  bug_fixing/tower_defense_final_adjustments.md → PROJECT_STRUCTURE.md
- `bug_fixing/tower_agent_specification.md` --references--> `TowerManager.java`  [INFERRED]
  bug_fixing/tower_agent_specification.md → PROJECT_STRUCTURE.md
- `Golem Tower (5x5)` --implemented_in--> `TowerManager.java`  [INFERRED]
  PROJECT_SUMMARY.md → PROJECT_STRUCTURE.md
- `Happy Ghast Tower (5x5)` --implemented_in--> `TowerManager.java`  [INFERRED]
  PROJECT_SUMMARY.md → PROJECT_STRUCTURE.md
- `Golem Tower (5x5)` --uses--> `SetupManager.java`  [INFERRED]
  PROJECT_SUMMARY.md → PROJECT_STRUCTURE.md

## Import Cycles
- None detected.

## Hyperedges (group relationships)
- **Core Manager Classes** — project_structure_gamemanager, project_structure_mobmanager, project_structure_towermanager, project_structure_plotconfigmanager, project_structure_waypointconfigmanager [EXTRACTED 0.95]
- **Polymorphic Mob Upgrade System** — session_context_mobstateprofile, session_context_mobupgraderegistry, bugfix_mob_impl_polymorphic_csv, session_context_mob_chain_system [EXTRACTED 0.95]
- **Branching Path Towers** — bugfix_tower_agent_turret, bugfix_tower_agent_bombardier, bugfix_tower_agent_beehive, bugfix_tower_agent_branching_paths [EXTRACTED 0.95]
- **Towers with Branching Upgrade Paths** — tower_turret, tower_bombardier, tower_beehive [INFERRED 0.95]
- **Bug Fixing Documentation Set** — bug_fixing_batch7_fixes, bug_fixing_batch8_fixes, bug_fixing_batch9_fixes, bug_fixing_polish_fixes, bug_fixing_progression_fixes [EXTRACTED 0.95]
- **Core Configuration Files** — resources_config_yml, resources_towers_yaml, resources_waves_yml, resources_plugin_yml [INFERRED 0.95]

## Communities (68 total, 5 thin omitted)

### Community 0 - "Tower Placement & Lifecycle"
Cohesion: 0.05
Nodes (33): Bee, HappyGhast, InventoryClickEvent, Iterator, Particle, ProjectileHitEvent, ArmorStand, BlockVector (+25 more)

### Community 1 - "Game Manager Core"
Cohesion: 0.13
Nodes (4): GameManager, GameState, Match, PlayerChangedWorldEvent

### Community 2 - "Mob Spawning & Upgrades"
Cohesion: 0.06
Nodes (29): MobManager, MobStateProfile, MobUpgradeRegistry, MobUpgradeRegistry, PresetMobType, ChatColor, EntityType, Integer (+21 more)

### Community 3 - "Mob Event Listeners"
Cohesion: 0.06
Nodes (31): BlockFadeEvent, CreatureSpawnEvent, getHealth(), EntityCombustEvent, EntityDamageByEntityEvent, EntityDamageEvent, EntityDeathEvent, EntityDismountEvent (+23 more)

### Community 4 - "Docs & Design Notes"
Cohesion: 0.25
Nodes (11): bug_fixing/tower_defense_code_fixes_batch6.md, bug_fixing/tower_defense_final_adjustments_v5.md, bug_fixing/tower_defense_fixes.md, Waypoint (Mob Path Node), GameManager.java, MobListener.java, MobManager.java, TDMob.java (+3 more)

### Community 5 - "Wave & Tower Config"
Cohesion: 0.08
Nodes (20): ConfigurationSection, WaveManager, WaveSession, NavigableMap, Set, Match, TowerDefense, Integer (+12 more)

### Community 6 - "Plugin Entrypoint & World Mgmt"
Cohesion: 0.15
Nodes (12): GameManager, JavaPlugin, MobManager, PlotConfigManager, QueueManager, SetupManager, Override, TowerConfigManager (+4 more)

### Community 7 - "Mob Pathfinding & Waypoints"
Cohesion: 0.09
Nodes (12): TDWaypoint, TDMob, List, Location, String, TDMob, List, Location (+4 more)

### Community 8 - "Queue & Map Selection"
Cohesion: 0.09
Nodes (16): QueueManager, VotingSession, MapData, MapManager, MapManager, List, MapData, Player (+8 more)

### Community 9 - "Setup & Wand Tooling"
Cohesion: 0.20
Nodes (9): Color, WandListener, EventHandler, Location, Player, PlayerInteractEvent, String, TDWaypoint (+1 more)

### Community 10 - "Match State"
Cohesion: 0.08
Nodes (18): Match, Long, MapData, TowerDefense, ArmorStand, BossBar, GameState, Integer (+10 more)

### Community 11 - "Plot Configuration"
Cohesion: 0.19
Nodes (8): PlotConfigManager, File, Location, Map, Match, String, TowerDefense, World

### Community 12 - "Waypoint Config"
Cohesion: 0.15
Nodes (10): WaypointConfigManager, File, List, Location, Map, Match, String, TDWaypoint (+2 more)

### Community 13 - "Preset Mob Types"
Cohesion: 0.17
Nodes (9): getColor(), getDisplayName(), getEntityType(), getMaterial(), PresetMobType(), ChatColor, EntityType, Material (+1 more)

### Community 14 - "Tower Configs (Batch 1)"
Cohesion: 0.21
Nodes (10): Tower Defense Update 3, Golem Tower Target Acquisition Fix, Mob Level Reset Per Match, Archer Tower, Fire Tower, Golem Tower, Happy Ghast Tower, Ice Tower (+2 more)

### Community 15 - "Tower Configs (Batch 2)"
Cohesion: 0.19
Nodes (15): Beehive Goliath Path, Beehive Swarm Path, Tower Defense Fixes Batch 7, Tower Defense Fixes Batch 8, Tower Defense Fixes Batch 9, Beehive I-Frames Fix, Dripstone Mechanics Fix, EMP Targeting Fix (+7 more)

### Community 16 - "Tower Type Enum"
Cohesion: 0.23
Nodes (8): ChatColor, Material, String, getBaseMaterial(), getColor(), getDisplayName(), getMiddleMaterial(), TowerType()

### Community 17 - "Game Config & Spells"
Cohesion: 0.15
Nodes (12): Max Castle Health Setting, Players Per Match Setting, Starting Gold Setting, Damage Storm Spell, Freeze Spell, Haste Rush Spell, Overcharge Spell, Slow Shield Spell (+4 more)

### Community 18 - "Wave Mob Roster"
Cohesion: 0.20
Nodes (9): Blaze Mob, Creeper Mob, Ghast Mob, Zombie Pigman Mob, Silverfish Mob, Skeleton Mob, Slime/Magma Cube Mob, Spider Mob (+1 more)

### Community 19 - "Pathing & Siege Fixes"
Cohesion: 0.29
Nodes (8): Tower Defense Polish Fixes, Tower Defense Progression Fixes, UI Pathing and Siege Combined Fixes, Universal Castle Attacking Fix, Chorus Tower Teleportation Fix, Giant Zombie Pathing Fix, Giant Mob, Chorus Tower

### Community 20 - "Velocity & Wave Fixes"
Cohesion: 0.29
Nodes (7): Tower Defense Claude V2 Update, Tower Defense Update 2, Health-Based Speed Scaling, Universal Velocity Movement Fix, Wave GUI Enhancements, Tower EMP Spell, Wave System

### Community 21 - "Plugin Manifest"
Cohesion: 0.29
Nodes (6): Classpath Dependencies, Kyori Adventure API 4.26.1, Google Guava 33.5.0, Paper API 26.1.2, SnakeYAML 2.2, TowerDefense Main Plugin Class

### Community 22 - "Balancing Docs"
Cohesion: 0.17
Nodes (11): 1. Mob Statistics, Costs, & Rewards, 2. Player Upgrades & Shops, 3. Tower Progression Balances, Melee Sword Upgrades, Mob Statistics & Rewards, Passive Gold Generator, Permanent Splash Potion Purchases, Player Upgrades (Sword/Bow/Gold Generator) (+3 more)

### Community 23 - "Targeting Modes"
Cohesion: 0.67
Nodes (3): String, getDisplayName(), TargetingMode()

### Community 28 - "Community 28"
Cohesion: 0.06
Nodes (34): 10. Map Configuration Files Reference, 11. Common Issues, 12. Quick Setup Checklist, 1. Map File Structure, 2. Prepare Your World Folder, 3. Create map.yml, 4. Load the World, 5.1 Get the Setup Wand (+26 more)

### Community 29 - "Community 29"
Cohesion: 0.08
Nodes (24): 1.10 Dripstone Tower (`dripstone`), 1.11 Thunder Tower (`thunder`), 1.1 Archer Tower (`archer`), 1.2 Fire Tower (`fire`), 1.3 Prismarine Tower (`prismarine`), 1.4 Chorus Tower (`chorus`), 1.5 Redstone Tower (`redstone`), 1.6 Poison Tower (`poison`) (+16 more)

### Community 31 - "Community 31"
Cohesion: 0.12
Nodes (21): bug_fixing/mob_implementation_guide.md, mob_upgrades_polymorphic.csv, bug_fixing/MOB_UPDATE_6_6_25.md, Queue Migration: PresetMobType → Chain String Keys, Mob Tier Sub-GUI (openMobTierGUI), Critical Missing Files — Must Be Created, Deployment, Git History (container state) (+13 more)

### Community 32 - "Community 32"
Cohesion: 0.11
Nodes (18): Changes to `MobListener.java`, Changes to `MobManager.java`, `getQueue(UUID)` updated, MOB Update – 6.6.25, `MobStateProfile.java`, `MobUpgradeRegistry.java`, New `addToQueue(UUID, String)` / `removeFromQueue(UUID, String)` overloads, New files (+10 more)

### Community 33 - "Community 33"
Cohesion: 0.22
Nodes (9): Command, CommandExecutor, CommandSender, TDCommand, File, Override, Player, String (+1 more)

### Community 34 - "Community 34"
Cohesion: 0.20
Nodes (3): PlayerJoinEvent, Player, getInt()

### Community 35 - "Community 35"
Cohesion: 0.29
Nodes (4): SetupManager, SetupState, String, UUID

### Community 36 - "Community 36"
Cohesion: 0.13
Nodes (15): 1. Newly Implemented Features, 2. Code Modifications, 3. Server Directory & Troubleshooting, Directory Structure, [GameManager.java](file:///C:/Users/pjang/IdeaProjects/Tower%20Defense/src/main/java/com/pauljang/towerDefense/core/GameManager.java), Golem Tower (5x5), Golem Tower (5x5 Size), Happy Ghast Tower (5x5 Size) (+7 more)

### Community 37 - "Community 37"
Cohesion: 0.13
Nodes (14): 1. Removing the Standalone Endermite from GUI, 1. Stop Disabling AI (`MobManager.java`), 2. Consolidate and Fix the Velocity Condition, 2. Updating Silverfish Lvl 4 & 5 Attributes, 3. Prevent Endermen Teleporting (`MobListener.java`), Analysis of the Harness Crash, Fix the Material Enum (`TowerManager` / `HappyGhast` initialization class), Part 1: Pathfinding System and Movement Fixes (+6 more)

### Community 38 - "Community 38"
Cohesion: 0.15
Nodes (13): 1. Archer Tower (`archer`), 2. Fire Tower (`fire`), 3. Prismarine Tower (`prismarine`), 4. Chorus Tower (`chorus`), 5. Redstone Tower (`redstone`), 6. Poison Tower (`poison`), 7. Ice Tower (`ice`), Fire Tower (+5 more)

### Community 39 - "Community 39"
Cohesion: 0.15
Nodes (12): 1. Global Speed Calibration & HP Normalization, 2. Golem Tower Target Acquisition Fix, 3. Happy Ghast Hover Targeting Adjustments, 4. Mob Level Reset & Session Cleanup, 5. Player Compass Inventory Allocation, Dynamic Range Offset, Global Speed Constant Reduction, Issue: Copper & Iron Golems Ignore Mobs (+4 more)

### Community 40 - "Community 40"
Cohesion: 0.28
Nodes (7): WorldUnloadListener, EventHandler, File, PlayerChangedWorldEvent, PlayerQuitEvent, TowerDefense, World

### Community 41 - "Community 41"
Cohesion: 0.15
Nodes (12): 1. Project Overview, 2. Architecture & File Structure, 3. Current Progress (Phase 1 Completed), 4. Immediate Next Steps (Phase 2), Package: `.core` (Game Engine & Commands), Package: `.data` (File Persistence), Package: `.listeners` (Event Handling), Package: `.setup` (Admin Setup State) (+4 more)

### Community 42 - "Community 42"
Cohesion: 0.17
Nodes (11): 10. CSV Adjustments (mob_upgrades_polymorphic.csv), 1. Queueing Mechanics & Mixed Mob Levels, 2. Universal Velocity Movement & Collision, 3. Spell Cooldowns & Dynamic Cost Scaling, 4. Spell Adjustments (Overcharge & Freeze), 5. Mob Immunities & Status Display Fixes, 6. Chorus Tower Teleportation & Visual Glitch Fix, 7. Tower Fixes (EMP) (+3 more)

### Community 43 - "Community 43"
Cohesion: 0.18
Nodes (10): 1. Matchmaking Queue Timer & /td forcestart, 2. /td challenge "Game Ended" Screen Flash, 3. Chorus Tower Teleportation Fail, 4. Ice Tower Slowness Symbol Bug, 5. Witch Potion Drinking & Healing Mechanics, 6. Warden Aggression & Sonic Boom, 7. Giant Zombie Summoning Coordinates & Pathing, 8. Player Bow Does No Damage (+2 more)

### Community 44 - "Community 44"
Cohesion: 0.18
Nodes (10): 1. Chorus Tower Teleportation Fix, 2. Witch & Warden AI Fixes, 3. Mob Spawning GUI Overhaul, 4. Unlock Queue Bug, 5. Breeze Flying Fix, 6. Slow vs. Freeze Disambiguation, 7. Giant Zombie Summoning, 8. Invisible Spider Health Bars & Tower Targeting (+2 more)

### Community 45 - "Community 45"
Cohesion: 0.27
Nodes (10): TowerConfigManager.java, PresetMobType.java (Enum), SetupManager.java, TargetingMode.java (Enum), TDCommand.java, Tower.java (Instance Model), TowerManager.java, TowerType.java (Enum) (+2 more)

### Community 46 - "Community 46"
Cohesion: 0.25
Nodes (5): Collection, BossBar, ItemStack, List, Material

### Community 47 - "Community 47"
Cohesion: 0.20
Nodes (9): 1. Wave GUI Enhancements ("Send Wave" Button), 2. Warden Behavior Adjustments, 3. Health-Based Speed Scaling, 4. Sequential Mob Level Unlocking, Feature: Display Queued Mobs and Total XP Payout, Feature: Remove Warden Blindness (Darkness Effect), Feature: Require Linear Progression (Level 1 -> 2 -> 3), Feature: Speed Inversely Proportional to Health (+1 more)

### Community 48 - "Community 48"
Cohesion: 0.29
Nodes (8): Arena, Map Template (GAME_WORLD_TEMPLATES), Plot (5x5 Tower Build Area), Match Class (Isolated Game State), QueueManager, WaveManager (Single Player Wave Engine), World Cloning System, PlotConfigManager.java

### Community 49 - "Community 49"
Cohesion: 0.20
Nodes (10): 1. Main Entrypoint, 2. Core Game Loop & Orchestration (`core/`), 3. Data & Configuration Persistence (`data/`), 4. Custom Mobs & Traversal System (`entities/`), 5. Event Listeners (`listeners/`), 6. Map Construction/Setup (`setup/`), 7. Custom Towers & Summons (`towers/`), Class Breakdown & Descriptions (+2 more)

### Community 50 - "Community 50"
Cohesion: 0.22
Nodes (8): 1. `towers.yaml` Updates, 2. `TowerConfigManager.java` Updates, 3. GUI Updates in `TowerManager.java`, 4. Dripstone Mechanics in `TowerManager.java`, 5. Turret & Bombardier Fixes in `TowerManager.java`, 6. Beehive Mechanics in `TowerManager.java`, 7. 5x5 Tower Placement Validation in `TowerManager.java`, Tower Defense Final Adjustments & Fixes

### Community 51 - "Community 51"
Cohesion: 0.25
Nodes (7): 1. The Data Structure, 2. Core Implementation Rules for AI Agents, Example Configuration Schema (Java/Paper), Implementing Polymorphic Mob Upgrades, Rule 1: Use a Variant State Engine, Rule 2: Dynamic Entity Replacement, Rule 3: Attribute and Equipment Injection

### Community 52 - "Community 52"
Cohesion: 0.25
Nodes (7): 1. `towers.yaml` - Removing Level 4s, 2. General Tower Ticker & Placement (EMP Fixes), 3. Dripstone Mechanics (Syncing, Full Block Waves, & Upside-down fixes), 4. Turret Spread & Bombardier Arcs/Mines, 5. Beehive Updates, 6. Chain Lightning Damage Buff, Tower Defense Adjustments & Fixes

### Community 53 - "Community 53"
Cohesion: 0.25
Nodes (8): 1. GameManager.java (`src/main/java/com/pauljang/towerDefense/core/GameManager.java`), 2. TDCommand.java (`src/main/java/com/pauljang/towerDefense/core/TDCommand.java`), 3. MobListener.java (`src/main/java/com/pauljang/towerDefense/listeners/MobListener.java`), 4. MobManager.java (`src/main/java/com/pauljang/towerDefense/entities/MobManager.java`), 5. TowerManager.java (`src/main/java/com/pauljang/towerDefense/towers/TowerManager.java`), 6. Tower.java & TowerType.java, 7. mob_upgrades_polymorphic.csv (`mob_upgrades_polymorphic.csv`), Tower Defense Plugin Update Instructions

### Community 54 - "Community 54"
Cohesion: 0.46
Nodes (8): bug_fixing/tower_defense_final_adjustments.md, Beehive Tower (Goliath/Swarm paths), Bombardier Tower (BiggerBombs/Landmines paths), Branching Path Tower System, Dripstone Tower, bug_fixing/tower_agent_specification.md, Thunder Tower, Turret Tower (Gatling/Scatter paths)

### Community 55 - "Community 55"
Cohesion: 0.25
Nodes (8): 1. Concurrent Match Architecture, 2. Dynamic Map Template System, 3. Matchmaking & Voting System, 4. Single Player Wave Engine, 5. Streamlined Admin Tooling & Template Editing, 6. Map Creation Documentation, 7. Technical Integrity & Bug Fixes, Tower Defense: Multi-Map & Single Player Update Summary

### Community 56 - "Community 56"
Cohesion: 0.29
Nodes (6): 1. Chorus Tower Teleportation Rewrite, 2. Giant's Zombie Pathing Fix, 3. Health Bar Status Symbol Stacking & Coloring, 4. Witch Potion Drinking Prevention, 5. Mob Level GUI Dequeueing, Tower Defense Plugin: Advanced Bug Fixes & Polish (Batch 4)

### Community 57 - "Community 57"
Cohesion: 0.29
Nodes (6): 1. Shift-Click Dequeue in Mob Spawning GUI, 2. Health Bar Symbol Stacking, 3. Giant's Zombie Spawning (Track Projection), 4. Add Attack Cooldown Tracking to TDMob, 5. Continuous Attack Logic at the Castle Door, Tower Defense Plugin: UI, Pathing, & Castle Siege Polish

### Community 58 - "Community 58"
Cohesion: 0.29
Nodes (6): CRITICAL CORE DIRECTIVE, [File Name] - [Class/Method Name], GRAPHIFY EXECUTION DIRECTIVE, STEP 1: GRAPH-BASED BUG HUNTING & STATE MANAGEMENT, STEP 2: PERFORMANCE & TICK-LOOP OPTIMIZATION, STEP 3: REFACTORING DELIVERY FORMAT

### Community 59 - "Community 59"
Cohesion: 0.33
Nodes (5): 1. GUI Queue/Dequeue Logic Relocation & Shift-Click Support, 2. Health Bar Symbol Uncapping (Fixing the 3-Symbol Limit), 3. Giant Sudden Disappearance (Despawn Prevention), 4. Giant's Summoned Zombie Perfect Pathing, Tower Defense Plugin: Advanced UI, Pathing, and Entity Persistence Fixes

### Community 60 - "Community 60"
Cohesion: 0.33
Nodes (5): 1. Dripstone 3D Falling Blocks (`TowerManager.java`), 2. Dripstone 3D Ground Hazards (`TowerManager.java`), 3. Turret Scatter Arrows Bouncing Inside Tower (`TowerManager.java`), 4. Beehive Targeting & Sounds (`TowerManager.java`), Tower Defense Adjustments & Fixes

### Community 61 - "Community 61"
Cohesion: 0.33
Nodes (5): 1. `towers.yaml` - Thunder Tower Tier 3 Global Strike, 2. Dripstone Hazards (Act like Landmines), 3. Turret Scatter Arrow Lifespan, 4. Beehive Slab Fixes & Timeout Safeties, Tower Defense Adjustments & Fixes

### Community 63 - "Community 63"
Cohesion: 0.40
Nodes (4): 1. Remove Compass on Game Start, 2. Giant's Zombie Spawning & Pathing Fix, 3. Universal Castle Attacking (Waypoint Reach Logic), Tower Defense Plugin: Critical Progression & Mechanics Fixes

### Community 65 - "Community 65"
Cohesion: 0.50
Nodes (4): bug_fixing/tower_defense_final_polish.md, Happy Ghast Harness Fix (RED_HARNESS/BLUE_HARNESS), Velocity Branch Fix (setAI removal), Happy Ghast Tower (5x5)

### Community 66 - "Community 66"
Cohesion: 0.67
Nodes (3): Bombardier Bigger Bombs Path, Bombardier Landmines Path, Bombardier Tower

## Knowledge Gaps
- **301 isolated node(s):** `MapData`, `Long`, `VotingSession`, `MapData`, `Override` (+296 more)
  These have ≤1 connection - possible missing edges or undocumented components.
- **5 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `GameManager` connect `Game Manager Core` to `Tower Placement & Lifecycle`, `Community 34`, `Match State`, `Waypoint Config`, `Community 46`, `Community 62`, `Community 30`?**
  _High betweenness centrality (0.030) - this node is a cross-community bridge._
- **Why does `MobListener` connect `Mob Event Listeners` to `Tower Placement & Lifecycle`, `Game Manager Core`, `Community 34`?**
  _High betweenness centrality (0.024) - this node is a cross-community bridge._
- **Why does `Match` connect `Match State` to `Community 34`, `Plot Configuration`, `Community 30`?**
  _High betweenness centrality (0.016) - this node is a cross-community bridge._
- **What connects `MapData`, `Long`, `VotingSession` to the rest of the system?**
  _308 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `Tower Placement & Lifecycle` be split into smaller, more focused modules?**
  _Cohesion score 0.053312008529921365 - nodes in this community are weakly interconnected._
- **Should `Game Manager Core` be split into smaller, more focused modules?**
  _Cohesion score 0.12941176470588237 - nodes in this community are weakly interconnected._
- **Should `Mob Spawning & Upgrades` be split into smaller, more focused modules?**
  _Cohesion score 0.06080246913580247 - nodes in this community are weakly interconnected._