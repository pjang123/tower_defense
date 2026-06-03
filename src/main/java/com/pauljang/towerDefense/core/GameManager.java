package com.pauljang.towerDefense.core;

import com.pauljang.towerDefense.TowerDefense;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;

public class GameManager {

    private final TowerDefense plugin;
    private GameState currentState = null;

    private final int maxCastleHealth = 20;
    private int currentCastleHealth = 20;
    private BossBar castleBossBar = null;
    
    private final java.util.Map<java.util.UUID, Integer> playerGold = new java.util.HashMap<>();
    private final java.util.Map<java.util.UUID, Integer> playerExp = new java.util.HashMap<>();
    private final java.util.Map<java.util.UUID, Integer> goldGenLevels = new java.util.HashMap<>();
    private final java.util.Map<java.util.UUID, Integer> swordLevels = new java.util.HashMap<>();
    private final java.util.Map<java.util.UUID, Integer> bowLevels = new java.util.HashMap<>();

    public GameManager(TowerDefense plugin) {
        this.plugin = plugin;
        
        // Start game loop (every 1 second) for passive gold income and HUD updates
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            boolean isActive = (currentState == GameState.ACTIVE);
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (isActive) {
                    int level = getGoldGenLevel(player.getUniqueId());
                    int amount = switch (level) {
                        case 1 -> 5;
                        case 2 -> 20;
                        case 3 -> 50;
                        case 4 -> 100;
                        default -> 5;
                    };
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
        return currentCastleHealth;
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
        currentCastleHealth = maxCastleHealth;
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
        currentCastleHealth = maxCastleHealth;
        showBossBar();
        Bukkit.broadcastMessage(ChatColor.GREEN + "[Tower Defense] The game has begun! Defend the castle!");
        for (Player player : Bukkit.getOnlinePlayers()) {
            giveStarterWeapons(player);
            player.playSound(player.getLocation(), Sound.EVENT_RAID_HORN, 1.0f, 1.0f);
        }
    }

    private void handleGameEnd() {
        cleanupBossBar();
        plugin.getMobManager().cleanup();

        if (currentCastleHealth <= 0) {
            Bukkit.broadcastMessage(ChatColor.RED + "" + ChatColor.BOLD + "========================================");
            Bukkit.broadcastMessage(ChatColor.RED + "" + ChatColor.BOLD + " DEFEAT! The castle was destroyed!");
            Bukkit.broadcastMessage(ChatColor.RED + "" + ChatColor.BOLD + "========================================");
            for (Player player : Bukkit.getOnlinePlayers()) {
                player.playSound(player.getLocation(), Sound.ENTITY_WITHER_DEATH, 0.8f, 0.8f);
                player.sendTitle(ChatColor.RED + "DEFEAT", ChatColor.GRAY + "The castle was overrun", 10, 70, 20);
            }
        } else {
            Bukkit.broadcastMessage(ChatColor.GREEN + "" + ChatColor.BOLD + "========================================");
            Bukkit.broadcastMessage(ChatColor.GREEN + "" + ChatColor.BOLD + " VICTORY! The castle survived!");
            Bukkit.broadcastMessage(ChatColor.GREEN + "" + ChatColor.BOLD + "========================================");
            for (Player player : Bukkit.getOnlinePlayers()) {
                player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
                player.sendTitle(ChatColor.GREEN + "VICTORY", ChatColor.GRAY + "The castle survived the assault!", 10, 70, 20);
            }
        }
    }

    public void damageCastle(int amount) {
        if (currentState != GameState.ACTIVE) return;

        currentCastleHealth = Math.max(0, currentCastleHealth - amount);
        updateBossBar();

        for (Player player : Bukkit.getOnlinePlayers()) {
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.5f);
            player.sendActionBar(ChatColor.RED + "⚠ Castle Damaged! Health: " + currentCastleHealth + "/" + maxCastleHealth + " ⚠");
        }

        if (currentCastleHealth <= 0) {
            setGameState(GameState.ENDED);
        }
    }

    public void showBossBar() {
        if (castleBossBar == null) {
            castleBossBar = Bukkit.createBossBar(
                ChatColor.RED + "Castle Health: " + currentCastleHealth + "/" + maxCastleHealth,
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
        castleBossBar.setTitle(ChatColor.RED + "Castle Health: " + currentCastleHealth + "/" + maxCastleHealth);
        double progress = (double) currentCastleHealth / maxCastleHealth;
        castleBossBar.setProgress(Math.max(0.0, Math.min(1.0, progress)));
    }

    public void cleanupBossBar() {
        if (castleBossBar != null) {
            castleBossBar.removeAll();
            castleBossBar = null;
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
        lines.add(ChatColor.RED + "Castle Health: " + ChatColor.WHITE + currentCastleHealth + "/" + maxCastleHealth);
        lines.add(" ");
        lines.add(ChatColor.GOLD + "Your Gold: " + ChatColor.YELLOW + "$" + getGold(player.getUniqueId()));
        lines.add(ChatColor.GREEN + "Your EXP: " + ChatColor.LIGHT_PURPLE + getExp(player.getUniqueId()) + " XP");
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
                    meta.addEnchant(org.bukkit.enchantments.Enchantment.MULTISHOT, 1, true);
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
        bowLore.add(ChatColor.YELLOW + "Current Weapon: " + ChatColor.WHITE + (bLvl == 1 ? "Bow" : bLvl == 2 ? "Crossbow (Quick Draw)" : "Crossbow (Quick Draw + Multishot + Fireworks)"));
        bowLore.add("");
        if (bLvl < 3) {
            int nextCost = bLvl == 1 ? 150 : 450;
            String nextWeapon = bLvl == 1 ? "Crossbow (Quick Draw)" : "Crossbow (Quick Draw + Multishot + Fireworks)";
            bowLore.add(ChatColor.GREEN + "Upgrade to " + nextWeapon + ":");
            bowLore.add(ChatColor.GOLD + " - Cost: " + ChatColor.YELLOW + nextCost + " TD EXP");
            bowLore.add("");
            bowLore.add(ChatColor.YELLOW + "Click to upgrade!");
        } else {
            bowLore.add(ChatColor.RED + "MAX LEVEL REACHED");
        }
        gui.setItem(11, createCustomGUIItem(bowMat, bowName, bowLore));

        // 4. Potions (Slot 15, 16, 17)
        java.util.List<String> healLore = new java.util.ArrayList<>();
        healLore.add(ChatColor.GRAY + "Heals splash area instantly.");
        healLore.add(ChatColor.GOLD + "Cost: " + ChatColor.YELLOW + "30 TD EXP");
        healLore.add("");
        healLore.add(ChatColor.YELLOW + "Click to purchase Splash Potion of Healing II!");
        gui.setItem(15, createPotionGUIItem("HEALING", "INSTANT_HEAL", ChatColor.RED + "Healing Potion II", healLore));

        java.util.List<String> slowLore = new java.util.ArrayList<>();
        slowLore.add(ChatColor.GRAY + "Slows down target area mobs.");
        slowLore.add(ChatColor.GOLD + "Cost: " + ChatColor.YELLOW + "45 TD EXP");
        slowLore.add("");
        slowLore.add(ChatColor.YELLOW + "Click to purchase Splash Potion of Slowness II!");
        gui.setItem(16, createPotionGUIItem("SLOWNESS", "SLOW", ChatColor.BLUE + "Slowness Splash Potion", slowLore));

        java.util.List<String> harmLore = new java.util.ArrayList<>();
        harmLore.add(ChatColor.GRAY + "Deals instant damage to target area mobs.");
        harmLore.add(ChatColor.GOLD + "Cost: " + ChatColor.YELLOW + "50 TD EXP");
        harmLore.add("");
        harmLore.add(ChatColor.YELLOW + "Click to purchase Splash Potion of Harming II!");
        gui.setItem(17, createPotionGUIItem("HARMING", "INSTANT_DAMAGE", ChatColor.DARK_PURPLE + "Harming Splash Potion II", harmLore));

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