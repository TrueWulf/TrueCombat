<div align="center">

# TrueCombat

![TrueCombat icon](TrueCombat-icon.png)

**Fast, lightweight combat protection for Minecraft servers and networks.**

Combat tagging, combat-log punishment, item cooldowns, and PvP controls in one small, configurable plugin for Bukkit-compatible servers.

![Minecraft](https://img.shields.io/badge/Minecraft-1.20.x%20%7C%201.21.x%20%7C%2026.x-2ea043?style=flat-square)
![Java](https://img.shields.io/badge/Java-17%20%7C%2021%20%7C%2025-e76f00?style=flat-square)
![License](https://img.shields.io/badge/License-GPL--3.0-blue?style=flat-square)

[Support TrueCombat on Ko-fi](https://ko-fi.com/truewulf/goal?g=0)

</div>

## Overview

TrueCombat keeps PvP predictable and fair without adding unnecessary server overhead. It tags players during combat, handles combat logging, controls high-impact combat items, and displays active timers in a compact action bar.

The plugin is designed to be fast to start, light at runtime, easy to configure, and suitable for both standalone servers and larger Bukkit-compatible networks.

## Features

- 30-second configurable PvP tag.
- Combat-log punishment with inventory drop, optional death, and optional rewards.
- Cooldowns for Ender Pearls, Riptide Tridents, Wind Charges, and Maces.
- Item cooldown overlays and a minimal action-bar HUD.
- Server-wide Ender Pearl and Wind Charge controls.
- Optional totem limit, including bundles.
- Folia-safe scheduling and player state handling.
- Built-in locales: English, Russian, German, French, Italian, Spanish, and Brazilian Portuguese.
- Admin commands with tab completion.

## Compatibility

| Minecraft | Artifact | Java | Compatible software | Status |
| --- | --- | --- | --- | --- |
| 1.20.x | `TrueCombat-1.20.x.jar` | 17+ | Paper, Purpur, Folia, Leaf, Patina | Build verified |
| 1.20.x | `TrueCombat-Spigot-1.20.x.jar` | 17+ | Spigot and Bukkit-compatible forks | Build verified |
| 1.21.x | `TrueCombat-1.21.x.jar` | 21+ | Paper, Purpur, Folia, Leaf, Patina | Build verified |
| 1.21.x | `TrueCombat-Spigot-1.21.x.jar` | 21+ | Spigot and Bukkit-compatible forks | Build verified |
| 26.1.x | `TrueCombat-26.1.x.jar` | 25+ | Matching Paper-family servers | Build verified with JDK 26 |
| 26.2 | `TrueCombat-26.2.jar` | 25+ | Matching Paper-family servers | Build verified with JDK 26 |

TrueCombat uses Bukkit-compatible APIs and does not depend on Paper-only runtime classes. On Minecraft 1.20.x, 1.21-only items such as Maces and Wind Charges are unavailable and their features are automatically inactive.

Arclight and Mohist are best-effort compatibility targets and should be tested with the matching server version before production use.

### Folia

Folia is supported through platform-aware global and player schedulers. Install the artifact matching the Minecraft version and verify the server build in a staging environment before production deployment.

## Installation

1. Download the artifact matching your Minecraft version and server API.
2. Place it in your server `plugins` directory.
3. Restart the server.
4. Edit `plugins/TrueCombat/config.yml` if needed.

All bundled locale files are extracted to `plugins/TrueCombat/lang/` on first startup.

## Commands

| Command | Description |
| --- | --- |
| `/truecombat reload` | Reload configuration and locale files. |
| `/truecombat status` | Show plugin status. |
| `/truecombat tag <player>` | Apply a PvP tag. |
| `/truecombat untag <player>` | Remove a PvP tag. |
| `/truecombat clear <player>` | Clear all TrueCombat timers. |

Alias: `/tc`.

Permission: `truecombat.admin`.

## Combat-Log Rewards

TrueCombat does not depend on a specific hearts plugin. To make a heart drop from a combat logger, enable `combat-log.reward-commands` and configure the command provided by your hearts plugin:

```yml
combat-log:
  reward-commands:
    enabled: true
    commands:
      - 'yourhearts give <logger> heart 1'
```

The command runs before the logger's inventory is dropped, so the awarded heart drops with their items. `<logger>` is the player who disconnected; `<attacker>` is their last PvP attacker.

## Default Cooldowns

| Item | Cooldown |
| --- | --- |
| Ender Pearl | 15 seconds |
| Riptide Trident | 20 seconds |
| Wind Charge | 3 seconds |
| Mace | 60 seconds |

## License

TrueCombat is licensed under [GNU General Public License v3.0](LICENSE).

## Support

If TrueCombat is useful for your server, you can support development on [Ko-fi](https://ko-fi.com/truewulf/goal?g=0).

TrueWulf is the original author and maintainer of TrueCombat.
