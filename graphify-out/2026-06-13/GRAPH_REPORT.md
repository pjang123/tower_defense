# Graph Report - Tower Defense  (2026-06-13)

## Corpus Check
- 63 files · ~85,248 words
- Verdict: corpus is large enough that graph structure adds value.

## Summary
- 1291 nodes · 3092 edges · 76 communities (66 shown, 10 thin omitted)
- Extraction: 70% EXTRACTED · 30% INFERRED · 0% AMBIGUOUS · INFERRED: 927 edges (avg confidence: 0.8)
- Token cost: 0 input · 0 output

## Graph Freshness
- Built from commit: `4fa3753b`
- Run `git rev-parse HEAD` and compare to check if the graph is stale.
- Run `graphify update .` after code changes (no API cost).

## Community Hubs (Navigation)
- [[_COMMUNITY_Tower Placement & Mob Movement|Tower Placement & Mob Movement]]
- [[_COMMUNITY_Game State & Bukkit Events|Game State & Bukkit Events]]
- [[_COMMUNITY_Plot Configuration Management|Plot Configuration Management]]
- [[_COMMUNITY_Tower Config Types & Definitions|Tower Config Types & Definitions]]
- [[_COMMUNITY_Matchmaking Queue & Voting|Matchmaking Queue & Voting]]
- [[_COMMUNITY_Match State & Economy|Match State & Economy]]
- [[_COMMUNITY_Custom Mob (TDMob) Logic|Custom Mob (TDMob) Logic]]
- [[_COMMUNITY_Map Setup Guide|Map Setup Guide]]
- [[_COMMUNITY_GameManager Cleanup & Lifecycle|GameManager Cleanup & Lifecycle]]
- [[_COMMUNITY_Mob Spawner GUI|Mob Spawner GUI]]
- [[_COMMUNITY_Player Stats & Upgrades|Player Stats & Upgrades]]
- [[_COMMUNITY_TD Command Handling|TD Command Handling]]
- [[_COMMUNITY_Waypoint Configuration|Waypoint Configuration]]
- [[_COMMUNITY_Tower Agent Specification|Tower Agent Specification]]
- [[_COMMUNITY_Mob Implementation Guide|Mob Implementation Guide]]
- [[_COMMUNITY_Code Fixes Batch 6|Code Fixes Batch 6]]
- [[_COMMUNITY_Mob Queue Management|Mob Queue Management]]
- [[_COMMUNITY_GameManager GUI Items|GameManager GUI Items]]
- [[_COMMUNITY_Mob Update 6.6.25|Mob Update 6.6.25]]
- [[_COMMUNITY_Final Tower Adjustments|Final Tower Adjustments]]
- [[_COMMUNITY_Project Summary|Project Summary]]
- [[_COMMUNITY_Preset Mob Types|Preset Mob Types]]
- [[_COMMUNITY_MobManager Internals|MobManager Internals]]
- [[_COMMUNITY_Match Lifecycle & Spells|Match Lifecycle & Spells]]
- [[_COMMUNITY_Wave Management|Wave Management]]
- [[_COMMUNITY_Core Plugin Dependencies|Core Plugin Dependencies]]
- [[_COMMUNITY_Mob Upgrade Registry|Mob Upgrade Registry]]
- [[_COMMUNITY_Fixes Batch 7|Fixes Batch 7]]
- [[_COMMUNITY_World Unload Listener|World Unload Listener]]
- [[_COMMUNITY_Tower Type Materials|Tower Type Materials]]
- [[_COMMUNITY_Towers Reference Doc|Towers Reference Doc]]
- [[_COMMUNITY_Branching Tower Paths|Branching Tower Paths]]
- [[_COMMUNITY_Polish & Progression Fixes|Polish & Progression Fixes]]
- [[_COMMUNITY_Mob AI & Velocity Fixes|Mob AI & Velocity Fixes]]
- [[_COMMUNITY_Update 3 Balancing|Update 3 Balancing]]
- [[_COMMUNITY_Config & Spell Settings|Config & Spell Settings]]
- [[_COMMUNITY_Multi-Map & Singleplayer Update|Multi-Map & Singleplayer Update]]
- [[_COMMUNITY_Project Description|Project Description]]
- [[_COMMUNITY_Claude V2 Update|Claude V2 Update]]
- [[_COMMUNITY_Project Structure|Project Structure]]
- [[_COMMUNITY_Preliminary Balancing|Preliminary Balancing]]
- [[_COMMUNITY_Waypoint Traversal|Waypoint Traversal]]
- [[_COMMUNITY_Mob Types|Mob Types]]
- [[_COMMUNITY_Architecture Overview|Architecture Overview]]
- [[_COMMUNITY_Wave GUI & Warden Fixes|Wave GUI & Warden Fixes]]
- [[_COMMUNITY_Polymorphic Mob Upgrades|Polymorphic Mob Upgrades]]
- [[_COMMUNITY_Core File Modifications|Core File Modifications]]
- [[_COMMUNITY_Velocity Branch Fixes|Velocity Branch Fixes]]
- [[_COMMUNITY_Branching Path Towers|Branching Path Towers]]
- [[_COMMUNITY_Active Match Tracking|Active Match Tracking]]
- [[_COMMUNITY_Map Setup Concepts|Map Setup Concepts]]
- [[_COMMUNITY_Final Polish Fixes|Final Polish Fixes]]
- [[_COMMUNITY_Update 2 Fixes|Update 2 Fixes]]
- [[_COMMUNITY_Classpath Dependencies|Classpath Dependencies]]
- [[_COMMUNITY_Gemini Audit Directive|Gemini Audit Directive]]
- [[_COMMUNITY_Dripstone & Bombardier Mechanics|Dripstone & Bombardier Mechanics]]
- [[_COMMUNITY_Claude V2 Tower Fixes|Claude V2 Tower Fixes]]
- [[_COMMUNITY_Tower Adjustments Batch|Tower Adjustments Batch]]
- [[_COMMUNITY_Progression Fixes|Progression Fixes]]
- [[_COMMUNITY_TDKeys NamespacedKeys|TDKeys NamespacedKeys]]
- [[_COMMUNITY_Architecture Audit Directives|Architecture Audit Directives]]
- [[_COMMUNITY_Targeting Mode Enum|Targeting Mode Enum]]
- [[_COMMUNITY_Beehive Tower Mechanics|Beehive Tower Mechanics]]
- [[_COMMUNITY_Turret Scatter Mechanics|Turret Scatter Mechanics]]
- [[_COMMUNITY_State Desync Bugs|State Desync Bugs]]
- [[_COMMUNITY_World File Lock Bugs|World File Lock Bugs]]
- [[_COMMUNITY_Thunder Tower Strike|Thunder Tower Strike]]
- [[_COMMUNITY_Wand Plot Hash Bug|Wand Plot Hash Bug]]
- [[_COMMUNITY_Gemini Code (Java) 2|Gemini Code (Java) 2]]
- [[_COMMUNITY_Multiplayer Map Config|Multiplayer Map Config]]
- [[_COMMUNITY_Singleplayer Map Config|Singleplayer Map Config]]
- [[_COMMUNITY_EMP Disable Fix|EMP Disable Fix]]
- [[_COMMUNITY_Community 75|Community 75]]

## God Nodes (most connected - your core abstractions)
1. `GameManager` - 98 edges
2. `Tower` - 42 edges
3. `TowerManager` - 42 edges
4. `MobListener` - 38 edges
5. `MobManager` - 34 edges
6. `EventHandler` - 34 edges
7. `UUID` - 32 edges
8. `String` - 29 edges
9. `Match` - 29 edges
10. `TDMob` - 26 edges

## Surprising Connections (you probably didn't know these)
- `TowerConfigManager.java` --used_by--> `TowerManager.java`  [INFERRED]
  bug_fixing/tower_defense_final_adjustments.md → PROJECT_STRUCTURE.md
- `Graphify-Based Architecture Audit Directive` --conceptually_related_to--> `Graphify PreToolUse Enforcement Hook`  [INFERRED]
  gemini-code-1781158577643.md → .claude/settings.json
- `Dripstone 3D BlockDisplay Rendering` --semantically_similar_to--> `Dripstone Strike Impact-Synced Damage (ArmorStand)`  [INFERRED] [semantically similar]
  tower_defense_fixes_batch8.md → tower_defense_fixes_batch7.md
- `Turret Scatter Forward Safe-Spawn Offset` --semantically_similar_to--> `Turret Scatter Spread Mechanics`  [INFERRED] [semantically similar]
  tower_defense_fixes_batch8.md → tower_defense_fixes_batch7.md
- `Dripstone Hazards As Dynamic Landmines (Ticker)` --semantically_similar_to--> `Bombardier Arc & Landmine Triggers`  [INFERRED] [semantically similar]
  tower_defense_fixes_batch9.md → tower_defense_fixes_batch7.md

## Import Cycles
- None detected.

## Hyperedges (group relationships)
- **Dripstone Mechanic Evolution (ArmorStand to BlockDisplay to Landmine Ticker)** — tower_defense_fixes_batch7_dripstone_strike, tower_defense_fixes_batch8_dripstone_blockdisplay, tower_defense_fixes_batch9_dripstone_landmine, towers_dripstone [INFERRED 0.85]
- **Beehive Swarm Targeting Fix Progression** — tower_defense_fixes_batch7_beehive_iframes, tower_defense_fixes_batch8_beehive_targeting, tower_defense_fixes_batch9_beehive_slab_timeout, towers_beehive [INFERRED 0.80]
- **Match World Lifecycle State & File-Lock Bug Cluster** — bug_report_issue1_game_not_starting, bug_report_issue4_template_plots_waypoints, bug_report_issue5_match_world_dimensions_folder, bug_report_world_file_lock_cleanup [INFERRED 0.80]

## Communities (76 total, 10 thin omitted)

### Community 0 - "Tower Placement & Mob Movement"
Cohesion: 0.05
Nodes (41): Bee, BlockDisplay, getHealth(), HappyGhast, InventoryClickEvent, Iterator, Method, Particle (+33 more)

### Community 1 - "Game State & Bukkit Events"
Cohesion: 0.08
Nodes (24): BlockFadeEvent, CreatureSpawnEvent, EntityCombustEvent, EntityDamageByEntityEvent, EntityDamageEvent, EntityDismountEvent, EntityExplodeEvent, EntityKnockbackEvent (+16 more)

### Community 2 - "Plot Configuration Management"
Cohesion: 0.08
Nodes (22): Color, PlotConfigManager, WandListener, SetupManager, SetupState, File, List, Location (+14 more)

### Community 3 - "Tower Config Types & Definitions"
Cohesion: 0.09
Nodes (20): ConfigurationSection, WaveManager, WaveSession, NavigableMap, Set, TowerDefense, Integer, Map (+12 more)

### Community 4 - "Matchmaking Queue & Voting"
Cohesion: 0.06
Nodes (26): QueueManager, VotingSession, GameManager, Inventory, InventoryHolder, JavaPlugin, MapManager, MenuType (+18 more)

### Community 5 - "Match State & Economy"
Cohesion: 0.10
Nodes (12): Match, MapData, Match, BossBar, GameState, List, MapData, Player (+4 more)

### Community 6 - "Custom Mob (TDMob) Logic"
Cohesion: 0.10
Nodes (11): TDWaypoint, TDMob, List, Location, String, List, Location, Map (+3 more)

### Community 7 - "Map Setup Guide"
Cohesion: 0.06
Nodes (34): 10. Map Configuration Files Reference, 11. Common Issues, 12. Quick Setup Checklist, 1. Map File Structure, 2. Prepare Your World Folder, 3. Create map.yml, 4. Load the World, 5.1 Get the Setup Wand (+26 more)

### Community 8 - "GameManager Cleanup & Lifecycle"
Cohesion: 0.17
Nodes (3): GameManager, File, PlayerChangedWorldEvent

### Community 9 - "Mob Spawner GUI"
Cohesion: 0.06
Nodes (32): Collection, MobManager, MobStateProfile, MobUpgradeRegistry, EntityDeathEvent, MobUpgradeRegistry, PresetMobType, ChatColor (+24 more)

### Community 11 - "TD Command Handling"
Cohesion: 0.11
Nodes (18): Command, CommandExecutor, CommandSender, TDCommand, WorldUnloadListener, Path, SetupManager, File (+10 more)

### Community 12 - "Waypoint Configuration"
Cohesion: 0.16
Nodes (10): WaypointConfigManager, File, List, Location, Map, Match, String, TDWaypoint (+2 more)

### Community 13 - "Tower Agent Specification"
Cohesion: 0.08
Nodes (24): 1.10 Dripstone Tower (`dripstone`), 1.11 Thunder Tower (`thunder`), 1.1 Archer Tower (`archer`), 1.2 Fire Tower (`fire`), 1.3 Prismarine Tower (`prismarine`), 1.4 Chorus Tower (`chorus`), 1.5 Redstone Tower (`redstone`), 1.6 Poison Tower (`poison`) (+16 more)

### Community 14 - "Mob Implementation Guide"
Cohesion: 0.11
Nodes (22): bug_fixing/mob_implementation_guide.md, mob_upgrades_polymorphic.csv, bug_fixing/MOB_UPDATE_6_6_25.md, Queue Migration: PresetMobType → Chain String Keys, Mob Tier Sub-GUI (openMobTierGUI), PresetMobType.java (Enum), Critical Missing Files — Must Be Created, Deployment (+14 more)

### Community 15 - "Code Fixes Batch 6"
Cohesion: 0.09
Nodes (20): 1. Matchmaking Queue Timer & /td forcestart, 2. /td challenge "Game Ended" Screen Flash, 3. Chorus Tower Teleportation Fail, 4. Ice Tower Slowness Symbol Bug, 5. Witch Potion Drinking & Healing Mechanics, 6. Warden Aggression & Sonic Boom, 7. Giant Zombie Summoning Coordinates & Pathing, 8. Player Bow Does No Damage (+12 more)

### Community 16 - "Mob Queue Management"
Cohesion: 0.13
Nodes (8): Double, Long, ArmorStand, Integer, Map, String, Tower, UUID

### Community 17 - "GameManager GUI Items"
Cohesion: 0.10
Nodes (10): Objective, Scoreboard, BossBar, ChatColor, ItemStack, List, Material, String (+2 more)

### Community 18 - "Mob Update 6.6.25"
Cohesion: 0.11
Nodes (18): Changes to `MobListener.java`, Changes to `MobManager.java`, `getQueue(UUID)` updated, MOB Update – 6.6.25, `MobStateProfile.java`, `MobUpgradeRegistry.java`, New `addToQueue(UUID, String)` / `removeFromQueue(UUID, String)` overloads, New files (+10 more)

### Community 19 - "Final Tower Adjustments"
Cohesion: 0.13
Nodes (20): 1. `towers.yaml` Updates, 2. `TowerConfigManager.java` Updates, 3. GUI Updates in `TowerManager.java`, 4. Dripstone Mechanics in `TowerManager.java`, 5. Turret & Bombardier Fixes in `TowerManager.java`, 6. Beehive Mechanics in `TowerManager.java`, 7. 5x5 Tower Placement Validation in `TowerManager.java`, Tower Defense Final Adjustments & Fixes (+12 more)

### Community 20 - "Project Summary"
Cohesion: 0.12
Nodes (16): 1. Newly Implemented Features, 2. Code Modifications, 3. Server Directory & Troubleshooting, Directory Structure, [GameManager.java](file:///C:/Users/pjang/IdeaProjects/Tower%20Defense/src/main/java/com/pauljang/towerDefense/core/GameManager.java), Golem Tower (5x5), Golem Tower (5x5 Size), Happy Ghast Tower (5x5) (+8 more)

### Community 21 - "Preset Mob Types"
Cohesion: 0.17
Nodes (9): getColor(), getDisplayName(), getEntityType(), getMaterial(), PresetMobType(), ChatColor, EntityType, Material (+1 more)

### Community 22 - "MobManager Internals"
Cohesion: 0.23
Nodes (6): MapData, MapManager, File, MapData, String, TowerDefense

### Community 23 - "Match Lifecycle & Spells"
Cohesion: 0.17
Nodes (4): Player, Override, List, Override

### Community 24 - "Wave Management"
Cohesion: 0.13
Nodes (14): 1. Performance & Server Lag (TPS) Optimizations, 2. Architecture & OOP (Code Smells), 3. Safety & Bukkit API Practices, Blatant Code Duplication, Data Fragmentation (`GameManager.java`), Expensive Entity Lookups (`TowerManager.java`), Heavy Ticker Operations (`TowerManager.java`), Inventory Title Matching (`MobListener.java`) (+6 more)

### Community 25 - "Core Plugin Dependencies"
Cohesion: 0.17
Nodes (11): Bug Report & Code Audit Fixes, Code Fixes, Fix 1 — `GameManager.java` (Game Startup, Placement GUI, and Deletions), Fix 2 — `endMatch` File Lock Deletions in `GameManager.java`, Fix 3 — `WandListener.java` Human-Readable Plot Output, Issue 1: Game Wasn't Starting After Timer Ended (Missing Scoreboards/Towers), Issue 2: Player Allowed to Queue While About to Be Transported, Issue 3: Some TD Commands Broke (`/td loadworld` / `/td unloadworld`) (+3 more)

### Community 26 - "Mob Upgrade Registry"
Cohesion: 0.22
Nodes (8): 1. Concurrent Match Architecture, 2. Dynamic Map Template System, 3. Matchmaking & Voting System, 4. Single Player Wave Engine, 5. Streamlined Admin Tooling & Template Editing, 6. Map Creation Documentation, 7. Technical Integrity & Bug Fixes, Tower Defense: Multi-Map & Single Player Update Summary

### Community 27 - "Fixes Batch 7"
Cohesion: 0.18
Nodes (14): Beehive Goliath Path, Beehive Swarm Path, Tower Defense Fixes Batch 7, Tower Defense Claude V2 Update, Beehive I-Frames Fix, Dripstone Mechanics Fix, EMP Targeting Fix, Turret Scatter Fix (+6 more)

### Community 28 - "World Unload Listener"
Cohesion: 0.29
Nodes (6): CRITICAL CORE DIRECTIVE, [File Name] - [Class/Method Name], GRAPHIFY EXECUTION DIRECTIVE, STEP 1: GRAPH-BASED BUG HUNTING & STATE MANAGEMENT, STEP 2: PERFORMANCE & TICK-LOOP OPTIMIZATION, STEP 3: REFACTORING DELIVERY FORMAT

### Community 29 - "Tower Type Materials"
Cohesion: 0.23
Nodes (8): ChatColor, Material, String, getBaseMaterial(), getColor(), getDisplayName(), getMiddleMaterial(), TowerType()

### Community 30 - "Towers Reference Doc"
Cohesion: 0.25
Nodes (8): 1. Archer Tower (`archer`), 2. Fire Tower (`fire`), 3. Prismarine Tower (`prismarine`), 4. Chorus Tower (`chorus`), 5. Redstone Tower (`redstone`), 6. Poison Tower (`poison`), 7. Ice Tower (`ice`), Tower Defense: Tower Reference Guide

### Community 31 - "Branching Tower Paths"
Cohesion: 0.20
Nodes (10): Bombardier Bigger Bombs Path, Bombardier Landmines Path, Freeze Spell, Archer Tower, Bombardier Tower, Dripstone Tower, Fire Tower, Ice Tower (+2 more)

### Community 32 - "Polish & Progression Fixes"
Cohesion: 0.15
Nodes (13): Tower Defense Polish Fixes, Tower Defense Progression Fixes, 1. Shift-Click Dequeue in Mob Spawning GUI, 2. Health Bar Symbol Stacking, 3. Giant's Zombie Spawning (Track Projection), 4. Add Attack Cooldown Tracking to TDMob, 5. Continuous Attack Logic at the Castle Door, Tower Defense Plugin: UI, Pathing, & Castle Siege Polish (+5 more)

### Community 33 - "Mob AI & Velocity Fixes"
Cohesion: 0.15
Nodes (14): 1. Removing the Standalone Endermite from GUI, 1. Stop Disabling AI (`MobManager.java`), 2. Consolidate and Fix the Velocity Condition, 2. Updating Silverfish Lvl 4 & 5 Attributes, 3. Prevent Endermen Teleporting (`MobListener.java`), Analysis of the Harness Crash, Fix the Material Enum (`TowerManager` / `HappyGhast` initialization class), Part 1: Pathfinding System and Movement Fixes (+6 more)

### Community 34 - "Update 3 Balancing"
Cohesion: 0.15
Nodes (12): 1. Global Speed Calibration & HP Normalization, 2. Golem Tower Target Acquisition Fix, 3. Happy Ghast Hover Targeting Adjustments, 4. Mob Level Reset & Session Cleanup, 5. Player Compass Inventory Allocation, Dynamic Range Offset, Global Speed Constant Reduction, Issue: Copper & Iron Golems Ignore Mobs (+4 more)

### Community 35 - "Config & Spell Settings"
Cohesion: 0.17
Nodes (11): Max Castle Health Setting, Players Per Match Setting, Starting Gold Setting, Damage Storm Spell, Haste Rush Spell, Overcharge Spell, Slow Shield Spell, Prismarine Tower (+3 more)

### Community 37 - "Project Description"
Cohesion: 0.14
Nodes (13): 1. Project Overview, 2. Architecture & File Structure, 3. Current Progress (Phase 1 Completed), 4. Immediate Next Steps (Phase 2), Package: `.core` (Game Engine & Commands), Package: `.data` (File Persistence), Package: `.listeners` (Event Handling), Package: `.setup` (Admin Setup State) (+5 more)

### Community 38 - "Claude V2 Update"
Cohesion: 0.17
Nodes (11): 10. CSV Adjustments (mob_upgrades_polymorphic.csv), 1. Queueing Mechanics & Mixed Mob Levels, 2. Universal Velocity Movement & Collision, 3. Spell Cooldowns & Dynamic Cost Scaling, 4. Spell Adjustments (Overcharge & Freeze), 5. Mob Immunities & Status Display Fixes, 6. Chorus Tower Teleportation & Visual Glitch Fix, 7. Tower Fixes (EMP) (+3 more)

### Community 39 - "Project Structure"
Cohesion: 0.43
Nodes (7): SetupManager.java, TargetingMode.java (Enum), TDCommand.java, Tower.java (Instance Model), TowerManager.java, TowerType.java (Enum), WandListener.java

### Community 40 - "Preliminary Balancing"
Cohesion: 0.17
Nodes (11): 1. Mob Statistics, Costs, & Rewards, 2. Player Upgrades & Shops, 3. Tower Progression Balances, Melee Sword Upgrades, Mob Statistics & Rewards, Passive Gold Generator, Permanent Splash Potion Purchases, Player Upgrades (Sword/Bow/Gold Generator) (+3 more)

### Community 41 - "Waypoint Traversal"
Cohesion: 0.33
Nodes (6): Archer Tower, Fire Tower, Ice Tower (Freeze), Poison Tower, Prismarine Tower (Slow), Redstone Tower (Speed Boost)

### Community 42 - "Mob Types"
Cohesion: 0.20
Nodes (9): Blaze Mob, Creeper Mob, Ghast Mob, Zombie Pigman Mob, Silverfish Mob, Skeleton Mob, Slime/Magma Cube Mob, Spider Mob (+1 more)

### Community 43 - "Architecture Overview"
Cohesion: 0.20
Nodes (10): 1. Main Entrypoint, 2. Core Game Loop & Orchestration (`core/`), 3. Data & Configuration Persistence (`data/`), 4. Custom Mobs & Traversal System (`entities/`), 5. Event Listeners (`listeners/`), 6. Map Construction/Setup (`setup/`), 7. Custom Towers & Summons (`towers/`), Class Breakdown & Descriptions (+2 more)

### Community 44 - "Wave GUI & Warden Fixes"
Cohesion: 0.20
Nodes (9): 1. Wave GUI Enhancements ("Send Wave" Button), 2. Warden Behavior Adjustments, 3. Health-Based Speed Scaling, 4. Sequential Mob Level Unlocking, Feature: Display Queued Mobs and Total XP Payout, Feature: Remove Warden Blindness (Darkness Effect), Feature: Require Linear Progression (Level 1 -> 2 -> 3), Feature: Speed Inversely Proportional to Health (+1 more)

### Community 45 - "Polymorphic Mob Upgrades"
Cohesion: 0.25
Nodes (7): 1. The Data Structure, 2. Core Implementation Rules for AI Agents, Example Configuration Schema (Java/Paper), Implementing Polymorphic Mob Upgrades, Rule 1: Use a Variant State Engine, Rule 2: Dynamic Entity Replacement, Rule 3: Attribute and Equipment Injection

### Community 46 - "Core File Modifications"
Cohesion: 0.25
Nodes (8): 1. GameManager.java (`src/main/java/com/pauljang/towerDefense/core/GameManager.java`), 2. TDCommand.java (`src/main/java/com/pauljang/towerDefense/core/TDCommand.java`), 3. MobListener.java (`src/main/java/com/pauljang/towerDefense/listeners/MobListener.java`), 4. MobManager.java (`src/main/java/com/pauljang/towerDefense/entities/MobManager.java`), 5. TowerManager.java (`src/main/java/com/pauljang/towerDefense/towers/TowerManager.java`), 6. Tower.java & TowerType.java, 7. mob_upgrades_polymorphic.csv (`mob_upgrades_polymorphic.csv`), Tower Defense Plugin Update Instructions

### Community 47 - "Velocity Branch Fixes"
Cohesion: 0.36
Nodes (8): bug_fixing/tower_defense_code_fixes_batch6.md, Happy Ghast Harness Fix (RED_HARNESS/BLUE_HARNESS), Velocity Branch Fix (setAI removal), bug_fixing/tower_defense_fixes.md, MobListener.java, MobManager.java, TDMob.java, Mob Movement Architecture (Velocity vs Pathfinder)

### Community 48 - "Branching Path Towers"
Cohesion: 0.39
Nodes (9): bug_fixing/tower_defense_final_adjustments.md, TowerConfigManager.java, Beehive Tower (Goliath/Swarm paths), Bombardier Tower (BiggerBombs/Landmines paths), Branching Path Tower System, Dripstone Tower, bug_fixing/tower_agent_specification.md, Thunder Tower (+1 more)

### Community 49 - "Active Match Tracking"
Cohesion: 0.17
Nodes (4): PlayerJoinEvent, PlayerRespawnEvent, GameState, PlayerInteractEvent

### Community 50 - "Map Setup Concepts"
Cohesion: 0.31
Nodes (8): Arena, Map Template (GAME_WORLD_TEMPLATES), Plot (5x5 Tower Build Area), Waypoint (Mob Path Node), World Cloning System, PlotConfigManager.java, WaypointConfigManager.java, Chorus Tower (Teleport Back)

### Community 51 - "Final Polish Fixes"
Cohesion: 0.29
Nodes (6): 1. Chorus Tower Teleportation Rewrite, 2. Giant's Zombie Pathing Fix, 3. Health Bar Status Symbol Stacking & Coloring, 4. Witch Potion Drinking Prevention, 5. Mob Level GUI Dequeueing, Tower Defense Plugin: Advanced Bug Fixes & Polish (Batch 4)

### Community 52 - "Update 2 Fixes"
Cohesion: 0.22
Nodes (9): Tower Defense Update 2, Golem Tower Target Acquisition Fix, Health-Based Speed Scaling, Mob Level Reset Per Match, Universal Velocity Movement Fix, Wave GUI Enhancements, Golem Tower, Happy Ghast Tower (+1 more)

### Community 53 - "Classpath Dependencies"
Cohesion: 0.29
Nodes (6): Classpath Dependencies, Kyori Adventure API 4.26.1, Google Guava 33.5.0, Paper API 26.1.2, SnakeYAML 2.2, TowerDefense Main Plugin Class

### Community 54 - "Gemini Audit Directive"
Cohesion: 0.33
Nodes (5): 🧟 Mob Fixes & Balancing, 🛡️ Player & Interaction Changes, Tower Defense Game Updates & Fixes, 🗼 Tower & Spell Fixes, 📊 UI, Match Flow & End of Game

### Community 55 - "Dripstone & Bombardier Mechanics"
Cohesion: 0.40
Nodes (6): Bombardier Arc & Landmine Triggers, Dripstone Strike Impact-Synced Damage (ArmorStand), Dripstone 3D BlockDisplay Rendering, Dripstone Hazards As Dynamic Landmines (Ticker), Bombardier Tower Config (Bombs/Landmines Paths), Dripstone Tower Config

### Community 56 - "Claude V2 Tower Fixes"
Cohesion: 0.33
Nodes (5): 1. Dripstone 3D Falling Blocks (`TowerManager.java`), 2. Dripstone 3D Ground Hazards (`TowerManager.java`), 3. Turret Scatter Arrows Bouncing Inside Tower (`TowerManager.java`), 4. Beehive Targeting & Sounds (`TowerManager.java`), Tower Defense Adjustments & Fixes

### Community 57 - "Tower Adjustments Batch"
Cohesion: 0.33
Nodes (5): 1. `towers.yaml` - Thunder Tower Tier 3 Global Strike, 2. Dripstone Hazards (Act like Landmines), 3. Turret Scatter Arrow Lifespan, 4. Beehive Slab Fixes & Timeout Safeties, Tower Defense Adjustments & Fixes

### Community 58 - "Progression Fixes"
Cohesion: 0.40
Nodes (4): 1. Remove Compass on Game Start, 2. Giant's Zombie Spawning & Pathing Fix, 3. Universal Castle Attacking (Waypoint Reach Logic), Tower Defense Plugin: Critical Progression & Mechanics Fixes

### Community 60 - "Architecture Audit Directives"
Cohesion: 0.50
Nodes (4): God-Node / Betweenness-Centrality Audit, Graphify-Based Architecture Audit Directive, Tick-Loop Hot-Path & GC Pressure Optimization, Graphify PreToolUse Enforcement Hook

### Community 61 - "Targeting Mode Enum"
Cohesion: 0.67
Nodes (3): String, getDisplayName(), TargetingMode()

### Community 62 - "Beehive Tower Mechanics"
Cohesion: 0.83
Nodes (4): Beehive Swarm I-Frame Clearing, Beehive Unique-Target Assignment & Sounds, Beehive Slab-Stuck Timeout Safety, Beehive Tower Config (Goliath/Swarm Paths)

### Community 63 - "Turret Scatter Mechanics"
Cohesion: 0.83
Nodes (4): Turret Scatter Spread Mechanics, Turret Scatter Forward Safe-Spawn Offset, Scatter Arrow Lifespan Despawn, Turret Tower Config (Gatling/Scatter Paths)

### Community 64 - "State Desync Bugs"
Cohesion: 0.67
Nodes (3): Issue 1: Game Not Starting (Global vs Match State Desync), Issue 2: Player Can Queue During Transition Window, Issue 4: Template Plots/Waypoints Not Mapped To Match World

### Community 65 - "World File Lock Bugs"
Cohesion: 1.00
Nodes (3): Issue 3: loadworld/unloadworld File Lock Failures, Issue 5: Match Worlds Nested In Lobby Dimensions Folder, Match World File Lock Cleanup Strategy

## Knowledge Gaps
- **321 isolated node(s):** `Objective`, `Team`, `Long`, `Double`, `VotingSession` (+316 more)
  These have ≤1 connection - possible missing edges or undocumented components.
- **10 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `getHealth()` connect `Tower Placement & Mob Movement` to `Mob Spawner GUI`, `Preset Mob Types`, `Game State & Bukkit Events`?**
  _High betweenness centrality (0.032) - this node is a cross-community bridge._
- **Why does `GameManager` connect `GameManager Cleanup & Lifecycle` to `Tower Placement & Mob Movement`, `Match State & Economy`, `Mob Spawner GUI`, `Player Stats & Upgrades`, `Mob Queue Management`, `GameManager GUI Items`, `Active Match Tracking`, `Match Lifecycle & Spells`?**
  _High betweenness centrality (0.030) - this node is a cross-community bridge._
- **Why does `MobListener` connect `Game State & Bukkit Events` to `Tower Placement & Mob Movement`, `Active Match Tracking`, `Mob Spawner GUI`, `GameManager Cleanup & Lifecycle`?**
  _High betweenness centrality (0.021) - this node is a cross-community bridge._
- **What connects `Objective`, `Team`, `Long` to the rest of the system?**
  _333 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `Tower Placement & Mob Movement` be split into smaller, more focused modules?**
  _Cohesion score 0.05046248715313464 - nodes in this community are weakly interconnected._
- **Should `Game State & Bukkit Events` be split into smaller, more focused modules?**
  _Cohesion score 0.07510204081632653 - nodes in this community are weakly interconnected._
- **Should `Plot Configuration Management` be split into smaller, more focused modules?**
  _Cohesion score 0.07540983606557378 - nodes in this community are weakly interconnected._