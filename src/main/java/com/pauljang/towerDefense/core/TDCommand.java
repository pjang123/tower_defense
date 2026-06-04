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
            player.sendMessage(ChatColor.RED + "Usage: /td <list|start|stop|status|plotmode [arena]|waypointmode [arena]|wand|clearwaypoints [arena]|spawnmob|gui|upgrades|giveitems|setarena [player] [arena]|challenge [player]|accept>");
            return true;
        }

        GameManager gameManager = plugin.getGameManager();

        // Handle the arguments
        switch (args[0].toLowerCase()) {
            case "list":
                player.sendMessage(ChatColor.GOLD + "--- Tower Defense Commands ---");
                player.sendMessage(ChatColor.YELLOW + "/td list " + ChatColor.WHITE + "- Shows this help menu");
                player.sendMessage(ChatColor.YELLOW + "/td challenge [player] " + ChatColor.WHITE + "- Duel another player in 1v1 TD");
                player.sendMessage(ChatColor.YELLOW + "/td accept " + ChatColor.WHITE + "- Accept a pending duel challenge");
                player.sendMessage(ChatColor.YELLOW + "/td start " + ChatColor.WHITE + "- Force start the game");
                player.sendMessage(ChatColor.YELLOW + "/td stop " + ChatColor.WHITE + "- Force stop the game");
                player.sendMessage(ChatColor.YELLOW + "/td status " + ChatColor.WHITE + "- Show current game state");
                player.sendMessage(ChatColor.YELLOW + "/td wand " + ChatColor.WHITE + "- Gives you the Setup Wand");
                player.sendMessage(ChatColor.YELLOW + "/td plotmode [arena] " + ChatColor.WHITE + "- Toggle Plot Setup mode for an arena");
                player.sendMessage(ChatColor.YELLOW + "/td waypointmode [arena] " + ChatColor.WHITE + "- Toggle Waypoint Setup mode for an arena");
                player.sendMessage(ChatColor.YELLOW + "/td clearwaypoints [arena] " + ChatColor.WHITE + "- Wipe waypoints for an arena");
                player.sendMessage(ChatColor.YELLOW + "/td spawnmob [type] [speed] [health] [armor] [slowImmune] [fireImmune] " + ChatColor.WHITE + "- Spawn a custom test mob");
                player.sendMessage(ChatColor.YELLOW + "/td gui " + ChatColor.WHITE + "- Open the Mob Spawner GUI");
                player.sendMessage(ChatColor.YELLOW + "/td upgrades " + ChatColor.WHITE + "- Open the Player Upgrades GUI");
                player.sendMessage(ChatColor.YELLOW + "/td giveitems " + ChatColor.WHITE + "- Give yourself the GUI opening menu hotbar items");
                player.sendMessage(ChatColor.YELLOW + "/td givegold [amount] " + ChatColor.WHITE + "- Give yourself gold (Admin)");
                player.sendMessage(ChatColor.YELLOW + "/td givexp [amount] " + ChatColor.WHITE + "- Give yourself EXP (Admin)");
                player.sendMessage(ChatColor.YELLOW + "/td setarena [player] [arena] " + ChatColor.WHITE + "- Set player's assigned arena (Admin)");
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
                if (gameManager.getCurrentState() == GameState.ACTIVE || gameManager.getCurrentState() == GameState.STARTING) {
                    player.sendMessage(ChatColor.YELLOW + "A game is already active! Gracefully restarting the match...");
                    gameManager.setGameState(GameState.ENDED);
                    
                    // Small delay to ensure clean termination before starting a new countdown
                    org.bukkit.Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        gameManager.setGameState(GameState.STARTING);
                    }, 5L);
                } else {
                    gameManager.setGameState(GameState.STARTING);
                    player.sendMessage(ChatColor.GREEN + "Forcing game to STARTING state!");
                }
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
                    String arena = "1";
                    if (args.length > 1) {
                        arena = args[1];
                    }
                    plugin.getSetupManager().setEditingArena(player.getUniqueId(), arena);
                    if (!player.getInventory().contains(Material.BLAZE_ROD)) {
                        giveWand(player);
                    }
                    plugin.getSetupManager().setState(player.getUniqueId(), SetupManager.SetupState.AWAITING_PLOT_P1);
                    player.sendMessage(ChatColor.AQUA + "--- Plot Setup Mode: ENABLED (Arena " + arena + ") ---");
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
                    String arena = "1";
                    if (args.length > 1) {
                        arena = args[1];
                    }
                    plugin.getSetupManager().setEditingArena(player.getUniqueId(), arena);
                    if (!player.getInventory().contains(Material.BLAZE_ROD)) {
                        giveWand(player);
                    }
                    plugin.getSetupManager().setState(player.getUniqueId(), SetupManager.SetupState.WAYPOINT_MODE);
                    player.sendMessage(ChatColor.AQUA + "--- Waypoint Mode: ENABLED (Arena " + arena + ") ---");
                    player.sendMessage(ChatColor.YELLOW + "LEFT-CLICK blocks to add waypoints in order. Type /td waypointmode to exit.");
                } else {
                    plugin.getSetupManager().setState(player.getUniqueId(), SetupManager.SetupState.IDLE);
                    player.sendMessage(ChatColor.RED + "--- Waypoint Mode: DISABLED ---");
                }
                break;

            case "clearwaypoints":
                String clearArena = "1";
                if (args.length > 1) {
                    clearArena = args[1];
                }
                plugin.getWaypointConfigManager().clearAllWaypoints(clearArena);
                player.sendMessage(ChatColor.RED + "All waypoints for Arena " + clearArena + " have been wiped from memory!");
                break;

            case "upgrades":
                gameManager.openUpgradesGUI(player);
                break;

            case "giveitems":
            case "menuitems":
                // Give Mob Spawner Menu Item in slot 7
                org.bukkit.inventory.ItemStack spawnerItem = new org.bukkit.inventory.ItemStack(Material.NETHER_STAR);
                org.bukkit.inventory.meta.ItemMeta spawnerMeta = spawnerItem.getItemMeta();
                if (spawnerMeta != null) {
                    spawnerMeta.setDisplayName(ChatColor.LIGHT_PURPLE + "" + ChatColor.BOLD + "Mob Spawner Menu");
                    spawnerMeta.setLore(java.util.Arrays.asList(ChatColor.GRAY + "Right-click to open the Mob Spawner GUI."));
                    spawnerItem.setItemMeta(spawnerMeta);
                }
                player.getInventory().setItem(7, spawnerItem);

                // Give Player Upgrades Menu Item in slot 8
                org.bukkit.inventory.ItemStack upgradeItem = new org.bukkit.inventory.ItemStack(Material.EMERALD);
                org.bukkit.inventory.meta.ItemMeta upgradeMeta = upgradeItem.getItemMeta();
                if (upgradeMeta != null) {
                    upgradeMeta.setDisplayName(ChatColor.GOLD + "" + ChatColor.BOLD + "Player Upgrades Menu");
                    upgradeMeta.setLore(java.util.Arrays.asList(ChatColor.GRAY + "Right-click to open the Player Upgrades GUI."));
                    upgradeItem.setItemMeta(upgradeMeta);
                }
                player.getInventory().setItem(8, upgradeItem);

                player.sendMessage(ChatColor.GREEN + "Gave you the GUI opening hotbar items in slots 7 and 8!");
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

            case "setarena":
                if (!player.isOp()) {
                    player.sendMessage(ChatColor.RED + "You must be OP to use testing/admin commands.");
                    break;
                }
                if (args.length < 3) {
                    player.sendMessage(ChatColor.RED + "Usage: /td setarena <player> <arena>");
                    break;
                }
                Player targetPlayer = org.bukkit.Bukkit.getPlayer(args[1]);
                if (targetPlayer == null) {
                    player.sendMessage(ChatColor.RED + "Player not found!");
                    break;
                }
                String targetArena = args[2];
                plugin.getGameManager().setPlayerArena(targetPlayer.getUniqueId(), targetArena);
                player.sendMessage(ChatColor.GREEN + "Set " + targetPlayer.getName() + "'s arena to: " + targetArena);
                targetPlayer.sendMessage(ChatColor.GREEN + "You have been assigned to Arena: " + targetArena);
                break;

            case "challenge":
                if (args.length < 2) {
                    player.sendMessage(ChatColor.RED + "Usage: /td challenge <player>");
                    break;
                }
                Player challengedPlayer = org.bukkit.Bukkit.getPlayer(args[1]);
                if (challengedPlayer == null) {
                    player.sendMessage(ChatColor.RED + "Player not found!");
                    break;
                }
                gameManager.challengePlayer(player, challengedPlayer);
                break;

            case "accept":
                gameManager.acceptChallenge(player);
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
