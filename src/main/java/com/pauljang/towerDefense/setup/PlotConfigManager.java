package com.pauljang.towerDefense.setup;

import com.pauljang.towerDefense.TowerDefense;
import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class PlotConfigManager {

    private final TowerDefense plugin;
    private File file;
    private FileConfiguration config;

    public PlotConfigManager(TowerDefense plugin) {
        this.plugin = plugin;
        setup();
    }

    private void setup() {
        file = new File(plugin.getDataFolder(), "plots.yml");

        if (!file.exists()) {
            try {
                plugin.getDataFolder().mkdirs();
                file.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        config = YamlConfiguration.loadConfiguration(file);
    }

    public void savePlot(Location p1, Location p2) {
        String plotId = UUID.randomUUID().toString().substring(0, 8);
        String path = "plots." + plotId;

        config.set(path + ".world", p1.getWorld().getName());
        config.set(path + ".p1.x", p1.getBlockX());
        config.set(path + ".p1.y", p1.getBlockY());
        config.set(path + ".p1.z", p1.getBlockZ());

        config.set(path + ".p2.x", p2.getBlockX());
        config.set(path + ".p2.y", p2.getBlockY());
        config.set(path + ".p2.z", p2.getBlockZ());

        save();
    }

    private void save() {
        try {
            config.save(file);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void reload() {
        config = YamlConfiguration.loadConfiguration(file);
    }
}
