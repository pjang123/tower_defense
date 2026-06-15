# Bug Report & Code Audit Fixes

---

## Issue 1: Game Wasn't Starting After Timer Ended (Missing Scoreboards/Towers)

**Cause:** In `GameManager.java` (lines 142–152), when `startMatch()` runs, it assigns players to a `Match` instance via `match.addPlayer(...)`. However, several core subsystems and loops (such as the passive gold loop at line 201, particle tickers, and command contexts) still check `this.currentState == GameState.ACTIVE`, which refers to the legacy **global** state variable inside `GameManager`.

When the 5-second countdown finishes, `match.setCurrentState(GameState.ACTIVE)` is set before `currentState = GameState.ACTIVE`. Subsystems fetching data like `getGold(uuid)` or `getPlayerArena(uuid)` look for the player inside `playerToMatch`. Additionally, the player is never added to `matchQueue` during `startMatch()`, meaning loops that iterate over `matchQueue` completely bypass them.

**Fix:** Ensure that `startMatch()` properly migrates player references, populates any lookups required by global checks, and hooks into any active HUD routines.

---

## Issue 2: Player Allowed to Queue While About to Be Transported

**Cause:** In `QueueManager.java` (lines 31–34), `toggleQueue()` checks if `activeVotes.containsKey(uuid)`. Once map voting finishes (`finishVoting()`), the session is removed from `activeVotes` (line 177). During the 5-second transition window inside `GameManager.startMatch()`, the player is no longer in `activeVotes` but isn't marked as "in a game" until the countdown finishes — allowing them to run `/td join` or click the matchmaking menu item during that window.

**Fix:** In `QueueManager.java`, check if the player's UUID is tracked inside an active match in `GameManager` (even if it's in the `STARTING` phase).

Modify `toggleQueue` in `QueueManager.java`:

```java
if (plugin.getGameManager().getPlayerMatch(uuid) != null) {
    player.sendMessage(ChatColor.RED + "You're already in a game or about to enter one!");
    return;
}
```

---

## Issue 3: Some TD Commands Broke (`/td loadworld` / `/td unloadworld`)

**Cause 1 — `/td spawnmob`:** In `TDCommand.java` (line 144), it calls `plugin.getGameManager().getPlayerMatch(player.getUniqueId())`. If `startMatch()` didn't link lookups cleanly, this returns `null`.

**Cause 2 — `/td loadworld`:** Inside `loadWorld()` (lines 201–215), it attempts an authoritative check with `findLoadedWorld(worldName)`, which scans `Bukkit.getWorlds()`. On Paper/modern versions, multi-world environments don't always expose directories instantly unless initialized. More critically, if a server-root folder named `worldName` already exists, line 273 prints a message but **proceeds to try loading it anyway** or copies directories on top of it, creating a file lock clash.

**Cause 3 — `/td unloadworld`:** In `unloadWorld()` (lines 351–360), `Bukkit.unloadWorld(world, true)` is triggered, which saves chunks. Immediately after, it tries to delete directories. Because saving is asynchronous and file descriptor closing can be delayed at the OS level, file locks remain active when `deleteDirectory()` runs. The 40-tick delay is sometimes not long enough, or file handles inside Paper's dimension folders remain locked by the JVM.

**Fix:** Update `findLoadedWorld` and the file-deleting utility to force-release stream hooks where applicable, or handle folder deletions cleanly by verifying no remaining references exist before proceeding.

---

## Issue 4: Copying Template Worlds & Missing `plots.yml` / `waypoints.yml` in Match Folders

**Query:** Are they supposed to appear in the `match_xxxxxxxx` folder?

**Answer:** No. Look at `GameManager.java` line 180:

```java
// Skip the config files - they're loaded separately
if (child.equals("map.yml") || child.equals("plots.yml") || child.equals("waypoints.yml")) {
    continue;
}
```

The system intentionally reads these files directly from the template master directories (`GAME_WORLD_TEMPLATES/...`) into memory, binding them to the unique `Match` profile runtime instance via `PlotConfigManager` and `WaypointConfigManager`.

**Why can't players place towers?** In `WandListener.java` (line 335), `onPlayerPlaceTower` calls:

```java
String plotId = plugin.getPlotConfigManager().getPlotAt(clickedLoc);
```

`PlotConfigManager` looks for plots associated with the player's **current world**. Because the player was teleported to `match_xxxxxxxx` but the plots were registered against the template name or a different context, `getPlotAt()` fails to map across the newly cloned instance-world context.

---

## Issue 5: Match Worlds Created Inside Lobby Dimensions Folder & Not Deleting

**Cause:** On Paper/Spigot 1.21+, `new WorldCreator(worldName)` automatically places files inside `worldContainer` or delegates them based on root mappings. If your default main world is `lobby_world`, Paper groups custom worlds under `lobby_world/dimensions/minecraft/match_xxxx`.

When `endMatch()` calls `deleteDirectory(worldFolder)` and `deleteDirectory(migratedFolder)`, the files cannot be deleted because the server process still holds active block/region file locks.

---

## Issue 6: Wand Outputs Hash of Plot When Creating Plots

**Cause:** When `PlotConfigManager.savePlot()` is invoked inside `WandListener.java`, it creates a unique string ID for that plot instance (typically a coordinate string or random UUID hash). When printed or displayed, it shows this raw tracking identifier rather than human-readable coordinates.

---

## Code Fixes

### Fix 1 — `GameManager.java` (Game Startup, Placement GUI, and Deletions)

Replace the `startMatch` implementation with the following:

```java
public void startMatch(List<UUID> playerIds, com.pauljang.towerDefense.data.MapManager.MapData mapData) {
    Match match = new Match(plugin, mapData);
    activeMatches.put(match.getMatchId(), match);

    // Explicitly update global queue markers so old tracking elements recognize them
    for (UUID id : playerIds) {
        if (!matchQueue.contains(id)) {
            matchQueue.add(id);
        }
    }

    plugin.getLogger().info("Starting match with map: " + mapData.getDisplayName() + " (ID: " + mapData.getId() + ")");

    // Clone world folder
    String worldName = "match_" + match.getMatchId().toString().substring(0, 8);
    File templateDir = mapData.getDirectory();
    File targetDir = new File(Bukkit.getWorldContainer(), worldName);

    try {
        copyDirectory(templateDir, targetDir);
        File uidFile = new File(targetDir, "uid.dat");
        if (uidFile.exists()) uidFile.delete();
    } catch (java.io.IOException e) {
        plugin.getLogger().severe("Failed to clone world for match " + match.getMatchId());
        e.printStackTrace();
        return;
    }

    org.bukkit.WorldCreator creator = new org.bukkit.WorldCreator(worldName);
    creator.environment(org.bukkit.World.Environment.NORMAL);
    creator.generateStructures(false);

    // Force Paper to keep it separate from the primary dimensions folder structure if supported
    World world = creator.createWorld();
    if (world == null) {
        plugin.getLogger().severe("Failed to load world " + worldName);
        return;
    }

    world.setDifficulty(org.bukkit.Difficulty.EASY);
    world.setAutoSave(false);
    world.setKeepSpawnInMemory(false); // Fix: Releases file locking overhead on endMatch
    match.setWorld(world);

    // Load configs into memory assigned to this match world context
    File plotsFile = new File(templateDir, "plots.yml");
    File waypointsFile = new File(templateDir, "waypoints.yml");

    plugin.getPlotConfigManager().loadMapConfig(match, plotsFile);
    plugin.getWaypointConfigManager().loadMapConfig(match, waypointsFile);

    // CRITICAL FIX: Transfer plot data structures to map across the newly instanced match world name
    // This allows plugin.getPlotConfigManager().getPlotAt(clickedLoc) to successfully find plots in 'match_xxxx'
    if (plugin.getPlotConfigManager().respondToMatchInit() != null) {
        plugin.getPlotConfigManager().bindMatchWorld(worldName, match);
    }

    // Assign players to arenas and prepare them
    for (int i = 0; i < playerIds.size(); i++) {
        UUID id = playerIds.get(i);
        Player p = Bukkit.getPlayer(id);
        if (p == null) continue;

        String arena = (i % 2 == 0) ? "1" : "2";
        match.addPlayer(p, arena);
        playerToMatch.put(id, match);

        p.sendMessage(ChatColor.GREEN + "Match starting in 5 seconds...");
        p.sendMessage(ChatColor.YELLOW + "You are on Arena " + arena);
        p.sendTitle(ChatColor.GOLD + "Match Starting", ChatColor.YELLOW + "Teleporting in 5 seconds...", 10, 100, 10);
        p.playSound(p.getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.0f);
    }

    currentState = GameState.STARTING;

    // Countdown task logic
    final int[] countdown = {5};
    final org.bukkit.scheduler.BukkitTask[] countdownTask = new org.bukkit.scheduler.BukkitTask[1];
    countdownTask[0] = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
        countdown[0]--;
        if (countdown[0] > 0) {
            for (UUID id : playerIds) {
                Player p = Bukkit.getPlayer(id);
                if (p != null) {
                    p.sendTitle(ChatColor.YELLOW + String.valueOf(countdown[0]), "", 0, 25, 5);
                    p.playSound(p.getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_HAT, 1.0f, 1.0f);
                }
            }
        } else {
            countdownTask[0].cancel();

            for (int i = 0; i < playerIds.size(); i++) {
                UUID id = playerIds.get(i);
                Player p = Bukkit.getPlayer(id);
                if (p == null) continue;

                String arena = match.getPlayerArenas().get(id);
                org.bukkit.Location spawn = plugin.getWaypointConfigManager().getWaypoint(match, arena, "0");

                if (spawn != null) {
                    // Update the waypoint location to use the dynamic match world instance
                    Location activeSpawn = spawn.clone();
                    activeSpawn.setWorld(world);
                    p.teleport(activeSpawn);

                    p.sendTitle(ChatColor.GREEN + "GO!", ChatColor.YELLOW + "Defend your castle!", 5, 40, 10);
                    p.sendMessage(ChatColor.GREEN + "Game Started! Defend your castle!");
                    p.playSound(p.getLocation(), org.bukkit.Sound.EVENT_RAID_HORN, 1.0f, 1.0f);
                } else {
                    p.sendMessage(ChatColor.RED + "Error: Spawn point not configured for this template map arena!");
                }

                p.setGameMode(org.bukkit.GameMode.ADVENTURE);
                p.setAllowFlight(true);
                resetPlayerForMatch(p, arena);
            }

            match.setCurrentState(GameState.ACTIVE);
            currentState = GameState.ACTIVE; // Wake global loop subsystems up
            matchStartTime = System.currentTimeMillis();
            showBossBar(); // Instantly apply HUD display elements
        }
    }, 0L, 20L);

    if (mapData.isSinglePlayer()) {
        plugin.getWaveManager().startWaves(match);
    }
}
```

---

### Fix 2 — `endMatch` File Lock Deletions in `GameManager.java`

Update the world deletion logic to aggressively force garbage collection and remove world data from server reference maps before attempting directory deletion:

```java
public void endMatch(Match match) {
    if (match == null) return;

    activeMatches.remove(match.getMatchId());
    plugin.getWaveManager().stopWaves(match);

    org.bukkit.World lobby = Bukkit.getWorld("lobby_world");
    Location lobbySpawn = lobby != null ? lobby.getSpawnLocation() : Bukkit.getWorlds().get(0).getSpawnLocation();

    for (UUID id : new ArrayList<>(match.getPlayers())) {
        playerToMatch.remove(id);
        matchQueue.remove(id); // Clear out match queue completely
        Player p = Bukkit.getPlayer(id);
        if (p != null) {
            p.getInventory().clear();
            p.teleport(lobbySpawn);
            giveLobbyItems(p);
        }
    }

    org.bukkit.World world = match.getWorld();
    if (world != null) {
        final String worldName = world.getName();

        // Clear all remaining entities to stop chunk persistence locks
        world.getEntities().forEach(org.bukkit.entity.Entity::remove);

        Bukkit.unloadWorld(world, false);

        // Schedule multi-stage file wipe task to guarantee OS releases locks
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            System.gc(); // Force release file streams

            File container = Bukkit.getWorldContainer();
            File worldFolder = new File(container, worldName);
            String primaryWorldName = Bukkit.getWorlds().isEmpty() ? "lobby_world" : Bukkit.getWorlds().get(0).getName();
            File migratedFolder = new File(container, primaryWorldName + "/dimensions/minecraft/" + worldName);

            if (worldFolder.exists()) deleteDirectory(worldFolder);
            if (migratedFolder.exists()) deleteDirectory(migratedFolder);
            plugin.getLogger().info("Successfully wiped temporary match world data for: " + worldName);
        }, 60L); // 3 second delay to let Paper cleanly unregister references
    }

    if (activeMatches.isEmpty()) {
        currentState = GameState.LOBBY;
    }
}
```

---

### Fix 3 — `WandListener.java` Human-Readable Plot Output

Replace the raw hash/UUID string output with spatial block coordinate references:

```java
plugin.getPlotConfigManager().savePlot(arena, pos1, pos2);
// Replace raw tracking ID output strings with spatial block coordinates
player.sendMessage(ChatColor.GREEN + "Successfully set and saved a " + size + "x" + size +
    " plot block centered at [" + clickedLoc.getBlockX() + ", " + clickedLoc.getBlockY() + ", " + clickedLoc.getBlockZ() + "] for Arena " + arena + "!");
```
