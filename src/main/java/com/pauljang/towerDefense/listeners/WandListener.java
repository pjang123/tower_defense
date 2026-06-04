package com.pauljang.towerDefense.listeners;

import com.pauljang.towerDefense.TowerDefense;
import com.pauljang.towerDefense.data.PlotConfigManager;
import com.pauljang.towerDefense.data.WaypointConfigManager;
import com.pauljang.towerDefense.data.TDWaypoint;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class WandListener implements Listener {

    private final TowerDefense plugin;

    public WandListener(TowerDefense plugin) {
        this.plugin = plugin;
        startPreviewTask();
    }

    private void startPreviewTask() {
        org.bukkit.Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player player : org.bukkit.Bukkit.getOnlinePlayers()) {
                SetupManager sm = plugin.getSetupManager();
                SetupState state = sm.getState(player.getUniqueId());
                
                // If they are holding the wand
                ItemStack item = player.getInventory().getItemInMainHand();
                if (item != null && item.getType() == Material.BLAZE_ROD && item.hasItemMeta()) {
                    org.bukkit.inventory.meta.ItemMeta meta = item.getItemMeta();
                    if (meta != null && meta.hasDisplayName() && meta.getDisplayName().contains("TD Setup Wand")) {
                        
                        if (state == SetupState.AWAITING_PLOT) {
                            org.bukkit.block.Block targetBlock = player.getTargetBlockExact(15);
                            if (targetBlock != null && targetBlock.getType() != Material.AIR) {
                                Location blockLoc = targetBlock.getLocation();
                                int size = sm.getPlotSize(player.getUniqueId());
                                drawPlotHighlight(blockLoc, size);
                            }
                        } else if (state == SetupState.WAYPOINT_MODE) {
                            String arena = sm.getEditingArena(player.getUniqueId());
                            drawWaypointGraphHighlight(player, arena);
                        }
                        
                    }
                }
            }
        }, 0L, 5L); // every 250ms
    }

    private void drawPlotHighlight(Location center, int size) {
        int radius = size / 2;
        double minX = center.getBlockX() - radius;
        double maxX = center.getBlockX() + radius + 1.0;
        double minZ = center.getBlockZ() - radius;
        double maxZ = center.getBlockZ() + radius + 1.0;
        double y = center.getBlockY() + 1.02; // slightly above block
        
        org.bukkit.World world = center.getWorld();
        org.bukkit.Color color = (size == 5) ? org.bukkit.Color.YELLOW : org.bukkit.Color.AQUA;
        org.bukkit.Particle.DustOptions dust = new org.bukkit.Particle.DustOptions(color, 1.0f);
        
        for (double x = minX; x <= maxX; x += 0.5) {
            world.spawnParticle(org.bukkit.Particle.DUST, new Location(world, x, y, minZ), 1, 0, 0, 0, 0, dust);
            world.spawnParticle(org.bukkit.Particle.DUST, new Location(world, x, y, maxZ), 1, 0, 0, 0, 0, dust);
        }
        for (double z = minZ; z <= maxZ; z += 0.5) {
            world.spawnParticle(org.bukkit.Particle.DUST, new Location(world, minX, y, z), 1, 0, 0, 0, 0, dust);
            world.spawnParticle(org.bukkit.Particle.DUST, new Location(world, maxX, y, z), 1, 0, 0, 0, 0, dust);
        }
    }

    private void drawWaypointGraphHighlight(Player player, String arena) {
        Map<String, TDWaypoint> graph = plugin.getWaypointConfigManager().getWaypointGraph(arena);
        String selectedWpId = plugin.getSetupManager().getSelectedWaypointId(player.getUniqueId());
        
        for (TDWaypoint wp : graph.values()) {
            Location loc = wp.getLocation();
            org.bukkit.World world = loc.getWorld();
            
            boolean isSelected = wp.getId().equals(selectedWpId);
            org.bukkit.Color dotColor = isSelected ? org.bukkit.Color.fromRGB(255, 215, 0) : org.bukkit.Color.LIME;
            org.bukkit.Particle.DustOptions dotDust = new org.bukkit.Particle.DustOptions(dotColor, 2.0f);
            
            // vertical column showing waypoint node
            for (double dy = 0; dy <= 2.0; dy += 0.5) {
                world.spawnParticle(org.bukkit.Particle.DUST, loc.clone().add(0, dy - 0.8, 0), 1, 0, 0, 0, 0, dotDust);
            }
            
            if (isSelected) {
                world.spawnParticle(org.bukkit.Particle.HAPPY_VILLAGER, loc.clone().add(0, 1.5, 0), 1, 0.1, 0.1, 0.1, 0);
            }
            
            // connections paths
            for (String nextId : wp.getNextIds()) {
                TDWaypoint nextWp = graph.get(nextId);
                if (nextWp != null) {
                    Location targetLoc = nextWp.getLocation();
                    drawMovingParticleLine(loc.clone().add(0, 0.2, 0), targetLoc.clone().add(0, 0.2, 0), isSelected ? org.bukkit.Color.ORANGE : org.bukkit.Color.AQUA);
                }
            }
        }
    }

    private void drawMovingParticleLine(Location start, Location end, org.bukkit.Color color) {
        double distance = start.distance(end);
        org.bukkit.util.Vector direction = end.toVector().subtract(start.toVector()).normalize();
        org.bukkit.World world = start.getWorld();
        org.bukkit.Particle.DustOptions dust = new org.bukkit.Particle.DustOptions(color, 1.0f);
        
        long time = System.currentTimeMillis();
        double offset = (time / 150.0) % 1.0; // animation flow speed
        
        for (double d = offset; d < distance; d += 1.0) {
            Location point = start.clone().add(direction.clone().multiply(d));
            world.spawnParticle(org.bukkit.Particle.DUST, point, 1, 0, 0, 0, 0, dust);
        }
    }

    private TDWaypoint findWaypointAt(String arena, Location blockLoc) {
        Map<String, TDWaypoint> graph = plugin.getWaypointConfigManager().getWaypointGraph(arena);
        for (TDWaypoint wp : graph.values()) {
            Location wpLoc = wp.getLocation();
            if (wpLoc.getBlockX() == blockLoc.getBlockX() &&
                wpLoc.getBlockY() == blockLoc.getBlockY() + 1 &&
                wpLoc.getBlockZ() == blockLoc.getBlockZ()) {
                return wp;
            }
        }
        return null;
    }

    @EventHandler
    public void onWandClick(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItem();

        if (item == null || item.getType() != Material.BLAZE_ROD || !item.hasItemMeta()) return;
        
        org.bukkit.inventory.meta.ItemMeta meta = item.getItemMeta();
        if (!meta.hasDisplayName() || !meta.getDisplayName().contains("TD Setup Wand")) return;

        event.setCancelled(true);
        Action action = event.getAction();
        UUID uuid = player.getUniqueId();
        SetupManager setupManager = plugin.getSetupManager();
        SetupState state = setupManager.getState(uuid);
        String arena = setupManager.getEditingArena(uuid);

        // A. Toggle sizes for Plot placement on Right-click
        if (state == SetupState.AWAITING_PLOT && (action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK)) {
            int currentSize = setupManager.getPlotSize(uuid);
            int nextSize = (currentSize == 3) ? 5 : 3;
            setupManager.setPlotSize(uuid, nextSize);
            player.sendMessage(ChatColor.YELLOW + "Switched plot placement size to: " + ChatColor.GOLD + nextSize + "x" + nextSize);
            player.playSound(player.getLocation(), org.bukkit.Sound.UI_BUTTON_CLICK, 0.5f, 1.2f);
            return;
        }

        // B. Clear waypoint selection on Right-click air / non-waypoint block
        if (state == SetupState.WAYPOINT_MODE && (action == Action.RIGHT_CLICK_AIR || 
            (action == Action.RIGHT_CLICK_BLOCK && event.getClickedBlock() != null && findWaypointAt(arena, event.getClickedBlock().getLocation()) == null))) {
            setupManager.clearSelectedWaypointId(uuid);
            player.sendMessage(ChatColor.YELLOW + "Cleared waypoint selection.");
            player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_CHEST_CLOSE, 0.5f, 0.8f);
            return;
        }

        if (event.getClickedBlock() == null) return;
        Location clickedLoc = event.getClickedBlock().getLocation();

        // C. Plot Placement Flow
        if (state == SetupState.AWAITING_PLOT) {
            if (action == Action.LEFT_CLICK_BLOCK) {
                int size = setupManager.getPlotSize(uuid);
                int radius = size / 2;
                
                Location pos1 = clickedLoc.clone().subtract(radius, 0, radius);
                Location pos2 = clickedLoc.clone().add(radius, 0, radius);
                
                if (plugin.getPlotConfigManager().isPlotOverlapping(pos1, pos2)) {
                    player.sendMessage(ChatColor.RED + "Error: This " + size + "x" + size + " plot overlaps with an existing one!");
                    player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 0.5f, 1.0f);
                    return;
                }
                
                plugin.getPlotConfigManager().savePlot(arena, pos1, pos2);
                player.sendMessage(ChatColor.GREEN + "Successfully set and saved a " + size + "x" + size + " plot centered at " + clickedLoc.getBlockX() + ", " + clickedLoc.getBlockY() + ", " + clickedLoc.getBlockZ() + " for Arena " + arena + "!");
                player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_STONE_PLACE, 1.0f, 1.0f);
            }
        }

        // D. Split Waypoint graph creation flow
        else if (state == SetupState.WAYPOINT_MODE) {
            TDWaypoint clickedWp = findWaypointAt(arena, clickedLoc);
            
            if (action == Action.LEFT_CLICK_BLOCK) {
                if (clickedWp != null) {
                    // Selected existing waypoint
                    setupManager.setSelectedWaypointId(uuid, clickedWp.getId());
                    player.sendMessage(ChatColor.GREEN + "Selected waypoint: " + ChatColor.GOLD + "WP " + clickedWp.getId());
                    player.playSound(player.getLocation(), org.bukkit.Sound.UI_BUTTON_CLICK, 0.5f, 1.2f);
                } else {
                    // Create new waypoint and link it
                    Location wpLoc = clickedLoc.clone().add(0.5, 1.0, 0.5);
                    Map<String, TDWaypoint> graph = plugin.getWaypointConfigManager().getWaypointGraph(arena);
                    int nextIntId = 0;
                    while (graph.containsKey(String.valueOf(nextIntId))) {
                        nextIntId++;
                    }
                    String newWpId = String.valueOf(nextIntId);
                    
                    plugin.getWaypointConfigManager().addWaypoint(arena, newWpId, wpLoc, new ArrayList<>());
                    
                    String selectedId = setupManager.getSelectedWaypointId(uuid);
                    if (selectedId != null && graph.containsKey(selectedId)) {
                        plugin.getWaypointConfigManager().addConnection(arena, selectedId, newWpId);
                        player.sendMessage(ChatColor.LIGHT_PURPLE + "Created waypoint " + ChatColor.GOLD + "WP " + newWpId + ChatColor.LIGHT_PURPLE + " and connected " + ChatColor.GOLD + "WP " + selectedId + ChatColor.LIGHT_PURPLE + " -> " + ChatColor.GOLD + "WP " + newWpId);
                    } else {
                        player.sendMessage(ChatColor.LIGHT_PURPLE + "Created waypoint " + ChatColor.GOLD + "WP " + newWpId);
                    }
                    
                    setupManager.setSelectedWaypointId(uuid, newWpId);
                    player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.5f, 1.5f);
                }
            } else if (action == Action.RIGHT_CLICK_BLOCK) {
                if (clickedWp != null) {
                    String selectedId = setupManager.getSelectedWaypointId(uuid);
                    if (selectedId != null) {
                        if (selectedId.equals(clickedWp.getId())) {
                            player.sendMessage(ChatColor.RED + "Cannot connect a waypoint to itself!");
                            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 0.5f, 1.0f);
                            return;
                        }
                        plugin.getWaypointConfigManager().addConnection(arena, selectedId, clickedWp.getId());
                        player.sendMessage(ChatColor.LIGHT_PURPLE + "Connected " + ChatColor.GOLD + "WP " + selectedId + ChatColor.LIGHT_PURPLE + " -> " + ChatColor.GOLD + "WP " + clickedWp.getId());
                        setupManager.setSelectedWaypointId(uuid, clickedWp.getId());
                        player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.5f, 1.5f);
                    } else {
                        setupManager.setSelectedWaypointId(uuid, clickedWp.getId());
                        player.sendMessage(ChatColor.GREEN + "Selected waypoint: " + ChatColor.GOLD + "WP " + clickedWp.getId());
                        player.playSound(player.getLocation(), org.bukkit.Sound.UI_BUTTON_CLICK, 0.5f, 1.2f);
                    }
                }
            }
        }
    }

    @EventHandler
    public void onPlayerPlaceTower(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getClickedBlock() == null) return;

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
}
