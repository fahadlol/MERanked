# MERanked

Paper **1.21** ranked PvP plugin with Glicko-2 ratings, per-gamemode queues, arena voting, staff tooling, and anti-abuse systems.

## Requirements

- Paper 1.21+ (or compatible fork)
- Java 21
- SQLite (default) or MySQL
- Optional: WorldEdit (arena schematics), PlaceholderAPI, Redis (live status API)

## Quick start

1. Drop the built jar in `plugins/` and start the server once to generate configs.
2. Set database in `plugins/MERanked/database.yml` (`SQLITE` or `MYSQL`).
3. Review `gamemodes.yml` — enable/disable modes and map each to a default kit id.
4. Default kits live in `kits.yml` (material strings). Edit or use `/kiteditor` + `/ranked setdefaultkit <gamemode>`.
5. Configure arenas in `arenas.yml` or `/arena` commands.
6. Grant permissions (see below) and run `/ranked` to open the menu.

## Player commands

| Command | Description |
|---------|-------------|
| `/ranked` | Main menu — queue, profile, leaderboard |
| `/ranked queue <gamemode>` | Join ranked queue |
| `/ranked leave` | Leave queue |
| `/ranked card [player]` | Identity card |
| `/kiteditor [gamemode]` | Edit your kit |
| `/matches` | List live matches |
| `/spectate <matchId>` | Spectate a match |

## Staff commands

| Command | Permission |
|---------|------------|
| `/ranked staff` | `meranked.staff` — staff center GUI |
| `/ranked alerts` | `meranked.staff` |
| `/ranked suspicious` | `meranked.staff` |
| `/ranked suspicion <player>` | `meranked.staff` |
| `/ranked rollback <matchId>` | `meranked.staff` |
| `/ranked kit audit <player> <gamemode>` | `meranked.staff` |
| `/ranked punish <player>` | `meranked.punish` |

## Key permissions

- `meranked.use` — basic ranked access
- `meranked.staff` — staff center, alerts, rollbacks
- `meranked.admin` — seasons, ratings, arena admin
- `meranked.punish` / `meranked.unpunish` — punishment GUI

## Default kits (`kits.yml`)

Kits support YAML material definitions:

```yaml
inventory:
  - material: DIAMOND_SWORD
    amount: 1
  - NETHERITE_AXE
armor:
  - material: DIAMOND_HELMET
```

Players without a saved kit receive the gamemode default from `kits.yml`.

## Database

SQLite file defaults to `plugins/MERanked/meranked.db`. For MySQL, set `type: MYSQL` and connection fields in `database.yml`. Schema is created automatically on first run; column migrations run on startup.

## Build

```bash
./gradlew build
```

Run tests:

```bash
./gradlew test
```

## Config highlights

| File | Purpose |
|------|---------|
| `gamemodes.yml` | Enabled modes, placement count, default kit ids |
| `kits.yml` | Default kit layouts per gamemode |
| `suspicion.yml` | Suspicion scoring factors and thresholds |
| `anti-boost.yml` | Same-IP, short match, opponent limits |
| `staff-center.yml` | Staff GUI layout |
| `scoreboards.yml` | Sidebar placeholders (`%mode1%`, `%mode1_tier%`, etc.) |

## Support

Report issues on the project repository. For production, always test arena regeneration, kit validation, and staff rollback on a staging server before going live.
