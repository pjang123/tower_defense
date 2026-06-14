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
            player.sendMessage(ChatColor.RED + "Usage: /td <list|start|stop|status|plotmode [arena]|deleteplotmode [arena]|waypointmode [arena]|wand|clearwaypoints [arena]|saveconfig|loadworld|unloadworld|spawnmob|gui|upgrades|giveitems|givegold|givexp|setarena|challenge|accept|lobby|forfeit|forcestart|armageddon>");
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
                player.sendMessage(ChatColor.YELLOW + "/td armageddon " + ChatColor.WHITE + "- Force Armageddon mode in your match for testing (Admin)");
                break;

            case "gui":
                plugin.getMobManager().openMobSpawnerGUI(player);
                break;

            case "armageddon":
                if (!player.hasPermission("towerdefense.admin")) {
                    player.sendMessage(ChatColor.RED + "No permission.");
                    break;
                }
                Match armageddonMatch = gameManager.getPlayerMatch(player.getUniqueId());
                if (armageddonMatch == null) {
                    player.sendMessage(ChatColor.RED + "You must be in an active match to trigger Armageddon.");
                    break;
                }
                if (gameManager.forceArmageddon(armageddonMatch)) {
                    player.sendMessage(ChatColor.GREEN + "Forced Armageddon mode for your match.");
                } else {
                    player.sendMessage(ChatColor.RED + "Could not start Armageddon (match not active, or already in Armageddon).");
                }
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

        // The server root IS Bukkit's world container (two levels up from <root>/plugins/TowerDefense).
        // We stage the world here and let Bukkit.createWorld load it; Paper then migrates the live world
        // into <primary>/dimensions/minecraft/<name> on its own. We deliberately DO NOT pre-copy into the
        // dimensions folder — doing so makes Paper fail with "failed to migrate legacy world <name>".
        File serverRoot = plugin.getDataFolder().getParentFile().getParentFile();
        File rootCopy = new File(serverRoot, worldName);

        // Already loaded? createWorld would just hand back the existing instance, so short-circuit.
        org.bukkit.World existing = org.bukkit.Bukkit.getWorld(worldName);
        if (existing != null) {
            player.sendMessage(ChatColor.YELLOW + "World '" + worldName + "' is already loaded. Teleporting to spawn...");
            player.teleport(existing.getSpawnLocation());
            return;
        }

        // Resolve the source: SINGLE_PLAYER/ or MULTI_PLAYER/ template, else a world installed directly at
        // the server root. Tracked in loadedTemplates so edits sync back on unload.
        File templateWorld = resolveTemplateDir(serverRoot, worldName);
        boolean rootResident = isRootSource(serverRoot, worldName, templateWorld);

        // Stage a working copy at the server root unless one is already present (a root-resident world, or
        // a leftover staging copy from a previous load).
        if (!rootCopy.exists()) {
            if (templateWorld == null) {
                player.sendMessage(ChatColor.RED + "World '" + worldName + "' not found!");
                player.sendMessage(ChatColor.GRAY + "Looked in GAME_WORLD_TEMPLATES/{SINGLE_PLAYER,MULTI_PLAYER}/ and the server root "
                        + serverRoot.getAbsolutePath());
                return;
            }
            File levelDat = new File(templateWorld, "level.dat");
            if (!levelDat.exists()) {
                player.sendMessage(ChatColor.RED + "Invalid world: level.dat not found!");
                player.sendMessage(ChatColor.GRAY + "Expected: " + levelDat.getAbsolutePath());
                return;
            }
            player.sendMessage(ChatColor.YELLOW + "Copying template '" + worldName + "' to server root...");
            try {
                copyDirectoryNIO(templateWorld.toPath(), rootCopy.toPath());
                player.sendMessage(ChatColor.GREEN + "Copied template to server root.");
            } catch (java.io.IOException e) {
                player.sendMessage(ChatColor.RED + "Failed to copy template to server root: " + e.getMessage());
                plugin.getLogger().severe("Template copy failed for " + worldName + ": " + e.getMessage());
                e.printStackTrace();
                return;
            }
        } else if (rootResident) {
            player.sendMessage(ChatColor.YELLOW + "Loading server-root world '" + worldName + "' in place.");
        } else {
            player.sendMessage(ChatColor.YELLOW + "Existing copy found at server root. Reusing it.");
        }

        // Strip uid.dat so a stale world UUID can't collide with another loaded world (matches the proven
        // match-clone flow in GameManager).
        File uidFile = new File(rootCopy, "uid.dat");
        if (uidFile.exists() && uidFile.delete()) {
            plugin.getLogger().info("Removed stale uid.dat from " + rootCopy.getName());
        }

        // Clear any leftover Paper-migrated copy from a previous (possibly failed) load. If a stale folder
        // sits at <primary>/dimensions/minecraft/<name>, createWorld tries to migrate a legacy world into
        // an occupied path and fails ("failed to migrate legacy world").
        String primaryWorldName = org.bukkit.Bukkit.getWorlds().isEmpty()
                ? "lobby_world" : org.bukkit.Bukkit.getWorlds().get(0).getName();
        File staleDim = new File(serverRoot, primaryWorldName + "/dimensions/minecraft/" + worldName);
        if (staleDim.exists()) {
            try {
                deleteDirectoryNIO(staleDim.toPath());
                plugin.getLogger().info("Cleared stale migrated copy: " + staleDim.getAbsolutePath());
            } catch (java.io.IOException e) {
                player.sendMessage(ChatColor.YELLOW + "Warning: could not clear a stale migrated copy at "
                        + staleDim.getAbsolutePath() + " - load may fail. " + e.getMessage());
                plugin.getLogger().warning("Could not clear stale migrated copy for " + worldName + ": " + e.getMessage());
            }
        }

        // Load. Bukkit reads from the server-root copy; Paper migrates it into the dimensions folder.
        player.sendMessage(ChatColor.YELLOW + "Loading world: " + worldName + "...");
        try {
            org.bukkit.World world = new org.bukkit.WorldCreator(worldName)
                    .environment(org.bukkit.World.Environment.NORMAL)
                    .generateStructures(false)
                    .createWorld();
            if (world == null) {
                player.sendMessage(ChatColor.RED + "Failed to load world. Check server logs.");
                return;
            }

            // Track the source AFTER the world is registered so unload can sync changes back to it.
            if (templateWorld != null) {
                loadedTemplates.put(worldName, templateWorld);
            }
            plugin.getLogger().info("World loaded: " + world.getName() + " | tracked: " + loadedTemplates.keySet());

            player.sendMessage(ChatColor.GREEN + "Loaded world: " + worldName);
            player.sendMessage(ChatColor.YELLOW + "Edits sync back to its source on unload; auto-unloads when empty.");

            org.bukkit.Location spawnLoc = world.getSpawnLocation();
            player.sendMessage(ChatColor.GRAY + "World spawn: " + spawnLoc.getBlockX() + ", " + spawnLoc.getBlockY() + ", " + spawnLoc.getBlockZ());
            player.teleport(spawnLoc);
        } catch (Exception e) {
            player.sendMessage(ChatColor.RED + "Error loading world: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Locate a template world directory by name, checking SINGLE_PLAYER first then MULTI_PLAYER under
     * GAME_WORLD_TEMPLATES. Returns null if neither exists.
     */
    private File resolveTemplateDir(File serverRoot, String worldName) {
        File templatesRoot = new File(serverRoot, "GAME_WORLD_TEMPLATES");
        File single = new File(templatesRoot, "SINGLE_PLAYER" + File.separator + worldName);
        if (single.isDirectory()) return single;
        File multi = new File(templatesRoot, "MULTI_PLAYER" + File.separator + worldName);
        if (multi.isDirectory()) return multi;
        // Fallback: a world folder installed directly at the server root (next to lobby_world), outside
        // GAME_WORLD_TEMPLATES. Only accept it if it actually looks like a world (has level.dat) so a
        // stray/temp folder is never mistaken for a map. Such a folder is its own permanent home, so the
        // load/unload flow must NOT delete it (see isRootSource guards).
        File rootResident = new File(serverRoot, worldName);
        if (rootResident.isDirectory() && new File(rootResident, "level.dat").exists()) return rootResident;
        return null;
    }

    /**
     * True when the resolved source folder IS the world's home directly at the server root (rather than a
     * temporary staging copy created during load). When true, neither the load-time root cleanup nor the
     * unload-time post-check may delete it — it's the authoritative copy we sync changes back into.
     */
    private boolean isRootSource(File serverRoot, String worldName, File source) {
        if (source == null) return false;
        File rootFolder = new File(serverRoot, worldName);
        return source.getAbsoluteFile().equals(rootFolder.getAbsoluteFile());
    }

    /** True if both files resolve to the same absolute path (used to avoid copying a folder onto itself). */
    private boolean sameFolder(File a, File b) {
        return a != null && b != null && a.getAbsoluteFile().equals(b.getAbsoluteFile());
    }

    private void unloadWorld(Player player, String worldName) {
        org.bukkit.World world = org.bukkit.Bukkit.getWorld(worldName);

        if (world == null) {
            player.sendMessage(ChatColor.RED + "World '" + worldName + "' is not loaded!");
            return;
        }

        player.sendMessage(ChatColor.YELLOW + "Unloading world: " + worldName + "...");
        unloadTemplateWorld(world, player);
    }

    /**
     * Shared teardown for a loaded template world, used by both {@code /td unloadworld} (pass the issuing
     * player for chat feedback) and the auto-unload listener (pass {@code null} for log-only feedback):
     *  1. evacuate remaining players to lobby, then {@link org.bukkit.Bukkit#unloadWorld} with save=true;
     *  2. NIO sync-back of the live world data (Paper's migrated dimensions copy, else the server-root
     *     copy) into the resolved source (template or root-resident home);
     *  3. delete Paper's migrated copy at &lt;primaryWorld&gt;/dimensions/minecraft/&lt;name&gt;;
     *  4. post-check + force-delete any leftover staging copy of &lt;name&gt; at the server root (but keep a
     *     root-resident world that is its own permanent home).
     * The disk phase (2-4) runs ~2s later so Paper releases its file handles (Windows locking).
     *
     * @return true if the world was unloaded from Bukkit.
     */
    public boolean unloadTemplateWorld(org.bukkit.World world, Player feedback) {
        final String worldName = world.getName();
        File serverRoot = plugin.getDataFolder().getParentFile().getParentFile();
        // Paper migrates a loaded world into <primaryWorld>/dimensions/minecraft/<name> (usually lobby_world).
        String primaryWorldName = org.bukkit.Bukkit.getWorlds().isEmpty()
                ? "lobby_world" : org.bukkit.Bukkit.getWorlds().get(0).getName();
        File dimensionsFolder = new File(serverRoot, primaryWorldName + "/dimensions/minecraft/" + worldName);
        File rootFolder = new File(serverRoot, worldName);

        // Resolve the template to sync changes back into: prefer the tracked source, otherwise fall back
        // to searching GAME_WORLD_TEMPLATES so a manually-loaded world can still round-trip.
        File templateSource = loadedTemplates.get(worldName);
        if (templateSource == null) templateSource = resolveTemplateDir(serverRoot, worldName);

        // Untrack BEFORE we touch players so the auto-unload listener can't race this teardown.
        loadedTemplates.remove(worldName);

        // --- Step 1: Bukkit unload (evacuate players, then flush to disk) ---
        org.bukkit.World lobbyWorld = org.bukkit.Bukkit.getWorld("lobby_world");
        if (lobbyWorld == null) lobbyWorld = org.bukkit.Bukkit.getWorlds().get(0); // Fallback to first world

        for (org.bukkit.entity.Player p : world.getPlayers()) {
            p.teleport(lobbyWorld.getSpawnLocation());
            p.sendMessage(ChatColor.YELLOW + "World " + worldName + " is being unloaded.");
        }

        boolean unloaded = org.bukkit.Bukkit.unloadWorld(world, true); // save=true flushes level.dat/region to disk
        if (!unloaded) {
            // Re-track so the world isn't orphaned from the listener's perspective.
            if (templateSource != null) loadedTemplates.put(worldName, templateSource);
            plugin.getLogger().warning("Failed to unload world: " + worldName);
            if (feedback != null) feedback.sendMessage(ChatColor.RED + "Failed to unload world. Check server logs.");
            return false;
        }

        if (feedback != null) {
            feedback.sendMessage(ChatColor.YELLOW + "World unloaded. Syncing back to template and cleaning up in 2 seconds...");
        }
        plugin.getLogger().info("Scheduled teardown for: " + worldName
                + " | dimensions=" + dimensionsFolder.getAbsolutePath() + " | root=" + rootFolder.getAbsolutePath());

        final File fTemplate = templateSource;
        final Player fFeedback = feedback;
        // Defer the disk phase so Paper releases its file handles (Windows locking).
        org.bukkit.Bukkit.getScheduler().runTaskLater(plugin, () -> {
            // --- Step 2: Sync edits back to the source ---
            // After unloadWorld(save=true) a loaded world's live data is split across TWO places, because
            // Paper's legacy-world migration only MOVES the chunk directories:
            //   * level.dat + world-root metadata (data/, players/, icon.png, the_nether/the_end,
            //     overworld/data) are flushed to the server-root copy (rootFolder).
            //   * the overworld region/entities/poi chunks were migrated (moved) into
            //     <primaryWorld>/dimensions/minecraft/<name>/ (dimensionsFolder) and are flushed there.
            // So we must reconstruct the template's self-contained layout from BOTH. The old code copied
            // only dimensionsFolder onto the template root, which dropped level.dat entirely (its edits —
            // spawn, gamerules, time, border — were lost) and mislanded chunks at <template>/region instead
            // of <template>/dimensions/minecraft/overworld/region.
            if (fTemplate != null) {
                try {
                    // 2a. World-root metadata, including the freshly-flushed level.dat. Skip when the source
                    // IS the live root folder (a root-resident world already has level.dat updated in place).
                    if (rootFolder.exists() && !sameFolder(rootFolder, fTemplate)) {
                        copyDirectoryNIO(rootFolder.toPath(), fTemplate.toPath());
                        plugin.getLogger().info("Synced world-root metadata (level.dat, players, data) back to: "
                                + fTemplate.getAbsolutePath());
                    }
                    // 2b. Live overworld chunks back into the template's overworld dimension (the layout a
                    // self-contained world folder expects, and that the next load will read from).
                    if (dimensionsFolder.exists()) {
                        File templateOverworld = new File(fTemplate,
                                "dimensions" + File.separator + "minecraft" + File.separator + "overworld");
                        copyDirectoryNIO(dimensionsFolder.toPath(), templateOverworld.toPath());
                        plugin.getLogger().info("Synced overworld chunk data back to: "
                                + templateOverworld.getAbsolutePath());
                    }
                    if (fFeedback != null) fFeedback.sendMessage(ChatColor.GREEN
                            + "Synced world changes (blocks + level.dat) back to: " + fTemplate.getName());
                } catch (java.io.IOException e) {
                    plugin.getLogger().warning("Sync-back failed for " + worldName + ": " + e.getMessage());
                    if (fFeedback != null) fFeedback.sendMessage(ChatColor.YELLOW
                            + "Warning: could not sync world back to source: " + e.getMessage());
                    e.printStackTrace();
                }
            } else {
                plugin.getLogger().info("No source resolved for " + worldName + "; skipping sync-back.");
            }

            // --- Step 3: Dimensions cleanup (delete lobby_world/dimensions/minecraft/<name>) ---
            try {
                deleteDirectoryNIO(dimensionsFolder.toPath());
                plugin.getLogger().info("Deleted dimensions folder for " + worldName);
            } catch (java.io.IOException e) {
                plugin.getLogger().warning("Dimensions cleanup failed for " + worldName + ": " + e.getMessage());
                if (fFeedback != null) fFeedback.sendMessage(ChatColor.YELLOW + "Warning: could not delete dimensions folder: " + e.getMessage());
                e.printStackTrace();
            }

            // --- Post-Check Validation: aggressively ensure no leftover at server root ---
            // Skip when the root folder IS the map's permanent home (a server-root-resident world we just
            // synced changes back into) — only temp staging copies get force-deleted here.
            if (isRootSource(serverRoot, worldName, fTemplate)) {
                plugin.getLogger().info("Server-root world '" + worldName + "' kept at root (its permanent home).");
            } else {
                try {
                    if (rootFolder.exists()) {
                        plugin.getLogger().info("Leftover root folder detected for " + worldName
                                + ", force-deleting: " + rootFolder.getAbsolutePath());
                        deleteDirectoryNIO(rootFolder.toPath());
                    }
                } catch (java.io.IOException e) {
                    plugin.getLogger().warning("Root post-check cleanup failed for " + worldName + ": " + e.getMessage());
                    if (fFeedback != null) fFeedback.sendMessage(ChatColor.YELLOW + "Warning: leftover root folder could not be removed: " + e.getMessage());
                    e.printStackTrace();
                }
            }

            plugin.getLogger().info("World '" + worldName + "' fully unloaded and cleaned up.");
            if (fFeedback != null) fFeedback.sendMessage(ChatColor.GREEN + "World '" + worldName + "' fully unloaded and cleaned up.");
        }, 40L); // Wait 2 seconds (40 ticks)
        return true;
    }

    /**
     * Recursively copy a directory tree using NIO. Existing destination files are replaced. {@code uid.dat}
     * is intentionally skipped so cloned/synced worlds never collide on Bukkit's world UUID. Robust against
     * Windows file-locking via {@link java.nio.file.Files#copy}.
     */
    private void copyDirectoryNIO(java.nio.file.Path source, java.nio.file.Path target) throws java.io.IOException {
        java.nio.file.Files.walkFileTree(source, new java.nio.file.SimpleFileVisitor<java.nio.file.Path>() {
            @Override
            public java.nio.file.FileVisitResult preVisitDirectory(java.nio.file.Path dir,
                    java.nio.file.attribute.BasicFileAttributes attrs) throws java.io.IOException {
                java.nio.file.Files.createDirectories(target.resolve(source.relativize(dir).toString()));
                return java.nio.file.FileVisitResult.CONTINUE;
            }

            @Override
            public java.nio.file.FileVisitResult visitFile(java.nio.file.Path file,
                    java.nio.file.attribute.BasicFileAttributes attrs) throws java.io.IOException {
                if (file.getFileName().toString().equals("uid.dat")) {
                    return java.nio.file.FileVisitResult.CONTINUE; // avoid duplicate-world-UUID conflicts
                }
                java.nio.file.Path dest = target.resolve(source.relativize(file).toString());
                java.nio.file.Files.copy(file, dest, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                return java.nio.file.FileVisitResult.CONTINUE;
            }
        });
    }

    /**
     * Recursively delete a directory tree using NIO, deepest paths first. Retries a few times to ride out
     * transient Windows file locks; throws only if the root still exists after all attempts.
     */
    private void deleteDirectoryNIO(java.nio.file.Path path) throws java.io.IOException {
        if (path == null || !java.nio.file.Files.exists(path)) return;

        java.io.IOException lastError = null;
        for (int attempt = 0; attempt < 5; attempt++) {
            try (java.util.stream.Stream<java.nio.file.Path> walk = java.nio.file.Files.walk(path)) {
                java.util.List<java.nio.file.Path> paths =
                        walk.sorted(java.util.Comparator.reverseOrder()).collect(java.util.stream.Collectors.toList());
                lastError = null;
                for (java.nio.file.Path p : paths) {
                    try {
                        java.nio.file.Files.deleteIfExists(p);
                    } catch (java.io.IOException e) {
                        lastError = e;
                    }
                }
            }
            if (!java.nio.file.Files.exists(path)) return;
            try {
                Thread.sleep(100); // brief pause before retrying for Windows file-lock release
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        if (java.nio.file.Files.exists(path)) {
            throw new java.io.IOException("Failed to delete after retries: " + path
                    + (lastError != null ? " (" + lastError.getMessage() + ")" : ""));
        }
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
