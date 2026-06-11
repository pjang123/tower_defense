package com.pauljang.towerDefense.listeners;

import com.pauljang.towerDefense.TowerDefense;
import com.pauljang.towerDefense.core.TDCommand;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.io.File;

public class WorldUnloadListener implements Listener {

    private final TowerDefense plugin;

    public WorldUnloadListener(TowerDefense plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerChangeWorld(PlayerChangedWorldEvent event) {
        checkAndUnloadEmptyTemplateWorld(event.getFrom());
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        // Check the world they were in when they quit
        checkAndUnloadEmptyTemplateWorld(event.getPlayer().getWorld());
    }

    private void checkAndUnloadEmptyTemplateWorld(World world) {
        // Check if this world is a loaded template copy
        TDCommand tdCommand = (TDCommand) plugin.getCommand("td").getExecutor();
        File templateSource = tdCommand.getTemplateSource(world.getName());

        if (templateSource == null) {
            // Not a template world, ignore
            return;
        }

        // Schedule check for next tick (to ensure player has fully left)
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (world.getPlayers().isEmpty()) {
                plugin.getLogger().info("No players left in template world " + world.getName() + ", auto-unloading...");

                File serverRoot = plugin.getDataFolder().getParentFile().getParentFile();
                File worldFolder = new File(serverRoot, world.getName());
                File levelDatCopy = new File(worldFolder, "level.dat");
                File levelDatTemplate = new File(templateSource, "level.dat");

                // Save level.dat back to template
                if (levelDatCopy.exists()) {
                    try {
                        java.nio.file.Files.copy(levelDatCopy.toPath(), levelDatTemplate.toPath(),
                            java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                        plugin.getLogger().info("Saved level.dat changes back to template: " + templateSource.getName());
                    } catch (Exception e) {
                        plugin.getLogger().warning("Could not save level.dat to template: " + e.getMessage());
                    }
                }

                // Unload the world
                boolean unloaded = Bukkit.unloadWorld(world, true); // Save before unloading

                if (unloaded) {
                    plugin.getLogger().info("Successfully unloaded world: " + world.getName());

                    // Remove from tracking first
                    tdCommand.removeTrackedWorld(world.getName());

                    // Schedule deletion after a delay (file locking issues on Windows)
                    final String finalWorldName = world.getName();
                    File migratedFolder = new File(serverRoot, "lobby_world/dimensions/minecraft/" + finalWorldName);

                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        int deletedCount = 0;

                        // Delete original folder
                        if (worldFolder.exists()) {
                            try {
                                deleteDirectory(worldFolder);
                                plugin.getLogger().info("Deleted original temporary world folder: " + finalWorldName);
                                deletedCount++;
                            } catch (Exception e) {
                                plugin.getLogger().warning("Could not delete original world folder: " + e.getMessage());
                                e.printStackTrace();
                            }
                        }

                        // Delete migrated folder (Paper migration)
                        if (migratedFolder.exists()) {
                            try {
                                deleteDirectory(migratedFolder);
                                plugin.getLogger().info("Deleted migrated world folder: " + finalWorldName);
                                deletedCount++;
                            } catch (Exception e) {
                                plugin.getLogger().warning("Could not delete migrated world folder: " + e.getMessage());
                                e.printStackTrace();
                            }
                        }

                        if (deletedCount > 0) {
                            plugin.getLogger().info("Auto-cleanup completed for " + finalWorldName + " (" + deletedCount + " location(s))");
                        }
                    }, 60L); // Wait 3 seconds (60 ticks) for file handles to release
                } else {
                    plugin.getLogger().warning("Failed to unload world: " + world.getName());
                }
            }
        }, 1L); // Wait 1 tick
    }

    private void deleteDirectory(File directory) throws java.io.IOException {
        if (directory.isDirectory()) {
            File[] files = directory.listFiles();
            if (files != null) {
                for (File file : files) {
                    deleteDirectory(file);
                }
            }
        }
        if (!directory.delete()) {
            throw new java.io.IOException("Failed to delete: " + directory.getAbsolutePath());
        }
    }
}
