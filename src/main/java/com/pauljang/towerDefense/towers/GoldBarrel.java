package com.pauljang.towerDefense.towers;

import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Interaction;

/**
 * A single Gold Tower "gold bundle" placed on the track: a non-solid {@link BlockDisplay} barrel paired
 * with an {@link Interaction} hitbox the player clicks to claim it. Mobs never collide with either, so
 * pathfinding is unaffected. Lifecycle (spawn / despawn / auto-collect) is driven centrally by
 * {@link TowerManager#tickGoldTower}; instances are tracked on their owning {@link Tower}.
 */
class GoldBarrel {
    final BlockDisplay display;
    final Interaction interaction;
    final int gold;          // payout for a non-gambling barrel; ignored when gambling is true
    final boolean gambling;  // true on the gambling path: payout/effect is rolled on collection
    final long spawnTick;    // ticker tick this barrel was created on
    final long despawnTick;  // ticker tick to remove it on (0 = never despawns)
    final long autoCollectTick; // ticker tick the tower auto-collects it (0 = manual only)
    final long spawnMillis;  // wall-clock spawn time, used to detect left-click attacks after spawn
    boolean warned;          // true once the "despawning soon" urgency cue has fired

    GoldBarrel(BlockDisplay display, Interaction interaction, int gold, boolean gambling,
               long spawnTick, long despawnTick, long autoCollectTick, long spawnMillis) {
        this.display = display;
        this.interaction = interaction;
        this.gold = gold;
        this.gambling = gambling;
        this.spawnTick = spawnTick;
        this.despawnTick = despawnTick;
        this.autoCollectTick = autoCollectTick;
        this.spawnMillis = spawnMillis;
    }

    boolean isValid() {
        return display != null && display.isValid() && interaction != null && interaction.isValid();
    }

    void remove() {
        if (display != null && display.isValid()) display.remove();
        if (interaction != null && interaction.isValid()) interaction.remove();
    }
}
