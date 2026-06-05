package com.pauljang.towerDefense.core;

import com.pauljang.towerDefense.TowerDefense;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.Location;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.entity.ArmorStand;

public class GameManager {

    private final TowerDefense plugin;
    private GameState currentState = null;
    private long matchStartTime = 0L;

    private int maxCastleHealth = 100;
    private final java.util.Map<String, Integer> arenaHealth = new java.util.HashMap<>();
    private final java.util.Map<String, java.util.Map<String, Long>> activeSpells = new java.util.HashMap<>();
    private final java.util.Map<org.bukkit.Location, org.bukkit.Material> originalFloorBlocks = new java.util.HashMap<>();
    private final java.util.Map<String, ArmorStand> castleHolograms = new java.util.HashMap<>();
    private BossBar castleBossBar = null;

    private java.util.UUID pendingChallenger = null;
    private java.util.UUID pendingChallengeTarget = null;
    private long challengeTimestamp = 0L;
    
    private final java.util.Map<java.util.UUID, Integer> playerGold = new java.util.HashMap<>();
    private final java.util.Map<java.util.UUID, Integer> playerExp = new java.util.HashMap<>();
    private final java.util.Map<java.util.UUID, Integer> goldGenLevels = new java.util.HashMap<>();
    private final java.util.Map<java.util.UUID, Integer> swordLevels = new java.util.HashMap<>();
    private final java.util.Map<java.util.UUID, Integer> bowLevels = new java.util.HashMap<>();
    private final java.util.Map<java.util.UUID, String> playerArenas = new java.util.HashMap<>();
    private final java.util.List<java.util.UUID> matchQueue = new java.util.ArrayList<>();
    private org.bukkit.scheduler.BukkitTask lobbyQueueTask = null;
    private int lobbyQueueSecondsLeft = 0;

    public GameManager(TowerDefense plugin) {
        this.plugin = plugin;
        this.maxCastleHealth = plugin.getConfig().getInt("game.max-castle-health", 100);
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

        // Populate matchQueue from the players physically in game_world if not already queued
        if (matchQueue.size() < 2) {
            matchQueue.clear();
            if (gameWorld != null) {
                for (Player p : gameWorld.getPlayers()) {
                    if (matchQueue.size() < 2) {
                        matchQueue.add(p.getUniqueId());
                    }
                }
            }
        }

        // Teleport queued players to their arena starts, assign arenas, and initialize stats
        org.bukkit.Location spawnLoc = gameWorld != null ? gameWorld.getSpawnLocation() : org.bukkit.Bukkit.getWorlds().get(0).getSpawnLocation();

        int count = 1;
        for (java.util.UUID uuid : matchQueue) {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null) continue;

            // Assign arenas: first queued player gets Arena 1, second gets Arena 2
            String assignedArena = count == 1 ? "1" : "2";
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
            subtitleMsg = (player2 != null ? player2.getName() : "Arena 2") + " wins the game!";
            Bukkit.broadcastMessage(ChatColor.GOLD + "[Tower Defense] " + (player2 != null ? player2.getName() : "Arena 2") + " has won the duel!");
        } else if (hp2 <= 0 && hp1 > 0) {
            titleMsg = ChatColor.GREEN + "VICTORY";
            subtitleMsg = (player1 != null ? player1.getName() : "Arena 1") + " wins the game!";
            Bukkit.broadcastMessage(ChatColor.GOLD + "[Tower Defense] " + (player1 != null ? player1.getName() : "Arena 1") + " has won the duel!");
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
        goldGenLevels.clear();
        swordLevels.clear();
        bowLevels.clear();
        playerArenas.clear();

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
        matchQueue.remove(player.getUniqueId());

        // Cancel lobby queue countdown if a queued player leaves
        if (lobbyQueueTask != null && matchQueue.size() < 2) {
            cancelLobbyQueueCountdown(player.getName() + " has disconnected.");
        }

        if (currentState != GameState.ACTIVE && currentState != GameState.STARTING) {
            return;
        }

        String quittingArena = getPlayerArena(player.getUniqueId());
        if (!"1".equals(quittingArena) && !"2".equals(quittingArena)) {
            return; // Not in the active game
        }

        // Determine the winning arena
        String winningArena = "1".equals(quittingArena) ? "2" : "1";

        // Find the remaining online player in the winning arena
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

    public java.util.List<java.util.UUID> getMatchQueue() {
        return matchQueue;
    }

    public void toggleQueue(Player player) {
        if (currentState == GameState.ACTIVE || currentState == GameState.STARTING) {
            player.sendMessage(ChatColor.RED + "Cannot join the queue while a game is in progress!");
            return;
        }

        java.util.UUID uuid = player.getUniqueId();
        if (matchQueue.contains(uuid)) {
            matchQueue.remove(uuid);
            player.sendMessage(ChatColor.YELLOW + "You have left the match queue (" + matchQueue.size() + "/2).");
            giveLobbyItems(player);
            if (lobbyQueueTask != null) {
                cancelLobbyQueueCountdown("A player has left the queue.");
            }
        } else {
            if (matchQueue.size() >= 2) {
                player.sendMessage(ChatColor.RED + "The match queue is already full!");
                return;
            }
            matchQueue.add(uuid);
            player.sendMessage(ChatColor.GREEN + "You have joined the match queue (" + matchQueue.size() + "/2)!");
            giveLobbyItems(player);
            // Check if queue is full to start the 30-second lobby queue countdown
            if (matchQueue.size() == 2) {
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
                lore.add(ChatColor.GRAY + "Right-click to view open games.");
                meta.setLore(lore);
            }
            compass.setItemMeta(meta);
        }
        player.getInventory().setItem(4, compass); // Put it in the center slot
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
        
        // Slot 13: Join matchmaking queue
        org.bukkit.inventory.ItemStack gameItem = new org.bukkit.inventory.ItemStack(org.bukkit.Material.MAP);
        org.bukkit.inventory.meta.ItemMeta gameMeta = gameItem.getItemMeta();
        if (gameMeta != null) {
            gameMeta.setDisplayName(ChatColor.GREEN + "" + ChatColor.BOLD + "Tower Defense - Matchmaking Queue");
            java.util.List<String> lore = new java.util.ArrayList<>();
            lore.add(ChatColor.GRAY + "Click to join or leave matchmaking.");
            lore.add("");
            
            int waitingCount = matchQueue.size();
            
            lore.add(ChatColor.GRAY + "Players queued: " + ChatColor.YELLOW + waitingCount + "/2");
            gameMeta.setLore(lore);
            gameItem.setItemMeta(gameMeta);
        }
        gui.setItem(13, gameItem);
        
        player.openInventory(gui);
    }

    public void checkGameStartConditions() {
        if (currentState != GameState.LOBBY) return;
        org.bukkit.World gameWorld = org.bukkit.Bukkit.getWorld("game_world");
        if (gameWorld != null && gameWorld.getPlayers().size() >= 2) {
            setGameState(GameState.STARTING);
        }
    }

    public void checkGameStopConditions() {
        if (currentState == GameState.STARTING) {
            org.bukkit.World gameWorld = org.bukkit.Bukkit.getWorld("game_world");
            if (gameWorld == null || gameWorld.getPlayers().size() < 2) {
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

        // Load or reset game world
        resetGameWorld();
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

        // 3. Copy the game_world_template directory to game_world
        java.io.File templateFolder = new java.io.File(org.bukkit.Bukkit.getWorldContainer(), "game_world_template");
        if (templateFolder.exists()) {
            try {
                copyDirectory(templateFolder, gameWorldFolder);
                plugin.getLogger().info("Successfully copied game_world_template to game_world.");
            } catch (java.io.IOException e) {
                plugin.getLogger().severe("Failed to copy game_world_template to game_world: " + e.getMessage());
                e.printStackTrace();
            }
        } else {
            plugin.getLogger().warning("game_world_template folder not found! A new empty world will be generated.");
        }

        // 4. Load the fresh game_world map
        org.bukkit.WorldCreator gameCreator = new org.bukkit.WorldCreator("game_world");
        org.bukkit.World createdWorld = org.bukkit.Bukkit.createWorld(gameCreator);
        if (createdWorld != null) {
            createdWorld.setDifficulty(org.bukkit.Difficulty.EASY);
            createdWorld.setGameRule(org.bukkit.GameRule.DO_MOB_SPAWNING, false);
            createdWorld.setGameRule(org.bukkit.GameRule.KEEP_INVENTORY, true);
        }
        plugin.getLogger().info("game_world loaded successfully.");
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

    private void copyDirectory(java.io.File source, java.io.File destination) throws java.io.IOException {
        if (source.isDirectory()) {
            if (!destination.exists()) {
                destination.mkdirs();
            }
            String[] files = source.list();
            if (files != null) {
                for (String file : files) {
                    java.io.File srcFile = new java.io.File(source, file);
                    java.io.File destFile = new java.io.File(destination, file);
                    copyDirectory(srcFile, destFile);
                }
            }
        } else {
            java.nio.file.Files.copy(source.toPath(), destination.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
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
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.5f);
            player.sendActionBar(ChatColor.RED + "⚠ Arena " + arena + " Damaged! Health: " + updated + "/" + maxCastleHealth + " ⚠");
        }

        if (updated <= 0) {
            setGameState(GameState.ENDED);
        }
    }

    public void showBossBar() {
        if (castleBossBar == null) {
            castleBossBar = Bukkit.createBossBar(
                ChatColor.GREEN + "Arena 1: " + ChatColor.WHITE + arenaHealth.getOrDefault("1", maxCastleHealth) + " HP " +
                ChatColor.GRAY + "| " +
                ChatColor.RED + "Arena 2: " + ChatColor.WHITE + arenaHealth.getOrDefault("2", maxCastleHealth) + " HP",
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
            ChatColor.GREEN + "Arena 1: " + ChatColor.WHITE + hp1 + " HP " +
            ChatColor.GRAY + "| " +
            ChatColor.RED + "Arena 2: " + ChatColor.WHITE + hp2 + " HP"
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
        ArmorStand stand = castleHolograms.get(arena);
        if (stand == null || !stand.isValid()) {
            if (currentState == GameState.ACTIVE || currentState == GameState.STARTING) {
                java.util.List<Location> waypoints = plugin.getWaypointConfigManager().getWaypoints(arena);
                if (!waypoints.isEmpty()) {
                    Location lastWp = waypoints.get(waypoints.size() - 1);
                    Location spawnLoc = lastWp.clone().add(0, 3.0, 0);
                    
                    // Clean up any existing armor stands at the spawn location first to prevent stacking/ghost holograms
                    spawnLoc.getWorld().getNearbyEntities(spawnLoc, 2.0, 2.0, 2.0).stream()
                        .filter(e -> e instanceof ArmorStand)
                        .filter(as -> !castleHolograms.containsValue(as))
                        .forEach(org.bukkit.entity.Entity::remove);

                    stand = spawnLoc.getWorld().spawn(spawnLoc, ArmorStand.class, as -> {
                        as.setVisible(false);
                        as.setGravity(false);
                        as.setMarker(true);
                        as.setInvulnerable(true);
                        as.setPersistent(false);
                        as.setCustomName("");
                        as.setCustomNameVisible(true);
                    });
                    castleHolograms.put(arena, stand);
                }
            }
        }

        if (stand != null && stand.isValid()) {
            int health = arenaHealth.getOrDefault(arena, maxCastleHealth);
            double ratio = (double) health / maxCastleHealth;
            int totalBars = 20;
            int greenBars = (int) Math.round(ratio * totalBars);
            int grayBars = totalBars - greenBars;

            ChatColor color;
            if (ratio >= 0.6) {
                color = ChatColor.GREEN;
            } else if (ratio >= 0.25) {
                color = ChatColor.YELLOW;
            } else {
                color = ChatColor.RED;
            }

            StringBuilder bar = new StringBuilder();
            bar.append(ChatColor.GOLD).append(ChatColor.BOLD).append("CASTLE HP: ");
            bar.append(color);
            for (int i = 0; i < greenBars; i++) {
                bar.append("■");
            }
            bar.append(ChatColor.GRAY);
            for (int i = 0; i < grayBars; i++) {
                bar.append("■");
            }
            bar.append(ChatColor.GRAY).append(" (").append(health).append("/").append(maxCastleHealth).append(")");

            stand.setCustomName(bar.toString());
            stand.setCustomNameVisible(true);
        }
    }

    public void cleanupCastleHolograms() {
        for (ArmorStand stand : castleHolograms.values()) {
            if (stand != null && stand.isValid()) {
                stand.remove();
            }
        }
        castleHolograms.clear();
    }

    // --- Active Spells and Matchmaking Systems ---

    public boolean isSpellActive(String arena, String spellType) {
        java.util.Map<String, Long> spells = activeSpells.get(arena);
        if (spells == null) return false;
        Long end = spells.get(spellType.toUpperCase());
        return end != null && System.currentTimeMillis() < end;
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
        // Reset game state
        setGameState(GameState.ENDED);

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
        return playerGold.getOrDefault(uuid, plugin.getConfig().getInt("game.starting-gold", 150));
    }

    public void addGold(java.util.UUID uuid, int amount) {
        addGold(uuid, amount, false);
    }

    public void addGold(java.util.UUID uuid, int amount, boolean silent) {
        int current = getGold(uuid);
        playerGold.put(uuid, current + amount);
        
        if (!silent) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                player.sendMessage(ChatColor.GOLD + "+$" + amount + " Gold!");
                player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.5f, 1.2f);
            }
        }
    }

    public boolean removeGold(java.util.UUID uuid, int amount) {
        int current = getGold(uuid);
        if (current >= amount) {
            playerGold.put(uuid, current - amount);
            return true;
        }
        return false;
    }

    public boolean hasGold(java.util.UUID uuid, int amount) {
        return getGold(uuid) >= amount;
    }

    // --- Experience / EXP System ---

    public int getExp(java.util.UUID uuid) {
        return playerExp.getOrDefault(uuid, 0); // Default start EXP: 0
    }

    public void addExp(java.util.UUID uuid, int amount) {
        int current = getExp(uuid);
        playerExp.put(uuid, current + amount);
        
        Player player = Bukkit.getPlayer(uuid);
        if (player != null && player.isOnline()) {
            player.sendMessage(ChatColor.GREEN + "+" + amount + " TD EXP!");
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.4f, 1.5f);
        }
    }

    // --- Scoreboard HUD ---

    public void updateScoreboard(Player player) {
        org.bukkit.scoreboard.ScoreboardManager manager = Bukkit.getScoreboardManager();
        if (manager == null) return;

        if (currentState != GameState.ACTIVE) {
            player.setScoreboard(manager.getMainScoreboard());
            return;
        }

        org.bukkit.scoreboard.Scoreboard board = manager.getNewScoreboard();
        org.bukkit.scoreboard.Objective objective = board.registerNewObjective(
            "td_board",
            "dummy",
            ChatColor.GOLD + "" + ChatColor.BOLD + "TOWER DEFENSE"
        );
        objective.setDisplaySlot(org.bukkit.scoreboard.DisplaySlot.SIDEBAR);

        java.util.List<String> lines = new java.util.ArrayList<>();
        lines.add(ChatColor.GRAY + "--------------------");
        lines.add(ChatColor.YELLOW + "State: " + ChatColor.WHITE + (currentState != null ? currentState.name() : "LOBBY"));
        
        long elapsedSeconds = 0L;
        if (currentState == GameState.ACTIVE && matchStartTime > 0L) {
            elapsedSeconds = (System.currentTimeMillis() - matchStartTime) / 1000L;
        }
        long min = elapsedSeconds / 60;
        long sec = elapsedSeconds % 60;
        lines.add(ChatColor.YELLOW + "Time: " + ChatColor.WHITE + String.format("%02d:%02d", min, sec));
        
        lines.add(ChatColor.GREEN + "Arena 1 Health: " + ChatColor.WHITE + arenaHealth.getOrDefault("1", maxCastleHealth) + "/" + maxCastleHealth);
        lines.add(ChatColor.RED + "Arena 2 Health: " + ChatColor.WHITE + arenaHealth.getOrDefault("2", maxCastleHealth) + "/" + maxCastleHealth);
        lines.add(" ");
        lines.add(ChatColor.GOLD + "Your Gold: " + ChatColor.YELLOW + "$" + getGold(player.getUniqueId()));
        lines.add(ChatColor.GREEN + "Your EXP: " + ChatColor.LIGHT_PURPLE + getExp(player.getUniqueId()) + " XP");
        lines.add(ChatColor.YELLOW + "Income: " + ChatColor.GOLD + getPlayerIncomeRate(player.getUniqueId()) + " Gold/s");
        lines.add("  ");
        lines.add(ChatColor.AQUA + "Active Mobs: " + ChatColor.WHITE + plugin.getMobManager().getActiveMobs().size());
        lines.add(ChatColor.GRAY + "-------------------");

        int score = lines.size();
        for (String line : lines) {
            org.bukkit.scoreboard.Score scoreLine = objective.getScore(line);
            scoreLine.setScore(score);
            score--;
        }

        player.setScoreboard(board);
    }

    public void updateTabNames() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            String worldName = p.getWorld().getName();
            String prefix;
            if (worldName.equals("lobby_world")) {
                prefix = ChatColor.GRAY + "[Lobby] ";
            } else if (worldName.equals("game_world")) {
                if (currentState == GameState.ACTIVE) {
                    if (matchQueue.contains(p.getUniqueId())) {
                        String arena = getPlayerArena(p.getUniqueId());
                        if ("1".equals(arena)) {
                            prefix = ChatColor.GREEN + "[Arena 1] ";
                        } else if ("2".equals(arena)) {
                            prefix = ChatColor.RED + "[Arena 2] ";
                        } else {
                            prefix = ChatColor.GRAY + "[Spectator] ";
                        }
                    } else {
                        prefix = ChatColor.GRAY + "[Spectator] ";
                    }
                } else if (currentState == GameState.STARTING) {
                    if (matchQueue.contains(p.getUniqueId())) {
                        String arena = getPlayerArena(p.getUniqueId());
                        if ("1".equals(arena)) {
                            prefix = ChatColor.GREEN + "[Arena 1] ";
                        } else if ("2".equals(arena)) {
                            prefix = ChatColor.RED + "[Arena 2] ";
                        } else {
                            prefix = ChatColor.YELLOW + "[Waiting] ";
                        }
                    } else {
                        prefix = ChatColor.YELLOW + "[Waiting] ";
                    }
                } else {
                    prefix = ChatColor.YELLOW + "[Waiting] ";
                }
            } else {
                prefix = ChatColor.GRAY + "[Lobby] ";
            }
            p.setPlayerListName(prefix + ChatColor.WHITE + p.getName());
        }
    }

    public String getPlayerArena(java.util.UUID uuid) {
        return playerArenas.getOrDefault(uuid, "1");
    }

    public void setPlayerArena(java.util.UUID uuid, String arena) {
        playerArenas.put(uuid, arena);
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

    public void removeExp(java.util.UUID uuid, int amount) {
        int current = getExp(uuid);
        playerExp.put(uuid, Math.max(0, current - amount));
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
        int overchargeCost = plugin.getConfig().getInt("spells.overcharge.cost", 250);
        int overchargeDur = plugin.getConfig().getInt("spells.overcharge.duration", 10);
        java.util.List<String> overchargeLore = new java.util.ArrayList<>();
        overchargeLore.add(ChatColor.GRAY + "Increases tower attack speeds on your track by 50%.");
        overchargeLore.add(ChatColor.GRAY + "Duration: " + overchargeDur + " seconds");
        overchargeLore.add(ChatColor.GOLD + "Cost: " + ChatColor.YELLOW + overchargeCost + " Gold");
        overchargeLore.add("");
        overchargeLore.add(ChatColor.YELLOW + "Click to cast on your track!");
        gui.setItem(12, createCustomGUIItem(Material.EMERALD_BLOCK, ChatColor.GREEN + "" + ChatColor.BOLD + "Spell: Overcharge", overchargeLore));

        int freezeCost = plugin.getConfig().getInt("spells.freeze.cost", 200);
        int freezeDur = plugin.getConfig().getInt("spells.freeze.duration", 10);
        double freezeSlow = plugin.getConfig().getDouble("spells.freeze.slow-multiplier", 0.4);
        int freezePct = (int) Math.round((1.0 - freezeSlow) * 100.0);
        java.util.List<String> freezeLore = new java.util.ArrayList<>();
        freezeLore.add(ChatColor.GRAY + "Slows down mobs traversing your track by " + freezePct + "%.");
        freezeLore.add(ChatColor.GRAY + "(Does not affect slow-immune mobs)");
        freezeLore.add(ChatColor.GRAY + "Duration: " + freezeDur + " seconds");
        freezeLore.add(ChatColor.GOLD + "Cost: " + ChatColor.YELLOW + freezeCost + " Gold");
        freezeLore.add("");
        freezeLore.add(ChatColor.YELLOW + "Click to cast on your track!");
        gui.setItem(13, createCustomGUIItem(Material.SNOW_BLOCK, ChatColor.AQUA + "" + ChatColor.BOLD + "Spell: Freeze", freezeLore));

        int stormCost = plugin.getConfig().getInt("spells.damage-storm.cost", 300);
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
        int hasteCost = plugin.getConfig().getInt("spells.haste-rush.cost", 200);
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
 
        int empCost = plugin.getConfig().getInt("spells.tower-emp.cost", 250);
        int empDur = plugin.getConfig().getInt("spells.tower-emp.duration", 6);
        java.util.List<String> empLore = new java.util.ArrayList<>();
        empLore.add(ChatColor.GRAY + "Disables a random tower on the opponent's");
        empLore.add(ChatColor.GRAY + "track, stopping its attacks completely.");
        empLore.add(ChatColor.GRAY + "Duration: " + empDur + " seconds");
        empLore.add(ChatColor.GOLD + "Cost: " + ChatColor.YELLOW + empCost + " Gold");
        empLore.add("");
        empLore.add(ChatColor.RED + "Click to cast on OPPONENT'S track!");
        gui.setItem(22, createCustomGUIItem(Material.REDSTONE_BLOCK, ChatColor.RED + "" + ChatColor.BOLD + "Sabotage: Tower EMP", empLore));
 
        int shieldCost = plugin.getConfig().getInt("spells.slow-shield.cost", 150);
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
        
        lobbyQueueSecondsLeft = 30;
        
        for (java.util.UUID uuid : matchQueue) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) {
                p.sendMessage(ChatColor.GREEN + "Match found! Transporting to game in 30 seconds.");
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