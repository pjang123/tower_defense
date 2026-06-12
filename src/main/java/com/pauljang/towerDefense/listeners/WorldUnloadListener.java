package com.pauljang.towerDefense.listeners;

import com.pauljang.towerDefense.TowerDefense;
import com.pauljang.towerDefense.core.TDCommand;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerQuitEvent;

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
        if (tdCommand.getTemplateSource(world.getName()) == null) {
            // Not a template world, ignore
            return;
        }

        // Schedule check for next tick (to ensure the player has fully left), then delegate the entire
        // teardown — full sync-back to the template plus NIO dimensions/root cleanup — to TDCommand so the
        // auto-unload path behaves identically to /td unloadworld. Pass null feedback for log-only output.
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (world.getPlayers().isEmpty()) {
                plugin.getLogger().info("No players left in template world " + world.getName() + ", auto-unloading...");
                tdCommand.unloadTemplateWorld(world, null);
            }
        }, 1L); // Wait 1 tick
    }
}
