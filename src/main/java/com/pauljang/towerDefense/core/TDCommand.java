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
            player.sendMessage(ChatColor.RED + "Usage: /td <list|start|stop|status|plotmode [arena]|deleteplotmode [arena]|waypointmode [arena]|wand|clearwaypoints [arena]|saveconfig|loadworld|unloadworld|spawnmob|gui|upgrades|giveitems|setarena|challenge|accept|lobby|forfeit|forcestart>");
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
                player.sendMessage(ChatColor.YELLOW + "/td forcestart " + ChatColor.WHITE + "- Start the countdown with 2+ queued players");
                player.sendMessage(ChatColor.YELLOW + "/td wand " + ChatColor.WHITE + "- Gives you the Setup Wand");
                player.sendMessage(ChatColor.YELLOW + "/td plotmode [arena] " + ChatColor.WHITE + "- Toggle Plot Setup mode for an arena");
                player.sendMessage(ChatColor.YELLOW + "/td deleteplotmode [arena] " + ChatColor.WHITE + "- Toggle Plot Deletion mode for an arena");
                player.sendMessage(ChatColor.YELLOW + "/td waypointmode [arena] " + ChatColor.WHITE + "- Toggle Waypoint Setup mode for an arena");
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
                    player.sendMessage(ChatColor.YELLOW + "Right-click any block within a plot to delete it");
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
                org.bukkit.World lobbyWorld = org.bukkit.Bukkit.getWorld("lobby_world");
                if (lobbyWorld != null) player.teleport(lobbyWorld.getSpawnLocation());
                gameManager.giveLobbyItems(player);
                player.sendMessage(ChatColor.GREEN + "Returned to lobby.");
                break;

            case "forfeit":
                gameManager.forfeit(player);
                break;

            case "status":
                player.sendMessage(ChatColor.YELLOW + "State: " + gameManager.getCurrentState().name());
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

        // Check if world is already loaded
        org.bukkit.World existingWorld = org.bukkit.Bukkit.getWorld(worldName);
        plugin.getLogger().info("Bukkit.getWorld result: " + (existingWorld != null ? existingWorld.getName() : "null"));

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
