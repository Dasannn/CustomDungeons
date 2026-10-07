# CustomDungeons

A **Paper 26.3 / Java 25** plugin for building multi-room dungeons entirely in-game, through menus and tools: waves, custom mobs, abilities, combos, boss phases, keys, puzzles, ambience and rewards for the survivors. Each dungeon runs one match at a time (no per-group instances).

## Download

| Version | Minecraft / Paper | Java | Download |
|---|---|---|---|
| **1.1.0** (latest) | 26.3 | 25 | [CustomDungeons-1.1.0.jar](https://github.com/Dasannn/CustomDungeons/releases/download/v1.1.0/CustomDungeons-1.1.0.jar) · [signature](https://github.com/Dasannn/CustomDungeons/releases/download/v1.1.0/CustomDungeons-1.1.0.jar.sig) |

Release notes and older versions: [Releases](https://github.com/Dasannn/CustomDungeons/releases). Jars are signed (Ed25519); `/customdungeon update` checks the signature before installing a new version.

## Features

- **Editor GUI**: every setting is editable from `/customdungeon`, with summaries, help and the valid range shown on every numeric input.
- **Creation wizard** (`/customdungeon create <id>`): 7 guided steps with progress, particles and resumable drafts.
- **Build mode** (`/customdungeon build <id>`): a 9-tool hotbar (areas, rooms, doors, spawners, pressure plates, points, undo). Your inventory is saved and restored safely, even after a crash.
- **Mobs**: custom templates with stats, equipment, potions, scale up to 16, abilities, combos and boss phases; live testing next to you.
- **Spawner templates and library**: reuse waves across rooms and dungeons.
- **Matches**: start by command, portal or pressure plates; entrance door; rooms that wake up when entered; keys dropped by mobs or given by command (puzzles); configurable finish (immediate, delayed or no teleport) and exit plates.
- **Atmosphere**: per-room titles, sounds, music, potion effects and particles; doors with sound, dust and shake; optional intro cinematic.
- **Scoreboard** during the match (title, colors, contextual objective, hearts).
- **Disconnects**: leaving mid-match can be penalized (die and drop items on rejoin); crashes and kicks are not penalized; safe respawn outside dungeons.
- **Persistence**: SQLite (default) or MySQL; crash recovery restores doors, blocks and players.
- Messages in Spanish and English, fully customizable (`&` colors, hex and MiniMessage).

## Installation

1. Stop your Paper 26.3 server and make sure it runs on Java 25.
2. Put `CustomDungeons-1.1.0.jar` in `plugins/` and start the server. Paper downloads the database libraries on first start, so it needs access to its repositories.
3. Check `plugins/CustomDungeons/config.yml`. Prepare a dedicated world and set `dungeon-world.name` (default `dungeons`); `auto-create: true` creates it empty if missing. Build the floor and rooms before playing.
4. Restart after changing the configuration and give the admin permissions to whoever will edit dungeons.

All integrations are optional; CustomDungeons starts without them:

| Plugin | Use |
|---|---|
| Vault + an economy provider | Money rewards. Without an economy, money is skipped with a console warning. |
| LuckPerms | Manage the CustomDungeons permission nodes. |
| WorldGuard | Protect the terrain; CustomDungeons does not replace that protection. See the [flags and mob spawning guide](docs/guides/worldguard.md). |
| Multiverse-Core | Create and load the dedicated world. |
| Multiverse-Portals 5.3.0+ | Enter through portals that run the `join` command. |

## Getting started

1. Run `/customdungeon create crypt` and follow the wizard, or open `/customdungeon` and use **Add** in the dungeon list.
2. In the **Mob library**, create templates (lowercase id, numbers, `_` or `-`, up to 32 chars): type, stats, equipment, potions, abilities, combos and phases. Use **Live test** to spawn one next to you.
3. Enter **build mode** with `/customdungeon build crypt` (or the editor button) to mark the area, rooms, doors, spawners, plates and points with the hotbar tools. Particles show everything you configured. Leave with `/customdungeon build exit` to get your inventory back.
4. Back in the editor, set waves, keys, ambience, scoreboard, rewards and settings (lobby, exit, lives, players, countdown, cooldown, start and finish modes). **Save** validates everything and lists any errors.
5. Test without rewards with `/customdungeon test crypt` (`skipwave`, `invulnerable`, `stop crypt`), then try a real entry with `/customdungeon join crypt`.

Ready-made examples (YAML, build function and portal) are in [`docs/reference/ejemplos/`](docs/reference/ejemplos/): Warden's Lair, Temple of Plates, Enigma Maze and Abyssal Colossus.

### Portals (Multiverse-Portals)

Select the portal volume with `/mvp wand` (or WorldEdit `//wand`) and run as an admin:

```text
/mvp create crypt_entrance
/mvp modify crypt_entrance action-type command
/mvp modify crypt_entrance action "console:customdungeon join %player% crypt"
/mvp modify crypt_entrance action-success-message @disabled
```

The player needs `multiverse.portal.access.crypt_entrance`, `customdungeons.player.join` and, if the dungeon requires it, `customdungeons.join.crypt`. CustomDungeons still checks state, permissions, limit and cooldown when the console runs the join.

## Commands and permissions

All subcommands start with `/customdungeon`. Aliases can be set in `command-aliases` (none by default).

| Command | Permission | Use |
|---|---|---|
| `/customdungeon` | `customdungeons.admin.edit` | Open the menu. |
| `create <id>` | `customdungeons.admin.edit` | Start the creation wizard. |
| `build <dungeon>` / `build exit` | `customdungeons.admin.edit` | Enter or leave build mode. |
| `tool <type>` / `tool clear` | `customdungeons.admin.tools` | Get or clear a single admin tool. |
| `test <dungeon>` | `customdungeons.admin.test` | Start a test match without rewards. |
| `livetest stop` | `customdungeons.admin.edit` | Stop a mob live test. |
| `start` / `stop` / `reset <dungeon>` | `customdungeons.admin.control` | Force start, stop or reset a match. |
| `show <dungeon>` | `customdungeons.admin.edit` | Preview the dungeon for 30 seconds. |
| `key give <players> [dungeon]` | `customdungeons.admin.key` | Give a room key (works from console and command blocks). |
| `reload` | `customdungeons.admin.reload` | Reload dungeons, mobs and messages (all dungeons must be free). |
| `update` / `update check` / `update confirm` | `customdungeons.admin.update` | Check for and install the latest signed release. |
| `debug` | `customdungeons.admin.debug` | Toggle debug mode. |
| `join <dungeon>` | `customdungeons.player.join` | Join (plus `customdungeons.join.<id>` if required). |
| `join <player> <dungeon>` | Console or `customdungeons.admin.join.others` | Put an online player into a dungeon. |
| `leave` / `stats` / `claim` | `customdungeons.player.leave` / `.stats` / `.claim` | Leave, see your stats, claim pending rewards. |
| `skipwave` / `invulnerable` | `customdungeons.admin.test` or `customdungeons.admin.debug` | Only inside a test match or with debug on. |
| (no command) | `customdungeons.bypass.cooldown` / `customdungeons.bypass.limit` | Ignore cooldown / player limit. |

`customdungeons.admin` groups the admin nodes (ops by default). `customdungeons.player.*` groups `join`, `leave`, `stats` and `claim` (everyone by default).

## Configuration and data

Files live in `plugins/CustomDungeons/`:

- `config.yml`: prefix, language (`es`/`en`), database, world, dungeon defaults, mob and particle limits, sounds, aliases and updater. See the [default file](src/main/resources/config.yml).
- `messages.yml` / `messages_en.yml`: all texts. New keys are added automatically on update.
- `dungeons/<id>.yml` and `mobs/<id>.yml`: definitions saved by the GUI, also editable by hand. Out-of-range values are clamped on load with a warning.
- Database: SQLite by default (`data.db`). For MySQL set `database.type: mysql` and `host`, `port`, `database`, `user`, `password`, `pool-size`.

`/customdungeon reload` does not reload `config.yml` services (database, limits, defaults, aliases): restart the server for those.

## Crash recovery

On startup, interrupted matches are aborted: mobs and keys are removed, doors and temporary blocks are restored, and affected players are sent to the exit when they join. Matches are not resumed. Keep backups of the world, the plugin folder and the database.

## Compatibility notes

- **EssentialsX AntiBuild** cancels drops for players without `essentials.build.drop.*`, so items vanish on death inside a dungeon. Grant that permission in the dungeon world only, e.g. with LuckPerms: `lp group default permission set essentials.build.drop.* true world=<dungeon-world>`.

## Building from source

With JDK 25, from the repository root:

```bash
./gradlew build
```

The jar is written to `build/libs/CustomDungeons-<version>.jar`.

## Documentation

Project documents and guides are written in Spanish:
[specification](docs/spec.md), [architecture](ARCHITECTURE.md), [demo dungeon walkthrough](docs/guides/dungeon-demo.md), [Multiverse-Portals](docs/guides/multiverse-portals.md), [abilities reference](docs/reference/habilidades.md), [integration tests](docs/guides/pruebas-integradas.md) and [release process](docs/guides/release.md).
