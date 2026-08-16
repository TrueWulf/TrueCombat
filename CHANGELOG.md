# Changelog

## 2.3.2

- Changed the default build to Paper 1.21.x with Java 21.
- Fixed combat-tag cleanup after a kill while preserving combat with other opponents.
- Prevented active PvP from ending immediately because of a victim's death cleanup.

## 2.3.1

- Fixed PvP timers configured as zero ending immediately.
- Applied combat-log reward commands when a tagged player dies from any cause.
- Added short locale names such as `ru` and `en` and logged the loaded locale.

## 2.3.0

- Added Sponge API 8.2.x combat adapter.
- Added Velocity 3.4.x proxy guard for synchronized combat sessions.
- Added Waterfall/BungeeCord-compatible proxy guard for synchronized combat sessions.
- Added shared plugin-message protocol for backend combat state synchronization.
- Added expanded configuration, admin commands, permissions, and tab completion.
- Standardized the repository license as GNU GPLv3.

## 2.2.0

- Added compatibility profiles for Minecraft 1.20.x, 1.21.x, and 26.x.
- Added Paper, Spigot, Bukkit-compatible, Purpur, Folia, Leaf, and Patina compatibility guidance.
- Added Folia-aware global and player scheduling.
- Removed direct runtime dependencies on 1.21-only material constants.
- Bundled Adventure messaging libraries for consistent MiniMessage rendering.
- Fixed combat state tracking when a player is fighting multiple opponents.
- Moved combat logger persistence to a dedicated single-threaded saver.
- Added safe shutdown handling for scheduled tasks and persistence.
- Expanded the README with installation, artifact selection, compatibility, and support information.

## 2.1.1

- Added combat logging, PvP tagging, combat item cooldowns, totem limits, locales, and administration commands.
