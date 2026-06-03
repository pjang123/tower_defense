# Preliminary Balancing Reference Sheet

This document compiles the current values, statistics, and costs for mobs, towers, and player upgrades within the Tower Defense plugin.

---

## 1. Mob Statistics, Costs, & Rewards

Below is the balance sheet for all spawned mob types:

| Mob Type | Entity Type | Speed Mult | Health | Armor | Spawn Cost | Gold Reward (Bounty) | XP Reward | Special Traits |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **Default Zombie** | `ZOMBIE` | 1.0x | Default | 0.0 | 10 Gold | 15 Gold | 5 XP | None |
| **Giant Zombie** | `GIANT` | 0.5x | 150.0 | 8.0 | 50 Gold | 80 Gold | 40 XP | Immune to Slow |
| **Nether Fire Zombie** | `ZOMBIE` | 1.2x | 25.0 | 2.0 | 25 Gold | 40 Gold | 20 XP | Immune to Fire & Slow |
| **Piglin Attacker** | `PIGLIN` | 1.0x | Default | 0.0 | 15 Gold | 25 Gold | 10 XP | Immune to Zombification |
| **Hoglin Attacker** | `HOGLIN` | 1.0x | Default | 0.0 | 40 Gold | 60 Gold | 30 XP | Immune to Zombification |

---

## 2. Player Upgrades & Shops

All player upgrades cost custom **TD EXP** accumulated from killing waves of TD mobs.

### Melee Sword Upgrades
Players start with a Wooden Sword at game launch.

*   **Stone Sword**: Upgrade cost = **80 EXP**
*   **Iron Sword**: Upgrade cost = **200 EXP**
*   **Diamond Sword**: Upgrade cost = **450 EXP**
*   **Netherite Sword**: Upgrade cost = **900 EXP**

### Ranged Bow Upgrades
Players start with a standard Bow at game launch.

*   **Crossbow (Quick Charge I)**: Upgrade cost = **150 EXP**
*   **Crossbow (Quick Charge III + Piercing IV)**: Upgrade cost = **450 EXP**
    *   *Effect*: Automatically fires arrows that pierce through up to 4 targets.
*   *Note*: All upgraded bows provide infinite ammo.

### Passive Gold Generator
Automatically adds gold to your balance every second during active waves.

*   **Level 1 (Default)**: Generates **5 Gold/sec**
*   **Level 2**: Generates **20 Gold/sec** (Upgrade cost = **100 EXP**)
*   **Level 3**: Generates **50 Gold/sec** (Upgrade cost = **300 EXP**)
*   **Level 4 (Max)**: Generates **100 Gold/sec** (Upgrade cost = **600 EXP**)

### Permanent Splash Potion Purchases
Consumable splash potions purchased in exchange for experience points.

*   **Splash Potion of Healing II**: Costs **30 EXP**
*   **Splash Potion of Slowness II**: Costs **45 EXP**
*   **Splash Potion of Harming II**: Costs **50 EXP**

---

## 3. Tower Progression Balances

Towers are placed as centered 3-block-tall multiblock structures on plots. Players build a basic level 1 tower for **100 Gold** and can upgrade it up to Level 5.

| Level | Upgrade Cost | Attack Range | Attack Damage | Attack Speed (Cooldown) | Special Effects |
| :---: | :---: | :---: | :---: | :---: | :--- |
| **1** | — (Base) | 15.0 blocks | 1.5 HP | 1.0s (20 ticks) | Basic single target attack (Crit particles) |
| **2** | 150 Gold | 16.5 blocks | 3.0 HP | 0.9s (18 ticks) | Increased damage & range |
| **3** | 300 Gold | 18.0 blocks | 4.5 HP | 0.8s (16 ticks) | **Frost Shot**: Applies Slowness II (2s) on hit (Snowflake particles) |
| **4** | 500 Gold | 19.5 blocks | 6.0 HP | 0.7s (14 ticks) | **Flame Shot**: Heavy damage attack (Flame particles, blaze shoot sound) |
| **5** | 800 Gold | 21.0 blocks | 7.5 HP | 0.6s (12 ticks) | **Max Level**: Rapid fire flame attack |
