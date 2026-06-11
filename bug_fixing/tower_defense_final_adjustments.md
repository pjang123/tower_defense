# Tower Defense Final Adjustments & Fixes

Here are the complete, step-by-step code changes to implement all of the requested adjustments, incorporating your clarifications.

---

## 1. `towers.yaml` Updates
You will need to update the `towers.yaml` file to give the branching towers a base Level 1, adjust damages, and fix paths.

**Remove Level 4 from Dripstone:**
```yaml
dripstone:
  name: "Dripstone Tower"
  structure_file: "dripstone"
  base_material: DRIPSTONE_BLOCK
  levels:
    1:
      cost: 200
      range: 10.0
      # ... other stats ...
    # Delete the level 4 entry entirely
```

**Update Turret, Bombardier, and Beehive to have a Base Level 1:**

```yaml
turret:
  name: "Turret"
  structure_file: "turret"
  base_material: IRON_BLOCK
  levels:
    1:
      cost: 200
      range: 12.0
      damage: 2.0
      cooldown: 10
  paths:
    gatling:
      2:
        cost: 250
        range: 12.0
        damage: 0.5
        cooldown: 2
      3:
        cost: 350
        range: 13.5
        damage: 0.8
        cooldown: 2
      4:
        cost: 500
        range: 15.0
        damage: 1.2
        cooldown: 1
    scatter:
      2:
        cost: 250
        range: 10.0
        damage: 1.0
        cooldown: 20
        arrows: 5
      3:
        cost: 350
        range: 11.5
        damage: 1.5
        cooldown: 18
        arrows: 7
      4:
        cost: 500
        range: 13.0
        damage: 2.0
        cooldown: 15
        arrows: 9

bombardier:
  name: "Bombardier"
  structure_file: "bombardier"
  base_material: BRICKS
  levels:
    1:
      cost: 150
      range: 10.0
      damage: 4.0
      cooldown: 60
  paths:
    bigger_bombs:
      2:
        cost: 175
        range: 10.0
        damage: 6.0
        cooldown: 50
        radius: 3.5
      3:
        cost: 250
        range: 12.0
        damage: 8.0
        cooldown: 45
        radius: 4.5
      4:
        cost: 400
        range: 14.0
        damage: 12.0
        cooldown: 40
        radius: 5.5
    landmines:
      2:
        cost: 225
        range: 12.0
        damage: 25.0
        cooldown: 80
      3:
        cost: 325
        range: 14.0
        damage: 35.0
        cooldown: 75
      4:
        cost: 450
        range: 16.0
        damage: 45.0
        cooldown: 70

beehive:
  name: "Beehive"
  structure_file: "beehive"
  base_material: HONEYCOMB_BLOCK
  levels:
    1:
      cost: 100
      range: 5.0
      damage: 2.0
      cooldown: 50
  paths:
    goliath:
      2:
        cost: 150
        range: 5.0
        damage: 6.0
        cooldown: 40
        scale: 2.0
      # ... shift remaining levels up by 1 ...
    swarm:
      2:
        cost: 175
        range: 7.0
        damage: 2.0
        cooldown: 60
        bee_count: 3
      # ... shift remaining levels up by 1 ...
```

---

## 2. `TowerConfigManager.java` Updates

Update `parseDefinition` to read both `levels` and `paths` so Tier 1 base towers work.

```java
    private TowerDefinition parseDefinition(String id, ConfigurationSection section) {
        String name = section.getString("name", id);
        String structureFile = section.getString("structure_file", id);

        Material baseMaterial = null;
        String materialName = section.getString("base_material");
        if (materialName != null) {
            baseMaterial = Material.matchMaterial(materialName);
        }

        Map<String, NavigableMap<Integer, TowerLevelStats>> paths = new LinkedHashMap<>();
        ConfigurationSection levels = section.getConfigurationSection("levels");
        ConfigurationSection pathsSection = section.getConfigurationSection("paths");
        
        NavigableMap<Integer, TowerLevelStats> baseLevels = new TreeMap<>();
        if (levels != null) {
            baseLevels = parseLevels(id, DEFAULT_PATH, levels);
            paths.put(DEFAULT_PATH, baseLevels);
        }
        
        if (pathsSection != null) {
            for (String pathName : pathsSection.getKeys(false)) {
                ConfigurationSection pathLevels = pathsSection.getConfigurationSection(pathName);
                if (pathLevels != null) {
                    NavigableMap<Integer, TowerLevelStats> specificPathLevels = parseLevels(id, pathName, pathLevels);
                    if (!baseLevels.isEmpty() && !specificPathLevels.containsKey(1)) {
                        specificPathLevels.put(1, baseLevels.get(1));
                    }
                    paths.put(pathName, specificPathLevels);
                }
            }
        }

        if (paths.isEmpty()) {
            plugin.getLogger().warning("Tower '" + id + "' has no levels or paths; skipped.");
            return null;
        }
        return new TowerDefinition(id, name, structureFile, baseMaterial, paths);
    }
```

---

## 3. GUI Updates in `TowerManager.java`

**In `openBuyTowerGUI()`:** Replace slots 23, 24, and 25 to show Base Level 1 stats instead of branching paths.

```java
        // Slot 23: Turret
        int turretCost = plugin.getTowerConfigManager().getCost(TowerType.TURRET, 1, 200);
        double turretDamage = plugin.getTowerConfigManager().getDamage(TowerType.TURRET, 1, 2.0);
        double turretSpeed = plugin.getTowerConfigManager().getCooldown(TowerType.TURRET, 1, 10L) / 20.0;
        gui.setItem(23, createGUIItem(
            Material.OBSERVER,
            ChatColor.WHITE + "" + ChatColor.BOLD + "Turret",
            ChatColor.GRAY + "Base Cost: " + ChatColor.YELLOW + turretCost + " Gold",
            ChatColor.GRAY + "Damage: " + ChatColor.YELLOW + turretDamage + " HP",
            ChatColor.GRAY + "Attack Speed: " + ChatColor.YELLOW + turretSpeed + "s",
            "",
            ChatColor.GRAY + "Shoots rapid single arrows.",
            ChatColor.GREEN + "Upgrades into Gatling or Scatter paths at Level 2."
        ));

        // Slot 24: Bombardier
        int bombCost = plugin.getTowerConfigManager().getCost(TowerType.BOMBARDIER, 1, 150);
        double bombDamage = plugin.getTowerConfigManager().getDamage(TowerType.BOMBARDIER, 1, 4.0);
        gui.setItem(24, createGUIItem(
            Material.TNT,
            ChatColor.DARK_RED + "" + ChatColor.BOLD + "Bombardier",
            ChatColor.GRAY + "Base Cost: " + ChatColor.YELLOW + bombCost + " Gold",
            ChatColor.GRAY + "Damage: " + ChatColor.YELLOW + bombDamage + " HP",
            "",
            ChatColor.GRAY + "Throws explosive bombs.",
            ChatColor.GREEN + "Upgrades into Bigger Bombs or Landmines at Level 2."
        ));

        // Slot 25: Beehive
        int beeCost = plugin.getTowerConfigManager().getCost(TowerType.BEEHIVE, 1, 100);
        double beeDamage = plugin.getTowerConfigManager().getDamage(TowerType.BEEHIVE, 1, 2.0);
        gui.setItem(25, createGUIItem(
            Material.BEE_NEST,
            ChatColor.GOLD + "" + ChatColor.BOLD + "Beehive",
            ChatColor.GRAY + "Base Cost: " + ChatColor.YELLOW + beeCost + " Gold",
            ChatColor.GRAY + "Damage: " + ChatColor.YELLOW + beeDamage + " HP",
            "",
            ChatColor.GRAY + "Spawns a basic bee to attack mobs.",
            ChatColor.GREEN + "Upgrades into Goliath or Swarm paths at Level 2."
        ));
```

**In your Event Listener (wherever GUI clicks are handled):** Trigger the Path Picker when upgrading a Level 1 tower that has paths.

```java
// Logic inside your InventoryClickListener for the Manage Tower GUI:
if (clickedSlot == 22) { // Upgrade slot
    if (tower.getLevel() == 1 && (tower.getType() == TowerType.TURRET || tower.getType() == TowerType.BOMBARDIER || tower.getType() == TowerType.BEEHIVE)) {
        player.closeInventory();
        plugin.getTowerManager().openPathPickerGUI(player, plotId, tower.getType());
        return;
    }
    // Else do standard upgrade...
}
```

---

## 4. Dripstone Mechanics in `TowerManager.java`

**Add `playDripstoneStrike()` method:**

```java
    private void playDripstoneStrike(Location targetLoc, double damage, Mob target) {
        Location startLoc = targetLoc.clone().add(0, 5.0, 0);
        
        ArmorStand fallingSpike = startLoc.getWorld().spawn(startLoc, ArmorStand.class, as -> {
            as.setInvisible(true);
            as.setMarker(true);
            as.setGravity(false);
            if (as.getEquipment() != null) {
                as.getEquipment().setHelmet(new ItemStack(Material.POINTED_DRIPSTONE));
            }
        });

        startLoc.getWorld().playSound(startLoc, Sound.ENTITY_DRIPSTONE_FALL, 1.0f, 1.0f);

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
                
                if (current.getY() <= targetLoc.getY() + 0.5) {
                    current.getWorld().spawnParticle(org.bukkit.Particle.BLOCK, current, 15, 0.2, 0.1, 0.2, Material.POINTED_DRIPSTONE.createBlockData());
                    current.getWorld().playSound(current, Sound.BLOCK_POINTED_DRIPSTONE_LAND, 1.0f, 0.9f);
                    
                    if (target != null && target.isValid() && !target.isDead()) {
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

**Update `buildTowerStructure()` for Dripstone Hazards (Armor Stands):**

```java
        // Clear old hazard stands
        for (Location loc : tower.getHazardTiles()) {
            // Because we previously stored locations, we need to find and remove the stands at those locations
            center.getWorld().getNearbyEntities(loc, 1.0, 1.0, 1.0).stream()
                .filter(e -> e instanceof ArmorStand && e.getScoreboardTags().contains("td_hazard"))
                .forEach(org.bukkit.entity.Entity::remove);
        }
        tower.getHazardTiles().clear();

        if (type == TowerType.DRIPSTONE && level >= 2) {
            for (Location wpLoc : getTrackLocationsWithinRange(tower)) {
                Location hazardLoc = wpLoc.clone().add(0, -1.2, 0);
                ArmorStand hazardStand = hazardLoc.getWorld().spawn(hazardLoc, ArmorStand.class, as -> {
                    as.setInvisible(true);
                    as.setMarker(true);
                    as.setGravity(false);
                    as.addScoreboardTag("td_hazard");
                    if (as.getEquipment() != null) {
                        as.getEquipment().setHelmet(new ItemStack(Material.POINTED_DRIPSTONE));
                    }
                });
                tower.getHazardTiles().add(hazardStand.getEyeLocation());
                tower.getLandmines().add(hazardStand); // Ensure it cleans up on destroy
                if (tower.getHazardTiles().size() >= 6) break;
            }
        }
```

**Update 3x5 Wave (T3) in `shootTarget()`:**

```java
            case DRIPSTONE -> {
                playDripstoneStrike(target.getLocation(), tower.getDamage(), target);

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
                            
                            // 5 blocks back, 3 blocks wide
                            for (int length = 1; length <= 5; length++) {
                                for (int width = -1; width <= 1; width++) {
                                    Location strikeLoc = currentLoc.clone()
                                        .add(backDir.clone().multiply(length))
                                        .add(rightDir.clone().multiply(width));
                                        
                                    long delay = length * 3L;
                                    org.bukkit.Bukkit.getScheduler().runTaskLater(plugin, () -> {
                                        playDripstoneStrike(strikeLoc, 0, null);
                                        for (Mob m : getMobsInRadius(strikeLoc, 1.2, towerArena)) {
                                            m.damage(waveDamage);
                                        }
                                    }, delay);
                                }
                            }
                        }
                    }
                }
            }
```

---

## 5. Turret & Bombardier Fixes in `TowerManager.java`

**Turret Scatter Cone in `shootTarget()`:**

```java
            case TURRET -> {
                if ("scatter".equals(tower.getPathId())) {
                    int arrows = plugin.getTowerConfigManager().getStat(TowerType.TURRET, tower.getPathId(), tower.getLevel(), "arrows", 5);
                    double damage = tower.getDamage();
                    
                    org.bukkit.util.Vector dir = target.getEyeLocation().toVector().subtract(start.toVector()).normalize();
                    
                    for (int i = 0; i < arrows; i++) {
                        org.bukkit.entity.Arrow arrow = start.getWorld().spawn(start, org.bukkit.entity.Arrow.class);
                        arrow.setDamage(damage);
                        arrow.setShooter(null); // Prevents friendly fire issues
                        
                        // Add spread
                        double spread = 0.2;
                        org.bukkit.util.Vector spreadDir = dir.clone().add(new org.bukkit.util.Vector(
                            (Math.random() - 0.5) * spread,
                            (Math.random() - 0.5) * spread,
                            (Math.random() - 0.5) * spread
                        )).normalize();
                        
                        arrow.setVelocity(spreadDir.multiply(2.5));
                    }
                    start.getWorld().playSound(start, Sound.ENTITY_ARROW_SHOOT, 0.8f, 0.8f);
                } else {
                    // gatling or base Tier 1
                    target.damage(tower.getDamage());
                    drawParticleLine(start, target.getEyeLocation(), org.bukkit.Particle.CRIT);
                    start.getWorld().playSound(start, Sound.ENTITY_ARROW_SHOOT, 0.5f, 1.8f);
                }
            }
```

**Bombardier Landmine Interpolation in `tickLandmines()`:**

```java
        // Replace the placement logic at the bottom of tickLandmines:
        java.util.List<Location> track = getTrackLocationsWithinRange(tower);
        if (track.isEmpty()) return;
        
        // Pick a random waypoint
        Location wp1 = track.get(new Random().nextInt(track.size()));
        Location mineLoc = wp1.clone();
        
        // Find next waypoint to interpolate
        java.util.Map<String, com.pauljang.towerDefense.data.TDWaypoint> graph = plugin.getWaypointConfigManager().getWaypointGraph(arena);
        for (com.pauljang.towerDefense.data.TDWaypoint wp : graph.values()) {
            if (wp.getLocation().distanceSquared(wp1) < 0.1 && !wp.getNextIds().isEmpty()) {
                com.pauljang.towerDefense.data.TDWaypoint nextWp = graph.get(wp.getNextIds().get(0));
                if (nextWp != null) {
                    Location wp2 = nextWp.getLocation();
                    double lerp = Math.random();
                    mineLoc.add(wp2.toVector().subtract(wp1.toVector()).multiply(lerp));
                }
                break;
            }
        }

        // Check if too close to existing mine
        for (ArmorStand existing : tower.getLandmines()) {
            if (existing.isValid() && existing.getEyeLocation().distanceSquared(mineLoc) < 2.25) {
                return; // Abort this tick
            }
        }

        Location standLoc = mineLoc.clone().add(0, -1.4, 0);
        ArmorStand stand = standLoc.getWorld().spawn(standLoc, ArmorStand.class, as -> {
            as.setInvisible(true);
            as.setMarker(true);
            as.setGravity(false);
            if (as.getEquipment() != null) as.getEquipment().setHelmet(new ItemStack(Material.TNT));
        });
        tower.getLandmines().add(stand);
        tower.setLastAttackTick(tick);
        mineLoc.getWorld().playSound(mineLoc, Sound.ENTITY_TNT_PRIMED, 0.6f, 1.5f);
```

---

## 6. Beehive Mechanics in `TowerManager.java`

**Update `tickBeehive()`:**

```java
    private void tickBeehive(Tower tower, long tick) {
        String arena = plugin.getPlotConfigManager().getPlotArena(tower.getPlotId());
        tower.getSpawnedBees().removeIf(bee -> bee == null || !bee.isValid() || bee.isDead());

        boolean goliath = "goliath".equals(tower.getPathId());
        int cap = goliath ? 1 : plugin.getTowerConfigManager().getStat(
                TowerType.BEEHIVE, tower.getPathId(), tower.getLevel(), "bee_count", 1);
                
        // Fallback for Tier 1 base
        if (tower.getLevel() == 1) cap = 1; 

        double structureHeight = tower.getStructureSize() != null ? tower.getStructureSize().getBlockY() : 3.0;
        Location hiveTop = tower.getCenterLocation().clone().add(0, structureHeight + 1.0, 0);

        // Spawn entire swarm at once if empty and off cooldown
        if (tower.getSpawnedBees().isEmpty() && tick - tower.getLastAttackTick() >= getEffectiveCooldown(tower)) {
            for (int i = 0; i < cap; i++) {
                org.bukkit.entity.Bee bee = hiveTop.getWorld().spawn(hiveTop, org.bukkit.entity.Bee.class, b -> {
                    b.setInvulnerable(true);
                    b.setCollidable(false);
                    b.setPersistent(true);
                    b.setRemoveWhenFarAway(false);
                    b.getPersistentDataContainer().set(new org.bukkit.NamespacedKey(plugin, "td_tower_pet"),
                            org.bukkit.persistence.PersistentDataType.BYTE, (byte) 1);
                    try { org.bukkit.Bukkit.getMobGoals().removeAllGoals(b); } catch (Throwable ignored) {}
                });
                if (goliath) setEntityScale(bee, plugin.getTowerConfigManager().getStat(TowerType.BEEHIVE, tower.getPathId(), tower.getLevel(), "scale", 2.0));
                tower.getSpawnedBees().add(bee);
            }
            tower.setLastAttackTick(tick);
            // Sound removed here as requested
        }

        if (tick % 2 != 0) return;
        java.util.List<Mob> targets = getMobsInRadius(tower.getCenterLocation(), tower.getRange(), arena);
        
        // Find one target for the whole swarm to focus
        Mob primeTarget = targets.isEmpty() ? null : targets.get(0);

        int index = 0;
        java.util.Iterator<org.bukkit.entity.Bee> beeIt = tower.getSpawnedBees().iterator();
        while (beeIt.hasNext()) {
            org.bukkit.entity.Bee bee = beeIt.next();
            index++;
            
            if (primeTarget == null || !primeTarget.isValid()) {
                // Orbit smoothly around the tower
                double orbitSpeed = 0.05;
                double radius = 1.5 + (index * 0.2); // slight offset per bee
                double angle = (tick * orbitSpeed) + (index * Math.PI / 2);
                Location orbitLoc = hiveTop.clone().add(Math.cos(angle) * radius, Math.sin(tick * 0.05) * 0.5, Math.sin(angle) * radius);
                
                org.bukkit.util.Vector toOrbit = orbitLoc.toVector().subtract(bee.getLocation().toVector());
                bee.setVelocity(toOrbit.normalize().multiply(0.3));
                continue;
            }
            
            double distSq = primeTarget.getLocation().distanceSquared(bee.getLocation());
            if (distSq <= 1.44) {
                Location pop = bee.getLocation();
                pop.getWorld().spawnParticle(org.bukkit.Particle.EXPLOSION, pop, 1);
                pop.getWorld().playSound(pop, Sound.ENTITY_BEE_DEATH, 1.0f, 0.8f);
                primeTarget.damage(tower.getDamage());
                bee.remove();
                beeIt.remove();
            } else {
                org.bukkit.util.Vector dir = primeTarget.getLocation().add(0, 0.5, 0).toVector()
                        .subtract(bee.getLocation().toVector()).normalize();
                bee.setVelocity(dir.multiply(0.45));
            }
        }
    }
```

---

## 7. 5x5 Tower Placement Validation in `TowerManager.java`

**Add `canFit5x5()` helper and implement in `placeTower()`:**

```java
    private boolean canFit5x5(Location center) {
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                org.bukkit.block.Block floorBlock = center.clone().add(x, -1, z).getBlock();
                // AIR indicates it stepped off the edge of the plot pedestal. 
                // Adjust if your plots are flush with ground level!
                if (floorBlock.getType() == Material.AIR || floorBlock.getType() == Material.WATER) {
                    return false; 
                }
            }
        }
        return true;
    }

    public void placeTower(String plotId, TowerType type, java.util.UUID ownerId, String pathId) {
        Location center = plugin.getPlotConfigManager().getPlotCenter(plotId);
        if (center == null) return;
        
        // --- ADD THIS BLOCK HERE ---
        if (type == TowerType.GOLEM || type == TowerType.HAPPY_GHAST || type == TowerType.TURRET || 
            type == TowerType.BOMBARDIER || type == TowerType.THUNDER || type == TowerType.BEEHIVE) {
            
            if (!canFit5x5(center)) {
                if (ownerId != null) {
                    Player p = org.bukkit.Bukkit.getPlayer(ownerId);
                    if (p != null) p.sendMessage(ChatColor.RED + "You cannot place a 5x5 tower on a 3x3 plot!");
                }
                return;
            }
        }
        // ---------------------------

        if (hasTower(plotId)) {
            removeTower(plotId);
        }

        // ... existing cleanup and placement code continues ...
```
