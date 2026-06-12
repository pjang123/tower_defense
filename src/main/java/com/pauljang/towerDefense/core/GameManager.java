package com.pauljang.towerDefense.core;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.Collections;
import java.util.List;
import java.util.ArrayList;
import java.util.Collection;


import com.pauljang.towerDefense.TowerDefense;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.entity.ArmorStand;

public class GameManager {
    private final TowerDefense plugin;
    
    // Concurrent match management
    private final Map<UUID, Match> activeMatches = new HashMap<>();
    private final Map<UUID, Match> playerToMatch = new HashMap<>();

    // Map of player UUID -> (upgrade chain -> tier)
    private final Map<UUID, Map<String, Integer>> playerMobTiers = new HashMap<>();
    // Map of player UUID -> (upgrade chain -> set of unlocked tiers); tier 1 is always unlocked implicitly
    private final Map<UUID, Map<String, java.util.Set<Integer>>> playerUnlockedTiers = new HashMap<>();

    private GameState currentState = null;
    private long matchStartTime = 0L;

    private int maxCastleHealth = 1000;
    private int matchSize = 8;
    private final Map<String, Integer> arenaHealth = new HashMap<>();
    private final Map<String, Map<String, Long>> activeSpells = new HashMap<>();
    private final Map<Location, Material> originalFloorBlocks = new HashMap<>();
    private final Map<String, java.util.List<ArmorStand>> castleHolograms = new HashMap<>();
    private BossBar castleBossBar = null;

    private java.util.UUID pendingChallenger = null;
    private java.util.UUID pendingChallengeTarget = null;
    private long challengeTimestamp = 0L;
    
    private final java.util.Map<java.util.UUID, Integer> playerGold = new java.util.HashMap<>();
    private final java.util.Map<java.util.UUID, Integer> playerExp = new java.util.HashMap<>();
    private final java.util.Map<java.util.UUID, java.util.Set<com.pauljang.towerDefense.entities.PresetMobType>> playerUnlockedMobs = new java.util.HashMap<>();
    private final java.util.Map<java.util.UUID, Integer> goldGenLevels = new java.util.HashMap<>();
    private final java.util.Map<java.util.UUID, Integer> swordLevels = new java.util.HashMap<>();
    private final java.util.Map<java.util.UUID, Integer> bowLevels = new java.util.HashMap<>();
    private final java.util.Map<java.util.UUID, String> playerArenas = new java.util.HashMap<>();
    private final java.util.List<java.util.UUID> matchQueue = new java.util.ArrayList<>();
    private org.bukkit.scheduler.BukkitTask lobbyQueueTask = null;
    private int lobbyQueueSecondsLeft = 0;

    // Pending action-bar gains, flushed once per second to avoid chat flooding from many kills.
    private final Map<UUID, Integer> pendingGold = new HashMap<>();
    private final Map<UUID, Integer> pendingExp = new HashMap<>();

    // Dynamic spell pricing: track the last cast time and current cost multiplier per spell per player.
    // Each successive cast of a spell doubles its cost; the cost resets after 10s without casting it.
    private final Map<UUID, Map<String, Long>> lastSpellCast = new HashMap<>();
    private final Map<UUID, Map<String, Integer>> spellCostMultiplier = new HashMap<>();
    // Last time the player cast ANY spell, used to enforce a global 0.5s anti-spam cooldown.
    private final Map<UUID, Long> lastAnySpellCast = new HashMap<>();

    // Cooldown windows (ms): global anti-spam between any spells, and the long Freeze-specific cooldown.
    private static final long GLOBAL_SPELL_COOLDOWN_MS = 500L;
    private static final long SPELL_PRICE_RESET_MS = 10000L;
    private static final long FREEZE_COOLDOWN_MS = 30000L;

    /**
     * Returns the current tier for a given upgrade chain for the player.
     * If no tier is stored, defaults to 1.
     */
    public int getMobTier(UUID playerId, String upgradeChain) {
        return playerMobTiers
                .getOrDefault(playerId, Collections.emptyMap())
                .getOrDefault(upgradeChain, 1);
    }

    /**
     * Sets the tier for a given upgrade chain for the player.
     */
    public void setMobTier(UUID playerId, String upgradeChain, int tier) {
        playerMobTiers.computeIfAbsent(playerId, k -> new HashMap<>()).put(upgradeChain, tier);
    }

    /** Tier 1 is always unlocked; higher tiers require explicit unlock via XP. */
    public boolean isTierUnlocked(UUID playerId, String chain, int tier) {
        if (tier <= 1) return true;
        return playerUnlockedTiers
                .getOrDefault(playerId, Collections.emptyMap())
                .getOrDefault(chain.toLowerCase(), Collections.emptySet())
                .contains(tier);
    }

    public void unlockTier(UUID playerId, String chain, int tier) {
        playerUnlockedTiers
                .computeIfAbsent(playerId, k -> new HashMap<>())
                .computeIfAbsent(chain.toLowerCase(), k -> new java.util.HashSet<>())
                .add(tier);
    }

    public void startMatch(List<UUID> playerIds, com.pauljang.towerDefense.data.MapManager.MapData mapData) {
        Match match = new Match(plugin, mapData);
        activeMatches.put(match.getMatchId(), match);

        // The singleton subsystems (tower firing in TowerManager, mob combat/gold in MobListener,
        // and the passive-income/spell tickers in this class) all gate on the global currentState.
        // The new per-match flow tracks state on the Match, so we must also drive the global state
        // or the game never "wakes up" after players are teleported in.
        currentState = GameState.STARTING;

        plugin.getLogger().info("Starting match with map: " + mapData.getDisplayName() + " (ID: " + mapData.getId() + ")");
        plugin.getLogger().info("Map is Single Player: " + mapData.isSinglePlayer());
        plugin.getLogger().info("Template directory: " + mapData.getDirectory().getAbsolutePath());

        // Clone world
        String worldName = "match_" + match.getMatchId().toString().substring(0, 8);
        File templateDir = mapData.getDirectory();
        File targetDir = new File(Bukkit.getWorldContainer(), worldName);

        plugin.getLogger().info("Cloning world from " + templateDir.getAbsolutePath() + " to " + targetDir.getAbsolutePath());

        try {
            copyDirectory(templateDir, targetDir);
            // Remove uid.dat to avoid world conflicts
            File uidFile = new File(targetDir, "uid.dat");
            if (uidFile.exists()) uidFile.delete();

            // Verify critical world files were copied
            File regionDir = new File(targetDir, "region");
            File levelDat = new File(targetDir, "level.dat");

            if (!levelDat.exists()) {
                plugin.getLogger().severe("level.dat not found in cloned world! Path: " + levelDat.getAbsolutePath());
            }
            if (!regionDir.exists() || !regionDir.isDirectory()) {
                plugin.getLogger().severe("Region folder not found at world root! Path: " + regionDir.getAbsolutePath());

                // Check for nested structure
                File nestedRegion = new File(targetDir, "dimensions/minecraft/overworld/region");
                if (nestedRegion.exists()) {
                    plugin.getLogger().severe("Found region data in nested structure: dimensions/minecraft/overworld/region/");
                    plugin.getLogger().severe("This structure causes migration issues! Please move .mca files to region/ at world root.");
                }

                plugin.getLogger().severe("World will generate new terrain instead of using template!");
            } else {
                String[] regionFiles = regionDir.list();
                plugin.getLogger().info("Region folder found with " + (regionFiles != null ? regionFiles.length : 0) + " chunk files");
            }

            plugin.getLogger().info("Successfully cloned world directory");
        } catch (java.io.IOException e) {
            plugin.getLogger().severe("Failed to clone world for match " + match.getMatchId());
            e.printStackTrace();
            return;
        }

        // Create world with proper settings
        org.bukkit.WorldCreator creator = new org.bukkit.WorldCreator(worldName);
        creator.environment(org.bukkit.World.Environment.NORMAL);
        creator.generateStructures(false); // Don't generate new structures

        World world = creator.createWorld();
        if (world == null) {
            plugin.getLogger().severe("Failed to load world " + worldName);
            return;
        }

        // Configure world settings
        world.setDifficulty(org.bukkit.Difficulty.EASY);
        world.setAutoSave(false); // Don't auto-save match worlds
        world.setKeepSpawnInMemory(false); // Release spawn-chunk file locks so endMatch can delete the world

        plugin.getLogger().info("Successfully loaded world: " + worldName);
        match.setWorld(world);

        // Load map-specific config
        File plotsFile = new File(templateDir, "plots.yml");
        File waypointsFile = new File(templateDir, "waypoints.yml");
        plugin.getLogger().info("Loading plots from: " + plotsFile.getAbsolutePath() + " (exists: " + plotsFile.exists() + ")");
        plugin.getLogger().info("Loading waypoints from: " + waypointsFile.getAbsolutePath() + " (exists: " + waypointsFile.exists() + ")");

        plugin.getPlotConfigManager().loadMapConfig(match, plotsFile);
        plugin.getWaypointConfigManager().loadMapConfig(match, waypointsFile);

        // CRITICAL: also mirror the template's plots/waypoints into the in-memory GLOBAL config, remapped
        // to this cloned match world's name. The singleton tower-placement, tower-targeting, spell and
        // castle-HUD systems all read the global config by world name (getPlotAt, getPlotArena,
        // getWaypoints(arena), getWaypointGraph(arena), ...). Without this the cloned world has no plots
        // registered against it and players cannot place towers, and towers/holograms have no waypoints.
        plugin.getPlotConfigManager().loadGlobalForMatch(plotsFile, world);
        plugin.getWaypointConfigManager().loadGlobalForMatch(waypointsFile, world);

        // Reset the global castle health used by the boss bar / holograms for the new match.
        arenaHealth.put("1", maxCastleHealth);
        arenaHealth.put("2", maxCastleHealth);

        // Assign players to arenas and prepare them
        for (int i = 0; i < playerIds.size(); i++) {
            UUID id = playerIds.get(i);
            Player p = Bukkit.getPlayer(id);
            if (p == null) continue;

            // Single-player maps author their track, plots and castle under arena "2" (the wave engine
            // spawns mobs on "2"), so the lone player must be on "2" as well — otherwise they spawn on
            // the empty arena "1", cannot damage the arena "2" mobs, and never see the arena "2" castle.
            String arena = mapData.isSinglePlayer() ? "2" : ((i % 2 == 0) ? "1" : "2");
            match.addPlayer(p, arena);
            playerToMatch.put(id, match);

            p.sendMessage(ChatColor.GREEN + "Match starting in 5 seconds...");
            p.sendMessage(ChatColor.YELLOW + "You are on Arena " + arena);
            p.sendTitle(ChatColor.GOLD + "Match Starting", ChatColor.YELLOW + "Teleporting in 5 seconds...", 10, 100, 10);
            p.playSound(p.getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.0f);
        }

        // Countdown
        final int[] countdown = {5};
        org.bukkit.scheduler.BukkitTask[] countdownTask = new org.bukkit.scheduler.BukkitTask[1];
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

                // Teleport all players
                for (int i = 0; i < playerIds.size(); i++) {
                    UUID id = playerIds.get(i);
                    Player p = Bukkit.getPlayer(id);
                    if (p == null) continue;

                    String arena = mapData.isSinglePlayer() ? "2" : ((i % 2 == 0) ? "1" : "2");

                    // Teleport to arena start (Waypoint 0)
                    org.bukkit.Location spawn = plugin.getWaypointConfigManager().getWaypoint(match, arena, "0");
                    if (spawn != null) {
                        p.teleport(spawn);
                        p.sendTitle(ChatColor.GREEN + "GO!", ChatColor.YELLOW + "Defend your castle!", 5, 40, 10);
                        p.sendMessage(ChatColor.GREEN + "Game Started! Defend your castle!");
                        p.playSound(p.getLocation(), org.bukkit.Sound.EVENT_RAID_HORN, 1.0f, 1.0f);
                        plugin.getLogger().info("Teleported " + p.getName() + " to arena " + arena + " at " + spawn);
                    } else {
                        plugin.getLogger().severe("Failed to teleport " + p.getName() + " - waypoint '0' not found for arena " + arena);
                        p.sendMessage(ChatColor.RED + "Error: Spawn point not found!");
                    }

                    // Give starting equipment
                    p.setGameMode(org.bukkit.GameMode.ADVENTURE);
                    p.setAllowFlight(true);
                    p.getInventory().clear();
                    resetPlayerForMatch(p, arena);
                    giveStarterWeapons(p);
                }

                match.setCurrentState(GameState.ACTIVE);
                // Wake the singleton subsystems (towers, mob combat, passive gold/HUD tickers) which
                // still read the global state. Without this the match world loads and players spawn,
                // but nothing actually runs — the "game never starts" symptom.
                currentState = GameState.ACTIVE;
                matchStartTime = System.currentTimeMillis();

                // Show the castle HUD now the match is live. The new per-match startMatch flow never
                // called these (only the legacy handleGameStart did), so the boss bar and castle
                // holograms never appeared — the "missing scoreboards" symptom.
                showBossBar();
                updateCastleHologram(match, "1");
                updateCastleHologram(match, "2");

                plugin.getLogger().info("Match " + match.getMatchId() + " is now ACTIVE");
            }
        }, 0L, 20L); // Run every second
        
        // If Single Player, start the wave manager
        if (mapData.isSinglePlayer()) {
            plugin.getWaveManager().startWaves(match);
        }
    }

    public void copyDirectory(File source, File target) throws java.io.IOException {
        if (source.isDirectory()) {
            if (!target.exists()) target.mkdirs();
            String[] children = source.list();
            if (children != null) {
                for (String child : children) {
                    // Skip the config files - they're loaded separately
                    if (child.equals("map.yml") || child.equals("plots.yml") || child.equals("waypoints.yml")) {
                        continue;
                    }
                    copyDirectory(new File(source, child), new File(target, child));
                }
            }
        } else {
            java.nio.file.Files.copy(source.toPath(), target.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public java.util.Collection<Match> getActiveMatches() {
        return activeMatches.values();
    }

    public Match getPlayerMatch(UUID playerId) {
        return playerToMatch.get(playerId);
    }

    public GameManager(TowerDefense plugin) {
        this.plugin = plugin;
        this.maxCastleHealth = plugin.getConfig().getInt("game.max-castle-health", 1000);
        this.matchSize = Math.max(2, plugin.getConfig().getInt("game.players-per-match", 8));
        arenaHealth.put("1", maxCastleHealth);
        arenaHealth.put("2", maxCastleHealth);
        
        // Start game loop (every 1 second) for passive gold income and HUD updates
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            boolean isActive = (currentState == GameState.ACTIVE);
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (isActive) {
                    int amount = getPlayerIncomeRate(player.getUniqueId());
                    addGold(player.getUniqueId(), amount, true); // Quiet passive generation
                }
                updateScoreboard(player);
                // Keep the inventory completely clear of arrows
                player.getInventory().remove(Material.ARROW);

                // Flush any accumulated gold/exp gains as a single combined chat message. Action bars
                // were unreliable (overridden by other HUD updates), so use standard chat instead.
                Integer pGold = pendingGold.remove(player.getUniqueId());
                Integer pExp = pendingExp.remove(player.getUniqueId());
                if (pGold != null || pExp != null) {
                    StringBuilder sb = new StringBuilder();
                    if (pGold != null && pGold != 0) {
                        sb.append(ChatColor.GOLD).append("+").append(pGold).append(" Gold");
                    }
                    if (pExp != null && pExp != 0) {
                        if (sb.length() > 0) sb.append(ChatColor.GRAY).append(" | ");
                        sb.append(ChatColor.GREEN).append("+").append(pExp).append(" XP");
                    }
                    if (sb.length() > 0) {
                        player.sendMessage(sb.toString());
                    }
                }
            }
            // Keep the castle HP holograms alive and current. They live at the far end of each track,
            // whose chunks unload when no one is near (keepSpawnInMemory=false), removing the stands;
            // refreshing here recreates them whenever a player is back in range to see them.
            if (isActive) {
                updateBossBar();
                updateCastleHologram("1");
                updateCastleHologram("2");
            }
            updateTabNames();
        }, 0L, 20L);

        // Spell Particle Ticker (every 5 ticks)
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (currentState != GameState.ACTIVE) return;
            
            for (String arena : new String[]{"1", "2"}) {
                boolean overcharge = isSpellActive(arena, "OVERCHARGE");
                boolean freeze = isSpellActive(arena, "FREEZE");
                boolean storm = isSpellActive(arena, "DAMAGE_STORM");
                boolean haste = isSpellActive(arena, "HASTE_RUSH");
                boolean emp = isSpellActive(arena, "TOWER_EMP");
                boolean shield = isSpellActive(arena, "SLOW_SHIELD");
                
                if (!overcharge && !freeze && !storm && !haste && !emp && !shield) continue;
                
                java.util.List<Location> waypoints = plugin.getWaypointConfigManager().getWaypoints(arena);
                for (Location wp : waypoints) {
                    Location loc = wp.clone().add(0, 0.1, 0);
                    org.bukkit.World world = loc.getWorld();
                    if (world == null) continue;
                    
                    if (overcharge) {
                        world.spawnParticle(org.bukkit.Particle.HAPPY_VILLAGER, loc, 1, 0.3, 0.1, 0.3, 0.0);
                    }
                    if (freeze) {
                        world.spawnParticle(org.bukkit.Particle.SNOWFLAKE, loc, 1, 0.3, 0.1, 0.3, 0.0);
                    }
                    if (storm) {
                        world.spawnParticle(org.bukkit.Particle.FLAME, loc, 1, 0.3, 0.1, 0.3, 0.02);
                    }
                    if (haste) {
                        world.spawnParticle(org.bukkit.Particle.CRIT, loc, 1, 0.3, 0.1, 0.3, 0.0);
                    }
                    if (emp) {
                        world.spawnParticle(org.bukkit.Particle.SMOKE, loc, 1, 0.3, 0.1, 0.3, 0.0);
                    }
                    if (shield) {
                        world.spawnParticle(org.bukkit.Particle.PORTAL, loc, 1, 0.3, 0.1, 0.3, 0.0);
                    }
                }
            }
        }, 0L, 5L);
    }

    public GameState getCurrentState() {
        return currentState;
    }

    public int getCastleHealth() {
        return Math.min(arenaHealth.getOrDefault("1", maxCastleHealth), arenaHealth.getOrDefault("2", maxCastleHealth));
    }

    public int getArenaHealth(String arena) {
        return arenaHealth.getOrDefault(arena, maxCastleHealth);
    }

    public BossBar getBossBar() {
        return castleBossBar;
    }

    public void setGameState(GameState newState) {
        if (this.currentState == newState) return;

        this.currentState = newState;
        plugin.getLogger().info("Game state changed to: " + newState.name());

        // Handle transitions
        switch (newState) {
            case LOBBY -> handleLobbySetup();
            case STARTING -> handleCountdown();
            case ACTIVE -> handleGameStart();
            case ENDED -> handleGameEnd();
        }
        updateTabNames();
    }

    private void handleLobbySetup() {
        cleanupBossBar();
        cleanupCastleHolograms();
        arenaHealth.put("1", maxCastleHealth);
        arenaHealth.put("2", maxCastleHealth);
    }

    private void handleCountdown() {
        cleanupBossBar();
        arenaHealth.put("1", maxCastleHealth);
        arenaHealth.put("2", maxCastleHealth);
        plugin.getMobManager().clearAllQueues();

        org.bukkit.World gameWorld = org.bukkit.Bukkit.getWorld("game_world");
        if (gameWorld != null) {
            gameWorld.setDifficulty(org.bukkit.Difficulty.EASY);
            gameWorld.setGameRule(org.bukkit.GameRule.DO_MOB_SPAWNING, false);
            gameWorld.setGameRule(org.bukkit.GameRule.KEEP_INVENTORY, true);
        }

        // FIX: First, teleport all queued players from lobby_world to game_world
        // This ensures they're in game_world before we try to assign them to arenas
        org.bukkit.Location gameWorldSpawn = gameWorld != null ? gameWorld.getSpawnLocation() : org.bukkit.Bukkit.getWorlds().get(0).getSpawnLocation();
        for (java.util.UUID uuid : matchQueue) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && !player.getWorld().getName().equals("game_world")) {
                player.teleport(gameWorldSpawn);
                player.sendMessage(ChatColor.GREEN + "Teleporting to the game world!");
            }
        }

        // Populate matchQueue from the players physically in game_world if not already queued
        // (This handles the legacy case where players manually enter game_world)
        if (matchQueue.size() < matchSize) {
            if (gameWorld != null) {
                for (Player p : gameWorld.getPlayers()) {
                    if (!matchQueue.contains(p.getUniqueId()) && matchQueue.size() < matchSize) {
                        matchQueue.add(p.getUniqueId());
                    }
                }
            }
        }

        // Teleport queued players to their arena starts, assign arenas, and initialize stats
        org.bukkit.Location spawnLoc = gameWorldSpawn;

        int count = 0;
        for (java.util.UUID uuid : matchQueue) {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null) continue;

            // Alternate team assignments to support up to 4v4: even -> Blue (Arena 1), odd -> Red (Arena 2)
            String assignedArena = (count % 2 == 0) ? "1" : "2";
            setPlayerArena(player.getUniqueId(), assignedArena);
            count++;

            resetPlayerForMatch(player, getPlayerArena(player.getUniqueId()));
            player.setGameMode(org.bukkit.GameMode.ADVENTURE);
            player.setAllowFlight(true);

            // Teleport to arena start waypoint, or fallback to spawnLoc if no waypoints
            java.util.List<Location> waypoints = plugin.getWaypointConfigManager().getWaypoints(assignedArena);
            if (waypoints != null && !waypoints.isEmpty()) {
                player.teleport(waypoints.get(0).clone().add(0, 1, 0));
            } else {
                player.teleport(spawnLoc);
            }
            player.sendMessage(ChatColor.GREEN + "Teleporting to the game world!");
        }

        Bukkit.broadcastMessage(ChatColor.GOLD + "[Tower Defense] Game starting in 10 seconds!");
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (currentState == GameState.STARTING) {
                setGameState(GameState.ACTIVE);
            }
        }, 200L); // 10 seconds (20 ticks = 1 sec)
    }

    private void handleGameStart() {
        matchStartTime = System.currentTimeMillis();
        arenaHealth.put("1", maxCastleHealth);
        arenaHealth.put("2", maxCastleHealth);
        showBossBar();
        updateCastleHologram("1");
        updateCastleHologram("2");
        Bukkit.broadcastMessage(ChatColor.GREEN + "[Tower Defense] The game has begun! Defend your castles!");
        for (java.util.UUID uuid : matchQueue) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                player.setGameMode(org.bukkit.GameMode.ADVENTURE);
                player.setAllowFlight(true);
                player.playSound(player.getLocation(), Sound.EVENT_RAID_HORN, 1.0f, 1.0f);
                teleportToArenaStart(player, getPlayerArena(uuid));
                // Strip the "Return to Lobby" compass entirely now the match is live.
                player.getInventory().remove(org.bukkit.Material.COMPASS);
            }
        }
    }

    private void handleGameEnd() {
        cleanupBossBar();
        cleanupCastleHolograms();
        plugin.getMobManager().cleanup();
        plugin.getTowerManager().cleanup();
        cleanupSpells();

        int hp1 = arenaHealth.getOrDefault("1", maxCastleHealth);
        int hp2 = arenaHealth.getOrDefault("2", maxCastleHealth);

        Player player1 = null;
        Player player2 = null;
        for (Player player : Bukkit.getOnlinePlayers()) {
            String arena = getPlayerArena(player.getUniqueId());
            if ("1".equals(arena)) {
                player1 = player;
            } else if ("2".equals(arena)) {
                player2 = player;
            }
        }

        String titleMsg;
        String subtitleMsg;

        if (hp1 <= 0 && hp2 > 0) {
            titleMsg = ChatColor.GREEN + "VICTORY";
            subtitleMsg = (player2 != null ? player2.getName() : "Red Team") + " wins the game!";
            Bukkit.broadcastMessage(ChatColor.GOLD + "[Tower Defense] " + (player2 != null ? player2.getName() : "Red Team") + " has won the duel!");
        } else if (hp2 <= 0 && hp1 > 0) {
            titleMsg = ChatColor.GREEN + "VICTORY";
            subtitleMsg = (player1 != null ? player1.getName() : "Blue Team") + " wins the game!";
            Bukkit.broadcastMessage(ChatColor.GOLD + "[Tower Defense] " + (player1 != null ? player1.getName() : "Blue Team") + " has won the duel!");
        } else if (hp1 <= 0 && hp2 <= 0) {
            titleMsg = ChatColor.RED + "DRAW";
            subtitleMsg = "Both castles were overrun!";
            Bukkit.broadcastMessage(ChatColor.GOLD + "[Tower Defense] The game ended in a draw!");
        } else {
            titleMsg = ChatColor.YELLOW + "GAME ENDED";
            subtitleMsg = "The game was stopped.";
            Bukkit.broadcastMessage(ChatColor.GOLD + "[Tower Defense] The game was stopped.");
        }

        // Clear all projectiles in the worlds of our waypoints
        java.util.Set<org.bukkit.World> worlds = new java.util.HashSet<>();
        for (Location wp : plugin.getWaypointConfigManager().getWaypoints("1")) {
            worlds.add(wp.getWorld());
        }
        for (Location wp : plugin.getWaypointConfigManager().getWaypoints("2")) {
            worlds.add(wp.getWorld());
        }
        for (org.bukkit.World world : worlds) {
            for (org.bukkit.entity.Entity entity : world.getEntities()) {
                if (entity instanceof org.bukkit.entity.Projectile) {
                    entity.remove();
                }
            }
        }

        // Reset individual player stats
        playerGold.clear();
        playerExp.clear();
        playerUnlockedMobs.clear();
        goldGenLevels.clear();
        swordLevels.clear();
        bowLevels.clear();
        playerArenas.clear();
        // Clear mob progression so unlocked tiers don't carry over into the next match;
        // everyone returns to Tier 1 (getMobTier defaults to 1, higher tiers default to locked).
        playerMobTiers.clear();
        playerUnlockedTiers.clear();
        pendingGold.clear();
        pendingExp.clear();
        lastSpellCast.clear();
        spellCostMultiplier.clear();
        lastAnySpellCast.clear();

        // Teleport back to spawn and reset queue
        org.bukkit.World lobbyWorld = org.bukkit.Bukkit.getWorld("lobby_world");
        org.bukkit.Location lobbySpawn = lobbyWorld != null ? lobbyWorld.getSpawnLocation() : org.bukkit.Bukkit.getWorlds().get(0).getSpawnLocation();

        for (Player player : Bukkit.getOnlinePlayers()) {
            player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
            player.sendTitle(titleMsg, subtitleMsg, 10, 70, 20);
            
            // Teleport back to lobby spawn
            player.teleport(lobbySpawn);
            // Clear items
            player.getInventory().clear();
            giveLobbyItems(player);
            
            // Reset gamemode and preserve flight properties
            player.setGameMode(org.bukkit.GameMode.ADVENTURE);
            player.setAllowFlight(true);
        }

        matchQueue.clear();

        // Trigger map reset (Step 2)
        resetGameWorld();
    }

    public void handlePlayerDisconnect(Player player) {
        // Capture match membership before removing — a disconnecting spectator must not trigger a forfeit.
        boolean wasInMatch = matchQueue.contains(player.getUniqueId());
        matchQueue.remove(player.getUniqueId());

        // Cancel lobby queue countdown if a queued player leaves
        if (lobbyQueueTask != null && matchQueue.size() < matchSize) {
            cancelLobbyQueueCountdown(player.getName() + " has disconnected.");
        }

        if (currentState != GameState.ACTIVE && currentState != GameState.STARTING) {
            return;
        }

        // Only an actual match participant disconnecting should forfeit the game.
        if (!wasInMatch) {
            return;
        }

        String quittingArena = getPlayerArena(player.getUniqueId());
        if (!"1".equals(quittingArena) && !"2".equals(quittingArena)) {
            return; // Not in the active game
        }

        // Determine the winning arena
        String winningArena = "1".equals(quittingArena) ? "2" : "1";

        Player winner = null;
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!p.getUniqueId().equals(player.getUniqueId())) {
                String arena = getPlayerArena(p.getUniqueId());
                if (winningArena.equals(arena)) {
                    winner = p;
                    break;
                }
            }
        }

        // Broadcast disconnection and victory
        Bukkit.broadcastMessage(ChatColor.RED + "[Tower Defense] " + player.getName() + " has disconnected! The match has ended.");
        if (winner != null) {
            Bukkit.broadcastMessage(ChatColor.GOLD + "[Tower Defense] " + winner.getName() + " wins by forfeit!");
        } else {
            Bukkit.broadcastMessage(ChatColor.GOLD + "[Tower Defense] The match ended because both players disconnected.");
        }

        // Force set the loser's health to 0 to trigger setGameState(GameState.ENDED) and display victory
        arenaHealth.put(quittingArena, 0);
        setGameState(GameState.ENDED);

        // Go back to lobby state after a short delay
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            setGameState(GameState.LOBBY);
        }, 100L); // 5 seconds delay to see the victory screen
    }

    /**
     * Forfeits the match for the given player by zeroing their arena's castle health, which ends the
     * game and awards the win to the opposing team. Returns the lobby shortly after.
     */
    public void forfeit(Player player) {
        // Per-match games (single player and the cloned-world duels) track membership via playerToMatch,
        // not the legacy matchQueue, so resolve the match that way and tear it down through endMatch.
        Match match = playerToMatch.get(player.getUniqueId());
        if (match == null) {
            player.sendMessage(ChatColor.RED + "You are not currently in an active game.");
            return;
        }

        String arena = match.getPlayerArenas().getOrDefault(player.getUniqueId(), "1");

        // Tell everyone in the match who forfeited.
        for (UUID id : new ArrayList<>(match.getPlayers())) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) {
                p.sendMessage(ChatColor.RED + "[Tower Defense] " + player.getName() + " has forfeited the match!");
            }
        }

        // In multiplayer, award the win to the opposing arena before the match closes.
        if (!match.getMapData().isSinglePlayer()) {
            String winningArena = "1".equals(arena) ? "2" : "1";
            for (UUID id : new ArrayList<>(match.getPlayers())) {
                if (winningArena.equals(match.getPlayerArenas().get(id))) {
                    Player w = Bukkit.getPlayer(id);
                    if (w != null) {
                        w.sendTitle(ChatColor.GREEN + "VICTORY", ChatColor.YELLOW + "Your opponent forfeited!", 10, 70, 20);
                        w.playSound(w.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
                    }
                }
            }
        }

        match.setCurrentState(GameState.ENDED);
        endMatch(match);
    }

    public java.util.List<java.util.UUID> getMatchQueue() {
        return matchQueue;
    }

    /** Number of players required to fill a match. Configurable via game.players-per-match (min 2). */
    public int getMatchSize() {
        return matchSize;
    }

    public void toggleQueue(Player player) {
        if (currentState == GameState.ACTIVE || currentState == GameState.STARTING) {
            player.sendMessage(ChatColor.RED + "Cannot join the queue while a game is in progress!");
            return;
        }

        java.util.UUID uuid = player.getUniqueId();
        if (matchQueue.contains(uuid)) {
            matchQueue.remove(uuid);
            player.sendMessage(ChatColor.YELLOW + "You have left the match queue (" + matchQueue.size() + "/" + matchSize + ").");
            giveLobbyItems(player);
            if (lobbyQueueTask != null) {
                cancelLobbyQueueCountdown("A player has left the queue.");
            }
        } else {
            if (matchQueue.size() >= matchSize) {
                player.sendMessage(ChatColor.RED + "The match queue is already full!");
                return;
            }
            matchQueue.add(uuid);
            player.sendMessage(ChatColor.GREEN + "You have joined the match queue (" + matchQueue.size() + "/" + matchSize + ")!");
            giveLobbyItems(player);
            // Start the lobby countdown once at least 2 players are queued; others may still join.
            if (matchQueue.size() >= 2 && lobbyQueueTask == null) {
                startLobbyQueueCountdown();
            }
        }
        updateTabNames();
    }

    public void giveLobbyItems(Player player) {
        player.getInventory().clear();
        player.setHealth(player.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue());
        player.setFoodLevel(20);
        player.setFireTicks(0);
        for (org.bukkit.potion.PotionEffect effect : player.getActivePotionEffects()) {
            player.removePotionEffect(effect.getType());
        }
        
        player.getInventory().setItem(4, createCompass(player)); // Put it in the center slot
    }

    /**
     * Builds the navigation compass appropriate for the player's current world: a "Return to Lobby"
     * compass in the game world, otherwise the lobby "Game Selector" compass.
     */
    public org.bukkit.inventory.ItemStack createCompass(Player player) {
        org.bukkit.inventory.ItemStack compass = new org.bukkit.inventory.ItemStack(org.bukkit.Material.COMPASS);
        org.bukkit.inventory.meta.ItemMeta meta = compass.getItemMeta();
        if (meta != null) {
            String worldName = player.getWorld().getName();
            if (worldName.equals("game_world")) {
                meta.setDisplayName(ChatColor.RED + "" + ChatColor.BOLD + "Return to Lobby");
                java.util.List<String> lore = new java.util.ArrayList<>();
                lore.add(ChatColor.GRAY + "Left-click to return to the lobby.");
                lore.add(ChatColor.GRAY + "Right-click to teleport forward.");
                meta.setLore(lore);
            } else {
                meta.setDisplayName(ChatColor.GOLD + "" + ChatColor.BOLD + "Game Selector");
                java.util.List<String> lore = new java.util.ArrayList<>();
                lore.add(ChatColor.GRAY + "Click to view open games.");
                meta.setLore(lore);
            }
            compass.setItemMeta(meta);
        }
        return compass;
    }

    /**
     * Guarantees the player is holding the navigation compass appropriate for their current world,
     * without disturbing the rest of their inventory. Used on join and on world change (even mid-match,
     * where {@link #giveLobbyItems} — which wipes the inventory — must not be called).
     *
     * Any compass that does not match the world-appropriate one is stripped. In particular this
     * removes the lobby "Game Selector" matchmaking compass once the player enters the game world /
     * a match has started, so it can't linger and re-open the matchmaking GUI during play.
     */
    public void ensureCompass(Player player) {
        org.bukkit.inventory.ItemStack desired = createCompass(player);
        String desiredName = (desired.getItemMeta() != null && desired.getItemMeta().hasDisplayName())
                ? desired.getItemMeta().getDisplayName() : null;

        org.bukkit.inventory.PlayerInventory inv = player.getInventory();
        boolean hasDesired = false;
        for (int i = 0; i < inv.getSize(); i++) {
            org.bukkit.inventory.ItemStack it = inv.getItem(i);
            if (it == null || it.getType() != org.bukkit.Material.COMPASS) continue;
            String name = (it.hasItemMeta() && it.getItemMeta().hasDisplayName())
                    ? it.getItemMeta().getDisplayName() : null;
            if (desiredName != null && desiredName.equals(name)) {
                hasDesired = true; // keep the world-appropriate compass
            } else {
                inv.setItem(i, null); // strip the wrong-world compass (e.g. the matchmaking compass)
            }
        }
        if (!hasDesired) {
            inv.setItem(4, desired);
        }
    }

    public void openGamesGUI(Player player) {
        org.bukkit.inventory.Inventory gui = Bukkit.createInventory(null, 27, ChatColor.DARK_BLUE + "Open Games");

        // Fill background with gray glass panes
        org.bukkit.inventory.ItemStack filler = new org.bukkit.inventory.ItemStack(org.bukkit.Material.GRAY_STAINED_GLASS_PANE);
        org.bukkit.inventory.meta.ItemMeta fillerMeta = filler.getItemMeta();
        if (fillerMeta != null) {
            fillerMeta.setDisplayName(" ");
            filler.setItemMeta(fillerMeta);
        }
        for (int i = 0; i < 27; i++) {
            gui.setItem(i, filler);
        }

        // Slot 11: Single Player Queue
        org.bukkit.inventory.ItemStack singlePlayerItem = new org.bukkit.inventory.ItemStack(org.bukkit.Material.PLAYER_HEAD);
        org.bukkit.inventory.meta.ItemMeta singlePlayerMeta = singlePlayerItem.getItemMeta();
        if (singlePlayerMeta != null) {
            singlePlayerMeta.setDisplayName(ChatColor.AQUA + "" + ChatColor.BOLD + "Single Player");
            java.util.List<String> lore = new java.util.ArrayList<>();
            lore.add(ChatColor.GRAY + "Fight waves of mobs alone!");
            lore.add("");
            lore.add(ChatColor.YELLOW + "• 100 pre-designed waves");
            lore.add(ChatColor.YELLOW + "• Endless mode after wave 100");
            lore.add(ChatColor.YELLOW + "• Starts immediately");
            lore.add("");
            lore.add(ChatColor.GREEN + "Click to join Single Player queue!");
            singlePlayerMeta.setLore(lore);
            singlePlayerItem.setItemMeta(singlePlayerMeta);
        }
        gui.setItem(11, singlePlayerItem);

        // Slot 15: Multiplayer Queue
        org.bukkit.inventory.ItemStack multiPlayerItem = new org.bukkit.inventory.ItemStack(org.bukkit.Material.DIAMOND_SWORD);
        org.bukkit.inventory.meta.ItemMeta multiPlayerMeta = multiPlayerItem.getItemMeta();
        if (multiPlayerMeta != null) {
            multiPlayerMeta.setDisplayName(ChatColor.RED + "" + ChatColor.BOLD + "Multiplayer");
            java.util.List<String> lore = new java.util.ArrayList<>();
            lore.add(ChatColor.GRAY + "Play against other players!");
            lore.add("");
            lore.add(ChatColor.YELLOW + "• Send mobs to opponents");
            lore.add(ChatColor.YELLOW + "• Vote for your map");
            lore.add(ChatColor.YELLOW + "• Requires at least " + plugin.getConfig().getInt("game.players-per-match", 2) + " players");
            lore.add("");
            lore.add(ChatColor.GREEN + "Click to join multilayer queue!");
            multiPlayerMeta.setLore(lore);
            multiPlayerItem.setItemMeta(multiPlayerMeta);
        }
        gui.setItem(15, multiPlayerItem);

        player.openInventory(gui);
    }

    public void checkGameStartConditions() {
        if (currentState != GameState.LOBBY) return;
        org.bukkit.World gameWorld = org.bukkit.Bukkit.getWorld("game_world");
        if (gameWorld != null && gameWorld.getPlayers().size() >= matchSize) {
            setGameState(GameState.STARTING);
        }
    }

    public void checkGameStopConditions() {
        if (currentState == GameState.STARTING) {
            org.bukkit.World gameWorld = org.bukkit.Bukkit.getWorld("game_world");
            if (gameWorld == null || gameWorld.getPlayers().size() < matchSize) {
                setGameState(GameState.LOBBY);
                Bukkit.broadcastMessage(ChatColor.RED + "[Tower Defense] Start countdown cancelled. Waiting for players...");
            }
        }
    }

    public void setupWorlds() {
        // Load lobby world
        org.bukkit.WorldCreator lobbyCreator = new org.bukkit.WorldCreator("lobby_world");
        org.bukkit.World lobbyWorld = org.bukkit.Bukkit.createWorld(lobbyCreator);
        if (lobbyWorld != null) {
            lobbyWorld.setGameRule(org.bukkit.GameRule.DO_MOB_SPAWNING, false);
        }

        // Sweep any leftover match_xxxx worlds from a previous run (crash, or a delete that was still
        // file-locked when the server shut down) so they don't accumulate on disk.
        cleanupStaleMatchWorlds();

        // Load or reset game world
        resetGameWorld();
    }

    /**
     * Deletes leftover cloned match worlds (folders named "match_...") both at the world-container root
     * and under each loaded world's dimensions/minecraft/ folder where Paper migrates them. Runs at
     * startup, when no match is active, so nothing in use is touched.
     */
    private void cleanupStaleMatchWorlds() {
        File container = Bukkit.getWorldContainer();
        deleteMatchFoldersIn(container);
        for (World w : Bukkit.getWorlds()) {
            deleteMatchFoldersIn(new File(container, w.getName() + "/dimensions/minecraft"));
        }
    }

    private void deleteMatchFoldersIn(File dir) {
        File[] matchDirs = dir.listFiles((d, name) -> name.startsWith("match_"));
        if (matchDirs == null) return;
        for (File matchDir : matchDirs) {
            if (matchDir.isDirectory()) {
                deleteDirectory(matchDir);
                plugin.getLogger().info("Removed stale match world folder: " + matchDir.getAbsolutePath());
            }
        }
    }

    public void resetGameWorld() {
        plugin.getLogger().info("Starting game_world reset process...");
        
        // 1. Unload game_world without saving
        org.bukkit.World gameWorld = org.bukkit.Bukkit.getWorld("game_world");
        if (gameWorld != null) {
            // Teleport any players out first
            org.bukkit.World lobbyWorld = org.bukkit.Bukkit.getWorld("lobby_world");
            org.bukkit.Location fallbackLoc = lobbyWorld != null ? lobbyWorld.getSpawnLocation() : org.bukkit.Bukkit.getWorlds().get(0).getSpawnLocation();
            for (Player player : gameWorld.getPlayers()) {
                player.teleport(fallbackLoc);
                player.sendMessage(ChatColor.YELLOW + "You have been teleported to the lobby because the game world is resetting.");
            }
            
            boolean unloaded = org.bukkit.Bukkit.unloadWorld(gameWorld, false);
            plugin.getLogger().info("game_world unload success: " + unloaded);
        }

        // Force JVM GC to release files lock if any (Windows workaround)
        System.gc();

        // 2. Delete the active game_world directory
        java.io.File gameWorldFolder = new java.io.File(org.bukkit.Bukkit.getWorldContainer(), "game_world");
        if (gameWorldFolder.exists()) {
            deleteDirectory(gameWorldFolder);
            plugin.getLogger().info("Deleted active game_world folder.");
        }

        // 2b. Delete the migrated game_world folder inside the primary world (Paper 1.21+ structure)
        String primaryWorldName = org.bukkit.Bukkit.getWorlds().isEmpty() ? "lobby_world" : org.bukkit.Bukkit.getWorlds().get(0).getName();
        java.io.File migratedFolder = new java.io.File(org.bukkit.Bukkit.getWorldContainer(), primaryWorldName + "/dimensions/minecraft/game_world");
        if (migratedFolder.exists()) {
            deleteDirectory(migratedFolder);
            plugin.getLogger().info("Deleted migrated game_world folder in " + primaryWorldName + " directory.");
        }

        // The legacy single "game_world" is no longer used: the multi-map architecture clones a fresh
        // per-match world (match_xxxx) for every game from GAME_WORLD_TEMPLATES. We deliberately do NOT
        // recreate game_world here anymore — recreating it caused Paper to migrate a stray world into
        // lobby_world/dimensions/minecraft/game_world on every startup. This method now only cleans up
        // any leftover game_world folders from older versions.
        plugin.getLogger().info("game_world reset complete (legacy world intentionally not recreated).");
    }

    private void deleteDirectory(java.io.File file) {
        java.io.File[] contents = file.listFiles();
        if (contents != null) {
            for (java.io.File f : contents) {
                deleteDirectory(f);
            }
        }
        file.delete();
    }

    /**
     * Deletes the given match-world folders off the main thread, retrying for a while. After a world is
     * unloaded the OS (especially Windows) can keep region-file handles open for a moment, so the first
     * delete attempt often fails; retrying on an async thread (where sleeping is safe) lets the handles
     * release without freezing the server. Pure filesystem work — no Bukkit API is touched here.
     */
    private void deleteWorldFoldersAsync(String worldName, java.io.File... folders) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            System.gc(); // hint the JVM to release memory-mapped region files
            boolean allGone = false;
            for (int attempt = 1; attempt <= 12 && !allGone; attempt++) {
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                allGone = true;
                for (java.io.File folder : folders) {
                    if (folder.exists()) {
                        deleteDirectory(folder);
                        if (folder.exists()) allGone = false;
                    }
                }
            }
            if (allGone) {
                plugin.getLogger().info("Cleaned up match world files for: " + worldName);
            } else {
                plugin.getLogger().warning("Could not fully delete match world files for " + worldName
                        + " (files may still be locked); they will be swept on the next server start.");
            }
        });
    }

    public void damageCastle(int amount) {
        damageCastle("1", amount);
    }

    public void damageCastle(String arena, int amount) {
        if (currentState != GameState.ACTIVE) return;

        int current = arenaHealth.getOrDefault(arena, maxCastleHealth);
        int updated = Math.max(0, current - amount);
        arenaHealth.put(arena, updated);
        updateBossBar();
        updateCastleHologram(arena);

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (arena.equals(getPlayerArena(player.getUniqueId()))) {
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.5f);
            }
            String teamName = "1".equals(arena) ? "Blue Team" : "Red Team";
            player.sendActionBar(ChatColor.RED + "⚠ " + teamName + " Damaged! Health: " + updated + "/" + maxCastleHealth + " ⚠");
        }

        if (updated <= 0) {
            // Defer the game-end transition by a tick. damageCastle() runs from inside the mob
            // movement ticker while it iterates activeMobs; ending immediately would run
            // handleGameEnd() → cleanup() → activeMobs.clear() mid-iteration, throwing a
            // ConcurrentModificationException. The guard avoids re-ending if already transitioned.
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (currentState == GameState.ACTIVE) {
                    setGameState(GameState.ENDED);
                }
            });
        }
    }

    public void showBossBar() {
        if (castleBossBar == null) {
            castleBossBar = Bukkit.createBossBar(
                ChatColor.BLUE + "Blue Team: " + ChatColor.WHITE + arenaHealth.getOrDefault("1", maxCastleHealth) + " HP " +
                ChatColor.GRAY + "| " +
                ChatColor.RED + "Red Team: " + ChatColor.WHITE + arenaHealth.getOrDefault("2", maxCastleHealth) + " HP",
                BarColor.RED,
                BarStyle.SOLID
            );
            castleBossBar.setVisible(true);
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            castleBossBar.addPlayer(player);
        }
    }

    public void updateBossBar() {
        if (castleBossBar == null) return;
        int hp1 = arenaHealth.getOrDefault("1", maxCastleHealth);
        int hp2 = arenaHealth.getOrDefault("2", maxCastleHealth);
        castleBossBar.setTitle(
            ChatColor.BLUE + "Blue Team: " + ChatColor.WHITE + hp1 + " HP " +
            ChatColor.GRAY + "| " +
            ChatColor.RED + "Red Team: " + ChatColor.WHITE + hp2 + " HP"
        );
        double progress1 = (double) hp1 / maxCastleHealth;
        double progress2 = (double) hp2 / maxCastleHealth;
        double progress = (progress1 + progress2) / 2.0;
        castleBossBar.setProgress(Math.max(0.0, Math.min(1.0, progress)));
    }

    public void cleanupBossBar() {
        if (castleBossBar != null) {
            castleBossBar.removeAll();
            castleBossBar = null;
        }
    }

    public void updateCastleHologram(String arena) {
        updateCastleHologram(null, arena);
    }

    public void updateCastleHologram(Match match, String arena) {
        if (currentState != GameState.ACTIVE && currentState != GameState.STARTING) return;

        // The castle sits at the highest-numbered waypoint. The track has Y-splits, so the
        // next-pointer chain can dead-end on a short branch before reaching the castle — the
        // authoring convention is that the max waypoint ID is the end. Pull it from the per-match
        // graph so the Location carries the live match world; fall back to the legacy numeric-sorted
        // list (last element = highest ID) when there's no match (e.g. the periodic HUD ticker).
        Location lastWp;
        if (match != null) {
            lastWp = plugin.getWaypointConfigManager().getLastWaypoint(match, arena);
        } else {
            java.util.List<Location> wps = plugin.getWaypointConfigManager().getWaypoints(arena);
            lastWp = wps.isEmpty() ? null : wps.get(wps.size() - 1);
        }
        if (lastWp == null || lastWp.getWorld() == null) return;

        int health = arenaHealth.getOrDefault(arena, maxCastleHealth);
        double ratio = Math.max(0.0, Math.min(1.0, (double) health / maxCastleHealth));
        int totalBars = 20;
        int filledBars = (int) Math.round(ratio * totalBars);

        ChatColor barColor = ratio >= 0.6 ? ChatColor.GREEN : (ratio >= 0.25 ? ChatColor.YELLOW : ChatColor.RED);
        StringBuilder bar = new StringBuilder();
        bar.append(barColor);
        for (int i = 0; i < filledBars; i++) bar.append("■");
        bar.append(ChatColor.GRAY);
        for (int i = filledBars; i < totalBars; i++) bar.append("■");

        boolean blue = "1".equals(arena);
        ChatColor teamColor = blue ? ChatColor.BLUE : ChatColor.RED;
        String teamName = blue ? "BLUE" : "RED";

        // Two-line, tower-style floating display: a coloured title and a health bar with the count.
        java.util.List<String> lines = new java.util.ArrayList<>();
        lines.add(teamColor.toString() + ChatColor.BOLD + "⚔ " + teamName + " CASTLE");
        lines.add(ChatColor.RED + "❤ " + bar + ChatColor.GRAY + " ("
                + ChatColor.WHITE + health + ChatColor.GRAY + "/" + maxCastleHealth + ")");

        // Render the lines as a stack of floating ArmorStands, mirroring TowerManager.updateHologram so
        // the display survives chunk reloads: reuse/teleport existing stands, spawn missing ones, trim
        // extras. The periodic game-loop ticker calls this every second, so a stand that unloaded while
        // no player was near the castle is recreated as soon as a player returns and the chunk loads.
        Location base = lastWp.clone().add(0, 3.0, 0);
        java.util.List<ArmorStand> stands = castleHolograms.computeIfAbsent(arena, k -> new java.util.ArrayList<>());

        // Remove any stray castle-hologram stands at this spot that we are NOT tracking — ghosts left
        // by a chunk reload or an earlier render. Without this they stack and the text becomes an
        // unreadable blur. Restricted to invisible marker stands with a custom name so it never touches
        // tower holograms or other entities; the tracked stands below are reused, not removed.
        for (org.bukkit.entity.Entity e : base.getWorld().getNearbyEntities(base, 1.5, 3.0, 1.5)) {
            if (e instanceof ArmorStand as && as.isMarker() && !as.isVisible()
                    && as.getCustomName() != null && !stands.contains(as)) {
                as.remove();
            }
        }

        double spacing = 0.28;
        for (int i = 0; i < lines.size(); i++) {
            String text = lines.get(i);
            Location lineLoc = base.clone().add(0, -i * spacing, 0);
            if (i < stands.size() && stands.get(i) != null && stands.get(i).isValid()) {
                ArmorStand as = stands.get(i);
                as.teleport(lineLoc);
                as.setCustomName(text);
                as.setCustomNameVisible(true);
            } else {
                final String nameText = text;
                ArmorStand newAs = lineLoc.getWorld().spawn(lineLoc, ArmorStand.class, as -> {
                    as.setVisible(false);
                    as.setGravity(false);
                    as.setMarker(true);
                    as.setInvulnerable(true);
                    as.setPersistent(false);
                    as.setCustomName(nameText);
                    as.setCustomNameVisible(true);
                });
                if (i < stands.size()) stands.set(i, newAs);
                else stands.add(newAs);
            }
        }
        while (stands.size() > lines.size()) {
            ArmorStand extra = stands.remove(stands.size() - 1);
            if (extra != null && extra.isValid()) extra.remove();
        }
    }

    public void cleanupCastleHolograms() {
        for (java.util.List<ArmorStand> stands : castleHolograms.values()) {
            for (ArmorStand stand : stands) {
                if (stand != null && stand.isValid()) {
                    stand.remove();
                }
            }
        }
        castleHolograms.clear();
    }

    // --- Active Spells and Matchmaking Systems ---

    public boolean isSpellActive(Match match, String arena, String spellType) {
        java.util.Map<String, Long> spells = match.getActiveSpells().get(arena);
        if (spells == null) return false;
        Long end = spells.get(spellType.toUpperCase());
        return end != null && System.currentTimeMillis() < end;
    }

    public boolean isSpellActive(String arena, String spellType) {
        java.util.Map<String, Long> spells = activeSpells.get(arena);
        if (spells == null) return false;
        Long end = spells.get(spellType.toUpperCase());
        return end != null && System.currentTimeMillis() < end;
    }

    /** Returns the epoch-millis end time of an active spell in the arena, or 0 if not active. */
    public long getSpellEndTime(String arena, String spellType) {
        java.util.Map<String, Long> spells = activeSpells.get(arena);
        if (spells == null) return 0L;
        Long end = spells.get(spellType.toUpperCase());
        if (end == null || System.currentTimeMillis() >= end) return 0L;
        return end;
    }

    public void damageCastle(Match match, String arena, int amount) {
        int current = match.getArenaHealth().getOrDefault(arena, maxCastleHealth);
        int updated = Math.max(0, current - amount);
        match.getArenaHealth().put(arena, updated);

        // Mirror to the global castle health so the singleton boss bar / holograms reflect the damage.
        // (Only one match is live at a time for these HUD elements.)
        arenaHealth.put(arena, updated);
        updateBossBar();
        updateCastleHologram(match, arena);

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (arena.equals(getPlayerArena(player.getUniqueId()))) {
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.5f);
            }
            String teamName = "1".equals(arena) ? "Blue Team" : "Red Team";
            player.sendActionBar(ChatColor.RED + "⚠ " + teamName + " Damaged! Health: " + updated + "/" + maxCastleHealth + " ⚠");
        }

        if (updated <= 0) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (match.getCurrentState() == GameState.ACTIVE) {
                    match.setCurrentState(GameState.ENDED);
                    handleMatchEnd(match);
                }
            });
        }
    }

    private void handleMatchEnd(Match match) {
        endMatch(match);
    }

    /**
     * Cleanly tears down a match: stops its wave session, returns its players to the lobby, releases its
     * per-match plot/waypoint config, and unloads + deletes its cloned world (both the server-root copy
     * and Paper's migrated copy under the primary world's dimensions/ folder). When the last match ends
     * the global state drops back to LOBBY so the singleton tower/mob/gold systems go dormant again.
     */
    public void endMatch(Match match) {
        if (match == null) return;

        activeMatches.remove(match.getMatchId());
        plugin.getWaveManager().stopWaves(match);

        org.bukkit.World lobby = Bukkit.getWorld("lobby_world");
        Location lobbySpawn = lobby != null ? lobby.getSpawnLocation() : Bukkit.getWorlds().get(0).getSpawnLocation();
        for (UUID id : new ArrayList<>(match.getPlayers())) {
            playerToMatch.remove(id);
            playerScoreboards.remove(id);
            Player p = Bukkit.getPlayer(id);
            if (p != null) {
                p.getInventory().clear();
                p.teleport(lobbySpawn);
                p.setGameMode(org.bukkit.GameMode.ADVENTURE);
                p.setAllowFlight(true);
                p.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
                giveLobbyItems(p);
                p.sendMessage(ChatColor.YELLOW + "The match has ended. You have been returned to the lobby.");
            }
        }

        // Release per-match config so a future match on the same template loads fresh.
        plugin.getWaypointConfigManager().unloadMatch(match);
        plugin.getPlotConfigManager().unloadMatch(match);

        // Drop the global mirror of this match's plots/waypoints and tear down the shared HUD so neither
        // lingers into the lobby or the next match.
        plugin.getPlotConfigManager().clearGlobal();
        plugin.getWaypointConfigManager().clearGlobal();
        cleanupBossBar();
        cleanupCastleHolograms();

        // Unload and delete the cloned match world.
        org.bukkit.World world = match.getWorld();
        if (world != null && !world.getPlayers().isEmpty()) {
            for (Player p : world.getPlayers()) p.teleport(lobbySpawn);
        }
        if (world != null) {
            final String worldName = world.getName();

            // Remove all entities first so chunks aren't held by entity references when we delete the files.
            world.getEntities().forEach(org.bukkit.entity.Entity::remove);

            boolean unloaded = Bukkit.unloadWorld(world, false); // false = do not save the throwaway match world
            plugin.getLogger().info("Unloaded match world " + worldName + ": " + unloaded);

            File container = Bukkit.getWorldContainer();
            File worldFolder = new File(container, worldName);
            // Paper migrates loaded worlds into <primary>/dimensions/minecraft/<name>, so delete that copy too.
            String primaryWorldName = Bukkit.getWorlds().isEmpty() ? "lobby_world" : Bukkit.getWorlds().get(0).getName();
            File migratedFolder = new File(container, primaryWorldName + "/dimensions/minecraft/" + worldName);

            deleteWorldFoldersAsync(worldName, worldFolder, migratedFolder);
        }

        // Drop the global state once no matches remain so towers/mobs/gold stop ticking in the lobby.
        if (activeMatches.isEmpty()) {
            currentState = GameState.LOBBY;
        }
        plugin.getLogger().info("Match " + match.getMatchId() + " ended and cleaned up.");
    }

    /**
     * Computes the cost multiplier that would apply to the player's next cast of this spell, without
     * mutating any state. Each successive cast doubles the multiplier; 10 seconds of inactivity on that
     * specific spell resets it back to 1. Used both for display (openUpgradesGUI) and to price a cast.
     */
    public int getNextSpellMultiplier(UUID uuid, String spellType) {
        String spell = spellType.toUpperCase();
        long now = System.currentTimeMillis();
        Long last = lastSpellCast.getOrDefault(uuid, Collections.emptyMap()).get(spell);
        int storedMult = spellCostMultiplier.getOrDefault(uuid, Collections.emptyMap()).getOrDefault(spell, 1);
        if (last == null || now - last > SPELL_PRICE_RESET_MS) {
            return 1;
        }
        // Within the reset window, every additional cast doubles the cost.
        return storedMult * 2;
    }

    /** Records that the player just cast this spell at the given multiplier, for dynamic pricing. */
    public void recordSpellCast(UUID uuid, String spellType, int multiplier) {
        String spell = spellType.toUpperCase();
        long now = System.currentTimeMillis();
        lastSpellCast.computeIfAbsent(uuid, k -> new HashMap<>()).put(spell, now);
        spellCostMultiplier.computeIfAbsent(uuid, k -> new HashMap<>()).put(spell, multiplier);
        lastAnySpellCast.put(uuid, now);
    }

    /** True if the player is still within the global anti-spam cooldown after any spell cast. */
    public boolean isGlobalSpellOnCooldown(UUID uuid) {
        Long last = lastAnySpellCast.get(uuid);
        return last != null && (System.currentTimeMillis() - last) < GLOBAL_SPELL_COOLDOWN_MS;
    }

    /** Remaining Freeze-specific cooldown in milliseconds (0 if ready). */
    public long getFreezeCooldownRemaining(UUID uuid) {
        Long last = lastSpellCast.getOrDefault(uuid, Collections.emptyMap()).get("FREEZE");
        if (last == null) return 0L;
        long remaining = FREEZE_COOLDOWN_MS - (System.currentTimeMillis() - last);
        return Math.max(0L, remaining);
    }

    public void castSpell(String arena, String spellType, int durationSeconds) {
        String spell = spellType.toUpperCase();
        long endTime = System.currentTimeMillis() + (durationSeconds * 1000L);
        activeSpells.computeIfAbsent(arena, k -> new java.util.HashMap<>()).put(spell, endTime);

        // Play cast sound at the middle of the waypoints (3D sound)
        java.util.List<Location> waypoints = plugin.getWaypointConfigManager().getWaypoints(arena);
        if (!waypoints.isEmpty()) {
            Location soundLoc = waypoints.get(waypoints.size() / 2);
            Sound castSound = switch (spell) {
                case "OVERCHARGE" -> Sound.BLOCK_ANVIL_PLACE;
                case "FREEZE" -> Sound.BLOCK_GLASS_BREAK;
                case "DAMAGE_STORM" -> Sound.ENTITY_BLAZE_SHOOT;
                case "HASTE_RUSH" -> Sound.ENTITY_BAT_TAKEOFF;
                case "TOWER_EMP" -> Sound.ENTITY_LIGHTNING_BOLT_THUNDER;
                case "SLOW_SHIELD" -> Sound.ITEM_ARMOR_EQUIP_NETHERITE;
                default -> null;
            };
            if (castSound != null) {
                soundLoc.getWorld().playSound(soundLoc, castSound, 2.0f, 1.0f);
            }
        }

        Material blockMat = switch (spell) {
            case "OVERCHARGE" -> Material.EMERALD_BLOCK;
            case "FREEZE" -> Material.SNOW_BLOCK;
            case "DAMAGE_STORM" -> Material.MAGMA_BLOCK;
            case "HASTE_RUSH" -> Material.GOLD_BLOCK;
            case "TOWER_EMP" -> Material.REDSTONE_BLOCK;
            case "SLOW_SHIELD" -> Material.LAPIS_BLOCK;
            default -> null;
        };

        if (blockMat != null) {
            for (Location wp : waypoints) {
                Location floorLoc = wp.clone().subtract(0, 1, 0);
                if (!originalFloorBlocks.containsKey(floorLoc)) {
                    originalFloorBlocks.put(floorLoc, floorLoc.getBlock().getType());
                }
                floorLoc.getBlock().setType(blockMat);
            }

            final long thisRunEndTime = endTime;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (activeSpells.containsKey(arena) && activeSpells.get(arena).getOrDefault(spell, 0L).equals(thisRunEndTime)) {
                    activeSpells.get(arena).remove(spell);
                    restoreFloorBlocks(arena);
                }
            }, durationSeconds * 20L);
        }
    }

    public void restoreFloorBlocks(String arena) {
        String remainingSpell = null;
        if (activeSpells.containsKey(arena)) {
            for (java.util.Map.Entry<String, Long> entry : activeSpells.get(arena).entrySet()) {
                if (System.currentTimeMillis() + 50L < entry.getValue()) {
                    remainingSpell = entry.getKey();
                    break;
                }
            }
        }

        java.util.List<Location> waypoints = plugin.getWaypointConfigManager().getWaypoints(arena);
        if (remainingSpell != null) {
            Material remainingMat = switch (remainingSpell) {
                case "OVERCHARGE" -> Material.EMERALD_BLOCK;
                case "FREEZE" -> Material.SNOW_BLOCK;
                case "DAMAGE_STORM" -> Material.MAGMA_BLOCK;
                case "HASTE_RUSH" -> Material.GOLD_BLOCK;
                case "TOWER_EMP" -> Material.REDSTONE_BLOCK;
                case "SLOW_SHIELD" -> Material.LAPIS_BLOCK;
                default -> null;
            };
            if (remainingMat != null) {
                for (Location wp : waypoints) {
                    Location floorLoc = wp.clone().subtract(0, 1, 0);
                    floorLoc.getBlock().setType(remainingMat);
                }
                return;
            }
        }

        for (Location wp : waypoints) {
            Location floorLoc = wp.clone().subtract(0, 1, 0);
            Material original = originalFloorBlocks.remove(floorLoc);
            if (original != null) {
                floorLoc.getBlock().setType(original);
            }
        }
    }

    public void cleanupSpells() {
        activeSpells.clear();
        for (java.util.Map.Entry<Location, Material> entry : originalFloorBlocks.entrySet()) {
            entry.getKey().getBlock().setType(entry.getValue());
        }
        originalFloorBlocks.clear();
    }

    public void disableRandomTower(String arena, int durationSeconds) {
        java.util.List<com.pauljang.towerDefense.towers.Tower> arenaTowers = new java.util.ArrayList<>();
        for (com.pauljang.towerDefense.towers.Tower tower : plugin.getTowerManager().getPlacedTowers().values()) {
            if (tower.isDisabled()) continue; // Don't re-target an already-disabled tower
            String towerArena = plugin.getPlotConfigManager().getPlotArena(tower.getPlotId());
            if (arena.equals(towerArena)) {
                arenaTowers.add(tower);
            }
        }
        if (arenaTowers.isEmpty()) {
            return; // No towers to disable
        }
        // Pick a random tower
        java.util.Collections.shuffle(arenaTowers);
        com.pauljang.towerDefense.towers.Tower targetTower = arenaTowers.get(0);
        
        // Set disabled until
        long durationMs = durationSeconds * 1000L;
        targetTower.setDisabledUntil(System.currentTimeMillis() + durationMs);
        
        // Play sound and visual effect at the tower
        Location center = targetTower.getCenterLocation();
        center.getWorld().playSound(center, Sound.ENTITY_ITEM_BREAK, 1.0f, 0.5f);
        center.getWorld().playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 0.8f, 0.8f);
        center.getWorld().spawnParticle(org.bukkit.Particle.EXPLOSION, center.clone().add(0, 2, 0), 1);
        
        // Alert players on that track
        for (Player player : Bukkit.getOnlinePlayers()) {
            String pArena = getPlayerArena(player.getUniqueId());
            if (arena.equals(pArena)) {
                player.sendMessage(ChatColor.RED + "⚡ Your " + targetTower.getType().getDisplayName() + " at " + targetTower.getPlotId() + " has been disabled by an EMP for " + durationSeconds + " seconds! ⚡");
                player.playSound(player.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.8f, 0.8f);
            }
        }
    }

    public void challengePlayer(Player challenger, Player target) {
        if (currentState == GameState.ACTIVE || currentState == GameState.STARTING) {
            challenger.sendMessage(ChatColor.RED + "A match is already in progress!");
            return;
        }
        if (challenger.getUniqueId().equals(target.getUniqueId())) {
            challenger.sendMessage(ChatColor.RED + "You cannot challenge yourself!");
            return;
        }
        
        pendingChallenger = challenger.getUniqueId();
        pendingChallengeTarget = target.getUniqueId();
        challengeTimestamp = System.currentTimeMillis();

        challenger.sendMessage(ChatColor.GOLD + "[Tower Defense] You challenged " + target.getName() + " to a duel! They have 30 seconds to accept.");
        challenger.playSound(challenger.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.2f);
        
        target.sendMessage(ChatColor.GOLD + "[Tower Defense] " + challenger.getName() + " has challenged you to a Tower Defense duel!");
        target.sendMessage(ChatColor.YELLOW + "Type " + ChatColor.GREEN + "/td accept" + ChatColor.YELLOW + " to accept the challenge!");
        target.playSound(target.getLocation(), Sound.EVENT_RAID_HORN, 0.8f, 1.0f);
    }

    public void acceptChallenge(Player target) {
        if (pendingChallengeTarget == null || !pendingChallengeTarget.equals(target.getUniqueId())) {
            target.sendMessage(ChatColor.RED + "You do not have any pending challenges!");
            return;
        }
        if (System.currentTimeMillis() - challengeTimestamp > 30000L) {
            target.sendMessage(ChatColor.RED + "The challenge has expired!");
            pendingChallenger = null;
            pendingChallengeTarget = null;
            return;
        }
        if (currentState == GameState.ACTIVE || currentState == GameState.STARTING) {
            target.sendMessage(ChatColor.RED + "A match is already in progress!");
            return;
        }

        Player challenger = Bukkit.getPlayer(pendingChallenger);
        if (challenger == null || !challenger.isOnline()) {
            target.sendMessage(ChatColor.RED + "The challenger is no longer online!");
            pendingChallenger = null;
            pendingChallengeTarget = null;
            return;
        }

        pendingChallenger = null;
        pendingChallengeTarget = null;

        Bukkit.broadcastMessage(ChatColor.GREEN + "" + ChatColor.BOLD + "========================================");
        Bukkit.broadcastMessage(ChatColor.YELLOW + challenger.getName() + " vs " + target.getName() + " duel has started!");
        Bukkit.broadcastMessage(ChatColor.GREEN + "" + ChatColor.BOLD + "========================================");

        startPvPMatch(challenger, target);
    }

    public void startPvPMatch(Player p1, Player p2) {
        // Silent cleanup instead of triggering the ENDED state, which would flash the victory screen.
        // (The actual cleanup calls live in the "Wipe active objects" block below.)

        // Reset players
        resetPlayerForMatch(p1, "1");
        resetPlayerForMatch(p2, "2");

        // Set health
        arenaHealth.put("1", maxCastleHealth);
        arenaHealth.put("2", maxCastleHealth);

        // Wipe active objects
        plugin.getMobManager().cleanup();
        plugin.getTowerManager().cleanup();
        cleanupSpells();
        cleanupCastleHolograms();

        // Teleport
        teleportToArenaStart(p1, "1");
        teleportToArenaStart(p2, "2");

        // Start match state
        setGameState(GameState.STARTING);
    }

    private void resetPlayerForMatch(Player player, String arena) {
        java.util.UUID uuid = player.getUniqueId();
        playerGold.put(uuid, plugin.getConfig().getInt("game.starting-gold", 150));
        playerExp.put(uuid, 0);
        goldGenLevels.put(uuid, 1);
        swordLevels.put(uuid, 1);
        bowLevels.put(uuid, 1);
        setPlayerArena(uuid, arena);
        player.getInventory().clear();
        // Strip the lobby matchmaking compass so it can't linger into the match.
        player.getInventory().remove(org.bukkit.Material.COMPASS);
        player.setHealth(player.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue());
        player.setFoodLevel(20);
        player.setFireTicks(0);
        for (org.bukkit.potion.PotionEffect effect : player.getActivePotionEffects()) {
            player.removePotionEffect(effect.getType());
        }
        giveStarterWeapons(player);
    }

    private void teleportToArenaStart(Player player, String arena) {
        java.util.List<Location> waypoints = plugin.getWaypointConfigManager().getWaypoints(arena);
        if (!waypoints.isEmpty()) {
            player.teleport(waypoints.get(0).clone().add(0, 1, 0));
        } else {
            player.sendMessage(ChatColor.RED + "Warning: No waypoints configured for Arena " + arena + ". Could not teleport!");
        }
    }

    // --- Economy / Gold System ---

    public int getGold(java.util.UUID uuid) {
        Match match = playerToMatch.get(uuid);
        if (match != null) {
            return match.getPlayerGold().getOrDefault(uuid, plugin.getConfig().getInt("game.starting-gold", 150));
        }
        return 0;
    }

    public boolean hasGold(java.util.UUID uuid, int amount) {
        return getGold(uuid) >= amount;
    }

    public void addGold(java.util.UUID uuid, int amount, boolean silent) {
        Match match = playerToMatch.get(uuid);
        if (match != null) {
            int current = match.getPlayerGold().getOrDefault(uuid, 0);
            match.getPlayerGold().put(uuid, current + amount);
            
            if (!silent) {
                pendingGold.merge(uuid, amount, Integer::sum);
                Player player = Bukkit.getPlayer(uuid);
                if (player != null && player.isOnline()) {
                    player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.5f, 1.2f);
                }
            }
        }
    }

    public boolean removeGold(java.util.UUID uuid, int amount) {
        Match match = playerToMatch.get(uuid);
        if (match != null) {
            int current = match.getPlayerGold().getOrDefault(uuid, 0);
            if (current >= amount) {
                match.getPlayerGold().put(uuid, current - amount);
                return true;
            }
        }
        return false;
    }

    public int getExp(java.util.UUID uuid) {
        Match match = playerToMatch.get(uuid);
        if (match != null) {
            return match.getPlayerExp().getOrDefault(uuid, 0);
        }
        return 0;
    }

    public void addExp(java.util.UUID uuid, int amount) {
        Match match = playerToMatch.get(uuid);
        if (match != null) {
            int current = match.getPlayerExp().getOrDefault(uuid, 0);
            match.getPlayerExp().put(uuid, current + amount);

            pendingExp.merge(uuid, amount, Integer::sum);
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.4f, 1.5f);
            }
        }
    }

    public boolean removeExp(java.util.UUID uuid, int amount) {
        Match match = playerToMatch.get(uuid);
        if (match != null) {
            int current = match.getPlayerExp().getOrDefault(uuid, 0);
            if (current >= amount) {
                match.getPlayerExp().put(uuid, current - amount);
                return true;
            }
        }
        return false;
    }

    public String getPlayerArena(java.util.UUID uuid) {
        Match match = playerToMatch.get(uuid);
        if (match != null) {
            return match.getPlayerArenas().getOrDefault(uuid, "1");
        }
        return playerArenas.getOrDefault(uuid, "1");
    }

    public void setPlayerArena(java.util.UUID uuid, String arena) {
        playerArenas.put(uuid, arena);
        // If the player is already in a match, getPlayerArena() reads the match's own arena map and
        // ignores the global one, so mirror the assignment there too — otherwise /td setarena has no
        // effect on a player who is already in a game.
        Match match = playerToMatch.get(uuid);
        if (match != null) {
            match.getPlayerArenas().put(uuid, arena);
        }
    }

    public int getPlayerIncomeRate(java.util.UUID uuid) {
        int genLevel = getGoldGenLevel(uuid);
        return switch (genLevel) {
            case 1 -> 5;
            case 2 -> 20;
            case 3 -> 50;
            case 4 -> 100;
            default -> 5;
        };
    }


    public int getGoldGenLevel(java.util.UUID uuid) {
        return goldGenLevels.getOrDefault(uuid, 1);
    }

    public void setGoldGenLevel(java.util.UUID uuid, int level) {
        goldGenLevels.put(uuid, level);
    }

    public int getSwordLevel(java.util.UUID uuid) {
        return swordLevels.getOrDefault(uuid, 1);
    }

    public void setSwordLevel(java.util.UUID uuid, int level) {
        swordLevels.put(uuid, level);
    }

    public int getBowLevel(java.util.UUID uuid) {
        return bowLevels.getOrDefault(uuid, 1);
    }

    public void setBowLevel(java.util.UUID uuid, int level) {
        bowLevels.put(uuid, level);
    }

    public void giveStarterWeapons(Player player) {
        java.util.UUID uuid = player.getUniqueId();
        int sLvl = getSwordLevel(uuid);
        int bLvl = getBowLevel(uuid);
        
        Material swordMat = switch (sLvl) {
            case 2 -> Material.STONE_SWORD;
            case 3 -> Material.IRON_SWORD;
            case 4 -> Material.DIAMOND_SWORD;
            case 5 -> Material.NETHERITE_SWORD;
            default -> Material.WOODEN_SWORD;
        };
        
        Material bowMat = switch (bLvl) {
            case 2, 3 -> Material.CROSSBOW;
            default -> Material.BOW;
        };
        
        replaceItemInInventory(player, swordMat, "SWORD");
        replaceItemInInventory(player, bowMat, "BOW");

        // Clear any arrows that might have been in the inventory
        player.getInventory().remove(Material.ARROW);
        // Strip the lobby matchmaking compass; the world-appropriate compass is granted via ensureCompass.
        player.getInventory().remove(Material.COMPASS);

        // Give Mob Spawner Menu Item in slot 7 (index 7)
        org.bukkit.inventory.ItemStack spawnerItem = new org.bukkit.inventory.ItemStack(Material.NETHER_STAR);
        org.bukkit.inventory.meta.ItemMeta spawnerMeta = spawnerItem.getItemMeta();
        if (spawnerMeta != null) {
            spawnerMeta.setDisplayName(ChatColor.LIGHT_PURPLE + "" + ChatColor.BOLD + "Mob Spawner Menu");
            spawnerMeta.setLore(java.util.Arrays.asList(ChatColor.GRAY + "Right-click to open the Mob Spawner GUI."));
            spawnerItem.setItemMeta(spawnerMeta);
        }
        player.getInventory().setItem(7, spawnerItem);

        // Give Player Upgrades Menu Item in slot 8 (index 8)
        org.bukkit.inventory.ItemStack upgradeItem = new org.bukkit.inventory.ItemStack(Material.EMERALD);
        org.bukkit.inventory.meta.ItemMeta upgradeMeta = upgradeItem.getItemMeta();
        if (upgradeMeta != null) {
            upgradeMeta.setDisplayName(ChatColor.GOLD + "" + ChatColor.BOLD + "Player Upgrades Menu");
            upgradeMeta.setLore(java.util.Arrays.asList(ChatColor.GRAY + "Right-click to open the Player Upgrades GUI."));
            upgradeItem.setItemMeta(upgradeMeta);
        }
        player.getInventory().setItem(8, upgradeItem);
    }

    private void replaceItemInInventory(Player player, Material newMat, String type) {
        org.bukkit.inventory.PlayerInventory inv = player.getInventory();
        
        if (type.equals("SWORD")) {
            for (int i = 0; i < inv.getSize(); i++) {
                org.bukkit.inventory.ItemStack item = inv.getItem(i);
                if (item != null && item.getType().name().endsWith("_SWORD")) {
                    inv.setItem(i, null);
                }
            }
        } else if (type.equals("BOW")) {
            for (int i = 0; i < inv.getSize(); i++) {
                org.bukkit.inventory.ItemStack item = inv.getItem(i);
                if (item != null && (item.getType() == Material.BOW || item.getType() == Material.CROSSBOW)) {
                    inv.setItem(i, null);
                }
            }
        }
        
        org.bukkit.inventory.ItemStack item = new org.bukkit.inventory.ItemStack(newMat);
        if (newMat == Material.CROSSBOW) {
            org.bukkit.inventory.meta.CrossbowMeta meta = (org.bukkit.inventory.meta.CrossbowMeta) item.getItemMeta();
            if (meta != null) {
                int bowLvl = getBowLevel(player.getUniqueId());
                if (bowLvl == 2) {
                    meta.addEnchant(org.bukkit.enchantments.Enchantment.QUICK_CHARGE, 1, true);
                } else if (bowLvl == 3) {
                    meta.addEnchant(org.bukkit.enchantments.Enchantment.QUICK_CHARGE, 3, true);
                    meta.addEnchant(org.bukkit.enchantments.Enchantment.PIERCING, 4, true);
                }
                item.setItemMeta(meta);
            }
        }
        
        inv.addItem(item);
    }

    public void upgradeSword(Player player) {
        java.util.UUID uuid = player.getUniqueId();
        int currentLevel = getSwordLevel(uuid);
        if (currentLevel >= 5) {
            player.sendMessage(ChatColor.RED + "Your sword is already at maximum level!");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.8f, 1.0f);
            return;
        }
        
        int cost = switch (currentLevel) {
            case 1 -> 80;   // Stone
            case 2 -> 200;  // Iron
            case 3 -> 450;  // Diamond
            case 4 -> 900;  // Netherite
            default -> 9999;
        };
        
        if (getExp(uuid) < cost) {
            player.sendMessage(ChatColor.RED + "Not enough TD EXP! Requires " + cost + " EXP.");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.8f, 1.0f);
            return;
        }
        
        removeExp(uuid, cost);
        setSwordLevel(uuid, currentLevel + 1);
        giveStarterWeapons(player);
        player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 1.0f, 1.2f);
    }

    public void openUpgradesGUI(Player player) {
        org.bukkit.inventory.Inventory gui = Bukkit.createInventory(null, 54, ChatColor.DARK_BLUE + "Player Upgrades");
        java.util.UUID uuid = player.getUniqueId();
        
        org.bukkit.inventory.ItemStack border = createGUIItem(Material.BLACK_STAINED_GLASS_PANE, " ");
        org.bukkit.inventory.ItemStack background = createGUIItem(Material.GRAY_STAINED_GLASS_PANE, " ");
        
        for (int i = 0; i < 54; i++) {
            if (i < 9 || i >= 45 || i % 9 == 0 || i % 9 == 8) {
                gui.setItem(i, border);
            } else {
                gui.setItem(i, background);
            }
        }
        
        // 1. Passive Gold Generator (Slot 4 - Top Middle)
        int goldLvl = getGoldGenLevel(uuid);
        Material genMat = Material.GOLD_INGOT;
        String genName = ChatColor.GOLD + "Passive Gold Generator";
        java.util.List<String> genLore = new java.util.ArrayList<>();
        genLore.add(ChatColor.GRAY + "Generates passive gold during the wave.");
        genLore.add(ChatColor.YELLOW + "Current Level: " + ChatColor.WHITE + goldLvl + "/4");
        genLore.add(ChatColor.YELLOW + "Current Rate: " + ChatColor.WHITE + getPlayerIncomeRate(uuid) + " Gold/sec");
        genLore.add("");
        if (goldLvl < 4) {
            int nextCost = goldLvl == 1 ? plugin.getConfig().getInt("upgrades.gold-gen.upgrade-costs.level2", 100) : goldLvl == 2 ? plugin.getConfig().getInt("upgrades.gold-gen.upgrade-costs.level3", 300) : plugin.getConfig().getInt("upgrades.gold-gen.upgrade-costs.level4", 600);
            int nextRate = goldLvl == 1 ? plugin.getConfig().getInt("upgrades.gold-gen.level2-income", 20) : goldLvl == 2 ? plugin.getConfig().getInt("upgrades.gold-gen.level3-income", 50) : plugin.getConfig().getInt("upgrades.gold-gen.level4-income", 100);
            genLore.add(ChatColor.GREEN + "Upgrade to Level " + (goldLvl + 1) + ":");
            genLore.add(ChatColor.GRAY + " - Rate: " + nextRate + " Gold/sec");
            genLore.add(ChatColor.GOLD + " - Cost: " + ChatColor.YELLOW + nextCost + " TD EXP");
            genLore.add("");
            genLore.add(ChatColor.YELLOW + "Click to upgrade!");
        } else {
            genLore.add(ChatColor.RED + "MAX LEVEL REACHED");
        }
        gui.setItem(4, createCustomGUIItem(genMat, genName, genLore));
        
        // 2. Sword Upgrade (Slot 10)
        int sLvl = getSwordLevel(uuid);
        Material swordMat = sLvl == 1 ? Material.WOODEN_SWORD : sLvl == 2 ? Material.STONE_SWORD : sLvl == 3 ? Material.IRON_SWORD : sLvl == 4 ? Material.DIAMOND_SWORD : Material.NETHERITE_SWORD;
        String swordName = ChatColor.AQUA + "Sword Upgrade";
        java.util.List<String> swordLore = new java.util.ArrayList<>();
        swordLore.add(ChatColor.GRAY + "Upgrades your melee combat sword.");
        swordLore.add(ChatColor.YELLOW + "Current Tier: " + ChatColor.WHITE + swordMat.name().replace("_", " "));
        swordLore.add("");
        if (sLvl < 5) {
            int nextCost = sLvl == 1 ? plugin.getConfig().getInt("upgrades.sword.upgrade-costs.level2", 80) : sLvl == 2 ? plugin.getConfig().getInt("upgrades.sword.upgrade-costs.level3", 200) : sLvl == 3 ? plugin.getConfig().getInt("upgrades.sword.upgrade-costs.level4", 450) : plugin.getConfig().getInt("upgrades.sword.upgrade-costs.level5", 900);
            String nextTier = sLvl == 1 ? "Stone" : sLvl == 2 ? "Iron" : sLvl == 3 ? "Diamond" : "Netherite";
            swordLore.add(ChatColor.GREEN + "Upgrade to " + nextTier + " Sword:");
            swordLore.add(ChatColor.GOLD + " - Cost: " + ChatColor.YELLOW + nextCost + " TD EXP");
            swordLore.add("");
            swordLore.add(ChatColor.YELLOW + "Click to upgrade!");
        } else {
            swordLore.add(ChatColor.RED + "MAX LEVEL REACHED");
        }
        gui.setItem(10, createCustomGUIItem(swordMat, swordName, swordLore));
        
        // 3. Bow Upgrade (Slot 11)
        int bLvl = getBowLevel(uuid);
        Material bowMat = bLvl == 1 ? Material.BOW : Material.CROSSBOW;
        String bowName = ChatColor.LIGHT_PURPLE + "Bow Upgrade";
        java.util.List<String> bowLore = new java.util.ArrayList<>();
        bowLore.add(ChatColor.GRAY + "Upgrades your ranged combat weapon.");
        bowLore.add(ChatColor.YELLOW + "Current Weapon: " + ChatColor.WHITE + (bLvl == 1 ? "Bow" : bLvl == 2 ? "Crossbow (Quick Charge I)" : "Crossbow (Quick Charge III + Piercing IV)"));
        bowLore.add("");
        if (bLvl < 3) {
            int nextCost = bLvl == 1 ? plugin.getConfig().getInt("upgrades.bow.upgrade-costs.level2", 150) : plugin.getConfig().getInt("upgrades.bow.upgrade-costs.level3", 450);
            String nextWeapon = bLvl == 1 ? "Crossbow (Quick Charge I)" : "Crossbow (Quick Charge III + Piercing IV)";
            bowLore.add(ChatColor.GREEN + "Upgrade to " + nextWeapon + ":");
            bowLore.add(ChatColor.GOLD + " - Cost: " + ChatColor.YELLOW + nextCost + " TD EXP");
            bowLore.add("");
            bowLore.add(ChatColor.YELLOW + "Click to upgrade!");
        } else {
            bowLore.add(ChatColor.RED + "MAX LEVEL REACHED");
        }
        gui.setItem(11, createCustomGUIItem(bowMat, bowName, bowLore));

        // 4. Defensive Spells (Slot 12, 13, 14)
        int overchargeCost = plugin.getConfig().getInt("spells.overcharge.cost", 250) * getNextSpellMultiplier(uuid, "OVERCHARGE");
        int overchargeDur = plugin.getConfig().getInt("spells.overcharge.duration", 10);
        java.util.List<String> overchargeLore = new java.util.ArrayList<>();
        overchargeLore.add(ChatColor.GRAY + "Increases tower attack speeds on your track by 15%.");
        overchargeLore.add(ChatColor.GRAY + "Duration: " + overchargeDur + " seconds");
        overchargeLore.add(ChatColor.GOLD + "Cost: " + ChatColor.YELLOW + overchargeCost + " Gold");
        overchargeLore.add("");
        overchargeLore.add(ChatColor.YELLOW + "Click to cast on your track!");
        gui.setItem(12, createCustomGUIItem(Material.EMERALD_BLOCK, ChatColor.GREEN + "" + ChatColor.BOLD + "Spell: Overcharge", overchargeLore));

        int freezeCost = plugin.getConfig().getInt("spells.freeze.cost", 200) * getNextSpellMultiplier(uuid, "FREEZE");
        int freezeDur = plugin.getConfig().getInt("spells.freeze.duration", 10);
        double freezeSlow = plugin.getConfig().getDouble("spells.freeze.slow-multiplier", 0.7);
        int freezePct = (int) Math.round((1.0 - freezeSlow) * 100.0);
        java.util.List<String> freezeLore = new java.util.ArrayList<>();
        freezeLore.add(ChatColor.GRAY + "Slows down mobs traversing your track by " + freezePct + "%.");
        freezeLore.add(ChatColor.GRAY + "(Does not affect slow-immune mobs)");
        freezeLore.add(ChatColor.GRAY + "Duration: " + freezeDur + " seconds");
        freezeLore.add(ChatColor.GOLD + "Cost: " + ChatColor.YELLOW + freezeCost + " Gold");
        freezeLore.add(ChatColor.RED + "Cooldown: 30 seconds");
        long freezeRemaining = getFreezeCooldownRemaining(uuid);
        if (freezeRemaining > 0) {
            freezeLore.add(ChatColor.RED + "On cooldown: " + ((freezeRemaining / 1000) + 1) + "s remaining");
        }
        freezeLore.add("");
        freezeLore.add(ChatColor.YELLOW + "Click to cast on your track!");
        gui.setItem(13, createCustomGUIItem(Material.SNOW_BLOCK, ChatColor.AQUA + "" + ChatColor.BOLD + "Spell: Freeze", freezeLore));

        int stormCost = plugin.getConfig().getInt("spells.damage-storm.cost", 300) * getNextSpellMultiplier(uuid, "DAMAGE_STORM");
        int stormDur = plugin.getConfig().getInt("spells.damage-storm.duration", 10);
        double stormDps = plugin.getConfig().getDouble("spells.damage-storm.damage-per-second", 2.0);
        java.util.List<String> stormLore = new java.util.ArrayList<>();
        stormLore.add(ChatColor.GRAY + "Deals periodic damage (" + stormDps + " HP/s) to mobs traversing your track.");
        stormLore.add(ChatColor.GRAY + "Duration: " + stormDur + " seconds");
        stormLore.add(ChatColor.GOLD + "Cost: " + ChatColor.YELLOW + stormCost + " Gold");
        stormLore.add("");
        stormLore.add(ChatColor.YELLOW + "Click to cast on your track!");
        gui.setItem(14, createCustomGUIItem(Material.MAGMA_BLOCK, ChatColor.RED + "" + ChatColor.BOLD + "Spell: Damage Storm", stormLore));
 
        // 5. Offensive Spells / Sabotages (Slot 21, 22, 23)
        int hasteCost = plugin.getConfig().getInt("spells.haste-rush.cost", 200) * getNextSpellMultiplier(uuid, "HASTE_RUSH");
        int hasteDur = plugin.getConfig().getInt("spells.haste-rush.duration", 6);
        double hasteMult = plugin.getConfig().getDouble("spells.haste-rush.speed-multiplier", 1.6);
        int hastePct = (int) Math.round((hasteMult - 1.0) * 100.0);
        java.util.List<String> hasteLore = new java.util.ArrayList<>();
        hasteLore.add(ChatColor.GRAY + "Grants Speed I (+" + hastePct + "% speed) to all mobs");
        hasteLore.add(ChatColor.GRAY + "currently traversing the opponent's track.");
        hasteLore.add(ChatColor.GRAY + "Duration: " + hasteDur + " seconds");
        hasteLore.add(ChatColor.GOLD + "Cost: " + ChatColor.YELLOW + hasteCost + " Gold");
        hasteLore.add("");
        hasteLore.add(ChatColor.RED + "Click to cast on OPPONENT'S track!");
        gui.setItem(21, createCustomGUIItem(Material.GOLD_BLOCK, ChatColor.GOLD + "" + ChatColor.BOLD + "Sabotage: Haste Rush", hasteLore));
 
        int empCost = plugin.getConfig().getInt("spells.tower-emp.cost", 250) * getNextSpellMultiplier(uuid, "TOWER_EMP");
        int empDur = plugin.getConfig().getInt("spells.tower-emp.duration", 6);
        java.util.List<String> empLore = new java.util.ArrayList<>();
        empLore.add(ChatColor.GRAY + "Disables a random tower on the opponent's");
        empLore.add(ChatColor.GRAY + "track, stopping its attacks completely.");
        empLore.add(ChatColor.GRAY + "Duration: " + empDur + " seconds");
        empLore.add(ChatColor.GOLD + "Cost: " + ChatColor.YELLOW + empCost + " Gold");
        empLore.add("");
        empLore.add(ChatColor.RED + "Click to cast on OPPONENT'S track!");
        gui.setItem(22, createCustomGUIItem(Material.REDSTONE_BLOCK, ChatColor.RED + "" + ChatColor.BOLD + "Sabotage: Tower EMP", empLore));
 
        int shieldCost = plugin.getConfig().getInt("spells.slow-shield.cost", 150) * getNextSpellMultiplier(uuid, "SLOW_SHIELD");
        int shieldDur = plugin.getConfig().getInt("spells.slow-shield.duration", 5);
        java.util.List<String> shieldLore = new java.util.ArrayList<>();
        shieldLore.add(ChatColor.GRAY + "Grants slow immunity to any mobs spawned");
        shieldLore.add(ChatColor.GRAY + "on the opponent's track during the spell.");
        shieldLore.add(ChatColor.GRAY + "Duration: " + shieldDur + " seconds");
        shieldLore.add(ChatColor.GOLD + "Cost: " + ChatColor.YELLOW + shieldCost + " Gold");
        shieldLore.add("");
        shieldLore.add(ChatColor.RED + "Click to cast on OPPONENT'S track!");
        gui.setItem(23, createCustomGUIItem(Material.LAPIS_BLOCK, ChatColor.BLUE + "" + ChatColor.BOLD + "Sabotage: Slow Shield", shieldLore));

        // Stats Display (Slot 49)
        java.util.List<String> statLore = new java.util.ArrayList<>();
        statLore.add(ChatColor.GOLD + "Gold: " + ChatColor.YELLOW + "$" + getGold(uuid));
        statLore.add(ChatColor.GREEN + "EXP: " + ChatColor.LIGHT_PURPLE + getExp(uuid) + " XP");
        gui.setItem(49, createCustomGUIItem(Material.PLAYER_HEAD, ChatColor.YELLOW + player.getName() + "'s Stats", statLore));

        // Back to spawner GUI (Slot 45)
        gui.setItem(45, createGUIItem(Material.ARROW, ChatColor.GRAY + "Back to Spawner GUI"));

        player.openInventory(gui);
    }

    private org.bukkit.inventory.ItemStack createCustomGUIItem(Material material, String name, java.util.List<String> lore) {
        org.bukkit.inventory.ItemStack item = new org.bukkit.inventory.ItemStack(material);
        org.bukkit.inventory.meta.ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private org.bukkit.inventory.ItemStack createGUIItem(Material material, String name) {
        return createCustomGUIItem(material, name, new java.util.ArrayList<>());
    }

    private org.bukkit.inventory.ItemStack createPotionGUIItem(String effectName, String fallbackName, String displayName, java.util.List<String> lore) {
        org.bukkit.inventory.ItemStack item = new org.bukkit.inventory.ItemStack(Material.SPLASH_POTION);
        org.bukkit.inventory.meta.PotionMeta meta = (org.bukkit.inventory.meta.PotionMeta) item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(displayName);
            meta.setLore(lore);
            org.bukkit.potion.PotionEffectType type = org.bukkit.potion.PotionEffectType.getByName(effectName);
            if (type == null) {
                type = org.bukkit.potion.PotionEffectType.getByName(fallbackName);
            }
            if (type != null) {
                int duration = (effectName.contains("HEAL") || effectName.contains("HARM") || effectName.contains("DAMAGE")) ? 1 : 200;
                meta.addCustomEffect(new org.bukkit.potion.PotionEffect(type, duration, 1), true);
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    public void startLobbyQueueCountdown() {
        if (lobbyQueueTask != null) return;

        lobbyQueueSecondsLeft = 45;

        for (java.util.UUID uuid : matchQueue) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) {
                p.sendMessage(ChatColor.GREEN + "Match found! Transporting to game in 45 seconds.");
                p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.0f);
            }
        }
        
        lobbyQueueTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            lobbyQueueSecondsLeft--;
            
            if (lobbyQueueSecondsLeft <= 0) {
                if (lobbyQueueTask != null) {
                    lobbyQueueTask.cancel();
                    lobbyQueueTask = null;
                }
                
                setGameState(GameState.STARTING);
                return;
            }
            
            if (lobbyQueueSecondsLeft == 20 || lobbyQueueSecondsLeft == 10 || lobbyQueueSecondsLeft <= 5) {
                for (java.util.UUID uuid : matchQueue) {
                    Player p = Bukkit.getPlayer(uuid);
                    if (p != null) {
                        p.sendMessage(ChatColor.YELLOW + "Transporting in " + lobbyQueueSecondsLeft + " seconds...");
                        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 1.0f, 1.0f);
                        p.sendTitle(ChatColor.YELLOW + String.valueOf(lobbyQueueSecondsLeft), ChatColor.GRAY + "seconds until transport", 0, 20, 0);
                    }
                }
            }
        }, 20L, 20L);
    }

    /**
     * Forces the lobby countdown to finish in 3 seconds, transporting everyone queued. Requires at
     * least 2 players; starts the countdown first if it isn't already running.
     */
    public void forceStartMatch() {
        if (matchQueue.size() < 2) return;
        if (lobbyQueueTask == null) startLobbyQueueCountdown();
        lobbyQueueSecondsLeft = 3; // Force transport in 3 seconds
    }

    // Per-player sidebar scoreboards, reused across ticks so updates don't flicker.
    private final Map<UUID, org.bukkit.scoreboard.Scoreboard> playerScoreboards = new HashMap<>();

    public void updateScoreboard(Player player) {
        UUID uuid = player.getUniqueId();
        Match match = playerToMatch.get(uuid);

        // Not in a match, or registered to a match but not yet physically in its world (e.g. still in
        // the lobby during the pre-teleport countdown): tear down any sidebar we gave them and restore
        // the main scoreboard. Players join playerToMatch ~5s before they are teleported in, so gating
        // on world membership keeps the sidebar hidden until they actually arrive in the game world.
        if (match == null || match.getWorld() == null || !player.getWorld().equals(match.getWorld())) {
            if (playerScoreboards.remove(uuid) != null) {
                player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
            }
            return;
        }

        // Get (or build) this player's dedicated scoreboard + sidebar objective.
        org.bukkit.scoreboard.Scoreboard board = playerScoreboards.get(uuid);
        org.bukkit.scoreboard.Objective obj;
        if (board == null || player.getScoreboard() != board) {
            board = Bukkit.getScoreboardManager().getNewScoreboard();
            obj = board.registerNewObjective("td_sidebar", "dummy",
                    ChatColor.GOLD + "" + ChatColor.BOLD + "TOWER DEFENSE");
            obj.setDisplaySlot(org.bukkit.scoreboard.DisplaySlot.SIDEBAR);
            playerScoreboards.put(uuid, board);
            player.setScoreboard(board);
        } else {
            obj = board.getObjective("td_sidebar");
            if (obj == null) {
                obj = board.registerNewObjective("td_sidebar", "dummy",
                        ChatColor.GOLD + "" + ChatColor.BOLD + "TOWER DEFENSE");
                obj.setDisplaySlot(org.bukkit.scoreboard.DisplaySlot.SIDEBAR);
            }
        }

        // Compose the lines (top to bottom).
        String arena = getPlayerArena(uuid);
        String teamLabel = "1".equals(arena)
                ? ChatColor.BLUE + "" + ChatColor.BOLD + "BLUE"
                : ChatColor.RED + "" + ChatColor.BOLD + "RED";
        int myHealth = match.getArenaHealth().getOrDefault(arena, maxCastleHealth);

        // Strikethrough spaces render as a clean horizontal rule regardless of font glyph support.
        String sep = ChatColor.DARK_GRAY + "" + ChatColor.STRIKETHROUGH + "              ";

        java.util.List<String> lines = new java.util.ArrayList<>();
        lines.add(sep);
        lines.add(ChatColor.GRAY + "Team: " + teamLabel);
        lines.add(ChatColor.GOLD + "Gold: " + ChatColor.WHITE + "$" + getGold(uuid));
        lines.add(ChatColor.GREEN + "EXP: " + ChatColor.WHITE + getExp(uuid) + " XP");
        lines.add(" "); // spacer
        lines.add(ChatColor.RED + "" + ChatColor.BOLD + "Your Castle " + ChatColor.GRAY + "(" + myHealth + "/" + maxCastleHealth + ")");
        lines.add(healthBar(myHealth));
        if (match.getMapData().isSinglePlayer()) {
            int wave = plugin.getWaveManager().getCurrentWave(match);
            lines.add("  "); // spacer
            lines.add(ChatColor.AQUA + "" + ChatColor.BOLD + "Wave: " + ChatColor.WHITE + wave);
        } else {
            String oppArena = "1".equals(arena) ? "2" : "1";
            int oppHealth = match.getArenaHealth().getOrDefault(oppArena, maxCastleHealth);
            lines.add("  "); // spacer
            lines.add(ChatColor.LIGHT_PURPLE + "" + ChatColor.BOLD + "Enemy Castle " + ChatColor.GRAY + "(" + oppHealth + "/" + maxCastleHealth + ")");
            lines.add(healthBar(oppHealth));
        }
        lines.add(sep);

        renderSidebar(board, obj, lines);
    }

    /**
     * Renders the given lines into the sidebar objective using one team per row. Each row uses a unique,
     * invisible entry string (a color code) as a stable key and carries its visible text in the team
     * prefix, so re-rendering each tick updates text in place rather than removing/re-adding lines
     * (which causes flicker).
     */
    private void renderSidebar(org.bukkit.scoreboard.Scoreboard board, org.bukkit.scoreboard.Objective obj, java.util.List<String> lines) {
        int n = lines.size();
        for (int i = 0; i < n; i++) {
            String teamId = "td_line_" + i;
            String entry = ChatColor.values()[i].toString() + ChatColor.RESET; // unique + invisible
            org.bukkit.scoreboard.Team team = board.getTeam(teamId);
            if (team == null) {
                team = board.registerNewTeam(teamId);
            }
            if (!team.hasEntry(entry)) {
                team.addEntry(entry);
            }
            team.setPrefix(lines.get(i));
            obj.getScore(entry).setScore(n - i); // higher score = higher on the sidebar
        }
        // Drop any rows left over from a previously longer render.
        for (int i = n; i < ChatColor.values().length; i++) {
            String teamId = "td_line_" + i;
            String entry = ChatColor.values()[i].toString() + ChatColor.RESET;
            board.resetScores(entry);
            org.bukkit.scoreboard.Team team = board.getTeam(teamId);
            if (team != null) team.unregister();
        }
    }

    /**
     * Builds a 10-segment castle-health bar using the same "■" glyph as the castle holograms (proven to
     * render here). Filled segments are coloured by remaining ratio; the rest are dark gray.
     */
    private String healthBar(int health) {
        int totalBars = 10;
        double ratio = maxCastleHealth > 0 ? (double) health / maxCastleHealth : 0.0;
        ratio = Math.max(0.0, Math.min(1.0, ratio));
        int filled = (int) Math.round(ratio * totalBars);

        ChatColor color = ratio >= 0.6 ? ChatColor.GREEN : ratio >= 0.25 ? ChatColor.YELLOW : ChatColor.RED;

        StringBuilder bar = new StringBuilder();
        bar.append(color);
        for (int i = 0; i < filled; i++) bar.append("■");
        bar.append(ChatColor.DARK_GRAY);
        for (int i = filled; i < totalBars; i++) bar.append("■");
        return bar.toString();
    }

    public void updateTabNames() {
        // Show each player's team in the tab player list and group the names by team. The vanilla tab
        // list orders players by their scoreboard team's registered name, so we use "td_team_1" (Blue)
        // and "td_team_2" (Red) to make Blue sort above Red. The coloured prefix labels which team each
        // player is on. Teams live on each viewer's own sidebar board, since that is the scoreboard the
        // client uses to render and sort the tab list.
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            UUID vid = viewer.getUniqueId();
            Match match = playerToMatch.get(vid);
            org.bukkit.scoreboard.Scoreboard board = playerScoreboards.get(vid);
            if (match == null || board == null || viewer.getScoreboard() != board) {
                continue;
            }

            org.bukkit.scoreboard.Team blue = getOrCreateTabTeam(board, "td_team_1",
                    ChatColor.BLUE, ChatColor.BLUE + "" + ChatColor.BOLD + "[BLUE] " + ChatColor.RESET);
            org.bukkit.scoreboard.Team red = getOrCreateTabTeam(board, "td_team_2",
                    ChatColor.RED, ChatColor.RED + "" + ChatColor.BOLD + "[RED] " + ChatColor.RESET);

            for (UUID id : match.getPlayers()) {
                Player p = Bukkit.getPlayer(id);
                if (p == null) continue;
                String arena = match.getPlayerArenas().getOrDefault(id, "1");
                org.bukkit.scoreboard.Team team = "1".equals(arena) ? blue : red;
                // addEntry moves the player off any other team on this board, so a re-assigned
                // player (e.g. via /td setarena) lands in the right group on the next tick.
                if (!team.hasEntry(p.getName())) {
                    team.addEntry(p.getName());
                }
            }
        }
    }

    private org.bukkit.scoreboard.Team getOrCreateTabTeam(org.bukkit.scoreboard.Scoreboard board,
            String id, ChatColor color, String prefix) {
        org.bukkit.scoreboard.Team team = board.getTeam(id);
        if (team == null) {
            team = board.registerNewTeam(id);
        }
        team.setColor(color);
        team.setPrefix(prefix);
        return team;
    }

    public void cancelLobbyQueueCountdown(String reason) {
        if (lobbyQueueTask != null) {
            lobbyQueueTask.cancel();
            lobbyQueueTask = null;
        }
        lobbyQueueSecondsLeft = 0;
        
        for (java.util.UUID uuid : matchQueue) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) {
                p.sendMessage(ChatColor.RED + "Queue countdown cancelled: " + reason);
                p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                p.sendTitle(" ", " ", 0, 10, 0);
            }
        }
    }
}