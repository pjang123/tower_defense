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
    private Difficulty difficulty = Difficulty.NORMAL;
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

    // Match timing + per-player end-of-game stats
    private long startTimeMillis = 0L;
    private long endTimeMillis = 0L;
    private final Map<UUID, Integer> mobsSpawned = new HashMap<>();
    private final Map<UUID, Integer> mobsKilled = new HashMap<>();
    private final Map<UUID, Double> damageDealt = new HashMap<>();
    private final Map<UUID, Integer> totalGoldEarned = new HashMap<>();
    private final Map<UUID, Integer> totalExpEarned = new HashMap<>();

    // Armageddon mode (triggered late-game): flag, the last countdown threshold already announced
    // (seconds-before-start; Long.MAX_VALUE = none announced yet), and the UUIDs of the spawned
    // Wither bosses so they can be ticked and cleaned up.
    private boolean armageddonActive = false;
    private long armageddonLastWarn = Long.MAX_VALUE;
    // Build-phase grace window: players are in the map and may place towers, but cannot spawn mobs and
    // the wave/Armageddon clocks haven't started yet. Set to (now + 10s) when the match goes live.
    private long graceUntil = 0L;
    private final List<UUID> armageddonWithers = new ArrayList<>();

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
    public Difficulty getDifficulty() { return difficulty; }
    public void setDifficulty(Difficulty difficulty) { this.difficulty = difficulty; }
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

    public long getStartTimeMillis() { return startTimeMillis; }
    public void setStartTimeMillis(long startTimeMillis) { this.startTimeMillis = startTimeMillis; }
    public long getEndTimeMillis() { return endTimeMillis; }
    public void setEndTimeMillis(long endTimeMillis) { this.endTimeMillis = endTimeMillis; }

    public Map<UUID, Integer> getMobsSpawned() { return mobsSpawned; }
    public Map<UUID, Integer> getMobsKilled() { return mobsKilled; }
    public Map<UUID, Double> getDamageDealt() { return damageDealt; }
    public Map<UUID, Integer> getTotalGoldEarned() { return totalGoldEarned; }
    public Map<UUID, Integer> getTotalExpEarned() { return totalExpEarned; }

    public boolean isArmageddonActive() { return armageddonActive; }
    public void setArmageddonActive(boolean armageddonActive) { this.armageddonActive = armageddonActive; }

    public long getGraceUntil() { return graceUntil; }
    public void setGraceUntil(long graceUntil) { this.graceUntil = graceUntil; }
    /** True during the post-teleport build phase: towers allowed, mob spawning blocked. */
    public boolean isInGracePeriod() { return System.currentTimeMillis() < graceUntil; }
    public long getArmageddonLastWarn() { return armageddonLastWarn; }
    public void setArmageddonLastWarn(long armageddonLastWarn) { this.armageddonLastWarn = armageddonLastWarn; }
    public List<UUID> getArmageddonWithers() { return armageddonWithers; }

    public void addPlayer(Player player, String arena) {
        players.add(player.getUniqueId());
        playerArenas.put(player.getUniqueId(), arena);
        int baseGold = plugin.getConfig().getInt("game.starting-gold", 100);
        playerGold.put(player.getUniqueId(), difficulty.scaleGold(baseGold));
        playerExp.put(player.getUniqueId(), 0);
    }
}
