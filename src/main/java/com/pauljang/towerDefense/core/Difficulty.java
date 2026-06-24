package com.pauljang.towerDefense.core;

import org.bukkit.ChatColor;
import org.bukkit.Material;

/**
 * Single-player difficulty levels chosen by the player before a match begins.
 *
 * <p>{@code countMultiplier} scales the number of mobs in every wave group (so EASY thins out the
 * swarms, HARD bulks them up), and {@code goldMultiplier} scales each player's starting gold to give
 * an easier or tighter opening economy. NORMAL leaves both at the authored values, so multiplayer —
 * which always runs on NORMAL — is unaffected.
 */
public enum Difficulty {
    EASY("Easy", ChatColor.GREEN, Material.LIME_DYE, 11, 0.6, 1.5,
            "Smaller swarms and a bigger starting budget.", "Great for learning the ropes."),
    NORMAL("Normal", ChatColor.GOLD, Material.YELLOW_DYE, 13, 1.0, 1.0,
            "The standard wave schedule.", "A balanced challenge."),
    HARD("Hard", ChatColor.RED, Material.RED_DYE, 15, 1.5, 0.75,
            "Larger swarms and a tighter budget.", "For veterans seeking a real fight.");

    private final String displayName;
    private final ChatColor color;
    private final Material icon;
    private final int guiSlot;
    private final double countMultiplier;
    private final double goldMultiplier;
    private final String loreLine1;
    private final String loreLine2;

    Difficulty(String displayName, ChatColor color, Material icon, int guiSlot,
               double countMultiplier, double goldMultiplier, String loreLine1, String loreLine2) {
        this.displayName = displayName;
        this.color = color;
        this.icon = icon;
        this.guiSlot = guiSlot;
        this.countMultiplier = countMultiplier;
        this.goldMultiplier = goldMultiplier;
        this.loreLine1 = loreLine1;
        this.loreLine2 = loreLine2;
    }

    public String getDisplayName() { return displayName; }
    public ChatColor getColor() { return color; }
    public Material getIcon() { return icon; }
    public int getGuiSlot() { return guiSlot; }
    public double getCountMultiplier() { return countMultiplier; }
    public double getGoldMultiplier() { return goldMultiplier; }
    public String getLoreLine1() { return loreLine1; }
    public String getLoreLine2() { return loreLine2; }

    /** Colored display name for chat/titles, e.g. {@code §aEasy}. */
    public String getColoredName() { return color + displayName; }

    /** Scales an authored wave mob count, never dropping a group below a single mob. */
    public int scaleCount(int baseCount) {
        return Math.max(1, (int) Math.round(baseCount * countMultiplier));
    }

    /** Scales the configured starting gold for this difficulty. */
    public int scaleGold(int baseGold) {
        return Math.max(0, (int) Math.round(baseGold * goldMultiplier));
    }

    /** Maps a difficulty-picker GUI slot back to a difficulty, or {@code null} if it isn't one. */
    public static Difficulty fromGuiSlot(int slot) {
        for (Difficulty d : values()) {
            if (d.guiSlot == slot) return d;
        }
        return null;
    }
}
