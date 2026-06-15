# Tower Defense Plugin — Session Context

This document contains everything a new Claude Code session needs to continue work on this project.

---

## Project Overview

A 1v1 PvP Tower Defense Minecraft plugin built on **Paper 1.21.5** (api-version `26.1.2`). Two players occupy mirrored arenas, send mobs against each other, and place towers to defend a castle. The plugin is hosted on PebbleHost (`na2092.pebblehost.net:2222`).

**GitHub repo:** `https://github.com/pjang123/tower_defense`  
**Build system:** Maven (`mvn package` → produces JAR in `target/`)  
**Main class:** `com.pauljang.towerDefense.TowerDefense`  
**Plugin name:** `Tower_Defense`

---

## Source File Map

```
src/main/java/com/pauljang/towerDefense/
├── TowerDefense.java               — Plugin entry point, registers managers/listeners
├── core/
│   ├── GameManager.java            — Game state, gold/XP economy, castle health, spells,
│   │                                 mob tier tracking (getMobTier/setMobTier/isTierUnlocked/unlockTier)
│   ├── GameState.java              — Enum: LOBBY, ACTIVE, ENDED
│   └── TDCommand.java              — /td command handler
├── data/
│   ├── PlotConfigManager.java      — Reads plot center locations from config
│   ├── TDWaypoint.java             — Waypoint data object (id, location, nextIds)
│   └── WaypointConfigManager.java  — Loads waypoint graphs from config per arena
├── entities/
│   ├── MobManager.java             — Mob spawning, per-tick movement, queue/send wave,
│   │                                 mob spawner GUI, tier-selection GUI
│   ├── MobStateProfile.java        — *** MISSING FROM GIT (see below) ***
│   ├── MobUpgradeRegistry.java     — *** MISSING FROM GIT (see below) ***
│   ├── PresetMobType.java          — Legacy enum (being replaced by chain+tier system)
│   └── TDMob.java                  — Tracks one live mob: entity ref, waypoint state, path history
├── listeners/
│   ├── MobListener.java            — Inventory click handler (spawner GUI, tower GUI, tier GUI),
│   │                                 player events, Happy Ghast mount/dismount
│   └── WandListener.java           — TD Wand right-click for waypoint/plot setup
├── setup/
│   └── SetupManager.java           — Admin setup flow
└── towers/
    ├── TargetingMode.java           — Enum: FIRST, LAST, STRONGEST, WEAKEST, CLOSEST
    ├── Tower.java                   — Per-tower state: type, level, lastAttackTick, holograms,
    │                                  structureSize, targetingMode, spawnedGhast, ownerId
    ├── TowerManager.java            — Places/removes towers, per-tick attack logic, tower GUIs,
    │                                  Happy Ghast spawning
    └── TowerType.java               — Enum of all tower types with base stats
```

**Key resources:**
- `src/main/resources/config.yml` — mob stats (height-offset, speed, health, armor), tower stats, spell configs
- `src/main/resources/plugin.yml` — commands and api-version
- `mob_upgrades_polymorphic.csv` — 16 mob chains × 5 tiers, defines EntityType/HP/speed/price/equipment/mount/mechanics

---

## Critical Missing Files — Must Be Created

`MobManager.java` imports and uses two classes that **have never been committed to the git repo**:

```java
import com.pauljang.towerDefense.entities.MobStateProfile;
import com.pauljang.towerDefense.entities.MobUpgradeRegistry;
```

These files presumably exist on the user's local machine (the plugin compiles and runs) but were never committed. A new session should:
1. Ask the user to commit them (`git add src/.../MobStateProfile.java src/.../MobUpgradeRegistry.java && git commit`)
2. Or recreate them from scratch based on usage in `MobManager.java`

**`MobStateProfile` — what MobManager calls on it:**
```java
profile.getPrice()       // double
profile.getHp()          // double
profile.getSpeed()       // double
profile.getDamage()      // double
profile.getExpReward()   // int
profile.getImmunities()  // List<String> (tower type names like "MAGE", "ZEUS", "CHORUS")
profile.getMountType()   // EntityType (nullable)
profile.getEquipment()   // Material (nullable)
profile.getSpecialMechanics() // String
profile.getEntityType()  // EntityType
```

**`MobUpgradeRegistry` — what MobManager calls on it:**
```java
registry.getProfile(String chain, int tier)         // MobStateProfile (nullable)
registry.getAllTiers(String chain)                   // Map<Integer, MobStateProfile>
registry.getAvailableChains()                       // List<String> (ordered)
```
The registry reads from `mob_upgrades_polymorphic.csv`. The CSV has columns:
`Upgrade Chain, Tier, Entity Type, Price, Damage, HP, Speed, EXP Reward, Immunities, Mount, Equipment, Special Mechanics`

---

## Mob Chain System

16 mob chains, each with 5 tiers. The chain name (lowercase) is the key everywhere:

| Chain | T1 Entity | T5 Entity | Notes |
|---|---|---|---|
| zombie | ZOMBIE | ZOMBIE | Equipment improves each tier |
| spider | SPIDER | CAVE_SPIDER | T3+ becomes Cave Spider |
| skeleton | SKELETON | WITHER_SKELETON | T2+ mounted on horse; T4-5 = Wither Skeleton |
| creeper | CREEPER | CREEPER | T3+ mounted on Pig/Cow; explodes at castle |
| silverfish | SILVERFISH | SILVERFISH | |
| blaze | BLAZE | BLAZE | Flying (height-offset: 1.5) |
| pigman | ZOMBIFIED_PIGLIN | ZOMBIFIED_PIGLIN | Brain AI |
| witch | WITCH | WITCH | |
| slime | SLIME | MAGMA_CUBE | T4-5 = MagmaCube; height-offset: 1.5 |
| giant | GIANT | GIANT | Velocity-driven; 0 base speed floored to 0.25 |
| warden | WARDEN | WARDEN | Brain AI; velocity-driven |
| ravager | RAVAGER | RAVAGER | |
| hoglin | HOGLIN | ZOGLIN | Brain AI; T5 = Zoglin |
| enderman | ENDERMAN | ENDERMAN | Brain AI; teleport suppressed via setAI(false) |
| endermite | ENDERMITE | ENDERMITE | |
| breeze | BREEZE | BREEZE | Brain AI; hovering |

**Height offsets (from config.yml):**
- Blaze: 1.5, Slime/MagmaCube: 1.5, Ghast: 4.0 (Happy Ghast tower)
- All others: 0.0

---

## Mob Movement Architecture

`TDMob` tracks each live mob's waypoint state. `MobManager.handleMobMovement()` is called every tick.

Two movement branches:

**Velocity branch** — used for: GIANT, SLIME (includes MAGMA_CUBE), CREEPER, WARDEN, ENDERMAN, ZOMBIFIED_PIGLIN, HOGLIN, ZOGLIN, BREEZE, any mob whose entity has a vehicle (`getVehicle() instanceof Mob`), or any mob with heightOffset > 0.
- Applies `entity.setVelocity(dir * speed)` directly
- For mounted mobs (e.g. Skeleton on horse), `physicalMover = vehicle` so the vehicle is driven

**Pathfinder branch** — all others
- Calls `entity.getPathfinder().moveTo(target)` each tick

**Key rule:** Brain AI mobs (Warden, Zombified Piglin, Hoglin, Zoglin, Breeze) and Enderman **ignore** `removeAllGoals()` (which `TDMob` constructor calls). They MUST have `setAI(false)` called in `spawnMob()` AND be routed through the velocity branch.

---

## Git History (container state)

```
6bb9b39  fix: add diagnostic logging to Happy Ghast harness equip
3bd0ff5  fix: clamp setHealth to attribute effective value to avoid Paper 1.21 crash
88cd7b6  chore: commit prior session changes  (GameManager tiers, MobListener GUI refactor, Tower.ownerId)
4c1a784  fix: disable AI on Brain-AI/teleport mobs and route all to velocity branch
de413fc  cleaning
8b53261  feat: implement mob unlocks, lobby compass restrictions, and Happy Ghast steering
...older commits...
```

**User's local machine is at commit `4c1a784`.** Commits `88cd7b6`, `3bd0ff5`, and `6bb9b39` exist only in the container and have NOT been applied to the user's local files yet.

---

## Pending Patches — Not Yet on User's Machine

### Patch 1 (critical — apply first): `critical_fixes_only.patch`

Two bug fixes targeting only `MobManager.java` and `TowerManager.java`:

**Fix A — Health cap crash (MobManager.java ~line 237):**
```java
// BEFORE:
entity.setHealth(maxHealth);

// AFTER:
entity.setHealth(Math.min(maxHealth, maxHealthAttr.getValue()));
```
Paper 1.21 hard-caps `setHealth()` at 1024.0 regardless of `MAX_HEALTH` attribute. High-HP mobs (Giant T1 = 3000 HP, Warden T5 = 2000+ HP) crashed with `IllegalArgumentException` on every spawn. The entity appeared in the world but TDMob was never created, making it look like the mob was continuously respawning.

**Fix B — Happy Ghast harness (TowerManager.java ~line 207):**
Replace the silent `getMaterial("HARNESS")`-returns-null block with:
1. Try `ghast.getClass().getMethod("setHarnessed", boolean.class)` via reflection
2. Fall back to equipping `Material.valueOf("HARNESS")` in the **BODY** slot (not HELMET)
3. Each step logs to server console so you can see exactly which method worked or what error occurred

### Patch 2 (feature — apply after Patch 1): GameManager/MobListener/Tower changes

These are in commits `88cd7b6` and were part of the larger tier-selection GUI system:
- `GameManager.java`: adds `getMobTier()`, `setMobTier()`, `isTierUnlocked()`, `unlockTier()` methods plus `playerMobTiers` and `playerUnlockedTiers` maps
- `MobListener.java`: rewrites mob spawner GUI click handler to use chain-based system (opens tier sub-GUI on left-click), adds tier-selection GUI handler, adds Happy Ghast "return to post" button, adds owner-only mount check, adds dismount drift-fix
- `Tower.java`: adds `ownerId` field and getter/setter

Apply order matters: Patch 2 depends on the `getMobTier()` etc. methods that Patch 1 does not add. Apply Patch 1, build and test, then apply Patch 2.

---

## Known Paper 1.21.5 Constraints

| Issue | Detail |
|---|---|
| `setHealth()` cap | Hard ceiling of 1024.0 regardless of MAX_HEALTH attribute. Use `Math.min(maxHealth, maxHealthAttr.getValue())`. |
| Brain AI mobs | `Bukkit.getMobGoals().removeAllGoals(entity)` is a no-op for Warden, Zombified Piglin, Hoglin, Zoglin, Breeze. Must call `setAI(false)` instead. |
| Happy Ghast harness | `Material.getMaterial("HARNESS")` returns null. Use `setHarnessed(boolean)` via reflection or `Material.valueOf("HARNESS")` in BODY equipment slot. |
| Network policy | From Claude Code remote containers, PaperMC repos and PebbleHost SFTP are blocked. Cannot `mvn compile` in-container. Cannot deploy via SFTP. |

---

## Deployment

1. Build locally: `mvn package` → `target/Tower_Defense-0.1-Snapshot.jar`
2. Upload JAR to PebbleHost via SFTP or control panel
3. Server host: `na2092.pebblehost.net`, port `2222`
4. After restart, check server log for `[HappyGhast]` diagnostic lines to confirm harness status

---

## What Still Needs Testing / Work

1. **Apply `critical_fixes_only.patch`** locally and rebuild. This fixes the crash that caused Giant/Warden to appear to continuously respawn, and adds harness diagnostic logging.
2. **Check server log after rebuild** for `[HappyGhast] Harness applied via setHarnessed()` or `[HappyGhast] BODY slot equip failed: ...` — the exact error will determine if another approach is needed for the harness.
3. **Commit `MobStateProfile.java` and `MobUpgradeRegistry.java`** — they exist on the user's machine but were never added to git. Until they're committed, the repo cannot be built by a fresh clone.
4. **Apply Patch 2** (GameManager/MobListener/Tower changes) — the tier-selection sub-GUI and chain-based queue system.
5. **Test all mob chains** — especially Brain AI mobs (Warden, Pigman, Hoglin, Breeze, Enderman) and mounted mobs (Skeleton T2-T5 on horse) after the AI/velocity fixes are confirmed working.

---

## How to Push Changes (from user's local machine)

The container cannot push to GitHub (PAT doesn't have write access). The user must apply patches locally and push from their own machine:

```powershell
# In project root (Tower Defense/):
git apply critical_fixes_only.patch   # or use --3way if context conflicts
git add -p                            # review changes
git commit -m "your message"
git push origin main
```

To get the patch files: download them from the Claude Code session file delivery, or copy the diff content manually.
