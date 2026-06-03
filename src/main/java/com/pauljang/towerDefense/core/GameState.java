package com.pauljang.towerDefense.core;

public enum GameState {
    LOBBY,      // Waiting for players to join
    STARTING,   // Countdown sequence before the match begins
    ACTIVE,     // Game is currently being played
    ENDED       // Game is finished, cleaning up and reverting maps
}