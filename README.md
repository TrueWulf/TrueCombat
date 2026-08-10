# TrueCombat

![TrueCombat icon](TrueCombar-icon1.png)

TrueCombat is a super lightweight combat-log and PvP cooldown plugin for Paper and Purpur 1.21.x.

It keeps PvP simple: tag players after combat, punish combat logging, control combat items, and show cooldowns in a clean action bar.

## Features

- 30-second configurable PvP tag.
- Combat-log punishment with inventory drop, optional death, and optional rewards.
- Cooldowns for Ender Pearls, Riptide Tridents, Wind Charges, and Maces.
- Item cooldown overlays and a minimal action-bar HUD.
- Server-wide Ender Pearl and Wind Charge controls.
- Optional totem limit, including bundles.
- Built-in locales: English, Russian, German, French, Italian, Spanish, and Brazilian Portuguese.
- Admin commands with tab completion.

## Compatibility

- Server software: Paper and Purpur 1.21.x.
- Java: 21.

## Installation

1. Download `TrueCombat-2.1.1.jar`.
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
