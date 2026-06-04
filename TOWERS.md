# Tower Defense: Tower Reference Guide

This document lists all 7 defense towers, their building and upgrade costs, combat stats, and details on how their mechanics are implemented.

---

## 1. Archer Tower (`archer`)
A classic single-target physical defense tower that targets the first mob in its range. It features high range and consistent attack speed.

| Tier | Name | Build/Upgrade Cost | Attack Range | Damage (HP) | Cooldown (Ticks/Seconds) |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **Tier 1** | `archer_1` | 100 Gold | 15.0 Blocks | 1.5 HP | 20 Ticks (1.0s) |
| **Tier 2** | `archer_2` | 150 Gold | 16.5 Blocks | 3.0 HP | 18 Ticks (0.9s) |
| **Tier 3** | `archer_3` | 300 Gold | 18.0 Blocks | 4.5 HP | 16 Ticks (0.8s) |

*   **Mechanic**: Fires normal arrows towards the target. (Arrows cannot be picked up by players and do not clutter their inventories).

---

## 2. Fire Tower (`fire`)
An area-of-effect (AoE) elemental tower that applies fire ticks to all mobs in its radius. It deals damage over time rather than a single target strike.

| Tier | Name | Build/Upgrade Cost | Attack Range | Direct Damage (HP) | Cooldown (Ticks/Seconds) | Burn Duration |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **Tier 1** | `fire_1` | 175 Gold | 8.0 Blocks | 1.0 HP | 80 Ticks (4.0s) | 60 Ticks (3.0s) |
| **Tier 2** | `fire_2` | 250 Gold | 10.0 Blocks | 2.0 HP | 80 Ticks (4.0s) | 100 Ticks (5.0s) |
| **Tier 3** | `fire_3` | 400 Gold | 12.0 Blocks | 3.5 HP | 80 Ticks (4.0s) | 140 Ticks (7.0s) |

*   **Mechanic**: Applies a custom fire damage level to all targeted mobs. Mobs with fire immunity (e.g. Pigmen, Blazes, Magma Cubes) are unaffected. Mobs show the `🔥` emoji on their health bar.

---

## 3. Prismarine Tower (`prismarine`)
A supportive crowd-control tower that slows down all mobs within its radius.

| Tier | Name | Build/Upgrade Cost | Attack Range | Direct Damage (HP) | Cooldown (Ticks) | Slowness Effect |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **Tier 1** | `prismarine_1` | 125 Gold | 8.0 Blocks | 0.5 HP | 30 Ticks (1.5s) | Slowness I (2.0s) |
| **Tier 2** | `prismarine_2` | 200 Gold | 10.0 Blocks | 1.0 HP | 30 Ticks (1.5s) | Slowness II (3.0s) |
| **Tier 3** | `prismarine_3` | 350 Gold | 12.0 Blocks | 1.5 HP | 30 Ticks (1.5s) | Slowness III (4.0s) |

*   **Mechanic**: Slows all non-immune mobs in its radius. Shows the `❄` emoji on the health bar.

---

## 4. Chorus Tower (`chorus`)
A tactical utility watchtower that teleports the leading mob backwards along the path's waypoints.

| Tier | Name | Build/Upgrade Cost | Attack Range | Direct Damage (HP) | Cooldown (Ticks/Seconds) | Teleport Distance |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **Tier 1** | `chorus_1` | 150 Gold | 8.0 Blocks | 0.0 HP | 120 Ticks (6.0s) | 1 Waypoint backward |
| **Tier 2** | `chorus_2` | 250 Gold | 10.0 Blocks | 0.0 HP | 80 Ticks (4.0s) | 2 Waypoints backward |
| **Tier 3** | `chorus_3` | 400 Gold | 12.0 Blocks | 0.0 HP | 40 Ticks (2.0s) | 3 Waypoints backward |

*   **Mechanic**: Targets the leading mob in its range. If teleported, the mob's waypoint index is set back, forcing it to retrace its steps.

---

## 5. Redstone Tower (`redstone`)
A passive support structure that boosts the firing speed of all towers built within its grid radius.

| Tier | Name | Build/Upgrade Cost | Boost Range | Attack Speed Boost | Special Ability |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **Tier 1** | `redstone_1` | 200 Gold | 10.0 Blocks | +30% Speed | Boosts adjacent towers |
| **Tier 2** | `redstone_2` | 300 Gold | 16.0 Blocks | +30% Speed | Boosts adjacent towers |
| **Tier 3** | `redstone_3` | 500 Gold | 24.0 Blocks | +30% Speed | Boosts adjacent towers |

*   **Mechanic**: Nearby towers within range have their cooldown shortened by 30%. Affected towers show `⚡ [Speed] ⚡` in their holograms and list the boost details inside their click GUIs. 

---

## 6. Poison Tower (`poison`)
An area-of-effect (AoE) status tower that inflicts poison ticks to all mobs within its radius.

| Tier | Name | Build/Upgrade Cost | Attack Range | Direct Damage (HP) | Cooldown (Ticks/Seconds) | Poison Effect & Dur. |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **Tier 1** | `poison_1` | 150 Gold | 8.0 Blocks | 0.5 HP | 80 Ticks (4.0s) | Poison I (3.0s) |
| **Tier 2** | `poison_2` | 250 Gold | 10.0 Blocks | 1.0 HP | 80 Ticks (4.0s) | Poison II (4.0s) |
| **Tier 3** | `poison_3` | 400 Gold | 12.0 Blocks | 1.5 HP | 80 Ticks (4.0s) | Poison III (5.0s) |

*   **Mechanic**: Applies custom poison damage metadata to targeted mobs. Spiders are immune. Mobs show the `🤢` emoji on their health bar.

---

## 7. Ice Tower (`ice`)
A high-tier crowd-control tower that freezes mobs solid in place, stopping all track progression.

| Tier | Name | Build/Upgrade Cost | Attack Range | Direct Damage (HP) | Cooldown (Ticks) | Freeze Duration |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **Tier 1** | `ice_1` | 125 Gold | 8.0 Blocks | 0.5 HP | 30 Ticks (1.5s) | 40 Ticks (2.0s) |
| **Tier 2** | `ice_2` | 200 Gold | 10.0 Blocks | 1.0 HP | 30 Ticks (1.5s) | 60 Ticks (3.0s) |
| **Tier 3** | `ice_3` | 350 Gold | 12.0 Blocks | 1.5 HP | 30 Ticks (1.5s) | 80 Ticks (4.0s) |

*   **Mechanic**: Halts both pathfinding and manual velocity movement for the freeze duration. Displays snowflake particles around frozen targets. Mobs with slow immunity are skipped.
