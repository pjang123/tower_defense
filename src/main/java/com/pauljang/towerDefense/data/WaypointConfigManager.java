package com.pauljang.towerDefense.data;

import com.pauljang.towerDefense.TowerDefense;
import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;

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

    public void addWaypoint(Location loc) {
        addWaypoint("1", loc);
    }

    public void addWaypoint(String arena, Location loc) {
        int nextId = 0;
        String arenaPath = "waypoints." + arena;
        if (config.contains(arenaPath)) {
            nextId = config.getConfigurationSection(arenaPath).getKeys(false).size();
        }

        String path = arenaPath + "." + nextId;
        config.set(path + ".world", loc.getWorld().getName());
        config.set(path + ".x", loc.getX());
        config.set(path + ".y", loc.getY());
        config.set(path + ".z", loc.getZ());

        saveFile();
    }

    public void clearAllWaypoints() {
        clearAllWaypoints("1");
    }

    public void clearAllWaypoints(String arena) {
        config.set("waypoints." + arena, null);
        saveFile();
    }

    public java.util.List<Location> getWaypoints() {
        return getWaypoints("1");
    }

    public java.util.List<Location> getWaypoints(String arena) {
        java.util.List<Location> waypoints = new java.util.ArrayList<>();
        String arenaPath = "waypoints." + arena;
        if (!config.contains(arenaPath)) return waypoints;

        for (String key : config.getConfigurationSection(arenaPath).getKeys(false)) {
            String path = arenaPath + "." + key;
            String worldName = config.getString(path + ".world");
            double x = config.getDouble(path + ".x");
            double y = config.getDouble(path + ".y");
            double z = config.getDouble(path + ".z");

            org.bukkit.World world = org.bukkit.Bukkit.getWorld(worldName);
            if (world != null) {
                waypoints.add(new Location(world, x, y, z));
            }
        }
        return waypoints;
    }

    private void saveFile() {
        try {
            config.save(file);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}