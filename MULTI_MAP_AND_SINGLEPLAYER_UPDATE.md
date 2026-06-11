# Tower Defense: Multi-Map & Single Player Update Summary

This document summarizes the massive architectural refactor and feature implementation completed to support concurrent matches and a dedicated Single Player mode.

## 1. Concurrent Match Architecture
The plugin has been transformed from a singleton state to a **multi-instance architecture**, allowing many games to run simultaneously.
*   **`Match` Class:** Encapsulates all isolated game state including economy (Gold/XP), arena health, active mobs, and placed towers.
*   **`MatchManager` Integration:** `GameManager` now acts as a container for multiple `Match` instances, ensuring state isolation between different worlds.
*   **Instance-Aware Systems:** `MobManager`, `TowerManager`, and all listeners were refactored to require a `Match` context for spawning, ticking, and damage logic.

## 2. Dynamic Map Template System
A modular map system replaces the legacy hardcoded configuration files.
*   **Directory Structure:** Maps are now stored as independent packages in `GAME_WORLD_TEMPLATES/` (categorized by `SINGLE_PLAYER` and `MULTI_PLAYER`).
*   **Map Packages:** Each map is self-contained with its own world data, `map.yml` (metadata), `plots.yml` (tower locations), and `waypoints.yml` (mob pathing).
*   **World Cloning:** When a match starts, the chosen template is dynamically cloned into a unique world folder (e.g., `match_xxxx`) and loaded by the server.

## 3. Matchmaking & Voting System
The matchmaking flow has been overhauled to include a player-driven map selection phase.
*   **`QueueManager`:** Implements separate queues for Single Player (instant start) and Multiplayer (waits for required players).
*   **Voting GUI:** When a queue is full, players enter a 15-second voting phase to choose between three randomly selected maps from the template library.

## 4. Single Player Wave Engine
A robust automated spawning system for solo players.
*   **`WaveManager`:** A dedicated engine that drives progression in Single Player matches.
*   **100 Predefined Waves:** A complete balanced progression defined in `waves.yml`, featuring tiered mobs and tactical multi-group compositions.
*   **Endgame Progression:** The system supports a transition from Wave 100 to an algorithmic Endless Mode.

## 5. Streamlined Admin Tooling & Template Editing
Updated tools to support the new template-based map system with live editing capabilities.
*   **`/td loadworld <name>`:** Load a template world from `GAME_WORLD_TEMPLATES` for editing. Creates a temporary copy at server root.
*   **`/td unloadworld <name>`:** Manually unload and clean up a template copy, saving level.dat changes back to the original template.
*   **`/td deleteplotmode [arena]`:** Toggle plot deletion mode to remove plots with the wand.
*   **`/td clearwaypoints [arena]`:** Wipe all waypoints for a specific arena.
*   **Direct Template Editing:** Plots and waypoints automatically save to the template's own config files when editing loaded template worlds (no need for `/td saveconfig`).
*   **Auto-cleanup System:** `WorldUnloadListener` automatically unloads, saves level.dat, and deletes temporary world copies when the last player leaves.
*   **Paper Migration Handling:** Deletion system handles both original world location and Paper's migrated location (`lobby_world/dimensions/minecraft/<worldname>`).
*   **World Structure Validation:** Added checks to ensure templates use `region/` folder at world root (not nested in `dimensions/`), with helpful error messages for migration issues.

## 6. Map Creation Documentation
*   **MAP_SETUP_GUIDE.md:** Comprehensive guide covering world structure, template creation, plot/waypoint setup, and common troubleshooting.
*   **Workflow Simplification:** Load → Edit → Auto-save → Auto-cleanup workflow eliminates manual export/import steps.
*   **Spawn Point Preservation:** Level.dat (containing spawn points and gamerules) automatically saved back to template on world unload.

## 7. Technical Integrity & Bug Fixes
*   **Type Alignment:** Fixed significant type mismatches in `QueueManager` and `GameManager` relating to `java.util.List` usage.
*   **Mob Spawning:** Standardized 8 different `spawnMob` overloads to ensure match-awareness across the entire plugin.
*   **Legacy Fallbacks:** Maintained compatibility for Lobby-side features (Tab-List, Scoreboards) while migrating active gameplay to the instance system.
*   **Missing Commands:** Implemented previously-documented but missing commands (`deleteplotmode`, `clearwaypoints`).
*   **Template Tracking:** Added tracking system in `TDCommand` to monitor which loaded worlds are template copies.
*   **File Deletion Reliability:** Implemented retry logic for Windows file locking issues during world folder deletion.

---
**Status:** Implementation Complete. Ready for Maven Compilation and Deployment.
