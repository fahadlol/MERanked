# MERanked Discord Bridge API Contract

## Authentication

All requests must include:

```
Authorization: Bearer <discord-bridge.secret-token>
X-MERanked-Server: <discord-bridge.server-id>
```

## Outbound events (Plugin → Bot)

### Punishment created

```json
{
  "eventId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "serverId": "meranked-main",
  "category": "punishment-logs",
  "type": "PUNISHMENT_CREATED",
  "severity": "HIGH",
  "timestamp": 1720000000000,
  "player": { "name": "BadPlayer", "uuid": "550e8400-e29b-41d4-a716-446655440000" },
  "staff": { "name": "StaffName", "uuid": "660e8400-e29b-41d4-a716-446655440001" },
  "summary": "BadPlayer was punished (BAN) by StaffName",
  "data": {
    "punishmentId": "P1A2B3C",
    "punishmentType": "BAN",
    "reason": "Cheating",
    "duration": "30d",
    "createdAt": 1720000000000,
    "expiresAt": 1722592000000,
    "active": true,
    "silent": false
  }
}
```

### Match end

```json
{
  "category": "match-logs",
  "type": "MATCH_END",
  "severity": "INFO",
  "summary": "PlayerA beat PlayerB (1-0)",
  "data": {
    "matchId": "A1B2C3D4",
    "gamemode": "mace",
    "arena": "desert-duel",
    "winner": "PlayerA",
    "loser": "PlayerB",
    "score": "1-0",
    "durationMs": 45000,
    "ratingChange": { "uuid1": 24.5, "uuid2": -24.5 }
  }
}
```

### Suspicion alert

```json
{
  "category": "suspicion-logs",
  "type": "FRIEND_FARMING_SUSPECTED",
  "severity": "HIGH",
  "summary": "Possible friend farming: PlayerA fought PlayerB 8 times in 24 hours.",
  "data": {
    "suspicionScore": 82,
    "reason": "Repeated matches against same player",
    "relatedPlayers": ["PlayerA", "PlayerB"],
    "evidence": ["8 matches in 24h", "3 gamemodes"],
    "recommendedAction": "Staff review recommended"
  }
}
```

### Report created

```json
{
  "category": "report-logs",
  "type": "REPORT_CREATED",
  "severity": "MEDIUM",
  "summary": "Reporter reported Target: reason text",
  "data": {
    "reportId": "RPT-XYZ",
    "reason": "Cheating in ranked",
    "world": "world"
  }
}
```

### Staff duty

```json
{
  "category": "staff-logs",
  "type": "STAFF_DUTY_ON",
  "severity": "INFO",
  "summary": "StaffName is now on duty",
  "staff": { "name": "StaffName", "uuid": "..." }
}
```

### System

```json
{
  "category": "system-logs",
  "type": "DISCORD_BRIDGE_CONNECTED",
  "severity": "INFO",
  "summary": "Discord bridge connected"
}
```

## Inbound requests (Bot → Plugin)

### Player lookup response shape

```json
{
  "requestId": "abc123",
  "success": true,
  "type": "PLAYER_LOOKUP_RESPONSE",
  "player": {
    "name": "ExampleName",
    "uuid": "...",
    "online": true,
    "lastSeen": 1720000000000,
    "firstJoined": 1710000000000,
    "rank": "HT3",
    "region": "ME",
    "currentWorld": "world"
  }
}
```

### Ticket evidence (appeals/reports)

```json
{
  "requestId": "abc123",
  "success": true,
  "type": "TICKET_PLAYER_LOOKUP_RESPONSE",
  "ticketEvidence": {
    "player": { "name": "...", "uuid": "...", "online": false },
    "suspicionScore": 42,
    "reports": { "reportCount": 2 },
    "activePunishments": 1,
    "staffNotesCount": 3
  }
}
```

## Heartbeat

Plugin sends periodically:

```json
{
  "type": "HEARTBEAT",
  "serverId": "meranked-main",
  "timestamp": 1720000000000
}
```

## Event types reference

### Punishment
`PUNISHMENT_CREATED`, `PUNISHMENT_REMOVED`, `PUNISHMENT_EXPIRED`, `PUNISHMENT_REDUCED`, `PUNISHMENT_COMMAND_FAILED`, `APPEAL_ACCEPTED`, `APPEAL_DENIED`

### Queue
`QUEUE_JOIN`, `QUEUE_LEAVE`, `QUEUE_DODGE`, `MATCH_FOUND`, `QUEUE_TIMEOUT`, `QUEUE_CANCELLED`

### Match
`MATCH_START`, `MATCH_END`, `MATCH_CANCELLED`, `MATCH_DISCONNECT`

### Report
`REPORT_CREATED`, `REPORT_REVIEWED`, `REPORT_MARKED_VALID`, `REPORT_MARKED_INVALID`

### Staff
`STAFF_JOIN`, `STAFF_LEAVE`, `STAFF_DUTY_ON`, `STAFF_DUTY_OFF`, `STAFF_COMMAND`, `STAFF_DANGEROUS_COMMAND`, `STAFF_TELEPORT`, `STAFF_NOTE_ADDED`

### System
`PLUGIN_ENABLED`, `PLUGIN_DISABLED`, `DISCORD_BRIDGE_CONNECTED`, `DISCORD_BRIDGE_DISCONNECTED`, `DISCORD_BRIDGE_RECONNECT_FAILED`, `TPS_WARNING`, `MEMORY_WARNING`

### Arena
`ARENA_CREATED`, `ARENA_REMOVED`, `ARENA_DISABLED`, `ARENA_SELECTED`, `ARENA_RESET_COMPLETE`, `ARENA_RESET_FAILED`

### Suspicion
`FRIEND_FARMING_SUSPECTED`, `RATING_SPIKE`, `QUEUE_GHOSTING`, `REGION_SWITCHING`, etc.

## Bot implementation notes

1. Verify `Authorization` header on every connection/message.
2. Map `category` string → Discord channel ID in bot config (not in plugin).
3. Render embeds from `summary`, `severity`, `type`, and `data`.
4. Never expose raw IPs from plugin data (plugin redacts them).
5. Use `STAFF_STATUS_REQUEST` + duty events for ticket routing.
6. Plugin does not auto-decide appeals/reports — staff only.
