# Tower Defense Map Setup Guide

This guide explains how to add a new map to the Tower Defense plugin, from initial setup to creating arenas with plots and waypoints.

---

## 1. Map File Structure

All maps must be placed in the `GAME_WORLD_TEMPLATES` folder in your server's root directory.

```
server/
├── GAME_WORLD_TEMPLATES/
│   ├── SINGLE_PLAYER/
│   │   └── your_map_name/
│   └── MULTI_PLAYER/
│       └── your_map_name/
```

### Choose Map Type
- **SINGLE_PLAYER/**: Maps for solo play (1 player)
- **MULTI_PLAYER/**: Maps for multiplayer matches (2+ players)

---

## 2. Prepare Your World Folder

Your map folder should contain this structure:

```
your_map_name/
├── region/                    # World chunk data (REQUIRED)
│   ├── r.0.0.mca
│   ├── r.0.-1.mca
│   └── ...
├── level.dat                  # World settings (REQUIRED)
├── map.yml                    # Map metadata (REQUIRED)
├── plots.yml                  # Plot locations (auto-generated)
└── waypoints.yml              # Mob pathfinding (auto-generated)
```

**Important**: 
- Place region files directly in `region/` folder at the world root
- Do NOT use the nested `dimensions/minecraft/overworld/region/` structure - this causes migration issues
- If your world uses the modern nested structure, move the `.mca` files from `dimensions/minecraft/overworld/region/` to `region/` and delete the `dimensions/` folder

---

## 3. Create map.yml

In your map's root folder, create a `map.yml` file with this structure:

```yaml
display-name: "Your Map Name"
author: "YourUsername"
description: "Optional description of your map"
max-players: 4  # Maximum players that can share this arena
arenas: 1       # Number of arenas in this map
```

**Arena Information**:
- An **arena** is a complete game area with its own set of plots and waypoint path
- In **multiplayer maps**, all players share the same arena (cooperative/team gameplay)
- Multiple players work together to defend the same path by building towers on shared plots
- Most maps have 1 arena, but you can create multiple arenas for variety
- Arena IDs start at "1" (not "0")

---

## 4. Load the World

Once your map folder is in place, load it into the server:

```
/td loadworld your_map_name
```

This command:
- Loads the world from the template folder
- Teleports you to the world's spawn point
- Allows you to begin editing

**Note**: The world name must match the folder name exactly.

---

## 5. Setup Arena Plots

Plots are 5x5 areas where players can build towers.

### 5.1 Get the Setup Wand
```
/td wand
```

You'll receive a **Blaze Rod** called "TD Setup Wand".

### 5.2 Enter Plot Mode
```
/td plotmode [arena]
```

Examples:
- `/td plotmode 1` - Edit plots for Arena 1
- `/td plotmode 2` - Edit plots for Arena 2

### 5.3 Select Plot Area
1. **Left-click** with the wand to set the first corner
2. **Right-click** with the wand to set the second corner
3. The plot is automatically saved when you select both corners

**Plot Guidelines**:
- Plots should be **5x5 blocks** (width x depth)
- Height is flexible but typically 1-2 blocks tall
- Plots should **NOT overlap** (the plugin will warn you)
- Leave space between plots for players to walk
- Distribute plots evenly along the mob path

### 5.4 Exit Plot Mode
```
/td plotmode
```

(Running the command again toggles it off)

---

## 6. Setup Waypoints

Waypoints define the path that mobs follow from spawn to the end goal.

### 6.1 Enter Waypoint Mode
```
/td waypointmode [arena]
```

Examples:
- `/td waypointmode 1` - Edit waypoints for Arena 1
- `/td waypointmode 2` - Edit waypoints for Arena 2

### 6.2 Place Waypoints
**Right-click** with the wand at each point along the path:
1. Start at the mob spawn point
2. Create waypoints along the desired path
3. End at the goal/base location

Waypoints are automatically connected in sequence (waypoint 0 → 1 → 2 → 3...).

**Waypoint Guidelines**:
- Place waypoints **every 10-20 blocks** along the path
- More waypoints = smoother mob movement
- Waypoints should follow a clear, logical path
- Avoid sharp turns or obstacles
- First waypoint (0) is the spawn point
- Last waypoint is where mobs reach the player's base

### 6.3 Exit Waypoint Mode
```
/td waypointmode
```

### 6.4 Clear Waypoints (if needed)
If you need to restart:
```
/td clearwaypoints [arena]
```

---

## 7. Delete Plots (if needed)

If you need to remove plots:

### 7.1 Enter Delete Plot Mode
```
/td deleteplotmode [arena]
```

### 7.2 Delete a Plot
**Right-click** any block within the plot with the wand to delete it.

### 7.3 Exit Delete Plot Mode
```
/td deleteplotmode
```

---

## 8. Direct Editing Benefits

**Important**: When editing worlds inside `GAME_WORLD_TEMPLATES`, all changes are saved **directly** to that world's config files:
- Plots save to `your_map_name/plots.yml`
- Waypoints save to `your_map_name/waypoints.yml`

**This means**:
- No need to run `/td saveconfig` for template worlds
- Changes are immediately bundled with the map
- When the world is cloned for a match, it includes your plots and waypoints
- Easy to share maps by copying the entire folder

---

## 9. Testing Your Map

### 9.1 Test Single Player
1. Return to lobby: `/td lobby`
2. Open the games GUI: Right-click "Open Games" item
3. Select "Single Player"
4. Your map will appear in the voting GUI

### 9.2 Test Multiplayer
1. Have 2+ players in the lobby
2. Open the games GUI
3. Select "Multiplayer"
4. Vote for your map
5. Wait for other players to vote
6. The match starts after voting ends

---

## 10. Map Configuration Files Reference

### plots.yml Structure
```yaml
plots:
  a1b2c3d4:  # Unique plot ID (auto-generated)
    arena: "1"
    pos1:
      world: "your_map_name"
      x: 100
      y: 64
      z: 200
    pos2:
      world: "your_map_name"
      x: 105
      y: 65
      z: 205
```

### waypoints.yml Structure
```yaml
waypoints:
  "1":  # Arena ID
    "0":  # Waypoint ID (sequential)
      world: "your_map_name"
      x: 150.5
      y: 64.0
      z: 250.5
      next: ["1"]  # Connected waypoint IDs
    "1":
      world: "your_map_name"
      x: 160.5
      y: 64.0
      z: 250.5
      next: ["2"]
    "2":
      world: "your_map_name"
      x: 170.5
      y: 64.0
      z: 260.5
      next: []  # Empty = end of path
```

---

## 11. Common Issues

### "No plots found"
- Make sure you're in plot mode for the correct arena
- Verify plots.yml exists in the world folder
- Check that plots don't overlap

### "No waypoints found"
- Make sure you're in waypoint mode for the correct arena
- Verify waypoints.yml exists in the world folder
- Create at least 2 waypoints (start and end)

### "World failed to load" or "Failed to migrate legacy world"
- Check that level.dat exists
- Verify the world folder name matches exactly
- Ensure region data exists in `region/` folder at world root (not nested in `dimensions/`)
- If you see migration errors, delete the world folder from server root and reload with corrected structure
- Delete any leftover dimension folders in lobby_world or other worlds

### Mobs don't spawn
- Check that waypoint 0 exists (the spawn point)
- Verify waypoints form a complete path
- Make sure the arena ID matches (usually "1" or "2")

---

## 12. Quick Setup Checklist

- [ ] Create world folder in `GAME_WORLD_TEMPLATES/SINGLE_PLAYER/` or `MULTI_PLAYER/`
- [ ] Add `map.yml` with display name and arena count
- [ ] Ensure world has `level.dat` and region data
- [ ] Load world with `/td loadworld <name>`
- [ ] Get setup wand with `/td wand`
- [ ] For each arena:
  - [ ] Enable plot mode: `/td plotmode [arena]`
  - [ ] Select 5x5 areas for plots (left-click, right-click)
  - [ ] Exit plot mode
  - [ ] Enable waypoint mode: `/td waypointmode [arena]`
  - [ ] Right-click to place waypoints along mob path
  - [ ] Exit waypoint mode
- [ ] Test the map in-game

---

## Tips for Great Maps

1. **Balance plot distribution**: Place plots evenly along the path so players can defend all sections
2. **Enough plots for teamwork**: In multiplayer maps, ensure there are enough plots for all players to build towers (e.g., 15-20 plots for a 4-player map)
3. **Clear mob paths**: Avoid obstacles or tight spaces that mobs might get stuck in
4. **Reasonable distance**: Don't make the path too long (bored players) or too short (too easy)
5. **Visual clarity**: Use different blocks or markers to indicate the start/end of the path
6. **Team coordination**: Design plot layouts that encourage players to work together strategically
7. **Test thoroughly**: Run through multiple waves to ensure mobs path correctly

---

**Need Help?**
- Use `/td list` to see all available commands
- Check server logs for detailed error messages
- Verify your map structure matches the examples above
