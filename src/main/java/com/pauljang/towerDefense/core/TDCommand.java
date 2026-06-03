package com.pauljang.towerDefense.core;

import com.pauljang.towerDefense.TowerDefense;
import com.pauljang.towerDefense.data.WaypointConfigManager;
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
            player.sendMessage(ChatColor.RED + "Usage: /td <list|start|stop|status|plotmode|waypointmode|wand|clearwaypoints|spawnmob|gui|upgrades>");
            return true;
        }

        GameManager gameManager = plugin.getGameManager();

        // Handle the arguments
        switch (args[0].toLowerCase()) {
            case "list":
                player.sendMessage(ChatColor.GOLD + "--- Tower Defense Commands ---");
                player.sendMessage(ChatColor.YELLOW + "/td list " + ChatColor.WHITE + "- Shows this help menu");
                player.sendMessage(ChatColor.YELLOW + "/td start " + ChatColor.WHITE + "- Force start the game");
                player.sendMessage(ChatColor.YELLOW + "/td stop " + ChatColor.WHITE + "- Force stop the game");
                player.sendMessage(ChatColor.YELLOW + "/td status " + ChatColor.WHITE + "- Show current game state");
                player.sendMessage(ChatColor.YELLOW + "/td wand " + ChatColor.WHITE + "- Gives you the Setup Wand");
                player.sendMessage(ChatColor.YELLOW + "/td plotmode " + ChatColor.WHITE + "- Toggle Plot Setup mode");
                player.sendMessage(ChatColor.YELLOW + "/td waypointmode " + ChatColor.WHITE + "- Toggle Waypoint Setup mode");
                player.sendMessage(ChatColor.YELLOW + "/td clearwaypoints " + ChatColor.WHITE + "- Wipe all waypoints");
                player.sendMessage(ChatColor.YELLOW + "/td spawnmob [type] [speed] [health] [armor] [slowImmune] [fireImmune] " + ChatColor.WHITE + "- Spawn a custom test mob");
                player.sendMessage(ChatColor.YELLOW + "/td gui " + ChatColor.WHITE + "- Open the Mob Spawner GUI");
                player.sendMessage(ChatColor.YELLOW + "/td upgrades " + ChatColor.WHITE + "- Open the Player Upgrades GUI");
                player.sendMessage(ChatColor.YELLOW + "/td givegold [amount] " + ChatColor.WHITE + "- Give yourself gold (Admin)");
                player.sendMessage(ChatColor.YELLOW + "/td givexp [amount] " + ChatColor.WHITE + "- Give yourself EXP (Admin)");
                break;

            case "gui":
                plugin.getMobManager().openMobSpawnerGUI(player);
                break;

            case "spawnmob":
                org.bukkit.entity.EntityType mobType = org.bukkit.entity.EntityType.ZOMBIE;
                double speedMult = 1.0;
                double maxHealth = -1.0;
                double armor = 0.0;
                boolean immuneToSlow = false;
                boolean immuneToFire = false;

                if (args.length > 1) {
                    try {
                        mobType = org.bukkit.entity.EntityType.valueOf(args[1].toUpperCase());
                    } catch (IllegalArgumentException e) {
                        player.sendMessage(ChatColor.RED + "Invalid entity type '" + args[1] + "'! Defaulting to ZOMBIE.");
                    }
                }
                if (args.length > 2) {
                    try {
                        speedMult = Double.parseDouble(args[2]);
                    } catch (NumberFormatException e) {
                        player.sendMessage(ChatColor.RED + "Invalid speed multiplier! Defaulting to 1.0.");
                    }
                }
                if (args.length > 3) {
                    try {
                        maxHealth = Double.parseDouble(args[3]);
                    } catch (NumberFormatException e) {
                        player.sendMessage(ChatColor.RED + "Invalid max health! Defaulting to default.");
                    }
                }
                if (args.length > 4) {
                    try {
                        armor = Double.parseDouble(args[4]);
                    } catch (NumberFormatException e) {
                        player.sendMessage(ChatColor.RED + "Invalid armor! Defaulting to 0.0.");
                    }
                }
                if (args.length > 5) {
                    immuneToSlow = Boolean.parseBoolean(args[5]);
                }
                if (args.length > 6) {
                    immuneToFire = Boolean.parseBoolean(args[6]);
                }

                plugin.getMobManager().spawnMob(mobType, speedMult, maxHealth, armor, immuneToSlow, immuneToFire);
                player.sendMessage(ChatColor.GREEN + "Spawned custom " + mobType.name() + " at first waypoint!");
                player.sendMessage(ChatColor.DARK_GREEN + " - Speed Mult: " + speedMult + "x | Health: " + (maxHealth > 0 ? maxHealth : "Default") + " | Armor: " + armor);
                player.sendMessage(ChatColor.DARK_GREEN + " - Slow Immune: " + immuneToSlow + " | Fire Immune: " + immuneToFire);
                break;

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

            case "waypointmode":
                SetupManager.SetupState wpState = plugin.getSetupManager().getState(player.getUniqueId());

                if (wpState != SetupManager.SetupState.WAYPOINT_MODE) {
                    if (!player.getInventory().contains(Material.BLAZE_ROD)) {
                        giveWand(player);
                    }
                    plugin.getSetupManager().setState(player.getUniqueId(), SetupManager.SetupState.WAYPOINT_MODE);
                    player.sendMessage(ChatColor.AQUA + "--- Waypoint Mode: ENABLED ---");
                    player.sendMessage(ChatColor.YELLOW + "LEFT-CLICK blocks to add waypoints in order. Type /td waypointmode to exit.");
                } else {
                    plugin.getSetupManager().setState(player.getUniqueId(), SetupManager.SetupState.IDLE);
                    player.sendMessage(ChatColor.RED + "--- Waypoint Mode: DISABLED ---");
                }
                break;

            case "clearwaypoints":
                plugin.getWaypointConfigManager().clearAllWaypoints();
                player.sendMessage(ChatColor.RED + "All waypoints have been wiped from memory!");
                break;

            case "upgrades":
                gameManager.openUpgradesGUI(player);
                break;

            case "givegold":
                if (!player.isOp()) {
                    player.sendMessage(ChatColor.RED + "You must be OP to use testing/admin commands.");
                    break;
                }
                int goldAmount = 1000;
                if (args.length > 1) {
                    try {
                        goldAmount = Integer.parseInt(args[1]);
                    } catch (NumberFormatException e) {
                        player.sendMessage(ChatColor.RED + "Invalid gold amount! Defaulting to 1000.");
                    }
                }
                gameManager.addGold(player.getUniqueId(), goldAmount);
                player.sendMessage(ChatColor.GREEN + "Gave yourself " + goldAmount + " Gold!");
                break;

            case "givexp":
                if (!player.isOp()) {
                    player.sendMessage(ChatColor.RED + "You must be OP to use testing/admin commands.");
                    break;
                }
                int xpAmount = 100;
                if (args.length > 1) {
                    try {
                        xpAmount = Integer.parseInt(args[1]);
                    } catch (NumberFormatException e) {
                        player.sendMessage(ChatColor.RED + "Invalid EXP amount! Defaulting to 100.");
                    }
                }
                gameManager.addExp(player.getUniqueId(), xpAmount);
                break;

            default:
                player.sendMessage(ChatColor.RED + "Unknown argument. Use /td list to see all commands.");
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
