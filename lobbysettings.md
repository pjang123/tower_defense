# Architecture Handoff: Single-Server Multi-World System

## 1. Overview
Since the infrastructure is limited to a single 6GB server instance (PebbleHost), we cannot use a Velocity/BungeeCord proxy network. Instead, the "Lobby" and the "Game" will be hosted on the same server, separated by **Worlds**.

The plugin will handle routing players between a static `lobby_world` and a volatile `game_world`.

## 2. World Management & Map Resets
In a Tower Defense game, players physically place blocks (towers). Therefore, the `game_world` must be reset after every match.

* **The Template:** Keep a clean, unmodified copy of the arena world folder in the server's root directory (e.g., `game_world_template`).
* **The Reset Logic:** When a game ends (or before the server starts):
    1. Use `Bukkit.unloadWorld("game_world", false)` to unload the active arena *without* saving changes.
    2. Write standard Java `java.nio.file.Files` logic to delete the corrupted `game_world` directory.
    3. Copy the `game_world_template` directory and paste it as `game_world`.
    4. Use `new WorldCreator("game_world").createWorld()` to load the fresh map into the server's memory.

## 3. Player Join Logic (The Lobby)
When a player connects to the server IP, they must automatically be routed to the lobby, regardless of where they logged off.

* **Event:** Listen for `PlayerJoinEvent`.
* **Action:**
    1. Instantly teleport the player to the `lobby_world` spawn coordinates.
    2. Clear their inventory (`player.getInventory().clear()`).
    3. Set their Gamemode to Adventure (`GameMode.ADVENTURE`).
    4. Give them lobby-specific items (e.g., a "Join Match" compass or dye).

## 4. The Queue System
Players need a way to opt into the next match.

* **Data Structure:** Create a `List<UUID> matchQueue = new ArrayList<>();` inside the GameManager.
* **Opt-In:** When a player clicks the "Join Match" item or right-clicks a Lobby NPC, add their UUID to `matchQueue`.
* **Validation:** Prevent players from joining the queue if `GameState` is already `ACTIVE`.
* **Triggers:** If `matchQueue.size()` reaches the required player count (or a countdown timer finishes), trigger the match transition.

## 5. Match Transition (Lobby -> Game)
When the queue pops, shift the players from the lobby to the arena.

* **State Change:** Change `GameState` from `LOBBY` to `STARTING`.
* **Teleportation:** Iterate through the `matchQueue`. Teleport every UUID in the list to the `game_world` spawn point.
* **Initialization:**
    1. Grant them starting gold/coins.
    2. Give them the Shop GUI item.
    3. Change `GameState` to `ACTIVE` and begin the custom mob pathfinding waves.

## 6. Post-Match Cleanup
When the final wave ends or the Castle health reaches 0:

* **State Change:** Set `GameState` to `ENDED`.
* **Return:** Teleport all players in the `game_world` back to the `lobby_world` spawn.
* **Data Wipe:** Clear the `matchQueue` list. Reset individual player stats (gold, score).
* **Map Reset:** Trigger the World Management reset logic (Step 2) to prepare for the next queue pop.