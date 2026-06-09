# Implementing Polymorphic Mob Upgrades

When developing the spawning and upgrade system using AI agents (like Antigravity CLI), standardizing how mob data is read is crucial for handling complex transformations. If a mob changes its fundamental entity type (e.g., Slime to Magma Cube, Hoglin to Zoglin), explicit state mapping must be used.

## 1. The Data Structure
The `mob_upgrades_polymorphic.csv` file has been reformatted to decouple the **Upgrade Chain** (what the player sees in the GUI) from the **Entity Type** (what the server spawns). 

* **`Upgrade Chain`:** The static name of the mob lineage (e.g., "Slime", "Skeleton").
* **`Tier`:** The current level (1-5).
* **`Entity Type`:** The literal Minecraft Entity Type to spawn. **This can change per tier.**
* **`Immunities`:** Specific tower targeting exceptions (e.g., "MAGE", "CHORUS"). 
* **`Mount`:** If the entity rides another entity (e.g., "ZOMBIE_HORSE").
* **`Equipment`:** Hand or armor items explicitly forced onto the entity.
* **`Special Mecahnics`:** Special things the entity should be able to do or special attributes the entity has.

## 2. Core Implementation Rules for AI Agents
When prompting an agent to generate your spawning or data-loading logic, pass them these instructions:

### Rule 1: Use a Variant State Engine
Do not map "Slime" directly to `EntityType.SLIME` globally. Instead, create a registry where `Upgrade Chain + Tier` acts as a composite key that points to a `MobState` profile.

### Rule 2: Dynamic Entity Replacement
When a player upgrades a mob chain, the backend should query the CSV. If the `Entity Type` differs from the previous tier, the spawning engine must **instantiate the new Entity Type** rather than trying to apply NBT tags to the old one.

### Rule 3: Attribute and Equipment Injection
1. Apply the `Equipment` string directly to the entity's armor/hand slots.
2. Apply `Immunities` as a persistent Custom NBT Tag or via a `HashMap<UUID, List<TowerType>>` in memory.
3. Apply `Mount` by spawning the secondary entity and setting the primary entity as a passenger.

### Example Configuration Schema (Java/Paper)
Provide this structure to your code-generation agent to ensure it builds the parser correctly:

```java
public class MobStateProfile {
    private final String upgradeChain;
    private final int tier;
    private final EntityType entityType; // e.g. MAGMA_CUBE
    
    private final double maxHealth;
    private final double speed;
    private final double damage;
    
    private final List<TowerType> immunities;
    private final EntityType mountType;
    private final Material equipment;
    
    // The spawner should use this exact profile per wave based on the player's unlocked tier.
}
```

By enforcing this structure, you eliminate bugs where Magma Cubes spawn with incorrect Slime attributes or Endermites get stuck with Silverfish properties.
