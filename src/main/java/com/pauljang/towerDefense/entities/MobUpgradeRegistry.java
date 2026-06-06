package com.pauljang.towerDefense.entities;

import com.pauljang.towerDefense.TowerDefense;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;

public class MobUpgradeRegistry {
    private final LinkedHashMap<String, Map<Integer, MobStateProfile>> registry = new LinkedHashMap<>();
    private final TowerDefense plugin;

    public MobUpgradeRegistry(TowerDefense plugin) {
        this.plugin = plugin;
        loadCsv();
    }

    // Properly handles quoted CSV fields containing commas
    private String[] parseCsvLine(String line) {
        List<String> parts = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        for (char c : line.toCharArray()) {
            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (c == ',' && !inQuotes) {
                parts.add(current.toString().trim());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        parts.add(current.toString().trim());
        return parts.toArray(new String[0]);
    }

    private void loadCsv() {
        // Try server root (plugins/../ = server root), then working directory
        File csvFile = new File(plugin.getDataFolder().getParentFile().getParentFile(), "mob_upgrades_polymorphic.csv");
        if (!csvFile.exists()) {
            csvFile = new File("mob_upgrades_polymorphic.csv");
        }
        if (!csvFile.exists()) {
            plugin.getLogger().warning("mob_upgrades_polymorphic.csv not found. Checked: " + csvFile.getAbsolutePath());
            return;
        }
        try (BufferedReader br = new BufferedReader(new FileReader(csvFile))) {
            String line = br.readLine(); // skip header
            while ((line = br.readLine()) != null) {
                if (line.trim().isEmpty()) continue;
                String[] parts = parseCsvLine(line);
                if (parts.length < 12) continue;

                // Normalize chain key to lowercase for consistent lookup
                String upgradeChain = parts[0].trim().toLowerCase();
                int tier = Integer.parseInt(parts[1].trim());
                EntityType entityType = EntityType.valueOf(parts[2].trim().toUpperCase());
                double price = Double.parseDouble(parts[3].trim());
                double damage = Double.parseDouble(parts[4].trim());
                double hp = Double.parseDouble(parts[5].trim());
                double speed = Double.parseDouble(parts[6].trim());
                int expReward = Integer.parseInt(parts[7].trim());

                String immunitiesRaw = parts[8].trim();
                List<String> immunities = new ArrayList<>();
                if (!immunitiesRaw.isEmpty() && !immunitiesRaw.equalsIgnoreCase("None")) {
                    for (String imp : immunitiesRaw.split("\\s*,\\s*")) {
                        String t = imp.trim();
                        if (!t.isEmpty()) immunities.add(t.toUpperCase());
                    }
                }

                String mountRaw = parts[9].trim();
                EntityType mountType = null;
                if (!mountRaw.isEmpty() && !mountRaw.equalsIgnoreCase("None")) {
                    try { mountType = EntityType.valueOf(mountRaw.toUpperCase()); } catch (IllegalArgumentException ignored) {}
                }

                String equipmentRaw = parts[10].trim();
                Material equipment = null;
                if (!equipmentRaw.isEmpty() && !equipmentRaw.equalsIgnoreCase("None")) {
                    try { equipment = Material.valueOf(equipmentRaw.toUpperCase()); } catch (IllegalArgumentException ignored) {}
                }

                String specialMechanics = parts[11].trim();
                if (specialMechanics.equalsIgnoreCase("None")) specialMechanics = "";

                MobStateProfile profile = new MobStateProfile(upgradeChain, tier, entityType, price,
                        damage, hp, speed, expReward, immunities, mountType, equipment, specialMechanics);
                registry.computeIfAbsent(upgradeChain, k -> new LinkedHashMap<>()).put(tier, profile);
            }
            plugin.getLogger().info("Loaded " + registry.size() + " mob chains from mob_upgrades_polymorphic.csv.");
        } catch (IOException | IllegalArgumentException e) {
            plugin.getLogger().severe("Failed to load mob upgrades CSV: " + e.getMessage());
        }
    }

    public MobStateProfile getProfile(String upgradeChain, int tier) {
        Map<Integer, MobStateProfile> tierMap = registry.get(upgradeChain.toLowerCase());
        if (tierMap == null) return null;
        return tierMap.get(tier);
    }

    /** Returns all chain keys in CSV insertion order (lowercase). */
    public List<String> getAvailableChains() {
        return new ArrayList<>(registry.keySet());
    }

    /** Returns all tier profiles for a chain, keyed by tier number. */
    public Map<Integer, MobStateProfile> getAllTiers(String upgradeChain) {
        return registry.getOrDefault(upgradeChain.toLowerCase(), Collections.emptyMap());
    }

    public int getMaxTier(String upgradeChain) {
        Map<Integer, MobStateProfile> tierMap = registry.get(upgradeChain.toLowerCase());
        if (tierMap == null || tierMap.isEmpty()) return 1;
        return Collections.max(tierMap.keySet());
    }
}
