package com.pauljang.towerDefense.ui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/**
 * Custom {@link InventoryHolder} used to tag every TD menu so click handling can dispatch on a
 * stable {@link MenuType} instead of fragile title-string matching. The optional {@link #data}
 * payload carries menu-specific context (a plot id, a mob chain key, ...) that the click handler
 * previously had to recover by sub-stringing the inventory title.
 */
public class TDMenuHolder implements InventoryHolder {

    public enum MenuType {
        OPEN_GAMES,
        VOTE_MAP,
        CHOOSE_DIFFICULTY,
        MOB_SPAWNER,
        MOB_TIER,
        BUY_TOWER,
        CHOOSE_PATH,
        MANAGE_TOWER,
        PLAYER_UPGRADES
    }

    private final MenuType type;
    private final String data;
    private Inventory inventory;

    public TDMenuHolder(MenuType type) {
        this(type, null);
    }

    public TDMenuHolder(MenuType type, String data) {
        this.type = type;
        this.data = data;
    }

    public MenuType getType() {
        return type;
    }

    /** Menu-specific payload (e.g. plot id or mob chain key); may be {@code null}. */
    public String getData() {
        return data;
    }

    /**
     * Bukkit needs the inventory before the holder can reference it, so callers build the
     * inventory with this holder and then hand it back here.
     */
    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
