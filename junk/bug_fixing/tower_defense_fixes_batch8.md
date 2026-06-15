# Tower Defense Adjustments & Fixes

Here are the code updates to resolve the requested issues (3D Dripstone, Turret Scatter logic, Beehive targeting & sounds). 

I've swapped the Dripstone visuals to use `BlockDisplay` entities, which guarantee a true 3D block representation in the world without disrupting mob pathfinding.

---

### 1. Dripstone 3D Falling Blocks (`TowerManager.java`)

Replace your `playDripstoneStrike()` method. This uses a `BlockDisplay` to perfectly render a 3D Pointed Dripstone pointing downwards.

```java
    private void playDripstoneStrike(Tower tower, Location targetLoc, double damage, Mob target, boolean isWave) {
        Location startLoc = targetLoc.clone().add(0, 5.0, 0);
        
        // Spawn a BlockDisplay for true 3D visuals
        org.bukkit.entity.BlockDisplay fallingSpike = startLoc.getWorld().spawn(startLoc, org.bukkit.entity.BlockDisplay.class, bd -> {
            org.bukkit.block.data.type.PointedDripstone data = (org.bukkit.block.data.type.PointedDripstone) Material.POINTED_DRIPSTONE.createBlockData();
            data.setVerticalDirection(org.bukkit.block.BlockFace.DOWN); // Point downwards
            bd.setBlock(data);
            
            // Center the block properly
            bd.setTransformation(new org.bukkit.util.Transformation(
                new org.joml.Vector3f(-0.5f, 0f, -0.5f), 
                new org.joml.Quaternionf(), 
                new org.joml.Vector3f(1f, 1f, 1f),
                new org.joml.Quaternionf()
            ));
        });

        startLoc.getWorld().playSound(startLoc, Sound.ENTITY_DRIPSTONE_FALL, 1.0f, 1.0f);
        String arena = plugin.getPlotConfigManager().getPlotArena(tower.getPlotId());

        new BukkitRunnable() {
            int ticks = 0;
            @Override
            public void run() {
                if (ticks > 25 || !fallingSpike.isValid()) {
                    fallingSpike.remove();
                    this.cancel();
                    return;
                }
                
                Location current = fallingSpike.getLocation();
                current.subtract(0, 0.4, 0); // Falling speed
                fallingSpike.teleport(current);
                
                // On Impact:
                if (current.getY() <= targetLoc.getY() + 0.5) {
                    current.getWorld().spawnParticle(org.bukkit.Particle.BLOCK, current, 15, 0.2, 0.1, 0.2, Material.POINTED_DRIPSTONE.createBlockData());
                    current.getWorld().playSound(current, Sound.BLOCK_POINTED_DRIPSTONE_LAND, 1.0f, 0.9f);
                    
                    if (isWave) {
                        for (Mob m : getMobsInRadius(current, 1.5, arena)) {
                            m.damage(damage);
                        }
                    } else if (target != null && target.isValid() && !target.isDead()) {
                        target.damage(damage);
                    }
                    
                    fallingSpike.remove();
                    this.cancel();
                }
                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }
```

---

### 2. Dripstone 3D Ground Hazards (`TowerManager.java`)

In `buildTowerStructure()`, replace the Dripstone logic to spawn `BlockDisplay` ground hazards pointing *upwards*.

```java
        // Clear old hazards safely
        for (Location loc : tower.getHazardTiles()) {
            center.getWorld().getNearbyEntities(loc, 1.0, 2.0, 1.0).stream()
                .filter(e -> e.getScoreboardTags().contains("td_hazard"))
                .forEach(org.bukkit.entity.Entity::remove);
        }
        tower.getHazardTiles().clear();

        if (type == TowerType.DRIPSTONE && level >= 2) {
            java.util.List<Location> track = getTrackLocationsWithinRange(tower);
            int count = plugin.getTowerConfigManager().getStat(TowerType.DRIPSTONE, level, "hazard_count", 6);
            int placed = 0;
            
            for (int i = 0; i < 30; i++) { 
                if (placed >= count || track.isEmpty()) break;
                
                Location baseLoc = track.get(new java.util.Random().nextInt(track.size())).clone();
                baseLoc.add((Math.random() - 0.5) * 2.5, 0, (Math.random() - 0.5) * 2.5);
                
                if (baseLoc.distanceSquared(tower.getCenterLocation()) <= tower.getRange() * tower.getRange()) {
                    Location hazardLoc = baseLoc.clone(); 
                    
                    org.bukkit.entity.BlockDisplay hazardStand = hazardLoc.getWorld().spawn(hazardLoc, org.bukkit.entity.BlockDisplay.class, bd -> {
                        org.bukkit.block.data.type.PointedDripstone data = (org.bukkit.block.data.type.PointedDripstone) Material.POINTED_DRIPSTONE.createBlockData();
                        data.setVerticalDirection(org.bukkit.block.BlockFace.UP); // Point upwards from the ground
                        bd.setBlock(data);
                        bd.addScoreboardTag("td_hazard");
                        
                        bd.setTransformation(new org.bukkit.util.Transformation(
                            new org.joml.Vector3f(-0.5f, 0f, -0.5f), 
                            new org.joml.Quaternionf(), 
                            new org.joml.Vector3f(1f, 1f, 1f),
                            new org.joml.Quaternionf()
                        ));
                    });
                    
                    tower.getHazardTiles().add(hazardStand.getLocation());
                    placed++;
                }
            }
        }
```

**Cleanup addition:** Inside `removeTower()` make sure to properly delete these BlockDisplays when the tower is removed or sold! Add this right next to the landmine cleanup logic:

```java
            // Add this to removeTower() to clean up Dripstone Hazards
            for (Location loc : tower.getHazardTiles()) {
                if (loc.getWorld() != null) {
                    loc.getWorld().getNearbyEntities(loc, 1.0, 2.0, 1.0).stream()
                        .filter(e -> e.getScoreboardTags().contains("td_hazard"))
                        .forEach(org.bukkit.entity.Entity::remove);
                }
            }
            tower.getHazardTiles().clear();
```

---

### 3. Turret Scatter Arrows Bouncing Inside Tower (`TowerManager.java`)

In `shootTarget()`, replace the Turret Scatter logic. We offset the initial arrow spawn location *forward* out of the tower's collision box.

```java
            case TURRET -> {
                if ("scatter".equals(tower.getPathId())) {
                    int arrows = plugin.getTowerConfigManager().getStat(TowerType.TURRET, tower.getPathId(), tower.getLevel(), "arrows", 5);
                    double damage = tower.getDamage();
                    
                    org.bukkit.util.Vector dir = target.getEyeLocation().toVector().subtract(start.toVector()).normalize();
                    
                    // Push the spawn location 1.5 blocks outwards so arrows don't instantly hit the tower structure
                    Location safeStart = start.clone().add(dir.clone().multiply(1.5));
                    
                    for (int i = 0; i < arrows; i++) {
                        Location spawnLoc = safeStart.clone().add((Math.random()-0.5)*0.5, (Math.random()-0.5)*0.5, (Math.random()-0.5)*0.5);
                        org.bukkit.entity.Arrow arrow = safeStart.getWorld().spawn(spawnLoc, org.bukkit.entity.Arrow.class);
                        arrow.setDamage(damage);
                        arrow.setShooter(null);
                        
                        // Apply strong randomized spread to velocity
                        double spread = 0.35;
                        org.bukkit.util.Vector spreadDir = dir.clone().add(new org.bukkit.util.Vector(
                            (Math.random() - 0.5) * spread,
                            (Math.random() - 0.5) * spread,
                            (Math.random() - 0.5) * spread
                        )).normalize();
                        
                        arrow.setVelocity(spreadDir.multiply(2.5));
                    }
                    start.getWorld().playSound(start, Sound.ENTITY_ARROW_SHOOT, 0.8f, 0.8f);
                } else {
                    // gatling or tier 1
                    target.damage(tower.getDamage());
                    drawParticleLine(start, target.getEyeLocation(), org.bukkit.Particle.CRIT);
                    start.getWorld().playSound(start, Sound.ENTITY_ARROW_SHOOT, 0.5f, 1.8f);
                }
            }
```

---

### 4. Beehive Targeting & Sounds (`TowerManager.java`)

Replace the second half of `tickBeehive()` (below where the bees are spawned). This ensures Swarm bees each target a unique mob, don't overlap, and use the generic TNT sound.

```java
        if (tick % 2 != 0) return;
        java.util.List<Mob> targets = getMobsInRadius(tower.getCenterLocation(), tower.getRange(), arena);

        int targetIndex = 0;
        int index = 0;
        java.util.Iterator<org.bukkit.entity.Bee> beeIt = tower.getSpawnedBees().iterator();
        
        while (beeIt.hasNext()) {
            org.bukkit.entity.Bee bee = beeIt.next();
            index++;

            // Assign unique targets to each bee if possible
            Mob assignedTarget = null;
            if (targetIndex < targets.size()) {
                assignedTarget = targets.get(targetIndex);
                if (!goliath) {
                    targetIndex++; // Swarm bees each take a different target
                }
            }

            if (assignedTarget == null || !assignedTarget.isValid()) {
                // No targets available for this bee, orbit smoothly around the tower
                double orbitSpeed = 0.05;
                double radius = 1.5 + (index * 0.2); 
                double angle = (tick * orbitSpeed) + (index * Math.PI / 2);
                Location orbitLoc = hiveTop.clone().add(Math.cos(angle) * radius, Math.sin(tick * 0.05) * 0.5, Math.sin(angle) * radius);
                
                org.bukkit.util.Vector toOrbit = orbitLoc.toVector().subtract(bee.getLocation().toVector());
                bee.setVelocity(toOrbit.normalize().multiply(0.3));
                continue;
            }
            
            double distSq = assignedTarget.getLocation().distanceSquared(bee.getLocation());
            if (distSq <= 1.44) {
                Location pop = bee.getLocation();
                pop.getWorld().spawnParticle(org.bukkit.Particle.EXPLOSION, pop, 1);
                
                // Sound changed to standard TNT explosion
                pop.getWorld().playSound(pop, Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 1.0f);
                
                assignedTarget.setNoDamageTicks(0); 
                assignedTarget.damage(tower.getDamage());
                
                bee.remove();
                beeIt.remove();
            } else {
                org.bukkit.util.Vector dir = assignedTarget.getLocation().add(0, 0.5, 0).toVector()
                        .subtract(bee.getLocation().toVector()).normalize();
                bee.setVelocity(dir.multiply(0.45));
            }
        }
```
