# Compatibility

## Build Matrix

| Minecraft | API used for compilation | Java to build | Java to run | Artifact | Status |
| --- | --- | --- | --- | --- | --- |
| 1.20.x | Paper API 1.20.1 | 17+ | 17+ | `TrueCombat-1.20.x.jar` | Build verified |
| 1.20.x | Spigot API 1.20.1 | 17+ | 17+ | `TrueCombat-Spigot-1.20.x.jar` | Build verified |
| 1.21.x | Paper API 1.21.11 | 21+ | 21+ | `TrueCombat-1.21.x.jar` | Build verified |
| 1.21.x | Spigot API 1.21.1 | 21+ | 21+ | `TrueCombat-Spigot-1.21.x.jar` | Build verified |
| 26.1.x | Paper API 26.1.1 build 29 alpha | 25+ | 25+ | `TrueCombat-26.1.x.jar` | Build verified with JDK 26 |
| 26.2 | Paper API 26.2 build 112 stable | 25+ | 25+ | `TrueCombat-26.2.jar` | Build verified with JDK 26 |

Minecraft 26.x requires Java 25 or newer. The local 26.x builds were verified with JDK 26 because it is the available JDK in the build environment.

## Supported Software

The Paper artifact is intended for Paper, Purpur, Folia, Leaf, and Patina where the matching Paper API is available. The Spigot artifact is intended for Spigot and Bukkit-compatible forks.

Arclight and Mohist are best-effort compatibility targets. They are not runtime-tested separately and should be tested with the matching server version before production deployment.

Folia support uses platform-aware global and player schedulers. The plugin declares `folia-supported: true`; use the artifact matching the server's Minecraft version.

## Version-Specific Features

Maces and Wind Charges were introduced after Minecraft 1.20.1. On 1.20.x, those features are unavailable and automatically inactive. Ender Pearl, Riptide Trident, PvP tagging, combat logging, and totem limits remain available.

## Build Commands

```text
mvn clean package -Pmc-1.20 -DskipTests
mvn clean package -Pspigot-1.20 -DskipTests
mvn clean package -Pmc-1.21 -DskipTests
mvn clean package -Pspigot-1.21 -DskipTests
mvn clean package -Pmc-26-1 -DskipTests
mvn clean package -Pmc-26 -DskipTests
```
