# MERanked Discord Bridge

Internal bridge module that sends structured JSON events from the MERanked Paper plugin to your **external MERanked Discord bot**. The plugin never connects to Discord directly and never stores a Discord bot token.

## Architecture

```
Minecraft (MERanked)  --WebSocket/HTTP-->  MERanked Discord Bot  -->  Discord channels
                     <--requests (WS/inbound HTTP)--
```

- Plugin sends **category names** only (e.g. `punishment-logs`, `match-logs`).
- Your Discord bot maps categories to channel IDs.
- Authentication uses `discord-bridge.secret-token` (shared secret, not a Discord token).

## Setup

1. Build MERanked: `./gradlew build`
2. Copy `build/libs/MERanked-*.jar` to your Paper 1.21.x server `plugins/` folder.
3. Edit `plugins/MERanked/config.yml`:
   - Set `discord-bridge.secret-token` to a strong random value.
   - Set `discord-bridge.server-id` to identify this server.
   - Point `websocket-url` or `http-url` at your bot bridge endpoint.
4. Run your MERanked Discord bot with the same secret token.
5. Reload or restart the server.

## Connection modes

### WebSocket (recommended)

```yaml
discord-bridge:
  enabled: true
  mode: "websocket"
  websocket-url: "ws://127.0.0.1:8081/minecraft"
  secret-token: "your-shared-secret"
```

Bidirectional: bot can send lookup/ticket requests over the same socket.

### HTTP

```yaml
discord-bridge:
  mode: "http"
  http-url: "http://127.0.0.1:8081/minecraft/event"
  inbound-port: 8082
  inbound-path: "/bridge"
```

Plugin POSTs events to `http-url`. Bot POSTs requests to `http://<server>:8082/bridge` with `Authorization: Bearer <secret-token>`.

## Event format

Every log is JSON:

```json
{
  "eventId": "uuid",
  "serverId": "meranked-main",
  "category": "punishment-logs",
  "type": "PUNISHMENT_CREATED",
  "severity": "HIGH",
  "timestamp": 1720000000000,
  "summary": "BadPlayer was banned by StaffName for Cheating",
  "player": { "name": "BadPlayer", "uuid": "..." },
  "staff": { "name": "StaffName", "uuid": "..." },
  "data": {
    "punishmentType": "BAN",
    "reason": "Cheating",
    "duration": "30d",
    "punishmentId": "P1042"
  }
}
```

### Categories

| Category | Config key |
|----------|------------|
| staff-logs | `logs.staff` |
| queue-logs | `logs.queue` |
| match-logs | `logs.match` |
| suspicion-logs | `logs.suspicion` |
| report-logs | `logs.report` |
| system-logs | `logs.system` |
| arena-logs | `logs.arena` |
| punishment-logs | `logs.punishment` |
| discord-logs | `logs.discord` |

## Bot → Plugin requests

| Request type | Purpose |
|--------------|---------|
| `STAFF_STATUS_REQUEST` | Bridge status, duty count |
| `PLAYER_LOOKUP_REQUEST` | Player profile summary |
| `PUNISHMENT_LOOKUP_REQUEST` | Punishment summary |
| `REPORT_LOOKUP_REQUEST` | Report count |
| `MATCH_HISTORY_REQUEST` | Live match info |
| `SUSPICION_LOOKUP_REQUEST` | Suspicion score |
| `TICKET_PLAYER_LOOKUP` | Appeal/report evidence bundle |
| `STAFF_PING` | Health check |
| `BROADCAST_STAFF_MESSAGE` | Message online staff in-game |

Example request:

```json
{
  "requestId": "abc123",
  "type": "PLAYER_LOOKUP_REQUEST",
  "player": "ExampleName"
}
```

## Staff commands

| Command | Permission |
|---------|------------|
| `/bridge status` | `meranked.bridge.status` |
| `/bridge reload` | `meranked.bridge.reload` |
| `/bridge test <category>` | `meranked.bridge.test` |
| `/staffduty` `/duty` | `meranked.staff.duty` |
| `/staffstatus` | `meranked.staff.status` |
| `/staffpanel` | `meranked.staff.panel` |
| `/lookup <player>` | `meranked.staff.lookup` |
| `/staffnote <player> <note>` | `meranked.staff.note` |
| `/report <player> <reason>` | `meranked.report.use` |
| `/reports` | `meranked.report.review` |

## Punishments

All punishment logs use MERanked's **built-in** `PunishmentService` — no LiteBans, AdvancedBan, or external plugins.

## Security

- No Discord bot token in the plugin
- Text sanitized, IPs redacted, length capped
- Login/register/msg commands not logged
- Events queued safely when bot is offline (max configurable)
- Server does not crash if bot is unreachable

See `docs/discord-bridge-api.md` for the full API contract and more JSON examples.
