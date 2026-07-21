# Restore the newer command syntax

A Minecraft Forge 1.12.2 mod that backports useful parts of modern `/execute` and `/damage` commands.

## Requirements

- Minecraft 1.12.2
- Minecraft Forge 14.23.5.2847

## Commands

### Damage

```mcfunction
/damage <target> <amount> [type]
/dmg <target> <amount> [type]
```

Supported types include vanilla damage sources plus `heiwang`, `brimstone`, `rot`, and `apocalypse`.

### Execute

The backport supports chained execution with:

- `as`, `at`, `positioned`, `rotated`, `facing`
- `align`, `anchored`, `in`
- `if/unless entity`, `if/unless block`, `if/unless score`
- `run`
- Relative (`~`) and local (`^`) coordinates
- Basic modern `@s` compatibility

Features tied to modern datapacks, such as predicates and functions, are not available in Minecraft 1.12.2.

## Installation

Download the release JAR and place it in the Minecraft 1.12.2 `mods` directory.

## Building

This project uses Java 25 for Gradle and RetroFuturaGradle to produce Java 8-compatible mod binaries.

```powershell
.\gradlew.bat build
```

The release JAR is generated in `build/libs`.

## License

MIT
