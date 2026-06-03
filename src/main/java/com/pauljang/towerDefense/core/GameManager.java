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

public class GameManager {

    private final TowerDefense plugin;
    private GameState currentState = null;

    private final int maxCastleHealth = 20;
    private final java.util.Map<String, Integer> arenaHealth = new java.util.HashMap<>();
    private final java.util.Map<String, java.util.Map<String, Long>> activeSpells = new java.util.HashMap<>();
    private final java.util.Map<org.bukkit.Location, org.bukkit.Material> originalFloorBlocks = new java.util.HashMap<>();
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

    public GameManager(TowerDefense plugin) {
        this.plugin = plugin;
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
                if (player.getInventory().contains(Material.BOW) || player.getInventory().contains(Material.CROSSBOW)) {
                    if (!player.getInventory().contains(Material.ARROW)) {
                        player.getInventory().addItem(new org.bukkit.inventory.ItemStack(Material.ARROW));
                    }
                }
            }
        }, 0L, 20L);
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
    }

    private void handleLobbySetup() {
        cleanupBossBar();
        arenaHealth.put("1", maxCastleHealth);
        arenaHealth.put("2", maxCastleHealth);
    }

    private void handleCountdown() {
        cleanupBossBar();
        Bukkit.broadcastMessage(ChatColor.GOLD + "[Tower Defense] Game starting in 10 seconds!");
        // We'll auto-start after 10 seconds for testing
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (currentState == GameState.STARTING) {
                setGameState(GameState.ACTIVE);
            }
        }, 200L); // 10 seconds (20 ticks = 1 sec)
    }

    private void handleGameStart() {
        if (!arenaHealth.containsKey("1")) arenaHealth.put("1", maxCastleHealth);
        if (!arenaHealth.containsKey("2")) arenaHealth.put("2", maxCastleHealth);
        showBossBar();
        Bukkit.broadcastMessage(ChatColor.GREEN + "[Tower Defense] The game has begun! Defend your castles!");
        for (Player player : Bukkit.getOnlinePlayers()) {
            giveStarterWeapons(player);
            player.playSound(player.getLocation(), Sound.EVENT_RAID_HORN, 1.0f, 1.0f);
        }
    }

    private void handleGameEnd() {
        cleanupBossBar();
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

        for (Player player : Bukkit.getOnlinePlayers()) {
            player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
            player.sendTitle(titleMsg, subtitleMsg, 10, 70, 20);
            
            // Teleport back to spawn
            player.teleport(player.getWorld().getSpawnLocation());
            // Clear items
            player.getInventory().clear();
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

        Material blockMat = switch (spell) {
            case "OVERCHARGE" -> Material.EMERALD_BLOCK;
            case "FREEZE" -> Material.SNOW_BLOCK;
            case "DAMAGE_STORM" -> Material.MAGMA_BLOCK;
            default -> null;
        };

        if (blockMat != null) {
            java.util.List<Location> waypoints = plugin.getWaypointConfigManager().getWaypoints(arena);
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

        // Teleport
        teleportToArenaStart(p1, "1");
        teleportToArenaStart(p2, "2");

        // Start match state
        setGameState(GameState.STARTING);
    }

    private void resetPlayerForMatch(Player player, String arena) {
        java.util.UUID uuid = player.getUniqueId();
        playerGold.put(uuid, 150);
        playerExp.put(uuid, 0);
        goldGenLevels.put(uuid, 1);
        swordLevels.put(uuid, 1);
        bowLevels.put(uuid, 1);
        setPlayerArena(uuid, arena);
        player.getInventory().clear();
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
        return playerGold.getOrDefault(uuid, 150); // Default start gold: 150
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
        
        // Give 1 arrow for ammo bypass
        if (!player.getInventory().contains(Material.ARROW)) {
            player.getInventory().addItem(new org.bukkit.inventory.ItemStack(Material.ARROW));
        }

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
        genLore.add(ChatColor.YELLOW + "Current Rate: " + ChatColor.WHITE + (goldLvl == 1 ? "5" : goldLvl == 2 ? "20" : goldLvl == 3 ? "50" : "100") + " Gold/sec");
        genLore.add("");
        if (goldLvl < 4) {
            int nextCost = goldLvl == 1 ? 100 : goldLvl == 2 ? 300 : 600;
            int nextRate = goldLvl == 1 ? 20 : goldLvl == 2 ? 50 : 100;
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
            int nextCost = sLvl == 1 ? 80 : sLvl == 2 ? 200 : sLvl == 3 ? 450 : 900;
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
            int nextCost = bLvl == 1 ? 150 : 450;
            String nextWeapon = bLvl == 1 ? "Crossbow (Quick Charge I)" : "Crossbow (Quick Charge III + Piercing IV)";
            bowLore.add(ChatColor.GREEN + "Upgrade to " + nextWeapon + ":");
            bowLore.add(ChatColor.GOLD + " - Cost: " + ChatColor.YELLOW + nextCost + " TD EXP");
            bowLore.add("");
            bowLore.add(ChatColor.YELLOW + "Click to upgrade!");
        } else {
            bowLore.add(ChatColor.RED + "MAX LEVEL REACHED");
        }
        gui.setItem(11, createCustomGUIItem(bowMat, bowName, bowLore));

        // 4. Spells (Slot 14, 15, 16)
        java.util.List<String> overchargeLore = new java.util.ArrayList<>();
        overchargeLore.add(ChatColor.GRAY + "Increases tower attack speeds on your track by 50%.");
        overchargeLore.add(ChatColor.GRAY + "Duration: 10 seconds");
        overchargeLore.add(ChatColor.GOLD + "Cost: " + ChatColor.YELLOW + "250 Gold");
        overchargeLore.add("");
        overchargeLore.add(ChatColor.YELLOW + "Click to cast on your track!");
        gui.setItem(14, createCustomGUIItem(Material.EMERALD_BLOCK, ChatColor.GREEN + "" + ChatColor.BOLD + "Spell: Overcharge", overchargeLore));

        java.util.List<String> freezeLore = new java.util.ArrayList<>();
        freezeLore.add(ChatColor.GRAY + "Slows down mobs traversing your track.");
        freezeLore.add(ChatColor.GRAY + "(Does not affect slow-immune mobs)");
        freezeLore.add(ChatColor.GRAY + "Duration: 10 seconds");
        freezeLore.add(ChatColor.GOLD + "Cost: " + ChatColor.YELLOW + "200 Gold");
        freezeLore.add("");
        freezeLore.add(ChatColor.YELLOW + "Click to cast on your track!");
        gui.setItem(15, createCustomGUIItem(Material.SNOW_BLOCK, ChatColor.AQUA + "" + ChatColor.BOLD + "Spell: Freeze", freezeLore));

        java.util.List<String> stormLore = new java.util.ArrayList<>();
        stormLore.add(ChatColor.GRAY + "Deals periodic damage to mobs traversing your track.");
        stormLore.add(ChatColor.GRAY + "Duration: 10 seconds");
        stormLore.add(ChatColor.GOLD + "Cost: " + ChatColor.YELLOW + "300 Gold");
        stormLore.add("");
        stormLore.add(ChatColor.YELLOW + "Click to cast on your track!");
        gui.setItem(16, createCustomGUIItem(Material.MAGMA_BLOCK, ChatColor.RED + "" + ChatColor.BOLD + "Spell: Damage Storm", stormLore));

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
}