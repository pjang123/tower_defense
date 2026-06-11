package com.pauljang.towerDefense.core;

import com.pauljang.towerDefense.TowerDefense;
import com.pauljang.towerDefense.data.MapManager;
import com.pauljang.towerDefense.entities.TDMob;
import com.pauljang.towerDefense.towers.Tower;
import org.bukkit.World;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;

import java.util.*;

public class Match {
    private final UUID matchId;
    private final TowerDefense plugin;
    private GameState currentState = GameState.STARTING;
    private final MapManager.MapData mapData;
    private World world;
    
    private final List<UUID> players = new ArrayList<>();
    private final Map<UUID, String> playerArenas = new HashMap<>();
    
    // Instance-specific economies
    private final Map<UUID, Integer> playerGold = new HashMap<>();
    private final Map<UUID, Integer> playerExp = new HashMap<>();
    
    // Instance-specific game stats
    private final Map<String, Integer> arenaHealth = new HashMap<>();
    private final Map<String, Map<String, Long>> activeSpells = new HashMap<>();
    private final Map<String, ArmorStand> castleHolograms = new HashMap<>();
    private BossBar castleBossBar;
    
    // Instance-specific entity management
    private final List<TDMob> activeMobs = new ArrayList<>();
    private final Map<String, Tower> placedTowers = new HashMap<>(); // PlotID -> Tower

    public Match(TowerDefense plugin, MapManager.MapData mapData) {
        this.matchId = UUID.randomUUID();
        this.plugin = plugin;
        this.mapData = mapData;
        
        int maxHealth = plugin.getConfig().getInt("game.max-castle-health", 1000);
        arenaHealth.put("1", maxHealth);
        arenaHealth.put("2", maxHealth);
    }

    public UUID getMatchId() { return matchId; }
    public GameState getCurrentState() { return currentState; }
    public void setCurrentState(GameState currentState) { this.currentState = currentState; }
    public MapManager.MapData getMapData() { return mapData; }
    public World getWorld() { return world; }
    public void setWorld(World world) { this.world = world; }
    
    public List<UUID> getPlayers() { return players; }
    public Map<UUID, String> getPlayerArenas() { return playerArenas; }
    public Map<UUID, Integer> getPlayerGold() { return playerGold; }
    public Map<UUID, Integer> getPlayerExp() { return playerExp; }
    
    public Map<String, Integer> getArenaHealth() { return arenaHealth; }
    public Map<String, Map<String, Long>> getActiveSpells() { return activeSpells; }
    public List<TDMob> getActiveMobs() { return activeMobs; }
    public Map<String, Tower> getPlacedTowers() { return placedTowers; }
    public Map<String, ArmorStand> getCastleHolograms() { return castleHolograms; }
    
    public BossBar getCastleBossBar() { return castleBossBar; }
    public void setCastleBossBar(BossBar castleBossBar) { this.castleBossBar = castleBossBar; }

    public void addPlayer(Player player, String arena) {
        players.add(player.getUniqueId());
        playerArenas.put(player.getUniqueId(), arena);
        playerGold.put(player.getUniqueId(), plugin.getConfig().getInt("game.starting-gold", 100));
        playerExp.put(player.getUniqueId(), 0);
    }
}
