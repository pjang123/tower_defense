package com.pauljang.towerDefense.data;

import com.pauljang.towerDefense.TowerDefense;
import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;
import com.pauljang.towerDefense.core.Match;

public class PlotConfigManager {

    private final TowerDefense plugin;
    private File file;
    private FileConfiguration config;
    
    // Per-match plots: Match -> (PlotID -> Location)
    private final Map<Match, Map<String, Location>> matchPlots = new HashMap<>();

    public PlotConfigManager(TowerDefense plugin) {
        this.plugin = plugin;
        setupConfig();
    }

    public void loadMapConfig(Match match, File mapFile) {
        FileConfiguration mapConfig = YamlConfiguration.loadConfiguration(mapFile);
        Map<String, Location> plots = new HashMap<>();
        if (mapConfig.contains("plots")) {
            for (String key : mapConfig.getConfigurationSection("plots").getKeys(false)) {
                // Since plots were saved with world names, we need to remap them to the match world
                double x = mapConfig.getDouble("plots." + key + ".pos1.x");
                double y = mapConfig.getDouble("plots." + key + ".pos1.y");
                double z = mapConfig.getDouble("plots." + key + ".pos1.z");
                plots.put(key, new Location(match.getWorld(), x, y, z));
            }
            plugin.getLogger().info("Loaded " + plots.size() + " plots for match " + match.getMatchId());
        } else {
            plugin.getLogger().warning("No plots section found in " + mapFile.getName());
        }
        matchPlots.put(match, plots);
    }

    public void unloadMatch(Match match) {
        matchPlots.remove(match);
    }

    /**
     * Mirrors a template's plots into the in-memory global config, remapped to the live match world's
     * name. The singleton tower/placement systems (getPlotAt, getPlotArena, getPlotBounds, isPlotOverlapping,
     * getPlotCenter, ...) all read this global config and match plots by world name. Without this, the
     * freshly cloned match world ("match_xxxx") has no plots registered against it, so getPlotAt() returns
     * null and players cannot place towers. In-memory only — this never writes back to plots.yml on disk.
     *
     * Only one match is active at a time for the singleton systems, so this replaces any prior plots.
     */
    public void loadGlobalForMatch(File mapFile, org.bukkit.World world) {
        FileConfiguration mapConfig = YamlConfiguration.loadConfiguration(mapFile);
        config.set("plots", null); // single active match: drop any plots from a previous match
        if (mapConfig.contains("plots")) {
            org.bukkit.configuration.ConfigurationSection plotsSection = mapConfig.getConfigurationSection("plots");
            for (String key : plotsSection.getKeys(false)) {
                String src = "plots." + key + ".";
                String dst = "plots." + key + ".";
                config.set(dst + "arena", mapConfig.getString(src + "arena", "1"));
                config.set(dst + "pos1.world", world.getName());
                config.set(dst + "pos1.x", mapConfig.getInt(src + "pos1.x"));
                config.set(dst + "pos1.y", mapConfig.getInt(src + "pos1.y"));
                config.set(dst + "pos1.z", mapConfig.getInt(src + "pos1.z"));
                config.set(dst + "pos2.world", world.getName());
                config.set(dst + "pos2.x", mapConfig.getInt(src + "pos2.x"));
                config.set(dst + "pos2.y", mapConfig.getInt(src + "pos2.y"));
                config.set(dst + "pos2.z", mapConfig.getInt(src + "pos2.z"));
            }
            plugin.getLogger().info("Mirrored " + plotsSection.getKeys(false).size() + " plots into the global config for world " + world.getName());
        } else {
            plugin.getLogger().warning("No plots section found in " + mapFile.getName() + " to mirror into global config");
        }
    }

    /** Clears the in-memory global plots once a match ends. Does not touch plots.yml on disk. */
    public void clearGlobal() {
        config.set("plots", null);
    }

    public Map<String, Location> getPlots(Match match) {
        return matchPlots.getOrDefault(match, Collections.emptyMap());
    }

    public Location getPlot(Match match, String plotId) {
        return getPlots(match).get(plotId);
    }

    // Initialize the in-memory plot config. Plots live ONLY in each world's own plots.yml (template
    // folder); the plugin data folder must not accumulate a global plots.yml. We keep a purely
    // in-memory working set (mirrored from the active match/template on demand) and remove any stray
    // plugin-folder file left over from older versions.
    private void setupConfig() {
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }
        file = new File(plugin.getDataFolder(), "plots.yml");
        if (file.exists()) {
            // Preserve any legacy contents in memory for this session, then delete the stray file.
            config = YamlConfiguration.loadConfiguration(file);
            if (file.delete()) {
                plugin.getLogger().info("Removed stray plugin-folder plots.yml; plots now live only in their world folders.");
            }
        } else {
            config = new YamlConfiguration();
        }
    }

    // Save a new plot
    public void savePlot(String arena, Location pos1, Location pos2) {
        String plotId = UUID.randomUUID().toString().substring(0, 8);

        // Check if we're in a template world (direct editing) or a copy of one
        org.bukkit.World world = pos1.getWorld();
        File worldFolder = world.getWorldFolder();
        boolean isTemplateWorld = worldFolder.getAbsolutePath().contains("GAME_WORLD_TEMPLATES");

        // Check if this is a template copy loaded via /td loadworld
        File templateSource = plugin.getCommand("td") != null ?
            ((com.pauljang.towerDefense.core.TDCommand)plugin.getCommand("td").getExecutor()).getTemplateSource(world.getName()) : null;

        if (templateSource != null) {
            worldFolder = templateSource;
            isTemplateWorld = true;
        }

        plugin.getLogger().info("Saving plot - World folder: " + worldFolder.getAbsolutePath());
        plugin.getLogger().info("Is template world: " + isTemplateWorld);

        FileConfiguration targetConfig;
        File targetFile;

        if (isTemplateWorld) {
            // Direct editing - save to world's plots.yml
            targetFile = new File(worldFolder, "plots.yml");
            targetConfig = YamlConfiguration.loadConfiguration(targetFile);
            plugin.getLogger().info("Saving plot directly to template world: " + targetFile.getAbsolutePath());
        } else {
            // Legacy mode - save to global config
            targetConfig = config;
            targetFile = file;
        }

        String path = "plots." + plotId + ".";
        targetConfig.set(path + "arena", arena);
        targetConfig.set(path + "pos1.world", pos1.getWorld().getName());
        targetConfig.set(path + "pos1.x", pos1.getBlockX());
        targetConfig.set(path + "pos1.y", pos1.getBlockY());
        targetConfig.set(path + "pos1.z", pos1.getBlockZ());

        targetConfig.set(path + "pos2.world", pos2.getWorld().getName());
        targetConfig.set(path + "pos2.x", pos2.getBlockX());
        targetConfig.set(path + "pos2.y", pos2.getBlockY());
        targetConfig.set(path + "pos2.z", pos2.getBlockZ());

        try {
            if (isTemplateWorld) {
                targetConfig.save(targetFile);   // the world's own plots.yml
                config = targetConfig;           // keep the in-memory working set in sync for overlap detection
            }
            // Non-template (legacy) edits remain in the in-memory config only and are written out to a
            // world folder by /td saveconfig — never to the plugin data folder.
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save plot to " + targetFile.getAbsolutePath());
            e.printStackTrace();
        }
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

        // Find which world this plot belongs to
        String worldName = config.getString("plots." + plotId + ".pos1.world");
        org.bukkit.World world = org.bukkit.Bukkit.getWorld(worldName);

        config.set("plots." + plotId, null);

        if (world != null) {
            File worldFolder = world.getWorldFolder();
            boolean isTemplateWorld = worldFolder.getAbsolutePath().contains("GAME_WORLD_TEMPLATES");

            if (isTemplateWorld) {
                // Save directly to template world
                File targetFile = new File(worldFolder, "plots.yml");
                try {
                    config.save(targetFile);
                    plugin.getLogger().info("Deleted plot from template world: " + targetFile.getAbsolutePath());
                } catch (IOException e) {
                    plugin.getLogger().severe("Could not delete plot from " + targetFile.getAbsolutePath());
                    e.printStackTrace();
                }
            }
        }

        saveFile(); // Also save to global config for backwards compatibility
    }

    /**
     * All plot IDs whose pos1 world matches the given world (normalized). Used by the setup wand's
     * delete-plot mode to render every existing plot so the admin can see the full layout at once.
     */
    public java.util.List<String> getPlotIdsInWorld(org.bukkit.World world) {
        java.util.List<String> result = new java.util.ArrayList<>();
        org.bukkit.configuration.ConfigurationSection section = config.getConfigurationSection("plots");
        if (section == null || world == null) return result;
        String target = normalizeWorldName(world.getName());
        for (String plotId : section.getKeys(false)) {
            String savedWorld = normalizeWorldName(config.getString("plots." + plotId + ".pos1.world"));
            if (target.equals(savedWorld)) {
                result.add(plotId);
            }
        }
        return result;
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

    public void exportToMap(org.bukkit.World world, File targetFile) {
        YamlConfiguration exportConfig = new YamlConfiguration();
        if (config.contains("plots")) {
            for (String key : config.getConfigurationSection("plots").getKeys(false)) {
                String savedWorld = config.getString("plots." + key + ".pos1.world");
                if (world.getName().equals(savedWorld)) {
                    exportConfig.set("plots." + key, config.get("plots." + key));
                }
            }
        }
        try {
            exportConfig.save(targetFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Intentionally a no-op. The plugin data folder must never hold a global plots.yml — plots are
    // persisted to each world's own plots.yml (template folder) by savePlot/deletePlot, and exported
    // via /td saveconfig. The in-memory `config` is the live working set.
    private void saveFile() {
        // no-op: see method comment
    }
}