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
        // If it's caused by a block (fire/lava) or another entity, we allow it.
        if (event instanceof EntityCombustByBlockEvent || event instanceof EntityCombustByEntityEvent) {
            return;
        }

        Entity entity = event.getEntity();
        NamespacedKey key = new NamespacedKey(plugin, "td_mob");
        
        // If the entity has our custom TD Mob tag, cancel the sunlight burning
        if (entity.getPersistentDataContainer().has(key, PersistentDataType.BYTE)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onMobDamage(EntityDamageEvent event) {
        Entity entity = event.getEntity();
        if (!(entity instanceof Mob mob)) return;

        NamespacedKey key = new NamespacedKey(plugin, "td_mob");
        if (mob.getPersistentDataContainer().has(key, PersistentDataType.BYTE)) {
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
}

