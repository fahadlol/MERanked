# MERanked

Middle East Elo/Glicko queue-based tierlist system for Paper 1.21+ — **Gold Rift** theme.

## Requirements

- Paper 1.21+
- Java 21
- Optional: PlaceholderAPI, FastAsyncWorldEdit, WorldEdit

## Build

If Gradle reports a **lock timeout** on Windows (`.gradle/buildOutputCleanup/buildOutputCleanup.lock`):

```bat
fix-gradle-lock.bat
```

Or manually:

```bat
gradlew --stop
del /f /q .gradle\buildOutputCleanup\buildOutputCleanup.lock
gradlew build
```

On WSL, build from Linux path or `/tmp` if cross-filesystem I/O errors occur:

```bash
rsync -a --exclude .gradle --exclude build . /tmp/MERanked-build/
cd /tmp/MERanked-build && ./gradlew build
```

Normal build:

```bash
./gradlew build
```

Output: `build/libs/MERanked-1.0.0-SNAPSHOT.jar`

## Install

1. Drop the jar into your server's `plugins/` folder
2. Start the server to generate configs
3. Set spawn with `/setspawn`
4. Create arenas with `/arena create <name>` and configure spawns/regions
5. Enable the website API in `website.yml` if needed

## Quick Start

```
/region set KSA
/kiteditor Mace
/queue Mace
/ranked leave
/ranked
/settings
/ranked setdefaultkit Mace
```

## Architecture

Modular service layout:

- `rating` — Glicko-2 engine, tiers, placements, profiles, anti-boost
- `queue` / `matchmaking` — ranked queue with expanding rating range
- `matches` — full match lifecycle (vote → cinematic → fight → rating)
- `arenas` — cloning, regeneration, auto-disable
- `kits` — protected kit editor with hologram controls
- `replays` — batched combat event storage
- `staff` — alerts, suspicion, watchlist, rollback
- `api` — optional NanoHTTPD live data API
- `placeholders` — PlaceholderAPI bridge

## Database

SQLite by default (`plugins/MERanked/meranked.db`). Switch to MySQL in `database.yml`.

All writes are async. Profile/queue/match data is cached in memory.

## Theme

MiniMessage prefix:

`<gradient:#D6B36A:#7C3AED><bold>MERanked</bold></gradient>`

No rewards or economy systems are included.

## Permissions

- `meranked.admin` — reload, set ratings, seasons
- `meranked.staff` — alerts, rollback, spectate, watchlist
- `meranked.arena.admin` — arena setup

## License

Proprietary — MERanked
