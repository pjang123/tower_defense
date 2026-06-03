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
        // Find the next available ID (0, 1, 2, etc.)
        int nextId = 0;
        if (config.contains("waypoints")) {
            nextId = config.getConfigurationSection("waypoints").getKeys(false).size();
        }

        String path = "waypoints." + nextId;
        config.set(path + ".world", loc.getWorld().getName());
        // We save double values (X, Y, Z) so the mob can walk to the exact center of a block
        config.set(path + ".x", loc.getX());
        config.set(path + ".y", loc.getY());
        config.set(path + ".z", loc.getZ());

        saveFile();
    }

    // A handy method in case you mess up and need to clear the path
    public void clearAllWaypoints() {
        config.set("waypoints", null);
        saveFile();
    }

    private void saveFile() {
        try {
            config.save(file);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}