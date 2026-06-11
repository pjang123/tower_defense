package com.pauljang.towerDefense.data;

import com.pauljang.towerDefense.TowerDefense;
import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.io.File;
import java.io.IOException;
import java.util.*;
import com.pauljang.towerDefense.core.Match;

public class WaypointConfigManager {

    private final TowerDefense plugin;
    private File file;
    private FileConfiguration config;

    // Per-match waypoints: Match -> (ArenaID -> (WaypointID -> TDWaypoint))
    private final Map<Match, Map<String, Map<String, TDWaypoint>>> matchWaypoints = new HashMap<>();

    public WaypointConfigManager(TowerDefense plugin) {
        this.plugin = plugin;
        setupConfig();
    }

    public void loadMapConfig(Match match, File mapFile) {
        FileConfiguration mapConfig = YamlConfiguration.loadConfiguration(mapFile);
        Map<String, Map<String, TDWaypoint>> arenaWaypoints = new HashMap<>();

        if (mapConfig.contains("waypoints")) {
            for (String arena : mapConfig.getConfigurationSection("waypoints").getKeys(false)) {
                // Skip old-format waypoints (those with 'world' key directly under them)
                // Only process arena-grouped waypoints (those with nested waypoint IDs)
                if (mapConfig.contains("waypoints." + arena + ".world")) {
                    // This is an old-format waypoint, skip it
                    plugin.getLogger().info("Skipping old-format waypoint ID '" + arena + "'");
                    continue;
                }

                Map<String, TDWaypoint> waypoints = new HashMap<>();
                org.bukkit.configuration.ConfigurationSection arenaSection = mapConfig.getConfigurationSection("waypoints." + arena);

                if (arenaSection == null) {
                    plugin.getLogger().warning("Arena section is null for: " + arena);
                    continue;
                }

                for (String wpId : arenaSection.getKeys(false)) {
                    String path = "waypoints." + arena + "." + wpId;

                    // Read coordinates individually (not as Location object)
                    double x = mapConfig.getDouble(path + ".x");
                    double y = mapConfig.getDouble(path + ".y");
                    double z = mapConfig.getDouble(path + ".z");
                    List<String> nextIds = mapConfig.getStringList(path + ".next");

                    // Create location in match world
                    Location loc = new Location(match.getWorld(), x, y, z);
                    waypoints.put(wpId, new TDWaypoint(wpId, loc, nextIds));
                }
                arenaWaypoints.put(arena, waypoints);
                plugin.getLogger().info("Loaded " + waypoints.size() + " waypoints for arena " + arena);
            }
        } else {
            plugin.getLogger().warning("No waypoints section found in " + mapFile.getName());
        }
        matchWaypoints.put(match, arenaWaypoints);
    }

    public void unloadMatch(Match match) {
        matchWaypoints.remove(match);
    }

    /**
     * Mirrors a template's waypoints into the in-memory global config, remapped to the live match world's
     * name. The singleton systems that read waypoints by arena from the global config — tower targeting
     * (TowerManager.getWaypointGraph/getWaypoints), spell track particles, castle holograms, the
     * waypoint setup overlay — would otherwise find nothing for the freshly cloned match world. In-memory
     * only; never written back to waypoints.yml on disk.
     *
     * Only one match is active at a time for the singleton systems, so this replaces any prior waypoints.
     */
    public void loadGlobalForMatch(File mapFile, org.bukkit.World world) {
        FileConfiguration mapConfig = YamlConfiguration.loadConfiguration(mapFile);
        config.set("waypoints", null); // single active match: drop any waypoints from a previous match
        if (mapConfig.contains("waypoints")) {
            for (String arena : mapConfig.getConfigurationSection("waypoints").getKeys(false)) {
                // Skip old-format waypoints (those with a 'world' key directly under them)
                if (mapConfig.contains("waypoints." + arena + ".world")) continue;
                org.bukkit.configuration.ConfigurationSection arenaSection = mapConfig.getConfigurationSection("waypoints." + arena);
                if (arenaSection == null) continue;
                for (String wpId : arenaSection.getKeys(false)) {
                    String src = "waypoints." + arena + "." + wpId;
                    String dst = "waypoints." + arena + "." + wpId;
                    config.set(dst + ".world", world.getName());
                    config.set(dst + ".x", mapConfig.getDouble(src + ".x"));
                    config.set(dst + ".y", mapConfig.getDouble(src + ".y"));
                    config.set(dst + ".z", mapConfig.getDouble(src + ".z"));
                    config.set(dst + ".next", mapConfig.getStringList(src + ".next"));
                }
                plugin.getLogger().info("Mirrored waypoints for arena " + arena + " into the global config for world " + world.getName());
            }
        } else {
            plugin.getLogger().warning("No waypoints section found in " + mapFile.getName() + " to mirror into global config");
        }
    }

    /** Clears the in-memory global waypoints once a match ends. Does not touch waypoints.yml on disk. */
    public void clearGlobal() {
        config.set("waypoints", null);
    }

    public Map<String, TDWaypoint> getWaypointGraph(Match match, String arena) {
        return matchWaypoints.getOrDefault(match, Collections.emptyMap()).getOrDefault(arena, Collections.emptyMap());
    }

    public Location getWaypoint(Match match, String arena, String id) {
        TDWaypoint wp = getWaypointGraph(match, arena).get(id);
        return wp != null ? wp.getLocation() : null;
    }

    public List<Location> getWaypoints(Match match, String arena) {
        List<Location> list = new ArrayList<>();
        Map<String, TDWaypoint> graph = getWaypointGraph(match, arena);
        if (graph.isEmpty()) return list;

        String currentId = "0";
        while (currentId != null && graph.containsKey(currentId)) {
            TDWaypoint wp = graph.get(currentId);
            list.add(wp.getLocation());
            currentId = wp.getNextIds().isEmpty() ? null : wp.getNextIds().get(0);
        }
        return list;
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
        // Check if we're in a template world (direct editing) or a copy of one
        org.bukkit.World world = loc.getWorld();
        File worldFolder = world.getWorldFolder();
        boolean isTemplateWorld = worldFolder.getAbsolutePath().contains("GAME_WORLD_TEMPLATES");

        // Check if this is a template copy loaded via /td loadworld
        File templateSource = plugin.getCommand("td") != null ?
            ((com.pauljang.towerDefense.core.TDCommand)plugin.getCommand("td").getExecutor()).getTemplateSource(world.getName()) : null;

        if (templateSource != null) {
            worldFolder = templateSource;
            isTemplateWorld = true;
        }

        plugin.getLogger().info("Saving waypoint - World folder: " + worldFolder.getAbsolutePath());
        plugin.getLogger().info("Is template world: " + isTemplateWorld);

        FileConfiguration targetConfig;
        File targetFile;

        if (isTemplateWorld) {
            // Direct editing - save to world's waypoints.yml
            targetFile = new File(worldFolder, "waypoints.yml");
            targetConfig = YamlConfiguration.loadConfiguration(targetFile);
            plugin.getLogger().info("Saving waypoint directly to template world: " + targetFile.getAbsolutePath());
        } else {
            // Legacy mode - save to global config
            targetConfig = config;
            targetFile = file;
        }

        String path = "waypoints." + arena + "." + id;
        targetConfig.set(path + ".world", loc.getWorld().getName());
        targetConfig.set(path + ".x", loc.getX());
        targetConfig.set(path + ".y", loc.getY());
        targetConfig.set(path + ".z", loc.getZ());
        targetConfig.set(path + ".next", nextIds);

        try {
            targetConfig.save(targetFile);
            if (isTemplateWorld) {
                // Also update in-memory config for consistency
                config = targetConfig;
            }
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save waypoint to " + targetFile.getAbsolutePath());
            e.printStackTrace();
        }
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
            
            // Normalize game_world_template to game_world
            if ("game_world_template".equals(worldName)) {
                worldName = "game_world";
            }
            
            double x = config.getDouble(path + ".x");
            double y = config.getDouble(path + ".y");
            double z = config.getDouble(path + ".z");
            List<String> next = config.getStringList(path + ".next");

            org.bukkit.World world = org.bukkit.Bukkit.getWorld(worldName);
            if (world == null) {
                // Fallback to active game world
                world = org.bukkit.Bukkit.getWorld("game_world");
            }
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
        // Try to find which world this arena's waypoints belong to
        String firstWaypointPath = "waypoints." + arena;
        String worldName = null;

        if (config.contains(firstWaypointPath)) {
            org.bukkit.configuration.ConfigurationSection arenaSection = config.getConfigurationSection(firstWaypointPath);
            if (arenaSection != null) {
                String firstKey = arenaSection.getKeys(false).stream().findFirst().orElse(null);
                if (firstKey != null) {
                    worldName = config.getString(firstWaypointPath + "." + firstKey + ".world");
                }
            }
        }

        config.set("waypoints." + arena, null);

        // If we found a world and it's a template world, also clear there
        if (worldName != null) {
            org.bukkit.World world = org.bukkit.Bukkit.getWorld(worldName);
            if (world != null) {
                File worldFolder = world.getWorldFolder();
                boolean isTemplateWorld = worldFolder.getAbsolutePath().contains("GAME_WORLD_TEMPLATES");

                if (isTemplateWorld) {
                    File targetFile = new File(worldFolder, "waypoints.yml");
                    if (targetFile.exists()) {
                        FileConfiguration targetConfig = YamlConfiguration.loadConfiguration(targetFile);
                        targetConfig.set("waypoints." + arena, null);
                        try {
                            targetConfig.save(targetFile);
                            plugin.getLogger().info("Cleared waypoints from template world: " + targetFile.getAbsolutePath());
                        } catch (IOException e) {
                            plugin.getLogger().severe("Could not clear waypoints from " + targetFile.getAbsolutePath());
                            e.printStackTrace();
                        }
                    }
                }
            }
        }

        saveFile(); // Also save to global config for backwards compatibility
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

    public void exportToMap(org.bukkit.World world, File targetFile) {
        YamlConfiguration exportConfig = new YamlConfiguration();
        if (config.contains("waypoints")) {
            for (String arena : config.getConfigurationSection("waypoints").getKeys(false)) {
                for (String wpId : config.getConfigurationSection("waypoints." + arena).getKeys(false)) {
                    String savedWorld = config.getString("waypoints." + arena + "." + wpId + ".world");
                    if (world.getName().equals(savedWorld)) {
                        exportConfig.set("waypoints." + arena + "." + wpId, config.get("waypoints." + arena + "." + wpId));
                    }
                }
            }
        }
        try {
            exportConfig.save(targetFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void saveFile() {
        try {
            config.save(file);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}