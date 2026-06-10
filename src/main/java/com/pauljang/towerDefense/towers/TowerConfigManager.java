package com.pauljang.towerDefense.towers;

import com.pauljang.towerDefense.TowerDefense;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Set;
import java.util.TreeMap;

/**
 * Loads and serves tower definitions from towers.yaml.
 * Towers either have a single "default" upgrade path (levels:) or
 * multiple named upgrade paths (paths:).
 */
public class TowerConfigManager {

    public static final String DEFAULT_PATH = "default";

    private final TowerDefense plugin;
    private final Map<String, TowerDefinition> definitions = new LinkedHashMap<>();

    public TowerConfigManager(TowerDefense plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        definitions.clear();

        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }
        File file = new File(plugin.getDataFolder(), "towers.yaml");
        if (!file.exists()) {
            plugin.saveResource("towers.yaml", false);
        }
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);

        // Fall back to the bundled towers.yaml for keys missing from an older user copy
        InputStream bundled = plugin.getResource("towers.yaml");
        if (bundled != null) {
            config.setDefaults(YamlConfiguration.loadConfiguration(new InputStreamReader(bundled, StandardCharsets.UTF_8)));
        }

        for (String id : config.getKeys(false)) {
            ConfigurationSection section = config.getConfigurationSection(id);
            if (section == null) continue;
            TowerDefinition def = parseDefinition(id, section);
            if (def != null) {
                definitions.put(id, def);
            }
        }

        for (TowerType type : TowerType.values()) {
            if (!definitions.containsKey(type.name().toLowerCase())) {
                plugin.getLogger().warning("towers.yaml has no entry for tower type " + type.name() + "; enum fallback stats will be used.");
            }
        }
        plugin.getLogger().info("Loaded " + definitions.size() + " tower definitions from towers.yaml");
    }

    private TowerDefinition parseDefinition(String id, ConfigurationSection section) {
        String name = section.getString("name", id);
        String structureFile = section.getString("structure_file", id);

        Material baseMaterial = null;
        String materialName = section.getString("base_material");
        if (materialName != null) {
            baseMaterial = Material.matchMaterial(materialName);
            if (baseMaterial == null) {
                plugin.getLogger().warning("Unknown base_material '" + materialName + "' for tower '" + id + "' in towers.yaml");
            }
        }

        // Towers may declare a shared base via "levels:" (stored under DEFAULT_PATH) and/or
        // branching "paths:". Branching towers (turret/bombardier/beehive) carry both: the base
        // is their Level 1, and each named path starts at Level 2. We inject the base Level 1 into
        // every path so per-path lookups (cost/range/damage) resolve correctly at Level 1 too.
        Map<String, NavigableMap<Integer, TowerLevelStats>> paths = new LinkedHashMap<>();
        ConfigurationSection levels = section.getConfigurationSection("levels");
        ConfigurationSection pathsSection = section.getConfigurationSection("paths");

        NavigableMap<Integer, TowerLevelStats> baseLevels = new TreeMap<>();
        if (levels != null) {
            baseLevels = parseLevels(id, DEFAULT_PATH, levels);
            paths.put(DEFAULT_PATH, baseLevels);
        }

        if (pathsSection != null) {
            for (String pathName : pathsSection.getKeys(false)) {
                ConfigurationSection pathLevels = pathsSection.getConfigurationSection(pathName);
                if (pathLevels != null) {
                    NavigableMap<Integer, TowerLevelStats> specificPathLevels = parseLevels(id, pathName, pathLevels);
                    if (!baseLevels.isEmpty() && !specificPathLevels.containsKey(1)) {
                        specificPathLevels.put(1, baseLevels.get(1));
                    }
                    paths.put(pathName, specificPathLevels);
                }
            }
        }
        if (paths.isEmpty()) {
            plugin.getLogger().warning("Tower '" + id + "' in towers.yaml has no levels or paths; entry skipped.");
            return null;
        }
        return new TowerDefinition(id, name, structureFile, baseMaterial, paths);
    }

    private NavigableMap<Integer, TowerLevelStats> parseLevels(String id, String pathName, ConfigurationSection levelsSection) {
        NavigableMap<Integer, TowerLevelStats> levels = new TreeMap<>();
        for (String levelKey : levelsSection.getKeys(false)) {
            int level;
            try {
                level = Integer.parseInt(levelKey);
            } catch (NumberFormatException e) {
                plugin.getLogger().warning("Non-numeric level '" + levelKey + "' for tower '" + id + "' path '" + pathName + "' in towers.yaml; skipped.");
                continue;
            }
            ConfigurationSection levelSection = levelsSection.getConfigurationSection(levelKey);
            if (levelSection == null) continue;

            Map<String, Object> extras = new HashMap<>();
            for (String statKey : levelSection.getKeys(false)) {
                switch (statKey) {
                    case "cost", "range", "damage", "cooldown" -> { }
                    default -> extras.put(statKey, levelSection.get(statKey));
                }
            }
            levels.put(level, new TowerLevelStats(
                    levelSection.getInt("cost", 0),
                    levelSection.getDouble("range", 0.0),
                    levelSection.getDouble("damage", 0.0),
                    levelSection.getLong("cooldown", 20L),
                    Collections.unmodifiableMap(extras)));
        }
        return levels;
    }

    // --- Lookups ---

    public TowerDefinition getDefinition(String id) {
        return definitions.get(id);
    }

    public TowerDefinition getDefinition(TowerType type) {
        return definitions.get(type.name().toLowerCase());
    }

    public Set<String> getTowerIds() {
        return Collections.unmodifiableSet(definitions.keySet());
    }

    private TowerLevelStats getLevelStats(TowerType type, String path, int level) {
        TowerDefinition def = getDefinition(type);
        return def == null ? null : def.getLevel(path == null ? DEFAULT_PATH : path, level);
    }

    public int getCost(TowerType type, int level, int def) {
        return getCost(type, DEFAULT_PATH, level, def);
    }

    public int getCost(TowerType type, String path, int level, int def) {
        TowerLevelStats stats = getLevelStats(type, path, level);
        return stats == null ? def : stats.cost();
    }

    public double getRange(TowerType type, int level, double def) {
        return getRange(type, DEFAULT_PATH, level, def);
    }

    public double getRange(TowerType type, String path, int level, double def) {
        TowerLevelStats stats = getLevelStats(type, path, level);
        return stats == null ? def : stats.range();
    }

    public double getDamage(TowerType type, int level, double def) {
        return getDamage(type, DEFAULT_PATH, level, def);
    }

    public double getDamage(TowerType type, String path, int level, double def) {
        TowerLevelStats stats = getLevelStats(type, path, level);
        return stats == null ? def : stats.damage();
    }

    public long getCooldown(TowerType type, int level, long def) {
        return getCooldown(type, DEFAULT_PATH, level, def);
    }

    public long getCooldown(TowerType type, String path, int level, long def) {
        TowerLevelStats stats = getLevelStats(type, path, level);
        return stats == null ? def : stats.cooldown();
    }

    public int getMaxLevel(TowerType type) {
        return getMaxLevel(type, DEFAULT_PATH);
    }

    public int getMaxLevel(TowerType type, String path) {
        TowerDefinition def = getDefinition(type);
        return def == null ? 3 : def.getMaxLevel(path == null ? DEFAULT_PATH : path);
    }

    public int getStat(TowerType type, int level, String key, int def) {
        return getStat(type, DEFAULT_PATH, level, key, def);
    }

    public int getStat(TowerType type, String path, int level, String key, int def) {
        TowerLevelStats stats = getLevelStats(type, path, level);
        return stats == null ? def : stats.getInt(key, def);
    }

    public double getStat(TowerType type, int level, String key, double def) {
        return getStat(type, DEFAULT_PATH, level, key, def);
    }

    public double getStat(TowerType type, String path, int level, String key, double def) {
        TowerLevelStats stats = getLevelStats(type, path, level);
        return stats == null ? def : stats.getDouble(key, def);
    }

    public long getStat(TowerType type, int level, String key, long def) {
        return getStat(type, DEFAULT_PATH, level, key, def);
    }

    public long getStat(TowerType type, String path, int level, String key, long def) {
        TowerLevelStats stats = getLevelStats(type, path, level);
        return stats == null ? def : stats.getLong(key, def);
    }

    public boolean getStat(TowerType type, int level, String key, boolean def) {
        return getStat(type, DEFAULT_PATH, level, key, def);
    }

    public boolean getStat(TowerType type, String path, int level, String key, boolean def) {
        TowerLevelStats stats = getLevelStats(type, path, level);
        return stats == null ? def : stats.getBoolean(key, def);
    }

    public String getStructureFile(TowerType type) {
        TowerDefinition def = getDefinition(type);
        return def == null ? type.name().toLowerCase() : def.getStructureFile();
    }

    // --- Data model ---

    public static class TowerDefinition {
        private final String id;
        private final String name;
        private final String structureFile;
        private final Material baseMaterial;
        private final Map<String, NavigableMap<Integer, TowerLevelStats>> paths;

        TowerDefinition(String id, String name, String structureFile, Material baseMaterial,
                        Map<String, NavigableMap<Integer, TowerLevelStats>> paths) {
            this.id = id;
            this.name = name;
            this.structureFile = structureFile;
            this.baseMaterial = baseMaterial;
            this.paths = Collections.unmodifiableMap(paths);
        }

        public String getId() { return id; }
        public String getName() { return name; }
        public String getStructureFile() { return structureFile; }
        public Material getBaseMaterial() { return baseMaterial; }

        public boolean hasPaths() {
            for (String key : paths.keySet()) {
                if (!DEFAULT_PATH.equals(key)) return true;
            }
            return false;
        }

        public Set<String> getPathNames() {
            java.util.LinkedHashSet<String> names = new java.util.LinkedHashSet<>(paths.keySet());
            names.remove(DEFAULT_PATH);
            return names;
        }

        public NavigableMap<Integer, TowerLevelStats> getLevels(String path) {
            return paths.get(path);
        }

        public int getMaxLevel(String path) {
            NavigableMap<Integer, TowerLevelStats> levels = paths.get(path);
            return (levels == null || levels.isEmpty()) ? 0 : levels.lastKey();
        }

        public TowerLevelStats getLevel(String path, int level) {
            NavigableMap<Integer, TowerLevelStats> levels = paths.get(path);
            return levels == null ? null : levels.get(level);
        }
    }

    public record TowerLevelStats(int cost, double range, double damage, long cooldown, Map<String, Object> extras) {

        public int getInt(String key, int def) {
            Object value = extras.get(key);
            return value instanceof Number n ? n.intValue() : def;
        }

        public double getDouble(String key, double def) {
            Object value = extras.get(key);
            return value instanceof Number n ? n.doubleValue() : def;
        }

        public long getLong(String key, long def) {
            Object value = extras.get(key);
            return value instanceof Number n ? n.longValue() : def;
        }

        public boolean getBoolean(String key, boolean def) {
            Object value = extras.get(key);
            return value instanceof Boolean b ? b : def;
        }
    }
}
