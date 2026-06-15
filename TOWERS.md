# Tower Defense: Tower Reference Guide

This document lists all implemented defense towers, their building and upgrade costs, combat stats, and details on how their mechanics are implemented. Statistics are defined in `src/main/resources/towers.yaml`.

---

## 1. Archer Tower (`archer`)
A classic single-target physical defense tower that targets the first mob in its range. It features high range and consistent attack speed.
*   **Mechanic**: Fires arrows towards the target. (Arrows cannot be picked up by players).
*   **Base Material**: COBBLESTONE

## 2. Fire Tower (`fire`)
An area-of-effect (AoE) elemental tower that applies fire ticks to all mobs in its radius. It deals damage over time.
*   **Mechanic**: Applies custom fire damage to all targeted mobs. Mobs show the `🔥` emoji.
*   **Base Material**: POLISHED_BLACKSTONE

## 3. Prismarine Tower (`prismarine`)
A supportive crowd-control tower that slows down all mobs within its radius.
*   **Mechanic**: Slows all non-immune mobs in its radius. Shows the `❄` emoji.
*   **Base Material**: PRISMARINE

## 4. Chorus Tower (`chorus`)
A tactical utility watchtower that teleports the leading mob backwards along the path's waypoints.
*   **Mechanic**: Targets the leading mob. If teleported, the mob's waypoint index is set back.
*   **Base Material**: PURPUR_BLOCK

## 5. Redstone Tower (`redstone`)
A passive support structure that boosts the firing speed of all towers built within its grid radius.
*   **Mechanic**: Nearby towers have their cooldown shortened by 30%. Affected towers show `⚡ [Speed] ⚡`.
*   **Base Material**: SMOOTH_STONE

## 6. Poison Tower (`poison`)
An area-of-effect (AoE) status tower that inflicts poison ticks to all mobs within its radius.
*   **Mechanic**: Applies custom poison damage metadata. Mobs show the `🤢` emoji.
*   **Base Material**: MUD_BRICKS

## 7. Ice Tower (`ice`)
A high-tier crowd-control tower that freezes mobs solid in place, stopping all track progression.
*   **Mechanic**: Halts both pathfinding and manual velocity movement for the freeze duration.
*   **Base Material**: SNOW_BLOCK

## 8. Golem Tower (`golem`)
A 5x5 large tower that spawns a patrolling Golem on the lane path.
*   **Mechanic**: Spawns a Copper (T1) or Iron (T2) Golem that walks back and forth along the path waypoints, attacking mobs it encounters.
*   **Base Material**: SMOOTH_STONE

## 9. Happy Ghast Tower (`happy_ghast`)
A 5x5 large tower that spawns a ridable Happy Ghast.
*   **Mechanic**: The purchasing player can right-click to mount and steer. Left-click fires fireballs. When unridden, it uses autopilot targeting.
*   **Base Material**: SOUL_SAND

## 10. Dripstone Tower (`dripstone`)
A tactical tower that uses falling pointed dripstone and ground hazards.
*   **Mechanic**: Drops dripstone from above. At higher tiers, it generates ground hazards that act as landmines.
*   **Base Material**: DRIPSTONE_BLOCK

## 11. Thunder Tower (`thunder`)
An elemental tower that strikes mobs with lightning.
*   **Mechanic**: Single target lightning strikes. At Tier 3, it can trigger a global strike hitting all mobs in the arena.
*   **Base Material**: COPPER_BLOCK

## 12. Turret (`turret`)
A rapid-fire mechanical tower with branching upgrade paths.
*   **Paths**: 
    *   **Gatling**: Extreme fire rate on single targets.
    *   **Scatter**: Fires a spread of arrows for area coverage.
*   **Base Material**: IRON_BLOCK

## 13. Bombardier (`bombardier`)
A heavy artillery tower that fires explosive TNT.
*   **Paths**: 
    *   **Bigger Bombs**: Increased explosion radius and damage.
    *   **Landmines**: Places explosive charges on the path.
*   **Base Material**: BRICKS

## 14. Beehive (`beehive`)
A nature-based tower that summons defensive bees.
*   **Paths**: 
    *   **Goliath**: Summons fewer, much stronger tanky bees.
    *   **Swarm**: Summons a large cloud of fast-stinging bees.
*   **Base Material**: HONEYCOMB_BLOCK

## 15. Gold Tower (`gold`)
An economy tower that generates income via collectible barrels. Deals zero damage and never attacks.
*   **Mechanic**: Periodically spawns a "gold bundle" (a non-solid `BlockDisplay` barrel + an `Interaction` hitbox) on the track within its range. Mobs pass through it freely. Click a barrel (left or right) to claim its gold. Barrels despawn after a timer if ignored.
*   **Base Material**: RAW_GOLD_BLOCK
*   **Branches into three paths at Level 2:**
    *   **High Roller** — large per-barrel payouts (150 → 200 → 300) but a tightening despawn timer (15s → 10s → 5s). Rewards active clicking; barrels flash/smoke in their last 3 seconds.
    *   **Syndicate** — the tower auto-collects each barrel after ~2s (pulled in via a particle beam) for a smaller, taxed yield (60 → 75 → 100). No clicking required.
    *   **Gambling** — each barrel rolls a random outcome. ~70% is a gold payout from 1 to 10,000 (sharply decreasing odds, 0.2% jackpot); ~30% is a random event: a tower damage buff (good) or a nerf (player Weakness, +cooldown on all your towers, or a wave of mobs sent down your own path). Stronger results — jackpots, big buffs, severe nerfs, large waves — are deliberately rare.
