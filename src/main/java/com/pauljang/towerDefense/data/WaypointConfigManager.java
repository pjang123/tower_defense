package com.pauljang.towerDefense.data;

import com.pauljang.towerDefense.TowerDefense;
import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class WaypointConfigManager {

    private final TowerDefense plugin;
    private File file;
    private FileConfiguration config;

    public WaypointConfigManager(TowerDefense plugin) {
        this.plugin = plugin;
        setupConfig();
    }

    private void setupConfig() {
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }
        file = new File(plugin.getDataFolder(), "waypoints.yml");
        if (!file.exists()) {
            try {
                file.createNewFile();
                plugin.getLogger().info("Successfully generated waypoints.yml!");
            } catch (IOException e) {
                plugin.getLogger().severe("Could not create waypoints.yml!");
            }
        }
        config = YamlConfiguration.loadConfiguration(file);
    }

    // Graph-based methods
    public void addWaypoint(String arena, String id, Location loc, List<String> nextIds) {
        String path = "waypoints." + arena + "." + id;
        config.set(path + ".world", loc.getWorld().getName());
        config.set(path + ".x", loc.getX());
        config.set(path + ".y", loc.getY());
        config.set(path + ".z", loc.getZ());
        config.set(path + ".next", nextIds);
        saveFile();
    }

    public void addConnection(String arena, String fromId, String toId) {
        String path = "waypoints." + arena + "." + fromId + ".next";
        List<String> next = config.getStringList(path);
        if (!next.contains(toId)) {
            next.add(toId);
            config.set(path, next);
            saveFile();
        }
    }

    public Map<String, TDWaypoint> getWaypointGraph(String arena) {
        Map<String, TDWaypoint> graph = new HashMap<>();
        String arenaPath = "waypoints." + arena;
        if (!config.contains(arenaPath)) return graph;

        for (String key : config.getConfigurationSection(arenaPath).getKeys(false)) {
            String path = arenaPath + "." + key;
            String worldName = config.getString(path + ".world");
            double x = config.getDouble(path + ".x");
            double y = config.getDouble(path + ".y");
            double z = config.getDouble(path + ".z");
            List<String> next = config.getStringList(path + ".next");

            org.bukkit.World world = org.bukkit.Bukkit.getWorld(worldName);
            if (world != null) {
                Location loc = new Location(world, x, y, z);
                graph.put(key, new TDWaypoint(key, loc, next));
            }
        }
        return graph;
    }

    // Legacy / fallback list-based methods for backwards compatibility
    public void addWaypoint(Location loc) {
        addWaypoint("1", loc);
    }

    public void addWaypoint(String arena, Location loc) {
        // Convert to sequential string ID
        int nextId = 0;
        String arenaPath = "waypoints." + arena;
        if (config.contains(arenaPath)) {
            nextId = config.getConfigurationSection(arenaPath).getKeys(false).size();
        }
        String idStr = String.valueOf(nextId);
        
        // If there was a previous waypoint, connect it to this one
        if (nextId > 0) {
            String prevIdStr = String.valueOf(nextId - 1);
            addWaypoint(arena, idStr, loc, new ArrayList<>());
            addConnection(arena, prevIdStr, idStr);
        } else {
            addWaypoint(arena, idStr, loc, new ArrayList<>());
        }
    }

    public void clearAllWaypoints() {
        clearAllWaypoints("1");
    }

    public void clearAllWaypoints(String arena) {
        config.set("waypoints." + arena, null);
        saveFile();
    }

    public List<Location> getWaypoints() {
        return getWaypoints("1");
    }

    public List<Location> getWaypoints(String arena) {
        List<Location> list = new ArrayList<>();
        Map<String, TDWaypoint> graph = getWaypointGraph(arena);
        
        // Sort keys numerically if possible for backwards compatibility
        List<String> keys = new ArrayList<>(graph.keySet());
        keys.sort((a, b) -> {
            try {
                return Integer.compare(Integer.parseInt(a), Integer.parseInt(b));
            } catch (NumberFormatException e) {
                return a.compareTo(b);
            }
        });
        
        for (String key : keys) {
            list.add(graph.get(key).getLocation());
        }
        return list;
    }

    private void saveFile() {
        try {
            config.save(file);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}