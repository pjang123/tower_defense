package com.pauljang.towerDefense.data;

import com.pauljang.towerDefense.TowerDefense;
import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

public class PlotConfigManager {

    private final TowerDefense plugin;
    private File file;
    private FileConfiguration config;

    public PlotConfigManager(TowerDefense plugin) {
        this.plugin = plugin;
        setupConfig();
    }

    // Initialize the plots.yml file
    private void setupConfig() {
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }
        file = new File(plugin.getDataFolder(), "plots.yml");
        if (!file.exists()) {
            try {
                file.createNewFile();
                plugin.getLogger().info("Successfully generated a new plots.yml file!");
            } catch (IOException e) {
                plugin.getLogger().severe("Could not create plots.yml!");
            }
        }
        config = YamlConfiguration.loadConfiguration(file);
    }

    // Save a new plot
    public void savePlot(Location pos1, Location pos2) {
        String plotId = UUID.randomUUID().toString().substring(0, 8);
        String path = "plots." + plotId + ".";

        config.set(path + "pos1.world", pos1.getWorld().getName());
        config.set(path + "pos1.x", pos1.getBlockX());
        config.set(path + "pos1.y", pos1.getBlockY());
        config.set(path + "pos1.z", pos1.getBlockZ());

        config.set(path + "pos2.world", pos2.getWorld().getName());
        config.set(path + "pos2.x", pos2.getBlockX());
        config.set(path + "pos2.y", pos2.getBlockY());
        config.set(path + "pos2.z", pos2.getBlockZ());

        saveFile();
    }

    // Check if the newly selected area overlaps with ANY saved plot
    public boolean isPlotOverlapping(Location newPos1, Location newPos2) {
        if (config.getConfigurationSection("plots") == null) return false;

        String newWorldName = newPos1.getWorld().getName();
        int newMinX = Math.min(newPos1.getBlockX(), newPos2.getBlockX());
        int newMaxX = Math.max(newPos1.getBlockX(), newPos2.getBlockX());
        int newMinZ = Math.min(newPos1.getBlockZ(), newPos2.getBlockZ());
        int newMaxZ = Math.max(newPos1.getBlockZ(), newPos2.getBlockZ());

        for (String plotId : config.getConfigurationSection("plots").getKeys(false)) {
            String path = "plots." + plotId + ".";

            String savedWorld = config.getString(path + "pos1.world");
            if (!newWorldName.equals(savedWorld)) continue;

            int savedX1 = config.getInt(path + "pos1.x");
            int savedZ1 = config.getInt(path + "pos1.z");
            int savedX2 = config.getInt(path + "pos2.x");
            int savedZ2 = config.getInt(path + "pos2.z");

            int savedMinX = Math.min(savedX1, savedX2);
            int savedMaxX = Math.max(savedX1, savedX2);
            int savedMinZ = Math.min(savedZ1, savedZ2);
            int savedMaxZ = Math.max(savedZ1, savedZ2);

            // Touching boundaries are fine, but actual overlap is not.
            // Using strict inequality (<, >) allows for shared edges/corners.
            boolean isOverlapping = (newMinX < savedMaxX && newMaxX > savedMinX &&
                                     newMinZ < savedMaxZ && newMaxZ > savedMinZ);

            if (isOverlapping) {
                return true;
            }
        }
        return false;
    }

    private void saveFile() {
        try {
            config.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save to plots.yml!");
        }
    }
}