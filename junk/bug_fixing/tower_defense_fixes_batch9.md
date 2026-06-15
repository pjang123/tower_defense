# Tower Defense Adjustments & Fixes

Here are the code updates to resolve the requested issues (Dripstone Hazards acting as landmines, Scattershot arrow lifespan, Tier 3 Thunder Tower global attack, and Bee Swarm slab fixes).

---

### 1. `towers.yaml` - Thunder Tower Tier 3 Global Strike

Update the **Thunder Tower** level 3 configuration to have a 999.0 range, a 600 tick cooldown, 15 damage, and the `is_global` tag. This restores the global strike capability.

```yaml
thunder:
  name: "Thunder Tower"
  structure_file: "thunder"
  base_material: COPPER_BLOCK
  levels:
    1:
      cost: 200
      range: 12.0
      damage: 5.0
      cooldown: 80
      chain_count: 0
    2:
      cost: 300
      range: 15.0
      damage: 8.0
      cooldown: 80
      chain_count: 3
    3:
      cost: 450
      range: 999.0
      damage: 15.0
      cooldown: 600
      is_global: true
```

---

### 2. Dripstone Hazards (Act like Landmines)

We need to remove the hazard generation from `buildTowerStructure` and move it into the ticker so they spawn dynamically, and despawn upon being triggered by a mob.

**Step A: Update `startTowerTicker()`**
In `TowerManager.java` inside the `run()` loop of `startTowerTicker()`, find the Dripstone tick check and update it to check the level instead of if the list is empty (since the list starts empty now).

```java
                    // Replace the old dripstone ticker check with this:
                    if (tower.getType() == TowerType.DRIPSTONE && tower.getLevel() >= 2) {
                        tickDripstoneHazards(tower, tick);
                    }
```

**Step B: Replace `tickDripstoneHazards()`**
Replace your current `tickDripstoneHazards()` method with this dynamic respawning and despawning logic:

```java
    private void tickDripstoneHazards(Tower tower, long tick) {
        String arena = plugin.getPlotConfigManager().getPlotArena(tower.getPlotId());
        int cap = plugin.getTowerConfigManager().getStat(TowerType.DRIPSTONE, tower.getLevel(), "hazard_count", 6);

        // 1. Clean up invalid hazards and check for mob triggers
        java.util.Iterator<Location> it = tower.getHazardTiles().iterator();
        while (it.hasNext()) {
            Location loc = it.next();
            org.bukkit.entity.BlockDisplay display = null;
            
            if (loc.getWorld() != null) {
                for (org.bukkit.entity.Entity e : loc.getWorld().getNearbyEntities(loc, 0.5, 1.0, 0.5)) {
                    if (e instanceof org.bukkit.entity.BlockDisplay && e.getScoreboardTags().contains("td_hazard")) {
                        display = (org.bukkit.entity.BlockDisplay) e;
                        break;
                    }
                }
            }
            
            if (display == null || !display.isValid()) {
                it.remove();
                continue;
            }

            // Check for mobs stepping on the hazard (checking slightly above ground)
            java.util.List<Mob> hit = getMobsInRadius(loc.clone().add(0, 1.0, 0), 1.5, arena);
            if (!hit.isEmpty()) {
                // Apply vulnerability tag
                org.bukkit.NamespacedKey vulnKey = new org.bukkit.NamespacedKey(plugin, "td_vulnerable_until");
                long until = System.currentTimeMillis() + 4000L;
                for (Mob mob : hit) {
                    mob.getPersistentDataContainer().set(vulnKey, org.bukkit.persistence.PersistentDataType.LONG, until);
                }
                
                // Visuals & Sound for trigger
                loc.getWorld().playSound(loc, Sound.BLOCK_POINTED_DRIPSTONE_BREAK, 1.0f, 1.2f);
                loc.getWorld().spawnParticle(org.bukkit.Particle.BLOCK, loc.clone().add(0, 0.5, 0), 15, 0.2, 0.2, 0.2, Material.POINTED_DRIPSTONE.createBlockData());
                
                // Remove the hazard
                display.remove();
                it.remove();
            }
        }

        // 2. Spawn new hazards randomly over time (Try spawning 1 every 40 ticks / 2 seconds)
        if (tower.getHazardTiles().size() < cap && tick % 40 == 0) {
            java.util.List<Location> track = getTrackLocationsWithinRange(tower);
            if (track.isEmpty()) return;

            Location baseLoc = track.get(new java.util.Random().nextInt(track.size())).clone();
            
            // Random offset within the tower's radius
            baseLoc.add((Math.random() - 0.5) * 2.5, 0, (Math.random() - 0.5) * 2.5);
            
            if (baseLoc.distanceSquared(tower.getCenterLocation()) <= tower.getRange() * tower.getRange()) {
                org.bukkit.entity.BlockDisplay hazardStand = baseLoc.getWorld().spawn(baseLoc, org.bukkit.entity.BlockDisplay.class, bd -> {
                    org.bukkit.block.data.type.PointedDripstone data = (org.bukkit.block.data.type.PointedDripstone) Material.POINTED_DRIPSTONE.createBlockData();
                    data.setVerticalDirection(org.bukkit.block.BlockFace.UP);
                    bd.setBlock(data);
                    bd.addScoreboardTag("td_hazard");
                    bd.setTransformation(new org.bukkit.util.Transformation(
                        new org.joml.Vector3f(-0.5f, 0f, -0.5f), 
                        new org.joml.Quaternionf(), 
                        new org.joml.Vector3f(1f, 1f, 1f),
                        new org.joml.Quaternionf()
                    ));
                });
                tower.getHazardTiles().add(baseLoc);
                tower.getLandmines().add(hazardStand); // Auto cleanup when tower is destroyed
            }
        }
    }
```

**Step C: Remove Old Hazard Spawning**
Inside `buildTowerStructure()` in `TowerManager.java`, completely **delete** the block of code that spawns `td_hazard` entities (the block checking `if (type == TowerType.DRIPSTONE && level >= 2)` with the `for (int i = 0; i < 30; i++)` loop). You only need to keep the cleanup part:
```java
        // KEEP THIS CLEANUP PART:
        for (Location loc : tower.getHazardTiles()) {
            center.getWorld().getNearbyEntities(loc, 1.0, 2.0, 1.0).stream()
                .filter(e -> e.getScoreboardTags().contains("td_hazard"))
                .forEach(org.bukkit.entity.Entity::remove);
        }
        tower.getHazardTiles().clear();
```

---

### 3. Turret Scatter Arrow Lifespan

In `TowerManager.java`, find `case TURRET` -> `"scatter"` in `shootTarget()`. Add the lifespan scheduler immediately after spawning the arrow.

```java
                        // Apply strong randomized spread to velocity
                        double spread = 0.35;
                        org.bukkit.util.Vector spreadDir = dir.clone().add(new org.bukkit.util.Vector(
                            (Math.random() - 0.5) * spread,
                            (Math.random() - 0.5) * spread,
                            (Math.random() - 0.5) * spread
                        )).normalize();
                        
                        arrow.setVelocity(spreadDir.multiply(2.5));
                        
                        // NEW: Despawn the arrow after 20 ticks (1 second) so it disappears quickly
                        org.bukkit.Bukkit.getScheduler().runTaskLater(plugin, () -> {
                            if (arrow.isValid()) arrow.remove();
                        }, 20L);
```

---

### 4. Beehive Slab Fixes & Timeout Safeties

In `TowerManager.java`, update the two areas in `tickBeehive()`:

**Step A: Tag Bees with Spawn Time**
When spawning the bees, add a timestamp so we can kill them if they get permanently stuck somewhere:
```java
                org.bukkit.entity.Bee bee = hiveTop.getWorld().spawn(hiveTop, org.bukkit.entity.Bee.class, b -> {
                    b.setInvulnerable(true);
                    b.setCollidable(false);
                    b.setPersistent(true);
                    b.setRemoveWhenFarAway(false);
                    b.getPersistentDataContainer().set(new org.bukkit.NamespacedKey(plugin, "td_tower_pet"),
                            org.bukkit.persistence.PersistentDataType.BYTE, (byte) 1);
                    // ADD THIS LINE:
                    b.getPersistentDataContainer().set(new org.bukkit.NamespacedKey(plugin, "td_spawn_time"),
                            org.bukkit.persistence.PersistentDataType.LONG, System.currentTimeMillis());
                    try { org.bukkit.Bukkit.getMobGoals().removeAllGoals(b); } catch (Throwable ignored) {}
                });
```

**Step B: Increase Hit Radius & Check Timeout**
Replace the targeting/distance check in `tickBeehive()` to aim for the mob's *Eye Location*, increase the collision radius to 2.0 blocks (4.0 distance squared), and kill stuck bees:

```java
            if (assignedTarget == null || !assignedTarget.isValid()) {
                // (Orbit logic remains the same...)
                double orbitSpeed = 0.05;
                double radius = 1.5 + (index * 0.2); 
                double angle = (tick * orbitSpeed) + (index * Math.PI / 2);
                Location orbitLoc = hiveTop.clone().add(Math.cos(angle) * radius, Math.sin(tick * 0.05) * 0.5, Math.sin(angle) * radius);
                
                org.bukkit.util.Vector toOrbit = orbitLoc.toVector().subtract(bee.getLocation().toVector());
                bee.setVelocity(toOrbit.normalize().multiply(0.3));
                continue;
            }
            
            // Timeout Check: If bee is alive for more than 10 seconds (stuck on a slab), remove it to allow respawning
            long spawnTime = bee.getPersistentDataContainer().getOrDefault(new org.bukkit.NamespacedKey(plugin, "td_spawn_time"), org.bukkit.persistence.PersistentDataType.LONG, System.currentTimeMillis());
            if (System.currentTimeMillis() - spawnTime > 10000L) {
                bee.remove();
                beeIt.remove();
                continue;
            }
            
            // NEW: Aim for eye location to keep bees higher off the ground, and increase hit radius to 2.0 blocks (4.0 distSq)
            double distSq = assignedTarget.getEyeLocation().distanceSquared(bee.getLocation());
            if (distSq <= 4.0) {
                Location pop = bee.getLocation();
                pop.getWorld().spawnParticle(org.bukkit.Particle.EXPLOSION, pop, 1);
                pop.getWorld().playSound(pop, Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 1.0f);
                
                assignedTarget.setNoDamageTicks(0); 
                assignedTarget.damage(tower.getDamage());
                
                bee.remove();
                beeIt.remove();
            } else {
                org.bukkit.util.Vector dir = assignedTarget.getEyeLocation().toVector()
                        .subtract(bee.getLocation().toVector()).normalize();
                bee.setVelocity(dir.multiply(0.45));
            }
```
