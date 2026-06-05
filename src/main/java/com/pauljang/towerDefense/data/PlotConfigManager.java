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
    public void savePlot(String arena, Location pos1, Location pos2) {
        String plotId = UUID.randomUUID().toString().substring(0, 8);
        String path = "plots." + plotId + ".";

        config.set(path + "arena", arena);
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

    // Get the arena ID of a plot
    public String getPlotArena(String plotId) {
        if (config.getConfigurationSection("plots") == null) return "1";
        return config.getString("plots." + plotId + ".arena", "1");
    }

    // Check if the newly selected area overlaps with ANY saved plot
    public boolean isPlotOverlapping(Location newPos1, Location newPos2) {
        if (config.getConfigurationSection("plots") == null) return false;

        String newWorldName = normalizeWorldName(newPos1.getWorld().getName());
        int newMinX = Math.min(newPos1.getBlockX(), newPos2.getBlockX());
        int newMaxX = Math.max(newPos1.getBlockX(), newPos2.getBlockX());
        int newMinZ = Math.min(newPos1.getBlockZ(), newPos2.getBlockZ());
        int newMaxZ = Math.max(newPos1.getBlockZ(), newPos2.getBlockZ());

        for (String plotId : config.getConfigurationSection("plots").getKeys(false)) {
            String path = "plots." + plotId + ".";

            String savedWorld = normalizeWorldName(config.getString(path + "pos1.world"));
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

    public String getPlotAt(Location loc) {
        if (config.getConfigurationSection("plots") == null) return null;

        String worldName = normalizeWorldName(loc.getWorld().getName());
        int x = loc.getBlockX();
        int y = loc.getBlockY();
        int z = loc.getBlockZ();

        for (String plotId : config.getConfigurationSection("plots").getKeys(false)) {
            String path = "plots." + plotId + ".";

            String savedWorld = normalizeWorldName(config.getString(path + "pos1.world"));
            if (!worldName.equals(savedWorld)) continue;

            int x1 = config.getInt(path + "pos1.x");
            int y1 = config.getInt(path + "pos1.y");
            int z1 = config.getInt(path + "pos1.z");

            int x2 = config.getInt(path + "pos2.x");
            int y2 = config.getInt(path + "pos2.y");
            int z2 = config.getInt(path + "pos2.z");

            int minX = Math.min(x1, x2);
            int maxX = Math.max(x1, x2);
            int minY = Math.min(y1, y2) - 1;
            int maxBaseY = Math.max(y1, y2);
            int maxY = maxBaseY + 3;

            com.pauljang.towerDefense.towers.Tower tower = plugin.getTowerManager().getTower(plotId);
            if (tower != null) {
                int towerHeight = 3;
                if (tower.getStructureSize() != null) {
                    towerHeight = tower.getStructureSize().getBlockY();
                }
                maxY = maxBaseY + towerHeight + 2;
            }

            int minZ = Math.min(z1, z2);
            int maxZ = Math.max(z1, z2);

            if (x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ) {
                return plotId;
            }
        }
        return null;
    }

    public Location getPlotCenter(String plotId) {
        String path = "plots." + plotId + ".";
        if (config.getConfigurationSection("plots") == null || !config.contains(path)) return null;

        String worldName = normalizeWorldName(config.getString(path + "pos1.world"));
        int x1 = config.getInt(path + "pos1.x");
        int y1 = config.getInt(path + "pos1.y");
        int z1 = config.getInt(path + "pos1.z");

        int x2 = config.getInt(path + "pos2.x");
        int z2 = config.getInt(path + "pos2.z");

        org.bukkit.World world = org.bukkit.Bukkit.getWorld(worldName);
        if (world == null) {
            world = org.bukkit.Bukkit.getWorld("game_world");
        }
        if (world == null) return null;

        double centerX = (x1 + x2) / 2.0 + 0.5;
        double centerY = y1;
        double centerZ = (z1 + z2) / 2.0 + 0.5;

        return new Location(world, centerX, centerY, centerZ);
    }

    // Delete a plot by ID and remove any tower on it
    public void deletePlot(String plotId) {
        if (config.getConfigurationSection("plots") == null) return;
        if (plugin.getTowerManager() != null) {
            plugin.getTowerManager().removeTower(plotId);
        }
        config.set("plots." + plotId, null);
        saveFile();
    }

    // Get the bounding box/corners of a plot
    public Location[] getPlotBounds(String plotId) {
        String path = "plots." + plotId + ".";
        if (config.getConfigurationSection("plots") == null || !config.contains(path)) return null;
        String worldName = normalizeWorldName(config.getString(path + "pos1.world"));
        org.bukkit.World world = org.bukkit.Bukkit.getWorld(worldName);
        if (world == null) {
            world = org.bukkit.Bukkit.getWorld("game_world");
        }
        if (world == null) return null;
        int x1 = config.getInt(path + "pos1.x");
        int y1 = config.getInt(path + "pos1.y");
        int z1 = config.getInt(path + "pos1.z");
        int x2 = config.getInt(path + "pos2.x");
        int y2 = config.getInt(path + "pos2.y");
        int z2 = config.getInt(path + "pos2.z");
        Location pos1 = new Location(world, Math.min(x1, x2), y1, Math.min(z1, z2));
        Location pos2 = new Location(world, Math.max(x1, x2), y2, Math.max(z1, z2));
        return new Location[]{pos1, pos2};
    }

    private String normalizeWorldName(String name) {
        if ("game_world_template".equals(name)) {
            return "game_world";
        }
        return name;
    }

    private void saveFile() {
        try {
            config.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save to plots.yml!");
        }
    }
}