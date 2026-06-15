// Golem pathing movement tick: wander along the track within 3 blocks, or chase within range radius
                    if (tower.getType() == TowerType.GOLEM && tower.getSpawnedGolem() != null && tower.getSpawnedGolem().isValid()) {
                        org.bukkit.entity.LivingEntity golem = tower.getSpawnedGolem();
                        if (golem instanceof org.bukkit.entity.Mob golemMob && tick % 5 == 0) {
                            String golemArena = plugin.getPlotConfigManager().getPlotArena(tower.getPlotId());
                            Mob nearestTrackMob = null;
                            double nearestDistSq = Double.MAX_VALUE;
                            double maxRangeSq = tower.getRange() * tower.getRange();

                            // 1. Target Mobs inside the range radius
                            for (Mob candidate : getMobsInRadius(tower.getCenterLocation(), tower.getRange(), golemArena)) {
                                double dSq = candidate.getLocation().distanceSquared(golem.getLocation());
                                if (dSq < nearestDistSq) {
                                    nearestDistSq = dSq;
                                    nearestTrackMob = candidate;
                                }
                            }

                            if (nearestTrackMob != null) {
                                // Double check the target hasn't wandered completely outside tower's maximum range radius 
                                if (nearestTrackMob.getLocation().distanceSquared(tower.getCenterLocation()) <= maxRangeSq) {
                                    golemMob.getPathfinder().moveTo(nearestTrackMob.getLocation(), 1.25);
                                    golemMob.setTarget(nearestTrackMob);
                                } else {
                                    nearestTrackMob = null; // Target is too far outside radius bounds, go to idle wander instead
                                }
                            }
                            
                            // 2. Idle Wander Logic (Runs when no target mobs are nearby or target leaves radius)
                            if (nearestTrackMob == null) {
                                golemMob.setTarget(null);
                                
                                // Only pick a new random wander location every 60 ticks (3 seconds) to prevent pathing jitter
                                if (tick % 60 == 0) {
                                    java.util.List<Location> track = getTrackLocationsWithinRange(tower);
                                    if (!track.isEmpty()) {
                                        // Pick a random waypoint inside the range
                                        Location randomWp = track.get(new java.util.Random().nextInt(track.size()));
                                        Location destination = randomWp.clone();
                                        
                                        // Attempt to offset perpendicularly from the line of the track
                                        java.util.Map<String, com.pauljang.towerDefense.data.TDWaypoint> graph = plugin.getWaypointConfigManager().getWaypointGraph(golemArena);
                                        org.bukkit.util.Vector offsetDir = new org.bukkit.util.Vector(1, 0, 0); // Default fallback direction
                                        
                                        for (com.pauljang.towerDefense.data.TDWaypoint wp : graph.values()) {
                                            if (wp.getLocation().distanceSquared(randomWp) < 0.2 && !wp.getNextIds().isEmpty()) {
                                                com.pauljang.towerDefense.data.TDWaypoint nextWp = graph.get(wp.getNextIds().get(0));
                                                if (nextWp != null) {
                                                    // Get the vector between waypoints and find its perpendicular flat 2D vector
                                                    org.bukkit.util.Vector pathLine = nextWp.getLocation().toVector().subtract(wp.getLocation().toVector()).normalize();
                                                    offsetDir = new org.bukkit.util.Vector(-pathLine.getZ(), 0, pathLine.getX()).normalize();
                                                }
                                                break;
                                            }
                                        }
                                        
                                        // Apply a random float offset up to 3 blocks on either side (-3 to +3 blocks)
                                        double randomOffsetAmount = (Math.random() - 0.5) * 6.0;
                                        destination.add(offsetDir.multiply(randomOffsetAmount));
                                        
                                        // CAUTION BOUNDS CHECK: Only move to the destination if it doesn't break the tower's constraint radius
                                        if (destination.distanceSquared(tower.getCenterLocation()) <= maxRangeSq) {
                                            golemMob.getPathfinder().moveTo(destination, 1.0);
                                        } else {
                                            // Fallback back safely towards center if the offset overflows outside the circle range
                                            golemMob.getPathfinder().moveTo(tower.getCenterLocation(), 1.0);
                                        }
                                    } else {
                                        golemMob.getPathfinder().moveTo(tower.getCenterLocation(), 1.0);
                                    }
                                }
                            }
                        }
                    }