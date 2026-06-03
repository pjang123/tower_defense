package com.pauljang.towerDefense.listeners;

import com.pauljang.towerDefense.TowerDefense;
import com.pauljang.towerDefense.data.PlotConfigManager;
import com.pauljang.towerDefense.data.WaypointConfigManager;
import com.pauljang.towerDefense.setup.SetupManager;
import com.pauljang.towerDefense.setup.SetupManager.SetupState;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import java.util.UUID;

public class WandListener implements Listener {

    private final TowerDefense plugin;

    public WandListener(TowerDefense plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onWandClick(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItem();

        if (item == null || item.getType() != Material.BLAZE_ROD || !item.hasItemMeta()) return;
        
        org.bukkit.inventory.meta.ItemMeta meta = item.getItemMeta();
        if (!meta.hasDisplayName() || !meta.getDisplayName().contains("TD Setup Wand")) return;

        event.setCancelled(true);
        if (event.getClickedBlock() == null) return;

        Action action = event.getAction();
        Location clickedLoc = event.getClickedBlock().getLocation();
        UUID uuid = player.getUniqueId();
        SetupManager setupManager = plugin.getSetupManager();
        SetupState state = setupManager.getState(uuid);

        // --- The Interactive Flow ---

        if (state == SetupState.AWAITING_PLOT_P1) {
            // We only want them to left-click for Corner 1
            if (action == Action.LEFT_CLICK_BLOCK) {
                setupManager.setPos1(uuid, clickedLoc);
                player.sendMessage(ChatColor.GREEN + "Corner 1 saved at " + clickedLoc.getBlockX() + ", " + clickedLoc.getBlockY() + ", " + clickedLoc.getBlockZ());

                // Move them to the next step
                setupManager.setState(uuid, SetupState.AWAITING_PLOT_P2);
                player.sendMessage(ChatColor.YELLOW + "Now, please RIGHT-CLICK the opposite corner of the plot.");
            } else {
                player.sendMessage(ChatColor.RED + "Please LEFT-CLICK to select the first corner!");
            }
        }

        else if (state == SetupState.AWAITING_PLOT_P2) {
            if (action == Action.RIGHT_CLICK_BLOCK) {
                Location pos1 = setupManager.getPos1(uuid);
                
                if (pos1 == null) {
                    player.sendMessage(ChatColor.RED + "Error: Corner 1 is missing! Restarting setup...");
                    setupManager.setState(uuid, SetupState.AWAITING_PLOT_P1);
                    return;
                }

                if (!pos1.getWorld().equals(clickedLoc.getWorld())) {
                    player.sendMessage(ChatColor.RED + "Error: Both corners must be in the same world!");
                    return;
                }

                // Calculate the actual block dimensions (Difference + 1)
                int sizeX = Math.abs(pos1.getBlockX() - clickedLoc.getBlockX()) + 1;
                int sizeZ = Math.abs(pos1.getBlockZ() - clickedLoc.getBlockZ()) + 1;
                int diffY = Math.abs(pos1.getBlockY() - clickedLoc.getBlockY());

                // 1. Force the plot to be flat
                if (diffY != 0) {
                    player.sendMessage(ChatColor.RED + "Error: Both corners must be on the exact same Y-level!");
                    player.sendMessage(ChatColor.YELLOW + "Please RIGHT-CLICK a valid block to fix Corner 2.");
                    return; // Stop the code here, let them try again
                }

                // 2. Force the plot to be 3x3 or 5x5
                boolean is3x3 = (sizeX == 3 && sizeZ == 3);
                boolean is5x5 = (sizeX == 5 && sizeZ == 5);

                if (!is3x3 && !is5x5) {
                    player.sendMessage(ChatColor.RED + "Error: Plots must be exactly 3x3 or 5x5! You selected " + sizeX + "x" + sizeZ + ".");
                    player.sendMessage(ChatColor.YELLOW + "Please RIGHT-CLICK a valid block to fix Corner 2.");
                    return; // Stop the code here, let them try again
                }

                // 3. Check for overlapping plots
                if (plugin.getPlotConfigManager().isPlotOverlapping(pos1, clickedLoc)) {
                    player.sendMessage(ChatColor.RED + "Error: This plot overlaps with an existing one!");
                    player.sendMessage(ChatColor.YELLOW + "Please RIGHT-CLICK a different block for Corner 2.");
                    return;
                }

                // 4. If it passes all checks, save it!
                setupManager.setPos2(uuid, clickedLoc);
                player.sendMessage(ChatColor.GREEN + "Corner 2 saved at " + clickedLoc.getBlockX() + ", " + clickedLoc.getBlockY() + ", " + clickedLoc.getBlockZ());

                String arena = setupManager.getEditingArena(uuid);
                plugin.getPlotConfigManager().savePlot(arena, pos1, clickedLoc);
                player.sendMessage(ChatColor.AQUA + "Valid " + sizeX + "x" + sizeZ + " plot finalized and saved for Arena " + arena + "!");

                setupManager.setState(uuid, SetupState.AWAITING_PLOT_P1);
                player.sendMessage(ChatColor.YELLOW + "Ready for next plot: LEFT-CLICK the first corner, or type /td plotmode to exit.");
            } else if (action == Action.LEFT_CLICK_BLOCK) {
                // Allow them to re-select Corner 1 if they LEFT-CLICK again
                setupManager.setPos1(uuid, clickedLoc);
                player.sendMessage(ChatColor.GREEN + "Corner 1 UPDATED at " + clickedLoc.getBlockX() + ", " + clickedLoc.getBlockY() + ", " + clickedLoc.getBlockZ());
                player.sendMessage(ChatColor.YELLOW + "Now, please RIGHT-CLICK the opposite corner of the plot.");
            }
        }

        else if (state == SetupState.WAYPOINT_MODE) {
            if (action == Action.LEFT_CLICK_BLOCK) {
                // We add 0.5 to X and Z so the mob walks to the exact center of the block,
                // and 1.0 to Y so they walk ON TOP of the block, not inside it!
                Location wpLoc = clickedLoc.clone().add(0.5, 1.0, 0.5);

                String arena = setupManager.getEditingArena(uuid);
                plugin.getWaypointConfigManager().addWaypoint(arena, wpLoc);
                player.sendMessage(ChatColor.LIGHT_PURPLE + "Waypoint added for Arena " + arena + " at " + wpLoc.getBlockX() + ", " + wpLoc.getBlockY() + ", " + wpLoc.getBlockZ());
            }
        }

        else {
            // They clicked with the wand, but didn't run /td saveplot first
            player.sendMessage(ChatColor.RED + "You must type /td saveplot before selecting corners!");
        }
    }

    @EventHandler
    public void onPlayerPlaceTower(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getClickedBlock() == null) return;

        // If they are holding the wand, skip placement triggers
        ItemStack item = event.getItem();
        if (item != null && item.getType() == Material.BLAZE_ROD && item.hasItemMeta()) {
            org.bukkit.inventory.meta.ItemMeta meta = item.getItemMeta();
            if (meta != null && meta.hasDisplayName() && meta.getDisplayName().contains("TD Setup Wand")) {
                return;
            }
        }

        Location clickedLoc = event.getClickedBlock().getLocation();
        String plotId = plugin.getPlotConfigManager().getPlotAt(clickedLoc);

        if (plotId != null) {
            event.setCancelled(true);
            String plotArena = plugin.getPlotConfigManager().getPlotArena(plotId);
            String playerArena = plugin.getGameManager().getPlayerArena(player.getUniqueId());
            if (!plotArena.equals(playerArena)) {
                player.sendMessage(ChatColor.RED + "You cannot build on the opponent's plots!");
                player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 0.8f, 1.0f);
                return;
            }
            if (plugin.getTowerManager().hasTower(plotId)) {
                plugin.getTowerManager().openManageTowerGUI(player, plotId);
            } else {
                plugin.getTowerManager().openBuyTowerGUI(player, plotId);
            }
        }
    }

    private ItemStack createGUIItem(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material);
        org.bukkit.inventory.meta.ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            meta.setLore(java.util.Arrays.asList(lore));
            item.setItemMeta(meta);
        }
        return item;
    }
}
