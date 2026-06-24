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
    // Single-player players who have won a map vote and are picking a difficulty before the match starts.
    private final Map<UUID, MapManager.MapData> pendingDifficulty = new HashMap<>();

    public QueueManager(TowerDefense plugin) {
        this.plugin = plugin;
    }

    public void toggleQueue(Player player, boolean singlePlayer) {
        UUID uuid = player.getUniqueId();

        // Once map voting has begun the player has been pulled out of the queue and is about to be
        // teleported into a match world — don't let them re-queue and end up double-booked.
        if (activeVotes.containsKey(uuid)) {
            player.sendMessage(ChatColor.RED + "You're already being placed into a game! Please wait for the map vote to finish.");
            return;
        }
        if (plugin.getGameManager().getPlayerMatch(uuid) != null) {
            player.sendMessage(ChatColor.RED + "You're already in a game. Use /td forfeit to leave first.");
            return;
        }

        if (singlePlayer) {
            if (singlePlayerQueue.remove(uuid)) {
                player.sendMessage(ChatColor.YELLOW + "You left the Single Player queue.");
            } else {
                multiPlayerQueue.remove(uuid);
                removeVoteItem(player); // leaving MP for SP: drop the MP vote item
                singlePlayerQueue.add(uuid);
                player.sendMessage(ChatColor.GREEN + "You joined the Single Player queue!");
                plugin.getLogger().info(player.getName() + " joined Single Player queue. Queue size: " + singlePlayerQueue.size());
                checkQueues();
            }
        } else {
            if (multiPlayerQueue.remove(uuid)) {
                removeVoteItem(player);
                player.sendMessage(ChatColor.YELLOW + "You left the Multiplayer queue.");
            } else {
                singlePlayerQueue.remove(uuid);
                multiPlayerQueue.add(uuid);
                giveVoteItem(player);
                int required = plugin.getConfig().getInt("game.players-per-match", 2);
                player.sendMessage(ChatColor.GREEN + "You joined the Multiplayer queue! (" + multiPlayerQueue.size() + "/" + required + ")");
                plugin.getLogger().info(player.getName() + " joined Multiplayer queue. Queue size: " + multiPlayerQueue.size() + "/" + required);
                checkQueues();
            }
        }
    }

    /** Display name + identity of the queued-player "Map Vote" paper item. */
    public static final String VOTE_ITEM_NAME = ChatColor.AQUA + "" + ChatColor.BOLD + "Map Vote";

    public boolean isVoteItem(ItemStack item) {
        return item != null && item.hasItemMeta()
                && item.getItemMeta().getPersistentDataContainer().has(
                        com.pauljang.towerDefense.TDKeys.QUEUE_VOTE_ITEM,
                        org.bukkit.persistence.PersistentDataType.BYTE);
    }

    /** Gives the MP-queue player a non-droppable paper that opens the map-vote panel on right-click. */
    public void giveVoteItem(Player player) {
        removeVoteItem(player); // avoid duplicates
        ItemStack paper = new ItemStack(Material.PAPER);
        ItemMeta meta = paper.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(VOTE_ITEM_NAME);
            meta.setLore(Arrays.asList(
                    ChatColor.GRAY + "Right-click to open the map vote.",
                    ChatColor.DARK_GRAY + "Given while you're in the multiplayer queue."));
            meta.getPersistentDataContainer().set(
                    com.pauljang.towerDefense.TDKeys.QUEUE_VOTE_ITEM,
                    org.bukkit.persistence.PersistentDataType.BYTE, (byte) 1);
            paper.setItemMeta(meta);
        }
        player.getInventory().setItem(0, paper);
    }

    /** Strips any Map Vote item from the player (on leaving the queue / entering a match). */
    public void removeVoteItem(Player player) {
        org.bukkit.inventory.PlayerInventory inv = player.getInventory();
        for (int i = 0; i < inv.getSize(); i++) {
            if (isVoteItem(inv.getItem(i))) inv.setItem(i, null);
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

        // Show ALL maps for this mode in a consistent (already-sorted) order, capped to what the panel
        // can display, rather than 3 random picks.
        List<MapManager.MapData> options = new ArrayList<>(allMaps);
        if (options.size() > VotingSession.MAX_OPTIONS) {
            options = new ArrayList<>(options.subList(0, VotingSession.MAX_OPTIONS));
        }

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

    /**
     * Opens the single-player difficulty picker for {@code player} after they've settled on a map.
     * The chosen map is held in {@link #pendingDifficulty} until they click a difficulty in
     * {@link #chooseDifficulty}, which actually starts the match.
     */
    public void openDifficultyGUI(Player player, MapManager.MapData map) {
        pendingDifficulty.put(player.getUniqueId(), map);

        com.pauljang.towerDefense.ui.TDMenuHolder holder =
                new com.pauljang.towerDefense.ui.TDMenuHolder(com.pauljang.towerDefense.ui.TDMenuHolder.MenuType.CHOOSE_DIFFICULTY);
        Inventory gui = Bukkit.createInventory(holder, 27, ChatColor.DARK_GREEN + "" + ChatColor.BOLD + "Choose Difficulty");
        holder.setInventory(gui);

        ItemStack border = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta borderMeta = border.getItemMeta();
        if (borderMeta != null) { borderMeta.setDisplayName(" "); border.setItemMeta(borderMeta); }
        for (int i = 0; i < 27; i++) gui.setItem(i, border);

        for (Difficulty d : Difficulty.values()) {
            ItemStack item = new ItemStack(d.getIcon());
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(d.getColor() + "" + ChatColor.BOLD + d.getDisplayName());
                meta.setLore(Arrays.asList(
                        ChatColor.GRAY + d.getLoreLine1(),
                        ChatColor.GRAY + d.getLoreLine2(),
                        "",
                        ChatColor.YELLOW + "Click to play on " + d.getColoredName() + ChatColor.YELLOW + "!"));
                item.setItemMeta(meta);
            }
            gui.setItem(d.getGuiSlot(), item);
        }

        player.openInventory(gui);
    }

    /** True while {@code player} is on the difficulty-picker screen with a pending map selection. */
    public boolean isPickingDifficulty(UUID uuid) {
        return pendingDifficulty.containsKey(uuid);
    }

    /** Drops a pending difficulty selection (player escaped the picker) and returns them to the lobby. */
    public void cancelDifficultySelection(Player player) {
        if (pendingDifficulty.remove(player.getUniqueId()) != null) {
            player.sendMessage(ChatColor.YELLOW + "Match cancelled. Click Single Player to queue again.");
        }
    }

    /** Locks in the chosen difficulty and starts the player's single-player match. */
    public void chooseDifficulty(Player player, Difficulty difficulty) {
        MapManager.MapData map = pendingDifficulty.remove(player.getUniqueId());
        if (map == null) return; // stale click (e.g. match already starting)

        player.closeInventory();
        player.sendMessage(ChatColor.GREEN + "Difficulty: " + difficulty.getColoredName());
        plugin.getGameManager().startMatch(
                new ArrayList<>(Collections.singletonList(player.getUniqueId())), map, difficulty);
    }

    /**
     * Admin force-start: immediately begin the multiplayer map vote with whoever is currently queued,
     * bypassing the usual players-per-match wait. Returns false if the multiplayer queue is empty.
     */
    public boolean forceStartMultiplayer() {
        if (multiPlayerQueue.isEmpty()) return false;
        List<UUID> players = new ArrayList<>(multiPlayerQueue);
        multiPlayerQueue.clear();
        startVoting(players, false);
        return true;
    }

    public class VotingSession {
        private final List<UUID> participants;
        private final List<MapManager.MapData> options;
        private final boolean singlePlayer;
        private final Map<UUID, Integer> votes = new HashMap<>();
        private int timeLeft = 15;
        private BukkitRunnable timerTask;
        private boolean finished = false;

        public VotingSession(List<UUID> participants, List<MapManager.MapData> options, boolean singlePlayer) {
            this.participants = participants;
            this.options = options;
            this.singlePlayer = singlePlayer;
        }

        public void castVote(UUID uuid, int mapIndex) {
            votes.put(uuid, mapIndex);
            // Single player: there's no one else to wait for, so skip the countdown and start the match
            // immediately once the lone player has voted.
            if (singlePlayer) {
                if (timerTask != null) timerTask.cancel();
                finishVoting();
                return;
            }
            for (UUID id : participants) {
                Player p = Bukkit.getPlayer(id);
                if (p != null) openGUI(p);
            }
        }

        // The vote panel lays maps into the inner area of a 54-slot inventory (4 rows x 7 columns).
        static final int MAX_OPTIONS = 28;

        /** Inner content slot (skipping the border) for the i-th map option. */
        static int innerSlot(int i) {
            int row = 1 + i / 7;
            int col = 1 + i % 7;
            return row * 9 + col;
        }

        /** Maps a clicked raw slot back to a map option index, or -1 if it isn't a map button. */
        public int slotToOption(int rawSlot) {
            for (int i = 0; i < options.size(); i++) {
                if (innerSlot(i) == rawSlot) return i;
            }
            return -1;
        }

        public void openGUI(Player player) {
            com.pauljang.towerDefense.ui.TDMenuHolder holder =
                    new com.pauljang.towerDefense.ui.TDMenuHolder(com.pauljang.towerDefense.ui.TDMenuHolder.MenuType.VOTE_MAP);
            Inventory gui = Bukkit.createInventory(holder, 54, ChatColor.BLUE + "Vote for a Map! (" + timeLeft + "s)");
            holder.setInventory(gui);

            ItemStack border = new ItemStack(Material.BLUE_STAINED_GLASS_PANE);
            ItemMeta borderMeta = border.getItemMeta();
            if (borderMeta != null) { borderMeta.setDisplayName(" "); border.setItemMeta(borderMeta); }
            for (int i = 0; i < 54; i++) {
                if (i < 9 || i >= 45 || i % 9 == 0 || i % 9 == 8) gui.setItem(i, border);
            }

            for (int i = 0; i < options.size(); i++) {
                MapManager.MapData map = options.get(i);
                int mapVotes = 0;
                for (Integer v : votes.values()) if (v == i) mapVotes++;

                ItemStack item = new ItemStack(Material.PAPER);
                ItemMeta meta = item.getItemMeta();
                meta.setDisplayName(ChatColor.YELLOW + "" + ChatColor.BOLD + map.getDisplayName());
                meta.setLore(Arrays.asList(
                    ChatColor.GRAY + "Author: " + ChatColor.WHITE + map.getAuthor(),
                    ChatColor.GREEN + "Votes: " + ChatColor.WHITE + mapVotes,
                    "",
                    ChatColor.YELLOW + "Click to vote for this map!"
                ));
                item.setItemMeta(meta);
                gui.setItem(innerSlot(i), item);
            }

            player.openInventory(gui);
        }

        public void startTimer() {
            timerTask = new BukkitRunnable() {
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
            };
            timerTask.runTaskTimer(plugin, 20L, 20L);
        }

        private void finishVoting() {
            // Guard against running twice (e.g. a single-player vote firing right as the timer expires).
            if (finished) return;
            finished = true;
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
                    removeVoteItem(p); // entering a match — the queue vote item is no longer needed
                    p.sendMessage(ChatColor.GREEN + "Map selected: " + winner.getDisplayName());
                }
            }

            // Single player: let the lone player pick a difficulty before the match starts. The match
            // is launched from chooseDifficulty once they click. Multiplayer always runs on NORMAL.
            if (singlePlayer) {
                Player p = Bukkit.getPlayer(participants.get(0));
                if (p != null) {
                    openDifficultyGUI(p, winner);
                    return;
                }
            }

            List<UUID> playerList = new ArrayList<>(participants);
            plugin.getGameManager().startMatch(playerList, winner);
        }
    }
}
