# MOB Update – 6.6.25

Implemented the migration of the mob spawner queue storage from `PresetMobType` keys to upgrade‑chain string keys.

## What was changed

1. **Field definition**
   ```java
   private final Map<UUID, Map<String, Integer>> playerQueues = new HashMap<>();
   ```
   The inner map now uses the mob **upgrade chain** (`String`) as the key.

2. **Queue accessor**
   ```java
   public Map<String, Integer> getQueue(UUID uuid) { … }
   ```
   Initializes the map with all preset chains (`type.name().toLowerCase()`) set to `0`.

3. **Add / remove helpers**
   Updated to convert a `PresetMobType` to its chain name before modifying the map.
   ```java
   String chain = type.name().toLowerCase();
   Map<String, Integer> queue = getQueue(uuid);
   queue.put(chain, queue.getOrDefault(chain, 0) + 1);
   ```

4. **Queue clearing**
   Iterates over the string keys and resets each count to `0`.

5. **`sendQueue`**
   - Retrieves the string‑key map.
   - Builds a `List<String>` of upgrade chains to spawn.
   - Determines the player’s tier via `GameManager#getMobTier(UUID, String)`.
   - Calls `spawnMobByChain(targetArena, upgradeChain, tier)` for each entry.
   - Clears the queue after spawning.

6. **GUI updates (`openMobSpawnerGUI`)**
   - Queue look‑ups now use `preset.name().toLowerCase()` as the key.
   - Cost calculation iterates over `Map<String,Integer>` entries, obtains the correct tiered `MobStateProfile` from `MobUpgradeRegistry`, and falls back to config values if needed.

7. **Cost loop**
   Re‑written to work with string keys and include a fallback using `PresetMobType.valueOf(chain.toUpperCase())`.

## Result
The mob spawner now correctly tracks queued mobs by their upgrade chain, supports tiered upgrades, and calculates wave costs using the new `MobUpgradeRegistry`. All related methods and the GUI have been synchronized with this new data model.

*File created: `MOB_UPDATE_6_6_25.md` at the project root.*

---

## Session 2 – Polymorphic Mob Upgrade System (6.6.25)

Full implementation of the polymorphic mob upgrade system as outlined in `mob_implementation_guide.md`, using `mob_upgrades_polymorphic.csv` as the data source.

### New files

#### `MobStateProfile.java`
Immutable data class holding one row from the CSV:
`upgradeChain`, `tier`, `entityType`, `price`, `damage`, `hp`, `speed`, `expReward`, `immunities` (`List<String>`), `mountType` (`EntityType|null`), `equipment` (`Material|null`), `specialMechanics`.
All fields exposed via getters.

#### `MobUpgradeRegistry.java`
Loads and indexes `mob_upgrades_polymorphic.csv` at plugin startup.

Key design decisions:
- **Quoted-field CSV parser** (`parseCsvLine`): character-by-character loop tracking an `inQuotes` flag, handles fields like `"MAGE, ZEUS"` correctly. The original `line.split(",")` approach broke on these.
- **Path resolution**: tries `plugin.getDataFolder().getParentFile().getParentFile()` (server root) first, falls back to CWD. Removes the hardcoded absolute path that was in earlier drafts.
- **"None" guard**: explicit `equalsIgnoreCase("None")` check before `EntityType.valueOf()` / `Material.valueOf()` to avoid `IllegalArgumentException`.
- **`LinkedHashMap`** for registry outer map: preserves CSV insertion order so the GUI slot assignment matches the CSV order.
- Public API: `getProfile(chain, tier)`, `getAvailableChains()`, `getAllTiers(chain)`, `getMaxTier(chain)`.

---

### Changes to `MobManager.java`

#### Return type fix on `spawnMob` full overload
Changed signature from `void` to `Mob`:
```java
public Mob spawnMob(String arena, EntityType type, ..., String presetKey)
```
Early-exit guard updated from `return;` → `return null;` (was a compile error).
Now returns the spawned `Mob` entity so `spawnMobByChain` can apply post-spawn configuration.

#### New utility methods
| Method | Purpose |
|---|---|
| `getUpgradeRegistry()` | Exposes registry to listener layer |
| `MOB_SLOTS` (int[] constant) | Single source of truth for GUI slot → chain index |
| `getChainForSlot(int slot)` | Reverse-maps a clicked GUI slot to its chain key |
| `getChainDisplayName(String)` | Title-cases chain keys (`"zombie pigman"` → `"Zombie Pigman"`) |
| `getChainMaterial(String)` | Maps chain key to representative spawn-egg `Material` |
| `getTierColor(int)` | `GRAY/GREEN/AQUA/LIGHT_PURPLE/GOLD` for tiers 1–5 |

#### `getQueue(UUID)` updated
Now seeds the queue with all chains from `upgradeRegistry.getAvailableChains()` using a `LinkedHashMap` to preserve order.

#### New `addToQueue(UUID, String)` / `removeFromQueue(UUID, String)` overloads
Accept chain-key strings directly in addition to the legacy `PresetMobType` overloads (kept for backward compatibility).

#### `openMobSpawnerGUI` rewritten (54-slot)
- Places all 16 chains using `MOB_SLOTS`, one per slot.
- Each icon built by `createChainGUIItem`: shows current tier, queue count (as stack size), cost, EXP payout, stats, immunities, and special mechanics.
- Computes total queue cost from `MobUpgradeRegistry` prices.
- Retain Clear Queue (slot 38), Send Wave (slot 40), Player Upgrades (slot 42) buttons.

#### `openMobTierGUI(Player, String chain)` — new 27-slot sub-GUI
Title: `ChatColor.DARK_PURPLE + "Mob Tier: " + displayName` (used by listener for routing).
Tiers 1–5 placed at slots 10–14 via `createTierItem`.  Selected tier gets a `★ SELECTED` suffix.
Back button at slot 22.

#### `spawnMobByChain` updated
Post-spawn steps now applied using the `Mob` return value from `spawnMob`:
- **Flying**: disables gravity, teleports entity +3Y.
- **Immunities**: written to PDC `td_immunities` key as comma-separated string.
- **Equipment**: helmet → `setHelmet`, sword/axe → `setItemInMainHand`; drop chance set to 0.
- **Charged Creeper**: `setPowered(true)` when `specialMechanics.contains("Charged")`.
- **Mount**: spawns mount entity, disables its AI, calls `mount.addPassenger(entity)`.

---

### Changes to `MobListener.java`

#### "TD Mob Spawner" click handler replaced
- Uses `mobManager.getChainForSlot(slot)` to identify clicked chain.
- **Left-click**: opens tier sub-GUI via `mobManager.openMobTierGUI(player, chain)`.
- **Right-click / Shift+Right-click**: dequeues 1 or 10 of the chain and refunds gold.

#### New "Mob Tier: " handler
Routing condition: `title.startsWith(ChatColor.DARK_PURPLE + "Mob Tier: ")`.
Chain key extracted as `title.substring(prefix.length()).toLowerCase()`.

| Slot | Action |
|---|---|
| 10–14 | Tier 1–5 selection |
| 22 | Back → `openMobSpawnerGUI` |

On tier click:
1. `plugin.getGameManager().setMobTier(uuid, chainKey, selectedTier)`
2. `mobManager.addToQueue(uuid, chainKey)` × 1 (Shift+Left × 5)
3. Close inventory.
