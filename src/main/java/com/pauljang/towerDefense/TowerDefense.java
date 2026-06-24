package com.pauljang.towerDefense;

import com.pauljang.towerDefense.core.GameManager;
import com.pauljang.towerDefense.core.GameState;
import com.pauljang.towerDefense.core.QueueManager;
import com.pauljang.towerDefense.core.WaveManager;
import com.pauljang.towerDefense.core.TDCommand;
import com.pauljang.towerDefense.data.MapManager;
import com.pauljang.towerDefense.data.PlotConfigManager;
import com.pauljang.towerDefense.data.WaypointConfigManager;
import com.pauljang.towerDefense.entities.MobManager;
import com.pauljang.towerDefense.listeners.MobListener;
import com.pauljang.towerDefense.listeners.WandListener;
import com.pauljang.towerDefense.listeners.WorldUnloadListener;
import com.pauljang.towerDefense.setup.SetupManager;
import com.pauljang.towerDefense.towers.TowerConfigManager;
import com.pauljang.towerDefense.towers.TowerManager;
import org.bukkit.plugin.java.JavaPlugin;

public final class TowerDefense extends JavaPlugin {

    private GameManager gameManager;
    private SetupManager setupManager;
    private PlotConfigManager plotConfigManager;
    private WaypointConfigManager waypointConfigManager;
    private MapManager mapManager;
    private QueueManager queueManager;
    private WaveManager waveManager;
    private MobManager mobManager;
    private TowerConfigManager towerConfigManager;
    private TowerManager towerManager;

    @Override
    public void onEnable() {
        // Save default config.yml
        saveDefaultConfig();

        // One-time config migration. saveDefaultConfig() never overwrites an existing config.yml, so a
        // server first created when max-castle-health defaulted to 100 keeps that stale value even though
        // the bundled default is now 1000 — leaving every castle-HP display reading 100. Bump the legacy
        // value once, version-gated so a deliberate custom value set later is never clobbered. Runs before
        // GameManager/Match are constructed so they read the migrated value.
        if (getConfig().getInt("config-version", 0) < 1) {
            if (getConfig().getInt("game.max-castle-health", 1000) == 100) {
                getConfig().set("game.max-castle-health", 1000);
                getLogger().info("Config migration: bumped game.max-castle-health 100 -> 1000");
            }
            getConfig().set("config-version", 1);
            saveConfig();
        }

        // Build the shared NamespacedKeys before any manager spawns mobs or reads PDC data.
        TDKeys.init(this);

        // Create structures directory if it doesn't exist
        java.io.File structuresDir = new java.io.File(getDataFolder(), "structures");
        if (!structuresDir.exists()) {
            structuresDir.mkdirs();
        }

        // Initialize managers
        this.gameManager = new GameManager(this);
        this.setupManager = new SetupManager();
        this.plotConfigManager = new PlotConfigManager(this);
        this.waypointConfigManager = new WaypointConfigManager(this);
        this.mapManager = new MapManager(this);
        this.queueManager = new QueueManager(this);
        this.waveManager = new WaveManager(this);
        this.mobManager = new MobManager(this);
        this.towerConfigManager = new TowerConfigManager(this);
        this.towerManager = new TowerManager(this);

        // Register commands
        getCommand("td").setExecutor(new TDCommand(this));

        // Register listeners
        getServer().getPluginManager().registerEvents(new WandListener(this), this);
        getServer().getPluginManager().registerEvents(new MobListener(this), this);
        getServer().getPluginManager().registerEvents(new WorldUnloadListener(this), this);

        // Set up the lobby and game worlds
        this.gameManager.setupWorlds();

        // Set the default state
        this.gameManager.setGameState(GameState.LOBBY);

        getLogger().info("TowerDefense successfully enabled!");
    }

    @Override
    public void onDisable() {
        if (this.gameManager != null && this.gameManager.getOrchestrationService() != null) {
            this.gameManager.getOrchestrationService().close();
        }
        if (this.mobManager != null) {
            this.mobManager.cleanup();
        }
        if (this.towerManager != null) {
            this.towerManager.cleanup();
        }
        getLogger().info("TowerDefense successfully disabled!");
    }

    public GameManager getGameManager() {
        return gameManager;
    }

    public SetupManager getSetupManager() {
        return setupManager;
    }

    public PlotConfigManager getPlotConfigManager() {
        return plotConfigManager;
    }
    public WaypointConfigManager getWaypointConfigManager() {
        return waypointConfigManager;
    }
    public MapManager getMapManager() {
        return mapManager;
    }
    public QueueManager getQueueManager() {
        return queueManager;
    }
    public WaveManager getWaveManager() {
        return waveManager;
    }
    public MobManager getMobManager() {
        return mobManager;
    }
    public TowerConfigManager getTowerConfigManager() {
        return towerConfigManager;
    }
    public TowerManager getTowerManager() {
        return towerManager;
    }
}
