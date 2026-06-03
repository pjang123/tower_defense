package com.pauljang.towerDefense.core;

import com.pauljang.towerDefense.TowerDefense;
import com.pauljang.towerDefense.setup.SetupManager;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class TDCommand implements CommandExecutor {

    private final TowerDefense plugin;

    public TDCommand(TowerDefense plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // Ensure a player is sending the command, not the server console
        if (!(sender instanceof Player)) {
            sender.sendMessage("Only players can run this command!");
            return true;
        }

        Player player = (Player) sender;

        // If they just type /td with no arguments
        if (args.length == 0) {
            player.sendMessage(ChatColor.RED + "Usage: /td <start|stop|status|plotmode|wand>");
            return true;
        }

        GameManager gameManager = plugin.getGameManager();

        // Handle the arguments
        switch (args[0].toLowerCase()) {
            case "start":
                gameManager.setGameState(GameState.STARTING);
                player.sendMessage(ChatColor.GREEN + "Forcing game to STARTING state!");
                break;

            case "stop":
                gameManager.setGameState(GameState.ENDED);
                player.sendMessage(ChatColor.RED + "Forcing game to ENDED state!");
                break;

            case "status":
                player.sendMessage(ChatColor.YELLOW + "Current Game State: " + gameManager.getCurrentState().name());
                break;

            case "wand":
                giveWand(player);
                break;

            case "plotmode":
            case "saveplot":
                SetupManager.SetupState state = plugin.getSetupManager().getState(player.getUniqueId());

                // If they are IDLE, turn the mode ON
                if (state == SetupManager.SetupState.IDLE) {
                    if (!player.getInventory().contains(Material.BLAZE_ROD)) {
                        giveWand(player);
                    }
                    plugin.getSetupManager().setState(player.getUniqueId(), SetupManager.SetupState.AWAITING_PLOT_P1);
                    player.sendMessage(ChatColor.AQUA + "--- Plot Setup Mode: ENABLED ---");
                    player.sendMessage(ChatColor.YELLOW + "LEFT-CLICK the first corner of a plot.");
                }
                // If they are already in setup mode, turn it OFF
                else {
                    plugin.getSetupManager().clearSetupData(player.getUniqueId());
                    player.sendMessage(ChatColor.RED + "--- Plot Setup Mode: DISABLED ---");
                }
                break;

            default:
                player.sendMessage(ChatColor.RED + "Unknown argument. Use /td <start|stop|status|plotmode|wand>");
                break;
        }

        return true;
    }

    private void giveWand(Player player) {
        // Create a special Blaze Rod
        org.bukkit.inventory.ItemStack wand = new org.bukkit.inventory.ItemStack(Material.BLAZE_ROD);
        org.bukkit.inventory.meta.ItemMeta meta = wand.getItemMeta();
        meta.setDisplayName(ChatColor.GOLD + "TD Setup Wand");
        // Add a lore to explain how to use it
        meta.setLore(java.util.Arrays.asList(
                ChatColor.YELLOW + "Left-Click" + ChatColor.GRAY + " to select Position 1 / Add Waypoint",
                ChatColor.YELLOW + "Right-Click" + ChatColor.GRAY + " to select Position 2"
        ));
        wand.setItemMeta(meta);

        // Give it to the player
        player.getInventory().addItem(wand);
        player.sendMessage(ChatColor.GREEN + "You have been given the Setup Wand!");
    }
}
