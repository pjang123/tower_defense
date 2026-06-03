package com.pauljang.towerDefense.core;

import com.pauljang.towerDefense.TowerDefense;

public class GameManager {

    private final TowerDefense plugin;
    private GameState currentState = null;

    public GameManager(TowerDefense plugin) {
        this.plugin = plugin;
    }

    public GameState getCurrentState() {
        return currentState;
    }

    public void setGameState(GameState newState) {
        if (this.currentState == newState) return;

        this.currentState = newState;
        plugin.getLogger().info("Game state changed to: " + newState.name());

        // Handle transitions here later
        switch (newState) {
            case LOBBY -> handleLobbySetup();
            case STARTING -> handleCountdown();
            case ACTIVE -> handleGameStart();
            case ENDED -> handleGameEnd();
        }
    }

    private void handleLobbySetup() { /* Clear boards, reset health */ }
    private void handleCountdown() { /* Start scoreboard countdown timers */ }
    private void handleGameStart() { /* Teleport players, unlock shops */ }
    private void handleGameEnd() { /* Announce winner, teleport back to main hub */ }
}