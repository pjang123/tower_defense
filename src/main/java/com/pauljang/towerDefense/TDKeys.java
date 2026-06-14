package com.pauljang.towerDefense;

import org.bukkit.NamespacedKey;
import org.bukkit.plugin.Plugin;

/**
 * Central registry of the plugin's {@link NamespacedKey}s used for PersistentDataContainer storage on
 * mobs and items. Previously these were created inline as {@code new NamespacedKey(plugin, "td_...")}
 * at every call site; centralising them here keeps the string identifiers in one place and avoids
 * accidental typos that would silently read/write the wrong key.
 *
 * <p>{@link #init(Plugin)} must be called once during {@code onEnable} before any mob is spawned or any
 * PDC read occurs. The key strings are kept identical to the original inline values so existing data and
 * already-spawned entities remain compatible.
 */
public final class TDKeys {

    private TDKeys() {}

    public static NamespacedKey MOB;
    public static NamespacedKey PRESET;
    public static NamespacedKey ARENA;
    public static NamespacedKey GOLD_REWARD;
    public static NamespacedKey XP_REWARD;
    public static NamespacedKey CASTLE_DAMAGE;
    public static NamespacedKey IMMUNITIES;
    public static NamespacedKey SLOW_IMMUNE;
    public static NamespacedKey FREEZE_IMMUNE;
    public static NamespacedKey FIRE_IMMUNE;
    public static NamespacedKey FIRE_DAMAGE;
    public static NamespacedKey POISON_DAMAGE;
    public static NamespacedKey FROZEN_UNTIL;
    public static NamespacedKey POISONED_UNTIL;
    public static NamespacedKey VULNERABLE_UNTIL;
    public static NamespacedKey TOWER_PET;
    public static NamespacedKey ARMAGEDDON_WITHER;
    public static NamespacedKey QUEUE_VOTE_ITEM;

    /** Builds every key against the owning plugin. Call once from {@code onEnable}. */
    public static void init(Plugin plugin) {
        MOB = new NamespacedKey(plugin, "td_mob");
        PRESET = new NamespacedKey(plugin, "td_preset");
        ARENA = new NamespacedKey(plugin, "td_arena");
        GOLD_REWARD = new NamespacedKey(plugin, "td_gold_reward");
        XP_REWARD = new NamespacedKey(plugin, "td_xp_reward");
        CASTLE_DAMAGE = new NamespacedKey(plugin, "td_castle_damage");
        IMMUNITIES = new NamespacedKey(plugin, "td_immunities");
        SLOW_IMMUNE = new NamespacedKey(plugin, "td_slow_immune");
        FREEZE_IMMUNE = new NamespacedKey(plugin, "td_freeze_immune");
        FIRE_IMMUNE = new NamespacedKey(plugin, "td_fire_immune");
        FIRE_DAMAGE = new NamespacedKey(plugin, "td_fire_damage");
        POISON_DAMAGE = new NamespacedKey(plugin, "td_poison_damage");
        FROZEN_UNTIL = new NamespacedKey(plugin, "td_frozen_until");
        POISONED_UNTIL = new NamespacedKey(plugin, "td_poisoned_until");
        VULNERABLE_UNTIL = new NamespacedKey(plugin, "td_vulnerable_until");
        TOWER_PET = new NamespacedKey(plugin, "td_tower_pet");
        ARMAGEDDON_WITHER = new NamespacedKey(plugin, "td_armageddon_wither");
        QUEUE_VOTE_ITEM = new NamespacedKey(plugin, "td_queue_vote_item");
    }
}
