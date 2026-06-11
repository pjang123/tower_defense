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
