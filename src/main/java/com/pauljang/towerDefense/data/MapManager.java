package com.pauljang.towerDefense.data;

import com.pauljang.towerDefense.TowerDefense;
import org.bukkit.configuration.file.YamlConfiguration;
import java.io.File;
import java.util.*;

public class MapManager {
    private final TowerDefense plugin;
    private final Map<String, MapData> loadedMaps = new HashMap<>();
    private final File templateRoot;

    public MapManager(TowerDefense plugin) {
        this.plugin = plugin;
        this.templateRoot = new File(plugin.getServer().getWorldContainer(), "GAME_WORLD_TEMPLATES");
        loadMaps();
    }

    public void loadMaps() {
        loadedMaps.clear();
        plugin.getLogger().info("Loading maps from: " + templateRoot.getAbsolutePath());
        loadFromSubfolder("SINGLE_PLAYER", true);
        loadFromSubfolder("MULTI_PLAYER", false);
        plugin.getLogger().info("Loaded " + loadedMaps.size() + " map templates:");
        for (MapData map : loadedMaps.values()) {
            plugin.getLogger().info("  - " + map.getDisplayName() + " [" + (map.isSinglePlayer() ? "Single Player" : "Multiplayer") + "]");
        }
    }

    private void loadFromSubfolder(String subfolderName, boolean isSinglePlayer) {
        File folder = new File(templateRoot, subfolderName);
        if (!folder.exists()) {
            folder.mkdirs();
            return;
        }

        File[] maps = folder.listFiles(File::isDirectory);
        if (maps == null) return;

        for (File mapDir : maps) {
            File configFile = new File(mapDir, "map.yml");
            if (!configFile.exists()) {
                plugin.getLogger().warning("Map folder " + mapDir.getName() + " is missing map.yml!");
                continue;
            }

            YamlConfiguration config = YamlConfiguration.loadConfiguration(configFile);
            String displayName = config.getString("display-name", mapDir.getName());
            String author = config.getString("author", "Unknown");
            
            MapData data = new MapData(mapDir.getName(), displayName, author, isSinglePlayer, mapDir);
            loadedMaps.put(data.getId(), data);
        }
    }

    public List<MapData> getAvailableMaps(boolean singlePlayer) {
        List<MapData> result = new ArrayList<>();
        for (MapData data : loadedMaps.values()) {
            if (data.isSinglePlayer() == singlePlayer) {
                result.add(data);
            }
        }
        // Stable, deterministic ordering (by display name, then id) so the map-vote panel is consistent
        // every time instead of reflecting HashMap iteration order.
        result.sort(java.util.Comparator
                .comparing((MapData m) -> m.getDisplayName().toLowerCase())
                .thenComparing(MapData::getId));
        return result;
    }

    public MapData getMap(String id) {
        return loadedMaps.get(id);
    }

    public static class MapData {
        private final String id;
        private final String displayName;
        private final String author;
        private final boolean isSinglePlayer;
        private final File directory;

        public MapData(String id, String displayName, String author, boolean isSinglePlayer, File directory) {
            this.id = id;
            this.displayName = displayName;
            this.author = author;
            this.isSinglePlayer = isSinglePlayer;
            this.directory = directory;
        }

        public String getId() { return id; }
        public String getDisplayName() { return displayName; }
        public String getAuthor() { return author; }
        public boolean isSinglePlayer() { return isSinglePlayer; }
        public File getDirectory() { return directory; }
    }
}
