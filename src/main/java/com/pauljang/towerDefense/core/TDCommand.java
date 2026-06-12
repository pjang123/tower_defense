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
import java.io.File;
import java.util.List;
import java.util.UUID;

public class TDCommand implements CommandExecutor {

    private final TowerDefense plugin;
    // Track which loaded worlds are template copies (world name -> template path)
    private final java.util.Map<String, File> loadedTemplates = new java.util.HashMap<>();

    public TDCommand(TowerDefense plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Only players can run this command!");
            return true;
        }

        Player player = (Player) sender;

        if (args.length == 0) {
            player.sendMessage(ChatColor.RED + "Usage: /td <list|start|stop|status|plotmode [arena]|deleteplotmode [arena]|waypointmode [arena]|wand|clearwaypoints [arena]|saveconfig|loadworld|unloadworld|spawnmob|gui|upgrades|giveitems|givegold|givexp|setarena|challenge|accept|lobby|forfeit|forcestart>");
            return true;
        }

        GameManager gameManager = plugin.getGameManager();

        switch (args[0].toLowerCase()) {
            case "list":
                player.sendMessage(ChatColor.GOLD + "--- Tower Defense Commands ---");
                player.sendMessage(ChatColor.YELLOW + "/td challenge [player] " + ChatColor.WHITE + "- Duel another player in 1v1 TD");
                player.sendMessage(ChatColor.YELLOW + "/td accept " + ChatColor.WHITE + "- Accept a pending duel challenge");
                player.sendMessage(ChatColor.YELLOW + "/td lobby " + ChatColor.WHITE + "- Return to the lobby and leave the queue");
                player.sendMessage(ChatColor.YELLOW + "/td forfeit " + ChatColor.WHITE + "- Forfeit the active game");
                player.sendMessage(ChatColor.YELLOW + "/td forcestart " + ChatColor.WHITE + "- Force the multiplayer map vote with the current queue (Admin)");
                player.sendMessage(ChatColor.YELLOW + "/td start " + ChatColor.WHITE + "- Force-start a single-player match on a random map (Admin)");
                player.sendMessage(ChatColor.YELLOW + "/td stop " + ChatColor.WHITE + "- End your current match");
                player.sendMessage(ChatColor.YELLOW + "/td status " + ChatColor.WHITE + "- Show the current game state");
                player.sendMessage(ChatColor.YELLOW + "/td giveitems " + ChatColor.WHITE + "- Give yourself the GUI menu hotbar items");
                player.sendMessage(ChatColor.YELLOW + "/td givegold [player] <amount> " + ChatColor.WHITE + "- Give gold to a player in a match (Admin)");
                player.sendMessage(ChatColor.YELLOW + "/td givexp [player] <amount> " + ChatColor.WHITE + "- Give TD XP to a player in a match (Admin)");
                player.sendMessage(ChatColor.YELLOW + "/td setarena <player> <arena> " + ChatColor.WHITE + "- Assign a player to an arena (Admin)");
                player.sendMessage(ChatColor.YELLOW + "/td wand " + ChatColor.WHITE + "- Gives you the Setup Wand");
                player.sendMessage(ChatColor.YELLOW + "/td plotmode [arena] " + ChatColor.WHITE + "- Toggle Plot Setup mode for an arena");
                player.sendMessage(ChatColor.YELLOW + "/td deleteplotmode [arena] " + ChatColor.WHITE + "- Toggle Plot Deletion mode for an arena");
                player.sendMessage(ChatColor.YELLOW + "/td waypointmode [arena] " + ChatColor.WHITE + "- Toggle Waypoint Setup mode for an arena");
                player.sendMessage(ChatColor.YELLOW + "  ↳ Delete waypoint " + ChatColor.WHITE + "- In Waypoint mode, Sneak + click a node to delete it");
                player.sendMessage(ChatColor.YELLOW + "/td clearwaypoints [arena] " + ChatColor.WHITE + "- Wipe waypoints for an arena");
                player.sendMessage(ChatColor.YELLOW + "/td loadworld <name> " + ChatColor.WHITE + "- Load a template world for editing");
                player.sendMessage(ChatColor.YELLOW + "/td unloadworld <name> " + ChatColor.WHITE + "- Unload and delete a template world copy");
                player.sendMessage(ChatColor.YELLOW + "/td saveconfig " + ChatColor.WHITE + "- Save current world's plots/waypoints to its template folder");
                player.sendMessage(ChatColor.YELLOW + "/td spawnmob [type] " + ChatColor.WHITE + "- Spawn a custom test mob");
                player.sendMessage(ChatColor.YELLOW + "/td gui " + ChatColor.WHITE + "- Open the Mob Spawner GUI");
                player.sendMessage(ChatColor.YELLOW + "/td upgrades " + ChatColor.WHITE + "- Open the Player Upgrades GUI");
                break;

            case "gui":
                plugin.getMobManager().openMobSpawnerGUI(player);
                break;

            case "wand":
                if (!player.hasPermission("towerdefense.admin")) {
                    player.sendMessage(ChatColor.RED + "No permission.");
                    break;
                }
                giveWand(player);
                break;

            case "plotmode":
                if (!player.hasPermission("towerdefense.admin")) {
                    player.sendMessage(ChatColor.RED + "No permission.");
                    break;
                }
                SetupManager.SetupState state = plugin.getSetupManager().getState(player.getUniqueId());
                if (state == SetupManager.SetupState.IDLE) {
                    String arena = args.length > 1 ? args[1] : "1";
                    plugin.getSetupManager().setEditingArena(player.getUniqueId(), arena);
                    plugin.getSetupManager().setState(player.getUniqueId(), SetupManager.SetupState.AWAITING_PLOT);
                    player.sendMessage(ChatColor.AQUA + "--- Plot Mode: ENABLED (Arena " + arena + ") ---");
                } else {
                    plugin.getSetupManager().clearSetupData(player.getUniqueId());
                    player.sendMessage(ChatColor.RED + "--- Plot Mode: DISABLED ---");
                }
                break;

            case "deleteplotmode":
                if (!player.hasPermission("towerdefense.admin")) {
                    player.sendMessage(ChatColor.RED + "No permission.");
                    break;
                }
                SetupManager.SetupState deleteState = plugin.getSetupManager().getState(player.getUniqueId());
                if (deleteState != SetupManager.SetupState.DELETING_PLOT) {
                    String arena = args.length > 1 ? args[1] : "1";
                    plugin.getSetupManager().setEditingArena(player.getUniqueId(), arena);
                    plugin.getSetupManager().setState(player.getUniqueId(), SetupManager.SetupState.DELETING_PLOT);
                    player.sendMessage(ChatColor.AQUA + "--- Delete Plot Mode: ENABLED (Arena " + arena + ") ---");
                    player.sendMessage(ChatColor.YELLOW + "All plots are outlined; the one under your cursor turns white. Left-click it to delete.");
                } else {
                    plugin.getSetupManager().setState(player.getUniqueId(), SetupManager.SetupState.IDLE);
                    player.sendMessage(ChatColor.RED + "--- Delete Plot Mode: DISABLED ---");
                }
                break;

            case "waypointmode":
                if (!player.hasPermission("towerdefense.admin")) {
                    player.sendMessage(ChatColor.RED + "No permission.");
                    break;
                }
                SetupManager.SetupState wpState = plugin.getSetupManager().getState(player.getUniqueId());
                if (wpState != SetupManager.SetupState.WAYPOINT_MODE) {
                    String arena = args.length > 1 ? args[1] : "1";
                    plugin.getSetupManager().setEditingArena(player.getUniqueId(), arena);
                    plugin.getSetupManager().setState(player.getUniqueId(), SetupManager.SetupState.WAYPOINT_MODE);
                    player.sendMessage(ChatColor.AQUA + "--- Waypoint Mode: ENABLED (Arena " + arena + ") ---");
                    player.sendMessage(ChatColor.YELLOW + "Left-click: select/create  |  Right-click a node: connect  |  Sneak+click a node: delete");
                } else {
                    plugin.getSetupManager().setState(player.getUniqueId(), SetupManager.SetupState.IDLE);
                    player.sendMessage(ChatColor.RED + "--- Waypoint Mode: DISABLED ---");
                }
                break;

            case "clearwaypoints":
                if (!player.hasPermission("towerdefense.admin")) {
                    player.sendMessage(ChatColor.RED + "No permission.");
                    break;
                }
                String clearArena = args.length > 1 ? args[1] : "1";
                plugin.getWaypointConfigManager().clearAllWaypoints(clearArena);
                player.sendMessage(ChatColor.GREEN + "Cleared all waypoints for arena " + clearArena);
                break;

            case "saveconfig":
                if (!player.hasPermission("towerdefense.admin")) {
                    player.sendMessage(ChatColor.RED + "No permission.");
                    break;
                }
                saveMapConfig(player);
                break;

            case "loadworld":
                if (!player.hasPermission("towerdefense.admin")) {
                    player.sendMessage(ChatColor.RED + "No permission.");
                    break;
                }
                if (args.length < 2) {
                    player.sendMessage(ChatColor.RED + "Usage: /td loadworld <worldname>");
                    break;
                }
                loadWorld(player, args[1]);
                break;

            case "unloadworld":
                if (!player.hasPermission("towerdefense.admin")) {
                    player.sendMessage(ChatColor.RED + "No permission.");
                    break;
                }
                if (args.length < 2) {
                    player.sendMessage(ChatColor.RED + "Usage: /td unloadworld <worldname>");
                    break;
                }
                unloadWorld(player, args[1]);
                break;

            case "spawnmob":
                if (!player.hasPermission("towerdefense.admin")) {
                    player.sendMessage(ChatColor.RED + "No permission.");
                    break;
                }
                Match spawnMatch = plugin.getGameManager().getPlayerMatch(player.getUniqueId());
                if (spawnMatch == null) {
                    player.sendMessage(ChatColor.RED + "You must be in an active match to spawn mobs!");
                    break;
                }
                if (args.length < 2) {
                    player.sendMessage(ChatColor.RED + "Usage: /td spawnmob [type]");
                    break;
                }
                try {
                    org.bukkit.entity.EntityType type = org.bukkit.entity.EntityType.valueOf(args[1].toUpperCase());
                    String arena = plugin.getGameManager().getPlayerArena(player.getUniqueId());
                    plugin.getMobManager().spawnMob(spawnMatch, arena, type);
                    player.sendMessage(ChatColor.GREEN + "Spawned test mob: " + type.name());
                } catch (Exception e) {
                    player.sendMessage(ChatColor.RED + "Invalid mob type!");
                }
                break;

            case "challenge":
                if (args.length < 2) {
                    player.sendMessage(ChatColor.RED + "Usage: /td challenge <player>");
                    break;
                }
                Player target = org.bukkit.Bukkit.getPlayer(args[1]);
                if (target != null) gameManager.challengePlayer(player, target);
                break;

            case "accept":
                gameManager.acceptChallenge(player);
                break;

            case "lobby":
                // If they're in an active match, leaving must tear the match down (forfeit) so they
                // aren't left registered in it — otherwise the sidebar/gold loop keep treating them as
                // in-game while they stand in the lobby.
                if (gameManager.getPlayerMatch(player.getUniqueId()) != null) {
                    gameManager.forfeit(player);
                    break;
                }
                org.bukkit.World lobbyWorld = org.bukkit.Bukkit.getWorld("lobby_world");
                if (lobbyWorld != null) player.teleport(lobbyWorld.getSpawnLocation());
                gameManager.giveLobbyItems(player);
                player.sendMessage(ChatColor.GREEN + "Returned to lobby.");
                break;

            case "forfeit":
                gameManager.forfeit(player);
                break;

            case "start":
                if (!player.hasPermission("towerdefense.admin") && !player.isOp()) {
                    player.sendMessage(ChatColor.RED + "You must be OP or have towerdefense.admin to use this command.");
                    break;
                }
                if (gameManager.getPlayerMatch(player.getUniqueId()) != null) {
                    player.sendMessage(ChatColor.RED + "You're already in a match! Use /td stop first.");
                    break;
                }
                java.util.List<com.pauljang.towerDefense.data.MapManager.MapData> spMaps =
                        plugin.getMapManager().getAvailableMaps(true);
                if (spMaps.isEmpty()) {
                    player.sendMessage(ChatColor.RED + "No single-player maps found in GAME_WORLD_TEMPLATES/SINGLE_PLAYER/.");
                    break;
                }
                com.pauljang.towerDefense.data.MapManager.MapData chosenMap =
                        spMaps.get(new java.util.Random().nextInt(spMaps.size()));
                player.sendMessage(ChatColor.GREEN + "Force-starting a single-player match on "
                        + chosenMap.getDisplayName() + "...");
                gameManager.startMatch(java.util.Collections.singletonList(player.getUniqueId()), chosenMap);
                break;

            case "stop":
                Match stopMatch = gameManager.getPlayerMatch(player.getUniqueId());
                if (stopMatch == null) {
                    player.sendMessage(ChatColor.RED + "You're not in an active match.");
                    break;
                }
                gameManager.endMatch(stopMatch);
                player.sendMessage(ChatColor.GREEN + "Match ended.");
                break;

            case "forcestart":
                if (!player.hasPermission("towerdefense.admin") && !player.isOp()) {
                    player.sendMessage(ChatColor.RED + "You must be OP or have towerdefense.admin to use this command.");
                    break;
                }
                if (plugin.getQueueManager().forceStartMultiplayer()) {
                    player.sendMessage(ChatColor.GREEN + "Force-starting the multiplayer map vote with the current queue!");
                } else {
                    player.sendMessage(ChatColor.RED + "No players are in the multiplayer queue to force-start.");
                }
                break;

            case "upgrades":
                gameManager.openUpgradesGUI(player);
                break;

            case "giveitems":
            case "menuitems":
                // Mob Spawner menu item (slot 7)
                org.bukkit.inventory.ItemStack spawnerItem = new org.bukkit.inventory.ItemStack(Material.NETHER_STAR);
                org.bukkit.inventory.meta.ItemMeta spawnerMeta = spawnerItem.getItemMeta();
                if (spawnerMeta != null) {
                    spawnerMeta.setDisplayName(ChatColor.LIGHT_PURPLE + "" + ChatColor.BOLD + "Mob Spawner Menu");
                    spawnerMeta.setLore(java.util.Arrays.asList(ChatColor.GRAY + "Right-click to open the Mob Spawner GUI."));
                    spawnerItem.setItemMeta(spawnerMeta);
                }
                player.getInventory().setItem(7, spawnerItem);

                // Player Upgrades menu item (slot 8)
                org.bukkit.inventory.ItemStack upgradeItem = new org.bukkit.inventory.ItemStack(Material.EMERALD);
                org.bukkit.inventory.meta.ItemMeta upgradeMeta = upgradeItem.getItemMeta();
                if (upgradeMeta != null) {
                    upgradeMeta.setDisplayName(ChatColor.GOLD + "" + ChatColor.BOLD + "Player Upgrades Menu");
                    upgradeMeta.setLore(java.util.Arrays.asList(ChatColor.GRAY + "Right-click to open the Player Upgrades GUI."));
                    upgradeItem.setItemMeta(upgradeMeta);
                }
                player.getInventory().setItem(8, upgradeItem);
                player.sendMessage(ChatColor.GREEN + "Gave you the GUI menu hotbar items in slots 7 and 8!");
                break;

            case "setarena":
                if (!player.hasPermission("towerdefense.admin") && !player.isOp()) {
                    player.sendMessage(ChatColor.RED + "You must be OP or have towerdefense.admin to use this command.");
                    break;
                }
                if (args.length < 3) {
                    player.sendMessage(ChatColor.RED + "Usage: /td setarena <player> <arena>");
                    break;
                }
                Player arenaTarget = org.bukkit.Bukkit.getPlayer(args[1]);
                if (arenaTarget == null) {
                    player.sendMessage(ChatColor.RED + "Player not found!");
                    break;
                }
                gameManager.setPlayerArena(arenaTarget.getUniqueId(), args[2]);
                player.sendMessage(ChatColor.GREEN + "Set " + arenaTarget.getName() + "'s arena to: " + args[2]);
                arenaTarget.sendMessage(ChatColor.GREEN + "You have been assigned to Arena: " + args[2]);
                break;

            case "givegold": {
                if (!player.hasPermission("towerdefense.admin") && !player.isOp()) {
                    player.sendMessage(ChatColor.RED + "You must be OP or have towerdefense.admin to use this command.");
                    break;
                }
                // Forms: /td givegold <amount>  OR  /td givegold <player> <amount>
                Player goldTarget;
                String goldAmountArg;
                if (args.length >= 3) {
                    goldTarget = org.bukkit.Bukkit.getPlayer(args[1]);
                    goldAmountArg = args[2];
                } else if (args.length == 2) {
                    goldTarget = player;
                    goldAmountArg = args[1];
                } else {
                    player.sendMessage(ChatColor.RED + "Usage: /td givegold [player] <amount>");
                    break;
                }
                if (goldTarget == null) {
                    player.sendMessage(ChatColor.RED + "Player not found!");
                    break;
                }
                int goldAmount;
                try {
                    goldAmount = Integer.parseInt(goldAmountArg);
                } catch (NumberFormatException e) {
                    player.sendMessage(ChatColor.RED + "Amount must be a whole number!");
                    break;
                }
                if (gameManager.getPlayerMatch(goldTarget.getUniqueId()) == null) {
                    player.sendMessage(ChatColor.RED + goldTarget.getName() + " is not in an active match.");
                    break;
                }
                gameManager.addGold(goldTarget.getUniqueId(), goldAmount, false);
                player.sendMessage(ChatColor.GREEN + "Gave " + goldAmount + " gold to " + goldTarget.getName() + ".");
                if (goldTarget != player) {
                    goldTarget.sendMessage(ChatColor.GREEN + "You received " + goldAmount + " gold!");
                }
                break;
            }

            case "givexp": {
                if (!player.hasPermission("towerdefense.admin") && !player.isOp()) {
                    player.sendMessage(ChatColor.RED + "You must be OP or have towerdefense.admin to use this command.");
                    break;
                }
                // Forms: /td givexp <amount>  OR  /td givexp <player> <amount>
                Player xpTarget;
                String xpAmountArg;
                if (args.length >= 3) {
                    xpTarget = org.bukkit.Bukkit.getPlayer(args[1]);
                    xpAmountArg = args[2];
                } else if (args.length == 2) {
                    xpTarget = player;
                    xpAmountArg = args[1];
                } else {
                    player.sendMessage(ChatColor.RED + "Usage: /td givexp [player] <amount>");
                    break;
                }
                if (xpTarget == null) {
                    player.sendMessage(ChatColor.RED + "Player not found!");
                    break;
                }
                int xpAmount;
                try {
                    xpAmount = Integer.parseInt(xpAmountArg);
                } catch (NumberFormatException e) {
                    player.sendMessage(ChatColor.RED + "Amount must be a whole number!");
                    break;
                }
                if (gameManager.getPlayerMatch(xpTarget.getUniqueId()) == null) {
                    player.sendMessage(ChatColor.RED + xpTarget.getName() + " is not in an active match.");
                    break;
                }
                gameManager.addExp(xpTarget.getUniqueId(), xpAmount);
                player.sendMessage(ChatColor.GREEN + "Gave " + xpAmount + " XP to " + xpTarget.getName() + ".");
                if (xpTarget != player) {
                    xpTarget.sendMessage(ChatColor.GREEN + "You received " + xpAmount + " TD XP!");
                }
                break;
            }

            case "status":
                GameState curState = gameManager.getCurrentState();
                player.sendMessage(ChatColor.YELLOW + "State: " + (curState != null ? curState.name() : "NONE"));
                player.sendMessage(ChatColor.YELLOW + "Active matches: " + gameManager.getActiveMatches().size());
                break;

            default:
                player.sendMessage(ChatColor.RED + "Unknown command.");
                break;
        }
        return true;
    }

    private void saveMapConfig(Player player) {
        org.bukkit.World world = player.getWorld();
        File worldFolder = world.getWorldFolder();
        
        // Verify we are inside the GAME_WORLD_TEMPLATES structure
        if (!worldFolder.getAbsolutePath().contains("GAME_WORLD_TEMPLATES")) {
            player.sendMessage(ChatColor.RED + "Error: This world is not inside the GAME_WORLD_TEMPLATES folder!");
            player.sendMessage(ChatColor.GRAY + "Move your world folder to GAME_WORLD_TEMPLATES/SINGLE_PLAYER/<name> or MULTI_PLAYER/<name> first.");
            return;
        }

        // 1. Save map.yml (Metadata)
        File mapFile = new File(worldFolder, "map.yml");
        if (!mapFile.exists()) {
            try {
                org.bukkit.configuration.file.YamlConfiguration cfg = new org.bukkit.configuration.file.YamlConfiguration();
                cfg.set("display-name", world.getName());
                cfg.set("author", player.getName());
                cfg.save(mapFile);
            } catch (Exception e) { e.printStackTrace(); }
        }

        // 2. Save plots.yml
        plugin.getPlotConfigManager().exportToMap(world, new File(worldFolder, "plots.yml"));

        // 3. Save waypoints.yml
        plugin.getWaypointConfigManager().exportToMap(world, new File(worldFolder, "waypoints.yml"));

        player.sendMessage(ChatColor.GREEN + "Successfully saved Tower Defense configuration files to " + world.getName() + "!");
        player.sendMessage(ChatColor.YELLOW + "Map metadata, plots, and waypoints are now bundled with the world data.");
    }

    private void loadWorld(Player player, String worldName) {
        plugin.getLogger().info("loadWorld called for: " + worldName);

        // Check if world is already loaded. Bukkit.getWorld(name) can miss a world that Paper migrated
        // into another world's dimensions/ folder or registered under a namespaced key, which made the
        // check report "not loaded" for a world that actually was — and then re-copy/re-create it. Scan
        // every loaded world by name as an authoritative fallback.
        org.bukkit.World existingWorld = findLoadedWorld(worldName);
        plugin.getLogger().info("Loaded-world lookup for '" + worldName + "': " + (existingWorld != null ? existingWorld.getName() : "null"));
        if (existingWorld == null) {
            StringBuilder loaded = new StringBuilder();
            for (org.bukkit.World w : org.bukkit.Bukkit.getWorlds()) loaded.append(w.getName()).append(" ");
            plugin.getLogger().info("Currently loaded worlds: " + loaded.toString().trim());
        }

        if (existingWorld != null) {
            player.sendMessage(ChatColor.YELLOW + "World '" + worldName + "' is already loaded!");

            // If it's tracked as a template, remind them
            if (loadedTemplates.containsKey(worldName)) {
                player.sendMessage(ChatColor.GRAY + "This is a template copy. Changes save automatically.");
            }

            org.bukkit.Location spawnLoc = existingWorld.getSpawnLocation();
            player.sendMessage(ChatColor.GRAY + "Teleporting to spawn: " + spawnLoc.getBlockX() + ", " + spawnLoc.getBlockY() + ", " + spawnLoc.getBlockZ());
            player.teleport(spawnLoc);
            return;
        }

        plugin.getLogger().info("World not loaded, proceeding with template search...");

        // Search for the template in GAME_WORLD_TEMPLATES
        File serverRoot = plugin.getDataFolder().getParentFile().getParentFile();
        File templatesRoot = new File(serverRoot, "GAME_WORLD_TEMPLATES");

        File templateWorld = null;

        // Check SINGLE_PLAYER folder
        File singlePlayerTemplate = new File(templatesRoot, "SINGLE_PLAYER" + File.separator + worldName);
        if (singlePlayerTemplate.exists() && singlePlayerTemplate.isDirectory()) {
            templateWorld = singlePlayerTemplate;
        }

        // Check MULTI_PLAYER folder
        File multiPlayerTemplate = new File(templatesRoot, "MULTI_PLAYER" + File.separator + worldName);
        if (multiPlayerTemplate.exists() && multiPlayerTemplate.isDirectory()) {
            templateWorld = multiPlayerTemplate;
        }

        if (templateWorld == null) {
            player.sendMessage(ChatColor.RED + "Template world '" + worldName + "' not found in GAME_WORLD_TEMPLATES!");
            player.sendMessage(ChatColor.GRAY + "Looking in: " + templatesRoot.getAbsolutePath());
            return;
        }

        // Verify world structure
        File regionFolder = new File(templateWorld, "region");
        File levelDat = new File(templateWorld, "level.dat");
        File dimensionsFolder = new File(templateWorld, "dimensions");

        if (!levelDat.exists()) {
            player.sendMessage(ChatColor.RED + "Invalid world: level.dat not found!");
            player.sendMessage(ChatColor.GRAY + "Expected: " + levelDat.getAbsolutePath());
            return;
        }

        if (!regionFolder.exists() || !regionFolder.isDirectory()) {
            player.sendMessage(ChatColor.RED + "Invalid world: region/ folder not found at world root!");

            // Check if using nested structure
            File nestedRegion = new File(templateWorld, "dimensions/minecraft/overworld/region");
            if (nestedRegion.exists()) {
                player.sendMessage(ChatColor.YELLOW + "Found region data in nested structure: dimensions/minecraft/overworld/region/");
                player.sendMessage(ChatColor.YELLOW + "Please move .mca files from there to region/ at world root and delete dimensions/ folder");
                player.sendMessage(ChatColor.GRAY + "See MAP_SETUP_GUIDE.md for correct structure");
            } else {
                player.sendMessage(ChatColor.GRAY + "Expected: " + regionFolder.getAbsolutePath());
            }
            return;
        }

        if (dimensionsFolder.exists()) {
            player.sendMessage(ChatColor.YELLOW + "WARNING: dimensions/ folder detected - this may cause migration issues!");
            player.sendMessage(ChatColor.YELLOW + "Consider removing it if region/ already contains your world data");
        }

        player.sendMessage(ChatColor.YELLOW + "Found template at: " + templateWorld.getAbsolutePath());
        player.sendMessage(ChatColor.YELLOW + "Copying to server root...");

        // Copy the template to server root
        File targetWorld = new File(serverRoot, worldName);

        if (targetWorld.exists()) {
            player.sendMessage(ChatColor.YELLOW + "World folder already exists at server root. Loading existing world...");
            player.sendMessage(ChatColor.GRAY + "Note: This may be leftover from a previous session. Consider deleting it first.");
        } else {
            try {
                plugin.getGameManager().copyDirectory(templateWorld, targetWorld);
                player.sendMessage(ChatColor.GREEN + "Successfully copied world template!");
            } catch (Exception e) {
                player.sendMessage(ChatColor.RED + "Failed to copy template: " + e.getMessage());
                e.printStackTrace();
                return;
            }
        }

        // Load the world
        player.sendMessage(ChatColor.YELLOW + "Loading world: " + worldName + "...");
        try {
            org.bukkit.WorldCreator creator = new org.bukkit.WorldCreator(worldName);
            org.bukkit.World world = creator.createWorld();

            if (world != null) {
                plugin.getLogger().info("World created successfully: " + world.getName());

                // Track this as a template copy (do this AFTER world is created)
                loadedTemplates.put(worldName, templateWorld);
                plugin.getLogger().info("Tracking template copy. Loaded templates: " + loadedTemplates.keySet());

                player.sendMessage(ChatColor.GREEN + "Successfully loaded world: " + worldName);
                player.sendMessage(ChatColor.YELLOW + "This is a temporary copy. Changes save to the template. Auto-unloads when empty.");

                org.bukkit.Location spawnLoc = world.getSpawnLocation();
                player.sendMessage(ChatColor.GRAY + "World spawn: " + spawnLoc.getBlockX() + ", " + spawnLoc.getBlockY() + ", " + spawnLoc.getBlockZ());

                player.teleport(spawnLoc);
            } else {
                player.sendMessage(ChatColor.RED + "Failed to load world. Check server logs.");
            }
        } catch (Exception e) {
            player.sendMessage(ChatColor.RED + "Error loading world: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void unloadWorld(Player player, String worldName) {
        org.bukkit.World world = org.bukkit.Bukkit.getWorld(worldName);

        if (world == null) {
            player.sendMessage(ChatColor.RED + "World '" + worldName + "' is not loaded!");
            return;
        }

        // Save level.dat back to template if this is a template copy
        File templateSource = loadedTemplates.get(worldName);
        if (templateSource != null) {
            File serverRoot = plugin.getDataFolder().getParentFile().getParentFile();
            File worldFolder = new File(serverRoot, worldName);
            File levelDatCopy = new File(worldFolder, "level.dat");
            File levelDatTemplate = new File(templateSource, "level.dat");

            if (levelDatCopy.exists()) {
                try {
                    java.nio.file.Files.copy(levelDatCopy.toPath(), levelDatTemplate.toPath(),
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    player.sendMessage(ChatColor.GREEN + "Saved level.dat changes (spawn point, gamerules) back to template");
                } catch (Exception e) {
                    player.sendMessage(ChatColor.YELLOW + "Warning: Could not save level.dat to template: " + e.getMessage());
                }
            }
        }

        // Remove from tracking BEFORE teleporting players (so listener won't try to unload)
        loadedTemplates.remove(worldName);

        // Teleport all players out of this world
        org.bukkit.World lobbyWorld = org.bukkit.Bukkit.getWorld("lobby_world");
        if (lobbyWorld == null) lobbyWorld = org.bukkit.Bukkit.getWorlds().get(0); // Fallback to first world

        for (org.bukkit.entity.Player p : world.getPlayers()) {
            p.teleport(lobbyWorld.getSpawnLocation());
            p.sendMessage(ChatColor.YELLOW + "World " + worldName + " is being unloaded.");
        }

        // Unload the world
        player.sendMessage(ChatColor.YELLOW + "Unloading world: " + worldName + "...");
        boolean unloaded = org.bukkit.Bukkit.unloadWorld(world, true); // Save before unloading to write level.dat

        if (!unloaded) {
            player.sendMessage(ChatColor.RED + "Failed to unload world. Check server logs.");
            return;
        }

        // Delete the world folder after a delay (to avoid file locking issues)
        File serverRoot = plugin.getDataFolder().getParentFile().getParentFile();
        File worldFolder = new File(serverRoot, worldName);
        // Paper migrates worlds to lobby_world/dimensions/minecraft/<worldname>
        File migratedFolder = new File(serverRoot, "lobby_world/dimensions/minecraft/" + worldName);

        player.sendMessage(ChatColor.YELLOW + "World unloaded. Deleting folders in 2 seconds...");
        plugin.getLogger().info("Scheduled deletion for: " + worldFolder.getAbsolutePath());
        plugin.getLogger().info("Also checking migrated location: " + migratedFolder.getAbsolutePath());

        org.bukkit.Bukkit.getScheduler().runTaskLater(plugin, () -> {
            plugin.getLogger().info("Deletion task running for: " + worldName);
            int deletedCount = 0;

            // Delete original world folder
            if (worldFolder.exists()) {
                plugin.getLogger().info("Attempting to delete original: " + worldFolder.getAbsolutePath());
                try {
                    deleteDirectory(worldFolder);
                    plugin.getLogger().info("Successfully deleted original world folder");
                    deletedCount++;
                } catch (Exception e) {
                    plugin.getLogger().severe("Could not delete original world folder: " + e.getMessage());
                    e.printStackTrace();
                }
            } else {
                plugin.getLogger().info("Original world folder does not exist: " + worldFolder.getAbsolutePath());
            }

            // Delete migrated world folder (Paper puts it here)
            if (migratedFolder.exists()) {
                plugin.getLogger().info("Attempting to delete migrated: " + migratedFolder.getAbsolutePath());
                try {
                    deleteDirectory(migratedFolder);
                    plugin.getLogger().info("Successfully deleted migrated world folder");
                    deletedCount++;
                } catch (Exception e) {
                    plugin.getLogger().severe("Could not delete migrated world folder: " + e.getMessage());
                    e.printStackTrace();
                }
            } else {
                plugin.getLogger().info("Migrated world folder does not exist: " + migratedFolder.getAbsolutePath());
            }

            if (deletedCount > 0) {
                player.sendMessage(ChatColor.GREEN + "Successfully deleted world data (" + deletedCount + " location(s))");
            } else {
                player.sendMessage(ChatColor.YELLOW + "No world folders found to delete.");
            }
        }, 40L); // Wait 2 seconds (40 ticks)
    }

    private void deleteDirectory(File directory) throws java.io.IOException {
        if (!directory.exists()) return;

        if (directory.isDirectory()) {
            File[] files = directory.listFiles();
            if (files != null) {
                for (File file : files) {
                    deleteDirectory(file);
                }
            }
        }

        // Try multiple times (Windows can have file locking delays)
        int attempts = 0;
        while (directory.exists() && attempts < 5) {
            if (directory.delete()) {
                return; // Success
            }
            attempts++;
            try {
                Thread.sleep(100); // Wait 100ms between attempts
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        if (directory.exists()) {
            throw new java.io.IOException("Failed to delete after " + attempts + " attempts: " + directory.getAbsolutePath());
        }
    }

    /**
     * Authoritative "is this world loaded" check. Bukkit.getWorld(name) only matches the legacy world
     * name and can return null for worlds Paper migrated into a dimensions/ folder or registered under a
     * namespaced key, so we fall back to scanning every loaded world and matching by name (case-
     * insensitively, and tolerating namespaced/path-suffixed names).
     */
    private org.bukkit.World findLoadedWorld(String worldName) {
        org.bukkit.World world = org.bukkit.Bukkit.getWorld(worldName);
        if (world != null) return world;
        for (org.bukkit.World loaded : org.bukkit.Bukkit.getWorlds()) {
            String name = loaded.getName();
            if (name.equalsIgnoreCase(worldName)
                    || name.endsWith("/" + worldName)
                    || name.endsWith(":" + worldName)) {
                return loaded;
            }
        }
        return null;
    }

    public File getTemplateSource(String worldName) {
        return loadedTemplates.get(worldName);
    }

    public void removeTrackedWorld(String worldName) {
        loadedTemplates.remove(worldName);
    }

    private void giveWand(Player player) {
        org.bukkit.inventory.ItemStack wand = new org.bukkit.inventory.ItemStack(Material.BLAZE_ROD);
        org.bukkit.inventory.meta.ItemMeta meta = wand.getItemMeta();
        meta.setDisplayName(ChatColor.GOLD + "TD Setup Wand");
        meta.setLore(java.util.Arrays.asList(ChatColor.YELLOW + "Admin Tool"));
        wand.setItemMeta(meta);
        player.getInventory().addItem(wand);
        player.sendMessage(ChatColor.GREEN + "Gave Setup Wand!");
    }
}
