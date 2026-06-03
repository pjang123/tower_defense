package com.pauljang.towerDefense.core;

import com.pauljang.towerDefense.TowerDefense;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
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

    public GameManager(TowerDefense plugin) {
        this.plugin = plugin;
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
}