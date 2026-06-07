# Tower Defense System Updates: Wave GUI, Progression & Balance

This document outlines the necessary logic and configuration updates to enhance the wave sending interface, adjust Warden behavior, balance mob speeds, and enforce sequential progression for mob upgrades.

---

## 1. Wave GUI Enhancements ("Send Wave" Button)

### Feature: Display Queued Mobs and Total XP Payout
Players need better visibility into what they are sending and what their return on investment will be before initiating a wave.

**Implementation Strategy:**
*   **Locate the GUI Builder:** Find where the "Send Wave" button is constructed (likely within `TDCommand.java` or the GUI manager class).
*   **Calculate Totals:** Before updating the button's `ItemMeta`, iterate over the current queue of mobs for the wave. 
    *   Create a map/tally of each `PresetMobType` and their counts.
    *   Sum the XP values of all mobs in the queue to get the `totalXpPayout`.
*   **Update Lore:** Append this aggregated data to the item's lore.
    *   *Example Lore Addition:*
        ```text
        §7Queued Mobs:
        §8- §ex15 Zombie (Lvl 2)
        §8- §ex5 Skeleton (Lvl 1)
        
        §aTotal Payout: §2+350 XP
        ```

---

## 2. Warden Behavior Adjustments

### Feature: Remove Warden Blindness (Darkness Effect)
Wardens in Vanilla Minecraft periodically inflict the Darkness effect on nearby players, which is disruptive in a Tower Defense setting.

**Implementation Strategy:**
*   **Event Cancellation (`MobListener.java`):**
    The most reliable way to prevent this without breaking the Warden's entity data is to intercept the potion effect application.
    ```java
    @EventHandler
    public void onWardenEffect(org.bukkit.event.entity.EntityPotionEffectEvent event) {
        // Check if the entity receiving the effect is a player and the effect is Darkness/Blindness
        if (event.getEntity() instanceof org.bukkit.entity.Player) {
            if (event.getModifiedType().equals(org.bukkit.potion.PotionEffectType.DARKNESS) || 
                event.getModifiedType().equals(org.bukkit.potion.PotionEffectType.BLINDNESS)) {
                
                // If the cause is a Warden (or you check nearby entities for a TD Warden)
                if (event.getCause() == org.bukkit.event.entity.EntityPotionEffectEvent.Cause.WARDEN) {
                    event.setCancelled(true);
                }
            }
        }
    }
    ```

---

## 3. Health-Based Speed Scaling

### Feature: Speed Inversely Proportional to Health
To ensure heavy tanks do not rush the base and weak swarms pose a fast threat, mob speed must dynamically scale inversely to their maximum health.

**Implementation Strategy:**
*   **Adjust Spawning Logic (`MobManager.java`):**
    Instead of relying purely on static speed values from the CSV, calculate a speed modifier upon spawning based on the entity's max health attribute.
*   **Formula:** `Speed = Constant / Max Health` (with upper and lower bounds to prevent game-breaking physics).
    ```java
    double maxHealth = mob.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MAX_HEALTH).getValue();
    
    // Example formula: Base factor of 10. A mob with 20 HP gets 0.5 speed. A mob with 100 HP gets 0.1 speed.
    double calculatedSpeed = 10.0 / maxHealth; 
    
    // Clamp the speed to ensure it doesn't get too fast or completely freeze
    calculatedSpeed = Math.max(0.05, Math.min(calculatedSpeed, 0.5)); 
    
    // Apply speed to the TD pathfinding calculation or generic movement attribute
    ```

---

## 4. Sequential Mob Level Unlocking

### Feature: Require Linear Progression (Level 1 -> 2 -> 3)
Players should not be able to skip upgrade tiers (e.g., buying a Level 3 mob without first unlocking Level 2).

**Implementation Strategy:**
*   **GUI Click Handler Validation:**
    In the inventory click event where players purchase upgrades, fetch the player's (or team's) currently unlocked level for that specific mob line.
*   **Validation Check:**
    ```java
    int targetLevel = /* Level the player is trying to buy */;
    int currentLevel = /* Fetch from player data / team data */;
    
    if (targetLevel > currentLevel + 1) {
        player.sendMessage("§cYou must unlock Level " + (currentLevel + 1) + " first!");
        return; // Deny the purchase
    }
    ```
