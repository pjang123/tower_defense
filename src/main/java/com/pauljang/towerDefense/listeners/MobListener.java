package com.pauljang.towerDefense.listeners;

import com.pauljang.towerDefense.TowerDefense;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityCombustByBlockEvent;
import org.bukkit.event.entity.EntityCombustByEntityEvent;
import org.bukkit.event.entity.EntityCombustEvent;
import org.bukkit.persistence.PersistentDataType;

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
}
