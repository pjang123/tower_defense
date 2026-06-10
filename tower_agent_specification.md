# Tower Defense: Master AI Agent Tower Reference & Mechanics Guide

This comprehensive reference specification documents all 14 defense towers implemented within the system. It defines their programming identifiers, progression schemas, detailed targeting mechanics, internal Persistent Data Container (PDC) flags, and complete system stats. It is optimized for direct ingestion by automated LLM development agents.

---

## System Architecture Summary

Towers operate on a 1-tick loop (50ms execution cycles). Mobs are driven by custom velocity configurations managed via `MobManager`. Towers check for target validity, arena validation matching (`td_arena`), and immunity attributes (`td_immunities`) before executing specific projectile pathing or potion effect applications. 

Towers are divided into two structural categories:
1. **Linear Progression:** Progresses sequentially from Tier 1 up to Tier 3 or 4.
2. **Branching Paths:** Modulated by a `pathId` configuration, offering distinct specialization routes.

---

## 1. Linear Progression Towers

### 1.1 Archer Tower (`archer`)
* **Description:** Classic high-range, fast single-target physical defense tower.
* **Targeting Mode:** Follows standard priority targeting modes (FIRST, LAST, STRONG, WEAK, CLOSE). Targets the leading mob by tracking path history progression.
* **Special Mechanics:** Deflected by entities flagged as immune or specifically configured entities like `BREEZE`.
* **Stat Matrix:**
  | Tier | Name | Cost (Gold) | Attack Range | Damage (HP) | Cooldown (Ticks) |
  | :--- | :--- | :--- | :--- | :--- | :--- |
  | Tier 1 | `archer_1` | 100 | 15.0 Blocks | 1.5 HP | 20 Ticks (1.0s) |
  | Tier 2 | `archer_2` | 150 | 16.5 Blocks | 3.0 HP | 18 Ticks (0.9s) |
  | Tier 3 | `archer_3` | 300 | 18.0 Blocks | 4.5 HP | 16 Ticks (0.8s) |

### 1.2 Fire Tower (`fire`)
* **Description:** Elemental area-of-effect (AoE) tower focusing on damage-over-time.
* **Targeting Mode:** Radial scan (`getMobsInRadius`). Triggers an attack whenever any valid target enters its zone.
* **Special Mechanics:** Applies custom metadata level and write a double value to the PDC using key `td_fire_damage`. Displays a `🔥` emoji indicator on affected mob health bars. Mobs with native fire immunity (e.g., Piglins, Blazes, Magma Cubes) filter out of damage updates.
* **Stat Matrix:**
  | Tier | Name | Cost (Gold) | Attack Range | Damage (HP) | Cooldown (Ticks) | Burn Duration |
  | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
  | Tier 1 | `fire_1` | 175 | 8.0 Blocks | 1.0 HP | 80 Ticks (4.0s) | 60 Ticks (3.0s) |
  | Tier 2 | `fire_2` | 250 | 10.0 Blocks | 2.0 HP | 80 Ticks (4.0s) | 100 Ticks (5.0s) |
  | Tier 3 | `fire_3` | 400 | 12.0 Blocks | 3.5 HP | 80 Ticks (4.0s) | 140 Ticks (7.0s) |

### 1.3 Prismarine Tower (`prismarine`)
* **Description:** Support crowd-control infrastructure designed to reduce mob movement velocity.
* **Targeting Mode:** Radial zone sweep.
* **Special Mechanics:** Spreads vanilla `SLOWNESS` potion effects across all target entities inside the execution matrix. Writes the `❄` emoji to the custom visual overlay. Skips targets carrying the `td_slow_immune` tag.
* **Stat Matrix:**
  | Tier | Name | Cost (Gold) | Attack Range | Damage (HP) | Cooldown (Ticks) | Slowness Effect |
  | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
  | Tier 1 | `prismarine_1` | 125 | 8.0 Blocks | 0.5 HP | 30 Ticks (1.5s) | Slowness I (2.0s) |
  | Tier 2 | `prismarine_2` | 200 | 10.0 Blocks | 1.0 HP | 30 Ticks (1.5s) | Slowness II (3.0s) |
  | Tier 3 | `prismarine_3` | 350 | 12.0 Blocks | 1.5 HP | 30 Ticks (1.5s) | Slowness III (4.0s) |

### 1.4 Chorus Tower (`chorus`)
* **Description:** Utility dislocation assembly that sends target lane-mobs backward along path nodes.
* **Targeting Mode:** Isolates the current front-most unit within its perimeter zone.
* **Special Mechanics:** Reads `tdMob.getPathHistory()` and rewires target navigation vector nodes backwards. Adjusts the tracking waypoint ID pointer accordingly and forces a sudden coordinate teleportation. Preserves height offsets for flying types. Mobs flagged with `CHORUS` immunity skip this completely.
* **Stat Matrix:**
  | Tier | Name | Cost (Gold) | Attack Range | Damage (HP) | Cooldown (Ticks) | Dislocation Shift |
  | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
  | Tier 1 | `chorus_1` | 150 | 8.0 Blocks | 0.0 HP | 120 Ticks (6.0s) | 1 Waypoint node |
  | Tier 2 | `chorus_2` | 250 | 10.0 Blocks | 0.0 HP | 80 Ticks (4.0s) | 2 Waypoint nodes |
  | Tier 3 | `chorus_3` | 400 | 12.0 Blocks | 0.0 HP | 40 Ticks (2.0s) | 3 Waypoint nodes |

### 1.5 Redstone Tower (`redstone`)
* **Description:** Passive adjacent field amplifier providing a firing frequency rate boost to nearby units.
* **Targeting Mode:** Constant neighborhood scan checking for friendly tower blocks within its perimeter matrix.
* **Special Mechanics:** Does not execute targeted offensive moves. Scans every 20 ticks and drops tower cooldown variables by a fixed 30% speed scaling value (`cooldown * 0.7`). Emits dynamic `Particle.DUST` (Red) indicators directly into active target structures.
* **Stat Matrix:**
  | Tier | Name | Cost (Gold) | Boost Range | Attack Speed Modifier | Behavior |
  | :--- | :--- | :--- | :--- | :--- | :--- |
  | Tier 1 | `redstone_1` | 200 | 10.0 Blocks | +30% Attack Speed | Cooldown reduction field |
  | Tier 2 | `redstone_2` | 300 | 16.0 Blocks | +30% Attack Speed | Cooldown reduction field |
  | Tier 3 | `redstone_3` | 500 | 24.0 Blocks | +30% Attack Speed | Cooldown reduction field |

### 1.6 Poison Tower (`poison`)
* **Description:** Periodic ticking negative status effect deployment facility.
* **Targeting Mode:** Radial field zone broadcast. Generates an orbital cloud array (`Particle.WITCH`).
* **Special Mechanics:** Overrides the vanilla poison framework to circumvent Undead entity immunities. Writes double damage attributes directly to the PDC (`td_poison_damage`) and maps duration bounds using a millisecond timestamp parameter (`td_poisoned_until`). Displays the `🤢` health indicator emoji. Spiders are natively immune.
* **Stat Matrix:**
  | Tier | Name | Cost (Gold) | Attack Range | Damage (HP) | Cooldown (Ticks) | Status Applied |
  | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
  | Tier 1 | `poison_1` | 150 | 8.0 Blocks | 0.5 HP | 80 Ticks (4.0s) | Poison I (3.0s) |
  | Tier 2 | `poison_2` | 250 | 10.0 Blocks | 1.0 HP | 80 Ticks (4.0s) | Poison II (4.0s) |
  | Tier 3 | `poison_3` | 400 | 12.0 Blocks | 1.5 HP | 80 Ticks (4.0s) | Poison III (5.0s) |

### 1.7 Ice Tower (`ice`)
* **Description:** High-tier suppression assembly that anchors enemies firmly in place, blocking progression.
* **Targeting Mode:** Zone radial perimeter coverage.
* **Special Mechanics:** Halts pathfinding frameworks and sets manual path velocities directly to zero for the duration window. Assigns a hard limit timestamp directly inside the target entity PDC data using the key structure `td_frozen_until`. Emits `Particle.SNOWFLAKE` visual queues. Wardens and similar high-priority types are unaffected if carrying `ICE` or `SLOW` immunities.
* **Stat Matrix:**
  | Tier | Name | Cost (Gold) | Attack Range | Damage (HP) | Cooldown (Ticks) | Lock Frame Window |
  | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
  | Tier 1 | `ice_1` | 125 | 8.0 Blocks | 0.5 HP | 30 Ticks (1.5s) | 40 Ticks (2.0s) |
  | Tier 2 | `ice_2` | 200 | 10.0 Blocks | 1.0 HP | 30 Ticks (1.5s) | 60 Ticks (3.0s) |
  | Tier 3 | `ice_3` | 350 | 12.0 Blocks | 1.5 HP | 30 Ticks (1.5s) | 80 Ticks (4.0s) |

### 1.8 Golem Tower (`golem`)
* **Description:** Spawns physical melee defenders directly onto the battlefield that intercept lane targets.
* **Targeting Mode:** Automated Pathfinder logic channelling entity movement toward the nearest lane target. Returns to base coordinates if empty.
* **Special Mechanics:** Spawns a physical, persistent, invulnerable, non-collidable custom entity. Tier 1 summons a scaled Copper Golem entity, while Tier 2 features an Iron Golem with a massive vertical knock-up utility modification (`Particle.CLOUD`).
* **Stat Matrix:**
  | Tier | Name | Cost (Gold) | Engagement Range | Damage (HP) | Cooldown (Ticks) | Spawn Target Profile |
  | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
  | Tier 1 | `golem_1` | 400 | 10.0 Blocks | **10.0 HP** *(Nerfed)* | 40 Ticks (2.0s) | Copper Golem (Scale: 0.6) |
  | Tier 2 | `golem_2` | 600 | 12.0 Blocks | **20.0 HP** *(Nerfed)* | 38 Ticks (1.9s) | Iron Golem (Scale: 1.0) |

### 1.9 Happy Ghast Tower (`happy_ghast`)
* **Description:** Premium airborne fire support deployment tower supporting automated and interactive manual control.
* **Targeting Mode:** Defaults to automated tracking loops when unridden. Switches over to manual directional navigation control paths when boarded by players.
* **Special Mechanics:** Spawns a non-collidable, invulnerable custom Ghast variant. Equips team-colored harness elements within the `BODY` item slot (`RED_HARNESS` / `BLUE_HARNESS`) to establish vehicle registration parameters. Launches high-yield explosively neutralized `LargeFireball` projectile payloads tracking custom metadata parameters (`td_happy_fireball`).
* **Stat Matrix:**
  | Tier | Name | Cost (Gold) | Attack Range | Damage (HP) | Cooldown (Ticks) | Execution Notes |
  | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
  | Tier 1 | `happy_ghast_1` | 500 | 15.0 Blocks | **8.0 HP** *(Nerfed)* | 50 Ticks (2.5s) | Core fireball payload |
  | Tier 2 | `happy_ghast_2` | 750 | 17.5 Blocks | **12.0 HP** *(Nerfed)* | 48 Ticks (2.4s) | Enhanced explosive radius |
  | Tier 3 | `happy_ghast_3` | 1000 | 20.0 Blocks | **16.0 HP** *(Nerfed)* | 45 Ticks (2.25s) | Maximum tracking spread |

### 1.10 Dripstone Tower (`dripstone`)
* **Description:** Heavy single-target kinetic ceiling collapse execution mechanism.
* **Targeting Mode:** Isolates targeted lane units via system index checks.
* **Special Mechanics:** Spawns direct falling blocks (`POINTED_DRIPSTONE`) or processes instant vector coordinate trace strikes. 
  * **Tier 2:** Deploys permanent ground hazard blocks on track nodes. Intercepting targets are injected with a custom structural payload flag (`td_vulnerable`), magnifying all structural damage input metrics across the board by exactly 15%.
  * **Tier 3:** Initiates "Cave In" script arrays, sweeping downstream through sequential path vectors to cascade multi-point strikes.
* **Stat Matrix:**
  | Tier | Name | Cost (Gold) | Attack Range | Damage (HP) | Cooldown (Ticks) | Performance Notes |
  | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
  | Tier 1 | `dripstone_1` | 200 | 10.0 Blocks | 6.0 HP | 60 Ticks (3.0s) | Kinetic overhead single strike |
  | Tier 2 | `dripstone_2` | 300 | 12.0 Blocks | 9.0 HP | 55 Ticks (2.75s) | Sets 15% Vulnerability tiles |
  | Tier 3 | `dripstone_3` | 450 | 14.0 Blocks | 12.0 HP | 50 Ticks (2.5s) | Cascading Cave-In sweep |
  | Tier 4 | `dripstone_4` | 650 | 16.0 Blocks | 15.0 HP | 45 Ticks (2.25s) | Ultimate single-target crusher |

### 1.11 Thunder Tower (`thunder`)
* **Description:** Long-charge climate discharge apparatus utilizing targeted high-energy environmental strikes.
* **Targeting Mode:** Single-target focal point check tracking standard parameters, converting to an open universal map search loop at Tier 4.
* **Special Mechanics:** Calls the native `strikeLightningEffect` array to clear fire generation risks while maintaining maximum sensory feedback. 
  * **Tiers 2 & 3 (Chain Lightning):** Triggers sub-radius scans upon hitting primary targets, propagating electrical sparks (`Particle.ELECTRIC_SPARK`) to branch to adjacent units for 50% damage.
  * **Tier 4 (Global Strike):** Evaluates an independent execution clock (600 Ticks). When ready, it sweeps the absolute boundaries of the active lane and targets all valid actors across the track network simultaneously.
* **Stat Matrix:**
  | Tier | Name | Cost (Gold) | Attack Range | Damage (HP) | Cooldown (Ticks) | Chain Jump Profiles |
  | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
  | Tier 1 | `thunder_1` | 200 | 12.0 Blocks | 5.0 HP | 80 Ticks (4.0s) | Solo focus discharge |
  | Tier 2 | `thunder_2` | 300 | 15.0 Blocks | 8.0 HP | 80 Ticks (4.0s) | Arc distribution (Max 3 Mobs) |
  | Tier 3 | `thunder_3` | 450 | 18.0 Blocks | 11.0 HP | 75 Ticks (3.75s) | Arc distribution (Max 5 Mobs) |
  | Tier 4 | `thunder_4` | 600 | Global (999) | 15.0 HP | 600 Ticks (30.0s)| Global Strike (Map wipe) |

---

## 2. Branching Path Towers

### 2.1 Turret Tower (`turret`)
* **Description:** Fast physical firing platform supporting rapid stream configuration adjustments.
* **Branching Paths Selection Schema:**
  * `turret_gatling`: Hyper-accelerated single-target tracking profiles.
  * `turret_scatter`: Spread-fire fragmentation arrays.

#### Path 1: Gatling (`gatling`)
* **Mechanics:** Drops execution intervals to single-digit tick boundaries. Simulates continuous firing lines via linear tracer calculations (`Particle.CRIT`).
* **Stat Matrix:**
  | Level | Name | Cost (Gold) | Range | Damage (HP) | Cooldown (Ticks) |
  | :--- | :--- | :--- | :--- | :--- | :--- |
  | Level 1 | `turret_paths_gatling_1` | 250 | 12.0 Blocks | 0.2 HP | 2 Ticks (0.1s) |
  | Level 2 | `turret_paths_gatling_2` | 350 | 13.5 Blocks | 0.3 HP | 2 Ticks (0.1s) |
  | Level 3 | `turret_paths_gatling_3` | 500 | 15.0 Blocks | 0.4 HP | 1 Tick (0.05s) |

#### Path 2: Scatter Shot (`scatter`)
* **Mechanics:** Evaluates a randomized numerical constraint parameter (5 to 9) on attack cycles. Computes angular distribution offsets across individual projectile trace paths to fan damage across multiple target lanes.
* **Stat Matrix:**
  | Level | Name | Cost (Gold) | Range | Damage (HP) | Cooldown (Ticks) | Projectile Count |
  | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
  | Level 1 | `turret_paths_scatter_1` | 250 | 10.0 Blocks | 0.5 HP | 15 Ticks (0.75s) | 5 Arrows |
  | Level 2 | `turret_paths_scatter_2` | 350 | 11.5 Blocks | 0.8 HP | 13 Ticks (0.65s) | 7 Arrows |
  | Level 3 | `turret_paths_scatter_3` | 500 | 13.0 Blocks | 1.2 HP | 10 Ticks (0.50s) | 9 Arrows |

---

### 2.2 Bombardier Tower (`bombardier`)
* **Description:** Heavy combustion platform that deploys explosive payloads or proximity track defenses.
* **Branching Paths Selection Schema:**
  * `bombardier_bigger_bombs`: High-yield splash artillery.
  * `bombardier_landmines`: Proximity-based track defense deployment.

#### Path 1: Bigger Bombs (`bigger_bombs`)
* **Mechanics:** Spawns localized `TNTPrimed` instances directly into the lane matrix. Captures and suppresses environmental block destruction listeners via `EntityExplodeEvent`, querying targets cleanly within specific blast radii.
* **Stat Matrix:**
  | Level | Name | Cost (Gold) | Range | Damage (HP) | Cooldown (Ticks) | Blast Radius |
  | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
  | Level 1 | `bombardier_paths_bigger_bombs_1` | 175 | 10.0 Blocks | 2.5 HP | 50 Ticks (2.5s) | 3.5 Blocks (7 Dia) |
  | Level 2 | `bombardier_paths_bigger_bombs_2` | 250 | 12.0 Blocks | 4.0 HP | 45 Ticks (2.25s)| 4.5 Blocks (9 Dia) |
  | Level 3 | `bombardier_paths_bigger_bombs_3` | 400 | 14.0 Blocks | 6.0 HP | 40 Ticks (2.0s) | 5.5 Blocks (11 Dia)|

#### Path 2: Landmines (`landmines`)
* **Mechanics:** Deploys static tracking instances (`ArmorStand`) equipped with custom explosive block head assets (`Material.TNT`). Runs tick-based vector distance validations. If a target's position markers overlap the mine coordinates, it triggers an instant area blast (`Particle.EXPLOSION_HUGE`), applies single-target damage, and removes the node from the system cache.
* **Stat Matrix:**
  | Level | Name | Cost (Gold) | Deployment Radius | Damage (HP) | Cooldown (Ticks) | Behavior |
  | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
  | Level 1 | `bombardier_paths_landmines_1` | 225 | 12.0 Blocks | 18.0 HP | 80 Ticks (4.0s) | Proximity Track Trap |
  | Level 2 | `bombardier_paths_landmines_2` | 325 | 14.0 Blocks | 24.0 HP | 75 Ticks (3.75s) | Proximity Track Trap |
  | Level 3 | `bombardier_paths_landmines_3` | 450 | 16.0 Blocks | 32.0 HP | 70 Ticks (3.5s)  | Proximity Track Trap |

---

### 2.3 Beehive Tower (`beehive`)
* **Description:** Biological manufacturing array that handles autonomous entity deployment tasks.
* **Branching Paths Selection Schema:**
  * `beehive_goliath`: Single massive high-durability vanguard unit.
  * `beehive_swarm`: Distributed multi-entity distraction swarm.

#### Path 1: Goliath Bee (`goliath`)
* **Mechanics:** Spawns a singular custom `Bee` entity. Uses reflection pathways to update the target's underlying `GENERIC_SCALE` asset values securely. Commands the entity to intercept lane targets and explode upon contact.
* **Stat Matrix:**
  | Level | Name | Cost (Gold) | Tracking Radius | Damage (HP) | Cooldown (Ticks) | Entity Scale Attribute |
  | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
  | Level 1 | `beehive_paths_goliath_1` | 150 | 5.0 Blocks | 4.0 HP | 40 Ticks (2.0s) | Scale: 2.0x |
  | Level 2 | `beehive_paths_goliath_2` | 200 | 5.5 Blocks | 6.0 HP | 38 Ticks (1.9s) | Scale: 2.25x |
  | Level 3 | `beehive_paths_goliath_3` | 250 | 6.0 Blocks | 8.0 HP | 35 Ticks (1.75s)| Scale: 2.5x |
  | Level 4 | `beehive_paths_goliath_4` | 300 | 6.5 Blocks | 10.0 HP | 32 Ticks (1.6s) | Scale: 2.75x |
  | Level 5 | `beehive_paths_goliath_5` | 350 | 7.0 Blocks | 12.0 HP | 30 Ticks (1.5s) | Scale: 3.0x |

#### Path 2: Swarm (`swarm`)
* **Mechanics:** Allocates multi-entity loops tracking up to 9 distinct tracking actors simultaneously. Spawns standard-sized entities that overwhelm target paths and trigger consecutive explosions.
* **Stat Matrix:**
  | Level | Name | Cost (Gold) | Range | Damage (HP) | Cooldown (Ticks) | Max Swarm Fleet Size |
  | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
  | Level 1 | `beehive_paths_swarm_1` | 175 | 7.0 Blocks | 1.0 HP | 60 Ticks (3.0s) | 1 Active Bee |
  | Level 2 | `beehive_paths_swarm_2` | 225 | 8.0 Blocks | 1.5 HP | 55 Ticks (2.75s) | 3 Active Bees |
  | Level 3 | `beehive_paths_swarm_3` | 275 | 9.0 Blocks | 2.0 HP | 50 Ticks (2.5s) | 5 Active Bees |
  | Level 4 | `beehive_paths_swarm_4` | 325 | 10.0 Blocks | 2.5 HP | 45 Ticks (2.25s) | 7 Active Bees |
  | Level 5 | `beehive_paths_swarm_5` | 400 | 11.0 Blocks | 3.0 HP | 40 Ticks (2.0s) | 9 Active Bees |
