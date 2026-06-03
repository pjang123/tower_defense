package com.pauljang.towerDefense;

import com.pauljang.towerDefense.core.GameManager;
import com.pauljang.towerDefense.core.GameState;
import com.pauljang.towerDefense.core.TDCommand;
import com.pauljang.towerDefense.listeners.WandListener;
import com.pauljang.towerDefense.setup.PlotConfigManager;
import com.pauljang.towerDefense.setup.SetupManager;
import org.bukkit.plugin.java.JavaPlugin;

public final class TowerDefense extends JavaPlugin {

    private GameManager gameManager;
    private SetupManager setupManager;
    private PlotConfigManager plotConfigManager;

    @Override
    public void onEnable() {
        // Initialize managers
        this.gameManager = new GameManager(this);
        this.setupManager = new SetupManager();
        this.plotConfigManager = new PlotConfigManager(this);

        // Register commands
        getCommand("td").setExecutor(new TDCommand(this));

        // Register listeners
        getServer().getPluginManager().registerEvents(new WandListener(this), this);

        // Set the default state
        this.gameManager.setGameState(GameState.LOBBY);

        getLogger().info("TowerDefense successfully enabled!");
    }

    @Override
    public void onDisable() {
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
}