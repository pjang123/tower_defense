# Tower Defense Adjustments & Fixes

Here are the complete code updates addressing all of your requested tweaks (EMP targeting, Dripstone sync/orientation, Bombardier logic, Turret spread, Beehive i-frames, etc.).

---

### 1. `towers.yaml` - Removing Level 4s

Open your `towers.yaml` and remove the level 4 blocks for **Thunder** and **Dripstone** towers.

**Thunder Tower:**
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
      range: 18.0
      damage: 11.0
      cooldown: 75
      chain_count: 5
    # REMOVE LEVEL 4
```

**Dripstone Tower:**
```yaml
dripstone:
  name: "Dripstone Tower"
  structure_file: "dripstone"
  base_material: DRIPSTONE_BLOCK
  levels:
    1:
      cost: 200
      range: 10.0
      damage: 6.0
      cooldown: 60
    2:
      cost: 300
      range: 12.0
      damage: 9.0
      cooldown: 55
    3:
      cost: 450
      range: 14.0
      damage: 12.0
      cooldown: 50
    # REMOVE LEVEL 4
```

---

### 2. General Tower Ticker & Placement (EMP Fixes)

**Fix 1: Apply EMP to newly placed towers.**
In `TowerManager.java`, find `placeTower(...)` and add the spell check right after creating the `Tower` object:
```java
        Tower tower = new Tower(plotId, center, type);
        tower.setPathId(pathId);
        tower.setOwnerId(ownerId);
        
        // --- NEW: Check if EMP is active in this arena to disable new towers ---
        String arena = plugin.getPlotConfigManager().getPlotArena(plotId);
        if (plugin.getGameManager().isSpellActive(arena, "EMP")) {
            tower.setDisabled(true);
        }
        
        placedTowers.put(plotId, tower);
```

**Fix 2: Add Glitched Text to EMP Holograms.**
In `startTowerTicker()`, update the `isDisabled()` block:
```java
                    if (tower.isDisabled()) {
                        tower.setEmpDisplayed(true);
                        java.util.List<ArmorStand> empStands = tower.getHolograms();
                        if (!empStands.isEmpty() && empStands.get(0) != null && empStands.get(0).isValid()) {
                            // ADDED ChatColor.MAGIC for glitched text effect
                            empStands.get(0).setCustomName(ChatColor.RED + "" + ChatColor.MAGIC + "||| " + ChatColor.RED + ChatColor.BOLD + "[DISABLED EMP] " + ChatColor.RED + ChatColor.MAGIC + "|||");
                            empStands.get(0).setCustomNameVisible(true);
                        }
                        if (tick % 5 == 0) {
                            Location center = tower.getCenterLocation();
                            center.getWorld().spawnParticle(org.bukkit.Particle.LARGE_SMOKE, center.clone().add(0, 2, 0), 12, 0.5, 0.7, 0.5, 0.02);
                            center.getWorld().spawnParticle(org.bukkit.Particle.DUST, center.clone().add(0, 2.5, 0), 10, 0.5, 0.7, 0.5,
                                    new org.bukkit.Particle.DustOptions(org.bukkit.Color.RED, 1.5f));
                        }
                        continue;
                    }
```

---

### 3. Dripstone Mechanics (Syncing, Full Block Waves, & Upside-down fixes)

**Fix 1: The Unified Strike Method (Fixes desync & uses full block)**
Replace `playDripstoneStrike()` with this updated version that waits for impact to deal damage:
```java
    private void playDripstoneStrike(Tower tower, Location targetLoc, double damage, Mob target, boolean isWave) {
        Location startLoc = targetLoc.clone().add(0, 5.0, 0);
        
        // Use full block for waves, pointed dripstone for standard attacks
        Material blockMat = isWave ? Material.DRIPSTONE_BLOCK : Material.POINTED_DRIPSTONE;
        
        ArmorStand fallingSpike = startLoc.getWorld().spawn(startLoc, ArmorStand.class, as -> {
            as.setInvisible(true);
            as.setMarker(true);
            as.setGravity(false);
            if (as.getEquipment() != null) {
                as.getEquipment().setHelmet(new ItemStack(blockMat));
            }
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
                current.subtract(0, 0.4, 0);
                fallingSpike.teleport(current);
                
                // On Impact:
                if (current.getY() <= targetLoc.getY() + 0.5) {
                    current.getWorld().spawnParticle(org.bukkit.Particle.BLOCK, current, 15, 0.2, 0.1, 0.2, blockMat.createBlockData());
                    current.getWorld().playSound(current, Sound.BLOCK_POINTED_DRIPSTONE_LAND, 1.0f, 0.9f);
                    
                    // Delay the damage to hit exactly when the block hits the ground
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

**Fix 2: `shootTarget()` Updates for Dripstone (T2/T3 Wave)**
```java
            case DRIPSTONE -> {
                playDripstoneStrike(tower, target.getLocation(), tower.getDamage(), target, false);

                if (tower.getLevel() >= 3) {
                    com.pauljang.towerDefense.entities.TDMob tdMob = plugin.getMobManager().getActiveMobs().stream()
                        .filter(m -> m.getEntity().equals(target)).findFirst().orElse(null);
                        
                    if (tdMob != null && tdMob.getPathHistory().size() >= 2) {
                        Location currentLoc = target.getLocation();
                        String prevWpId = tdMob.getPathHistory().get(tdMob.getPathHistory().size() - 2);
                        com.pauljang.towerDefense.data.TDWaypoint prevWp = tdMob.getWaypointGraph().get(prevWpId);
                        
                        if (prevWp != null) {
                            org.bukkit.util.Vector backDir = prevWp.getLocation().toVector().subtract(currentLoc.toVector()).normalize();
                            org.bukkit.util.Vector rightDir = new org.bukkit.util.Vector(-backDir.getZ(), 0, backDir.getX()).normalize();
                            
                            double waveDamage = tower.getDamage() * 0.5;
                            
                            // 5 blocks long backwards, 3 blocks wide
                            for (int length = 1; length <= 5; length++) {
                                for (int width = -1; width <= 1; width++) {
                                    Location strikeLoc = currentLoc.clone()
                                        .add(backDir.clone().multiply(length))
                                        .add(rightDir.clone().multiply(width));
                                        
                                    long delay = length * 3L;
                                    org.bukkit.Bukkit.getScheduler().runTaskLater(plugin, () -> {
                                        // Pass true for "isWave" and pass waveDamage. The runnable handles the AoE damage!
                                        playDripstoneStrike(tower, strikeLoc, waveDamage, null, true);
                                    }, delay);
                                }
                            }
                        }
                    }
                }
            }
```

**Fix 3: Dripstone Hazards (Upside-down flip & random pathing)**
In `buildTowerStructure()`:
```java
        // Clear old hazards
        for (Location loc : tower.getHazardTiles()) {
            center.getWorld().getNearbyEntities(loc, 1.0, 2.0, 1.0).stream()
                .filter(e -> e instanceof ArmorStand && e.getScoreboardTags().contains("td_hazard"))
                .forEach(org.bukkit.entity.Entity::remove);
        }
        tower.getHazardTiles().clear();

        if (type == TowerType.DRIPSTONE && level >= 2) {
            java.util.List<Location> track = getTrackLocationsWithinRange(tower);
            int count = plugin.getTowerConfigManager().getStat(TowerType.DRIPSTONE, level, "hazard_count", 6);
            int placed = 0;
            
            for (int i = 0; i < 30; i++) { // Attempt up to 30 times to find random spots
                if (placed >= count || track.isEmpty()) break;
                
                Location baseLoc = track.get(new java.util.Random().nextInt(track.size())).clone();
                // Randomly offset along path within tower radius
                baseLoc.add((Math.random() - 0.5) * 2.5, 0, (Math.random() - 0.5) * 2.5);
                
                if (baseLoc.distanceSquared(tower.getCenterLocation()) <= tower.getRange() * tower.getRange()) {
                    Location hazardLoc = baseLoc.clone().add(0, -1.2, 0);
                    ArmorStand hazardStand = hazardLoc.getWorld().spawn(hazardLoc, ArmorStand.class, as -> {
                        as.setInvisible(true);
                        as.setMarker(true);
                        as.setGravity(false);
                        as.addScoreboardTag("td_hazard");
                        
                        // FLIP THE HEAD 180 DEGREES SO THE DRIPSTONE POINTS UPWARDS!
                        as.setHeadPose(new org.bukkit.util.EulerAngle(Math.PI, 0, 0)); 
                        
                        if (as.getEquipment() != null) {
                            as.getEquipment().setHelmet(new ItemStack(Material.POINTED_DRIPSTONE));
                        }
                    });
                    tower.getHazardTiles().add(hazardStand.getEyeLocation());
                    tower.getLandmines().add(hazardStand);
                    placed++;
                }
            }
        }
```

---

### 4. Turret Spread & Bombardier Arcs/Mines

**Turret Scatter Attack in `shootTarget()`:**
```java
            case TURRET -> {
                if ("scatter".equals(tower.getPathId())) {
                    int arrows = plugin.getTowerConfigManager().getStat(TowerType.TURRET, tower.getPathId(), tower.getLevel(), "arrows", 5);
                    double damage = tower.getDamage();
                    
                    org.bukkit.util.Vector dir = target.getEyeLocation().toVector().subtract(start.toVector()).normalize();
                    
                    for (int i = 0; i < arrows; i++) {
                        // Offset spawn locations slightly so arrows don't instantly collide with each other
                        Location spawnLoc = start.clone().add((Math.random()-0.5)*0.8, (Math.random()-0.5)*0.8, (Math.random()-0.5)*0.8);
                        org.bukkit.entity.Arrow arrow = start.getWorld().spawn(spawnLoc, org.bukkit.entity.Arrow.class);
                        arrow.setDamage(damage);
                        arrow.setShooter(null);
                        
                        // Apply strong randomized spread to velocity
                        double spread = 0.5;
                        org.bukkit.util.Vector spreadDir = dir.clone().add(new org.bukkit.util.Vector(
                            (Math.random() - 0.5) * spread,
                            (Math.random() - 0.5) * spread,
                            (Math.random() - 0.5) * spread
                        )).normalize();
                        
                        arrow.setVelocity(spreadDir.multiply(2.5));
                    }
                    start.getWorld().playSound(start, Sound.ENTITY_ARROW_SHOOT, 0.8f, 0.8f);
                }
```

**Bombardier Arc (Prevents sticking inside tower) in `shootTarget()`:**
```java
            case BOMBARDIER -> {
                if ("landmines".equals(tower.getPathId())) return;
                double radius = plugin.getTowerConfigManager().getStat(TowerType.BOMBARDIER, tower.getPathId(), tower.getLevel(), "radius", 3.5);
                double damage = tower.getDamage();
                Location targetLoc = target.getLocation().clone();

                // Spawn higher up to clear the tower's structure
                Location bombStart = start.clone().add(0, 1.5, 0); 
                
                org.bukkit.entity.TNTPrimed tnt = bombStart.getWorld().spawn(bombStart, org.bukkit.entity.TNTPrimed.class, t -> {
                    t.setFuseTicks(25);
                    t.setYield(0.0f);
                    t.setIsIncendiary(false);
                });
                org.bukkit.util.Vector dir = targetLoc.toVector().subtract(bombStart.toVector());
                double dist = Math.max(1.0, dir.length());
                
                // Boost the Y velocity so it arcs overhead
                tnt.setVelocity(dir.normalize().multiply(0.25 * dist).setY(0.7));
                bombStart.getWorld().playSound(bombStart, Sound.ENTITY_TNT_PRIMED, 1.0f, 1.0f);
                // ... rest of explosion code ...
```

**Bombardier Landmine Paths & Triggers in `tickLandmines()`:**
```java
    private void tickLandmines(Tower tower, long tick) {
        String arena = plugin.getPlotConfigManager().getPlotArena(tower.getPlotId());

        java.util.Iterator<ArmorStand> it = tower.getLandmines().iterator();
        while (it.hasNext()) {
            ArmorStand mine = it.next();
            if (mine == null || !mine.isValid()) {
                it.remove();
                continue;
            }
            // Mobs are usually walking above the stand's origin. Check slightly higher up with a larger 2.0 radius
            Location trigger = mine.getLocation().clone().add(0, 1.5, 0); 
            java.util.List<Mob> hit = getMobsInRadius(trigger, 2.0, arena);
            if (!hit.isEmpty()) {
                trigger.getWorld().spawnParticle(org.bukkit.Particle.EXPLOSION_EMITTER, trigger, 1);
                trigger.getWorld().playSound(trigger, Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 1.0f);
                hit.get(0).damage(tower.getDamage());
                mine.remove();
                it.remove();
            }
        }

        if (tower.getLandmines().size() >= 3) return;
        if (tick - tower.getLastAttackTick() < getEffectiveCooldown(tower)) return;

        java.util.List<Location> track = getTrackLocationsWithinRange(tower);
        if (track.isEmpty()) return;

        // Pick a random waypoint
        Location wp1 = track.get(new java.util.Random().nextInt(track.size()));
        Location mineLoc = wp1.clone();
        
        // Randomly offset the mine 1-2 blocks away from the path center
        mineLoc.add((Math.random() - 0.5) * 3.0, 0, (Math.random() - 0.5) * 3.0);
        
        // Ensure the offset mine is still inside the tower's shooting radius
        if (mineLoc.distanceSquared(tower.getCenterLocation()) > tower.getRange() * tower.getRange()) {
            return; // Abort tick, will try again next tick
        }

        // Check proximity to other mines
        for (ArmorStand existing : tower.getLandmines()) {
            if (existing.isValid() && existing.getLocation().distanceSquared(mineLoc) < 3.0) {
                return; 
            }
        }

        Location standLoc = mineLoc.clone().add(0, -1.4, 0);
        ArmorStand stand = standLoc.getWorld().spawn(standLoc, ArmorStand.class, as -> {
            as.setInvisible(true);
            as.setMarker(true);
            as.setGravity(false);
            if (as.getEquipment() != null) {
                as.getEquipment().setHelmet(new ItemStack(Material.TNT));
            }
        });
        tower.getLandmines().add(stand);
        tower.setLastAttackTick(tick);
        mineLoc.getWorld().playSound(mineLoc, Sound.ENTITY_TNT_PRIMED, 0.6f, 1.5f);
    }
```

---

### 5. Beehive Updates

**Fix 1: Respawn Bees on Upgrade**
In your code handling GUI clicks (where you actually upgrade the tower levels), add this snippet immediately after incrementing the tower's level:
```java
        // If a beehive is upgraded, clear its current active bees. 
        // The ticker will instantly resummon the new swarm at the upgraded count/size.
        if (tower.getType() == TowerType.BEEHIVE) {
            for (org.bukkit.entity.Bee bee : tower.getSpawnedBees()) {
                if (bee != null && bee.isValid()) bee.remove();
            }
            tower.getSpawnedBees().clear();
        }
```

**Fix 2: Fix Swarm i-frames so they all deal damage at once.**
In `tickBeehive()`:
```java
            if (distSq <= 1.44) {
                Location pop = bee.getLocation();
                pop.getWorld().spawnParticle(org.bukkit.Particle.EXPLOSION, pop, 1);
                
                // CRITICAL: Clear i-frames before damage so the swarm doesn't block each other!
                primeTarget.setNoDamageTicks(0); 
                primeTarget.damage(tower.getDamage());
                
                bee.remove();
                beeIt.remove();
            }
```

---

### 6. Chain Lightning Damage Buff
In `shootTarget()` under `case THUNDER`:
```java
                    // Increased chain lightning damage from 0.5 to 1.0 (100% damage)
                    double chainDamage = tower.getDamage() * 1.0; 
                    for (int i = 0; i < Math.min(chains, near.size()); i++) {
                        Mob chained = near.get(i);
                        drawParticleLine(target.getEyeLocation(), chained.getEyeLocation(), org.bukkit.Particle.ELECTRIC_SPARK);
                        chained.damage(chainDamage);
                    }
```
