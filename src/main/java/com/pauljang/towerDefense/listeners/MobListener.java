package com.pauljang.towerDefense.listeners;

import com.pauljang.towerDefense.TowerDefense;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mob;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityCombustByBlockEvent;
import org.bukkit.event.entity.EntityCombustByEntityEvent;
import org.bukkit.event.entity.EntityCombustEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;

public class MobListener implements Listener {

    private final TowerDefense plugin;

    public MobListener(TowerDefense plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onMobBurn(EntityCombustEvent event) {
        Entity entity = event.getEntity();
        NamespacedKey key = new NamespacedKey(plugin, "td_mob");
        
        // If the entity has our custom TD Mob tag
        if (entity.getPersistentDataContainer().has(key, PersistentDataType.BYTE)) {
            NamespacedKey fireImmuneKey = new NamespacedKey(plugin, "td_fire_immune");
            
            // If immune to fire, cancel all combustion
            if (entity.getPersistentDataContainer().has(fireImmuneKey, PersistentDataType.BYTE)) {
                event.setCancelled(true);
                return;
            }

            // If it's caused by a block (fire/lava) or another entity, we allow it.
            if (event instanceof EntityCombustByBlockEvent || event instanceof EntityCombustByEntityEvent) {
                return;
            }
            // Cancel sunlight burning
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onMobDamage(EntityDamageEvent event) {
        Entity entity = event.getEntity();
        if (!(entity instanceof Mob mob)) return;

        NamespacedKey key = new NamespacedKey(plugin, "td_mob");
        if (mob.getPersistentDataContainer().has(key, PersistentDataType.BYTE)) {
            // Check for fire-related damage if the mob has fire immunity
            NamespacedKey fireImmuneKey = new NamespacedKey(plugin, "td_fire_immune");
            if (mob.getPersistentDataContainer().has(fireImmuneKey, PersistentDataType.BYTE)) {
                EntityDamageEvent.DamageCause cause = event.getCause();
                if (cause == EntityDamageEvent.DamageCause.FIRE ||
                    cause == EntityDamageEvent.DamageCause.FIRE_TICK ||
                    cause == EntityDamageEvent.DamageCause.LAVA ||
                    cause == EntityDamageEvent.DamageCause.HOT_FLOOR ||
                    cause == EntityDamageEvent.DamageCause.MELTING) {
                    event.setCancelled(true);
                    return;
                }
            }

            // Update the healthbar 1 tick later to get the updated health value after damage is applied
            new BukkitRunnable() {
                @Override
                public void run() {
                    if (mob.isValid() && !mob.isDead()) {
                        plugin.getMobManager().updateHealthBar(mob);
                    }
                }
            }.runTaskLater(plugin, 1L);
        }
    }

    @EventHandler
    public void onMobKnockback(io.papermc.paper.event.entity.EntityKnockbackEvent event) {
        Entity entity = event.getEntity();
        NamespacedKey key = new NamespacedKey(plugin, "td_mob");
        if (entity.getPersistentDataContainer().has(key, PersistentDataType.BYTE)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPotionEffect(org.bukkit.event.entity.EntityPotionEffectEvent event) {
        Entity entity = event.getEntity();
        NamespacedKey key = new NamespacedKey(plugin, "td_mob");
        if (!entity.getPersistentDataContainer().has(key, PersistentDataType.BYTE)) return;

        NamespacedKey slowImmuneKey = new NamespacedKey(plugin, "td_slow_immune");
        if (entity.getPersistentDataContainer().has(slowImmuneKey, PersistentDataType.BYTE)) {
            org.bukkit.potion.PotionEffect newEffect = event.getNewEffect();
            if (newEffect != null) {
                org.bukkit.potion.PotionEffectType type = newEffect.getType();
                org.bukkit.potion.PotionEffectType slowType = org.bukkit.potion.PotionEffectType.getByName("SLOW");
                org.bukkit.potion.PotionEffectType slownessType = org.bukkit.potion.PotionEffectType.getByName("SLOWNESS");
                
                if ((slowType != null && type.equals(slowType)) || 
                    (slownessType != null && type.equals(slownessType))) {
                    event.setCancelled(true);
                }
            }
        }
    }

    @EventHandler
    public void onMobDeath(org.bukkit.event.entity.EntityDeathEvent event) {
        Entity entity = event.getEntity();
        NamespacedKey key = new NamespacedKey(plugin, "td_mob");
        if (entity.getPersistentDataContainer().has(key, PersistentDataType.BYTE)) {
            event.getDrops().clear();
            event.setDroppedExp(0);
        }
    }

    @EventHandler
    public void onMobTarget(org.bukkit.event.entity.EntityTargetEvent event) {
        Entity entity = event.getEntity();
        if (entity == null) return;

        NamespacedKey key = new NamespacedKey(plugin, "td_mob");
        if (entity.getPersistentDataContainer().has(key, PersistentDataType.BYTE)) {
            event.setCancelled(true);
            event.setTarget(null);
        }
    }

    @EventHandler
    public void onPlayerJoin(org.bukkit.event.player.PlayerJoinEvent event) {
        if (plugin.getGameManager().getBossBar() != null) {
            plugin.getGameManager().getBossBar().addPlayer(event.getPlayer());
        }
    }
}


