# Graph Report - .  (2026-06-11)

## Corpus Check
- 52 files · ~64,091 words
- Verdict: corpus is large enough that graph structure adds value.

## Summary
- 693 nodes · 1956 edges · 27 communities (23 shown, 4 thin omitted)
- Extraction: 69% EXTRACTED · 31% INFERRED · 0% AMBIGUOUS · INFERRED: 615 edges (avg confidence: 0.8)
- Token cost: 0 input · 0 output

## Community Hubs (Navigation)
- [[_COMMUNITY_Game Manager Core|Game Manager Core]]
- [[_COMMUNITY_Mob Spawning & Upgrades|Mob Spawning & Upgrades]]
- [[_COMMUNITY_Mob Event Listeners|Mob Event Listeners]]
- [[_COMMUNITY_Mob Pathfinding & Waypoints|Mob Pathfinding & Waypoints]]
- [[_COMMUNITY_Setup & Wand Tooling|Setup & Wand Tooling]]
- [[_COMMUNITY_Tower Config Loading|Tower Config Loading]]
- [[_COMMUNITY_Tower Type Definitions|Tower Type Definitions]]
- [[_COMMUNITY_Tower Entity State|Tower Entity State]]
- [[_COMMUNITY_Waypoint Config & Game State|Waypoint Config & Game State]]
- [[_COMMUNITY_Tower GUI & Path Selection|Tower GUI & Path Selection]]
- [[_COMMUNITY_Plot Configuration|Plot Configuration]]
- [[_COMMUNITY_Preset Mob Types|Preset Mob Types]]
- [[_COMMUNITY_Tower YAML Config|Tower YAML Config]]
- [[_COMMUNITY_Tower Placement & Structures|Tower Placement & Structures]]
- [[_COMMUNITY_Tower Tick Mechanics|Tower Tick Mechanics]]
- [[_COMMUNITY_Tower Structure Building|Tower Structure Building]]
- [[_COMMUNITY_Tower Spawn Cleanup|Tower Spawn Cleanup]]
- [[_COMMUNITY_Mob Movement Fixes|Mob Movement Fixes]]
- [[_COMMUNITY_Spell Config|Spell Config]]
- [[_COMMUNITY_Match & Siege Settings|Match & Siege Settings]]
- [[_COMMUNITY_Mob Upgrade System Docs|Mob Upgrade System Docs]]
- [[_COMMUNITY_Plugin Manifest & Classpath|Plugin Manifest & Classpath]]
- [[_COMMUNITY_Targeting Modes|Targeting Modes]]
- [[_COMMUNITY_Graphify & Deployment|Graphify & Deployment]]
- [[_COMMUNITY_Upgrade Config|Upgrade Config]]
- [[_COMMUNITY_Project Overview|Project Overview]]

## God Nodes (most connected - your core abstractions)
1. `GameManager` - 81 edges
2. `Tower` - 42 edges
3. `TowerManager` - 40 edges
4. `MobListener` - 38 edges
5. `EventHandler` - 34 edges
6. `MobManager` - 32 edges
7. `UUID` - 28 edges
8. `TDMob` - 26 edges
9. `String` - 24 edges
10. `String` - 23 edges

## Surprising Connections (you probably didn't know these)
- `Dripstone Ground Hazards (landmine-style)` --semantically_similar_to--> `Bombardier Landmine Mechanic`  [INFERRED] [semantically similar]
  tower_defense_fixes_batch9.md → tower_defense_final_adjustments.md
- `Dynamic Spell Pricing & Cooldown` --references--> `Spells Configuration`  [INFERRED]
  bug_fixing/tower_defense_update_claude_v2.md → src/main/resources/config.yml
- `Graphify Knowledge Graph Workflow` --conceptually_related_to--> `PebbleHost Deployment Flow`  [INFERRED]
  CLAUDE.md → SESSION_CONTEXT.md
- `Archer Tower` --references--> `Tower Progression Balances`  [INFERRED]
  TOWERS.md → PRELIMINARY_BALANCING.md
- `Warden Sonic Boom Friendly-Fire Cancel` --conceptually_related_to--> `Brain AI Mob setAI(false) Constraint`  [INFERRED]
  tower_defense_code_fixes_batch6.md → SESSION_CONTEXT.md

## Import Cycles
- None detected.

## Hyperedges (group relationships)
- **TowerDefense onEnable Manager Wiring** — towerdefense_main_class, core_gamemanager, entities_mobmanager, towers_towermanager, data_plotconfigmanager, data_waypointconfigmanager [EXTRACTED 0.95]
- **Mob Chain Spawn/Movement Flow** — entities_mobmanager, entities_mobupgraderegistry, entities_mobstateprofile, entities_tdmob, session_context_mob_movement_arch, config_mob_upgrades_csv [EXTRACTED 0.85]
- **Branching Path Tower System** — tower_turret, tower_bombardier, tower_beehive, tower_agent_specification_branching_paths, towerconfigmanager [INFERRED 0.85]
- **Polymorphic Mob Upgrade Data Flow** — bug_fixing_mob_upgrades_csv, bug_fixing_mob_upgrade_registry, bug_fixing_mob_state_profile, bug_fixing_mob_manager [EXTRACTED 1.00]
- **Velocity-Driven Pathing Subsystem** — bug_fixing_velocity_movement, bug_fixing_is_velocity_driven, bug_fixing_setai_false_bug, bug_fixing_waypoint_reach, bug_fixing_castle_siege [INFERRED 0.85]
- **Mob Spawner GUI Click Flow** — bug_fixing_mob_listener, bug_fixing_mob_manager, bug_fixing_mob_queue, bug_fixing_sequential_unlock [INFERRED 0.75]

## Communities (27 total, 4 thin omitted)

### Community 0 - "Game Manager Core"
Cohesion: 0.07
Nodes (12): GameManager, InventoryClickEvent, PlayerChangedWorldEvent, ItemStack, List, Material, Player, PresetMobType (+4 more)

### Community 1 - "Mob Spawning & Upgrades"
Cohesion: 0.05
Nodes (36): mob_upgrades_polymorphic.csv, MobManager, MobStateProfile, MobUpgradeRegistry, File, Giant Despawn Prevention (setPersistent), Health Bar Status Symbol Decoupling, Warden Sonic Boom Friendly-Fire Cancel (+28 more)

### Community 2 - "Mob Event Listeners"
Cohesion: 0.06
Nodes (32): BlockFadeEvent, CreatureSpawnEvent, EntityCombustEvent, EntityDamageByEntityEvent, EntityDamageEvent, EntityDismountEvent, EntityExplodeEvent, EntityKnockbackEvent (+24 more)

### Community 3 - "Mob Pathfinding & Waypoints"
Cohesion: 0.07
Nodes (18): TDWaypoint, getHealth(), TDMob, EntityDeathEvent, Giant Summoned Zombie Path Inheritance, List, Location, String (+10 more)

### Community 4 - "Setup & Wand Tooling"
Cohesion: 0.07
Nodes (31): Color, Command, CommandExecutor, CommandSender, TDCommand, GameManager, GameState, JavaPlugin (+23 more)

### Community 5 - "Tower Config Loading"
Cohesion: 0.13
Nodes (16): ConfigurationSection, NavigableMap, Set, Integer, Map, Material, String, TowerDefense (+8 more)

### Community 6 - "Tower Type Definitions"
Cohesion: 0.07
Nodes (34): Beehive Swarm/Goliath Bee Mechanic, Bombardier Landmine Mechanic, Dripstone Ground Hazards (landmine-style), Dripstone Strike Mechanic (BlockDisplay), Thunder Tower Tier 3 Global Strike, Turret Scatter Cone Mechanic, Chorus Teleportation pathHistory Rollback Fix, Tower Progression Balances (+26 more)

### Community 7 - "Tower Entity State"
Cohesion: 0.08
Nodes (7): Location, String, TowerConfigManager, TowerType, UUID, TargetingMode, Tower

### Community 8 - "Waypoint Config & Game State"
Cohesion: 0.11
Nodes (16): BossBar, WaypointConfigManager, Matchmaking Queue Timer & forceStartMatch, Passive Gold Generator, Player Upgrades & Shops (EXP economy), Lobby Selector GUI & Matchmaking, Sidebar Scoreboard Game Timer, Tier-Selection GUI System (+8 more)

### Community 9 - "Tower GUI & Path Selection"
Cohesion: 0.23
Nodes (7): List, Material, Player, String, TowerType, UUID, TowerManager

### Community 10 - "Plot Configuration"
Cohesion: 0.23
Nodes (8): towers.yaml / config.yml, PlotConfigManager, Phase 1: The Canvas (arena geometry), Location, String, TowerDefense, TowerConfigManager.java, getInt()

### Community 11 - "Preset Mob Types"
Cohesion: 0.15
Nodes (11): getColor(), getDisplayName(), getEntityType(), getMaterial(), PresetMobType(), Mob Statistics, Costs & Rewards, Mob Chain System (16 chains x 5 tiers), ChatColor (+3 more)

### Community 12 - "Tower YAML Config"
Cohesion: 0.12
Nodes (18): Chorus Tower Teleportation Logic, Happy Ghast Harness Material Fix, TowerManager, towers.yaml (Master Tower Config), Archer Tower Definition, Beehive Tower (Goliath/Swarm paths), Bombardier Tower (Bigger Bombs/Landmines paths), Chorus Tower Definition (+10 more)

### Community 13 - "Tower Placement & Structures"
Cohesion: 0.13
Nodes (11): 5x5 Tower Placement Validation (canFit5x5), EMP Tower Disable & Glitched Holograms, Particle, Golem Tower (5x5), Happy Ghast Tower (5x5), NBT Structure Loading, BlockVector, ItemStack (+3 more)

### Community 15 - "Tower Structure Building"
Cohesion: 0.21
Nodes (4): HappyGhast, BlockVector, LivingEntity, LivingEntity

### Community 16 - "Tower Spawn Cleanup"
Cohesion: 0.33
Nodes (3): ArmorStand, Bee, List

### Community 17 - "Mob Movement Fixes"
Cohesion: 0.27
Nodes (10): Giant Zombie Summoning & Pathing, Health-Based Speed Scaling, isVelocityDriven() Helper, MobListener, MobManager, Mob Spawner Queue (per-tier), Sequential Mob Tier Unlocking, setAI(false) Freeze Bug (+2 more)

### Community 18 - "Spell Config"
Cohesion: 0.25
Nodes (8): Health Bar Status Symbol Stacking, Damage Storm Spell, Freeze Spell, Haste Rush Spell, Overcharge Spell, Slow Shield Spell, Spells Configuration, Tower EMP Spell

### Community 19 - "Match & Siege Settings"
Cohesion: 0.29
Nodes (7): 8-Player (4v4) Match Support, Continuous Castle Siege Logic, Dynamic Spell Pricing & Cooldown, GameManager, TDMob (Mob Wrapper), Game Settings (config.yml), Players Per Match (8, 4v4)

### Community 20 - "Mob Upgrade System Docs"
Cohesion: 0.33
Nodes (7): Mob Immunities (td_immunities PDC), MobStateProfile (Immutable CSV Row), MOB Update 6.6.25 (Upgrade Chain Migration), MobUpgradeRegistry, mob_upgrades_polymorphic.csv, Polymorphic Mob Upgrade System, Variant State Engine (Chain+Tier composite key)

### Community 21 - "Plugin Manifest & Classpath"
Cohesion: 0.40
Nodes (5): TDCommand, classpath.txt (Maven Dependencies), Paper API Dependency (26.1.2), plugin.yml (Tower_Defense Manifest), /td Command Definition

### Community 22 - "Targeting Modes"
Cohesion: 0.67
Nodes (3): String, getDisplayName(), TargetingMode()

## Knowledge Gaps
- **79 isolated node(s):** `String`, `Override`, `TDWaypoint`, `Integer`, `List` (+74 more)
  These have ≤1 connection - possible missing edges or undocumented components.
- **4 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `GameManager` connect `Game Manager Core` to `Waypoint Config & Game State`, `Mob Event Listeners`, `Tower Tick Mechanics`?**
  _High betweenness centrality (0.067) - this node is a cross-community bridge._
- **Why does `TowerDefense.java (Plugin Entrypoint)` connect `Waypoint Config & Game State` to `Mob Spawning & Upgrades`, `Mob Event Listeners`, `Setup & Wand Tooling`, `Plot Configuration`, `Tower Placement & Structures`?**
  _High betweenness centrality (0.050) - this node is a cross-community bridge._
- **Why does `Tower` connect `Tower Entity State` to `Mob Event Listeners`, `Mob Pathfinding & Waypoints`, `Tower Placement & Structures`, `Tower Tick Mechanics`, `Tower Structure Building`, `Tower Spawn Cleanup`?**
  _High betweenness centrality (0.043) - this node is a cross-community bridge._
- **What connects `String`, `Override`, `TDWaypoint` to the rest of the system?**
  _83 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `Game Manager Core` be split into smaller, more focused modules?**
  _Cohesion score 0.06864035087719299 - nodes in this community are weakly interconnected._
- **Should `Mob Spawning & Upgrades` be split into smaller, more focused modules?**
  _Cohesion score 0.054069938289744345 - nodes in this community are weakly interconnected._
- **Should `Mob Event Listeners` be split into smaller, more focused modules?**
  _Cohesion score 0.057902973395931145 - nodes in this community are weakly interconnected._