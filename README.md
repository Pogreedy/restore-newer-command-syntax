# Restore the newer command syntax

A Minecraft Forge 1.12.2 mod that backports useful parts of modern `/execute`, `/damage`, and `/function` commands.

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

Features tied to newer datapack systems, such as predicates, are not available in Minecraft 1.12.2.

### Function

```mcfunction
/function <namespace>:<name>
```

Functions are read directly from directory datapacks in the current world:

```text
<world>/datapacks/<pack>/data/<namespace>/functions/<name>.mcfunction
```

For example, `example:test` is stored at:

```text
<world>/datapacks/my_pack/data/example/functions/test.mcfunction
```

Example `test.mcfunction`:

```mcfunction
# Lines beginning with # are comments.
say Function executed successfully
damage @p 4 magic
execute as @p at @s run summon lightning_bolt ~ ~ ~
```

Run it with:

```mcfunction
/function example:test
```

Commands in a function must not begin with `/`. Empty lines and comment lines are skipped. Files are read again on every execution, so changes do not require a game restart. Only directory datapacks are currently supported; ZIP datapacks and modern function macros are not.

#### Troubleshooting: `Unknown function`

This backport intentionally reads only the modern directory-datapack layout. The legacy Minecraft 1.12.2 directory below is **not** scanned:

```text
<world>/data/functions/<namespace>/<name>.mcfunction
```

If the game reports `Unknown function: namespace:name`, move the file into a directory datapack:

```text
<world>/datapacks/<any_pack_name>/data/<namespace>/functions/<name>.mcfunction
```

For example, this command:

```mcfunction
/function zhan_shen:ch1_aunt_house
```

requires this exact structure:

```text
<world>/datapacks/zhan_shen_pack/data/zhan_shen/functions/ch1_aunt_house.mcfunction
```

The `datapacks` and pack directories may need to be created manually. A `pack.mcmeta` file is not required by this mod's loader.

#### Troubleshooting: WorldEdit and ForgeEssentials permissions

Avoid wrapping WorldEdit commands in this form:

```mcfunction
execute as @p at @s run //pos1 -35 4 -7
execute as @p at @s run //set minecraft:stone
```

The backported `/execute` uses an `ICommandSender` execution-context wrapper. ForgeEssentials and WorldEdit may not recognize that wrapper as the real player, even when the player has `worldedit.*` or is using cheats in a single-player world. This can produce repeated `You do not have permission to use this command` messages.

When a player directly runs the function, put WorldEdit commands directly in the `.mcfunction` file instead:

```mcfunction
/pos1 -35 4 -7
/pos2 35 4 7
/set minecraft:stone
/walls minecraft:stonebrick
/schem save example_house
```

Use one leading `/` in this mod's `.mcfunction` files. The loader passes each line directly to `server.getCommandManager().executeCommand(sender, line)`, so it does not remove the chat command prefix.

To grant WorldEdit permissions with ForgeEssentials:

```mcfunction
/p user <player> allow worldedit.*
/p save
/p reload
```

Granting the permission does not fix the wrapped-sender incompatibility; direct WorldEdit function lines are still recommended.

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
