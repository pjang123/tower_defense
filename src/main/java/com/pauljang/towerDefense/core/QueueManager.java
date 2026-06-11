package com.pauljang.towerDefense.core;

import com.pauljang.towerDefense.TowerDefense;
import com.pauljang.towerDefense.data.MapManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

public class QueueManager {
    private final TowerDefense plugin;
    private final List<UUID> singlePlayerQueue = new ArrayList<>();
    private final List<UUID> multiPlayerQueue = new ArrayList<>();
    
    private final Map<UUID, VotingSession> activeVotes = new HashMap<>();

    public QueueManager(TowerDefense plugin) {
        this.plugin = plugin;
    }

    public void toggleQueue(Player player, boolean singlePlayer) {
        UUID uuid = player.getUniqueId();
        if (singlePlayer) {
            if (singlePlayerQueue.remove(uuid)) {
                player.sendMessage(ChatColor.YELLOW + "You left the Single Player queue.");
            } else {
                multiPlayerQueue.remove(uuid);
                singlePlayerQueue.add(uuid);
                player.sendMessage(ChatColor.GREEN + "You joined the Single Player queue!");
                plugin.getLogger().info(player.getName() + " joined Single Player queue. Queue size: " + singlePlayerQueue.size());
                checkQueues();
            }
        } else {
            if (multiPlayerQueue.remove(uuid)) {
                player.sendMessage(ChatColor.YELLOW + "You left the Multiplayer queue.");
            } else {
                singlePlayerQueue.remove(uuid);
                multiPlayerQueue.add(uuid);
                int required = plugin.getConfig().getInt("game.players-per-match", 2);
                player.sendMessage(ChatColor.GREEN + "You joined the Multiplayer queue! (" + multiPlayerQueue.size() + "/" + required + ")");
                plugin.getLogger().info(player.getName() + " joined Multiplayer queue. Queue size: " + multiPlayerQueue.size() + "/" + required);
                checkQueues();
            }
        }
    }

    private void checkQueues() {
        if (!singlePlayerQueue.isEmpty()) {
            startVoting(Collections.singletonList(singlePlayerQueue.remove(0)), true);
        }
        
        int required = plugin.getConfig().getInt("game.players-per-match", 2);
        if (multiPlayerQueue.size() >= required) {
            List<UUID> players = new ArrayList<>();
            for (int i = 0; i < required; i++) {
                players.add(multiPlayerQueue.remove(0));
            }
            startVoting(players, false);
        }
    }

    private void startVoting(List<UUID> playerIds, boolean singlePlayer) {
        String mode = singlePlayer ? "Single Player" : "Multiplayer";
        plugin.getLogger().info("Starting voting for " + mode + " mode with " + playerIds.size() + " players");

        List<MapManager.MapData> allMaps = plugin.getMapManager().getAvailableMaps(singlePlayer);
        plugin.getLogger().info("Found " + allMaps.size() + " " + mode + " maps available");

        if (allMaps.isEmpty()) {
            plugin.getLogger().warning("No " + mode + " maps found! Check GAME_WORLD_TEMPLATES/" + (singlePlayer ? "SINGLE_PLAYER" : "MULTI_PLAYER") + "/");
            for (UUID id : playerIds) {
                Player p = Bukkit.getPlayer(id);
                if (p != null) p.sendMessage(ChatColor.RED + "No maps available for this mode!");
            }
            return;
        }

        // Pick 3 random maps
        Collections.shuffle(allMaps);
        List<MapManager.MapData> options = allMaps.subList(0, Math.min(3, allMaps.size()));
        
        VotingSession session = new VotingSession(playerIds, options, singlePlayer);
        for (UUID id : playerIds) {
            activeVotes.put(id, session);
            Player p = Bukkit.getPlayer(id);
            if (p != null) session.openGUI(p);
        }
        
        session.startTimer();
    }

    public VotingSession getSession(UUID uuid) {
        return activeVotes.get(uuid);
    }

    public class VotingSession {
        private final List<UUID> participants;
        private final List<MapManager.MapData> options;
        private final boolean singlePlayer;
        private final Map<UUID, Integer> votes = new HashMap<>();
        private int timeLeft = 15;

        public VotingSession(List<UUID> participants, List<MapManager.MapData> options, boolean singlePlayer) {
            this.participants = participants;
            this.options = options;
            this.singlePlayer = singlePlayer;
        }

        public void castVote(UUID uuid, int mapIndex) {
            votes.put(uuid, mapIndex);
            for (UUID id : participants) {
                Player p = Bukkit.getPlayer(id);
                if (p != null) openGUI(p);
            }
        }

        public void openGUI(Player player) {
            Inventory gui = Bukkit.createInventory(null, 27, ChatColor.BLUE + "Vote for a Map! (" + timeLeft + "s)");

            for (int i = 0; i < options.size(); i++) {
                MapManager.MapData map = options.get(i);
                int mapVotes = 0;
                for (Integer v : votes.values()) if (v == i) mapVotes++;
                
                ItemStack item = new ItemStack(Material.PAPER);
                ItemMeta meta = item.getItemMeta();
                meta.setDisplayName(ChatColor.YELLOW + map.getDisplayName());
                meta.setLore(Arrays.asList(
                    ChatColor.GRAY + "Author: " + map.getAuthor(),
                    ChatColor.GREEN + "Votes: " + mapVotes
                ));
                item.setItemMeta(meta);
                gui.setItem(11 + i, item);
            }
            
            player.openInventory(gui);
        }

        public void startTimer() {
            new BukkitRunnable() {
                @Override
                public void run() {
                    timeLeft--;
                    if (timeLeft <= 0) {
                        cancel();
                        finishVoting();
                        return;
                    }
                    for (UUID id : participants) {
                        Player p = Bukkit.getPlayer(id);
                        if (p != null && p.getOpenInventory().getTitle().contains("Vote")) {
                            openGUI(p);
                        }
                    }
                }
            }.runTaskTimer(plugin, 20L, 20L);
        }

        private void finishVoting() {
            int[] voteCounts = new int[options.size()];
            for (Integer v : votes.values()) voteCounts[v]++;

            int winnerIdx = 0;
            for (int i = 1; i < voteCounts.length; i++) {
                if (voteCounts[i] > voteCounts[winnerIdx]) winnerIdx = i;
            }

            MapManager.MapData winner = options.get(winnerIdx);
            for (UUID id : participants) {
                activeVotes.remove(id);
                Player p = Bukkit.getPlayer(id);
                if (p != null) {
                    p.closeInventory();
                    p.sendMessage(ChatColor.GREEN + "Map selected: " + winner.getDisplayName());
                }
            }

            List<UUID> playerList = new ArrayList<>(participants);
            plugin.getGameManager().startMatch(playerList, winner);
        }
    }
}
