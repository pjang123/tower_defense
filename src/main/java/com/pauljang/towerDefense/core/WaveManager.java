package com.pauljang.towerDefense.core;

import com.pauljang.towerDefense.TowerDefense;
import com.pauljang.towerDefense.entities.PresetMobType;
import org.bukkit.ChatColor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.util.*;

public class WaveManager {
    private final TowerDefense plugin;
    private final Map<UUID, WaveSession> activeWaves = new HashMap<>();
    private FileConfiguration waveConfig;

    public WaveManager(TowerDefense plugin) {
        this.plugin = plugin;
        loadWaves();
    }

    public void loadWaves() {
        File file = new File(plugin.getDataFolder(), "waves.yml");
        if (!file.exists()) {
            plugin.saveResource("waves.yml", false);
        }
        waveConfig = YamlConfiguration.loadConfiguration(file);
    }

    public void startWaves(Match match) {
        WaveSession session = new WaveSession(match);
        activeWaves.put(match.getMatchId(), session);
        session.start();
    }

    public void stopWaves(Match match) {
        WaveSession session = activeWaves.remove(match.getMatchId());
        if (session != null) session.stop();
    }

    /** Current wave number for a match's single-player wave session, or 0 if it has no active session. */
    public int getCurrentWave(Match match) {
        WaveSession session = activeWaves.get(match.getMatchId());
        return session != null ? session.currentWave : 0;
    }

    public class WaveSession {
        private final Match match;
        private int currentWave = 1;
        private boolean isEndless = false;
        private BukkitRunnable ticker;
        private boolean waveInProgress = false;

        public WaveSession(Match match) {
            this.match = match;
        }

        public void start() {
            ticker = new BukkitRunnable() {
                @Override
                public void run() {
                    // The wave ticker can fire on the same tick the start countdown finishes, before the
                    // match flips to ACTIVE. Only stop permanently once the match has ENDED; while it's
                    // still STARTING, wait rather than cancelling (otherwise waves never spawn).
                    if (match.getCurrentState() == GameState.ENDED) {
                        cancel();
                        return;
                    }
                    if (match.getCurrentState() != GameState.ACTIVE) {
                        return;
                    }

                    if (!waveInProgress) {
                        startNextWave();
                    }
                }
            };
            ticker.runTaskTimer(plugin, 100L, 20L); // Start after 5s, check every second
        }

        private void startNextWave() {
            waveInProgress = true;
            match.getWorld().getPlayers().forEach(p -> 
                p.sendMessage(ChatColor.GOLD + "Wave " + currentWave + " is starting!"));

            if (currentWave <= 100 && !isEndless) {
                spawnPredefinedWave();
            } else {
                spawnEndlessWave();
            }
        }

        private void spawnPredefinedWave() {
            String path = "waves." + currentWave;
            if (!waveConfig.contains(path)) {
                if (currentWave > 100) {
                    triggerEndlessChoice();
                    return;
                }
                currentWave++;
                waveInProgress = false;
                return;
            }

            ConfigurationSection waveSection = waveConfig.getConfigurationSection(path);
            List<Map<?, ?>> groups = waveSection.getMapList("groups");
            
            if (groups.isEmpty()) {
                // Compatibility for old format
                Map<String, Object> singleGroup = new HashMap<>();
                singleGroup.put("mob", waveSection.getString("mob", "ZOMBIE"));
                singleGroup.put("count", waveSection.getInt("count", 10));
                singleGroup.put("tier", waveSection.getInt("tier", 1));
                singleGroup.put("interval", waveSection.getLong("interval", 20L));
                groups = Collections.singletonList(singleGroup);
            }

            int totalGroups = groups.size();
            final int[] finishedGroups = {0};

            for (Map<?, ?> group : groups) {
                long initialDelay = group.get("delay") != null ? ((Number) group.get("delay")).longValue() : 0L;
                String mobTypeStr = group.get("mob") != null ? (String) group.get("mob") : "ZOMBIE";
                int count = group.get("count") != null ? ((Number) group.get("count")).intValue() : 10;
                int tier = group.get("tier") != null ? ((Number) group.get("tier")).intValue() : 1;
                long interval = group.get("interval") != null ? ((Number) group.get("interval")).longValue() : 20L;

                PresetMobType type;
                try {
                    type = PresetMobType.valueOf(mobTypeStr.toUpperCase());
                } catch (IllegalArgumentException e) {
                    type = PresetMobType.ZOMBIE;
                }

                final PresetMobType finalType = type;
                final int finalTier = tier;
                
                new BukkitRunnable() {
                    int spawned = 0;
                    @Override
                    public void run() {
                        if (match.getCurrentState() != GameState.ACTIVE) {
                            cancel();
                            return;
                        }

                        plugin.getMobManager().spawnMobByChain(match, "2", finalType.name().toLowerCase(), finalTier);
                        spawned++;

                        if (spawned >= count) {
                            cancel();
                            finishedGroups[0]++;
                            if (finishedGroups[0] >= totalGroups) {
                                checkWaveEnd();
                            }
                        }
                    }
                }.runTaskTimer(plugin, initialDelay, interval);
            }
        }

        private void spawnEndlessWave() {
            currentWave++;
            waveInProgress = false;
        }

        private void checkWaveEnd() {
            new BukkitRunnable() {
                @Override
                public void run() {
                    if (match.getActiveMobs().isEmpty()) {
                        cancel();
                        waveInProgress = false;
                        currentWave++;
                        if (currentWave == 101 && !isEndless) {
                            triggerEndlessChoice();
                        }
                    }
                }
            }.runTaskTimer(plugin, 40L, 40L);
        }

        private void triggerEndlessChoice() {
            match.getWorld().getPlayers().forEach(p -> 
                p.sendMessage(ChatColor.LIGHT_PURPLE + "Congratulations! You've cleared Wave 100."));
        }

        public void stop() {
            if (ticker != null) ticker.cancel();
        }
    }
}
